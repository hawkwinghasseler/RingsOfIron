package com.ringsofiron.uihud.variants;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudVariantCycleTest {

    @AfterEach
    void reset() {
        HudVariants.setChosen(HudVariants.CLASSIC);
    }

    @Test
    void fromClassicEitherKeyStartsAtNasaPunkTask() {
        HudVariants.setChosen(HudVariants.CLASSIC);
        HudVariants.cycleSpec();
        assertEquals("nasapunk.task", HudVariants.getChosen());
        HudVariants.setChosen(HudVariants.CLASSIC);
        HudVariants.cycleStyle();
        assertEquals("nasapunk.task", HudVariants.getChosen());
    }

    @Test
    void specCyclesTaskStationsContextKeepingStyle() {
        HudVariants.setChosen("vfd.task");
        HudVariants.cycleSpec();
        assertEquals("vfd.stations", HudVariants.getChosen());
        HudVariants.cycleSpec();
        assertEquals("vfd.context", HudVariants.getChosen());
        HudVariants.cycleSpec();
        assertEquals("vfd.task", HudVariants.getChosen());
    }

    @Test
    void styleCyclesNasaPunkVfdCassetteKeepingSpec() {
        HudVariants.setChosen("nasapunk.context");
        HudVariants.cycleStyle();
        assertEquals("vfd.context", HudVariants.getChosen());
        HudVariants.cycleStyle();
        assertEquals("cassette.context", HudVariants.getChosen());
        HudVariants.cycleStyle();
        assertEquals("nasapunk.context", HudVariants.getChosen());
    }
}
