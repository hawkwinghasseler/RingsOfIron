package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * The alert band and the annunciator (functional-task 6, ALR and ANN; 9): the spec's alert list
 * (9.2) worked out from the game's alerts and the data, each with its full text and short form, the
 * warning window's fields, and the action that fits the post; which of them the band shows (one
 * warning, three cautions and a count of the rest); how a caution leaves the band after 10 s and
 * comes back when it worsens (9.3); and the ten lamps. Pure: tested without a screen.
 */
final class Catalogue {
    /** The ten lamps, in their order on the block. */
    enum Lamp {
        HULL, FIRE, AIR, POWER, PROP, DRIVE, ARMS, CREW, EVA, SHIELD
    }

    /** A lamp's four states. */
    enum Lit {
        DARK, ADVISORY, CAUTION, WARNING
    }

    /** MISSILE is a warning under this many seconds to impact (9.2). */
    static final float MISSILE_SOON = 20f;
    /** COLLISION: contact this soon at more than the safe speed, s. */
    static final float COLLISION_SOON = 10f;
    /** EVA BURN: a burn due this soon with crew outside, s. */
    static final float BURN_SOON = 30f;
    /** CAPTAIN AIR: suit air under this, s. */
    static final float AIR_SOON = 60f;
    /** CONTRACT: under this left with no plan, s. */
    static final double CONTRACT_SOON = 6 * 3600;
    /** PROP lights at caution under this times what home takes, at warning under the second (ANN). */
    static final double PROP_CAUTION = 1.5, PROP_WARNING = 1.25;
    /** SHIELD: a cloud under half lights the lamp, under a quarter in combat at warning. */
    static final float CLOUD_HALF = 0.5f, CLOUD_QUARTER = 0.25f;
    /** A fire this many tiles big is large, as one large tile is. */
    static final int LARGE_FIRE = 6;
    /** A caution stays in the band this long (9.3); a new warning pulses this long; a lamp blinks this long. */
    static final float CAUTION_STAYS = 10f, WARNING_PULSE = 3f, LAMP_BLINK = 3f;
    /** The caution row's slots. */
    static final int SLOTS = 3;
    /** A caution cell's line, in characters (two lines a cell). */
    static final int LINE = 19;

    /** One alert of the list (9.2). */
    static final class Entry {
        String id = "";
        Level level = Level.CAUTION;
        Lamp lamp;
        /** The full text in its two parts, and the short form's. */
        String full1 = "", full2 = "", short1 = "", short2 = "";
        /** The warning window: title, detail, the one number with its label and unit, the action behind its key, a note. */
        String title = "", detail = "", label = "", number = "", unit = "", key = "", action = "", note = "";
        /** Seconds to harm, NaN when none is known: warnings are ordered by it. */
        float harm = Float.NaN;
        /** How much of it there is (rooms, missiles): more of it is worse, and brings a caution back. */
        int count = 1;
        /** A widget on screen shows it in full: it takes no caution slot (9.1). */
        boolean shown;
        /** When it was first seen, and when it last came into the band, in HUD seconds. */
        float since, entered;

        void reset() {
            id = full1 = full2 = short1 = short2 = "";
            title = detail = label = number = unit = key = action = note = "";
            level = Level.CAUTION;
            lamp = null;
            harm = Float.NaN;
            count = 1;
            shown = false;
            since = entered = 0f;
        }

        /** Line {@code i} (0 or 1) of a caution cell: the full text's part when it fits, else the short form's. */
        String line(int i) {
            String full = i == 0 ? full1 : full2, brief = i == 0 ? short1 : short2;
            return full.length() <= LINE ? full : Words.cut(brief, LINE);
        }

        Entry text(String f1, String f2, String s1, String s2) {
            full1 = up(f1);
            full2 = up(f2);
            short1 = up(s1);
            short2 = up(s2);
            return this;
        }

        Entry window(String title, String detail, String label, String number, String unit) {
            this.title = up(title);
            this.detail = up(detail);
            this.label = up(label);
            this.number = number == null ? "" : number;
            this.unit = up(unit);
            return this;
        }

        Entry act(String key, String action, String note) {
            this.key = up(key);
            this.action = up(action);
            this.note = up(note);
            return this;
        }
    }

    /** What the band remembers of an alert between frames. */
    private static final class Seen {
        float since, entered;
        Level level;
        int count;
        boolean acked;
    }

    /** Every alert of the list that holds, in no order. */
    final Array<Entry> entries = new Array<>();
    /** The warning window's alert, or null; whether it pulses (new and not yet acknowledged). */
    Entry warning;
    boolean pulse;
    /** The caution row: pushed-down warnings first, then cautions by age; and how many more there are. */
    final Array<Entry> row = new Array<>();
    int more;
    /** Each lamp's state, whether it blinks (new), and whether the ship has the lamp at all (SHIELD needs an emitter). */
    final Lit[] lamps = new Lit[Lamp.values().length];
    final boolean[] blink = new boolean[Lamp.values().length];
    boolean shieldLamp;
    /** Main engines aboard and how many work, -1 when not known: DRIVE's warning. */
    int engines = -1, enginesWorking = -1;

