package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Traffic outside the player's bubble for the map view: stations and haulers on rails, and the
 * bodies they orbit. Implemented in the game wiring from macro-sim's rails snapshot, so ui-hud does
 * not depend on macro-sim. Called on the render thread a few times a second while the map is open.
 */
public interface TrafficMapProvider {

    /** Fills {@code out} (already cleared) with the current picture. */
    void collect(TrafficFrame out);

    enum ContactKind { PLAYER, STATION, HAULER, HOSTILE, OTHER }

    /** A body drawn as a circle. Positions are meters relative to the frame body (the map center). */
    final class MapBody {
        public String name = "";
        public double x, y, radius;
    }

    final class Contact {
        public String name = "";
        public ContactKind kind = ContactKind.OTHER;
        public double x, y;
        /** One short line under the name, e.g. "to Gateway, 2 h" or "docked at Gateway". */
        public String detail = "";
        /** Where one of the player's contracts points; ringed on the map. */
        public boolean objective;
        /** The player's navigation target; bracketed on the map. */
        public boolean target;
        /** Only a last known position: out of sensor range now, drawn dim. */
        public boolean stale;
        /** What the provider knows it by, for picking it on the map; empty if it can't be picked. */
        public String id = "";
    }

    /** Everything one map refresh shows; reused between refreshes. */
    final class TrafficFrame {
        /** Name and radius of the body at the map center; empty name means no frame. */
        public String frameName = "";
        public double frameRadius;
        /** Sim seconds the picture is for. */
        public double time;
        public final Array<MapBody> bodies = new Array<>();
        public final Array<Contact> contacts = new Array<>();
        private final Array<MapBody> bodyPool = new Array<>();
        private final Array<Contact> contactPool = new Array<>();

        public MapBody addBody() {
            MapBody b = bodyPool.size > 0 ? bodyPool.pop() : new MapBody();
            bodies.add(b);
            return b;
        }

        public Contact addContact() {
            Contact c = contactPool.size > 0 ? contactPool.pop() : new Contact();
            c.detail = "";
            c.objective = false;
            c.target = false;
            c.stale = false;
            c.id = "";
            contacts.add(c);
            return c;
        }

        public void clear() {
            bodyPool.addAll(bodies);
            contactPool.addAll(contacts);
            bodies.clear();
            contacts.clear();
            frameName = "";
            frameRadius = 0;
        }

        public int count(ContactKind kind) {
            int n = 0;
            for (Contact c : contacts) if (c.kind == kind) n++;
            return n;
        }
    }
}
