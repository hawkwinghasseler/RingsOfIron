package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.MapBody;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;

import java.util.Locale;

/**
 * Full-screen map of off-bubble traffic: the frame body in the middle, other bodies as circles,
 * stations as squares, haulers as diamonds and the player as a cyan marker. Scaled so every contact
 * fits; {@link #zoom} narrows it. Refreshed from a {@link TrafficMapProvider} a few times a second.
 * Known pirates show as red triangles, dim where only a last known position is held. Clicking a
 * contact picks it ({@link #setPickListener}); the picked one, the navigation target, is bracketed.
 */
public class TrafficMap extends Widget {
    public static final float REFRESH_INTERVAL = 0.25f;
    private static final Color STATION = new Color(0.4f, 0.75f, 1f, 1f);
    private static final Color HAULER = new Color(1f, 0.85f, 0.25f, 1f);
    private static final Color OBJECTIVE = new Color(1f, 0.7f, 0.1f, 1f);
    private static final Color PLAYER = HudSkin.HUD_CYAN;
    private static final Color BODY = new Color(0.35f, 0.45f, 0.55f, 1f);
    private static final Color HOSTILE = new Color(1f, 0.25f, 0.2f, 1f);
    private static final Color TARGET = new Color(1f, 0.35f, 0.9f, 1f);
    /** How near a click must land to a contact to pick it, pixels. */
    static final float PICK_RADIUS = 14f;
    private static final float LINE = 14f;
    private static final float LABEL_WIDTH = 150f;
    private final FloatArray labels = new FloatArray();
    /** Haulers docked at each station, by station name, this frame. */
    private final com.badlogic.gdx.utils.ObjectIntMap<String> docked = new com.badlogic.gdx.utils.ObjectIntMap<>();
    private static final Color GRID = new Color(0.2f, 0.3f, 0.4f, 0.5f);

