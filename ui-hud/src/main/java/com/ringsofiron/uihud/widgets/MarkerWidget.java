package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * One entity's HUD marker. The group's position is the entity's projected screen point.
 * On screen it draws corner brackets around the entity with its name and range on a line beneath;
 * off screen it draws an arrow at the screen edge pointing toward it.
 *
 * <p>A marker whose name would lie over its neighbours' carries theirs instead: "+3 others" under
 * its own, which a click opens into the list of them (and "-3 others" closes again); a click on a
 * name in the list picks that one ({@link Listener}).
 */
public class MarkerWidget extends Group {
    /** What a click on the list under a marker does. */
    public interface Listener {
        /** "+3 others" (or "-3 others") under {@code marker} was clicked. */
        void toggle(MarkerWidget marker);

        /** The {@code index}th name listed under {@code marker} was clicked. */
        void pick(MarkerWidget marker, int index);
    }

    private final Skin skin;
    private final TextureRegion white;
    private final Label name;
    private final Label others;
    private final Array<Label> rows = new Array<>();
    private int rowCount;
    private float bracketHalf = 12f;
    private boolean offscreen;
    private float arrowAngle;
    private boolean locked;
    private Listener listener;

    public MarkerWidget(Skin skin) {
        this.skin = skin;
        white = skin.getRegion("white");
        // Only the "+3 others" line and the names under it take clicks
        setTouchable(Touchable.childrenOnly);
        name = new Label("", skin);
        name.setAlignment(Align.center);
        name.setTouchable(Touchable.disabled);
        addActor(name);
        others = new Label("", skin);
        others.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) listener.toggle(MarkerWidget.this);
            }
        });
        others.setVisible(false);
        addActor(others);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Updates text, color and layout. {@code bracketHalf} is half the bracket box size in pixels.
     * The name and range go on one line, "Freighter  3.0 km".
     */
    public void set(String label, String rangeText, Color color, float bracketHalf, boolean offscreen, float arrowAngle, boolean locked) {
        this.bracketHalf = bracketHalf;
        this.offscreen = offscreen;
        this.arrowAngle = arrowAngle;
        this.locked = locked;
        setColor(color);
        String text = line(label, rangeText);
        if (!name.textEquals(text)) name.setText(text);
        name.setColor(color);
        name.pack();
        name.setVisible(true);
        if (offscreen) {
            // Text sits on the inward side of the arrow.
            float ix = -(float) Math.cos(Math.toRadians(arrowAngle)) * 28f;
            float iy = -(float) Math.sin(Math.toRadians(arrowAngle)) * 28f;
            name.setPosition(ix - name.getWidth() / 2f, iy - name.getHeight() / 2f);
        } else {
            name.setPosition(-name.getWidth() / 2f, -bracketHalf - name.getHeight() - 2f);
        }
        setOthers(0, false);
    }

    /** "Freighter  3.0 km", or the name alone without a range. */
    public static String line(String label, String rangeText) {
        return rangeText == null || rangeText.isEmpty() ? label : label + "  " + rangeText;
    }

    /**
     * Under the name, "+{@code count} others", or with the list {@code open} "-{@code count} others"
     * and room for that many names ({@link #setRow}); none for 0.
     */
    public void setOthers(int count, boolean open) {
        rowCount = open ? count : 0;
        for (int i = 0; i < rows.size; i++) rows.get(i).setVisible(i < rowCount);
        if (count == 0) {
            others.setVisible(false);
            return;
        }
        String text = (open ? "-" : "+") + count + (count == 1 ? " other" : " others");
        if (!others.textEquals(text)) others.setText(text);
        others.setColor(getColor());
        others.pack();
        others.setPosition(-others.getWidth() / 2f, name.getY() - others.getHeight());
        others.setVisible(true);
        while (rows.size < rowCount) {
            int index = rows.size;
            Label row = new Label("", skin);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (listener != null) listener.pick(MarkerWidget.this, index);
                }
            });
            addActor(row);
            rows.add(row);
        }
    }

    /** The {@code i}th name in the open list, and its color. */
    public void setRow(int i, String text, Color color) {
        Label row = rows.get(i);
        if (!row.textEquals(text)) row.setText(text);
        row.setColor(color);
        row.pack();
        row.setPosition(-row.getWidth() / 2f, others.getY() - (i + 1) * row.getHeight());
        row.setVisible(true);
    }

    /** The height of a line of the marker's text, pixels. */
    public float lineHeight() {
        return name.getPrefHeight();
    }

    /** The name line's box, in the parent's coordinates, into {@code out}. */
    public Rectangle nameBounds(Rectangle out) {
        return out.set(getX() + name.getX(), getY() + name.getY(), name.getWidth(), name.getHeight());
    }

    /** All the text's box (the name, "+3 others" and the open list), in the parent's coordinates, into {@code out}. */
    public Rectangle textBounds(Rectangle out) {
        nameBounds(out);
        if (others.isVisible()) out.merge(boxOf(others));
        for (int i = 0; i < rowCount; i++) out.merge(boxOf(rows.get(i)));
        return out;
    }

    private final Rectangle part = new Rectangle();

    private Rectangle boxOf(Label label) {
        return part.set(getX() + label.getX(), getY() + label.getY(), label.getWidth(), label.getHeight());
    }

    /** Hides the name (the bracket or arrow still shows), or shows it again. */
    public void setTextHidden(boolean hidden) {
        name.setVisible(!hidden);
        if (hidden) setOthers(0, false);
    }

    /** Moves the text by (dx, dy) from where {@link #set} put it, clear of another marker's. */
    public void nudgeText(float dx, float dy) {
        name.moveBy(dx, dy);
        others.moveBy(dx, dy);
        for (Label row : rows) row.moveBy(dx, dy);
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
        if (rowCount > 0) {
            // A dark backing for the open list, which may lie over anything
            Rectangle box = textBounds(new Rectangle());
            Rectangle top = nameBounds(new Rectangle());
            batch.setColor(0.02f, 0.02f, 0.05f, 0.75f * parentAlpha);
            float below = top.y;
            batch.draw(white, box.x - 4f, box.y - 2f, box.width + 8f, below - box.y + 2f);
        }
        batch.setColor(old);
        super.draw(batch, parentAlpha);
    }

    private void corner(Batch batch, float cx, float cy, float arm, float t, int sx, int sy) {
        batch.draw(white, sx > 0 ? cx : cx - arm, sy > 0 ? cy : cy - t, arm, t);
        batch.draw(white, sx > 0 ? cx : cx - t, sy > 0 ? cy : cy - arm, t, arm);
    }
}
