package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Round 8 part 5: the player is told before their people run out of air. */
class AirWatchTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
        // Out in the black, with no station to shelter aboard
        assertTrue(world.docking.undock(game.player));
        run(60);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** Empties every oxygen tank aboard, as a burst tank does; with {@code reserveToo}, life support's own oxygen as well. */
    private void dry(boolean reserveToo) {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            if (s.type == SubsystemComponent.SubsystemType.TANK && s.fluid == Fluid.OXYGEN) s.contents = 0;
            if (reserveToo && s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) s.reserveUsed = LifeSupportSystem.EMERGENCY_OXYGEN_KG * s.throughput;
        }
    }

    @Test
    void dryTanksAreToldOf() {
        dry(false);
        run(10);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.startsWith(AirWatch.TANKS_DRY)));
    }

    @Test
    void airRunningOutIsToldWithTheTimeLeftBeforeAnyoneGoesDown() {
        dry(true);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        grid.patchKits = 0;
        // Suited, as for battle stations: a breach this size knocks out anyone without a suit at once
        // with four minutes of air left in each, so the warning is due soon
        for (Entity e : world.crewCareer.ownCrew(game.player)) world.crew.crew(e).setSuited(true);
        world.crew.crew(game.avatar).setSuited(true);
        for (Entity e : world.crewCareer.ownCrew(game.player)) world.crew.crew(e).suitAir = 240f;
        world.crew.crew(game.avatar).suitAir = 240f;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && (x + y) % 3 == 0) t.setIntegrity(0f);
            }
        }
        NoticeLog.Notice warned = null;
        for (int i = 0; i < 60 * 60 * 15 && warned == null; i++) {
            world.update(FRAME);
            for (NoticeLog.Notice n : world.notices.history()) if (n.text.startsWith(AirWatch.RUNNING_OUT)) warned = n;
            for (Entity e : world.crewCareer.ownCrew(game.player)) {
                assertTrue(warned != null || world.crew.crew(e).isConscious(), world.crew.crew(e).name + " went down unwarned: "
                        + world.crew.crew(e).activity + ", suited " + world.crew.crew(e).suited + ", suit air " + world.crew.crew(e).suitAir
                        + ", " + world.crew.crew(e).wound + ", t " + i / 60);
            }
        }
        assertNotNull(warned, "warned");
        assertTrue(warned.text.matches(".*has [0-3]:\\d\\d of air left.*"), warned.text);
        long lines = world.notices.history().stream().filter(n -> n.text.startsWith(AirWatch.RUNNING_OUT)).count();
        run(60 * 60);
        assertEquals(lines, world.notices.history().stream().filter(n -> n.text.startsWith(AirWatch.RUNNING_OUT)).count(),
                "one line, revised in place");
    }
}
