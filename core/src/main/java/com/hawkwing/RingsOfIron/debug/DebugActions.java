package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.ShipConsoles;
import com.hawkwing.RingsOfIron.game.WorldDebug;
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
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.physicsexterior.PhysicsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the developer console does to the world, as one line each saying what happened: kill, heal
 * or wound a crew member, fire a weapon at a tile, set off a warhead, ram, set a tile alight,
 * breach or short it, repair a ship, spawn crew and ships. Where the interior demo's
 * {@link WorldDebug} has the tool (wounds through the crew's damage sink, weapons, blasts, rams,
 * fires, breaches, shorts), this calls it, so the demo's keys and the console's commands do the
 * same thing; what is here is what the console adds (killing and reviving, repairs, ships). A
 * target it can't act on is refused with an {@link IllegalArgumentException} saying why.
 */
public final class DebugActions {
    /** How far ahead of the player {@link #spawnShip} puts a new ship, meters. */
    public static final float SPAWN_DISTANCE = 300f;

    private final GameWorld world;
    /** The interior demo's tools, which most of these go through. */
    public final WorldDebug tools;

    public DebugActions(GameWorld world, WorldDebug tools) {
        this.world = world;
        this.tools = tools;
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
        tools.heal(crew);
        c.bleedOut = 0f;
        c.suitAir = c.def.suitAir;
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
        tools.hurt(crew, points, cause(cause));
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

    /**
     * Puts a suit on the crew member where they stand, or takes theirs off: a suit from the nearest
     * locker aboard with one (a pressure suit first), back into the nearest with room; with none, a
     * fresh pressure suit out of nowhere, or the suit gone.
     */
    public String suit(Entity crew) {
        CrewMemberComponent c = crewOf(crew);
        GridPositionComponent p = world.crew.position(crew);
        ShipGridComponent grid = p == null ? null : world.crew.grid(p.ship);
        if (c.suited) {
            Suit off = c.takeOff();
            Furnishing to = nearestLocker(grid, p, f -> SuitLockers.hasRoom(f, off.kind));
            if (to != null) SuitLockers.stow(to, off);
            return c.name + " takes their suit off" + (to != null ? " and hangs it in the " + to.type.name.toLowerCase(Locale.ROOT) + " at " + to.tileX + "," + to.tileY : "");
        }
        Furnishing from = nearestLocker(grid, p, f -> SuitLockers.hasSuit(f, Suit.Kind.IVA));
        if (from == null) from = nearestLocker(grid, p, f -> SuitLockers.hasSuit(f, Suit.Kind.EVA));
        c.wear(from != null ? SuitLockers.take(from, from.type.holds) : Suit.iva());
        return c.name + " suits up" + (from != null ? " from the " + from.type.name.toLowerCase(Locale.ROOT) + " at " + from.tileX + "," + from.tileY : "");
    }

    /** The locker aboard nearest {@code p} that {@code test} accepts, or null. */
    private static Furnishing nearestLocker(ShipGridComponent grid, GridPositionComponent p, java.util.function.Predicate<Furnishing> test) {
        Furnishing best = null;
        float bestD = Float.MAX_VALUE;
        for (Furnishing f : SuitLockers.all(grid)) {
            float d = Math.abs(f.x() - p.x) + Math.abs(f.y() - p.y);
            if (test.test(f) && d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
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
     * {@code x} is negative. They work for the ship's own crew, or for {@code faction} when it
     * is not negative: a boarder.
     */
    public Entity spawnCrew(Entity ship, String role, String name, int faction, int x, int y) {
        gridOf(ship);
        CrewRole r;
        try {
            r = role == null ? CrewRole.CREWMAN : CrewRole.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No role " + role + "; one of " + java.util.Arrays.toString(CrewRole.values()));
        }
        if (x < 0) {
            int[] free = tools.freeTile(ship);
            if (free == null) throw new IllegalArgumentException(DebugNames.name(ship) + " has no free deck");
            x = free[0];
            y = free[1];
        }
        Entity e = faction < 0 ? tools.spawnCrew(ship, r, x, y) : tools.spawnCrew(ship, r, faction, x, y);
        if (e == null) throw new IllegalArgumentException("Tile " + x + "," + y + " isn't open deck");
        if (name != null) e.getComponent(CrewMemberComponent.class).name = name;
        return e;
    }

    /**
     * A faction aboard {@code ship} hostile to its crew, for a boarder: the first faction whose
     * crew the ship's own count as enemies, else one past every faction in use.
     */
    public int boarderFaction(Entity ship) {
        int own = CrewFactory.ensureBoard(ship).faction;
        int highest = own;
        for (Entity e : world.crew.crewEntities()) highest = Math.max(highest, world.crew.crew(e).faction);
        for (int f = 0; f <= highest; f++) {
            if (f != own && world.crew.hostility.hostile(own, f)) return f;
        }
        return highest + 1;
    }

    /** Takes the crew member out of the world altogether. */
    public String removeCrew(Entity crew) {
        String name = crewOf(crew).name;
        tools.removeCrew(crew);
        return name + " removed";
    }

    /** Everyone aboard {@code ship}, alive or not. */
    public List<Entity> crewAboard(Entity ship) {
        return tools.crewAboard(ship);
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
        return tools.ignite(ship, x, y) + " at " + x + "," + y + " aboard " + DebugNames.name(ship);
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

    /** Takes {@code share} (0 to 1) off the system on the tile, or else off the tile. */
    public String damageTile(Entity ship, int x, int y, float share) {
        tileOf(gridOf(ship), x, y);
        return tools.damage(ship, x, y, share);
    }

    /** Knocks the tile out: a wall or hull piece gone (a breach, if it faced space), or a hole in the deck. */
    public String breach(Entity ship, int x, int y) {
        tileOf(gridOf(ship), x, y);
        return tools.breach(ship, x, y) + " at " + x + "," + y;
    }

    /** Shorts out the system on the tile, or the power conduit there; it may light a fire, as a hit's short does. */
    public String shortOut(Entity ship, int x, int y) {
        tileOf(gridOf(ship), x, y);
        return tools.shortOut(ship, x, y);
    }

    /** Fires {@code weapon} once at the tile from straight outboard: a real round, missile or beam. */
    public String shoot(Entity ship, int x, int y, String weapon) {
        tileOf(gridOf(ship), x, y);
        if (weapon == null) weapon = tools.weapons().isEmpty() ? "" : tools.weapons().get(0);
        return tools.fire(ship, weapon, x, y);
    }

    /**
     * Fires {@code weapon} once at the tile from {@code bearing} degrees off the ship's bow,
     * counterclockwise, {@code range} meters out; either NaN for the usual (straight outboard, the
     * weapon's usual distance).
     */
    public String shoot(Entity ship, int x, int y, String weapon, float bearing, float range) {
        tileOf(gridOf(ship), x, y);
        if (weapon == null) weapon = tools.weapons().isEmpty() ? "" : tools.weapons().get(0);
        return tools.fireFrom(ship, weapon, x, y, bearing, range);
    }

    /** Sets off {@code warhead} inside the hull at the tile. */
    public String blast(Entity ship, int x, int y, String warhead) {
        tileOf(gridOf(ship), x, y);
        if (warhead == null) warhead = tools.warheads().isEmpty() ? "" : tools.warheads().get(0);
        return tools.blast(ship, warhead, x, y);
    }

    /** Rams a rock of {@code mass} kg at {@code speed} m/s through the tile, from straight outboard. */
    public String ram(Entity ship, int x, int y, float mass, float speed) {
        tileOf(gridOf(ship), x, y);
        Vector2 at = tools.tileWorld(ship, x, y, new Vector2());
        Vector2 center = PhysicsManager.getPosition(world.docking.bodyOf(ship), new Vector2());
        Vector2 out = at.cpy().sub(center);
        if (out.isZero(1e-3f)) out.set(1f, 0f);
        out.nor();
        return tools.ram(ship, x, y, at.x + out.x, at.y + out.y, mass, speed);
    }

    /** Mends the tile and the system on it: full condition, no patch, no fire, no short. */
    public String repairTile(Entity ship, int x, int y) {
        ShipGridComponent g = gridOf(ship);
        Tile t = tileOf(g, x, y);
        mend(t);
        mend(g.getTileConduit(x, y));
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
            for (int x = 0; x < g.width; x++) {
                mend(g.getTile(x, y));
                mend(g.getTileConduit(x, y));
            }
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
        tools.freshAir(ship);
        for (Compartment c : g.compartments) c.smoke = 0;
        return "Fresh air throughout " + DebugNames.name(ship);
    }

    private static void mend(Tile t) {
        t.setIntegrity(1f);
        t.clearPatch();
        t.extinguish();
    }

    private static void mend(TileConduit c) {
        if (c == null) return;
        c.integrity = 1f;
        c.shorted = false;
        c.shortedFor = 0f;
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
