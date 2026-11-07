package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The boarding panel's one tab (functional-task 7, BRD; mockup tk-boarding), through the boarding's
 * phases: the ship latched to or standing off and how it stands; the party, with Board (or Board on
 * EVA), Storm and Recall as they can be given; once taken, its parts to strip most valuable first, each
 * with what a station pays for it and how it stands in the game's words, a click marking or leaving it;
 * its cargo, Take all on a lot; its prisoners against the berths free, Bring aboard; and a prize in tow's
 * terms, its sale and turn-in. Under the list, what there is to say about the row in focus.
 */
final class BoardingTab extends SectionTab {
    static final int PARTY = ITEM, PART = ITEM + 1, LOT = ITEM + 2, PRISONERS = ITEM + 3;
    static final int K0 = 0, K1 = 1, K2 = 2;

    private BoardingBoard board;
    private CrewSheet crew;
    private String prize = "", sell = "", turnIn = "";

    BoardingTab() {
        super("BOARDING", ClassicWindow.BOARDING, "K");
        keys.define(3, 3);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.boarding.isOpen();
    }

    @Override
    void build(Boards b, HudData d) {
        board = b.boarding;
        crew = b.crew;
        int w = cells - 1;
        head(board.craftName.toUpperCase(Locale.ROOT));
        if (!board.state.isEmpty()) note(board.state.toUpperCase(Locale.ROOT));
        add(PARTY, 0, party(board));
        if (board.salvage) {
            int marked = 0, off = 0;
            for (int i = 0; i < board.parts.size; i++) {
                Part p = board.parts.get(i);
                if (p.off) off++;
                else if (p.marked) marked++;
            }
            head("STRIP: " + marked + " MARKED, " + off + " OFF");
            for (int i = 0; i < board.parts.size; i++) {
                Part p = board.parts.get(i);
                String st = p.off ? "OFF" : p.status.isEmpty() ? "" : p.status.toUpperCase(Locale.ROOT);
                add(PART, i, cols.clear().left(p.name.toUpperCase(Locale.ROOT), Math.max(6, w - 22)).right(Glass.thousands(p.value), 7).gap(1)
                        .left(st, 14).toString());
            }
            if (board.parts.size == 0) note("NOTHING LEFT TO STRIP");
        } else {
            String why = board.salvageRefusal.isEmpty() ? board.boardRefusal : board.salvageRefusal;
            head("STRIP");
            note(why.isEmpty() ? "TAKE IT TO STRIP IT" : why.toUpperCase(Locale.ROOT));
        }
        head("CARGO");
        for (int i = 0; i < board.cargo.size; i++) {
            Cargo c = board.cargo.get(i);
            add(LOT, i, cols.clear().left(c.commodity.replace('_', ' ').toUpperCase(Locale.ROOT), w - 9).right(Glass.units(c.units), 8).toString());
        }
        if (board.cargo.size == 0) note(board.byEva ? "LATCH ON TO TAKE ITS CARGO" : "NO CARGO ABOARD");
        if (crew.prisonersAlongside || board.taken && board.prisoners > 0) {
            head("PRISONERS");
            add(PRISONERS, 0, board.prisoners > 0 ? board.prisoners + " SURRENDERED, ABOARD IT" : "ITS PRISONERS, TO BRING ABOARD");
            note(berths(board));
        }
        prize = b.shipyard.prize;
        if (!prize.isEmpty()) {
            head("PRIZE IN TOW");
            note(prize.toUpperCase(Locale.ROOT));
            sell = b.shipyard.sellLocked.isEmpty() ? b.shipyard.sellTerms : b.shipyard.sellLocked;
            turnIn = b.shipyard.turnInLocked.isEmpty() ? b.shipyard.turnInTerms : b.shipyard.turnInLocked;
            if (!sell.isEmpty()) note(("SELL IT: " + sell).toUpperCase(Locale.ROOT));
            if (!turnIn.isEmpty()) note(("TURN IT IN: " + turnIn).toUpperCase(Locale.ROOT));
        } else if (board.salvage) {
            head("PRIZE");
            note("HAND IT OVER AT A SHIPYARD WITHIN 2 KM");
        }
    }

