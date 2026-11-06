package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.GameOverProvider.Choice;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.Locale;

/**
 * The ship lost or the career over, as a telex (style guide 9.7): a sheet that prints over the dimmed
 * view at 30 rows a second, with a header line of the day and time, the title, and what happened in
 * capitals; under it, the ways on with their keys on a plate. The game keeps those keys (Enter, the
 * quick load, Esc); a click on a way on runs it as its key would.
 */
final class Telex extends YardPart implements Hits.Target {
    private static final Color SHEET_TOP = Yard.hex("F4F0E2"), SHEET_FOOT = Yard.hex("E7E0CB");
    private static final float ROWS_A_SECOND = 30f, ROW = 24f, W = 640f;
    /** The most characters on a row of what happened. */
    static final int LINE = 46;
    /** Which row is the title, typed larger and in red. */
    static final int TITLE_ROW = 3;
    private final Hits hits = new Hits();
    /** The sheet's rows as it will print them. */
    final Array<String> rows = new Array<>();
    private GameOver over;
    private float t;
    private String read;

    Telex() {
        super(0f, 0f);
    }

    void show(GameOver over, double simSeconds) {
        this.over = over;
        String key = over.title + "|" + over.line;
        if (!visible || !key.equals(read)) {
            read = key;
            t = 0f;
            rows.clear();
            rows.add("RINGS OF IRON TELEX   " + Fmt.stamp(simSeconds));
            rows.add("ZCZC");
            rows.add("");
            rows.add(over.title.toUpperCase(Locale.ROOT));
            rows.add("");
            // Telex lines are short: what happened breaks at the last space within LINE characters
            String s = over.line.toUpperCase(Locale.ROOT);
            while (s.length() > LINE) {
                int cut = s.lastIndexOf(' ', LINE);
                if (cut <= 0) cut = LINE;
                rows.add(s.substring(0, cut));
                s = s.substring(cut).trim();
            }
            rows.add(s);
            rows.add("");
            rows.add("NNNN");
        }
        visible = true;
    }

    void hide() {
        visible = false;
        read = null;
    }

    void update(float delta) {
        t += delta;
    }

    @Override
    public boolean pressable(float px, float py) {
        return hits.any(px, py);
    }

    @Override
    public boolean press(float px, float py) {
        hits.press(px, py);
        // Over the dimmed view nothing else takes the pointer
        return true;
    }

    @Override
    public void hit(int code) {
        if (over == null || code < 0 || code >= over.choices.size) return;
        Choice c = over.choices.get(code);
        if (c.run != null) c.run.run();
    }

    @Override
    protected void draw(YardPaint p) {
        hits.clear();
        p.rect(0f, 0f, w, h, Color.BLACK, 0.6f);
        if (over == null) return;
        int printed = Math.min(rows.size, (int) (t * ROWS_A_SECOND) + 1);
        float sheetH = rows.size * ROW + 48f, sx = (w - W) / 2f, sy = Math.max(30f, (h - sheetH - 120f) / 2f);
        // The sheet, torn off the roll along its top
        p.rect(sx + 3f, sy + 4f, W, sheetH, Yard.SHADOW, 0.7f);
        p.vgrad(sx, sy, W, sheetH, SHEET_TOP, SHEET_FOOT);
        for (float tx = sx; tx < sx + W; tx += 5f) p.rect(tx, sy - 1.5f, 2.5f, 1.6f, SHEET_TOP);
        p.rect(sx, sy + sheetH - 8f, W, 8f, Color.BLACK, 0.05f);
        for (int i = 0; i < printed; i++) {
            String s = rows.get(i);
            if (s.isEmpty()) continue;
            boolean title = i == TITLE_ROW;
            p.text(YardFonts.Face.PAPER, title ? 22f : 15f, s, sx + 34f, sy + 38f + i * ROW, title ? Paper.STAMP : Paper.INK);
        }
        if (printed < rows.size) return;
        // The ways on, their keys on a plate under the sheet
        float py = sy + sheetH + 18f, ph = 22f + over.choices.size * 30f;
        p.rrect(sx + 2f, py + 3f, W, ph, 4f, Yard.SHADOW, 0.6f);
        p.rrect(sx, py, W, ph, 4f, Yard.ALU);
        p.glass(sx, py, W, ph);
        p.screw(sx + 9f, py + 9f);
        p.screw(sx + W - 9f, py + 9f);
        for (int i = 0; i < over.choices.size; i++) {
            Choice c = over.choices.get(i);
            float ry = py + 14f + i * 30f;
            float kw = p.key(sx + 34f, ry + 2f, c.key.isEmpty() ? "--" : c.key);
            p.text(YardFonts.Face.LEGEND, 14f, c.label.toUpperCase(Locale.ROOT), sx + 34f + Math.max(kw, 60f) + 14f, ry + 14.5f, Yard.INK);
            if (c.run != null) hits.add(p, sx + 20f, ry - 3f, W - 40f, 26f, this, i);
        }
    }
}
