package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.ContactProvider.State;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * TGT, the target card in the right column (functional-task 6, TGT): one subject, the locked target
 * when there is one, else the navigation target, with the navigation target in one line under the
 * card when both are set. For a ship: the five relations as fixed words with HOSTILE behind red; the
 * name and the contract it belongs to; what it is; FIRING ON YOU and HAS YOU LOCKED; its state; its
 * hull as the sensors guess it in four steps and a word, never a number; range with closing or
 * opening and the rate; the closest approach and when. For a station: its owner and your standing,
 * its services, clearance, range and closing. In rails warp only range and closest approach; while
 * FLT docks, only the identity lines.
 */
final class TargetCard {
    static final String[] RELATIONS = {"OWN", "FRIEND", "NEUTRAL", "HOSTILE", "INERT"};
    static final int OWN = 0, FRIEND = 1, NEUTRAL = 2, HOSTILE = 3, INERT = 4;
    static final String[] HULL = {"CRIPPLED", "HEAVILY DAMAGED", "DAMAGED", "INTACT"};
    static final String[] SERVICES = {"MARKET", "YARD", "SHIPS", "JOBS", "HIRING"};
    /** A station's clearance shows within this, m. */
    static final float CLEARANCE_RANGE = 50_000f;
    static final int NAME = 14;

    boolean on, station, brief, warpOnly, rangeShown, cpaShown, firing, locked, struck;
    int rel = -1, hullSteps = -1;
    String name = "", contract = "", cls = "", state = "", hullWord = "", range = "", unit = "KM", motion = "", rate = "",
            cpa = "", cpaUnit = "KM", cpaT = "", owner = "", clearance = "", navLine = "";
    final boolean[] services = new boolean[SERVICES.length];

    /** {@code docking}: FLT is in DOCK mode and has the range and speeds. */
    void update(HudData d, Posts p, boolean docking) {
        TargetStatus t = d.targetStatus;
        NavReadout n = d.navReadout;
        boolean lock = t.present, nav = n.present;
        on = p.targetCard(lock || nav) && !(p.docked && !lock && nav && n.kind == NavigationProvider.Kind.STATION && sameName(n.name, d.ctx.stationName));
        if (!on) return;
        warpOnly = p.rails;
        firing = locked = struck = false;
        contract = owner = clearance = cls = state = hullWord = "";
        hullSteps = -1;
        for (int i = 0; i < services.length; i++) services[i] = false;
        if (lock) {
            ship(d, t, p);
            navLine = nav && !sameName(n.name, t.label) ? navLine(n) : "";
        } else {
            nav(d, n);
            navLine = "";
        }
        brief = docking && station;
    }

    private void ship(HudData d, TargetStatus t, Posts p) {
        Contact c = d.contactFrame.locked();
        station = c != null && c.kind == Kind.STATION;
        name = Fmt.name(t.label, NAME);
        rel = relation(t.relation, c);
        cls = c == null ? "" : kindWord(c.kind);
        locked = t.firingOnUs;
        firing = t.firingOnUs && p.combat;
        state = state(t, c, p.combat);
        struck = c != null && c.state == State.STRUCK || "struck".equalsIgnoreCase(t.state);
        if (!Float.isNaN(t.hull)) {
            hullSteps = hullSteps(t.hull);
            hullWord = HULL[hullSteps - 1];
        }
        range(t.range, t.closing);
        cpa(t.cpaDistance, t.cpaTime, t.closing);
        contract = contract(d, t.label);
    }

