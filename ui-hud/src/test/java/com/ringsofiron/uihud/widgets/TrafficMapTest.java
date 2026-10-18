package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.MapBody;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TrafficMapTest {

    @Test
    void niceStepRoundsDownToOneTwoOrFive() {
        assertEquals(1000, TrafficMap.niceStep(1999), 1e-9);
        assertEquals(2000, TrafficMap.niceStep(2000), 1e-9);
        assertEquals(2000, TrafficMap.niceStep(4999), 1e-9);
        assertEquals(5e6, TrafficMap.niceStep(9.9e6), 1e-3);
        assertEquals(1, TrafficMap.niceStep(0), 1e-9);
    }

    @Test
    void fitRadiusCoversFarthestContactAndBody() {
        TrafficFrame f = new TrafficFrame();
        f.frameRadius = 1000;
        assertEquals(1500, TrafficMap.fitRadius(f), 1e-9);
        Contact c = f.addContact();
        c.x = 3000;
        c.y = 4000;
        assertEquals(5000 * 1.15, TrafficMap.fitRadius(f), 1e-9);
        MapBody b = f.addBody();
        b.x = 10_000;
        b.radius = 500;
        assertEquals(10_500, TrafficMap.fitRadius(f), 1e-9);
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
        assertEquals("500 m", TrafficMap.formatDistance(500));
        assertEquals("400 km", TrafficMap.formatDistance(400e3));
        assertEquals("2,000 km", TrafficMap.formatDistance(2e6));
    }

    @Test
    void labelsAtTheSameSpotStackDownward() {
        FloatArray placed = new FloatArray();
        assertEquals(100f, TrafficMap.labelY(placed, 10f, 100f, 28f), 1e-6);
        assertEquals(70f, TrafficMap.labelY(placed, 12f, 100f, 14f), 1e-6);
        assertEquals(54f, TrafficMap.labelY(placed, 10f, 95f, 14f), 1e-6);
        assertEquals(100f, TrafficMap.labelY(placed, 400f, 100f, 14f), 1e-6);
    }
}
