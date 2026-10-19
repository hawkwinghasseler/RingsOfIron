package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.DamageTaskSink;
import com.ringsofiron.interiorgrid.crew.GridOccupantQuery;

/**
 * The crew module's shared services, and its implementation of the interfaces other
 * modules call: {@link GridOccupantQuery}, {@link CrewDamageSink} and
 * {@link DamageTaskSink}. One instance per engine; pass it to combat and to the crew
 * systems.
 *
 * It also owns the per-ship pathfinders, the {@link CrewPhysics} inside each deck, and the crew state machine (conscious,
 * unconscious, dead; see {@link #updateState}).
 */
public class CrewService implements GridOccupantQuery, CrewDamageSink, DamageTaskSink {

    public static final Family CREW = Family.all(CrewMemberComponent.class, GridPositionComponent.class).get();

    private final ComponentMapper<CrewMemberComponent> crewM = ComponentMapper.getFor(CrewMemberComponent.class);
    private final ComponentMapper<GridPositionComponent> posM = ComponentMapper.getFor(GridPositionComponent.class);
    private final ComponentMapper<BehaviorTreeComponent> btM = ComponentMapper.getFor(BehaviorTreeComponent.class);
    private static final Family HOLDS = Family.all(ShipGridComponent.class, CargoHoldComponent.class).get();
    private final ImmutableArray<Entity> holds;
    private final ComponentMapper<ShipGridComponent> gridM = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipTaskBoardComponent> boardM = ComponentMapper.getFor(ShipTaskBoardComponent.class);

    /** Whether crew of two factions fight. */
    public interface Hostility {
        boolean hostile(int a, int b);
    }

    /** Whether crew of two factions fight on sight; by default any two different factions do. */
    public Hostility hostility = (a, b) -> a != b;

    private final Engine engine;
    private final ImmutableArray<Entity> crewEntities;
    private final ObjectMap<ShipGridComponent, GridPathfinder> pathfinders = new ObjectMap<>();
    /** The Box2D world inside each deck crew are walking, and their bodies in it. */
    public final CrewPhysics physics = new CrewPhysics();
    /** The bulkhead doors crew open on their way, and shut behind them. */
    public final CrewDoors doors = new CrewDoors(this);
    public final RandomXS128 random;

    public CrewService(Engine engine) {
        this(engine, 0xC2E3L);
    }

    public CrewService(Engine engine, long seed) {
        this.engine = engine;
        this.crewEntities = engine.getEntitiesFor(CREW);
        this.holds = engine.getEntitiesFor(HOLDS);
        this.random = new RandomXS128(seed);
    }

    public Engine engine() {
        return engine;
    }

