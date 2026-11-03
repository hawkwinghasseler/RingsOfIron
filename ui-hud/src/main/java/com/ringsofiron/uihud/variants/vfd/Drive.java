package com.ringsofiron.uihud.variants.vfd;

/**
 * Drive levels: how hard the grid drives a lit element, as a share of full (style-vfd.md, section 4).
 * The level carries meaning, never decoration: dim for labels and context, normal for values, full
 * for what needs the eye now. {@link #GHOST} is an unlit element seen through the glass.
 */
public final class Drive {
    /** An unlit element, seen through the smoked glass. */
    public static final float GHOST = 0.075f;
    /** L1: legends, units, scales and anything that only gives context. */
    public static final float DIM = 0.40f;
    /** L2: values and states. */
    public static final float NORM = 0.80f;
    /** L3: what needs the eye now: the master alarm, a warning, a selected item. */
    public static final float FULL = 1f;

    private Drive() {
    }
}
