package com.hawkwing.RingsOfIron.debug;

import com.hawkwing.RingsOfIron.game.InteriorScene;

/**
 * The old interior demo, in the game: the player's ship alone in orbit and pinned, on deck with the
 * crew, to hurt in every way the game can ({@link InteriorScene}). The debug menu's scenario section
 * arms a tool (wound, weapon, blast, ram, fire, breach, short, damage, spawn or remove crew) for a
 * left click on the deck, cycles the hulls with the crew walked across, and sets an attacker on the
 * ship; the overlay keeps the account of everyone's wounds.
 */
final class InteriorScenario implements DebugScenario {
    private InteriorScene scene;

    @Override
    public void setUp(ScenarioSetup setup) {
        scene = new InteriorScene(setup.game, null, setup.tools());
        InteriorControls controls = new InteriorControls(setup, scene);
        controls.addActions();
        controls.onDeck();
        controls.say(scene.name(scene.hull) + ": pick a tool in the debug menu (F3), then click the deck");
        setup.notice("Interior: F3 for the tools, the hulls and an attacker");
    }

    @Override
    public void update(float delta) {
        scene.update();
    }

    @Override
    public void dispose() {
        if (scene != null) scene.dispose(false);
    }
}
