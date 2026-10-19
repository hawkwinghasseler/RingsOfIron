package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShipGaugesTest {

    @Test
    void barsTurnAmberThenRedAsTheyEmpty() {
        assertSame(HudSkin.HUD_CYAN, ShipGauges.levelColor(0.8f));
        assertSame(HudSkin.HUD_WARN, ShipGauges.levelColor(0.4f));
        assertSame(HudSkin.HUD_ALERT, ShipGauges.levelColor(0.1f));
        assertSame(HudSkin.HUD_DIM, ShipGauges.levelColor(Float.NaN));
    }

    @Test
    void readoutsSayWhatTheBarsShow() {
        ShipStatus s = new ShipStatus();
        assertEquals("no tanks", ShipGauges.propellantText(s));
        assertEquals("NO POWER", ShipGauges.powerText(s));
        assertEquals("air 100%", ShipGauges.oxygenText(s));

        s.propellant = 0.62f;
        s.propellantKg = 1240;
        s.power = 0.5f;
        s.powerOutputWatts = 3e6;
        s.oxygen = 0.9f;
        s.cabinPressure = 0.5f;
        assertEquals("62%  1,240 kg", ShipGauges.propellantText(s));
        assertEquals("50%  3.0 MW", ShipGauges.powerText(s));
        assertEquals("90%  LOW AIR", ShipGauges.oxygenText(s));
        s.cabinPressure = 0.05f;
        assertEquals("90%  VACUUM", ShipGauges.oxygenText(s));
        assertEquals("12 kW", ShipGauges.watts(12_000));
    }
}