    private void nav(HudData d, NavReadout n) {
        station = n.kind == NavigationProvider.Kind.STATION;
        name = Fmt.name(n.name, NAME);
        rel = n.kind == NavigationProvider.Kind.WRECK ? INERT
                : n.kind == NavigationProvider.Kind.BODY || n.kind == NavigationProvider.Kind.POINT ? -1 : relation(n.relation, null);
        cls = navKind(n.kind);
        range(n.distance, n.closing);
        cpa(n.cpaDistance, n.cpaIn, n.closing);
        contract = n.contract.isEmpty() ? contract(d, n.name) : Fmt.cut(n.contract, 8);
        if (station) {
            StationInfo s = n.station;
            owner = s.owner.isEmpty() ? "" : Fmt.cut(s.owner, 16);
            if (!s.standingName.isEmpty()) owner = Fmt.cut((owner.isEmpty() ? "" : owner + "  ") + s.standingName, 30);
            services[0] = s.market;
            services[1] = s.yard;
            services[2] = s.shipyard;
            services[3] = s.contracts;
            services[4] = s.hiring;
            if (!Double.isNaN(n.distance) && n.distance <= CLEARANCE_RANGE) clearance = clearance(d, n.name, s);
        }
    }

    /** The relation class (9.4): own, friend, neutral, hostile, inert; -1 when unknown. */
    static int relation(Relation r, Contact c) {
        if (c != null && (c.kind == Kind.DERELICT || c.state == State.WRECKED)) return INERT;
        switch (r) {
            case HOSTILE:
            case MISSILE: return HOSTILE;
            case FRIENDLY: return FRIEND;
            case NEUTRAL: return NEUTRAL;
            default: return -1;
        }
    }

    /** Its state in the spec's words: fighting, running, disabled with why, struck, wrecked, derelict, under way. */
    static String state(TargetStatus t, Contact c, boolean combat) {
        String s = t.state == null ? "" : t.state.toLowerCase(Locale.ROOT);
        if (c != null && c.state == State.WRECKED) return "WRECKED";
        if (c != null && c.kind == Kind.DERELICT) return "DERELICT";
        // The markers say "struck", "silenced", "running" or "fit"; anything else is a disablement's reason
        boolean known = s.isEmpty() || s.equals("fit") || s.equals("running") || s.equals("struck") || s.equals("silenced");
        if (c != null && c.state == State.DISABLED || !known) {
            String why = c == null ? "" : c.disabledReason;
            return why.isEmpty() ? "DISABLED" : Fmt.cut("DISABLED: " + why, 28);
        }
        if (c != null && c.state == State.STRUCK || s.equals("struck")) return "STRUCK";
        if (c != null && c.state == State.SILENCED || s.equals("silenced")) return "SILENCED";
        if (c != null && c.state == State.RUNNING || s.equals("running")) return "RUNNING";
        if (t.relation == Relation.HOSTILE && (t.firingOnUs || combat)) return "FIGHTING";
        return "UNDER WAY";
    }

    /** The hull's four steps from the sensors' share: crippled, heavily damaged, damaged, intact. */
    static int hullSteps(float hull) {
        if (hull >= 0.9f) return 4;
        if (hull >= 0.6f) return 3;
        if (hull >= 0.3f) return 2;
        return 1;
    }

    private void range(double meters, float closing) {
        rangeShown = !Double.isNaN(meters);
        range = Fmt.range(meters, 3);
        unit = Fmt.km(meters) ? "KM" : "M";
        motion = Float.isNaN(closing) || Math.abs(closing) < 0.5f ? "" : closing > 0f ? "CLOSING" : "OPENING";
        rate = motion.isEmpty() ? "" : Fmt.fit(Math.round(Math.abs(closing)), 3, 0);
    }

    /** The closest approach, only while it is passing: closing, with the closest point still ahead. */
    private void cpa(double meters, float seconds, float closing) {
        cpaShown = !Double.isNaN(meters) && !Float.isNaN(seconds) && seconds > 0f && !Float.isNaN(closing) && closing > 0f;
        cpa = cpaShown ? Fmt.range(meters, 3) : "";
        cpaUnit = Fmt.km(meters) ? "KM IN" : "M IN";
        cpaT = cpaShown ? Fmt.mmss(seconds) : "";
    }

