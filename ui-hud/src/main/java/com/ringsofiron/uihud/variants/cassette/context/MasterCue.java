package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The master cue on its own: standing alone at the bottom centre on deck, docked or latched, where no
 * seat's instrument carries it, or on a glareshield bracket over the weapons rack's right end, in the
 * gunner's eyeline, since the rack's rows fill it. A press, or the acknowledge key, puts it out.
 */
final class MasterCue extends YardPart {
    static final float W = 200f, H = 34f;

    private final ContextFrame f;
    private final Conditions c;
    private final Runnable acknowledge;
    /** On the bracket over the rack, rather than standing alone. */
    boolean bracket;

    MasterCue(ContextFrame f, Conditions c, Runnable acknowledge) {
        super(0f, 0f);
        this.f = f;
        this.c = c;
        this.acknowledge = acknowledge;
    }

    @Override
    protected void draw(YardPaint p) {
        if (bracket) {
            // The bracket's two struts down to the rack's top
            for (float sx : new float[]{24f, w - 24f}) {
                p.rect(sx - 2f, h - 2f, 4f, ContextLayout.GAP + 4f, Kit.ROD);
                p.rect(sx - 2f, h - 2f, 1.2f, ContextLayout.GAP + 4f, Kit.ROD_HI, 0.6f);
            }
        }
        Kit.pod(p, 0f, 0f, w, h);
        Kit.cue(p, 6f, 8f, w - 12f, c.cue, "\\", f.time);
    }

    @Override
    public boolean pressable(float px, float py) {
        return c.cue.lit;
    }

    @Override
    public boolean press(float px, float py) {
        if (!c.cue.lit) return false;
        acknowledge.run();
        return true;
    }

    @Override
    public String hover(float px, float py) {
        if (!c.cue.lit) return null;
        return "Master " + (c.cue.warning ? "warning: " + Conditions.longName(c.cue.code) : "caution") + " · click or \\ to acknowledge";
    }
}
