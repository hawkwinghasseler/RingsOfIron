package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.FloatArray;
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
 * tension readout goes in a corner. Refreshed from a {@link TrafficMapProvider} every frame the map is
 * open, so contacts move smoothly rather than lurching from one refresh to the next; {@link #pick} finds
 * the contact under a click.
 *
 * <p>The orbit map draws the body, the moons and the ship itself, so the frame's bodies and the player's
 * own contact are left to it. It works out where each contact and label goes; a {@link MapPainter}
 * draws them there ({@link #setPainter}).
 */
public class TrafficLayer {
    /** How near a click must land to a contact to pick it, pixels. */
    static final float PICK_RADIUS = 14f;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout measure = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    private final FloatArray labels = new FloatArray();
    /** Haulers docked at each station, by station name, this frame. */
    private final com.badlogic.gdx.utils.ObjectIntMap<String> docked = new com.badlogic.gdx.utils.ObjectIntMap<>();

    private final MapPainter classic;
    private MapPainter painter;
    private final TrafficFrame frame = new TrafficFrame();
    private TrafficMapProvider provider;
    /** Where each pickable contact was drawn last frame: x, y, index into the frame's contacts. */
    private final FloatArray picks = new FloatArray();
    /** Kinds a HUD variant's layer toggles hide, and whether contracts' waypoints are ringed. */
    private final java.util.EnumSet<ContactKind> hidden = java.util.EnumSet.noneOf(ContactKind.class);
    private boolean objectivesShown = true;

    public TrafficLayer(TextureRegion white, BitmapFont font) {
        classic = new MapPainter(white, font);
        painter = classic;
    }

    /** Draws the contacts with {@code painter}, as the orbit map under them does; null for the classic look. */
    public void setPainter(MapPainter painter) {
        this.painter = painter == null ? classic : painter;
    }

    public void setProvider(TrafficMapProvider provider) {
        this.provider = provider;
    }

    public boolean hasProvider() {
        return provider != null;
    }

    public TrafficFrame getFrame() {
        return frame;
    }

    /**
     * Shows or hides a kind of contact, as a HUD variant's layer toggle does: a hidden one is neither
     * drawn nor picked, but the navigation target shows whatever its kind. All show by default.
     */
    public void setShown(ContactKind kind, boolean shown) {
        if (shown) hidden.remove(kind);
        else hidden.add(kind);
    }

    public boolean isShown(ContactKind kind) {
        return !hidden.contains(kind);
    }

    /** Rings contracts' waypoints, or not (a variant's layer toggle); on by default. */
    public void setObjectivesShown(boolean shown) {
        objectivesShown = shown;
    }

    public boolean objectivesShown() {
        return objectivesShown;
    }

    /** Every kind of contact and the waypoints' rings shown again, as the map is by default. */
    public void showAll() {
        hidden.clear();
        objectivesShown = true;
    }

    /**
     * Refreshes the picture; call each frame the map is open. Every frame: the map follows the ship, and
     * a picture a quarter of a second old has everything lurch against it four times a second.
     */
    public void act(float delta) {
        refresh();
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
        BitmapFont font = painter.font();
        float line = painter.lineHeight();
        for (int index = 0; index < frame.contacts.size; index++) {
            Contact c = frame.contacts.get(index);
            if (c.kind == ContactKind.PLAYER || !c.target && hidden.contains(c.kind)) continue;
            float x = map.screenX(c.x), y = map.screenY(c.y);
            if (x < left || x > left + width || y < bottom || y > bottom + height) continue;
            if (!c.id.isEmpty()) picks.add(x, y, index);
            painter.contact(batch, c.kind, c.stale, x, y, alpha);
            // A contract's waypoint gets a gold ring
            if (c.objective && objectivesShown) painter.objective(batch, x, y, alpha);
            // The navigation target gets brackets
            if (c.target) painter.targetBrackets(batch, x, y, alpha);
            if (font == null || dockedQuietly(c) || c.faded && !c.target && !c.objective) continue;
            int atStation = c.kind == ContactKind.STATION ? docked.get(c.name, 0) : 0;
            String name = atStation == 0 ? c.name : c.name + "  (" + atStation + " docked)";
            float lines = c.detail.isEmpty() ? 1 : 2;
            measure.setText(font, name);
            float w = measure.width;
            if (!c.detail.isEmpty()) {
                measure.setText(font, c.detail);
                w = Math.max(w, measure.width);
            }
            float ly = labelY(labels, x + 9f, y + 7f, w, lines * line);
            if (ly != y + 7f) painter.leader(batch, c.kind, c.stale, x, y, x + 9f, ly - line / 2f, alpha);
            painter.contactName(batch, c.kind, c.stale, c.target, name, x + 9f, ly, alpha);
            if (!c.detail.isEmpty()) painter.contactDetail(batch, c.detail, x + 9f, ly - line, alpha);
        }
    }

    /** The tension readout, a line per region from {@code top} down, high ones in the hostile colour. */
    public void drawRegions(Batch batch, float alpha, float x, float top) {
        painter.regions(batch, frame.regions, x, top, alpha);
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
