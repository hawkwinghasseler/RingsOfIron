package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.stations.MfdState.Mode;
import com.ringsofiron.uihud.variants.nasapunk.stations.MfdState.Seat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the two MFDs show (section 9.1): the defaults, the player's choices, the alerts' paging, and warp. */
class MfdStateTest {
    private final MfdState m = new MfdState();
    private final List<Annunciation.Tile> tiles = new ArrayList<>();

    private static Annunciation.Tile tile(String key, Level l, Page home, double since) {
        Annunciation.Tile t = new Annunciation.Tile();
        t.key = key;
        t.level = l;
        t.home = home;
        t.station = home.station;
        t.since = since;
        return t;
    }

    private void frame(Seat seat, Mode mode, float dt) {
        m.update(seat, mode, false, false, tiles, dt);
    }

    @Test
    void atTheHelmInGreenBothFoldOnNavAndSensors() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.NAV, m.left.page);
        assertTrue(m.folded(m.left));
        assertEquals(Page.SENSORS, m.right.page);
        assertTrue(m.folded(m.right));
    }

    @Test
    void theDefaultsFollowTheCondition() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        frame(Seat.HELM, Mode.YELLOW, 0.1f);
        assertEquals(Page.SENSORS, m.left.page);
        assertFalse(m.folded(m.left));
        assertEquals(Page.NAV, m.right.page);
        assertTrue(m.folded(m.right));
        frame(Seat.HELM, Mode.RED, 0.1f);
        assertEquals(Page.DAMAGE, m.right.page);
        assertFalse(m.folded(m.right));
        frame(Seat.HELM, Mode.DOCKED, 0.1f);
        assertEquals(Page.DOCK, m.left.page);
        assertFalse(m.folded(m.left));
    }

    @Test
    void atTheWeaponsConsoleInRedTheRightTakesDefenseOnlyWithAnEmitter() {
        m.update(Seat.WEAPONS, Mode.RED, false, false, tiles, 0.1f);
        assertEquals(Page.SENSORS, m.left.page);
        assertEquals(Page.DAMAGE, m.right.page);
        MfdState e = new MfdState();
        e.update(Seat.WEAPONS, Mode.RED, true, false, tiles, 0.1f);
        assertEquals(Page.DEFENSE, e.right.page);
    }

    @Test
    void thePlayersChoiceHoldsUntilTheyStandUp() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        m.choose(true, Page.PLAN);
        frame(Seat.HELM, Mode.RED, 0.1f);
        assertEquals(Page.PLAN, m.left.page, "a change of condition leaves a chosen MFD alone");
        assertFalse(m.folded(m.left));
        assertEquals(Page.DAMAGE, m.right.page);
        frame(Seat.NONE, Mode.RED, 0.1f);
        frame(Seat.HELM, Mode.RED, 0.1f);
        assertEquals(Page.SENSORS, m.left.page, "sitting down again starts from the defaults");
    }

    @Test
    void anAlertTurnsTheRightMfdToItsPageAndItGoesBackTenSecondsAfterItClears() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        tiles.add(tile("FIRE", Level.WARNING, Page.DAMAGE, 1.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.DAMAGE, m.right.page);
        assertFalse(m.folded(m.right));
        tiles.clear();
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        frame(Seat.HELM, Mode.GREEN, 9.8f);
        assertEquals(Page.DAMAGE, m.right.page, "it waits 10 s");
        frame(Seat.HELM, Mode.GREEN, 0.2f);
        assertEquals(Page.SENSORS, m.right.page);
        assertTrue(m.folded(m.right), "folded again, as it was");
    }

    @Test
    void aPinnedRightMfdLeavesTheAlertToTheLeftAndTwoPinsToNeither() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        m.pin(false);
        tiles.add(tile("O2", Level.CAUTION, Page.ATMOS, 1.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.ATMOS, m.left.page);
        assertEquals(Page.SENSORS, m.right.page);
        m.pin(true);
        tiles.add(0, tile("HULL", Level.WARNING, Page.DAMAGE, 2.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.ATMOS, m.left.page);
        assertEquals(Page.SENSORS, m.right.page);
    }

    @Test
    void theWorstAlertKeepsTheMfdOverANewerCaution() {
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        tiles.add(tile("MISSILE", Level.WARNING, Page.DEFENSE, 1.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.DEFENSE, m.right.page);
        // the newer caution sorts after the warning, and waits on the annunciator
        tiles.add(tile("SMOKE", Level.CAUTION, Page.DAMAGE, 2.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.DEFENSE, m.right.page);
        // a newer warning takes it
        tiles.add(0, tile("HULL", Level.WARNING, Page.POWER, 3.0));
        frame(Seat.HELM, Mode.GREEN, 0.1f);
        assertEquals(Page.POWER, m.right.page);
    }

    @Test
    void underWarpBothFoldUnlessSomethingIsLit() {
        m.update(Seat.HELM, Mode.RED, false, true, tiles, 0.1f);
        assertTrue(m.folded(m.left));
        assertTrue(m.folded(m.right));
        tiles.add(tile("DELTA-V", Level.CAUTION, Page.PLAN, 1.0));
        m.update(Seat.HELM, Mode.RED, false, true, tiles, 0.1f);
        assertFalse(m.folded(m.left), "with a caution lit, warp folds nothing");
        assertEquals(Page.PLAN, m.right.page);
        assertFalse(m.folded(m.right));
    }

    @Test
    void aPageThePlayerOpensUnderWarpStaysOpen() {
        m.update(Seat.HELM, Mode.YELLOW, false, true, tiles, 0.1f);
        m.choose(true, Page.PLAN);
        m.update(Seat.HELM, Mode.YELLOW, false, true, tiles, 0.1f);
        assertFalse(m.folded(m.left));
        assertTrue(m.folded(m.right));
    }

    @Test
    void aStationsKeyCyclesItsPagesAndFoldOpensAndCloses() {
        frame(Seat.WEAPONS, Mode.GREEN, 0.1f);
        m.station(false, Station.ENG);
        assertEquals(Page.DAMAGE, m.right.page);
        assertFalse(m.folded(m.right));
        m.station(false, Station.ENG);
        assertEquals(Page.POWER, m.right.page);
        m.station(false, Station.ENG);
        m.station(false, Station.ENG);
        assertEquals(Page.DAMAGE, m.right.page, "round to the first");
        m.fold(false);
        assertTrue(m.folded(m.right));
        m.station(false, Station.ENG);
        assertEquals(Page.DAMAGE, m.right.page, "a folded MFD opens on the station's first page");
        assertFalse(m.folded(m.right));
    }
}
