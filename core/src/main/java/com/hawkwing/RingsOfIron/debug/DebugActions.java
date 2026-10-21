package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.ShipConsoles;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.physicsexterior.PhysicsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Things a developer does to the world by hand: kill, heal or wound a crew member, set a tile
 * alight, breach the hull, repair a ship, spawn crew and ships. The developer console's commands
 * call these, and a demo's debug keys can call the same ones. Each goes through the game's own
 * systems where there is one (a wound through {@link CrewService#applyDamage}, so a hit on someone
 * critical halves their time as a real hit does), and returns a line saying what it did. A target
 * it can't act on is refused with an {@link IllegalArgumentException} saying why.
 */
public final class DebugActions {
    /** How far ahead of the player {@link #spawnShip} puts a new ship, meters. */
    public static final float SPAWN_DISTANCE = 300f;

    private final GameWorld world;

    public DebugActions(GameWorld world) {
        this.world = world;
    }

    // --- Crew ------------------------------------------------------------------------

    /** Kills the crew member, as dying of their wounds does. */
    public String kill(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        if (!c.isAlive()) return c.name + " is already dead";
        if (c.woundCause == null) c.woundCause = DamageCause.OTHER;
        world.crew.dieOfWounds(crew);
        return c.name + " is dead";
    }

    /** Heals the crew member fully: health, wound, bleeding and suit air. The dead stay dead ({@link #revive}). */
    public String heal(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        if (!c.isAlive()) return c.name + " is dead; revive() brings them back";
        clearWound(c);
        c.health = c.def.maxHealth;
        c.suitAir = c.def.suitAir;
        world.crew.updateState(crew);
        return c.name + " is healed (" + c.state.name().toLowerCase(Locale.ROOT) + ")";
    }

    /** Brings a dead crew member back at full health, standing where they fell. */
    public String revive(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        if (c.isAlive()) return heal(crew);
        clearWound(c);
        c.health = c.def.maxHealth;
        c.state = CrewState.CONSCIOUS;
        c.activity = "idle";
        return c.name + " is alive again";
    }

    private static void clearWound(CrewMemberComponent c) {
        c.wound = Wound.NONE;
        c.injury = 0f;
        c.woundCause = null;
        c.bleedOut = 0f;
        c.stabilised = false;
        c.care = 0f;
        c.healing = 0f;
    }

    /**
     * A hit of {@code points} on the crew member, as a round or fragment would give: through the
     * game's wounding, so it grazes, wounds, worsens a wound, or kills from
     * {@code CrewDef.lethalWound} (200) up. {@code cause} is a {@link DamageCause} name;
     * FIRE, ASPHYXIATION, SMALL_ARMS and OTHER take health instead of wounding.
     */
    public String wound(Entity crew, float points, String cause) {
        CrewMemberComponent c = crewOf(crew);
        Wound was = c.wound;
        CrewState wasState = c.state;
        float bleed = c.bleedOut;
        world.crew.applyDamage(crew, points, cause(cause));
        StringBuilder sb = new StringBuilder(c.name).append(": ");
        if (!c.isAlive()) return sb.append("killed").toString();
        if (CrewService.wounds(cause(cause))) {
            sb.append(was == c.wound ? "still " : "").append(c.wound.label);
            if (c.wound == Wound.CRITICAL) sb.append(", ").append(Math.round(c.bleedOut)).append(" s to bleed out")
                    .append(was == Wound.CRITICAL && c.bleedOut < bleed ? " (was " + Math.round(bleed) + ")" : "");
        } else {
            sb.append(Math.round(c.health)).append(" hp");
        }
        if (c.state != wasState) sb.append(", ").append(c.state.name().toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    /** Knocks the crew member out: their health just under the line where they go down. */
    public String knockOut(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        if (!c.isAlive()) return c.name + " is dead";
        c.health = Math.min(c.health, c.def.maxHealth * c.def.unconsciousThreshold * 0.9f);
        world.crew.updateState(crew);
        return c.name + " is " + c.state.name().toLowerCase(Locale.ROOT);
    }

    /** Puts the crew member's space suit on, or takes it off. */
    public String suit(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        c.setSuited(!c.suited);
        return c.name + (c.suited ? " suits up" : " takes their suit off");
    }

    /** Puts the crew member on another tile of their ship, or of another ship. */
    public String teleport(Entity crew, Entity ship, int x, int y) {
        CrewMemberComponent c = crewOf(crew);
        ShipGridComponent g = gridOf(ship);
        Tile t = g.getTile(x, y);
        if (t == null || !t.isOpen()) throw new IllegalArgumentException("Tile " + x + "," + y + " isn't open deck");
        world.crew.releaseTask(crew);
        GridPositionComponent p = crew.getComponent(GridPositionComponent.class);
        p.stop();
        p.setShip(ship);
        p.setTile(x, y);
        return c.name + " is at " + x + "," + y + " aboard " + DebugNames.name(ship);
    }

    /**
     * A new crew member aboard {@code ship}, on the tile given or on a free deck tile when
     * {@code x} is negative, working for the ship's own faction.
     */
    public Entity spawnCrew(Entity ship, String role, String name, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        CrewRole r = role == null ? CrewRole.CREWMAN : CrewRole.valueOf(role.toUpperCase(Locale.ROOT));
        if (x < 0) {
            int[] free = freeTile(ship, g);
            if (free == null) throw new IllegalArgumentException(DebugNames.name(ship) + " has no free deck");
            x = free[0];
            y = free[1];
        } else if (g.getTile(x, y) == null || !g.getTile(x, y).isOpen()) {
            throw new IllegalArgumentException("Tile " + x + "," + y + " isn't open deck");
        }
        int faction = CrewFactory.ensureBoard(ship).faction;
        if (name == null) name = r.name().charAt(0) + r.name().substring(1).toLowerCase(Locale.ROOT) + " " + (crewAboard(ship).size() + 1);
        return CrewFactory.spawn(world.crew, ship, name, r, faction, x, y);
    }

    /** Everyone aboard {@code ship}, alive or not. */
    public List<Entity> crewAboard(Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) out.add(e);
        }
        return out;
    }

    /** An open deck tile nobody stands on, or null. */
    private int[] freeTile(Entity ship, ShipGridComponent g) {
        List<int[]> open = new ArrayList<>();
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t.isOpen() && t.mount == null) open.add(new int[]{x, y});
            }
        }
        List<Entity> aboard = crewAboard(ship);
        for (int i = 0; i < open.size(); i++) {
            int[] t = open.get((i * 7) % open.size());
            boolean taken = false;
            for (Entity e : aboard) {
                GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                if (p.tileX() == t[0] && p.tileY() == t[1]) taken = true;
            }
            if (!taken) return t;
        }
        return open.isEmpty() ? null : open.get(0);
    }

    // --- Decks -----------------------------------------------------------------------

    /**
     * Sets the tile alight. Bare deck has nothing to burn, so it is given a carpet's worth of
     * fuel first; a fire in thin air still dies on the next tick, as any fire does.
     */
    public String ignite(Entity ship, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        Tile t = tileOf(g, x, y);
        if (!t.holdsAir()) throw new IllegalArgumentException("Tile " + x + "," + y + " holds no air to burn in");
        if (t.fuel <= 0f) t.fuel = FloorCovering.CARPET.fuelPerArea * g.tileSize * g.tileSize;
        if (!t.ignite()) return "Tile " + x + "," + y + " is already burning";
        return "Fire at " + x + "," + y + " aboard " + DebugNames.name(ship);
    }

    /** Sets a random tile of the ship alight, one that holds air. */
    public String igniteSomewhere(Entity ship) {
        ShipGridComponent g = gridOf(ship);
        List<int[]> air = new ArrayList<>();
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                if (g.getTile(x, y).holdsAir() && !g.getTile(x, y).isBurning()) air.add(new int[]{x, y});
            }
        }
        if (air.isEmpty()) throw new IllegalArgumentException(DebugNames.name(ship) + " has nowhere left to burn");
        int[] t = air.get(MathUtils.random(air.size() - 1));
        return ignite(ship, t[0], t[1]);
    }

    /** Puts out the tile's fire, or with {@code x} negative every fire aboard. */
    public String extinguish(Entity ship, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        int out = 0;
        for (int ty = 0; ty < g.height; ty++) {
            for (int tx = 0; tx < g.width; tx++) {
                if (x >= 0 && (tx != x || ty != y)) continue;
                Tile t = g.getTile(tx, ty);
                if (t.isBurning()) out++;
                t.extinguish();
            }
        }
        return out == 0 ? "Nothing burning" : out + (out == 1 ? " fire" : " fires") + " out";
    }

    /**
     * Takes {@code share} (0 to 1) of full condition off the tile and the system on it, as a hit
     * would; at nothing a hull tile is breached and a wall falls.
     */
    public String damageTile(Entity ship, int x, int y, float share) {
        ShipGridComponent g = gridOf(ship);
        Tile t = tileOf(g, x, y);
        t.setIntegrity(t.getIntegrity() - share);
        SubsystemComponent s = systemOn(t);
        if (s != null) s.health = Math.max(0f, s.health - share);
        g.gridDirty = true;
        return "Tile " + x + "," + y + " at " + Math.round(t.getIntegrity() * 100f) + "%"
                + (s != null ? ", " + DebugNames.lower(s.type.name()) + " at " + Math.round(s.health * 100f) + "%" : "")
                + (t.isHullBreach() ? ", breached" : "");
    }

    /** Blows a hole in a hull tile: its condition to nothing, open to space. */
    public String breach(Entity ship, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        Tile t = tileOf(g, x, y);
        if (t.wallType != Tile.WallType.HULL) throw new IllegalArgumentException("Tile " + x + "," + y + " isn't hull; pick a hull tile");
        t.setIntegrity(0f);
        t.bulkheadSealed = false;
        t.clearPatch();
        g.gridDirty = true;
        return "Hull breached at " + x + "," + y;
    }

    /** Mends the tile and the system on it: full condition, no patch, no fire, no short. */
    public String repairTile(Entity ship, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        Tile t = tileOf(g, x, y);
        mend(t);
        SubsystemComponent s = systemOn(t);
        if (s != null) mend(s);
        g.gridDirty = true;
        return "Tile " + x + "," + y + " repaired";
    }

    /**
     * Mends the whole ship: every tile, wall and system to full condition, fires out, shorts
     * cleared, and every room filled with fresh air at room temperature.
     */
    public String repairShip(Entity ship) {
        ShipGridComponent g = gridOf(ship);
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) mend(g.getTile(x, y));
        }
        for (EdgeWall[][] edges : new EdgeWall[][][]{g.edgeH, g.edgeV}) {
            for (EdgeWall[] row : edges) {
                for (EdgeWall w : row) {
                    if (w == null) continue;
                    w.integrity = 1f;
                    w.patchedAt = 0f;
                }
            }
        }
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e.getComponent(SubsystemComponent.class);
                if (s != null) mend(s);
            }
        }
        g.incidents.clear();
        g.gridDirty = true;
        freshAir(ship);
        return DebugNames.name(ship) + " repaired";
    }

    /** Every room of the ship filled with fresh air at room temperature, and the smoke cleared. */
    public String freshAir(Entity ship) {
        ShipGridComponent g = gridOf(ship);
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) g.getTile(x, y).temperature = Compartment.AMBIENT_TEMPERATURE;
        }
        for (Compartment c : g.compartments) {
            c.temperature = Compartment.AMBIENT_TEMPERATURE;
            c.fillStandardAir();
            c.smoke = 0;
        }
        return "Fresh air throughout " + DebugNames.name(ship);
    }

    private static void mend(Tile t) {
        t.setIntegrity(1f);
        t.clearPatch();
        t.extinguish();
        t.shorted = false;
        t.shortedFor = 0f;
    }

    private static void mend(SubsystemComponent s) {
        s.health = 1f;
        s.stunned = 0f;
    }

    // --- Ships -----------------------------------------------------------------------

    /** Kills everyone aboard the ship. */
    public String killAll(Entity ship) {
        int n = 0;
        for (Entity e : crewAboard(ship)) {
            if (!crewOf(e).isAlive()) continue;
            kill(e);
            n++;
        }
        return n + " dead aboard " + DebugNames.name(ship);
    }

    /** Heals everyone aboard the ship. */
    public String healAll(Entity ship) {
        int n = 0;
        for (Entity e : crewAboard(ship)) {
            if (!crewOf(e).isAlive()) continue;
            heal(e);
            n++;
        }
        return n + " healed aboard " + DebugNames.name(ship);
    }

    /**
     * A new ship from {@code ships.toml}, {@link #SPAWN_DISTANCE} ahead of {@code near} (or at
     * the bubble's middle), with its consoles but no crew: {@link #spawnCrew} adds them.
     */
    public Entity spawnShip(String shipId, Entity near) {
        if (world.getContent() == null || !world.getContent().defs.has("ship", shipId)) {
            throw new IllegalArgumentException("No ship called " + shipId + "; ships() lists them");
        }
        float x = 0f, y = 0f, angle = 0f;
        Body body = near == null ? null : world.docking.bodyOf(near);
        if (body != null) {
            Vector2 at = PhysicsManager.getPosition(body, new Vector2());
            float heading = body.getAngle() + MathUtils.HALF_PI;
            x = at.x + MathUtils.cos(heading) * SPAWN_DISTANCE;
            y = at.y + MathUtils.sin(heading) * SPAWN_DISTANCE;
            angle = body.getAngle() * MathUtils.radiansToDegrees;
        }
        Entity ship = world.getContent().factory.spawnShip(shipId, x, y, angle);
        ShipConsoles.install(ship);
        return ship;
    }

    /** Takes a round, missile or decoy out of the world. */
    public String remove(Entity e) {
        world.engine.getEcsEngine().removeEntity(e);
        return DebugNames.name(e) + " removed";
    }

    // --- Lookups ---------------------------------------------------------------------

    public static CrewMemberComponent crewOf(Entity e) {
        CrewMemberComponent c = e == null ? null : e.getComponent(CrewMemberComponent.class);
        if (c == null) throw new IllegalArgumentException(DebugNames.name(e) + " isn't a crew member");
        return c;
    }

    public static ShipGridComponent gridOf(Entity ship) {
        ShipGridComponent g = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        if (g == null) throw new IllegalArgumentException(DebugNames.name(ship) + " has no decks");
        return g;
    }

    private static Tile tileOf(ShipGridComponent g, int x, int y) {
        Tile t = g.getTile(x, y);
        if (t == null || t.isVoid()) throw new IllegalArgumentException("No tile at " + x + "," + y);
        return t;
    }

    private static SubsystemComponent systemOn(Tile t) {
        return t.mount == null || t.mount.installed == null ? null : t.mount.installed.getComponent(SubsystemComponent.class);
    }

    static DamageCause cause(String name) {
        if (name == null) return DamageCause.PENETRATOR;
        try {
            return DamageCause.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No damage cause " + name + "; one of " + java.util.Arrays.toString(DamageCause.values()));
        }
    }
}
