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
        assertEquals("90%  cabin LOW AIR", ShipGauges.oxygenText(s));
        s.cabinPressure = 0.05f;
        assertEquals("90%  cabin VACUUM", ShipGauges.oxygenText(s));
        s.oxygen = 0.3f;
        s.cabinPressure = 1f;
        assertEquals("30%  LOW O2", ShipGauges.oxygenText(s));
        s.oxygen = 0f;
        s.emergencyOxygenKg = 41.6;
        s.emergencyOxygen = 0.69f;
        assertEquals("EMERGENCY 42 kg", ShipGauges.oxygenText(s));
        s.emergencyOxygenKg = Double.NaN;
        s.emergencyOxygen = Float.NaN;
        s.oxygen = Float.NaN;
        s.cabinPressure = 0.5f;
        assertEquals("air 50%  LOW AIR", ShipGauges.oxygenText(s));
        assertEquals("12 kW", ShipGauges.watts(12_000));
    }
}
