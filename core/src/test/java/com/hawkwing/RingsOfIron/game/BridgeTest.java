package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
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

/** Seats at the helm and weapons console, and the helm flying the ship while the player is away from it. */
class BridgeTest {
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

    @Test
    void thePlayerStartsSeatedAtTheHelmWithAHelmsmanAboard() {
        assertEquals(SubsystemType.HELM, world.bridge.seat());
        assertTrue(world.bridge.playerAtHelm());
        assertNotNull(game.player.getComponent(HelmComponent.class));
        boolean helmsman = false;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.getComponent(GridPositionComponent.class).ship == game.player && c.role == CrewRole.CREWMAN
                    && e != game.avatar) helmsman = true;
        }
        assertTrue(helmsman, "a crewman to take the helm");
        run(5f);
        assertEquals(SubsystemType.HELM, world.bridge.seat(), "still seated after the crew move about");
        assertTrue(unseated.isEmpty(), unseated.toString());
    }

    @Test
    void sittingNeedsAConsoleBesideTheAvatar() {
        world.bridge.stand();
        assertNull(world.bridge.seat());
        GridPositionComponent pos = game.avatar.getComponent(GridPositionComponent.class);
        pos.setTile(2, 2);
        assertNotNull(world.bridge.sit(), "nothing to sit at in the engine room");
        assertNull(world.bridge.seat());

        // Beside the weapons console, below the helm: the nearer console
        SubsystemComponent weapons = Bridge.console(game.player, SubsystemType.WEAPONS);
        pos.setTile(weapons.footprint.originX - 1, weapons.footprint.originY);
        assertNull(world.bridge.sit());
        assertEquals(SubsystemType.WEAPONS, world.bridge.seat());
        assertTrue(world.bridge.playerAtWeapons());
        assertFalse(world.bridge.playerAtHelm());
    }

    @Test
    void walkingAwayOrBeingKnockedOutStandsTheAvatarUp() {
        GridPositionComponent pos = game.avatar.getComponent(GridPositionComponent.class);
        pos.setTile(2, 2);
        world.update(FRAME);
        assertNull(world.bridge.seat());
        assertEquals(List.of("You left the helm"), unseated);

        SubsystemComponent helm = Bridge.console(game.player, SubsystemType.HELM);
        pos.setTile(helm.footprint.originX - 1, helm.footprint.originY);
        assertNull(world.bridge.sit());
        CrewMemberComponent you = world.crew.crew(game.avatar);
        world.crew.applyDamage(game.avatar, you.health * 0.95f, CrewDamageSink.DamageCause.BLAST);
        world.update(FRAME);
        assertNull(world.bridge.seat());
        assertEquals("You were knocked out at the helm", unseated.get(1));
    }

    @Test
    void theHelmsmanHoldsTheHeadingThePlayerLeft() {
        Body body = body();
        float heading = body.getAngle();
        world.bridge.stand();
        waitForHelmsman();
        // Something knocks the ship round, harder than its RCS can stop in a few ticks
        body.setAngularVelocity(0.4f);
        run(1f);
        assertTrue(Math.abs(angleOff(body, heading)) > 0.02f, "it turned off its heading");
        run(30f);
        assertEquals(0f, angleOff(body, heading), 0.01f, "the helmsman brought it back");
        assertEquals(0f, body.getAngularVelocity(), 0.01f);
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        assertTrue(controls.rotationAssist);
    }

    @Test
    void withNobodyAtTheHelmTheShipDriftsAndSpins() {
        Body body = body();
        Vector2 v0 = PhysicsManager.getVelocity(body, new Vector2()).cpy();
        world.bridge.stand();
        knockOutTheCrew();
        body.setAngularVelocity(0.1f);
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 5; i++) {
            controls.forward = 1f; // from the deck: nobody answers
            world.update(FRAME);
        }
        assertNull(world.bridge.helmsman(game.player));
        assertEquals(0.1f, body.getAngularVelocity(), 0.005f, "nobody stops the spin");
        assertEquals(v0.len(), PhysicsManager.getVelocity(body, new Vector2()).len(), 0.5f, "nor fires the engine");

        // Back at the helm the player flies it again, with their own rotation assist
        GridPositionComponent pos = game.avatar.getComponent(GridPositionComponent.class);
        assertTrue(world.crew.crew(game.avatar).isConscious());
        SubsystemComponent helm = Bridge.console(game.player, SubsystemType.HELM);
        pos.setTile(helm.footprint.originX - 1, helm.footprint.originY);
        assertNull(world.bridge.sit(SubsystemType.HELM));
        for (int i = 0; i < 60 * 3; i++) {
            controls.forward = 1f;
            world.update(FRAME);
        }
        assertTrue(PhysicsManager.getVelocity(body, new Vector2()).dst(v0) > 5f, "the main engine fires");
        assertTrue(controls.rotationAssist);
        assertTrue(Math.abs(body.getAngularVelocity()) < 0.01f, "and assist stops the spin");
    }

    @Test
    void aHelmWithNoPowerGivesThePlayerNoControl() {
        Body body = body();
        Vector2 v0 = PhysicsManager.getVelocity(body, new Vector2()).cpy();
        Bridge.console(game.player, SubsystemType.HELM).switchedOn = false;
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 2; i++) {
            controls.forward = 1f;
            world.update(FRAME);
        }
        assertEquals("the helm is switched off", world.bridge.helmFault(game.player));
        assertEquals(v0.len(), PhysicsManager.getVelocity(body, new Vector2()).len(), 0.5f);
    }

    @Test
    void theSeatAndTheHeldHeadingAreSaved() {
        world.bridge.stand();
        waitForHelmsman();
        float held = game.player.getComponent(HelmComponent.class).heldAngle;
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        Entity ship = world.getPlayer();
        HelmComponent helm = ship.getComponent(HelmComponent.class);
        assertNotNull(helm);
        assertTrue(helm.holding);
        assertFalse(helm.playerHad);
        assertEquals(held, helm.heldAngle, 0.05f);
        assertNull(world.bridge.seat(), "standing, as saved");

        world.bridge.sit(SubsystemType.HELM);
        save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        assertEquals(SubsystemType.HELM, world.bridge.seat(), "seated, as saved");
    }

    @Test
    void crewWalkingPastDoNotPushTheSeatedPlayerOffTheHelm(@org.junit.jupiter.api.io.TempDir Path saves) {
        // Round 7: a yard technician walking by shoved the captain off the helm, so the career's saves
        // loaded with them standing on deck and V had no helm to take
        run(1f);
        GridPositionComponent you = game.avatar.getComponent(GridPositionComponent.class);
        float x = you.x, y = you.y;
        Entity other = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e != game.avatar && e.getComponent(GridPositionComponent.class).ship == game.player) other = e;
        }
        assertNotNull(other);
        // Right on top of the seat, as close as anyone squeezing past comes
        GridPositionComponent p = other.getComponent(GridPositionComponent.class);
        p.path.clear();
        p.x = x + 0.05f;
        p.y = y;
        run(2f);
        assertEquals(x, you.x, 1e-3f, "the seated player isn't moved");
        assertEquals(y, you.y, 1e-3f);
        assertEquals(SubsystemType.HELM, world.bridge.seat());
        assertTrue(unseated.isEmpty(), unseated.toString());

        // Saved there, the game loads with the player at the helm, flying the ship
        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(saves.resolve("helm.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        run(1f);
        assertEquals(SubsystemType.HELM, world.bridge.seat(), "at the helm after the load");
        assertTrue(world.bridge.playerAtHelm());

        // Stood up, the player walks again, and is pushed aside like anyone
        world.bridge.stand();
        GridPositionComponent pos = game.avatar.getComponent(GridPositionComponent.class);
        float sx = pos.x;
        game.avatar.getComponent(com.ringsofiron.aicrew.PlayerAvatarComponent.class).moveX = -1f;
        run(0.5f);
        assertNotEquals(sx, pos.x, 1e-3f, "walks once stood up");
    }

    @Test
    void standingUpFromTheHelmAfterALoadHandsItToTheCrewmanTendingTheWounded(@org.junit.jupiter.api.io.TempDir Path saves) {
        // Round 7: crew knocked out in a fight, and Bea, the one crewman, sat with them. The player
        // loaded at the helm, stood up, and nobody took it: the dock order dropped
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e == game.avatar || e.getComponent(GridPositionComponent.class).ship != game.player) continue;
            if (world.crew.crew(e).role != CrewRole.CREWMAN) world.crew.applyDamage(e, 75f, CrewDamageSink.DamageCause.BLAST);
        }
        for (int i = 0; i < 60 * 60 && !(crewman().task instanceof com.ringsofiron.aicrew.TreatTask); i++) world.update(FRAME);
        assertInstanceOf(com.ringsofiron.aicrew.TreatTask.class, crewman().task, "Bea sits with the wounded while the player has the helm");

        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(saves.resolve("wounded.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        run(1f);
        assertEquals(SubsystemType.HELM, world.bridge.seat(), "at the helm after the load");
        for (int i = 0; i < 60 * 60 && !(crewman().task instanceof com.ringsofiron.aicrew.TreatTask); i++) world.update(FRAME);
        assertInstanceOf(com.ringsofiron.aicrew.TreatTask.class, crewman().task, "and back with the wounded");
        world.bridge.stand();
        for (int i = 0; i < 60 * HelmOrders.UNMANNED_LIMIT && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "a crewman takes the helm before a helm order would drop");
    }

    /** The one crewman aboard. */
    private CrewMemberComponent crewman() {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = world.crew.crew(e);
            if (e != game.avatar && e.getComponent(GridPositionComponent.class).ship == game.player && c.role == CrewRole.CREWMAN) return c;
        }
        return null;
    }

    private void waitForHelmsman() {
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "Bea takes the helm");
    }

    private void knockOutTheCrew() {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e == game.avatar || e.getComponent(GridPositionComponent.class).ship != game.player) continue;
            CrewMemberComponent c = world.crew.crew(e);
            world.crew.applyDamage(e, c.health * 0.95f, CrewDamageSink.DamageCause.BLAST);
        }
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) world.update(FRAME);
    }

    private Body body() {
        return game.player.getComponent(PhysicsBodyComponent.class).body;
    }

    private static float angleOff(Body body, float heading) {
        float e = body.getAngle() - heading;
        return MathUtils.atan2(MathUtils.sin(e), MathUtils.cos(e));
    }
}
