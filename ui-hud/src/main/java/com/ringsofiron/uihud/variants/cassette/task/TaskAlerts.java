package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.NoticeAlerts;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Comparator;
import java.util.Locale;

/**
 * Where each alert goes in the task spec (sections 6 ANN, 9 and 13): which of the ten annunciator
 * lamps it lights and how, and what the alert band shows. The game's {@link AlertRegistry} says what is
 * wrong at the game's own levels, which all nine variants share; this maps each of its alerts by id to
 * 9.2's alert and level (section 13's table), so a lamp and its alert always agree. It raises the
 * alerts 9.2 has that the game doesn't raise yet but the HUD's data can tell (EVA BURN, SUIT AIR by
 * time, WOUNDED, NO GUNS WORKING, a plan the propellant can't fly, a thin shield cloud, low oxygen
 * tanks, a damaged reactor, a wrecked RCS block, crew adrift), and lights the advisories no alert
 * carries. Then the band: the warning row (one, the soonest harm first), the caution row (three slots
 * and +n, in 9.2's order: people, then the ship, then the job), how long a caution stays in the band
 * (10 s, then it lives on as its lamp), and the band's words for each alert. Pure logic, so it is
 * tested without a screen.
 */
final class TaskAlerts {

    /** The annunciator's ten positions, in their grid order (two rows of five). */
    enum Lamp {
        HULL, FIRE, AIR, POWER, PROP, DRIVE, ARMS, CREW, EVA, SHIELD
    }

    /**
     * 9.2's alerts in its list's order, which ranks the caution row (9.1), each with the lamp it
     * lights. OTHER is anything the list doesn't name, ranked last.
     */
    enum Id {
        MISSILE(null), COLLISION(null), DISABLED(null), ABANDON(null), CAPTAIN_AIR(Lamp.AIR), CAPTAIN_HURT(Lamp.CREW),
        CRITICAL(Lamp.CREW), BOARDERS(null), EVA_BURN(Lamp.EVA), FIRE(Lamp.FIRE), VENTING(Lamp.AIR), AIR(Lamp.AIR),
        SUIT_AIR(Lamp.AIR), EVA(Lamp.EVA), WOUNDED(Lamp.CREW), LOCKED_ON(null), IMPACT(null), INTERCEPT(null),
        POWER(Lamp.POWER), DRIVE(Lamp.DRIVE), ARMS(Lamp.ARMS), SHIELD(Lamp.SHIELD), HULL(Lamp.HULL),
        PROPELLANT(Lamp.PROP), HELM(null), BURN_MISSED(null), COLLAR(null), CONTRACT(null), OTHER(null);

        final Lamp lamp;

        Id(Lamp lamp) {
            this.lamp = lamp;
        }
    }

    /** Seconds a caution stays in the band before it leaves for its lamp. */
    static final float CAUTION_SECONDS = 10f;
    /** Seconds something new blinks (a lamp) or pulses (the warning row). */
    static final float NEW_SECONDS = 3f;
    /** A missile is a warning under this many seconds from impact, else a caution (9.2). */
    static final float MISSILE_SOON = 20f;
    /** Crew outside with a burn due within this many seconds is EVA BURN. */
    static final float BURN_SOON = 30f;
    /** Sim seconds a fire may burn with nobody on it before it is a warning (v16). */
    static final float FIRE_UNANSWERED = 5f;
    /** Oxygen below this share of cabin air hurts someone without a suit ({@code CrewDef.asphyxiationThreshold}). */
    static final float AIR_HURTS = 0.3f;
    /** A suit's air in bad air: SUIT AIR under 4 minutes ({@code AirWatch.WARN_AT}), CAPTAIN AIR under a minute. */
    static final float SUIT_SOON = 240f, SUIT_LAST = 60f;
    /** Delta-v left under this many times what home takes is PROP's advisory (its caution is the game's propellant alert). */
    static final double PROP_THIN = 1.5;
    /** The shield cloud against its capacity: an advisory below half, a caution below a quarter. */
    static final float CLOUD_HALF = 0.5f, CLOUD_QUARTER = 0.25f;
    /** Oxygen tanks below this share are an AIR caution ({@code ShipGauges.CRITICAL}). */
    static final float O2_LOW = 0.25f;
    /** Seconds between reads of the crew roster, for the wounded. */
    static final float ROSTER_EVERY = 0.5f;
    /** The ids of the alerts this HUD raises itself: this, then 9.2's id. */
    static final String OWN = "task:";

    /** Each lamp's level: null dark. */
    final Level[] level = new Level[Lamp.values().length];
    /** A lamp that an alert under {@link #NEW_SECONDS} old lights, which blinks. */
    final boolean[] fresh = new boolean[Lamp.values().length];
    /** What lit each lamp, for hover: the alerts' own lines. */
    final String[] why = new String[Lamp.values().length];
    private final StringBuilder[] whyText = new StringBuilder[Lamp.values().length];
    /** The ship has a shield emitter: else the SHIELD position is a blanking plate. */
    boolean shieldFitted;

    /** The warning row's alert, or null. */
    Alert warning;
    /** The warning is new (pulses) and not acknowledged. */
    boolean warningNew;
    /** The caution row's alerts in order: warnings pushed down first, then cautions. */
    final Array<Alert> cautions = new Array<>();
    /** The band has a caution not yet acknowledged: MASTER CAUTION is lit. */
    boolean masterCaution;
    /** The warning row's alert has not been acknowledged: MASTER WARNING is lit. */
    boolean masterWarning;

    /** One alert as this spec reads it this frame: 9.2's alert, its level here, the lamps it lights. */
    static final class Row {
        Alert a;
        Id id = Id.OTHER;
        Level level = Level.ADVISORY;
        /** The lamp it lights, and a second one (a shorted engine's alert is POWER's, and it lights DRIVE too). */
        Lamp lamp, also;
        /**
         * It lights its lamps and takes no place in the band, where another alert says the same thing
         * (9.1: the same condition never shows twice): with no reactor feeding anything every system is
         * cut off, which is the one POWER warning; a reactor down is DISABLED's cause.
         */
        boolean quiet;
    }

