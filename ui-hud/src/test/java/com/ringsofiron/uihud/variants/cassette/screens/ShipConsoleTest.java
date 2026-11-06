package com.ringsofiron.uihud.variants.cassette.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import org.junit.jupiter.api.Test;

/** The ship console's Systems and Cargo tabs, and the fight's record the log keeps. */
class ShipConsoleTest {

    private static SubsystemComponent system(SubsystemType type, int x, int y, PowerCircuit circuit) {
        MountFootprint.MountKind kind = type.mountKind;
        SubsystemComponent s = new SubsystemComponent(new MountFootprint(kind, MountFootprint.MountSize.S, x, y, 0), type);
        s.circuit = circuit;
        s.powered = circuit != null;
        s.isOperational = circuit != null;
        return s;
    }

    /** A reactor, two engines (one damaged and dark), a gun switched off and a stunned life support. */
    private static Array<SubsystemComponent> ship(ShipGridComponent grid) {
        PowerCircuit c = new PowerCircuit();
        grid.circuits.add(c);
        Array<SubsystemComponent> out = new Array<>();
        out.add(system(SubsystemType.REACTOR, 1, 1, c));
        SubsystemComponent e1 = system(SubsystemType.ENGINE, 3, 1, c), e2 = system(SubsystemType.ENGINE, 5, 1, null);
        e2.health = 0.2f;
        out.add(e1);
        out.add(e2);
        SubsystemComponent gun = system(SubsystemType.TURRET_MOUNT, 7, 1, c);
        gun.switchedOn = false;
        gun.isOperational = false;
        out.add(gun);
        SubsystemComponent life = system(SubsystemType.LIFE_SUPPORT, 1, 5, c);
        life.stunned = 5f;
        out.add(life);
        return out;
    }

    private static Array<Mount> mounts() {
        WeaponFrame f = new WeaponFrame();
        Mount m = f.add();
        m.name = "FWD GUN";
        m.weapon = "Autocannon";
        m.tileX = 7;
        m.tileY = 1;
        return f.mounts;
    }

    @Test
    void systemsAreListedByKindWithTheirStates() {
        ShipGridComponent grid = new ShipGridComponent(12, 12, 1f);
        ShipSystems s = new ShipSystems();
        s.read(ship(grid), grid, (x, y) -> y >= 5 ? "LIFE SUP" : "ENGINES", mounts());
        assertEquals(4, s.rows.size, "the reactor, the engines' group, the gun, life support");
        ShipSystems.Row reactor = s.rows.get(0), engines = s.rows.get(1), gun = s.rows.get(2), life = s.rows.get(3);
        assertEquals("REACTOR", reactor.name);
        assertEquals("—", reactor.power, "a reactor makes its power");
        assertTrue(reactor.guarded, "switching the reactor off asks twice");
        assertEquals(ShipSystems.Row.GROUP, engines.kind);
        assertEquals("ENGINE ×2", engines.name);
        assertEquals("ENGINES", engines.where, "both in one room");
        assertEquals("1 DAMAGED · 1 ON", engines.state);
        assertEquals("SOME", engines.power, "one of them has no circuit");
        assertEquals(0.2f, engines.health, 1e-6, "a group shows its worst");
        assertEquals("AUTOCANNON", gun.name, "a gun by its weapon");
        assertEquals("FWD GUN", gun.where, "and its mount");
        assertEquals("OFF", gun.state);
        assertEquals("STUNNED 5 S", life.state);
        assertEquals("C1", life.power, "a powered system names its circuit");
        assertFalse(reactor.worst.bad());
        assertTrue(engines.worst.bad());
    }

