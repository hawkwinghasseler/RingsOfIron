package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The crisis placard's words (spec round 17): each way out's full cost, and the crew on each fault. */
class CrisisPlacardTest {
    private static final double DAY = 86_400.0;

    private final Orders o = new Orders();
    private final ObjectiveFrame frame = new ObjectiveFrame();
    private final AlertRegistry alerts = new AlertRegistry();

    @BeforeEach
    void starterCorvetteDisabledFarFromHighport() {
        o.towFee = 25_500;
        o.towStanding = 3f;
        o.towDays = 3f;
        o.towStation = "Highport";
        o.abandonStanding = 5f;
        o.abandonShip = "Patrol Corvette";
        alerts.begin(0);
    }

    private Objective contract(State state, double timeLeft) {
        Objective ob = frame.add();
        ob.state = state;
        ob.timeLeft = timeLeft;
        return ob;
    }

    private static String[] array(Array<String> lines) {
        String[] out = new String[lines.size];
        for (int i = 0; i < lines.size; i++) out[i] = lines.get(i);
        return out;
    }

    @Test
    void theTowSaysItsFeeStandingDaysTheContractsThoseDaysRunOutAndWhereItDocks() {
        contract(State.ACTIVE, 2 * DAY);
        contract(State.RETURN, 0.5 * DAY);
        assertArrayEquals(new String[]{
                "Fee 25,500 cr", "Standing -3, 3 days", "Both contracts run out", "Docked at Highport with its cargo and damage"
        }, array(CrisisPlacard.towTerms(o, frame)));
        assertArrayEquals(new String[]{
                "Fee 25,500 cr, standing -3", "3 days; both contracts run out", "Docked at Highport as it is"
        }, array(CrisisPlacard.strandedTerms(o, frame)));
    }

    @Test
    void onlyTheContractsHeldWhoseDeadlineFallsInsideTheDaysRunOut() {
        contract(State.ACTIVE, 2 * DAY);
        contract(State.ACTIVE, 5 * DAY);
        contract(State.ACTIVE, Double.NaN);
        // Finished and failed contracts are not held
        contract(State.DONE, DAY);
        contract(State.FAILED, DAY);
        assertEquals(3, CrisisPlacard.held(frame));
        assertEquals(1, CrisisPlacard.runOut(frame, 3f));
        assertEquals("Fee 25,500 cr", CrisisPlacard.towTerms(o, frame).first());
        assertEquals("1 of 3 contracts runs out", CrisisPlacard.towTerms(o, frame).get(2));
        assertEquals("No contract runs out", CrisisPlacard.runOut(0, 2));
        assertEquals("The contract runs out", CrisisPlacard.runOut(1, 1));
        assertEquals("All 3 contracts run out", CrisisPlacard.runOut(3, 3));
        assertEquals("2 of 3 contracts run out", CrisisPlacard.runOut(2, 3));
    }

    @Test
    void withNoContractHeldTheTowSaysNothingOfContracts() {
        assertArrayEquals(new String[]{
                "Fee 25,500 cr", "Standing -3, 3 days", "Docked at Highport with its cargo and damage"
        }, array(CrisisPlacard.towTerms(o, frame)));
        assertArrayEquals(new String[]{
                "Fee 25,500 cr, standing -3", "3 days on the rails", "Docked at Highport as it is"
        }, array(CrisisPlacard.strandedTerms(o, frame)));
        assertArrayEquals(new String[]{
                "Ship and cargo lost", "Standing -5, 3 days", "A Patrol Corvette free at Highport, credits kept"
        }, array(CrisisPlacard.abandonTerms(o, frame)));
    }

    @Test
    void abandoningSaysTheShipAndCargoGoEveryContractFailsTheStandingAndTheFreeShip() {
        contract(State.ACTIVE, 9 * DAY);
        contract(State.ACTIVE, Double.NaN);
        assertArrayEquals(new String[]{
                "Ship and cargo lost", "Both contracts fail", "Standing -5, 3 days", "A Patrol Corvette free at Highport, credits kept"
        }, array(CrisisPlacard.abandonTerms(o, frame)));
        contract(State.RETURN, DAY);
        assertEquals("All 3 contracts fail", CrisisPlacard.abandonTerms(o, frame).get(1));
        // Where the game can't say which ship, or where
        o.abandonShip = "";
        o.towStation = "";
        assertEquals("A ship free at the sponsor's station, credits kept", CrisisPlacard.abandonTerms(o, frame).peek());
        o.abandonShip = "Ore Hauler";
        assertEquals("An Ore Hauler free at the sponsor's station, credits kept", CrisisPlacard.abandonTerms(o, frame).peek());
    }

    @Test
    void theDaysOnTheRailsReadInDaysOrHours() {
        assertEquals("3 days", CrisisPlacard.days(3f));
        assertEquals("1 day", CrisisPlacard.days(1f));
        assertEquals("12 h", CrisisPlacard.days(0.5f));
    }

