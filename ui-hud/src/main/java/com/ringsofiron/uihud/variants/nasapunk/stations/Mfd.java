package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.EnumMap;
import java.util.Map;

/**
 * An MFD (section 9.1): a 288 x 160 display unit showing one page, its soft keys along the bottom
 * naming the station's pages in full, then PIN and FOLD at a little over half a page key's room
 * each. Folded it is its soft-key rail alone, 288 x 24, the world showing above it; a page key
 * opens it. A console with no power shows a dead glass reading NO POWER rather than a stale page.
 */
final class Mfd extends NpDu {
    static final float W = StationsLayout.MFD_W, H = StationsLayout.MFD_H, RAIL = StationsLayout.RAIL_H;
    private static final float SMALL = 0.55f;

    /** What a click asks for. */
    interface Keys {
        void page(Mfd m, Page p);

        void pin(Mfd m);

        void fold(Mfd m);

        /** SENSORS' range: −1 steps out, +1 in. */
        void range(Mfd m, int step);
    }

    final boolean left;
    Page page = Page.NAV;
    boolean folded = true, pinned, noPower;
    Pages pages;
    Keys onKeys;
    /** SENSORS' range on this MFD: the step, and whether the player set it. */
    int rangeStep = 3;
    boolean rangeSet;
    /** Where SENSORS' − and + sit on the glass, as drawn, x then y down; NaN with none. */
    float minusX = Float.NaN, plusX = Float.NaN, buttonsY = Float.NaN;

    private final Map<Station, Key[]> keySets = new EnumMap<>(Station.class);
    private Key[] shown;

    Mfd(NpKit kit, boolean left) {
        super(kit);
        this.left = left;
        bezel = 8f;
        for (Station s : Station.values()) {
            Page[] p = s.pages();
            Key[] k = new Key[p.length + 2];
            for (int i = 0; i < p.length; i++) k[i] = new Key("");
            k[p.length] = new Key("", SMALL);
            k[p.length + 1] = new Key("", SMALL);
            keySets.put(s, k);
        }
        show(Page.NAV, true, false);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (folded || page != Page.SENSORS || Float.isNaN(buttonsY) || onKeys == null) return false;
                float yd = down(y);
                if (yd < buttonsY || yd > buttonsY + 15f) return false;
                if (x >= minusX && x <= minusX + 18f) {
                    onKeys.range(Mfd.this, -1);
                    return true;
                }
                if (x >= plusX && x <= plusX + 18f) {
                    onKeys.range(Mfd.this, 1);
                    return true;
                }
                return false;
            }
        });
    }

    /** Shows {@code p}, open or folded, with its station's keys. */
    void show(Page p, boolean folded, boolean pinned) {
        this.page = p;
        this.folded = folded;
        this.pinned = pinned;
        shown = keySets.get(p.station);
        Page[] list = p.station.pages();
        for (int i = 0; i < list.length; i++) shown[i].state = list[i] == p ? KeyState.CURRENT : KeyState.IDLE;
        shown[list.length].state = pinned ? KeyState.ON : KeyState.IDLE;
        shown[list.length + 1].state = folded ? KeyState.CURRENT : KeyState.IDLE;
        setKeys(BOTTOM, shown);
        setSize(W, folded ? RAIL : H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        minusX = plusX = buttonsY = Float.NaN;
        if (!folded) {
            super.paint(cv);
            return;
        }
        // the rail: the bezel's foot with the legends on a strip of glass and the keys under them
        NpHardware.bezel(cv, 0f, 0f, W, RAIL, 3f);
        cv.fill(6f, 2f, W - 12f, 16f, NpColors.GLASS);
        float unit = (W - 12f) / weight(), run = 0f;
        Page[] list = page.station.pages();
        for (int i = 0; i < shown.length; i++) {
            float c = 6f + unit * (run + shown[i].weight / 2f);
            run += shown[i].weight;
            legend(cv, c, 14.5f, label(list, i), shown[i].state);
            NpHardware.softKey(cv, c - 13f, 19.5f, 26f, 3f, shown[i].state == KeyState.ON);
        }
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        if (noPower) {
            StDraw.dead(cv, gx, gy, gw, gh);
            return;
        }
        if (pages != null) pages.draw(cv, this, gx, gy, gw, gh);
    }

    /** The legends at the text floor, over the glass by each key. */
    @Override
    protected void over(NpCanvas cv) {
        float gx = glassX(), gw = glassW(), base = glassY() + glassH() - 6f;
        float unit = gw / weight(), run = 0f;
        Page[] list = page.station.pages();
        for (int i = 0; i < shown.length; i++) {
            float c = gx + unit * (run + shown[i].weight / 2f);
            run += shown[i].weight;
            legend(cv, c, base, label(list, i), shown[i].state);
        }
    }

    private static String label(Page[] list, int i) {
        return i < list.length ? list[i].title : i == list.length ? "PIN" : "FOLD";
    }

    private static void legend(NpCanvas cv, float cx, float base, String t, KeyState st) {
        Color c = st == KeyState.CURRENT ? NpColors.WHITE : st == KeyState.ON ? NpColors.GREEN : NpColors.DIM;
        float tw = cv.text(cx, base, t, StDraw.LB, true, c, 0.5f, NpCanvas.MIDDLE);
        if (st == KeyState.ON) cv.stroke(cx - tw / 2f - 4f, base - 11f, tw + 8f, 14f, 1f, NpColors.GREEN);
    }

    private float weight() {
        float t = 0f;
        for (Key k : shown) t += k.weight;
        return t;
    }

    @Override
    protected int[] keyAt(float x, float y) {
        if (!folded) return super.keyAt(x, y);
        if (x < 6f || x > W - 6f || y < 0f || y > RAIL) return null;
        float unit = (W - 12f) / weight(), run = 0f;
        for (int i = 0; i < shown.length; i++) {
            float a = 6f + unit * run, b = a + unit * shown[i].weight;
            run += shown[i].weight;
            if (x >= a && x < b) return new int[] {BOTTOM, i};
        }
        return null;
    }

    @Override
    protected void onKey(int edge, int index) {
        if (onKeys == null || edge != BOTTOM) return;
        Page[] list = page.station.pages();
        if (index < list.length) onKeys.page(this, list[index]);
        else if (index == list.length) onKeys.pin(this);
        else onKeys.fold(this);
    }
}
