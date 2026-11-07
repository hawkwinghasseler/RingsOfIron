package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * M2, the ship (functional-context 10.2; mockup 11.11): the whole screen under the rack, with the
 * Systems, the Crew, the Cargo and the Loadout. It stands for the crew window, whichever tab shows, so
 * I opens it at the tab used last (the Crew the first time, the roster the crew window was) and closes
 * it from any of them. Away from a yard it stands for the yard window too while the Loadout shows,
 * since the Loadout is the crew's field refit then: Y opens it there and closes it from there.
 */
final class ShipMode extends Mode {
    /** The frame's margin from the stage's sides and foot, and its top, under the rack. */
    static final float MARGIN = 12f, Y = 40f;

    final SystemsTab systems = new SystemsTab();
    final CrewTab crew = new CrewTab();
    final CargoTab cargo = new CargoTab();
    final LoadoutTab loadout = new LoadoutTab();
    /** The tab I opens it at: the one used last. */
    private ModeTab last = crew;

    ShipMode() {
        super(true);
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {systems, crew, cargo, loadout};
    }

    @Override
    boolean available(Boards b, HudData d) {
        return d.ctx.ship != null;
    }

    @Override
    ModeTab tabFor(ClassicWindow window) {
        if (window == ClassicWindow.CREW) return last;
        return super.tabFor(window);
    }

    @Override
    boolean showing(ClassicWindow window) {
        if (tab == null) return false;
        if (window == ClassicWindow.CREW) return true;
        return window == ClassicWindow.YARD && tab == loadout && modes != null && modes.boards.yard.field;
    }

    @Override
    void turn(ModeTab t) {
        super.turn(t);
        last = t;
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = b.crew.shipName.toUpperCase(Locale.ROOT);
        frame.sub = "";
    }

    /** I closes it from every tab; Y only from the field refit, so the foot names I. */
    @Override
    String closeKey() {
        return "I";
    }

    @Override
    void place(float w, float h) {
        frame.place(MARGIN, Y, w - 2f * MARGIN, h - Y - MARGIN);
    }
}
