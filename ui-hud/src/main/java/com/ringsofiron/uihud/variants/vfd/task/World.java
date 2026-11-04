package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * OVL, the world overlays outside (functional-task 6, OVL). Shape says what a thing is and treatment
 * its relation (9.4): the lock's square brackets with ticks, a hostile's red triangles, the navigation
 * target's diamond, a station's square and everything else's diamond glyph. Labels only for stations,
 * the lock, the navigation target, hostiles within 50 km and what a contract is about; glyphs within
 * 24 units merge into one with a count. Edge arrows only for hostiles, inbound missiles, the lock, the
 * navigation target and what a contract is about, and they carry names, never numbers: the time to
 * impact is the band's. At the helm prograde and retrograde in FLT's frame, square when the frame is
 * not the orbit; the next burn's arrow with its label; with a lock the lead reticle; missiles with
 * their tracks and where the inbound ones will strike. In rails warp, docked or latched, the markers only.
 *
 * <p>The game's magnified insets (style-vfd 8) carry the mark and name of their craft, which then has
 * none in the view nor an edge arrow; marks under an inset are left out and counted on it, as the
 * classic bubbles count the names they hide.
 */
final class World {
    static final int MARKS = 32, EDGES = 12, MISSILES = 16, CROSSES = 6, BOXES = 16;
    static final int NAME = 14, EDGE_NAME = 10;
    /** Hostiles nearer than this are labelled, m. */
    static final float HOSTILE_LABEL = 50_000f;
    static final float MERGE = 24f, CROSS_MERGE = 24f, VEL_R = 70f, BURN_R = 96f;

    static final class Mark {
        float x, y, half;
        Kit.Bracket bracket;
        String[] glyph;
        Phosphor color = Phosphor.ZINC;
        float level;
        boolean lock, hostile;
        int merged;
        String name = "";
    }

    static final class Edge {
        float x, y;
        char dir;
        String text = "";
        Phosphor color = Phosphor.ZINC;
        float level;
    }

    static final class Missile {
        float x, y, px, py;
        boolean incoming, track;
    }

    static final class Cross {
        float x, y;
        int count;
    }

    final Mark[] marks = new Mark[MARKS];
    /** The game's magnified insets this frame, and how each reads. */
    final Insets.Inset[] insets = new Insets.Inset[Insets.MAX];
    final Optic.Look[] looks = new Optic.Look[Insets.MAX];
    int insetCount;
    final Edge[] edges = new Edge[EDGES];
    final Missile[] missiles = new Missile[MISSILES];
    final Cross[] crosses = new Cross[CROSSES];
    int markCount, edgeCount, missileCount, crossCount;
    boolean on;
    boolean vel, velSquare, proShown, retroShown, burn, lead, leadOnTarget;
    float proX, proY, retroX, retroY, burnX, burnY, burnDx, burnDy, burnFrom = 30f, leadX, leadY, shipX, shipY;
    String burnLabel = "";
    float left, right, top, bottom, edgeL, edgeR, edgeT, edgeB;
    private final float[] boxes = new float[BOXES * 4];
    private int boxCount;
    private final Rectangle[] panels = new Rectangle[BOXES + EDGES];
    private final Vector2 v = new Vector2(), lp = new Vector2();
    private final Optic.Layout optic = new Optic.Layout();
    /** How far the player's ship reaches from its middle on the stage, and its box for the game. */
    private float shipReach;
    private final Rectangle shipPanel = new Rectangle();
    private float stageHeight = Geo.H;

    World() {
        for (int i = 0; i < MARKS; i++) marks[i] = new Mark();
        for (int i = 0; i < EDGES; i++) edges[i] = new Edge();
        for (int i = 0; i < MISSILES; i++) missiles[i] = new Missile();
        for (int i = 0; i < CROSSES; i++) crosses[i] = new Cross();
        for (int i = 0; i < Insets.MAX; i++) looks[i] = new Optic.Look();
        for (int i = 0; i < panels.length; i++) panels[i] = new Rectangle();
    }

