package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

/**
 * A FILAMENT HUD on the stage: one actor over the whole stage that draws a {@link Filament.Painter}
 * each frame, and the boxes in it that take clicks (soft keys, tiles, lamps). A click anywhere else
 * falls through to the world and the classic HUD under it, so the glass never steals the mouse.
 *
 * <p>Boxes are in the painter's coordinates: UI units from the stage's top left, y down. The HUD adds
 * them again each frame as it lays out ({@link #clearBoxes}, {@link #box}).
 */
public final class FilamentActor extends Actor implements Disposable {

    /** Told which box was clicked, by the id it was added with. */
    public interface Clicks {
        void clicked(int id);
    }

    /** Made on the first draw, since it holds GL resources: the click boxes work without it. */
    private Filament filament;
    private final Filament.Painter painter;
    /** x, y, w, h of each box, y down. */
    private final FloatArray boxes = new FloatArray();
    private final IntArray ids = new IntArray();
    private float unitsPx = 1f;

    public FilamentActor(Filament.Painter painter, Clicks clicks) {
        this.painter = painter;
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                int id = idAt(x, getHeight() - y);
                if (id < 0) return false;
                clicks.clicked(id);
                return true;
            }
        });
    }

    /** The kit it draws with, made on first use (it needs a GL context). */
    public Filament filament() {
        if (filament == null) filament = new Filament();
        return filament;
    }

    /** Screen pixels per UI unit: the stage's UI scale. */
    public void setUnitsPx(float unitsPx) {
        this.unitsPx = Math.max(0.25f, unitsPx);
    }

    /** Forgets every box, for the frame's layout to add them again. */
    public void clearBoxes() {
        boxes.clear();
        ids.clear();
    }

    /** A box that takes clicks as {@code id}: UI units from the stage's top left, y down. */
    public void box(int id, float x, float y, float w, float h) {
        boxes.add(x);
        boxes.add(y);
        boxes.add(w);
        boxes.add(h);
        ids.add(id);
    }

    /** The id of the last box added that holds {@code x, y} (y down), or -1. */
    public int idAt(float x, float yDown) {
        for (int i = ids.size - 1; i >= 0; i--) {
            float bx = boxes.get(4 * i), by = boxes.get(4 * i + 1), bw = boxes.get(4 * i + 2), bh = boxes.get(4 * i + 3);
            if (x >= bx && x < bx + bw && yDown >= by && yDown < by + bh) return ids.get(i);
        }
        return -1;
    }

    /** How many boxes take clicks now (for tests). */
    public int boxCount() {
        return ids.size;
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() != Touchable.enabled) return null;
        if (!isVisible()) return null;
        return idAt(x, getHeight() - y) >= 0 ? this : null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        filament().render(batch, getWidth(), getHeight(), unitsPx, painter);
    }

    @Override
    public void dispose() {
        if (filament != null) filament.dispose();
        filament = null;
    }
}
