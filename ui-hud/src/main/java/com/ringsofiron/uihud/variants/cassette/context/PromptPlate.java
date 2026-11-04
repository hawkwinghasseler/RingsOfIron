package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * P6, the prompt: what the player can do right here, with which key, on one strip tube over the seat's
 * instrument (the game gives the HUD no place in the world for the thing it refers to, so it is the
 * bottom centre's). While something is being placed, how to place it; on deck, what the interact key
 * does here; else at most three learning hints, dim, each retired once its action has been used three
 * times. While the ship is disabled the emergency placard carries abandon ship and the tow, so the hints
 * leave them out.
 */
final class PromptPlate extends YardPart {
    static final float W = 400f, H = 24f;
    /** Uses of an action after which its hint retires. */
    static final int RETIRE = 3;
    private static final int HINTS = 3;

    private final ContextFrame f;
    private final ObjectIntMap<String> uses = new ObjectIntMap<>();
    private final Array<String[]> hints = new Array<>();
    private String key = "", text = "";

    PromptPlate(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The player pressed {@code keycode}: the hints for the actions on that key count a use. */
    void keyPressed(int keycode) {
        HudData d = f.data;
        if (d == null) return;
        String name = Input.Keys.toString(keycode);
        if (name == null) return;
        for (int i = 0; i < d.keyHints.hints.size; i++) {
            KeyHint h = d.keyHints.hints.get(i);
            if (h.action.isEmpty()) continue;
            String k = d.key(h.action);
            if (name.equalsIgnoreCase(k)) uses.getAndIncrement(h.action, 0, 1);
        }
    }

    int uses(String action) {
        return uses.get(action, 0);
    }

    /** Decides what the plate says this frame; false with nothing to say. */
    boolean choose() {
        HudData d = f.data;
        hints.clear();
        key = "";
        text = "";
        if (d.keyHints.placing && !d.keyHints.placingText.isEmpty()) {
            text = d.keyHints.placingText;
            return true;
        }
        if (f.deck() && !d.promptAction.isEmpty()) {
            key = d.promptKey;
            text = d.promptAction;
            return true;
        }
        for (int i = 0; i < d.keyHints.hints.size && hints.size < HINTS; i++) {
            KeyHint h = d.keyHints.hints.get(i);
            if (!h.action.isEmpty() && uses.get(h.action, 0) >= RETIRE) continue;
            if (f.disabled && (h.action.equals("ABANDON") || h.action.equals("CUT"))) continue;
            hints.add(new String[]{h.key, h.label});
        }
        return hints.size > 0;
    }

    @Override
    protected void draw(YardPaint p) {
        Kit.plate(p, 0f, 0f, w, h, false);
        float tx = 3f, ty = 3f, tw = w - 6f, th = h - 6f;
        p.tube(tx, ty, tw, th, 2f, 1.5f, false);
        float base = ty + 13.5f, hx = tx + 8f;
        if (hints.size > 0) {
            for (int i = 0; i < hints.size; i++) {
                String[] hint = hints.get(i);
                hx += Kit.rev(p, hint[0], hx, base, 12.5f, 0f, Yard.P_DIM) + 4f;
                hx += p.tube(hint[1], hx, base, 13f, Yard.P_DIM, false) + 18f;
                if (hx > tx + tw - 60f) break;
            }
        } else if (!key.isEmpty()) {
            hx += Kit.rev(p, key, hx, base, 12.5f, 0f, Yard.P_AMBER) + 4f;
            keyed(p, text, hx, base, tx + tw - 8f);
        } else {
            keyed(p, text, hx, base, tx + tw - 8f);
        }
        p.tubeOver(tx, ty, tw, th);
    }

    /**
     * The game's words with each key it names in brackets ("[V] take the gun") as a reversed cell, like the
     * plate's own keys, up to {@code end}.
     */
    private static void keyed(YardPaint p, String text, float x, float base, float end) {
        java.util.List<String[]> parts = split(text);
        for (int i = 0; i < parts.size() && x < end - 12f; i++) {
            String[] part = parts.get(i);
            if (part[0] != null) x += Kit.rev(p, part[0], x, base, 12.5f, 0f, Yard.P_AMBER) + 4f;
            else x += Kit.tubeFit(p, part[1], x, base, 13f, Yard.P_AMBER, end - x) + 10f;
        }
    }

    /** {@code text} cut into its words and its bracketed keys: {key, null} or {null, words}, in order. */
    static java.util.List<String[]> split(String text) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        String rest = text == null ? "" : text;
        while (!rest.isEmpty()) {
            int open = rest.indexOf('['), close = open < 0 ? -1 : rest.indexOf(']', open);
            if (open < 0 || close < 0 || close - open > 12) {
                if (!rest.isBlank()) out.add(new String[]{null, rest.trim()});
                break;
            }
            String before = rest.substring(0, open).trim();
            if (!before.isEmpty()) out.add(new String[]{null, before});
            String k = rest.substring(open + 1, close).trim();
            if (!k.isEmpty()) out.add(new String[]{k, null});
            rest = rest.substring(close + 1);
        }
        return out;
    }
}
