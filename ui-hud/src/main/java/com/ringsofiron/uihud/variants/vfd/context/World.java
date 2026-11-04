package com.ringsofiron.uihud.variants.vfd.context;

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

/**
 * The world overlays at the exterior seats (functional-context 9: W1, W2, W5, W6). Glyphs for every
 * marked contact by allegiance and kind; the Lock's square brackets with ticks and the Nav's diamond;
 * labels by priority (the Lock, the Nav, the objective, hostiles, then stations, other ships and
 * derelicts), the lower ones dropping out where they would overlap; a range only on the Lock, the Nav
 * and hostiles, and only where no widget on screen already shows it. Off screen or under a widget, an
 * edge marker for what matters. Under warp only the Nav's and objective's labels. The velocity
 * markers at the helm carry the frame's tag when it is not ORBIT, on the side away from the ship; the
 * lead reticle with a lock at the weapons seats and at the helm in a fight; missiles with their
 * predicted tracks and where the inbound ones will strike.
 *
 * <p>W7, the inset views: the game's magnified insets in the kit's ring of dots, clear of every
 * widget, the side columns and the racks. Each carries its Lock or Nav mark and its craft's label as
 * W1 writes it, which then has none in the view nor an edge marker; the marks under an inset are left
 * out and counted on it.
 */
final class World {
    static final int MARKS = 24, EDGES = 12, MISSILES = 16, CROSSES = 6, BOXES = 16;
    static final int EDGE_NAME = 10;
    static final float CROSS_MERGE = 24f, VEL_R = 70f;
    /** Label priorities: the first two never drop out. */
    static final int LOCK = 0, NAV = 1, OBJECTIVE = 2, HOSTILE = 3, STATION = 4, SHIP = 5, DERELICT = 6;

    static final class Mark {
        float x, y, half;
        Kit.Bracket bracket;
        String[] glyph;
        Phosphor color = Phosphor.ZINC;
        float level;
        boolean lock, hostile;
        int priority;
        String name = "", sub = "";
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
    final Edge[] edges = new Edge[EDGES];
    final Missile[] missiles = new Missile[MISSILES];
    final Cross[] crosses = new Cross[CROSSES];
    int markCount, edgeCount, missileCount, crossCount;
    boolean on;
    boolean vel, velSquare, proShown, retroShown;
    String velTag = "";
    float proX, proY, retroX, retroY, velDx, velDy;
    boolean lead, leadOnTarget;
    float leadX, leadY, shipX, shipY;
    /** The screen labels may use, and the centre's box the edge markers keep to. */
    float left, right, top, bottom, edgeL, edgeR, edgeT, edgeB;
    /** The boxes widgets take this frame (x0, y0, x1, y1, stage units): labels avoid them. */
    private final float[] boxes = new float[BOXES * 4];
    private int boxCount;
    /** Labels placed so far, to drop the lower ones that would overlap them. */
    private final float[] placed = new float[MARKS * 4];
    private int placedCount;
    /** The game's magnified insets this frame (W7), and how each reads. */
    final Insets.Inset[] insets = new Insets.Inset[Insets.MAX];
    final Optic.Look[] looks = new Optic.Look[Insets.MAX];
    int insetCount;
    private float stageHeight = Geo.H, ownRing, floor = Geo.H;
    private final Optic.Layout optic = new Optic.Layout();
    private final Rectangle[] panels = new Rectangle[BOXES + 2];

    private final Vector2 v = new Vector2(), lp = new Vector2();

    World() {
        for (int i = 0; i < MARKS; i++) marks[i] = new Mark();
        for (int i = 0; i < EDGES; i++) edges[i] = new Edge();
        for (int i = 0; i < MISSILES; i++) missiles[i] = new Missile();
        for (int i = 0; i < CROSSES; i++) crosses[i] = new Cross();
        for (int i = 0; i < Insets.MAX; i++) looks[i] = new Optic.Look();
        for (int i = 0; i < panels.length; i++) panels[i] = new Rectangle();
    }

    /** Starts a frame's widget boxes. */
    void clearBoxes() {
        boxCount = 0;
    }

    /** A widget's box in stage units: labels and marks keep out of it. */
    void box(float x, float y, float w, float h) {
        if (boxCount == BOXES || w <= 0f || h <= 0f) return;
        boxes[4 * boxCount] = x;
        boxes[4 * boxCount + 1] = y;
        boxes[4 * boxCount + 2] = x + w;
        boxes[4 * boxCount + 3] = y + h;
        boxCount++;
    }

