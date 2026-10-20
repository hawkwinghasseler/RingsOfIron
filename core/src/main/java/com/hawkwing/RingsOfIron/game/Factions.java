package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.FactionDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The factions from the data ({@code [faction.<id>]}, {@code assets/data/factions.toml}) and the
 * crew faction number each goes by. Crew and ships carry a faction as a number: {@link
 * FactionStanding#PLAYER} (0) is the player's own crew, and the data's factions take the numbers
 * from {@link #FIRST} up, the sponsor first and the rest in id order. Any other number is a crew
 * of no one's in the data (a test's boarders, say).
 *
 * <p>Every station belongs to the faction that lists it; a station none lists belongs to the
 * sponsor. With no faction data at all there is one faction, owning every station.
 */
public final class Factions {
    /** The crew faction number of the first faction in the data: the sponsor's. */
    public static final int FIRST = StationCrew.FACTION;

    private final List<FactionDef> factions;
    private final Map<String, Integer> byId = new HashMap<>();
    private final Map<String, Integer> byStation = new HashMap<>();

    private Factions(List<FactionDef> factions) {
        this.factions = Collections.unmodifiableList(factions);
        for (int i = 0; i < factions.size(); i++) {
            byId.put(factions.get(i).id, FIRST + i);
            for (String station : factions.get(i).stations) byStation.putIfAbsent(station, FIRST + i);
        }
    }

    /** The factions in {@code defs}; {@link #none()} if it has none. */
    public static Factions from(DefRegistry defs) {
        List<FactionDef> all = new ArrayList<>(defs.all("faction", FactionDef.class));
        if (all.isEmpty()) return none();
        all.sort(Comparator.comparing((FactionDef f) -> !f.sponsor).thenComparing(f -> f.id));
        return new Factions(all);
    }

    /** One faction, the sponsor, owning every station: the game before there was faction data. */
    public static Factions none() {
        FactionDef only = new FactionDef();
        only.id = "stations";
        only.name = "Station authority";
        only.sponsor = true;
        return new Factions(new ArrayList<>(List.of(only)));
    }

    /** Every faction, the sponsor first. */
    public List<FactionDef> all() {
        return factions;
    }

    /** The faction the player serves. */
    public FactionDef sponsor() {
        return factions.get(0);
    }

    /** The sponsor's crew faction number. */
    public int sponsorIndex() {
        return FIRST;
    }

    /** The crew faction number of faction {@code id}, or -1 if there is no such faction. */
    public int index(String id) {
        Integer i = id == null ? null : byId.get(id);
        return i == null ? -1 : i;
    }

    /** The faction going by crew faction number {@code index}, or null if it is none of the data's. */
    public FactionDef def(int index) {
        int i = index - FIRST;
        return i >= 0 && i < factions.size() ? factions.get(i) : null;
    }

    /** The id of the faction going by {@code index}, or null. */
    public String id(int index) {
        FactionDef f = def(index);
        return f == null ? null : f.id;
    }

    /** Its name for the screen: the faction's name, or a stand-in for a number that is none of the data's. */
    public String name(int index) {
        FactionDef f = def(index);
        if (f != null) return f.name;
        return index == FactionStanding.PLAYER ? "Your crew" : "Unaligned";
    }

    /**
     * Gives {@code board} its crew's habits from the data of the faction that works it ({@link
     * FactionDef#crewsKeepClearOfFire}); a faction that is none of the data's keeps clear. Null is ignored.
     */
    public void crewHabits(ShipTaskBoardComponent board) {
        if (board == null) return;
        FactionDef f = def(board.faction);
        board.keepsClearOfFire = f == null || f.crewsKeepClearOfFire;
    }

    /** Whether {@code index} is one of the data's factions. */
    public boolean known(int index) {
        return def(index) != null;
    }

    /** The crew faction number of the station called {@code stationName}: its owner's, else the sponsor's. */
    public int ofStation(String stationName) {
        Integer i = stationName == null ? null : byStation.get(stationName);
        return i == null ? FIRST : i;
    }

    /** Whether factions {@code a} and {@code b} of the data are at war: either lists the other, or either is hostile to all. */
    public boolean atWar(int a, int b) {
        FactionDef fa = def(a), fb = def(b);
        if (fa == null || fb == null || a == b) return false;
        return fa.hostileToAll || fb.hostileToAll || fa.enemies.contains(fb.id) || fb.enemies.contains(fa.id);
    }

    /**
     * The factions sworn against {@code index}: those that list it in their {@code enemies}, or
     * that it lists. A hostile-to-all faction's war with everyone does not count, so beating
     * pirates pleases only the factions that call them an enemy.
     */
    public List<Integer> swornEnemiesOf(int index) {
        List<Integer> out = new ArrayList<>();
        FactionDef f = def(index);
        if (f == null) return out;
        for (int i = 0; i < factions.size(); i++) {
            FactionDef o = def(FIRST + i);
            if (FIRST + i != index && (o.enemies.contains(f.id) || f.enemies.contains(o.id))) out.add(FIRST + i);
        }
        return out;
    }

    /** The factions at war with {@code index}. */
    public List<Integer> enemiesOf(int index) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < factions.size(); i++) {
            if (atWar(index, FIRST + i)) out.add(FIRST + i);
        }
        return out;
    }
}
