package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * PER, the captain's own state (task spec section 6), as the suit's chest display: condition, the
 * suit and its air, and the room's air.
 */
final class SuitDisplay extends NpInstrument {
    final String[] rows = new String[6];
    final Color[] colors = new Color[3];

    /** Its size, units. */
    static final float W = 272f, H = 88f;

    SuitDisplay(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        setHeight(NpHardware.dcm(cv, 0f, 0f, getWidth(), rows, colors, 12f));
    }
}
