package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * The keys worth knowing where the player is now, for a HUD's own key strip: the hints the classic
 * bottom line draws from ({@code HintRegistry.forContexts} over the contexts that hold), with the
 * player's bindings, most useful first and each action once, then help last. Not cut to the bottom
 * line's five: a variant shows as many as it has room for. Filled once a frame by the game wiring
 * layer from the hint registry.
 */
public interface KeyHintProvider {
    /** Fills {@code out}, already cleared. */
    void collect(KeyHintFrame out);

    /**
     * Every hint the game knows, by where it applies, with the player's keys: what the classic
     * controls panel (F1) lists, for a variant's own ({@code ClassicWindow.HELP}). Fills {@code out},
     * already cleared. Asked when a screen shows it, not each frame. Leaves it empty by default.
     */
    default void collectAll(HelpFrame out) {
    }

    /** One place's keys on the controls panel: "At the helm", then its hints. */
    final class HelpSection {
        public String title = "";
        /** It holds where the player is now (at the helm, on deck), other than anywhere: a panel lists these first. */
        public boolean here;
        /** The gamepad's buttons rather than keys. */
        public boolean gamepad;
        public final Array<KeyHint> hints = new Array<>();
        private final Array<KeyHint> pool = new Array<>();

        public KeyHint add(String key, String label, String action) {
            KeyHint h = pool.size > 0 ? pool.pop() : new KeyHint();
            h.reset();
            h.key = key == null ? "" : key;
            h.label = label == null ? "" : label;
            h.action = action == null ? "" : action;
            hints.add(h);
            return h;
        }

        void clear() {
            title = "";
            here = gamepad = false;
            pool.addAll(hints);
            hints.clear();
        }
    }

    /** The controls panel's sections, in the classic panel's order. */
    final class HelpFrame {
        public final Array<HelpSection> sections = new Array<>();
        private final Array<HelpSection> pool = new Array<>();

        public HelpSection add(String title) {
            HelpSection s = pool.size > 0 ? pool.pop() : new HelpSection();
            s.clear();
            s.title = title == null ? "" : title;
            sections.add(s);
            return s;
        }

        public void clear() {
            pool.addAll(sections);
            sections.clear();
        }
    }

    final class KeyHint {
        /** The keys, as the player bound them: "W/S", "Space", "Wheel". */
        public String key = "";
        /** What they do: "thrust". */
        public String label = "";
        /** The {@code Action} name of the first key ("THRUST"), or "" for a key that is not rebindable. */
        public String action = "";

        void reset() {
            key = "";
            label = "";
            action = "";
        }
    }

    final class KeyHintFrame {
        /** Most useful first; help ("F1", "controls") last. Empty while {@link #placing}. */
        public final Array<KeyHint> hints = new Array<>();
        private final Array<KeyHint> pool = new Array<>();
        /** Placing furniture: its own keys take over, said in one line ({@link #placingText}). */
        public boolean placing;
        public String placingText = "";

        public KeyHint add() {
            KeyHint h = pool.size > 0 ? pool.pop() : new KeyHint();
            h.reset();
            hints.add(h);
            return h;
        }

        public void clear() {
            pool.addAll(hints);
            hints.clear();
            placing = false;
            placingText = "";
        }
    }
}
