package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.uihud.providers.MissionProvider;

import java.util.Locale;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * ui-hud's mission window from the {@link ContractBoard}: the offers on the board of the station the
 * player's ship is docked at with clearance, and the contracts the player holds.
 */
public class ContractMissions implements MissionProvider {
    private final GameContext ctx;
    private final DockingSystem docking;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final FactionTension tension;
    private final ContractBoard contracts;

    public ContractMissions(GameContext ctx, DockingSystem docking, MacroSimulation macroSim, FactionStanding standing, FactionTension tension, ContractBoard contracts) {
        this.ctx = ctx;
        this.docking = docking;
        this.macroSim = macroSim;
        this.standing = standing;
        this.tension = tension;
        this.contracts = contracts;
    }

    @Override
    public void collect(MissionBoard out) {
        ContractBoard board = contracts;
        Entity station = dockedStation();
        if (station != null) {
            String uuid = station.getComponent(UuidComponent.class).id;
            var node = macroSim.station(uuid);
            out.stationName = node == null || node.displayName == null ? "Station" : node.displayName;
            FactionStanding s = standing;
            int faction = s.factionOf(station);
            out.career = String.format(Locale.ROOT, "%s.  Standing with %s %+.0f", s.describeRank(),
                    s.factions().name(faction), s.standing(faction));
            out.tension = tension.describe(faction);
            for (Contract c : board.offers(uuid)) {
                Job j = out.addOffer();
                j.id = c.id;
                j.title = c.title();
                j.giver = s.factions().name(s.factions().index(c.giver));
                j.terms = terms(c, RailsTrafficMap.formatEta(c.deadline - ctx.engine.getSimSeconds()));
                String locked = board.locked(c);
                j.locked = locked == null ? "" : locked;
                j.note = c.reason == null ? "" : c.reason;
            }
        }
        for (Contract c : board.active()) {
            Job j = out.addHeld();
            j.id = c.id;
            j.title = c.heading();
            j.giver = standing.factions().name(standing.factions().index(c.giver));
            j.terms = terms(c, board.progress(c));
        }
    }

    /**
     * "1,500 cr, +4 standing, 2.0 d"; a delivery also names the price per unit, a convoy or defence
     * that its pay is shared out by how it goes, an operation its steps and that it pays at the end.
     */
    static String terms(Contract c, String time) {
        String pay = String.format(Locale.ROOT, "%,.0f cr", c.reward);
        if (c.type == Contract.Type.DELIVERY) pay = String.format(Locale.ROOT, "%,.1f cr/unit + %s", c.unitPrice, pay);
        if (c.type == Contract.Type.CONVOY || c.type == Contract.Type.DEFEND) pay = "up to " + pay;
        if (c.type == Contract.Type.OPERATION) {
            return String.format(Locale.ROOT, "%d steps, then %s, %+.0f standing", c.steps, pay, c.standing);
        }
        return String.format(Locale.ROOT, "%s, %+.0f standing, %s", pay, c.standing, time);
    }

    @Override
    public String accept(long id) {
        String why = contracts.accept(id);
        return why == null ? "Contract taken" : why;
    }

    @Override
    public String abandon(long id) {
        String why = contracts.abandon(id);
        return why == null ? "Contract abandoned" : why;
    }

    /** The station the player's ship is docked at with clearance, or null. */
    private Entity dockedStation() {
        Entity player = ctx.player();
        if (player == null || !docking.dockedWithClearance(player)) return null;
        Entity station = docking.dockedTo(player);
        return station == null || station.getComponent(UuidComponent.class) == null
                || macroSim.station(station.getComponent(UuidComponent.class).id) == null ? null : station;
    }
}
