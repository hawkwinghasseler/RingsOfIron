package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;

import java.util.Locale;

/**
 * What the classic windows' providers say, read once for every page of the consoles: the market, the
 * yard, the shipyard, the mission board, boarding and the crew. Read a few times a second while a
 * console shows and straight after an order, as the classic windows read them. Also what the station's
 * console shows on its head and rail, taken from them: the station's name, its owner and badge, the
 * player's standing there, and the tanks.
 */
final class Boards {
    final TradeBoard trade = new TradeBoard();
    final YardBoard yard = new YardBoard();
    final ShipyardProvider.Yard shipyard = new ShipyardProvider.Yard();
    final MissionBoard missions = new MissionBoard();
    final BoardingBoard boarding = new BoardingBoard();
    final CrewSheet crew = new CrewSheet();
    HudData data;

    /** The station docked at, its owner's name and initials; empty when not docked. */
    String station = "", owner = "", badge = "";
    /** The same in capitals, as the console's head engraves them. */
    String stationCaps = "", ownerCaps = "";
    private String stationRead = "";
    /** The player's ship's name in capitals, for the ship's console's flaps; empty with no ship. */
    String shipCaps = "";
    private String shipRead = "";
    /** The player's rank ("Lieutenant, Terran Patrol Authority") and standing with the station's owner. */
    String rank = "";
    /** The rank in capitals, as the slate's title bar engraves it. */
    String rankCaps = "";
    int standing;
    boolean hasStanding;
    /** Each tank's fluid and how full it is (0 to 1), from the yard's line. */
    final Array<String> tanks = new Array<>();
    final FloatArray tankFull = new FloatArray();
    private String tanksRead;
    private String careerRead;

    void refresh(HudData d) {
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
        station = !trade.stationName.isEmpty() ? trade.stationName : !missions.stationName.isEmpty() ? missions.stationName
                : !yard.stationName.isEmpty() ? yard.stationName : crew.stationName;
        career(missions.career);
        tanks(yard.tanks);
        if (!station.equals(stationRead)) {
            stationRead = station;
            stationCaps = station.toUpperCase(Locale.ROOT);
        }
        if (!crew.shipName.equals(shipRead)) {
            shipRead = crew.shipName;
            shipCaps = shipRead.toUpperCase(Locale.ROOT);
        }
    }

    /** "Lieutenant, Terran Patrol Authority.  Standing with Terran Patrol Authority +42", as the board puts it. */
    private void career(String line) {
        if (line == null) line = "";
        if (line.equals(careerRead)) return;
        careerRead = line;
        owner = "";
        ownerCaps = "";
        badge = "";
        hasStanding = false;
        // Away from a station the board has no career line: the captain keeps the rank last read, for the slate
        if (line.isEmpty()) return;
        rank = line;
        rankCaps = line.toUpperCase(Locale.ROOT);
        int at = line.indexOf("Standing with ");
        if (at < 0) return;
        rank = line.substring(0, at).trim();
        if (rank.endsWith(".")) rank = rank.substring(0, rank.length() - 1);
        rankCaps = rank.toUpperCase(Locale.ROOT);
        String rest = line.substring(at + "Standing with ".length()).trim();
        int sp = rest.lastIndexOf(' ');
        if (sp <= 0) return;
        owner = rest.substring(0, sp).trim();
        try {
            standing = Math.round(Float.parseFloat(rest.substring(sp + 1).replace("+", "")));
            hasStanding = true;
        } catch (NumberFormatException e) {
            hasStanding = false;
        }
        badge = initials(owner);
        ownerCaps = owner.toUpperCase(Locale.ROOT);
    }

    /** "TPA" for the Terran Patrol Authority: the capitals of its words, short words left out. */
    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (w.length() <= 3 && Character.isLowerCase(w.charAt(0))) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (sb.length() == 4) break;
        }
        return sb.toString();
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

    /** Whether the ship is docked at a station. */
    boolean docked() {
        return !station.isEmpty();
    }
}
