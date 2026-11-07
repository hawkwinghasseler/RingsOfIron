package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * One of the band's two multi-function displays (spec 9.1): a green tube in its pod, showing one page of
 * one station, with a rail of soft keys under it naming that station's pages, a PIN latch and a FOLD key.
 * Folded, it drops to its rail and leaves the world above it.
 *
 * <p>What it shows comes from three places, in this order: an alert that took it (auto-paging: the
 * newest worst caution or warning turns the right MFD to its home page, or the left when the right is
 * pinned, and gives it back 10 s after the alert clears); the player's own pick, which holds until they
 * stand up; and else the seat's and the condition's default (the 9.1 table, CARGO read as COMMS).
 */
final class Mfd extends YardPart {
    static final float W = StationsLayout.MFD_W, H = StationsLayout.UNIT_H;
    /** How long a page an alert took stays after the alert clears. */
    static final float GIVE_BACK = 10f;
    private static final float Y = 616f, FOLDED_Y = 744f, RAIL_H = 24f, EXTRA = 58f;
    private static final Color KEY_ON = Yard.hex("3E3732"), LATCH = Yard.hex("5A524B"), PIN_INK = Yard.hex("2A1606");

    final boolean right;
    private final StationsFrame f;
    private final Pages pages;

    Page page = Page.SENSORS;
    boolean folded, pinned;
    /** The player picked a page or folded it: it holds until they stand up. */
    boolean chosen;
    /** The alert that took it, and what it showed before. */
    String autoId;
    private Level autoLevel;
    private Page before;
    private boolean beforeFolded, beforeChosen;
    private float clearedAt = Float.NaN;
    /** The player opened it during warp, which folds the MFDs while nothing is wrong (14.2). */
    private boolean warpOpened;

    Mfd(StationsFrame f, Pages pages, boolean right) {
        super(right ? 896f : 96f, Y);
        this.f = f;
        this.pages = pages;
        this.right = right;
    }

    /** As the band shows it: folded by the player or a default, or by warp with no caution or warning up. */
    boolean showsFolded() {
        return folded || f.warp && !warpOpened && !f.alerts.annunciatorUp();
    }

    @Override
    protected float top() {
        return showsFolded() ? FOLDED_Y : Y;
    }

    /** The player stood up: every choice goes, and the defaults come back next time. */
    void reset() {
        chosen = pinned = warpOpened = false;
        autoId = null;
        clearedAt = Float.NaN;
    }

    /** The seat's and condition's default, unless the player chose or an alert holds it. */
    void defaults(Page p, boolean fold) {
        if (chosen || autoId != null) return;
        page = p;
        folded = fold;
    }

    /** The player's pick: a soft key, a tile, a station board key. Ends any alert's hold on it. */
    void pick(Page p) {
        page = p;
        folded = false;
        chosen = true;
        autoId = null;
        if (f.warp) warpOpened = true;
    }

    /** The station's first page, or its next when it already shows that station (8.5). */
    void cycle(Station s) {
        Page[] all = s.pages();
        Page next = all[0];
        if (page.station == s && !showsFolded()) {
            for (int i = 0; i < all.length; i++) if (all[i] == page) next = all[(i + 1) % all.length];
        }
        pick(next);
    }

    void fold() {
        if (showsFolded()) {
            folded = false;
            if (f.warp) warpOpened = true;
        } else folded = true;
        chosen = true;
        autoId = null;
    }

    void pin() {
        pinned = !pinned;
        chosen = true;
    }

    /**
     * Alert {@code a} would take this MFD: it does unless a worse alert already holds it (the most severe,
     * then the newest, keeps the MFD; the rest wait on the annunciator).
     */
    boolean take(Alert a, Page home, HudData d) {
        if (pinned) return false;
        if (autoId != null && !autoId.equals(a.id) && d.alerts.get(autoId) != null && autoLevel.ordinal() > a.level.ordinal()) return false;
        if (autoId == null) {
            before = page;
            beforeFolded = folded;
            beforeChosen = chosen;
        }
        autoId = a.id;
        autoLevel = a.level;
        clearedAt = Float.NaN;
        page = home;
        folded = false;
        return true;
    }

    /** Gives the page back 10 s after the alert that took it clears; back sooner if it comes back. */
    void tick(HudData d, float clock) {
        if (autoId == null) return;
        if (d.alerts.get(autoId) != null) {
            clearedAt = Float.NaN;
            return;
        }
        if (Float.isNaN(clearedAt)) clearedAt = clock;
        else if (clock - clearedAt >= GIVE_BACK) {
            autoId = null;
            page = before;
            folded = beforeFolded;
            chosen = beforeChosen;
        }
    }

    @Override
    protected void draw(YardPaint p) {
        float x = mx;
        if (showsFolded()) {
            Hardware.pod(p, x, FOLDED_Y, W, StationsLayout.FOLDED_H, true);
            rail(p, x + 5f, FOLDED_Y + 6f, W - 10f, true);
            return;
        }
        Hardware.pod(p, x, Y, W, H, true);
        Hardware.plate(p, x + 5f, Y + 10f, W - 10f, 118f, true);
        float tx = x + 16f, ty = Y + 18f, tw = W - 32f, th = 102f;
        boolean live = f.live(page.station);
        p.tube(tx, ty, tw, th, 6f, 5f, !live);
        if (live) pages.draw(p, f, page, tx, ty, tw, th);
        else Pages.dead(p, tx, ty, tw, th, page.station.tag + ": NO POWER");
        p.tubeOver(tx, ty, tw, th);
        rail(p, x + 5f, Y + 132f, W - 10f, false);
    }

