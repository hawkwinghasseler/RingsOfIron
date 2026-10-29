package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CameraFollowSystemTest {
    @Test
    void followsAfterPhysicsAndBeforeEveryHudSystem() {
        int follow = CameraFollowSystem.DEFAULT_PRIORITY;
        assertTrue(follow > PhysicsUpdateSystem.DEFAULT_PRIORITY);
        assertTrue(follow > FloatingOriginSystem.DEFAULT_PRIORITY, "after a rebase has moved the ship");
        for (int hud : new int[]{HudRenderSystem.DEFAULT_PRIORITY, WireframeSyncSystem.DEFAULT_PRIORITY,
                InteractPromptSystem.DEFAULT_PRIORITY, FlightReadoutSystem.DEFAULT_PRIORITY,
                RadarSystem.DEFAULT_PRIORITY, NavMarkerSystem.DEFAULT_PRIORITY}) {
            assertTrue(follow < hud, "before the HUD system at " + hud);
        }
    }

    @Test
    void runsTheFollowBeforeTheMarkersArePlaced() {
        List<String> order = new ArrayList<>();
        Engine engine = new Engine();
        engine.addSystem(new EntitySystem(NavMarkerSystem.DEFAULT_PRIORITY) {
            @Override
            public void update(float deltaTime) {
                order.add("markers");
            }
        });
        CameraFollowSystem camera = new CameraFollowSystem();
        camera.setFollow(() -> order.add("camera"));
        engine.addSystem(camera);
        engine.update(1f / 60f);
        assertEquals(List.of("camera", "markers"), order);
    }

    @Test
    void withNothingToFollowItDoesNothing() {
        Engine engine = new Engine();
        engine.addSystem(new CameraFollowSystem());
        assertDoesNotThrow(() -> engine.update(1f / 60f));
    }
}