    /** The party's line: how many are across and how it stands. */
    static String party(BoardingBoard b) {
        if (b.salvage) return b.aboard == 0 ? "THE CREW: NOBODY ACROSS" : "THE CREW: " + b.aboard + " ACROSS";
        if (b.boarding) return b.aboard > 0 ? "PARTY: " + b.aboard + " ABOARD IT" : "PARTY: ON THE WAY";
        return b.boardRefusal.isEmpty() ? "PARTY: READY" : "PARTY: STANDING BY";
    }

    @Override
    void keysFor(int focus) {
        for (int i = 0; i < 3; i++) keys.set(i, "", false);
        if (board == null) return;
        int k = kindAt(focus);
        if (k == PARTY) {
            boolean open = board.isOpen(), noParty = !open || board.salvage || board.boarding;
            keys.set(K0, board.byEva ? "BOARD ON EVA" : "BOARD", !noParty && board.boardRefusal.isEmpty());
            keys.set(K1, "STORM", !noParty && board.stormRefusal.isEmpty());
            keys.set(K2, "RECALL", open && board.boarding);
        } else if (k == PART) {
            Part p = board.parts.get(ref.get(focus));
            keys.set(K0, p.marked ? "LEAVE IT" : "STRIP", p.toggleable);
        } else if (k == LOT) {
            keys.set(K0, "TAKE ALL", board.cargo.get(ref.get(focus)).onOrder <= 0.05);
        } else if (k == PRISONERS) {
            keys.set(K0, "BRING ABOARD", crew != null && crew.prisonersAlongside);
        }
    }

    @Override
    void detailFor(int focus) {
        if (board == null) return;
        switch (kindAt(focus)) {
            case PARTY:
                tell(board.salvage ? (board.taken ? "Taken: the crew go across to strip what is marked, and carry it home"
                        : "No one aboard defends it: marking a part to strip takes it")
                        : board.boarding ? "Recall brings the party home"
                        : !board.boardRefusal.isEmpty() ? board.boardRefusal
                        : board.stormRefusal.isEmpty() ? "Board sends the party across; Storm forces every shut door on the way"
                        : "Board sends the party across");
                if (!board.salvage && board.defenders > 0) tell(board.defenders + " of its crew still stand");
                break;
            case PART: {
                Part p = board.parts.get(ref.get(focus));
                tell(p.name + ": sells for " + Glass.thousands(p.value) + " cr");
                if (!p.status.isEmpty()) tell(p.status);
                break;
            }
            case LOT: {
                Cargo c = board.cargo.get(ref.get(focus));
                tell(Glass.units(c.units) + " " + c.commodity.replace('_', ' ') + " aboard it");
                if (c.onOrder > 0.05) tell(Glass.units(c.onOrder) + " coming over");
                break;
            }
            case PRISONERS:
                tell(berths(board));
                tell("Brought aboard, a station takes them: hand them over, ransom them or let them go");
                break;
            default:
        }
    }

    /**
     * The berths free aboard for its prisoners, since only as many come across: "4 BERTHS FREE", or
     * "2 BERTHS FREE: ONLY 2 COME ACROSS".
     */
    static String berths(BoardingBoard b) {
        int free = Math.max(0, b.berthsFree);
        if (free == 0) return b.prisoners > 0 ? "NO BERTHS FREE: NONE COME ACROSS" : "NO BERTHS FREE";
        String n = free == 1 ? "1 BERTH FREE" : free + " BERTHS FREE";
        if (b.prisoners <= free) return n;
        return n + ": ONLY " + free + (free == 1 ? " COMES ACROSS" : " COME ACROSS");
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        BoardingProvider bp = d == null ? null : d.boarding;
        if (bp == null || board == null) return;
        String said = null;
        switch (kindAt(focus)) {
            case PARTY:
                said = k == K0 ? bp.orderBoarding() : k == K1 ? bp.storm() : bp.recall();
                break;
            case PART: {
                Part p = board.parts.get(ref.get(focus));
                said = bp.toggleStrip(p.x, p.y);
                break;
            }
            case LOT:
                said = bp.takeCargo(board.cargo.get(ref.get(focus)).commodity);
                break;
            case PRISONERS:
                if (d.crew != null) said = d.crew.takePrisoners();
                break;
            default:
        }
        windows.say(said);
        windows.refresh();
    }
}
