package com.hawkwing.RingsOfIron.game;

import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * A light walkthrough for a new game, short of {@link FirstPlay}: open the mission board, take a
 * job, then a last word on undocking and the map, and it stops. It only watches the game and says
 * what to do next; it can be turned off in the settings. Its step is saved with the game
 * ({@link GuideProgress#openingStep}).
 */
public final class OpeningGuide {
    public enum Step {
        OPEN_BOARD("You are docked at your home station. {MISSIONS} opens its mission board"),
        TAKE_JOB("Pick a job on the board and take it: the terms say where it goes and what it pays"),
        DONE("Job taken. {UNDOCK} undocks; at the helm {THRUST}/{REVERSE} thrust and {TURN_LEFT}/{TURN_RIGHT} turn. "
                + "{MAP} opens the map, which marks where the job goes. {HELP} lists every key");

        public final String text;

        Step(String text) {
            this.text = text;
        }
    }

    private final ContractBoard contracts;
    private final GuideProgress guides;
    private final BooleanSupplier boardOpen;
    private Step step;

    /**
     * @param boardOpen whether the mission board window is open
     * @param step      where to start: {@link Step#OPEN_BOARD} for a new game, or the saved step
     */
    public OpeningGuide(ContractBoard contracts, GuideProgress guides, BooleanSupplier boardOpen, Step step) {
        this.contracts = contracts;
        this.guides = guides;
        this.boardOpen = boardOpen;
        this.step = step;
        guides.openingStep = step.name();
    }

    /** The walkthrough a loaded game was saved in, or null when it had finished or was off. */
    public static OpeningGuide resume(GameWorld world, BooleanSupplier boardOpen) {
        return resume(world, boardOpen, true);
    }

    /**
     * As {@link #resume(GameWorld, BooleanSupplier)}; with {@code wanted} false (turned off in the
     * settings) the saved step is dropped, so later saves don't carry it on.
     */
    public static OpeningGuide resume(GameWorld world, BooleanSupplier boardOpen, boolean wanted) {
        String saved = world.guides.openingStep;
        if (!wanted) world.guides.openingStep = null;
        if (saved == null || !wanted) return null;
        try {
            return new OpeningGuide(world.contracts, world.guides, boardOpen, Step.valueOf(saved));
        } catch (IllegalArgumentException e) {
            world.guides.openingStep = null;
            return null;
        }
    }

    public Step step() {
        return step;
    }

    public boolean finished() {
        return step == Step.DONE;
    }

    /** What to do now, naming the player's keys. */
    public String text(Function<Action, String> keyName) {
        return HintRegistry.withKeys(step.text, keyName);
    }

    /** Moves on when the step is met; true when it did, and the new step should be shown. */
    public boolean update() {
        Step next = step;
        boolean holding = !contracts.active().isEmpty();
        switch (step) {
            case OPEN_BOARD:
                if (holding) next = Step.DONE;
                else if (boardOpen.getAsBoolean()) next = Step.TAKE_JOB;
                break;
            case TAKE_JOB:
                if (holding) next = Step.DONE;
                break;
            default:
                break;
        }
        if (next == step) return false;
        step = next;
        // Once the last word is said there is nothing left to save
        guides.openingStep = step == Step.DONE ? null : step.name();
        return true;
    }

    /** Stops it: turned off in the settings, or the player left the station first. */
    public void stop() {
        step = Step.DONE;
        guides.openingStep = null;
    }
}
