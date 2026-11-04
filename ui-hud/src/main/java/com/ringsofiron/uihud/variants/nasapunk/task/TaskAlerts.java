package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The task spec's alert rules (sections 6, ANN, and 9) over the shared {@link AlertRegistry}: which
 * lamp each alert lights and at what level, and what the band shows. The band holds one warning,
 * the one with the least time to harm; the rest of the warnings and the cautions share the second
 * row, worst first, then oldest. A caution leaves the band after 10 s, or at once when acknowledged,
 * and lives on as its lamp.
 */
final class TaskAlerts {
    /** How long a caution stays in the band, s (section 9.3). */
    static final double CAUTION_SECONDS = 10.0;
    /** Burning tiles that make a fire large (a warning, section 6 ANN). */
    static final int LARGE_FIRE = 6;
    /** A missile closer than this to impact is a warning, s (section 9.2). */
    static final float MISSILE_WARNING = 20f;

    /** One alert as the band shows it. */
    static final class Entry {
        final Alert alert;
        Level level;
        String tag = "", big = "", line2, code = "", where = "", who = "";
        final List<String[]> actions = new ArrayList<>();
        /** The game actions whose keys it shows (Action names). */
        final List<String> named = new ArrayList<>();
        /** Seconds to harm, for ordering warnings; NaN when it has none. */
        float harm = Float.NaN;

        Entry(Alert alert) {
            this.alert = alert;
            this.level = alert.level;
        }
    }

    private TaskAlerts() {}

    /** Lights {@code ann}'s lamps and fills {@code band} from {@code d}'s alerts at post {@code p}. */
    static void apply(HudData d, Presence p, Annunciator ann, AlertBand band, double now) {
        ann.darkAll();
        ann.shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        band.warning = null;
        band.cautions.clear();
        band.more = null;
        List<Entry> warnings = new ArrayList<>(), cautions = new ArrayList<>();
        for (Alert a : d.alerts.all()) {
            Entry e = entry(d, p, a, ann);
            if (e == null) continue;
            if (e.level == Level.WARNING) warnings.add(e);
            else if (e.level == Level.CAUTION && !a.acknowledged && now - a.since < CAUTION_SECONDS) cautions.add(e);
        }
        // the warning with the least time to harm; the rest go down a row in their short form
        warnings.sort((x, y) -> {
            boolean xt = !Float.isNaN(x.harm), yt = !Float.isNaN(y.harm);
            if (xt && yt) return Float.compare(x.harm, y.harm);
            if (xt != yt) return xt ? -1 : 1;
            return Double.compare(x.alert.since, y.alert.since);
        });
        if (!warnings.isEmpty()) {
            Entry w = warnings.get(0);
            AlertBand.Warning bw = new AlertBand.Warning();
            bw.tag = w.tag;
            bw.big = w.big;
            bw.line2 = w.line2;
            bw.actions.addAll(w.actions);
            bw.named.addAll(w.named);
            band.warning = bw;
            band.warningAge = (float) (now - w.alert.since);
            List<Entry> down = new ArrayList<>(warnings.subList(1, warnings.size()));
            down.addAll(cautions);
            cautions = down;
        }
        for (int i = 0; i < cautions.size() && i < 3; i++) {
            Entry c = cautions.get(i);
            band.cautions.add(new AlertBand.Caution(c.code, c.where, c.who));
        }
        if (cautions.size() > 3) band.more = "+" + (cautions.size() - 3);
    }

    /** Puts {@code key} on a cap in {@code e} with its verb, for game action {@code action}. */
    private static void action(Entry e, String key, String verb, String action) {
        e.actions.add(new String[] {upper(key), verb});
        e.named.add(action);
    }

