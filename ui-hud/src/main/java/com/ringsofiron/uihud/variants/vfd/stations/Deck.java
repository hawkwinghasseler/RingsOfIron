package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * The captain's own displays (functional-stations 7.3, 7.4): the suit readout at the bottom left,
 * deck only, an LCD that reflects and is backlit while it warns; and the prompt line at the bottom
 * centre, the E prompt when something is in reach, else at most three learning hints with their keys.
 */
final class Deck {
    /** Breathable: at least this share of cabin pressure and of its oxygen. */
    static final float BREATHABLE_PRESSURE = ShipAlerts.LOW_PRESSURE, BREATHABLE_OXYGEN = 0.75f;
    static final int HINTS = 3;

    // The suit readout
    boolean suit, suitAlert, suited;
    String air = "", kpa = "", o2 = "", wound = "", fire = "";
    private float lastPressure = Float.NaN, falling;

    // The prompt line: its y on the 1280 x 800 grid, and its keys and words
    boolean prompt;
    float promptY;
    final String[] keys = new String[HINTS], words = new String[HINTS];
    int count;
    /** The line is the E prompt, what the player can do now, rather than learning hints. */
    boolean action;

    void update(HudData d, boolean emergency, float delta) {
        suit(d, delta);
        prompt(d, emergency);
    }

    private void suit(HudData d, float delta) {
        CaptainStatus c = d.captainStatus;
        Presence p = d.ctx.presence;
        suit = false;
        if (!c.present || (p != Presence.DECK && p != Presence.EVA && p != Presence.ABOARD_OTHER)) {
            lastPressure = Float.NaN;
            return;
        }
        boolean outside = Float.isNaN(c.roomPressure);
        boolean breathable = !outside && c.roomPressure >= BREATHABLE_PRESSURE && c.roomOxygen >= BREATHABLE_OXYGEN;
        // Falling: the room has lost pressure over the last two seconds
        if (!outside && !Float.isNaN(lastPressure) && c.roomPressure < lastPressure - 0.002f) falling = 2f;
        else falling = Math.max(0f, falling - delta);
        lastPressure = outside ? Float.NaN : c.roomPressure;
        suited = c.suited;
        air = suited && !breathable ? Fmt.mmss(c.suitAir) : "";
        boolean showRoom = !outside && (!breathable || falling > 0f);
        kpa = showRoom ? Long.toString(Math.round(c.roomPressure * 101.3f)) : "";
        o2 = showRoom ? (c.roomOxygen < BREATHABLE_OXYGEN ? "O2 LOW" : "O2 " + Math.round(c.roomOxygen * 100f) + "%") : outside && suited ? "VACUUM" : "";
        wound = "";
        if (!c.wound.isEmpty() || !Float.isNaN(c.bleedOut)) {
            String w = c.wound.isEmpty() ? "BLEEDING" : c.wound.toUpperCase(Locale.ROOT);
            wound = Float.isNaN(c.bleedOut) ? Fmt.cut(w, 18) : Fmt.cut(w, 12) + " " + Fmt.mmss(c.bleedOut);
        }
        fire = fireIn(d, c.room) ? "FIRE" : "";
        boolean noSuit = !suited && !breathable;
        if (noSuit) air = "NO SUIT";
        suit = !air.isEmpty() || !kpa.isEmpty() || !wound.isEmpty() || !fire.isEmpty() || !o2.isEmpty();
        suitAlert = noSuit || (suited && !breathable && !Float.isNaN(c.suitAir) && c.suitAir < 60f) || !Float.isNaN(c.bleedOut)
                || (!outside && c.roomPressure < ShipAlerts.VACUUM_PRESSURE) || !fire.isEmpty();
    }