    private final TextureRegion white;
    private final BitmapFont font;
    private final TrafficFrame frame = new TrafficFrame();
    private TrafficMapProvider provider;
    private float refreshTimer;
    private float zoom = 1f;
    private final Color tmp = new Color();
    /** Where each pickable contact was drawn last frame: x, y, index into the frame's contacts. */
    private final FloatArray picks = new FloatArray();
    private java.util.function.Consumer<String> pickListener;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout measure = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    public TrafficMap(Skin skin) {
        white = skin.getRegion("white");
        font = skin.getFont("default-font");
        setTouchable(Touchable.disabled);
        addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                pick(getX() + x, getY() + y);
            }
        });
    }

    /** Called with a contact's {@link Contact#id} when the player clicks it; null turns clicking off. */
    public void setPickListener(java.util.function.Consumer<String> listener) {
        pickListener = listener;
        setTouchable(listener == null ? Touchable.disabled : Touchable.enabled);
    }

    /** Picks the contact drawn nearest (x, y), in the map's parent coordinates, if one is close enough. */
    public String pick(float x, float y) {
        String best = null;
        float nearest = PICK_RADIUS;
        for (int i = 0; i + 2 < picks.size; i += 3) {
            float d = (float) Math.hypot(picks.get(i) - x, picks.get(i + 1) - y);
            int index = (int) picks.get(i + 2);
            if (d < nearest && index < frame.contacts.size) {
                nearest = d;
                best = frame.contacts.get(index).id;
            }
        }
        if (best != null && pickListener != null) pickListener.accept(best);
        return best;
    }

    public void setProvider(TrafficMapProvider provider) {
        this.provider = provider;
        refreshTimer = 0f;
    }

    public TrafficFrame getFrame() {
        return frame;
    }

    /** Multiplies the zoom (1 = fit everything); clamped to 1..1000. */
    public void zoom(float factor) {
        zoom = MathUtils.clamp(zoom * factor, 1f, 1000f);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!isVisible()) return;
        refreshTimer -= delta;
        if (refreshTimer <= 0f) {
            refreshTimer = REFRESH_INTERVAL;
            refresh();
        }
    }

    /** Pulls a fresh picture from the provider now. */
    public void refresh() {
        frame.clear();
        if (provider != null) provider.collect(frame);
    }

    /**
     * Meters from the center to the map edge before zoom: the farthest contact or body, with margin.
     * Last known positions don't stretch it; they show when the map reaches them.
     */
    static double fitRadius(TrafficFrame f) {
        double r = f.frameRadius * 1.5;
        for (Contact c : f.contacts) if (!c.stale) r = Math.max(r, Math.hypot(c.x, c.y) * 1.15);
        for (MapBody b : f.bodies) r = Math.max(r, Math.hypot(b.x, b.y) + b.radius);
        return Math.max(r, 1.0);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        Color old = batch.getColor().cpy();
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f - 40f;

        rect(batch, getX(), getY(), getWidth(), getHeight(), tmp.set(0.01f, 0.02f, 0.04f, 0.94f), parentAlpha);

        if (frame.frameName.isEmpty()) {
            text(batch, "No traffic data", cx - 50f, cy, Color.LIGHT_GRAY, parentAlpha);
            title(batch, parentAlpha);
            batch.setColor(old);
            return;
        }
        double scale = half / (fitRadius(frame) / zoom); // pixels per meter
        // Zoomed in, the view slides from the frame body toward the player so the wheel zooms on you.
        Contact you = player(frame);
        if (you != null) {
            double pull = 1.0 - 1.0 / zoom;
            cx -= (float) (you.x * pull * scale);
            cy -= (float) (you.y * pull * scale);
        }
        float viewX = getX() + getWidth() / 2f, viewY = getY() + getHeight() / 2f;

        // Range rings at round distances.
        double ring = niceStep(half / scale / 3.0);
        // Rings close together label only those whose distance fits clear of the last one's
        float labelEnd = Float.NEGATIVE_INFINITY;
        for (int i = 1; i <= 6; i++) {
            float r = (float) (ring * i * scale);
            if (r > half * 1.5f) break;
            if (ring * i < frame.frameRadius) continue;   // inside the body
            circle(batch, cx, cy, r, 1f, GRID, parentAlpha);
            String distance = formatDistance(ring * i);
            measure.setText(font, distance);
            if (cx + r + 3f < labelEnd + 8f) continue;
            text(batch, distance, cx + r + 3f, cy + 12f, GRID, parentAlpha);
            labelEnd = cx + r + 3f + measure.width;
        }

        // Frame body. Body names go in the label list first, so stations near them label clear of them.
        labels.clear();
        float fr = Math.max(4f, (float) (frame.frameRadius * scale));
        circle(batch, cx, cy, fr, 2f, BODY, parentAlpha);
        text(batch, frame.frameName, cx - 20f, cy - fr - 6f, BODY, parentAlpha);
        labels.add(cx - 20f, cy - fr - 6f, LINE);

        for (MapBody b : frame.bodies) {
            float x = cx + (float) (b.x * scale), y = cy + (float) (b.y * scale);
            float r = Math.max(3f, (float) (b.radius * scale));
            circle(batch, x, y, r, 1.5f, BODY, parentAlpha);
            text(batch, b.name, x + r + 4f, y + 6f, BODY, parentAlpha);
            labels.add(x + r + 4f, y + 6f, LINE);
        }

        // Haulers sitting at a station are counted on the station's label rather than each listed
        docked.clear();
        for (Contact c : frame.contacts) {
            if (dockedQuietly(c)) docked.getAndIncrement(c.detail.substring(DOCKED_AT.length()), 0, 1);
        }
        picks.clear();
        for (int index = 0; index < frame.contacts.size; index++) {
            Contact c = frame.contacts.get(index);
            float x = cx + (float) (c.x * scale), y = cy + (float) (c.y * scale);
            if (Math.abs(x - viewX) > getWidth() / 2f || Math.abs(y - viewY) > getHeight() / 2f) continue;
            if (!c.id.isEmpty()) picks.add(x, y, index);
            Color col = c.kind == ContactKind.STATION ? STATION : c.kind == ContactKind.HAULER ? HAULER
                    : c.kind == ContactKind.PLAYER ? PLAYER : c.kind == ContactKind.HOSTILE ? HOSTILE : Color.LIGHT_GRAY;
            if (c.stale) col = tmp.set(col).mul(0.6f, 0.6f, 0.6f, 0.8f);
            batch.setColor(col.r, col.g, col.b, col.a * parentAlpha);
            if (c.kind == ContactKind.STATION) {
                outline(batch, x - 5f, y - 5f, 10f, 10f, 2f);
            } else if (c.kind == ContactKind.HAULER) {
                batch.draw(white, x - 3.5f, y - 3.5f, 3.5f, 3.5f, 7f, 7f, 1f, 1f, 45f);
            } else if (c.kind == ContactKind.HOSTILE) {
                triangle(batch, x, y, 6f, c.stale);
            } else {
                batch.draw(white, x - 5f, y - 5f, 5f, 5f, 10f, 10f, 1f, 1f, 45f);
                circle(batch, x, y, 10f, 1.5f, col, parentAlpha);
            }
            // A contract's waypoint gets a gold ring
            if (c.objective) circle(batch, x, y, 14f, 2f, OBJECTIVE, parentAlpha);
            // The navigation target gets brackets
            if (c.target) {
                batch.setColor(TARGET.r, TARGET.g, TARGET.b, parentAlpha);
                brackets(batch, x, y, 18f);
            }
            if (dockedQuietly(c) || c.faded && !c.target && !c.objective) continue;
            int atStation = c.kind == ContactKind.STATION ? docked.get(c.name, 0) : 0;
            String name = atStation == 0 ? c.name : c.name + "  (" + atStation + " docked)";
            float lines = c.detail.isEmpty() ? 1 : 2;
            float ly = labelY(labels, x + 9f, y + 7f, lines * LINE);
            if (ly != y + 7f) line(batch, x, y, x + 9f, ly - LINE / 2f, col, parentAlpha);
            text(batch, name, x + 9f, ly, col, parentAlpha);
            if (!c.detail.isEmpty()) text(batch, c.detail, x + 9f, ly - LINE, HudSkin.HUD_DIM, parentAlpha);
        }

        regions(batch, parentAlpha);
        title(batch, parentAlpha);
        batch.setColor(old);
    }

    /** The tension readout in the bottom left corner, a line per region, high ones in the hostile colour. */
    private void regions(Batch batch, float alpha) {
        if (frame.regions.size == 0) return;
        float x = getX() + 16f, y = getY() + 16f + LINE * frame.regions.size;
        text(batch, "TENSION", x, y + LINE, HudSkin.HUD_DIM, alpha);
        for (TrafficMapProvider.Region r : frame.regions) {
            text(batch, r.line, x, y, r.high ? HOSTILE : HudSkin.HUD_DIM, alpha);
            y -= LINE;
        }
    }

    private static final String DOCKED_AT = "docked at ";

    /** A hauler docked at a station that isn't the target or a contract's: no label of its own. */
    static boolean dockedQuietly(Contact c) {
        return c.kind == ContactKind.HAULER && c.detail.startsWith(DOCKED_AT) && !c.target && !c.objective;
    }

    /** A triangle pointing up, of half-size {@code r}, outlined only when {@code hollow}. */
    private void triangle(Batch batch, float x, float y, float r, boolean hollow) {
        if (!hollow) {
            for (float dy = -r; dy < r; dy += 1f) {
                float w = r * (r - dy) / (2f * r);
                batch.draw(white, x - w, y + dy, 2f * w, 1f);
            }
            return;
        }
        float side = (float) Math.hypot(r, 2f * r);
        float angle = MathUtils.atan2(2f * r, r) * MathUtils.radiansToDegrees;
        batch.draw(white, x - r, y - r, 2f * r, 1.5f);
        batch.draw(white, x - r, y - r, 0f, 0f, side, 1.5f, 1f, 1f, angle);
        batch.draw(white, x + r, y - r, 0f, 0f, side, 1.5f, 1f, 1f, 180f - angle);
    }

    /** Four corner brackets around (x, y), {@code size} across. */
    private void brackets(Batch batch, float x, float y, float size) {
        float h = size / 2f, arm = size / 4f, t = 2f;
        batch.draw(white, x - h, y + h - t, arm, t);
        batch.draw(white, x - h, y + h - arm, t, arm);
        batch.draw(white, x + h - arm, y + h - t, arm, t);
        batch.draw(white, x + h - t, y + h - arm, t, arm);
        batch.draw(white, x - h, y - h, arm, t);
        batch.draw(white, x - h, y - h, t, arm);
        batch.draw(white, x + h - arm, y - h, arm, t);
        batch.draw(white, x + h - t, y - h, t, arm);
    }

    static Contact player(TrafficFrame f) {
        for (Contact c : f.contacts) if (c.kind == ContactKind.PLAYER) return c;
        return null;
    }

    /**
     * The top of a label at (x, y) of the given height, moved down past labels already placed this
     * frame so contacts sharing an orbit slot (haulers docked at a station) stay readable.
     */
    static float labelY(FloatArray labels, float x, float y, float height) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int i = 0; i < labels.size; i += 3) {
                float lx = labels.get(i), top = labels.get(i + 1), bottom = top - labels.get(i + 2);
                if (Math.abs(lx - x) < LABEL_WIDTH && y > bottom && y - height < top) {
                    y = bottom - 2f;
                    moved = true;
                }
            }
        }
        labels.add(x, y, height);
        return y;
    }

    private void title(Batch batch, float alpha) {
        String head = frame.frameName.isEmpty() ? "TRAFFIC MAP" : String.format(Locale.ROOT,
                "TRAFFIC MAP  %s   %d stations   %d haulers%s", frame.frameName,
                frame.count(ContactKind.STATION), frame.count(ContactKind.HAULER),
                frame.count(ContactKind.HOSTILE) == 0 ? "" : "   " + frame.count(ContactKind.HOSTILE) + " pirates");
        text(batch, head, getX() + 16f, getY() + getHeight() - 14f, HudSkin.HUD_CYAN, alpha);
        text(batch, pickListener == null ? "[M] close   [mouse wheel] zoom"
                : "[M] close   [mouse wheel] zoom   [click] set navigation target", getX() + 16f, getY() + getHeight() - 32f, HudSkin.HUD_DIM, alpha);
    }

    private void text(Batch batch, String s, float x, float y, Color c, float alpha) {
        font.setColor(c.r, c.g, c.b, c.a * alpha);
        font.draw(batch, s, x, y);
    }

    private void line(Batch batch, float x1, float y1, float x2, float y2, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha * 0.5f);
        float dx = x2 - x1, dy = y2 - y1;
        batch.draw(white, x1, y1 - 0.5f, 0f, 0.5f, (float) Math.hypot(dx, dy), 1f, 1f, 1f,
                MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    private void rect(Batch batch, float x, float y, float w, float h, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x, y, w, h);
    }

    private void outline(Batch batch, float x, float y, float w, float h, float t) {
        batch.draw(white, x, y, w, t);
        batch.draw(white, x, y + h - t, w, t);
        batch.draw(white, x, y, t, h);
        batch.draw(white, x + w - t, y, t, h);
    }

    /** A circle outline as short segments. */
    private void circle(Batch batch, float cx, float cy, float r, float t, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        int n = MathUtils.clamp((int) (r * 0.6f), 12, 96);
        float seg = MathUtils.PI2 * r / n + 0.5f;
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float x = cx + MathUtils.cos(a) * r, y = cy + MathUtils.sin(a) * r;
            batch.draw(white, x, y - t / 2f, 0f, t / 2f, seg, t, 1f, 1f, a * MathUtils.radiansToDegrees + 90f);
        }
    }

    /** 1, 2 or 5 times a power of ten at or below {@code x}. */
    static double niceStep(double x) {
        if (x <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(x)));
        double m = x / p;
        return (m >= 5 ? 5 : m >= 2 ? 2 : 1) * p;
    }

    static String formatDistance(double m) {
        if (m < 1000) return String.format(Locale.ROOT, "%.0f m", m);
        if (m < 1e6) return String.format(Locale.ROOT, "%.0f km", m / 1000);
        return String.format(Locale.ROOT, "%,.0f km", m / 1000);
    }

    @Override
    public float getPrefWidth() {
        return 800f;
    }

    @Override
    public float getPrefHeight() {
        return 600f;
    }
}
