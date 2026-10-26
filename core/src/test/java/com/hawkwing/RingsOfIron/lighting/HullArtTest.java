package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** A craft wears its own def's hull, even on an entity the engine's pool handed out before. */
class HullArtTest {

    @Test
    void aRecycledEntityWearsItsNewShipsHullNotTheOldOnes() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld(new GameWorld.Modules());
        try {
            ModContext ctx = world.loadContent(assets, assets.resolve("no-mods-here"));
            HullArt art = new HullArt(ctx.defs);

            Entity freighter = world.engine.createEntity();
            freighter.add(new DefRefComponent("ship", "freighter_hauler"));
            world.engine.addEntity(freighter);
            assertEquals("freighter", art.chassisOf(freighter));
            world.engine.removeEntity(freighter);

            Entity corvette = world.engine.createEntity();
            assertSame(freighter, corvette, "the pool hands the same entity out again");
            corvette.add(new DefRefComponent("ship", "corvette_patrol"));
            world.engine.addEntity(corvette);
            assertEquals("corvette", art.chassisOf(corvette));
        } finally {
            world.dispose();
        }
    }
}
