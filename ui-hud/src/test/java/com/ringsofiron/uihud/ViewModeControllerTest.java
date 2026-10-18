package com.ringsofiron.uihud;

import com.ringsofiron.uihud.systems.HudRenderSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewModeControllerTest {

    @Test
    void listenersHearCurrentModeThenChanges() {
        ViewModeController c = new ViewModeController();
        List<String> heard = new ArrayList<>();
        c.addListener((prev, cur) -> heard.add(prev + ">" + cur));
        c.toggle();
        c.setMode(ViewMode.INTERIOR); // no change, no event
        c.toggle();
        assertEquals(List.of("EXTERIOR>EXTERIOR", "EXTERIOR>INTERIOR", "INTERIOR>EXTERIOR"), heard);
    }

    @Test
    void rangeFormatting() {
        assertEquals("850 m", HudRenderSystem.formatRange(849.6f));
        assertEquals("12.3 km", HudRenderSystem.formatRange(12_300f));
        assertEquals("1,234 km", HudRenderSystem.formatRange(1_234_000f));
    }
}
