package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a faction sells ships. {@code shipyard.<id>}. A station sells the faction's
 * {@link ShipSaleDef ship sales} when the faction owns it and it is one of these stations or of one
 * of these station types.
 * <pre>
 * [shipyard.patrol]
 * faction = "patrol"               # whose ships it sells
 * station_types = ["shipyard"]     # every station of these types (economy.json) the faction owns
 * stations = ["Highport"]          # and these stations, by name
 * </pre>
 */
public class ShipyardDef {
    public String id;
    public String faction;
    public List<String> stationTypes = new ArrayList<>();
    public List<String> stations = new ArrayList<>();
}
