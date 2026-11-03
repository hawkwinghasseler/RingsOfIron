package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * The suit readout (section 7.4), on deck only: the display on the captain's chest, a DCM, 200
 * across in the bottom left corner, standing on the zone's foot. Its rows come and go with what
 * matters: the suit's air in bad air, NO SUIT, the room's pressure and oxygen, FIRE, the wound.
 */
final class SuitReadout extends NpInstrument {
    static final float W = 200f;

    /** Label, value pairs. */
    final List<String> rows = new ArrayList<>();
    final List<Color> colors = new ArrayList<>();

    SuitReadout(NpKit kit) {
        super(kit);
        setSize(W, 0f);
    }

    void row(String label, String value, Color c) {
        rows.add(label);
        rows.add(value);
        colors.add(c);
    }

    void reset() {
        rows.clear();
        colors.clear();
    }

    /** The DCM's height for its rows, as NpHardware.dcm draws it at the text floor. */
    float heightFor() {
        return 34f + colors.size() * 19f;
    }

    @Override
    protected void paint(NpCanvas cv) {
        NpHardware.dcm(cv, 0f, 0f, W, rows.toArray(new String[0]), colors.toArray(new Color[0]), 12f);
    }
}
