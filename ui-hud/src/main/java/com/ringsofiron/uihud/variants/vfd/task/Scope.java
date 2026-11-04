package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
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
 * SNS, the sensor scope in the bottom right corner (functional-task 6, SNS): a square graphic dot
 * panel, north up like the view, with two dotted rings, the heading as a line, and the contacts by
 * relation in shape as well as colour (9.4): hostile a triangle, neutral an open diamond, friendly a
 * filled one, a station a square, the inert a cross, our own crew outside a small box; missiles in
 * red coming and zinc going. The lock carries corners and the navigation target four ticks. Contacts
 * past the range sit on the rim when they matter. The range is automatic, the smallest step that holds
 * every inbound missile and the lock with room to spare, else the nearest hostile, else the nearest
 * contact; the − and + keys under it set it by hand until the post changes, and AUTO goes dark.
 */
final class Scope {
    /** The range steps, km: RadarScope's with the 5 km step fights need. */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    /** With nothing to hold, 50 km. */
    static final int REST = 4;
    /** A step holds a contact with room to spare inside this share of it. */
    static final float ROOM = 0.8f;
    static final int MARKS = 32;
    static final int CLICK_OUT = 40, CLICK_IN = 41, CLICK_MARK = 100;
    static final int HOSTILE = 0, NEUTRAL = 1, STATION = 2, INERT = 3, MISSILE_IN = 4, MISSILE_OUT = 5, FRIENDLY = 6, OWN = 7;
    /** The outer ring's radius: the rim. */
    static final float RING = 60f;

    static final class Mark {
        float x, y;
        int kind;
        boolean lock, nav, rim;
        Entity entity;
    }

    final Mark[] marks = new Mark[MARKS];
    int count;
    boolean on, auto = true;
    int step = REST, hostiles;
    float heading = Float.NaN;
    private int manual = -1;
    private Posts.Post post = Posts.Post.NONE;

    Scope() {
        for (int i = 0; i < MARKS; i++) marks[i] = new Mark();
    }

    void update(HudData d, Posts p) {
        count = 0;
        hostiles = 0;
        if (p.post != post) {
            // A range set by hand lasts until the post changes
            post = p.post;
            manual = -1;
        }
        TransformComponent ship = d.ctx.ship == null ? null : d.ctx.ship.getComponent(TransformComponent.class);
        on = p.scope() && ship != null;
        if (!on) return;
        Array<Contact> all = d.contactFrame.contacts;
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        float hold = 0f, nearHostile = Float.NaN, near = Float.NaN;
        for (int i = 0; i < all.size; i++) {
            Contact c = all.get(i);
            if (c.kind == Kind.MISSILE || Float.isNaN(c.range)) continue;
            if (c.relation == Relation.HOSTILE) {
                hostiles++;
                nearHostile = Float.isNaN(nearHostile) ? c.range : Math.min(nearHostile, c.range);
            }
            if (c.kind != Kind.EVA) near = Float.isNaN(near) ? c.range : Math.min(near, c.range);
            if (c.locked) hold = Math.max(hold, c.range);
        }
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack t = tracks.get(i);
            if (t.side == Side.INCOMING && !Float.isNaN(t.range)) hold = Math.max(hold, t.range);
        }
        step = manual >= 0 ? manual : autoStep(hold, nearHostile, near);
        auto = manual < 0;
        heading = d.flightStatus.heading;

