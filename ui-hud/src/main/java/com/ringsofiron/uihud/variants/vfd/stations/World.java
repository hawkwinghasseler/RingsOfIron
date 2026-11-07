package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * The projected symbology over the world (functional-stations 11): the seat's projector, so it is up
 * only while seated and looking out, and bare in a blackout. Brackets and labels by 11.1 and 11.2
 * (hostile red triangles, the lock's square brackets with ticks, the nav target's diamond, neutral
 * traffic as a glyph), velocity markers at the helm, the lead reticle with a lock, missiles with
 * their tracks and impact crosses, and edge markers for what matters off screen or under the band
 * and the top column (rule 4 of 6.3). Burn arrows and fire arcs stay the game's own.
 *
 * <p>The optics (11.3) are the game's magnified insets in the kit's ring of dots: the lock's, the nav
 * target's at the helm only, and the own ship's only once its hull is under 12 pixels. Each carries
 * its craft's mark and name, which then has none in the view nor an edge marker; marks under an inset
 * are left out and counted on it.
 */
final class World {
    static final int MARKS = 24, EDGES = 12, MISSILES = 16, CROSSES = 6;
    /** An edge marker's name: "FREIGHTER" whole, "RAIDER 6". */
    static final int EDGE_NAME = 10;
    /** Impact crosses closer than this merge into one with a count: a cross is 16 units across. */
    static final float CROSS_MERGE = 24f;
    /** Units from the ship's centre to its velocity markers. */
    static final float VEL_R = 70f;
    /** The own ship's optic only once its hull is shorter than this on screen, pixels (11.3). */
    static final float OWN_OPTIC_PX = 12f;

    static final class Mark {
        float x, y, half;
        /** Brackets, or null for a glyph only. */
        Kit.Bracket bracket;
        String[] glyph;
        Phosphor color = Phosphor.ZINC;
        float level;
        boolean lock, hostile;
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
    float proX, proY, retroX, retroY;
    boolean lead, leadOnTarget, leadLine;
    float leadX, leadY, shipX, shipY;
    /** Where labels may sit: the screen less its margins, the top column and the band. */
    float left, right, top, bottom, colX0, colX1, colBottom;
    /**
     * Set before each {@link #update}: the top column's offset when a side panel has moved it (NaN: the
     * grid's), the panel's left on the stage (the world ends there), and whether a panel covers the world.
     */
    float column = Float.NaN, panelLeft = Float.POSITIVE_INFINITY;
    boolean hidden;
    private float stageWidth = Geo.W;
    private final Rectangle panelBox = new Rectangle();
    /** The game's magnified insets this frame, and how each reads. */
    final Insets.Inset[] insets = new Insets.Inset[Insets.MAX];
    final Optic.Look[] looks = new Optic.Look[Insets.MAX];
    int insetCount;
    /** The seat, and whether the own ship is drawn small enough for its optic (11.3). */
    Presence seat;
    boolean ownTiny;
    private float stageHeight = Geo.H, ownRing;
    private final Optic.Layout optic = new Optic.Layout();
    /** How far the player's ship reaches from its middle on the stage, and its box for the game. */
    private float shipReach;
    private final Rectangle shipPanel = new Rectangle();
    private final Rectangle[] edgePanels = new Rectangle[EDGES];

    private final Vector2 v = new Vector2(), lp = new Vector2();

    World() {
        for (int i = 0; i < MARKS; i++) marks[i] = new Mark();
        for (int i = 0; i < EDGES; i++) edges[i] = new Edge();
        for (int i = 0; i < MISSILES; i++) missiles[i] = new Missile();
        for (int i = 0; i < CROSSES; i++) crosses[i] = new Cross();
        for (int i = 0; i < Insets.MAX; i++) looks[i] = new Optic.Look();
        for (int i = 0; i < EDGES; i++) edgePanels[i] = new Rectangle();
    }

