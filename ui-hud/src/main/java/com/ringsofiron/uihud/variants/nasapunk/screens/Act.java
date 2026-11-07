package com.ringsofiron.uihud.variants.nasapunk.screens;

/**
 * An order a page offers: a row's (BUY 1) or the page's own (REPAIR ALL). Barred, it says why once,
 * and a press does nothing; lit, it is running (green) or waits for a second press (amber). A guarded
 * order (GIVE UP a contract, LET GO one of the crew) asks for that second press before it is given.
 */
public final class Act {
    public enum State { IDLE, ON, ARMED }

    /** Which order it is, kept across rebuilds so an armed guard stays armed: its row's id and its words. */
    public String id = "";
    public String label = "";
    /** The key that gives it besides a click, as the player has it ("ENTER"), or "". */
    public String key = "";
    /** Why it can't be given now, in capitals, or "" while it can. */
    public String why = "";
    public boolean live;
    /** Pressed twice: the first press arms it ({@link Page#press}). */
    public boolean guard;
    public State state = State.IDLE;
    public Runnable run;

    public Act set(String label, boolean live, String why, Runnable run) {
        this.label = label == null ? "" : label;
        this.live = live && run != null;
        this.why = this.live || why == null ? "" : why;
        this.run = run;
        id = this.label;
        key = "";
        guard = false;
        state = State.IDLE;
        return this;
    }

    public Act key(String key) {
        this.key = key == null ? "" : key;
        return this;
    }

    public Act state(State state) {
        this.state = state == null ? State.IDLE : state;
        return this;
    }

    /** It asks for a second press before it is given. */
    public Act guarded() {
        guard = true;
        return this;
    }

    /** Lit green while {@code on}: what it orders is under way, or switched on. */
    public Act on(boolean on) {
        if (on && state == State.IDLE) state = State.ON;
        return this;
    }
}