    void clearBoxes() {
        boxCount = 0;
    }

    /** A widget's box on the stage: labels and marks keep out of it. */
    void box(float x, float y, float w, float h) {
        if (boxCount == BOXES || w <= 0f || h <= 0f) return;
        boxes[4 * boxCount] = x;
        boxes[4 * boxCount + 1] = y;
        boxes[4 * boxCount + 2] = x + w;
        boxes[4 * boxCount + 3] = y + h;
        boxCount++;
    }

    /**
     * The widgets' boxes, the player's ship and the edge arrows as the game keeps its insets off them:
     * UI units, y up, on a stage {@code height} tall.
     */
    void panels(Array<Rectangle> out, float height) {
        for (int i = 0; i < boxCount; i++) {
            float x0 = boxes[4 * i], y0 = boxes[4 * i + 1], x1 = boxes[4 * i + 2], y1 = boxes[4 * i + 3];
            out.add(panels[i].set(x0, height - y1, x1 - x0, y1 - y0));
        }
        // The player's ship, so no other craft's magnified picture covers it
        if (on && shipReach > 0f) {
            float h = shipReach + 6f;
            out.add(shipPanel.set(shipX - h, height - shipY - h, 2f * h, 2f * h));
        }
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            Rectangle r = Kit.edgeBox(e.x, e.y, e.dir, e.text, panels[BOXES + i]);
            out.add(r.set(r.x - 4f, height - r.y - r.height - 4f, r.width + 8f, r.height + 8f));
        }
    }

    /**
     * Lays out this frame's overlays on a {@code width} x {@code height} stage, the edge arrows on the
     * box {@code l, t, r, b}; {@code burnShown} whether a burn is near enough to draw its arrow.
     */
    void update(HudData d, Posts p, ViewportManager views, float width, float height, float l, float t, float r, float b, boolean burnShown) {
        markCount = edgeCount = missileCount = crossCount = insetCount = 0;
        vel = burn = lead = false;
        TransformComponent ship = d.ctx.ship == null ? null : d.ctx.ship.getComponent(TransformComponent.class);
        on = p.outside() && d.ctx.exteriorView && !p.screen && ship != null && views != null;
        if (!on) return;
        stageHeight = height;
        float ownRing = 0f;
        for (int i = 0; i < d.insets.count() && insetCount < Insets.MAX; i++) {
            Insets.Inset in = d.insets.get(i);
            insets[insetCount] = in;
            Optic.Look look = looks[insetCount++].clear();
            // The player's own ship goes unnamed, and quiet
            if (in.own) {
                look.level = Drive.DIM;
                if (in.centered) ownRing = in.radius + Optic.RING_GAP;
            }
        }
        left = 8f;
        right = width - 8f;
        top = 8f;
        bottom = height - 8f;
        edgeL = l;
        edgeT = t;
        edgeR = r;
        edgeB = b;
        views.worldToUi(ship.x, ship.y, v);
        shipX = v.x;
        shipY = height - v.y;
        float unit = Math.max(1e-4f, views.metersPerUiUnit());
        shipReach = Optic.hullReach(d.ctx.ship, unit);
        boolean quiet = p.rails || p.docked || p.latched;
        Array<Objective> objectives = d.objectiveFrame.objectives;

        Array<Contact> all = d.contactFrame.contacts;
        Contact locked = null;
        float lockedX = 0f, lockedY = 0f, lockedHalf = 0f;
        // Labelled marks first, so a glyph merges into its neighbours and never into a label
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.size; i++) {
                Contact c = all.get(i);
                if (c.kind == Kind.MISSILE) continue;
                boolean hostile = c.relation == Relation.HOSTILE;
                boolean contract = contract(c.label, objectives, d.navReadout, c.nav);
                boolean labelled = c.locked || c.nav || c.kind == Kind.STATION || contract
                        || hostile && !Float.isNaN(c.range) && c.range <= HOSTILE_LABEL;
                if (labelled != (pass == 0)) continue;
                views.worldToUi(c.x, c.y, v);
                float sx = v.x, sy = height - v.y;
                float half = MathUtils.clamp(c.radius / unit + 6f, 10f, 90f);
                if (c.locked) {
                    locked = c;
                    lockedX = sx;
                    lockedY = sy;
                    lockedHalf = half;
                }
                int own = insetOf(c);
                if (own >= 0) {
                    // Marked and named on its inset rather than in the view or at the edge: the lock's
                    // zinc brackets round a hostile's red ring, as round its red triangles here
                    Optic.Look look = looks[own];
                    look.name = labelled ? Fmt.name(c.label, Optic.CELLS) : "";
                    look.color = hostile ? Phosphor.RED : Phosphor.ZINC;
                    look.mark = Phosphor.ZINC;
                    look.lock = c.locked;
                    look.nav = c.nav && !c.locked;
                    look.level = labelled ? Drive.NORM : Drive.DIM;
                    continue;
                }
                if (!inside(sx, sy)) {
                    // Arrows only for what matters, by name
                    if (c.locked || c.nav || hostile || contract) {
                        edge(sx, sy, Fmt.name(c.label, EDGE_NAME), hostile ? Phosphor.RED : Phosphor.ZINC, c.locked ? Drive.FULL : Drive.NORM);
                    }
                    continue;
                }
                // Under an inset: counted there, as the classic bubbles count the names they hide
                int under = under(sx, sy, half, labelled ? Math.min(NAME, Fmt.name(c.label, NAME).length()) : 0);
                if (under >= 0) {
                    looks[under].others++;
                    continue;
                }
                if (!labelled && merge(sx, sy)) continue;
                if (markCount == MARKS) continue;
                Mark m = marks[markCount++];
                m.x = sx;
                m.y = sy;
                m.half = half;
                m.lock = c.locked;
                m.hostile = hostile;
                m.merged = 1;
                m.glyph = null;
                m.bracket = null;
                m.color = hostile ? Phosphor.RED : Phosphor.ZINC;
                if (c.locked) {
                    m.bracket = Kit.Bracket.SQUARE;
                    m.level = Drive.FULL;
                } else if (c.nav) {
                    m.bracket = Kit.Bracket.DIAMOND;
                    m.level = Drive.NORM;
                } else if (hostile && !quiet) {
                    m.bracket = Kit.Bracket.TRI;
                    m.level = Drive.NORM;
                } else {
                    m.glyph = c.kind == Kind.STATION ? Kit.STATION : Kit.NEUTRAL;
                    m.level = labelled ? Drive.NORM : Drive.DIM;
                }
                m.name = labelled ? Fmt.name(c.label, NAME) : "";
            }
        }
        if (quiet) {
            settleEdges();
            return;
        }

        // Missiles: their glyphs and tracks, and where the inbound ones will strike
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack m = tracks.get(i);
            boolean incoming = m.side == Side.INCOMING;
            views.worldToUi(m.x, m.y, v);
            float mx = v.x, my = height - v.y;
            views.worldToUi(m.pathX, m.pathY, v);
            float px = v.x, py = height - v.y;
            if (!inside(mx, my)) {
                if (incoming) edge(mx, my, "MISSILE", Phosphor.RED, Drive.FULL);
            } else if (missileCount < MISSILES) {
                Missile s = missiles[missileCount++];
                s.x = mx;
                s.y = my;
                s.px = px;
                s.py = py;
                s.incoming = incoming;
                s.track = m.pathX != m.x || m.pathY != m.y;
            }
            if (incoming && !Float.isNaN(m.timeToImpact) && inside(px, py)) cross(px, py);
        }

        // Prograde and retrograde in FLT's frame, at the helm
        FlightStatus fl = d.flightStatus;
        float speed = (float) Math.sqrt(fl.vx * fl.vx + fl.vy * fl.vy);
        // Round the own ship's inset when it sits over the ship
        float velR = Math.max(VEL_R, ownRing + 16f);
        burnFrom = Math.max(30f, ownRing + 4f);
        if (p.post == Posts.Post.HELM && fl.present && speed > 0.5f) {
            float dx = fl.vx / speed, dy = -fl.vy / speed;
            proX = shipX + dx * velR;
            proY = shipY + dy * velR;
            retroX = shipX - dx * velR;
            retroY = shipY - dy * velR;
            velSquare = fl.frame != Frame.ORBIT && fl.frame != Frame.NONE;
            // Not on another craft's magnified picture, where it would read as a point on it
            proShown = !onOtherInset(proX, proY);
            retroShown = !onOtherInset(retroX, retroY);
            vel = true;
        }
        // The next burn's arrow, labelled, while BURN shows
        NavReadout n = d.navReadout;
        if (p.post == Posts.Post.HELM && burnShown && !Float.isNaN(n.burnHeadingDeg)) {
            float a = n.burnHeadingDeg * MathUtils.degreesToRadians;
            burnDx = MathUtils.cos(a);
            burnDy = -MathUtils.sin(a);
            burnX = shipX + burnDx * Math.max(BURN_R, burnFrom + 40f);
            burnY = shipY + burnDy * Math.max(BURN_R, burnFrom + 40f);
            burnLabel = "BURN" + (n.burnNumber > 0 ? " " + n.burnNumber : "");
            burn = true;
        }
        // The lead reticle for the gun in hand, with a lock, at gunnery and at the helm in a fight
        boolean weapons = p.post == Posts.Post.GUNNERY || p.combat;
        if (weapons && locked != null && d.targeting != null && d.targeting.getLeadPoint(lp)) {
            views.worldToUi(lp.x, lp.y, v);
            leadX = v.x;
            leadY = height - v.y;
            // Not over an inset's magnified picture, where it would read as a point on it
            lead = inside(leadX, leadY) && under(leadX, leadY, 9f, 0) < 0;
            leadOnTarget = Math.abs(leadX - lockedX) < lockedHalf && Math.abs(leadY - lockedY) < lockedHalf;
        }
        settleEdges();
    }

    /**
     * Whether a contact is what a contract is about: the navigation target's contract, a waypoint the
     * game tags "[contract]", or named in one.
     */
    static boolean contract(String label, Array<Objective> objectives, NavReadout n, boolean nav) {
        if (nav && !n.contract.isEmpty()) return true;
        if (label == null || label.isEmpty()) return false;
        if (label.trim().toLowerCase(Locale.ROOT).endsWith("[contract]")) return true;
        String bare = Fmt.bare(label), l = bare.toUpperCase(Locale.ROOT);
        if (l.isEmpty()) return false;
        for (int i = 0; i < objectives.size; i++) {
            Objective o = objectives.get(i);
            if (o.title.toUpperCase(Locale.ROOT).contains(l) || o.station.equalsIgnoreCase(bare)) return true;
        }
        return false;
    }

    /** Which of this frame's insets is onto {@code c}'s craft, or -1. */
    private int insetOf(Contact c) {
        if (c.entity == null) return -1;
        for (int i = 0; i < insetCount; i++) if (insets[i].entity == c.entity) return i;
        return -1;
    }

    /** Which inset a mark at {@code x, y}, {@code half} across with a name of {@code cells} under it, falls under, or -1. */
    private int under(float x, float y, float half, int cells) {
        float w = Math.max(2f * half, cells * 12f), h = 2f * half + (cells > 0 ? 28f : 0f);
        for (int i = 0; i < insetCount; i++) {
            if (Optic.covers(insets[i], stageHeight, x - w / 2f, y - half, w, h)) return i;
        }
        return -1;
    }

    /** Whether a velocity marker at {@code x, y} falls on the inset of a craft other than the own ship. */
    private boolean onOtherInset(float x, float y) {
        for (int i = 0; i < insetCount; i++) {
            if (!insets[i].own && Optic.covers(insets[i], stageHeight, x - 10f, y - 10f, 20f, 20f)) return true;
        }
        return false;
    }

    /** Folds an unlabelled glyph into one within 24 units; returns whether it did. */
    private boolean merge(float x, float y) {
        for (int i = 0; i < markCount; i++) {
            Mark m = marks[i];
            if (m.glyph == null || !m.name.isEmpty()) continue;
            if (Math.abs(m.x - x) < MERGE && Math.abs(m.y - y) < MERGE) {
                m.merged++;
                return true;
            }
        }
        return false;
    }

    private static final char[] DIRS = {'u', 'd', 'l', 'r'};
    private final int[] onEdge = new int[EDGES];
    private final float[] along = new float[EDGES], reach = new float[EDGES];

    /** Spreads the arrows on each edge along it, so two things off screen the same way both read. */
    private void settleEdges() {
        for (char dir : DIRS) {
            boolean across = dir == 'u' || dir == 'd';
            int n = 0;
            for (int i = 0; i < edgeCount; i++) {
                if (edges[i].dir != dir) continue;
                float p = across ? edges[i].x : edges[i].y;
                int j = n++;
                while (j > 0 && along[j - 1] > p) {
                    along[j] = along[j - 1];
                    onEdge[j] = onEdge[j - 1];
                    j--;
                }
                along[j] = p;
                onEdge[j] = i;
            }
            if (n < 2) continue;
            for (int k = 0; k < n; k++) reach[k] = Kit.edgeHalf(dir, edges[onEdge[k]].text);
            float lo = across ? edgeL - 12f : edgeT - 3f, hi = across ? edgeR + 12f : edgeB + 3f;
            Kit.spread(along, reach, n, lo, hi, across ? 12f : 3f);
            for (int k = 0; k < n; k++) {
                Edge e = edges[onEdge[k]];
                if (across) e.x = along[k];
                else e.y = along[k];
            }
        }
    }

    /** On screen and clear of every widget. */
    boolean inside(float x, float y) {
        if (x < left || x > right || y < top || y > bottom) return false;
        for (int i = 0; i < boxCount; i++) {
            if (x > boxes[4 * i] && x < boxes[4 * i + 2] && y > boxes[4 * i + 1] && y < boxes[4 * i + 3]) return false;
        }
        return true;
    }

    private void cross(float x, float y) {
        for (int i = 0; i < crossCount; i++) {
            Cross c = crosses[i];
            if (Math.abs(c.x - x) < CROSS_MERGE && Math.abs(c.y - y) < CROSS_MERGE) {
                c.count++;
                return;
            }
        }
        if (crossCount == CROSSES) return;
        Cross c = crosses[crossCount++];
        c.x = x;
        c.y = y;
        c.count = 1;
    }

    /** An edge arrow where the ray from the box's middle towards {@code x, y} leaves the box. */
    private void edge(float x, float y, String text, Phosphor color, float level) {
        if (edgeCount == EDGES) return;
        float l = edgeL, r = edgeR, t = edgeT, b = edgeB;
        if (r <= l || b <= t) return;
        float cx = (l + r) / 2f, cy = (t + b) / 2f, dx = x - cx, dy = y - cy;
        if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) return;
        float kx = dx != 0f ? ((dx > 0 ? r : l) - cx) / dx : Float.MAX_VALUE;
        float ky = dy != 0f ? ((dy > 0 ? b : t) - cy) / dy : Float.MAX_VALUE;
        float k = Math.min(kx, ky);
        Edge e = edges[edgeCount++];
        e.x = cx + dx * k;
        e.y = cy + dy * k;
        e.dir = kx <= ky ? (dx > 0 ? 'r' : 'l') : (dy > 0 ? 'd' : 'u');
        e.text = text;
        e.color = color;
        e.level = level;
    }

    // ------------------------------------------------------------------ painting, in stage units

    void paint(Filament f) {
        if (!on) return;
        for (int i = 0; i < insetCount; i++) Optic.paint(f, insets[i], stageHeight, looks[i], optic);
        if (vel) {
            if (proShown) Kit.velMark(f, true, proX, proY, velSquare, Drive.NORM);
            if (retroShown) Kit.velMark(f, false, retroX, retroY, velSquare, Drive.DIM);
        }
        if (burn) {
            f.dotLine(shipX + burnDx * burnFrom, shipY + burnDy * burnFrom, burnX, burnY, f.o().proj().step(5f).size(1.6f).norm());
            Kit.sprite(f, Kit.CHEVRON_U, burnX, burnY, 2f, Phosphor.ZINC, Drive.NORM);
            Kit.nameplate(f, burnX + burnDx * 16f, burnY + burnDy * 16f - 7f, burnLabel, Drive.NORM);
        }
        for (int i = 0; i < missileCount; i++) {
            Missile m = missiles[i];
            Phosphor c = m.incoming ? Phosphor.RED : Phosphor.ZINC;
            if (m.track) f.dotLine(m.x, m.y, m.px, m.py, f.o().proj().step(7f).size(1.3f).dim().color(c));
            Kit.missile(f, m.x, m.y, c);
        }
        for (int i = 0; i < crossCount; i++) {
            Cross c = crosses[i];
            Kit.cross(f, c.x, c.y);
            if (c.count > 1) f.dm(c.x + 14f, c.y - 26f, Integer.toString(c.count), f.o().pitch(2f).proj().red().full().noGhost());
        }
        for (int i = 0; i < markCount; i++) mark(f, marks[i]);
        if (lead) {
            if (leadOnTarget) f.scrim(leadX - 9f, leadY - 9f, 18f, 18f, 9f, 0.45f);
            Kit.reticle(f, leadX, leadY, leadOnTarget, Drive.NORM);
        }
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            Kit.edge(f, e.x, e.y, e.dir, e.text, e.color, e.level);
        }
    }

    private static void mark(Filament f, Mark m) {
        float below;
        if (m.bracket == null) {
            Kit.sprite(f, m.glyph, m.x, m.y, 2f, m.color, m.level);
            if (m.merged > 1) f.dm(m.x + 9f, m.y - 14f, Integer.toString(Math.min(99, m.merged)), f.o().pitch(1.5f).proj().dim().noGhost());
            below = 10f;
        } else if (m.lock) {
            // A hostile lock keeps its red triangles inside the lock's zinc brackets
            if (m.hostile) Kit.brackets(f, m.x, m.y, m.half, Kit.Bracket.TRI, 2f, 5, Phosphor.RED, Drive.NORM, false);
            float h = m.half + (m.hostile ? 10f : 0f);
            Kit.brackets(f, m.x, m.y, h, Kit.Bracket.SQUARE, 2f, 6, Phosphor.ZINC, Drive.FULL, true);
            below = h + 14f;
        } else if (m.bracket == Kit.Bracket.DIAMOND) {
            Kit.brackets(f, m.x, m.y, m.half, Kit.Bracket.DIAMOND, 2f, 6, Phosphor.ZINC, m.level, false);
            below = m.half * 1.25f + 8f;
        } else {
            Kit.brackets(f, m.x, m.y, m.half, m.bracket, 2f, 5, m.color, m.level, false);
            below = m.half + 8f;
        }
        if (!m.name.isEmpty()) Kit.nameplate(f, m.x, m.y + below, m.name, m.hostile ? Phosphor.RED : Phosphor.ZINC, m.lock ? Drive.NORM : m.level);
    }
}
