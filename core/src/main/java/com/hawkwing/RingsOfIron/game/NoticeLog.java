package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.signals.Signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Everything the game tells the player: a short stack of notices on screen, newest on top, each
 * held for its own time, and a log of the last {@link #KEEP} for the log window (L). Systems post
 * here, or keep their own notice lists for {@link NoticeFeeds} to bring in.
 *
 * <p>The same text posted again while it is still on screen is not stacked twice: it is held
 * again and counted ("Launcher reloading (x3)").
 */
public final class NoticeLog {
    /** How much a notice matters: important ones get their own colour, a sound and a longer hold. */
    public enum Kind {
        INFO(5f),
        /** Promotions, contracts done or failed. */
        IMPORTANT(8f),
        /**
         * Danger: a hostile intercepting, propellant running low, a missile inbound, and loss: a crew
         * member dead or critically wounded, the ship disabled or boarded (round 9: not in a promotion's green).
         */
        WARNING(8f);

        public final float seconds;

        Kind(float seconds) {
            this.seconds = seconds;
        }
    }

    public static final class Notice {
        public final String text;
        public final Kind kind;
        /** Sim seconds when it was first posted. */
        public final double time;
        /** Times posted while still on screen; 1 for once. */
        public int count = 1;
        /** Seconds it stays on screen. */
        float left;

        Notice(String text, Kind kind, double time) {
            this.text = text;
            this.kind = kind;
            this.time = time;
            this.left = kind.seconds;
        }

        /** The text, with the count when it came more than once. */
        public String label() {
            return count > 1 ? text + " (x" + count + ")" : text;
        }

        public float secondsLeft() {
            return left;
        }
    }

    /** The missile key pressed while the launcher reloads, told once per reload ({@link #postOnce}). */
    public static final String RELOADING = "Launcher reloading";

    /** Notices kept for the log. */
    public static final int KEEP = 50;
    /** Notices on screen at once; the oldest goes when another comes. */
    public static final int STACK = 4;

    /** Fires for every notice posted, repeats included, for sounds. */
    public final Signal<Notice> posted = new Signal<>();

    private final DoubleSupplier clock;
    /** Newest first. */
    private final ArrayDeque<Notice> history = new ArrayDeque<>();
    /** Newest first. */
    private final List<Notice> showing = new ArrayList<>();
    /** Texts {@link #postOnce} has told, until {@link #allowAgain}. */
    private final java.util.Set<String> told = new java.util.HashSet<>();

    /** @param clock sim seconds, for the log's timestamps */
    public NoticeLog(DoubleSupplier clock) {
        this.clock = clock;
    }

    public Notice post(String text) {
        return post(text, Kind.INFO);
    }

    /** Posts {@code text}; null or empty text is ignored. Returns the notice it went into, or null. */
    public Notice post(String text, Kind kind) {
        if (text == null || text.isEmpty()) return null;
        Notice n;
        Notice same = find(text);
        if (same != null) {
            n = same;
            n.count++;
            n.left = Math.max(n.left, kind.seconds);
            showing.remove(n);
        } else {
            n = new Notice(text, kind, clock.getAsDouble());
            history.addFirst(n);
            while (history.size() > KEEP) history.removeLast();
        }
        showing.add(0, n);
        while (showing.size() > STACK) showing.remove(showing.size() - 1);
        posted.dispatch(n);
        return n;
    }

    /**
     * Posts {@code text} in place of any notice on screen that starts with {@code topic}, for a
     * setting the player steps through ("Navigation target: ..."), so each press doesn't stack
     * another line. The log keeps them all.
     */
    public Notice replace(String topic, String text) {
        return replace(topic, text, Kind.INFO);
    }

    /** {@link #replace(String, String)} with how much it matters. */
    public Notice replace(String topic, String text, Kind kind) {
        if (text == null || text.isEmpty()) return null;
        showing.removeIf(n -> n.text.startsWith(topic) && !n.text.equals(text));
        return post(text, kind);
    }

    /**
     * Puts {@code text} in place of {@code old}, on screen and in the log, for a warning that grows
     * while it is still the same event (a missile salvo's count): one line, and no second sound.
     * Posts it afresh if {@code old} has left the log. Returns the notice now holding it.
     */
    public Notice revise(Notice old, String text) {
        return revise(old, text, old == null ? Kind.INFO : old.kind);
    }

    /**
     * {@link #revise(Notice, String)} as another kind: a wound that worsens in the same moment ("lightly",
     * then "critically wounded") is one line, in the worse one's colour.
     */
    public Notice revise(Notice old, String text, Kind kind) {
        if (text == null || text.isEmpty()) return old;
        if (old == null || !history.contains(old)) return post(text, kind);
        Notice n = new Notice(text, kind, old.time);
        n.left = Math.max(old.left, kind.seconds);
        List<Notice> kept = new ArrayList<>(history);
        kept.set(kept.indexOf(old), n);
        history.clear();
        history.addAll(kept);
        int at = showing.indexOf(old);
        if (at >= 0) showing.set(at, n);
        else {
            showing.add(0, n);
            while (showing.size() > STACK) showing.remove(showing.size() - 1);
        }
        return n;
    }

    /**
     * Posts {@code text} once, then not again until {@link #allowAgain} (or {@link #clear}), for a
     * refusal the player may press into many times over one wait: "Launcher reloading" once per
     * reload, not once per press (round 8's window: 6 of 25 lines in the log). Returns null when held back.
     */
    public Notice postOnce(String text, Kind kind) {
        if (text == null || text.isEmpty() || !told.add(text)) return null;
        return post(text, kind);
    }

    /** The wait {@link #postOnce} told of is over (the launcher fired): the next one is told again. */
    public void allowAgain(String text) {
        told.remove(text);
    }

    private Notice find(String text) {
        for (Notice n : showing) if (n.text.equals(text)) return n;
        return null;
    }

    /** Ages the notices on screen by {@code delta} real seconds. */
    public void update(float delta) {
        for (Iterator<Notice> it = showing.iterator(); it.hasNext(); ) {
            Notice n = it.next();
            n.left -= delta;
            if (n.left <= 0f) it.remove();
        }
    }

    /** What is on screen now, newest first. */
    public List<Notice> showing() {
        return Collections.unmodifiableList(showing);
    }

    /** The last {@link #KEEP} notices, newest first. */
    public List<Notice> history() {
        return new ArrayList<>(history);
    }

    /** The newest notice, or null. */
    public Notice latest() {
        return history.peekFirst();
    }

    /** Forgets everything, on screen and in the log: another game was loaded. */
    public void clear() {
        told.clear();
        showing.clear();
        history.clear();
    }

    /** Takes everything off the screen; the log keeps it. */
    public void clearScreen() {
        showing.clear();
    }
}
