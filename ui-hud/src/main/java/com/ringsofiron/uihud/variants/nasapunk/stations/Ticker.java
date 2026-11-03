package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * The callout ticker (section 7.2): three lines in the column, left-aligned, newest at the bottom,
 * each on its own slip with the station's tag boxed before the words. A line that runs past the
 * column wraps onto the next row rather than losing its end, which often carries its point; the
 * oldest rows give way to make room.
 */
final class Ticker extends NpInstrument {
    static final float W = 720f, H = 60f;
    private static final float ROW = 20f, SIZE = 13f, TAG = 11f;

    static final class Line {
        String tag = "SHIP", text = "", count;
        Color color = NpColors.WHITE;
        boolean advisory = true;
    }

    /** Oldest first. */
    final List<Line> lines = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();

    Ticker(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        rows.clear();
        for (Line l : lines) wrap(cv, l);
        int from = Math.max(0, rows.size() - 3);
        for (int i = from; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            Line l = (Line) r[0];
            String text = (String) r[1];
            boolean first = (Boolean) r[2];
            float y = (i - from) * ROW;
            if (first) {
                NpHardware.callout(cv, 0f, y, 0f, l.tag, text, l.color, l.advisory, SIZE, l.count);
            } else {
                // a wrapped row lines up with the words above it
                NpHardware.callout(cv, tagW(cv, l.tag) + 6f, y, 0f, "", text, l.color, l.advisory, SIZE, null);
            }
        }
    }

    private static float tagW(NpCanvas cv, String tag) {
        return cv.width(tag, TAG, true, 0.6f) + 10f;
    }

    /** Splits {@code l} into the rows it takes at the column's width. */
    private void wrap(NpCanvas cv, Line l) {
        float tw = tagW(cv, l.tag) + 12f;
        float count = l.count == null ? 0f : cv.width(l.count, 11f, true, 0f) + 8f;
        float room = W - tw - 14f - count;
        if (cv.width(l.text, SIZE, false, 0f) <= room) {
            rows.add(new Object[] {l, l.text, true});
            return;
        }
        words.clear();
        for (String s : l.text.split(" ")) words.add(s);
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (String s : words) {
            String next = b.length() == 0 ? s : b + " " + s;
            if (b.length() > 0 && cv.width(next, SIZE, false, 0f) > room) {
                rows.add(new Object[] {l, b.toString(), first});
                first = false;
                room = W - tw - 14f;
                b.setLength(0);
                b.append(s);
            } else {
                b.setLength(0);
                b.append(next);
            }
        }
        if (b.length() > 0) rows.add(new Object[] {l, b.toString(), first});
    }
}
