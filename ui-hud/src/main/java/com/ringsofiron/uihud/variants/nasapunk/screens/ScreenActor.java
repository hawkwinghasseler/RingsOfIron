package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * The actor a Nasa-punk screen draws in: it fills the stage in the variant's screen layer, paints on
 * the kit's canvas in UI units with y down from its top left, as the mockups are laid out, and sends
 * a click to whatever its painter laid a box down for there ({@link Hits}). Anywhere else the click
 * goes through, to the map or the world under it. The wheel goes to the painter while the pointer is
 * over the screen.
 */
public final class ScreenActor extends Actor {
    /** What draws a screen and answers its clicks. */
    public interface Painter {
        /** Draws the screen; {@code hits} is cleared before, for the boxes the clicks will go to. */
        void paint(NpCanvas cv, Camera cam, float w, float h, Hits hits);

        /** A click on a box laid down with {@code target} and {@code arg}. */
        void clicked(Object target, int arg);

        /** The wheel turned {@code amount} notches (down positive) over ({@code x}, {@code y}); true when used. */
        default boolean scrolled(float x, float y, int amount) {
            return false;
        }
    }

    private final NpKit kit;
    private final Painter painter;
    private final Hits hits = new Hits();

    public ScreenActor(NpKit kit, Painter painter) {
        this.kit = kit;
        this.painter = painter;
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                int i = hits.at(x, getHeight() - y);
                if (i < 0) return false;
                Object t = hits.target(i);
                if (t != null) painter.clicked(t, hits.arg(i));
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Stage s = getStage();
                if (s != null && pointer == -1) s.setScrollFocus(ScreenActor.this);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                Stage s = getStage();
                if (s != null && pointer == -1 && s.getScrollFocus() == ScreenActor.this) s.setScrollFocus(null);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (amountY == 0f) return false;
                return painter.scrolled(x, getHeight() - y, amountY > 0f ? 1 : -1);
            }
        });
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() != Touchable.enabled) return null;
        if (!isVisible()) return null;
        return hits.at(x, getHeight() - y) >= 0 ? this : null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        hits.clear();
        Stage s = getStage();
        Camera cam = s == null ? null : s.getCamera();
        NpCanvas cv = kit.canvas().begin(batch, getX(), getY() + getHeight(), parentAlpha * getColor().a);
        try {
            painter.paint(cv, cam, getWidth(), getHeight(), hits);
        } finally {
            cv.end();
        }
    }

    /** Lets go of the stage's scroll focus, as the screen closes. */
    public void release() {
        Stage s = getStage();
        if (s != null && s.getScrollFocus() == this) s.setScrollFocus(null);
    }

    public Hits hits() {
        return hits;
    }
}
