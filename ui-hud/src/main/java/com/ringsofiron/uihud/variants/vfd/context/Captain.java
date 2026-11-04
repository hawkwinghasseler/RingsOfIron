package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * The captain's own displays (functional-context 6, P6; 7, D1 and D3). D1, on deck: the captain's
 * kit, a reflective LCD backlit while it warns, with the condition, the suit, the air where they
 * stand and what they carry, and the suit's air while it is not breathing ship air (until X3 takes
 * it). D3, on deck: a projected tag on each hazard in view, one per room and code, and at most five
 * pointers at the edge for the rest. P6: what the player can do right here, with its key, or the
 * learning hints, at the bottom centre over the primary instrument.
 */
final class Captain {
    /** Breathable: at least this share of cabin pressure and of its oxygen (AirWatch's rule). */
    static final float BREATHABLE_OXYGEN = 0.75f;
    static final int TAGS = 12, POINTERS = 5, HINTS = 3;

    // D1
    boolean vitals, alert, airShown;
    String you = "", suit = "", here = "", air = "", carry = "";

    // D3
    static final class Tag {
        float x, y;
        String text = "";
        Kit.Sev sev = Kit.Sev.CAUTION;
        boolean pointer;
        char dir;
        String room = "";
    }

    final Tag[] tags = new Tag[TAGS];
    int tagCount;
    private final Vector2 v = new Vector2();

    // P6
    boolean prompt, action;
    final String[] keys = new String[HINTS], words = new String[HINTS];
    int count;

    Captain() {
        for (int i = 0; i < TAGS; i++) tags[i] = new Tag();
    }

    void update(HudData d, Situations s) {
        vitals(d, s);
        prompt(d, s);
    }

    // ------------------------------------------------------------------ D1

    private void vitals(HudData d, Situations s) {
        CaptainStatus c = d.captainStatus;
        vitals = false;
        if (!c.present || s.seat != Situations.Seat.DECK || s.fullScreen) return;
        boolean outside = Float.isNaN(c.roomPressure);
        boolean breathable = !outside && c.roomPressure >= ShipAlerts.LOW_PRESSURE
                && (Float.isNaN(c.roomOxygen) || c.roomOxygen >= BREATHABLE_OXYGEN);
        boolean smoke = !Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.3f;
        String w = c.wound == null ? "" : c.wound.toUpperCase(Locale.ROOT);
        boolean down = "unconscious".equalsIgnoreCase(c.state);
        if (down) you = "UNCONSCIOUS";
        else if (w.isEmpty() && Float.isNaN(c.bleedOut)) you = "OK";
        else you = Fmt.cut(w.isEmpty() ? "WOUNDED" : w, 12) + (Float.isNaN(c.bleedOut) ? "" : " " + Fmt.mmss(c.bleedOut));
        suit = c.suited ? "ON" : "OFF";
        here = outside ? "VACUUM" : c.roomPressure < ShipAlerts.VACUUM_PRESSURE ? "VACUUM" : !breathable ? "THIN AIR" : smoke ? "SMOKE" : "NORMAL";
        // The suit's air while it is not breathing ship air, until X3 takes it under 240 s
        airShown = c.suited && !breathable && !Float.isNaN(c.suitAir) && !s.suitAir;
        air = airShown ? Fmt.mmss(c.suitAir) : "";
        carry = Fmt.cut(c.carrying, 12);
        boolean normal = you.equals("OK") && here.equals("NORMAL") && carry.isEmpty();
        vitals = !normal || c.suited;
        alert = !you.equals("OK") || !breathable && !c.suited || smoke;
    }

    // ------------------------------------------------------------------ D3

