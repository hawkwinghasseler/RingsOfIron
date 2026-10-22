package com.hawkwing.RingsOfIron.debug;

import com.hawkwing.RingsOfIron.game.CrewScene;

/**
 * The old crew demo, in the game: on the deck of the player's corvette as a fire breaks out, the
 * reactor is hit, the hull is breached and boarders come aboard ({@link CrewScene}), to watch the
 * crew answer. Restart plays it again on a fresh corvette; the interior tools work here too, to add
 * more trouble.
 */
final class CrewScenario implements DebugScenario {
    private CrewScene scene;

    @Override
    public void setUp(ScenarioSetup setup) {
        scene = new CrewScene(setup.game, setup.tools());
        InteriorControls controls = new InteriorControls(setup, scene.interior);
        setup.action("Restart drill", "A fresh corvette and crew, and the trouble from the top", () -> {
            scene.restart();
            controls.say("Drill restarted");
        });
        controls.addActions();
        controls.onDeck();
    }

    @Override
    public void update(float delta) {
        scene.update();
    }

    @Override
    public void dispose() {
        if (scene != null) scene.interior.dispose(false);
    }
}
