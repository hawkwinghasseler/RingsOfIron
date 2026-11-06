package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * M5, the captain's record (functional-context 10.5; mockup cx-record): the captain's own book, so it
 * is the slate, across the screen under the rack ({@link ModeFrame#slate}), with the Career, the
 * Standing, the Contracts and the Log.
 *
 * <p>It stands for the log window on every tab, so the game treats it as it does the log: the flight
 * and deck keys wait while it shows, and Esc closes it rather than pausing. L opens it at the Log and
 * closes it from there, and turns to the Log from the other tabs, as T, Y and J do in the station's
 * terminal. Away from a station's board J opens it at the Contracts and closes it from there, as the
 * game's mission window held the contracts there; at a board, J is the board, in the station's terminal.
 */
final class RecordMode extends Mode {
    /** The frame's margin from the stage's sides and foot, and its top, under the rack. */
    static final float MARGIN = 12f, Y = 40f;

    final CareerTab career = new CareerTab();
    final StandingTab standing = new StandingTab();
    final ContractsTab contracts = new ContractsTab();
    final LogTab log = new LogTab();

    RecordMode() {
        super(true);
        frame.slate = true;
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {career, standing, contracts, log};
    }

    @Override
    ModeTab tabFor(ClassicWindow window) {
        if (window == ClassicWindow.LOG) return log;
        if (window == ClassicWindow.MISSIONS) return contracts;
        return null;
    }

    @Override
    boolean showing(ClassicWindow window) {
        if (tab == null) return false;
        if (window == ClassicWindow.LOG) return true;
        // Only while the game has a mission window to stand for (contracts held): it closes one it can't open
        return window == ClassicWindow.MISSIONS && tab == contracts && modes != null && modes.available(ClassicWindow.MISSIONS);
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = "CAPTAIN'S RECORD";
        frame.sub = "";
    }

    @Override
    void place(float w, float h) {
        frame.place(MARGIN, Y, w - 2f * MARGIN, h - Y - MARGIN);
    }

    @Override
    boolean key(int keycode) {
        if (keycode == Keys.L) {
            // On the Log, L goes on to the game, which closes the log it stands for
            if (tab == log) return false;
            turn(log);
            return true;
        }
        // With no mission window to stand for, J is the record's own (with one, the game's toggles it)
        if (keycode == Keys.J && modes != null && !modes.available(ClassicWindow.MISSIONS)) {
            if (tab == contracts) modes.closeAll();
            else turn(contracts);
            return true;
        }
        return super.key(keycode);
    }
}
