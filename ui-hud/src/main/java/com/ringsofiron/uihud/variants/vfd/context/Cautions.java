package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * P2, the caution and warning panel (functional-context 6, P2): every condition in the catalogue that
 * holds, one row a code, in the catalogue's short words, ranked; which of them P2 lists (a condition
 * an instrument on screen shows as its own reading is that instrument's); H4's four lights; and the
 * master alert cue. Pure: tested without a screen.
 *
 * <p>The game raises the alerts it knows ({@code GameAlerts}), with the crew on each; the catalogue
 * has more, which are worked out here from the data: NO POWER, POWER LOW, IMPACT, PLAN SHORT, NO
 * HELM, CREW OUT, FAST APPR, DOCK STRAIN and the shield's down and weak states.
 */
final class Cautions {
    /** A row's handling: needs the captain, crew on it (a dot each), no handling, clears by itself. */
    enum Handling {
        NONE, BACK, CREW, YOU
    }

    /** H4's lights, in order (three on a hull with no shield emitter). */
    enum Light {
        HULL, SHIELDS, POWER, LIFE_SUP
    }

    /** P2 shows six rows: up to five conditions, and a sixth that names the rest. */
    static final int ROWS = 6;
    /** A new warning row pulses this long; the master caution goes out by itself after the second. */
    static final float PULSE = 2f, CAUTION_CUE = 5f;
    /** A burn this close makes crew outside or an empty helm a condition, s. */
    static final float BURN_SOON = 60f;
    /** POWER LOW: the reactors under this share of their rating, or the store under the second for 3 s. */
    static final float OUTPUT_LOW = 0.6f, STORE_LOW = 0.25f, STORE_LOW_FOR = 3f;
    /** SHIELD WEAK: the cloud under this share of its capacity. */
    static final float CLOUD_THIN = 0.25f;
    /** DOCK STRAIN: strain over this. */
    static final float STRAIN = 0.5f;
    /** Turning faster than this with nobody at the helm, deg/s. */
    static final float TURNING = 2f;

    /** One row: a code, its parts merged. */
    static final class Row {
        String code = "", detail = "";
        Level level = Level.CAUTION;
        Handling handling = Handling.NONE;
        int crew;
        /** Seconds on its timer, NaN with none: to harm, or with {@link Handling#BACK} to recovery. */
        float timer = Float.NaN;
        boolean harm;
        /** Sim seconds it was first seen in this run of it. */
        double since;
        /** P2 lists it: no instrument on screen shows it as its own reading. */
        boolean listed = true;
        Light light;
        /** The places merged into it: the first, and how many. */
        String place = "";
        int places;

        void reset() {
            code = detail = place = "";
            level = Level.CAUTION;
            handling = Handling.NONE;
            crew = places = 0;
            timer = Float.NaN;
            harm = false;
            since = 0;
            listed = true;
            light = null;
        }

        /** Merges a part's handling: needing the captain wins, then crew (summed), then clearing by itself. */
        Row handle(Handling h, int n) {
            if (h == Handling.CREW) crew += n;
            if (h.ordinal() > handling.ordinal()) handling = h;
            return this;
        }

        /** The game's count of crew on {@code a}: none on it is the captain's; -1 says nothing. */
        Row crew(Alert a) {
            if (a.handling > 0) return handle(Handling.CREW, a.handling);
            if (a.handling == 0) return handle(Handling.YOU, 0);
            return this;
        }

        Row you() {
            return handle(Handling.YOU, 0);
        }

        /** Clears by itself in {@code seconds} (NaN: no time known). */
        Row back(float seconds) {
            handle(Handling.BACK, 0);
            if (!Float.isNaN(seconds) && !harm && (Float.isNaN(timer) || seconds < timer)) timer = seconds;
            return this;
        }

        /** Harm in {@code seconds}: the soonest of its parts. */
        Row harm(float seconds) {
            if (Float.isNaN(seconds) || seconds < 0f) return this;
            if (!harm || seconds < timer) timer = seconds;
            harm = true;
            return this;
        }

        Row detail(String d) {
            detail = Fmt.cut(d, 10);
            return this;
        }

