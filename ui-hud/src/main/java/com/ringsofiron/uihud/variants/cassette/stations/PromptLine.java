package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The prompt line (spec 7.3), Z7: one strip tube. For 4 s after the player's own input, the game's answer
 * to it ("Navigation target: Highport"); else, on deck, what the interact key does here; else at most three
 * learning hints, dim, each retired once the player has used its action three times. While the ship is
 * disabled the status strip carries the abandon and tow keys, so the hints leave them out; a ship that
 * can't get home on what it has keeps the tow's hint.
 */
final class PromptLine extends YardPart {
    static final float X = 280f, Y = 582f, W = StationsLayout.PROMPT_W, H = StationsLayout.PROMPT_H;
    /** Uses of an action after which its hint retires. */
    static final int RETIRE = 3;
    private static final int HINTS = 3;

    private final StationsFrame f;
    private final ObjectIntMap<String> uses = new ObjectIntMap<>();
    private final Array<String[]> hints = new Array<>();
    private String text = "";
    private boolean hint;

    PromptLine(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** The player pressed {@code keycode}: the hints for the actions on that key count a use. */
    void keyPressed(int keycode) {
        HudData d = f.data;
        if (d == null) return;
        String name = Input.Keys.toString(keycode);
        if (name == null) return;
        for (int hi = 0; hi < d.keyHints.hints.size; hi++) {
            KeyHint h = d.keyHints.hints.get(hi);
            if (h.action.isEmpty()) continue;
            String k = d.key(h.action);
            if (name.equalsIgnoreCase(k)) uses.getAndIncrement(h.action, 0, 1);
        }
    }

    int uses(String action) {
        return uses.get(action, 0);
    }

    /** Decides what the line says this frame; false with nothing to say. */
    boolean choose() {
        HudData d = f.data;
        hints.clear();
        text = "";
        hint = false;
        StationAlerts a = f.alerts;
        if (!a.response.isEmpty()) {
            text = a.response;
            return true;
        }
        if (d.keyHints.placing && !d.keyHints.placingText.isEmpty()) {
            text = d.keyHints.placingText;
            return true;
        }
        if (f.deck() && !d.promptAction.isEmpty()) {
            text = YardPaint.KEY + d.promptKey + "\u0000" + d.promptAction;
            return true;
        }
        for (int hi = 0; hi < d.keyHints.hints.size; hi++) {
            KeyHint h = d.keyHints.hints.get(hi);
            if (hints.size >= HINTS) break;
            if (!h.action.isEmpty() && uses.get(h.action, 0) >= RETIRE) continue;
            // Disabled: the status strip has abandon ship and the tow, keys first
            if (f.disabled && (h.action.equals("ABANDON") || h.action.equals("CUT"))) continue;
            hints.add(new String[]{h.key, h.label});
        }
        if (!f.disabled && stranded(d) && hints.size < HINTS) {
            String b = Hardware.key(d, "CUT", "B");
            hints.add(new String[]{b + " " + b, "call a tow"});
        }
        hint = hints.size > 0;
        return hint;
    }

    /** Short of propellant to get home, with a beacon that would answer: the tow's hint stays (13.4). */
    static boolean stranded(HudData d) {
        if (!d.orderState.towRefusal.isEmpty() || d.orders == null) return false;
        if (d.flightStatus.outOfPropellant) return true;
        double left = d.flightStatus.deltaVLeft, home = d.flightStatus.deltaVHome;
        return !Double.isNaN(left) && !Double.isInfinite(left) && !Double.isNaN(home) && left < home;
    }

    @Override
    protected void draw(YardPaint p) {
        // As wide as the layout leaves it: narrower beside the suit readout with a side panel up on deck
        float x = X, y = Y, pw = w > 0f ? Math.min(W, w) : W;
        Hardware.bar(p, x, y, pw, H);
        float tx = x + 4f, ty = y + 3f, tw = pw - 8f, th = 20f;
        p.tube(tx, ty, tw, th, 2f, 3f, false);
        float base = ty + 14.5f, hx = tx + 10f;
        if (hint) {
            for (String[] h : hints) {
                hx += Hardware.rev(p, h[0], hx, base, 12.5f, 0f, Yard.P_DIM) + 4f;
                hx += p.tube(h[1], hx, base, 13f, Yard.P_DIM, false) + 22f;
                if (hx > tx + tw - 40f) break;
            }
        } else if (text.charAt(0) == YardPaint.KEY) {
            int cut = text.indexOf('\u0000');
            hx += Hardware.rev(p, text.substring(1, cut), hx, base, 12.5f, 0f, Yard.P_AMBER) + 4f;
            Hardware.tubeFit(p, text.substring(cut + 1), hx, base, 13f, Yard.P_AMBER, tx + tw - 8f - hx);
        } else {
            Hardware.tubeFit(p, text, hx, base, 13f, Yard.P_AMBER, tw - 20f);
        }
        p.tubeOver(tx, ty, tw, th);
    }
}
