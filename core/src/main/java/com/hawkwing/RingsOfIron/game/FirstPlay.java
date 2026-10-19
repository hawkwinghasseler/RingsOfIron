package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

/**
 * A short scripted first play over the starting scenario, one objective at a time: the ship starts
 * docked at Highport; undock, fly clear, beat off the raider (it turns on you once you are clear),
 * come back and dock, and have the yard repair the damage. Each objective is met by playing, not by
 * a key: the script only watches the world and says what to do next. Docking again while the
 * raider still fights counts as getting away from it.
 *
 * <p>Call {@link #update} once a frame; it returns a line to show when the objective changes.
 */
public final class FirstPlay {
    /** The objectives, in order. */
    public enum Step {
        UNDOCK("Undock from Highport: U lets go of the port"),
        FLY_CLEAR("Fly clear of the station, 2 km out: W/S thrust, A/D turn, X rotation assist"),
        FIGHT("A raider is coming for you. Tab locks it, Space fires the guns, R a missile, F a decoy. "
                + "Knock out its guns or its reactor, or run back to Highport"),
        DOCK("Back to Highport and dock: H picks the port, G arms it and asks for clearance"),
        REPAIR("Open the yard with Y and order repairs"),
        DONE("That's the loop. Trade with T, rebuild your decks from the yard (Y), or fly on");

        public final String objective;

        Step(String objective) {
            this.objective = objective;
        }
    }

    /** How far from Highport counts as clear of it, meters. */
    public static final float CLEAR_DISTANCE = 2_000f;

    private final GameWorld world;
    private final GameScenario game;
    private Step step = Step.UNDOCK;
    private final Vector2 a = new Vector2(), b = new Vector2();

    private FirstPlay(GameWorld world, GameScenario game) {
        this.world = world;
        this.game = game;
    }

    /** Starts the first play on a new game: the player's ship latched to a free port of its size at Highport. */
    public static FirstPlay start(GameWorld world, GameScenario game) {
        dockAtHighport(world, game);
        return new FirstPlay(world, game);
    }

    /** Latches the player's first port onto a free port of the same size at Highport, at once. */
    static boolean dockAtHighport(GameWorld world, GameScenario game) {
        Entity highport = world.engine.getEntity(game.highport);
        DockingPortsComponent ports = highport.getComponent(DockingPortsComponent.class);
        DockingPortsComponent own = game.player.getComponent(DockingPortsComponent.class);
        if (ports == null || own == null || own.ports.length == 0) return false;
        MountFootprint.MountSize size = own.ports[0].size;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.size == size && p.state != DockingPort.State.DOCKED) {
                world.docking.dockInPlace(game.player, 0, highport, i);
                return true;
            }
        }
        return false;
    }

    public Step step() {
        return step;
    }

    /** "2/5: ...", the objective now, for the screen. */
    public String objective() {
        if (step == Step.DONE) return step.objective;
        return (step.ordinal() + 1) + "/" + Step.DONE.ordinal() + ": " + step.objective;
    }

    /** Moves on when the objective is met; returns the next one to show, or null when nothing changed. */
    public String update() {
        Step next = next();
        if (next == step) return null;
        step = next;
        if (step == Step.FIGHT) game.provokeRaider();
        return objective();
    }

    private Step next() {
        Entity highport = world.engine.getEntity(game.highport);
        boolean docked = highport != null && world.docking.dockedTo(game.player) == highport;
        switch (step) {
            case UNDOCK:
                return docked ? step : Step.FLY_CLEAR;
            case FLY_CLEAR:
                return distance(game.player, highport) > CLEAR_DISTANCE ? Step.FIGHT : step;
            case FIGHT:
                if (docked) return Step.REPAIR;
                return raiderBeaten() ? Step.DOCK : step;
            case DOCK:
                return docked ? Step.REPAIR : step;
            case REPAIR:
                if (!docked) return step;
                return world.yard.repairing(game.player) || world.yard.damage(game.player).isEmpty() ? Step.DONE : step;
            default:
                return step;
        }
    }

    /** True once the raider can no longer fight: gone, its reactor down, or every gun silenced. */
    boolean raiderBeaten() {
        Entity raider = game.raider;
        if (raider == null || world.engine.getEcsEngine().getEntities().indexOf(raider, true) < 0) return true;
        ShipSubsystemRegistryComponent registry = raider.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null && !powered(registry)) return true;
        WeaponsComponent weapons = raider.getComponent(WeaponsComponent.class);
        if (weapons == null) return true;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def == null) continue;
            SubsystemComponent sub = m.subsystem == null ? null : m.subsystem.getComponent(SubsystemComponent.class);
            if (sub == null || (!sub.isDisabled() && sub.isOperational)) return false;
        }
        return true;
    }

    private static boolean powered(ShipSubsystemRegistryComponent registry) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) return true;
        }
        return false;
    }

    private float distance(Entity ship, Entity station) {
        Body s = bodyOf(ship), t = station == null ? null : world.docking.bodyOf(station);
        if (s == null || t == null) return 0f;
        return PhysicsManager.getPosition(s, a).dst(PhysicsManager.getPosition(t, b));
    }

    private static Body bodyOf(Entity ship) {
        PhysicsBodyComponent body = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        return body == null ? null : body.body;
    }
}
