package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.variants.cassette.Yard;

import java.util.Locale;

/**
 * The helm panel's Comms tab (the Stations spec's 10.2): the station the ship is docked at or making for,
 * who holds it, the captain's standing there, what it offers and any refusal; the clearance its port gave;
 * what a tow would cost and whether one will come; then the newest messages, the newest first.
 */
final class CommsPage extends FactsPage {
    /** Messages the tab shows. */
    static final int MESSAGES = 6;

    private HudData data;
    private boolean own;
    private String right;

    CommsPage() {
        super("COMMS", "COMMS");
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        own = data != null && ConsolePanels.ship(data) == data.ctx.ship;
        begin();
        if (!own) return;
        StationInfo s = data.navReadout.docked.present ? data.navReadout.docked : data.navReadout.station;
        right = s.present ? Fmt.upper(s.name) : null;
        head(data.navReadout.docked.present ? "DOCKED AT" : "STATION", s.present ? Fmt.upper(s.name) : "NONE");
        if (s.present) {
            if (!s.owner.isEmpty()) fact("HELD BY", Fmt.upper(s.owner) + (s.sponsor ? ", YOUR SPONSOR" : ""), AMBER);
            if (!Float.isNaN(s.standing) || !s.standingName.isEmpty()) fact("STANDING", standing(s.standing, s.standingName), s.standing < 0f ? Yard.P_RED : AMBER);
            String services = services(s);
            fact("OFFERS", services.isEmpty() ? "NOTHING" : services, services.isEmpty() ? DIM : AMBER);
            if (!s.refusal.isEmpty()) text(Fmt.upper(s.refusal), Yard.P_RED);
        } else text("NO STATION IN TOUCH", DIM);
        if (data.docking.present && data.docking.clearance != Clearance.NONE) {
            fact("CLEARANCE", HelmPage.clearance(data.docking), data.docking.clearance == Clearance.GRANTED ? AMBER : Yard.P_RED);
            if (!data.docking.refusal.isEmpty() && !s.present) text(Fmt.upper(data.docking.refusal), Yard.P_RED);
        }

        Orders o = data.orderState;
        head("TOW", "");
        if (!o.towRefusal.isEmpty()) text(Fmt.upper(o.towRefusal), DIM);
        else fact("A TUG", o.towFee > 0 ? "COMES FOR " + Fmt.credits(o.towFee).toUpperCase(Locale.ROOT) : "WILL COME", AMBER);
        if (!Float.isNaN(o.towStanding) && o.towStanding != 0f) fact("COSTS", standing(-Math.abs(o.towStanding), "") + " STANDING", DIM);

        Array<MessageFeed.Message> all = data.messages.all();
        head("MESSAGES", all.size == 0 ? "NONE" : "");
        for (int i = 0; i < all.size && i < MESSAGES; i++) {
            MessageFeed.Message m = all.get(i);
            text(Fmt.upper(m.label()), m.level == AlertRegistry.Level.ADVISORY ? DIM : m.level.ordinal() >= AlertRegistry.Level.WARNING.ordinal() ? Yard.P_RED : AMBER);
        }
        keepSelection();
    }

    /** A standing: "+12, TRUSTED", "-4". */
    static String standing(float v, String name) {
        String n = Float.isNaN(v) ? "" : String.format(Locale.ROOT, "%+d", Math.round(v));
        if (name.isEmpty()) return n;
        return n.isEmpty() ? Fmt.upper(name) : n + ", " + Fmt.upper(name);
    }

    /** What a station offers: "MARKET, YARD, JOBS". */
    static String services(StationInfo s) {
        StringBuilder b = new StringBuilder();
        if (s.market) b.append("MARKET");
        if (s.yard) b.append(b.length() > 0 ? ", " : "").append("YARD");
        if (s.shipyard) b.append(b.length() > 0 ? ", " : "").append("SHIPS");
        if (s.contracts) b.append(b.length() > 0 ? ", " : "").append("JOBS");
        if (s.hiring) b.append(b.length() > 0 ? ", " : "").append("CREW");
        if (s.infirmary) b.append(b.length() > 0 ? ", " : "").append("INFIRMARY");
        return b.toString();
    }

    @Override
    String titleRight() {
        return own ? right : null;
    }

    @Override
    void keys() {
    }
}
