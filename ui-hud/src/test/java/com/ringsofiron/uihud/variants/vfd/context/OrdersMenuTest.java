package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C1: every order with its key and how it stands, each reason once with the keys it blocks. */
class OrdersMenuTest {
    private final HudData d = new HudData();
    private final Situations s = new Situations();
    private final OrdersMenu menu = new OrdersMenu();

    @BeforeEach
    void openAtTheHelm() {
        d.ctx.ship = new Entity();
        d.ctx.presence = Presence.HELM;
        d.orderState.seated = true;
        s.update(d, false, false, false, 0.1f);
        menu.toggle();
    }

    @Test
    void aReasonIsGivenOnceWithEveryKeyItBlocks() {
        String nav = "The helm needs a navigation target for that (N picks one).";
        for (HelmOrder h : new HelmOrder[] {HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH, HelmOrder.DOCK}) {
            d.orderState.helmRefusal[h.ordinal()] = nav;
        }
        d.orderState.helm = HelmOrder.FLY_BURNS;
        menu.update(d, s);
        assertEquals(OrdersMenu.ON, menu.helm[0], "the burns are being flown");
        for (int i = 1; i <= 4; i++) assertEquals(OrdersMenu.NA, menu.helm[i]);
        assertEquals(OrdersMenu.IDLE, menu.helm[5]);
        assertEquals(2, menu.reasonCount, "the nav reason, and the shields' with no emitter aboard");
        assertEquals("THE HELM NEEDS A NAVIGATION TARGET FOR THAT (N PICKS ONE)", menu.reasons[0]);
        assertEquals("2 3 4 5", menu.blocked[0]);
        assertEquals("1 FLY BURNS", menu.helmLabel[0]);
    }

    @Test
    void theMenuClosesOnAnOrdersOwnKeyAndOnNothingElse() {
        assertTrue(OrdersMenu.orderKey(d, Keys.NUM_1));
        assertTrue(OrdersMenu.orderKey(d, Keys.NUM_9));
        assertTrue(OrdersMenu.orderKey(d, Keys.P));
        assertFalse(OrdersMenu.orderKey(d, Keys.ESCAPE));
        assertFalse(OrdersMenu.orderKey(d, Keys.NUM_7));
        assertFalse(OrdersMenu.orderKey(d, Keys.W));
    }

    @Test
    void aFullScreenModeOrNoShipClosesIt() {
        s.update(d, false, false, true, 0.1f);
        menu.update(d, s);
        assertFalse(menu.open);
        assertEquals(0f, menu.height());
    }

    @Test
    void aLongReasonIsCutAtAWholeWord() {
        StringBuilder sb = new StringBuilder("6  NOBODY ABOARD TO TAKE THE HELM: THE AUTOPILOT IS A HELMSMAN'S JOB");
        Rows.fit(sb, 40);
        assertEquals("6  NOBODY ABOARD TO TAKE THE HELM", sb.toString());
        // An aside the cut would break goes whole
        sb.setLength(0);
        sb.append("2  HELM ORDER (MATCH) WAITS: YOU HAVE THE HELM (V HANDS IT OVER)");
        Rows.fit(sb, 50);
        assertEquals("2  HELM ORDER (MATCH) WAITS: YOU HAVE THE HELM", sb.toString());
        sb.setLength(0);
        sb.append("B  NO TOW");
        Rows.fit(sb, 40);
        assertEquals("B  NO TOW", sb.toString());
    }
}
