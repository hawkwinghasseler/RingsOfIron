package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The Station link's contract Board (functional-stations 10.3): the offers, each with its pay or LOCKED
 * (its giver, its terms, why it is locked and its note under the list), Take on the focused one; the
 * faction tension line; and at the foot the contracts held, a line that opens them on the slate.
 */
final class BoardPage extends SectionPage {
    static final int OFFER = ITEM, HOLDING = ITEM + 1;
    static final int TAKE = 0, SLATE = 1;

    private MissionBoard board;

    BoardPage() {
        super("BOARD", ClassicWindow.MISSIONS, "J");
        keys.define(2, 2);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && b.contracts && !b.missions.stationName.isEmpty();
    }

    @Override
    void build(Boards b, HudData d) {
        board = b.missions;
        int w = cells - 1;
        head(board.offers.size == 0 ? "OFFERS" : "OFFERS: " + board.offers.size);
        for (int i = 0; i < board.offers.size; i++) {
            Job j = board.offers.get(i);
            add(OFFER, i, cols.clear().left(j.title.toUpperCase(Locale.ROOT), w - 10).right(j.locked.isEmpty() ? pay(j.terms) : "LOCKED", 10)
                    .toString());
        }
        if (board.offers.size == 0) note("NO OFFERS HERE TODAY");
        if (!board.tension.isEmpty()) note(board.tension.toUpperCase(Locale.ROOT));
        head("HELD");
        add(HOLDING, 0, holding(board.held.size));
    }

    /** "Holding 2 contracts", for the line that opens them on the slate. */
    static String holding(int n) {
        if (n == 0) return "NO CONTRACTS HELD";
        return n == 1 ? "HOLDING 1 CONTRACT, ON THE SLATE" : "HOLDING " + n + " CONTRACTS, ON THE SLATE";
    }

    /** "3,000 cr, +5 standing, 2.0 d" to "3,000 CR": up to the unit, since the pay has its own commas. */
    static String pay(String terms) {
        if (terms == null) return "";
        int cr = terms.indexOf(" cr");
        return cr > 0 ? terms.substring(0, cr + 3).trim().toUpperCase(Locale.ROOT) : "";
    }

    @Override
    void keysFor(int focus) {
        keys.set(TAKE, "TAKE", false).set(SLATE, "SLATE", false);
        if (board == null) return;
        int k = kindAt(focus);
        if (k == OFFER) keys.set(TAKE, "TAKE", board.offers.get(ref.get(focus)).locked.isEmpty());
        else if (k == HOLDING) keys.set(SLATE, "SLATE", board.held.size > 0);
    }

    @Override
    void detailFor(int focus) {
        if (board == null) return;
        int k = kindAt(focus);
        if (k == OFFER) {
            Job j = board.offers.get(ref.get(focus));
            tell(j.title + (j.giver.isEmpty() ? "" : ", FOR " + j.giver));
            tell(j.terms);
            if (!j.locked.isEmpty()) tell("LOCKED: " + j.locked);
            if (!j.note.isEmpty()) tell(j.note);
        } else if (k == HOLDING) {
            for (int i = 0; i < board.held.size; i++) tell(board.held.get(i).title);
        }
    }

    @Override
    void press(int k, int focus) {
        HudData d = panels.data();
        if (d == null || d.missions == null || board == null) return;
        int kd = kindAt(focus);
        if (kd == OFFER && k == TAKE) {
            panels.say(d.missions.accept(board.offers.get(ref.get(focus)).id));
            panels.refresh();
        } else if (kd == HOLDING && k == SLATE) {
            panels.show(panels.slate, panels.slate.contracts);
        }
    }
}