    /**
     * What the inset views keep off (W7), as the game takes them (UI units, y up, on a stage
     * {@code width} x {@code height}): every widget's box, the status strip and the bottom rack.
     */
    void panels(Array<Rectangle> out, float width, float height) {
        if (!on) return;
        for (int i = 0; i < boxCount; i++) {
            float x0 = boxes[4 * i], y0 = boxes[4 * i + 1], x1 = boxes[4 * i + 2], y1 = boxes[4 * i + 3];
            out.add(panels[i].set(x0 - 6f, height - y1 - 6f, x1 - x0 + 12f, y1 - y0 + 12f));
        }
        out.add(panels[BOXES].set(0f, height - Geo.STRIP_B, width, Geo.STRIP_B));
        if (floor < height) out.add(panels[BOXES + 1].set(0f, 0f, width, height - floor));
    }

    /**
     * Lays out this frame's marks on a {@code width} x {@code height} stage. {@code floor} is the y
     * (down) of the bottom rack's top, {@code edges} the centre's box (l, t, r, b) the edge markers keep
     * to; {@code shows} which ranges a widget already gives: the Lock's, the Nav's, the hostiles'.
     */
    void update(HudData d, Situations s, ViewportManager views, float width, float height, float floor, float[] centre,
            boolean lockShown, boolean navShown, boolean hostilesShown, boolean missileTimesShown, int frame) {
        markCount = edgeCount = missileCount = crossCount = placedCount = insetCount = 0;
        vel = lead = false;
        TransformComponent ship = d.ctx.ship == null ? null : d.ctx.ship.getComponent(TransformComponent.class);
        on = s.exterior() && d.ctx.exteriorView && !s.fullScreen && ship != null && views != null;
        if (!on) return;
        stageHeight = height;
        this.floor = floor;
        ownRing = 0f;
        for (int i = 0; i < d.insets.count() && insetCount < Insets.MAX; i++) {
            Insets.Inset in = d.insets.get(i);
            insets[insetCount] = in;
            Optic.Look look = looks[insetCount++].clear();
            look.text = Phosphor.ZINC;
            if (in.own) {
                // The own ship goes unnamed, and quiet
                look.level = Drive.DIM;
                if (in.centered) ownRing = in.radius + Optic.RING_GAP;
            }
        }
        left = 8f;
        right = width - 8f;
        top = Geo.STRIP_B + 4f;
        bottom = floor - 6f;
        edgeL = centre[0] + 12f;
        edgeT = centre[1] + 14f;
        edgeR = centre[2] - 12f;
        edgeB = centre[3] - 14f;
        views.worldToUi(ship.x, ship.y, v);
        shipX = v.x;
        shipY = height - v.y;
        float unit = Math.max(1e-4f, views.metersPerUiUnit());
        boolean quiet = s.warp || s.docked || s.latched;
        Situations.Seat seat = s.seat;
        Objective objective = d.objectiveFrame.first();
        String goal = objective == null ? "" : objective.next;

        Array<Contact> all = d.contactFrame.contacts;
        Contact locked = null;
        float lockedX = 0f, lockedY = 0f, lockedHalf = 0f;
        // Highest priority first, so a lower label gives way to a higher one
        for (int p = LOCK; p <= DERELICT; p++) {
            for (int i = 0; i < all.size; i++) {
                Contact c = all.get(i);
                if (c.kind == Kind.MISSILE || priority(c, goal) != p) continue;
                boolean hostile = c.relation == Relation.HOSTILE;
                if (seat == Situations.Seat.GUN && !c.locked && !hostile) continue;
                views.worldToUi(c.x, c.y, v);
                float sx = v.x, sy = height - v.y;
                float half = MathUtils.clamp(c.radius / unit + 6f, 10f, 90f);
                if (c.locked) {
                    locked = c;
                    lockedX = sx;
                    lockedY = sy;
                    lockedHalf = half;
                }
                boolean showRange = c.locked ? !lockShown : c.nav ? !navShown : hostile && !hostilesShown;
                int own = insetOf(c);
                if (own >= 0) {
                    // On its inset rather than in the view or at the edge, labelled as W1 would label it
                    Optic.Look look = looks[own];
                    boolean named = !quiet || p == NAV || p == OBJECTIVE || p == LOCK && !s.warp;
                    look.name = named ? label(c, Optic.CELLS) : "";
                    look.sub = named && showRange ? Fmt.rangeText(c.range) : "";
                    look.color = hostile ? Phosphor.RED : Phosphor.ZINC;
                    look.mark = Phosphor.ZINC;
                    look.lock = c.locked;
                    look.nav = c.nav && !c.locked;
                    look.level = Drive.NORM;
                    continue;
                }
                if (!inside(sx, sy)) {
                    // One edge marker for each that matters: the Lock, the Nav, the objective, hostiles
                    boolean matters = p <= HOSTILE && (!quiet || p == NAV || p == OBJECTIVE);
                    if (matters) {
                        String name = label(c, EDGE_NAME);
                        edge(sx, sy, showRange ? name + " " + Fmt.rangeText(c.range) : name,
                                hostile ? Phosphor.RED : Phosphor.ZINC, c.locked ? Drive.FULL : Drive.NORM);
                    }
                    continue;
                }
                // Under an inset: counted there
                int under = under(sx, sy, half, p <= OBJECTIVE || !quiet);
                if (under >= 0) {
                    looks[under].others++;
                    continue;
                }
                if (markCount == MARKS) continue;
                Mark m = marks[markCount++];
                m.x = sx;
                m.y = sy;
                m.half = half;
                m.lock = c.locked;
                m.hostile = hostile;
                m.priority = p;
                m.glyph = null;
                m.bracket = null;
                m.color = hostile ? Phosphor.RED : Phosphor.ZINC;
                m.sub = showRange ? Fmt.rangeText(c.range) : "";
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
                    m.level = p == OBJECTIVE ? Drive.NORM : Drive.DIM;
                }
                m.name = label(c, 14);
                // Under warp, docked or latched only the Nav's and the objective's labels
                if (quiet && p != NAV && p != OBJECTIVE && !(p == LOCK && !s.warp)) {
                    m.name = "";
                    m.sub = "";
                }
                if (!m.name.isEmpty() && !place(m) && p > NAV) {
                    m.name = "";
                    m.sub = "";
                }
            }
        }

