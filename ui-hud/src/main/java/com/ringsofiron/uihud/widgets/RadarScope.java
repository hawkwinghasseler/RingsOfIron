package com.ringsofiron.uihud.widgets;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.systems.HudRenderSystem;

import java.util.function.Consumer;

/**
 * The proximity scanner (GDD section 2): contacts around the player's ship, north up like the
 * exterior view, with the ship's heading as a line from the middle. Contacts beyond the range sit
 * on the rim as smaller, faded dots; only the locked target is ringed. The + and − buttons either
 * side of the range step it through {@link #RANGES}; a click anywhere else picks the contact under
 * the cursor, if any, for the {@link #setPickListener pick listener}.
 *
 * <p>{@link com.ringsofiron.uihud.systems.RadarSystem} fills it each frame.
 */
public class RadarScope extends Widget {
    /** Ranges the buttons step through, meters from the ship to the rim, closest first. */
    public static final float[] RANGES = {500f, 2_000f, 10_000f, 50_000f, 250_000f, 1_000_000f};
    public static final int DEFAULT_RANGE = 2;
    private static final float SIZE = 104f;
    /** The strip under the scope that holds the range and the zoom buttons, so they stay off the rim. */
    private static final float LABEL_HEIGHT = 16f;
    private static final float BUTTON = 14f;
    /** How near the cursor, in pixels, a click has to be to a contact's mark to pick it. */
    static final float PICK_RADIUS = 8f;

    private final TextureRegion white;
    private final BitmapFont font;
    /** This frame's contacts: dx, dy in meters from the ship. */
    private final FloatArray offsets = new FloatArray();
    private final Array<Color> colors = new Array<>();
    private final FloatArray locked = new FloatArray();
    private final Array<Entity> entities = new Array<>();
    private final Vector2 p = new Vector2();
    private float headingDeg = 90f;
    private boolean hasShip;
    private int range = DEFAULT_RANGE;
    private Consumer<Entity> pickListener;

