package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.MapBody;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficLayerTest {

    @Test
    void niceStepRoundsDownToOneTwoOrFive() {
        assertEquals(1000, TrafficLayer.niceStep(1999), 1e-9);
        assertEquals(2000, TrafficLayer.niceStep(2000), 1e-9);
        assertEquals(2000, TrafficLayer.niceStep(4999), 1e-9);
        assertEquals(5e6, TrafficLayer.niceStep(9.9e6), 1e-3);
        assertEquals(1, TrafficLayer.niceStep(0), 1e-9);
    }

    @Test
    void fitRadiusCoversFarthestContactAndBody() {
        TrafficFrame f = new TrafficFrame();
        f.frameRadius = 1000;
        assertEquals(1500, TrafficLayer.fitRadius(f), 1e-9);
        Contact c = f.addContact();
        c.x = 3000;
        c.y = 4000;
        assertEquals(5000 * 1.15, TrafficLayer.fitRadius(f), 1e-9);
        MapBody b = f.addBody();
        b.x = 10_000;
        b.radius = 500;
        assertEquals(10_500, TrafficLayer.fitRadius(f), 1e-9);
    }

    @Test
    void aLastKnownPositionDoesNotStretchTheMap() {
        TrafficFrame f = new TrafficFrame();
        f.frameRadius = 1000;
        Contact c = f.addContact();
        c.x = 1e7;
        c.stale = true;
        assertEquals(1500, TrafficLayer.fitRadius(f), 1e-9);
    }

    @Test
    void frameReusesPooledEntriesAndResetsDetail() {
        TrafficFrame f = new TrafficFrame();
        Contact first = f.addContact();
        first.kind = ContactKind.HAULER;
        first.detail = "to Highport";
        assertEquals(1, f.count(ContactKind.HAULER));
        f.clear();
        assertEquals(0, f.contacts.size);
        Contact again = f.addContact();
        assertSame(first, again);
        assertEquals("", again.detail);
    }

    @Test
    void formatsDistances() {
        assertEquals("500 m", TrafficLayer.formatDistance(500));
        assertEquals("400 km", TrafficLayer.formatDistance(400e3));
        assertEquals("2,000 km", TrafficLayer.formatDistance(2e6));
    }

    @Test
    void labelsAtTheSameSpotStackDownward() {
        FloatArray placed = new FloatArray();
        assertEquals(100f, TrafficLayer.labelY(placed, 10f, 100f, 120f, 28f), 1e-6);
        assertEquals(70f, TrafficLayer.labelY(placed, 12f, 100f, 120f, 14f), 1e-6);
        assertEquals(54f, TrafficLayer.labelY(placed, 10f, 95f, 120f, 14f), 1e-6);
        assertEquals(100f, TrafficLayer.labelY(placed, 400f, 100f, 120f, 14f), 1e-6);
        assertEquals(100f, TrafficLayer.labelY(placed, 140f, 100f, 120f, 14f), 1e-6, "beside the first, clear of it");
    }

    @Test
    void theLayerOnlyDrawsAroundTheOrbitMapsOwnBody() {
        TrafficLayer layer = new TrafficLayer(null, null);
        assertFalse(layer.matches("Earth"), "no traffic yet");
        layer.setProvider(f -> {
            f.frameName = "Earth";
            f.frameRadius = 6.371e6;
            f.addContact().kind = ContactKind.STATION;
            f.addContact().kind = ContactKind.HAULER;
            f.addContact().kind = ContactKind.HOSTILE;
            f.addRegion("Selene Mining Combine: 60 HIGH", true);
        });
        layer.refresh();
        assertTrue(layer.matches("Earth"));
        assertFalse(layer.matches("Moon"), "around another body the two would not line up");
        assertEquals("1 stations   1 haulers   1 pirates", layer.counts());
        assertEquals(2, layer.regionLines(), "a heading and a line per region");
        assertNull(layer.pick(0, 0), "nothing drawn, nothing to pick");
    }
}
