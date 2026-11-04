package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.Sector;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Mfd;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Page;

import java.util.Locale;

/**
 * The MFD pages (functional-stations 9.2). Each page is the 288 x 78 glass under an MFD's title row:
 * most are four rows of dot matrix on a fixed character grid, as an alphanumeric VFD module is, and
 * SENSORS, DEFENSE, DAMAGE and NAV add their drawings. Only the pages on show are read each frame,
 * and every page paints from what was read, never from the game.
 */
final class Pages {
    /** The character grid: rows 19 apart, cells 9 wide at pitch 1.5. */
    static final float ROW = 19f, PITCH = 1.5f, CELL = 9f;
    /** Characters across a full-width page. */
    static final int COLS = 30;
    /** The scope's range steps, km: 9.2 adds the 5 km step for a gun fight. */
    static final float[] STEPS_KM = {0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1000f};
    static final String[] STEP_TEXT = {"0.5 KM", "1 KM", "2 KM", "5 KM", "10 KM", "20 KM", "50 KM", "100 KM", "200 KM", "500 KM", "1000 KM"};

    /** Rows of dot matrix on the character grid: each row a few spans, the rest of its cells ghosts. */
    static final class Screen {
        static final int SPANS = 8;
        final int rows, cols;
        final int[] spans;
        final int[][] at;
        final String[][] text;
        final float[][] level;
        final Phosphor[][] color;
        /** The span is a key: its characters cut out of a lit block, as the status strip's keys are. */
        final boolean[][] key;
        /** A '▶' before the row: the locked target. */
        final boolean[] marked;

        Screen(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
            spans = new int[rows];
            at = new int[rows][SPANS];
            text = new String[rows][SPANS];
            level = new float[rows][SPANS];
            color = new Phosphor[rows][SPANS];
            key = new boolean[rows][SPANS];
            marked = new boolean[rows];
        }

        void clear() {
            for (int r = 0; r < rows; r++) {
                spans[r] = 0;
                marked[r] = false;
            }
        }

        /** {@code s} from cell {@code col}, cut at the row's end. */
        void put(int row, int col, String s, float lv) {
            put(row, col, s, lv, Phosphor.ZINC);
        }

        void put(int row, int col, String s, float lv, Phosphor c) {
            if (row < 0 || row >= rows || col >= cols || s == null || s.isEmpty() || spans[row] == SPANS) return;
            int i = spans[row]++;
            at[row][i] = Math.max(0, col);
            text[row][i] = s.length() > cols - at[row][i] ? s.substring(0, cols - at[row][i]) : s;
            level[row][i] = lv;
            color[row][i] = c;
            key[row][i] = false;
        }

        /** A key's name from cell {@code col}, cut out of a lit block. */
        void key(int row, int col, String s, float lv) {
            int before = row >= 0 && row < rows ? spans[row] : 0;
            put(row, col, s, lv, Phosphor.ZINC);
            if (row >= 0 && row < rows && spans[row] > before) key[row][spans[row] - 1] = true;
        }

        /** {@code s} ending just before cell {@code end}, cut to {@code cells}. */
        void right(int row, int end, int cells, String s, float lv) {
            right(row, end, cells, s, lv, Phosphor.ZINC);
        }

        void right(int row, int end, int cells, String s, float lv, Phosphor c) {
            if (s == null || s.isEmpty()) return;
            String t = s.length() > cells ? s.substring(s.length() - cells) : s;
            put(row, end - t.length(), t, lv, c);
        }

        void paint(Filament f, float x, float y) {
            for (int r = 0; r < rows; r++) {
                float ry = y + 2f + r * ROW;
                f.dm(x, ry, "", f.o().pitch(PITCH).cells(cols));
                for (int i = 0; i < spans[r]; i++) {
                    Filament.Opt o = f.o().pitch(PITCH).level(level[r][i]).color(color[r][i]).noGhost();
                    if (key[r][i]) o.inverse(0, text[r][i].length());
                    f.dm(x + at[r][i] * CELL, ry, text[r][i], o);
                }
                if (marked[r]) f.dm(x - 11f, ry, "▶", f.o().pitch(PITCH).norm().noGhost());
            }
        }
    }

    private final Screen[] screens = new Screen[Page.values().length];
    private final String[] settings = new String[Page.values().length];
    private final boolean[] read = new boolean[Page.values().length];

    Pages() {
        for (Page p : Page.values()) {
            screens[p.ordinal()] = new Screen(4, p == Page.SENSORS ? 19 : p == Page.NAV ? 20 : p == Page.DEFENSE ? 19 : COLS);
            settings[p.ordinal()] = "";
        }
        for (int i = 0; i < scope.length; i++) scope[i] = new Kit.ScopeContact();
    }

    Screen screen(Page p) {
        return screens[p.ordinal()];
    }

    /** What the page's title row says it is set to ("5 KM"), or "". */
    String setting(Page p) {
        return settings[p.ordinal()];
    }

    /** Reads the pages on show on {@code mfds}, once a frame. */
    void update(HudData d, Mfds mfds) {
        for (int i = 0; i < read.length; i++) read[i] = false;
        page(d, mfds.left);
        page(d, mfds.right);
    }

    private void page(HudData d, Mfd m) {
        if (m.folded || read[m.page.ordinal()]) return;
        read[m.page.ordinal()] = true;
        read(m.page, d);
    }

    /** Reads {@code p} from {@code d}, whether or not it is on show (for tests). */
    void read(Page p, HudData d) {
        screen(p).clear();
        settings[p.ordinal()] = "";
        switch (p) {
            case HELM: helm(d); break;
            case DOCK: dock(d); break;
            case NAV: nav(d); break;
            case PLAN: plan(d); break;
            case SENSORS: sensors(d); break;
            case WEAPONS: weapons(d); break;
            case DEFENSE: defense(d); break;
            case DAMAGE: damage(d); break;
            case POWER: power(d); break;
            case ATMOS: atmos(d); break;
            case COMMS: comms(d); break;
            case CREW: crew(d); break;
            case CONTRACTS: contracts(d); break;
            default: break;
        }
    }

    void paint(Filament f, Page p, float x, float y, float w, float h) {
        switch (p) {
            case SENSORS:
                Kit.sectorScope(f, x + 44f, y + h / 2f, 36f, scope, scopeCount);
                screen(p).paint(f, x + 100f, y);
                for (int r = 0; r < 4; r++) Kit.pip(f, x + w - 9f, y + 2f + r * ROW + 5.25f, Phosphor.RED, sensorPip[r]);
                break;
            case DEFENSE:
                paintDefense(f, x, y, w, h);
                break;
            case DAMAGE:
                paintDamage(f, x, y, w, h);
                break;
            case NAV:
                paintOrbit(f, x + 44f, y + h / 2f);
                screen(p).paint(f, x + 100f, y);
                break;
            default:
                screen(p).paint(f, x + 8f, y);
                break;
        }
    }