        // Missiles: their glyphs and tracks, and where the inbound ones will strike
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack t = tracks.get(i);
            boolean incoming = t.side == Side.INCOMING;
            if (!incoming && seat == Situations.Seat.GUN) continue;
            views.worldToUi(t.x, t.y, v);
            float mx = v.x, my = height - v.y;
            views.worldToUi(t.pathX, t.pathY, v);
            float px = v.x, py = height - v.y;
            if (!inside(mx, my)) {
                boolean timed = !missileTimesShown && !Float.isNaN(t.timeToImpact) && t.timeToImpact >= 0f;
                if (incoming) edge(mx, my, timed ? "MSL " + Fmt.secs(t.timeToImpact) + " S" : "MSL", Phosphor.RED, Drive.FULL);
            } else if (missileCount < MISSILES) {
                Missile m = missiles[missileCount++];
                m.x = mx;
                m.y = my;
                m.px = px;
                m.py = py;
                m.incoming = incoming;
                m.track = t.pathX != t.x || t.pathY != t.y;
            }
            if (incoming && !Float.isNaN(t.timeToImpact) && inside(px, py)) cross(px, py);
        }

        // W2: prograde and retrograde in H1's frame, its tag when it is not ORBIT
        FlightStatus fl = d.flightStatus;
        float speed = (float) Math.sqrt(fl.vx * fl.vx + fl.vy * fl.vy);
        if (seat == Situations.Seat.HELM && fl.present && speed > 0.5f && !s.docked && !s.latched) {
            velDx = fl.vx / speed;
            velDy = -fl.vy / speed;
            // Outside the own ship's inset when it sits over the ship
            float r = Math.max(VEL_R, ownRing + 16f);
            proX = shipX + velDx * r;
            proY = shipY + velDy * r;
            retroX = shipX - velDx * r;
            retroY = shipY - velDy * r;
            // Not on another craft's magnified picture, where it would read as a point on it
            proShown = !onOtherInset(proX, proY);
            retroShown = !onOtherInset(retroX, retroY);
            velSquare = frame != Flight.ORBIT;
            velTag = frame == Flight.ORBIT ? "" : Flight.FRAMES[frame];
            vel = true;
        }