    /**
     * Lays out this frame's marks on a {@code width} x {@code height} stage. {@code topX} is the top
     * column's offset; {@code floor} the y (down) above which the world shows: the band's top, the
     * gun plate's, or the stage's bottom.
     */
    void update(HudData d, ViewportManager views, float width, float height, float topX, float floor, boolean blackout) {
        markCount = edgeCount = missileCount = crossCount = insetCount = 0;
        vel = lead = ownTiny = false;
        Presence seat = d.ctx.presence;
        this.seat = seat;
        boolean seated = seat == Presence.HELM || seat == Presence.WEAPONS || seat == Presence.GUN;
        TransformComponent ship = d.ctx.ship == null ? null : d.ctx.ship.getComponent(TransformComponent.class);
        on = !hidden && seated && d.ctx.exteriorView && !blackout && ship != null && views != null;
        if (!on) return;
        stageHeight = height;
        ShipGridComponent grid = d.ctx.ship.getComponent(ShipGridComponent.class);
        float hull = grid == null ? 0f : Math.max(grid.width, grid.height) * grid.tileSize;
        ownTiny = hull > 0f && hull / Math.max(1e-4f, views.metersPerPixel()) < OWN_OPTIC_PX;
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
        stageWidth = width;
        left = 16f;
        right = Math.min(width - 16f, panelLeft - 8f);
        top = 16f;
        bottom = floor - 8f;
        colX0 = (Float.isNaN(column) ? topX : column) + Geo.TOP_X;
        colX1 = colX0 + Geo.TOP_W;
        colBottom = Geo.TICKER_Y + 60f + 6f;
        views.worldToUi(ship.x, ship.y, v);
        shipX = v.x;
        shipY = height - v.y;
        float unit = Math.max(1e-4f, views.metersPerUiUnit());
        shipReach = Optic.hullReach(d.ctx.ship, unit);
        boolean green = d.ctx.condition == Condition.GREEN;

        // Contacts: brackets and labels by seat and condition (11.1, 11.2)
        Array<Contact> all = d.contactFrame.contacts;
        Contact locked = null;
        float lockedX = 0f, lockedY = 0f, lockedHalf = 0f;
        for (int i = 0; i < all.size; i++) {
            Contact c = all.get(i);
            if (c.kind == Kind.MISSILE) continue;
            boolean hostile = c.relation == Relation.HOSTILE;
            boolean nav = c.nav && seat != Presence.GUN;
            if (seat == Presence.GUN && !c.locked && !hostile) continue;
            if (seat == Presence.WEAPONS && !c.locked && !hostile && !nav && c.kind != Kind.STATION) continue;
            views.worldToUi(c.x, c.y, v);
            float sx = v.x, sy = height - v.y;
            float half = MathUtils.clamp(c.radius / unit + 6f, 10f, 90f);
            if (c.locked) {
                locked = c;
                lockedX = sx;
                lockedY = sy;
                lockedHalf = half;
            }
            boolean bracketed = c.locked || nav || hostile && (!green || seat != Presence.HELM);
            int own = insetOf(c);
            if (own >= 0) {
                // On its optic rather than in the view or at the edge: named as its mark would be, the
                // lock by name only, its zinc brackets round a hostile's red ring
                Optic.Look look = looks[own];
                look.name = Fmt.name(c.label, Optic.CELLS);
                look.color = hostile ? Phosphor.RED : Phosphor.ZINC;
                look.mark = Phosphor.ZINC;
                look.lock = c.locked;
                look.nav = nav && !c.locked;
                look.level = Drive.NORM;
                continue;
            }
            if (!inside(sx, sy)) {
                // Off screen, or under the band or the top column: an edge marker for what matters (11.4)
                boolean matters = c.locked || hostile && (!green || seat != Presence.HELM) || nav && seat == Presence.HELM;
                if (matters) edge(sx, sy, width, height, label(c, EDGE_NAME) + " " + Fmt.rangeText(c.range),
                        hostile ? Phosphor.RED : Phosphor.ZINC, c.locked ? Drive.FULL : Drive.NORM);
                continue;
            }
            // Under an optic: counted there
            int under = under(sx, sy, half, bracketed || c.kind == Kind.STATION || c.range < 2_000f);
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
            m.glyph = null;
            m.bracket = null;
            m.color = hostile ? Phosphor.RED : Phosphor.ZINC;
            m.sub = "";
            if (c.locked) {
                // The lock by name only: its numbers are in the cluster (3.8)
                m.bracket = Kit.Bracket.SQUARE;
                m.level = Drive.FULL;
                m.name = label(c, 14);
            } else if (bracketed && hostile) {
                m.bracket = Kit.Bracket.TRI;
                m.level = Drive.NORM;
                m.name = label(c, 14);
                m.sub = Fmt.rangeText(c.range);
            } else if (nav) {
                m.bracket = Kit.Bracket.DIAMOND;
                m.level = Drive.NORM;
                m.name = label(c, 14);
            } else if (c.kind == Kind.STATION) {
                m.glyph = Kit.STATION;
                m.level = Drive.DIM;
                m.name = label(c, 14);
            } else {
                // Neutral traffic, and a hostile at the helm in Green: the glyph; its name and range within 2 km
                m.glyph = Kit.NEUTRAL;
                m.level = Drive.DIM;
                m.name = c.range < 2_000f ? label(c, 14) : "";
                m.sub = c.range < 2_000f ? Fmt.rangeText(c.range) : "";
            }
        }

        // Missiles: their glyphs and tracks, and where the inbound ones will strike
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack t = tracks.get(i);
            boolean incoming = t.side == Side.INCOMING;
            if (!incoming && seat == Presence.GUN) continue;
            views.worldToUi(t.x, t.y, v);
            float mx = v.x, my = height - v.y;
            views.worldToUi(t.pathX, t.pathY, v);
            float px = v.x, py = height - v.y;
            if (!inside(mx, my)) {
                if (incoming) edge(mx, my, width, height, "MSL " + Fmt.mmss(t.timeToImpact), Phosphor.RED, Drive.FULL);
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

        // The velocity markers, at the helm: prograde and retrograde in the frame that matters
        FlightStatus fl = d.flightStatus;
        float speed = (float) Math.sqrt(fl.vx * fl.vx + fl.vy * fl.vy);
        if (seat == Presence.HELM && fl.present && speed > 0.5f && !d.ctx.docked && !d.ctx.latched) {
            // Outside the own ship's optic when it sits over the ship
            float r = Math.max(VEL_R, ownRing + 16f);
            float dx = fl.vx / speed, dy = -fl.vy / speed;
            proX = shipX + dx * r;
            proY = shipY + dy * r;
            retroX = shipX - dx * r;
            retroY = shipY - dy * r;
            velSquare = fl.frame == Frame.TARGET || fl.frame == Frame.NAV || fl.frame == Frame.STATION;
            // Not on another craft's magnified picture, where it would read as a point on it
            proShown = !onOtherInset(proX, proY);
            retroShown = !onOtherInset(retroX, retroY);
            vel = true;
        }

        // The lead reticle with a lock, and the intercept line to it
        if (locked != null && d.targeting != null && d.targeting.getLeadPoint(lp)) {
            views.worldToUi(lp.x, lp.y, v);
            leadX = v.x;
            leadY = height - v.y;
            // Never on an optic's magnified picture, where it would read as a point on it
            lead = inside(leadX, leadY) && under(leadX, leadY, 9f, false) < 0;
            leadOnTarget = Math.abs(leadX - lockedX) < lockedHalf && Math.abs(leadY - lockedY) < lockedHalf;
            leadLine = seat != Presence.HELM || d.ctx.condition == Condition.RED;
        }
        settleEdges();
    }

    /** Which of this frame's insets is onto {@code c}'s craft, or -1. */
    private int insetOf(Contact c) {
        if (c.entity == null) return -1;
        for (int i = 0; i < insetCount; i++) if (insets[i].entity == c.entity) return i;
        return -1;
    }

    /** Which inset a mark at {@code x, y}, {@code half} across with a name under it or not, falls under, or -1. */
    private int under(float x, float y, float half, boolean named) {
        float w = Math.max(2f * half, named ? 14f * 12f : 0f), h = 2f * half + (named ? 40f : 0f);
        for (int i = 0; i < insetCount; i++) {
            if (Optic.covers(insets[i], stageHeight, x - w / 2f, y - half, w, h)) return i;
        }
        return -1;
    }

    /** Whether a velocity marker at {@code x, y} falls on the optic of a craft other than the own ship. */
    private boolean onOtherInset(float x, float y) {
        for (int i = 0; i < insetCount; i++) {
            if (!insets[i].own && Optic.covers(insets[i], stageHeight, x - 10f, y - 10f, 20f, 20f)) return true;
        }
        return false;
    }

    /**
     * What the optics keep off, as the game takes them (UI units, y up): the top column, the band or
     * the gun's plate under the floor, the player's ship and the edge markers.
     */
    void panels(Array<Rectangle> out, float topX, float height, Rectangle column, Rectangle band) {
        if (!on) return;
        out.add(column.set(colX0 - 6f, height - colBottom, colX1 - colX0 + 12f, colBottom));
        float floor = bottom + 8f;
        if (floor < height) out.add(band.set(topX + Geo.BAND_X - 6f, 0f, Geo.BAND_W + 12f, height - floor));
        // A side panel, down its height
        if (panelLeft < stageWidth) out.add(panelBox.set(panelLeft - 6f, 0f, stageWidth - panelLeft + 6f, height));
        // The player's ship, so no other craft's magnified picture covers it
        if (on && shipReach > 0f) {
            float h = shipReach + 6f;
            out.add(shipPanel.set(shipX - h, height - shipY - h, 2f * h, 2f * h));
        }
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            Rectangle r = Kit.edgeBox(e.x, e.y, e.dir, e.text, edgePanels[i]);
            out.add(r.set(r.x - 4f, height - r.y - r.height - 4f, r.width + 8f, r.height + 8f));
        }
    }

