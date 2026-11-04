package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.task.Flight.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FLT's four modes, in the spec's order, and what each one reads. */
class FlightTest {
    private final HudData d = new HudData();
    private final Posts p = new Posts();
    private final Flight f = new Flight();

    @BeforeEach
    void atTheHelm() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
        d.flightStatus.present = true;
    }

    private Mode mode() {
        p.update(d, false);
        f.update(d, p);
        return f.mode;
    }

    /** Highport 800 m off as the navigation target, a port armed and clearance given. */
    private void approach() {
        d.navReadout.present = true;
        d.navReadout.name = "Highport";
        d.navReadout.kind = Kind.STATION;
        d.navReadout.distance = 800;
        d.docking.present = true;
        d.docking.armed = true;
        d.docking.clearance = Clearance.GRANTED;
        d.docking.range = 800f;
        d.docking.closing = 3f;
        d.docking.safeSpeed = 2f;
    }

    @Test
    void cruiseAtRestDockOnAnApproachDepartureDocked() {
        assertEquals(Mode.CRUISE, mode());
        approach();
        assertEquals(Mode.DOCK, mode());
        assertEquals("CLEARED", f.clearance);
        assertEquals(1, f.closingState, "3 m/s against a safe 2 is caution");
        d.ctx.docked = true;
        assertEquals(Mode.DEPARTURE, mode());
    }

    @Test
    void anApproachWithNoPortArmedNoClearanceAndNoDockOrderStaysInCruise() {
        approach();
        d.docking.armed = false;
        d.docking.clearance = Clearance.NONE;
        assertEquals(Mode.CRUISE, mode());
        d.orderState.helm = HelmOrder.DOCK;
        assertEquals(Mode.DOCK, mode());
    }

    @Test
    void aBurnBeingFlownBeatsDockButItsCountdownDoesNot() {
        approach();
        d.navReadout.burnStartIn = 150f;
        d.navReadout.turnIn = 30f;
        assertEquals(Mode.DOCK, mode(), "the countdown is the chronometer's while docking");
        d.navReadout.turnIn = -5f;
        assertEquals(Mode.BURN, mode(), "the turn onto it has begun");
        d.navReadout.turnIn = Float.NaN;
        d.navReadout.burnStartIn = Float.NaN;
        d.navReadout.burning = true;
        assertEquals(Mode.BURN, mode());
    }

    @Test
    void burnFromThreeMinutesBeforeTheEngineLights() {
        d.navReadout.burnStartIn = Flight.BURN_SOON + 1f;
        d.navReadout.turnIn = 61f;
        assertEquals(Mode.CRUISE, mode());
        d.navReadout.burnStartIn = Flight.BURN_SOON - 1f;
        d.navReadout.turnIn = 59f;
        d.navReadout.burnNumber = 1;
        d.navReadout.burnsPlanned = 2;
        d.navReadout.burnDeltaV = 412f;
        assertEquals(Mode.BURN, mode());
        assertEquals("1", f.burnN);
        assertEquals("2", f.burnOf);
        assertEquals("412", f.toGo);
        assertEquals("0:59", f.turnIn);
        assertTrue(f.shows("WARP_TO_BURN"));
    }

    @Test
    void cruiseNamesTheFrameAndMarksHomeOnTheTanks() {
        d.flightStatus.frame = com.ringsofiron.uihud.providers.FlightStatusProvider.Frame.STATION;
        d.flightStatus.frameName = "Highport";
        d.flightStatus.frameDistance = 3_000f;
        d.flightStatus.speed = 0.4f;
        d.flightStatus.deltaVLeft = 7_007;
        d.flightStatus.deltaVHome = 410;
        d.flightStatus.rotationAssist = true;
        d.shipStatus.propellant = 0.76f;
        mode();
        assertEquals("HIGHPORT", f.frame);
        assertEquals("3.0", f.frameKm);
        assertEquals("0.4", f.speed);
        assertEquals("7007", f.dv);
        assertFalse(f.assistOff);
        assertEquals(0.76f, f.dvFrac, 1e-4f);
        assertEquals(0.76f * 410f / 7_007f, f.homeFrac, 1e-4f);
        assertFalse(f.homeShort);
        d.flightStatus.frame = com.ringsofiron.uihud.providers.FlightStatusProvider.Frame.ORBIT;
        d.flightStatus.frameName = "Earth";
        mode();
        assertEquals("ORBIT, EARTH", f.frame);
        assertEquals("", f.frameKm);
    }

    @Test
    void hiddenAtGunneryAndInRailsWarp() {
        d.ctx.presence = Presence.WEAPONS;
        mode();
        assertFalse(f.on);
        d.ctx.presence = Presence.HELM;
        d.ctx.railsWarp = true;
        d.ctx.warp = 100;
        mode();
        assertFalse(f.on);
    }
}
