package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * M1, the map (functional-context 10.1; mockup cx-map): the mode's frame over the whole screen under
 * the rack, titled with the system the map shows ("EARTH SYSTEM"), its plate cut through where the
 * game's orbit canvas shows between the panes. One tab and no tab keys, so the number keys keep the
 * helm's orders. M opens and closes it, as Esc closes it.
 */
final class MapMode extends Mode {
    final MapTab tab0 = new MapTab();
    /** The game's key for the map, for the foot. */
    private String mapKey = "M";

    MapMode() {
        super(false);
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {tab0};
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = tab0.body.isEmpty() ? "MAP" : tab0.body + " SYSTEM";
        frame.sub = "";
        String k = MapPlan.cap(d.key("MAP"));
        mapKey = k.isEmpty() ? "M" : k;
    }

    @Override
    String closeKey() {
        return mapKey;
    }

    @Override
    void place(float w, float h) {
        frame.place(ShipMode.MARGIN, ShipMode.Y, w - 2f * ShipMode.MARGIN, h - ShipMode.Y - ShipMode.MARGIN);
    }

    @Override
    void layout(float stageW, float stageH) {
        super.layout(stageW, stageH);
        // The window through the plate: the plot, where the canvas shows
        frame.hole[0] = tab0.px;
        frame.hole[1] = tab0.py;
        frame.hole[2] = tab0.pw;
        frame.hole[3] = tab0.ph;
    }

    /** The map's keys are the game's: none is the mode's. */
    @Override
    boolean key(int keycode) {
        return false;
    }

    /** The plate round the plot takes the clicks that miss the panes; the plot's are the map's. */
    @Override
    void boxes(FilamentActor a) {
        float[] o = frame.hole;
        float right = o[0] + o[2], bottom = o[1] + o[3];
        a.box(Modes.PLATE, frame.x, frame.y, frame.w, o[1] - frame.y);
        a.box(Modes.PLATE, frame.x, bottom, frame.w, frame.y + frame.h - bottom);
        a.box(Modes.PLATE, frame.x, o[1], o[0] - frame.x, o[3]);
        a.box(Modes.PLATE, right, o[1], frame.x + frame.w - right, o[3]);
        frame.boxes(a);
        tab0.boxes(a);
    }
}