    public ImmutableArray<Entity> crewEntities() {
        return crewEntities;
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public CrewMemberComponent crew(Entity e) {
        return e == null ? null : crewM.get(e);
    }

    public GridPositionComponent position(Entity e) {
        return e == null ? null : posM.get(e);
    }

    public ShipGridComponent grid(Entity ship) {
        return ship == null ? null : gridM.get(ship);
    }

    public ShipTaskBoardComponent board(Entity ship) {
        return ship == null ? null : boardM.get(ship);
    }

    /** The craft {@code crew} works for: their home, or else the one they are aboard. */
    public Entity homeOf(Entity crew) {
        CrewMemberComponent c = crew(crew);
        if (c != null && c.home != null) return c.home;
        GridPositionComponent p = position(crew);
        return p == null ? null : p.ship;
    }

    /**
     * How far {@code to} is from {@code from}, in tiles along each axis (Manhattan), on the same
     * craft or on one docked to it (measured across the link, the craft being held port to port).
     * Infinite between craft not joined.
     */
    public float distance(GridPositionComponent from, GridPositionComponent to) {
        if (from.ship == to.ship) return Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
        GridLink l = linkBetween(grid(from.ship), grid(to.ship));
        if (l == null) return Float.POSITIVE_INFINITY;
        l.mate.carryAcross(to.x, to.y, across);
        return Math.abs(across[0] - from.x) + Math.abs(across[1] - from.y);
    }

    /** True if nothing blocks the line from {@code from}'s tile to {@code to}'s, on one craft or through a dock. */
    public boolean canSee(GridPositionComponent from, GridPositionComponent to) {
        ShipGridComponent a = grid(from.ship), b = grid(to.ship);
        if (a == null || b == null) return false;
        return GridRayTraversal.hasLineOfSight(a, from.tileX(), from.tileY(), b, to.tileX(), to.tileY());
    }

    /** True if craft {@code a} and {@code b} are the same craft, or docked straight to each other. */
    public boolean joined(Entity a, Entity b) {
        return a == b || linkBetween(grid(a), grid(b)) != null;
    }

    /** A link from grid {@code a} straight onto grid {@code b}, or null if they are not docked. */
    private static GridLink linkBetween(ShipGridComponent a, ShipGridComponent b) {
        if (a == null || b == null) return null;
        for (int i = 0; i < a.links.size; i++) if (a.links.get(i).other == b) return a.links.get(i);
        return null;
    }

    private final float[] across = new float[2];

    /** Who may go through the doors of a craft whose crew are hostile to them anyway. */
    public interface DoorRights {
        boolean mayPass(Entity crew, Entity ship);
    }

    /** Lets a boarding party through the doors of the craft it boards; by default no one. */
    public DoorRights doorRights = (crew, ship) -> false;

    /** True if crew members {@code a} and {@code b} are enemies. A prisoner is no one's enemy. */
    public boolean hostile(CrewMemberComponent a, CrewMemberComponent b) {
        if (a.held || b.held) return false;
        return hostility.hostile(a.faction, b.faction);
    }

    /** Drops the cached pathfinders and interior worlds, e.g. after a load replaced every grid. */
    public void clearPathfinders() {
        pathfinders.clear();
        physics.clear();
        doors.clear();
    }

    /** The pathfinder for a grid, created on first use, knowing the craft's cargo hold if it has one. */
    public GridPathfinder pathfinder(ShipGridComponent grid) {
        GridPathfinder pf = pathfinders.get(grid);
        if (pf == null) {
            pf = new GridPathfinder(grid);
            pathfinders.put(grid, pf);
        }
        // A hold can be fitted after the crew first walk the deck, so it is looked up each time
        pf.graph.hold = holdOf(grid);
        return pf;
    }

    /** The cargo hold of the craft whose grid is {@code grid}, or null. */
    private CargoHoldComponent holdOf(ShipGridComponent grid) {
        for (int i = 0; i < holds.size(); i++) {
            Entity e = holds.get(i);
            if (gridM.get(e) == grid) return e.getComponent(CargoHoldComponent.class);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // GridOccupantQuery
    // -----------------------------------------------------------------------

    @Override
    public void occupantsAt(Entity ship, int x, int y, Array<Entity> out) {
        // Indexed: ImmutableArray's iterator cannot be nested, and callers may be iterating crew
        for (int i = 0; i < crewEntities.size(); i++) {
            Entity e = crewEntities.get(i);
            GridPositionComponent p = posM.get(e);
            if (p.ship == ship && p.tileX() == x && p.tileY() == y) out.add(e);
        }
    }

    // -----------------------------------------------------------------------
    // DamageTaskSink
    // -----------------------------------------------------------------------

    @Override
    public boolean post(Entity ship, CrewTask task) {
        ShipTaskBoardComponent board = board(ship);
        return board != null && board.post(task);
    }

    // -----------------------------------------------------------------------
    // CrewDamageSink and the state machine
    // -----------------------------------------------------------------------

    /**
     * Hits ({@link #wounds}) wound rather than take health: see {@link #wound}. Everything else
     * (air, heat, fire, small arms) takes health as before.
     */
    @Override
    public void applyDamage(Entity crewEntity, float amount, DamageCause cause) {
        CrewMemberComponent c = crew(crewEntity);
        if (c == null || c.state == CrewState.DEAD || amount <= 0f) return;
        c.lastDamageCause = cause;
        if (wounds(cause)) {
            wound(crewEntity, c, amount, cause);
            return;
        }
        c.health = Math.max(0f, c.health - amount);
        updateState(crewEntity);
    }

    /**
     * Whether {@code cause} wounds ({@link Wound}) rather than takes health: rounds, spall,
     * fragments, blasts and weapon burns. Small arms take health, so boarding fights go as they
     * did: a crew member shot down falls unconscious and comes round.
     */
    public static boolean wounds(DamageCause cause) {
        if (cause == null) return false;
        switch (cause) {
            case PENETRATOR: case SPALL: case FRAGMENT: case BLAST: case BURN: return true;
            default: return false;
        }
    }

    /**
     * Wounds {@code c} by a hit of {@code amount} health points. A hit under
     * {@link CrewDef#grazeWound} is a graze and does nothing; one of {@link CrewDef#lethalWound} or
     * more kills outright. Otherwise the hit adds to {@link CrewMemberComponent#injury}, and the
     * wound is as bad as the injury: light, serious from {@link CrewDef#seriousWound}, critical from
     * {@link CrewDef#criticalWound}. So a hit that would once have killed (100 points) leaves a
     * critical wound, which starts its {@link CrewDef#bleedOutTime} clock; a serious hit on someone
     * already critical halves what is left of it.
     */
    private void wound(Entity e, CrewMemberComponent c, float amount, DamageCause cause) {
        CrewDef d = c.def;
        if (amount < d.grazeWound) return;
        if (amount >= d.lethalWound) {
            // Nothing to treat: a warhead going off beside them, say
            c.woundCause = cause;
            c.wound = Wound.CRITICAL;
            dieOfWounds(e);
            return;
        }
        Wound was = c.wound;
        c.injury += amount;
        Wound now = d.woundFor(c.injury);
        if (was == Wound.CRITICAL && amount >= d.seriousWound) c.bleedOut *= 0.5f;
        if (now.compareTo(was) <= 0) return;
        c.wound = now;
        c.woundCause = cause;
        c.care = 0f;
        c.healing = 0f;
        if (now == Wound.CRITICAL) c.bleedOut = d.bleedOutTime;
        updateState(e);
    }

    /**
     * Brings {@code e}'s wound down a level, as treatment does, and their injury to the least of
     * that level; a crew member no longer down gets up if their health allows.
     */
    public void easeWound(Entity e) {
        CrewMemberComponent c = crew(e);
        if (c == null || c.state == CrewState.DEAD || c.wound == Wound.NONE) return;
        c.wound = c.wound.better();
        c.injury = c.def.injuryFor(c.wound);
        c.care = 0f;
        c.healing = 0f;
        if (c.wound == Wound.NONE) c.woundCause = null;
        updateState(e);
    }

    /** {@code e} dies of their wound. */
    public void dieOfWounds(Entity e) {
        CrewMemberComponent c = crew(e);
        if (c == null || c.state == CrewState.DEAD) return;
        c.lastDamageCause = c.woundCause != null ? c.woundCause : c.lastDamageCause;
        c.health = 0f;
        updateState(e);
    }

    /** Heals (amount positive), clamped to max health, then updates the state. Dead crew stay dead. */
    public void heal(Entity crewEntity, float amount) {
        CrewMemberComponent c = crew(crewEntity);
        if (c == null || c.state == CrewState.DEAD) return;
        c.health = Math.min(c.def.maxHealth, c.health + amount);
        updateState(crewEntity);
    }

    /**
     * Moves the crew member between states from their health and wound: dead at 0 health, down
     * (unconscious) at or below the threshold or with a serious or critical wound, and up again
     * once above the threshold plus the wake margin with no worse than a light wound.
     */
    public void updateState(Entity e) {
        CrewMemberComponent c = crew(e);
        if (c.state == CrewState.DEAD) return;
        float frac = c.healthFraction();
        if (c.health <= 0f) {
            setState(e, c, CrewState.DEAD);
        } else if (c.state == CrewState.CONSCIOUS && (frac <= c.def.unconsciousThreshold || c.wound.disabling())) {
            setState(e, c, CrewState.UNCONSCIOUS);
        } else if (c.state == CrewState.UNCONSCIOUS && frac > c.def.unconsciousThreshold + c.def.wakeMargin
                && !c.wound.disabling()) {
            setState(e, c, CrewState.CONSCIOUS);
        }
    }

    private void setState(Entity e, CrewMemberComponent c, CrewState next) {
        CrewState prev = c.state;
        if (prev == next) return;
        c.state = next;
        if (prev == CrewState.CONSCIOUS) {
            // Knocked out or killed while acting: stop everything and hand the job back
            BehaviorTreeComponent bt = btM.get(e);
            if (bt != null) bt.tree.resetTask();
            releaseTask(e);
            GridPositionComponent p = position(e);
            if (p != null) p.stop();
            c.activity = next == CrewState.DEAD ? "dead" : "unconscious";
        } else if (next == CrewState.CONSCIOUS) {
            c.activity = "idle";
            c.treatedBy = null;
            if (c.carriedBy != null) drop(c.carriedBy);
        }
        if (next == CrewState.DEAD) {
            c.activity = "dead";
            c.treatedBy = null;
        }
    }

    /**
     * Gives the crew member's claimed task back to the board, drops anyone they carry, and lets go
     * of goods in hand (they go back where they came from).
     */
    public void releaseTask(Entity e) {
        CrewMemberComponent c = crew(e);
        if (c == null) return;
        drop(e);
        dropLoad(c);
        if (c.task != null) {
            // The board may be on another craft (a loader aboard a docked ship): release it directly
            if (c.task.claimedBy == e) {
                if (c.task.status == CrewTask.Status.CLAIMED) c.task.status = CrewTask.Status.OPEN;
                c.task.claimedBy = null;
            }
            if (c.task instanceof TreatTask) {
                CrewMemberComponent patient = crew(((TreatTask) c.task).patient);
                if (patient != null && patient.treatedBy == e) patient.treatedBy = null;
            }
            c.task = null;
        }
    }

    /** The goods in {@code c}'s hands never arrive: their job takes them back. */
    public void dropLoad(CrewMemberComponent c) {
        if (c == null || c.load == null) return;
        c.load.task.job.abandon(c.load.units);
        c.load = null;
    }

    /** {@code carrier} picks up {@code casualty}. */
    public void pickUp(Entity carrier, Entity casualty) {
        CrewMemberComponent a = crew(carrier), b = crew(casualty);
        drop(carrier);
        a.carrying = casualty;
        b.carriedBy = carrier;
    }

    /** {@code carrier} puts down whoever they carry on their current tile. */
    public void drop(Entity carrier) {
        CrewMemberComponent a = crew(carrier);
        if (a == null || a.carrying == null) return;
        CrewMemberComponent b = crew(a.carrying);
        GridPositionComponent pa = position(carrier), pb = position(a.carrying);
        if (pa != null && pb != null) pb.setTile(pa.tileX(), pa.tileY());
        if (b != null) b.carriedBy = null;
        a.carrying = null;
    }
}
