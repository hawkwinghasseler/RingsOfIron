package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.uihud.providers.CrewProvider;
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
 * Round 3's crew: hiring at stations, wages and morale, veterancy, prisoners, and all of it through
 * a save and a rescue, on the real new game, headless.
 */
class CrewCareerTest {
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private boolean runUntil(int seconds, BooleanSupplier done) {
        for (int i = 0; i < 60 * seconds; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private String noticesText() {
        StringBuilder out = new StringBuilder();
        for (NoticeLog.Notice n : world.notices.history()) out.append(n.text).append(" | ");
        return out.toString();
    }

    private boolean noticed(String text) {
        return noticesText().contains(text);
    }

    private TraderComponent account() {
        return game.player.getComponent(TraderComponent.class);
    }

    private Entity crewNamed(String name) {
        for (Entity e : world.crewCareer.ownCrew(game.player)) if (world.crew.crew(e).name.equals(name)) return e;
        throw new AssertionError("no " + name + " aboard");
    }

    /** A new career, docked at Highport. */
    private void docked() {
        game.beginCareer();
        run(120);
        assertNotNull(world.crewHiring.hiringStation(), "Highport hires");
    }

    // ------------------------------------------------------------------ hiring

    @Test
    void aStationsPoolIsTheSameAllDayAndNewTheNext() {
        List<CrewHiring.Candidate> a = CrewHiring.post(game.highport, 3), b = CrewHiring.post(game.highport, 3);
        assertTrue(a.size() >= CrewHiring.MIN_POOL && a.size() <= CrewHiring.MAX_POOL, "pool of " + a.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).name, b.get(i).name);
            assertEquals(a.get(i).record.wage, b.get(i).record.wage);
            assertTrue(a.get(i).record.wage > 0, "everyone hired asks a wage");
        }
        List<String> today = new ArrayList<>(), tomorrow = new ArrayList<>();
        for (CrewHiring.Candidate c : a) today.add(c.name);
        for (CrewHiring.Candidate c : CrewHiring.post(game.highport, 4)) tomorrow.add(c.name);
        assertNotEquals(today, tomorrow);
    }

    @Test
    void hiringWhileDockedPaysAWeekUpFrontAndTheHireWalksAboard() {
        docked();
        int crewBefore = world.crewCareer.ownCrew(game.player).size();
        List<CrewHiring.Candidate> pool = world.crewHiring.pool(game.highport);
        CrewHiring.Candidate c = pool.get(0);
        account().credits = 10_000;
        assertNull(world.crewHiring.hire(c.key));
        assertEquals(10_000 - c.signing, account().credits, 1e-6);
        assertEquals(crewBefore + 1, world.crewCareer.ownCrew(game.player).size());
        assertEquals(pool.size() - 1, world.crewHiring.pool(game.highport).size(), "hired once");
        assertNotNull(world.crewHiring.hire(c.key), "not twice");

        Entity hire = crewNamed(c.name);
        CrewMemberComponent m = world.crew.crew(hire);
        assertEquals(c.record.wage, m.record().wage, 1e-6);
        assertEquals(c.record.engineering, m.record().engineering, 1e-6);
        assertEquals(c.role, m.role);
        assertEquals(CrewDef.forRole(c.role).repairRate * CrewRecord.factor(c.record.engineering), m.def.repairRate, 1e-6,
                "their skill sets how they work");
        assertTrue(noticed("signs on"), noticesText());
        // ... and they go to work like any other crew
        run(60);
        assertTrue(m.isConscious());
    }

