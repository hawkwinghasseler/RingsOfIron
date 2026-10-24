package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.Wound;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * A running account of what happens to everyone's wounds, for watching the wound rules at work:
 * each wound and what gave it, each hit on someone already critical and what it did to their
 * bleed-out clock, the medic reaching them, the bleeding stopped, each level treated away, people
 * carried, falling unconscious or coming round, and deaths. It only looks at the crew, a frame at
 * a time, so it changes nothing.
 *
 * <p>The game tells the player some of this as notices ({@link CrewMedical}); this tells all of it,
 * for every crew member in the world, with the numbers.
 */
public final class WoundWatch {
    /** Lines kept. */
    public static final int KEEP = 200;
    /** A critical casualty's clock falling below this share of the last look was cut by a hit (one halves it). */
    static final float CLOCK_CUT = CrewMedical.CLOCK_CUT;

    /** One line of the account. */
    public static final class Line {
        public final double time;
        public final String text;
        /** Who it is about. */
        public final Entity crew;

        Line(double time, String text, Entity crew) {
            this.time = time;
            this.text = text;
            this.crew = crew;
        }

        @Override
        public String toString() {
            return String.format("%6.1fs  %s", time, text);
        }
    }

    /** What a crew member looked like at the last frame. */
    private static final class Seen {
        /** Whose this is: a removed crew member's entity comes back from the engine's pool as a new one, with a new component. */
        CrewMemberComponent member;
        Wound wound;
        float injury, bleedOut;
        boolean stabilised;
        CrewState state;
        Entity treatedBy, carriedBy;
        /** Hits taken while already critical, since the wound went critical. */
        int hitsWhileCritical;
    }

    private final CrewService crew;
    private final DoubleSupplier clock;
    private final Map<Entity, Seen> seen = new IdentityHashMap<>();
    private final List<Line> lines = new ArrayList<>();

    public WoundWatch(CrewService crew, DoubleSupplier clock) {
        this.crew = crew;
        this.clock = clock;
    }

    /** The account so far, oldest first; the last {@link #KEEP} lines. */
    public List<Line> lines() {
        return lines;
    }

    /** Hits {@code e} has taken while already critical, since their wound last went critical. */
    public int hitsWhileCritical(Entity e) {
        Seen s = seen.get(e);
        return s == null ? 0 : s.hitsWhileCritical;
    }

    /** Adds a line of the caller's own: what a debug tool did, say. */
    public void note(String text) {
        add(text, null);
    }

    /** Looks at everyone once: call after each world update. */
    public void update() {
        for (Entity e : crew.crewEntities()) {
            CrewMemberComponent c = crew.crew(e);
            Seen s = seen.get(e);
            if (s == null || s.member != c) {
                seen.put(e, s = new Seen());
                s.member = c;
                copy(c, s);
                continue;
            }
            look(e, c, s);
            copy(c, s);
        }
        seen.entrySet().removeIf(en -> crew.crew(en.getKey()) != en.getValue().member || en.getKey().isScheduledForRemoval());
    }

    private void look(Entity e, CrewMemberComponent c, Seen s) {
        String who = c.name;
        if (s.state != CrewState.DEAD && c.state == CrewState.DEAD) {
            add(who + " died" + (c.lastDamageCause == null ? "" : " (" + cause(c.lastDamageCause) + ")")
                    + (s.wound == Wound.CRITICAL && !s.stabilised ? String.format(", %.1f s left on the clock", s.bleedOut) : ""), e);
            return;
        }
        if (c.state == CrewState.DEAD) return;
        String cause = c.woundCause != null ? cause(c.woundCause) : cause(c.lastDamageCause);
        if (c.wound.compareTo(s.wound) > 0) {
            if (c.wound == Wound.CRITICAL) s.hitsWhileCritical = 0;
            add(String.format("%s %s: %s (injury %.0f)%s", who, cause, c.wound.label.toLowerCase(), c.injury,
                    c.wound == Wound.CRITICAL ? String.format(", %.0f s to bleed out", c.bleedOut) : ""), e);
        } else if (c.wound == Wound.CRITICAL && s.wound == Wound.CRITICAL && c.injury > s.injury) {
            // A hit on someone already down: halves the clock if it was a serious one
            boolean cut = c.bleedOut < s.bleedOut * CLOCK_CUT;
            if (cut) s.hitsWhileCritical++;
            add(String.format("%s hit again while critical (%s, +%.0f injury)%s", who, cause(c.lastDamageCause),
                    c.injury - s.injury, cut ? String.format(": bleed-out %.1f s -> %.1f s%s (clock cut %d times)",
                            s.bleedOut, c.bleedOut, s.stabilised ? ", bleeding again" : "", s.hitsWhileCritical) : ""), e);
        } else if (c.injury > s.injury) {
            add(String.format("%s %s, +%.0f injury (%s)", who, cause(c.lastDamageCause), c.injury - s.injury,
                    c.wound.label.toLowerCase()), e);
        }
        if (c.wound.compareTo(s.wound) < 0) {
            add(String.format("%s treated: %s -> %s%s", who, s.wound.label.toLowerCase(), c.wound.label.toLowerCase(),
                    s.treatedBy != null ? " by " + crew.crew(s.treatedBy).name : ""), e);
        }
        if (c.stabilised && !s.stabilised) {
            add(String.format("%s stabilised with %.1f s left%s", who, c.bleedOut,
                    c.treatedBy != null ? " by " + crew.crew(c.treatedBy).name : ""), e);
        }
        if (c.treatedBy != null && c.treatedBy != s.treatedBy) {
            add(String.format("%s reaches %s (%s%s)", crew.crew(c.treatedBy).name, who, c.wound.label.toLowerCase(),
                    c.wound == Wound.CRITICAL && !c.stabilised ? String.format(", %.1f s left", c.bleedOut) : ""), e);
        }
        if (c.carriedBy != null && c.carriedBy != s.carriedBy) add(crew.crew(c.carriedBy).name + " picks up " + who, e);
        if (c.carriedBy == null && s.carriedBy != null) add(who + " set down", e);
        if (s.state == CrewState.CONSCIOUS && c.state == CrewState.UNCONSCIOUS && !c.wound.disabling()) {
            add(who + " unconscious (" + cause(c.lastDamageCause) + String.format(", health %.0f)", c.health), e);
        }
        if (s.state == CrewState.UNCONSCIOUS && c.state == CrewState.CONSCIOUS) add(who + " back on their feet", e);
    }

    private static void copy(CrewMemberComponent c, Seen s) {
        s.wound = c.wound;
        s.injury = c.injury;
        s.bleedOut = c.bleedOut;
        s.stabilised = c.stabilised;
        s.state = c.state;
        s.treatedBy = c.treatedBy;
        s.carriedBy = c.carriedBy;
    }

    private static String cause(Enum<?> cause) {
        return cause == null ? "hurt" : cause.name().toLowerCase().replace('_', ' ');
    }

    private void add(String text, Entity e) {
        lines.add(new Line(clock.getAsDouble(), text, e));
        if (lines.size() > KEEP) lines.remove(0);
    }
}
