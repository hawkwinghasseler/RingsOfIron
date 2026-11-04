package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import java.util.Locale;

/**
 * X2's words from the game's boarding board (section 8): the other ship and how it is held, how it
 * stands, the boarding party, the parts marked to strip, the cargo coming across, and the prisoners
 * to bring aboard. {@link BoardingPanel} draws them; a row left empty is left out.
 */
final class BoardingRows {
    /** The spec cuts the name to 16, so "latched" or "by EVA" shares its row. */
    static final int NAME = 16;

    String ship = "", held = "";
    /** How it stands: a head in its colour (TAKEN:, BEING BOARDED:, DISABLED:), then the rest in white. */
    String stateHead = "", state = "";
    Color stateColor = NpColors.WHITE;
    /** The party: how many aboard and what they do, crossing, ready, or why none can go. */
    String party = "";
    /** The party row says why no party can go: amber, and it may take two lines. */
    boolean partyWhy;
    /** Parts marked and how many are off; the lots coming across, or to take. */
    String salvage = "", cargo = "";
    /** Latched to a ship taken, with prisoners held aboard it to bring across. */
    boolean prisoners;

    /**
     * Whether the board makes a boarding (4.2): latched, or a ship in reach on EVA that is beaten,
     * or that our party is boarding or has taken. A fit ship in reach is still a fight.
     */
    static boolean boarding(BoardingBoard b) {
        return b != null && b.isOpen() && (!b.byEva || b.disabled || b.boarding || b.taken);
    }

    /** Reads {@code b}, which is open; {@code latched} as the ship itself says this frame. */
    void read(BoardingBoard b, boolean latched) {
        ship = CautionRows.cut(upper(b.craftName.trim()), NAME);
        held = latched ? "LATCHED" : "BY EVA";
        state(b);
        party(b);
        salvage = salvage(b);
        cargo = cargo(b);
        prisoners = latched && b.taken && b.prisoners > 0;
    }

    /** Only the ship latched to, while the board has yet to name it. */
    void only(String name, boolean latched) {
        ship = CautionRows.cut(upper(name.trim()), NAME);
        held = latched ? "LATCHED" : "BY EVA";
        stateHead = state = party = salvage = cargo = "";
        stateColor = NpColors.WHITE;
        partyWhy = prisoners = false;
    }

    private void state(BoardingBoard b) {
        stateHead = state = "";
        stateColor = NpColors.WHITE;
        if (b.taken) {
            stateHead = b.prisoners > 0 ? "TAKEN:" : "TAKEN";
            stateColor = NpColors.GREEN;
            if (b.prisoners > 0) state = b.prisoners + " HELD";
            return;
        }
        if (b.boarding) {
            stateHead = b.defenders > 0 ? "BEING BOARDED:" : "BEING BOARDED";
            stateColor = NpColors.AMBER;
            if (b.defenders > 0) state = b.defenders + (b.defenders == 1 ? " DEFENDER" : " DEFENDERS");
            return;
        }
        // the game's own line: "Disabled: reactor down", or "Fit to fight"
        String s = b.state.trim();
        int colon = s.indexOf(':');
        if (colon < 0) {
            stateHead = upper(s);
            if (!b.disabled) stateColor = NpColors.AMBER;
            return;
        }
        String why = upper(s.substring(colon + 1).trim());
        if (why.equals("SURRENDERED") || why.equals("ABANDONED") || why.equals("WRECK")) {
            // a reason that says it all on its own
            stateHead = why;
            return;
        }
        stateHead = upper(s.substring(0, colon + 1));
        stateColor = NpColors.AMBER;
        state = why;
    }

    private void party(BoardingBoard b) {
        partyWhy = false;
        party = "";
        if (b.taken) {
            // once the party has come home there is no party to speak of
            if (b.aboard > 0) party = b.aboard + " ABOARD · HOLDING";
        } else if (b.boarding) {
            if (b.aboard > 0) party = b.aboard + " ABOARD · " + (b.defenders > 0 ? "FIGHTING" : "HOLDING");
            else if (b.stormRefusal.isEmpty()) party = "CROSSING";
            else why(b.stormRefusal);
        } else if (b.boardRefusal.isEmpty()) {
            party = "READY";
        } else if (!b.disabled && b.stormRefusal.isEmpty()) {
            party = "READY TO STORM";
        } else {
            why(b.disabled ? b.boardRefusal : b.stormRefusal);
        }
    }

    private void why(String refusal) {
        party = upper(refusal.trim());
        partyWhy = true;
    }

    private static String salvage(BoardingBoard b) {
        if (!b.salvage || b.parts.size == 0) return "";
        int marked = 0, off = 0;
        for (Part p : b.parts) {
            if (p.marked) marked++;
            if (p.off) off++;
        }
        if (marked == 0) return parts(b.parts.size) + " · NONE MARKED";
        return parts(marked) + " MARKED" + (off > 0 ? " · " + off + " OFF" : "");
    }

    private static String parts(int n) {
        return n + (n == 1 ? " PART" : " PARTS");
    }

    private static String cargo(BoardingBoard b) {
        if (b.cargo.size == 0) return "";
        int coming = 0;
        // the boarding window's own measure of a lot still on its way
        for (Cargo c : b.cargo) if (c.onOrder > 0.05) coming++;
        int n = coming > 0 ? coming : b.cargo.size;
        return n + (n == 1 ? " LOT " : " LOTS ") + (coming > 0 ? "COMING ACROSS" : "TO TAKE");
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
