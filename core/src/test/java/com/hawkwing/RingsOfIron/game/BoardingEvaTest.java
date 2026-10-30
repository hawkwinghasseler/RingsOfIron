package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Boarding with no dock: the raider, beaten and surrendered, standing off beside the player's
 * corvette, is boarded on EVA (the party across on their jetpacks, in by its airlock pried open, as
 * it has no power) and taken; the party then comes back across. And storming: latched to a raider
 * that can still fight, the party is sent to force its way in, every door on the way.
 */
class BoardingEvaTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

    GameWorld world;
    GameScenario game;
    final List<Entity> marines = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        int[] t = floor.get(floor.size() - 1);
        CrewFactory.spawn(world.crew, game.raider, "Raider", CrewRole.CREWMAN, RAIDERS, t[0], t[1]);
        for (Entity e : world.crewCareer.ownCrew(game.player)) {
            if (world.crew.crew(e).role == CrewRole.MARINE) marines.add(e);
        }
        if (marines.isEmpty()) {
            int[] o = FloorTiles.of(game.player.getComponent(ShipGridComponent.class)).get(0);
            marines.add(CrewFactory.spawn(world.crew, game.player, "Dag", CrewRole.MARINE, 0, o[0], o[1]));
        }
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private boolean runUntil(float seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME && !done.getAsBoolean(); i++) world.update(FRAME);
        return done.getAsBoolean();
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void reactors(boolean on) {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = on;
        }
    }

    /** The raider still beside the player's ship, {@code meters} off its side, drifting with it. */
    private void standOff(float meters) {
        Body ours = body(game.player), theirs = body(game.raider);
        Vector2 at = ours.getPosition(), v = ours.getLinearVelocity();
        float a = ours.getAngle();
        float off = meters / PhysicsManager.METERS_PER_UNIT;
        theirs.setTransform(at.x - (float) Math.sin(a) * off, at.y + (float) Math.cos(a) * off, a);
        theirs.setLinearVelocity(v);
        theirs.setAngularVelocity(0f);
    }

    private boolean anyAcross() {
        for (Entity e : marines) if (world.crew.position(e).ship == game.raider) return true;
        return false;
    }

    @Test
    void aSurrenderedShipStandingOffIsBoardedOnEvaAndTheBoardersComeBack() {
        reactors(false);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        standOff(40f);
        run(1f);
        assertNull(world.docking.dockedTo(game.player), "no dock between");
        assertSame(game.raider, world.boarding.boardable(game.player), "in reach on EVA");
        assertNull(world.boarding.refusal(game.player, game.raider));
        String said = world.boarding.order(game.player);
        assertTrue(said.contains("across on EVA"), said);

        boolean out = false;
        for (int i = 0; i < 600 / FRAME && !world.boarding.takenBy(game.raider, game.player); i++) {
            world.update(FRAME);
            for (Entity e : marines) out |= EvaSystem.of(e) != null && EvaSystem.of(e).crossing;
        }
        assertTrue(out, "across on EVA");
        assertTrue(world.boarding.takenBy(game.raider, game.player), "taken once a boarder is aboard");
        assertTrue(world.spacewalks.railsWarpBlockedReason() != null, "rails warp waits for them to come back");

        assertTrue(runUntil(900f, () -> !anyAcross() && world.spacewalks.onEva().isEmpty()),
                "the party back across and in: " + world.crew.crew(marines.get(0)).activity);
        for (Entity e : marines) assertSame(game.player, world.crew.position(e).ship);
        assertNull(world.spacewalks.railsWarpBlockedReason());
    }

    @Test
    void stormingALatchedShipThatCanStillFightForcesItsDoors() {
        reactors(true);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(2f);
        assertFalse(ShipDisablement.isDisabled(game.raider));
        assertNotNull(world.boarding.refusal(game.player, game.raider), "not boarded as a beaten ship");
        assertNull(world.boarding.refusal(game.player, game.raider, true), "but it can be stormed");
        String said = world.boarding.storm(game.player);
        assertTrue(said.startsWith("Boarders ordered to storm"), said);
        assertTrue(world.boarding.storming(0, RAIDERS), "its crew fight the boarders");

        // No free way through its doors: each is cut, the reactor running
        ShipGridComponent grid = game.raider.getComponent(ShipGridComponent.class);
        EdgeWall door = null;
        for (EdgeWall[] row : grid.edgeH) {
            for (EdgeWall w : row) if (door == null && w != null && w.hasBulkhead) door = w;
        }
        assertNotNull(door, "the raider has doors to force");
        assertEquals(ForceMethod.CUT.seconds, world.boarding.forcing(marines.get(0), game.raider, door).seconds);
        assertTrue(runUntil(300f, this::anyAcross), "a boarder aboard");
        assertTrue(runUntil(300f, () -> world.boarding.takenBy(game.raider, game.player)), "stormed and taken");
        assertFalse(world.boarding.storming(0, RAIDERS), "the fight over once it is taken");
    }
}
