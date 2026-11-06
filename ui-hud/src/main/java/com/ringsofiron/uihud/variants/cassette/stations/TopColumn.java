package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;
import com.ringsofiron.uihud.variants.cassette.stations.StationAlerts.Line;
import com.ringsofiron.uihud.variants.cassette.stations.StationAlerts.Tile;

import java.util.Locale;

/**
 * The always-on overlay's top column (spec 7.1, 7.2, 7.5, 7.7), hung from a rack: two rods from the top
 * edge, each overhead unit clamped to them at its fixed height. The status strip (the condition on a drum
 * flag, BATTLE STATIONS, DOCKED and LATCHED on lenses, the emergency on a red tube), the annunciator (the
 * master lamps either end, six tiles between), the warp strip, the callout ticker. A unit with nothing to
 * say is drawn up out of sight; the others keep their heights, so the ticker is always at y 108.
 */
final class TopColumn extends YardPart {
    static final float X = 280f, W = StationsLayout.COLUMN_W;
    private static final float TILE_W = 94f, TILE_H = 36f;
    /** Where the ticker's text starts and how wide a row of it may run. */
    private static final float TEXT_X = 84f, TEXT_W = 618f;

    private final StationsFrame f;
    private final Actions actions;
    boolean strip, ann, warp, ticker;
    /** A screen is up under the column: the strip and the annunciator hang over it whatever they say. */
    boolean screen;
    /** The ticker's rows this frame, oldest first: a line takes two when it runs long. */
    private final Array<String[]> rows = new Array<>();
    private final Array<Line> rowLines = new Array<>();

    TopColumn(StationsFrame f, Actions actions) {
        super(X, 0f);
        this.f = f;
        this.actions = actions;
    }

    /** Decides which units hang this frame. */
    void choose() {
        StationsFrame fr = f;
        HudData d = fr.data;
        strip = fr.condition != StationsFrame.Condition.GREEN || fr.docked || fr.latched || fr.disabled || fr.abandoning || fr.battle;
        ann = fr.alerts.annunciatorUp();
        warp = fr.warp;
        ticker = fr.alerts.lines.size > 0;
        if (screen) {
            // Over a screen the two stay put, and the warp strip and the ticker would only be under it
            strip = ann = true;
            warp = ticker = false;
        }
        if (d.ctx.ship == null) strip = ann = false;
    }

    @Override
    protected void draw(YardPaint p) {
        float bottom = ticker ? 112f : warp ? 88f : ann ? 48f : strip ? 20f : 0f;
        if (bottom <= 0f) return;
        rod(p, X + 30f, bottom);
        rod(p, X + W - 34f, bottom);
        if (strip) statusStrip(p);
        if (ann) annunciator(p);
        if (warp) warpStrip(p);
        if (ticker) ticker(p);
    }

    private static void rod(YardPaint p, float x, float bottom) {
        p.rect(x, 0f, 3.5f, bottom, Hardware.ROD);
        p.rect(x + 0.6f, 0f, 1f, bottom, Color.WHITE, 0.14f);
    }

    // --- Z1s: the status strip -------------------------------------------------------------------

    private void statusStrip(YardPaint p) {
        float x = X, y = StationsLayout.STRIP_Y, w = W, h = StationsLayout.STRIP_H;
        StationsFrame fr = f;
        Hardware.bar(p, x, y, w, h);
        Hardware.condFlag(p, x + 6f, y + 4f, 84f, 16f, fr.condition);
        Hardware.lens(p, x + 104f, y + 4f, 120f, 16f, "BATTLE STATIONS", Yard.RED_LAMP, fr.battle, true);
        Hardware.lens(p, x + 234f, y + 4f, 62f, 16f, "DOCKED", Yard.WHITE_LAMP, fr.docked, true);
        Hardware.lens(p, x + 304f, y + 4f, 66f, 16f, "LATCHED", Yard.WHITE_LAMP, fr.latched, true);
        float tx = x + 382f, tw = w - 388f;
        p.tube(tx, y + 3f, tw, 18f, 2f, 3f, false);
        String[] segs = emergency(fr);
        float ex = x + 390f, base = y + 16.5f;
        for (String s : segs) {
            if (s.charAt(0) == YardPaint.KEY) {
                Hardware.rev(p, s.substring(1), ex + 2f, base, 12.5f, Math.max(14f, p.width(Face.TUBE_BOLD, 12.5f, s.substring(1)) + 6f), Yard.P_RED);
                ex += Math.max(17f, p.width(Face.TUBE_BOLD, 12.5f, s.substring(1)) + 9f);
            } else {
                ex += p.tube(s, ex, base, 12.5f, Yard.P_RED, false);
            }
        }
        p.tubeOver(tx, y + 3f, tw, 18f);
    }