    private final Array<Entry> pool = new Array<>(), warnings = new Array<>(), cautions = new Array<>();
    private final ObjectMap<String, Seen> seen = new ObjectMap<>();
    private final Array<String> gone = new Array<>();
    private final Lit[] lastLamp = new Lit[Lamp.values().length];
    private final float[] lampSince = new float[Lamp.values().length];

    Catalogue() {
        for (int i = 0; i < lamps.length; i++) {
            lamps[i] = lastLamp[i] = Lit.DARK;
            lampSince[i] = -LAMP_BLINK;
        }
    }

    /** Reads this frame's alerts and lamps at HUD time {@code now} (seconds, not sim time: warp does not hurry it). */
    void update(HudData d, Posts p, float now) {
        for (Entry e : entries) pool.add(e);
        entries.clear();
        list(d, p);
        band(now);
        lamps(d, p, now);
    }

    /** Clicking the band (9.3): cautions leave it, warnings stop pulsing; what worsens comes back. */
    void acknowledge() {
        for (ObjectMap.Entry<String, Seen> e : seen) e.value.acked = true;
        pulse = false;
        // and the lamps stop blinking
        for (int i = 0; i < lampSince.length; i++) {
            lampSince[i] = -LAMP_BLINK;
            blink[i] = false;
        }
    }

    // ------------------------------------------------------------------ the band

    private void band(float now) {
        // When each was first seen and last came into the band: new, or worse than it was
        for (Entry e : entries) {
            Seen s = seen.get(e.id);
            if (s == null) {
                s = new Seen();
                s.since = s.entered = now;
                s.level = e.level;
                s.count = e.count;
                seen.put(e.id, s);
            } else if (e.level.ordinal() > s.level.ordinal() || e.level == s.level && e.count > s.count) {
                s.entered = now;
                s.acked = false;
            }
            s.level = e.level;
            s.count = e.count;
            e.since = s.since;
            e.entered = s.entered;
        }
        gone.clear();
        for (ObjectMap.Entry<String, Seen> e : seen) if (!has(e.key)) gone.add(e.key);
        for (String id : gone) seen.remove(id);

        // Warnings by time to harm (unknown last), then the oldest; the first has the window
        warnings.clear();
        cautions.clear();
        for (Entry e : entries) {
            if (e.level == Level.WARNING) warnings.add(e);
            else if (e.level == Level.CAUTION && !e.shown && now - e.entered < CAUTION_STAYS && !seen.get(e.id).acked) cautions.add(e);
        }
        warnings.sort(Catalogue::urgency);
        cautions.sort((a, b) -> Float.compare(a.since, b.since));
        warning = warnings.size > 0 ? warnings.first() : null;
        pulse = warning != null && !seen.get(warning.id).acked && now - warning.since < WARNING_PULSE;
        row.clear();
        for (int i = 1; i < warnings.size; i++) row.add(warnings.get(i));
        row.addAll(cautions);
        more = Math.max(0, row.size - SLOTS);
        row.truncate(Math.min(row.size, SLOTS));
    }

    /** The sooner harm first, a known time before an unknown one; then the older. */
    static int urgency(Entry a, Entry b) {
        boolean ta = !Float.isNaN(a.harm), tb = !Float.isNaN(b.harm);
        if (ta != tb) return ta ? -1 : 1;
        if (ta && a.harm != b.harm) return Float.compare(a.harm, b.harm);
        return Float.compare(a.since, b.since);
    }

    private boolean has(String id) {
        for (Entry e : entries) if (e.id.equals(id)) return true;
        return false;
    }

    // ------------------------------------------------------------------ the list (9.2)

    private Entry entry(String id, Level level, Lamp lamp) {
        for (Entry e : entries) {
            if (!e.id.equals(id)) continue;
            if (level.ordinal() > e.level.ordinal()) e.level = level;
            return e;
        }
        Entry e = pool.size > 0 ? pool.pop() : new Entry();
        e.reset();
        e.id = id;
        e.level = level;
        e.lamp = lamp;
        entries.add(e);
        return e;
    }

    private void list(HudData d, Posts p) {
        if (d.ctx.ship == null) return;
        missile(d, p);
        collision(d);
        impact(d, p);
        disabled(d);
        abandon(d);
        captain(d);
        evaBurn(d);
        rooms(d);
        power(d);
        propellant(d);
        helm(d);
        contracts(d);
        shield(d, p);
        collar(d, p);
        wounded(d);
    }

