package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** M3's rail and M4's prisoners as spec version 19 and 20 give them, from the game's data. */
class StationWindowsTest {
    @Test
    void theRailSignsTheStandingAndNamesTheInfirmaryOnlyWhileItHasWorkOrABill() {
        HudData d = new HudData();
        StationInfo st = d.navReadout.docked;
        st.present = true;
        st.name = "Highport";
        st.infirmary = true;
        st.standing = 0f;
        Boards b = new Boards();
        b.refresh(d);
        assertEquals("+0", b.standing, "the game's number, signed: it names no levels");
        assertEquals("", StationRail.infirmary(b), "nobody hurt, nothing billed: no line");
        st.treating.add("Ada");
        b.refresh(d);
        assertEquals("TREATING ADA", StationRail.infirmary(b));
        st.treating.add("Bram");
        b.refresh(d);
        assertEquals("TREATING 2", StationRail.infirmary(b));
        st.treating.clear();
        st.treated = 1;
        st.treatedCost = 1_150;
        b.refresh(d);
        assertEquals("1 TREATED · 1,150 CR", StationRail.infirmary(b));
        st.infirmary = false;
        b.refresh(d);
        assertEquals("", StationRail.infirmary(b), "a station with no infirmary");
    }

    @Test
    void onlyAsManyPrisonersComeAcrossAsThereAreBerthsFree() {
        BoardingBoard b = new BoardingBoard();
        b.prisoners = 3;
        b.berthsFree = 4;
        assertEquals("4 BERTHS FREE", BoardingTab.berths(b));
        b.berthsFree = 2;
        assertEquals("2 BERTHS FREE: ONLY 2 COME ACROSS", BoardingTab.berths(b));
        b.berthsFree = 1;
        assertEquals("1 BERTH FREE: ONLY 1 COMES ACROSS", BoardingTab.berths(b));
        b.berthsFree = 0;
        assertEquals("NO BERTHS FREE: NONE COME ACROSS", BoardingTab.berths(b));
    }
}
