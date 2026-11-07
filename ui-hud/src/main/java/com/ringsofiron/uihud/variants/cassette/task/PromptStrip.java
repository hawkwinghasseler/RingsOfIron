package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * INT, the interaction prompt on deck: a short amber strip with the key and what it does ("E  PUT ON A
 * PRESSURE SUIT"), standing above the helm line's zone at the bottom centre, where the classic prompt
 * was. The game gives the prompt's words, not where the thing is, so the strip can't stand beside it yet.
 */
final class PromptStrip extends YardPart {
    static final float H = 28f;
    private static final float SIZE = 13f;

    private final TaskFrame f;

    PromptStrip(TaskFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The prompt's words without the classic line's "to": "PUT ON A PRESSURE SUIT", "FOR THE HELM PANEL". */
    static String words(HudData d) {
        String s = d.promptAction.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("to ")) s = s.substring(3);
        return s.toUpperCase(Locale.ROOT);
    }

    static String key(HudData d) {
        String k = d.promptKey == null || d.promptKey.isEmpty() ? "E" : d.promptKey;
        return k.toUpperCase(Locale.ROOT);
    }

    /**
     * The strip's segments: the key, then the words, a "[V]" among them a key of its own, as the game's
     * prompts put a second key in brackets ("FOR THE GUN PANEL   [V] TAKE THE GUN"). At the helm and the
     * weapons console E opens no panel but seats the captain, as V does (spec CON): "E OR V  SIT AND FLY".
     */
    static String[] segments(HudData d) {
        List<String> out = new ArrayList<>();
        out.add(YardPaint.KEY + key(d));
        String w = words(d);
        if (ConsolePanel.seat(d.promptConsole)) {
            int a = w.indexOf('['), b = a < 0 ? -1 : w.indexOf(']', a);
            if (a >= 0 && b > a && b - a <= 9) {
                out.add("OR ");
                out.add(YardPaint.KEY + w.substring(a + 1, b));
                out.add(w.substring(b + 1).trim());
                return out.toArray(new String[0]);
            }
        }
        int i = 0;
        while (i < w.length()) {
            int a = w.indexOf('[', i), b = a < 0 ? -1 : w.indexOf(']', a);
            if (a < 0 || b < 0 || b - a > 9) {
                out.add(w.substring(i));
                break;
            }
            String before = w.substring(i, a).trim();
            if (!before.isEmpty()) out.add(before + "   ");
            out.add(YardPaint.KEY + w.substring(a + 1, b));
            i = b + 1;
            while (i < w.length() && w.charAt(i) == ' ') i++;
        }
        return out.toArray(new String[0]);
    }

    /** Every key the strip shows, so the sill can leave them out. */
    static List<String> keys(HudData d) {
        List<String> out = new ArrayList<>();
        for (String s : segments(d)) if (s.charAt(0) == YardPaint.KEY) out.add(s.substring(1));
        return out;
    }

    /** The strip's width for this frame's words. */
    float measure(YardPaint p) {
        return p.tubeLineWidth(SIZE, segments(f.data)) + 30f;
    }

    @Override
    protected void draw(YardPaint p) {
        p.rrect(1f, 2f, w, H, 4f, Yard.SHADOW, 0.6f);
        p.rrect(0f, 0f, w, H, 4f, Yard.BAKELITE);
        p.tube(8f, 5f, w - 16f, H - 10f, 2f, 2f, false);
        p.tubeLine(14f, 18.5f, SIZE, Yard.P_AMBER, segments(f.data));
        p.tubeOver(8f, 5f, w - 16f, H - 10f);
    }
}
