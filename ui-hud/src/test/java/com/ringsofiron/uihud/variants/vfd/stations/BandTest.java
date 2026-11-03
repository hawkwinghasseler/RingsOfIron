package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandTest {

    @Test
    void theApproachHasTheClusterOnlyForTheNavTargetsStationOrAnArmedPort() {
        DockingApproach dk = new DockingApproach();
        NavReadout nav = new NavReadout();
        dk.present = true;
        dk.capture = Capture.APPROACH;
        dk.other = "Pirate raider 6";
        // A raider's port in guidance range is no approach of ours
        assertFalse(Band.approach(dk, nav));
        // Highport, the nav target, with a free port in range is
        dk.other = "Highport";
        nav.present = true;
        nav.kind = Kind.STATION;
        nav.name = "Highport";
        assertTrue(Band.approach(dk, nav));
        // A ship as the nav target is not, unless the port is armed for it
        nav.kind = Kind.SHIP;
        assertFalse(Band.approach(dk, nav));
        dk.armed = true;
        assertTrue(Band.approach(dk, nav));
        // Caught by the collars, it holds the cluster whatever the target
        dk.armed = false;
        dk.capture = Capture.CAPTURED;
        assertTrue(Band.approach(dk, nav));
        dk.capture = Capture.FREE;
        assertFalse(Band.approach(dk, nav));
    }
}
