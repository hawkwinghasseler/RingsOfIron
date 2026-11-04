package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * ANN, the caution-and-warning panel (task spec section 6): ten lamps in two rows of five, dark when
 * well. A lamp is dark, advisory (lit softly), caution (amber) or warning (red, with a ring, so the
 * level reads without colour). A ship with no shield emitter has a blanking plate where SHIELD
 * would be, as a panel built for a system this ship doesn't carry.
 */
final class Annunciator extends NpInstrument {
    enum Lamp { HULL, FIRE, AIR, POWER, PROP, DRIVE, ARMS, CREW, EVA, SHIELD }

    enum State { DARK, ADVISORY, CAUTION, WARNING }

    private static final Color PLATE = NpColors.rgb(0x26292D);
    private static final Color SCREW = NpColors.rgb(0x1A1C1F);
    final State[] states = new State[Lamp.values().length];
    boolean shields;

    /** Its size, units. */
    static final float W = 272f, H = 64f;

    Annunciator(NpKit kit) {
        super(kit);
        setSize(W, H);
        darkAll();
    }

    void darkAll() {
        java.util.Arrays.fill(states, State.DARK);
    }

    void set(Lamp lamp, State s) {
        if (s.ordinal() > states[lamp.ordinal()].ordinal()) states[lamp.ordinal()] = s;
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        NpHardware.bezel(cv, 0f, 0f, w, h, 3f);
        NpHardware.fastener(cv, 6f, 6f);
        NpHardware.fastener(cv, w - 6f, 6f);
        NpHardware.fastener(cv, 6f, h - 6f);
        NpHardware.fastener(cv, w - 6f, h - 6f);
        Lamp[] lamps = Lamp.values();
        for (int i = 0; i < lamps.length; i++) {
            float lx = 11f + (i % 5) * 51f, ly = 8f + (i / 5) * 26f;
            if (lamps[i] == Lamp.SHIELD && !shields) {
                cv.fillRound(lx - 1f, ly - 1f, 51f, 24f, 2f, NpColors.BEZEL_SHADOW, 1f);
                cv.fillRound(lx, ly, 49f, 22f, 1.5f, PLATE, 1f);
                for (float fx : new float[] {lx + 7f, lx + 42f}) {
                    cv.disc(fx, ly + 11f, 4f, SCREW);
                    cv.ring(fx, ly + 11f, 2f, 0.5f, NpColors.EDGE_HI);
                }
                continue;
            }
            State s = states[i];
            Color lit = s == State.WARNING ? NpColors.RED : s == State.CAUTION ? NpColors.AMBER : null;
            NpHardware.lens(cv, lx, ly, 49f, 22f, lamps[i].name(), lit, s == State.ADVISORY, 12f);
            if (s == State.WARNING) cv.stroke(lx - 2f, ly - 2f, 53f, 26f, 1.2f, NpColors.RED);
        }
    }
}