    @Test
    void hiringStopsWhenTheBerthsAreFullOrTheMoneyIsShort() {
        docked();
        account().credits = 1;
        CrewHiring.Candidate c = world.crewHiring.pool(game.highport).get(0);
        assertTrue(world.crewHiring.hire(c.key).contains("up front"));
        account().credits = 1_000_000;
        int berths = CrewHiring.berths(game.player);
        assertTrue(berths >= CrewHiring.MIN_BERTHS);
        assertEquals(4, world.crewHiring.aboard(game.player), "the player and three crew to start");
        int[] t = FloorTiles.of(game.player.getComponent(ShipGridComponent.class)).get(0);
        while (world.crewHiring.aboard(game.player) < berths) {
            CrewFactory.spawn(world.crew, game.player, "Extra", CrewRole.CREWMAN, FactionStanding.PLAYER, t[0], t[1]);
        }
        assertTrue(world.crewHiring.hire(c.key).startsWith("No berth"));
    }

    @Test
    void crewCanBeLetGoOnlyWhileDocked() {
        Entity ada = crewNamed("Ada");
        assertTrue(world.crewHiring.dismiss(ada).startsWith("Dock"));
        docked();
        assertNull(world.crewHiring.dismiss(ada));
        assertFalse(world.crewCareer.ownCrew(game.player).contains(ada));
        assertNull(world.crew.crew(ada), "gone from the world");
    }

    // ------------------------------------------------------------------ wages and morale

    @Test
    void wagesComeOutOnPaydayAndUnpaidCrewLoseHeart() {
        Entity ada = crewNamed("Ada"), bea = crewNamed("Bea");
        CrewRecord a = world.crew.crew(ada).record(), b = world.crew.crew(bea).record();
        assertEquals(0f, a.wage, "the starting crew are the service's");
        a.wage = 300f;
        b.wage = 200f;
        account().credits = 400;
        float moraleA = a.morale, moraleB = b.morale;
        assertEquals(500, world.crewCareer.weeklyWages(game.player), 1e-6);

        double paid = world.crewCareer.payWages();
        // Ada comes first and is paid; there is not enough left for Bea
        assertEquals(300, paid, 1e-6);
        assertEquals(100, account().credits, 1e-6);
        assertEquals(moraleA + CrewCareer.PAID_MORALE, a.morale, 1e-6);
        assertEquals(moraleB + CrewCareer.UNPAID_MORALE, b.morale, 1e-6);
        assertTrue(noticed("went unpaid"), noticesText());
    }

    @Test
    void wagesArePaidWithTheBudget() {
        world.crew.crew(crewNamed("Ada")).record().wage = 300f;
        double start = account().credits;
        // The budget's first payday is set a week on, and paid when it comes
        world.budget.pay();
        world.standing.record().nextPay = world.engine.getSimSeconds();
        world.budget.pay();
        assertEquals(start + world.standing.rank().budget - 300, account().credits, 1e-6);
    }

    @Test
    void aShipmateKilledCostsTheRestMoraleAndLowMoraleSlowsWork() {
        Entity ada = crewNamed("Ada"), bea = crewNamed("Bea");
        world.crewCareer.update();
        float before = world.crew.crew(bea).record().morale;
        world.crew.applyDamage(ada, 10_000f, null);
        world.crewCareer.update();
        assertTrue(noticed("Ada is dead"), noticesText());
        // A death is a warning, not a promotion's green (round 9)
        assertEquals(NoticeLog.Kind.WARNING, world.notices.latest().kind);
        assertEquals(before + CrewCareer.DEATH_MORALE, world.crew.crew(bea).record().morale, 1e-6);

        CrewMemberComponent c = world.crew.crew(bea);
        float rate = c.def.repairRate;
        c.record().morale = 0.1f;
        c.record().applyTo(c);
        assertEquals(rate * CrewRecord.LOW_MORALE_WORK, c.def.repairRate, 1e-6);
    }

    @Test
    void moraleDriftsBackTowardSteady() {
        CrewRecord r = new CrewRecord();
        r.morale = 0.2f;
        CrewCareer.drift(r, 2f);
        assertEquals(0.2f + 2 * CrewCareer.MORALE_DRIFT, r.morale, 1e-6);
        CrewCareer.drift(r, 100f);
        assertEquals(CrewRecord.STEADY_MORALE, r.morale, 1e-6, "and stops there");
        r.morale = 1f;
        CrewCareer.drift(r, 1f);
        assertEquals(1f - CrewCareer.MORALE_DRIFT, r.morale, 1e-6, "high spirits settle too");
    }

