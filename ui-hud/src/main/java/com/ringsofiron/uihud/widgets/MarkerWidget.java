package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;

/**
 * One entity's HUD marker. The group's position is the entity's projected screen point.
 * On screen it draws corner brackets around the entity with the name and range beneath;
 * off screen it draws an arrow at the screen edge pointing toward it.
 */
public class MarkerWidget extends Group {
    private final TextureRegion white;
    private final Label name;
    private final Label range;
    private float bracketHalf = 12f;
    private boolean offscreen;
    private float arrowAngle;
    private boolean locked;

    public MarkerWidget(Skin skin) {
        white = skin.getRegion("white");
        setTouchable(Touchable.disabled);
        name = new Label("", skin);
        range = new Label("", skin, "dim");
        name.setAlignment(Align.center);
        range.setAlignment(Align.center);
        addActor(name);
        addActor(range);
    }

    /** Updates text, color and layout. {@code bracketHalf} is half the bracket box size in pixels. */
    public void set(String label, String rangeText, Color color, float bracketHalf, boolean offscreen, float arrowAngle, boolean locked) {
        this.bracketHalf = bracketHalf;
        this.offscreen = offscreen;
        this.arrowAngle = arrowAngle;
        this.locked = locked;
        setColor(color);
        if (!name.textEquals(label)) name.setText(label);
        if (!range.textEquals(rangeText)) range.setText(rangeText);
        name.setColor(color);
        name.pack();
        range.pack();
        if (offscreen) {
            // Text sits on the inward side of the arrow.
            float ix = -(float) Math.cos(Math.toRadians(arrowAngle)) * 28f;
            float iy = -(float) Math.sin(Math.toRadians(arrowAngle)) * 28f;
            name.setPosition(ix - name.getWidth() / 2f, iy);
            range.setPosition(ix - range.getWidth() / 2f, iy - range.getHeight());
        } else {
            name.setPosition(-name.getWidth() / 2f, -bracketHalf - name.getHeight() - 2f);
            range.setPosition(-range.getWidth() / 2f, -bracketHalf - name.getHeight() - range.getHeight() - 2f);
        }
    }

    /** The name and range text's box, in the parent's coordinates, into {@code out}. */
    public com.badlogic.gdx.math.Rectangle textBounds(com.badlogic.gdx.math.Rectangle out) {
        float left = Math.min(name.getX(), range.getX());
        float width = Math.max(name.getX() + name.getWidth(), range.getX() + range.getWidth()) - left;
        return out.set(getX() + left, getY() + range.getY(), width, name.getY() + name.getHeight() - range.getY());
    }

    /** Hides the name and range (the bracket or arrow still shows), or shows them again. */
    public void setTextHidden(boolean hidden) {
        name.setVisible(!hidden);
        range.setVisible(!hidden);
    }

    /** Moves the text by (dx, dy) from where {@link #set} put it, clear of another marker's. */
    public void nudgeText(float dx, float dy) {
        name.moveBy(dx, dy);
        range.moveBy(dx, dy);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color c = getColor();
        Color old = batch.getColor().cpy();
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
        float x = getX();
        float y = getY();
        float t = locked ? 2f : 1.5f;
        if (offscreen) {
            // Chevron: two bars meeting at the point nearest the screen edge.
            float len = 12f;
            batch.draw(white, x - len, y - t / 2f, len, t / 2f, len, t, 1f, 1f, arrowAngle + 35f);
            batch.draw(white, x - len, y - t / 2f, len, t / 2f, len, t, 1f, 1f, arrowAngle - 35f);
        } else {
            float h = bracketHalf;
            float arm = Math.max(4f, h * 0.45f);
            // Four corner brackets.
            corner(batch, x - h, y - h, arm, t, 1, 1);
            corner(batch, x + h, y - h, arm, t, -1, 1);
            corner(batch, x - h, y + h, arm, t, 1, -1);
            corner(batch, x + h, y + h, arm, t, -1, -1);
            if (locked) {
                float d = 3f;
                batch.draw(white, x - d / 2f, y - d / 2f, d, d);
            }
        }
        batch.setColor(old);
        super.draw(batch, parentAlpha);
    }

    private void corner(Batch batch, float cx, float cy, float arm, float t, int sx, int sy) {
        batch.draw(white, sx > 0 ? cx : cx - arm, sy > 0 ? cy : cy - t, arm, t);
        batch.draw(white, sx > 0 ? cx : cx - t, sy > 0 ? cy : cy - arm, t, arm);
    }
}
