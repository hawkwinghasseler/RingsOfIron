package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * H5, the contact scope (functional-context 7, H5): a round pod standing out of the rack's right wing
 * with a plotting scope, north up like the view: contacts as glyphs by allegiance and kind, the Lock's
 * corners and the Nav's ticks, missiles inbound and outbound, the ship's heading, two range rings and
 * the range. The range is set by the situation (an approach 2 km, a fight the smallest step that holds
 * the Lock and every inbound missile, cruise 50 km), and its − and + keys set it by hand until the
 * situation changes. In cruise with nothing inside 50 km it folds to a key showing the count beyond.
 */
final class Scope {
    /** The range steps, km (RadarScope.RANGES with the 5 km step). */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    static final int APPROACH_STEP = 1, CRUISE_STEP = 4;
    /** A step holds a contact with room to spare inside this share of it. */
    static final float ROOM = 0.8f;
    static final int MARKS = 32;
    static final int CLICK_IN = 31, CLICK_OUT = 32;
    static final int HOSTILE = 0, NEUTRAL = 1, STATION = 2, WRECK = 3, MISSILE_IN = 4, MISSILE_OUT = 5, FRIENDLY = 6;

    static final class Mark {
        float x, y;
        int kind;
        boolean lock, nav, rim;
    }

    final Mark[] marks = new Mark[MARKS];
    int count;
    /** The pod is out; else, at a seat, the key on the wing. */
    boolean open, seated;
    int step = CRUISE_STEP, beyond;
    float heading;
    private int manual = -1, situation = -1;
    private boolean pinned;

    Scope() {
        for (int i = 0; i < MARKS; i++) marks[i] = new Mark();
    }

    void update(HudData d, Situations s) {
        count = 0;
        TransformComponent ship = d.ctx.ship == null ? null : d.ctx.ship.getComponent(TransformComponent.class);
        seated = s.exterior() && !s.warp && !s.docked && !s.latched && !s.fullScreen && ship != null;
        if (!seated) {
            open = false;
            return;
        }
        // The situation the automatic range follows; a hand-set range and a pinned pod last until it changes
        int now = s.approach ? 1 : s.combat ? 2 : 0;
        if (now != situation) {
            situation = now;
            manual = -1;
            pinned = false;
        }
        Array<Contact> all = d.contactFrame.contacts;
        float near = Float.MAX_VALUE, hold = 0f;
        beyond = 0;
        for (int i = 0; i < all.size; i++) {
            Contact c = all.get(i);
            if (c.kind == Kind.MISSILE || Float.isNaN(c.range)) continue;
            near = Math.min(near, c.range);
            if (c.range > STEPS[CRUISE_STEP] * 1000f) beyond++;
            if (c.locked) hold = Math.max(hold, c.range);
        }
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack t = tracks.get(i);
            if (t.side == Side.INCOMING && !Float.isNaN(t.range)) hold = Math.max(hold, t.range);
        }
        int auto = s.approach ? APPROACH_STEP : s.combat ? holding(hold) : CRUISE_STEP;
        step = manual >= 0 ? manual : auto;
        open = s.approach || s.combat || pinned || near <= STEPS[CRUISE_STEP] * 1000f;
        if (!open) return;