    /**
     * The emergency text (7.7): the state and the player's options, keys first. The tow is offered only
     * while the beacon would answer, and only where its key calls the tug (the helm and the deck).
     */
    static String[] emergency(StationsFrame fr) {
        HudData d = fr.data;
        String p = Hardware.key(d, "ABANDON", "P"), b = Hardware.key(d, "CUT", "B");
        char k = YardPaint.KEY;
        if (fr.abandoning) return new String[]{"ABANDON SHIP  ", k + p, " LAUNCH NOW"};
        if (!fr.disabled) return new String[0];
        java.util.List<String> s = new java.util.ArrayList<>();
        s.add("DISABLED  ");
        if (!d.orderState.abandonRefusal.isEmpty()) s.add("");
        else if (d.orderState.abandonArmed > 0f) {
            s.add(k + p);
            s.add(" AGAIN: ABANDON  ");
        } else {
            s.add(k + p);
            s.add(k + p);
            s.add(" ABANDON  ");
        }
        if (d.orderState.towRefusal.isEmpty() && d.orders != null) {
            boolean here = fr.seat == StationsFrame.Seat.HELM || fr.seat == StationsFrame.Seat.DECK;
            if (!here) s.add("TOW: HELM OR DECK");
            else if (d.orderState.towArmed > 0f) {
                s.add(k + b);
                s.add(" AGAIN: TOW");
            } else {
                s.add(k + b);
                s.add(k + b);
                s.add(" TOW");
            }
        }
        s.removeIf(String::isEmpty);
        return s.toArray(new String[0]);
    }

    // --- Z1: the annunciator ------------------------------------------------------------------

    private void annunciator(YardPaint p) {
        float x = X, y = StationsLayout.ANN_Y, w = W, h = StationsLayout.ANN_H;
        StationAlerts a = f.alerts;
        Hardware.bar(p, x, y, w, h);
        // The master lamps flash at 1.5 Hz while lit
        boolean on = (p.time() * 1.5f) % 1f < 0.6f;
        p.push(x + 4f, y + 4f, 48f, 28f, new String[]{"MASTER", "WARNING"}, Yard.RED_LAMP, a.masterWarning && on, YardPaint.Kind.INDICATOR, false, true);
        p.push(x + w - 52f, y + 4f, 48f, 28f, new String[]{"MASTER", "CAUTION"}, Yard.AMBER_LAMP, a.masterCaution && on, YardPaint.Kind.INDICATOR, false, false);
        for (int i = 0; i < StationAlerts.SLOTS; i++) tile(p, x + 62f + i * 100f, y, i < a.tiles.size ? a.tiles.get(i) : null);
    }

    private void tile(YardPaint p, float x, float y, Tile t) {
        p.rrect(x, y, TILE_W, TILE_H, 2f, Hardware.TILE_BG);
        if (t == null) {
            p.rect(x + 2f, y + 2f, TILE_W - 4f, 15f, Hardware.TILE_DARK);
            p.flaps(x + 9f, y + 20f, "", 8, 9.5f, 13f, 12f, Yard.FLAP_TEXT);
            return;
        }
        boolean warning = t.level == Level.WARNING;
        Yard.Lamp lamp = warning ? Yard.RED_LAMP : Yard.AMBER_LAMP;
        if (!t.acknowledged && !t.clearing()) p.glowRect(x + 2f, y + 2f, TILE_W - 4f, 15f, 5f, lamp.on, 0.8f);
        p.rect(x + 2f, y + 2f, TILE_W - 4f, 15f, lamp.on);
        p.glass(x + 2f, y + 2f, TILE_W - 4f, 15f);
        float tw = p.width(Face.LEGEND, 12f, t.tag), gw = 10f, tx = x + (TILE_W - tw - gw) / 2f;
        Hardware.mark(p, warning, tx + 3.5f, y + 9.6f, 3.6f, Hardware.LEGEND_ON);
        p.text(Face.LEGEND, 12f, t.tag, tx + gw, y + 13.8f, Hardware.LEGEND_ON);
        p.flaps(x + 9f, y + 20f, t.value, 8, 9.5f, 13f, 12f, Yard.FLAP_TEXT);
        if (t.clearing()) p.rrect(x, y, TILE_W, TILE_H, 2f, Hardware.TILE_BG, 0.6f);
        else if (t.fresh && (p.time() * 2f) % 1f < 0.6f) {
            // New and not yet acknowledged: a ring round the tile, flashing
            p.glowRect(x - 2.5f, y - 2.5f, TILE_W + 5f, TILE_H + 5f, 4f, lamp.on, 0.35f);
            p.frame(x - 2.5f, y - 2.5f, TILE_W + 5f, TILE_H + 5f, 2f, lamp.on, 0.85f);
        }
    }

