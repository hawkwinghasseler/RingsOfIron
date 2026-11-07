package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Whether the career has ended, the way the classic HUD's ship-lost screen says it: the ship lost
 * (crashed into a body) or the career over (the captain killed), what happened, and how to go on.
 * The game keeps its keys on that screen (Enter loads the latest autosave, the quick-load key the
 * quick save, Esc the menu), so a variant's own screen shows them and may run them on a click,
 * but must not take those keys. Read once a frame by the game wiring layer.
 */
public interface GameOverProvider {
    /** Fills {@code out}, already cleared. */
    void read(GameOver out);

    /** One way to go on: its key, what it does, and the action a click runs. */
    final class Choice {
        /** The key as the player has it bound ("F9", "Enter", "Esc"). */
        public String key = "";
        /** The {@code Action} name behind the key ("QUICK_LOAD"), or "" for a fixed key. */
        public String action = "";
        /** What it does: "load the quick save". */
        public String label = "";
        /** What a click does, as the key would; null where the key is the only way (Esc). */
        public Runnable run;

        void reset() {
            key = "";
            action = "";
            label = "";
            run = null;
        }
    }

    final class GameOver {
        /** Whether the career has ended: the classic HUD shows its screen while this holds. */
        public boolean shown;
        /** True for the ship lost (a crash), false for the career over (the captain killed). */
        public boolean shipLost;
        /** "SHIP LOST" or "CAREER OVER". */
        public String title = "";
        /** What happened, in one sentence: "You crashed into Luna on day 12." */
        public String line = "";
        /** The ways to go on, in the classic screen's order. */
        public final Array<Choice> choices = new Array<>();
        private final Array<Choice> pool = new Array<>();

        public Choice add(String key, String action, String label, Runnable run) {
            Choice c = pool.size > 0 ? pool.pop() : new Choice();
            c.reset();
            c.key = key == null ? "" : key;
            c.action = action == null ? "" : action;
            c.label = label == null ? "" : label;
            c.run = run;
            choices.add(c);
            return c;
        }

        public void clear() {
            shown = false;
            shipLost = false;
            title = "";
            line = "";
            pool.addAll(choices);
            choices.clear();
        }
    }
}
