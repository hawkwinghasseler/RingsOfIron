package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.vfd.DeckPlan;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.ShipSystems;

import java.util.Locale;

/**
 * The ship's plan on the Systems tab (functional-context 10.2; mockup 11.11), at large scale on the
 * dot panel: the rooms' walls and their hazards as H4's plan has them (fire, smoke, a breach's X),
 * every system a box of dots in its footprint (solid while out of action), the power conduits a fine
 * chain (broken where one is cut), the RCS blocks on the hull, the systems on the list's focused row in
 * corner brackets, the rooms' names, the crew where they stand (a tick each side at work), and what
 * is wrong called out above and below the hull with its severity leading, as P2's rows have it. The
 * plan's key is printed along the pane's foot.
 */
final class SystemsPlan {
    static final int CALLOUTS = 4;

    final DeckPlan deck = new DeckPlan();
    private final float[] fit = new float[3];
    private ShipGridComponent grid;
    private ShipSystems systems;
    /** The conduits: from and to, in tiles (x0, y0, x1, y1 per run); the cut tiles (x, y). */
    private final FloatArray runs = new FloatArray(), cuts = new FloatArray();
    private int conduitRevision = -1;
    /** The systems in the list's focus, for the brackets. */
    private ShipSystems.Row focus;
    private final Array<Callout> callouts = new Array<>();
    private final Callout[] pool = new Callout[CALLOUTS];
    /** Rooms' names and where to print them (tiles), rebuilt with the layout. */
    private final Array<String> roomNames = new Array<>();
    private final FloatArray roomAt = new FloatArray();
    private int roomRevision = -1;
    /** The crew, where they stand, and the ship they stand on to be drawn. */
    private Array<Member> crew;
    private Entity ship;

    /** What is wrong somewhere aboard: its severity, two lines, and its tile. */
    static final class Callout {
        boolean warning;
        String what = "", detail = "";
        int tx, ty;
        float x, y, w;
        boolean above;
    }

    SystemsPlan() {
        for (int i = 0; i < CALLOUTS; i++) pool[i] = new Callout();
    }