    /** The alerts 9.2 has that the game doesn't raise yet, from the HUD's data. */
    private final AlertRegistry own = new AlertRegistry();
    /** Their caution slot's three parts (the bold code, then the two lines), by id. */
    private final ObjectMap<String, String[]> ownLines = new ObjectMap<>();
    private final Array<Row> rows = new Array<>();
    private final Array<Row> pool = new Array<>();
    private final Array<Row> sorted = new Array<>();
    /** This frame's power faults by their alert's id, for what a {@code sys:} alert is. */
    private final ObjectMap<String, SystemRow> systems = new ObjectMap<>();
    /** Each alert's level here last frame, so one that worsens comes back to the band (9.3). */
    private final ObjectMap<String, Level> was = new ObjectMap<>();
    private final CrewProvider.CrewSheet roster = new CrewProvider.CrewSheet();
    private float rosterIn;
    private Post post = Post.HELM;

    private final ObjectFloatMap<String> seen = new ObjectFloatMap<>();
    private final ObjectSet<String> present = new ObjectSet<>();
    private final Array<String> gone = new Array<>();
    private float clock;

    /** Reads this frame's alerts, for the captain at {@code post}: the band's words and what it shows fit the post (9.1). */
    void update(HudData d, float delta, Post post) {
        clock += delta;
        this.post = post;
        systems.clear();
        for (SystemRow r : d.powerFrame.faults) systems.put("sys:" + r.system + ":" + r.tileX + "," + r.tileY, r);
        shieldFitted = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        roster(d, delta);
        raiseOwn(d);

        pool.addAll(rows);
        rows.clear();
        add(d.alerts.all(), d);
        add(own.all(), d);

        // When each alert was first seen, in real seconds, so warp doesn't hurry a blink; one that
        // worsens is seen afresh and comes back unacknowledged
        present.clear();
        for (Row r : rows) {
            String id = r.a.id;
            present.add(id);
            Level before = was.get(id);
            if (!seen.containsKey(id) || before != null && r.level.ordinal() > before.ordinal()) {
                seen.put(id, clock);
                r.a.acknowledged = false;
            }
            was.put(id, r.level);
        }
        gone.clear();
        for (ObjectFloatMap.Entry<String> e : seen.entries()) if (!present.contains(e.key)) gone.add(e.key);
        for (String id : gone) {
            seen.remove(id, 0f);
            was.remove(id);
        }
        gone.clear();
        for (ObjectMap.Entry<String, String[]> e : ownLines.entries()) if (own.get(OWN + e.key) == null) gone.add(e.key);
        for (String id : gone) ownLines.remove(id);

        for (int i = 0; i < level.length; i++) {
            level[i] = null;
            fresh[i] = false;
            if (whyText[i] == null) whyText[i] = new StringBuilder();
            whyText[i].setLength(0);
        }
        for (Row r : rows) {
            boolean isNew = age(r.a) < NEW_SECONDS && !r.a.acknowledged;
            String says = hover(r.a);
            if (r.lamp != null) light(r.lamp, r.level, isNew, says);
            if (r.also != null) light(r.also, r.level, isNew, says);
        }
        advisories(d);
        for (int i = 0; i < level.length; i++) why[i] = whyText[i].length() == 0 ? null : whyText[i].toString();
        if (!shieldFitted) level[Lamp.SHIELD.ordinal()] = null;

        band();
    }

    /** Acknowledges the band (the visor pressed): the game's alerts and this HUD's own. */
    void acknowledge(HudData d) {
        d.alerts.acknowledgeAll();
        own.acknowledgeAll();
    }

    /** The row {@code id}'s alert makes this frame, or null when this spec drops it. For tests. */
    Row row(String id) {
        for (Row r : rows) if (r.a.id.equals(id)) return r;
        return null;
    }

    private void add(Array<Alert> all, HudData d) {
        for (Alert a : all) {
            Row r = pool.size > 0 ? pool.pop() : new Row();
            if (map(a, d, r)) rows.add(r);
            else pool.add(r);
        }
    }

    private void light(Lamp l, Level lv, boolean isNew, String text) {
        int i = l.ordinal();
        if (level[i] == null || lv.ordinal() > level[i].ordinal()) level[i] = lv;
        if (isNew) fresh[i] = true;
        if (text != null && !text.isEmpty()) {
            if (whyText[i].length() > 0) whyText[i].append(";  ");
            whyText[i].append(text);
        }
    }

    /**
     * Whether this HUD shows what a warning notice names ({@link MessageFeed.Message#alert}) in its
     * place this frame, so the notice is no news (9.2's table of the game's warning notices): its alert
     * is up (a caution past its time in the band lives on as its lamp), or the crisis placard is, for a
     * guarded order's question, low propellant and the ship disabled. Otherwise the warning is news, so
     * nothing the game warns of goes unshown: a wound someone is already treating, a leak before its
     * room's air has fallen, and what this HUD can't raise yet (INTERCEPT, BOARDERS, COLLAR, BURN MISSED,
     * HELM, SUIT AIR for the crew, EVA's other forms, and the autopilot's hold, which no part of it says).
     */
    boolean carries(String alert, boolean crisis) {
        switch (alert) {
            case NoticeAlerts.MISSILE: return up(Id.MISSILE, null);
            case NoticeAlerts.PROPELLANT: return crisis || up(Id.PROPELLANT, null);
            case NoticeAlerts.DISABLED: return crisis || up(Id.DISABLED, null);
            // A leak is its room's air: the breach, then the vacuum or the low pressure
            case NoticeAlerts.VENTING: return up(Id.VENTING, null) || up(Id.AIR, null);
            case NoticeAlerts.AIR: return up(Id.AIR, null);
            case NoticeAlerts.CAPTAIN:
                return up(Id.CAPTAIN_AIR, null) || up(Id.CAPTAIN_HURT, null) || up(Id.SUIT_AIR, null) || up(Id.AIR, null);
            // EVA's caution is ADRIFT; crew outside is its advisory
            case NoticeAlerts.ADRIFT: return up(Id.EVA, Level.CAUTION);
            case NoticeAlerts.EVA_BURN: return up(Id.EVA_BURN, null);
            case NoticeAlerts.CRITICAL: return up(Id.CRITICAL, null);
            case NoticeAlerts.WOUNDED: return up(Id.WOUNDED, null);
            case NoticeAlerts.ASKED: return crisis;
            default: return false;
        }
    }