    // --- Z1w: the warp strip ----------------------------------------------------------------

    private void warpStrip(YardPaint p) {
        HudData d = f.data;
        float x = X, y = StationsLayout.WARP_Y, w = W, h = StationsLayout.WARP_H;
        Hardware.bar(p, x, y, w, h);
        p.legend("WARP", x + 10f, y + 14.5f, Yard.INK_LIGHT);
        p.flaps(x + 48f, y + 3f, YardText.fit(d.ctx.warp + "X", 6), 6, 9f, 14f, 12.5f, Yard.P_AMBER);
        p.lamp(x + 120f, y + 10f, 3.6f, Yard.WHITE_LAMP, d.ctx.railsWarp);
        p.legend("RAILS", x + 128f, y + 14.5f, d.ctx.railsWarp ? Yard.INK_LIGHT : Hardware.DIM_INK);
        float tx = x + 176f, tw = w - 182f;
        p.tube(tx, y + 3f, tw, 14f, 2f, 2f, false);
        boolean held = !d.ctx.warpHold.isEmpty();
        Hardware.tubeFit(p, warpText(d), x + 184f, y + 14f, 12.5f, held ? Yard.P_AMBER : Yard.P_DIM, tw - 16f);
        p.tubeOver(tx, y + 3f, tw, 14f);
    }

