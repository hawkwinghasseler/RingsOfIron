package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A power the player can serve, fight or trade with. {@code faction.<id>}.
 * <pre>
 * [faction.navy]
 * name = "Terran Navy"
 * color = "#4a90d9"                # how its ships and stations are marked
 * sponsor = true                   # the faction the player serves
 * stations = ["Highport"]          # station names it owns
 * enemies = ["pirates"]            # faction ids it is at war with (war goes both ways)
 * hostile_to_all = false           # at war with everyone, the player included
 * </pre>
 */
public class FactionDef {
    public String id;
    public String name;
    public String color = "#ffffff";
    public boolean sponsor;
    public List<String> stations = new ArrayList<>();
    public List<String> enemies = new ArrayList<>();
    public boolean hostileToAll;
}
