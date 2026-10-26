package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WarheadDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.LinearTrajectory;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug tools for a running {@link GameWorld}: putting crew aboard a ship, taking them off, and
 * hurting them and the ship in the ways the game does. Nothing here models damage of its own. A
 * shot is a real round put in flight at the hull, which ballistics and impact resolution take from
 * there; a ram is a fast pass swept through the ship, or under 1 km/s a Box2D body that crashes
 * into it ({@code CollisionDamageSystem}); a blast inside is the warhead path impact
 * resolution takes when a round goes off inside; a fire is the deck's own; a wound goes through the
 * crew's damage sink. So whatever those systems become, these tools hurt the ship the same way.
 *
 * <p>Every tool is a plain call on a ship entity and tile coordinates, so the developer console can
 * run them as commands, in the interior demo ({@link InteriorLab}) or in a game. Tools that need
 * time to play out (a beam burning, a rock flying through) run on the physics tick, from the step
 * listener this installs.
 */
public final class WorldDebug implements PhysicsManager.StepListener {
    /** How far outside the hull a shot or a missile starts, meters. */
    public static final float STANDOFF = 60f;
    /** How far out a missile is launched from, meters: far enough for its motor to light and its seeker to lock. */
    public static final float MISSILE_STANDOFF = 600f;
    /** A ram's defaults: a two-tonne rock at 2 km/s, fast enough to be a fast pass (over 1 km/s). */
    public static final float RAM_MASS = 2_000f, RAM_SPEED = 2_000f;
    /** A ram's rock's radius, meters. */
    public static final float RAM_RADIUS = 0.5f;
    /** Seconds a ram's rock stays in the world, long past the ship. */
    static final float RAM_LIFETIME = 2f;
    /**
     * At or below this a ram's rock is a Box2D body that crashes into the hull, as ships do;
     * above it, a fast pass swept through it. m/s.
     */
    public static final float FAST_PASS_SPEED = 1_000f;
    /** How far outside the grid a slow ram's rock starts, meters. */
    static final float SLOW_RAM_STANDOFF = 2f;

    private final GameWorld world;
    /** Beams burning now. */
    private final Array<Beam> beams = new Array<>();
    /** Rocks flying through, removed once past. */
    private final Array<Rock> rocks = new Array<>();
    private final Array<Body> crashRocks = new Array<>();
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();
    private final DamageReport report = new DamageReport();
    private int spawned;

    /** A beam burning one ship from one direction for a while: what {@code BeamSystem} does for a laser on target. */
    public static final class Beam {
        public final Entity ship;
        public final int tileX, tileY;
        /** Unit direction it burns along, world frame. */
        public final float dirX, dirY;
        public final float power;
        public float secondsLeft;
        boolean touched;

        Beam(Entity ship, int tileX, int tileY, float dirX, float dirY, float power, float seconds) {
            this.ship = ship;
            this.tileX = tileX;
            this.tileY = tileY;
            this.dirX = dirX;
            this.dirY = dirY;
            this.power = power;
            this.secondsLeft = seconds;
        }
    }

    /** A ram's rock, and when it is taken out of the world again. */
    private static final class Rock {
        final Entity entity;
        final double until;

        Rock(Entity entity, double until) {
            this.entity = entity;
            this.until = until;
        }
    }

    public WorldDebug(GameWorld world) {
        this.world = world;
        world.physics.addStepListener(this);
    }

    /** Stops the tools' step listener, for a world that goes on without them. */
    public void dispose() {
        world.physics.removeStepListener(this);
    }

    // ------------------------------------------------------------------
    // Crew
    // ------------------------------------------------------------------

    /**
     * Puts a crew member of {@code role} aboard {@code ship} at tile (x, y), working for the ship's
     * own crew (its task board's faction). Null if the tile is not open deck.
     */
    public Entity spawnCrew(Entity ship, CrewRole role, int x, int y) {
        return spawnCrew(ship, role, CrewFactory.ensureBoard(ship).faction, x, y);
    }

