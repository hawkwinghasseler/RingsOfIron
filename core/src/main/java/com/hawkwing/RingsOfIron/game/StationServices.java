package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.Factions;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.shipops.ShipNames;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;

/**
 * What a station is to the player, for the navigation readout: who runs it, how they stand with
 * the player, whether it would give clearance, and what it offers. A station run by a faction
 * hostile to all offers nothing; every other station has a yard and an infirmary, and a market,
 * a contract board and crew for hire when it trades.
 */
final class StationServices {
    /** Standing at or above these reads as friendly and trusted; below {@link FactionStanding#REFUSE_BELOW}, unwelcome. */
    static final float FRIENDLY_AT = 15f, TRUSTED_AT = 40f;

    private final MacroSimulation macroSim;
    private final RailsUniverse universe;
    private final FactionStanding standing;
    private final DockingSystem docking;
    private final Shipyard shipyard;

    StationServices(MacroSimulation macroSim, RailsUniverse universe, FactionStanding standing, DockingSystem docking, Shipyard shipyard) {
        this.macroSim = macroSim;
        this.universe = universe;
        this.standing = standing;
        this.docking = docking;
        this.shipyard = shipyard;
    }

    /** {@code station} as the player's {@code ship} would find it, into {@code out}. */
    void fill(StationInfo out, Entity station, Entity ship) {
        out.present = true;
        out.name = ShipNames.name(station);
        Factions factions = standing.factions();
        int faction = standing.factionOf(station);
        FactionDef owner = factions.def(faction);
        out.owner = owner == null || owner.name == null ? "" : owner.name;
        out.sponsor = owner != null && faction == standing.sponsor();
        if (owner != null) {
            out.standing = standing.standing(faction);
            out.standingName = word(out.standing, owner.hostileToAll);
        }
        String refused = docking.clearance.refuse(station, ship);
        out.refusal = refused == null ? "" : refused.isEmpty() ? "refuses clearance" : refused;
        boolean open = owner == null || !owner.hostileToAll;
        String uuid = universe.uuidOf(station);
        boolean trades = uuid != null && macroSim.station(uuid) != null;
        out.market = open && trades;
        out.contracts = open && trades;
        out.hiring = open && trades;
        out.yard = open;
        out.infirmary = open;
        out.shipyard = open && shipyard.sellsShips(station);
    }

    /** The player's standing as a word: "Hostile", "Unwelcome", "Neutral", "Friendly", "Trusted". */
    static String word(float standing, boolean hostileToAll) {
        if (hostileToAll || standing <= FactionStanding.HOSTILE_AT) return "Hostile";
        if (standing < FactionStanding.REFUSE_BELOW) return "Unwelcome";
        if (standing < FRIENDLY_AT) return "Neutral";
        return standing < TRUSTED_AT ? "Friendly" : "Trusted";
    }
}
