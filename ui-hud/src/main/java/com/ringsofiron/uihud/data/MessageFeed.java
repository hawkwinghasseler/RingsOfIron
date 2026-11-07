package com.ringsofiron.uihud.data;

import com.badlogic.gdx.utils.Array;

/**
 * What the game has told the player lately, as rows a callout ticker or news line can style: the
 * game's notices, newest first. Conditions that hold (a fire, a missile inbound) belong in the
 * {@link AlertRegistry}; this is for events. Filled by the game wiring layer from its notice log.
 */
public final class MessageFeed {
    /** Rows kept: as many as the game's log keeps, so a HUD's log can show a career's last weeks. */
    public static final int KEEP = 500;

    public static final class Message {
        public String text = "";
        public AlertRegistry.Level level = AlertRegistry.Level.ADVISORY;
        /** True for good news (a promotion, a contract done), which is not a caution. */
        public boolean important;
        /** Sim seconds when it was posted. */
        public double time;
        /** Times posted while it was on screen; 1 for once. */
        public int count = 1;
        /** Seconds it stays on the classic HUD's stack; 0 once it has gone from there. */
        public float secondsLeft;
        /**
         * The id of the alert that carries this notice's condition (the salvo's "missile"), or empty
         * when none does. An alert is logged as an alert, not as a notice (the Task spec's 9.2), so a
         * log that keeps each alert once leaves the notice out.
         */
        public String alert = "";

        /** The text, with the count when it came more than once. */
        public String label() {
            return count > 1 ? text + " (x" + count + ")" : text;
        }
    }

    private final Array<Message> rows = new Array<>();
    private final Array<Message> pool = new Array<>();
    private long revision;

    /** Starts a refill: the game calls this, then {@link #add} for each notice newest first. */
    public void clear() {
        pool.addAll(rows);
        rows.clear();
    }

    public Message add(String text, AlertRegistry.Level level, double time) {
        Message m = pool.size > 0 ? pool.pop() : new Message();
        m.text = text;
        m.level = level;
        m.important = false;
        m.time = time;
        m.count = 1;
        m.secondsLeft = 0f;
        m.alert = "";
        rows.add(m);
        return m;
    }

    /** Bumped by the game each time a notice is posted, so a ticker can tell something new came. */
    public void posted() {
        revision++;
    }

    public long revision() {
        return revision;
    }

    /** Newest first. Do not change the array. */
    public Array<Message> all() {
        return rows;
    }

    /** The newest, or null with none. */
    public Message newest() {
        return rows.size == 0 ? null : rows.first();
    }
}