    /** Whether an alert of {@code id} is up this frame, at {@code level} when one is given. */
    private boolean up(Id id, Level at) {
        for (int i = 0; i < rows.size; i++) {
            Row r = rows.get(i);
            if (r.id == id && (at == null || r.level == at)) return true;
        }
        return false;
    }

    // --- The game's alerts, as 9.2 has them (section 13's table) -------------------------------

    /** Reads {@code a} into {@code r}: 9.2's alert it is, its level here and its lamps. False drops it: another alert or widget says it. */
    private boolean map(Alert a, HudData d, Row r) {
        r.a = a;
        r.level = a.level;
        r.lamp = r.also = null;
        r.id = Id.OTHER;
        r.quiet = false;
        String id = a.id;
        CaptainStatus c = d.captainStatus;
        if (id.startsWith(OWN)) {
            int end = id.indexOf(':', OWN.length());
            r.id = Id.valueOf(id.substring(OWN.length(), end < 0 ? id.length() : end));
        } else if (id.equals("missile")) {
            r.id = Id.MISSILE;
            r.level = !Float.isNaN(a.timer) && a.timer < MISSILE_SOON ? Level.WARNING : Level.CAUTION;
        } else if (id.equals("disabled")) {
            r.id = Id.DISABLED;
            r.lamp = causeLamp(d.ctx.disabledReason);
        } else if (id.equals("abandon")) {
            r.id = Id.ABANDON;
        } else if (id.equals("captain:air")) {
            // No suit in thin air: the room's AIR says it, until the air hurts
            if (!Float.isNaN(c.roomOxygen) && c.roomOxygen < AIR_HURTS) {
                r.id = Id.CAPTAIN_AIR;
                r.level = Level.WARNING;
            } else {
                String room = a.room.isEmpty() ? c.room : a.room;
                if (d.alerts.get("air:" + room) != null) return false;
                r.id = Id.AIR;
                r.level = Level.CAUTION;
            }
        } else if (id.equals("captain:suit")) {
            // By time instead of by share: SUIT AIR, then CAPTAIN AIR, this HUD's own
            return false;
        } else if (id.equals("captain:bleeding")) {
            r.id = Id.CAPTAIN_HURT;
            r.level = Level.WARNING;
        } else if (id.startsWith("casualty:")) {
            // Treatment holds the clock (CrewHealthSystem), so a critical wound being treated is an advisory; stabilising does not until it is done
            String state = condition(id.substring(9));
            r.id = Id.CRITICAL;
            if (state != null && state.endsWith("being treated")) {
                r.level = Level.ADVISORY;
                // The roster's line lights the lamp, in its own words
                return true;
            }
            r.level = Level.WARNING;
        } else if (id.startsWith("fire:")) {
            // A caution for its first 5 s and while someone is on it, outside the captain's room (v16);
            // where the game can't say who is on it, nobody is known to be
            boolean unanswered = a.handling <= 0 && d.ctx.simSeconds - a.since >= FIRE_UNANSWERED;
            boolean here = c.present && !c.room.isEmpty() && c.room.equals(a.room);
            r.id = Id.FIRE;
            r.level = unanswered || here ? Level.WARNING : Level.CAUTION;
        } else if (id.startsWith("breach:")) {
            r.id = Id.VENTING;
            r.level = Level.CAUTION;
        } else if (id.startsWith("air:") || id.startsWith("smoke:") || id.equals("oxygen")) {
            // Until someone without a suit is in air that hurts them, which the HUD can tell only for the captain
            r.id = Id.AIR;
            r.level = Level.CAUTION;
        } else if (id.equals("eva")) {
            r.id = Id.EVA;
            r.level = Level.ADVISORY;
        } else if (id.equals("hostile")) {
            // The target's lock on the ship: LOCKED ON, which takes no slot where the target plate shows it. A contact alone is dropped
            if (a.level == Level.ADVISORY || post != Post.DECK) return false;
            r.id = Id.LOCKED_ON;
            r.level = Level.CAUTION;
        } else if (id.startsWith("sys:")) {
            return system(a, d, r);
        } else if (id.startsWith("shield:")) {
            r.id = Id.SHIELD;
            r.level = Level.CAUTION;
        } else if (id.equals("hull")) {
            // Below 45% the ship is disabled, which is the warning
            r.id = Id.HULL;
            r.level = Level.CAUTION;
        } else if (id.equals("propellant")) {
            // Too little to fly home is the crisis placard's: the tow
            r.id = Id.PROPELLANT;
            r.level = Level.CAUTION;
        } else if (id.equals("helm") || id.startsWith("empty:")) {
            // The helm console's fault is its own system's alert (HELM is about orders); an empty launcher is WPN's 0/4
            return false;
        }
        if (r.lamp == null) r.lamp = r.id.lamp;
        return true;
    }

