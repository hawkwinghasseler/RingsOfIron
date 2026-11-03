package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfdsTest {
    private final Mfds mfds = new Mfds();
    private final AlertRegistry alerts = new AlertRegistry();
    private final Array<Alert> none = new Array<>();

    private void sit(Presence seat, Condition c, boolean docked, boolean shields) {
        mfds.update(seat, c, docked, shields, false, none, 0.016f);
    }

    @Test
    void theSeatAndTheConditionPickThePages() {
        sit(Presence.HELM, Condition.GREEN, false, false);
        assertEquals(Page.NAV, mfds.left.page);
        assertTrue(mfds.left.folded);
        assertEquals(Page.SENSORS, mfds.right.page);
        assertTrue(mfds.right.folded);

        sit(Presence.HELM, Condition.RED, false, false);
        assertEquals(Page.SENSORS, mfds.left.page);
        assertFalse(mfds.left.folded);
        assertEquals(Page.DAMAGE, mfds.right.page);

        sit(Presence.HELM, Condition.GREEN, true, false);
        assertEquals(Page.DOCK, mfds.left.page);
        assertEquals(Page.COMMS, mfds.right.page);
        assertTrue(mfds.right.folded);
    }

    @Test
    void inRedTheWeaponsConsoleShowsDefenseOnlyWithAShieldEmitter() {
        sit(Presence.WEAPONS, Condition.RED, false, true);
        assertEquals(Page.DEFENSE, mfds.right.page);
        sit(Presence.WEAPONS, Condition.RED, false, false);
        assertEquals(Page.DAMAGE, mfds.right.page);
    }

    @Test
    void aPickHoldsUntilThePlayerStandsUp() {
        sit(Presence.HELM, Condition.GREEN, false, false);
        mfds.station(mfds.right, 3);
        assertEquals(Page.DAMAGE, mfds.right.page);
        assertFalse(mfds.right.folded);
        // A change of condition leaves it alone
        sit(Presence.HELM, Condition.RED, false, false);
        assertEquals(Page.DAMAGE, mfds.right.page);
        sit(Presence.HELM, Condition.YELLOW, false, false);
        assertEquals(Page.DAMAGE, mfds.right.page);
        // Standing up forgets it
        sit(Presence.DECK, Condition.YELLOW, false, false);
        sit(Presence.HELM, Condition.YELLOW, false, false);
        assertEquals(Page.NAV, mfds.right.page);
    }

    @Test
    void aStationsKeyAgainCyclesItsPages() {
        // At the helm in Green both MFDs are folded: the key opens its station's first page
        sit(Presence.HELM, Condition.GREEN, false, false);
        assertTrue(mfds.left.folded);
        mfds.station(mfds.left, 2);
        assertEquals(Page.SENSORS, mfds.left.page);
        mfds.station(mfds.left, 2);
        assertEquals(Page.WEAPONS, mfds.left.page);
        mfds.station(mfds.left, 2);
        assertEquals(Page.DEFENSE, mfds.left.page);
        mfds.station(mfds.left, 2);
        assertEquals(Page.SENSORS, mfds.left.page);
        mfds.softKey(mfds.left, 1);
        assertEquals(Page.WEAPONS, mfds.left.page);
        mfds.fold(mfds.left);
        assertTrue(mfds.left.folded);
        mfds.fold(mfds.left);
        assertFalse(mfds.left.folded);
        // On a station's page already, as the weapons console opens on SENSORS in Yellow, the key turns it
        sit(Presence.WEAPONS, Condition.YELLOW, false, false);
        assertEquals(Page.SENSORS, mfds.left.page);
        mfds.station(mfds.left, 2);
        assertEquals(Page.WEAPONS, mfds.left.page);
    }

    @Test
    void aWarningTurnsTheRightMfdToItsPageAndGivesItBackAfterTenSeconds() {
        sit(Presence.HELM, Condition.GREEN, false, false);
        alerts.begin(1);
        alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
        alerts.end();
        mfds.update(Presence.HELM, Condition.GREEN, false, false, false, alerts.all(), 0.016f);
        assertEquals(Page.DAMAGE, mfds.right.page);
        assertFalse(mfds.right.folded);
        // Cleared: it holds the page for ten seconds, then goes back to what it showed, folded again
        alerts.begin(2);
        alerts.end();
        mfds.update(Presence.HELM, Condition.GREEN, false, false, false, alerts.all(), 5f);
        assertEquals(Page.DAMAGE, mfds.right.page);
        mfds.update(Presence.HELM, Condition.GREEN, false, false, false, alerts.all(), 5.5f);
        assertEquals(Page.SENSORS, mfds.right.page);
        assertTrue(mfds.right.folded);
    }

    @Test
    void aPinnedMfdKeepsItsPageAndTheOtherTakesTheAlert() {
        sit(Presence.HELM, Condition.YELLOW, false, false);
        mfds.pin(mfds.right);
        alerts.begin(1);
        alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%");
        alerts.end();
        mfds.update(Presence.HELM, Condition.YELLOW, false, false, false, alerts.all(), 0.016f);
        assertEquals(Page.NAV, mfds.right.page);
        assertEquals(Page.DAMAGE, mfds.left.page);
    }

    @Test
    void aClickedTilePutsItsPageUpUnlessItIsUpAlready() {
        sit(Presence.WEAPONS, Condition.YELLOW, false, false);
        mfds.show(Page.POWER);
        assertEquals(Page.POWER, mfds.right.page);
        assertFalse(mfds.right.folded);
        mfds.show(Page.SENSORS);
        assertEquals(Page.POWER, mfds.right.page);
        assertEquals(Page.SENSORS, mfds.left.page);
    }

    @Test
    void eachAlertHasItsHomePage() {
        alerts.begin(0);
        Alert missile = alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound");
        Alert sys = alerts.raise("sys:REACTOR:1,1", Level.WARNING, Category.ENGINEERING).set("REACTOR", "Reactor down");
        Alert o2 = alerts.raise("o2", Level.CAUTION, Category.LIFE_SUPPORT).set("O2", "Oxygen 20%");
        alerts.end();
        assertEquals(Page.DEFENSE, Mfds.home(missile, true));
        assertEquals(Page.SENSORS, Mfds.home(missile, false));
        assertEquals(Page.POWER, Mfds.home(sys, true));
        assertEquals(Page.ATMOS, Mfds.home(o2, true));
    }
}
