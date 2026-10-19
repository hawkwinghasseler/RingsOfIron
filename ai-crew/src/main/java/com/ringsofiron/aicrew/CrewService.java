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
    public final RandomXS128 random;

    public CrewService(Engine engine) {
        this(engine, 0xC2E3L);
    }

    public CrewService(Engine engine, long seed) {
        this.engine = engine;
        this.crewEntities = engine.getEntitiesFor(CREW);
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

    /** True if crew members {@code a} and {@code b} are enemies. */
    public boolean hostile(CrewMemberComponent a, CrewMemberComponent b) {
        return hostility.hostile(a.faction, b.faction);
    }

    /** Drops the cached pathfinders and interior worlds, e.g. after a load replaced every grid. */
    public void clearPathfinders() {
        pathfinders.clear();
        physics.clear();
    }

    /** The pathfinder for a grid, created on first use. */
    public GridPathfinder pathfinder(ShipGridComponent grid) {
        GridPathfinder pf = pathfinders.get(grid);
        if (pf == null) {
            pf = new GridPathfinder(grid);
            pathfinders.put(grid, pf);
        }
        return pf;
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

    @Override
    public void applyDamage(Entity crewEntity, float amount, DamageCause cause) {
        CrewMemberComponent c = crew(crewEntity);
        if (c == null || c.state == CrewState.DEAD || amount <= 0f) return;
        c.health = Math.max(0f, c.health - amount);
        c.lastDamageCause = cause;
        updateState(crewEntity);
    }

    /** Heals (amount positive), clamped to max health, then updates the state. Dead crew stay dead. */
    public void heal(Entity crewEntity, float amount) {
        CrewMemberComponent c = crew(crewEntity);
        if (c == null || c.state == CrewState.DEAD) return;
        c.health = Math.min(c.def.maxHealth, c.health + amount);
        updateState(crewEntity);
    }

    /**
     * Moves the crew member between states from their health: dead at 0, unconscious at
     * or below the threshold, conscious again once above the threshold plus the wake
     * margin.
     */
    public void updateState(Entity e) {
        CrewMemberComponent c = crew(e);
        if (c.state == CrewState.DEAD) return;
        float frac = c.healthFraction();
        if (c.health <= 0f) {
            setState(e, c, CrewState.DEAD);
        } else if (c.state == CrewState.CONSCIOUS && frac <= c.def.unconsciousThreshold) {
            setState(e, c, CrewState.UNCONSCIOUS);
        } else if (c.state == CrewState.UNCONSCIOUS && frac > c.def.unconsciousThreshold + c.def.wakeMargin) {
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
