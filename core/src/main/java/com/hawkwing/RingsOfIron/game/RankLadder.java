package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.moddingdata.defs.ShipDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The sponsor's ranks from the data ({@code [rank.<id>]}), lowest first: the standing each needs,
 * the budget it pays each in-game week, and the largest chassis it may requisition a ship of.
 * With no rank data there is one rank with no budget and no limit.
 */
public final class RankLadder {
    private final List<RankDef> ranks;
    private final DefRegistry defs;

    private RankLadder(List<RankDef> ranks, DefRegistry defs) {
        this.ranks = Collections.unmodifiableList(ranks);
        this.defs = defs;
    }

    public static RankLadder from(DefRegistry defs) {
        List<RankDef> all = new ArrayList<>(defs.all("rank", RankDef.class));
        if (all.isEmpty()) return none();
        all.sort(Comparator.comparingInt((RankDef r) -> r.order).thenComparingDouble(r -> r.standing));
        return new RankLadder(all, defs);
    }

    public static RankLadder none() {
        RankDef only = new RankDef();
        only.id = "officer";
        only.name = "Officer";
        return new RankLadder(new ArrayList<>(List.of(only)), null);
    }

    public int size() {
        return ranks.size();
    }

    /** Rank {@code i}, held within the ladder. */
    public RankDef get(int i) {
        return ranks.get(Math.max(0, Math.min(ranks.size() - 1, i)));
    }

    /** The highest rank {@code standing} earns. */
    public int earnedBy(float standing) {
        int best = 0;
        for (int i = 0; i < ranks.size(); i++) {
            if (standing >= ranks.get(i).standing) best = i;
        }
        return best;
    }

    /**
     * Whether rank {@code i} may requisition a ship of {@code shipId}: its chassis has no more
     * tiles than the rank's {@code max_chassis}. A rank with no limit, or a ship or limit the data
     * cannot size, allows it.
     */
    public boolean mayRequisition(int i, String shipId) {
        RankDef rank = get(i);
        if (defs == null || rank.maxChassis == null) return true;
        ShipDef ship = defs.get("ship", shipId, ShipDef.class);
        int size = ship == null ? -1 : chassisSize(ship.chassis);
        int limit = chassisSize(rank.maxChassis);
        return size < 0 || limit < 0 || size <= limit;
    }

    /** Tiles of chassis {@code id} that are not void, or -1 if there is no such chassis. */
    int chassisSize(String id) {
        ChassisDef c = id == null ? null : defs.get("chassis", id, ChassisDef.class);
        if (c == null || c.rows == null) return -1;
        int n = 0;
        for (String row : c.rows) {
            for (int k = 0; k < row.length(); k++) {
                if (row.charAt(k) != ' ') n++;
            }
        }
        return n;
    }
}
