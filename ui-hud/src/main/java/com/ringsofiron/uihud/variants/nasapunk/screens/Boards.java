package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
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
 * What the classic windows' providers say, read at once for every page of a screen: the market, the
 * yard, the shipyard, the mission board, boarding and the crew, as the classic windows read them, a
 * few times a second while a screen shows and straight after each order. With them what a screen's
 * header and rail show on every page: the station docked at, its owner, the player's standing there,
 * the services it offers, the tanks, the berths, and the held deliveries this market can supply.
 */
public final class Boards {
    public final TradeBoard trade = new TradeBoard();
    public final YardBoard yard = new YardBoard();
    public final ShipyardProvider.Yard shipyard = new ShipyardProvider.Yard();
    public final MissionBoard missions = new MissionBoard();
    public final BoardingBoard boarding = new BoardingBoard();
    public final CrewSheet crew = new CrewSheet();
    /** The data layer read from. */
    public HudData data;

    /** The station docked at, in capitals, and its owner; empty when not docked. */
    public String station = "", owner = "";
    /** The player's standing there as the game's signed number ("+0"), and its word if the game names one; empty when there is none. */
    public String standing = "", standingWord = "";
    /** Its owner sponsors the player's career. */
    public boolean sponsor;
    /** What the station offers: a market, a yard, ships for sale, contracts, crew for hire, an infirmary. */
    public boolean market, yardOffered, shipyardOffered, contracts, hiring, infirmary;
    /** The player's rank and service ("Lieutenant, Terran Patrol Authority"), from the mission board's career line. */
    public String rank = "";
    /** Each tank's fluid in capitals and how full it is, 0 to 1. */
    public final Array<String> tanks = new Array<>();
    public final FloatArray tankFull = new FloatArray();
    private String tanksRead;
    /** The held deliveries whose goods this market sells. */
    public final Array<Delivery> deliveries = new Array<>();
    /** Crew aboard who are hurt: an infirmary treats them while the ship is docked. */
    public int wounded;
    /** Berths aboard and those taken, from the crew sheet's summary; −1 when it doesn't say. */
    public int berths = -1, berthsTaken = -1;

    /** A held contract to deliver goods this market sells: what, how many, where to, and how long is left. */
    public static final class Delivery {
        public Job job;
        public TradeLine line;
        public int units;
        public String commodity = "", to = "", left = "";
    }

    private static final Pattern DELIVER = Pattern.compile("^Deliver (\\d+) (.+?) to (.+)$");
    private static final Pattern LEFT = Pattern.compile("([0-9][0-9.,:]* ?[a-zA-Z]*(?: [0-9]+ ?[a-zA-Z]+)?) left");
    private static final Pattern BERTHS = Pattern.compile("(\\d+) ?/ ?(\\d+) berths");

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
        StationInfo st = d.navReadout.docked;
        String name = st.present && !st.name.isEmpty() ? st.name : !trade.stationName.isEmpty() ? trade.stationName
                : !missions.stationName.isEmpty() ? missions.stationName : !yard.stationName.isEmpty() ? yard.stationName
                : !shipyard.stationName.isEmpty() ? shipyard.stationName : "";
        station = name.toUpperCase(Locale.ROOT);
        owner = st.present ? st.owner.toUpperCase(Locale.ROOT) : "";
        sponsor = st.present && st.sponsor;
        standingWord = st.present ? st.standingName.toUpperCase(Locale.ROOT) : "";
        standing = !st.present || Float.isNaN(st.standing) ? "" : Fmt.signed(st.standing);
        market = st.present ? st.market : trade.isDocked();
        yardOffered = st.present ? st.yard : !yard.stationName.isEmpty();
        shipyardOffered = st.present ? st.shipyard : !shipyard.stationName.isEmpty();
        contracts = st.present ? st.contracts : !missions.stationName.isEmpty();
        hiring = st.present ? st.hiring : !crew.stationName.isEmpty();
        infirmary = st.present && st.infirmary;
        rank = rank(missions.career);
        tanks(yard.tanks);
        deliveries();
        wounded = 0;
        for (int i = 0; i < crew.crew.size; i++) if (hurt(crew.crew.get(i))) wounded++;
        berths = berthsTaken = -1;
        Matcher m = BERTHS.matcher(crew.summary);
        if (m.find()) {
            berthsTaken = Integer.parseInt(m.group(1));
            berths = Integer.parseInt(m.group(2));
        }
    }

    /** Whether the ship is docked at a station. */
    public boolean docked() {
        return !station.isEmpty();
    }

    /** Berths free aboard, or −1 when the sheet doesn't say. */
    public int berthsFree() {
        return berths < 0 ? -1 : Math.max(0, berths - berthsTaken);
    }

    /** Whether crew member {@code m} is hurt: wounded or out cold. */
    public static boolean hurt(Member m) {
        String c = m.condition.toLowerCase(Locale.ROOT);
        return c.contains("wound") || c.contains("unconscious");
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
                // not a tank's share: left out
            }
        }
    }

    /** The held deliveries whose goods this market sells or holds. */
    private void deliveries() {
        deliveries.clear();
        if (!trade.isDocked()) return;
        for (int i = 0; i < missions.held.size; i++) {
            Job j = missions.held.get(i);
            Matcher m = DELIVER.matcher(j.title);
            if (!m.matches()) continue;
            TradeLine line = line(m.group(2));
            if (line == null || line.available <= 0 && line.buyPrice <= 0 && line.held <= 0) continue;
            Delivery dl = new Delivery();
            dl.job = j;
            dl.line = line;
            dl.units = Integer.parseInt(m.group(1));
            dl.commodity = m.group(2).toUpperCase(Locale.ROOT);
            dl.to = m.group(3).toUpperCase(Locale.ROOT);
            Matcher left = LEFT.matcher(j.terms);
            dl.left = left.find() ? left.group(1).toUpperCase(Locale.ROOT) + " LEFT" : "";
            deliveries.add(dl);
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