        /** Adds a place (a room, a name): the first, with "+n" for the rest. */
        Row place(String where) {
            if (where == null || where.isEmpty()) return this;
            if (places++ == 0) place = where.toUpperCase(Locale.ROOT);
            if (places == 1) {
                detail = Fmt.cut(place, 10);
            } else {
                String more = " +" + (places - 1);
                detail = Fmt.cut(place, 10 - more.length()) + more;
            }
            return this;
        }

        boolean timeCritical() {
            return harm && !Float.isNaN(timer);
        }
    }

    /** What is on screen this frame, which takes a condition from P2 while it shows it (P2's table). */
    static final class Shown {
        /** The captain sits at a seat: on deck D1 shows a wound. */
        boolean seated;
        /** H3, whose closing gauge shows FAST APPR. */
        boolean dockingDisplay;
        /** H2's PLAN line, which shows PLAN SHORT. */
        boolean planLine;
        /** F3, which shows the shield down or weak. */
        boolean shieldControl;
        /** Flying: not docked or latched. */
        boolean flying;
    }

    /** Every condition that holds, ranked; and P2's rows, at most five, with {@link #more} for the rest. */
    final Array<Row> rows = new Array<>(), panel = new Array<>();
    /** The sixth row: "+2 MORE: SMOKE, O2 LOW", or "". */
    String more = "";
    /** Each H4 light's level, null when nominal. */
    final Level[] lights = new Level[Light.values().length];
    /** The NO POWER row while it holds, else null; and whether that power is due back by itself. */
    Row noPower;
    boolean powerDueBack;

    /** The master alert cue: MASTER WARN with the newest warning's code, MASTER CAUT; how long each has been lit. */
    boolean warn, caut;
    String warnCode = "";
    float warnAge, cautAge;

    private final Array<Row> pool = new Array<>();
    private final ObjectMap<String, Double> seen = new ObjectMap<>();
    private final ObjectMap<String, Level> seenLevel = new ObjectMap<>();
    private final ObjectSet<String> acked = new ObjectSet<>(), present = new ObjectSet<>();
    private final Array<String> gone = new Array<>();
    private float storeLowFor;

    void update(HudData d, Shown shown, float delta) {
        for (Row r : rows) pool.add(r);
        rows.clear();
        noPower = null;
        powerDueBack = false;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) fromAlert(d, all.get(i), shown);
        derive(d, shown, delta);

        // When each was first seen, and the master cue for the new and the worsened
        double now = d.ctx.simSeconds;
        present.clear();
        Row newest = null;
        boolean newCaution = false;
        for (Row r : rows) {
            present.add(r.code);
            Double t = seen.get(r.code);
            Level was = seenLevel.get(r.code);
            if (t == null) {
                t = now;
                seen.put(r.code, t);
            }
            r.since = t;
            if (was == null || r.level.ordinal() > was.ordinal()) {
                acked.remove(r.code);
                if (r.level == Level.WARNING) {
                    if (newest == null || r.since >= newest.since) newest = r;
                } else if (r.level == Level.CAUTION) {
                    newCaution = true;
                }
            }
            seenLevel.put(r.code, r.level);
        }
        gone.clear();
        for (ObjectMap.Entry<String, Double> e : seen) if (!present.contains(e.key)) gone.add(e.key);
        for (String k : gone) {
            seen.remove(k);
            seenLevel.remove(k);
            acked.remove(k);
        }
        if (newest != null) {
            warn = true;
            warnCode = newest.code;
            warnAge = 0f;
        } else if (warn) {
            warnAge += delta;
        }
        if (newCaution) {
            caut = true;
            cautAge = 0f;
        } else if (caut) {
            cautAge += delta;
            if (cautAge >= CAUTION_CUE) caut = false;
        }

