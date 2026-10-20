package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 7 part 4: a pirate that beats the player boards them for their cargo and credits, and leaves. */
class PirateBoardingTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;
    final List<PirateBoarding.Take> takes = new ArrayList<>();
    /** Every notice posted. */
    final List<String> said = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
        world.pirateBoarding.robbed.add((sig, t) -> takes.add(t));
        world.notices.posted.add((sig, n) -> said.add(n.text));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aPirateInGoodShapeBoardsTakesTheCargoAndATenthOfTheCreditsAndLeaves() {
        CargoHoldComponent hold = game.player.getComponent(CargoHoldComponent.class);
        assertEquals(10, hold.load("ore", 10, 1, 1), 1e-9);
        credits(20_000);
        float[] health = crewHealth();
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        HostileComponent h = hostiles.get(id);
        beatThePlayer();
        assertTrue(runUntil(60, () -> world.pirateBoarding.boarder() == ship), "it comes to board");
        assertNull(ship.getComponent(SensorComponent.class).lockedTarget, "with its guns cold");
        assertFalse(h.brokeOff);
        assertTrue(runUntil(600, () -> !takes.isEmpty()), "it took what it came for");
        PirateBoarding.Take take = takes.get(0);
        assertSame(ship, take.pirate);
        assertEquals(List.of("ore"), take.commodities);
        assertEquals(10, take.units.get(0), 1e-9);
        assertEquals(2_000, take.credits, 1e-9);
        assertEquals(0, hold.amount("ore"), 1e-9, "the hold is empty");
        assertEquals(18_000, account().credits, 1e-9);
        CargoHoldComponent theirs = ship.getComponent(CargoHoldComponent.class);
        if (theirs != null) assertTrue(theirs.amount("ore") > 0, "what fits goes in the pirate's hold");
        assertTrue(said.stream().anyMatch(n -> n.contains("took 10 ore and 2,000 cr")));
        run(3);
        assertTrue(h.brokeOff, "and it leaves");
        assertNull(world.pirateBoarding.boarder());
        float[] after = crewHealth();
        for (int i = 0; i < health.length; i++) assertEquals(health[i], after[i], 1e-3f, "the crew aren't harmed");
    }

    @Test
    void theCreditsTakenAreCapped() {
        credits(200_000);
        huntingPirate();
        beatThePlayer();
        assertTrue(runUntil(600, () -> !takes.isEmpty()));
        assertEquals(PirateBoarding.CREDIT_CAP, takes.get(0).credits, 1e-9);
        assertEquals(195_000, account().credits, 1e-9);
    }

    @Test
    void aBadlyHurtPirateLeavesThePlayerDriftingInstead() {
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        beatThePlayer();
        assertTrue(runUntil(2, () -> ShipDisablement.isDisabled(game.player)));
        // Then hit: below its boarding line, above its break-off line (hurt before, it would have run)
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.68f);
            }
        }
        run(5);
        assertTrue(hostiles.get(id).brokeOff);
        assertNull(world.pirateBoarding.boarder());
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("leaves you drifting")));
        run(120);
        assertTrue(takes.isEmpty());
        assertEquals(20_000, account().credits, 1e-9);
    }

    @Test
    void aBoardingUnderWayComesBackFromASave(@TempDir Path dir) {
        credits(20_000);
        String id = huntingPirate();
        beatThePlayer();
        assertTrue(runUntil(600, () -> world.pirateBoarding.boarder() != null
                && said.stream().anyMatch(n -> n.contains("has latched on"))), "latched on");
        run(10);
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        assertSame(world.engine.getEntity(id), world.pirateBoarding.boarder(), "still aboard after the load");
        // The load leaves the two a few hundred meters apart: it comes alongside again and finishes
        // what it had begun
        assertTrue(runUntil((float) PirateBoarding.BOARD_SECONDS + 120f, () -> !takes.isEmpty()), "and it finishes");
        assertEquals(2_000, takes.get(0).credits, 1e-9);
    }

    @Test
    void aWingmanBoardingFinishesWhenItsLeaderLeaves() {
        // Round 7 integration: in the raid's real-window run a raider began to board, its leader left
        // the player drifting, and the raider broke off with it: "pirates are aboard", then nothing
        credits(20_000);
        List<String> ids = world.hostiles.spawnWing(HostileTraffic.RAIDER, 2, game.highport, "raider_chaingun");
        HostileComponent lead = hostiles.get(ids.get(0));
        HostileComponent wing = hostiles.get(ids.get(1));
        lead.targetUuid = HostileComponent.PLAYER;
        wing.targetUuid = HostileComponent.PLAYER;
        bringIn(ids.get(0), 2_000, 0);
        Entity w = bringIn(ids.get(1), 2_000, 300);
        run(2);
        beatThePlayer();
        assertTrue(runUntil(2, () -> ShipDisablement.isDisabled(game.player)));
        // The wingman comes alongside first; the leader, finding it taken, leaves the player drifting
        assertTrue(world.pirateBoarding.board(ids.get(1), w, wing, game.player, 0));
        assertTrue(runUntil(30, () -> lead.brokeOff), "the leader leaves");
        assertTrue(runUntil((float) PirateBoarding.BOARD_SECONDS + 120f, () -> !takes.isEmpty()), "the wingman finishes its boarding");
        assertSame(w, takes.get(0).pirate);
        assertTrue(world.hostiles.notices.stream().noneMatch(n -> n.equals(wing.displayName + " is breaking off with its leader")));
    }

    @Test
    void aBoarderThatIsHitBreaksOffAndTakesNothing() {
        // Round 8: the railgun ace took hits while latched and carried on (wound survey, "Hits after")
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        HostileComponent h = hostiles.get(id);
        beatThePlayer();
        assertTrue(runUntil(600, () -> said.stream().anyMatch(n -> n.contains("has latched on"))), "latched on");
        // Round 9: boarded is a warning, not a promotion's green
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("has latched on") && n.kind == NoticeLog.Kind.WARNING));
        world.combatEvents.impacts.dispatch(hitFrom(game.player, ship));
        assertNull(world.pirateBoarding.boarder(), "one hit ends it");
        assertTrue(said.contains(h.displayName + " breaks off its boarding under fire"));
        assertTrue(world.pirateBoarding.drivenOff(id));
        assertTrue(runUntil(5, () -> h.brokeOff), "it leaves");
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("leaves you drifting")), "the player left drifting");
        run(PirateBoarding.BOARD_SECONDS + 60);
        assertTrue(takes.isEmpty(), "it took nothing");
        assertNull(world.pirateBoarding.boarder(), "nor came back to board again");
        assertEquals(20_000, account().credits, 1e-9);
    }

    @Test
    void aHitFromSomeoneElseDoesNotDriveTheBoarderOff() {
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        beatThePlayer();
        assertTrue(runUntil(60, () -> world.pirateBoarding.boarder() == ship));
        world.combatEvents.impacts.dispatch(hitFrom(null, ship));
        assertSame(ship, world.pirateBoarding.boarder());
        assertFalse(world.pirateBoarding.drivenOff(id));
    }

    @Test
    void beingDrivenOffComesBackFromASaveAndEndsWithTheFight(@TempDir Path dir) {
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        beatThePlayer();
        assertTrue(runUntil(60, () -> world.pirateBoarding.boarder() == ship));
        world.combatEvents.impacts.dispatch(hitFrom(game.player, ship));
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        assertTrue(world.pirateBoarding.drivenOff(id), "kept in the save");
        // The reactor back on: the fight is over, and the pirate may board another day
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = true;
        }
        assertTrue(runUntil(10, () -> !world.pirateBoarding.drivenOff(id)));
    }

    @Test
    void aDockedPlayerIsNeverBoarded() {
        credits(20_000);
        assertTrue(game.dockAtHighport());
        run(5);
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        huntingPirate();
        beatThePlayer();
        run(PirateBoarding.BOARD_SECONDS + 120);
        assertTrue(said.stream().noneMatch(n -> n.contains("coming alongside to board")), String.join("\n", said));
        assertNull(world.pirateBoarding.boarder());
        assertTrue(takes.isEmpty());
        assertEquals(20_000, account().credits, 1e-9);
        assertSame(highport, world.docking.dockedTo(game.player), "still at the berth");
    }

    @Test
    void aBoardingInProgressEndsOnDocking() {
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        HostileComponent h = hostiles.get(id);
        beatThePlayer();
        assertTrue(runUntil(600, () -> said.stream().anyMatch(n -> n.contains("has latched on"))), "latched on");
        SensorComponent ours = game.player.getComponent(SensorComponent.class);
        ours.lockedTarget = ship;
        assertTrue(game.dockAtHighport());
        run(2);
        assertNull(world.pirateBoarding.boarder(), "docked, the boarding is over");
        assertTrue(said.contains(h.displayName + " casts off: you are at a berth"));
        // A gun left locked on it would open up from the berth once the power is back, and tear the latch
        assertNotSame(ship, ours.lockedTarget, "our guns let it go");
        assertTrue(runUntil(5, () -> h.brokeOff));
        run(PirateBoarding.BOARD_SECONDS + 60);
        assertTrue(takes.isEmpty());
        assertNotSame(ship, world.pirateBoarding.boarder());
        assertEquals(20_000, account().credits, 1e-9);
    }

    @Test
    void aBoardingInProgressEndsWhenThePlayerIsTakenInTow() {
        credits(20_000);
        String id = huntingPirate();
        HostileComponent h = hostiles.get(id);
        beatThePlayer();
        assertTrue(runUntil(600, () -> said.stream().anyMatch(n -> n.contains("has latched on"))), "latched on");
        assertNull(world.rescue.tow(game.player));
        assertNull(world.pirateBoarding.boarder());
        assertTrue(said.contains(h.displayName + " casts off as you are taken in tow"));
        run(PirateBoarding.BOARD_SECONDS + 60);
        assertTrue(takes.isEmpty());
    }

    @Test
    void aLatchedBoarderNeverMovesADockedShip() {
        // Seed 2026 (round 7): the boarding finished at Highport's berth and the ship ended docked at nothing
        credits(20_000);
        String id = huntingPirate();
        Entity ship = world.engine.getEntity(id);
        beatThePlayer();
        assertTrue(runUntil(600, () -> said.stream().anyMatch(n -> n.contains("has latched on"))), "latched on");
        assertTrue(game.dockAtHighport());
        run(5);
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        com.badlogic.gdx.math.Vector2 at = offset(game.player, highport);
        // However the boarder flies now, the berth holds the ship where it is
        for (int i = 0; i < 120 / FRAME; i++) {
            world.update(FRAME);
            assertSame(highport, world.docking.dockedTo(game.player), "pulled off the berth after " + i * FRAME + " s");
        }
        assertEquals(0, at.dst(offset(game.player, highport)), 1f, "the ship hasn't moved on its berth");
        assertTrue(takes.isEmpty());
        assertNotSame(ship, world.pirateBoarding.boarder());
    }

    // ---------------------------------------------------------------------------------------------

    private String huntingPirate() {
        String id = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && HostileTraffic.PIRATE_GUNSHIP.equals(e.getValue().shipId)) id = e.getKey();
        }
        assertNotNull(id);
        hostiles.get(id).targetUuid = HostileComponent.PLAYER;
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(id, GameScenario.EARTH, s.set(s.x + 3_000, s.y, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(id);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        run(2);
        assertSame(game.player, ship.getComponent(SensorComponent.class).lockedTarget);
        return id;
    }

    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(), s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    /** The player's reactor down: beaten. */
    private void beatThePlayer() {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
    }

    private com.ringsofiron.combatsys.events.ImpactEvent hitFrom(Entity owner, Entity target) {
        var hit = new com.ringsofiron.combatsys.events.ImpactEvent();
        hit.target = target;
        hit.owner = owner;
        return hit;
    }

    /** Where {@code ship} sits in {@code station}'s frame, m. */
    private com.badlogic.gdx.math.Vector2 offset(Entity ship, Entity station) {
        var a = world.docking.bodyOf(ship);
        var b = world.docking.bodyOf(station);
        var d = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(a, new com.badlogic.gdx.math.Vector2())
                .sub(com.ringsofiron.physicsexterior.PhysicsManager.getPosition(b, new com.badlogic.gdx.math.Vector2()));
        return d.rotateRad(-b.getAngle());
    }

    private TraderComponent account() {
        return game.player.getComponent(TraderComponent.class);
    }

    private void credits(double credits) {
        account().credits = credits;
    }

    private float[] crewHealth() {
        List<Entity> crew = world.crewCareer.ownCrew(game.player);
        float[] out = new float[crew.size()];
        for (int i = 0; i < out.length; i++) {
            CrewMemberComponent c = world.crew.crew(crew.get(i));
            out[i] = c.health - c.injury;
        }
        return out;
    }

    private boolean runUntil(float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            if (done.getAsBoolean()) return true;
            world.update(FRAME);
        }
        return done.getAsBoolean();
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
