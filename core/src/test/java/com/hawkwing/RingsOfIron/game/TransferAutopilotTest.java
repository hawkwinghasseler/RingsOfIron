package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The transfer autopilot on the Moon trip: from open space off Highport, one key plans the
 * transfer to the lunar mine, and the autopilot warps from burn to burn, takes up the capture in the
 * Moon's sphere, warps to arrival and hands the ship to the helm, which docks there. Nobody touches
 * the warp keys; a hold (something coming close) is answered as a player would, by going on.
 */
class TransferAutopilotTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.bridge.stand();
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "a crew member takes the helm");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Tag("slow")
    @Test
    void theAutopilotFliesTheMoonTripAndDocks() {
        String mine = game.lunarMine;
        world.nav.target.select(mine);
        String said = world.nav.autopilot.toggle();
        assertTrue(said.startsWith("Autopilot: flying the transfer to"), said);
        assertTrue(world.burns.nodes().nodes.size > 0, "the plan was accepted");
        double t0 = world.engine.getSimSeconds();
        List<String> holds = new ArrayList<>();
        int frames = 0;
        boolean arrived = false;
        while (frames++ < 200_000 && world.getCrash() == null) {
            world.update(FRAME);
            AutopilotComponent a = world.nav.autopilot.state();
            if (a != null && a.held) {
                holds.add(a.holdReason);
                world.nav.autopilot.toggle();
            }
            if (!arrived && a == null && world.nav.helm.mode() == HelmOrderComponent.Mode.DOCK) {
                arrived = true;
                // Watching the helm bring it in, sped up
                world.timeWarp.setWarp(10);
            }
            if (world.docking.docked(game.player, world.engine.getEntity(mine))) break;
            if (world.engine.getSimSeconds() - t0 > 14 * 86_400) break;
        }
        world.timeWarp.setWarp(1);
        List<String> notices = world.notices.history().stream().map(n -> n.text).toList();
        assertNull(world.getCrash(), () -> "no crash: " + world.getCrash().bodyName);
        assertTrue(arrived, () -> "arrived and handed to the helm; " + world.nav.autopilot.describe() + " / "
                + world.nav.helm.describe() + "; holds " + holds + "; notices " + notices);
        assertTrue(world.docking.docked(game.player, world.engine.getEntity(mine)),
                () -> "docked at the mine: " + world.nav.helm.describe() + "; notices " + notices);
        double days = (world.engine.getSimSeconds() - t0) / 86_400;
        assertTrue(days > 1 && days < 14, "a few days' trip: " + days);
        assertTrue(notices.stream().anyMatch(n -> n.startsWith("Autopilot: Transfer to")), "the capture was taken up: " + notices);
        ManeuverNodeComponent m = world.burns.nodes();
        assertTrue(m == null || m.nodes.size == 0, "every burn flown");
        assertTrue(holds.size() < 5, "few holds: " + holds);
        System.out.printf("Moon trip on the autopilot: %.1f days, %d frames, holds %s%n", days, frames, holds);
    }

    @Test
    void theAutopilotRefusesWhileDockedAndStopsOnTheKey() {
        world.nav.target.select(game.lunarMine);
        assertTrue(game.dockAtHighport());
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertTrue(world.nav.autopilot.toggle().startsWith("Undock first"));
        assertFalse(world.nav.autopilot.engaged());
        world.docking.undock(game.player);
        for (int i = 0; i < 300; i++) world.update(FRAME);
        assertTrue(world.nav.autopilot.toggle().startsWith("Autopilot: flying"));
        assertTrue(world.nav.autopilot.engaged());
        assertTrue(world.nav.autopilot.toggle().startsWith("Autopilot off"));
        assertFalse(world.nav.autopilot.engaged());
    }
}
