package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The round-2 playtest's fixes that are rules, not balance, played headless on the real new game:
 * every faction was pleased by a pirate's death, and a ship latched to a holed one lost its air
 * through the port. Its balance targets (the first fight's length, salvage against the bounty, fights
 * to a promotion) are the playtest benchmark {@link FirstHourBalanceBenchmark}.
 */
class Round2FixesTest {
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private boolean runUntil(float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    /** Puts the player {@code meters} off the raider's beam, moving with it: the flying, done by hand. */
    private void flyTo(float meters) {
        Vector2 at = PhysicsManager.getPosition(body(game.raider), new Vector2());
        Vector2 v = PhysicsManager.getVelocity(body(game.raider), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, -90f * MathUtils.degRad);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    private void undocked() {
        game.beginCareer();
        run(2f);
        assertTrue(world.docking.undock(game.player));
        run(1f);
    }

    @Test
    void beatingAPirateOnlyPleasesTheFactionsSwornAgainstPirates() {
        world.standing.onShipDestroyed(game.raider);
        assertEquals(5f, world.standing.standing("patrol"), 1e-4, "the Patrol names them");
        assertEquals(0f, world.standing.standing("selene"), 1e-4, "the Selene Combine was never in the fight (was +15)");
        assertEquals(-FactionStanding.KILL_COST, world.standing.standing("pirates"), 1e-4);
    }

    @Test
    void aShipLatchedToAHoledOneKeepsItsAirAndItsCrewStillCrossSuited() {
        undocked();
        flyTo(40f);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        assertTrue(runUntil(10f, () -> world.docking.isRigid(game.player)), "latched on");
        ShipGridComponent ours = game.player.getComponent(ShipGridComponent.class);
        GridLink port = ours.links.first();
        assertTrue(port.canCross(), "both doors open while the air is alike");

        // Hole the raider's room behind its port, wide open to space
        ShipGridComponent theirs = port.other;
        Compartment room = theirs.compartmentAt(port.ox, port.oy);
        assertNotNull(room);
        int holes = 0;
        for (int y = 0; y < theirs.height && holes < 3; y++) {
            for (int x = 0; x < theirs.width && holes < 3; x++) {
                Tile tile = theirs.getTile(x, y);
                if (tile == null || !tile.isIntactWall() || !outerWall(theirs, x, y) || !besideRoom(theirs, room, x, y)) continue;
                tile.setIntegrity(0f);
                holes++;
            }
        }
        assertTrue(holes > 0, "a hull wall of that room");
        theirs.gridDirty = true;

        // Our port door shuts on the pressure difference; the air aboard holds
        float lowest = 1f;
        for (int i = 0; i < 60 / FRAME; i++) {
            world.update(FRAME);
            lowest = Math.min(lowest, ours.pressureRatioAt(port.x, port.y));
        }
        EdgeWall door = port.edge();
        assertTrue(door.bulkheadSealed && door.opensForCrew, "our door held shut for pressure");
        assertTrue(theirs.pressureRatioAt(port.ox, port.oy) < 0.1f, "their side is near vacuum: " + theirs.pressureRatioAt(port.ox, port.oy));
        assertTrue(lowest > 0.9f, "our air held: lowest " + lowest);
        assertTrue(port.canCross(), "still a way across for crew");
        assertEquals(0f, port.flowWidth(), 1e-6, "but no air through it");

        // A boarding party still goes across (the door opening for each of them), and takes it
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub.type == SubsystemComponent.SubsystemType.REACTOR) sub.switchedOn = false;
        }
        assertTrue(runUntil(5f, () -> ShipDisablement.isDisabled(game.raider)), "its reactor out");
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        assertTrue(runUntil(240f, () -> world.boarding.takenBy(game.raider, game.player)), "taken: " + world.boarding.notices);
        run(10f);
        assertTrue(ours.pressureRatioAt(port.x, port.y) > 0.75f, "breathable after the crossings: " + ours.pressureRatioAt(port.x, port.y));
    }

    private static boolean outerWall(ShipGridComponent grid, int x, int y) {
        for (Side s : Side.values()) {
            if (grid.getTile(x + s.dx, y + s.dy) == null) return true;
        }
        return false;
    }

    private static boolean besideRoom(ShipGridComponent grid, Compartment room, int x, int y) {
        for (Side s : Side.values()) {
            if (grid.inBounds(x + s.dx, y + s.dy) && grid.compartmentAt(x + s.dx, y + s.dy) == room) return true;
        }
        return false;
    }
}
