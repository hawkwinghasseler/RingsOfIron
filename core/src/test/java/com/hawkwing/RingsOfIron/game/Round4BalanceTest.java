package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 4's balance pass (planning/round4-balance.md), each finding of the round 3 playtest checked
 * on the real new game: a prize paid ten bounties, the gunship was forty weeks of pay away, an ace
 * ran from the first hit, and seed 4 lost freighters day after day. {@link Round3CareerTest} plays
 * the whole career through on these numbers.
 */
class Round4BalanceTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** Every hauler on the rails set underway to Highport, as ones in flight would be. */
    private void underway() {
        int n = 0;
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            Entity ship = world.engine.getEntity(e.getKey());
            if (ship == null || world.universe.orbit(ship) == null) continue;
            e.getValue().state = FreighterComponent.State.TO_DELIVERY;
            e.getValue().destinationUuid = game.highport;
            n++;
        }
        assertTrue(n >= 2, n + " haulers on the rails");
    }

    @Test
    void aRaiderTakenAsAPrizePaysOneToTwoOfItsBounties() {
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) bounty = c;
        }
        assertNotNull(bounty);
        double prize = Shipyard.prizeValue(game.raider);
        assertTrue(prize >= bounty.reward && prize <= 2 * bounty.reward,
                "prize " + prize + " against a bounty of " + bounty.reward);
        // Stripping it first never pays more than taking it whole
        assertTrue(Shipyard.STRIPPED_PRIZE_SHARE < Shipyard.PRIZE_SHARE);
    }

    @Test
    void theGunshipIsAFewWeeksOfALieutenantCommandersPayAfterTheCorvettesTradeIn() {
        assertTrue(world.docking.undock(game.player));
        run(60);
        assertTrue(game.dockAt(game.shipyard));
        run(120);
        Shipyard.Listing gunship = world.shipyard.listing("patrol_gunship");
        assertNotNull(gunship);
        double cost = world.shipyard.cost(gunship, game.player);
        double pay = world.standing.ranks().get(2).budget;
        double weeks = cost / pay;
        // Round 3 had it at 207,587 net against 5,000 a week: about 40 weeks
        assertTrue(weeks > 4 && weeks < 8, String.format("%.0f cr after the trade-in is %.1f weeks of %.0f", cost, weeks, pay));
        assertTrue(gunship.price > 304_000, "still priced over the gunship's 304,000 value");
    }

    @Test
    void anAceFightsOnWhereAPlainPirateWouldRun() {
        HostileComponent plain = new HostileComponent("Pirate raider 1", HostileTraffic.RAIDER, HostileTraffic.PIRATES,
                HostileComponent.Role.PIRATE, 18_000);
        HostileComponent ace = new HostileComponent("Red Kestrel", HostileTraffic.RAIDER, HostileTraffic.PIRATES,
                HostileComponent.Role.PIRATE, 18_000);
        ace.ace = true;
        assertEquals(HostileTraffic.BREAK_OFF_HULL, HostileTraffic.breakOffHull(plain, false), 1e-9);
        assertTrue(HostileTraffic.breakOffHull(ace, false) < HostileTraffic.BREAK_OFF_HULL, "an ace holds on longer");
        assertTrue(HostileTraffic.breakOffHull(ace, false) > ShipDisablement.SURRENDER_HULL, "and still breaks off before it strikes");
        ace.partDropped = true;
        assertEquals(HostileTraffic.BREAK_OFF_HULL, HostileTraffic.breakOffHull(ace, false), 1e-9, "a beaten ace is a pirate again");
    }

    @Test
    void anAcesChaingunHitsHarderThanTheGunItReplaces() {
        LoadoutDef refit = Loadouts.pick(world.combatDefs, HostileTraffic.RAIDER, 1.0, true, new Random(1));
        assertNotNull(refit);
        assertEquals("raider_ace_chaingun", refit.id, "below difficulty 1.5 an ace raider is refitted with the chaingun");
        var mk1 = world.combatDefs.projectiles.get(world.combatDefs.weapons.get("chaingun_mk1").projectile);
        var mk2 = world.combatDefs.projectiles.get(world.combatDefs.weapons.get("chaingun_mk2").projectile);
        double e1 = 0.5 * mk1.mass * mk1.muzzleSpeed * mk1.muzzleSpeed, e2 = 0.5 * mk2.mass * mk2.muzzleSpeed * mk2.muzzleSpeed;
        assertTrue(e2 > 5 * e1, "the Mk2's rounds carry " + e2 / e1 + " times the Mk1's energy");
    }

    @Test
    void aFreighterIsLostNoMoreThanOnceInFiveDays() {
        underway();
        long day = 10;
        Random rnd = new Random(4);
        assertTrue(world.events.happen(WorldEvents.Kind.FREIGHTER_LOST, day, rnd), "a hauler is lost");
        for (long d = day + 1; d < day + WorldEvents.LOST_GAP_DAYS; d++) {
            assertFalse(world.events.happen(WorldEvents.Kind.FREIGHTER_LOST, d, rnd), "not another on day " + d);
        }
        assertTrue(world.events.happen(WorldEvents.Kind.FREIGHTER_LOST, day + WorldEvents.LOST_GAP_DAYS, rnd),
                "another, five days on");
    }

    @Test
    void aLostFreightersCrewGoWithIt() {
        underway();
        int before = world.crew.crewEntities().size();
        assertTrue(world.events.happen(WorldEvents.Kind.FREIGHTER_LOST, 10, new Random(4)));
        for (Entity e : world.crew.crewEntities()) {
            Entity ship = world.crew.position(e).ship;
            assertNotNull(ship.getComponent(ShipGridComponent.class), "nobody left aboard a ship that is gone");
        }
        assertTrue(world.crew.crewEntities().size() <= before);
    }

    @Test
    void theYardPatchesAHoledDeckPlate() {
        // The ace's 40 mm rounds hole deck plates, which vent to space; the yard used to leave them
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        Tile plate = null;
        int px = -1, py = -1;
        for (int y = 0; y < grid.height && plate == null; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.piece == null && t.isFloor() && !t.isWall()) {
                    plate = t;
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        assertNotNull(plate);
        plate.integrity = 0.2f;
        assertTrue(plate.leakFactor() > 0, "it vents");
        boolean listed = false;
        for (StationYard.Damage d : world.yard.damage(game.player)) {
            listed |= d.kind == StationYard.Damage.Kind.HULL && d.x == px && d.y == py;
        }
        assertTrue(listed, "the yard lists the plate at (" + px + ", " + py + ")");
        assertTrue(world.yard.repairCost(game.player) > 0);
    }

    @Test
    void aCaptainKilledAtTheStationWakesThereAndTheShipStaysLatched() {
        // Round 4's career: the ace's rounds left the ship venting, the captain died docked at Highport,
        // and the tow home undocked it, which called off the yard's repairs
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        assertTrue(world.yard.orderRepairs(game.player, highport) != null);
        int[] carried = {0};
        world.carriedHome.add((signal, t) -> carried[0]++);
        Entity avatar = null;
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) avatar = e;
        world.crew.applyDamage(avatar, 1e6f, CrewDamageSink.DamageCause.BLAST);
        run(2);
        assertNull(world.getCareerOver());
        assertEquals(1, carried[0]);
        assertTrue(avatar.getComponent(CrewMemberComponent.class).isConscious(), "hurt, but alive");
        assertSame(highport, world.docking.dockedTo(game.player), "still latched at Highport");
    }
}
