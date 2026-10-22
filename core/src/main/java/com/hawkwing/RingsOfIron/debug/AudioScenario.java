package com.hawkwing.RingsOfIron.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.AudioLab;
import com.hawkwing.RingsOfIron.game.WorldDebug;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The audio demo as a scenario: the avatar aboard the audio demo ship, whose rooms show how walls,
 * doors, distance and thin air change what is heard (see {@link AudioLab}). The game's own view, keys
 * and audio run it: walk with WASD, V between the deck and the outside (where interior sounds are
 * heard as from the helm). The debug menu adds buttons to light a fire, vent the lab, switch the
 * reactor off, seal the nearest door, fire a laser outside, hit the hull, and cycle through every
 * ship and station hull; the "audio" overlay marks each sound aboard, colored from clear (green) to
 * muffled (red), with its walls, cutoff and volume.
 */
final class AudioScenario implements DebugScenario {
    private AudioLab lab;
    private ScenarioSetup setup;
    private final List<AudioEmitterComponent> sounds = new ArrayList<>();

    @Override
    public void setUp(ScenarioSetup setup) {
        this.setup = setup;
        WorldDebug tools = setup.tools();
        lab = new AudioLab(setup.world, setup.game, tools);
        String aboard = lab.show(AudioLab.DEMO_HULL);
        setup.playerShipChanged();
        setup.deckView();

        setup.action("Next hull", "Aboard the next ship or station hull", () -> hull(1));
        setup.action("Previous hull", "Aboard the hull before", () -> hull(-1));
        setup.action("Fire", "Light a fire (the quarters on the demo hull), or put it out", () -> setup.notice(lab.toggleFire()));
        setup.action("Vent", "Hole the hull (the vacuum lab on the demo hull), or patch it and refill the air",
                () -> setup.notice(lab.toggleVent()));
        setup.action("Reactor", "Switch the reactors off, or back on: their hum, and power to doors and lights",
                () -> setup.notice(lab.toggleReactor()));
        setup.action("Door", "Seal the door nearest the avatar, or open it", () -> setup.notice(lab.toggleNearestDoor()));
        setup.action("Laser outside", "A laser shot just off the hull: an exterior sound", lab::laser);
        setup.action("Hull hit", "Something strikes the hull: heard through the frame", lab::hullHit);
        setup.overlay("audio", "Each sound aboard, green when heard clear to red when muffled, with walls, cutoff and volume",
                new Readout(), true);
        setup.notice(aboard + ". F3 for the debug menu: fire, vent, reactor, doors and other hulls");
    }

    private void hull(int step) {
        setup.notice(lab.cycle(step));
        setup.playerShipChanged();
        setup.deckView();
    }

    @Override
    public void update(float delta) {
        lab.update();
    }

    /** Each sound aboard: a disc at it and a line to the avatar, colored by how muffled it is, and its numbers. */
    private final class Readout implements DebugOverlay {
        private final Vector2 at = new Vector2(), you = new Vector2(), ui = new Vector2();

        @Override
        public void drawWorld(OverlayView view) {
            if (!view.deckView) return;
            lab.soundsAboard(sounds);
            Body body = setup.world.docking.bodyOf(lab.craft());
            if (body == null) return;
            boolean youKnown = lab.avatarLocal(you) != null;
            if (youKnown) WorldDebug.toWorld(body, you.x, you.y, you);
            float r = lab.grid().tileSize;
            Gdx.gl.glEnable(GL20.GL_BLEND);
            view.shapes.begin(ShapeRenderer.ShapeType.Line);
            for (AudioEmitterComponent em : sounds) {
                if (!youKnown) break;
                WorldDebug.toWorld(body, em.localX, em.localY, at);
                color(view.shapes, em, 0.8f);
                view.shapes.line(at.x, at.y, you.x, you.y);
            }
            view.shapes.end();
            view.shapes.begin(ShapeRenderer.ShapeType.Filled);
            float pulse = 0.12f * MathUtils.sin(Gdx.graphics.getFrameId() * 0.2f);
            for (AudioEmitterComponent em : sounds) {
                WorldDebug.toWorld(body, em.localX, em.localY, at);
                color(view.shapes, em, 0.45f);
                view.shapes.circle(at.x, at.y, (0.4f + pulse) * r, 20);
            }
            view.shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        @Override
        public void drawScreen(OverlayView view) {
            if (!view.deckView) return;
            Body body = setup.world.docking.bodyOf(lab.craft());
            if (body == null) return;
            lab.soundsAboard(sounds);
            view.batch.begin();
            for (AudioEmitterComponent em : sounds) {
                WorldDebug.toWorld(body, em.localX, em.localY, at);
                view.worldToUi(at.x, at.y, ui);
                view.font.setColor(Color.WHITE);
                view.font.draw(view.batch, AudioLab.soundName(em), ui.x + 12f, ui.y + 30f);
                view.font.setColor(Color.LIGHT_GRAY);
                view.font.draw(view.batch, describe(em), ui.x + 12f, ui.y + 14f);
            }
            view.font.setColor(Color.WHITE);
            view.batch.end();
        }
    }

    /** Green when clear, through amber, to red when fully muffled. */
    private static void color(ShapeRenderer shapes, AudioEmitterComponent em, float alpha) {
        float o = em.occlusion;
        shapes.setColor(Math.min(1f, 0.2f + 1.6f * o), Math.min(1f, 1.6f * (1f - o)), 0.2f, alpha);
    }

    /** Walls between, the rooms' loss, the air, and what the mix makes of them. */
    static String describe(AudioEmitterComponent em) {
        StringBuilder s = new StringBuilder(em.wallCount == 1 ? "1 wall" : em.wallCount + " walls");
        if (em.roomLossDb > 0f && !Float.isInfinite(em.roomLossDb)) s.append(String.format(Locale.ROOT, ", -%.0f dB", em.roomLossDb));
        if (em.pressure < 0.99f) s.append(String.format(Locale.ROOT, ", air %.0f%%", 100f * em.pressure));
        if (em.instance instanceof SimulatedInstance) {
            SimulatedInstance sim = (SimulatedInstance) em.instance;
            s.append(String.format(Locale.ROOT, "  %.0f Hz  vol %.1f%%", sim.mix.cutoffHz, 100f * sim.mix.gain));
        }
        return s.toString();
    }
}
