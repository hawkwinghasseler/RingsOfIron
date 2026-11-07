package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.ContactProvider.State;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;

/**
 * What every unit of the Ship Stations Cassette HUD reads this frame: the data, the alert routing, and
 * where the captain is and the ship's condition and states, which the visibility rules (spec section
 * 14) turn on.
 */
final class StationsFrame {
    /** Where the captain is, as the spec's presences count it (section 4). */
    enum Seat {
        /** No ship: nothing but the overlay. */
        NONE,
        /** On foot, aboard or outside: the overlay, the prompt line and the suit readout. */
        DECK,
        HELM,
        WEAPONS,
        GUN
    }

    /** The threat level (section 12), derived each frame. */
    enum Condition {
        GREEN, YELLOW, RED
    }

    /** A hostile within this many metres sets Yellow. */
    static final float YELLOW_RANGE = 50_000f;

    HudData data;
    final StationAlerts alerts = new StationAlerts();

    Seat seat = Seat.NONE;
    Condition condition = Condition.GREEN;
    /** The crew are at battle stations: Red, with BATTLE STATIONS lit. */
    boolean battle;
    boolean docked, latched;
    /** Time warp above 1x. */
    boolean warp;
    boolean disabled, abandoning;
    /** No reactor running: the clusters and MFDs go to their NO POWER face (5.1, rule 4). */
    boolean blackout;
    /** The hull has a shield emitter. */
    boolean emitter;
    /** The full-screen map is up: the band hides (6.3, rule 3). */
    boolean map;
    /** Seconds the HUD has been up, real time. */
    float time;

    void read(HudData d, boolean mapOpen) {
        data = d;
        Presence p = d.ctx.presence;
        boolean outside = d.ctx.exteriorView;
        if (p == Presence.NONE || d.ctx.ship == null) seat = Seat.NONE;
        else if (p == Presence.HELM && outside) seat = Seat.HELM;
        else if (p == Presence.WEAPONS && outside) seat = Seat.WEAPONS;
        else if (p == Presence.GUN && outside) seat = Seat.GUN;
        // Seated with the deck view up is the deck, as far as the screen goes
        else seat = Seat.DECK;
        docked = d.ctx.docked;
        latched = d.ctx.latched;
        warp = d.ctx.warp > 1;
        disabled = !d.ctx.disabledReason.isEmpty();
        abandoning = d.ctx.abandoning || d.orderState.abandoning;
        blackout = d.powerFrame.ratedWatts > 0 && d.powerFrame.outputWatts <= 0;
        emitter = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        battle = d.orderState.battleStations;
        condition = condition(d);
        map = mapOpen;
    }

    /**
     * Section 12: Red when fired upon, a missile is inbound, a hostile has the ship locked or the crew are
     * at battle stations; Yellow with a hostile within 50 km; else Green. Faults aboard do not change it.
     */
    static Condition condition(HudData d) {
        boolean red = d.ctx.missilesInbound > 0 || d.orderState.battleStations || (d.targetStatus.present && d.targetStatus.firingOnUs);
        boolean yellow = false;
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (c.relation != Relation.HOSTILE || c.kind != Kind.SHIP && c.kind != Kind.STATION) continue;
            if (c.state != State.FIT && c.state != State.RUNNING) continue;
            if (c.threat) red = true;
            if (!Float.isNaN(c.range) && c.range <= YELLOW_RANGE) yellow = true;
        }
        return red ? Condition.RED : yellow ? Condition.YELLOW : Condition.GREEN;
    }

    boolean seated() {
        return seat == Seat.HELM || seat == Seat.WEAPONS || seat == Seat.GUN;
    }

    /** At a command seat: the band with its MFDs and the station board. */
    boolean commandSeat() {
        return seat == Seat.HELM || seat == Seat.WEAPONS;
    }

    boolean deck() {
        return seat == Seat.DECK;
    }

    /**
     * Whether station {@code s}'s pages are live (5.1): its hosting console has power. Helm, Navigation and
     * Comms are the helm console's, Tactical the weapons console's (the helm's on a hull without one),
     * Engineering the reactor's and life support's; Command is the captain's own and always live. A wrecked
     * console's station is still read (rule 2); an unpowered or stunned one's pages are dead (rule 3). In a
     * blackout the ship can neither fly nor fight, but what gets the player out stays up (rule 4): with no
     * slate in this build, the MFDs carry the slate's blackout pages, Engineering, Comms and Command.
     */
    boolean live(Station s) {
        if (s == Station.CMD) return true;
        if (blackout) return s == Station.ENG || s == Station.COMMS;
        switch (s) {
            case TAC: return powered(Post.WEAPONS, "WEAPONS");
            case ENG: return powered(Post.REACTOR, "REACTOR") || powered(Post.LIFE_SUPPORT, "LIFE SUP");
            default: return powered(Post.HELM, "HELM");
        }
    }

    /** Whether the station's console is out (8.5: struck through on the board): wrecked or without power. */
    boolean struck(Station s) {
        if (s == Station.CMD) return false;
        // A blackout strikes what it kills: Engineering and Comms stay readable (5.1, 14.2)
        if (blackout) return !live(s);
        switch (s) {
            case TAC: return !working(Post.WEAPONS);
            case ENG: return !working(Post.REACTOR) && !working(Post.LIFE_SUPPORT);
            default: return !working(Post.HELM);
        }
    }

    private boolean working(Post post) {
        com.ringsofiron.uihud.providers.StationBoardProvider.Seat seat = data.stations.at(post);
        if (seat == null) return post == Post.HELM || working(Post.HELM);
        return seat.working;
    }

    private boolean powered(Post post, String system) {
        com.ringsofiron.uihud.providers.StationBoardProvider.Seat seat = data.stations.at(post);
        // A hull without the console: the helm hosts its station (rule 1)
        if (seat == null) return post == Post.HELM || powered(Post.HELM, "HELM");
        if (seat.working) return true;
        for (int ri = 0; ri < data.powerFrame.faults.size; ri++) {
            SystemRow r = data.powerFrame.faults.get(ri);
            if (r.system.equals(system) && (r.fault == Fault.DESTROYED || r.fault == Fault.DISABLED)) return true;
        }
        return false;
    }

    /** Red, Yellow: the board's "you" lights; in Green the whole board is dim. */
    boolean alerted() {
        return condition != Condition.GREEN;
    }
}
