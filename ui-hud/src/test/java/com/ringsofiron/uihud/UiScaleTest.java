package com.ringsofiron.uihud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiScaleTest {
    @AfterEach
    void reset() {
        UiScale.setChosen(0f);
    }

    @Test
    void autoFollowsTheWindowHeight() {
        UiScale.setChosen(0f);
        assertEquals(1f, UiScale.effective(1280, 800));
        assertEquals(1f, UiScale.effective(1920, 1080));
        assertEquals(1.25f, UiScale.effective(2560, 1440));
        assertEquals(2f, UiScale.effective(3840, 2160));
    }

    @Test
    void aChoiceIsHeldToWhatFitsTheWindow() {
        UiScale.setChosen(2f);
        // 1920x1080 holds 1280x800 units at 1.35x at most
        assertEquals(1.35f, UiScale.effective(1920, 1080), 1e-6f);
        assertEquals(2f, UiScale.effective(3840, 2160));
        // Never under 1x, even in a small window
        assertEquals(1f, UiScale.effective(1024, 768));
    }

    @Test
    void labels() {
        assertEquals("Auto", UiScale.label(0f));
        assertEquals("125%", UiScale.label(1.25f));
    }
}
