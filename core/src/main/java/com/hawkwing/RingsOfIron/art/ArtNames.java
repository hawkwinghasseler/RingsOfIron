package com.hawkwing.RingsOfIron.art;

import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The region names of the art for game state, following the naming in documentation/art/Design.md:
 * folder, data id, then size, state, {@code unpowered} and orientation. Pure functions, so they
 * are tested without a GL context.
 */
public final class ArtNames {
    /** Neighbor bits for wall joins, in the {@code nesw} order the file names use. */
    public static final int N = 1, E = 2, S = 4, W = 8;

    private ArtNames() {
    }

    /** {@code n}, {@code es}, {@code nesw}... for a join mask, or {@code 0} for none. */
    public static String sides(int mask) {
        if (mask == 0) return "0";
        StringBuilder sb = new StringBuilder(4);
        if ((mask & N) != 0) sb.append('n');
        if ((mask & E) != 0) sb.append('e');
        if ((mask & S) != 0) sb.append('s');
        if ((mask & W) != 0) sb.append('w');
        return sb.toString();
    }

    public static String weight(WeightClass w) {
        return (w == null ? WeightClass.MEDIUM : w).name().toLowerCase(Locale.ROOT);
    }

    /** The floor art of an open tile: steel deck, or a conduit (dark without power). */
    public static String floor(Tile t) {
        if (t.isPowerConduit()) return t.isPowered ? "tiles/conduit" : "tiles/conduit_unpowered";
        return "tiles/floor";
    }

    /** One arm of a conduit, drawn toward each neighboring conduit. */
    public static String conduitArm(boolean powered) {
        return powered ? "tiles/conduit_arm" : "tiles/conduit_arm_unpowered";
    }

    /** A conduit run along one tile edge, laid out like an edge wall segment. */
    public static String edgeConduit(boolean powered, boolean horizontal) {
        return "edges/edge_conduit_" + (powered ? "" : "unpowered_") + (horizontal ? "h" : "v");
    }

    /** Where edge conduits meet or end at a vertex, by the sides they leave toward; null for none. */
    public static String edgeConduitJoint(boolean powered, int mask) {
        return mask == 0 ? null : "edges/edge_conduit_joint_" + (powered ? "" : "unpowered_") + sides(mask);
    }

    /** The junction box where a tile conduit's arm meets an edge run, on the middle of the edge. */
    public static String edgeConduitTap(boolean powered) {
        return powered ? "edges/edge_conduit_tap" : "edges/edge_conduit_tap_unpowered";
    }

    /** The valve plate over a pipe across a tile; arms ({@link #PIPE_ARM}) reach toward its pipe neighbors. */
    public static final String PIPE_HUB = "tiles/pipe_hub";
    /** One arm of a pipe across a tile, drawn toward the top edge and turned toward each neighbor. */
    public static final String PIPE_ARM = "tiles/pipe_arm";
    /** The tee where a tile's pipe arm meets a pipe along the edge, on the middle of the edge. */
    public static final String EDGE_PIPE_TAP = "edges/edge_pipe_tap";

    /** A pipe along one tile edge, laid out like an edge wall segment. */
    public static String edgePipe(boolean horizontal) {
        return "edges/edge_pipe_" + (horizontal ? "h" : "v");
    }

    /** Where edge pipes meet or end at a vertex, by the sides they leave toward; null for none. */
    public static String edgePipeJoint(int mask) {
        return mask == 0 ? null : "edges/edge_pipe_joint_" + sides(mask);
    }