    private void missile(HudData d, Posts p) {
        int n = Math.max(d.threatFrame.incoming(), d.ctx.missilesInbound);
        if (n == 0) return;
        float t = d.threatFrame.soonestImpact();
        Entry e = entry("MISSILE", !Float.isNaN(t) && t < MISSILE_SOON ? Level.WARNING : Level.CAUTION, null);
        e.count = n;
        e.harm = t;
        String s = Fmt.secs(t);
        String key = "", action, note = pointDefense(d);
        if (p.post == Posts.Post.DECK) {
            // On deck F forces doors: the action that fits is getting to a console
            action = "V AT A CONSOLE TO FIGHT";
        } else {
            key = key(d, "DECOY", "F");
            action = d.weaponFrame.decoyCooldown > 0f ? "DECOY " + Fmt.secs(d.weaponFrame.decoyCooldown) + " S" : "DECOY";
        }
        String act = key.isEmpty() ? action : key + " " + action;
        e.text("MISSILE  " + n + " INBOUND", s.isEmpty() ? act : "IMPACT " + s + " S  " + act,
                "MISSILE " + n, s.isEmpty() ? "INBOUND" : s + " S")
                .window("MISSILE", n + " INBOUND", s.isEmpty() ? "" : "IMPACT", s, s.isEmpty() ? "" : "S")
                .act(key, action, note);
    }

