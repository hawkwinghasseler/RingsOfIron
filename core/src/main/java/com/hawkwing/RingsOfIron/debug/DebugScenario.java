package com.hawkwing.RingsOfIron.debug;

import com.ringsofiron.career.NewGame;

/**
 * A test setup the debug menu (F3) loads into the real game: a fresh {@link com.hawkwing.RingsOfIron.game.GameScenario}
 * is started as for a new career, and then, instead of docking at the sponsor's home, {@link #setUp} turns it into
 * the scenario (moves the player, spawns rocks or boarders, lights fires, adds overlays and buttons). The game's own
 * screen, HUD, console and keys run it from there: a scenario only builds the situation.
 *
 * <p>Register one in {@link DebugScenarios#BUILT_IN} with an id, a name and a line of description. A new instance is
 * made for each load, so a scenario may keep state in fields. Saves are not autosaved while a scenario runs.
 */
public interface DebugScenario {
    /** The seed and sponsor of the world it is set up in; the designed start unless the scenario wants another. */
    default NewGame newGame() {
        return NewGame.designed();
    }

    /**
     * Once, after the game has made its world, HUD and console, before the first frame. {@code setup} has the
     * world and the career, and is where the scenario's overlays, buttons and console commands are added.
     */
    void setUp(ScenarioSetup setup);

    /** Every frame the world runs, after it has run; not while the game or the console has it stopped. */
    default void update(float delta) {
    }

    /** When the game the scenario was loaded into is thrown away. */
    default void dispose() {
    }
}