    /** {@link #spawnCrew(Entity, CrewRole, int, int)} for crew of {@code faction}: a boarder, say. */
    public Entity spawnCrew(Entity ship, CrewRole role, int faction, int x, int y) {
        ShipGridComponent grid = grid(ship);
        Tile t = grid == null || !grid.inBounds(x, y) ? null : grid.getTile(x, y);
        if (t == null || !t.isOpen() || t.mount != null) return null;
        String name = name(role) + " " + (++spawned);
        return CrewFactory.spawn(world.crew, ship, name, role, faction, x, y);
    }

    /** {@link #spawnCrew(Entity, CrewRole, int, int)} on the first free deck tile from the middle out; null if none. */
    public Entity spawnCrew(Entity ship, CrewRole role) {
        int[] t = freeTile(ship);
        return t == null ? null : spawnCrew(ship, role, t[0], t[1]);
    }

    /** Open deck with nobody on it, scanning from the middle row out, or null. */
    public int[] freeTile(Entity ship) {
        Array<Entity> here = new Array<>();
        for (int[] t : GameScenario.floorTiles(grid(ship))) {
            here.clear();
            world.crew.occupantsAt(ship, t[0], t[1], here);
            if (here.size == 0) return t;
        }
        return null;
    }

    /** Takes {@code crew} out of the world, setting down whoever they carry and letting go of their job. */
    public void removeCrew(Entity crew) {
        CrewMemberComponent c = world.crew.crew(crew);
        if (c == null) return;
        if (c.carrying != null) world.crew.drop(crew);
        if (c.carriedBy != null) world.crew.drop(c.carriedBy);
        world.crew.releaseTask(crew);
        world.engine.getEcsEngine().removeEntity(crew);
    }

    /**
     * Hurts {@code crew} through the crew's damage sink, as a hit or hazard of {@code cause} would:
     * rounds, spall, fragments, blasts and burns wound ({@link Wound}); fire, air and small arms take health.
     */
    public void hurt(Entity crew, float amount, DamageCause cause) {
        world.crew.applyDamage(crew, amount, cause);
    }

    /** Heals {@code crew} outright: no wound, full health. The dead stay dead. */
    public void heal(Entity crew) {
        CrewMemberComponent c = world.crew.crew(crew);
        if (c == null || c.state == CrewState.DEAD) return;
        while (c.wound != Wound.NONE) world.crew.easeWound(crew);
        world.crew.heal(crew, c.def.maxHealth);
    }

