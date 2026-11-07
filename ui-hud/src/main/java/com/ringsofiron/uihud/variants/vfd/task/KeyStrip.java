package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * HNT, the key strip along the bottom (functional-task 6, HNT): at most four keys for what can be
 * done here and now, then F1 for the controls, each a row of key caps with its word in a small window.
 * A key the player has used three times in a context drops off there (the default, learning). A key
 * already on screen never repeats here: a mode's keys, the action the warning names, the crisis
 * panel's and the prompt's. In a crisis it drops to F1 alone, so nothing competes with the panel. It
 * fades to dim after 10 s without a change of context, and is not drawn over a screen. Each word's
 * window is as wide as the word; a strip that would run past its limit drops keys from its end, F1
 * last of all.
 */
final class KeyStrip {
    static final int ITEMS = 4;
    /** Uses in one context after which a key drops off. */
    static final int LEARNED = 3;
    static final float FADE = 10f;
    /** The longest word a window takes; the game's are shorter ("sit at the console", "raise / lower shields"). */
    static final int WORD_CELLS = 24;
    /** The strip's right end may not pass this. */
    static final float LIMIT = 1264f;

    boolean on, faded;
    int count;
    final String[][] caps = new String[ITEMS + 1][];
    final String[] words = new String[ITEMS + 1], ids = new String[ITEMS + 1];
    String placing = "";
    private String context = "";
    private float since;
    private final ObjectIntMap<String> uses = new ObjectIntMap<>();
    private final Array<String> shown = new Array<>();

    /**
     * Reads this frame's hints. {@code elsewhere} holds the keys shown by other parts this frame (in
     * capitals): those are left out.
     */
    void update(HudData d, Posts p, float now, Array<String> elsewhere) {
        count = 0;
        placing = "";
        on = p.common();
        if (!on) return;
        String ctx = context(p, d);
        if (!ctx.equals(context)) {
            context = ctx;
            since = now;
        }
        faded = now - since > FADE;
        if (d.keyHints.placing) {
            placing = Words.cut(d.keyHints.placingText, 60);
            return;
        }
        Array<KeyHint> hints = d.keyHints.hints;
        KeyHint help = null;
        for (int i = 0; i < hints.size; i++) {
            KeyHint h = hints.get(i);
            if (h.key.isEmpty() || h.label.isEmpty()) continue;
            if (Action.HELP.name().equals(h.action)) {
                help = h;
                continue;
            }
            if (p.crisis || count == ITEMS) continue;
            String[] c = caps(h.key);
            if (repeats(c, elsewhere)) continue;
            String id = id(h);
            if (uses.get(ctx + "|" + id, 0) >= LEARNED) continue;
            add(c, ControlsTab.word(h), id);
        }
        // Help last, always
        add(help == null ? new String[] {Catalogue.key(d, Action.HELP.name(), Action.HELP.defaultKeyName())} : caps(help.key),
                help == null ? "CONTROLS" : help.label, Action.HELP.name());
    }

    private void add(String[] c, String word, String id) {
        caps[count] = c;
        words[count] = Words.cut(word, WORD_CELLS);
        ids[count] = id;
        count++;
    }

    /** The context a key is learned in: the post and the conditions that change what it does. */
    static String context(Posts p, HudData d) {
        return p.post.name() + (p.docked ? ":DOCKED" : "") + (p.latched ? ":LATCHED" : "") + (p.combat ? ":COMBAT" : "")
                + (p.rails ? ":WARP" : "") + (p.atGun ? ":GUN" : "") + (d.keyHints.placing ? ":PLACING" : "");
    }

    private static String id(KeyHint h) {
        return h.action.isEmpty() ? h.key.toUpperCase(Locale.ROOT) : h.action;
    }

    /** "W/S" is two caps, W and S; "Space" is SPACE. */
    static String[] caps(String key) {
        String k = key.trim();
        // The slash is a key too: "/" is one cap, "W/S" two
        if (k.equals("/")) return new String[] {"/"};
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : k.split("\\s*/\\s*")) if (!part.isEmpty()) out.add(part.toUpperCase(Locale.ROOT));
        return out.isEmpty() ? new String[] {k.toUpperCase(Locale.ROOT)} : out.toArray(new String[0]);
    }

    private static boolean repeats(String[] c, Array<String> elsewhere) {
        for (String k : c) if (elsewhere.contains(k, false)) return true;
        return false;
    }

    /** A key the classic HUD did not use: a use of whichever shown key it is, in this context. */
    void pressed(int keycode) {
        if (!on || count == 0) return;
        String name = Action.keyName(keycode).toUpperCase(Locale.ROOT);
        for (int i = 0; i < count; i++) {
            if (Action.HELP.name().equals(ids[i])) continue;
            for (String k : caps[i]) {
                if (k.equals(name)) {
                    String key = context + "|" + ids[i];
                    uses.put(key, uses.get(key, 0) + 1);
                    return;
                }
            }
        }
    }

    /** The keys this frame shows, for the parts that check they are not repeated. */
    Array<String> shownKeys() {
        shown.clear();
        for (int i = 0; i < count; i++) for (String k : caps[i]) shown.add(k);
        return shown;
    }

    // ------------------------------------------------------------------ painting, from the bottom left origin

    void paint(Filament f) {
        if (!on || count == 0 && placing.isEmpty()) return;
        float x = Geo.LX, y = Geo.HNT_Y, h = Geo.HNT_H;
        float level = faded ? Drive.DIM : Drive.NORM;
        if (!placing.isEmpty()) {
            float w = 12f + f.dmWidth(placing.length(), f.o().pitch(Geo.P));
            f.plate(x, y, w, h, 3f, 6f);
            f.module(x, y + 3f, w, 18f, f.o().filaments(0).noGlare().radius(2f));
            f.dm(x + 6f, y + 6.75f, placing, f.o().pitch(Geo.P).level(level));
            return;
        }
        // F1 is last and always shown: the keys before it go from the end while the strip runs too long
        int last = count - 1, shownCount = count;
        // The plate's padding (6 each side) takes the place of the last item's gap
        float w = itemWidth(f, last);
        for (int i = 0; i < last; i++) w += itemWidth(f, i);
        while (shownCount > 1 && x + w > LIMIT) {
            shownCount--;
            w -= itemWidth(f, shownCount - 1);
        }
        f.plate(x, y, w, h, 3f, 6f);
        float cx = x + 6f;
        for (int n = 0; n < shownCount; n++) {
            int i = n == shownCount - 1 ? last : n;
            for (String k : caps[i]) cx += f.keycap(cx, y + 3f, k, 18f, 12f, false) + 3f;
            cx += 1f;
            float ww = f.dmWidth(words[i].length(), f.o().pitch(Geo.P)) + 12f;
            f.module(cx, y + 3f, ww, 18f, f.o().filaments(0).noGlare().radius(2f));
            f.dm(cx + 6f, y + 6.75f, words[i], f.o().pitch(Geo.P).level(level));
            cx += ww + 12f;
        }
        Kit.fits(x + w, LIMIT, "key strip");
    }

    /** One key's caps and word window, and the gap after it. */
    private float itemWidth(Filament f, int i) {
        return capsWidth(f, caps[i]) + 4f + f.dmWidth(words[i].length(), f.o().pitch(Geo.P)) + 12f + 12f;
    }

    private static float capsWidth(Filament f, String[] c) {
        float w = 0f;
        for (String k : c) w += f.keycapWidth(k, 18f, 12f) + 3f;
        return w - 3f;
    }
}
