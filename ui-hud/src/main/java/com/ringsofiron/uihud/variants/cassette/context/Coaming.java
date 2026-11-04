package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The coaming: the top edge of the seat's console, along the foot of the screen in the margin under the
 * zones, that the bottom units stand on. Only at a seat: on deck there is no console, so no coaming.
 */
final class Coaming extends YardPart {
    static final float H = 12f;

    Coaming() {
        super(0f, 0f);
    }

    @Override
    protected void draw(YardPaint p) {
        p.vgrad(0f, 0f, w, h, Yard.CHASSIS_TOP, Yard.CHASSIS_DEEP);
        p.rect(0f, 0f, w, 1.5f, Kit.BAR_EDGE);
        p.rect(0f, 1.5f, w, 1f, Color.WHITE, 0.08f);
        // A seam every so often, as the console's panels meet
        for (float sx = 160f; sx < w; sx += 320f) p.rect(sx, 3f, 1f, h - 3f, Color.BLACK, 0.35f);
    }

    @Override
    public boolean contains(float px, float py) {
        // The coaming never takes the pointer: the world goes to its edge
        return false;
    }
}