    public RadarScope(Skin skin) {
        white = skin.getRegion("white");
        font = skin.getFont("default-font");
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                click(x, y);
            }
        });
    }

    /** Called with the contact a click on the scope lands on; clicks on empty scope call nothing. */
    public void setPickListener(Consumer<Entity> listener) {
        pickListener = listener;
    }

    /** Steps to the next closer range, showing more detail; false when already at the closest. */
    public boolean zoomIn() {
        if (range == 0) return false;
        range--;
        return true;
    }

    /** Steps to the next longer range; false when already at the longest. */
    public boolean zoomOut() {
        if (range == RANGES.length - 1) return false;
        range++;
        return true;
    }

    /** A click at {@code x, y} in the widget's own coordinates: a zoom button, else a pick. */
    void click(float x, float y) {
        if (y <= LABEL_HEIGHT) {
            if (x <= BUTTON + 2f) zoomOut();
            else if (x >= getWidth() - BUTTON - 2f) zoomIn();
            return;
        }
        if (pickListener == null || !hasShip) return;
        int i = pick(x - getWidth() / 2f, y - centerY(), offsets, getRange(), radius(), p);
        if (i >= 0) pickListener.accept(entities.get(i));
    }

    /**
     * Which contact a click {@code x, y} pixels from the scope's middle lands on: the index into
     * {@code offsets} (dx, dy pairs in meters) of the nearest mark within {@link #PICK_RADIUS}, or -1.
     */
    static int pick(float x, float y, FloatArray offsets, float range, float radius, Vector2 tmp) {
        int best = -1;
        float bestD2 = PICK_RADIUS * PICK_RADIUS;
        for (int i = 0; i < offsets.size / 2; i++) {
            plot(offsets.get(2 * i), offsets.get(2 * i + 1), range, radius, tmp);
            float d2 = tmp.dst2(x, y);
            if (d2 <= bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    private float radius() {
        return Math.min(getWidth(), getHeight() - LABEL_HEIGHT) / 2f - 2f;
    }

    /** The scope's middle, up from the widget's bottom. */
    private float centerY() {
        return LABEL_HEIGHT + (getHeight() - LABEL_HEIGHT) / 2f;
    }

    public float getRange() {
        return RANGES[range];
    }

    /** Starts a frame's contacts; with no ship the scope shows only its rings. */
    public void begin(boolean hasShip, float headingDeg) {
        this.hasShip = hasShip;
        this.headingDeg = headingDeg;
        offsets.clear();
        colors.clear();
        locked.clear();
        entities.clear();
    }

    public void addContact(Entity entity, float dx, float dy, Color color, boolean isLocked) {
        entities.add(entity);
        offsets.add(dx, dy);
        colors.add(color);
        locked.add(isLocked ? 1f : 0f);
    }

    public int getContactCount() {
        return colors.size;
    }

    /**
     * Where a contact {@code dx, dy} meters away plots on a scope of {@code radius} pixels showing
     * {@code range} meters, relative to the scope's middle. True when it is within range; beyond it,
     * the point is on the rim in its direction.
     */
    public static boolean plot(float dx, float dy, float range, float radius, Vector2 out) {
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d <= range) {
            out.set(dx, dy).scl(radius / range);
            return true;
        }
        out.set(dx, dy).scl(radius / d);
        return false;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor().cpy();
        float r = radius();
        float cx = getX() + getWidth() / 2f, cy = getY() + centerY();
        HudDraw.color(batch, HudSkin.HUD_DIM, parentAlpha * 0.6f);
        HudDraw.ring(batch, white, cx, cy, r, 1.5f);
        HudDraw.color(batch, HudSkin.HUD_DIM, parentAlpha * 0.3f);
        HudDraw.ring(batch, white, cx, cy, r / 2f, 1f);
        HudDraw.line(batch, white, cx - r, cy, cx + r, cy, 1f);
        HudDraw.line(batch, white, cx, cy - r, cx, cy + r, 1f);
        if (hasShip) {
            HudDraw.color(batch, HudSkin.HUD_CYAN, parentAlpha);
            float a = headingDeg * MathUtils.degreesToRadians;
            HudDraw.line(batch, white, cx, cy, cx + MathUtils.cos(a) * 10f, cy + MathUtils.sin(a) * 10f, 1.5f);
            HudDraw.disc(batch, white, cx, cy, 4f);
        }
        for (int i = 0; i < colors.size; i++) {
            boolean inside = plot(offsets.get(2 * i), offsets.get(2 * i + 1), getRange(), r, p);
            // Only the target is ringed; a contact beyond the range is a smaller, faded dot on the rim
            HudDraw.color(batch, colors.get(i), parentAlpha * (inside ? 1f : 0.5f));
            HudDraw.disc(batch, white, cx + p.x, cy + p.y, inside ? 5f : 3.5f);
            if (locked.get(i) > 0f) {
                HudDraw.color(batch, colors.get(i), parentAlpha);
                HudDraw.ring(batch, white, cx + p.x, cy + p.y, 6f, 1.5f);
            }
        }
        font.setColor(HudSkin.HUD_DIM.r, HudSkin.HUD_DIM.g, HudSkin.HUD_DIM.b, parentAlpha);
        font.draw(batch, HudRenderSystem.formatRange(getRange()), getX(), getY() + LABEL_HEIGHT - 2f, getWidth(),
                Align.center, false);
        float by = getY() + (LABEL_HEIGHT - BUTTON) / 2f;
        zoomButton(batch, getX(), by, false, range < RANGES.length - 1, parentAlpha);
        zoomButton(batch, getX() + getWidth() - BUTTON, by, true, range > 0, parentAlpha);
        batch.setColor(old);
    }

    /** A small boxed + or −, dimmed when it can step no further. */
    private void zoomButton(Batch batch, float x, float y, boolean plus, boolean enabled, float parentAlpha) {
        HudDraw.color(batch, HudSkin.HUD_DIM, parentAlpha * (enabled ? 0.8f : 0.3f));
        HudDraw.line(batch, white, x, y + 0.5f, x + BUTTON, y + 0.5f, 1f);
        HudDraw.line(batch, white, x, y + BUTTON - 0.5f, x + BUTTON, y + BUTTON - 0.5f, 1f);
        HudDraw.line(batch, white, x + 0.5f, y, x + 0.5f, y + BUTTON, 1f);
        HudDraw.line(batch, white, x + BUTTON - 0.5f, y, x + BUTTON - 0.5f, y + BUTTON, 1f);
        HudDraw.color(batch, enabled ? HudSkin.HUD_CYAN : HudSkin.HUD_DIM, parentAlpha * (enabled ? 1f : 0.4f));
        float mx = x + BUTTON / 2f, my = y + BUTTON / 2f;
        HudDraw.line(batch, white, mx - 4f, my, mx + 4f, my, 1.5f);
        if (plus) HudDraw.line(batch, white, mx, my - 4f, mx, my + 4f, 1.5f);
    }

    @Override
    public float getPrefWidth() {
        return SIZE;
    }

    @Override
    public float getPrefHeight() {
        return SIZE + LABEL_HEIGHT;
    }
}
