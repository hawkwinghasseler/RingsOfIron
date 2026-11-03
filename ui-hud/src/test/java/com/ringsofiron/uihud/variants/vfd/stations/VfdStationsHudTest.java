package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The layout without a GL context: what takes clicks where, on the 1280 x 800 grid and a bigger stage. */
class VfdStationsHudTest {
    private static final float[][] STAGES = {{1280f, 800f}, {1920f, 1200f}, {1600f, 900f}};
    private final HudData d = new HudData();
    private final VfdStationsHud hud = new VfdStationsHud();
    private final FilamentActor actor = new FilamentActor(hud, hud);

    private void frame(float w, float h) {
        hud.layout(d, w, h, 0.016f);
        hud.boxes(actor);
    }

    private void missileInbound() {
        d.alerts.begin(1);
        d.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "1 missile inbound").count(1);
        d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%");
        d.alerts.end();
    }

    @Test
    void seatedTheLampsKeysAndBoardTakeClicksAndTheWorldTheRest() {
        d.ctx.presence = Presence.HELM;
        d.ctx.condition = Condition.RED;
        missileInbound();
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1], ox = Geo.topX(w), oy = Geo.bandY(h);
            frame(w, h);
            assertEquals(TopColumn.CLICK_WARN, actor.idAt(ox + Geo.TOP_X + 20f, Geo.ANN_Y + 10f));
            assertEquals(TopColumn.CLICK_CAUT, actor.idAt(ox + Geo.TOP_X + Geo.TOP_W - 20f, Geo.ANN_Y + 10f));
            assertEquals(Band.CLICK_BOARD + 2, actor.idAt(ox + Geo.BAND_X + 2.5f * 150f, oy + Geo.BOARD_Y + 10f));
            assertEquals(Band.CLICK_SOFT, actor.idAt(ox + Geo.LMFD + 40f, oy + Geo.KEY_Y + 10f));
            assertEquals(Band.CLICK_SOFT + 10 + 1, actor.idAt(ox + Geo.RMFD + Geo.SLOT * 1.5f, oy + Geo.KEY_Y + 10f));
            // The world takes every click between the top column and the band
            assertEquals(-1, actor.idAt(w / 2f, h / 2f));
            assertEquals(-1, actor.idAt(w / 2f, oy + Geo.BAND_Y - 40f));
            assertEquals(-1, actor.idAt(10f, Geo.ANN_Y + 10f));
        }
    }

    @Test
    void onDeckOnlyTheTopColumnTakesClicks() {
        d.ctx.presence = Presence.DECK;
        d.ctx.condition = Condition.RED;
        missileInbound();
        frame(1280f, 800f);
        assertEquals(TopColumn.CLICK_WARN, actor.idAt(Geo.TOP_X + 20f, Geo.ANN_Y + 10f));
        assertEquals(-1, actor.idAt(Geo.BAND_X + 2.5f * 150f, Geo.BOARD_Y + 10f));
        assertEquals(-1, actor.idAt(Geo.LMFD + 40f, Geo.KEY_Y + 10f));
    }

    @Test
    void theZonesKeepToTheirPlaces() {
        // The band and the top column never meet, on the grid or on a bigger stage
        for (float[] stage : STAGES) {
            float oy = Geo.bandY(stage[1]);
            assertTrue(Geo.TICKER_Y + 60f < oy + Geo.PROMPT_SEATED_Y, "the ticker clears the seated prompt");
            assertTrue(oy + Geo.PROMPT_SEATED_Y + 24f <= oy + Geo.BAND_Y - 10f, "the seated prompt clears the band's rack");
            assertTrue(Geo.topX(stage[0]) + Geo.TOP_X + Geo.TOP_W <= stage[0], "the top column is on the stage");
        }
        // The suit readout sits in the deck's corner, under nothing
        assertTrue(Geo.SUIT_Y + Geo.SUIT_H <= Geo.H);
        assertTrue(Geo.SUIT_X + Geo.SUIT_W < Geo.W / 2f - 150f);
    }

    @Test
    void altKeysWorkTheMfdsAndATileTurnsOneToItsAlert() {
        d.ctx.presence = Presence.WEAPONS;
        d.ctx.condition = Condition.YELLOW;
        frame(1280f, 800f);
        assertTrue(hud.mfdKey(Keys.NUM_1, false));
        assertEquals(Page.HELM, hud.mfds.right.page);
        assertFalse(hud.mfds.right.folded);
        assertTrue(hud.mfdKey(Keys.NUM_2, true));
        assertEquals(Page.NAV, hud.mfds.left.page);
        assertTrue(hud.mfdKey(Keys.NUM_0, false));
        assertTrue(hud.mfds.right.pinned);
        assertTrue(hud.mfdKey(Keys.MINUS, true));
        assertTrue(hud.mfds.left.folded);
        assertFalse(hud.mfdKey(Keys.NUM_7, false));
    }
}
