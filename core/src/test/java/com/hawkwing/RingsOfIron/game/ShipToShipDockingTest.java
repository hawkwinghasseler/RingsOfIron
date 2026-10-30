package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The corvette's active collar latches onto another ship, whose port is not armed: the start of boarding. */
class ShipToShipDockingTest {
    static final float FRAME = 1f / 60f;
    private static final float U = PhysicsManager.UNITS_PER_METER;

    GameWorld world;
    GameScenario game;
    PlayerDocking docking;
    final List<String> notices = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, notices::add);
    }

    @AfterEach
    void tearDown() {
        docking.dispose();
        world.dispose();
    }

    @Test
    void theCorvettesPortIsActive() {
        DockingPort port = port(game.player);
        assertTrue(port.active, "a collar to reach another ship with");
        assertEquals(com.ringsofiron.interiorgrid.MountFootprint.MountSize.S, port.size);
    }

    @Test
    void theCollarTakesHoldOfAnUnarmedShipAndDrawsItIn() {
        world.update(FRAME);
        Entity raider = game.raider;
        parkAlongside(raider, 0.6f);
        assertFalse(port(raider).armed, "nobody aboard the raider agreed to this");

        // Highport is 8 km off, too far to ask: the port just arms
        assertEquals("Port armed", docking.toggleArm());
        for (int i = 0; i < 60 * 30 && port(game.player).state != DockingPort.State.DOCKED; i++) world.update(FRAME);
        assertEquals(DockingPort.State.DOCKED, port(game.player).state);
        assertSame(raider, world.docking.dockedTo(game.player));
        assertSame(game.player, world.docking.dockedTo(raider));
        assertFalse(port(game.player).permitted, "latched on without leave");
        for (int i = 0; i < 60 * 5 && !world.docking.isRigid(game.player); i++) world.update(FRAME);
        assertTrue(world.docking.isRigid(game.player), "welded");
        assertTrue(notices.stream().anyMatch(n -> n.startsWith("Docked at Raider")), notices.toString());
        assertTrue(docking.readout()[1].startsWith("Docked at Raider"), docking.readout()[1]);

        // ... and lets go again
        assertEquals("Undocked from Raider", docking.undock());
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertNull(world.docking.dockedTo(game.player));
    }

    /** Puts {@code other} beside the player's ship, its port facing the player's {@code gap} meters off. */
    private void parkAlongside(Entity other, float gap) {
        Body ship = body(game.player), ob = body(other);
        DockingPort pp = port(game.player), op = port(other);
        Vector2 n = ship.getWorldVector(new Vector2(pp.normalX, pp.normalY)).cpy();
        float angle = MathUtils.atan2(n.y, n.x) + MathUtils.PI - MathUtils.atan2(op.normalY, op.normalX);
        Vector2 target = face(ship, pp).mulAdd(n, gap);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        PhysicsManager.setPosition(ob, target.x - (c * op.faceX() - s * op.faceY()),
                target.y - (s * op.faceX() + c * op.faceY()), angle);
        Vector2 v = PhysicsManager.getVelocity(ship, new Vector2());
        PhysicsManager.setVelocity(ob, v.x, v.y);
        ob.setAngularVelocity(0f);
        ship.setAngularVelocity(0f);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).ports[0];
    }

    private static Vector2 face(Body body, DockingPort p) {
        Vector2 w = body.getWorldPoint(new Vector2(p.faceX() * U, p.faceY() * U));
        return new Vector2(w).scl(PhysicsManager.METERS_PER_UNIT);
    }
}
