package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What of round 3's enemies lives through a save into a new game: a fight with the player, ion stuns, an ace's crew. */
class PirateSaveGapsTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;
    GameWorld loaded;
    GameScenario again;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
        if (loaded != null) loaded.dispose();
    }

    /** Saves this game and loads it into a new world, as a restart does. */
    private void saveAndLoad(Path dir) {
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        loaded = new GameWorld();
        again = GameScenario.start(loaded, assets, assets.resolve("no-mods-here"));
        again.quickLoad(file);
    }

    @Test
    void aPirateThatFoughtThePlayerAndGetsAwayAfterALoadBecomesAnAce(@TempDir Path dir) {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        world.hostiles.onShotAt(ship, game.player);
        run(world, 2);
        assertFalse(h.ace);
        assertTrue(h.foughtPlayer);

        saveAndLoad(dir);
        HostileComponent back = loaded.macroSim.hostiles().get(id);
        assertTrue(back.foughtPlayer, "the fight came through the save");
        loaded.hostiles.onExitBubble(id, loaded.macroSim.now());
        assertTrue(back.ace && back.grudge, "it got away from the player: an ace with a grudge");
        assertEquals(1, back.escapes);
        assertFalse(back.foughtPlayer, "that fight is over");
    }

    @Test
    void aPirateThatNeverFoughtThePlayerIsNoAceForLeaving() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        bringIn(id, 3_000, 0);
        world.hostiles.onExitBubble(id, world.macroSim.now());
        assertFalse(h.ace);
        assertEquals(0, h.escapes);
    }

    @Test
    void anIonStunComesThroughASave(@TempDir Path dir) {
        SubsystemComponent reactor = reactorOf(game.player);
        reactor.stunned = 25f;
        saveAndLoad(dir);
        SubsystemComponent back = reactorOf(again.player);
        assertNotSame(reactor, back);
        assertEquals(25f, back.stunned, 1e-3f, "still stunned");
        assertTrue(back.isStunned());
        for (int i = 0; i < 60; i++) loaded.update(FRAME);
        assertTrue(back.stunned > 0f && back.stunned < 25f, "and it wears off from there: " + back.stunned);
    }

    @Test
    void anAcesCrewAndRefitComeThroughASave(@TempDir Path dir) {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, hostiles.all().values(), 1, true));
        String rare = PirateAces.bestWeapon(ship);
        assertNotNull(rare);
        String loadout = h.loadout;

        saveAndLoad(dir);
        Entity back = loaded.engine.getEntity(id);
        HostileComponent hb = loaded.macroSim.hostiles().get(id);
        assertTrue(hb.ace);
        assertEquals(loadout, hb.loadout, "the ace refit");
        assertEquals(rare, PirateAces.bestWeapon(back), "still carrying its refit's guns");
        int crew = 0;
        for (Entity c : loaded.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (c.getComponent(GridPositionComponent.class).ship != back) continue;
            assertTrue(c.getComponent(CrewMemberComponent.class).def.gunnerySkill >= PirateAces.ACE_SKILL, "an ace's gunner");
            crew++;
        }
        assertTrue(crew > 0, "its crew came back aboard");
        // ... and into the bubble, its guns are laid as well as before
        bringIn(loaded, id, 3_000, 0);
        for (WeaponMountComponent m : back.getComponent(WeaponsComponent.class).mounts) {
            assertTrue(m.gunnerySkill >= PirateAces.ACE_SKILL, m.def.id + " at " + m.gunnerySkill);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static SubsystemComponent reactorOf(Entity ship) {
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) return s;
        }
        fail("no reactor");
        return null;
    }

    @Test
    void pirateCrewsStayAtTheirPostsUnderFireThroughASave(@TempDir Path dir) {
        int pirates = world.standing.factions().index(HostileTraffic.PIRATES);
        assertEquals(pirates, boardOf(world, pirate(HostileTraffic.RAIDER)).faction);
        assertFalse(boardOf(world, pirate(HostileTraffic.RAIDER)).keepsClearOfFire, "crews_keep_clear_of_fire = false");
        assertTrue(world.crew.board(game.player).keepsClearOfFire, "our crew keep clear");
        int pirateBoards = boardsKeepTheirFaction(world, pirates);
        assertTrue(pirateBoards > 1, "the raiders and the base: " + pirateBoards);

        saveAndLoad(dir);
        assertEquals(pirateBoards, boardsKeepTheirFaction(loaded, pirates));
    }

    private static ShipTaskBoardComponent boardOf(GameWorld w, String uuid) {
        return w.crew.board(w.engine.getEntity(uuid));
    }

    /** Checks every crewed board keeps clear of fire unless the pirates work it; returns how many they do. */
    private static int boardsKeepTheirFaction(GameWorld w, int pirates) {
        int n = 0;
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(Family.all(ShipTaskBoardComponent.class).get())) {
            ShipTaskBoardComponent b = e.getComponent(ShipTaskBoardComponent.class);
            assertEquals(b.faction != pirates, b.keepsClearOfFire, "faction " + b.faction);
            if (b.faction == pirates) n++;
        }
        return n;
    }

    private String pirate(String shipId) {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && shipId.equals(e.getValue().shipId)) return e.getKey();
        }
        fail("no " + shipId);
        return null;
    }

    private Entity bringIn(String uuid, float dx, float dy) {
        return bringIn(world, uuid, dx, dy);
    }

    /** Puts a hostile on the rails {@code dx, dy} meters from the bubble origin and lets it come in. */
    private static Entity bringIn(GameWorld w, String uuid, float dx, float dy) {
        double now = w.macroSim.now();
        StateVector s = w.macroSim.gateway().anchorStateAt(now, new StateVector());
        w.macroSim.rails().attach(uuid, GameScenario.EARTH, s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = w.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) w.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private static void run(GameWorld w, double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) w.update(FRAME);
    }
}