        rows.sort(Cautions::rank);
        for (int i = 0; i < lights.length; i++) lights[i] = null;
        for (Row r : rows) {
            if (r.light == null) continue;
            Level l = lights[r.light.ordinal()];
            if (l == null || r.level.ordinal() > l.ordinal()) lights[r.light.ordinal()] = r.level;
        }
        panel.clear();
        for (Row r : rows) if (r.listed) panel.add(r);
        more = "";
        if (panel.size > ROWS - 1) {
            int hidden = panel.size - (ROWS - 1);
            StringBuilder s = new StringBuilder("+").append(hidden).append(" MORE:");
            for (int i = ROWS - 1; i < panel.size; i++) {
                String c = (i == ROWS - 1 ? " " : ", ") + panel.get(i).code;
                // the sixth row's 30 cells: as many of the hidden codes as fit, then an ellipsis
                if (s.length() + c.length() + (i < panel.size - 1 ? 2 : 0) > 30) {
                    s.append(i == ROWS - 1 ? " …" : ", …");
                    break;
                }
                s.append(c);
            }
            more = s.toString();
            panel.truncate(ROWS - 1);
        }
    }

    /** Warnings first; then rows that need the captain, crew on, no handling, clearing; harm soonest, then newest. */
    static int rank(Row a, Row b) {
        if (a.level != b.level) return b.level.ordinal() - a.level.ordinal();
        if (a.handling != b.handling) return rankOf(a.handling) - rankOf(b.handling);
        boolean ta = a.timeCritical(), tb = b.timeCritical();
        if (ta != tb) return ta ? -1 : 1;
        if (ta) return Float.compare(a.timer, b.timer);
        return Double.compare(b.since, a.since);
    }

    private static int rankOf(Handling h) {
        switch (h) {
            case YOU: return 0;
            case CREW: return 1;
            case NONE: return 2;
            default: return 3;
        }
    }

    /** Puts the cue out and stops the rows' pulse; the rows stay while their conditions hold. */
    void acknowledge() {
        warn = caut = false;
        for (Row r : rows) acked.add(r.code);
    }

    /** Whether row {@code r} pulses now: a new warning for 2 s, a time-critical one until acknowledged. */
    boolean pulsing(Row r, double now) {
        if (r.level != Level.WARNING || acked.contains(r.code)) return false;
        return r.timeCritical() || now - r.since < PULSE;
    }

    // ------------------------------------------------------------------ the catalogue

    private Row row(String code, Level level, Light light) {
        for (Row r : rows) {
            if (!r.code.equals(code)) continue;
            if (level.ordinal() > r.level.ordinal()) r.level = level;
            return r;
        }
        Row r = pool.size > 0 ? pool.pop() : new Row();
        r.reset();
        r.code = code;
        r.level = level;
        r.light = light;
        rows.add(r);
        return r;
    }

    private void fromAlert(HudData d, Alert a, Shown s) {
        String id = a.id;
        switch (a.code) {
            case "FIRE":
                row("FIRE", Level.WARNING, Light.LIFE_SUP).place(a.room).crew(a);
                return;
            case "BREACH":
                row("BREACH", Level.WARNING, Light.HULL).place(a.room).crew(a);
                return;
            case "VACUUM":
                row("VACUUM", Level.WARNING, Light.LIFE_SUP).place(a.room).crew(a);
                return;
            case "PRESSURE":
                row("PRESS LOW", Level.CAUTION, Light.LIFE_SUP).place(a.room).crew(a);
                return;
            case "SMOKE":
                row("SMOKE", Level.CAUTION, Light.LIFE_SUP).place(a.room);
                return;
            case "HULL":
                row("HULL DAMAGE", Level.CAUTION, Light.HULL).crew(a);
                return;
            case "CASUALTY":
                row("CREW CRIT", Level.WARNING, null).place(id.startsWith("casualty:") ? id.substring(9) : "").crew(a).harm(a.timer);
                return;
            case "BLEEDING": {
                Row r = row("CAPT WOUND", Level.WARNING, null).detail(woundWord(d.captainStatus.wound)).harm(a.timer);
                if (a.handling > 0) r.crew(a);
                else r.you();
                // On deck D1 shows it
                r.listed = s.seated;
                return;
            }
            case "SUIT AIR":
            case "NO SUIT":
                // The captain's own air is X3's and D1's, never a P2 row; it still lights the cue
                row("SUIT AIR", a.level, null).listed = false;
                return;
            case "FUEL":
                row("PROP LOW", Level.CAUTION, null).detail(home(d.flightStatus.deltaVHome)).you();
                return;
            case "O2":
                if (a.level == Level.WARNING) row("EMERG O2", Level.WARNING, Light.LIFE_SUP).you();
                else row("O2 LOW", Level.CAUTION, Light.LIFE_SUP);
                return;
            case "SHIELD":
                if (id.equals("shield:down")) {
                    row("SHIELD DOWN", Level.CAUTION, Light.SHIELDS).detail("LOWERED").you().listed = !s.shieldControl;
                } else {
                    Row r = row("SHIELD WEAK", Level.CAUTION, Light.SHIELDS)
                            .detail(a.count > 1 ? a.count + " EMITTERS" : "EMITTER").back(Float.NaN);
                    r.listed = !s.shieldControl;
                }
                return;
            default:
                if (id.startsWith("sys:")) system(d, a, s);
        }
    }

    /** A vital system that is switched on and not working: SYSTEM DOWN, by the system's short name. */
    private void system(HudData d, Alert a, Shown s) {
        String sys = a.code;
        if (a.level == Level.ADVISORY) return; // switched off: not a fault
        if (sys.equals("REACTOR")) return; // NO POWER and POWER LOW say it
        boolean vital = sys.equals("ENGINE") || sys.equals("HELM") || sys.equals("WEAPONS") || sys.equals("TURRET")
                || sys.equals("SHIELDS") || sys.equals("LIFE SUP");
        if (!vital) return;
        boolean unpowered = a.text.endsWith(": no power");
        // With no power anywhere every system is cut off: NO POWER says that once
        if (unpowered && noPowerAboard(d)) return;
        boolean flight = (sys.equals("ENGINE") || sys.equals("HELM")) && s.flying;
        Light light = sys.equals("SHIELDS") ? Light.SHIELDS : sys.equals("LIFE SUP") ? Light.LIFE_SUP : null;
        Row r = row("SYSTEM DOWN", flight ? Level.WARNING : Level.CAUTION, light).place(systemName(d, a));
        if (a.timerKind == TimerKind.RECOVERY && a.handling <= 0) r.back(a.timer);
        else if (!unpowered) r.crew(a);
    }

    /** A system's short name for the detail: a gun by its mount's name (F2's rule), an emitter as SHIELD. */
    private static String systemName(HudData d, Alert a) {
        switch (a.code) {
            case "TURRET":
                for (Mount m : d.weaponFrame.mounts) {
                    if (m.tileX == a.tileX && m.tileY == a.tileY && !m.name.isEmpty()) return m.name;
                }
                return "GUN";
            case "SHIELDS": return "SHIELD";
            default: return a.code;
        }
    }

    static boolean noPowerAboard(HudData d) {
        PowerFrame p = d.powerFrame;
        return d.ctx.ship != null && p.circuits.size > 0 && p.outputWatts <= 0;
    }

    /** The conditions the game raises no alert for, worked out from the data. */
    private void derive(HudData d, Shown s, float delta) {
        UiContext ctx = d.ctx;
        if (ctx.ship == null) return;
        FlightStatus f = d.flightStatus;
        NavReadout n = d.navReadout;
        PowerFrame p = d.powerFrame;

        if (noPowerAboard(d)) {
            Row r = row("NO POWER", Level.WARNING, Light.POWER);
            noPower(d, r);
            noPower = r;
            powerDueBack = r.handling == Handling.BACK;
        } else {
            boolean damaged = p.ratedWatts > 0 && p.outputWatts > 0 && p.outputWatts < OUTPUT_LOW * p.ratedWatts;
            storeLowFor = p.circuits.size > 0 && p.outputWatts > 0 && p.charge() < STORE_LOW ? storeLowFor + delta : 0f;
            if (damaged || storeLowFor >= STORE_LOW_FOR) {
                row("POWER LOW", Level.CAUTION, Light.POWER).detail(damaged ? "DAMAGED" : "WEAPONS");
            }
        }

        OrbitInfo orbit = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (s.flying && orbit != null && orbit.impactWarning && n.burnsLeft == 0) {
            row("IMPACT", Level.WARNING, null).detail(orbit.bodyName).you();
        }
        double planned = n.planDeltaV > 0 ? n.planDeltaV : f.deltaVPlanned;
        if (s.flying && planned > 0 && !Double.isNaN(f.deltaVLeft) && planned > f.deltaVLeft) {
            row("PLAN SHORT", Level.CAUTION, null).detail("NEED " + group(planned)).you().listed = !s.planLine;
        }
        float burnIn = n.burnsLeft > 0 && !n.burning ? n.burnStartIn : Float.NaN;
        boolean burnSoon = !Float.isNaN(burnIn) && burnIn <= BURN_SOON;
        boolean turning = !Float.isNaN(f.spin) && Math.abs(f.spin) > TURNING;
        if (s.flying && ctx.helmsman.isEmpty() && (burnSoon || turning)) {
            row("NO HELM", Level.CAUTION, null).you().harm(burnSoon ? burnIn : Float.NaN);
        }
        if (s.flying && ctx.crewOutside && (f.mainEngine || burnSoon || n.burning)) {
            row("CREW OUT", Level.CAUTION, null).you().harm(burnSoon ? burnIn : Float.NaN);
        }
        DockingApproach k = d.docking;
        if (s.flying && k.present && k.capture == Capture.APPROACH && !Float.isNaN(k.closing) && !Float.isNaN(k.safeSpeed)
                && k.safeSpeed > 0f && k.closing > k.safeSpeed) {
            float over = k.closing - k.safeSpeed;
            Row r = row("FAST APPR", k.closing >= 2f * k.safeSpeed ? Level.WARNING : Level.CAUTION, null)
                    .detail(over < 9.5f ? Math.round(over) + " M/S OVER" : "+" + Math.round(over) + " M/S");
            if (d.orderState.helm == HelmOrder.DOCK) r.handle(Handling.CREW, 1);
            else r.you();
            if (!Float.isNaN(k.range) && k.closing > 0f) r.harm(k.range / k.closing);
            // While H3 shows its closing gauge the gauge is its home; it still lights the cue
            r.listed = !s.dockingDisplay;
        }
        if (!s.flying && k.present && !Float.isNaN(k.strain) && k.strain > STRAIN) {
            row("DOCK STRAIN", Level.CAUTION, null).detail(Math.round(k.strain * 100f) + "%");
        }
        ShieldStatus sh = d.shieldStatus;
        if (sh.present && sh.emitters > 0 && sh.emittersWorking == 0) {
            row("SHIELD DOWN", Level.CAUTION, Light.SHIELDS).detail("EMITTERS").listed = !s.shieldControl;
        } else if (sh.present && sh.raised && sh.capacity > 0f && sh.cloud < CLOUD_THIN * sh.capacity) {
            row("SHIELD WEAK", Level.CAUTION, Light.SHIELDS).detail(thinSide(sh)).listed = !s.shieldControl;
        }
    }

    /** NO POWER's why and handling, from the reactors' faults (worst first). */
    private static void noPower(HudData d, Row r) {
        SystemRow reactor = null;
        for (SystemRow f : d.powerFrame.faults) {
            if (f.system.equals("REACTOR")) {
                reactor = f;
                break;
            }
        }
        if (reactor == null) {
            r.detail("").you();
            return;
        }
        Alert a = d.alerts.get("sys:REACTOR:" + reactor.tileX + "," + reactor.tileY);
        int crew = a == null ? -1 : a.handling;
        switch (reactor.fault) {
            case STUNNED:
                r.detail("STUNNED").back(reactor.timer);
                break;
            case SHORTED:
                r.detail("SHORTED");
                if (crew > 0) r.handle(Handling.CREW, crew);
                else r.back(reactor.timer);
                break;
            case DESTROYED:
            case DISABLED:
                r.detail("DAMAGED");
                if (crew > 0) r.handle(Handling.CREW, crew);
                else r.you();
                break;
            default:
                r.detail(reactor.fault == Fault.OFF ? "OFF" : "").you();
        }
    }

    /** The side the cloud is thinnest. */
    static String thinSide(ShieldStatus sh) {
        String[] names = {"BOW", "PORT", "STERN", "STBD"};
        int at = 0;
        for (int i = 1; i < sh.sectors.length; i++) if (sh.sectors[i] < sh.sectors[at]) at = i;
        return names[at];
    }

    static String woundWord(String wound) {
        String w = wound == null ? "" : wound.toLowerCase(Locale.ROOT);
        if (w.contains("crit")) return "CRITICAL";
        if (w.contains("serious")) return "SERIOUS";
        return "BLEEDING";
    }

    /** What the trip home needs: "HOME 1,240". */
    static String home(double deltaV) {
        return Double.isNaN(deltaV) || Double.isInfinite(deltaV) ? "" : "HOME " + group(deltaV);
    }

    /** Thousands grouped: "1,240". */
    static String group(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }
}