    /** A covering laid over the deck, or null for bare steel. */
    public static String covering(FloorCovering c) {
        return c == null || c == FloorCovering.NONE ? null : "tiles/covering_" + c.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The file prefix of a wall material's art: hull steel is the plain {@code hull} art, any
     * other material its own id ({@code composite_armor}).
     */
    public static String materialPrefix(String material) {
        return material == null || material.equals(MaterialTable.DEFAULT_MATERIAL) ? "hull" : material;
    }

    /**
     * The art family a wall tile goes by: {@code internal_wall}, or {@code <material>_<weight>}
     * ({@code hull_heavy}, {@code composite_armor_light}). Null for a tile that is not a wall.
     * Walls join neighbors with the same id.
     */
    public static String wallId(Tile t) {
        if (t == null || !t.isWall()) return null;
        if (t.wallType == Tile.WallType.INTERNAL) return "internal_wall";
        return materialPrefix(t.material) + "_" + weight(t.weightClass);
    }

    /** A full-tile wall joined to the neighbors in {@code mask}. */
    public static String wall(String wallId, int mask) {
        return "walls/" + wallId + "_" + sides(mask);
    }

    /** A wall tile's art, best first: its material's, then hull steel's of the same weight. */
    public static String[] wall(Tile t, int mask) {
        String id = wallId(t);
        if (t.wallType == Tile.WallType.INTERNAL) return new String[]{wall(id, mask)};
        return distinct(wall(id, mask), wall("hull_" + weight(t.weightClass), mask));
    }

    /** A wedge hull piece's art, best first. See {@link #wedge(String, WeightClass, int, int)}. */
    public static String[] wedge(HullPiece p) {
        return wedge(p.material, p.weightClass, p.baseWidth, p.baseHeight);
    }

    /**
     * The art for a wedge of that unrotated size, best first: its material's wedge of exactly
     * that size, then the tall one ({@code 1x2} for a {@code 2x1}), then hull steel's. Tall art
     * stands in for a wide wedge mirrored; see {@link #wedgeMirrored}.
     */
    public static String[] wedge(String material, WeightClass w, int baseWidth, int baseHeight) {
        String exact = "_wedge_" + baseWidth + "x" + baseHeight;
        String tall = "_wedge_" + Math.min(baseWidth, baseHeight) + "x" + Math.max(baseWidth, baseHeight);
        String own = "walls/" + materialPrefix(material) + "_" + weight(w), steel = "walls/hull_" + weight(w);
        return distinct(own + exact, own + tall, steel + exact, steel + tall);
    }

    /**
     * True when {@code region}, the wedge art found for a wedge of that size, is tall art standing
     * in for a wide wedge. Its triangle is the tall one reflected across its diagonal: the tall art
     * mirrored left to right and turned a quarter turn clockwise.
     */
    public static boolean wedgeMirrored(String region, int baseWidth, int baseHeight) {
        return baseWidth > baseHeight && !region.endsWith("_wedge_" + baseWidth + "x" + baseHeight);
    }

    /** A thin hull steel wall along one tile edge. */
    public static String edgeWall(WeightClass w, boolean horizontal) {
        return "edges/edge_wall_" + weight(w) + (horizontal ? "_h" : "_v");
    }

    /** A thin wall along one tile edge, best first: its material's art, then hull steel's. */
    public static String[] edgeWall(String material, WeightClass w, boolean horizontal) {
        return distinct("edges/edge_wall_" + edgePrefix(material) + weight(w) + (horizontal ? "_h" : "_v"),
                edgeWall(w, horizontal));
    }

    /** The joint at a grid vertex where the edges in {@code mask} carry a hull steel wall; null for none. */
    public static String edgeJoint(WeightClass w, int mask) {
        return mask == 0 ? null : "edges/edge_wall_" + weight(w) + "_joint_" + sides(mask);
    }

    /** {@link #edgeJoint(WeightClass, int)} for walls of {@code material}, falling back to hull steel. */
    public static String[] edgeJoint(String material, WeightClass w, int mask) {
        if (mask == 0) return new String[0];
        return distinct("edges/edge_wall_" + edgePrefix(material) + weight(w) + "_joint_" + sides(mask),
                edgeJoint(w, mask));
    }

    private static String edgePrefix(String material) {
        String p = materialPrefix(material);
        return p.equals("hull") ? "" : p + "_";
    }

    private static String[] distinct(String... names) {
        Set<String> out = new LinkedHashSet<>();
        for (String n : names) out.add(n);
        return out.toArray(new String[0]);
    }

    public static String door(boolean sealed, boolean powered, boolean horizontal) {
        return "edges/bulkhead_door_" + (sealed ? "sealed" : "open") + (powered ? "" : "_unpowered")
                + (horizontal ? "_h" : "_v");
    }

    /** The built-in component a behavior falls back to: {@code REACTOR} is {@code reactor}. */
    public static String builtInComponent(SubsystemComponent.SubsystemType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Where to look for a system's art, best first: its own component's art, then its behavior's
     * built-in one; each in the power state asked for, then the plain file (passive systems such
     * as cargo have no {@code _unpowered} variant). {@code state} is null or a state such as
     * {@code open}.
     */
    public static String[] component(String componentId, SubsystemComponent.SubsystemType type,
                                     MountFootprint.MountSize size, String state, boolean powered) {
        String builtIn = builtInComponent(type);
        String suffix = "_" + size.name().toLowerCase(Locale.ROOT) + (state == null ? "" : "_" + state);
        String own = componentId == null || componentId.equals(builtIn) ? null : "components/" + componentId + suffix;
        String base = "components/" + builtIn + suffix;
        if (own == null) return powered ? new String[]{base} : new String[]{base + "_unpowered", base};
        return powered ? new String[]{own, base}
                : new String[]{own + "_unpowered", own, base + "_unpowered", base};
    }

    /** How damaged a system's art shows it: intact, damaged (below half health) or destroyed. */
    public enum Condition { INTACT, DAMAGED, DESTROYED }

    /** Health below which a system shows its damaged art. */
    public static final float DAMAGED_BELOW = 0.5f;

    public static Condition condition(float health) {
        if (health <= 0f) return Condition.DESTROYED;
        return health < DAMAGED_BELOW ? Condition.DAMAGED : Condition.INTACT;
    }

    /**
     * {@link #component(String, SubsystemComponent.SubsystemType, MountFootprint.MountSize, String, boolean)}
     * in a {@link Condition}: {@code _damaged} art (dimmed, {@code _damaged_unpowered} dark) or
     * {@code _destroyed} art (always dark) first, then the intact art as a fallback.
     */
    public static String[] component(String componentId, SubsystemComponent.SubsystemType type,
                                     MountFootprint.MountSize size, String state, boolean powered, Condition condition) {
        String[] intact = component(componentId, type, size, state, powered && condition != Condition.DESTROYED);
        if (condition == null || condition == Condition.INTACT) return intact;
        String builtIn = builtInComponent(type);
        String suffix = "_" + size.name().toLowerCase(Locale.ROOT) + (state == null ? "" : "_" + state);
        java.util.List<String> bases = new java.util.ArrayList<>(2);
        if (componentId != null && !componentId.equals(builtIn)) bases.add("components/" + componentId + suffix);
        bases.add("components/" + builtIn + suffix);
        Set<String> out = new LinkedHashSet<>();
        for (String b : bases) {
            if (condition == Condition.DESTROYED) {
                out.add(b + "_destroyed");
            } else {
                if (!powered) out.add(b + "_damaged_unpowered");
                out.add(b + "_damaged");
            }
        }
        for (String n : intact) out.add(n);
        return out.toArray(new String[0]);
    }

    /** A docking port's latch collar, half a tile outside its outer edge. */
    public static String collar(MountFootprint.MountSize size, boolean powered) {
        return "components/docking_collar_" + size.name().toLowerCase(Locale.ROOT) + (powered ? "" : "_unpowered");
    }

    public static String fire(Tile.FireSize size) {
        return size == Tile.FireSize.NONE ? null : "effects/fire_" + size.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Damage showing on a tile at {@code integrity}: light below half (where leaks start), heavy
     * below a quarter; null above.
     */
    public static String damage(float integrity) {
        if (integrity < Tile.LEAK_CAP_CONDITION) return "effects/damage_heavy";
        if (integrity < Tile.LEAK_START) return "effects/damage_light";
        return null;
    }

    /** A bay tile's cargo stack at {@code fill} (0-1) of its height: a third, two thirds or full. */
    public static String cargoStack(float fill) {
        if (fill <= 0f) return null;
        int level = fill > 2f / 3f ? 3 : fill > 1f / 3f ? 2 : 1;
        return "interactive/cargo_stack_" + level;
    }

    /** The exterior of a chassis. */
    public static String hull(String chassisId) {
        return chassisId == null ? null : "hulls/" + chassisId;
    }

    /** The engine drawn in the engine mounts of a hull with no engine systems. */
    public static final String ENGINE_SIZE = "s";

    /** The exterior engine and exhaust size for an engine system's slot. */
    public static String engineSize(MountFootprint.MountSize size) {
        return size.name().toLowerCase(Locale.ROOT);
    }

    /** An engine bolted into a hull's engine mount, its throat hot while it burns. */
    public static String[] engine(String size, boolean burning) {
        String base = "engines/engine_" + size;
        return burning ? new String[]{base} : new String[]{base + "_unpowered", base};
    }

    /** The plume behind an engine of that size, pivoting on the same mount socket. */
    public static String exhaust(String size) {
        return "engines/exhaust_" + size;
    }

    /** A weapon's turret, by its id in weapons.toml. */
    public static String[] turret(String weaponId, boolean powered) {
        String base = "turrets/" + weaponId;
        return powered ? new String[]{base} : new String[]{base + "_unpowered", base};
    }

    /**
     * A round or missile's art, best first: the file its definition names ({@code art}), one named
     * like its id, then for a missile the generic {@code missile}.
     */
    public static String[] projectile(String art, String id, boolean missile) {
        java.util.List<String> out = new java.util.ArrayList<>(3);
        if (art != null) out.add("projectiles/" + art);
        if (id != null) out.add("projectiles/" + id);
        if (missile) out.add("projectiles/missile");
        return out.toArray(new String[0]);
    }

    public static final String DECOY = "projectiles/decoy_flare";
    /** The laser strip, stretched along a beam. */
    public static final String BEAM = "effects/beam";
    /** The gangway across two latched collars, stretched along the crossing. */
    public static final String WALKWAY = "tiles/dock_walkway";

    /** Frames of {@link #explosion}. */
    public static final int EXPLOSION_FRAMES = 4;

    /** The explosion frame at {@code progress} (0 at the flash, 1 when it has faded). */
    public static String explosion(float progress) {
        int frame = 1 + (int) (Math.max(0f, Math.min(0.999f, progress)) * EXPLOSION_FRAMES);
        return "effects/explosion_" + frame;
    }

    /** Whose look a crew member has. */
    public enum Crew { SHIP, STATION, PLAYER }

    /**
     * How a crew member lies or stands: on their feet, down (unconscious, or with a wound that puts
     * them down) or dead. A wound is drawn over the figure ({@link #crewWound}).
     */
    public enum CrewPose {
        STANDING(""), DOWN("_down"), DEAD("_dead");

        private final String suffix;

        CrewPose(String suffix) {
            this.suffix = suffix;
        }

        /** True for the poses drawn lying on the deck, which carry nothing. */
        public boolean lying() {
            return this != STANDING;
        }

        public static CrewPose of(CrewMemberComponent c) {
            return c.state == CrewState.DEAD ? DEAD : c.state == CrewState.UNCONSCIOUS ? DOWN : STANDING;
        }
    }

    /** A wound's art for a standing figure, whose presence says the pack has wounds. */
    public static final String WOUND_ART = "crew/wound_light";

    /**
     * The wound drawn over a crew member's figure in {@code pose}, by how bad it is (a critical one
     * dressed once stabilised), or null when they are unhurt or dead.
     */
    public static String crewWound(CrewMemberComponent c, CrewPose pose) {
        if (pose == CrewPose.DEAD) return null;
        String level;
        switch (c.wound) {
            case LIGHT: level = "light"; break;
            case SERIOUS: level = "serious"; break;
            case CRITICAL: level = c.stabilised ? "stabilised" : "critical"; break;
            default: return null;
        }
        return "crew/wound_" + level + (pose.lying() ? "_down" : "");
    }

    /** A crew member, holding a crate while they carry a load. */
    public static String crew(Crew who, boolean carrying) {
        return crew(who, CrewPose.STANDING, carrying);
    }

    /** A crew member in {@code pose}, holding a crate while they carry a load on their feet. */
    public static String crew(Crew who, CrewPose pose, boolean carrying) {
        return "crew/crew_" + who.name().toLowerCase(Locale.ROOT) + pose.suffix + (carrying && !pose.lying() ? "_carrying" : "");
    }

    /** A light on a wall, dark without power. */
    public static String wallLight(boolean powered) {
        return powered ? "decor/wall_light" : "decor/wall_light_unpowered";
    }

    /** The emissive layer of a region, for a glow pass. */
    public static String glow(String region) {
        return "glow/" + region;
    }
}
