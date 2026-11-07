package com.ringsofiron.uihud.variants.vfd.screens;

import com.ringsofiron.uihud.providers.GameOverProvider.Choice;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Ink;

import java.util.Locale;

/**
 * The ship lost or the career over (the mockups' card-lost): the tubes go dark, and a printed card says
 * what happened and the ways on, each its key's cap and what it does. The game keeps those keys (its
 * quick load, Enter, Esc): the card only names them, and a click on a way runs it as its key would.
 */
public final class LostCard {
    /** The card's width, its height with three ways on, and a way's pitch. */
    public static final float W = 520f, H = 236f, ROW = 28f;
    /** Where a way's words start at the least, and the most lines what happened wraps to. */
    static final float WORDS = 96f;
    static final int MAX_LINES = 3;

    private float x, y, h, stageW, stageH;
    private int ways;
    private final String[] lines = new String[MAX_LINES];
    private int lineCount;
    private final StringBuilder sb = new StringBuilder();

    /** Places the card in the middle of a {@code w} x {@code h} stage for {@code g}'s ways on. */
    public void place(float stageW, float stageH, GameOver g) {
        this.stageW = stageW;
        this.stageH = stageH;
        ways = g.choices.size;
        fit();
    }

    /** The card's height for its ways and what happened's lines, in the middle of the stage. */
    private void fit() {
        h = H + Math.max(0, ways - 3) * ROW + Math.max(0, lineCount - 1) * 19f;
        x = (stageW - W) / 2f;
        y = Math.max(44f, (stageH - h) / 2f - 10f);
    }

    public void paint(Filament f, GameOver g) {
        wrap(f, g.line);
        ways = g.choices.size;
        fit();
        f.card(x, y, W, h);
        f.print(x + 30f, y + 52f, g.title, f.o().size(30f).track(0.04f));
        float ly = y + 80f;
        for (int i = 0; i < lineCount; i++) f.print(x + 30f, ly + i * 19f, lines[i], f.o().size(15f));
        float rule = y + 98f + Math.max(0, lineCount - 1) * 19f;
        f.ink(x + 30f, rule, W - 60f, 1.4f, 0f, Ink.CARD_INK, 1f);
        for (int i = 0; i < g.choices.size; i++) {
            Choice c = g.choices.get(i);
            float ry = rule + 36f + i * ROW, cx = x + 30f;
            cx += f.printedKey(cx, ry - 13f, c.key.toUpperCase(Locale.ROOT), 18f, 12f) + 3f;
            f.print(Math.max(cx + 6f, x + 30f + WORDS), ry, c.label, f.o().size(13f));
        }
    }

    /** What happened in lines that fit the card. */
    private void wrap(Filament f, String text) {
        lineCount = 0;
        sb.setLength(0);
        float room = W - 60f;
        String s = text == null ? "" : text;
        int at = 0;
        while (at < s.length() && lineCount < MAX_LINES) {
            int sp = s.indexOf(' ', at);
            if (sp < 0) sp = s.length();
            String word = s.substring(at, sp);
            int before = sb.length();
            if (before > 0) sb.append(' ');
            sb.append(word);
            if (before > 0 && f.printWidth(sb, 15f, 0.02f) > room && lineCount < MAX_LINES - 1) {
                sb.setLength(before);
                lines[lineCount++] = sb.toString();
                sb.setLength(0);
                sb.append(word);
            }
            at = sp + 1;
        }
        if (sb.length() > 0 && lineCount < MAX_LINES) lines[lineCount++] = sb.toString();
    }

    /** Each way that a click can run, as {@code base + i}. */
    public void boxes(FilamentActor a, int base, GameOver g) {
        float rule = y + 98f + Math.max(0, lineCount - 1) * 19f;
        for (int i = 0; i < g.choices.size; i++) {
            if (g.choices.get(i).run == null) continue;
            a.box(base + i, x + 20f, rule + 36f + i * ROW - 19f, W - 40f, ROW - 2f);
        }
    }

    /** A click on way {@code i}: runs it, as its key would. */
    public void clicked(int i, GameOver g) {
        if (i < 0 || i >= g.choices.size) return;
        Runnable run = g.choices.get(i).run;
        if (run != null) run.run();
    }
}
