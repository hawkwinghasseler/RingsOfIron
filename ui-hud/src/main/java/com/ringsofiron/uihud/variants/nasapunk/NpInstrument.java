package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * An instrument: an actor drawn on the canvas in local UI units with y down from its top left
 * corner, so its layout reads as the mockups' do.
 */
public abstract class NpInstrument extends Actor {
    protected final NpKit kit;

    protected NpInstrument(NpKit kit) {
        this.kit = kit;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        NpCanvas cv = kit.canvas().begin(batch, getX(), getY() + getHeight(), parentAlpha * getColor().a);
        try {
            paint(cv);
        } finally {
            cv.end();
        }
    }

    /** Draws the instrument: (0, 0) is its top left corner, ({@code w}, {@code h}) its bottom right. */
    protected abstract void paint(NpCanvas cv);

    /** A point given in this actor's own coordinates (y up), as y down from its top. */
    protected float down(float y) {
        return getHeight() - y;
    }
}