    /**
     * Lays out the hazard tags on a {@code width} x {@code height} stage: a tag on each hazard in view,
     * and pointers at the edges of {@code l, t, r, b} for the rest, warnings first.
     */
    void tags(HudData d, Situations s, ViewportManager views, float width, float height, float l, float t, float r, float b) {
        tagCount = 0;
        if (s.seat != Situations.Seat.DECK || s.fullScreen || views == null || d.ctx.ship == null
                || d.ctx.presence == Presence.ABOARD_OTHER || d.ctx.presence == Presence.EVA) return;
        ShipGridComponent g = d.ctx.ship.getComponent(ShipGridComponent.class);
        TransformComponent tx = d.ctx.ship.getComponent(TransformComponent.class);
        if (g == null || tx == null) return;
        float cos = MathUtils.cosDeg(tx.rotation), sin = MathUtils.sinDeg(tx.rotation);
        int pointers = 0;
        // Warnings first, so the five pointers go to the worst
        for (int pass = 0; pass < 2; pass++) {
            Array<Alert> all = d.alerts.all();
            for (int i = 0; i < all.size && tagCount < TAGS; i++) {
                Alert a = all.get(i);
                if (a.tileX < 0 || (a.level == Level.WARNING) != (pass == 0)) continue;
                String text = tagText(a);
                if (text.isEmpty() || seen(text, a.room)) continue;
                float lx = g.gridOriginLocal.x + (a.tileX + 0.5f) * g.tileSize, ly = g.gridOriginLocal.y + (a.tileY + 0.5f) * g.tileSize;
                views.worldToUi(tx.x + lx * cos - ly * sin, tx.y + lx * sin + ly * cos, v);
                float sx = v.x, sy = height - v.y;
                boolean in = sx > l && sx < r && sy > t && sy < b;
                if (!in && pointers == POINTERS) continue;
                Tag tag = tags[tagCount++];
                tag.text = text;
                tag.room = a.room;
                tag.sev = a.level == Level.WARNING ? Kit.Sev.WARNING : Kit.Sev.CAUTION;
                tag.pointer = !in;
                if (in) {
                    tag.x = sx;
                    tag.y = sy;
                } else {
                    pointers++;
                    pointer(tag, sx, sy, l, t, r, b);
                }
            }
        }
    }

    /** One tag per room and code. */
    private boolean seen(String text, String room) {
        for (int i = 0; i < tagCount; i++) if (tags[i].text.equals(text) && tags[i].room.equals(room)) return true;
        return false;
    }

    /** The P2 short code, or the crew member's name for a casualty; "" for what has no place on the deck. */
    static String tagText(Alert a) {
        switch (a.code) {
            case "FIRE": return "FIRE";
            case "BREACH": return "BREACH";
            case "VACUUM": return "VACUUM";
            case "SMOKE": return "SMOKE";
            case "PRESSURE": return "PRESS LOW";
            case "CASUALTY": return Fmt.name(a.id.startsWith("casualty:") ? a.id.substring(9) : "CREW", 10);
            default: return "";
        }
    }

