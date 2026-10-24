package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BehaviorTreeComponent;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.EvacuateTask;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.coreengine.EntityRemovalListener;
import com.ringsofiron.interiorgrid.EscapePodLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * Abandoning ship (GDD section 5, Technical Design Decisions section 6). {@link #order} sends a
 * ship's crew to its escape pods: every seat goes on the task board as an {@link EvacuateTask}
 * above any other job, so the crew drop what they are doing and make for the nearest pod. The
 * player's avatar walks there too, by itself unless the player steers it.
 *
 * <p>A pod launches with whoever is aboard once it is full, once every conscious crew member
 * aboard the ship is in a pod, or {@link #LAUNCH_AFTER} seconds after the order (at once on
 * {@link #launchNow}). The crew in it leave the ship and {@link #launches} reports who got away;
 * a launched pod is spent (switched off, its bay empty). Crew who are down, or who don't reach a
 * seat, stay behind. Once a pod has gone and no conscious crew of the ship's own are left aboard,
 * the ship is {@link DisabledComponent.Reason#ABANDONED}.
 */
public class AbandonShip extends EntitySystem {
    /** Seconds after the order when every pod with anyone aboard goes. */
    public static final double LAUNCH_AFTER = 30.0;

    private static final Family ORDERED = Family.all(AbandonOrderComponent.class, ShipGridComponent.class,
            ShipSubsystemRegistryComponent.class).get();
    private static final ComponentMapper<AbandonOrderComponent> ORDER = ComponentMapper.getFor(AbandonOrderComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);

    /** Seats in an escape pod of each size. */
    public static int seats(MountFootprint.MountSize size) {
        switch (size) {
            case S: return 4;
            case M: return 8;
            case L: return 20;
            default: return 48;
        }
    }

    /** A crew member who got away in a pod. */
    public static final class Survivor {
        public final String name;
        public final CrewRole role;
        /** Their skills, wage and history, carried to the next ship; null for none. */
        public CrewRecord record;

        public Survivor(String name, CrewRole role) {
            this.name = name;
            this.role = role;
        }
    }

    /** A pod launched. */
    public static final class Escape {
        public final Entity ship;
        public final Entity pod;
        /** Who was in it, the player's avatar aside. */
        public final List<Survivor> crew;
        /** True if the player's avatar was in it. */
        public final boolean player;
        public final double time;

        Escape(Entity ship, Entity pod, List<Survivor> crew, boolean player, double time) {
            this.ship = ship;
            this.pod = pod;
            this.crew = crew;
            this.player = player;
            this.time = time;
        }
    }

    /** Every pod launch. */
    public final Signal<Escape> launches = new Signal<>();

    private final CrewService crew;
    private final ShipDisablement disablement;
    private final DoubleSupplier simSeconds;
    /** The craft a ship is latched to, or null. */
    private final Function<Entity, Entity> dockedTo;
    private Engine engine;
    private ImmutableArray<Entity> ordered;
    /** Seats posted for each ship under orders; not saved, posted again after a load. */
    private final Map<Entity, List<EvacuateTask>> posted = new HashMap<>();
    /** A ship gone from the engine comes back from its pool as a new one, which has no seats posted yet. */
    private final EntityRemovalListener forget = posted::remove;
    private final Array<Entity> buffer = new Array<>();
    // Walking the player's avatar to a pod
    private final Array<GridNode> avatarPath = new Array<>();
    private int avatarStep;
    private double repathAt = Double.NEGATIVE_INFINITY;
    private float steerX, steerY;

    public AbandonShip(CrewService crew, ShipDisablement disablement, DoubleSupplier simSeconds,
                       Function<Entity, Entity> dockedTo) {
        this.crew = crew;
        this.disablement = disablement;
        this.simSeconds = simSeconds;
        this.dockedTo = dockedTo;
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        ordered = engine.getEntitiesFor(ORDERED);
        engine.addEntityListener(forget);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(forget);
        this.engine = null;
        ordered = null;
        posted.clear();
    }

    /** Orders {@code ship}'s crew to the pods. Returns why not, or null once the order is given. */
    public String order(Entity ship) {
        if (ship == null) return "no ship";
        if (ORDER.get(ship) != null) return "already abandoning ship";
        if (dockedTo.apply(ship) != null) return "docked: walk off instead";
        if (usablePods(ship).isEmpty()) return "no escape pod left";
        if (ownCrew(ship, null) == 0) return "nobody aboard to abandon ship";
        ship.add(new AbandonOrderComponent(simSeconds.getAsDouble()));
        post(ship);
        return null;
    }

    /** True while {@code ship}'s crew are under orders to abandon it. */
    public static boolean abandoning(Entity ship) {
        return ship != null && ORDER.get(ship) != null;
    }

    /** Launches every pod with anyone aboard at the next step, without waiting for the rest. */
    public void launchNow(Entity ship) {
        AbandonOrderComponent order = ship == null ? null : ORDER.get(ship);
        // A second past due, so rounding never leaves it a hair short
        if (order != null) order.orderedAt = simSeconds.getAsDouble() - LAUNCH_AFTER - 1;
    }

    /** Calls the order off: the crew go back to work. */
    public void callOff(Entity ship) {
        if (ship == null || ORDER.get(ship) == null) return;
        ship.remove(AbandonOrderComponent.class);
        closeSeats(ship);
    }

    /** The ship's escape pods that can still launch: not spent, not wrecked. */
    public static List<Entity> usablePods(Entity ship) {
        List<Entity> out = new ArrayList<>();
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        if (registry == null) return out;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.ESCAPE_POD && s.switchedOn && !s.isDisabled()) {
                out.add(e);
            }
        }
        return out;
    }

    /** Seats in the ship's usable pods. */
    public static int seats(Entity ship) {
        int n = 0;
        for (Entity pod : usablePods(ship)) n += seats(pod.getComponent(SubsystemComponent.class).footprint.size);
        return n;
    }

    @Override
    public void update(float deltaTime) {
        if (ordered == null) return;
        for (Entity ship : ordered) buffer.add(ship);
        for (Entity ship : buffer) step(ship);
        buffer.clear();
    }

    private void step(Entity ship) {
        AbandonOrderComponent order = ORDER.get(ship);
        if (!posted.containsKey(ship)) post(ship);
        double now = simSeconds.getAsDouble();
        List<Entity> people = new ArrayList<>();
        ownCrew(ship, people);
        List<Entity> pods = usablePods(ship);
        boolean everyoneIn = true;
        for (Entity e : people) if (podAt(pods, e) == null) everyoneIn = false;
        boolean late = now - order.orderedAt >= LAUNCH_AFTER;
        for (Entity pod : pods) {
            SubsystemComponent s = pod.getComponent(SubsystemComponent.class);
            int seats = seats(s.footprint.size);
            List<Entity> aboard = new ArrayList<>();
            for (Entity e : people) if (aboard.size() < seats && podAt(List.of(pod), e) != null) aboard.add(e);
            if (!aboard.isEmpty() && (aboard.size() >= seats || everyoneIn || late)) launch(ship, order, pod, aboard, now);
        }
        int left = ownCrew(ship, null);
        if (order.launched > 0 && left == 0) {
            ship.remove(AbandonOrderComponent.class);
            closeSeats(ship);
            disablement.set(ship, DisabledComponent.Reason.ABANDONED);
            return;
        }
        if (usablePods(ship).isEmpty()) {
            // No seats left for whoever is still aboard
            ship.remove(AbandonOrderComponent.class);
            closeSeats(ship);
            return;
        }
        steerAvatar(ship, now);
    }

    private void launch(Entity ship, AbandonOrderComponent order, Entity pod, List<Entity> aboard, double now) {
        SubsystemComponent s = pod.getComponent(SubsystemComponent.class);
        s.switchedOn = false;
        order.launched++;
        List<Survivor> away = new ArrayList<>();
        boolean player = false;
        for (Entity e : aboard) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            crew.releaseTask(e);
            if (e.getComponent(PlayerAvatarComponent.class) != null) {
                // The avatar is the player: it stays, for the rescue to put aboard their next ship
                player = true;
                e.getComponent(GridPositionComponent.class).stop();
                PlayerAvatarComponent a = e.getComponent(PlayerAvatarComponent.class);
                a.moveX = a.moveY = steerX = steerY = 0f;
                continue;
            }
            Survivor survivor = new Survivor(c.name, c.role);
            survivor.record = c.record();
            away.add(survivor);
            engine.removeEntity(e);
        }
        List<EvacuateTask> tasks = posted.get(ship);
        if (tasks != null) for (EvacuateTask t : tasks) if (t.pod == pod) t.open = false;
        launches.dispatch(new Escape(ship, pod, away, player, now));
    }

    /** The pod whose bay {@code crewEntity} stands in, out of {@code pods}, or null. */
    private static Entity podAt(List<Entity> pods, Entity crewEntity) {
        GridPositionComponent p = crewEntity.getComponent(GridPositionComponent.class);
        if (p.walkway != null) return null;
        int x = p.tileX(), y = p.tileY();
        for (Entity pod : pods) if (pod.getComponent(SubsystemComponent.class).covers(x, y)) return pod;
        return null;
    }

    /**
     * Counts the conscious crew of the ship's own aboard it (the player's avatar included), adding
     * them to {@code out} if it is not null.
     */
    private int ownCrew(Entity ship, List<Entity> out) {
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        int faction = board == null ? 0 : board.faction;
        int n = 0;
        for (Entity e : crew.crewEntities()) {
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.isScheduledForRemoval() || p.ship != ship || c.faction != faction || !c.isConscious()) continue;
            if (c.home != null && c.home != ship) continue;
            n++;
            if (out != null) out.add(e);
        }
        return n;
    }

    /** Puts a seat for every place in the ship's pods on its board, and pulls the crew off what they were doing. */
    private void post(Entity ship) {
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        List<EvacuateTask> tasks = new ArrayList<>();
        posted.put(ship, tasks);
        if (board == null) return;
        for (Entity pod : usablePods(ship)) {
            SubsystemComponent s = pod.getComponent(SubsystemComponent.class);
            // Its hatch end is in the hull: the crew make for the bay behind it
            int[] bay = EscapePodLayout.bayTile(s.footprint);
            for (int i = 0; i < seats(s.footprint.size); i++) {
                EvacuateTask t = new EvacuateTask(pod, bay[0], bay[1]);
                board.post(t);
                tasks.add(t);
            }
        }
        List<Entity> people = new ArrayList<>();
        ownCrew(ship, people);
        for (Entity e : people) {
            if (e.getComponent(PlayerAvatarComponent.class) != null) continue;
            crew.releaseTask(e);
            BehaviorTreeComponent bt = e.getComponent(BehaviorTreeComponent.class);
            if (bt != null) bt.tree.resetTask();
        }
    }

    private void closeSeats(Entity ship) {
        List<EvacuateTask> tasks = posted.remove(ship);
        if (tasks != null) for (EvacuateTask t : tasks) t.open = false;
        avatarPath.clear();
    }

    /**
     * Each tick before the crew move: the walk to the pod, again, if the screen cleared it. The game
     * clears the figure's walk from the keys each frame before the world updates, and this system
     * updates after the sim ticks, so in a real window the walk to the pod never moved the figure
     * (round 10, as with {@link CaptainSafety#keepWalking()}). The player's own keys still win.
     */
    public void keepWalking() {
        if (steerX == 0f && steerY == 0f) return;
        Entity avatar = null;
        for (Entity e : crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) avatar = e;
        if (avatar == null) return;
        GridPositionComponent p = avatar.getComponent(GridPositionComponent.class);
        PlayerAvatarComponent walk = avatar.getComponent(PlayerAvatarComponent.class);
        if (!abandoning(p.ship) || !avatar.getComponent(CrewMemberComponent.class).isConscious() || p.walkway != null) {
            steerX = steerY = 0f;
            return;
        }
        if (walk.moveX != 0f || walk.moveY != 0f) return;
        walk.moveX = steerX;
        walk.moveY = steerY;
    }

    /**
     * Walks the player's avatar, if it is aboard {@code ship}, to the nearest pod with a free seat,
     * unless the player is steering it themselves.
     */
    private void steerAvatar(Entity ship, double now) {
        Entity avatar = null;
        for (Entity e : crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) avatar = e;
        if (avatar == null) return;
        GridPositionComponent p = avatar.getComponent(GridPositionComponent.class);
        CrewMemberComponent c = avatar.getComponent(CrewMemberComponent.class);
        PlayerAvatarComponent walk = avatar.getComponent(PlayerAvatarComponent.class);
        if (p.ship != ship || !c.isConscious() || p.walkway != null) return;
        List<Entity> pods = usablePods(ship);
        if (podAt(pods, avatar) != null) {
            if (walk.moveX == steerX && walk.moveY == steerY) walk.moveX = walk.moveY = steerX = steerY = 0f;
            return;
        }
        // The player's own input wins
        if ((walk.moveX != 0f || walk.moveY != 0f) && (walk.moveX != steerX || walk.moveY != steerY)) {
            repathAt = Double.NEGATIVE_INFINITY;
            return;
        }
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (now >= repathAt || avatarStep >= avatarPath.size) {
            repathAt = now + 1.0;
            avatarStep = 0;
            if (!crew.pathfinder(grid).nearest(p.tileX(), p.tileY(), (x, y) -> {
                for (Entity pod : pods) if (pod.getComponent(SubsystemComponent.class).covers(x, y)) return true;
                return false;
            }, avatarPath)) {
                avatarPath.clear();
            }
        }
        while (avatarStep < avatarPath.size) {
            GridNode n = avatarPath.get(avatarStep);
            float dx = n.x + 0.5f - p.x, dy = n.y + 0.5f - p.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.15f) {
                avatarStep++;
                continue;
            }
            steerX = dx / d;
            steerY = dy / d;
            walk.moveX = steerX;
            walk.moveY = steerY;
            return;
        }
        walk.moveX = walk.moveY = steerX = steerY = 0f;
    }
}