    /** Everyone aboard {@code ship}, alive or dead, in the order they joined the world. */
    public List<Entity> crewAboard(Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent p = world.crew.position(e);
            if (p != null && p.ship == ship) out.add(e);
        }
        return out;
    }

    /** The living crew member standing nearest tile (x, y) of {@code ship} within {@code reach} tiles, or null. */
    public Entity crewAt(Entity ship, float x, float y, float reach) {
        Entity best = null;
        float nearest = reach * reach;
        for (Entity e : crewAboard(ship)) {
            GridPositionComponent p = world.crew.position(e);
            float d = Vector2.dst2(p.x, p.y, x, y);
            if (d <= nearest && world.crew.crew(e).state != CrewState.DEAD) {
                nearest = d;
                best = e;
            }
        }
        return best;
    }

    private static String name(CrewRole role) {
        String s = role.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------
    // Weapons, blasts and rams
    // ------------------------------------------------------------------

    /**
     * Fires {@code weaponId} once at tile (x, y) of {@code ship}, from straight outboard of it (out
     * from the ship's center through the tile). See {@link #fire(Entity, String, int, int, float, float)}.
     */
    public String fire(Entity ship, String weaponId, int x, int y) {
        return fire(ship, weaponId, x, y, STANDOFF);
    }

    /** As {@link #fire(Entity, String, int, int)}, a gun's round put in flight {@code standoff} meters out. */
    public String fire(Entity ship, String weaponId, int x, int y, float standoff) {
        Vector2 at = tileWorld(ship, x, y, new Vector2());
        Vector2 center = PhysicsManager.getPosition(body(ship), new Vector2());
        Vector2 out = at.cpy().sub(center);
        if (out.isZero(1e-3f)) out.set(1f, 0f);
        out.nor();
        return fire(ship, weaponId, x, y, at.x + out.x, at.y + out.y, standoff);
    }

    /**
     * Fires {@code weaponId} once at tile (x, y) of {@code ship}, coming from the direction of the
     * world point ({@code fromX}, {@code fromY}). A gun's round is put in flight {@link #STANDOFF}
     * meters out at its muzzle speed; a launcher's missile is launched {@link #MISSILE_STANDOFF}
     * out at the ship; a beam burns for its pulse at its power. Returns what happened, or why not.
     */
    public String fire(Entity ship, String weaponId, int x, int y, float fromX, float fromY) {
        return fire(ship, weaponId, x, y, fromX, fromY, STANDOFF);
    }

    /** As {@link #fire(Entity, String, int, int, float, float)}, a gun's round put in flight {@code standoff} meters out. */
    public String fire(Entity ship, String weaponId, int x, int y, float fromX, float fromY, float standoff) {
        WeaponDef weapon = world.combatDefs.weapons.get(weaponId);
        if (weapon == null) return "No weapon " + weaponId;
        Body body = body(ship);
        ShipGridComponent grid = grid(ship);
        if (body == null || grid == null || !grid.inBounds(x, y)) return "No ship there";
        Vector2 at = tileWorld(ship, x, y, new Vector2());
        Vector2 dir = new Vector2(at).sub(fromX, fromY);
        if (dir.isZero(1e-3f)) dir.set(-1f, 0f);
        dir.nor();
        Vector2 shipVel = tmp2.set(body.getLinearVelocity()).scl(PhysicsManager.METERS_PER_UNIT);
        if (weapon.mount == WeaponDef.MountType.BEAM) {
            beams.add(new Beam(ship, x, y, dir.x, dir.y, weapon.beamPower, weapon.beamDuration));
            return weapon.displayName() + " burning";
        }
        if (weapon.missile != null) {
            MissileDef missile = world.combatDefs.missiles.get(weapon.missile);
            if (missile == null) return "No missile " + weapon.missile;
            float sx = at.x - dir.x * MISSILE_STANDOFF, sy = at.y - dir.y * MISSILE_STANDOFF;
            world.missiles.launch(missile, null, null, ship, sx, sy,
                    shipVel.x + dir.x * missile.ejectSpeed, shipVel.y + dir.y * missile.ejectSpeed, dir.angleRad());
            return missile.id + " launched";
        }
        ProjectileDef round = weapon.projectile == null ? null : world.combatDefs.projectiles.get(weapon.projectile);
        if (round == null) return "No round for " + weaponId;
        float sx = at.x - dir.x * standoff, sy = at.y - dir.y * standoff;
        Entity e = world.ballistics.spawn(round, round.mass, sx, sy,
                shipVel.x + dir.x * round.muzzleSpeed, shipVel.y + dir.y * round.muzzleSpeed, null);
        e.getComponent(ProjectileComponent.class).aimedAt = ship;
        return round.id + " fired";
    }

    /** The weapons a {@link #fire} can use, by id, in data order. */
    public List<String> weapons() {
        List<String> out = new ArrayList<>();
        for (String id : world.combatDefs.weapons.keys()) out.add(id);
        out.sort(null);
        return out;
    }

    /** The warheads a {@link #blast} can use, by id. */
    public List<String> warheads() {
        List<String> out = new ArrayList<>();
        for (String id : world.combatDefs.warheads.keys()) out.add(id);
        out.sort(null);
        return out;
    }

    /**
     * Sets off warhead {@code warheadId} inside {@code ship} at tile (x, y): its overpressure and
     * fragments, as when a missile or shell goes off inside the hull. Returns what happened, or why not.
     */
    public String blast(Entity ship, String warheadId, int x, int y) {
        WarheadDef warhead = world.combatDefs.warheads.get(warheadId);
        ShipGridComponent grid = grid(ship);
        if (warhead == null) return "No warhead " + warheadId;
        if (grid == null || !grid.inBounds(x, y)) return "No ship there";
        report.clear();
        report.ship = ship;
        float absorbed = world.impacts.getResolver().detonateInside(grid, ship, x, y, warhead,
                world.impacts.rngFor(ship), report);
        if (report.structureChanged) grid.gridDirty = true;
        if (!report.isEmpty()) world.combatEvents.damage.dispatch(report);
        return String.format("%s went off: %.1f MJ into the ship", warheadId, absorbed / 1e6f);
    }

    /** Rams a rock of {@link #RAM_MASS} at {@link #RAM_SPEED} through tile (x, y), from straight outboard. */
    public String ram(Entity ship, int x, int y) {
        Vector2 at = tileWorld(ship, x, y, new Vector2());
        Vector2 center = PhysicsManager.getPosition(body(ship), new Vector2());
        Vector2 out = at.cpy().sub(center);
        if (out.isZero(1e-3f)) out.set(1f, 0f);
        out.nor();
        return ram(ship, x, y, at.x + out.x, at.y + out.y, RAM_MASS, RAM_SPEED);
    }

    /**
     * Sends a rock of {@code mass} kg at {@code speed} m/s through tile (x, y) of {@code ship}, from
     * the direction of world point ({@code fromX}, {@code fromY}). Over {@link #FAST_PASS_SPEED} it
     * is a fast pass, swept through the hull by physics and resolved as a ram; at or below, a Box2D
     * body flown at the hull, which crashes into it as another ship would (nothing under 25 m/s).
     */
    public String ram(Entity ship, int x, int y, float fromX, float fromY, float mass, float speed) {
        if (!(speed > 0f) || !(mass > 0f)) return "A ram needs a speed and a mass";
        if (speed <= FAST_PASS_SPEED) return crashRock(ship, x, y, fromX, fromY, mass, speed);
        Body body = body(ship);
        if (body == null || grid(ship) == null) return "No ship there";
        Vector2 at = tileWorld(ship, x, y, new Vector2());
        Vector2 dir = new Vector2(at).sub(fromX, fromY);
        if (dir.isZero(1e-3f)) dir.set(-1f, 0f);
        dir.nor();
        Vector2 v = tmp2.set(body.getLinearVelocity()).scl(PhysicsManager.METERS_PER_UNIT).mulAdd(dir, speed);
        // Starts one tick's travel and the standoff out, so the first sweep begins outside the hull
        float back = STANDOFF + speed / 30f;
        PhysicsManager p = world.physics;
        double now = world.engine.getSimSeconds();
        LinearTrajectory path = new LinearTrajectory(p.getOriginX() + at.x - dir.x * back,
                p.getOriginY() + at.y - dir.y * back, p.getOriginVx() + v.x, p.getOriginVy() + v.y, now);
        Entity rock = new Entity();
        rock.add(new TransformComponent());
        KinematicTransientComponent k = new KinematicTransientComponent(path, RAM_RADIUS);
        k.mass = mass;
        k.angle = dir.angleRad();
        rock.add(k);
        world.engine.addEntity(rock);
        rocks.add(new Rock(rock, now + RAM_LIFETIME));
        return String.format("%.0f kg at %.1f km/s", mass, speed / 1000f);
    }

    /**
     * A rock flown at tile (x, y) from the direction of world point ({@code fromX}, {@code fromY}),
     * starting just outside the grid: a Box2D body, so the crash is the one ships have.
     */
    private String crashRock(Entity ship, int x, int y, float fromX, float fromY, float mass, float speed) {
        Body body = body(ship);
        if (body == null || grid(ship) == null) return "No ship there";
        Vector2 at = tileWorld(ship, x, y, new Vector2());
        Vector2 dir = new Vector2(at).sub(fromX, fromY);
        if (dir.isZero(1e-3f)) dir.set(-1f, 0f);
        dir.nor();
        Vector2 start = entry(ship, x, y, dir.x, dir.y, new Vector2()).mulAdd(dir, -SLOW_RAM_STANDOFF);
        Vector2 v = PhysicsManager.getVelocity(body, new Vector2()).mulAdd(dir, speed);
        Entity rock = new Entity();
        rock.add(new TransformComponent());
        Body b = world.physics.createCircleBody(rock, start.x, start.y, RAM_RADIUS, 1f,
                CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL);
        PhysicsManager.setMassData(b, mass, 0f, 0f, 0.5f * mass * RAM_RADIUS * RAM_RADIUS);
        PhysicsManager.setVelocity(b, v.x, v.y);
        rock.add(new PhysicsBodyComponent(b));
        world.engine.addEntity(rock);
        float flight = start.dst(at) / speed;
        rocks.add(new Rock(rock, world.engine.getSimSeconds() + flight + RAM_LIFETIME));
        return String.format("%.0f kg at %.0f m/s", mass, speed);
    }

    /** The bodies of slow rams' rocks still in the world, for drawing. */
    public Array<Body> crashRocks() {
        crashRocks.clear();
        for (Rock r : rocks) {
            PhysicsBodyComponent pb = r.entity.getComponent(PhysicsBodyComponent.class);
            if (pb != null && pb.body != null) crashRocks.add(pb.body);
        }
        return crashRocks;
    }

    /** Beams burning now, for drawing. */
    public Array<Beam> beams() {
        return beams;
    }

    @Override
    public void beforeStep(float dt) {
        for (int i = beams.size - 1; i >= 0; i--) {
            Beam b = beams.get(i);
            if (b.secondsLeft <= 0f || body(b.ship) == null) {
                beams.removeIndex(i);
                continue;
            }
            // Where the beam meets the grid's edge on its way to the tile, as BeamSystem finds the hull
            Vector2 entry = entry(b.ship, b.tileX, b.tileY, b.dirX, b.dirY, tmp);
            world.impacts.burn(b.ship, entry.x, entry.y, b.dirX, b.dirY, b.power * dt, !b.touched);
            b.touched = true;
            b.secondsLeft -= dt;
        }
        double now = world.engine.getSimSeconds();
        for (int i = rocks.size - 1; i >= 0; i--) {
            if (now < rocks.get(i).until) continue;
            world.engine.removeEntity(rocks.removeIndex(i).entity);
        }
    }

    /** The world point where a line along (dirX, dirY) to tile (x, y) enters the ship's grid. */
    private Vector2 entry(Entity ship, int x, int y, float dirX, float dirY, Vector2 out) {
        ShipGridComponent grid = grid(ship);
        Vector2 at = tileWorld(ship, x, y, out);
        float reach = (grid.width + grid.height) * grid.tileSize;
        return at.mulAdd(tmp2.set(dirX, dirY), -reach);
    }

    // ------------------------------------------------------------------
    // The deck
    // ------------------------------------------------------------------

    /** Lights tile (x, y), if there is anything there to burn and air to burn it. Returns what happened. */
    public String ignite(Entity ship, int x, int y) {
        Tile t = tile(ship, x, y);
        if (t == null || t.isVoid()) return "No deck there";
        if (t.isBurning()) return "Already burning";
        if (t.ignite()) return "Fire";
        return t.holdsAir() ? "Nothing to burn there" : "No air to burn";
    }

    /** Puts out every fire aboard. */
    public void extinguish(Entity ship) {
        ShipGridComponent grid = grid(ship);
        if (grid == null) return;
        for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) grid.getTile(x, y).extinguish();
    }

    /** Knocks tile (x, y) out: a wall or hull piece gone (a breach, if it faced space), or a hole in the deck. */
    public String breach(Entity ship, int x, int y) {
        Tile t = tile(ship, x, y);
        if (t == null || t.isVoid()) return "No deck there";
        t.setIntegrity(0f);
        t.bulkheadSealed = false;
        grid(ship).gridDirty = true;
        return t.isHullBreach() ? "Hull breach" : "Holed";
    }

    /**
     * Takes {@code amount} (0-1) off the system mounted at tile (x, y), or else off the tile, as
     * the old interior demo's middle click did. Returns what it damaged.
     */
    public String damage(Entity ship, int x, int y, float amount) {
        Tile t = tile(ship, x, y);
        if (t == null || t.isVoid()) return "No deck there";
        SubsystemComponent s = system(t);
        grid(ship).gridDirty = true;
        if (s != null) {
            s.health = Math.max(0f, s.health - amount);
            return String.format("%s at %.0f%%", s.type, s.health * 100f);
        }
        t.setIntegrity(t.getIntegrity() - amount);
        return String.format("Tile at %.0f%%", t.getIntegrity() * 100f);
    }

    /** Shorts out the system mounted at tile (x, y), or the power conduit there: as a hit's short does, it may light a fire. */
    public String shortOut(Entity ship, int x, int y) {
        Tile t = tile(ship, x, y);
        if (t == null) return "No deck there";
        SubsystemComponent s = system(t);
        if (s != null) {
            DamageStates.shortOut(grid(ship), s, x, y, world.impacts.rngFor(ship));
            return s.type + " shorted";
        }
        if (!t.isPowerConduit()) return "No system or conduit there";
        DamageStates.shortOut(grid(ship), x, y, world.impacts.rngFor(ship));
        return "Conduit shorted";
    }

    /** Fresh air in every room, and the ship cooled to room temperature. */
    public void freshAir(Entity ship) {
        ShipGridComponent grid = grid(ship);
        if (grid == null) return;
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) grid.getTile(x, y).temperature = Compartment.AMBIENT_TEMPERATURE;
        for (Compartment c : grid.compartments) {
            c.temperature = Compartment.AMBIENT_TEMPERATURE;
            c.fillStandardAir();
        }
    }

    /** Switches every reactor aboard off, or back on if they were all off. Returns whether they are on now. */
    public boolean toggleReactors(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return false;
        boolean anyOn = false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR) anyOn |= s.switchedOn;
        }
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = !anyOn;
        }
        grid(ship).gridDirty = true;
        return !anyOn;
    }

    /** Seals every door aboard, or opens them all if any was sealed. Returns whether they are sealed now. */
    public boolean toggleDoors(Entity ship) {
        ShipGridComponent grid = grid(ship);
        if (grid == null) return false;
        boolean anySealed = false;
        List<EdgeWall> doors = new ArrayList<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall w = grid.edgeV[y][x];
                if (w != null && w.hasBulkhead) doors.add(w);
            }
        }
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeWall w = grid.edgeH[y][x];
                if (w != null && w.hasBulkhead) doors.add(w);
            }
        }
        for (EdgeWall w : doors) anySealed |= w.bulkheadSealed;
        for (EdgeWall w : doors) w.bulkheadSealed = !anySealed;
        grid.gridDirty = true;
        return !anySealed;
    }

    // ------------------------------------------------------------------
    // Where things are
    // ------------------------------------------------------------------

    /** The middle of tile (x, y) of {@code ship}, world (bubble) meters. */
    public Vector2 tileWorld(Entity ship, int x, int y, Vector2 out) {
        Vector2 local = grid(ship).tileToLocal(x, y);
        return toWorld(body(ship), local.x, local.y, out);
    }

    /** A point in {@code body}'s own frame, meters, in world (bubble) meters. */
    public static Vector2 toWorld(Body body, float x, float y, Vector2 out) {
        Vector2 w = body.getWorldPoint(out.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER));
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    /** A world (bubble) point, meters, in {@code body}'s own frame, meters. */
    public static Vector2 toLocal(Body body, float x, float y, Vector2 out) {
        Vector2 l = body.getLocalPoint(out.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER));
        return out.set(l.x * PhysicsManager.METERS_PER_UNIT, l.y * PhysicsManager.METERS_PER_UNIT);
    }

    static Body body(Entity ship) {
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        return pb == null ? null : pb.body;
    }

    private static ShipGridComponent grid(Entity ship) {
        return ship == null ? null : ship.getComponent(ShipGridComponent.class);
    }

    private static Tile tile(Entity ship, int x, int y) {
        ShipGridComponent grid = grid(ship);
        return grid == null || !grid.inBounds(x, y) ? null : grid.getTile(x, y);
    }

    private static SubsystemComponent system(Tile t) {
        return t.mount == null || t.mount.installed == null ? null : t.mount.installed.getComponent(SubsystemComponent.class);
    }
}
