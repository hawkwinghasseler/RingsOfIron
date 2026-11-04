package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrdersProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P2's rows (functional-context.md, section 6, P2): the game's alerts and the conditions this HUD
 * works out itself, put in the catalogue's words, one row a code, in the spec's order. Also what
 * the rows light: H4's four lights and the master alert cue. Pure data, so it is tested without
 * a screen.
 *
 * <p>The game raises the alerts it knows ({@code GameAlerts}); the catalogue has more, which this
 * class raises into a registry of its own from the data ({@link #derive}): PLAN SHORT, NO HELM,
 * CREW OUT, FAST APPR, DOCK STRAIN, POWER LOW, IMPACT, and the shield's weak and down states. The
 * game can't yet say who works a condition, so a crew-handled row shows no handling mark until it
 * can; the rows only the captain can act on carry YOU.
 */
final class CautionRows {
    /** A row's handling field: needs the captain, crew on it (a dot each), unknown or none, clears by itself. */
    enum Handling { YOU, CREW, NONE, BACK }

    /** The H4 lights, in order. */
    enum Light { HULL, SHIELDS, POWER, LIFE_SUP }

    /** One code's row: its parts merged. */
    static final class Row {
        Level level = Level.CAUTION;
        String code = "", detail = "";
        Handling handling = Handling.NONE;
        int crew;
        /** Seconds on the row's timer, NaN with none; a harm timer counts down to harm, else to recovery. */
        float timer = Float.NaN;
        boolean harm;
        /** When its newest part was first raised, sim seconds; and whether every part is acknowledged. */
        double since;
        boolean acknowledged;
        /** The first part's alert id. */
        String id = "";
        /** A P2 row, rather than a condition another widget always shows (the captain's own air). */
        boolean listed = true;
        private final List<String> details = new ArrayList<>();
    }

    /** Which widgets are on screen, which take conditions from P2 while they show them (section 6, P2). */
    static final class Shown {
        /** The captain sits at a seat (CAPT WOUND is D1's on deck). */
        boolean seated;
        /** H3, whose closing gauge shows FAST APPR. */
        boolean dockingDisplay;
        /** H2's PLAN line, which shows PLAN SHORT. */
        boolean planLine;
        /** F3, which shows the shield's weak and down states. */
        boolean shieldControl;
    }

    /** Every catalogue condition that holds, ordered; then the ones P2 lists. */
    final List<Row> all = new ArrayList<>();
    final List<Row> panel = new ArrayList<>();
    /** Each H4 light's level, null when nominal. */
    final Level[] lights = new Level[Light.values().length];
    /** The newest condition not yet acknowledged, at caution or worse: the master alert cue's; or null. */
    Row cue;

    private final List<Row> pool = new ArrayList<>();
    private float storeLowFor;

    /** P2 shows six rows; past six, five and a sixth that says how many more. */
    static final int ROWS = 6;

    // ---- the conditions the game doesn't raise ---------------------------------------------------

    /** Within this of a burn, crew outside or nobody at the helm is a caution, s. */
    static final float BURN_SOON = 60f;
    /** POWER LOW: a reactor under this share of its rating, or the store under the second for 3 s. */
    static final float OUTPUT_LOW = 0.6f, STORE_LOW = 0.25f, STORE_LOW_FOR = 3f;
    /** SHIELD WEAK: the cloud under this share of its capacity. */
    static final float CLOUD_THIN = 0.25f;
    /** DOCK STRAIN over this share. */
    static final float STRAIN = 0.5f;

    /**
     * Raises into {@code out} (between its begin and end) the catalogue's conditions the game's
     * alerts don't cover, from this frame's data; {@code delta} is the frame's real seconds.
     */
    void derive(HudData d, AlertRegistry out, float delta) {
        UiContext ctx = d.ctx;
        FlightStatus f = d.flightStatus;
        NavReadout n = d.navReadout;
        boolean flying = ctx.ship != null && !ctx.docked && !ctx.latched;
        float burnIn = n.burnsLeft > 0 && !n.burning ? n.burnStartIn : Float.NaN;
        boolean burnSoon = !Float.isNaN(burnIn) && burnIn <= BURN_SOON;
        if (flying && f.deltaVPlanned > 0 && !Double.isInfinite(f.deltaVLeft) && f.deltaVPlanned > f.deltaVLeft) {
            out.raise("ctx:planshort", Level.CAUTION, Category.NAV).set("PLAN SHORT", "NEED " + NpFormat.mps(f.deltaVPlanned));
        }
        boolean nobody = ctx.helmsman.isEmpty() && ctx.presence != Presence.HELM;
        boolean turning = !Float.isNaN(f.spin) && Math.abs(f.spin) > 2f;
        if (flying && nobody && (burnSoon || turning)) {
            out.raise("ctx:nohelm", Level.CAUTION, Category.FLIGHT).set("NO HELM", "")
                    .timer(burnSoon ? burnIn : Float.NaN, burnSoon ? TimerKind.HARM : TimerKind.NONE);
        }
        if (flying && ctx.crewOutside && (f.mainEngine || burnSoon || n.burning)) {
            out.raise("ctx:crewout", Level.CAUTION, Category.CREW).set("CREW OUT", "")
                    .timer(burnSoon ? burnIn : Float.NaN, burnSoon ? TimerKind.HARM : TimerKind.NONE);
        }
        DockingApproach k = d.docking;
        if (flying && k.present && !Float.isNaN(k.closing) && !Float.isNaN(k.safeSpeed) && k.closing > k.safeSpeed && k.safeSpeed > 0f) {
            float over = k.closing - k.safeSpeed;
            float contact = !Float.isNaN(k.range) && k.closing > 0f ? k.range / k.closing : Float.NaN;
            out.raise("ctx:fastappr", k.closing >= 2f * k.safeSpeed ? Level.WARNING : Level.CAUTION, Category.FLIGHT)
                    .set("FAST APPR", NpFormat.tenths(over) + " M/S OVER")
                    .timer(contact, Float.isNaN(contact) ? TimerKind.NONE : TimerKind.HARM);
        }
        if (!Float.isNaN(k.strain) && k.strain > STRAIN && (ctx.docked || ctx.latched)) {
            out.raise("ctx:strain", Level.CAUTION, Category.COMMS).set("DOCK STRAIN", NpFormat.percent(k.strain));
        }
        PowerFrame p = d.powerFrame;
        boolean damaged = p.ratedWatts > 0 && p.outputWatts > 0 && p.outputWatts < OUTPUT_LOW * p.ratedWatts;
        storeLowFor = p.circuits.size > 0 && p.outputWatts > 0 && p.charge() < STORE_LOW ? storeLowFor + delta : 0f;
        if (damaged || storeLowFor >= STORE_LOW_FOR) {
            out.raise("ctx:powerlow", Level.CAUTION, Category.ENGINEERING).set("POWER LOW", damaged ? "DAMAGED" : "WEAPONS");
        }
        OrbitInfoProvider.OrbitInfo orbit = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (flying && orbit != null && orbit.impactWarning && n.burnsLeft == 0) {
            out.raise("ctx:impact", Level.WARNING, Category.NAV).set("IMPACT", upper(orbit.bodyName));
        }
        ShieldStatus s = d.shieldStatus;
        if (s.present && s.emitters > 0) {
            if (s.emittersWorking == 0 && s.emittersTripped == 0) {
                out.raise("ctx:shielddown", Level.CAUTION, Category.TACTICAL).set("SHIELD DOWN", "EMITTERS");
            } else if (s.raised && s.capacity > 0f && s.share() < CLOUD_THIN) {
                out.raise("ctx:shieldweak", Level.CAUTION, Category.TACTICAL).set("SHIELD WEAK", thinSide(s));
            }
        }
    }

    private static String thinSide(ShieldStatus s) {
        int min = 0;
        for (int i = 1; i < s.sectors.length; i++) if (s.sectors[i] < s.sectors[min]) min = i;
        return new String[] {"BOW", "PORT", "STERN", "STBD"}[min];
    }

    // ---- the rows -------------------------------------------------------------------------------

    /** Builds the rows from the game's alerts and {@code derived}, as this frame shows {@code shown}. */
    void build(HudData d, AlertRegistry derived, Shown shown) {
        pool.addAll(all);
        all.clear();
        panel.clear();
        java.util.Arrays.fill(lights, null);
        cue = null;
        add(d, d.alerts.all(), shown);
        add(d, derived.all(), shown);
        all.sort(CautionRows::order);
        for (Row r : all) {
            if (r.listed && !leftOut(r, shown)) panel.add(r);
            Light l = light(r);
            if (l != null && (lights[l.ordinal()] == null || r.level.ordinal() > lights[l.ordinal()].ordinal())) lights[l.ordinal()] = r.level;
            if (!r.acknowledged && (cue == null || r.since > cue.since)) cue = r;
        }
    }

    private void add(HudData d, Array<Alert> alerts, Shown shown) {
        // by index: a part may look through the same alerts, and libGDX's shared iterators can't nest
        for (int i = 0; i < alerts.size; i++) {
            Alert a = alerts.get(i);
            Part p = part(d, a, shown);
            if (p == null) continue;
            Row r = null;
            for (Row x : all) if (x.code.equals(p.code)) { r = x; break; }
            if (r == null) {
                r = pool.isEmpty() ? new Row() : pool.remove(pool.size() - 1);
                r.level = p.level;
                r.code = p.code;
                r.details.clear();
                r.handling = p.handling;
                r.crew = 0;
                r.timer = Float.NaN;
                r.harm = false;
                r.since = a.since;
                r.acknowledged = true;
                r.id = a.id;
                r.listed = p.listed;
                all.add(r);
            } else {
                if (p.level.ordinal() > r.level.ordinal()) r.level = p.level;
                r.handling = merge(r.handling, p.handling);
                r.since = Math.max(r.since, a.since);
                r.listed |= p.listed;
            }
            if (!p.detail.isEmpty() && !r.details.contains(p.detail)) r.details.add(p.detail);
            if (p.handling == Handling.CREW) r.crew += Math.max(0, a.handling);
            if (!Float.isNaN(p.timer)) {
                if (p.harm && (!r.harm || Float.isNaN(r.timer) || p.timer < r.timer)) {
                    r.timer = p.timer;
                    r.harm = true;
                } else if (!p.harm && !r.harm && (Float.isNaN(r.timer) || p.timer > r.timer)) {
                    r.timer = p.timer;
                }
            }
            r.acknowledged &= a.acknowledged;
            int extra = r.details.size() - 1;
            r.detail = r.details.isEmpty() ? "" : r.details.get(0) + (extra > 0 ? " +" + extra : "");
        }
    }

    /** Merged handling: a part that needs the captain makes the row need them; one nobody can name keeps it unnamed. */
    private static Handling merge(Handling a, Handling b) {
        if (a == Handling.YOU || b == Handling.YOU) return Handling.YOU;
        if (a == Handling.NONE || b == Handling.NONE) return Handling.NONE;
        if (a == Handling.CREW || b == Handling.CREW) return Handling.CREW;
        return Handling.BACK;
    }

    /** Warnings first; then needs you, crew on it, clears by itself; then harm timers soonest first, then newest first. */
    static int order(Row a, Row b) {
        if (a.level != b.level) return b.level.ordinal() - a.level.ordinal();
        int ga = group(a), gb = group(b);
        if (ga != gb) return ga - gb;
        boolean ta = a.harm && !Float.isNaN(a.timer), tb = b.harm && !Float.isNaN(b.timer);
        if (ta != tb) return ta ? -1 : 1;
        if (ta && a.timer != b.timer) return Float.compare(a.timer, b.timer);
        return Double.compare(b.since, a.since);
    }

    private static int group(Row r) {
        switch (r.handling) {
            case YOU: return 0;
            case BACK: return 2;
            default: return 1;
        }
    }

    /** A condition another widget on screen shows as its own reading (section 6, P2). */
    private static boolean leftOut(Row r, Shown shown) {
        switch (r.code) {
            case "CAPT WOUND": return !shown.seated;
            case "FAST APPR": return shown.dockingDisplay;
            case "PLAN SHORT": return shown.planLine;
            case "SHIELD DOWN":
            case "SHIELD WEAK": return shown.shieldControl;
            default: return false;
        }
    }

    private static Light light(Row r) {
        switch (r.code) {
            case "HULL DAMAGE":
            case "BREACH": return Light.HULL;
            case "SHIELD DOWN":
            case "SHIELD WEAK": return Light.SHIELDS;
            case "NO POWER":
            case "POWER LOW": return Light.POWER;
            case "EMERG O2":
            case "O2 LOW":
            case "VACUUM":
            case "PRESS LOW":
            case "FIRE":
            case "SMOKE": return Light.LIFE_SUP;
            case "SYSTEM DOWN":
                if (r.details.contains("SHIELDS")) return Light.SHIELDS;
                if (r.details.contains("REACTOR")) return Light.POWER;
                if (r.details.contains("LIFE SUP")) return Light.LIFE_SUP;
                return null;
            default: return null;
        }
    }

    // ---- one alert in the catalogue's words -----------------------------------------------------

    /** An alert as a part of a row, before merging. */
    private static final class Part {
        Level level;
        String code, detail = "";
        Handling handling = Handling.NONE;
        float timer = Float.NaN;
        boolean harm, listed = true;
    }

    private final Part part = new Part();

    /** {@code a} in the catalogue's words, or null when it isn't one of its conditions (section 6, P2). */
    private Part part(HudData d, Alert a, Shown shown) {
        Part p = part;
        p.level = a.level;
        p.detail = "";
        p.handling = a.handling >= 0 ? (a.handling == 0 ? Handling.YOU : Handling.CREW) : Handling.NONE;
        p.timer = a.timer;
        p.harm = a.timerKind == TimerKind.HARM;
        p.listed = true;
        String id = a.id;
        if (id.startsWith("fire:")) return code(p, "FIRE", Level.WARNING, a.room);
        if (id.startsWith("breach:")) return code(p, "BREACH", Level.WARNING, a.room);
        if (id.startsWith("air:")) return code(p, a.level == Level.WARNING ? "VACUUM" : "PRESS LOW", a.level, a.room);
        if (id.startsWith("smoke:")) {
            p.handling = Handling.NONE;
            return code(p, "SMOKE", Level.CAUTION, a.room);
        }
        if (id.equals("hull")) return code(p, "HULL DAMAGE", Level.CAUTION, "");
        if (id.startsWith("sys:")) return system(d, a, p);
        if (id.equals("helm")) {
            if (!d.ctx.helmFault.contains("switched off") && hasSystemAlert(d, "HELM")) return null;
            p.handling = d.ctx.helmFault.contains("switched off") ? Handling.YOU : Handling.NONE;
            return code(p, "SYSTEM DOWN", inFlight(d) ? Level.WARNING : Level.CAUTION, "HELM");
        }
        if (id.equals("captain:bleeding")) {
            p.handling = Handling.YOU;
            return code(p, "CAPT WOUND", Level.WARNING, "BLEEDING");
        }
        if (id.equals("captain:suit")) {
            // the captain's own suit air is X3's under 240 s and D1's above: never a P2 row
            p.listed = false;
            return code(p, "SUIT AIR", a.level, "");
        }
        if (id.equals("captain:air")) {
            p.listed = false;
            return code(p, "NO SUIT", a.level, "");
        }
        if (id.startsWith("casualty:")) return code(p, "CREW CRIT", Level.WARNING, cut(upper(id.substring("casualty:".length())), 10));
        if (id.equals("shield:tripped")) {
            p.handling = Handling.BACK;
            return code(p, "SHIELD WEAK", Level.CAUTION, "EMITTER");
        }
        if (id.equals("shield:down")) {
            p.handling = Handling.YOU;
            return code(p, "SHIELD DOWN", Level.CAUTION, "LOWERED");
        }
        if (id.equals("oxygen")) {
            if (a.level == Level.WARNING) {
                p.handling = Handling.YOU;
                return code(p, "EMERG O2", Level.WARNING, "");
            }
            return code(p, "O2 LOW", Level.CAUTION, "");
        }
        if (id.equals("propellant")) {
            p.handling = Handling.YOU;
            double home = d.flightStatus.deltaVHome;
            return code(p, "PROP LOW", Level.CAUTION, Double.isNaN(home) || Double.isInfinite(home) || home <= 0 ? "" : "HOME " + NpFormat.mps(home));
        }
        if (id.startsWith("ctx:")) {
            String detail = a.text;
            switch (id) {
                case "ctx:planshort":
                case "ctx:nohelm":
                case "ctx:crewout":
                case "ctx:impact":
                    p.handling = Handling.YOU;
                    break;
                case "ctx:fastappr":
                    p.handling = d.orderState.helm == OrdersProvider.HelmOrder.DOCK ? Handling.NONE : Handling.YOU;
                    break;
                case "ctx:shieldweak":
                    p.handling = Handling.BACK;
                    break;
                default:
                    p.handling = Handling.NONE;
            }
            return code(p, a.code, a.level, detail);
        }
        // missiles are P3's, a hostile's lock is the lock card's, and the rest are advisories, X3's or the feed's
        return null;
    }

    private static Part code(Part p, String code, Level level, String detail) {
        p.code = code;
        p.level = level;
        p.detail = detail == null ? "" : detail;
        return p;
    }

    /** A system's fault: NO POWER for the reactor with no power left, else SYSTEM DOWN for a vital one. */
    private static Part system(HudData d, Alert a, Part p) {
        String text = a.text;
        int colon = text.lastIndexOf(": ");
        String what = colon >= 0 ? text.substring(colon + 2) : "";
        String system = a.code;
        boolean recovery = a.timerKind == TimerKind.RECOVERY;
        if (recovery) p.handling = Handling.BACK;
        if (system.equals("REACTOR")) {
            if (d.powerFrame.outputWatts > 0) {
                if (what.equals("off")) return null;
                return code(p, "SYSTEM DOWN", Level.CAUTION, "REACTOR");
            }
            if (what.equals("off")) p.handling = Handling.YOU;
            String why = what.equals("stunned") ? "STUNNED" : what.equals("shorted") ? "SHORTED" : what.equals("off") ? "OFF"
                    : what.equals("no power") ? "DRY" : "DAMAGED";
            return code(p, "NO POWER", Level.WARNING, why);
        }
        // only a switched-on vital system (section 6, P2): engines, the helm, the weapons console, guns, emitters, life support
        if (what.equals("off")) return null;
        switch (system) {
            case "ENGINE":
            case "HELM":
                return code(p, "SYSTEM DOWN", inFlight(d) ? Level.WARNING : Level.CAUTION, system);
            case "WEAPONS":
            case "SHIELDS":
            case "LIFE SUP":
                return code(p, "SYSTEM DOWN", Level.CAUTION, system);
            case "TURRET":
                return code(p, "SYSTEM DOWN", Level.CAUTION, mountAt(d, a.tileX, a.tileY));
            default:
                return null;
        }
    }

    private static boolean hasSystemAlert(HudData d, String system) {
        Array<Alert> all = d.alerts.all();
        String prefix = "sys:" + system + ":";
        for (int i = 0; i < all.size; i++) if (all.get(i).id.startsWith(prefix)) return true;
        return false;
    }

    private static boolean inFlight(HudData d) {
        return !d.ctx.docked && !d.ctx.latched;
    }

    /** The mount on tile ({@code x}, {@code y}) by F2's rule, or GUN. */
    private static String mountAt(HudData d, int x, int y) {
        for (Mount m : d.weaponFrame.mounts) {
            if (m.tileX == x && m.tileY == y) return cut(WeaponsRack.place(m.name), 10);
        }
        return "GUN";
    }

    static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
