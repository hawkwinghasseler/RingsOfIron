package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import static com.ringsofiron.uihud.variants.vfd.stations.Geo.ANN_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.STRIP_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.TICKER_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.TOP_W;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.TOP_X;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.WARP_Y;

/**
 * The top column, every presence's (functional-stations 7): the status strip, the annunciator with
 * its master lamps, the warp strip and the callout ticker, as plates that power up in fixed slots. A
 * slot with nothing to say is dark; nothing below it moves up.
 */
final class TopColumn {
    static final int CLICK_WARN = 100, CLICK_CAUT = 101, CLICK_TILE = 110;
    /** The master lamps flash at this rate while their alerts are unacknowledged, Hz (7.1: 2 Hz or slower). */
    static final float FLASH_HZ = 1.25f;
    /** Ticker cells, at the body pitch. */
    static final int TICKER_CELLS = (int) ((TOP_W - 68f - 2f) / 12f);
    /** The width of the ticker's tag column, short of its text. */
    static final float TAG_W = 32f;

    final Annunciator ann = new Annunciator();
    final Ticker ticker = new Ticker();

    // The status strip
    boolean strip;
    Condition condition = Condition.GREEN;
    boolean battle, docked, latched;
    /** The emergency text, its keys in braces: "DISABLED {P} {P} ABANDON". */
    String emergency = "";
    // The warp strip
    int warp = 1;
    boolean rails;
    String warpText = "";
    // The lamps
    boolean warnLit, cautLit;
    private float flash;
    private static final String[] BERTH = {"DOCKED", "LATCHED"};
    private final float[] berth = new float[2];

    void update(HudData d, boolean blackout, float delta) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        ann.fill(d.alerts.all(), d.powerFrame, blackout);
        ticker.fill(d.messages, delta);
        flash = (flash + delta) % (1f / FLASH_HZ);
        boolean on = flash < 0.5f / FLASH_HZ;
        warnLit = ann.warnUnacked ? on : false;
        cautLit = ann.cautUnacked ? on : false;

        condition = ctx.condition;
        battle = o.battleStations;
        docked = ctx.docked;
        latched = ctx.latched;
        emergency = emergency(d);
        strip = ctx.ship != null && (condition != Condition.GREEN || docked || latched || !emergency.isEmpty());

