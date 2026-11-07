package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * The captain's career, for a captain's record (functional-context 10.5, the Career and Standing
 * tabs): the faction served, the rank held and the next, the budget and its payday, when each rank
 * was given, the contracts and bounties behind it, the standing with every faction, and how strained
 * the lawful factions' stations are. Implemented in the game wiring layer, so ui-hud does not depend
 * on the career. Read when a screen shows it, not each frame; called on the render thread.
 */
public interface CareerProvider {
    /** Fills {@code out}, already cleared. */
    void read(Career out);

    /** A faction, and how the player stands with it. */
    final class Faction {
        public String name = "";
        /** Standing, {@link Career#MIN} to {@link Career#MAX}; 0 until the player has dealt with it. */
        public float standing;
        /** The faction the player serves. */
        public boolean sponsor;
        /** At war with everyone (the pirates): no standing to be had. */
        public boolean outlaw;
        /** At or below {@link Career#hostileAt}: its ships fire on the player's. */
        public boolean hostile;
        /** Below {@link Career#refuseBelow}: its stations refuse the player. */
        public boolean refuses;

        void reset() {
            name = "";
            standing = 0f;
            sponsor = outlaw = hostile = refuses = false;
        }
    }

    /** A lawful faction's stations, and the strain on them: their highest tension with any other faction. */
    final class Region {
        public String name = "";
        public float tension;
        /** At {@link Career#highAt} or over: patrols reinforced, rivals clearing only friends. */
        public boolean high;
        /** What its yards charge over their base prices for it, as a share (0.12 for 12%). */
        public float yardRise;
        /** Combat jobs its boards post over their usual number. */
        public int combatJobs;

        void reset() {
            name = "";
            tension = yardRise = 0f;
            high = false;
            combatJobs = 0;
        }
    }

    /** A rank given, and when (sim seconds; 0 for the career's start). */
    final class Promotion {
        public String rank = "";
        public double at;
    }

    final class Career {
        public static final float MIN = -100f, MAX = 100f;
        /** Whether there is a career to show. */
        public boolean present;
        /** The faction served, and the rank held, as that faction calls it. */
        public String sponsor = "", rank = "";
        /** Standing with the faction served. */
        public float standing;
        /** The next rank up and the standing that earns it; "" and NaN at the top of the ladder. */
        public String nextRank = "";
        public float nextAt = Float.NaN;
        /** Sim seconds the rank must still be held before the next may be given; 0 when it may be now. */
        public double gradeLeft;
        /** The budget paid each week at this rank, credits; seconds to the next payday; and the budget paid in all. */
        public double budget, payIn, budgetPaid;
        /** The largest chassis the rank may fly ("corvette"), or "" with no limit. */
        public String mayFly = "";
        /** Each rank given, lowest first, from the career's start. */
        public final Array<Promotion> promotions = new Array<>();
        /** Of the last {@link #recordOf} contracts ended, how many were done, failed and given up; the game keeps no more. */
        public int done, failed, abandoned, recordOf;
        /** Whether the game's record of ended contracts is full, so older ones are gone from it. */
        public boolean recordFull;
        /** Bounties among the contracts done, and what they paid. */
        public int bounties;
        public double bountyPay;
        /** Every faction, the sponsor first. */
        public final Array<Faction> factions = new Array<>();
        /** Each lawful faction's stations and the strain on them. */
        public final Array<Region> regions = new Array<>();
        /** The standings at which a faction's stations refuse the player and its ships turn hostile. */
        public float refuseBelow, hostileAt;
        /** The tension that is high, and the strain from which boards post more combat jobs. */
        public float highAt, combatJobsAt;
        private final Array<Faction> factionPool = new Array<>();
        private final Array<Region> regionPool = new Array<>();
        private final Array<Promotion> promotionPool = new Array<>();

        public Faction addFaction() {
            Faction f = factionPool.size > 0 ? factionPool.pop() : new Faction();
            f.reset();
            factions.add(f);
            return f;
        }

        public Region addRegion() {
            Region r = regionPool.size > 0 ? regionPool.pop() : new Region();
            r.reset();
            regions.add(r);
            return r;
        }

        public Promotion addPromotion(String rank, double at) {
            Promotion p = promotionPool.size > 0 ? promotionPool.pop() : new Promotion();
            p.rank = rank == null ? "" : rank;
            p.at = at;
            promotions.add(p);
            return p;
        }

        public void clear() {
            present = false;
            sponsor = rank = nextRank = mayFly = "";
            standing = 0f;
            nextAt = Float.NaN;
            gradeLeft = budget = payIn = budgetPaid = 0;
            done = failed = abandoned = recordOf = 0;
            recordFull = false;
            bounties = 0;
            bountyPay = 0;
            refuseBelow = hostileAt = highAt = combatJobsAt = 0f;
            factionPool.addAll(factions);
            factions.clear();
            regionPool.addAll(regions);
            regions.clear();
            promotionPool.addAll(promotions);
            promotions.clear();
        }
    }
}
