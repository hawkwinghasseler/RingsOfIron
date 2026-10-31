package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.career.GuideProgress;
import com.ringsofiron.shipops.StationYard;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.damage.IonStun;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.coreengine.input.Action;

import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.uihud.HintRegistry;
import java.util.function.Function;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * A short scripted first play over the starting scenario, one objective at a time: the ship starts
 * docked at Highport; take the bounty on the raider from the mission board, undock, fly clear, beat
 * the raider (it turns on you once you are clear), latch on and board it, come back and dock, and
 * have the yard repair the damage, look back over the notice log, and plan a transfer to a station
 * beyond Highport. Each objective is met by playing, not by a key: the script only
 * watches the world and says what to do next. Docking again while the raider still fights counts as
 * getting away from it; docking again before boarding it skips the boarding, and undocking without
 * a contract skips taking one.
 *
 * <p>Call {@link #update} once a frame; it returns a line to show when the objective changes.
 */
public final class FirstPlay {
    /** The objectives, in order. */
    public enum Step {
        ACCEPT("{MISSIONS} opens Highport's mission board: take the bounty on the raider outside"),
        UNDOCK("Undock from Highport: {UNDOCK} lets go of the port"),
        FLY_CLEAR("Fly clear of the station, 2 km out: {THRUST}/{REVERSE} thrust, {TURN_LEFT}/{TURN_RIGHT} turn, "
                + "{ROTATION_ASSIST} rotation assist"),
        FIGHT("A raider is coming for you. Your crew's guns open up on it in range; {NEXT_TARGET} locks it, {FIRE} "
                + "fires the guns at once, {MISSILE} a salvo of missiles, "
                + "{DECOY} a decoy. Knock out its guns or its reactor until it gives up, or run back to Highport"),
        BOARD("The raider is beaten. Pull alongside it and press {ARM_PORT} to latch on, then {BOARD}: send a "
                + "boarding party, and strip what you want once it is taken"),
        DOCK("Back to Highport and dock: {NEXT_PORT} picks the port, {ARM_PORT} arms it and asks for clearance"),
        REPAIR("Open the yard with {YARD} and order repairs"),
        NOTICES("{LOG} opens the notice log: the fight, the bounty and the yard's work are all there"),
        PLAN("Plan a trip: {NAV_TARGET} picks a navigation target (Shackleton Ice, at the Moon, say), {PLAN_TO_TARGET} "
                + "plans the transfer, {ACCEPT_PLAN} accepts it and {DISCARD_PLAN} discards it"),
        DONE("That's the loop: work from the board ({MISSIONS}) pays and earns rank. Trade or sell salvage with "
                + "{TRADE}, refit at the yard ({YARD}), or fly on");

        public final String objective;

        Step(String objective) {
            this.objective = objective;
        }
    }

    /** How far from Highport counts as clear of it, meters. */
    public static final float CLEAR_DISTANCE = 2_000f;

    private final GameContext ctx;
    private final DockingSystem docking;
    private final BurnPlanner burns;
    private final Navigation nav;
    private final ContractBoard contracts;
    private final StationYard yard;
    private final Boarding boarding;
    private final GuideProgress guides;
    private final GameScenario game;
    private Step step = Step.ACCEPT;
    private boolean logOpened;
    private final Vector2 a = new Vector2(), b = new Vector2();

    private FirstPlay(GameContext ctx, DockingSystem docking, BurnPlanner burns, Navigation nav, ContractBoard contracts,
                      StationYard yard, Boarding boarding, GuideProgress guides, GameScenario game) {
        this.ctx = ctx;
        this.docking = docking;
        this.burns = burns;
        this.nav = nav;
        this.contracts = contracts;
        this.yard = yard;
        this.boarding = boarding;
        this.guides = guides;
        this.game = game;
    }

    private static FirstPlay in(GameWorld world, GameScenario game) {
        return new FirstPlay(world.context, world.docking, world.burns, world.nav, world.contracts, world.yard, world.boarding,
                world.guides, game);
    }

    /** Starts the first play on a new game: the player's ship latched to a free port of its size at Highport. */
    public static FirstPlay start(GameWorld world, GameScenario game) {
        game.beginCareer();
        FirstPlay f = in(world, game);
        world.guides.firstPlayStep = f.step.name();
        return f;
    }

    /** The first play a loaded game was saved in, at its objective then; null when it was not one. */
    public static FirstPlay resume(GameWorld world, GameScenario game) {
        String saved = world.guides.firstPlayStep;
        if (saved == null) return null;
        FirstPlay f = in(world, game);
        try {
            f.step = Step.valueOf(saved);
        } catch (IllegalArgumentException e) {
            f.step = Step.ACCEPT;
        }
        world.guides.firstPlayStep = f.step.name();
        return f;
    }

    /** Latches the player's first port onto a free port of the same size at Highport, at once. */
    static boolean dockAtHighport(GameWorld world, GameScenario game) {
        return game.dockAtHighport();
    }

    /** Called when the player opens the notice log. */
    public void logOpened() {
        logOpened = true;
    }

    public Step step() {
        return step;
    }

    /** "2/7: ...", the objective now, with the default keys. */
    public String objective() {
        return objective(Action::defaultKeyName);
    }

    /** "2/7: ...", the objective now, naming the player's keys. */
    public String objective(Function<Action, String> keyName) {
        String text = HintRegistry.withKeys(step.objective, keyName);
        if (step == Step.DONE) return text;
        return (step.ordinal() + 1) + "/" + Step.DONE.ordinal() + ": " + text;
    }

    /** Moves on when the objective is met; returns the next one to show, or null when nothing changed. */
    public String update() {
        Step next = next();
        if (next == step) return null;
        step = next;
        guides.firstPlayStep = step.name();
        if (step == Step.FIGHT) game.provokeRaider();
        return objective();
    }

    private Step next() {
        Entity highport = ctx.engine.getEntity(game.highport);
        boolean docked = highport != null && docking.dockedTo(game.player) == highport;
        switch (step) {
            case ACCEPT:
                if (!docked) return Step.FLY_CLEAR;
                return contracts.active().isEmpty() ? step : Step.UNDOCK;
            case UNDOCK:
                return docked ? step : Step.FLY_CLEAR;
            case FLY_CLEAR:
                return distance(game.player, highport) > CLEAR_DISTANCE ? Step.FIGHT : step;
            case FIGHT:
                if (docked) return Step.REPAIR;
                return raiderBeaten() ? Step.BOARD : step;
            case BOARD:
                if (docked) return Step.REPAIR;
                if (game.raider == null || ctx.engine.getEcsEngine().getEntities().indexOf(game.raider, true) < 0
                        || boarding.takenBy(game.raider, game.player)) return Step.DOCK;
                return step;
            case DOCK:
                return docked ? Step.REPAIR : step;
            case REPAIR:
                if (!docked) return step;
                return yard.repairing(game.player) || yard.damage(game.player).isEmpty() ? Step.NOTICES : step;
            case NOTICES:
                return logOpened ? Step.PLAN : step;
            case PLAN:
                ManeuverNodeComponent nodes = burns.nodes();
                return nav.transfer.proposal() != null || (nodes != null && nodes.nodes.size > 0) ? Step.DONE : step;
            default:
                return step;
        }
    }

    /** True once the raider can no longer fight: gone, disabled or surrendered, its reactor down, or every gun silenced. */
    boolean raiderBeaten() {
        return beaten(ctx.engine, game.raider);
    }

    /**
     * True once {@code ship} can no longer fight: gone, disabled or surrendered, its reactor down, or every gun silenced.
     * Contracts' bounties use it too. A ship an ion hit has knocked out is not beaten while a stun lasts (it comes back
     * by itself), unless it has surrendered, been abandoned or wrecked, nor while its crew are about to reset a short.
     */
    public static boolean beaten(GameWorld world, Entity ship) {
        return beaten(world.engine, ship);
    }

    /** {@link #beaten(GameWorld, Entity)}, in {@code engine}'s world. */
    public static boolean beaten(GameEngine engine, Entity ship) {
        if (ship == null || engine.getEcsEngine().getEntities().indexOf(ship, true) < 0) return true;
        DisabledComponent.Reason why = ShipDisablement.reason(ship);
        if (why != null && why.sticky()) return true;
        // A stun passes, so a ship dark only for it isn't beaten; a broken hull or a downed crew is,
        // stunned or not (round 8: the frigate broken at the raid wasn't, so the base's ion raider kept
        // it in the fight and the base shot it to a wreck)
        if (why != null && why != DisabledComponent.Reason.NO_POWER) return true;
        if (IonStun.anyStunned(ship)) return false;
        if (why != null) return true;
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (DamageStates.awaitingReset(ship.getComponent(ShipGridComponent.class), registry)) return false;
        if (registry != null && !powered(registry)) return true;
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return true;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def == null) continue;
            SubsystemComponent sub = m.subsystem == null ? null : m.subsystem.getComponent(SubsystemComponent.class);
            if (sub == null || (!sub.isDisabled() && sub.isOperational)) return false;
        }
        return true;
    }

    /**
     * True for a named ace the player has beaten once ({@link PirateAces#dropPart}). Its crew may get it
     * flying again out on the rails, so {@link #beaten} can say no days later, but contracts and
     * operations are done with it: no recon or bounty names it again (round 6: Hunt the Hunters sent
     * the frigate to find Iron Dagny, beaten a week before, and an operation's bounty on it never closed).
     */
    public static boolean aceBeaten(Entity ship) {
        HostileComponent h = ship == null ? null : ship.getComponent(HostileComponent.class);
        return h != null && h.ace && h.partDropped;
    }

    private static boolean powered(ShipSubsystemRegistryComponent registry) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) return true;
        }
        return false;
    }

    private float distance(Entity ship, Entity station) {
        Body s = bodyOf(ship), t = station == null ? null : docking.bodyOf(station);
        if (s == null || t == null) return 0f;
        return PhysicsManager.getPosition(s, a).dst(PhysicsManager.getPosition(t, b));
    }

    private static Body bodyOf(Entity ship) {
        PhysicsBodyComponent body = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        return body == null ? null : body.body;
    }
}