        warp = ctx.warp;
        rails = ctx.railsWarp;
        if (!ctx.warpHold.isEmpty()) warpText = "HELD: " + ctx.warpHold;
        else if (!Float.isNaN(ctx.warpNextStop)) warpText = "DROPS OUT IN " + Fmt.mmss(ctx.warpNextStop);
        else warpText = "";
        warpText = warpText.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * The status strip's emergency text (7.7): the state and the player's options, keys first. The
     * tow is offered only while the beacon would answer; once abandon ship is ordered, the abandon key
     * launches the pods. A first press waiting for its second says so.
     */
    static String emergency(HudData d) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        String p = keyCell(d.key("ABANDON"), "P"), b = keyCell(d.key("CUT"), "B");
        if (o.abandonArmed > 0f && !ctx.abandoning) return "{" + p + "} AGAIN: ABANDON SHIP";
        if (o.towArmed > 0f) return "{" + b + "} AGAIN: CALL A TOW";
        if (ctx.abandoning) return "ABANDON SHIP {" + p + "} LAUNCH NOW";
        if (!ctx.disabledReason.isEmpty()) {
            String s = "DISABLED {" + p + "} {" + p + "} ABANDON";
            if (o.towRefusal.isEmpty()) s += " {" + b + "} {" + b + "} TOW";
            return s;
        }
        return "";
    }

    /** A key as one inverse cell: its name when it is a single character, else the default. */
    private static String keyCell(String name, String fallback) {
        return name != null && name.length() == 1 ? name.toUpperCase(java.util.Locale.ROOT) : fallback;
    }

    /** The boxes that take clicks: the lamps acknowledge; a tile shows its alert's home page. */
    void boxes(FilamentActor actor, float ox) {
        if (!ann.any()) return;
        float lampW = 56f, tileW = 94f, gap = (TOP_W - 2f * lampW - 6f * tileW) / 7f;
        actor.box(CLICK_WARN, ox + TOP_X, ANN_Y, lampW, 36f);
        actor.box(CLICK_CAUT, ox + TOP_X + TOP_W - lampW, ANN_Y, lampW, 36f);
        for (int i = 0; i < Annunciator.TILES; i++) {
            if (ann.slots[i] == null) continue;
            actor.box(CLICK_TILE + i, ox + TOP_X + lampW + gap + i * (tileW + gap), ANN_Y, tileW, 36f);
        }
    }

    // ------------------------------------------------------------------ painting

    void paint(Filament f) {
        if (strip) statusStrip(f);
        if (ann.any()) annunciator(f);
        if (warp > 1) warpStrip(f);
        if (anyLine()) ticker(f);
    }

    private boolean anyLine() {
        for (Ticker.Line l : ticker.lines) if (l != null) return true;
        return false;
    }

    private void statusStrip(Filament f) {
        float x = TOP_X, y = STRIP_Y, h = 24f;
        f.plate(x, y, TOP_W, h, 4f, 5f);
        float cx = x;
        cx = cell(f, cx, y, h, "YELLOW", Phosphor.AMBER, condition == Condition.YELLOW);
        cx = cell(f, cx, y, h, "RED", Phosphor.RED, condition == Condition.RED);
        float bw = f.legendWidth("BATTLE STATIONS", f.o()) + 14f;
        f.module(cx, y, bw, h, f.o().filaments(0).noGlare().radius(2f));
        f.legend(cx + bw / 2f, y + 16.7f, "BATTLE STATIONS", f.o().centre().red().lit(battle).norm());
        cx += bw + 4f;
        float sw = f.legendWidth("DOCKED", f.o()) + f.legendWidth("LATCHED", f.o()) + 20f;
        f.module(cx, y, sw, h, f.o().filaments(0).noGlare().radius(2f));
        berth[0] = docked ? Drive.NORM : Float.NaN;
        berth[1] = latched ? Drive.NORM : Float.NaN;
        Kit.states(f, cx + 6f, y + 16.7f, BERTH, berth, 13f, 8f);
        cx += sw + 4f;
        float ew = x + TOP_W - cx;
        int cells = (int) ((ew - 10f) / 9f);
        f.module(cx, y, ew, h, f.o().filaments(1).noGlare().radius(2f));
        f.dm(cx + 6f, y + 6.75f, "", f.o().pitch(1.5f).cells(cells));
        // A key it names is one inverse cell, the display's own key cap, so the text keeps its grid
        int cell = 0;
        String e = emergency;
        for (int i = 0; i < e.length() && cell < cells; ) {
            char ch = e.charAt(i);
            if (ch == '{' && i + 2 < e.length() && e.charAt(i + 2) == '}') {
                f.dm(cx + 6f + cell * 9f, y + 6.75f, String.valueOf(e.charAt(i + 1)), f.o().pitch(1.5f).inverse(0, 1).norm().noGhost());
                cell++;
                i += 3;
                continue;
            }
            int j = i;
            while (j < e.length() && e.charAt(j) != '{') j++;
            String run = e.substring(i, Math.min(j, i + cells - cell));
            f.dm(cx + 6f + cell * 9f, y + 6.75f, run, f.o().pitch(1.5f).norm().noGhost());
            cell += run.length();
            i = j;
        }
    }

    private static float cell(Filament f, float cx, float y, float h, String word, Phosphor color, boolean on) {
        float w = f.legendWidth(word, f.o()) + 14f;
        f.module(cx, y, w, h, f.o().filaments(0).noGlare().radius(2f));
        if (on) {
            f.rectLit(cx + 2f, y + 2f, w - 4f, h - 4f, f.o().color(color).norm().r(1.2f));
            f.knockout(cx + w / 2f, y + 16.7f, word, f.o().centre());
        } else {
            f.legend(cx + w / 2f, y + 16.7f, word, f.o().centre().color(color).lit(false));
        }
        return cx + w + 4f;
    }

    private void annunciator(Filament f) {
        float x = TOP_X, y = ANN_Y, lampW = 56f, tileW = 94f, gap = (TOP_W - 2f * lampW - 6f * tileW) / 7f;
        f.plate(x, y, TOP_W, 36f, 4f, 5f);
        f.lampKey(x, y, lampW, 36f, "MASTER", "WARN", Filament.Lamp.RED, warnLit);
        f.lampKey(x + TOP_W - lampW, y, lampW, 36f, "MASTER", "CAUT", Filament.Lamp.AMBER, cautLit);
        for (int i = 0; i < Annunciator.TILES; i++) {
            float tx = x + lampW + gap + i * (tileW + gap);
            f.module(tx, y, tileW, 36f, f.o().filaments(1).radius(2f));
            float lx = tx + (tileW - 70.5f) / 2f, l1 = y + 5.5f, l2 = y + 20f;
            Annunciator.Tile t = ann.slots[i];
            if (t == null) {
                f.dm(lx, l1, "", f.o().pitch(1.5f).cells(8).amber());
                f.dm(lx, l2, "", f.o().pitch(1.5f).cells(8).amber());
                continue;
            }
            if (t.warning) {
                f.rectLit(tx + 3f, y + 3f, tileW - 6f, 30f, f.o().red().norm().r(1.2f));
                Kit.knock(f, lx, l1, t.tag, 8, 1.5f);
                Kit.knock(f, lx, l2, t.value, 8, 1.5f);
            } else {
                f.frameLit(tx + 2.5f, y + 2.5f, tileW - 5f, 31f, 1.1f, f.o().amber().norm());
                f.dm(lx, l1, t.tag, f.o().pitch(1.5f).cells(8).amber().full());
                f.dm(lx, l2, t.value, f.o().pitch(1.5f).cells(8).amber().norm());
            }
        }
    }

    private void warpStrip(Filament f) {
        float x = TOP_X, y = WARP_Y, h = 20f;
        f.plate(x, y, TOP_W, h, 4f, 5f);
        f.module(x, y, TOP_W, h, f.o().filaments(0).noGlare().radius(2f));
        float cx = x + 8f;
        cx += f.legend(cx, y + 14.5f, "WARP", f.o().dim()) + 6f;
        cx += f.seg7(cx, y + 4f, 12f, Integer.toString(warp), f.o().cells(5).right().norm()) + 3f;
        cx += f.legend(cx, y + 14.5f, "X", f.o().dim()) + 12f;
        cx += f.legend(cx, y + 14.5f, "RAILS", f.o().lit(rails).norm()) + 14f;
        int cells = (int) ((x + TOP_W - 8f - cx) / 9f);
        f.dm(cx, y + 4.75f, warpText, f.o().pitch(1.5f).cells(cells).norm());
    }

    private void ticker(Filament f) {
        float x = TOP_X, y = TICKER_Y, tx = x + 68f;
        f.plate(x, y, TOP_W, 60f, 4f, 5f);
        f.module(x, y, TOP_W, 60f, f.o().filaments(2).radius(2f));
        int newest = -1;
        for (int i = 0; i < Ticker.LINES; i++) if (ticker.lines[i] != null) newest = i;
        for (int i = 0; i < Ticker.LINES; i++) {
            Ticker.Line ln = ticker.lines[i];
            float ly = y + 4f + i * 18f;
            Kit.pip(f, x + 10f, ly + 7f, Phosphor.RED, ln != null && ln.level == Level.WARNING);
            Kit.pip(f, x + 19f, ly + 7f, Phosphor.AMBER, ln != null && ln.level == Level.CAUTION);
            float lv = ln != null && i == newest ? Drive.NORM : Drive.DIM;
            if (ln != null) {
                // The tag's column holds four letters at the legend's own condensing; COMMS is squeezed into it
                float tw = f.legendWidth(ln.tag, f.o());
                float condense = tw > TAG_W ? 0.8f * TAG_W / tw : 0.8f;
                f.legend(x + 28f, ly + 11.75f, ln.tag, f.o().level(lv).noGhost().condense(condense));
            }
            String text = "";
            if (ln != null) {
                int from = Ticker.scroll(ln.text.length(), TICKER_CELLS, ln.shown);
                text = ln.text.substring(from, Math.min(ln.text.length(), from + TICKER_CELLS));
            }
            f.dm(tx, ly, text, f.o().pitch(2f).cells(TICKER_CELLS).level(lv));
        }
    }
}
