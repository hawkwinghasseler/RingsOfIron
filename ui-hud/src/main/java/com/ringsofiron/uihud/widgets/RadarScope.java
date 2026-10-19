package com.ringsofiron.uihud.widgets;

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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.systems.HudRenderSystem;

/**
 * The proximity scanner (GDD section 2): contacts around the player's ship, north up like the
 * exterior view, with the ship's heading as a line from the middle. Contacts beyond the range sit
 * on the rim as hollow marks. A click cycles the range through {@link #RANGES}.
 *
 * <p>{@link com.ringsofiron.uihud.systems.RadarSystem} fills it each frame.
 */
public class RadarScope extends Widget {
    /** Ranges a click cycles through, meters from the ship to the rim. */
    public static final float[] RANGES = {2_000f, 10_000f, 50_000f, 250_000f};
    public static final int DEFAULT_RANGE = 1;
    private static final float SIZE = 150f;

    private final TextureRegion white;
    private final BitmapFont font;
    /** This frame's contacts: dx, dy in meters from the ship. */
    private final FloatArray offsets = new FloatArray();
    private final Array<Color> colors = new Array<>();
    private final FloatArray locked = new FloatArray();
    private final Vector2 p = new Vector2();
    private float headingDeg = 90f;
    private boolean hasShip;
    private int range = DEFAULT_RANGE;

    public RadarScope(Skin skin) {
        white = skin.getRegion("white");
        font = skin.getFont("default-font");
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cycleRange();
            }
        });
    }

    public void cycleRange() {
        range = (range + 1) % RANGES.length;
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
    }

    public void addContact(float dx, float dy, Color color, boolean isLocked) {
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
        float r = Math.min(getWidth(), getHeight()) / 2f - 2f;
        float cx = getX() + getWidth() / 2f, cy = getY() + getHeight() / 2f;
        HudDraw.color(batch, HudSkin.HUD_DIM, parentAlpha * 0.6f);
        HudDraw.ring(batch, white, cx, cy, r, 1.5f);
        HudDraw.color(batch, HudSkin.HUD_DIM, parentAlpha * 0.3f);
        HudDraw.ring(batch, white, cx, cy, r / 2f, 1f);
        HudDraw.line(batch, white, cx - r, cy, cx + r, cy, 1f);
        HudDraw.line(batch, white, cx, cy - r, cx, cy + r, 1f);
        if (hasShip) {
            HudDraw.color(batch, HudSkin.HUD_CYAN, parentAlpha);
            float a = headingDeg * MathUtils.degreesToRadians;
            HudDraw.line(batch, white, cx, cy, cx + MathUtils.cos(a) * 14f, cy + MathUtils.sin(a) * 14f, 2f);
            HudDraw.dot(batch, white, cx, cy, 4f);
        }
        for (int i = 0; i < colors.size; i++) {
            boolean inside = plot(offsets.get(2 * i), offsets.get(2 * i + 1), getRange(), r, p);
            HudDraw.color(batch, colors.get(i), parentAlpha * (inside ? 1f : 0.6f));
            if (inside) HudDraw.dot(batch, white, cx + p.x, cy + p.y, 5f);
            else HudDraw.ring(batch, white, cx + p.x, cy + p.y, 3f, 1f);
            if (locked.get(i) > 0f) HudDraw.ring(batch, white, cx + p.x, cy + p.y, 7f, 1.5f);
        }
        font.setColor(HudSkin.HUD_DIM.r, HudSkin.HUD_DIM.g, HudSkin.HUD_DIM.b, parentAlpha);
        font.draw(batch, HudRenderSystem.formatRange(getRange()), getX() + 2f, getY() + 14f);
        batch.setColor(old);
    }

    @Override
    public float getPrefWidth() {
        return SIZE;
    }

    @Override
    public float getPrefHeight() {
        return SIZE;
    }
}