    /**
     * A system that is not working: DRIVE's, ARMS's, SHIELD's or AIR's by its system while it is
     * damaged or stunned; POWER's for a short or a cut (a shorted engine still lights DRIVE, and a gun
     * cut off ARMS), and its advisory for a system switched off. A reactor's fault is a POWER caution
     * while another reactor feeds the ship, a warning when none feeds anything, and so is a reactor
     * switched off then (the lamp table's "every reactor off"). While none feeds anything a system cut
     * off is quiet, since the reactor's warning says it once; so is the reactor's own while the ship is
     * disabled for want of power, which DISABLED says. An airlock's or a docking port's lights nothing,
     * since FLT's DOCK mode and COLLAR show the port.
     */
    private boolean system(Alert a, HudData d, Row r) {
        int at = a.id.indexOf(':', 4);
        String sys = a.id.substring(4, at < 0 ? a.id.length() : at);
        if (sys.equals("AIRLOCK") || sys.equals("DOCK PORT")) return false;
        Lamp its;
        switch (sys) {
            case "ENGINE": case "HELM": its = Lamp.DRIVE; break;
            case "TURRET": case "WEAPONS": its = Lamp.ARMS; break;
            case "SHIELDS": its = Lamp.SHIELD; break;
            case "LIFE SUP": its = Lamp.AIR; break;
            default: its = Lamp.POWER; break;
        }
        boolean reactor = sys.equals("REACTOR"), dark = noPower(d);
        Level fault = reactor && dark ? Level.WARNING : Level.CAUTION;
        if (reactor) r.quiet = d.alerts.get("disabled") != null && causeLamp(d.ctx.disabledReason) == Lamp.POWER;
        switch (faultOf(a)) {
            case OFF:
                r.id = Id.POWER;
                r.level = reactor && dark ? Level.WARNING : Level.ADVISORY;
                r.lamp = Lamp.POWER;
                break;
            case NO_POWER:
                r.id = Id.POWER;
                r.level = fault;
                r.lamp = Lamp.POWER;
                if (its == Lamp.ARMS) r.also = Lamp.ARMS;
                if (dark) r.quiet = true;
                break;
            case SHORTED:
                r.id = Id.POWER;
                r.level = fault;
                r.lamp = Lamp.POWER;
                if (its == Lamp.DRIVE || its == Lamp.ARMS) r.also = its;
                break;
            default:
                r.id = its == Lamp.DRIVE ? Id.DRIVE : its == Lamp.ARMS ? Id.ARMS : its == Lamp.SHIELD ? Id.SHIELD
                        : its == Lamp.AIR ? Id.AIR : Id.POWER;
                r.level = fault;
                r.lamp = its;
                break;
        }
        return true;
    }

    /** What a {@code sys:} alert's system has, from the power frame (its id names the system's tile), else from its words. */
    private Fault faultOf(Alert a) {
        SystemRow r = systems.get(a.id);
        return r != null ? r.fault : faultIn(a.text);
    }

    /** The fault a {@code sys:} alert's words name ("ENGINE in HOLD: destroyed"). */
    static Fault faultIn(String text) {
        if (text.endsWith(": destroyed")) return Fault.DESTROYED;
        if (text.endsWith(": disabled")) return Fault.DISABLED;
        if (text.endsWith(": shorted")) return Fault.SHORTED;
        if (text.endsWith(": stunned")) return Fault.STUNNED;
        if (text.endsWith(": no power")) return Fault.NO_POWER;
        return Fault.OFF;
    }

    /**
     * A system's fault in the words the ship's console uses for its state, so the band, the crisis
     * placard and the Systems tab agree (the game's "disabled" would read as the ship's DISABLED).
     */
    static String word(Fault f) {
        switch (f) {
            case DESTROYED: return "WRECKED";
            case DISABLED: return "DAMAGED";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "CUT OFF";
            default: return "OFF";
        }
    }

    /** No reactor feeds anything: every reactor off, shorted, stunned or broken. */
    static boolean noPower(HudData d) {
        PowerFrame p = d.powerFrame;
        return p.circuits.size > 0 && p.outputWatts <= 0 || p.circuits.size == 0 && p.ratedWatts > 0;
    }

    /** The lamp a disabled ship's cause lights. */
    static Lamp causeLamp(String reason) {
        String r = reason.toLowerCase(Locale.ROOT);
        if (r.contains("hull") || r.contains("wreck")) return Lamp.HULL;
        if (r.contains("crew")) return Lamp.CREW;
        return Lamp.POWER;
    }

    /** A crew member's condition in the roster ("Critical wound, being treated"), or null. */
    private String condition(String name) {
        for (CrewProvider.Member m : roster.crew) if (m.name.equals(name)) return m.condition;
        return null;
    }

    private void roster(HudData d, float delta) {
        rosterIn -= delta;
        if (rosterIn > 0f) return;
        rosterIn = ROSTER_EVERY;
        roster.clear();
        if (d.crew != null) d.crew.collect(roster);
    }

    // --- The alerts 9.2 has that the game doesn't raise yet -----------------------------------

    private void raiseOwn(HudData d) {
        own.begin(d.ctx.simSeconds);
        try {
            evaBurn(d);
            adrift(d);
            suit(d);
            wounded(d);
            noGuns(d);
            plan(d);
            cloud(d);
            oxygen(d);
            reactor(d);
            rcs(d);
        } finally {
            own.end();
        }
    }

    private Alert raise(String id, Level lv, Category cat, String code, String l1, String l2, String text) {
        String[] w = ownLines.get(id);
        if (w == null) ownLines.put(id, w = new String[3]);
        w[0] = code;
        w[1] = l1;
        w[2] = l2;
        return own.raise(OWN + id, lv, cat).set(code, text);
    }

    /** EVA BURN: crew outside with a burn under way or due in 30 s. */
    private void evaBurn(HudData d) {
        if (!d.ctx.crewOutside || d.ctx.docked) return;
        NavReadout n = d.navReadout;
        boolean burning = n.burning || d.ctx.burnFlown || d.flightStatus.mainEngine;
        float due = !burning && n.burnsLeft > 0 ? n.burnStartIn : Float.NaN;
        if (!burning && (Float.isNaN(due) || due > BURN_SOON)) return;
        Alert eva = d.alerts.get("eva");
        String who = outside(eva == null ? "" : eva.text);
        String when = burning ? "BURN UNDER WAY" : "BURN IN " + YardText.span(due);
        raise("EVA_BURN", Level.WARNING, Category.CREW, "CREW OUT", who, when,
                "Crew outside" + (who.isEmpty() ? "" : " (" + who + ")") + " with a burn " + (burning ? "under way" : "due in " + YardText.span(due))
                        + ": hold the burn, or call them in")
                .timer(burning ? 0f : Math.max(0f, due), TimerKind.HARM);
    }