    /** The contract a ship or place belongs to, by its name in a held contract's title or destination. */
    static String contract(HudData d, String label) {
        if (label == null || label.isEmpty()) return "";
        String l = Fmt.bare(label).toLowerCase(Locale.ROOT);
        if (!l.isEmpty()) {
            for (Objective o : d.objectiveFrame.objectives) {
                if (o.title.toLowerCase(Locale.ROOT).contains(l) || o.next.toLowerCase(Locale.ROOT).contains(l)) {
                    return Fmt.cut(o.kind.isEmpty() ? "CONTRACT" : o.kind, 8);
                }
            }
        }
        // A waypoint the game tags as a contract's, which no objective names
        return label.trim().toLowerCase(Locale.ROOT).endsWith("[contract]") ? "CONTRACT" : "";
    }

    private static String clearance(HudData d, String stationName, StationInfo s) {
        if (!s.refusal.isEmpty()) return Fmt.cut("REFUSED: " + s.refusal, 30);
        if (!d.docking.present || !sameName(d.docking.other, stationName)) return "";
        Clearance c = d.docking.clearance;
        switch (c) {
            case GRANTED: return "CLEARED, PORT " + Math.max(1, d.docking.otherPort);
            case DENIED: return d.docking.refusal.isEmpty() ? "REFUSED" : Fmt.cut("REFUSED: " + d.docking.refusal, 30);
            case NO_PORT: return "NO FREE PORT";
            case YIELDS: return "HOLD: TRAFFIC";
            default: return "NOT ASKED";
        }
    }

    static String kindWord(Kind k) {
        switch (k) {
            case STATION: return "STATION";
            case DERELICT: return "DERELICT";
            case EVA: return "CREW OUTSIDE";
            case MISSILE: return "MISSILE";
            default: return "SHIP";
        }
    }

    static String navKind(NavigationProvider.Kind k) {
        switch (k) {
            case STATION: return "STATION";
            case SHIP: return "SHIP";
            case WRECK: return "WRECK";
            case BODY: return "BODY";
            default: return "WAYPOINT";
        }
    }

    /** The navigation target in one line: "NAV  HIGHPORT  3.0 KM". */
    static String navLine(NavReadout n) {
        String r = Fmt.rangeText(n.distance);
        return "NAV  " + Fmt.name(n.name, 16) + (r.isEmpty() ? "" : "  " + r);
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null && !a.isEmpty() && Fmt.bare(a).equalsIgnoreCase(Fmt.bare(b));
    }

    /** The card's height: identity alone while FLT docks or in warp; a station's or a ship's full card. */
    float height() {
        if (!on) return 0f;
        int rows = brief ? 3 : warpOnly ? 4 : station ? 6 : 8;
        return 8f + rows * Geo.TGT_ROW - 2f + (navLine.isEmpty() ? 0f : Geo.TGT_ROW);
    }

