package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Where each alert goes in the task spec (section 9): which of the ten annunciator lamps it lights and
 * how, and what the alert band shows. The game's {@link AlertRegistry} says what is wrong; this decides
 * the warning row (one, the soonest harm first), the caution row (three slots and +n), how long a
 * caution stays in the band (10 s, then it lives on as its lamp), and the band's words for each alert.
 * Pure logic, so it is tested without a screen.
 */
final class TaskAlerts {

    /** The annunciator's ten positions, in their grid order (two rows of five). */
    enum Lamp {
        HULL, FIRE, AIR, POWER, PROP, DRIVE, ARMS, CREW, EVA, SHIELD
    }

    /** Seconds a caution stays in the band before it leaves for its lamp. */
    static final float CAUTION_SECONDS = 10f;
    /** Seconds something new blinks (a lamp) or pulses (the warning row). */
    static final float NEW_SECONDS = 3f;

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

    private final ObjectFloatMap<String> seen = new ObjectFloatMap<>();
    private final ObjectSet<String> present = new ObjectSet<>();
    private final Array<String> gone = new Array<>();
    private float clock;

    /** Reads this frame's alerts. {@code gunnery}: the weapons panel shows every mount in full, so a mount's fault takes no caution slot. */
    void update(HudData d, float delta, boolean gunnery) {
        clock += delta;
        AlertRegistry reg = d.alerts;
        Array<Alert> all = reg.all();
        // When each alert was first seen, in real seconds, so warp doesn't hurry a blink
        present.clear();
        for (Alert a : all) {
            present.add(a.id);
            if (!seen.containsKey(a.id)) seen.put(a.id, clock);
        }
        gone.clear();
        for (ObjectFloatMap.Entry<String> e : seen.entries()) if (!present.contains(e.key)) gone.add(e.key);
        for (String id : gone) seen.remove(id, 0f);

        for (int i = 0; i < level.length; i++) {
            level[i] = null;
            fresh[i] = false;
            if (whyText[i] == null) whyText[i] = new StringBuilder();
            whyText[i].setLength(0);
        }
        for (Alert a : all) {
            Lamp l = lampOf(a, d);
            if (l == null) continue;
            light(l, a.level, age(a) < NEW_SECONDS && !a.acknowledged, a.text);
        }
        arms(d);
        shieldFitted = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        if (shieldFitted && !d.shieldStatus.raised && !d.ctx.combat && !d.shieldStatus.loweredForDock) {
            light(Lamp.SHIELD, Level.ADVISORY, false, "Shields lowered");
        }
        if (d.ctx.crewOutside && (d.ctx.burnFlown || d.flightStatus.mainEngine)) {
            light(Lamp.EVA, Level.WARNING, false, "Crew outside with a burn under way");
        }
        for (int i = 0; i < level.length; i++) why[i] = whyText[i].length() == 0 ? null : whyText[i].toString();
        if (!shieldFitted) level[Lamp.SHIELD.ordinal()] = null;

        band(all, gunnery, d);
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

    /** ARMS from the mounts themselves: caution while any is not working, a warning when none works in combat. */
    private void arms(HudData d) {
        int broken = 0, working = 0;
        for (WeaponStatusProvider.Mount m : d.weaponFrame.mounts) {
            if (m.working() || m.state == WeaponStatusProvider.State.EMPTY) working++;
            else broken++;
        }
        if (broken == 0) return;
        Level lv = working == 0 && d.ctx.combat ? Level.WARNING : Level.CAUTION;
        light(Lamp.ARMS, lv, false, broken == 1 ? "A mount is not working" : broken + " mounts not working");
    }

    /** The lamp an alert lights, or null for one that lights none (it is the band's alone). */
    static Lamp lampOf(Alert a, HudData d) {
        String id = a.id;
        if (id.startsWith("fire:")) return Lamp.FIRE;
        if (id.startsWith("air:") || id.startsWith("smoke:") || id.startsWith("breach:") || id.equals("oxygen")) return Lamp.AIR;
        if (id.equals("hull")) return Lamp.HULL;
        if (id.equals("propellant")) return Lamp.PROP;
        if (id.startsWith("casualty:") || id.equals("captain:bleeding") || id.equals("abandon")) return Lamp.CREW;
        if (id.equals("eva")) return Lamp.EVA;
        if (id.startsWith("shield:")) return Lamp.SHIELD;
        if (id.startsWith("empty:")) return Lamp.ARMS;
        if (id.equals("disabled")) {
            String r = d.ctx.disabledReason.toLowerCase(Locale.ROOT);
            if (r.contains("hull")) return Lamp.HULL;
            if (r.contains("crew")) return Lamp.CREW;
            return Lamp.POWER;
        }
        if (id.startsWith("sys:")) {
            String sys = id.substring(4, id.indexOf(':', 4) < 0 ? id.length() : id.indexOf(':', 4));
            switch (sys) {
                case "ENGINE": return Lamp.DRIVE;
                case "TURRET": case "WEAPONS": return Lamp.ARMS;
                case "SHIELDS": return Lamp.SHIELD;
                case "LIFE SUP": return Lamp.AIR;
                default: return Lamp.POWER;
            }
        }
        return null;
    }

    private float age(Alert a) {
        return clock - seen.get(a.id, clock);
    }

    /** The band: one warning on the top row, the rest of the warnings and the fresh cautions below. */
    private void band(Array<Alert> all, boolean gunnery, HudData d) {
        warning = null;
        cautions.clear();
        // The warning row: the soonest harm first, then the oldest
        for (Alert a : all) {
            if (a.level != Level.WARNING) continue;
            if (warning == null || sooner(a, warning)) warning = a;
        }
        for (Alert a : all) {
            if (a.level == Level.WARNING && a != warning) cautions.add(a);
        }
        // Warnings pushed down go first, the soonest first
        cautions.sort((a, b) -> sooner(a, b) ? -1 : sooner(b, a) ? 1 : Double.compare(a.since, b.since));
        for (Alert a : all) {
            if (a.level != Level.CAUTION || a.acknowledged) continue;
            if (age(a) >= CAUTION_SECONDS) continue;
            // No caution for what a visible widget shows in full: at gunnery a mount's fault is WPN's row
            if (gunnery && lampOf(a, d) == Lamp.ARMS) continue;
            cautions.add(a);
        }
        masterWarning = warning != null && !warning.acknowledged;
        warningNew = warning != null && !warning.acknowledged && age(warning) < NEW_SECONDS;
        masterCaution = false;
        for (Alert a : cautions) if (!a.acknowledged) masterCaution = true;
    }

    /** {@code a} harms sooner than {@code b}: a countdown to harm beats none, a shorter one a longer. */
    private static boolean sooner(Alert a, Alert b) {
        boolean ta = a.timerKind == TimerKind.HARM && !Float.isNaN(a.timer), tb = b.timerKind == TimerKind.HARM && !Float.isNaN(b.timer);
        if (ta && tb) return a.timer < b.timer;
        if (ta != tb) return ta;
        return a.since < b.since;
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
        if (id.equals("captain:bleeding")) return Float.isNaN(a.timer) ? "YOU ARE BLEEDING" : "YOU ARE BLEEDING   " + YardText.span(a.timer);
        if (id.equals("captain:suit")) return Float.isNaN(a.timer) ? "YOUR AIR" : "YOUR AIR   " + YardText.span(a.timer);
        if (id.equals("captain:air")) return "NO SUIT   THIN AIR";
        if (id.startsWith("casualty:")) return "CASUALTY   " + id.substring(9).toUpperCase(Locale.ROOT);
        if (id.startsWith("fire:")) return "FIRE   " + a.room;
        if (id.startsWith("breach:")) return "HULL BREACH   " + a.room;
        if (id.startsWith("air:")) return a.room + " IN VACUUM";
        if (id.equals("hull")) return "HULL   " + percent(a.text);
        if (id.equals("oxygen")) return "OXYGEN TANKS DRY";
        if (id.equals("propellant")) return "PROPELLANT LOW";
        if (id.startsWith("sys:")) return a.code + "   " + after(a.text).toUpperCase(Locale.ROOT);
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
        if (id.equals("captain:bleeding")) return new String[]{"FIND A MEDIC"};
        if (id.equals("captain:suit") || id.equals("captain:air")) return new String[]{key(d, "USE"), "AT A LOCKER, OR LEAVE THE ROOM"};
        if (id.startsWith("casualty:")) return new String[]{"BLEEDING OUT" + (Float.isNaN(a.timer) ? "" : " IN " + YardText.span(a.timer)) + "   ", key(d, "CREW"), "CREW"};
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
    static String[] lines(Alert a, HudData d) {
        String id = a.id, code = a.code, l1 = "", l2 = "";
        if (id.equals("missile")) {
            l1 = String.valueOf(a.count);
            l2 = Float.isNaN(a.timer) ? "INBOUND" : YardText.span(a.timer);
        } else if (id.startsWith("fire:") || id.startsWith("smoke:")) {
            l1 = a.room;
            l2 = who(a);
        } else if (id.startsWith("breach:")) {
            code = "VENTING";
            l1 = a.room;
            l2 = who(a);
        } else if (id.startsWith("air:")) {
            l1 = a.room;
            l2 = percent(a.text);
        } else if (id.equals("hull") || id.equals("oxygen")) {
            l1 = percent(a.text);
        } else if (id.equals("propellant")) {
            code = "PROP";
            l1 = "LOW";
            l2 = after(a.text);
        } else if (id.startsWith("sys:")) {
            l1 = a.room;
            l2 = a.timerKind == TimerKind.RECOVERY && !Float.isNaN(a.timer) ? "BACK IN " + YardText.span(a.timer) : after(a.text);
        } else if (id.equals("captain:suit")) {
            l2 = Float.isNaN(a.timer) ? percent(a.text) : YardText.span(a.timer);
        } else if (id.equals("captain:air")) {
            // Below a warning that outranks it: what the air is and where, not the title over again
            l1 = "THIN AIR";
            l2 = a.room;
        } else if (id.equals("captain:bleeding")) {
            l2 = Float.isNaN(a.timer) ? "FIND A MEDIC" : "OUT IN " + YardText.span(a.timer);
        } else if (id.equals("hostile")) {
            // "Pirate raider 6 has you locked": LOCKED beside the code, the ship's name under it
            int k = a.text.indexOf(" has you locked");
            if (k > 0) {
                l1 = "LOCKED";
                l2 = a.text.substring(0, k);
            } else {
                l2 = after(a.text);
            }
        } else if (id.startsWith("casualty:")) {
            l1 = id.substring(9);
            l2 = "BLEEDING";
        } else if (id.equals("helm")) {
            l2 = after(a.text);
        } else if (id.startsWith("shield:")) {
            l1 = id.endsWith("tripped") ? "TRIPPED" : "DOWN";
        } else {
            l2 = after(a.text);
        }
        int room1 = Math.max(0, 19 - code.length() - 1);
        return new String[]{code, YardText.fit(l1, room1), YardText.fit(l2, 19)};
    }

    private static String who(Alert a) {
        if (a.handling > 0) return a.handling == 1 ? "1 CREW ON IT" : a.handling + " CREW ON IT";
        if (a.handling == 0) return "NOBODY ON IT";
        return "";
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