    // ------------------------------------------------------------------ veterancy

    @Test
    void experienceRaisesSkillsEverMoreSlowlyTowardTheCap() {
        CrewRecord r = new CrewRecord();
        float last = r.engineering, lastGain = Float.MAX_VALUE;
        for (int i = 0; i < 200; i++) {
            r.gain(CrewRecord.Skill.ENGINEERING, 0.05f);
            float gain = r.engineering - last;
            assertTrue(gain <= lastGain + 1e-6, "slower each time");
            assertTrue(r.engineering <= CrewRecord.CAP);
            last = r.engineering;
            lastGain = gain;
        }
        assertEquals(CrewRecord.CAP, r.engineering, 1e-3);
        r.gain(CrewRecord.Skill.GUNNERY, 0.5f);
        assertEquals((CrewRecord.BASELINE + CrewRecord.CAP) / 2, r.gunnery, 1e-6);
        assertEquals(r.gunnery, r.tracking, 1e-6, "tracking rises with gunnery");
    }

    @Test
    void workAndFightingTurnIntoExperience() {
        Entity ada = crewNamed("Ada"), cole = crewNamed("Cole");
        CrewMemberComponent a = world.crew.crew(ada), c = world.crew.crew(cole);
        float baseRepair = a.def.repairRate, baseHit = c.def.hitChance;
        a.record().pendingWork = 600f;
        c.record().pendingHits = 5;
        c.record().pendingDowns = 2;
        world.crewCareer.update();
        assertEquals(600f, a.record().workSeconds, 1e-3);
        assertTrue(a.record().engineering > CrewRecord.BASELINE);
        assertTrue(a.def.repairRate > baseRepair, "a better engineer repairs faster");
        assertEquals(2, c.record().downed);
        assertTrue(c.def.hitChance > baseHit, "a better marine hits more often");
        assertEquals(0f, a.record().pendingWork);
    }

