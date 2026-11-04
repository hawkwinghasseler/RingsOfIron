package com.ringsofiron.uihud.variants.nasapunk.task;

/**
 * The Task HUD's zones (task spec section 5, as the variation draws them), in UI units from the
 * window's top left: each widget anchored to its edge or corner, every zone ending above the hint
 * strip at the bottom edge, and the centre ellipse left clear. A pure function of the window and
 * the few sizes that vary, so a test can hold it to the zone rules.
 */
final class TaskLayout {
    enum Part { CLOCK, LAMPS, SUIT, BAND, OBJECTIVES, TARGET, SCOPE, WEAPONS, FLIGHT, HELM, HINTS }

    /** The clear zone: an ellipse this wide and tall on the window's centre (section 5). */
    static final float CLEAR_W = 600f, CLEAR_H = 400f;
    /** The gap to the window's edges. */
    static final float MARGIN = 16f;
    /** Where the bottom zones end: the hint strip's top less the margin, from the bottom edge. */
    static final float BOTTOM = HintStrip.H + MARGIN;
    /** The target card never rises above this, so it keeps its place under one or two objectives. */
    static final float TARGET_TOP = 214f;
    /** The weapons display's right edge sits this far left of the window's, beside the scope. */
    static final float WEAPONS_RIGHT = MARGIN + ScopeDisplay.S + 8f;

    /** The sizes that change with what is shown. */
    static final class Sizes {
        /** The objectives and news, as tall as their rows. */
        float objectives;
        float suit = SuitDisplay.H;
        /** The flight display is up, so the helm strip sits on it at its width. */
        boolean flight;
        boolean compactWeapons;
    }

    private TaskLayout() {}

    /** Where {@code p} sits in a window {@code w} by {@code h} units: {x, top, width, height}. */
    static float[] box(Part p, float w, float h, Sizes s) {
        switch (p) {
            case CLOCK: return b(MARGIN, MARGIN, Chronometer.W, Chronometer.H);
            case LAMPS: return b(MARGIN, MARGIN + Chronometer.H + 8f, Annunciator.W, Annunciator.H);
            case SUIT: return b(MARGIN, MARGIN + Chronometer.H + 8f + Annunciator.H + 8f, SuitDisplay.W, s.suit);
            case BAND: return b(w / 2f - AlertBand.W / 2f, MARGIN, AlertBand.W, AlertBand.H);
            case OBJECTIVES: return b(w - MARGIN - Objectives.W, MARGIN, Objectives.W, s.objectives);
            case TARGET: return b(w - MARGIN - TargetCard.W, Math.max(TARGET_TOP, MARGIN + s.objectives + 12f), TargetCard.W, TargetCard.H);
            case SCOPE: return b(w - MARGIN - ScopeDisplay.S, h - BOTTOM - ScopeDisplay.S, ScopeDisplay.S, ScopeDisplay.S);
            case WEAPONS: {
                // compact at the helm, it shrinks to its zone's bottom right corner, clear of the flight display
                float ww = s.compactWeapons ? WeaponsDisplay.COMPACT_W : WeaponsDisplay.W;
                float wh = s.compactWeapons ? WeaponsDisplay.COMPACT_H : WeaponsDisplay.H;
                return b(w - WEAPONS_RIGHT - ww, h - BOTTOM - wh, ww, wh);
            }
            case FLIGHT: return b(w / 2f - FlightDisplay.W / 2f, h - BOTTOM - FlightDisplay.H, FlightDisplay.W, FlightDisplay.H);
            case HELM:
                if (s.flight) {
                    return b(w / 2f - HelmStrip.W_OVER_FLIGHT / 2f, h - BOTTOM - FlightDisplay.H - 8f - HelmStrip.H, HelmStrip.W_OVER_FLIGHT, HelmStrip.H);
                }
                return b(w / 2f - 312f, h - BOTTOM - HelmStrip.H, HelmStrip.W, HelmStrip.H);
            case HINTS: return b(0f, h - HintStrip.H, w, HintStrip.H);
            default: throw new IllegalArgumentException(p.name());
        }
    }

    private static float[] b(float x, float top, float w, float h) {
        return new float[] {x, top, w, h};
    }
}
