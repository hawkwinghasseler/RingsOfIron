package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.Factions;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.career.ServiceBudget;
import com.ringsofiron.career.ServiceRecord;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.uihud.providers.CareerProvider;

import java.util.List;

/**
 * ui-hud's {@link CareerProvider}: the player's {@link ServiceRecord} as {@link FactionStanding} keeps
 * it (the faction served, the rank and when each was given, the standing with every faction), the
 * {@link ServiceBudget}'s pay, the contracts the {@link ContractBoard} remembers ending, and the
 * {@link FactionTension} on each lawful faction's stations.
 */
public class PlayerCareer implements CareerProvider {
    private final FactionStanding standing;
    private final ServiceBudget budget;
    private final FactionTension tension;
    private final ContractBoard contracts;

    public PlayerCareer(FactionStanding standing, ServiceBudget budget, FactionTension tension, ContractBoard contracts) {
        this.standing = standing;
        this.budget = budget;
        this.tension = tension;
        this.contracts = contracts;
    }

    @Override
    public void read(Career out) {
        Factions f = standing.factions();
        RankLadder ranks = standing.ranks();
        ServiceRecord record = standing.record();
        if (f == null || ranks == null || record == null) return;
        out.present = true;
        int sponsor = standing.sponsor();
        out.sponsor = name(f, sponsor);
        out.rank = standing.rankName();
        out.standing = standing.standing(sponsor);
        int next = record.rank + 1;
        if (next < ranks.size()) {
            out.nextRank = standing.rankName(next);
            out.nextAt = ranks.get(next).standing;
            out.gradeLeft = standing.timeInGradeLeft(next);
        }
        RankDef rank = standing.rank();
        out.budget = rank.budget;
        out.payIn = budget == null ? 0 : budget.untilNextPay();
        out.budgetPaid = record.budgetPaid;
        out.mayFly = rank.maxChassis == null ? "" : rank.maxChassis;
        for (int i = 0; i <= record.rank && i < ranks.size(); i++) out.addPromotion(standing.rankName(i), record.promotedTo(i));
        record(out);
        out.refuseBelow = FactionStanding.REFUSE_BELOW;
        out.hostileAt = FactionStanding.HOSTILE_AT;
        factions(out, f, sponsor);
        out.highAt = FactionTension.HIGH;
        out.combatJobsAt = tension == null ? 0f : tension.tuning().combatJobsAt;
        if (tension != null) regions(out, f);
    }

    /** The contracts the board remembers ending: done, failed and given up, and the bounties among them. */
    private void record(Career out) {
        List<Contract> history = contracts == null ? List.of() : contracts.history();
        out.recordOf = history.size();
        out.recordFull = history.size() >= ContractBoard.HISTORY;
        for (Contract c : history) {
            if (c.status == Contract.Status.DONE) {
                out.done++;
                if (c.type == Contract.Type.BOUNTY) {
                    out.bounties++;
                    out.bountyPay += c.reward;
                }
            } else if (c.status == Contract.Status.FAILED) {
                out.failed++;
            } else if (c.status == Contract.Status.ABANDONED) {
                out.abandoned++;
            }
        }
    }

    /** Every faction the data has: the sponsor first, then the lawful ones in the data's order, then those at war with all. */
    private void factions(Career out, Factions f, int sponsor) {
        addFaction(out, f, sponsor, sponsor);
        for (int pass = 0; pass < 2; pass++) {
            for (FactionDef def : f.all()) {
                int i = f.index(def.id);
                if (i != sponsor && def.hostileToAll == (pass == 1)) addFaction(out, f, i, sponsor);
            }
        }
    }

    private void addFaction(Career out, Factions f, int i, int sponsor) {
        FactionDef def = f.def(i);
        if (def == null) return;
        Faction row = out.addFaction();
        row.name = name(f, i);
        row.sponsor = i == sponsor;
        row.outlaw = def.hostileToAll;
        row.standing = standing.standing(i);
        row.hostile = def.hostileToAll || row.standing <= FactionStanding.HOSTILE_AT;
        row.refuses = def.hostileToAll || row.standing < FactionStanding.REFUSE_BELOW;
    }

    /** Each lawful faction with stations, and the strain on them, as the traffic map lists them. */
    private void regions(Career out, Factions f) {
        for (FactionDef def : f.all()) {
            if (def.hostileToAll || def.stations.isEmpty()) continue;
            int i = f.index(def.id);
            Region r = out.addRegion();
            r.name = name(f, i);
            r.tension = tension.regional(i);
            r.high = r.tension >= FactionTension.HIGH;
            r.yardRise = tension.tuning().priceRise * r.tension / FactionTension.MAX;
            r.combatJobs = tension.combatJobs(i);
        }
    }

    private static String name(Factions f, int i) {
        String n = f.name(i);
        return n == null ? "" : n;
    }
}