    @Test
    void aGroupSwitchesTogetherAndOpensToItsMembers() {
        ShipGridComponent grid = new ShipGridComponent(12, 12, 1f);
        Array<SubsystemComponent> systems = ship(grid);
        ShipSystems s = new ShipSystems();
        s.read(systems, grid, (x, y) -> "", mounts());
        assertEquals("Every engine switched off", s.toggle(1));
        assertFalse(systems.get(1).switchedOn);
        assertFalse(systems.get(2).switchedOn);
        s.read(systems, grid, (x, y) -> "", mounts());
        // Members that disagree are all switched on; once all are on, all off (Context 10.2, v18)
        systems.get(1).switchedOn = true;
        s.read(systems, grid, (x, y) -> "", mounts());
        assertEquals("Every engine switched on", s.toggle(1));
        assertTrue(systems.get(2).switchedOn);
        // Opened, the group lists each engine with its own switch
        assertEquals(1, s.rowOf(systems.get(2)), "a shut group's member selects the group");
        s.openOrShut(1);
        s.read(systems, grid, (x, y) -> "", mounts());
        assertEquals(6, s.rows.size);
        assertEquals(ShipSystems.Row.MEMBER, s.rows.get(2).kind);
        assertEquals(3, s.rowOf(systems.get(2)), "an open group's member selects its own row");
        assertEquals("STBD", s.rows.get(3).where, "an engine outside every room by its side of the keel");
        assertEquals("Engine (STBD) switched off", s.toggle(3));
        assertTrue(systems.get(1).switchedOn, "the other engine stays on");
        // The grid's +y is port: a system above the middle line is on the port side
        SubsystemComponent port = system(SubsystemType.ENGINE, 3, 9, null);
        assertEquals("PORT", ShipSystems.side(port, grid));
        int middle = (grid.height - port.footprint.height()) / 2;
        assertEquals("", ShipSystems.side(system(SubsystemType.ENGINE, 3, middle, null), grid), "on the middle line");
    }

    @Test
    void aGroupsStateFitsItsColumnAndADownGunIsNamedByItsMount() {
        assertEquals("1 SHORTED · 1 ON", SystemsPage.fit("1 SHORTED · 1 ON", 16));
        assertEquals("1 SHORTED", SystemsPage.fit("1 SHORTED · 1 ON", 14), "the worst counts first, the rest go");
        assertEquals("1 DAMAGED · 1 STUNNED", SystemsPage.fit("1 DAMAGED · 1 STUNNED · 2 ON", 22));
        assertEquals("STUNNED 1…", SystemsPage.fit("STUNNED 12 S", 10), "one state is cut as any text is");
        AlertRegistry alerts = new AlertRegistry();
        alerts.begin(0);
        alerts.raise("sys:TURRET:7,1", Level.CAUTION, Category.ENGINEERING).set("TURRET", "TURRET in HOLD: shorted")
                .room("HOLD").tile(7, 1);
        alerts.end();
        ConditionLog log = new ConditionLog();
        log.update(alerts, mounts(), 0);
        log.update(alerts, mounts(), ConditionLog.HOLD);
        assertEquals("System down: FWD GUN", log.entries.peek().text, "a gun by its mount, as F2 names it");
        assertEquals("TURRET (HOLD)", ConditionLog.where(alerts.get("sys:TURRET:7,1")), "with no mounts, the system and its room");
    }

    @Test
    void rcsBlocksAreOneRowWithNoSwitch() {
        ShipGridComponent grid = new ShipGridComponent(12, 12, 1f);
        grid.rcsThrusters.add(new com.ringsofiron.interiorgrid.RcsThruster(0, 0, com.ringsofiron.interiorgrid.Side.LEFT,
                com.ringsofiron.interiorgrid.RcsThruster.Kind.values()[0]));
        ShipSystems s = new ShipSystems();
        s.read(ship(grid), grid, (x, y) -> "", mounts());
        ShipSystems.Row rcs = s.rows.get(2);
        assertEquals(ShipSystems.Row.RCS, rcs.kind, "after the engines");
        assertEquals("RCS ×1", rcs.name);
        assertEquals("0 OF 1 FIRING", rcs.state, "its hull tile is gone");
        assertEquals(null, s.toggle(2), "no switch");
    }

