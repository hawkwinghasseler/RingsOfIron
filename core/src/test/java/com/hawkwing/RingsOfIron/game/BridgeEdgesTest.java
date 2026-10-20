package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The bridge at its edges: a helm shot away, a wrecked helm, sitting when hurt, the weapons seat saved, and view.anywhere. */
class BridgeEdgesTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    final List<String> unseated = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.bridge.unseated.add((signal, why) -> unseated.add(why));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) world.update(FRAME);
    }

    private Body body() {
        return game.player.getComponent(PhysicsBodyComponent.class).body;
    }

    @Test
    void aHelmShotAwayStandsThePlayerUpAndLeavesNoHelm() {
        assertEquals(SubsystemType.HELM, world.bridge.seat());
        SubsystemComponent helm = Bridge.console(game.player, SubsystemType.HELM);
        helm.health = 0f;
        world.update(FRAME);
        assertNull(world.bridge.seat());
        assertEquals(List.of("The helm is destroyed"), unseated);
        assertEquals("no helm", world.bridge.helmFault(game.player));
        assertNotNull(world.bridge.sit(SubsystemType.HELM), "nothing left to sit at");
    }

    @Test
    void aWreckedHelmCantBeFlown() {
        SubsystemComponent helm = Bridge.console(game.player, SubsystemType.HELM);
        helm.health = SubsystemComponent.DISABLE_THRESHOLD * 0.5f;
        assertEquals("the helm is wrecked", world.bridge.helmFault(game.player));
        Body body = body();
        Vector2 v0 = PhysicsManager.getVelocity(body, new Vector2()).cpy();
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 2; i++) {
            controls.forward = 1f;
            world.update(FRAME);
        }
        assertEquals(v0.len(), PhysicsManager.getVelocity(body, new Vector2()).len(), 0.5f, "the engine doesn't answer");
    }

    @Test
    void anAvatarKnockedOutCantSitDown() {
        world.bridge.stand();
        CrewMemberComponent you = world.crew.crew(game.avatar);
        world.crew.applyDamage(game.avatar, you.health * 0.95f, CrewDamageSink.DamageCause.BLAST);
        assertFalse(you.isConscious());
        assertEquals("You're in no state to", world.bridge.sit());
        assertNull(world.bridge.seat());
    }

    @Test
    void theWeaponsSeatIsSaved() {
        world.bridge.stand();
        GridPositionComponent pos = game.avatar.getComponent(GridPositionComponent.class);
        SubsystemComponent weapons = Bridge.console(game.player, SubsystemType.WEAPONS);
        pos.setTile(weapons.footprint.originX - 1, weapons.footprint.originY);
        assertNull(world.bridge.sit(SubsystemType.WEAPONS));
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        assertEquals(SubsystemType.WEAPONS, world.bridge.seat(), "at the guns, as saved");
        assertTrue(world.bridge.playerAtWeapons());
        assertFalse(world.bridge.playerAtHelm(), "and not flying");
    }

    @Test
    void viewAnywhereLetsThePlayerFlyFromTheDeck() {
        world.bridge.playerAlwaysAtHelm = true;
        world.bridge.stand();
        game.avatar.getComponent(GridPositionComponent.class).setTile(2, 2);
        assertTrue(world.bridge.playerAtHelm(), "always at the helm");
        Body body = body();
        Vector2 v0 = PhysicsManager.getVelocity(body, new Vector2()).cpy();
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 3; i++) {
            controls.forward = 1f;
            world.update(FRAME);
        }
        assertTrue(PhysicsManager.getVelocity(body, new Vector2()).dst(v0) > 5f, "the main engine fires");
    }
}