        heading = d.flightStatus.heading;
        float scale = 1f / (STEPS[step] * 1000f);
        for (int i = 0; i < all.size && count < MARKS; i++) {
            Contact c = all.get(i);
            if (c.kind == Kind.MISSILE) continue;
            int kind = c.relation == Relation.HOSTILE ? HOSTILE : c.kind == Kind.STATION ? STATION
                    : c.kind == Kind.DERELICT ? WRECK : c.relation == Relation.FRIENDLY ? FRIENDLY : NEUTRAL;
            plot(c.x - ship.x, c.y - ship.y, scale, kind, c.locked, c.nav, c.locked || c.nav || kind == HOSTILE);
        }
        for (int i = 0; i < tracks.size && count < MARKS; i++) {
            MissileTrack t = tracks.get(i);
            if (t.side == Side.OTHER) continue;
            plot(t.x - ship.x, t.y - ship.y, scale, t.side == Side.INCOMING ? MISSILE_IN : MISSILE_OUT, false, false, false);
        }
    }

    /** The smallest step that holds {@code range} with room to spare. */
    static int holding(float range) {
        for (int i = 0; i < STEPS.length; i++) if (range <= STEPS[i] * 1000f * ROOM) return i;
        return STEPS.length - 1;
    }

    /** A contact at ({@code dx}, {@code dy}) metres from the ship, world y up; past the rim only when it matters. */
    private void plot(float dx, float dy, float scale, int kind, boolean lock, boolean nav, boolean keep) {
        float u = dx * scale, v = -dy * scale, len = (float) Math.sqrt(u * u + v * v);
        boolean rim = len > 1f;
        if (rim && !keep) return;
        if (rim) {
            u /= len;
            v /= len;
        }
        Mark m = marks[count++];
        m.x = u;
        m.y = v;
        m.kind = kind;
        m.lock = lock;
        m.nav = nav;
        m.rim = rim;
    }

    /** The − and + keys: a step in or out, held until the situation changes. */
    void zoom(int by) {
        manual = Math.max(0, Math.min(STEPS.length - 1, step + by));
        step = manual;
    }

    /** The folded key pressed: the pod comes out until the situation changes. */
    void unfold() {
        pinned = true;
    }

    void boxes(FilamentActor a, float rightX, float bottomY) {
        if (!open) return;
        float cx = rightX + Geo.SCOPE_CX, cy = bottomY + Geo.SCOPE_CY, r = Geo.SCOPE_R;
        a.box(CLICK_OUT, cx - r - 8f, cy + r - 16f, 24f, 24f);
        a.box(CLICK_IN, cx + r - 16f, cy + r - 16f, 24f, 24f);
    }

    static String rangeText(int step) {
        float km = STEPS[step];
        return (km < 1f ? "0.5" : Integer.toString(Math.round(km))) + " KM";
    }

    // ------------------------------------------------------------------ painting, on the grid from the right

    private static final String[] TRI = {"..#..", ".#.#.", "#####"}, CROSS = {".#.", "###", ".#."}, DIAMOND = {".#.", "#.#", ".#."};
    private static final String[] SQUARE = {"###", "#.#", "###"}, WRECKED = {"#.#", ".#.", "#.#"};

    void paint(Filament f) {
        if (!open) return;
        float cx = Geo.SCOPE_CX, cy = Geo.SCOPE_CY, r = Geo.SCOPE_R;
        f.pod(cx, cy, r + 10f);
        f.roundModule(cx, cy, r, f.o().filaments(3));
        for (float yy = -r; yy <= r; yy += 4f) {
            for (float xx = -r; xx <= r; xx += 4f) {
                if (xx * xx + yy * yy < (r - 3f) * (r - 3f)) f.dot(cx + xx, cy + yy, f.o().size(1.1f).ghostOnly().k(0.9f));
            }
        }
        Kit.ringDots(f, cx, cy, r * 0.5f, 4f, 1.2f, Phosphor.ZINC, Drive.DIM);
        Kit.ringDots(f, cx, cy, r - 6f, 4f, 1.2f, Phosphor.ZINC, Drive.DIM);
        float a = (float) Math.toRadians(heading);
        f.dotLine(cx, cy, cx + (float) Math.cos(a) * (r - 10f), cy - (float) Math.sin(a) * (r - 10f), f.o().step(3f).size(1.2f).dim());
        f.sprite(cx, cy, CROSS, f.o().centred().pitch(1.6f).norm());
        for (int i = 0; i < count; i++) {
            Mark m = marks[i];
            float px = cx + m.x * (r - 6f), py = cy + m.y * (r - 6f);
            if (m.rim) {
                f.dot(px, py, f.o().size(2.4f).color(m.kind == HOSTILE ? Phosphor.RED : Phosphor.ZINC).norm());
            } else {
                switch (m.kind) {
                    case HOSTILE: f.sprite(px, py, TRI, f.o().centred().pitch(1.6f).red().norm()); break;
                    case MISSILE_IN: f.sprite(px, py, CROSS, f.o().centred().pitch(1.3f).red().full()); break;
                    case MISSILE_OUT: f.sprite(px, py, CROSS, f.o().centred().pitch(1.3f).norm()); break;
                    case STATION: f.sprite(px, py, SQUARE, f.o().centred().pitch(1.6f).norm()); break;
                    case WRECK: f.sprite(px, py, WRECKED, f.o().centred().pitch(1.6f).dim()); break;
                    case FRIENDLY: f.sprite(px, py, TRI, f.o().centred().pitch(1.6f).norm()); break;
                    default: f.sprite(px, py, DIAMOND, f.o().centred().pitch(1.6f).dim());
                }
            }
            if (m.lock) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        f.dot(px + sx * 7f, py + sy * 7f, f.o().size(1.3f).full());
                        f.dot(px + sx * 5f, py + sy * 7f, f.o().size(1.3f).full());
                        f.dot(px + sx * 7f, py + sy * 5f, f.o().size(1.3f).full());
                    }
                }
            }
            if (m.nav) {
                f.dot(px, py - 7f, f.o().size(1.3f).norm());
                f.dot(px, py + 7f, f.o().size(1.3f).norm());
                f.dot(px - 7f, py, f.o().size(1.3f).norm());
                f.dot(px + 7f, py, f.o().size(1.3f).norm());
            }
        }
        String range = rangeText(step);
        float rw = f.legendWidth(range, f.o());
        f.legend(cx - rw / 2f, cy + r - 12f, range, f.o().dim());
        f.key(cx - r - 8f, cy + r - 16f, 24f, 24f, "−", f.o().size(14f));
        f.key(cx + r - 16f, cy + r - 16f, 24f, 24f, "+", f.o().size(14f));
    }
}