    private static boolean fireIn(HudData d, String room) {
        if (room == null || room.isEmpty()) return false;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if ("FIRE".equals(a.code) && room.equals(a.room)) return true;
        }
        return false;
    }

    private void prompt(HudData d, boolean emergency) {
        Presence p = d.ctx.presence;
        promptY = p == Presence.HELM || p == Presence.WEAPONS ? Geo.PROMPT_SEATED_Y : p == Presence.GUN ? 696f : Geo.PROMPT_DECK_Y;
        count = 0;
        action = !d.promptAction.isEmpty();
        if (action) {
            // A console's prompt can offer its seat too: "for the gun panel   [V] take the gun"
            String act = d.promptAction;
            int open = act.indexOf('['), close = open < 0 ? -1 : act.indexOf(']', open);
            String seatKey = "", seatWords = "";
            if (open > 0 && close > open + 1) {
                seatKey = act.substring(open + 1, close).trim();
                seatWords = act.substring(close + 1).trim();
                act = act.substring(0, open);
            }
            keys[0] = d.promptKey.isEmpty() ? "E" : d.promptKey;
            words[0] = promptWords(act);
            count = 1;
            if (!seatKey.isEmpty() && !seatWords.isEmpty()) {
                keys[1] = seatKey.toUpperCase(Locale.ROOT);
                words[1] = seatWords.toUpperCase(Locale.ROOT);
                count = 2;
            }
        } else if (d.keyHints.placing) {
            keys[0] = "";
            words[0] = Fmt.cut(d.keyHints.placingText, 60);
            count = 1;
        } else {
            Array<KeyHint> hints = d.keyHints.hints;
            for (int i = 0; i < hints.size && count < HINTS; i++) {
                KeyHint h = hints.get(i);
                // While the ship is disabled the status strip carries the abandon and tow keys (7.3)
                if (emergency && ("ABANDON".equals(h.action) || "CUT".equals(h.action))) continue;
                if (h.key.isEmpty() || h.label.isEmpty()) continue;
                keys[count] = h.key;
                words[count] = h.label.toUpperCase(Locale.ROOT);
                count++;
            }
        }
        prompt = count > 0;
    }

    /** The classic prompt's words after "Press [E] ", as an order: "to put on a pressure suit" is "PUT ON A PRESSURE SUIT". */
    static String promptWords(String action) {
        String s = action.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("press ")) {
            // The whole prompt as the classic HUD words it: "Press [E] to ..."
            int close = s.indexOf(']');
            if (close > 0) s = s.substring(close + 1).trim();
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("to ")) s = s.substring(3);
        else if (lower.startsWith("for the ")) s = s.substring(8);
        else if (lower.startsWith("for ")) s = s.substring(4);
        return s.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ painting

    /** The suit readout, on the 1280 x 800 grid anchored to the stage's bottom left. */
    void paintSuit(Filament f) {
        if (!suit) return;
        float x = Geo.SUIT_X, y = Geo.SUIT_Y, w = Geo.SUIT_W, h = Geo.SUIT_H;
        f.plate(x, y, w, h, 5f, 7f);
        Filament.Opt panel = f.o();
        if (suitAlert) panel.backlight();
        f.lcdPanel(x + 2f, y + 2f, w - 4f, h - 4f, panel);
        if (air.equals("NO SUIT")) {
            f.lcdText(x + 10f, y + 40f, "NO SUIT", f.o().size(20f));
        } else {
            f.lcdText(x + 10f, y + 17f, "AIR", f.o());
            f.lcdSeg7(x + 10f, y + 23f, 32f, air, f.o().slant(6f));
        }
        if (!kpa.isEmpty()) {
            f.lcdText(x + 108f, y + 17f, "ROOM", f.o());
            f.lcdSeg7(x + 108f, y + 23f, 18f, kpa, f.o().slant(6f));
            f.lcdText(x + 150f, y + 40f, "KPA", f.o());
        }
        if (!fire.isEmpty()) f.lcdText(x + 150f, y + 17f, fire, f.o());
        if (!o2.isEmpty()) f.lcdText(x + 108f, y + 60f, o2, f.o());
        if (!wound.isEmpty()) f.lcdText(x + 10f, y + 72f, wound, f.o());
    }

    /** The prompt line, centred on the 1280 x 800 grid. */
    void paintPrompt(Filament f) {
        if (!prompt) return;
        float y = promptY, w = 0f;
        for (int i = 0; i < count; i++) {
            w += (keys[i].isEmpty() ? 0f : f.keycapWidth(keys[i], 18f, 12f) + 5f) + words[i].length() * 9f - 1.5f + 16f;
        }
        w -= 16f;
        float x = Geo.W / 2f - w / 2f;
        f.slab(x - 8f, y, w + 16f, 24f, 3f, 1, false);
        float cx = x;
        for (int i = 0; i < count; i++) {
            if (!keys[i].isEmpty()) cx += f.keycap(cx, y + 3f, keys[i], 18f, 12f, action && i == 0) + 5f;
            cx += f.dm(cx, y + 6.75f, words[i], f.o().pitch(1.5f).level(action || count == 1 ? Drive.NORM : Drive.DIM).noGhost()) + 16f;
        }
    }
}