    /** "drops out in 4:12 for burn 2", or why warp is held. */
    static String warpText(HudData d) {
        if (!d.ctx.warpHold.isEmpty()) return "held: " + lower(d.ctx.warpHold);
        float t = d.ctx.warpNextStop;
        if (Float.isNaN(t)) return "";
        String s = "drops out in " + YardText.clock(t);
        float burn = d.navReadout.burnStartIn;
        if (d.navReadout.burnNumber > 0 && !Float.isNaN(burn) && Math.abs(burn - t) < 60f) s += " for burn " + d.navReadout.burnNumber;
        return s;
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    // --- Z2: the callout ticker -------------------------------------------------------------

    private void ticker(YardPaint p) {
        float x = X, y = StationsLayout.TICKER_Y, w = W, h = StationsLayout.TICKER_H;
        p.rrect(x + 1f, y + 3f, w, h, 4f, Yard.SHADOW, 0.6f);
        p.rrect(x, y, w, h, 4f, Yard.BAKELITE);
        p.tube(x + 5f, y + 5f, w - 10f, h - 10f, 4f, 4f, false);
        layoutRows(p);
        for (int i = 0; i < rows.size; i++) {
            String[] r = rows.get(i);
            Line l = rowLines.get(i);
            float yy = y + 21f + i * 16f;
            if (r[0] != null) {
                if (l.level != null) Hardware.mark(p, l.level == Level.WARNING, x + 17f, yy - 4.5f, 3.8f, l.level == Level.WARNING ? Yard.P_RED : Yard.P_AMBER);
                p.glowRect(x + 27f, yy - 11f, 46f, 13.5f, 3f, Yard.P_AMBER, 0.25f);
                p.rect(x + 27f, yy - 11f, 46f, 13.5f, Yard.P_AMBER);
                p.text(Face.TUBE_BOLD, 12.5f, r[0], x + 30f, yy, Yard.CRT_BLACK);
            }
            p.tube(r[1], x + TEXT_X, yy, 13f, l.level == Level.WARNING ? Yard.P_RED : Yard.P_AMBER, false);
        }
        p.tubeOver(x + 5f, y + 5f, w - 10f, h - 10f);
    }

    /** The newest lines that fit in three rows, a long one wrapped onto a second, never cut. */
    private void layoutRows(YardPaint p) {
        rows.clear();
        rowLines.clear();
        Array<Line> lines = f.alerts.lines;
        int used = 0;
        int first = lines.size;
        for (int i = lines.size - 1; i >= 0; i--) {
            int n = wrap(p, text(lines.get(i))).length;
            if (used + n > StationAlerts.LINES) break;
            used += n;
            first = i;
        }
        for (int i = first; i < lines.size; i++) {
            Line l = lines.get(i);
            String[] parts = wrap(p, text(l));
            for (int k = 0; k < parts.length; k++) {
                rows.add(new String[]{k == 0 ? l.tag : null, parts[k]});
                rowLines.add(l);
            }
        }
    }

    private static String text(Line l) {
        return l.who + ": " + l.text + (l.count > 1 ? "  x" + l.count : "");
    }

    private static String[] wrap(YardPaint p, String s) {
        if (p.width(Face.TUBE, 13f, s) <= TEXT_W) return new String[]{s};
        int cut = s.length();
        while (cut > 0) {
            cut = s.lastIndexOf(' ', cut - 1);
            if (cut <= 0) break;
            if (p.width(Face.TUBE, 13f, s.substring(0, cut)) <= TEXT_W) {
                String rest = s.substring(cut + 1);
                // A third row's worth is the style's to scroll: the second row keeps the line's end
                while (p.width(Face.TUBE, 13f, rest) > TEXT_W && rest.indexOf(' ') > 0) rest = "…" + rest.substring(rest.indexOf(' ') + 1);
                return new String[]{s.substring(0, cut), rest};
            }
        }
        return new String[]{s};
    }

    // --- Pointer --------------------------------------------------------------------------------

    private Tile tileAt(float lx, float ly) {
        if (!ann || ly < StationsLayout.ANN_Y || ly > StationsLayout.ANN_Y + TILE_H) return null;
        StationAlerts a = f.alerts;
        for (int i = 0; i < a.tiles.size; i++) {
            float tx = X + 62f + i * 100f;
            if (lx >= tx && lx <= tx + TILE_W) return a.tiles.get(i);
        }
        return null;
    }

    private boolean onMaster(float lx, float ly) {
        if (!ann || ly < StationsLayout.ANN_Y || ly > StationsLayout.ANN_Y + TILE_H) return false;
        return lx <= X + 56f || lx >= X + W - 56f;
    }

    private Line lineAt(float ly) {
        if (!ticker) return null;
        int i = (int) Math.floor((ly - (StationsLayout.TICKER_Y + 8f)) / 16f);
        return i >= 0 && i < rowLines.size ? rowLines.get(i) : null;
    }

    @Override
    public boolean pressable(float px, float py) {
        float lx = localX(px), ly = localY(py);
        return onMaster(lx, ly) || tileAt(lx, ly) != null || lineAt(ly) != null && lineAt(ly).home != null;
    }

    @Override
    public boolean press(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (onMaster(lx, ly)) {
            actions.acknowledge();
            return true;
        }
        Tile t = tileAt(lx, ly);
        if (t != null) {
            actions.show(t.home);
            return true;
        }
        Line l = lineAt(ly);
        if (l != null && l.home != null) {
            actions.show(l.home);
            return true;
        }
        return false;
    }

    @Override
    public String hover(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (onMaster(lx, ly)) return "Acknowledge: stops the flashing; the tiles stay while their conditions hold";
        Tile t = tileAt(lx, ly);
        if (t != null) return t.text;
        if (strip && ly >= StationsLayout.STRIP_Y && ly <= StationsLayout.STRIP_Y + StationsLayout.STRIP_H && f.disabled) {
            return "Ship disabled: " + f.data.ctx.disabledReason;
        }
        if (warp && ly >= StationsLayout.WARP_Y && ly <= StationsLayout.WARP_Y + StationsLayout.WARP_H && !f.data.ctx.warpHold.isEmpty()) {
            return "Warp held: " + f.data.ctx.warpHold;
        }
        return null;
    }
}
