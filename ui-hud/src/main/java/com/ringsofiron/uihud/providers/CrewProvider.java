package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * The player's crew for the crew window: who is aboard with their skills, condition, morale, wage
 * and record; while docked, the station's crew looking for a berth; and the prisoners held aboard.
 * Implemented in the game wiring, so ui-hud does not depend on the crew AI or the factions. Called
 * on the render thread.
 */
public interface CrewProvider {

    /** Fills {@code out} (already cleared). Leave {@link CrewSheet#shipName} empty when the player has no ship. */
    void collect(CrewSheet out);

    /** Hires candidate {@code key}; returns what came of it. */
    String hire(String key);

    /** Lets crew member {@code id} go; returns what came of it. */
    String dismiss(long id);

    /** Brings the prisoners of the taken ship alongside aboard; returns what came of it. */
    String takePrisoners();

    /** Hands over, ransoms or releases prisoner {@code id}, as {@link Prisoner#option} says; returns what came of it. */
    String settlePrisoner(long id);

    /** Takes prisoner {@code id}, who offered to join, into the crew; returns what came of it. */
    String recruit(long id);

    /** Turns suits at battle stations on or off for the player's ship; returns what came of it. */
    default String toggleSuits() {
        return "";
    }

    /** Holds everyone on the player's ship in from EVA, or lets them go out again; returns what came of it. */
    default String toggleEva() {
        return "";
    }

    /** Does what {@link Member#evaAction} says for crew member {@code id}; returns what came of it. */
    default String evaOrder(long id) {
        return "";
    }

    final class Member {
        public long id;
        public String name = "";
        public String role = "";
        /** "Gun 62  Eng 50  Mar 50". */
        public String skills = "";
        /** "OK 100%", "Unconscious 20%", "Light wound 100%", "Critical wound, 4:10 to live". */
        public String condition = "";
        /** "Morale 70%". */
        public String morale = "";
        /** "250 cr/wk", or "Service" for crew the sponsor pays. */
        public String wage = "";
        /** "3 ships beaten, 1 boarded". */
        public String history = "";
        /** True when the crew member can be let go here (docked). */
        public boolean dismissable;
        /** What the player can order them to do about EVA: "Send out", "Call in", "Let out"; empty for nothing. */
        public String evaAction = "";
    }

    final class Hire {
        public String key = "";
        public String name = "";
        public String role = "";
        public String skills = "";
        /** "250 cr/wk, 250 cr to sign". */
        public String terms = "";
    }

    final class Prisoner {
        public long id;
        public String name = "";
        /** Their faction and role. */
        public String detail = "";
        /** As {@link Member#condition}. */
        public String condition = "";
        /** What can be done with them here: "Hand over", "Ransom", "Release"; empty when not docked. */
        public String option = "";
        public boolean offersToJoin;
    }

    final class CrewSheet {
        /** The player's ship; empty when they have none. */
        public String shipName = "";
        /** "5 / 8 berths, 470 cr/wk in wages". */
        public String summary = "";
        /** Station whose crew are looking for a berth; empty when not docked at one that hires. */
        public String stationName = "";
        /** True when a taken ship is alongside with prisoners to bring aboard. */
        public boolean prisonersAlongside;
        /** "Suits at battle stations: on"; empty when it can't be set. */
        public String suits = "";
        /** "EVA: crew go out for work outside as it comes"; empty when it can't be set. */
        public String eva = "";
        public final Array<Member> crew = new Array<>();
        public final Array<Hire> hires = new Array<>();
        public final Array<Prisoner> prisoners = new Array<>();

        public Member addMember() {
            Member m = new Member();
            crew.add(m);
            return m;
        }

        public Hire addHire() {
            Hire h = new Hire();
            hires.add(h);
            return h;
        }

        public Prisoner addPrisoner() {
            Prisoner p = new Prisoner();
            prisoners.add(p);
            return p;
        }

        /** True when there is a ship to show. */
        public boolean isOpen() {
            return !shipName.isEmpty();
        }

        public void clear() {
            shipName = "";
            summary = "";
            stationName = "";
            prisonersAlongside = false;
            suits = "";
            eva = "";
            crew.clear();
            hires.clear();
            prisoners.clear();
        }
    }
}
