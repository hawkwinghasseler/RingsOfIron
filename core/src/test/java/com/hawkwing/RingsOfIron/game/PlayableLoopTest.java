package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The round-2 loop played by script on the real new game, headless: take a contract, undock, fight
 * the pirate raider until it gives up, board it, strip a gun, dock, get paid and gain standing. The
 * second test loses the ship and goes through abandon ship, rescue and requisition.
 *
 * <p>Flying is the one step done by hand: the ship is put near the raider rather than flown there
 * (flight and docking have their own tests). The fight is real: the player's guns and missiles
 * against the raider's, until the raider is disabled or surrenders.
 */
class PlayableLoopTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) step();
    }

    private void step() {
        world.update(FRAME);
        for (String n : world.standing.notices) notices.add(n);
        world.standing.notices.clear();
    }

    private boolean runUntil(float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            step();
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    /** Puts the player {@code meters} off the raider's beam, moving with it: the flying, done by hand. */
    private void flyTo(Entity target, float meters) {
        Vector2 at = PhysicsManager.getPosition(body(target), new Vector2());
        Vector2 v = PhysicsManager.getVelocity(body(target), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, -90f * com.badlogic.gdx.math.MathUtils.degRad);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    private String crewDoing() {
        StringBuilder out = new StringBuilder();
        // Who is aboard which craft, doing what: for a failure's message
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(com.ringsofiron.aicrew.CrewService.CREW)) {
            var c = world.crew.crew(e);
            var p = e.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class);
            out.append("\n  ").append(c.name).append(' ').append(c.role).append(' ').append(c.state).append(" on ")
                    .append(p.ship == game.player ? "ours" : p.ship == game.raider ? "raider" : "other")
                    .append(": ").append(c.activity);
        }
        return out.toString();
    }

    private Contract raiderBounty() {
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) return c;
        }
        return null;
    }

    @Test
    void aContractFlownFoughtBoardedAndStrippedPaysAndGainsStanding() {
        // A new career: an ensign of the sponsor, docked at Highport
        game.beginCareer();
        run(120);
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player), "starts docked at Highport");
        assertEquals(0, world.standing.record().rank);
        assertEquals("patrol", world.standing.factions().id(world.standing.sponsor()));
        TraderComponent account = game.player.getComponent(TraderComponent.class);
        double credits = account.credits;
        float standing = world.standing.standing(world.standing.sponsor());

        // The board's first offer is the bounty on the raider outside
        Contract bounty = raiderBounty();
        assertNotNull(bounty);
        assertSame(bounty, world.contracts.offers(game.highport).stream()
                .filter(c -> c.type == Contract.Type.BOUNTY).findFirst().orElseThrow(), "the first bounty offered");
        assertNull(world.contracts.locked(bounty), "an ensign may take it");
        assertNull(world.contracts.accept(bounty.id));

        // Undock and fly out to it
        assertTrue(world.docking.undock(game.player));
        run(60);
        flyTo(game.raider, 1_500f);
        run(2);

        // The fight: guns and missiles until it gives up; it fights back
        game.provokeRaider();
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.raider);
        assertTrue(runUntil(240f, () -> {
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            return ShipDisablement.isDisabled(game.raider);
        }), "the raider is beaten");
        assertFalse(ShipDisablement.isDisabled(game.player), "and the player's ship is not");
        // Missiles still in flight land, and what burns aboard it burns out
        run(60 * 30);

        // The bounty pays; beating a pirate gains standing with the factions sworn against them
        assertEquals(Contract.Status.DONE, bounty.status);
        assertEquals(credits + bounty.reward, account.credits, 1e-6);
        assertTrue(world.loop.credited(world.universe.uuidOf(game.raider)), "the kill counts as the player's");
        assertTrue(world.standing.standing(world.standing.sponsor()) >= standing + bounty.standing
                + FactionStanding.KILL_COST * FactionStanding.KILL_SHARE - 1e-3);

        // Latch on and board it; a pirate costs no standing to board
        float before = world.standing.standing(world.standing.sponsor());
        flyTo(game.raider, 40f);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        assertTrue(runUntil(10f, () -> world.boarding.target(game.player) == game.raider
                && world.docking.isRigid(game.player)), "latched on, a walkway between");
        run(120);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        assertTrue(runUntil(240f, () -> world.boarding.takenBy(game.raider, game.player)), "taken: " + world.boarding.notices);
        assertEquals(before, world.standing.standing(world.standing.sponsor()), 1e-3);

        // Strip a gun; it comes home as a crate
        WeaponMountComponent mount = null;
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) {
            if (m.def != null && !m.def.pointDefense && m.subsystem != null) mount = m;
        }
        assertNotNull(mount, "a gun left to strip");
        String part = Salvage.PART + mount.def.id;
        MountFootprint f = mount.subsystem.getComponent(SubsystemComponent.class).footprint;
        said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.startsWith("Engineers ordered to strip"), said);
        CargoHoldComponent hold = game.player.getComponent(CargoHoldComponent.class);
        assertTrue(runUntil(600f, () -> hold.amount(part) >= 1), "the part came home: " + world.salvage.notices + crewDoing());

        // Back to Highport: dock and sell the part
        world.docking.undock(game.player);
        run(60);
        assertTrue(game.dockAtHighport());
        assertTrue(runUntil(5f, () -> world.docking.dockedTo(game.player) == highport), "docked again");
        double beforeSale = account.credits;
        assertEquals(1, world.cargoSettlement.sellParts(game.player, part, 1), 1e-9);
        assertTrue(account.credits > beforeSale, "the part sold");
        // How many such fights make a Lieutenant is a balance target: FirstHourBalanceBenchmark
    }

    @Test
    void aLostShipEndsInRescueAtASponsorStationInTheShipTheRankAllows() {
        // A Lieutenant Commander may requisition the gunship
        world.standing.reward(world.standing.sponsor(), 40f, "test");
        assertEquals(2, world.standing.record().rank);
        assertEquals("gunship_heavy", world.loop.bestRequisition());
        // A plain job: the operations heading the board may have nothing to do just now
        Contract held = world.contracts.offers(game.highport).stream()
                .filter(c -> c.type != Contract.Type.OPERATION && c.type != Contract.Type.RAID).findFirst().orElseThrow();
        assertNull(world.contracts.accept(held.id));
        game.player.getComponent(TraderComponent.class).credits = 9_000;

        List<Rescue.Result> rescues = new ArrayList<>();
        world.rescue.rescues.add((signal, r) -> rescues.add(r));
        Entity oldShip = game.player;
        assertNull(world.abandon.order(game.player));
        assertTrue(runUntil(60f, () -> !rescues.isEmpty()), "rescued");
        Rescue.Result r = rescues.get(0);

        // At a station of the sponsor, aboard a gunship, seated at its helm
        assertNotSame(oldShip, game.player);
        assertEquals(world.standing.sponsor(), world.standing.factionOf(r.station), "a sponsor station: " + r.stationName);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        assertEquals(22, grid.width, "the gunship's layout");
        assertEquals(0, r.price, "the service issues it");
        assertNotNull(game.player.getComponent(HelmComponent.class));
        assertTrue(world.bridge.playerAtHelm(), "seated at the new ship's helm");
        assertEquals(9_000, game.player.getComponent(TraderComponent.class).credits, 1e-6);

        // The career and the contract held come through it
        assertEquals(2, world.standing.record().rank);
        assertTrue(world.contracts.active().contains(held) || held.status != Contract.Status.ACTIVE);
        run(120);
        assertSame(r.station, world.docking.dockedTo(game.player), "docked at " + r.stationName);
    }

    @Test
    void aLoadedCareerFindsItsStationsAgain(@TempDir Path dir) {
        game.beginCareer();
        run(120);
        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        assertNull(game.quickSave(file));
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            // The new game's stations had other uuids; the loaded ones are found by name
            assertEquals(game.highport, loaded.highport);
            assertEquals(game.depot, loaded.depot);
            assertEquals(game.lunarMine, loaded.lunarMine);
            Entity highport = fresh.engine.getEntity(loaded.highport);
            assertNotNull(highport);
            assertFalse(fresh.contracts.offers(loaded.highport).isEmpty(), "Highport's board is there");
            fresh.docking.undock(loaded.player);
            for (int i = 0; i < 60; i++) fresh.update(FRAME);
            assertTrue(loaded.dockAtHighport(), "docks at the loaded Highport");
        } finally {
            fresh.dispose();
        }
    }
}
