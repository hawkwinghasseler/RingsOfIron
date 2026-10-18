package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatOverlayTest {

    private static MissileTrack missile(ThreatFrame f, Side side, float tti) {
        MissileTrack m = f.add();
        m.side = side;
        m.timeToImpact = tti;
        return m;
    }

    @Test
    void noBannerWithoutIncomingMissiles() {
        ThreatFrame f = new ThreatFrame();
        missile(f, Side.OWN, 3f);
        missile(f, Side.OTHER, 2f);
        assertNull(ThreatOverlay.bannerText(f));
        assertFalse(ThreatOverlay.urgent(f));
    }

    @Test
    void bannerCountsIncomingAndShowsSoonestImpact() {
        ThreatFrame f = new ThreatFrame();
        missile(f, Side.INCOMING, 12f);
        missile(f, Side.INCOMING, 4.25f);
        missile(f, Side.INCOMING, Float.NaN);
        missile(f, Side.OWN, 1f);
        assertEquals(3, f.incoming());
        assertEquals(4.25f, f.soonestImpact(), 1e-6);
        assertEquals("MISSILE WARNING   3 inbound   impact 4.3 s", ThreatOverlay.bannerText(f));
        assertTrue(ThreatOverlay.urgent(f));
    }

    @Test
    void incomingThatIsNotClosingHasNoImpactTime() {
        ThreatFrame f = new ThreatFrame();
        missile(f, Side.INCOMING, Float.NaN);
        assertEquals("MISSILE WARNING   1 inbound", ThreatOverlay.bannerText(f));
        assertFalse(ThreatOverlay.urgent(f));
    }

    @Test
    void pooledTracksAreReset() {
        ThreatFrame f = new ThreatFrame();
        MissileTrack m = missile(f, Side.INCOMING, 2f);
        m.burning = true;
        f.clear();
        MissileTrack again = f.add();
        assertEquals(Side.OTHER, again.side);
        assertTrue(Float.isNaN(again.timeToImpact));
        assertFalse(again.burning);
    }
}
