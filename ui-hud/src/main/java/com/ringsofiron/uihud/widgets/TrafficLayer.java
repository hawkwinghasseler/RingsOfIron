package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.MapBody;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;

import java.util.Locale;

/**
 * Off-bubble traffic drawn over the {@link ManeuverMap}'s {@link OrbitMap}, in the same frame: stations
 * as squares, haulers as diamonds, known pirates as red triangles (hollow where only a last known
 * position is held), derelicts and the like as grey diamonds. A contract's waypoint is ringed in gold
 * and the navigation target bracketed. Haulers sitting at a station are counted on its label. The
 * tension readout goes in a corner. Refreshed from a {@link TrafficMapProvider} a few times a second;
 * {@link #pick} finds the contact under a click.
 *
 * <p>The orbit map draws the body, the moons and the ship itself, so the frame's bodies and the player's
 * own contact are left to it.
 */
public class TrafficLayer {
    public static final float REFRESH_INTERVAL = 0.25f;
    private static final Color STATION = new Color(0.4f, 0.75f, 1f, 1f);
    private static final Color HAULER = new Color(1f, 0.85f, 0.25f, 1f);
    private static final Color OBJECTIVE = new Color(1f, 0.7f, 0.1f, 1f);
    private static final Color HOSTILE = new Color(1f, 0.25f, 0.2f, 1f);
    private static final Color TARGET = new Color(1f, 0.35f, 0.9f, 1f);
    /** How near a click must land to a contact to pick it, pixels. */
    static final float PICK_RADIUS = 14f;
    static final float LINE = 14f;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout measure = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    private final FloatArray labels = new FloatArray();
    /** Haulers docked at each station, by station name, this frame. */
    private final com.badlogic.gdx.utils.ObjectIntMap<String> docked = new com.badlogic.gdx.utils.ObjectIntMap<>();

    private final TextureRegion white;
    private final BitmapFont font;
    private final TrafficFrame frame = new TrafficFrame();
    private TrafficMapProvider provider;
    private float refreshTimer;
    private final Color tmp = new Color();
    /** Where each pickable contact was drawn last frame: x, y, index into the frame's contacts. */
    private final FloatArray picks = new FloatArray();

    public TrafficLayer(TextureRegion white, BitmapFont font) {
        this.white = white;
        this.font = font;
    }

    public void setProvider(TrafficMapProvider provider) {
        this.provider = provider;
        refreshTimer = 0f;
    }

    public boolean hasProvider() {
        return provider != null;
    }

    public TrafficFrame getFrame() {
        return frame;
    }

    /** Counts down to the next refresh; call each frame the map is open. */
    public void act(float delta) {
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

    /** Whether the traffic is around the body the orbit map is drawn around, so the two line up. */
    public boolean matches(String bodyName) {
        return !frame.frameName.isEmpty() && frame.frameName.equals(bodyName);
    }

    /**
     * The id of the contact drawn nearest (x, y), stage coordinates, within {@link #PICK_RADIUS}; null
     * for none.
     */
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
        return best;
    }

    /**
     * Meters from the body to take in every contact and body, with margin: the zoom that shows all the
     * traffic. Last known positions don't stretch it; they show when the map reaches them.
     */
    static double fitRadius(TrafficFrame f) {
        double r = f.frameRadius * 1.5;
        for (Contact c : f.contacts) if (!c.stale) r = Math.max(r, Math.hypot(c.x, c.y) * 1.15);
        for (MapBody b : f.bodies) r = Math.max(r, Math.hypot(b.x, b.y) + b.radius);
        return Math.max(r, 1.0);
    }

    public double fitRadius() {
        return fitRadius(frame);
    }

