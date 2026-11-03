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