    /** A pointer where the ray from the region's middle towards ({@code x, y}) leaves it. */
    private static void pointer(Tag tag, float x, float y, float l, float t, float r, float b) {
        float cx = (l + r) / 2f, cy = (t + b) / 2f, dx = x - cx, dy = y - cy;
        if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) dx = 1f;
        float kx = dx != 0f ? ((dx > 0 ? r - 40f : l + 40f) - cx) / dx : Float.MAX_VALUE;
        float ky = dy != 0f ? ((dy > 0 ? b - 14f : t + 14f) - cy) / dy : Float.MAX_VALUE;
        float k = Math.min(kx, ky);
        tag.x = cx + dx * k;
        tag.y = cy + dy * k;
        tag.dir = kx <= ky ? (dx > 0 ? 'r' : 'l') : (dy > 0 ? 'd' : 'u');
    }

    // ------------------------------------------------------------------ P6

    private void prompt(HudData d, Situations s) {
        count = 0;
        prompt = false;
        // P6's column of the visibility matrix: not under warp, in a fight at a seat, or in a mode
        if (s.fullScreen || s.seat == Situations.Seat.NONE) return;
        if (s.exterior() && (s.warp || s.combat || s.seat != Situations.Seat.HELM)) return;
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
            keys[0] = d.promptKey.isEmpty() ? "E" : d.promptKey.toUpperCase(Locale.ROOT);
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
                // While the ship is disabled X3 carries the abandon and tow keys
                if (s.emergency && ("ABANDON".equals(h.action) || "CUT".equals(h.action))) continue;
                if (h.key.isEmpty() || h.label.isEmpty()) continue;
                keys[count] = h.key.toUpperCase(Locale.ROOT);
                words[count] = Fmt.cut(h.label, 24);
                count++;
            }
        }
        prompt = count > 0;
    }

    /** The classic prompt's words after "Press [E] ", as an order: "to put on a pressure suit" is "PUT ON A PRESSURE SUIT". */
    static String promptWords(String action) {
        String s = action.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("press ")) {
            int close = s.indexOf(']');
            if (close > 0) s = s.substring(close + 1).trim();
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("to ")) s = s.substring(3);
        else if (lower.startsWith("for the ")) s = s.substring(8);
        else if (lower.startsWith("for ")) s = s.substring(4);
        return s.toUpperCase(Locale.ROOT);
    }

    float promptWidth(Filament f) {
        float w = 0f;
        for (int i = 0; i < count; i++) {
            w += (keys[i].isEmpty() ? 0f : f.keycapWidth(keys[i], 18f, 12f) + 5f) + words[i].length() * Geo.CELL - Geo.P + 16f;
        }
        return w - 16f;
    }

    // ------------------------------------------------------------------ painting

    /** D1 at the bottom of the left column, on the grid from the stage's bottom. */
    void paintVitals(Filament f) {
        if (!vitals) return;
        float x = Geo.LX, y = Geo.D1_Y, w = Geo.CW, h = Geo.D1_H;
        f.plate(x, y, w, h, 5f, 7f);
        Filament.Opt panel = f.o();
        if (alert) panel.backlight();
        f.lcdPanel(x + 2f, y + 2f, w - 4f, h - 4f, panel);
        lcdRow(f, x, y + 22f, "YOU", you);
        lcdRow(f, x, y + 44f, "SUIT", suit);
        lcdRow(f, x, y + 66f, "HERE", here);
        if (airShown) {
            f.lcdText(x + 170f, y + 44f, "AIR", f.o().size(13f));
            f.lcdSeg7(x + 198f, y + 30f, 16f, air, f.o().slant(6f));
        } else if (!carry.isEmpty()) {
            f.lcdText(x + 170f, y + 44f, "CARRY", f.o().size(13f));
            f.lcdText(x + 170f, y + 66f, carry, f.o().size(13f));
        }
    }

    private static void lcdRow(Filament f, float x, float b, String k, String v) {
        f.lcdText(x + 12f, b, k, f.o().size(13f));
        f.lcdText(x + 64f, b, v, f.o().size(13f));
    }

    /** D3's tags and pointers, in stage units. */
    void paintTags(Filament f) {
        for (int i = 0; i < tagCount; i++) {
            Tag t = tags[i];
            if (t.pointer) Kit.edge(f, t.x, t.y, t.dir, t.text, t.sev.color(), Drive.NORM);
            else Kit.deckTag(f, t.x, t.y, t.text, t.sev, Drive.NORM);
        }
    }

    /** P6 centred on the grid with its top at {@code y}. */
    void paintPrompt(Filament f, float y) {
        if (!prompt) return;
        float w = promptWidth(f), x = Geo.W / 2f - w / 2f;
        f.slab(x - 8f, y, w + 16f, 24f, 3f, 1, false);
        float cx = x;
        for (int i = 0; i < count; i++) {
            if (!keys[i].isEmpty()) cx += f.keycap(cx, y + 3f, keys[i], 18f, 12f, action && i == 0) + 5f;
            cx += f.dm(cx, y + 6.75f, words[i], f.o().pitch(Geo.P).level(action || count == 1 ? Drive.NORM : Drive.DIM).noGhost()) + 16f;
        }
    }
}