    /** EVA: crew adrift, from the spacewalk line ("Ada adrift 340 m off, ..."). */
    private void adrift(HudData d) {
        Alert eva = d.alerts.get("eva");
        if (eva == null) return;
        String line = eva.text.startsWith("EVA: ") ? eva.text.substring(5) : eva.text;
        for (String part : line.split("; ")) {
            int k = part.indexOf(" adrift");
            if (k <= 0) continue;
            int dot = part.indexOf(". ");
            String said = dot < 0 ? part : part.substring(0, dot);
            String name = part.substring(0, k);
            int from = part.indexOf("adrift ", k), to = from < 0 ? -1 : part.indexOf(" off", from);
            String off = from >= 0 && to > from ? part.substring(from + 7, to).toUpperCase(Locale.ROOT) : "";
            raise("EVA:" + name, Level.CAUTION, Category.CREW, "ADRIFT", name.toUpperCase(Locale.ROOT), off, said + ": bring the ship to them");
        }
    }

    /** Who is outside, from the spacewalk line: the first by name, and +n for the rest ("ADA +1"). */
    static String outside(String line) {
        String s = line.startsWith("EVA: ") ? line.substring(5) : line;
        if (s.isEmpty()) return "";
        String[] parts = s.split("; ");
        String first = parts[0];
        int cut = first.length();
        for (String w : new String[]{" on ", " in ", " off ", " adrift", " crossing", " outside"}) {
            int at = first.indexOf(w);
            if (at > 0 && at < cut) cut = at;
        }
        String name = first.substring(0, cut).toUpperCase(Locale.ROOT);
        return parts.length > 1 ? name + " +" + (parts.length - 1) : name;
    }

    /** SUIT AIR by time, in bad air or outside: under 4 minutes a caution, under a minute CAPTAIN AIR. */
    private void suit(HudData d) {
        CaptainStatus c = d.captainStatus;
        if (!c.present || !c.suited || Float.isNaN(c.suitAir)) return;
        boolean bad = Float.isNaN(c.roomPressure) || c.roomPressure < ShipAlerts.LOW_PRESSURE
                || !Float.isNaN(c.roomOxygen) && c.roomOxygen < AIR_HURTS;
        if (!bad || c.suitAir >= SUIT_SOON) return;
        String t = YardText.span(c.suitAir);
        if (c.suitAir < SUIT_LAST) {
            raise("CAPTAIN_AIR", Level.WARNING, Category.CAPTAIN, "YOUR AIR", "SUIT", t, "Your suit air: " + t + " left: a locker, or good air")
                    .timer(c.suitAir, TimerKind.HARM);
        } else {
            raise("SUIT_AIR", Level.CAUTION, Category.CAPTAIN, "SUIT AIR", "YOU", t, "Your suit air: " + t + " left: get to good air")
                    .timer(c.suitAir, TimerKind.HARM);
        }
    }

    /** WOUNDED: seriously wounded or out cold, or critical and stabilised, and nobody treating them (the roster's words). */
    private void wounded(HudData d) {
        for (CrewProvider.Member m : roster.crew) {
            String c = m.condition, state;
            if (c.startsWith("Serious wound, down")) state = "DOWN";
            else if (c.startsWith("Unconscious")) state = "OUT COLD";
            else if (c.equals("Critical wound, stabilised")) state = "STABILISED";
            else continue;
            raise("WOUNDED:" + m.name, Level.CAUTION, Category.CREW, "WOUNDED", m.name.toUpperCase(Locale.ROOT), state,
                    "Wounded: " + m.name + ", " + c.toLowerCase(Locale.ROOT) + ", nobody treating them");
        }
    }

    /** ARMS at its warning: in combat with no gun able to fire. */
    private void noGuns(HudData d) {
        Array<WeaponStatusProvider.Mount> mounts = d.weaponFrame.mounts;
        if (mounts.size == 0 || !d.ctx.combat) return;
        for (WeaponStatusProvider.Mount m : mounts) if (m.working()) return;
        raise("ARMS", Level.WARNING, Category.TACTICAL, "ARMS", "NO GUNS", "BREAK OFF", "No guns working: break off");
    }

    /** PROPELLANT short of the accepted plan, while the game's own propellant alert doesn't hold. */
    private void plan(HudData d) {
        if (d.alerts.get("propellant") != null) return;
        FlightStatus f = d.flightStatus;
        NavReadout n = d.navReadout;
        double plan = n.proposal ? 0 : Math.max(n.planDeltaV, f.deltaVPlanned);
        if (plan <= 0 || !Double.isFinite(f.deltaVLeft) || f.deltaVLeft >= plan) return;
        raise("PROPELLANT", Level.CAUTION, Category.FLIGHT, "PROP", YardText.thousands(f.deltaVLeft) + " M/S", "PLAN " + YardText.thousands(plan),
                String.format(Locale.ROOT, "Propellant: %,.0f m/s left, the plan takes %,.0f", f.deltaVLeft, plan));
    }

    /** SHIELD: the cloud below a quarter with the shields up. */
    private void cloud(HudData d) {
        ShieldStatus s = d.shieldStatus;
        if (!shieldFitted || !s.raised || s.capacity <= 0f || s.share() >= CLOUD_QUARTER) return;
        String pct = Math.round(s.share() * 100f) + "%";
        raise("SHIELD", Level.CAUTION, Category.TACTICAL, "SHIELD", "CLOUD", pct, "Shield cloud at " + pct);
    }

    /** AIR: the oxygen tanks below a quarter, before the game's own oxygen alert (below a fifth). */
    private void oxygen(HudData d) {
        float o = d.shipStatus.oxygen;
        if (d.alerts.get("oxygen") != null || Float.isNaN(o) || o >= O2_LOW) return;
        String pct = Math.round(o * 100f) + "%";
        raise("AIR:O2", Level.CAUTION, Category.LIFE_SUPPORT, "O2", pct, "LOW", "Oxygen tanks at " + pct);
    }