    /** Point defense's state, the game's way: "POINT DEFENSE ENGAGING", or "" with none aboard. */
    static String pointDefense(HudData d) {
        boolean any = false, engaging = false;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind != Kind.POINT_DEFENSE) continue;
            any = true;
            if (m.engaging || m.defending) engaging = true;
        }
        if (!any) return "";
        return d.weaponFrame.pointDefenseActive || engaging ? "POINT DEFENSE ENGAGING" : "POINT DEFENSE IDLE";
    }

    private void collision(HudData d) {
        DockingApproach k = d.docking;
        if (!k.present || k.capture != Capture.APPROACH || Float.isNaN(k.closing) || Float.isNaN(k.safeSpeed) || Float.isNaN(k.range)) return;
        if (k.closing <= k.safeSpeed || k.closing <= 0f) return;
        float t = k.range / k.closing;
        if (t >= COLLISION_SOON) return;
        String who = Fmt.name(k.other, 12), s = Fmt.secs(t);
        entry("COLLISION", Level.WARNING, null)
                .text("COLLISION  " + who, "IN " + s + " S  BRAKE", "COLLISION", s + " S")
                .window("COLLISION", who, "IN", s, "S")
                .act("", "BRAKE", "")
                .harm = t;
    }

    private void impact(HudData d, Posts p) {
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (o == null || !o.impactWarning || p.docked || d.navReadout.burnsLeft > 0) return;
        // The game gives no time to impact: a caution, which says what to do
        String m = key(d, "MAP", "M");
        entry("IMPACT", Level.CAUTION, null)
                .text("SURFACE IMPACT", "RAISE PERIAPSIS (" + m + ")", "IMPACT " + Fmt.name(o.bodyName, 10), m)
                .window("IMPACT", o.bodyName, "", "", "")
                .act(m, "RAISE PERIAPSIS", "");
    }

    private void disabled(HudData d) {
        String why = d.ctx.disabledReason;
        if (why.isEmpty()) return;
        String w = up(why);
        entry("DISABLED", Level.WARNING, causeLamp(why))
                .text("SHIP DISABLED", w, "DISABLED", w)
                .window("DISABLED", w, "", "", "")
                .act("", "OPTIONS BELOW", "");
    }

    /** The lamp a disabling cause lights (9.2: "as the cause"). */
    static Lamp causeLamp(String why) {
        String w = why.toLowerCase(Locale.ROOT);
        if (w.contains("reactor") || w.contains("power")) return Lamp.POWER;
        if (w.contains("hull")) return Lamp.HULL;
        if (w.contains("crew")) return Lamp.CREW;
        if (w.contains("engine") || w.contains("drive")) return Lamp.DRIVE;
        if (w.contains("air") || w.contains("life")) return Lamp.AIR;
        return null;
    }

    private void abandon(HudData d) {
        if (!d.ctx.abandoning && !d.orderState.abandoning) return;
        float t = d.orderState.podsLaunchIn;
        String s = Fmt.secs(t), p = key(d, "ABANDON", "P");
        Entry e = entry("ABANDON", Level.WARNING, Lamp.CREW)
                .text("ABANDON SHIP", s.isEmpty() ? "TO THE PODS  " + p + " NOW" : "PODS LAUNCH IN " + s + " S",
                        s.isEmpty() ? "ABANDON" : "ABANDON " + s + " S", p)
                .window("ABANDON", "PODS LAUNCH", s.isEmpty() ? "" : "IN", s, s.isEmpty() ? "" : "S")
                .act(p, "NOW", "");
        e.harm = t;
    }

    private void captain(HudData d) {
        CaptainStatus c = d.captainStatus;
        if (!c.present) return;
        Alert suit = d.alerts.get("captain:suit"), air = d.alerts.get("captain:air");
        if (suit != null && !Float.isNaN(c.suitAir) && c.suitAir < AIR_SOON || air != null) {
            boolean suited = suit != null && air == null;
            String s = suited ? Fmt.secs(c.suitAir) : "";
            String e = key(d, "USE", "E");
            Entry x = entry("CAPTAIN AIR", Level.WARNING, null)
                    .text(s.isEmpty() ? "YOUR AIR  NO SUIT" : "YOUR AIR  " + s + " S", e + " AT A LOCKER, OR LEAVE THE ROOM",
                            "YOUR AIR", s.isEmpty() ? "NO SUIT" : s + " S")
                    .window("YOUR AIR", suited ? "SUIT" : "NO SUIT", s.isEmpty() ? "" : "LEFT", s, s.isEmpty() ? "" : "S")
                    .act(e, "AT A LOCKER", "OR LEAVE THE ROOM");
            x.harm = suited ? c.suitAir : Float.NaN;
        }
        boolean bleeding = !Float.isNaN(c.bleedOut);
        boolean critical = c.wound != null && c.wound.toLowerCase(Locale.ROOT).contains("crit");
        if (bleeding || critical) {
            Alert a = d.alerts.get("captain:bleeding");
            boolean cared = a != null && a.handling > 0;
            String s = bleeding && c.bleedOut < 1000f ? Fmt.secs(c.bleedOut) : "";
            String what = bleeding ? "BLEEDING" : "CRITICAL";
            String act = cared ? "A MEDIC IS ON IT" : "FIND A MEDIC";
            Entry x = entry("CAPTAIN HURT", Level.WARNING, Lamp.CREW)
                    .text(bleeding ? "YOU ARE BLEEDING" : "YOU ARE CRITICAL", act, what, act)
                    .window(what, bleeding ? "" : "WOUND", s.isEmpty() ? "" : "OUT IN", s, s.isEmpty() ? "" : "S")
                    .act("", act, "");
            x.harm = bleeding ? c.bleedOut : Float.NaN;
        }
    }

    private void evaBurn(HudData d) {
        if (!d.ctx.crewOutside || d.ctx.docked) return;
        NavReadout n = d.navReadout;
        boolean burning = n.burning || d.ctx.burnFlown || d.flightStatus.mainEngine;
        float due = n.burnsLeft > 0 && !n.burning ? n.burnStartIn : Float.NaN;
        if (!burning && (Float.isNaN(due) || due > BURN_SOON)) return;
        Alert eva = d.alerts.get("eva");
        String who = up(evaWho(eva == null ? "" : eva.text)), i = key(d, "CREW", "I");
        Entry e = entry("EVA BURN", Level.WARNING, Lamp.EVA)
                .text("CREW OUTSIDE  " + who, "HOLD THE BURN; " + i + ": CALL THEM IN", "CREW OUTSIDE", Fmt.cut(who, LINE))
                .window("CREW OUT", who, burning ? "" : "BURN IN", burning ? "" : Fmt.secs(due), burning ? "" : "S")
                .act(i, "CALL THEM IN", "HOLD THE BURN");
        e.harm = burning ? 0f : due;
    }

    /** The first crew member in the spacewalk line ("EVA: Ada on the hull; Bea ..." gives "Ada on the hull"). */
    static String evaWho(String line) {
        String s = line == null ? "" : line.trim();
        if (s.startsWith("EVA:")) s = s.substring(4).trim();
        int cut = s.length();
        for (char c : new char[] {';', '.'}) {
            int at = s.indexOf(c);
            if (at >= 0 && at < cut) cut = at;
        }
        return s.substring(0, cut).trim();
    }

    /** FIRE and VENTING, a room each: who is on it, or nobody. */
    private void rooms(HudData d) {
        String captainRoom = d.captainStatus.present ? d.captainStatus.room : "";
        ShipGridComponent grid = d.ctx.ship.getComponent(ShipGridComponent.class);
        for (Alert a : d.alerts.all()) {
            if (a.code.equals("FIRE")) {
                String size = fireSize(grid, a);
                boolean large = "LARGE".equals(size) || a.count >= LARGE_FIRE;
                boolean here = !captainRoom.isEmpty() && captainRoom.equalsIgnoreCase(a.room);
                String room = up(a.room), who = onIt(a.handling);
                entry("FIRE:" + a.room, large || here ? Level.WARNING : Level.CAUTION, Lamp.FIRE)
                        .text("FIRE  " + room, who, "FIRE " + room, shortWho(a.handling))
                        .window("FIRE", room + (size.isEmpty() ? "" : " (" + size + ")"), "", "", "")
                        .act("", who, here ? "YOU ARE IN IT" : "")
                        .count = large ? 2 : 1;
            } else if (a.code.equals("BREACH")) {
                String room = up(a.room), who = a.handling > 0 ? a.handling + " SEALING" : "NOBODY";
                entry("VENTING:" + a.room, Level.CAUTION, Lamp.AIR)
                        .text("VENTING  " + room, who, "VENTING " + room, a.handling > 0 ? a.handling + " ON IT" : "NOBODY")
                        .window("VENTING", room, "", "", "")
                        .act("", who, "")
                        .count = Math.max(1, a.count);
            }
        }
    }

    /** "1 ON IT", "2 ON IT", "NOBODY ON IT"; "" when the game cannot say. */
    static String onIt(int crew) {
        if (crew < 0) return "";
        return crew == 0 ? "NOBODY ON IT" : crew + " ON IT";
    }

    static String shortWho(int crew) {
        if (crew < 0) return "";
        return crew == 0 ? "NOBODY" : crew + " ON IT";
    }

    /** The fire's size at the alert's tile, "SMALL", "MEDIUM" or "LARGE"; "" when unknown. */
    static String fireSize(ShipGridComponent grid, Alert a) {
        if (grid == null || a.tileX < 0) return "";
        Tile t = grid.getTile(a.tileX, a.tileY);
        if (t == null) return "";
        Tile.FireSize s = t.fireSize();
        return s == Tile.FireSize.NONE ? "" : s.name();
    }

    /** POWER: a caution as the lamp's (a short, a system cut off, a damaged reactor); NO POWER with no reactor feeding anything. */
    private void power(HudData d) {
        PowerFrame p = d.powerFrame;
        SystemRow worst = null;
        int faults = 0;
        for (SystemRow r : p.faults) {
            if (!powerFault(r)) continue;
            faults++;
            if (worst == null) worst = r;
        }
        if (noPower(d)) {
            SystemRow reactor = reactor(p);
            Alert a = reactor == null ? null : d.alerts.get("sys:REACTOR:" + reactor.tileX + "," + reactor.tileY);
            String why = reactor == null ? "NO REACTOR RUNNING" : "REACTOR " + (reactor.fault == Fault.SHORTED ? "SHORTED" : faultWord(reactor.fault));
            String who = a == null ? "" : resetting(a.handling, reactor.fault);
            boolean back = reactor != null && (reactor.fault == Fault.SHORTED || reactor.fault == Fault.STUNNED) && !Float.isNaN(reactor.timer);
            String s = back ? Fmt.secs(reactor.timer) : "";
            Entry e = entry("POWER", Level.WARNING, Lamp.POWER)
                    .text("NO POWER  " + why, who.isEmpty() ? (s.isEmpty() ? "" : "BACK IN " + s + " S") : who,
                            "NO POWER", who.isEmpty() ? (s.isEmpty() ? why : "BACK " + s + " S") : who)
                    .window("NO POWER", why, s.isEmpty() ? "" : "BACK IN", s, s.isEmpty() ? "" : "S")
                    .act("", who.isEmpty() ? "" : who, "");
            e.harm = Float.NaN;
            return;
        }
        if (worst == null) return;
        Alert a = d.alerts.get("sys:" + worst.system + ":" + worst.tileX + "," + worst.tileY);
        String room = up(worst.room), what = up(worst.system) + " " + faultWord(worst.fault);
        String who = a == null ? "" : resetting(a.handling, worst.fault);
        Entry e = entry("POWER", Level.CAUTION, Lamp.POWER)
                .text("POWER  " + (room.isEmpty() ? what : room + " " + what), who,
                        "POWER " + (room.isEmpty() ? up(worst.system) : room), who.isEmpty() ? faultWord(worst.fault) : shortResetting(a.handling))
                .window("POWER", (room.isEmpty() ? "" : room + " ") + what, "", "", "")
                .act("", who, "");
        e.count = faults;
    }

    /** A fault the POWER lamp and alert are for: a short anywhere, a system cut off, a damaged reactor. */
    static boolean powerFault(SystemRow r) {
        if (r.fault == Fault.SHORTED || r.fault == Fault.NO_POWER) return true;
        return "REACTOR".equals(r.system) && (r.fault == Fault.DESTROYED || r.fault == Fault.DISABLED || r.fault == Fault.STUNNED);
    }

    /** No reactor feeds anything: every reactor off, shorted, stunned or broken. */
    static boolean noPower(HudData d) {
        PowerFrame p = d.powerFrame;
        return d.ctx.ship != null && p.circuits.size > 0 && p.outputWatts <= 0;
    }

    private static SystemRow reactor(PowerFrame p) {
        for (SystemRow r : p.faults) if ("REACTOR".equals(r.system)) return r;
        return null;
    }

    static String faultWord(Fault f) {
        switch (f) {
            case DESTROYED: return "DESTROYED";
            case DISABLED: return "DAMAGED";
            case SHORTED: return "SHORT";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            default: return "OFF";
        }
    }

    /** Who is on a system's fault: "1 RESETTING", "2 REPAIRING", "NOBODY"; "" for what clears by itself or nobody can mend. */
    static String resetting(int crew, Fault f) {
        boolean crewWork = f == Fault.SHORTED || f == Fault.DESTROYED || f == Fault.DISABLED;
        if (!crewWork || crew < 0) return "";
        if (crew == 0) return "NOBODY";
        return crew + (f == Fault.SHORTED ? " RESETTING" : " REPAIRING");
    }

    private static String shortResetting(int crew) {
        return crew <= 0 ? "NOBODY" : crew + " ON IT";
    }

    private void propellant(HudData d) {
        FlightStatus f = d.flightStatus;
        if (Double.isNaN(f.deltaVLeft) || d.ctx.docked || driveDown()) return;
        double home = f.deltaVHome, plan = Math.max(d.navReadout.planDeltaV, f.deltaVPlanned);
        boolean low = !Double.isNaN(home) && !Double.isInfinite(home) && f.deltaVLeft < PROP_WARNING * home;
        boolean shortOfPlan = plan > 0 && f.deltaVLeft < plan;
        if (!low && !shortOfPlan) return;
        String left = group(f.deltaVLeft), need = shortOfPlan ? "THE PLAN TAKES " + group(plan) : "HOME TAKES " + group(home);
        entry("PROPELLANT", Level.CAUTION, Lamp.PROP)
                .text("PROPELLANT  " + left + " M/S LEFT", need, "PROP " + left + " M/S",
                        shortOfPlan ? "PLAN " + group(plan) : "HOME " + group(home))
                .window("PROPELLANT", left + " M/S LEFT", "", "", "")
                .act("", need, "");
    }

    private void helm(HudData d) {
        String fault = d.ctx.helmFault;
        boolean waiting = d.orderState.helm != HelmOrder.NONE && d.ctx.helmsman.isEmpty();
        if (fault.isEmpty() && !waiting) return;
        if (!fault.isEmpty()) {
            String f = up(stripThe(fault));
            entry("HELM", Level.CAUTION, null)
                    .text("HELM  " + f, "ORDERS WAIT", "HELM DOWN", Fmt.cut(f, LINE))
                    .window("HELM", f, "", "", "")
                    .act("", "ORDERS WAIT", "");
        } else {
            String order = orderWord(d.orderState.helm);
            entry("HELM", Level.CAUTION, null)
                    .text("HELM ORDER WAITING", order + ": NO HELMSMAN", "HELM WAITING", "NO HELMSMAN")
                    .window("HELM", order + " WAITING", "", "", "")
                    .act("", "NO HELMSMAN", "");
        }
    }

    private static String stripThe(String s) {
        return s.toLowerCase(Locale.ROOT).startsWith("the ") ? s.substring(4) : s;
    }

    static String orderWord(HelmOrder o) {
        switch (o) {
            case FLY_BURNS: return "FLY BURNS";
            case MATCH_VELOCITY: return "MATCH";
            case HOLD_POSITION: return "HOLD";
            case APPROACH: return "APPROACH";
            case DOCK: return "DOCK";
            default: return "";
        }
    }

    private void contracts(HudData d) {
        String m = key(d, "MAP", "M");
        int late = 0;
        Objective first = null;
        for (Objective o : d.objectiveFrame.objectives) {
            if (o.state == State.DONE || o.state == State.FAILED) continue;
            boolean soon = !Double.isNaN(o.timeLeft) && o.timeLeft < CONTRACT_SOON && d.navReadout.burnsLeft == 0 && !o.nextIsNav;
            if (!o.late && !soon) continue;
            if (late++ == 0) first = o;
        }
        if (first == null) return;
        String where = up(first.station.isEmpty() ? first.title : first.station);
        String left = Double.isNaN(first.timeLeft) ? "" : " BY " + Fmt.mmss(first.timeLeft);
        Entry e = entry("CONTRACT", Level.CAUTION, null)
                .text("LATE  " + up(first.kind.isEmpty() ? "CONTRACT" : first.kind) + " TO " + where + left, m,
                        "LATE " + Fmt.name(where, 14), m)
                .window("LATE", where, "", "", "")
                .act(m, "PLAN", "");
        e.count = late;
    }

    private void shield(HudData d, Posts p) {
        ShieldStatus s = d.shieldStatus;
        if (!s.present || s.emitters <= 0) return;
        Entry e = null;
        if (s.emittersTripped > 0) {
            e = entry("SHIELD", Level.CAUTION, Lamp.SHIELD)
                    .text("SHIELD TRIPPED", s.emittersTripped > 1 ? s.emittersTripped + " EMITTERS" : "", "SHIELD TRIPPED", "")
                    .window("SHIELD", "TRIPPED", "", "", "");
            e.count = s.emittersTripped;
        } else if (!s.raised && !s.loweredForDock && p.combat) {
            String k = key(d, "SHIELDS", "9");
            e = entry("SHIELD", Level.CAUTION, Lamp.SHIELD)
                    .text("SHIELD DOWN", k + " RAISES IT", "SHIELD DOWN", k)
                    .window("SHIELD", "DOWN", "", "", "")
                    .act(k, "RAISE", "");
        } else if (s.raised && s.capacity > 0f && s.share() < CLOUD_QUARTER) {
            String pct = Math.round(s.share() * 100f) + "%";
            e = entry("SHIELD", Level.CAUTION, Lamp.SHIELD)
                    .text("SHIELD LOW  " + pct, "", "SHIELD LOW", pct)
                    .window("SHIELD", "LOW " + pct, "", "", "");
        }
        // At gunnery WPN's shield row shows all of it (9.1)
        if (e != null) e.shown = p.post == Posts.Post.GUNNERY;
    }

    private void collar(HudData d, Posts p) {
        DockingApproach k = d.docking;
        if (!k.present || !k.collarJammed) return;
        String port = "PORT " + Math.max(1, k.port) + " JAMMED";
        String who = p.combat ? "NOBODY  " + key(d, "CREW", "I") + ": SEND SOMEONE OUT" : "CREW FREE IT ON EVA";
        if (k.ports > 1) who = key(d, "NEXT_PORT", "H") + " NEXT PORT";
        entry("COLLAR", Level.CAUTION, null)
                .text(port, who, port, k.ports > 1 ? key(d, "NEXT_PORT", "H") : p.combat ? "NOBODY" : "CREW ON EVA")
                .window("COLLAR", port, "", "", "")
                .act("", who, "");
    }

    private void wounded(HudData d) {
        String first = "";
        int n = 0;
        for (Alert a : d.alerts.all()) {
            if (!a.code.equals("CASUALTY") || a.handling > 0) continue;
            if (n++ == 0) first = a.id.startsWith("casualty:") ? a.id.substring(9) : "CREW";
        }
        if (n == 0) return;
        String who = up(first) + (n > 1 ? " +" + (n - 1) : "");
        Entry e = entry("WOUNDED", Level.CAUTION, Lamp.CREW)
                .text("WOUNDED  " + who, "NO MEDIC FREE", "WOUNDED " + Fmt.cut(who, 11), "NO MEDIC")
                .window("WOUNDED", who, "", "", "")
                .act("", "NO MEDIC FREE", "");
        e.count = n;
    }

    // ------------------------------------------------------------------ the lamps (ANN)

    private void lamps(HudData d, Posts p, float now) {
        Lit[] l = lamps;
        for (int i = 0; i < l.length; i++) l[i] = Lit.DARK;
        ShieldStatus sh = d.shieldStatus;
        shieldLamp = sh.present && sh.emitters > 0;
        if (d.ctx.ship != null) {
            String captainRoom = d.captainStatus.present ? d.captainStatus.room : "";
            boolean thinRoom = false, tanksDry = false;
            for (Alert a : d.alerts.all()) {
                switch (a.code) {
                    case "HULL": lift(Lamp.HULL, a.level == Level.WARNING ? Lit.WARNING : Lit.CAUTION); break;
                    case "PRESSURE": thinRoom = true; lift(Lamp.AIR, Lit.CAUTION); break;
                    case "SMOKE":
                    case "BREACH": lift(Lamp.AIR, Lit.CAUTION); break;
                    case "VACUUM":
                        thinRoom = true;
                        // A room crew are in: the captain's is the one the HUD can tell
                        lift(Lamp.AIR, captainRoom.equalsIgnoreCase(a.room) ? Lit.WARNING : Lit.CAUTION);
                        break;
                    case "O2":
                        if (a.level == Level.WARNING) tanksDry = true;
                        lift(Lamp.AIR, Lit.CAUTION);
                        break;
                    case "CASUALTY": lift(Lamp.CREW, a.handling > 0 ? Lit.CAUTION : Lit.WARNING); break;
                    case "EVA": lift(Lamp.EVA, Lit.ADVISORY); break;
                    default:
                        if (a.id.startsWith("sys:") && a.level == Level.ADVISORY) lift(Lamp.POWER, Lit.ADVISORY);
                }
            }
            if (tanksDry && thinRoom) lift(Lamp.AIR, Lit.WARNING);
            if (d.ctx.crewOutside) lift(Lamp.EVA, Lit.ADVISORY);
            for (SystemRow r : d.powerFrame.faults) {
                if (powerFault(r)) lift(Lamp.POWER, Lit.CAUTION);
                if ("ENGINE".equals(r.system) && r.fault != Fault.OFF && r.fault != Fault.NO_POWER) lift(Lamp.DRIVE, Lit.CAUTION);
            }
            if (noPower(d)) lift(Lamp.POWER, Lit.WARNING);
            if (engines > 0 && enginesWorking == 0) lift(Lamp.DRIVE, Lit.WARNING);
            prop(d);
            arms(d, p);
            if (shieldLamp) shieldLamp(sh, p);
        }
        // The list's own lamps: FIRE, CREW for the captain and the pods, EVA for a burn, the cause of a disablement
        for (Entry e : entries) {
            if (e.lamp == null) continue;
            if (e.id.startsWith("FIRE:")) lift(Lamp.FIRE, e.level == Level.WARNING ? Lit.WARNING : Lit.CAUTION);
            else if (e.level == Level.WARNING) lift(e.lamp, Lit.WARNING);
            else lift(e.lamp, Lit.CAUTION);
        }
        if (!shieldLamp) l[Lamp.SHIELD.ordinal()] = Lit.DARK;
        // A lamp blinks for a while when it lights or worsens
        for (int i = 0; i < l.length; i++) {
            if (l[i].ordinal() > lastLamp[i].ordinal() && l[i].ordinal() >= Lit.CAUTION.ordinal()) lampSince[i] = now;
            lastLamp[i] = l[i];
            blink[i] = l[i].ordinal() >= Lit.CAUTION.ordinal() && now - lampSince[i] < LAMP_BLINK;
        }
    }

    /**
     * No main engine works: the game's delta-v left is what working engines can draw, so it reads 0
     * with full tanks. That is DRIVE's warning, not a propellant caution.
     */
    private boolean driveDown() {
        return engines > 0 && enginesWorking == 0;
    }

    private void lift(Lamp lamp, Lit lit) {
        if (lit.ordinal() > lamps[lamp.ordinal()].ordinal()) lamps[lamp.ordinal()] = lit;
    }

    private void prop(HudData d) {
        FlightStatus f = d.flightStatus;
        if (Double.isNaN(f.deltaVLeft) || driveDown()) return;
        double home = f.deltaVHome, plan = Math.max(d.navReadout.planDeltaV, f.deltaVPlanned);
        boolean known = !Double.isNaN(home) && !Double.isInfinite(home);
        if (known && f.deltaVLeft < PROP_WARNING * home || plan > 0 && f.deltaVLeft < plan) lift(Lamp.PROP, Lit.WARNING);
        else if (known && f.deltaVLeft < PROP_CAUTION * home) lift(Lamp.PROP, Lit.CAUTION);
    }

    private void arms(HudData d, Posts p) {
        int working = 0, mounts = 0;
        for (Mount m : d.weaponFrame.mounts) {
            mounts++;
            if (m.working()) working++;
            else if (m.state != com.ringsofiron.uihud.providers.WeaponStatusProvider.State.EMPTY) lift(Lamp.ARMS, Lit.CAUTION);
        }
        if (mounts > 0 && working == 0 && p.combat) lift(Lamp.ARMS, Lit.WARNING);
    }

    private void shieldLamp(ShieldStatus s, Posts p) {
        if (s.emittersTripped > 0 || s.raised && s.capacity > 0f && s.share() < CLOUD_QUARTER && p.combat) {
            lift(Lamp.SHIELD, Lit.WARNING);
        } else if (!s.raised && p.combat || s.raised && s.capacity > 0f && s.share() < CLOUD_HALF) {
            lift(Lamp.SHIELD, Lit.CAUTION);
        } else if (!s.raised) {
            lift(Lamp.SHIELD, Lit.ADVISORY);
        }
    }

    /** Whether SCH has something to show: any of HULL, FIRE, AIR, POWER, CREW or EVA lit (6, SCH). */
    boolean schematicLit() {
        for (Lamp l : new Lamp[] {Lamp.HULL, Lamp.FIRE, Lamp.AIR, Lamp.POWER, Lamp.CREW, Lamp.EVA}) {
            if (lamps[l.ordinal()].ordinal() >= Lit.ADVISORY.ordinal()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ words

    /** The key bound to {@code action}, in capitals, or {@code fallback} when the game does not say. */
    static String key(HudData d, String action, String fallback) {
        String k = d.key(action);
        return (k.isEmpty() ? fallback : k).toUpperCase(Locale.ROOT);
    }

    static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** Thousands grouped: "1,240". */
    static String group(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }
}