    @Test
    void theDamageControlBoardListsWarningsFirst() {
        AlertRegistry alerts = new AlertRegistry();
        alerts.begin(10);
        alerts.raise("smoke:HOLD", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in HOLD").room("HOLD");
        alerts.raise("fire:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in BRIDGE").room("BRIDGE").handling(2);
        alerts.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
        alerts.end();
        Array<AlertRegistry.Alert> jobs = new Array<>();
        SystemsPage.jobs(alerts, jobs);
        assertEquals(2, jobs.size, "a hostile lock is no crew job");
        assertEquals("FIRE", jobs.get(0).code);
        assertEquals("2 CREW", SystemsPage.who(jobs.get(0)));
        assertEquals("—", SystemsPage.who(jobs.get(1)), "the game can't say");
        assertEquals("FIRE, 1 TILE", SystemsPage.task(jobs.get(0), null), "a fire by its tiles burning");
        assertEquals("BRIDGE", SystemsPage.place(jobs.get(0), null));
        assertEquals("3.0 MW", SystemsPage.watts(3.0e6));
        assertEquals("850 KW", SystemsPage.watts(850e3));
    }

    @Test
    void aFireIsSizedInTilesAndSaysWhetherItSpreadsAgainstTenSecondsBefore() {
        AlertRegistry alerts = new AlertRegistry();
        FireTrend fires = new FireTrend();
        int[] tiles = {1, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2};
        for (int t = 0; t < tiles.length; t++) {
            alerts.begin(t);
            alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD").count(tiles[t]);
            alerts.end();
            fires.update(alerts, t);
            if (t == 2) {
                assertEquals("FIRE, 3 TILES", SystemsPage.task(alerts.get("fire:HOLD"), null));
                assertEquals("spreading", fires.trend("fire:HOLD", 3, t), "young, against its first tile");
            }
            if (t == 12) assertEquals("", fires.trend("fire:HOLD", 3, t), "3 tiles now and 10 s before: holding");
            if (t == 13) assertEquals("shrinking", fires.trend("fire:HOLD", 2, t), "2 against the 3 at 3 s");
        }
        assertEquals("shrinking", fires.trend("fire:HOLD", 1, 14));
        // Out, it is forgotten
        alerts.begin(15);
        alerts.end();
        fires.update(alerts, 15);
        assertEquals("", fires.trend("fire:HOLD", 2, 15));
    }

    @Test
    void lifeSupportSaysWhichRoomsGetAirAndWhyNotTheRest() {
        Array<String> rooms = names("BRIDGE", "HOLD");
        assertEquals("AIR TO BRIDGE AND HOLD", SystemsPage.air(1, rooms, names("BRIDGE", "HOLD"), names()));
        assertEquals("AIR TO THE HOLD ONLY: BRIDGE VENTING", SystemsPage.air(1, rooms, names("HOLD"), names("BRIDGE")));
        assertEquals("AIR TO THE BRIDGE ONLY: HOLD BEHIND A SHUT DOOR", SystemsPage.air(1, rooms, names("BRIDGE"), names()));
        assertEquals("NO AIR: BRIDGE VENTING, HOLD BEHIND A SHUT DOOR", SystemsPage.air(1, rooms, names(), names("BRIDGE")));
        assertEquals("NO AIR: LIFE SUPPORT DOWN", SystemsPage.air(1, rooms, names(), names()));
        assertEquals("NO LIFE SUPPORT ABOARD", SystemsPage.air(0, rooms, names(), names()));
        Array<String> gunship = names("ENGINES", "REACTOR", "BRIDGE", "AFT", "FWD");
        assertEquals("AIR TO ALL 5 ROOMS", SystemsPage.air(1, gunship, gunship, names()));
        assertEquals("AIR TO ENGINES, REACTOR AND BRIDGE ONLY: AFT AND FWD BEHIND SHUT DOORS",
                SystemsPage.air(1, gunship, names("ENGINES", "REACTOR", "BRIDGE"), names()));
        assertEquals("O2 TANKS 72%", SystemsPage.tanks(0.72f, Double.NaN));
        assertEquals("EMERGENCY O2 42 KG", SystemsPage.tanks(0f, 42.4));
        assertEquals("", SystemsPage.tanks(Float.NaN, Double.NaN), "a ship with no oxygen tanks");
    }

    private static Array<String> names(String... n) {
        Array<String> a = new Array<>();
        a.addAll(n);
        return a;
    }

    @Test
    void aSwitchedOffSystemIsNoJobAndARoomsAirIsOne() {
        ShipGridComponent grid = new ShipGridComponent(12, 12, 1f);
        Array<SubsystemComponent> systems = ship(grid);
        systems.get(1).shorted = true;
        systems.get(1).isOperational = false;
        ShipSystems s = new ShipSystems();
        s.read(systems, grid, (x, y) -> "ENGINES", mounts());
        AlertRegistry alerts = new AlertRegistry();
        alerts.begin(10);
        alerts.raise("sys:TURRET:7,1", Level.ADVISORY, Category.ENGINEERING).set("TURRET", "TURRET in ENGINES: off")
                .room("ENGINES").tile(7, 1);
        alerts.raise("sys:ENGINE:3,1", Level.CAUTION, Category.ENGINEERING).set("ENGINE", "ENGINE in ENGINES: shorted")
                .room("ENGINES").tile(3, 1);
        alerts.raise("breach:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in HOLD").room("HOLD");
        alerts.raise("air:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("VACUUM", "HOLD is in vacuum").room("HOLD");
        alerts.raise("casualty:Ada", Level.WARNING, Category.CREW).set("CASUALTY", "Ada is critical");
        alerts.end();
        Array<AlertRegistry.Alert> jobs = new Array<>();
        SystemsPage.jobs(alerts, jobs);
        assertEquals(3, jobs.size, "the gun switched off is the crew's own choice, and the hold's vacuum waits on its breach");
        assertEquals("HULL BREACH", SystemsPage.task(jobs.get(0), s));
        assertEquals("CREW CRITICAL", SystemsPage.task(jobs.get(1), s));
        assertEquals("ADA", SystemsPage.place(jobs.get(1), s), "a casualty by name");
        assertEquals("ENGINE SHORTED", SystemsPage.task(jobs.get(2), s), "a system by its name and state");
        assertEquals("ENGINES", SystemsPage.place(jobs.get(2), s));
        // And the log keeps no record of the switch
        ConditionLog log = new ConditionLog();
        log.update(alerts, 10);
        assertEquals(2, log.entries.size, "the hazards at once; the engine's fault once it has held a moment");
        log.update(alerts, 10 + ConditionLog.HOLD);
        for (int i = 0; i < log.entries.size; i++) assertFalse(log.entries.get(i).text.contains("TURRET"), log.entries.get(i).text);
        assertEquals(3, log.entries.size, "the breach, the shorted engine, the casualty");
    }

    @Test
    void theCargoTabSumsTheHoldByCommodity() {
        CargoHoldComponent hold = new CargoHoldComponent(2f, new int[]{0, 0, 1, 0, 2, 0, 3, 0});
        hold.load("water", 2, 1, 1000);
        hold.load("part:autocannon", 1, 1, 400);
        hold.load("food", 3, 1, 100);
        TradeBoard trade = new TradeBoard();
        TradeLine l = trade.addLine();
        l.commodityId = "food";
        l.name = "Food rations";
        Array<CargoPage.Line> lines = new Array<>(), pool = new Array<>();
        CargoPage.read(hold, trade, lines, pool);
        assertEquals(3, lines.size);
        assertEquals("Food rations", lines.get(0).name, "the market's name where it trades it");
        assertEquals("Water", lines.get(1).name, "else the id in words");
        assertEquals("Salvaged autocannon", lines.get(2).name, "the parts last");
        assertTrue(lines.get(2).part);
        assertEquals(2000, lines.get(1).mass, 1e-6);
        assertEquals("1.6 T", CargoPage.mass(1600));
        assertEquals("850 KG", CargoPage.mass(850));
        assertEquals("12.0 M³", CargoPage.volume(12));
    }

    @Test
    void aRoomsAirIsOneCourseInTheLog() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        // Holed: the breach and the vacuum at once, and the breach speaks
        frame(alerts, log, 10, "breach:HOLD", "BREACH", Level.WARNING, "air:HOLD", "VACUUM", Level.WARNING);
        frame(alerts, log, 13, "breach:HOLD", "BREACH", Level.WARNING, "air:HOLD", "VACUUM", Level.WARNING);
        // Sealed, still airless; then refilling
        frame(alerts, log, 20, "air:HOLD", "VACUUM", Level.WARNING, null, null, null);
        frame(alerts, log, 30, "air:HOLD", "PRESSURE", Level.CAUTION, null, null, null);
        frame(alerts, log, 40, null, null, null, null, null, null);
        assertEquals(3, log.entries.size, "the clearing waits a moment");
        frame(alerts, log, 43, null, null, null, null, null, null);
        assertEquals(4, log.entries.size);
        assertEquals("Hull breach: HOLD", log.entries.get(0).text);
        assertEquals("Vacuum: HOLD", log.entries.get(1).text);
        assertEquals("Low pressure: HOLD", log.entries.get(2).text);
        assertEquals("Low pressure: HOLD, cleared after 0:30", log.entries.get(3).text, "timed from the first");
        assertEquals("ENGINEERING", log.entries.get(0).source);
        assertEquals(Level.CAUTION, log.entries.get(3).level, "at the row's severity");
    }

    @Test
    void missilesAndAFlickerInTheLog() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        alerts.begin(5);
        alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2);
        alerts.end();
        log.update(alerts, 5);
        assertEquals(2, log.entries.size, "each missile fired at the ship");
        assertEquals("GUNNERY", log.entries.get(0).source);
        alerts.begin(9);
        alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "1 missile inbound").count(1);
        alerts.end();
        log.update(alerts, 9);
        assertEquals("Missile gone", log.entries.peek().text);
        frame(alerts, log, 12, null, null, null, null, null, null);
        assertEquals(4, log.entries.size, "the last one gone too");
        // A fire out for a moment and back is one fire
        frame(alerts, log, 20, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        assertEquals("Fire: HOLD", log.entries.peek().text, "a hazard at once: the crew can have it out inside two seconds");
        frame(alerts, log, 21, null, null, null, null, null, null);
        frame(alerts, log, 22, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        frame(alerts, log, 23, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        int n = log.entries.size;
        frame(alerts, log, 24, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        assertEquals(n, log.entries.size);
        assertEquals("Fire: HOLD", log.entries.peek().text, "no clearing for a flicker");
        assertEquals(20, log.entries.peek().time, 1e-9, "at the time it began");
        // A blip, gone before it has held a moment, leaves no line: a system just switched on, dark
        // until the power routing's next tick
        frame(alerts, log, 26, null, null, null, null, null, null);
        frame(alerts, log, 29, null, null, null, null, null, null);
        assertEquals("Fire: HOLD, cleared after 0:06", log.entries.peek().text);
        n = log.entries.size;
        frame(alerts, log, 30, "sys:TURRET:7,1", "TURRET", Level.CAUTION, null, null, null);
        frame(alerts, log, 31.5, "sys:TURRET:7,1", "TURRET", Level.CAUTION, null, null, null);
        frame(alerts, log, 32, null, null, null, null, null, null);
        frame(alerts, log, 35, null, null, null, null, null, null);
        assertEquals(n, log.entries.size);
        assertEquals("11:04", ConditionLog.duration(664));
        assertEquals("1:02:03", ConditionLog.duration(3723));
    }

    @Test
    void theLogsSeverityIsAThreshold() {
        assertTrue(LogPage.passes(0, Level.ADVISORY, false));
        assertTrue(LogPage.passes(1, Level.ADVISORY, true), "notable: good news");
        assertFalse(LogPage.passes(1, Level.ADVISORY, false));
        assertTrue(LogPage.passes(1, Level.CAUTION, false), "and up");
        assertFalse(LogPage.passes(2, Level.ADVISORY, true));
        assertTrue(LogPage.passes(2, Level.WARNING, false));
        assertFalse(LogPage.passes(3, Level.CAUTION, false));
        assertTrue(LogPage.passes(3, Level.WARNING, false));
    }

    @Test
    void theLogMergesTheRecordWithTheNoticesAndSortsBySource() {
        com.ringsofiron.uihud.data.HudData d = new com.ringsofiron.uihud.data.HudData();
        Boards b = new Boards();
        b.data = d;
        d.messages.clear();
        d.messages.add("Third", Level.ADVISORY, 180);
        d.messages.add("Second", Level.ADVISORY, 120);
        d.messages.add("First", Level.ADVISORY, 60);
        ConditionLog record = new ConditionLog();
        frame(d.alerts, record, 90, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        frame(d.alerts, record, 93, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        LogPage log = new LogPage(record);
        log.refresh(b);
        assertEquals(4, log.lines());
        assertEquals("Third", log.line(0), "the newest at the platen");
        assertEquals("Fire: HOLD", log.line(2), "the fire between the notices it came between");
        assertEquals("ENGINEERING", log.lineSource(2));
        assertEquals("", log.lineSource(0), "a notice has no source yet");
        // One source: only the record's lines from it, the notices under all only
        log.hit(11);
        assertEquals(1, log.lines());
        assertEquals("Fire: HOLD", log.line(0));
        log.hit(11);
        assertEquals(4, log.lines(), "round to all again");
        // Warnings only
        log.hit(3);
        assertEquals(1, log.lines());
    }

    /** One frame of the game's alerts: up to two raised, then the log reads them. */
    private static void frame(AlertRegistry alerts, ConditionLog log, double now, String id1, String code1, Level l1,
                              String id2, String code2, Level l2) {
        alerts.begin(now);
        if (id1 != null) raise(alerts, id1, code1, l1);
        if (id2 != null) raise(alerts, id2, code2, l2);
        alerts.end();
        log.update(alerts, now);
    }

    private static void raise(AlertRegistry alerts, String id, String code, Level level) {
        String room = id.substring(id.indexOf(':') + 1);
        alerts.raise(id, level, Category.LIFE_SUPPORT).set(code, code + " in " + room).room(room);
    }
}