    /** Who is on it, from the registry's count of crew working it: a name is not known here. */
    private static String who(Alert a) {
        if (a.handling < 0) return "";
        return a.handling == 0 ? "NOBODY" : a.handling == 1 ? "CREW ON IT" : a.handling + " ON IT";
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** The band's entry for {@code a}, lighting its lamp; null when it takes no place in the band. */
    static Entry entry(HudData d, Presence p, Alert a, Annunciator ann) {
        String id = a.id;
        Entry e = new Entry(a);
        boolean gunnery = p == Presence.WEAPONS || p == Presence.GUN;
        boolean seated = gunnery || p == Presence.HELM;
        if (id.equals("missile")) {
            e.level = !Float.isNaN(a.timer) && a.timer < MISSILE_WARNING ? Level.WARNING : Level.CAUTION;
            e.harm = a.timer;
            e.tag = "MISSILE\n" + a.count + " IN";
            e.big = Float.isNaN(a.timer) ? "INBOUND" : "IMPACT " + NpFormat.clock(a.timer);
            if (seated) {
                if (d.weaponFrame.pointDefenseActive) e.line2 = "POINT DEFENSE ENGAGING";
                String k = d.key("DECOY");
                if (!k.isEmpty()) action(e, k, "DECOY", "DECOY");
            } else {
                String k = d.key("VIEW");
                e.line2 = "POINT DEFENSE ENGAGING" + (k.isEmpty() ? "" : "; " + upper(k) + " AT A CONSOLE TO FIGHT");
                if (!k.isEmpty()) e.named.add("VIEW");
            }
            e.code = "MISSILE";
            e.where = Integer.toString(a.count);
            e.who = Float.isNaN(a.timer) ? "" : NpFormat.clock(a.timer);
            return e;
        }
        if (id.startsWith("fire:")) {
            boolean large = a.count >= LARGE_FIRE || (!a.room.isEmpty() && a.room.equals(d.captainStatus.room));
            e.level = large ? Level.WARNING : Level.CAUTION;
            ann.set(Annunciator.Lamp.FIRE, large ? Annunciator.State.WARNING : Annunciator.State.CAUTION);
            e.tag = "FIRE";
            e.big = "FIRE " + a.room;
            e.line2 = a.handling == 0 ? "NOBODY ON IT" : a.handling > 0 ? who(a) : null;
            e.code = "FIRE";
            e.where = a.room;
            e.who = who(a);
            return e;
        }
        if (id.startsWith("breach:")) {
            e.level = Level.CAUTION;
            ann.set(Annunciator.Lamp.AIR, Annunciator.State.CAUTION);
            e.code = "VENTING";
            e.where = a.room;
            e.who = who(a);
            return e;
        }
        if (id.startsWith("air:") || id.startsWith("smoke:") || id.equals("oxygen")) {
            ann.set(Annunciator.Lamp.AIR, lamp(a.level));
            return null;
        }
        if (id.equals("hull")) {
            ann.set(Annunciator.Lamp.HULL, lamp(a.level));
            return null;
        }
        if (id.startsWith("sys:")) {
            String sys = a.code;
            Annunciator.Lamp lamp = "ENGINE".equals(sys) ? Annunciator.Lamp.DRIVE
                    : "TURRET".equals(sys) || "WEAPONS".equals(sys) ? Annunciator.Lamp.ARMS
                    : "SHIELDS".equals(sys) ? Annunciator.Lamp.SHIELD : Annunciator.Lamp.POWER;
            ann.set(lamp, lamp(a.level));
            // at gunnery a gun's fault is the weapons display's row in full, so it takes no slot
            if (lamp == Annunciator.Lamp.ARMS && gunnery) return null;
            if (a.level == Level.ADVISORY) return null;
            boolean reactor = "REACTOR".equals(sys);
            e.tag = reactor ? "NO\nPOWER" : sys;
            e.big = reactor ? "NO POWER" : sys + " " + a.room;
            e.line2 = upper(a.text);
            e.code = reactor ? "NO POWER" : lamp == Annunciator.Lamp.POWER ? "POWER" : sys;
            e.where = reactor ? "REACTOR" : a.room.isEmpty() ? sys : a.room;
            e.who = !Float.isNaN(a.timer) && a.timerKind == AlertRegistry.TimerKind.RECOVERY ? "BACK IN " + NpFormat.clock(a.timer) : who(a);
            return e;
        }
        if (id.equals("propellant")) {
            ann.set(Annunciator.Lamp.PROP, lamp(a.level));
            e.tag = "PROP\nLOW";
            e.big = "PROPELLANT LOW";
            e.line2 = upper(a.text);
            e.code = "PROP";
            e.where = "LOW";
            return e;
        }
        if (id.equals("disabled")) {
            e.tag = "SHIP\nDISABLED";
            e.big = "SHIP DISABLED";
            e.line2 = upper(a.text.replace("Ship disabled: ", ""));
            e.code = "DISABLED";
            return e;
        }
        if (id.equals("abandon")) {
            ann.set(Annunciator.Lamp.CREW, Annunciator.State.WARNING);
            e.tag = "ABANDON\nSHIP";
            e.big = "ABANDON SHIP";
            e.line2 = "CREW TO THE PODS";
            String k = d.key("ABANDON");
            if (!k.isEmpty()) action(e, k, "LAUNCH NOW", "ABANDON");
            e.code = "ABANDON";
            return e;
        }
        if (id.equals("eva")) {
            ann.set(Annunciator.Lamp.EVA, lamp(a.level));
            return a.level == Level.ADVISORY ? null : caution(e, "EVA", upper(a.text), "");
        }
        if (id.equals("helm")) return caution(e, "HELM", upper(d.ctx.helmFault), "");
        if (id.equals("hostile")) return null;
        if (id.startsWith("empty:")) {
            ann.set(Annunciator.Lamp.ARMS, Annunciator.State.ADVISORY);
            return null;
        }
        if (id.equals("captain:bleeding")) {
            ann.set(Annunciator.Lamp.CREW, Annunciator.State.WARNING);
            e.harm = a.timer;
            e.tag = "YOU ARE\nHURT";
            e.big = "YOU ARE BLEEDING";
            e.line2 = "FIND A MEDIC";
            e.code = "BLEEDING";
            e.where = "YOU";
            e.who = "FIND A MEDIC";
            return e;
        }
        if (id.equals("captain:suit") || id.equals("captain:air")) {
            e.harm = a.timer;
            e.tag = "YOUR\nAIR";
            e.big = Float.isNaN(a.timer) ? "YOUR AIR" : "YOUR AIR " + NpFormat.clock(a.timer);
            e.line2 = id.equals("captain:air") ? "NO SUIT, AND THE AIR IS THIN" : "FIND A LOCKER, OR LEAVE THE ROOM";
            e.code = "YOUR AIR";
            e.who = Float.isNaN(a.timer) ? "" : NpFormat.clock(a.timer);
            return e;
        }
        if (id.startsWith("casualty:")) {
            ann.set(Annunciator.Lamp.CREW, Annunciator.State.WARNING);
            String name = upper(id.substring("casualty:".length()));
            e.harm = a.timer;
            e.tag = "CREW\nHURT";
            e.big = name + " BLEEDING OUT";
            e.line2 = "NEEDS A MEDIC";
            e.code = "WOUNDED";
            e.where = name;
            e.who = "NO MEDIC";
            return e;
        }
        if (id.startsWith("shield:")) {
            ann.set(Annunciator.Lamp.SHIELD, lamp(a.level));
            return caution(e, "SHIELD", id.endsWith("tripped") ? "TRIPPED" : "DOWN", "");
        }
        // anything else the game raises: its code and text, at its own level
        e.tag = a.code;
        e.big = a.code;
        e.line2 = upper(a.text);
        e.code = a.code;
        e.where = a.room;
        return a.level == Level.ADVISORY ? null : e;
    }

    private static Entry caution(Entry e, String code, String where, String who) {
        e.code = code;
        e.where = where;
        e.who = who;
        e.tag = code;
        e.big = code + " " + where;
        return e;
    }

    private static Annunciator.State lamp(Level l) {
        switch (l) {
            case WARNING: return Annunciator.State.WARNING;
            case CAUTION: return Annunciator.State.CAUTION;
            default: return Annunciator.State.ADVISORY;
        }
    }
}