    private static String label(Contact c, int cells) {
        return Fmt.name(c.label, cells);
    }

    private static final char[] DIRS = {'u', 'd', 'l', 'r'};
    private final int[] onEdge = new int[EDGES];
    private final float[] along = new float[EDGES], reach = new float[EDGES];

    /** Spreads the markers on each edge along it, so two things off screen the same way both read. */
    void settleEdges() {
        float lo, hi;
        for (char dir : DIRS) {
            boolean across = dir == 'u' || dir == 'd';
            int n = 0;
            for (int i = 0; i < edgeCount; i++) {
                if (edges[i].dir != dir) continue;
                // In order along the edge, so the spread keeps each marker's place
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
            if (across) {
                lo = left;
                hi = right;
            } else {
                lo = colBottom + 14f - 11f;
                hi = bottom - 14f + 11f;
            }
            Kit.spread(along, reach, n, lo, hi, across ? 12f : 3f);
            for (int k = 0; k < n; k++) {
                Edge e = edges[onEdge[k]];
                if (across) e.x = along[k];
                else e.y = along[k];
            }
        }
    }

    /** Whether a point is in the world labels may use: on screen, clear of the top column and the band. */
    boolean inside(float x, float y) {
        if (x < left || x > right || y < top || y > bottom) return false;
        return !(x > colX0 && x < colX1 && y < colBottom);
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

    /** An edge marker where the ray from the world's middle towards {@code x, y} leaves the world. */
    void edge(float x, float y, float width, float height, String text, Phosphor color, float level) {
        if (edgeCount == EDGES) return;
        float l = left + 12f, r = right - 12f, t = colBottom + 14f, b = bottom - 14f;
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
            if (leadLine) {
                float dx = leadX - shipX, dy = leadY - shipY, len = (float) Math.sqrt(dx * dx + dy * dy);
                // From outside the own ship's optic when it sits over the ship
                float from = Math.max(30f, ownRing + 4f);
                if (len > from + 20f) {
                    float ux = dx / len, uy = dy / len;
                    f.dotLine(shipX + ux * from, shipY + uy * from, leadX - ux * 12f, leadY - uy * 12f,
                            f.o().proj().step(7f).size(1.3f).dim());
                }
            }
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