    /**
     * Draws the contacts where {@code map} draws its frame, those inside the box (x, y, w, h) in stage
     * coordinates; labels already placed (the orbit map's) are not known, so contacts label clear of
     * each other only.
     */
    public void draw(Batch batch, float alpha, OrbitMap map, float left, float bottom, float width, float height) {
        labels.clear();
        picks.clear();
        // Clear of the orbit map's own labels: the apsides, the moon's pass, the burns
        for (com.badlogic.gdx.math.Rectangle r : map.getPlacedLabels()) labels.addAll(r.x - 2f, r.y + r.height + 2f, r.width + 4f, r.height + 4f);
        // Haulers sitting at a station are counted on the station's label rather than each listed
        docked.clear();
        for (Contact c : frame.contacts) {
            if (dockedQuietly(c)) docked.getAndIncrement(c.detail.substring(DOCKED_AT.length()), 0, 1);
        }
        for (int index = 0; index < frame.contacts.size; index++) {
            Contact c = frame.contacts.get(index);
            if (c.kind == ContactKind.PLAYER) continue;
            float x = map.screenX(c.x), y = map.screenY(c.y);
            if (x < left || x > left + width || y < bottom || y > bottom + height) continue;
            if (!c.id.isEmpty()) picks.add(x, y, index);
            Color col = c.kind == ContactKind.STATION ? STATION : c.kind == ContactKind.HAULER ? HAULER
                    : c.kind == ContactKind.HOSTILE ? HOSTILE : Color.LIGHT_GRAY;
            if (c.stale) col = tmp.set(col).mul(0.6f, 0.6f, 0.6f, 0.8f);
            batch.setColor(col.r, col.g, col.b, col.a * alpha);
            if (c.kind == ContactKind.STATION) {
                outline(batch, x - 5f, y - 5f, 10f, 10f, 2f);
            } else if (c.kind == ContactKind.HAULER) {
                batch.draw(white, x - 3.5f, y - 3.5f, 3.5f, 3.5f, 7f, 7f, 1f, 1f, 45f);
            } else if (c.kind == ContactKind.HOSTILE) {
                triangle(batch, x, y, 6f, c.stale);
            } else {
                batch.draw(white, x - 4f, y - 4f, 4f, 4f, 8f, 8f, 1f, 1f, 45f);
            }
            // A contract's waypoint gets a gold ring
            if (c.objective) circle(batch, x, y, 14f, 2f, OBJECTIVE, alpha);
            // The navigation target gets brackets
            if (c.target) {
                batch.setColor(TARGET.r, TARGET.g, TARGET.b, alpha);
                brackets(batch, x, y, 18f);
            }
            if (dockedQuietly(c) || c.faded && !c.target && !c.objective) continue;
            int atStation = c.kind == ContactKind.STATION ? docked.get(c.name, 0) : 0;
            String name = atStation == 0 ? c.name : c.name + "  (" + atStation + " docked)";
            Color nameColor = c.target ? TARGET : col;
            float lines = c.detail.isEmpty() ? 1 : 2;
            measure.setText(font, name);
            float w = measure.width;
            if (!c.detail.isEmpty()) {
                measure.setText(font, c.detail);
                w = Math.max(w, measure.width);
            }
            float ly = labelY(labels, x + 9f, y + 7f, w, lines * LINE);
            if (ly != y + 7f) line(batch, x, y, x + 9f, ly - LINE / 2f, col, alpha);
            text(batch, name, x + 9f, ly, nameColor, alpha);
            if (!c.detail.isEmpty()) text(batch, c.detail, x + 9f, ly - LINE, HudSkin.HUD_DIM, alpha);
        }
    }

    /** The tension readout, a line per region from {@code top} down, high ones in the hostile colour. */
    public void drawRegions(Batch batch, float alpha, float x, float top) {
        if (frame.regions.size == 0) return;
        text(batch, "TENSION", x, top, HudSkin.HUD_DIM, alpha);
        float y = top - LINE;
        for (TrafficMapProvider.Region r : frame.regions) {
            text(batch, r.line, x, y, r.high ? HOSTILE : HudSkin.HUD_DIM, alpha);
            y -= LINE;
        }
    }

    /** Lines the tension readout takes, its heading included; 0 for none. */
    public int regionLines() {
        return frame.regions.size == 0 ? 0 : frame.regions.size + 1;
    }

    /** "5 stations   12 haulers   2 pirates" for the title; empty with no traffic. */
    public String counts() {
        if (frame.frameName.isEmpty()) return "";
        int pirates = frame.count(ContactKind.HOSTILE);
        return String.format(Locale.ROOT, "%d stations   %d haulers%s", frame.count(ContactKind.STATION),
                frame.count(ContactKind.HAULER), pirates == 0 ? "" : "   " + pirates + " pirates");
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

    /**
     * The top of a label at (x, y) of the given size, moved down past labels already placed this frame
     * (x, top, width, height each) so contacts sharing an orbit slot (haulers docked at a station) stay
     * readable.
     */
    static float labelY(FloatArray labels, float x, float y, float width, float height) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int i = 0; i + 3 < labels.size; i += 4) {
                float lx = labels.get(i), top = labels.get(i + 1), lw = labels.get(i + 2), bottom = top - labels.get(i + 3);
                if (x < lx + lw && x + width > lx && y > bottom && y - height < top) {
                    y = bottom - 2f;
                    moved = true;
                }
            }
        }
        labels.addAll(x, y, width, height);
        return y;
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
}
