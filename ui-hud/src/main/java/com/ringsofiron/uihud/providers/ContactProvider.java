package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;

/**
 * Every contact the player's ship knows of in the bubble, as values rather than a marker's tint and
 * label text: what it is, whose side it is on, what state it is in, where it is against the ship.
 * Filled once a frame by the game wiring layer from the HUD markers, the sensors and the factions.
 * Positions are bubble metres, as {@code PhysicsManager.getPosition} gives them; the camera and
 * {@code TransformComponent} work in physics units of {@code PhysicsManager.METERS_PER_UNIT} metres.
 */
public interface ContactProvider {
    /** Fills {@code out}, already cleared. */
    void collect(ContactFrame out);

    enum Kind {
        SHIP, STATION, MISSILE,
        /** A wreck, an abandoned hull or an escape pod. */
        DERELICT,
        /** A place to go that is not a craft. */
        WAYPOINT,
        /** One of the player's crew outside, adrift or on a line. */
        EVA
    }

    /** What state a ship is in, as the markers word it. */
    enum State {
        /** Whole and under way. */
        FIT,
        /** Running from a fight. */
        RUNNING,
        /** Struck its colours (surrendered). */
        STRUCK,
        /** Its guns silenced by the player's order (a pirate base told to stand down). */
        SILENCED,
        /** Disabled: no power, no crew, no helm, adrift. */
        DISABLED,
        /** Wrecked. */
        WRECKED
    }

    final class Contact {
        public Entity entity;
        /** The marker's label ("Raider", "Highport"). */
        public String label = "";
        public Kind kind = Kind.SHIP;
        public Relation relation = Relation.UNKNOWN;
        public State state = State.FIT;
        /** Why it is disabled as the game says it ("NO_POWER", "WRECKED"), or "". */
        public String disabledReason = "";
        public float x, y;
        /** Range, m; bearing from the player's nose, degrees, port +; closing, m/s, + closing. */
        public float range = Float.NaN, bearingDeg = Float.NaN, closing = Float.NaN;
        /** Its size, m, so a marker can frame it. */
        public float radius;
        /** The player's lock, and the nav target. */
        public boolean locked, nav;
        /** It threatens the ship: hostile and has it locked, or a missile guided at it. */
        public boolean threat;

        void reset() {
            entity = null;
            label = "";
            kind = Kind.SHIP;
            relation = Relation.UNKNOWN;
            state = State.FIT;
            disabledReason = "";
            x = y = 0f;
            range = bearingDeg = closing = Float.NaN;
            radius = 0f;
            locked = nav = threat = false;
        }
    }

    final class ContactFrame {
        /** Nearest first. */
        public final Array<Contact> contacts = new Array<>();
        private final Array<Contact> pool = new Array<>();

        public Contact add() {
            Contact c = pool.size > 0 ? pool.pop() : new Contact();
            c.reset();
            contacts.add(c);
            return c;
        }

        public void clear() {
            pool.addAll(contacts);
            contacts.clear();
        }

        /** The locked contact, or null. Indexed, as is {@link #count}, so either can be called while walking {@link #contacts}. */
        public Contact locked() {
            for (int i = 0; i < contacts.size; i++) if (contacts.get(i).locked) return contacts.get(i);
            return null;
        }

        /** How many contacts of {@code relation} there are. */
        public int count(Relation relation) {
            int n = 0;
            for (int i = 0; i < contacts.size; i++) if (contacts.get(i).relation == relation) n++;
            return n;
        }
    }
}
