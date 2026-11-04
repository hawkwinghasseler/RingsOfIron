package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Who is at each of the ship's posts: the consoles (helm, weapons, each gun, life support, the
 * reactor) and whether the player, a crew member or nobody works it. Filled once a frame by the game
 * wiring layer from the ship's consoles, the bridge and the crew's tasks.
 */
public interface StationBoardProvider {
    /** Fills {@code out}, already cleared. */
    void collect(StationBoard out);

    enum Post {
        HELM, WEAPONS, GUN, LIFE_SUPPORT, REACTOR
    }

    /** Who works a post. */
    enum Hand {
        NOBODY, PLAYER, CREW,
        /** A crew member is on the way to it. */
        COMING
    }

    final class Seat {
        public Post post = Post.HELM;
        /** The post's name by its place: "HELM", "FWD GUN", "PORT GUN 2". */
        public String name = "";
        /** The console's subsystem entity. */
        public Entity console;
        public Hand hand = Hand.NOBODY;
        /** Who: "you", the crew member's name, or "". */
        public String who = "";
        /** The console works (powered, switched on, not wrecked). */
        public boolean working;
        /** Its footprint's tile on the ship's grid. */
        public int tileX, tileY;

        void reset() {
            post = Post.HELM;
            name = "";
            console = null;
            hand = Hand.NOBODY;
            who = "";
            working = false;
            tileX = tileY = 0;
        }
    }

    final class StationBoard {
        public final Array<Seat> seats = new Array<>();
        private final Array<Seat> pool = new Array<>();
        /** Crew aboard (not counting the player), and how many are free for a post: conscious and on no task. */
        public int crewAboard, crewFree;

        public Seat add() {
            Seat s = pool.size > 0 ? pool.pop() : new Seat();
            s.reset();
            seats.add(s);
            return s;
        }

        public void clear() {
            pool.addAll(seats);
            seats.clear();
            crewAboard = crewFree = 0;
        }

        /** The first seat at {@code post}, or null. Indexed, so it can be called while walking {@link #seats}. */
        public Seat at(Post post) {
            for (int i = 0; i < seats.size; i++) if (seats.get(i).post == post) return seats.get(i);
            return null;
        }
    }
}
