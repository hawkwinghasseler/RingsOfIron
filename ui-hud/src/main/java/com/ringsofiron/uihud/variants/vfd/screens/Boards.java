package com.ringsofiron.uihud.variants.vfd.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.CareerProvider.Career;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the classic windows' providers say, read at once for every page of a FILAMENT terminal: the
 * market, the yard, the shipyard, the mission board, boarding and the crew, as the classic windows
 * read them, and the career for the captain's record, a few times a second while a page shows and straight after each order. With them what
 * the terminal's title row and rail show on every page: the station docked at, its owner, the player's
 * standing there as the game's signed number, the services it offers, the tanks, the infirmary's
 * record of the docking, and the held deliveries this market can supply.
 */
public final class Boards {
    public final TradeBoard trade = new TradeBoard();
    public final YardBoard yard = new YardBoard();
    public final ShipyardProvider.Yard shipyard = new ShipyardProvider.Yard();
    public final MissionBoard missions = new MissionBoard();
    public final BoardingBoard boarding = new BoardingBoard();
    public final CrewSheet crew = new CrewSheet();
    /** The captain's career: rank, pay, the record, the standing with every faction and the strain on their stations. */
    public final Career career = new Career();
    /** The data layer read from, for the providers' orders. */
    public HudData data;

    /** The station docked at, in capitals, and its owner; empty when not docked. */
    public String station = "", owner = "";
    /** The player's standing there, the game's signed number ("+0", "-12"); empty when there is none. The game names no levels. */
    public String standing = "";
    /** What the station offers: a market, a yard, ships for sale, contracts, crew for hire, an infirmary. */
    public boolean market, yardOffered, shipyardOffered, contracts, hiring, infirmary;
    /** The player's rank ("Lieutenant"), from the mission board's career line. */
    public String rank = "";
    /** Each tank's fluid in capitals and how full it is, 0 to 1. */
    public final Array<String> tanks = new Array<>();
    public final FloatArray tankFull = new FloatArray();
    private String tanksRead;
    /** The held deliveries this market sells the goods for, each in a line. */
    public final Array<String> deliveries = new Array<>();
    /**
     * The infirmary's record of the docking: the crew it is treating now, by name, and how many it has
     * treated since the ship docked and what it billed for them, credits.
     */
    public final Array<String> treating = new Array<>();
    public int treated;
    public double treatedCost;

    private static final Pattern DELIVER = Pattern.compile("^Deliver (\\d+) (.+) to (.+)$");
    private static final Pattern LEFT = Pattern.compile("([0-9.,:]+ ?[a-zA-Z]*) left");

    /** Reads every provider again. */
    public void refresh(HudData d) {
        data = d;
        trade.clear();
        if (d.trade != null) d.trade.collect(trade);
        yard.clear();
        if (d.yard != null) d.yard.collect(yard);
        shipyard.clear();
        if (d.shipyard != null) d.shipyard.collect(shipyard);
        missions.clear();
        if (d.missions != null) d.missions.collect(missions);
        boarding.clear();
        if (d.boarding != null) d.boarding.collect(boarding);
        crew.clear();
        if (d.crew != null) d.crew.collect(crew);
        career.clear();
        if (d.career != null) d.career.read(career);
        StationInfo st = d.navReadout.docked;
        String name = st.present && !st.name.isEmpty() ? st.name : !trade.stationName.isEmpty() ? trade.stationName
                : !missions.stationName.isEmpty() ? missions.stationName : !yard.stationName.isEmpty() ? yard.stationName : "";
        station = name.toUpperCase(Locale.ROOT);
        owner = st.present ? st.owner.toUpperCase(Locale.ROOT) : "";
        standing = !st.present || Float.isNaN(st.standing) ? "" : String.format(Locale.ROOT, "%+d", Math.round(st.standing));
        market = st.present ? st.market : trade.isDocked();
        yardOffered = st.present ? st.yard : !yard.stationName.isEmpty();
        shipyardOffered = st.present ? st.shipyard : !shipyard.stationName.isEmpty();
        contracts = st.present ? st.contracts : !missions.stationName.isEmpty();
        hiring = st.present ? st.hiring : !crew.stationName.isEmpty();
        infirmary = st.present && st.infirmary;
        rank = rank(missions.career);
        tanks(yard.tanks);
        deliveries();
        treating.clear();
        treated = 0;
        treatedCost = 0;
        if (infirmary) {
            treating.addAll(st.treating);
            treated = st.treated;
            treatedCost = st.treatedCost;
        }
    }

    /** Whether the ship is docked at a station. */
    public boolean docked() {
        return !station.isEmpty();
    }

    /** "Lieutenant, Terran Patrol Authority.  Standing with ..." to "Lieutenant, Terran Patrol Authority". */
    static String rank(String career) {
        if (career == null) return "";
        int at = career.indexOf("Standing with ");
        String r = (at < 0 ? career : career.substring(0, at)).trim();
        if (r.endsWith(".")) r = r.substring(0, r.length() - 1);
        return r;
    }

    /** "Propellant 96%, oxygen 100%" into each tank and its share. */
    private void tanks(String line) {
        if (line == null) line = "";
        if (line.equals(tanksRead)) return;
        tanksRead = line;
        tanks.clear();
        tankFull.clear();
        for (String part : line.split(",")) {
            String s = part.trim();
            int sp = s.lastIndexOf(' ');
            if (sp <= 0 || !s.endsWith("%")) continue;
            try {
                float f = Float.parseFloat(s.substring(sp + 1, s.length() - 1)) / 100f;
                tanks.add(s.substring(0, sp).toUpperCase(Locale.ROOT));
                tankFull.add(Math.max(0f, Math.min(1f, f)));
            } catch (NumberFormatException ignored) {
                // Not a tank's share: left out
            }
        }
    }

    /**
     * The held deliveries whose goods this market sells, as the spec puts them under the table:
     * "DELIVER 28 FOOD → SHACKLETON ICE · 12 OF 28 ABOARD · 5.2 D LEFT".
     */
    private void deliveries() {
        deliveries.clear();
        if (!trade.isDocked()) return;
        for (int i = 0; i < missions.held.size; i++) {
            Job j = missions.held.get(i);
            Matcher m = DELIVER.matcher(j.title);
            if (!m.matches()) continue;
            TradeLine line = line(m.group(2));
            if (line == null || line.available <= 0 && line.buyPrice <= 0) continue;
            StringBuilder sb = new StringBuilder();
            sb.append("DELIVER ").append(m.group(1)).append(' ').append(m.group(2)).append(" → ").append(m.group(3));
            sb.append(" · ").append(Glass.units(line.held)).append(" OF ").append(m.group(1)).append(" ABOARD");
            Matcher left = LEFT.matcher(j.terms);
            if (left.find()) sb.append(" · ").append(left.group(1)).append(" LEFT");
            deliveries.add(sb.toString().toUpperCase(Locale.ROOT));
        }
    }

    /** The market's line for the commodity named {@code name}, or null. */
    public TradeLine line(String name) {
        for (int i = 0; i < trade.lines.size; i++) {
            TradeLine l = trade.lines.get(i);
            if (l.name.equalsIgnoreCase(name) || l.commodityId.equalsIgnoreCase(name)) return l;
        }
        return null;
    }
}