    @Test
    void crewOnTheJobGainExperience() {
        // Knock out a system: Ada the engineer goes and repairs it
        Entity ada = crewNamed("Ada");
        SubsystemComponent broken = null;
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.ENGINE) broken = s;
        }
        assertNotNull(broken);
        broken.health = 0.5f;
        SubsystemComponent target = broken;
        assertTrue(runUntil(240, () -> target.health >= 1f), "repaired");
        run(90);
        assertTrue(world.crew.crew(ada).record().workSeconds > 0f
                || world.crewCareer.ownCrew(game.player).stream().anyMatch(e -> world.crew.crew(e).record().workSeconds > 0f),
                "someone learned from the repair");
    }

    // ------------------------------------------------------------------ prisoners

    /** Two pirate crewmen aboard the raider, one who will offer to join and one who won't. */
    private List<Entity> pirateCrew() {
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        List<Entity> out = new ArrayList<>();
        boolean joiner = false, other = false;
        for (int i = 0; out.size() < 2; i++) {
            String name = "Pirate " + i;
            boolean offers = Math.floorMod((name + CrewRole.CREWMAN).hashCode(), Prisoners.JOIN_ODDS) == 0;
            if (offers ? joiner : other) continue;
            if (offers) joiner = true;
            else other = true;
            int[] t = floor.get(floor.size() - 1 - out.size());
            out.add(CrewFactory.spawn(world.crew, game.raider, name, CrewRole.CREWMAN, pirates, t[0], t[1]));
        }
        return out;
    }

    private void beatLatchAndTake() {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(120);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered"), said);
        assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)), "taken");
    }

    @Test
    void prisonersAreBroughtAboardAndHandedOverOrRecruited() {
        List<Entity> pirates = pirateCrew();
        beatLatchAndTake();
        int boarded = 0;
        for (Entity e : world.crewCareer.ownCrew(game.player)) boarded += world.crew.crew(e).record().boardings;
        assertTrue(boarded >= 1, "whoever went across has a boarding to their name");
        assertSame(game.raider, world.prisoners.takenAlongside());
        assertTrue(world.prisoners.aboard(game.player).isEmpty());

        assertNull(world.prisoners.takeAboard());
        assertEquals(2, world.prisoners.aboard(game.player).size());
        assertEquals(0, Boarding.of(game.raider).prisoners);
        assertEquals(6, world.crewHiring.aboard(game.player), "prisoners take berths");
        assertNull(world.prisoners.option(pirates.get(0)), "nothing to do with them in flight");
        assertTrue(world.prisoners.settleAll().startsWith("Dock"));

        // Home to Highport, a sponsor station: pirates are handed over
        world.docking.undock(game.player);
        run(60);
        assertTrue(game.dockAtHighport());
        assertTrue(runUntil(5, () -> world.docking.dockedWithClearance(game.player)), "docked at Highport");
        Entity joiner = world.prisoners.offersToJoin(pirates.get(0)) ? pirates.get(0) : pirates.get(1);
        Entity other = joiner == pirates.get(0) ? pirates.get(1) : pirates.get(0);
        assertFalse(world.prisoners.offersToJoin(other));
        assertEquals("hand over", world.prisoners.option(other));

        double credits = account().credits;
        float standing = world.standing.standing(world.standing.sponsor());
        assertNull(world.prisoners.settle(other));
        assertEquals(credits + Prisoners.HANDOVER_PAY, account().credits, 1e-6);
        assertEquals(standing + Prisoners.HANDOVER_STANDING, world.standing.standing(world.standing.sponsor()), 1e-3);
        assertNull(world.crew.crew(other), "handed over");

        assertNotNull(world.prisoners.recruit(other), "gone already");
        assertNull(world.prisoners.recruit(joiner));
        CrewMemberComponent j = world.crew.crew(joiner);
        assertFalse(j.held);
        assertEquals(FactionStanding.PLAYER, j.faction);
        assertEquals(Prisoners.JOIN_SKILL, j.record().engineering, 1e-6);
        assertTrue(j.record().wage > 0);
        assertTrue(world.crewCareer.ownCrew(game.player).contains(joiner));
        assertTrue(world.prisoners.aboard(game.player).isEmpty());
    }

    @Test
    void prisonersOfAFactionAtPeaceAreRansomedAtItsStationOrReleased() {
        docked();
        int selene = world.standing.factions().index("selene");
        int[] t = FloorTiles.of(game.player.getComponent(ShipGridComponent.class)).get(0);
        Entity p = CrewFactory.spawn(world.crew, game.player, "Miner", CrewRole.CREWMAN, selene, t[0], t[1]);
        world.crew.crew(p).held = true;
        // Highport is no Selene station, and Selene are no one the Patrol fights: only release
        assertEquals("release", world.prisoners.option(p));
        float standing = world.standing.standing(selene);
        assertNull(world.prisoners.settle(p));
        assertEquals(standing, world.standing.standing(selene), 1e-6, "released far from home: nothing");
        assertNull(world.crew.crew(p));
    }

    // ------------------------------------------------------------------ window, saves and rescue

    @Test
    void theRosterShowsCrewHiresAndPrisoners() {
        docked();
        CrewRoster roster = new CrewRoster(world.context, world.crew, world.macroSim, world.standing, world.crewCareer, world.crewHiring, world.medical, world.prisoners, world.spacewalks);
        CrewProvider.CrewSheet sheet = new CrewProvider.CrewSheet();
        roster.collect(sheet);
        assertTrue(sheet.isOpen());
        assertEquals(3, sheet.crew.size, "Ada, Bea and Cole");
        assertEquals("Service", sheet.crew.get(0).wage);
        assertTrue(sheet.crew.get(0).dismissable, "docked");
        assertFalse(sheet.stationName.isEmpty());
        assertEquals(world.crewHiring.pool(game.highport).size(), sheet.hires.size);
        assertTrue(sheet.summary.startsWith("4 / " + CrewHiring.berths(game.player)), sheet.summary);

        account().credits = 10_000;
        assertEquals("Hired", roster.hire(sheet.hires.get(0).key));
        sheet.clear();
        roster.collect(sheet);
        assertEquals(4, sheet.crew.size);
        assertTrue(sheet.crew.get(3).wage.endsWith("cr/wk"));
        assertTrue(roster.dismiss(sheet.crew.get(3).id).endsWith("leaves the ship"));
    }

    @Test
    void recordsAndHiresComeThroughASave(@TempDir Path dir) {
        docked();
        account().credits = 10_000;
        CrewHiring.Candidate c = world.crewHiring.pool(game.highport).get(0);
        assertNull(world.crewHiring.hire(c.key));
        CrewRecord ada = world.crew.crew(crewNamed("Ada")).record();
        ada.gain(CrewRecord.Skill.ENGINEERING, 0.5f);
        ada.morale = 0.4f;
        ada.shipsBeaten = 3;
        float engineering = ada.engineering;
        FileHandle file = new FileHandle(dir.resolve("crew.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            for (int i = 0; i < 60; i++) fresh.update(FRAME);
            CrewRecord back = null, hire = null;
            for (Entity e : fresh.crewCareer.ownCrew(loaded.player)) {
                CrewMemberComponent m = fresh.crew.crew(e);
                if (m.name.equals("Ada")) back = m.record();
                if (m.name.equals(c.name)) hire = m.record();
            }
            assertNotNull(back);
            assertEquals(engineering, back.engineering, 1e-6);
            assertEquals(3, back.shipsBeaten);
            assertTrue(back.morale > 0.39f && back.morale < 0.45f, "morale " + back.morale);
            assertNotNull(hire, "the hire is still aboard");
            assertEquals(c.record.wage, hire.wage, 1e-6);
            String uuid = loaded.player == null ? null : loaded.highport;
            for (CrewHiring.Candidate o : fresh.crewHiring.pool(uuid)) assertNotEquals(c.key, o.key, "hired already");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aVeteranWhoReachesAPodKeepsTheirRecord() {
        CrewRecord ada = world.crew.crew(crewNamed("Ada")).record();
        ada.gain(CrewRecord.Skill.ENGINEERING, 0.8f);
        ada.wage = 120f;
        float engineering = ada.engineering;
        List<Rescue.Result> rescues = new ArrayList<>();
        world.rescue.rescues.add((signal, r) -> rescues.add(r));
        assertNull(world.abandon.order(game.player));
        PlayerToPod.board(world, game.player);
        assertTrue(runUntil(60, () -> !rescues.isEmpty()), "rescued");
        boolean found = false;
        for (Entity e : world.crewCareer.ownCrew(game.player)) {
            CrewMemberComponent c = world.crew.crew(e);
            if (!c.name.equals("Ada")) continue;
            found = true;
            assertEquals(engineering, c.record().engineering, 1e-6);
            assertEquals(120f, c.record().wage, 1e-6);
            assertEquals(CrewDef.forRole(CrewRole.ENGINEER).repairRate * CrewRecord.factor(engineering), c.def.repairRate, 1e-5);
        }
        assertTrue(found || rescues.get(0).survivors.stream().noneMatch(s -> s.name.equals("Ada")),
                "Ada came through if she reached a pod");
    }

    @Test
    void anOldSaveWithoutRecordsGetsThemFromTheDef() {
        CrewMemberComponent c = new CrewMemberComponent();
        c.role = CrewRole.MARINE;
        c.def = CrewDef.marine();
        assertNull(c.record);
        assertEquals(0.75f, c.record().gunnery, 1e-6, "the marine's gunnery from the def");
        assertEquals(0f, c.record().wage);
        String uuid = game.player.getComponent(UuidComponent.class).id;
        assertNotNull(uuid);
    }
}
