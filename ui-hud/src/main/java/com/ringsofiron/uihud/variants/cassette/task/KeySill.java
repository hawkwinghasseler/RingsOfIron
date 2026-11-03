package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.providers.KeyHintProvider;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.Locale;

/**
 * Z9, the key sill (HNT): the console's front edge runs the full width under every unit, and the hint
 * line is a long low amber tube set into it, since its words change with the post. At most four keys
 * for what can be done here and now, then F1. The beam dims after 10 s without a change of context.
 */
final class KeySill extends YardPart {
    static final float H = 38f;
    /** Seconds without a change of context before the keys dim. */
    static final float FADE_AFTER = 10f;

    private final TaskFrame f;
    private final Array<String[]> items = new Array<>();
    private String context = "";
    private float still;
    private final Color dim = new Color(Yard.P_DIM);

    KeySill(TaskFrame f) {
        super(0f, 762f);
        this.f = f;
    }

    /** Actions whose keys another unit shows now (FLT's mode keys, the crisis placard's), never repeated here. */
    final ObjectSet<String> elsewhere = new ObjectSet<>();
    /** The keys the interaction prompt shows while it is up (its own, and any second key in its words). */
    final ObjectSet<String> promptKeys = new ObjectSet<>();
    private final StringBuilder sig = new StringBuilder();

    /**
     * Picks this frame's keys: the game's own hints for where the player is (the hint registry, most
     * useful first), less any key already on screen, at most four, then help. In a crisis only help, so
     * nothing competes with the placard. Without the game's hints, a short list for the post.
     */
    void choose(float delta) {
        HudData d = f.data;
        items.clear();
        sig.setLength(0);
        String help = d.key("HELP");
        if (f.crisis) {
            sig.append("crisis");
        } else if (d.keyHints.placing) {
            sig.append("placing");
            if (!d.keyHints.placingText.isEmpty()) items.add(new String[]{d.keyHints.placingText.toUpperCase(Locale.ROOT)});
        } else if (d.keyHints.hints.size > 0) {
            for (KeyHintProvider.KeyHint h : d.keyHints.hints) {
                if ("HELP".equals(h.action)) {
                    if (!h.key.isEmpty()) help = h.key;
                    continue;
                }
                if (items.size >= 4 || h.key.isEmpty() || elsewhere.contains(h.action)) continue;
                if (promptKeys.contains(h.key.toUpperCase(Locale.ROOT))) continue;
                items.add(new String[]{h.key.toUpperCase(Locale.ROOT), h.label.toUpperCase(Locale.ROOT)});
                sig.append(h.action).append(',');
            }
        } else {
            fallback(d);
        }
        if (!help.isEmpty()) items.add(new String[]{help.toUpperCase(Locale.ROOT), "CONTROLS"});
        String ctx = sig.toString();
        if (!ctx.equals(context)) {
            context = ctx;
            still = 0f;
        } else {
            still += delta;
        }
    }

    /** The post's keys when the game gives no hints. */
    private void fallback(HudData d) {
        if (f.deck()) {
            sig.append("deck");
            add(d, "WALK", "THRUST", "TURN_LEFT", "REVERSE", "TURN_RIGHT");
            if (d.ctx.latched) add(d, "BOARD", "BOARD");
            add(d, "LIGHTS", "LIGHTS");
            add(d, "CREW", "CREW");
        } else if (f.gunnery()) {
            sig.append("gunnery");
            add(d, "FIRE", "FIRE");
            add(d, "MISSILE", "MISSILE");
            add(d, "NEXT TARGET", "NEXT_TARGET");
            add(d, "STAND UP", "VIEW");
        } else if (f.docked) {
            sig.append("docked");
            add(d, "MAP", "MAP");
            add(d, "CONTRACTS", "MISSIONS");
            add(d, "CREW", "CREW");
        } else if (f.combat) {
            sig.append("helm-combat");
            add(d, "FIRE", "FIRE");
            add(d, "MISSILE", "MISSILE");
            add(d, "NEXT TARGET", "NEXT_TARGET");
            add(d, "DECOY", "DECOY");
        } else {
            sig.append("helm");
            add(d, "THRUST", "THRUST", "REVERSE");
            add(d, "TURN", "TURN_LEFT", "TURN_RIGHT");
            add(d, "MAP", "MAP");
            add(d, "DESTINATION", "NAV_TARGET");
        }
        while (items.size > 4) items.pop();
    }

    /** Adds {@code verb} with the keys of {@code actions}, skipping actions with no key. */
    private void add(HudData d, String verb, String... actions) {
        String[] row = new String[actions.length + 1];
        int n = 0;
        for (String a : actions) {
            String k = d.key(a);
            if (!k.isEmpty()) row[n++] = k.toUpperCase(Locale.ROOT);
        }
        if (n == 0) return;
        String[] out = new String[n + 1];
        System.arraycopy(row, 0, out, 0, n);
        out[n] = verb;
        items.add(out);
    }

    @Override
    protected void draw(YardPaint p) {
        float y = 762f, right = mx + w;
        p.rrect(-12f, y + 3f, w + 26f, 52f, 6f, Yard.SHADOW, 0.6f);
        p.shade(-14f, y, w + 28f, 52f, 6f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.vgrad(0f, y, w, 4f, Yard.hex("6A5D50"), Yard.CHASSIS_TOP);
        p.screw(8f, y + 22f);
        p.screw(right - 8f, y + 22f);
        float wx = 20f, wy = y + 11f, ww = w - 40f, wh = 24f;
        p.tube(wx, wy, ww, wh, 3f, 3f, false);
        Color c = still >= FADE_AFTER ? dim : Yard.P_AMBER;
        float xx = wx + 12f, base = wy + 16.5f;
        for (int n = 0; n < items.size; n++) {
            String[] it = items.get(n);
            // Help is last and always shown: an item that would run it off the tube is dropped
            float need = itemWidth(p, it) + (n < items.size - 1 ? itemWidth(p, items.peek()) : 0f);
            if (xx + need > wx + ww - 8f && n < items.size - 1) continue;
            for (int i = 0; i < it.length - 1; i++) xx += p.tubeKey(it[i], xx, base, 13f, c) + 4f;
            xx += 4f;
            xx += p.tube(it[it.length - 1], xx, base, 13f, c, false) + 26f;
        }
        p.tubeOver(wx, wy, ww, wh);
    }

    /** The widest the line can be, for the layout test: every key and verb at their sizes. */
    float lineWidth(YardPaint p) {
        float w = 0f;
        for (String[] it : items) w += itemWidth(p, it);
        return w;
    }

    private static float itemWidth(YardPaint p, String[] it) {
        float w = 0f;
        for (int i = 0; i < it.length - 1; i++) w += p.width(YardFonts.Face.TUBE_BOLD, 13f, it[i]) + 8f + 4f;
        return w + 4f + p.width(YardFonts.Face.TUBE, 13f, it[it.length - 1]) + 26f;
    }
}