    // ------------------------------------------------------------------ SENSORS

    private final Kit.ScopeContact[] scope = new Kit.ScopeContact[24];
    private int scopeCount;
    private final Contact[] top = new Contact[4];
    private final boolean[] sensorPip = new boolean[4];

    private void sensors(HudData d) {
        Array<Contact> all = d.contactFrame.contacts;
        boolean alert = d.ctx.condition != Condition.GREEN;
        // The range: the smallest step that holds the lock, every inbound missile and, in Yellow and Red, the hostiles
        float need = 0f;
        for (int i = 0; i < 4; i++) top[i] = null;
        for (int i = 0; i < all.size; i++) {
            Contact c = all.get(i);
            if (Float.isNaN(c.range)) continue;
            if (c.locked || inbound(c) || (alert && c.relation == Relation.HOSTILE)) need = Math.max(need, c.range);
            insert(c);
        }
        if (need <= 0f) for (Contact c : top) if (c != null) need = Math.max(need, c.range);
        int step = STEPS_KM.length - 1;
        if (need <= 0f) step = 4;
        else for (int i = 0; i < STEPS_KM.length; i++) {
            if (STEPS_KM[i] * 1000f >= need * 1.05f) {
                step = i;
                break;
            }
        }
        float range = STEPS_KM[step] * 1000f;
        settings[Page.SENSORS.ordinal()] = STEP_TEXT[step];
        scopeCount = 0;
        for (int i = 0; i < all.size && scopeCount < scope.length; i++) {
            Contact c = all.get(i);
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg) || c.range > range) continue;
            Kit.ScopeContact s = scope[scopeCount++];
            // The contact's bearing is port-positive; the scope's runs clockwise from the nose
            s.bearing = -c.bearingDeg;
            s.ring = Math.min(3, (int) (c.range / (range / 4f)));
            s.hostile = c.relation == Relation.HOSTILE;
            s.lock = c.locked;
            s.missile = inbound(c);
        }
        Screen sc = screen(Page.SENSORS);
        for (int r = 0; r < 4; r++) {
            Contact c = top[r];
            sensorPip[r] = false;
            if (c == null) continue;
            boolean in = inbound(c);
            float lv = c.locked || in ? Drive.NORM : Drive.DIM;
            sc.put(r, 0, contactName(c), lv);
            sc.right(r, 14, 6, range6(c.range), lv);
            if (in) sc.right(r, 19, 4, Fmt.mmss(impactIn(c, d)), lv);
            sc.marked[r] = c.locked;
            sensorPip[r] = c.relation == Relation.HOSTILE || in;
        }
    }

    /**
     * A contact's name in the list's seven cells (the mockup's "HARPOON 2.4 KM 0:08" fills the row):
     * "Pirate raider 6" is "RAIDE 6", which tells it from raider 7 where "PIRATE " would not, and the
     * player's own missiles are "OUR MSL".
     */
    static String contactName(Contact c) {
        if (c.kind == Kind.MISSILE) return c.label.startsWith("OUR") ? "OUR MSL" : "MISSILE";
        return Fmt.name(c.label, 7);
    }

    private static boolean inbound(Contact c) {
        return c.kind == Kind.MISSILE && c.threat;
    }

    /** Threat order: missiles inbound, the lock, hostiles locked on us, hostiles, the nav target, the rest; nearest first in each. */
    static int rank(Contact c) {
        if (inbound(c)) return 0;
        if (c.locked) return 1;
        if (c.relation == Relation.HOSTILE) return c.threat ? 2 : 3;
        if (c.nav) return 4;
        if (c.kind == Kind.MISSILE) return 5;
        return 6;
    }

    private void insert(Contact c) {
        int rank = rank(c);
        for (int i = 0; i < top.length; i++) {
            Contact t = top[i];
            if (t == null || rank < rank(t) || (rank == rank(t) && c.range < t.range)) {
                for (int j = top.length - 1; j > i; j--) top[j] = top[j - 1];
                top[i] = c;
                return;
            }
        }
    }

    /** A range in six characters: "140 M", "2.4 KM", "123 KM". */
    static String range6(float m) {
        if (Float.isNaN(m)) return "";
        if (m < 1000f) return Math.round(m) + " M";
        if (m < 10_000f) return String.format(Locale.ROOT, "%.1f KM", m / 1000f);
        if (m < 999_500f) return Math.round(m / 1000f) + " KM";
        return ">999KM";
    }

    /** Seconds until an inbound missile strikes: its track's, else its range over its closing speed. */
    private static float impactIn(Contact c, HudData d) {
        Array<MissileTrack> tracks = d.threatFrame.missiles;
        MissileTrack best = null;
        float bestD = 25f;
        for (int i = 0; i < tracks.size; i++) {
            MissileTrack t = tracks.get(i);
            if (t.side != Side.INCOMING) continue;
            float dx = t.x - c.x, dy = t.y - c.y, dd = dx * dx + dy * dy;
            if (dd < bestD) {
                bestD = dd;
                best = t;
            }
        }
        if (best != null) return !Float.isNaN(best.timeToImpact) ? best.timeToImpact : best.closestApproach;
        return c.closing > 1f ? c.range / c.closing : Float.NaN;
    }

    // ------------------------------------------------------------------ WEAPONS

    private void weapons(HudData d) {
        Screen s = screen(Page.WEAPONS);
        WeaponFrame w = d.weaponFrame;
        boolean locked = d.targetStatus.present;
        int total = w.mounts.size, row = 0;
        // Guns and launchers first, then point defense; past four, the last row counts the rest
        for (int pass = 0; pass < 2 && row < 4; pass++) {
            for (int i = 0; i < total && row < 4; i++) {
                Mount m = w.mounts.get(i);
                if ((m.kind == com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.POINT_DEFENSE) != (pass == 1)) continue;
                if (row == 3 && total > 4) break;
                mount(s, row++, m, w, locked);
            }
        }
        if (total > 4) s.put(3, 0, "+" + (total - 3) + " MORE", Drive.DIM);
        settings[Page.WEAPONS.ordinal()] = w.holdFire ? "HOLD FIRE" : "";
    }

    private static void mount(Screen s, int r, Mount m, WeaponFrame w, boolean locked) {
        boolean pd = m.kind == com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.POINT_DEFENSE;
        String state = Band.gunState(m, locked && !pd);
        if (pd && (m.engaging || m.defending) && m.state == State.READY) state = "ENGAGING";
        else if (m.ammo >= 0 && m.state == State.READY) state = Fmt.cut("READY " + m.ammo, 8);
        float lv = m.state == State.READY ? Drive.DIM : Drive.NORM;
        s.put(r, 0, Fmt.cut(m.name, 8), Drive.NORM);
        s.put(r, 9, state, lv);
        String mode = mode(m, w);
        s.put(r, 18, mode, Drive.DIM, "HOLD".equals(mode) ? Phosphor.AMBER : Phosphor.ZINC);
        if (locked && !pd && !Float.isNaN(m.hitChance)) s.right(r, 30, 4, Math.round(m.hitChance * 100f) + "%", Drive.NORM);
    }

    /** Who works a mount, and so how it fires (9.2). */
    static String mode(Mount m, WeaponFrame w) {
        boolean pd = m.kind == com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.POINT_DEFENSE;
        switch (m.controller) {
            case PLAYER: return "YOURS";
            case CREW: return w.holdFire && !pd ? "HOLD" : "AT WILL";
            case CONSOLE: return w.holdFire && !pd ? "HOLD" : "TRIGGER";
            case AUTO: return pd ? "PD AUTO" : "AUTO";
            default: return "";
        }
    }

    // ------------------------------------------------------------------ DEFENSE

    private boolean shieldFitted;
    private float shieldShare;
    private final float[] sectorDensity = new float[4];
    private final float[] defenseOn = new float[3];
    private String defenseMass = "", defenseTrim = "", defenseEmit = "", defenseTrip = "";
    private float defenseHeat;
    private boolean defenseStunned, defenseDock;
    static final String[] DEFENSE_MODES = {"RAISED", "LOWERED", "STOWING"};
    /** The sectors clockwise from the bow, as {@link Sector} ordinals. */
    private static final int[] CLOCKWISE = {Sector.BOW.ordinal(), Sector.STARBOARD.ordinal(), Sector.STERN.ordinal(), Sector.PORT.ordinal()};
    private static final float[] HULL = {0f, -22f, 6f, -13f, 7f, 15f, 3.5f, 22f, -3.5f, 22f, -7f, 15f, -6f, -13f};
    private final Filament.RingLevels shieldRing = this::shieldLevel;

    private void defense(HudData d) {
        ShieldStatus sh = d.shieldStatus;
        shieldFitted = sh.present && sh.emitters > 0;
        if (!shieldFitted) return;
        shieldShare = sh.share();
        for (int i = 0; i < 4; i++) sectorDensity[i] = sh.sectors[i];
        boolean stowing = !sh.raised && sh.cloud > 0f;
        defenseOn[0] = sh.raised ? Drive.NORM : Float.NaN;
        defenseOn[1] = !sh.raised && !stowing ? Drive.NORM : Float.NaN;
        defenseOn[2] = stowing ? Drive.NORM : Float.NaN;
        defenseMass = Fmt.fit(Math.min(99f, shieldShare * 100f), 2, 0);
        defenseTrim = "starboard".equals(sh.trim) ? "STBD" : Fmt.cut(sh.trim, 5);
        defenseEmit = sh.emittersWorking + " OF " + sh.emitters;
        defenseTrip = sh.emittersTripped > 0 ? sh.emittersTripped + " TRIP" : "";
        defenseHeat = sh.hottest;
        defenseStunned = sh.powerStunned;
        defenseDock = sh.loweredForDock;
        settings[Page.DEFENSE.ordinal()] = sh.raised ? "" : "LOWERED";
    }

    /** A cell of the shield ring, 0 at the bow and clockwise: the cloud's density there, interpolated between sectors. */
    float shieldLevel(int i, int n) {
        float q = i * 4f / n;
        int k0 = (int) q % 4, k1 = (k0 + 1) % 4;
        float t = q - (int) q;
        float a = sectorDensity[CLOCKWISE[k0]], b = sectorDensity[CLOCKWISE[k1]];
        float th = shieldShare * (a + (b - a) * t);
        return th > 0.75f ? Drive.NORM : th > 0.45f ? Drive.DIM : Float.NaN;
    }

    private void paintDefense(Filament f, float x, float y, float w, float h) {
        if (!shieldFitted) {
            // A hull with no shield emitter: blank glass, not a ghosted ring (guide 7.8)
            f.legend(x + w / 2f, y + h / 2f + 4f, "NO SHIELD EMITTER", f.o().size(12f).centre().dim());
            return;
        }
        float cx = x + 42f, cy = y + h / 2f;
        f.ring(cx, cy, 28f, 35f, 96, shieldRing, f.o().gapDeg(1.2f));
        for (int i = 0; i < HULL.length; i += 2) {
            int j = (i + 2) % HULL.length;
            f.dotLine(cx + HULL[i], cy + HULL[i + 1], cx + HULL[j], cy + HULL[j + 1], f.o().step(2.4f).size(1.1f).dim());
        }
        float tx = x + 92f, lim = x + w - 8f;
        Kit.fits(Kit.states(f, tx, y + 13f, DEFENSE_MODES, defenseOn, 13f, 8f) - 8f, lim, "defense modes");
        float xx = tx;
        xx += f.legend(xx, y + 37f, "MASS", f.o().dim()) + 5f;
        xx += f.seg7(xx, y + 21f, 16f, defenseMass, f.o().cells(2).right().norm()) + 2f;
        xx += f.legend(xx, y + 37f, "%", f.o().dim()) + 14f;
        xx += f.legend(xx, y + 37f, "TRIM", f.o().dim()) + 5f;
        Kit.fits(xx + f.dm(xx, y + 26.5f, defenseTrim, f.o().pitch(PITCH).cells(5).norm()), lim, "defense trim");
        float ey = y + 46f;
        f.dm(tx, ey, "EMIT", f.o().pitch(PITCH).dim());
        f.dm(tx + 45f, ey, defenseEmit, f.o().pitch(PITCH).cells(6).norm());
        f.bar(tx + 108f, ey + 1.5f, 60f, 8f, 10, defenseHeat, f.o().zone(Kit.RISING).norm());
        f.dm(tx, ey + 17f, defenseTrip, f.o().pitch(PITCH).cells(6).amber().norm());
        float sx = tx + 63f;
        sx += f.legend(sx, ey + 26f, "STUN", f.o().size(12f).lit(defenseStunned).amber().norm()) + 8f;
        f.legend(sx, ey + 26f, "DOCK", f.o().size(12f).lit(defenseDock).dim());
    }

    // ------------------------------------------------------------------ DAMAGE

    /** The plan's walls, rebuilt when the deck plan changes: x0, y0, x1, y1 in tiles from the plan's top left, y down. */
    private final FloatArray planFaces = new FloatArray();
    /** For each face, the room on its floor side, and whether it is a door. */
    private final IntArray planRoom = new IntArray();
    private final IntArray planDoor = new IntArray();
    private ShipGridComponent planGrid;
    private int planRevision = -1;
    private int planW, planH, planMinX, planMaxY;
    /** This frame: each room's trouble, and the tiles burning, smoky and breached (packed y * width + x). */
    private final IntIntMap roomTrouble = new IntIntMap();
    private final IntArray burning = new IntArray(), smoky = new IntArray(), breached = new IntArray();
    private int hitX = -1, hitY = -1;
    private int gridWidth;
    static final int FIRE = 1, SMOKE = 2, VACUUM = 4, BREACH = 8, LOW = 16;

    private void damage(HudData d) {
        ShipGridComponent g = d.ctx.ship == null ? null : d.ctx.ship.getComponent(ShipGridComponent.class);
        if (g != planGrid || g != null && g.layoutRevision != planRevision) buildPlan(g);
        roomTrouble.clear();
        burning.clear();
        smoky.clear();
        breached.clear();
        hitX = hitY = -1;
        if (g == null) return;
        gridWidth = g.width;
        Array<Compartment> rooms = g.compartments;
        int fires = 0, breaches = 0;
        for (int i = 0; i < rooms.size; i++) {
            Compartment c = rooms.get(i);
            int bits = 0;
            float p = c.pressureRatio();
            if (p < ShipAlerts.VACUUM_PRESSURE) bits |= VACUUM;
            else if (p < ShipAlerts.LOW_PRESSURE) bits |= LOW;
            boolean smoke = c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f;
            if (smoke) bits |= SMOKE;
            for (int k = 0; k < c.tiles.size; k++) {
                int packed = c.tiles.get(k);
                Tile t = g.getTile(packed % g.width, packed / g.width);
                if (t == null) continue;
                if (t.isBurning()) {
                    bits |= FIRE;
                    burning.add(packed);
                    fires++;
                }
                if (t.isHullBreach()) {
                    bits |= BREACH;
                    breached.add(packed);
                    breaches++;
                }
                if (smoke && t.isFloor()) smoky.add(packed);
            }
            if (bits != 0) roomTrouble.put(c.id, bits);
        }
        if (!Float.isNaN(d.lastHullHit.age) && d.lastHullHit.age < 1.2f) {
            hitX = d.lastHullHit.tileX;
            hitY = d.lastHullHit.tileY;
        }
        settings[Page.DAMAGE.ordinal()] = fires + breaches == 0 ? "" : (fires > 0 ? fires + " FIRE" : "") + (fires > 0 && breaches > 0 ? " " : "")
                + (breaches > 0 ? breaches + " BRCH" : "");
    }

    private void buildPlan(ShipGridComponent g) {
        planGrid = g;
        planFaces.clear();
        planRoom.clear();
        planDoor.clear();
        planW = planH = 0;
        if (g == null) return;
        planRevision = g.layoutRevision;
        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) return;
        planMinX = minX;
        planMaxY = maxY;
        planW = maxX - minX + 1;
        planH = maxY - minY + 1;
        // Each floor tile's sides that face a wall, the outside, or a thin wall: the room's outline
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Tile t = g.getTile(x, y);
                if (t == null || !t.isFloor()) continue;
                int room = t.compartment == null ? -1 : t.compartment.id;
                float u = x - minX, v = maxY - y;
                face(g, x, y, x - 1, y, u, v, u, v + 1f, room);
                face(g, x, y, x + 1, y, u + 1f, v, u + 1f, v + 1f, room);
                face(g, x, y, x, y + 1, u, v, u + 1f, v, room);
                face(g, x, y, x, y - 1, u, v + 1f, u + 1f, v + 1f, room);
            }
        }
    }

    private void face(ShipGridComponent g, int x, int y, int nx, int ny, float x0, float y0, float x1, float y1, int room) {
        Tile n = g.inBounds(nx, ny) ? g.getTile(nx, ny) : null;
        boolean floor = n != null && n.isFloor();
        EdgeWall e = g.getEdgeBetween(x, y, nx, ny);
        boolean door = false;
        if (floor) {
            if (e == null || e.isDestroyed()) return;
            door = e.hasBulkhead;
            // Between two floor tiles a thin wall is shared: drawn once, from the left or lower tile
            if (nx < x || ny < y) return;
        }
        planFaces.add(x0);
        planFaces.add(y0);
        planFaces.add(x1);
        planFaces.add(y1);
        planRoom.add(room);
        planDoor.add(door ? 1 : 0);
    }

    private void paintDamage(Filament f, float x, float y, float w, float h) {
        if (planW == 0) return;
        float p = 2f, aw = w - 16f, ah = h - 6f;
        float t = Math.max(p, (float) Math.floor(Math.min(aw / planW, ah / planH) / p) * p);
        float ox = x + 8f + (float) Math.floor((aw - planW * t) / 2f / p) * p;
        float oy = y + 2f + (float) Math.floor((ah - planH * t) / 2f / p) * p;
        float size = p * 0.78f;
        // Burning tiles: a dense checker at full; smoke: a sparse dither, dim
        for (int i = 0; i < burning.size; i++) fill(f, burning.get(i), ox, oy, t, p, size, true);
        for (int i = 0; i < smoky.size; i++) fill(f, smoky.get(i), ox, oy, t, p, size, false);
        // The walls: lit round a room with something wrong in it
        for (int i = 0; i < planRoom.size; i++) {
            int bits = roomTrouble.get(planRoom.get(i), 0);
            float lv = bits != 0 ? Drive.NORM : Drive.DIM;
            float x0 = ox + planFaces.get(4 * i) * t, y0 = oy + planFaces.get(4 * i + 1) * t;
            float x1 = ox + planFaces.get(4 * i + 2) * t, y1 = oy + planFaces.get(4 * i + 3) * t;
            int n = Math.max(1, Math.round(t / p));
            boolean door = planDoor.get(i) == 1;
            for (int k = 0; k <= n; k++) {
                // A door is its frame: the ends of its wall
                if (door && k > 0 && k < n) continue;
                f.dot(x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, f.o().size(size).level(lv));
            }
        }
        // A breach: an X across its tile
        for (int i = 0; i < breached.size; i++) {
            float bx = tileX(breached.get(i), ox, t), by = tileY(breached.get(i), oy, t);
            int n = Math.max(1, Math.round(t / p));
            for (int k = 0; k <= n; k++) {
                f.dot(bx + t * k / n, by + t * k / n, f.o().size(size).full());
                f.dot(bx + t - t * k / n, by + t * k / n, f.o().size(size).full());
            }
        }
        // The last hit on the hull: its tile flashes
        if (hitX >= 0 && gridWidth > 0) {
            int packed = hitY * gridWidth + hitX;
            float bx = tileX(packed, ox, t), by = tileY(packed, oy, t);
            for (float yy = by + p / 2f; yy < by + t; yy += p) for (float xx = bx + p / 2f; xx < bx + t; xx += p) f.dot(xx, yy, f.o().size(size).full());
        }
    }

    private float tileX(int packed, float ox, float t) {
        return ox + (packed % gridWidth - planMinX) * t;
    }

    private float tileY(int packed, float oy, float t) {
        return oy + (planMaxY - packed / gridWidth) * t;
    }

    private void fill(Filament f, int packed, float ox, float oy, float t, float p, float size, boolean fire) {
        float bx = tileX(packed, ox, t), by = tileY(packed, oy, t);
        int i = 0;
        for (float yy = by + p / 2f; yy < by + t; yy += p) {
            int j = 0;
            for (float xx = bx + p / 2f; xx < bx + t; xx += p) {
                boolean on = fire ? (i + j) % 2 == 0 : i % 2 == 0 && (j + i / 2) % 2 == 0;
                if (on) f.dot(xx, yy, fire ? f.o().size(size).full() : f.o().size(size * 0.9f).dim());
                j++;
            }
            i++;
        }
    }

    // ------------------------------------------------------------------ POWER

    private final Array<SystemRow> faults = new Array<>();

    private void power(HudData d) {
        Screen s = screen(Page.POWER);
        PowerFrame pf = d.powerFrame;
        // Each reactor's output against its rating, and the circuit's store
        int row = 0;
        for (int i = 0; i < pf.circuits.size && row < 2; i++) {
            Circuit c = pf.circuits.get(i);
            if (c.reactors == 0 && c.capacity <= 0) continue;
            // Output falls with damage, so it reads against the rating: "REACTOR   84/120 KW  STORE  62%"
            boolean low = c.ratedWatts > 0 && c.outputWatts < 0.999 * c.ratedWatts;
            s.put(row, 0, pf.circuits.size > 1 ? "CIRC " + (i + 1) : "REACTOR", Drive.DIM);
            s.right(row, 12, 4, kw(c.outputWatts), Drive.NORM, low ? Phosphor.AMBER : Phosphor.ZINC);
            s.put(row, 12, "/" + kw(c.ratedWatts) + " KW", Drive.DIM);
            if (c.capacity > 0) {
                s.put(row, 20, "STORE", Drive.DIM);
                s.right(row, 30, 4, Math.round(100f * c.charge()) + "%", Drive.NORM);
            }
            row++;
        }
        if (row == 0) {
            s.put(0, 0, "NO REACTOR", Drive.DIM);
            row = 1;
        }
        // Then the systems that are not working, those that come back by themselves first, soonest first
        faults.clear();
        faults.addAll(pf.faults);
        faults.sort((a, b) -> {
            boolean sa = selfClearing(a), sb = selfClearing(b);
            if (sa != sb) return sa ? -1 : 1;
            if (sa) return Float.compare(a.timer, b.timer);
            return Integer.compare(order(a.fault), order(b.fault));
        });
        int shown = 0;
        for (int i = 0; i < faults.size && row < 4; i++) {
            SystemRow r = faults.get(i);
            if (row == 3 && faults.size - shown > 1) {
                s.put(row, 0, "+" + (faults.size - shown) + " MORE", Drive.DIM);
                break;
            }
            s.put(row, 0, Fmt.cut(r.system, 9), Drive.NORM);
            s.put(row, 10, faultWord(r), Drive.NORM, r.fault == Fault.DESTROYED || r.fault == Fault.DISABLED ? Phosphor.AMBER : Phosphor.ZINC);
            s.put(row, 19, clears(r), Drive.DIM);
            row++;
            shown++;
        }
        settings[Page.POWER.ordinal()] = pf.ratedWatts > 0 ? kw(pf.outputWatts) + " KW" : "";
    }

    private static String kw(double watts) {
        return Long.toString(Math.round(watts / 1000.0));
    }

    private static boolean selfClearing(SystemRow r) {
        return (r.fault == Fault.SHORTED || r.fault == Fault.STUNNED) && !Float.isNaN(r.timer);
    }

    private static int order(Fault f) {
        switch (f) {
            case NO_POWER: return 0;
            case DISABLED: return 1;
            case DESTROYED: return 2;
            default: return 3;
        }
    }

    /** The state, in the words the gun chips use (8.3). */
    static String faultWord(SystemRow r) {
        switch (r.fault) {
            case DESTROYED: return "WRECKED";
            case DISABLED: return "DISABLED";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            default: return "OFF";
        }
    }

    /** What clears it: the countdown, or the work it needs. */
    static String clears(SystemRow r) {
        switch (r.fault) {
            case SHORTED:
            case STUNNED: return Float.isNaN(r.timer) ? "RESET" : Fmt.mmss(r.timer);
            case NO_POWER: return "CONDUIT";
            case DISABLED: return "REPAIR";
            case DESTROYED: return "REBUILD";
            default: return "SWITCH ON";
        }
    }

    // ------------------------------------------------------------------ ATMOS

    private final Array<Compartment> worst = new Array<>();
    private RoomNames atmosNames;
    private ShipGridComponent atmosGrid;

    private void atmos(HudData d) {
        Screen s = screen(Page.ATMOS);
        float o2 = d.hasShipStatus ? d.shipStatus.oxygen : Float.NaN;
        s.put(0, 0, "O2 TANKS", Drive.DIM);
        if (!Float.isNaN(o2)) s.right(0, 13, 4, Math.round(o2 * 100f) + "%", o2 < 0.25f ? Drive.FULL : Drive.NORM, o2 < 0.25f ? Phosphor.AMBER : Phosphor.ZINC);
        if (d.hasShipStatus && !Float.isNaN(d.shipStatus.emergencyOxygen) && o2 <= 0.001f) {
            s.put(0, 15, "EMERG", Drive.DIM);
            s.right(0, 25, 4, Math.round(d.shipStatus.emergencyOxygen * 100f) + "%", Drive.NORM, Phosphor.AMBER);
        }
        // The worst rooms: vacuum, then low pressure, then smoke
        ShipGridComponent g = d.ctx.ship == null ? null : d.ctx.ship.getComponent(ShipGridComponent.class);
        RoomNames names = d.roomNames();
        worst.clear();
        if (g != null && names != null) {
            Array<Compartment> rooms = names.rooms(g);
            for (int i = 0; i < rooms.size; i++) {
                Compartment c = rooms.get(i);
                if (c.pressureRatio() < 0.9f || c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f || c.oxygenRatio() < 0.8f) worst.add(c);
            }
            worst.sort((a, b) -> Float.compare(a.pressureRatio() - smokeWeight(a), b.pressureRatio() - smokeWeight(b)));
        }
        if (worst.size == 0) {
            s.put(1, 0, "ALL ROOMS", Drive.DIM);
            s.right(1, 17, 7, "101 KPA", Drive.DIM);
        }
        for (int i = 0; i < worst.size && i < 3; i++) {
            Compartment c = worst.get(i);
            int r = i + 1;
            if (i == 2 && worst.size > 3) {
                s.put(r, 0, "+" + (worst.size - 2) + " MORE", Drive.DIM);
                break;
            }
            float p = c.pressureRatio();
            boolean bad = p < ShipAlerts.LOW_PRESSURE;
            s.put(r, 0, Fmt.cut(names.of(g, c), 10), bad ? Drive.NORM : Drive.DIM);
            s.right(r, 15, 4, Long.toString(Math.round(c.pressure())), Drive.NORM, bad ? Phosphor.AMBER : Phosphor.ZINC);
            s.put(r, 15, "KPA", Drive.DIM);
            s.right(r, 23, 4, Math.round(Math.min(c.oxygenRatio(), 9.99f) * 100f) + "%", Drive.NORM);
            s.put(r, 23, "O2", Drive.DIM);
            if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) s.put(r, 26, "SMK", Drive.NORM, Phosphor.AMBER);
        }
        float cabin = d.hasShipStatus ? d.shipStatus.cabinPressure : Float.NaN;
        settings[Page.ATMOS.ordinal()] = Float.isNaN(cabin) ? "" : Math.round(cabin * 101.3f) + " KPA";
    }

    private static float smokeWeight(Compartment c) {
        return c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f ? 0.3f : 0f;
    }

    // ------------------------------------------------------------------ NAV

    private boolean orbit, impact;

    private void nav(HudData d) {
        Screen s = screen(Page.NAV);
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        orbit = o != null;
        impact = o != null && o.impactWarning;
        if (o == null) {
            s.put(0, 0, "NO ORBIT", Drive.DIM);
            return;
        }
        settings[Page.NAV.ordinal()] = Fmt.cut(o.bodyName, 12);
        orbitRow(s, 0, "ALT", o.altitude);
        orbitRow(s, 1, "AP", o.apoapsis);
        orbitRow(s, 2, "PE", o.periapsis);
        s.put(3, 0, "PER", Drive.DIM);
        s.right(3, 13, 9, Double.isInfinite(o.period) ? "ESCAPE" : Fmt.mmss(o.period), Drive.NORM);
        if (impact) s.put(3, 14, "IMPACT", Drive.FULL, Phosphor.AMBER);
    }

    private static void orbitRow(Screen s, int r, String label, double meters) {
        s.put(r, 0, label, Drive.DIM);
        s.right(r, 13, 9, Fmt.fit(meters / 1000.0, 7, 1), Drive.NORM);
        s.put(r, 14, "KM", Drive.DIM);
    }

    /** The orbit's sketch: the body, the path round it, and the ship on it. */
    private void paintOrbit(Filament f, float cx, float cy) {
        if (!orbit) return;
        circle(f, cx, cy, 30f, 3.2f, 1.2f, impact ? Phosphor.AMBER : Phosphor.ZINC, Drive.DIM);
        for (float r = 2f; r <= 10f; r += 2.4f) circle(f, cx, cy, r, 2.2f, 1.2f, Phosphor.ZINC, Drive.NORM);
        f.dot(cx, cy - 30f, f.o().size(3f).full());
    }

    /** Dots round a circle behind the glass, about every {@code step} units. */
    private static void circle(Filament f, float cx, float cy, float r, float step, float size, Phosphor color, float level) {
        int n = Math.max(6, Math.round(com.badlogic.gdx.math.MathUtils.PI2 * r / step));
        for (int i = 0; i < n; i++) {
            float a = i / (float) n * com.badlogic.gdx.math.MathUtils.PI2;
            f.dot(cx + com.badlogic.gdx.math.MathUtils.cos(a) * r, cy + com.badlogic.gdx.math.MathUtils.sin(a) * r, f.o().size(size).color(color).level(level));
        }
    }

    // ------------------------------------------------------------------ PLAN

    private void plan(HudData d) {
        Screen s = screen(Page.PLAN);
        NavReadout nav = d.navReadout;
        Orders o = d.orderState;
        if (nav.burnsPlanned > 0 || nav.burning) {
            int n = Math.max(1, nav.burnNumber), of = Math.max(n, nav.burnsPlanned);
            s.put(0, 0, "BURN " + n + "/" + of, Drive.NORM);
            if (nav.burning) {
                s.put(0, 10, "TO GO", Drive.DIM);
                s.right(0, 22, 5, Fmt.fit(nav.burnToGo, 5, 0), Drive.FULL);
            } else {
                s.put(0, 10, "IN", Drive.DIM);
                float in = !Float.isNaN(nav.burnStartIn) ? nav.burnStartIn : nav.burnIn;
                s.right(0, 19, 7, Fmt.mmss(in), Drive.NORM);
                s.right(0, 24, 4, Fmt.fit(nav.burnDeltaV, 4, 0), Drive.NORM);
            }
            s.put(0, 25, "M/S", Drive.DIM);
            settings[Page.PLAN.ordinal()] = n + " OF " + of;
        } else {
            s.put(0, 0, "NO BURNS", Drive.DIM);
        }
        s.put(1, 0, "TOTAL", Drive.DIM);
        s.right(1, 11, 5, nav.planDeltaV > 0 ? Fmt.fit(Math.min(nav.planDeltaV, 99_999), 5, 0) : "", Drive.NORM);
        s.put(1, 13, "LEFT", Drive.DIM);
        double left = d.flightStatus.deltaVLeft;
        boolean short_ = !Double.isNaN(left) && nav.planDeltaV > left;
        s.right(1, 23, 5, Double.isNaN(left) ? "" : Fmt.fit(Math.min(left, 99_999), 5, 0), Drive.NORM, short_ ? Phosphor.AMBER : Phosphor.ZINC);
        s.put(1, 25, "M/S", Drive.DIM);
        if (nav.proposal) s.put(2, 0, "TRANSFER PROPOSED", Drive.NORM);
        if (o.autopilot != Autopilot.OFF) {
            s.put(3, 0, o.autopilot == Autopilot.HELD ? "AUTO HELD" : "AUTO", Drive.NORM, o.autopilot == Autopilot.HELD ? Phosphor.AMBER : Phosphor.ZINC);
            String why = o.autopilot == Autopilot.HELD ? o.autopilotHeld : o.autopilotTo;
            s.put(3, 10, Fmt.cut(why, 20), Drive.DIM);
        } else if (!o.autopilotRefusal.isEmpty()) {
            s.put(3, 0, "AUTO", Drive.DIM);
            s.put(3, 5, Fmt.cut(o.autopilotRefusal, 25), Drive.DIM);
        }
    }

    // ------------------------------------------------------------------ HELM

    /** The orders the page keys (9.2: orders 1 to 6, and 0), by their Action names, keys and words. */
    static final String[] ORDER_ACTIONS = {"HELM_FLY_BURNS", "HELM_MATCH_VELOCITY", "HELM_HOLD", "HELM_APPROACH", "HELM_DOCK",
            "AUTOPILOT", "HELM_CANCEL"};
    static final String[] ORDER_KEYS = {"1", "2", "3", "4", "5", "6", "0"};
    static final String[] ORDER_WORDS = {"BURNS", "MATCH", "HOLD", "APPROACH", "DOCK", "AUTO", "CANCEL"};
    /** The helm order each key gives; null for the autopilot, which is not a helm order. */
    static final HelmOrder[] ORDER_GIVEN = {HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION,
            HelmOrder.APPROACH, HelmOrder.DOCK, null, HelmOrder.NONE};

    private void helm(HudData d) {
        Screen s = screen(Page.HELM);
        Orders o = d.orderState;
        boolean you = d.ctx.presence == com.ringsofiron.uihud.providers.UiContextProvider.Presence.HELM;
        String who = you ? "YOU" : d.ctx.helmsman;
        // Who has the helm, and why it won't answer
        s.put(0, 0, "HELM", Drive.DIM);
        s.put(0, 5, who.isEmpty() ? "NOBODY" : Fmt.cut(who, 10), who.isEmpty() ? Drive.DIM : Drive.NORM);
        String fault = helmFault(d.ctx.helmFault);
        if (!fault.isEmpty()) s.right(0, COLS, 14, fault, Drive.NORM, Phosphor.AMBER);
        else if (o.helm != HelmOrder.NONE && helmWaits(o.helmLine)) s.right(0, COLS, 14, "WAITS", Drive.NORM, Phosphor.AMBER);
        // The order in force and how it is going, in the game's words
        if (o.helm != HelmOrder.NONE) s.put(1, 0, Fmt.cut(helmProgress(o), COLS), Drive.NORM);
        else s.put(1, 0, "NO ORDER", Drive.DIM);
        // The orders' keys, flowed over the last two rows: lit where they would be taken, ghosted where
        // refused, the one in force at full
        int row = 2, col = 0;
        for (int i = 0; i < ORDER_KEYS.length; i++) {
            String key = d.key(ORDER_ACTIONS[i]);
            if (key.length() != 1) key = ORDER_KEYS[i];
            String word = ORDER_WORDS[i];
            int w = 1 + word.length();
            if (col + w > COLS) {
                if (++row >= s.rows) break;
                col = 0;
            }
            HelmOrder given = ORDER_GIVEN[i];
            boolean inForce, ok;
            if (given == null) {
                inForce = o.autopilot != Autopilot.OFF;
                ok = inForce || o.autopilotRefusal.isEmpty();
            } else {
                String refusal = o.helmRefusal[given.ordinal()];
                inForce = given != HelmOrder.NONE && o.helm == given;
                ok = refusal == null || refusal.isEmpty();
            }
            float refused = Drive.GHOST * 2f;
            s.key(row, col, key.toUpperCase(Locale.ROOT), inForce ? Drive.FULL : ok ? Drive.NORM : refused);
            s.put(row, col + 1, word, inForce ? Drive.NORM : ok ? Drive.DIM : refused);
            col += w + 1;
        }
        settings[Page.HELM.ordinal()] = d.ctx.burnFlown ? "BURNING" : "";
    }

    /** Why the helm won't answer, in a word or two: "the helm has no power" is "NO POWER". */
    static String helmFault(String fault) {
        if (fault == null || fault.isEmpty()) return "";
        String f = fault.toLowerCase(Locale.ROOT);
        for (String lead : new String[]{"the helm is ", "the helm has ", "the helm "}) {
            if (f.startsWith(lead)) {
                f = f.substring(lead.length());
                break;
            }
        }
        return Fmt.cut(f, 14);
    }

    /** The order waits: the player has the helm, or nobody does. */
    static boolean helmWaits(String line) {
        return line != null && line.startsWith("Helm order (") && line.contains(") waits");
    }

    /**
     * How the order is going, from the game's line without its speaker: "Helm: approaching Highport to
     * 500 m" is "APPROACHING HIGHPORT TO 500 M", and an order that waits is named by what it will do.
     */
    static String helmProgress(Orders o) {
        String line = o.helmLine == null ? "" : o.helmLine;
        if (helmWaits(line)) return line.substring("Helm order (".length(), line.indexOf(") waits")).toUpperCase(Locale.ROOT);
        int colon = line.indexOf(": ");
        if (line.startsWith("Helm: ") || line.startsWith("Flight computer: ")) return line.substring(colon + 2).toUpperCase(Locale.ROOT);
        if (!line.isEmpty()) return line.toUpperCase(Locale.ROOT);
        return orderName(o.helm) + (o.helmTarget.isEmpty() ? "" : " " + o.helmTarget.toUpperCase(Locale.ROOT));
    }

    static String orderName(HelmOrder h) {
        switch (h) {
            case FLY_BURNS: return "FLY BURNS";
            case MATCH_VELOCITY: return "MATCH VELOCITY";
            case HOLD_POSITION: return "HOLD POSITION";
            case APPROACH: return "APPROACH";
            case DOCK: return "DOCK";
            default: return "";
        }
    }

    // ------------------------------------------------------------------ DOCK

    private void dock(HudData d) {
        Screen s = screen(Page.DOCK);
        DockingApproach a = d.docking;
        if (!a.present || a.ports == 0) {
            s.put(0, 0, "NO DOCKING PORT", Drive.DIM);
            return;
        }
        s.put(0, 0, "PORT " + a.port + "/" + a.ports, Drive.NORM);
        s.put(0, 9, Fmt.cut(a.portSize, 6), Drive.DIM);
        s.put(0, 17, "ARMED", a.armed ? Drive.NORM : Drive.GHOST * 2f);
        if (a.collarTorn) s.put(0, 23, "TORN", Drive.FULL, Phosphor.AMBER);
        else if (a.collarJammed) s.put(0, 23, "JAMMED", Drive.FULL, Phosphor.AMBER);
        if (!a.other.isEmpty()) {
            s.put(1, 0, Fmt.cut(a.other, 12), Drive.NORM);
            if (a.otherPort > 0) s.put(1, 13, "P" + a.otherPort, Drive.DIM);
            s.put(1, 17, clearance(a), Drive.NORM, a.clearance == com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance.DENIED
                    || a.clearance == com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance.NO_PORT ? Phosphor.AMBER : Phosphor.ZINC);
            settings[Page.DOCK.ordinal()] = Fmt.cut(a.other, 12);
        }
        if (!a.refusal.isEmpty()) s.put(2, 0, Fmt.cut(a.refusal, COLS), Drive.DIM);
        String state = a.capture == Capture.DOCKED ? "DOCKED" : a.capture == Capture.CAPTURED ? "CAPTURED" : a.capture == Capture.APPROACH ? "APPROACH" : "FREE";
        s.put(3, 0, state, Drive.NORM);
        if (d.ctx.latched) {
            s.put(3, 9, "LATCHED", Drive.NORM);
            if (!Float.isNaN(a.strain)) {
                s.put(3, 17, "STRAIN", Drive.DIM);
                s.right(3, 28, 4, Math.round(a.strain * 100f) + "%", Drive.NORM, a.strain > 0.7f ? Phosphor.AMBER : Phosphor.ZINC);
            }
        } else if (a.capture == Capture.APPROACH && !Float.isNaN(a.range)) {
            s.put(3, 9, "RANGE", Drive.DIM);
            s.right(3, 21, 6, range6(a.range), Drive.NORM);
        }
    }

    private static String clearance(DockingApproach a) {
        switch (a.clearance) {
            case GRANTED: return Float.isNaN(a.clearanceLeft) ? "CLEARED" : "CLEAR " + Fmt.mmss(a.clearanceLeft);
            case DENIED: return "DENIED";
            case NO_PORT: return "NO PORT";
            case YIELDS: return "YIELDS";
            default: return "";
        }
    }

    // ------------------------------------------------------------------ COMMS

    private void comms(HudData d) {
        Screen s = screen(Page.COMMS);
        StationInfo st = d.navReadout.docked.present ? d.navReadout.docked : d.navReadout.station;
        if (!st.present) {
            s.put(0, 0, "NO STATION IN RANGE", Drive.DIM);
        } else {
            s.put(0, 0, Fmt.cut(st.name, 14), Drive.NORM);
            s.put(0, 15, Fmt.cut(st.owner, 15), Drive.DIM);
            s.put(1, 0, "STANDING", Drive.DIM);
            s.put(1, 9, Fmt.cut(st.standingName, 12), Drive.NORM);
            if (!Float.isNaN(st.standing)) s.right(1, 30, 5, Fmt.fit(st.standing, 4, 0), Drive.DIM);
            int c = 0;
            c = service(s, c, "MARKET", st.market);
            c = service(s, c, "YARD", st.yard || st.shipyard);
            c = service(s, c, "JOBS", st.contracts);
            c = service(s, c, "HIRE", st.hiring);
            service(s, c, "MED", st.infirmary);
            if (!st.refusal.isEmpty()) s.put(3, 0, Fmt.cut(st.refusal, COLS), Drive.NORM, Phosphor.AMBER);
            settings[Page.COMMS.ordinal()] = d.ctx.docked ? "DOCKED" : "";
        }
        Orders o = d.orderState;
        if (st.present && !st.refusal.isEmpty()) return;
        if (!o.towRefusal.isEmpty()) {
            s.put(3, 0, "TOW", Drive.DIM);
            s.put(3, 4, Fmt.cut(o.towRefusal, 26), Drive.DIM);
        } else if (o.towFee > 0) {
            s.put(3, 0, "TOW", Drive.DIM);
            s.put(3, 4, Math.round(o.towFee) + " CR", Drive.NORM);
        }
    }

    private static int service(Screen s, int c, String word, boolean on) {
        s.put(2, c, word, on ? Drive.NORM : Drive.GHOST * 2f);
        return c + word.length() + 1;
    }

    // ------------------------------------------------------------------ CREW

    private void crew(HudData d) {
        Screen s = screen(Page.CREW);
        Array<Seat> seats = d.stations.seats;
        int row = 0;
        for (int i = 0; i < seats.size && row < 4; i++) {
            Seat seat = seats.get(i);
            if (row == 3 && seats.size > 4) {
                s.put(3, 0, "+" + (seats.size - 3) + " POSTS", Drive.DIM);
                break;
            }
            s.put(row, 0, Fmt.cut(seat.name, 9), Drive.DIM);
            String who = seat.hand == Hand.PLAYER ? "YOU" : seat.hand == Hand.NOBODY ? "" : Fmt.cut(seat.who, 10);
            s.put(row, 10, who, seat.hand == Hand.NOBODY ? Drive.DIM : Drive.NORM);
            if (seat.hand == Hand.COMING) s.put(row, 21, "COMING", Drive.DIM);
            else if (seat.hand == Hand.CREW && seat.working) s.put(row, 21, "ON IT", Drive.DIM);
            row++;
        }
        if (seats.size == 0) s.put(0, 0, "NO POSTS", Drive.DIM);
        settings[Page.CREW.ordinal()] = d.stations.crewAboard + " ABOARD" + (d.ctx.crewOutside ? " +EVA" : "");
    }

    // ------------------------------------------------------------------ CONTRACTS

    private void contracts(HudData d) {
        Screen s = screen(Page.CONTRACTS);
        Array<Objective> all = d.objectiveFrame.objectives;
        int row = 0;
        if (!d.objectiveFrame.operation.isEmpty()) {
            s.put(0, 0, Fmt.cut(d.objectiveFrame.operation, 22), Drive.NORM);
            if (d.objectiveFrame.operationSteps > 0) {
                s.right(0, 30, 6, d.objectiveFrame.operationStep + "/" + d.objectiveFrame.operationSteps, Drive.DIM);
            }
            row = 1;
        }
        for (int i = 0; i < all.size && row < 4; i++) {
            Objective o = all.get(i);
            if (row == 3 && all.size - i > 1) {
                s.put(3, 0, "+" + (all.size - i) + " MORE", Drive.DIM);
                break;
            }
            s.put(row, 0, Fmt.cut(o.title, 20), Drive.NORM);
            String left = Double.isNaN(o.timeLeft) ? "" : Fmt.mmss(o.timeLeft);
            s.right(row, 30, 8, left, o.late ? Drive.FULL : Drive.DIM, o.late ? Phosphor.AMBER : Phosphor.ZINC);
            row++;
        }
        if (row == 0) s.put(0, 0, "NO CONTRACTS HELD", Drive.DIM);
        settings[Page.CONTRACTS.ordinal()] = all.size > 0 ? all.size + " HELD" : "";
    }
}