    /** The soft keys: the station's pages on flap legends, the PIN latch, the FOLD key with its chevron. */
    private void rail(YardPaint p, float x, float y, float w, boolean fold) {
        p.rrect(x, y, w, RAIL_H, 3f, Yard.CHASSIS_DEEP);
        Page[] all = page.station.pages();
        int n = all.length;
        float kw = keyWidth(w, n);
        for (int i = 0; i < n; i++) {
            float kx = x + 4f + i * (kw + 5f);
            boolean on = all[i] == page && !fold;
            float dy = on ? 1.5f : 0f;
            p.rrect(kx + 0.8f, y + 4.5f + dy, kw, RAIL_H - 6f, 2.5f, Yard.SHADOW, 0.6f);
            p.rrect(kx, y + 3f + dy, kw, RAIL_H - 6f, 2.5f, on ? KEY_ON : Yard.BAKELITE);
            String l = all[i].label();
            float fw = l.length() * 8.4f;
            p.flaps(kx + (kw - fw) / 2f, y + 6.5f + dy, l, l.length(), 8.4f, 11.5f, 11.5f, on ? Yard.P_AMBER : Yard.FLAP_TEXT);
        }
        float px = x + w - EXTRA + 2f, pdy = pinned ? 2f : 0f;
        p.rrect(px - 1f, y + 2f, 26f, RAIL_H - 4f, 2f, LATCH);
        p.rrect(px + 1.6f, y + 5.5f + pdy, 22f, RAIL_H - 9f, 2f, Yard.SHADOW, 0.6f);
        p.rrect(px + 1f, y + 4f + pdy, 22f, RAIL_H - 9f, 2f, pinned ? Yard.ORANGE : Yard.BAKELITE);
        p.textC(Face.LEGEND, 12f, "PIN", px + 12f, y + RAIL_H / 2f + 4f + pdy, pinned ? PIN_INK : Yard.INK_LIGHT);
        float fx = px + 29f, cy = y + RAIL_H / 2f;
        p.rrect(fx + 0.8f, y + 4.5f, 24f, RAIL_H - 6f, 2.5f, Yard.SHADOW, 0.6f);
        p.rrect(fx, y + 3f, 24f, RAIL_H - 6f, 2.5f, Yard.BAKELITE);
        if (fold) p.polyline(new float[]{fx + 7f, cy + 3f, fx + 12f, cy - 3f, fx + 17f, cy + 3f}, false, 1.8f, Yard.INK_LIGHT, 1f);
        else p.polyline(new float[]{fx + 7f, cy - 3f, fx + 12f, cy + 3f, fx + 17f, cy - 3f}, false, 1.8f, Yard.INK_LIGHT, 1f);
    }

    private static float keyWidth(float w, int n) {
        return (w - EXTRA - 8f - (n - 1) * 5f) / n;
    }

    // --- Presses -------------------------------------------------------------------------------------

    /** Which of the rail's keys is at mockup ({@code lx}, {@code ly}): a page's index, -1 PIN, -2 FOLD, else -3. */
    private int keyAt(float lx, float ly) {
        float rx = mx + 5f, rw = W - 10f, ry = showsFolded() ? FOLDED_Y + 6f : Y + 132f;
        if (ly < ry || ly > ry + RAIL_H || lx < rx || lx > rx + rw) return -3;
        Page[] all = page.station.pages();
        float kw = keyWidth(rw, all.length);
        for (int i = 0; i < all.length; i++) {
            float kx = rx + 4f + i * (kw + 5f);
            if (lx >= kx && lx <= kx + kw) return i;
        }
        float px = rx + rw - EXTRA + 2f;
        if (lx >= px - 1f && lx <= px + 25f) return -1;
        if (lx >= px + 29f && lx <= px + 53f) return -2;
        return -3;
    }

    @Override
    public boolean pressable(float px, float py) {
        return keyAt(localX(px), localY(py)) != -3;
    }

    @Override
    public boolean press(float px, float py) {
        int k = keyAt(localX(px), localY(py));
        if (k == -3) return false;
        if (k == -1) pin();
        else if (k == -2) fold();
        else pick(page.station.pages()[k]);
        return true;
    }

    @Override
    public String hover(float px, float py) {
        int k = keyAt(localX(px), localY(py));
        if (k == -1) return pinned ? "Pinned: an alert will not turn this display" : "Pin: an alert will not turn this display";
        if (k == -2) return showsFolded() ? "Raise the display" : "Fold the display to its keys";
        if (k >= 0) return page.station.tag + " station: " + page.station.pages()[k].label();
        if (!showsFolded() && !f.live(page.station)) return "The " + page.station.tag + " console has no power: its pages are dead";
        return null;
    }
}