        float scale = 1f / (STEPS[step] * 1000f);
        for (int i = 0; i < all.size && count < MARKS; i++) {
            Contact c = all.get(i);
            if (c.kind == Kind.MISSILE) continue;
            int kind = kind(c);
            plot(c.x - ship.x, c.y - ship.y, scale, kind, c.locked, c.nav, c.locked || c.nav || kind == HOSTILE || kind == STATION, c.entity);
        }
        for (int i = 0; i < tracks.size && count < MARKS; i++) {
            MissileTrack t = tracks.get(i);
            if (t.side == Side.OTHER) continue;
            plot(t.x - ship.x, t.y - ship.y, scale, t.side == Side.INCOMING ? MISSILE_IN : MISSILE_OUT, false, false, false, null);
        }
    }

    /** The automatic step: the lock and every inbound missile; else the nearest hostile; else the nearest contact. */
    static int autoStep(float hold, float nearHostile, float near) {
        if (hold > 0f) return holding(hold);
        if (!Float.isNaN(nearHostile)) return holding(nearHostile);
        if (!Float.isNaN(near)) return holding(near);
        return REST;
    }

    /** The smallest step that holds {@code range}, m, with room to spare. */
    static int holding(float range) {
        for (int i = 0; i < STEPS.length; i++) if (range <= STEPS[i] * 1000f * ROOM) return i;
        return STEPS.length - 1;
    }

    /** A contact's class on the scope: relation first, then what it is. */
    static int kind(Contact c) {
        if (c.kind == Kind.EVA) return OWN;
        if (c.relation == Relation.HOSTILE) return HOSTILE;
        if (c.kind == Kind.STATION) return STATION;
        if (c.kind == Kind.DERELICT) return INERT;
        if (c.relation == Relation.FRIENDLY) return FRIENDLY;
        return NEUTRAL;
    }

    /** A contact ({@code dx}, {@code dy}) metres from the ship, world y up; past the rim only when it matters. */
    private void plot(float dx, float dy, float scale, int kind, boolean lock, boolean nav, boolean keep, Entity e) {
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
        m.entity = e;
    }

    /** The − and + keys: a step out or in, held until the post changes. */
    void zoom(int by) {
        manual = Math.max(0, Math.min(STEPS.length - 1, step + by));
        step = manual;
        auto = false;
    }

    /** The keys, and a box on each contact that can be picked, on the stage from the pod's origin. */
    void boxes(FilamentActor a, float ox, float oy) {
        if (!on) return;
        a.box(CLICK_OUT, ox + Geo.ZOOM_OUT_X, oy + Geo.ZOOM_Y, Geo.ZOOM_S, Geo.ZOOM_S);
        a.box(CLICK_IN, ox + Geo.ZOOM_IN_X, oy + Geo.ZOOM_Y, Geo.ZOOM_S, Geo.ZOOM_S);
        float cx = ox + Geo.SNS_X + Geo.SNS_W / 2f, cy = oy + Geo.SNS_Y + Geo.SNS_H / 2f;
        for (int i = 0; i < count; i++) {
            Mark m = marks[i];
            if (m.entity == null) continue;
            a.box(CLICK_MARK + i, cx + m.x * RING - 8f, cy + m.y * RING - 8f, 16f, 16f);
        }
    }

    /** The contact behind a mark's click box, or null. */
    Entity picked(int id) {
        int i = id - CLICK_MARK;
        return i >= 0 && i < count ? marks[i].entity : null;
    }

    static String rangeText(int step) {
        float km = STEPS[step];
        return (km < 1f ? "0.5" : Integer.toString(Math.round(km))) + " KM";
    }

    // ------------------------------------------------------------------ painting, from the pod's origin

    static final String[] TRI = {"..#..", ".#.#.", "#####"}, CROSS = {".#.", "###", ".#."}, FILLED = {"..#..", ".###.", "#####", ".###.", "..#.."};
    static final String[] BOX = {"###", "#.#", "###"}, INERT_X = {"#.#", ".#.", "#.#"}, SHIP = {".#.", "###", ".#."};

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.SNS_X, y = Geo.SNS_Y, w = Geo.SNS_W, h = Geo.SNS_H;
        f.module(x, y, w, h, f.o().filaments(3));
        // The panel's dots, unlit through the glass
        for (float yy = y + 4f; yy < y + h - 1f; yy += 4f) {
            for (float xx = x + 4f; xx < x + w - 1f; xx += 4f) f.dot(xx, yy, f.o().size(1.05f).ghostOnly().k(0.9f));
        }
        float cx = x + w / 2f, cy = y + h / 2f, r = RING;
        Kit.ringDots(f, cx, cy, r * 0.5f, 4f, 1.2f, Phosphor.ZINC, Drive.DIM);
        Kit.ringDots(f, cx, cy, r, 4f, 1.2f, Phosphor.ZINC, Drive.DIM);
        if (!Float.isNaN(heading)) {
            float a = (float) Math.toRadians(heading);
            f.dotLine(cx, cy, cx + (float) Math.cos(a) * (r - 6f), cy - (float) Math.sin(a) * (r - 6f), f.o().step(3f).size(1.2f).dim());
        }
        f.sprite(cx, cy, SHIP, f.o().centred().pitch(1.6f).norm());
        for (int i = 0; i < count; i++) mark(f, marks[i], cx + marks[i].x * r, cy + marks[i].y * r);
        // The corners hold the words: hostiles top left, the range bottom left, AUTO bottom right
        String hc = hostiles > 0 ? Integer.toString(Math.min(99, hostiles)) : "";
        float hx = x + 6f + f.seg7(x + 6f, y + 5f, 12f, hc, f.o().cells(2).right().red().norm()) + 7f;
        f.legend(hx, y + 17f, "HOSTILE", f.o().red().lit(hostiles > 0).norm());
        f.legend(x + 6f, y + h - 6f, rangeText(step), f.o().norm());
        float aw = f.legendWidth("AUTO", f.o());
        f.legend(x + w - 8f - aw, y + h - 6f, "AUTO", f.o().lit(auto).dim().box());
        f.key(Geo.ZOOM_OUT_X, Geo.ZOOM_Y, Geo.ZOOM_S, Geo.ZOOM_S, "−", f.o().size(14f));
        f.key(Geo.ZOOM_IN_X, Geo.ZOOM_Y, Geo.ZOOM_S, Geo.ZOOM_S, "+", f.o().size(14f));
    }

    private static void mark(Filament f, Mark m, float px, float py) {
        if (m.rim) {
            boolean red = m.kind == HOSTILE || m.kind == MISSILE_IN;
            f.dot(px, py, f.o().size(2.4f).color(red ? Phosphor.RED : Phosphor.ZINC).norm());
        } else {
            switch (m.kind) {
                case HOSTILE: f.sprite(px, py, TRI, f.o().centred().pitch(1.6f).red().norm()); break;
                case MISSILE_IN: f.sprite(px, py, CROSS, f.o().centred().pitch(1.3f).red().full()); break;
                case MISSILE_OUT: f.sprite(px, py, CROSS, f.o().centred().pitch(1.3f).norm()); break;
                case STATION: f.sprite(px, py, Kit.STATION, f.o().centred().pitch(1.4f).norm()); break;
                case INERT: f.sprite(px, py, INERT_X, f.o().centred().pitch(1.6f).dim()); break;
                case FRIENDLY: f.sprite(px, py, FILLED, f.o().centred().pitch(1.4f).norm()); break;
                case OWN: f.sprite(px, py, BOX, f.o().centred().pitch(1.4f).norm()); break;
                default: f.sprite(px, py, Kit.NEUTRAL, f.o().centred().pitch(1.4f).dim());
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
}
