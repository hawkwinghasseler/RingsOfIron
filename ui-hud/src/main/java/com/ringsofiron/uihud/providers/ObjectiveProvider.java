package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * What the player has taken on: the contracts held, each with its deadline and next step, and the
 * operation running if one is. Filled once a frame by the game wiring layer from the contract board.
 */
public interface ObjectiveProvider {
    /** Fills {@code out}, already cleared. */
    void collect(ObjectiveFrame out);

    /**
     * Makes where contract {@code id} sends the ship next ({@link Objective#next}) the nav target;
     * returns what the game says. Nothing by default.
     */
    default String navTo(long id) {
        return "";
    }

    enum State {
        /** Under way. */
        ACTIVE,
        /** Its work is done: back to the station that gave it to be paid. */
        RETURN,
        DONE,
        FAILED
    }

    final class Objective {
        /** The contract's id, as the mission board knows it. */
        public long id = -1;
        /** "Food to Shackleton Ice". */
        public String title = "";
        /** The kind as a word ("Delivery", "Escort", "Patrol"), for a short line. */
        public String kind = "";
        /** Who gave it, and the station it came from. */
        public String giver = "", station = "";
        public State state = State.ACTIVE;
        /** Seconds to the deadline, negative once past it; NaN with no deadline. */
        public double timeLeft = Double.NaN;
        public boolean late;
        /** Where it sends the ship next ("Shackleton Ice"), or "". */
        public String next = "";
        /** That next place is the nav target. */
        public boolean nextIsNav;
        /** Progress as the game counts it (units delivered, marks made), and of how many; both 0 when it does not count. */
        public double progress, of;
        /** The pay, credits, and the standing it brings with the giver. */
        public double reward;
        public float standing;

        void reset() {
            id = -1;
            standing = 0f;
            title = "";
            kind = "";
            giver = "";
            station = "";
            state = State.ACTIVE;
            timeLeft = Double.NaN;
            late = false;
            next = "";
            nextIsNav = false;
            progress = of = 0;
            reward = 0;
        }
    }

    final class ObjectiveFrame {
        /** The contracts held, soonest deadline first. */
        public final Array<Objective> objectives = new Array<>();
        private final Array<Objective> pool = new Array<>();
        /** The operation (a chain of contracts) running, its step and of how many; "" and 0 with none. */
        public String operation = "";
        public int operationStep, operationSteps;

        public Objective add() {
            Objective o = pool.size > 0 ? pool.pop() : new Objective();
            o.reset();
            objectives.add(o);
            return o;
        }

        public void clear() {
            pool.addAll(objectives);
            objectives.clear();
            operation = "";
            operationStep = operationSteps = 0;
        }

        /** The objective with the soonest deadline, or the first, or null with none. */
        public Objective first() {
            return objectives.size == 0 ? null : objectives.first();
        }
    }
}
