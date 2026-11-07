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
    /** A station type's name ("Ice Mine") by its id, or null; none by default. */
    private final java.util.function.Function<String, String> typeNames;
    private final java.util.Map<String, String> typeNameCache = new java.util.HashMap<>();

    StationServices(MacroSimulation macroSim, RailsUniverse universe, FactionStanding standing, DockingSystem docking, Shipyard shipyard) {
        this(macroSim, universe, standing, docking, shipyard, id -> null);
    }

    StationServices(MacroSimulation macroSim, RailsUniverse universe, FactionStanding standing, DockingSystem docking, Shipyard shipyard,
                    java.util.function.Function<String, String> typeNames) {
        this.macroSim = macroSim;
        this.universe = universe;
        this.standing = standing;
        this.docking = docking;
        this.shipyard = shipyard;
        this.typeNames = typeNames;
    }

    /**
     * The {@code station_type} defs' names, from the content loaded into {@code world}: null before it
     * loads, "" for a type it has no name for.
     */
    static java.util.function.Function<String, String> typeNames(GameWorld world) {
        return id -> {
            com.ringsofiron.moddingdata.ModContext content = world.getContent();
            if (content == null || content.defs == null) return null;
            com.ringsofiron.moddingdata.defs.StationTypeDef def = id == null || !content.defs.has("station_type", id) ? null
                    : content.defs.get("station_type", id, com.ringsofiron.moddingdata.defs.StationTypeDef.class);
            return def == null || def.name == null ? "" : def.name;
        };
    }

    /** {@code station} as the player's {@code ship} would find it, into {@code out}. */
    void fill(StationInfo out, Entity station, Entity ship) {
        out.present = true;
        out.name = ShipNames.name(station);
        Factions factions = standing.factions();
        int faction = standing.factionOf(station);
        FactionDef owner = factions.def(faction);
        out.owner = owner == null || owner.name == null ? "" : owner.name;
        out.type = type(station);
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

    /** What kind of station it is, by its type's name ("Ice Mine"); "" when its type has none. */
    private String type(Entity station) {
        com.ringsofiron.stationsdocking.StationComponent sc = station.getComponent(com.ringsofiron.stationsdocking.StationComponent.class);
        if (sc == null || sc.typeId == null) return "";
        String name = typeNameCache.get(sc.typeId);
        if (name == null) {
            String found = typeNames.apply(sc.typeId);
            name = found == null ? "" : found;
            // Kept once there is content to ask; before it loads, asked again next time
            if (found != null) typeNameCache.put(sc.typeId, name);
        }
        return name;
    }

    /** The player's standing as a word: "Hostile", "Unwelcome", "Neutral", "Friendly", "Trusted". */
    static String word(float standing, boolean hostileToAll) {
        if (hostileToAll || standing <= FactionStanding.HOSTILE_AT) return "Hostile";
        if (standing < FactionStanding.REFUSE_BELOW) return "Unwelcome";
        if (standing < FRIENDLY_AT) return "Neutral";
        return standing < TRUSTED_AT ? "Friendly" : "Trusted";
    }
}
