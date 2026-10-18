package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

/** Crew run on sim ticks, so physics warp speeds them up along with the hazards. */
class CrewWarpTest {

    @Test
    void burnsAndWalkingKeepPaceWithPhysicsWarp() {
        float[] atOneX = run(1, 600);
        float[] atTenX = run(10, 60);
        assertTrue(atOneX[0] < CrewDef.forRole(CrewRole.MARINE).maxHealth, "the hot tile hurts");
        assertTrue(atOneX[1] > 8f, "the walker got going");
        assertEquals(atOneX[0], atTenX[0], 0.5f, "the same burns over the same sim time");
        assertEquals(atOneX[1], atTenX[1], 0.05f, "the same walk over the same sim time");
    }

    /** Health of a crewman on a hot tile and x of one walking away, after {@code frames} 60 Hz frames. */
    private static float[] run(int warp, int frames) {
        GameEngine game = new GameEngine();
        CrewService crew = CrewFactory.install(game.getEcsEngine());
        Entity ship = game.createEntityWithUuid();
        ShipGridComponent grid = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "##################################",
                "#................................#",
                "#................................#",
                "##################################")).grid;
        CompartmentBuilder.rebuildIfChanged(grid);
        ship.add(grid);
        game.addEntity(ship);
        // Hot enough to hurt, with no hazard system to cool it
        grid.getTile(1, 1).temperature = 340f;
        Entity burnt = CrewFactory.spawn(crew, ship, "Burnt", CrewRole.MARINE, 0, 1, 1);
        Entity walker = CrewFactory.spawn(crew, ship, "Walker", CrewRole.MARINE, 0, 2, 2);
        // Keep the burnt one in place and send the other down the corridor
        burnt.remove(BehaviorTreeComponent.class);
        walker.remove(BehaviorTreeComponent.class);
        Array<GridNode> path = new Array<>();
        assertTrue(crew.pathfinder(grid).findPath(2, 2, 32, 2, path));
        crew.position(walker).setPath(path);

        game.setPhysicsWarp(warp);
        for (int i = 0; i < frames; i++) game.update(1f / 60f);
        return new float[]{crew.crew(burnt).health, crew.position(walker).x};
    }
}
