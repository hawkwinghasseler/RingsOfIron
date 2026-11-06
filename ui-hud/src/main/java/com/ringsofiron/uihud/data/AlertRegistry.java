package com.ringsofiron.uihud.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Everything wrong right now, as structured rows rather than lines of text: what the annunciator,
 * the alert band, the caution panel and the callout ticker of the HUD variants read. The game raises
 * every alert that holds once a frame, between {@link #begin} and {@link #end}; an alert not raised
 * in a frame has cleared. An alert keeps its {@link Alert#since} and whether it has been
 * acknowledged for as long as it holds, so a held alert does not flash again each frame.
 *
 * <p>Ids name the condition and the instance: {@code "fire:BRIDGE"}, {@code "missile"},
 * {@code "short:reactor"}. Two raises of one id in a frame are one alert, with the worse level.
 */
public final class AlertRegistry {

    /** How much an alert matters. */
    public enum Level {
        /** Worth knowing; no action called for. */
        ADVISORY,
        /** Needs attention soon. */
        CAUTION,
        /** Needs action now. */
        WARNING
    }

    /** Which job an alert belongs to, so a station design can route it to the right seat. */
    public enum Category {
        /** Flying: propellant, collision, the helm. */
        FLIGHT,
        /** Getting somewhere: navigation, warp. */
        NAV,
        /** Fighting: missiles, hostiles, weapons, shields. */
        TACTICAL,
        /** Power, systems, damage. */
        ENGINEERING,
        /** Air, pressure, fire, breaches. */
        LIFE_SUPPORT,
        /** The crew: wounds, crew outside, boarders. */
        CREW,
        /** The captain's own body and suit. */
        CAPTAIN,
        /** Contracts, docking, the station. */
        COMMS,
        /** The whole ship: disabled, abandon ship. */
        SHIP
    }

    /** What an alert's {@link Alert#timer} counts down to. */
    public enum TimerKind {
        NONE,
        /** Something bad happens at zero (impact, bleed-out, air gone). */
        HARM,
        /** Something comes back at zero (a breaker recloses, a stun wears off). */
        RECOVERY
    }

    public static final class Alert {
        public final String id;
        public Level level = Level.ADVISORY;
        public Category category = Category.SHIP;
        /** Short label in capitals, at most about 12 characters: "FIRE", "MISSILE", "REACTOR". */
        public String code = "";
        /** One line for the player: "Fire in HOLD, two crew on it". */
        public String text = "";
        /** The room it is in ({@code RoomNames}), or "". */
        public String room = "";
        /** A tile on the player's ship it is at, or -1. */
        public int tileX = -1, tileY = -1;
        /** Seconds on its countdown, NaN with none, and what the countdown is to. */
        public float timer = Float.NaN;
        public TimerKind timerKind = TimerKind.NONE;
        /** How many of it there are (missiles inbound, tiles burning); 1 for one. */
        public int count = 1;
        /** Crew working on it, -1 when the game cannot say. */
        public int handling = -1;
        /**
         * The one of them to name ("Ada"): one at the work before one on the way to it, then the
         * soonest done; "" while nobody is on it, or when the game cannot say who.
         */
        public String who = "";
        /** {@link #who} is at the work now, rather than on the way to it. */
        public boolean atWork;
        /**
         * Seconds of work {@link #who} has left at their pace: what is left once at it, the whole
         * job while on the way (the walk there is not known); NaN when the game cannot say.
         */
        public float workLeft = Float.NaN;
        /** Sim seconds when it was first raised in this run of it. */
        public double since;
        /** The player has acknowledged it (the master caution's key). A worse level clears this. */
        public boolean acknowledged;
        /** Raised for the first time this frame. */
        public boolean fresh;
        boolean raised;

        Alert(String id) {
            this.id = id;
        }

        /** Sets the fields most alerts use, returning this. */
        public Alert set(String code, String text) {
            this.code = code;
            this.text = text;
            return this;
        }

        public Alert room(String room) {
            this.room = room == null ? "" : room;
            return this;
        }

        public Alert tile(int x, int y) {
            tileX = x;
            tileY = y;
            return this;
        }

        public Alert timer(float seconds, TimerKind kind) {
            timer = seconds;
            timerKind = kind;
            return this;
        }

        public Alert count(int n) {
            count = n;
            return this;
        }

        /** How many crew are on it; who they are is cleared until {@link #crew} names one. */
        public Alert handling(int crew) {
            handling = crew;
            who = "";
            atWork = false;
            workLeft = Float.NaN;
            return this;
        }

        /** Names the one of the crew on it to show, whether they are at the work, and its seconds left (NaN: unknown). */
        public Alert crew(String name, boolean atIt, float secondsLeft) {
            who = name == null ? "" : name;
            atWork = atIt;
            workLeft = secondsLeft;
            return this;
        }
    }

    private final ObjectMap<String, Alert> byId = new ObjectMap<>();
    private final Array<Alert> active = new Array<>();
    private final Array<Alert> sorted = new Array<>();
    private boolean sortedDirty = true;
    private double now;
    private boolean inFrame;

    /** Starts a frame's raises at sim time {@code now}. */
    public void begin(double now) {
        this.now = now;
        inFrame = true;
        for (Alert a : active) {
            a.raised = false;
            a.fresh = false;
        }
    }

    /**
     * Raises {@code id} for this frame at {@code level}; returns it for its fields, which hold from
     * the last frame until set again. A level worse than last frame's clears the acknowledgement.
     */
    public Alert raise(String id, Level level, Category category) {
        Alert a = byId.get(id);
        if (a == null) {
            a = new Alert(id);
            a.level = level;
            a.since = now;
            a.fresh = true;
            byId.put(id, a);
            active.add(a);
        } else if (a.raised) {
            if (level.ordinal() > a.level.ordinal()) a.level = level;
        } else if (level.ordinal() > a.level.ordinal()) {
            a.level = level;
            a.acknowledged = false;
        } else {
            a.level = level;
        }
        a.category = category;
        a.raised = true;
        sortedDirty = true;
        return a;
    }

    /** Ends the frame: every alert not raised since {@link #begin} has cleared and goes. */
    public void end() {
        for (int i = active.size - 1; i >= 0; i--) {
            Alert a = active.get(i);
            if (!a.raised) {
                active.removeIndex(i);
                byId.remove(a.id);
                sortedDirty = true;
            }
        }
        inFrame = false;
    }

    /** Clears every alert, as for a new game. */
    public void reset() {
        active.clear();
        byId.clear();
        sortedDirty = true;
    }

    /** Whether the game is between {@link #begin} and {@link #end}. */
    public boolean inFrame() {
        return inFrame;
    }

    /** The alert with {@code id}, or null while it does not hold. */
    public Alert get(String id) {
        return byId.get(id);
    }

    /** Every alert that holds, worst first, then oldest first. Do not change the array. */
    public Array<Alert> all() {
        if (sortedDirty) {
            sorted.clear();
            sorted.addAll(active);
            sorted.sort((a, b) -> a.level != b.level ? b.level.ordinal() - a.level.ordinal() : Double.compare(a.since, b.since));
            sortedDirty = false;
        }
        return sorted;
    }

    /** The alerts of {@code category}, worst first, into {@code out} (cleared first). */
    public Array<Alert> of(Category category, Array<Alert> out) {
        out.clear();
        for (Alert a : all()) if (a.category == category) out.add(a);
        return out;
    }

    /** The worst level that holds, or null with nothing wrong. */
    public Level worst() {
        Array<Alert> all = all();
        return all.size == 0 ? null : all.first().level;
    }

    /** The worst level among alerts not yet acknowledged, or null: what lights the master lamp. */
    public Level master() {
        Level worst = null;
        for (Alert a : active) {
            if (a.acknowledged || a.level == Level.ADVISORY) continue;
            if (worst == null || a.level.ordinal() > worst.ordinal()) worst = a.level;
        }
        return worst;
    }

    /** Acknowledges every alert that holds now: the master lamp goes out until something new or worse. */
    public void acknowledgeAll() {
        for (Alert a : active) a.acknowledged = true;
    }

    public int size() {
        return active.size;
    }
}
