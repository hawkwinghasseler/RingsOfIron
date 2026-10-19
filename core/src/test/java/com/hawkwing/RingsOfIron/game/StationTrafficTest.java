package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Freighters that come into the bubble at a station with no port free for them. */
class StationTrafficTest {
    GameWorld world;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (world != null) world.dispose();
    }

    @Test
    void freightersWithNoFreePortWaitApartNotPiledOnEachOther() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        double hold = InteriorGridData.cargoBayVolume(game.content.defs, GameScenario.FREIGHTER_SHIP);
        List<String> extra = new ArrayList<>();
        // Far more than Highport has Medium ports
        for (int i = 0; i < 12; i++) extra.add(world.macroSim.addFreighter("Waiting " + i, hold, 20_000, game.highport));
        for (int i = 0; i < 30; i++) world.update(1f / 60f);

        List<Vector2> waiting = new ArrayList<>();
        for (String uuid : extra) {
            Entity e = world.engine.getEntity(uuid);
            PhysicsBodyComponent pb = e == null ? null : e.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null || world.docking.dockedTo(e) != null) continue;
            waiting.add(PhysicsManager.getPosition(pb.body, new Vector2()));
        }
        assertTrue(waiting.size() >= 6, "most found no port and wait outside: " + waiting.size());
        for (int i = 0; i < waiting.size(); i++) {
            for (int j = i + 1; j < waiting.size(); j++) {
                assertTrue(waiting.get(i).dst(waiting.get(j)) > StationTraffic.NOSE_X * 2,
                        "freighters " + i + " and " + j + " overlap at " + waiting.get(i));
            }
        }
    }
}
