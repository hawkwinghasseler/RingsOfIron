package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.uihud.providers.CrewProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 5 part 2 in the game: battle stations, wound notices, medics' wages, stores at the yard and saves. */
class CrewMedicalTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        game.beginCareer();
        run(world, 120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private static void run(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(FRAME);
    }

    private Entity crewNamed(GameWorld w, Entity ship, String name) {
        for (Entity e : w.crewCareer.ownCrew(ship)) if (name.equals(w.crew.crew(e).name)) return e;
        return null;
    }

    private boolean noticed(String text) {
        return world.notices.history().stream().anyMatch(n -> n.text.equals(text));
    }

    private String notices() {
        return world.notices.history().stream().map(n -> n.text).toList().toString();
    }

    private ShipTaskBoardComponent board() {
        return game.player.getComponent(ShipTaskBoardComponent.class);
    }

    @Test
    void aShipIsAtBattleStationsWhileFoughtAndAMinuteAfterTheLastShot() {
        run(world, 2);
        assertFalse(board().battleStations, "docked at Highport, nobody fighting");
        game.provokeRaider();
        run(world, 2);
        assertTrue(board().battleStations, "the raider has us as its target");
        assertTrue(world.medical.atBattleStations());
        assertTrue(world.medical.suitsLine().endsWith("(at battle stations)"), world.medical.suitsLine());

        // Shot at a while ago, and no longer anyone's target
        world.medical.update();
        assertTrue(world.medical.inBattle(game.player, world.engine.getSimSeconds()));
        game.raider.remove(com.ringsofiron.aicrew.pilot.NpcCombatComponent.class);
        world.combatEvents.shots.dispatch(shot(game.player));
        double now = world.engine.getSimSeconds();
        assertTrue(world.medical.inBattle(game.player, now + CrewMedical.BATTLE_HOLD - 1));
        assertFalse(world.medical.inBattle(game.player, now + CrewMedical.BATTLE_HOLD + 1));
    }

    private static com.ringsofiron.combatsys.events.ShotFiredEvent shot(Entity shooter) {
        com.ringsofiron.combatsys.events.ShotFiredEvent s = new com.ringsofiron.combatsys.events.ShotFiredEvent();
        s.shooter = shooter;
        s.weaponDefId = "chaingun_mk1";
        return s;
    }

    @Test
    void theCrewWindowTurnsSuitsAtBattleStationsOff() {
        CrewRoster roster = new CrewRoster(world);
        CrewProvider.CrewSheet sheet = new CrewProvider.CrewSheet();
        roster.collect(sheet);
        assertEquals("Suits at battle stations: on", sheet.suits);
        assertTrue(sheet.summary.endsWith(ShipGridComponent.DEFAULT_MEDICAL_STORES + " medical stores"), sheet.summary);
        assertTrue(roster.toggleSuits().contains("stay out of their suits"));
        assertFalse(board().suitsAtBattleStations);
        sheet.clear();
        roster.collect(sheet);
        assertEquals("Suits at battle stations: off", sheet.suits);
        roster.toggleSuits();
        assertTrue(board().suitsAtBattleStations);
    }

    @Test
    void theWoundedAreNoticedAndShownAndADeathFromWoundsSaysSo() {
        Entity bea = crewNamed(world, game.player, "Bea");
        Entity ada = crewNamed(world, game.player, "Ada");
        world.crew.applyDamage(bea, 100f, DamageCause.PENETRATOR);
        world.crew.applyDamage(ada, 30f, DamageCause.SPALL);
        run(world, 1);
        assertTrue(noticed("Bea is critically wounded: treat within 5:00 or they die"), notices());
        assertTrue(noticed("Ada is lightly wounded and working slower"), notices());
        assertEquals("Light wound 100%", CrewCareer.condition(world.crew.crew(ada)));
        assertTrue(CrewCareer.condition(world.crew.crew(bea)).startsWith("Critical wound"),
                CrewCareer.condition(world.crew.crew(bea)));

        world.crew.dieOfWounds(bea);
        world.crewCareer.update();
        assertTrue(noticed("Bea has died of wounds"), notices());
    }

    @Test
    void aMedicsWageFollowsTheirMedicineAndTheYardRestocksTheStores() {
        CrewRecord green = new CrewRecord(), seasoned = new CrewRecord();
        green.medicine = 0.2f;
        seasoned.medicine = 0.8f;
        assertTrue(CrewCareer.wageFor(CrewRole.MEDIC, seasoned) > CrewCareer.wageFor(CrewRole.MEDIC, green));
        assertEquals(CrewCareer.wageFor(CrewRole.MEDIC, green), CrewCareer.wageFor(CrewRole.MEDIC, withOthers(green, 0.9f)),
                "only medicine counts for a medic");
        for (CrewHiring.Candidate c : CrewHiring.post("highport", 7, 3)) {
            if (c.role == CrewRole.MEDIC) assertEquals(CrewCareer.wageFor(CrewRole.MEDIC, c.record), c.record.wage, 1e-6);
        }

        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        grid.medicalStores = 1;
        Entity station = world.docking.dockedTo(game.player);
        assertNotNull(station);
        String said = world.yard.orderRepairs(game.player, station);
        assertEquals("Nothing aboard needs repairing: stores restocked", said);
        assertEquals(ShipGridComponent.DEFAULT_MEDICAL_STORES, grid.medicalStores);
    }

    private static CrewRecord withOthers(CrewRecord r, float others) {
        CrewRecord o = new CrewRecord();
        o.medicine = r.medicine;
        o.gunnery = o.engineering = o.marine = others;
        return o;
    }

    @Test
    void woundsStoresAndTheSuitSettingComeThroughASave(@TempDir Path dir) {
        Entity bea = crewNamed(world, game.player, "Bea");
        world.crew.applyDamage(bea, 75f, DamageCause.FRAGMENT);
        int[] t = GameScenario.floorTiles(game.player.getComponent(ShipGridComponent.class)).get(0);
        Entity prisoner = CrewFactory.spawn(world.crew, game.player, "Held", CrewRole.CREWMAN,
                world.standing.factions().index(GameScenario.PIRATES), t[0], t[1]);
        world.crew.crew(prisoner).held = true;
        world.crew.applyDamage(prisoner, 95f, DamageCause.PENETRATOR);
        game.player.getComponent(ShipGridComponent.class).medicalStores = 3;
        board().suitsAtBattleStations = false;
        FileHandle file = new FileHandle(dir.resolve("wounds.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            run(fresh, 2);
            CrewMemberComponent b = fresh.crew.crew(crewNamed(fresh, loaded.player, "Bea"));
            assertEquals(Wound.SERIOUS, b.wound);
            assertEquals(75f, b.injury, 1e-3f);
            assertEquals(DamageCause.FRAGMENT, b.woundCause);
            CrewMemberComponent p = fresh.crew.crew(fresh.prisoners.aboard(loaded.player).get(0));
            assertEquals(Wound.CRITICAL, p.wound);
            assertTrue(p.bleedOut > 0f && p.bleedOut <= p.def.bleedOutTime);
            assertEquals(3, loaded.player.getComponent(ShipGridComponent.class).medicalStores);
            assertFalse(loaded.player.getComponent(ShipTaskBoardComponent.class).suitsAtBattleStations);
            // The prisoner is treated aboard after the load, and lives
            run(fresh, 60 * 60 * 6);
            assertTrue(p.isAlive(), "treated in time: " + CrewMedical.condition(p));
            assertTrue(p.wound.compareTo(Wound.CRITICAL) < 0, CrewMedical.condition(p));
        } finally {
            fresh.dispose();
        }
    }
}
