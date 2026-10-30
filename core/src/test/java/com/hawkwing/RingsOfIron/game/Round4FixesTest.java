package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.career.NewGame;
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
 * Round 4's fixes that are rules, not balance (planning/round4-balance.md), checked on the real new
 * game: an ace ran from the first hit, seed 4 lost freighters day after day, and the yard and the
 * captain's death at the berth. Its balance targets (a prize against its bounty, the gunship in weeks
 * of pay, the ace's chaingun) are the playtest benchmark {@link MidCareerBalanceBenchmark}.
 */
class Round4FixesTest {
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