    /** POWER: a working reactor below full output from damage. */
    private void reactor(HudData d) {
        PowerFrame p = d.powerFrame;
        if (p.ratedWatts <= 0 || p.outputWatts <= 0 || p.outputWatts >= p.ratedWatts * 0.995) return;
        for (SystemRow r : p.faults) if ("REACTOR".equals(r.system)) return;
        String pct = Math.round(p.outputWatts / p.ratedWatts * 100) + "%";
        raise("POWER:REACTOR", Level.CAUTION, Category.ENGINEERING, "POWER", "REACTOR", "AT " + pct, "Reactor output at " + pct + " of its rating");
    }

    /** DRIVE: an RCS block whose hull tile is gone. The crew frame it back up on EVA, never at battle stations. */
    private void rcs(HudData d) {
        Entity ship = d.ctx.ship;
        ShipGridComponent g = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        if (g == null) return;
        int down = 0;
        for (int i = 0; i < g.rcsThrusters.size; i++) if (!g.rcsThrusters.get(i).works(g)) down++;
        if (down == 0) return;
        raise("DRIVE:RCS", Level.CAUTION, Category.FLIGHT, "RCS", down == 1 ? "1 BLOCK" : down + " BLOCKS", "WRECKED",
                (down == 1 ? "An RCS block is" : down + " RCS blocks are") + " wrecked: the crew frame the hull back up on EVA").count(down);
    }

    /** The advisories no alert carries: delta-v thinning, the shields lowered or thin, light wounds, wounds being treated, low morale. */
    private void advisories(HudData d) {
        FlightStatus f = d.flightStatus;
        if (Double.isFinite(f.deltaVLeft) && Double.isFinite(f.deltaVHome) && f.deltaVHome > 0 && f.deltaVLeft < PROP_THIN * f.deltaVHome) {
            light(Lamp.PROP, Level.ADVISORY, false,
                    String.format(Locale.ROOT, "Delta-v %,.0f m/s left, home takes %,.0f", f.deltaVLeft, f.deltaVHome));
        }
        ShieldStatus s = d.shieldStatus;
        if (shieldFitted && !s.raised && !d.ctx.combat && !s.loweredForDock) light(Lamp.SHIELD, Level.ADVISORY, false, "Shields lowered");
        if (shieldFitted && s.raised && s.capacity > 0f && s.share() < CLOUD_HALF) {
            light(Lamp.SHIELD, Level.ADVISORY, false, "Shield cloud below half");
        }
        for (CrewProvider.Member m : roster.crew) {
            String c = m.condition;
            if (c.startsWith("Light wound") || c.endsWith("being treated")) {
                light(Lamp.CREW, Level.ADVISORY, false, m.name + ": " + c.toLowerCase(Locale.ROOT));
            }
            if (m.morale.endsWith("(slacking)")) light(Lamp.CREW, Level.ADVISORY, false, m.name + ": morale low");
        }
    }

    private float age(Alert a) {
        return clock - seen.get(a.id, clock);
    }

    // --- The band ------------------------------------------------------------------------------

    /** Warnings: the soonest harm first, then 9.2's order, then the oldest. */
    private static final Comparator<Row> WARNINGS = (a, b) -> sooner(a, b) ? -1 : sooner(b, a) ? 1 : 0;
    /** Cautions: 9.2's order (what can hurt someone, then what the ship can't do, then the job), then the oldest. */
    private static final Comparator<Row> CAUTIONS = (a, b) -> a.id != b.id ? a.id.compareTo(b.id) : Double.compare(a.a.since, b.a.since);

    /** One warning on the top row, the rest of the warnings and the fresh cautions below. A quiet row takes neither. */
    private void band() {
        Row top = null;
        for (Row r : rows) if (r.level == Level.WARNING && !r.quiet && (top == null || sooner(r, top))) top = r;
        warning = top == null ? null : top.a;
        cautions.clear();
        sorted.clear();
        for (Row r : rows) if (r.level == Level.WARNING && !r.quiet && r != top) sorted.add(r);
        sorted.sort(WARNINGS);
        for (Row r : sorted) cautions.add(r.a);
        sorted.clear();
        for (Row r : rows) {
            if (r.level != Level.CAUTION || r.quiet || r.a.acknowledged || age(r.a) >= CAUTION_SECONDS) continue;
            // No caution for what a visible widget shows in full: at gunnery a mount's fault is WPN's row
            if (post == Post.GUNNERY && (r.lamp == Lamp.ARMS || r.also == Lamp.ARMS)) continue;
            sorted.add(r);
        }
        sorted.sort(CAUTIONS);
        for (Row r : sorted) cautions.add(r.a);
        sorted.clear();
        masterWarning = warning != null && !warning.acknowledged;
        warningNew = warning != null && !warning.acknowledged && age(warning) < NEW_SECONDS;
        masterCaution = false;
        for (Alert a : cautions) if (!a.acknowledged) masterCaution = true;
    }

    /** {@code a} harms sooner than {@code b}: a countdown to harm beats none, a shorter one a longer; then 9.2's order, then age. */
    private static boolean sooner(Row a, Row b) {
        boolean ta = harm(a.a), tb = harm(b.a);
        if (ta && tb && a.a.timer != b.a.timer) return a.a.timer < b.a.timer;
        if (ta != tb) return ta;
        if (a.id != b.id) return a.id.ordinal() < b.id.ordinal();
        return a.a.since < b.a.since;
    }

    private static boolean harm(Alert a) {
        return a.timerKind == TimerKind.HARM && !Float.isNaN(a.timer);
    }

    /** Whether the band has anything to show: the visor is down. */
    boolean bandUp() {
        return warning != null || cautions.size > 0;
    }

    /** Whether any of the lamps the schematic answers for is lit at caution or worse. */
    boolean schematicWanted() {
        for (Lamp l : new Lamp[]{Lamp.HULL, Lamp.FIRE, Lamp.AIR, Lamp.POWER, Lamp.CREW, Lamp.EVA}) {
            Level lv = level[l.ordinal()];
            if (lv != null && lv != Level.ADVISORY) return true;
        }
        return false;
    }

    // --- The band's words ----------------------------------------------------------------------

    /** Where the captain is, for the action an alert names (spec 9.1: the action fits the post). */
    enum Post {
        HELM, GUNNERY, DECK
    }