    private static SystemRow fault(String system, Fault fault, float timer) {
        SystemRow r = new SystemRow();
        r.system = system;
        r.fault = fault;
        r.timer = timer;
        r.tileX = 4;
        r.tileY = 2;
        return r;
    }

    private Alert onIt(String id) {
        return alerts.raise(id, Level.WARNING, Category.ENGINEERING);
    }

    @Test
    void aShortSaysWhoIsGoingToItTheWorksOwnTimeAndWhenTheBreakerReclosesByItself() {
        SystemRow r = fault("REACTOR", Fault.SHORTED, 38f);
        Alert a = onIt("sys:REACTOR:4,2").handling(1).crew("Ada", false, 5f);
        assertArrayEquals(new String[]{
                "ADA GOING TO THE REACTOR'S SHORT, 5 S TO RESET IT THERE;", "THE BREAKER RECLOSES BY ITSELF IN 38 S"
        }, CrisisPlacard.faultWords(r, a));
        a.crew("Ada", true, 2.4f);
        assertArrayEquals(new String[]{
                "ADA RESETTING THE REACTOR'S SHORT, 3 S;", "THE BREAKER RECLOSES BY ITSELF IN 38 S"
        }, CrisisPlacard.faultWords(r, a));
        a.handling(0);
        assertEquals("REACTOR SHORTED, NOBODY ON IT;", CrisisPlacard.faultWords(r, a)[0]);
        a.handling(2);
        assertEquals("REACTOR SHORTED, 2 CREW ON IT;", CrisisPlacard.faultWords(r, a)[0]);
        // Without the alert the game can't say who
        assertEquals("REACTOR SHORTED;", CrisisPlacard.faultWords(r, null)[0]);
    }

    @Test
    void damageSaysWhoIsRepairingItAndAWreckIsRepairedToo() {
        SystemRow r = fault("ENGINE", Fault.DISABLED, Float.NaN);
        Alert a = onIt("sys:ENGINE:4,2").handling(1).crew("Cole", true, 40f);
        assertArrayEquals(new String[]{"COLE REPAIRING THE ENGINE, 40 S", ""}, CrisisPlacard.faultWords(r, a));
        a.crew("Cole", false, Float.NaN);
        assertArrayEquals(new String[]{"COLE GOING TO THE ENGINE", ""}, CrisisPlacard.faultWords(r, a));
        assertArrayEquals(new String[]{"ENGINE DAMAGED, NEEDS REPAIR", ""}, CrisisPlacard.faultWords(r, null));
        // The crew repair a wrecked system too, from nothing
        r.fault = Fault.DESTROYED;
        assertArrayEquals(new String[]{"ENGINE WRECKED, NEEDS REPAIR", ""}, CrisisPlacard.faultWords(r, null));
        a.handling(0);
        assertArrayEquals(new String[]{"ENGINE WRECKED, NOBODY ON IT", ""}, CrisisPlacard.faultWords(r, a));
        a.handling(1).crew("Cole", true, 55f);
        assertArrayEquals(new String[]{"COLE REPAIRING THE ENGINE, 55 S", ""}, CrisisPlacard.faultWords(r, a));
        r.fault = Fault.STUNNED;
        r.timer = 12f;
        assertArrayEquals(new String[]{"ENGINE STUNNED;", "IT WEARS OFF IN 12 S"}, CrisisPlacard.faultWords(r, null));
    }

    @Test
    void withNoFaultToWorkTheTubeSaysASwitchedOffReactorOrOneBackOnLine() {
        PowerFrame power = new PowerFrame();
        String[] nothing = {"REACTOR DOWN", "NOTHING CLEARS IT\nBY ITSELF"};
        String[] back = {"REACTOR BACK ON LINE", "POWER COMING BACK"};
        assertArrayEquals(nothing, CrisisPlacard.downWords(power, "reactor down", false));
        assertArrayEquals(new String[]{"CREW DOWN", "NOTHING CLEARS IT\nBY ITSELF"}, CrisisPlacard.downWords(power, "crew down", true));
        // Mended past the line where it runs, its fault gone a moment before the game counts the ship
        // as up (the tour found the tube saying nothing would clear it then), or feeding again
        assertArrayEquals(back, CrisisPlacard.downWords(power, "reactor down", true));
        power.outputWatts = 1e6;
        assertArrayEquals(back, CrisisPlacard.downWords(power, "reactor down", false));
        power.outputWatts = 0;
        SystemRow off = power.addFault();
        off.system = "REACTOR";
        off.fault = Fault.OFF;
        assertArrayEquals(new String[]{"REACTOR SWITCHED OFF", "SWITCH IT ON AT THE\nSHIP'S CONSOLE"},
                CrisisPlacard.downWords(power, "reactor down", true));
    }
}
