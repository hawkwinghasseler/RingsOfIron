package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * A display unit (lib.js {@code du}): a bezel with fasteners at its corners, the glass, soft keys
 * along any edge with their legends on the glass beside them, a stencilled title in the top border
 * and a part plate in the bottom one. Subclasses draw the glass; a click on a soft key calls
 * {@link #onKey}.
 */
public abstract class NpDu extends NpInstrument {
    public static final int TOP = 0, BOTTOM = 1, LEFT = 2, RIGHT = 3;
    private static final float KEY_ROW = 14f;

    /** How a soft key's legend reads: dim when it can be pressed, white when its page shows. */
    public enum KeyState { IDLE, CURRENT, ON, HIDDEN }

    /** A soft key: its legend, state, share of its edge, and a keyboard key printed on its cap. */
    public static final class Key {
        public String text;
        public KeyState state = KeyState.IDLE;
        public float weight = 1f;
        public String cap;

        public Key(String text) {
            this.text = text;
        }

        public Key(String text, float weight) {
            this.text = text;
            this.weight = weight;
        }
    }

    protected float bezel = 12f;
    protected String title, plate;
    protected float titleSize = 8.5f;
    protected final Key[][] keys = new Key[4][];
    private float gx, gy, gw, gh;

    protected NpDu(NpKit kit) {
        super(kit);
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                int[] hit = keyAt(x, down(y));
                if (hit == null) return false;
                onKey(hit[0], hit[1]);
                return true;
            }
        });
    }

    public void setKeys(int edge, Key... list) {
        keys[edge] = list;
    }

    public Key[] keys(int edge) {
        return keys[edge];
    }

    /** The glass's rectangle in local units, as of the last layout. */
    public float glassX() {
        layoutGlass();
        return gx;
    }

    public float glassY() {
        layoutGlass();
        return gy;
    }

    public float glassW() {
        layoutGlass();
        return gw;
    }

    public float glassH() {
        layoutGlass();
        return gh;
    }

    private void layoutGlass() {
        float w = getWidth(), h = getHeight();
        float padT = keys[TOP] != null ? KEY_ROW : 0f, padB = keys[BOTTOM] != null ? KEY_ROW : 0f;
        float padL = keys[LEFT] != null ? KEY_ROW : 0f, padR = keys[RIGHT] != null ? KEY_ROW : 0f;
        gx = bezel + padL;
        gy = bezel + padT;
        gw = w - 2f * bezel - padL - padR;
        gh = h - 2f * bezel - padT - padB - (plate != null ? 10f : 0f);
    }

    @Override
    protected void paint(NpCanvas cv) {
        layoutGlass();
        float w = getWidth(), h = getHeight();
        NpHardware.bezel(cv, 0f, 0f, w, h);
        if (bezel >= 8f) {
            NpHardware.fastener(cv, 6f, 6f);
            NpHardware.fastener(cv, w - 6f, 6f);
            NpHardware.fastener(cv, 6f, h - 6f);
            NpHardware.fastener(cv, w - 6f, h - 6f);
        }
        NpHardware.glass(cv, gx, gy, gw, gh);
        for (int e = 0; e < 4; e++) if (keys[e] != null) caps(cv, e);
        if (title != null) NpHardware.stencil(cv, w / 2f, bezel - 3f, title, titleSize, 1.4f, NpCanvas.MIDDLE);
        if (plate != null) NpHardware.plate(cv, 18f, h - 13f, plate);
        boolean clipped = getStage() != null && cv.clip(getStage().getCamera(), gx, gy, gw, gh);
        try {
            glass(cv, gx, gy, gw, gh);
        } finally {
            if (clipped) cv.unclip();
        }
        for (int e = 0; e < 4; e++) if (keys[e] != null) legends(cv, e);
        over(cv);
    }

    /** Draws the glass's content; it is clipped to the glass. */
    protected abstract void glass(NpCanvas cv, float gx, float gy, float gw, float gh);

    /** Draws over everything, legends included (a hood, a flag); nothing by default. */
    protected void over(NpCanvas cv) {}

    /** A soft key was clicked: {@code edge} is {@link #TOP} and so on, {@code index} its place. */
    protected void onKey(int edge, int index) {}

    private float span(int edge) {
        return edge == TOP || edge == BOTTOM ? gw : gh;
    }

    private float totalWeight(Key[] list) {
        float t = 0f;
        for (Key k : list) t += k.weight;
        return t;
    }

    /** The centre of key {@code i} along its edge. */
    private float centre(int edge, int i) {
        Key[] list = keys[edge];
        float unit = span(edge) / totalWeight(list), run = 0f;
        for (int j = 0; j < i; j++) run += list[j].weight;
        return (edge == TOP || edge == BOTTOM ? gx : gy) + unit * (run + list[i].weight / 2f);
    }

    private void caps(NpCanvas cv, int edge) {
        Key[] list = keys[edge];
        boolean along = edge == TOP || edge == BOTTOM;
        float kw = along ? 26f : 10f, kh = along ? 10f : 22f;
        for (int i = 0; i < list.length; i++) {
            float c = centre(edge, i), kx, ky;
            if (edge == BOTTOM) { kx = c - kw / 2f; ky = gy + gh + (plate != null ? 2f : 3f); }
            else if (edge == TOP) { kx = c - kw / 2f; ky = bezel - 1f; }
            else if (edge == LEFT) { kx = bezel - 2f; ky = c - kh / 2f; }
            else { kx = gx + gw + 3f; ky = c - kh / 2f; }
            NpHardware.softKey(cv, kx, ky, kw, kh, list[i].state == KeyState.ON);
            if (list[i].cap != null) NpHardware.stencil(cv, kx + kw / 2f, ky + kh - 2.2f, list[i].cap, 7f, 0.4f, NpCanvas.MIDDLE);
        }
    }

    private void legends(NpCanvas cv, int edge) {
        Key[] list = keys[edge];
        for (int i = 0; i < list.length; i++) {
            Key k = list[i];
            if (k.text == null || k.text.isEmpty() || k.state == KeyState.HIDDEN) continue;
            float c = centre(edge, i), lx, ly;
            int anchor;
            if (edge == BOTTOM) { lx = c; ly = gy + gh - 6f; anchor = NpCanvas.MIDDLE; }
            else if (edge == TOP) { lx = c; ly = gy + 15f; anchor = NpCanvas.MIDDLE; }
            else if (edge == LEFT) { lx = gx + 6f; ly = c + 4f; anchor = NpCanvas.START; }
            else { lx = gx + gw - 6f; ly = c + 4f; anchor = NpCanvas.END; }
            Color fill = k.state == KeyState.CURRENT ? NpColors.WHITE : k.state == KeyState.ON ? NpColors.GREEN : NpColors.DIM;
            float tw = cv.text(lx, ly, k.text, 11f, true, fill, 0.6f, anchor);
            if (k.state == KeyState.ON) {
                float bw = tw + 8f, bx = anchor == NpCanvas.MIDDLE ? lx - bw / 2f : anchor == NpCanvas.END ? lx - bw + 4f : lx - 4f;
                cv.stroke(bx, ly - 11f, bw, 15f, 1f, NpColors.GREEN);
            }
        }
    }

    /** Which soft key holds local point ({@code x}, {@code y} down), as {edge, index}, or null. */
    protected int[] keyAt(float x, float y) {
        layoutGlass();
        for (int e = 0; e < 4; e++) {
            Key[] list = keys[e];
            if (list == null) continue;
            boolean along = e == TOP || e == BOTTOM;
            float unit = span(e) / totalWeight(list), run = 0f;
            for (int i = 0; i < list.length; i++) {
                float a = (along ? gx : gy) + unit * run, b = a + unit * list[i].weight;
                run += list[i].weight;
                if (list[i].state == KeyState.HIDDEN) continue;
                boolean inSpan = along ? x >= a && x < b : y >= a && y < b;
                if (!inSpan) continue;
                // the key's band: its cap in the bezel and its legend's line on the glass
                boolean inBand;
                if (e == BOTTOM) inBand = y >= gy + gh - 18f && y <= getHeight();
                else if (e == TOP) inBand = y >= 0f && y <= gy + 20f;
                else if (e == LEFT) inBand = x >= 0f && x <= gx + Math.min(gw, 90f);
                else inBand = x >= gx + gw - Math.min(gw, 90f) && x <= getWidth();
                if (inBand) return new int[] {e, i};
            }
        }
        return null;
    }
}