    /**
     * The warning row's title, large: what and the one number that matters. The diamond is drawn
     * before it as a shape.
     */
    static String title(Alert a, HudData d) {
        String id = a.id;
        if (id.equals("missile")) {
            String t = "MISSILE   " + a.count + " INBOUND";
            return Float.isNaN(a.timer) ? t : t + "   IMPACT " + YardText.span(a.timer);
        }
        if (id.equals("disabled")) return "SHIP DISABLED";
        if (id.equals("abandon")) return "ABANDON SHIP";
        if (id.equals("captain:bleeding")) return Float.isNaN(a.timer) ? "YOU ARE CRITICAL" : "YOU ARE CRITICAL   " + YardText.span(a.timer) + " TO LIVE";
        if (id.equals(OWN + "CAPTAIN_AIR")) return "YOUR AIR   " + YardText.span(a.timer);
        if (id.equals("captain:air")) {
            CaptainStatus c = d.captainStatus;
            String room = a.room.isEmpty() ? c.room : a.room;
            if (Float.isNaN(c.roomOxygen)) return "YOUR AIR   NO SUIT";
            return "YOUR AIR   " + (room.isEmpty() ? "" : room + " ") + Math.round(c.roomOxygen * 100f) + "% OXYGEN";
        }
        if (id.startsWith("casualty:")) {
            String name = id.substring(9).toUpperCase(Locale.ROOT);
            return Float.isNaN(a.timer) ? "CRITICAL   " + name : "CRITICAL   " + name + "   " + YardText.span(a.timer) + " TO LIVE";
        }
        if (id.equals(OWN + "EVA_BURN")) {
            String who = outside(d.alerts.get("eva") == null ? "" : d.alerts.get("eva").text);
            return "CREW OUTSIDE" + (who.isEmpty() ? "" : "   " + who);
        }
        if (id.equals(OWN + "ARMS")) return "NO GUNS WORKING";
        if (id.startsWith("fire:")) return "FIRE   " + a.room + ", " + tiles(a.count);
        if (id.startsWith("breach:")) return "HULL BREACH   " + a.room;
        if (id.startsWith("air:")) return a.room + " IN VACUUM";
        if (id.equals("hull")) return "HULL   " + percent(a.text);
        if (id.equals("oxygen")) return "OXYGEN TANKS DRY";
        if (id.equals("propellant")) return "PROPELLANT LOW";
        if (id.startsWith("sys:REACTOR")) return "NO POWER   REACTOR " + word(faultIn(a.text));
        if (id.startsWith("sys:")) return a.code + "   " + word(faultIn(a.text));
        return a.code;
    }

    /** The warning row's second line: the action, with its keys as {@link YardPaint#KEY} segments. */
    static String[] action(Alert a, HudData d, Post post) {
        String id = a.id;
        if (id.equals("missile")) {
            boolean pd = d.weaponFrame.pointDefenseActive;
            if (post == Post.DECK) return new String[]{pd ? "POINT DEFENSE ENGAGING;  " : "", key(d, "VIEW"), "AT A CONSOLE TO FIGHT"};
            return new String[]{key(d, "DECOY"), "DECOY", pd ? "      POINT DEFENSE ENGAGING" : ""};
        }
        if (id.equals("disabled")) return new String[]{d.ctx.disabledReason.toUpperCase(Locale.ROOT), "   OPTIONS BELOW"};
        if (id.equals("abandon")) return new String[]{"PODS LAUNCH SOON   ", key(d, "ABANDON"), "NOW"};
        if (id.equals("captain:bleeding")) return new String[]{a.handling > 0 ? who(a) : "NOBODY FREE TO TREAT YOU"};
        if (id.equals("captain:air") || id.equals(OWN + "CAPTAIN_AIR")) return new String[]{key(d, "USE"), "AT A LOCKER, OR LEAVE THE ROOM"};
        if (id.startsWith("casualty:")) return new String[]{(a.handling > 0 ? who(a) : "NOBODY FREE") + "   ", key(d, "CREW"), "CREW"};
        if (id.equals(OWN + "EVA_BURN")) {
            String when = a.timer > 0f ? "BURN IN " + YardText.span(a.timer) + ":  " : "";
            return new String[]{when + "HOLD THE BURN;  ", key(d, "CREW"), "CALL THEM IN"};
        }
        if (id.equals(OWN + "ARMS")) return new String[]{"BREAK OFF"};
        if (id.startsWith("fire:")) return new String[]{who(a)};
        if (id.startsWith("breach:")) return new String[]{"VENTING   " + who(a)};
        if (id.startsWith("sys:")) {
            return new String[]{a.timerKind == TimerKind.RECOVERY && !Float.isNaN(a.timer) ? "BACK IN " + YardText.span(a.timer) : who(a)};
        }
        if (id.equals("propellant")) return new String[]{after(a.text).toUpperCase(Locale.ROOT)};
        return new String[]{a.text.toUpperCase(Locale.ROOT)};
    }

    /**
     * A caution's two lines, each within 19 characters (spec 9.2): what and where, then who is on it,
     * the time or the key. The first line starts with the code, drawn bold.
     */
    String[] lines(Alert a, HudData d) {
        String[] w = a.id.startsWith(OWN) ? ownLines.get(a.id.substring(OWN.length())) : null;
        if (w != null) return fitted(w[0], w[1], w[2]);
        return gameLines(a, d);
    }

