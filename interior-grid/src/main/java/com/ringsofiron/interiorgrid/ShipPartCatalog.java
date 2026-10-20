package com.ringsofiron.interiorgrid;

import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tile types and ship components a chassis map can draw, each under its one-character
 * symbol. Tile types and components share that namespace.
 *
 * {@link #builtIn()} holds the characters the game has always had ({@code #}, {@code 1}-{@code 6},
 * {@code I}, {@code .}, {@code m} (a medbay's deck), {@code +}, space, and the system letters {@code R S T L H W C A D}, plus
 * {@code E} for the engine, {@code P O N} for propellant, oxygen and nitrogen tanks, and {@code B} for an escape pod (lifeboat)).
 * {@link #from(DefRegistry)} starts from those and applies the {@code [tile.*]} and
 * {@code [component.*]} definitions, which replace a built-in of the same id or add new ones.
 */
public class ShipPartCatalog {

    /** What a tile type builds. */
    public enum TileBase {
        VOID, FLOOR, CONDUIT, HULL, INTERNAL_WALL;

        public boolean isWall() {
            return this == HULL || this == INTERNAL_WALL;
        }
    }

    /** A tile type: one map character and the tile it builds. */
    public static class TileType {
        public final String id;
        public final String name;
        public final char symbol;
        public final TileBase base;
        public final WeightClass weightClass;
        public final String material;
        public final FloorCovering covering;
        /** 0xRRGGBB, for the editor. */
        public final int color;
        /** The art it draws with (a region, or a wall family), or null to go by base and material. */
        public final String art;

        public TileType(String id, String name, char symbol, TileBase base, WeightClass weightClass,
                        String material, FloorCovering covering, int color) {
            this(id, name, symbol, base, weightClass, material, covering, color, null);
        }

        public TileType(String id, String name, char symbol, TileBase base, WeightClass weightClass,
                        String material, FloorCovering covering, int color, String art) {
            this.art = art;
            this.id = id;
            this.name = name;
            this.symbol = symbol;
            this.base = base;
            this.weightClass = weightClass;
            this.material = material;
            this.covering = covering;
            this.color = color;
        }

        /** Sets a fresh tile up as this type. */
        public void apply(Tile tile, float tileSize) {
            switch (base) {
                case HULL:
                    tile.wallType = Tile.WallType.HULL;
                    tile.floorType = Tile.FloorType.NONE;
                    break;
                case INTERNAL_WALL:
                    tile.wallType = Tile.WallType.INTERNAL;
                    tile.floorType = Tile.FloorType.NONE;
                    break;
                case FLOOR:
                    tile.floorType = Tile.FloorType.FLOOR;
                    break;
                case CONDUIT:
                    tile.floorType = Tile.FloorType.POWER_CONDUIT;
                    break;
                default:
                    return;
            }
            if (base.isWall()) {
                tile.weightClass = weightClass;
                tile.material = material;
            } else if (covering != FloorCovering.NONE) {
                tile.setCovering(covering, tileSize);
            }
        }
    }

    /** A ship component: a map letter for a system built on one subsystem behavior. */
    public static class ComponentType {
        public final String id;
        public final String name;
        public final char symbol;
        public final SubsystemComponent.SubsystemType behavior;
        /** Mount sizes it comes in. */
        public final EnumSet<MountFootprint.MountSize> sizes;
        public final int color;
        /** REACTOR: watts at full health by size; sizes missing here use the standard output. */
        public final Map<MountFootprint.MountSize, Double> powerOutput;
        /** LIFE_SUPPORT: multiplier on scrubbing, air and oxygen supply. */
        public final float throughput;
        /** TANK: what it holds; null for every other behavior. */
        public final Fluid fluid;
        /** TANK: kilograms it holds by size; sizes missing here hold {@link Fluid#capacityPerTile} a tile. */
        public final Map<MountFootprint.MountSize, Double> capacity;
        /** Chance a damaging hit shorts a system of this component; below 0, the standard for its behavior (see {@link DamageStates}). */
        public float shortChance = -1f;
        /** TANK: health below which a hit can burst it; below 0, the standard. */
        public float burstThreshold = -1f;
        /** TANK: chance each hit below the threshold bursts it; below 0, the standard for its fluid. */
        public float burstChance = -1f;

        public ComponentType(String id, String name, char symbol, SubsystemComponent.SubsystemType behavior,
                             EnumSet<MountFootprint.MountSize> sizes, int color,
                             Map<MountFootprint.MountSize, Double> powerOutput, float throughput) {
            this(id, name, symbol, behavior, sizes, color, powerOutput, throughput, null, Collections.emptyMap());
        }

        public ComponentType(String id, String name, char symbol, SubsystemComponent.SubsystemType behavior,
                             EnumSet<MountFootprint.MountSize> sizes, int color,
                             Map<MountFootprint.MountSize, Double> powerOutput, float throughput,
                             Fluid fluid, Map<MountFootprint.MountSize, Double> capacity) {
            if (behavior == SubsystemComponent.SubsystemType.TANK && fluid == null) {
                throw new IllegalArgumentException("component " + id + ": a TANK needs a fluid");
            }
            this.fluid = behavior == SubsystemComponent.SubsystemType.TANK ? fluid : null;
            this.capacity = capacity;
            this.id = id;
            this.name = name;
            this.symbol = symbol;
            this.behavior = behavior;
            this.sizes = sizes;
            this.color = color;
            this.powerOutput = powerOutput;
            this.throughput = throughput;
        }

        public MountFootprint.MountKind mountKind() {
            return behavior.mountKind;
        }

        /** Rated output of a reactor of this component in a mount of {@code size}, watts. */
        public double ratedOutput(MountFootprint.MountSize size) {
            Double w = powerOutput.get(size);
            return w != null ? w : PowerRoutingSystem.standardOutput(size);
        }

        /** Kilograms a tank of this component in a mount of {@code size} holds; 0 for anything but a tank. */
        public double capacity(MountFootprint.MountSize size) {
            if (fluid == null) return 0;
            Double kg = capacity.get(size);
            if (kg != null) return kg;
            return (double) fluid.capacityPerTile * size.baseWidth(mountKind()) * size.baseHeight(mountKind());
        }

        /** Copies this component's identity and settings onto a system installed from it. A tank starts full. */
        public void configure(SubsystemComponent system) {
            system.component = id;
            system.ratedOutput = behavior == SubsystemComponent.SubsystemType.REACTOR
                    ? ratedOutput(system.footprint.size) : 0;
            system.throughput = throughput;
            system.fluid = fluid;
            system.capacity = capacity(system.footprint.size);
            system.contents = system.capacity;
            system.shortChance = shortChance;
            system.burstThreshold = fluid != null ? burstThreshold : -1f;
            system.burstChance = fluid != null ? burstChance : -1f;
        }
    }

    /** Every size that exists for a kind of mount. */
    public static EnumSet<MountFootprint.MountSize> allSizes(MountFootprint.MountKind kind) {
        EnumSet<MountFootprint.MountSize> out = EnumSet.noneOf(MountFootprint.MountSize.class);
        for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) if (s.exists(kind)) out.add(s);
        return out;
    }

    private final Map<String, TileType> tiles = new LinkedHashMap<>();
    private final Map<String, ComponentType> components = new LinkedHashMap<>();
    private final Map<Character, TileType> tileBySymbol = new HashMap<>();
    private final Map<Character, ComponentType> componentBySymbol = new HashMap<>();
    private final Map<SubsystemComponent.SubsystemType, ComponentType> standard = new HashMap<>();

    private static final ShipPartCatalog BUILT_IN = createBuiltIn();

    /** The catalog of built-in characters only. Shared; do not change it. */
    public static ShipPartCatalog builtIn() {
        return BUILT_IN;
    }

    private static ShipPartCatalog createBuiltIn() {
        ShipPartCatalog c = new ShipPartCatalog();
        String steel = MaterialTable.DEFAULT_MATERIAL;
        c.put(new TileType("void", "Void", ' ', TileBase.VOID, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x000000));
        c.put(new TileType("floor", "Steel floor", '.', TileBase.FLOOR, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x4a5058));
        c.put(new TileType("medbay", "Medbay deck", 'm', TileBase.FLOOR, WeightClass.MEDIUM, steel, FloorCovering.MEDBAY, 0x9fc4c0));
        c.put(new TileType("conduit", "Power conduit", '+', TileBase.CONDUIT, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x5a6a3a));
        c.put(new TileType("hull", "Hull, medium", '#', TileBase.HULL, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x9aa4b0));
        c.put(new TileType("hull_light", "Hull, light", '1', TileBase.HULL, WeightClass.LIGHT, steel, FloorCovering.NONE, 0xb8c0ca));
        c.put(new TileType("hull_medium", "Hull, medium (2)", '2', TileBase.HULL, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x9aa4b0));
        c.put(new TileType("hull_heavy", "Hull, heavy", '3', TileBase.HULL, WeightClass.HEAVY, steel, FloorCovering.NONE, 0x6c7684));
        c.put(new TileType("internal_wall", "Internal wall", 'I', TileBase.INTERNAL_WALL, WeightClass.MEDIUM, steel, FloorCovering.NONE, 0x80705a));
        String composite = "composite_armor";
        c.put(new TileType("armor_light", "Composite armor, light", '4', TileBase.HULL, WeightClass.LIGHT, composite, FloorCovering.NONE, 0x7d8c88));
        c.put(new TileType("armor_medium", "Composite armor, medium", '5', TileBase.HULL, WeightClass.MEDIUM, composite, FloorCovering.NONE, 0x66756f));
        c.put(new TileType("armor_heavy", "Composite armor, heavy", '6', TileBase.HULL, WeightClass.HEAVY, composite, FloorCovering.NONE, 0x4d5a56));
        builtInComponent(c, "reactor", "Reactor", 'R', SubsystemComponent.SubsystemType.REACTOR, 0xe0703a);
        builtInComponent(c, "shield_emitter", "Shield emitter", 'S', SubsystemComponent.SubsystemType.SHIELD_EMITTER, 0x4aa0e0);
        builtInComponent(c, "turret_mount", "Turret mount", 'T', SubsystemComponent.SubsystemType.TURRET_MOUNT, 0xd04a4a);
        builtInComponent(c, "life_support", "Life support", 'L', SubsystemComponent.SubsystemType.LIFE_SUPPORT, 0x4ac07a);
        builtInComponent(c, "helm", "Helm", 'H', SubsystemComponent.SubsystemType.HELM, 0xe0d04a);
        builtInComponent(c, "weapons", "Weapons console", 'W', SubsystemComponent.SubsystemType.WEAPONS, 0xc06ad0);
        builtInComponent(c, "cargo", "Cargo bay", 'C', SubsystemComponent.SubsystemType.CARGO, 0xa08050);
        builtInComponent(c, "airlock", "Airlock", 'A', SubsystemComponent.SubsystemType.AIRLOCK, 0x50c0c0);
        builtInComponent(c, "engine", "Engine", 'E', SubsystemComponent.SubsystemType.ENGINE, 0x6aa8e8);
        builtInComponent(c, "docking_port", "Docking port", 'D', SubsystemComponent.SubsystemType.DOCKING_PORT, 0xe0e0e0);
        builtInComponent(c, "escape_pod", "Escape pod", 'B', SubsystemComponent.SubsystemType.ESCAPE_POD, 0xff7a2a);
        builtInTank(c, "tank", "Propellant tank", 'P', Fluid.PROPELLANT, 0xc08a3a);
        builtInTank(c, "oxygen_tank", "Oxygen tank", 'O', Fluid.OXYGEN, 0x6aa8e0);
        builtInTank(c, "nitrogen_tank", "Nitrogen tank", 'N', Fluid.NITROGEN, 0x8a94a8);
        return c;
    }

    private static void builtInComponent(ShipPartCatalog c, String id, String name, char symbol,
                                         SubsystemComponent.SubsystemType behavior, int color) {
        c.put(new ComponentType(id, name, symbol, behavior, allSizes(behavior.mountKind), color,
                Collections.emptyMap(), 1f));
    }

    private static void builtInTank(ShipPartCatalog c, String id, String name, char symbol, Fluid fluid, int color) {
        SubsystemComponent.SubsystemType tank = SubsystemComponent.SubsystemType.TANK;
        c.put(new ComponentType(id, name, symbol, tank, allSizes(tank.mountKind), color,
                Collections.emptyMap(), 1f, fluid, Collections.emptyMap()));
    }

    /** A catalog of the built-ins overlaid with every tile and component definition. */
    public static ShipPartCatalog from(DefRegistry defs) {
        ShipPartCatalog c = new ShipPartCatalog();
        for (TileType t : BUILT_IN.tiles.values()) c.tiles.put(t.id, t);
        for (ComponentType t : BUILT_IN.components.values()) c.components.put(t.id, t);
        if (defs.registeredClass("tile") != null) {
            for (TileTypeDef d : defs.all("tile", TileTypeDef.class)) c.tiles.put(d.id, toTileType(d));
        }
        if (defs.registeredClass("component") != null) {
            for (ShipComponentDef d : defs.all("component", ShipComponentDef.class)) c.components.put(d.id, toComponentType(d));
        }
        c.reindex();
        return c;
    }

    /**
     * Builds a catalog from explicit lists, as the editor does.
     *
     * @param problems if not null, a symbol used twice is listed here and the later user skipped
     *                 instead of throwing
     */
    public static ShipPartCatalog of(Collection<TileType> tiles, Collection<ComponentType> components,
                                     List<String> problems) {
        ShipPartCatalog c = new ShipPartCatalog();
        Map<Character, String> owner = new HashMap<>();
        for (TileType t : tiles) {
            if (problems == null || claimLeniently(owner, t.symbol, "tile " + t.id, problems)) c.tiles.put(t.id, t);
        }
        for (ComponentType t : components) {
            if (problems == null || claimLeniently(owner, t.symbol, "component " + t.id, problems)) c.components.put(t.id, t);
        }
        c.reindex();
        return c;
    }

    private static boolean claimLeniently(Map<Character, String> owner, char symbol, String who, List<String> problems) {
        String prev = owner.putIfAbsent(symbol, who);
        if (prev == null) return true;
        problems.add("Symbol '" + symbol + "' is used by both " + prev + " and " + who);
        return false;
    }

    private void put(TileType t) {
        tiles.put(t.id, t);
        reindex();
    }

    private void put(ComponentType t) {
        components.put(t.id, t);
        reindex();
    }

    /** Rebuilds the symbol lookups. @throws IllegalArgumentException on a symbol used twice. */
    private void reindex() {
        tileBySymbol.clear();
        componentBySymbol.clear();
        standard.clear();
        Map<Character, String> owner = new HashMap<>();
        for (TileType t : tiles.values()) {
            claim(owner, t.symbol, "tile " + t.id);
            tileBySymbol.put(t.symbol, t);
        }
        for (ComponentType t : components.values()) {
            claim(owner, t.symbol, "component " + t.id);
            componentBySymbol.put(t.symbol, t);
        }
        for (ComponentType t : standardOrder()) standard.putIfAbsent(t.behavior, t);
    }

    /** Components with built-in ids first, so each behavior's standard component is its built-in one if present. */
    private List<ComponentType> standardOrder() {
        List<ComponentType> out = new ArrayList<>();
        for (SubsystemComponent.SubsystemType type : SubsystemComponent.SubsystemType.values()) {
            ComponentType t = components.get(type.name().toLowerCase());
            if (t != null && t.behavior == type) out.add(t);
        }
        out.addAll(components.values());
        return out;
    }

    private static void claim(Map<Character, String> owner, char symbol, String who) {
        String prev = owner.put(symbol, who);
        if (prev != null) {
            throw new IllegalArgumentException("Symbol '" + symbol + "' is used by both " + prev + " and " + who);
        }
    }

    /** The definition that describes a tile type, as a data file would. */
    public static TileTypeDef toDef(TileType t) {
        TileTypeDef d = new TileTypeDef();
        d.id = t.id;
        d.name = t.name;
        d.symbol = String.valueOf(t.symbol);
        d.base = t.base.name();
        d.weightClass = t.weightClass.name();
        d.material = t.material;
        d.covering = t.covering.name();
        d.color = String.format("#%06x", t.color);
        d.art = t.art;
        return d;
    }

    /** The definition that describes a component, as a data file would. */
    public static ShipComponentDef toDef(ComponentType t) {
        ShipComponentDef d = new ShipComponentDef();
        d.id = t.id;
        d.name = t.name;
        d.symbol = String.valueOf(t.symbol);
        d.behavior = t.behavior.name();
        if (!t.sizes.equals(allSizes(t.behavior.mountKind))) {
            for (MountFootprint.MountSize size : t.sizes) d.sizes.add(size.name());
        }
        d.color = String.format("#%06x", t.color);
        for (MountFootprint.MountSize size : MountFootprint.MountSize.values()) {
            Double w = t.powerOutput.get(size);
            if (w != null) d.powerOutput.put(size.name(), w);
        }
        d.throughput = t.throughput;
        if (t.fluid != null) d.fluid = t.fluid.name();
        for (MountFootprint.MountSize size : MountFootprint.MountSize.values()) {
            Double kg = t.capacity.get(size);
            if (kg != null) d.capacity.put(size.name(), kg);
        }
        if (t.shortChance >= 0f) d.shortChance = t.shortChance;
        if (t.burstThreshold >= 0f) d.burstThreshold = t.burstThreshold;
        if (t.burstChance >= 0f) d.burstChance = t.burstChance;
        return d;
    }

    /** The tile type a definition describes. @throws IllegalArgumentException if a field is invalid */
    public static TileType toTileType(TileTypeDef d) {
        TileBase base = parseEnum(TileBase.class, d.base, "tile " + d.id + " base");
        return new TileType(d.id, d.name != null ? d.name : d.id, symbol(d.symbol, "tile " + d.id), base,
                parseEnum(WeightClass.class, d.weightClass, "tile " + d.id + " weight_class"),
                d.material != null ? d.material : MaterialTable.DEFAULT_MATERIAL,
                parseEnum(FloorCovering.class, d.covering, "tile " + d.id + " covering"),
                parseColor(d.color, 0x808080), d.art == null || d.art.isEmpty() ? null : d.art);
    }

    /** The component a definition describes. @throws IllegalArgumentException if a field is invalid */
    public static ComponentType toComponentType(ShipComponentDef d) {
        String who = "component " + d.id;
        SubsystemComponent.SubsystemType behavior = parseEnum(SubsystemComponent.SubsystemType.class, d.behavior,
                who + " behavior");
        EnumSet<MountFootprint.MountSize> sizes = EnumSet.noneOf(MountFootprint.MountSize.class);
        for (String s : d.sizes) {
            MountFootprint.MountSize size = parseEnum(MountFootprint.MountSize.class, s, who + " size");
            if (!size.exists(behavior.mountKind)) {
                throw new IllegalArgumentException(who + ": there is no " + size + " " + behavior.mountKind + " mount");
            }
            sizes.add(size);
        }
        if (sizes.isEmpty()) sizes = allSizes(behavior.mountKind);
        Map<MountFootprint.MountSize, Double> output = new HashMap<>();
        for (Map.Entry<String, Double> e : d.powerOutput.entrySet()) {
            output.put(parseEnum(MountFootprint.MountSize.class, e.getKey(), who + " power_output size"), e.getValue());
        }
        Fluid fluid = null;
        if (behavior == SubsystemComponent.SubsystemType.TANK) {
            fluid = d.fluid == null ? Fluid.PROPELLANT : parseEnum(Fluid.class, d.fluid, who + " fluid");
        }
        Map<MountFootprint.MountSize, Double> capacity = new HashMap<>();
        for (Map.Entry<String, Double> e : d.capacity.entrySet()) {
            capacity.put(parseEnum(MountFootprint.MountSize.class, e.getKey(), who + " capacity size"), e.getValue());
        }
        char symbol = symbol(d.symbol, who);
        if (!Character.isLetter(symbol)) throw new IllegalArgumentException(who + ": symbol must be a letter");
        ComponentType type = new ComponentType(d.id, d.name != null ? d.name : d.id, symbol, behavior, sizes,
                parseColor(d.color, 0x808080), output, d.throughput, fluid, capacity);
        if (d.shortChance != null) type.shortChance = chance(d.shortChance, who + " short_chance");
        if (d.burstThreshold != null) type.burstThreshold = chance(d.burstThreshold, who + " burst_threshold");
        if (d.burstChance != null) type.burstChance = chance(d.burstChance, who + " burst_chance");
        return type;
    }

    private static float chance(float value, String what) {
        if (!(value >= 0f && value <= 1f)) throw new IllegalArgumentException(what + " must be between 0 and 1");
        return value;
    }

    private static char symbol(String s, String who) {
        if (s == null || s.length() != 1) throw new IllegalArgumentException(who + ": symbol must be one character");
        return s.charAt(0);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String value, String what) {
        try {
            return Enum.valueOf(cls, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + " '" + value + "' is not one of " + java.util.Arrays.toString(cls.getEnumConstants()));
        }
    }

    /** Parses "#rrggbb" (or "rrggbb"), or returns {@code fallback} when absent. */
    public static int parseColor(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        try {
            return Integer.parseInt(hex, 16) & 0xffffff;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Color '" + s + "' is not #rrggbb");
        }
    }

    public Collection<TileType> tiles() {
        return Collections.unmodifiableCollection(tiles.values());
    }

    public Collection<ComponentType> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    public TileType tile(String id) {
        return tiles.get(id);
    }

    public ComponentType component(String id) {
        return components.get(id);
    }

    /** The tile type drawn with {@code symbol}, or null if it is a component letter or unknown. */
    public TileType tileFor(char symbol) {
        return tileBySymbol.get(symbol);
    }

    /** The component drawn with {@code symbol}, or null. */
    public ComponentType componentFor(char symbol) {
        return componentBySymbol.get(symbol);
    }

    /** The component a bare behavior name stands for: its built-in one, else the first with that behavior. */
    public ComponentType standardComponent(SubsystemComponent.SubsystemType behavior) {
        ComponentType t = standard.get(behavior);
        if (t == null) throw new IllegalArgumentException("No component has behavior " + behavior);
        return t;
    }
}
