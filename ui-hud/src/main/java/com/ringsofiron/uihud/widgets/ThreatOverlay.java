package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.ScreenEdge;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame;

import java.util.Locale;

/**
 * Missile threats over the exterior view: each missile's predicted path as a dashed line (red when
 * it is coming for you, cyan for yours, amber for others), an aim cross where an incoming one will
 * meet you, edge arrows for incoming missiles off screen, a "MISSILE WARNING" banner with the count
 * and the soonest impact, and a ring where point defense hit a missile.
 */
public class ThreatOverlay extends Actor {
    public static final float FLASH_SECONDS = 1.2f;
    /** The banner blinks when an impact is closer than this, seconds. */
    public static final float URGENT_SECONDS = 5f;
    private static final float EDGE_MARGIN = 30f;
    private static final Color OWN = HudSkin.HUD_CYAN;
    private static final Color OTHER = new Color(1f, 0.75f, 0.2f, 1f);

    private final ViewportManager viewports;
    private final TextureRegion white;
    private final BitmapFont font;
    private final Label banner;
    private final ThreatFrame frame = new ThreatFrame();
    /** Point-defense hits: {x, y, age, destroyed ? 1 : 0}. */
    private final Array<float[]> flashes = new Array<>();
    private final Vector2 a = new Vector2(), b = new Vector2();
    private ThreatProvider provider;
    private float clock;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout fit = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    public ThreatOverlay(ViewportManager viewports, Skin skin, Label banner) {
        this.viewports = viewports;
        this.white = skin.getRegion("white");
        this.font = skin.getFont("default-font");
        this.banner = banner;
        setTouchable(Touchable.disabled);
        banner.setVisible(false);
    }

    public void setProvider(ThreatProvider provider) {
        this.provider = provider;
    }

    /** Marks a point-defense hit on a missile at a world point. */
    public void flashIntercept(float worldX, float worldY, boolean destroyed) {
        flashes.add(new float[]{worldX, worldY, 0f, destroyed ? 1f : 0f});
    }

    public ThreatFrame getFrame() {
        return frame;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        clock += delta;
        for (int i = flashes.size - 1; i >= 0; i--) {
            float[] f = flashes.get(i);
            f[2] += delta;
            if (f[2] > FLASH_SECONDS) flashes.removeIndex(i);
        }
        frame.clear();
        if (provider != null && isVisible()) provider.collect(frame);
        String text = bannerText(frame);
        // Too wide for the space it has: the impact time goes on a line of its own rather than
        // the wrap leaving its unit behind
        if (text != null && banner.getWidth() > 0f) {
            fit.setText(banner.getStyle().font, text);
            if (fit.width > banner.getWidth()) text = text.replace("   impact", "\nimpact");
        }
        banner.setVisible(text != null && isVisible() && (!urgent(frame) || clock % 0.5f < 0.3f));
        banner.setText(text == null ? "" : text);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible && banner != null) banner.setVisible(false);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor().cpy();
        float w = viewports.getUiWidth(), h = viewports.getUiHeight();
        for (MissileTrack m : frame.missiles) {
            Color c = m.side == Side.INCOMING ? HudSkin.HUD_ALERT : m.side == Side.OWN ? OWN : OTHER;
            float alpha = parentAlpha * (m.burning ? 1f : 0.7f);
            viewports.worldToUi(m.x, m.y, a);
            viewports.worldToUi(m.pathX, m.pathY, b);
            dashed(batch, a.x, a.y, b.x, b.y, c, alpha * 0.8f);
            if (m.side == Side.INCOMING && !Float.isNaN(m.timeToImpact)) {
                cross(batch, b.x, b.y, 6f, c, alpha);
            }
            if (ScreenEdge.isOnScreen(a.x, a.y, w, h)) {
                box(batch, a.x, a.y, 4f, c, alpha);
            } else if (m.side == Side.INCOMING) {
                float angle = ScreenEdge.clampToEdge(a, w, h, EDGE_MARGIN);
                arrow(batch, a.x, a.y, angle, c, parentAlpha);
                if (!Float.isNaN(m.timeToImpact)) {
                    text(batch, String.format(Locale.ROOT, "%.0f s", m.timeToImpact), a.x - 10f, a.y - 12f, c, parentAlpha);
                }
            }
        }
        for (float[] f : flashes) {
            viewports.worldToUi(f[0], f[1], a);
            float t = f[2] / FLASH_SECONDS;
            Color c = f[3] > 0 ? HudSkin.HUD_WARN : HudSkin.HUD_DIM;
            ring(batch, a.x, a.y, 6f + 26f * t, c, parentAlpha * (1f - t));
            if (f[3] > 0) text(batch, "PD KILL", a.x + 10f, a.y + 18f, c, parentAlpha * (1f - t));
        }
        batch.setColor(old);
    }

    /** "MISSILE WARNING  2 inbound  impact 6 s", or null when nothing is coming. */
    static String bannerText(ThreatFrame f) {
        int n = f.incoming();
        if (n == 0) return null;
        float t = f.soonestImpact();
        return String.format(Locale.ROOT, "MISSILE WARNING   %d inbound%s", n,
                Float.isNaN(t) ? "" : String.format(Locale.ROOT, "   impact %.1f s", t));
    }

    static boolean urgent(ThreatFrame f) {
        float t = f.soonestImpact();
        return !Float.isNaN(t) && t < URGENT_SECONDS;
    }

    private void dashed(Batch batch, float x1, float y1, float x2, float y2, Color c, float alpha) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float dash = 8f, gap = 6f;
        // Cap the dash count so a path far off screen costs nothing.
        float step = Math.max(dash + gap, len / 200f);
        for (float s = 0; s < len; s += step) {
            float d = Math.min(dash, len - s);
            batch.draw(white, x1 + dx / len * s, y1 + dy / len * s - 0.75f, 0f, 0.75f, d, 1.5f, 1f, 1f, angle);
        }
    }

    private void cross(Batch batch, float x, float y, float r, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x - r, y - 1f, r, 1f, 2f * r, 2f, 1f, 1f, 45f);
        batch.draw(white, x - r, y - 1f, r, 1f, 2f * r, 2f, 1f, 1f, -45f);
    }

    private void box(Batch batch, float x, float y, float r, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x - r, y - r, r, r, 2f * r, 2f * r, 1f, 1f, 45f);
    }

    private void arrow(Batch batch, float x, float y, float angleDeg, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        // Two strokes making a chevron that points along angleDeg.
        batch.draw(white, x - 12f, y - 1.5f, 12f, 1.5f, 12f, 3f, 1f, 1f, angleDeg + 150f);
        batch.draw(white, x - 12f, y - 1.5f, 12f, 1.5f, 12f, 3f, 1f, 1f, angleDeg - 150f);
    }

    private void ring(Batch batch, float cx, float cy, float r, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        int n = MathUtils.clamp((int) (r * 0.6f), 10, 48);
        float seg = MathUtils.PI2 * r / n + 0.5f;
        for (int i = 0; i < n; i++) {
            float ang = MathUtils.PI2 * i / n;
            batch.draw(white, cx + MathUtils.cos(ang) * r, cy + MathUtils.sin(ang) * r - 1f, 0f, 1f, seg, 2f, 1f, 1f,
                    ang * MathUtils.radiansToDegrees + 90f);
        }
    }

    private void text(Batch batch, String s, float x, float y, Color c, float alpha) {
        font.setColor(c.r, c.g, c.b, c.a * alpha);
        font.draw(batch, s, x, y);
    }
}