    // ------------------------------------------------------------------ painting, from the right group's origin

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.RX, y = Geo.TGT_Y, w = Geo.CW, L = x + 6f, v0 = x + 56f;
        f.slab(x, y, w, height(), 5f, 2, true);
        // The relations: five fixed words, only the true one lit, HOSTILE behind red glass
        float cx = L;
        for (int i = 0; i < RELATIONS.length; i++) {
            boolean lit = rel == i;
            Filament.Opt o = f.o().lit(lit).norm();
            if (i == HOSTILE) o.red();
            cx += f.legend(cx, row(y, 0), RELATIONS[i], o) + 9f;
        }
        Kit.fits(cx - 9f, x + w - 6f, "TGT relations");
        // The name, big, and the contract it belongs to at the end of the line
        float cw = f.dmWidth(8, f.o().pitch(Geo.P));
        f.dm(L, row(y, 1) - 14f, name, f.o().pitch(2f).cells(NAME).full());
        f.icon("target", x + w - 6f - cw - 18f, row(y, 1) - 12f, 13f, f.o().lit(!contract.isEmpty()).norm());
        f.dm(x + w - 6f - cw, row(y, 1) - 10.5f, contract, f.o().pitch(Geo.P).cells(8).norm());
        f.dm(L, row(y, 2) - 10.5f, station ? owner.isEmpty() ? cls : owner : cls, f.o().pitch(Geo.P).cells(Geo.fit(L, x + w - 6f)).dim());
        int r = 3;
        if (!brief && !warpOnly) {
            if (station) {
                float sx = L;
                for (int i = 0; i < SERVICES.length; i++) sx += f.legend(sx, row(y, r), SERVICES[i], f.o().lit(services[i]).norm()) + 10f;
                Kit.fits(sx - 10f, x + w - 6f, "TGT services");
                r++;
                f.legend(L, row(y, r), "DOCK", f.o().dim());
                f.dm(v0, row(y, r) - 10.5f, clearance, f.o().pitch(Geo.P).cells(Geo.fit(v0, x + w - 6f)).norm());
                r++;
            } else {
                float fx = L + f.legend(L, row(y, r), "FIRING ON YOU", f.o().red().lit(firing).full()) + 12f;
                f.legend(fx, row(y, r), "HAS YOU LOCKED", f.o().red().lit(locked).norm());
                r++;
                f.legend(L, row(y, r), "STATE", f.o().dim());
                f.dm(v0, row(y, r) - 10.5f, state, f.o().pitch(Geo.P).cells(Geo.fit(v0, x + w - 6f)).level(struck ? Drive.FULL : Drive.NORM));
                r++;
                // Hull as the sensors guess it: four steps, never a percentage the sensors can't know
                f.legend(L, row(y, r), "HULL", f.o().dim());
                f.bar(v0, row(y, r) - 9f, 52f, 9f, 4, hullSteps < 0 ? 0f : hullSteps / 4f, f.o().norm().gap(2f));
                f.dm(v0 + 60f, row(y, r) - 10.5f, struck ? "FIRING LIFTS SURRENDER" : hullSteps < 0 ? "UNKNOWN" : hullWord,
                        f.o().pitch(Geo.P).cells(Geo.fit(v0 + 60f, x + w - 30f)).level(struck ? Drive.FULL : Drive.NORM));
                f.legend(x + w - 6f, row(y, r), "EST", f.o().dim().right());
                r++;
            }
        }
        if (!brief) {
            f.legend(L, row(y, r), "RANGE", f.o().dim());
            float rx = v0 + f.seg7(v0, row(y, r) - 15f, 15f, range, f.o().cells(3).right().norm()) + 5f;
            rx += f.legend(rx, row(y, r), unit, f.o().dim().lit(rangeShown)) + 14f;
            rx += f.dm(rx, row(y, r) - 10.5f, motion, f.o().pitch(Geo.P).cells(7).norm()) + 8f;
            rx += f.seg7(rx, row(y, r) - 13f, 13f, rate, f.o().cells(3).right().norm()) + 5f;
            Kit.fits(rx + f.legend(rx, row(y, r), "M/S", f.o().dim().lit(!rate.isEmpty())), x + w - 6f, "TGT range row");
            r++;
            if (!station || warpOnly) {
                f.legend(L, row(y, r), "CPA", f.o().dim());
                float px = v0 + f.seg7(v0, row(y, r) - 13f, 13f, cpa, f.o().cells(3).right().norm()) + 5f;
                px += f.legend(px, row(y, r), cpaUnit, f.o().dim().lit(cpaShown)) + 6f;
                Kit.fits(px + f.seg7(px, row(y, r) - 13f, 13f, cpaT, f.o().cells(5).colons(2).norm()), x + w - 6f, "TGT CPA row");
                r++;
            }
        }
        if (!navLine.isEmpty()) {
            f.rectLit(x + 6f, row(y, r) - 16f, w - 12f, 0.7f, f.o().ghostOnly().k(2f));
            f.dm(L, row(y, r) - 10.5f, navLine, f.o().pitch(Geo.P).cells(Geo.fit(L, x + w - 6f)).norm().color(Phosphor.ZINC));
        }
    }

    /** Row {@code i}'s baseline. */
    private static float row(float y, int i) {
        return y + 18f + i * Geo.TGT_ROW;
    }
}
