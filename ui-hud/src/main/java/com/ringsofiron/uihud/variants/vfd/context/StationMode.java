package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * M3, the station's terminal (functional-context 10.3; mockup 11.10): a panel 1,120 wide over the
 * docked view, as tall as its tab, with the Market, the Yard, the Shipyard, the Missions and Hire,
 * each only where the station offers it, and the rail on the right of every tab. Docked only; with a
 * prize in tow off a station that buys it, the Shipyard alone.
 */
final class StationMode extends Mode {
    static final float W = 1120f, Y = 40f, LEFT_W = 792f, RAIL_GAP = 8f;

    final MarketTab market = new MarketTab();
    final YardTab yard = new YardTab();
    final ShipyardTab shipyard = new ShipyardTab();
    final MissionsTab missions = new MissionsTab();
    final HireTab hire = new HireTab();
    final StationRail rail = new StationRail();

    StationMode() {
        super(true);
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {market, yard, shipyard, missions, hire};
    }

    @Override
    boolean available(Boards b, HudData d) {
        return b.docked() || !b.shipyard.prize.isEmpty();
    }

    @Override
    void title(Boards b, HudData d) {
        String name = b.station;
        if (name.isEmpty()) name = prizeStation(b.shipyard.prize);
        frame.title = name;
        frame.sub = b.owner;
    }

    /** "Raider 'Vex' (taken), off Highport" to "HIGHPORT". */
    static String prizeStation(String prize) {
        int at = prize.lastIndexOf(", off ");
        return at < 0 ? "" : prize.substring(at + 6).trim().toUpperCase(Locale.ROOT);
    }

    @Override
    void refresh(Boards b, HudData d) {
        super.refresh(b, d);
        rail.refresh(b);
    }

    @Override
    void place(float w, float h) {
        float x = (w - W) / 2f;
        float want = Math.max(tab == null ? 0f : tab.wantedHeight(LEFT_W), rail.wantedHeight());
        float most = h - Y - 12f;
        frame.place(x, Y, W, Math.min(most, want + ModeFrame.chrome(true)));
    }

    @Override
    float contentWidth() {
        return LEFT_W;
    }

    @Override
    void paint(Filament f, float time) {
        super.paint(f, time);
        float rx = frame.x + LEFT_W + RAIL_GAP;
        rail.place(rx, frame.top(), frame.x + frame.w - rx, frame.bottom() - frame.top());
        rail.paint(f);
    }
}