    /** The two lines of one of the game's alerts. */
    static String[] gameLines(Alert a, HudData d) {
        String id = a.id, code = a.code, l1 = "", l2 = "";
        if (id.equals("missile")) {
            l1 = String.valueOf(a.count);
            l2 = Float.isNaN(a.timer) ? "INBOUND" : YardText.span(a.timer);
        } else if (id.startsWith("fire:")) {
            // Sized as the game counts it, then who is on it: "3 TILES · 1 CREW" (v16)
            l1 = a.room;
            String on = crew(a);
            l2 = on.isEmpty() ? tiles(a.count) : tiles(a.count) + " · " + on;
        } else if (id.startsWith("smoke:")) {
            l1 = a.room;
            l2 = who(a);
        } else if (id.startsWith("breach:")) {
            code = "VENTING";
            l1 = a.room;
            l2 = who(a);
        } else if (id.startsWith("air:")) {
            if (code.equals("PRESSURE")) code = "THIN AIR";
            l1 = a.room;
            l2 = percent(a.text);
        } else if (id.equals("oxygen")) {
            if (a.text.startsWith("Oxygen tanks dry")) {
                code = "O2 DRY";
                l1 = "RESERVE " + percent(a.text);
                l2 = "A YARD REFILLS";
            } else {
                l1 = percent(a.text);
                l2 = "LOW";
            }
        } else if (id.equals("hull")) {
            l1 = percent(a.text);
            l2 = a.handling > 0 ? who(a) : "";
        } else if (id.equals("propellant")) {
            FlightStatus f = d.flightStatus;
            code = "PROP";
            if (Double.isFinite(f.deltaVLeft)) {
                l1 = YardText.thousands(f.deltaVLeft) + " M/S";
                l2 = Double.isFinite(f.deltaVHome) ? "HOME " + YardText.thousands(f.deltaVHome) : after(a.text);
            } else {
                l1 = "LOW";
                l2 = after(a.text);
            }
        } else if (id.startsWith("sys:")) {
            l1 = a.room;
            l2 = a.timerKind == TimerKind.RECOVERY && !Float.isNaN(a.timer) ? "BACK IN " + YardText.span(a.timer) : word(faultIn(a.text));
        } else if (id.equals("captain:air")) {
            // Below a warning that outranks it, or the thin air of a room no AIR caution names: the oxygen once it hurts, else the pressure
            CaptainStatus c = d.captainStatus;
            String room = a.room.isEmpty() ? c.room : a.room;
            code = "YOUR AIR";
            l1 = "NO SUIT";
            if (!Float.isNaN(c.roomOxygen) && c.roomOxygen < AIR_HURTS) l2 = room + "  " + Math.round(c.roomOxygen * 100f) + "% O2";
            else if (!Float.isNaN(c.roomPressure)) l2 = room + "  " + Math.round(c.roomPressure * 100f) + "%";
            else l2 = room;
            l2 = l2.trim();
        } else if (id.equals("captain:bleeding")) {
            code = "YOU";
            l1 = "CRITICAL";
            l2 = Float.isNaN(a.timer) ? "FIND A MEDIC" : YardText.span(a.timer);
        } else if (id.equals("hostile")) {
            // "Pirate raider 6 has you locked": LOCKED ON, the ship's name under it
            code = "LOCKED ON";
            int k = a.text.indexOf(" has you locked");
            l2 = k > 0 ? a.text.substring(0, k) : after(a.text);
        } else if (id.startsWith("casualty:")) {
            code = "CRITICAL";
            l1 = id.substring(9);
            l2 = Float.isNaN(a.timer) ? "BLEEDING" : YardText.span(a.timer);
        } else if (id.startsWith("shield:")) {
            l1 = id.endsWith("tripped") ? "TRIPPED" : "DOWN";
        } else {
            l2 = after(a.text);
        }
        return fitted(code, l1, l2);
    }

    private static String[] fitted(String code, String l1, String l2) {
        int room1 = Math.max(0, 19 - code.length() - 1);
        return new String[]{code, YardText.fit(l1, room1), YardText.fit(l2, 19)};
    }

    /**
     * What a lamp's hover says of an alert: its own line, or a fire by its tiles and who is on it, by
     * name where the game names them ("FIRE: HOLD, 3 tiles, Cole on it", v16).
     */
    static String hover(Alert a) {
        if (!a.id.startsWith("fire:")) return a.text;
        String on;
        if (a.handling > 0 && !a.who.isEmpty()) on = a.handling == 1 ? ", " + a.who + " on it" : ", " + a.who + " and " + (a.handling - 1) + " more on it";
        else if (a.handling > 0) on = a.handling == 1 ? ", 1 crew on it" : ", " + a.handling + " crew on it";
        else on = a.handling == 0 ? ", nobody on it" : "";
        return "FIRE: " + a.room + ", " + tiles(a.count).toLowerCase(Locale.ROOT) + on;
    }

    /** A fire's size as the game counts it, in tiles burning: "1 TILE", "3 TILES". */
    static String tiles(int count) {
        int n = Math.max(1, count);
        return n == 1 ? "1 TILE" : n + " TILES";
    }

    /**
     * Who is on a fire, short, after its tiles: the one the game names with how many more ("COLE",
     * "COLE +1"), "1 CREW" where it names nobody, "NOBODY", or "" where it can't say.
     */
    private static String crew(Alert a) {
        if (a.handling > 0) return a.who.isEmpty() ? a.handling + " CREW" : named(a);
        return a.handling == 0 ? "NOBODY" : "";
    }

    /** Who is on it, as a line: "COLE ON IT", "COLE +1 ON IT", "2 CREW ON IT", "NOBODY ON IT", or "". */
    static String who(Alert a) {
        if (a.handling > 0) return (a.who.isEmpty() ? a.handling + " CREW" : named(a)) + " ON IT";
        if (a.handling == 0) return "NOBODY ON IT";
        return "";
    }

    private static String named(Alert a) {
        String name = a.who.toUpperCase(Locale.ROOT);
        return a.handling > 1 ? name + " +" + (a.handling - 1) : name;
    }

    /** The text after its first colon ("Propellant low: 500 kg left" gives "500 kg left"), or all of it. */
    static String after(String text) {
        int i = text.indexOf(": ");
        return i < 0 ? text : text.substring(i + 2);
    }

    /** The first percentage in {@code text} ("Hull at 62%" gives "62%"), or "". */
    static String percent(String text) {
        int p = text.indexOf('%');
        if (p < 0) return "";
        int s = p;
        while (s > 0 && (Character.isDigit(text.charAt(s - 1)) || text.charAt(s - 1) == '.')) s--;
        return text.substring(s, p + 1);
    }

    private static String key(HudData d, String action) {
        String k = d.key(action);
        return k.isEmpty() ? "" : YardPaint.KEY + k.toUpperCase(Locale.ROOT);
    }
}