        // W5: the lead reticle with a lock, at the weapons seats and at the helm in a fight
        boolean weapons = seat != Situations.Seat.HELM || s.combat;
        if (weapons && !quiet && locked != null && d.targeting != null && d.targeting.getLeadPoint(lp)) {
            views.worldToUi(lp.x, lp.y, v);
            leadX = v.x;
            leadY = height - v.y;
            // Never on an inset's magnified picture, where it would read as a point on it
            lead = inside(leadX, leadY) && under(leadX, leadY, 9f, false) < 0;
            leadOnTarget = Math.abs(leadX - lockedX) < lockedHalf && Math.abs(leadY - lockedY) < lockedHalf;
        }
        settleEdges();
    }

    /** A contact's label priority: the Lock, the Nav, the objective, hostiles, stations, other ships, derelicts. */
    static int priority(Contact c, String goal) {
        if (c.locked) return LOCK;
        if (c.nav) return NAV;
        if (!goal.isEmpty() && c.label.equalsIgnoreCase(goal)) return OBJECTIVE;
        if (c.relation == Relation.HOSTILE) return HOSTILE;
        if (c.kind == Kind.STATION) return STATION;
        if (c.kind == Kind.DERELICT) return DERELICT;
        return SHIP;
    }

    private static String label(Contact c, int cells) {
        return Fmt.name(c.label, cells);
    }

    /** Which of this frame's insets is onto {@code c}'s craft, or -1. */
    private int insetOf(Contact c) {
        if (c.entity == null) return -1;
        for (int i = 0; i < insetCount; i++) if (insets[i].entity == c.entity) return i;
        return -1;
    }

    /** Which inset a mark at {@code x, y}, {@code half} across with a label under it or not, falls under, or -1. */
    private int under(float x, float y, float half, boolean labelled) {
        float w = Math.max(2f * half, labelled ? 14f * 12f : 0f), h = 2f * half + (labelled ? 40f : 0f);
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

    /** Places {@code m}'s label if it overlaps none placed before it; returns whether it did. */
    private boolean place(Mark m) {
        float below = m.bracket == null ? 10f : m.half + 10f;
        float w = Math.max(m.name.length(), m.sub.length()) * 12f, h = m.sub.isEmpty() ? 16f : 35f;
        float x0 = m.x - w / 2f, y0 = m.y + below, x1 = x0 + w, y1 = y0 + h;
        for (int i = 0; i < placedCount; i++) {
            if (x0 < placed[4 * i + 2] && x1 > placed[4 * i] && y0 < placed[4 * i + 3] && y1 > placed[4 * i + 1]) return false;
        }
        if (placedCount < MARKS) {
            placed[4 * placedCount] = x0;
            placed[4 * placedCount + 1] = y0;
            placed[4 * placedCount + 2] = x1;
            placed[4 * placedCount + 3] = y1;
            placedCount++;
        }
        return true;
    }

    private static final char[] DIRS = {'u', 'd', 'l', 'r'};
    private final int[] onEdge = new int[EDGES];
    private final float[] along = new float[EDGES], reach = new float[EDGES];

    /** Spreads the markers on each edge along it, so two things off screen the same way both read. */
    void settleEdges() {
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

    /** Whether a point is in the world labels may use: on screen, between the racks, clear of every widget. */
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

    /** An edge marker where the ray from the centre's middle towards {@code x, y} leaves the centre. */
    void edge(float x, float y, String text, Phosphor color, float level) {
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

    void paint(Filament f) {
        if (!on) return;
        for (int i = 0; i < insetCount; i++) Optic.paint(f, insets[i], stageHeight, looks[i], optic);
        if (vel) {
            if (proShown) Kit.velMark(f, true, proX, proY, velSquare, Drive.NORM);
            if (retroShown) Kit.velMark(f, false, retroX, retroY, velSquare, Drive.DIM);
            if (!velTag.isEmpty()) {
                // The frame's tag beyond each marker, on the side away from the ship
                if (proShown) tag(f, proX + velDx * 20f, proY + velDy * 20f, Drive.NORM);
                if (retroShown) tag(f, retroX - velDx * 20f, retroY - velDy * 20f, Drive.DIM);
            }
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
            // The lead sits on the target at fight ranges: a soft disc under it keeps the two apart (W5)
            if (leadOnTarget) f.scrim(leadX - 9f, leadY - 9f, 18f, 18f, 9f, 0.45f);
            Kit.reticle(f, leadX, leadY, leadOnTarget, Drive.NORM);
        }
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            Kit.edge(f, e.x, e.y, e.dir, e.text, e.color, e.level);
        }
    }

    private void tag(Filament f, float x, float y, float level) {
        float w = velTag.length() * 6f * 1.5f - 1.5f;
        f.dm(x - w / 2f, y - 5.25f, velTag, f.o().pitch(1.5f).proj().level(level).noGhost());
    }

    private static void mark(Filament f, Mark m) {
        float below;
        if (m.bracket == null) {
            Kit.sprite(f, m.glyph, m.x, m.y, 2f, m.color, m.level);
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
        if (!m.name.isEmpty()) Kit.nameplate(f, m.x, m.y + below, m.name, m.lock ? Drive.NORM : m.level);
        if (!m.sub.isEmpty()) Kit.nameplate(f, m.x, m.y + below + 19f, m.sub, Drive.DIM);
    }
}
