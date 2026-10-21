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
 * sponsorable = true               # the player may choose to serve it in a new game
 * rank_names = ["Cadet", ...]      # its names for the rank ladder's steps, lowest first
 * home = "Highport"                # the station a career serving it starts docked at
 * crews_keep_clear_of_fire = true  # its crews leave raked tiles alone at battle stations
 * crews_reset_power_first = false  # its crews reset a shorted reactor or conduit before any other job
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
    /** The player may choose to serve it in a new game; the data's sponsor always may. */
    public boolean sponsorable;
    /** Its own names for the rank ladder's steps, lowest first; missing ones keep the ladder's names. */
    public List<String> rankNames = new ArrayList<>();
    /** The station a career serving it starts docked at; null for its first station. */
    public String home;
    /** Its crews keep clear of where the fire fell at battle stations, leaving the jobs there till the fight ends. */
    public boolean crewsKeepClearOfFire = true;
    /** Its crews reset a shorted reactor or conduit ahead of first aid, breaches and every other job but abandoning ship. */
    public boolean crewsResetPowerFirst;
}