    /** Reads this frame's ship: the plan, the conduits when the layout changed, and the callouts from the alerts. */
    void update(HudData d, ShipSystems sys, ShipSystems.Row focused, Array<Member> crew) {
        systems = sys;
        focus = focused;
        this.crew = crew;
        ship = d.ctx.ship;
        deck.update(d);
        grid = sys.grid;
        if (grid != null && (grid.layoutRevision != conduitRevision || grid.gridDirty)) conduits();
        if (grid != null && grid.layoutRevision != roomRevision) rooms();
        callouts.clear();
        Array<Alert> all = d.alerts.all();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.size && callouts.size < CALLOUTS; i++) {
                Alert a = all.get(i);
                if (a.tileX < 0 || (a.level == Level.WARNING) != (pass == 0) || a.level == Level.ADVISORY) continue;
                if (a.category != Category.ENGINEERING && a.category != Category.LIFE_SUPPORT) continue;
                if (near(a.tileX, a.tileY)) continue;
                Callout c = pool[callouts.size];
                c.warning = a.level == Level.WARNING;
                c.tx = a.tileX;
                c.ty = a.tileY;
                words(a, c);
                callouts.add(c);
            }
        }
    }

    private boolean near(int x, int y) {
        for (int i = 0; i < callouts.size; i++) {
            Callout c = callouts.get(i);
            if (Math.abs(c.tx - x) <= 1 && Math.abs(c.ty - y) <= 1) return true;
        }
        return false;
    }

    /** A callout's two lines from its alert: the system or hazard, then its state and who is on it. */
    private void words(Alert a, Callout c) {
        String who = a.handling > 0 ? (a.handling == 1 ? "1 CREW ON IT" : a.handling + " CREW ON IT") : "";
        ShipSystems.Sys s = a.id.startsWith("sys:") ? systems.at(a.tileX, a.tileY) : null;
        if (s != null) {
            c.what = s.name + (s.where.isEmpty() ? "" : " (" + s.where + ")");
            String state = s.state == ShipSystems.State.CUT_OFF ? "NO POWER" : ShipSystems.stateText(s);
            c.detail = Math.round(s.s.health * 100f) + "% · " + state + (who.isEmpty() ? "" : " · " + (s.state.down() ? "BEING REPAIRED" : who));
            return;
        }
        c.what = a.code.isEmpty() ? a.text.toUpperCase(Locale.ROOT) : a.code + (a.room.isEmpty() ? "" : " IN " + a.room);
        String n = a.count > 1 ? a.count + " TILES" : "";
        c.detail = n.isEmpty() ? who : who.isEmpty() ? n : n + " · " + who;
    }

    private void conduits() {
        conduitRevision = grid.layoutRevision;
        runs.clear();
        cuts.clear();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                TileConduit c = grid.getTileConduit(x, y);
                if (c == null) continue;
                if (c.isCut()) {
                    cuts.add(x);
                    cuts.add(y);
                }
                // Each link drawn once, to the right and up
                if (grid.getTileConduit(x + 1, y) != null && grid.hasConduitToward(x, y, Side.RIGHT)) run(x, y, x + 1, y);
                if (grid.getTileConduit(x, y + 1) != null && grid.hasConduitToward(x, y, Side.TOP)) run(x, y, x, y + 1);
            }
        }
    }

    private void run(int x0, int y0, int x1, int y1) {
        runs.add(x0);
        runs.add(y0);
        runs.add(x1);
        runs.add(y1);
    }

    private void rooms() {
        roomRevision = grid.layoutRevision;
        roomNames.clear();
        roomAt.clear();
        for (int i = 0; i < grid.compartments.size; i++) {
            Compartment c = grid.compartments.get(i);
            if (c.tiles.size < 6) continue;
            String name = systems.rooms.of(grid, c);
            if (name == null || name.isEmpty()) continue;
            int x0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            for (int k = 0; k < c.tiles.size; k++) {
                int p = c.tiles.get(k), tx = p % grid.width, ty = p / grid.width;
                if (ty > y1 || ty == y1 && tx < x0) {
                    y1 = ty;
                    x0 = tx;
                }
            }
            roomNames.add(name.toUpperCase(Locale.ROOT));
            roomAt.add(x0);
            roomAt.add(y1);
        }
    }

    /** The plan in pane {@code x, y, w, h}: callouts above and below the hull, the key along the foot. */
    void paint(Filament f, float x, float y, float w, float h) {
        if (!deck.present() || grid == null) {
            Glass.text(f, x + 12f, y + 40f, "NO PLAN", Drive.DIM);
            return;
        }
        float px = x + 18f, py = y + 81f, pw = w - 36f, ph = Math.max(40f, h - 81f - 98f);
        deck.fit(px, py, pw, ph, fit);
        float t = fit[0];
        // The conduits under everything, a fine chain; broken where a tile's run is cut
        for (int i = 0; i < runs.size; i += 4) {
            float x0 = cx(runs.get(i)), y0 = cy(runs.get(i + 1)), x1 = cx(runs.get(i + 2)), y1 = cy(runs.get(i + 3));
            boolean cutA = cut(runs.get(i), runs.get(i + 1)), cutB = cut(runs.get(i + 2), runs.get(i + 3));
            if (cutA && cutB) continue;
            if (cutA) {
                x0 = (x0 + x1) / 2f;
                y0 = (y0 + y1) / 2f;
                cap(f, x0, y0, x1 != x0);
            } else if (cutB) {
                x1 = (x0 + x1) / 2f;
                y1 = (y0 + y1) / 2f;
                cap(f, x1, y1, x1 != x0);
            }
            f.dotLine(x0, y0, x1, y1, f.o().size(1f).step(3f).dim());
        }
        deck.paint(f, px, py, pw, ph);
        // The systems: a box of dots inset from its tiles, solid while it is out of action
        for (int i = 0; i < systems.all.size; i++) {
            ShipSystems.Sys s = systems.all.get(i);
            if (s.s.footprint == null) continue;
            boolean down = s.state.down();
            PlanMarks.box(f, deck, fit, s.s.footprint.originX, s.s.footprint.originY, s.s.footprint.width(), s.s.footprint.height(),
                    down ? Drive.FULL : Drive.DIM, down ? 2f : 4f);
        }
        // The RCS blocks on the hull: a quad a cross of dots, a linear block a bar; none where one is stopped
        for (int i = 0; i < grid.rcsThrusters.size; i++) {
            RcsThruster r = grid.rcsThrusters.get(i);
            if (!r.works(grid)) continue;
            float bx = cx(r.x), by = cy(r.y);
            if (r.kind == RcsThruster.Kind.QUAD) {
                for (int k = -1; k <= 1; k++) {
                    f.dot(bx + k * 3f, by, f.o().size(1.6f).dim());
                    if (k != 0) f.dot(bx, by + k * 3f, f.o().size(1.6f).dim());
                }
            } else {
                for (int k = -1; k <= 1; k++) f.dot(bx + k * 3f, by, f.o().size(1.6f).dim());
            }
        }
        // The list's focus, bracketed
        if (focus != null) {
            for (int i = 0; i < focus.systems.size; i++) {
                ShipSystems.Sys s = focus.systems.get(i);
                if (s.s.footprint == null) continue;
                PlanMarks.brackets(f, deck, fit, s.s.footprint.originX, s.s.footprint.originY, s.s.footprint.width(), s.s.footprint.height());
            }
        }
        for (int i = 0; i < roomNames.size; i++) {
            Glass.text(f, deck.left((int) roomAt.get(2 * i), fit) + 4f, deck.top((int) roomAt.get(2 * i + 1), fit) + 4f, roomNames.get(i), Drive.DIM);
        }
        if (crew != null) PlanMarks.crew(f, deck, fit, crew, ship, false);
        callouts(f, x, y, w, py, ph);
        key(f, x + 10f, y + h - 22f);
    }

    private boolean cut(float x, float y) {
        for (int i = 0; i < cuts.size; i += 2) if (cuts.get(i) == x && cuts.get(i + 1) == y) return true;
        return false;
    }

    private float cx(float gx) {
        return deck.left((int) gx, fit) + fit[0] / 2f;
    }

    private float cy(float gy) {
        return deck.top((int) gy, fit) + fit[0] / 2f;
    }

    private static void cap(Filament f, float x, float y, boolean across) {
        if (across) f.dotLine(x, y - 5f, x, y + 5f, f.o().size(1.6f).step(2.5f).full());
        else f.dotLine(x - 5f, y, x + 5f, y, f.o().size(1.6f).step(2.5f).full());
    }

    /** What is wrong, above the hull for the plan's upper half and below it for the lower, each clear of the last. */
    private void callouts(Filament f, float x, float y, float w, float py, float ph) {
        float mid = py + ph / 2f, right = x + w - 12f;
        float aboveX = x + 12f, belowX = x + 12f;
        for (int i = 0; i < callouts.size; i++) {
            Callout c = callouts.get(i);
            float mx = cx(c.tx), my = cy(c.ty);
            c.above = my < mid;
            c.w = 2f * Glass.CW + Glass.width(Math.max(c.what.length(), c.detail.length()));
            float want = Math.max(mx + 6f, c.above ? aboveX : belowX);
            c.x = Math.min(want, right - c.w);
            if (c.above) aboveX = c.x + c.w + 16f;
            else belowX = c.x + c.w + 16f;
            c.y = c.above ? y + 24f : py + ph + 14f;
            Phosphor color = c.warning ? Phosphor.RED : Phosphor.AMBER;
            f.dm(c.x, c.y, c.warning ? "♦" : "▲", f.o().pitch(Glass.P).color(color).full().noGhost());
            Glass.text(f, c.x + 2f * Glass.CW, c.y, Glass.cut(c.what, Glass.cells(right - c.x) - 2), Drive.NORM);
            if (!c.detail.isEmpty()) Glass.text(f, c.x + 2f * Glass.CW, c.y + Glass.RH, Glass.cut(c.detail, Glass.cells(right - c.x) - 2), Drive.DIM);
            float ly = c.above ? c.y + (c.detail.isEmpty() ? 1 : 2) * Glass.RH : c.y - 3f;
            f.dotLine(mx, my + (c.above ? -fit[0] / 2f - 2f : fit[0] / 2f + 2f), c.x - 3f, ly, f.o().size(1f).step(2f).dim());
        }
    }

    /** The plan's key along the pane's foot: a system, out of action, a breach, a cut conduit, crew at work. */
    private void key(Filament f, float x, float y) {
        float kx = x;
        kx = PlanMarks.keyBox(f, kx, y, Drive.DIM, 4f, "SYSTEM");
        kx = PlanMarks.keyBox(f, kx, y, Drive.FULL, 2f, "OUT OF ACTION");
        for (int i = -2; i <= 2; i++) {
            f.dot(kx + 9f + i * 2.6f, y + i * 2.6f, f.o().size(1.6f).red().full());
            if (i != 0) f.dot(kx + 9f + i * 2.6f, y - i * 2.6f, f.o().size(1.6f).red().full());
        }
        kx += 26f;
        kx += Glass.dim(f, kx, y - 5f, "BREACH") + 22f;
        f.dotLine(kx, y, kx + 8f, y, f.o().size(1f).step(3f).dim());
        cap(f, kx + 10f, y, true);
        cap(f, kx + 16f, y, true);
        kx += 26f;
        kx += Glass.dim(f, kx, y - 5f, "CONDUIT, CUT") + 22f;
        PlanMarks.keyPerson(f, kx, y, true, false, "CREW AT WORK");
    }
}
