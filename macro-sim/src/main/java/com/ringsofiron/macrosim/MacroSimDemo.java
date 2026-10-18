package com.ringsofiron.macrosim;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.bubble.MockBubbleGateway;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Headless macro-sim demo: an Earth-sized planet with seven stations at real orbital scale, a fleet
 * of freighters hauling between them, and the player parked in low orbit. Runs under rails warp in
 * one-hour jumps and prints the economy, traffic and bubble activity.
 *
 * <p>Run with {@code gradlew :macro-sim:runMacroDemo} (add {@code -Pdays=60} for a longer run).
 */
public final class MacroSimDemo {
    static final double EARTH_MU = 3.986004418e14;
    static final String PLANET = "terra";

    private MacroSimDemo() {
    }

    /** Builds the demo system. Exposed so tests can run the same scenario. */
    public static MacroSimulation build(GameEngine engine) {
        StubRailsModel rails = new StubRailsModel();
        rails.addBody(PLANET, EARTH_MU);
        return build(engine, rails, PLANET);
    }

    /** The same scenario on any rails model; {@code planet} must already be a massive body in it. */
    public static MacroSimulation build(GameEngine engine, RailsModel rails, String planet) {
        // Player anchor: a circular orbit 1,500 km up, sharing the orbit with the farm.
        StubKeplerOrbit anchor = StubKeplerOrbit.circular(EARTH_MU, 7_871e3, 0.0, 0, 1);
        MockBubbleGateway gateway = new MockBubbleGateway(planet, anchor);
        MacroSimulation sim = new MacroSimulation(engine, rails, gateway, new DefaultCommodityCatalog(), null, 0);
        sim.registerMassiveBody(planet);

        String[][] stations = {
                {"Glacier Ice Works", "ice_mine", "11000", "0.4"},
                {"Greenhouse Ring", "farm", "7871", "2.5"},
                {"Red Rock Diggings", "ore_mine", "9000", "4.1"},
                {"Crucible Refinery", "refinery", "14000", "1.3"},
                {"Tank Farm Depot", "fuel_depot", "12500", "5.6"},
                {"Foundry Works", "factory", "20000", "3.0"},
                {"High Harbour Habitat", "habitat", "42164", "0.9"},
        };
        List<String> ids = new ArrayList<>();
        for (String[] s : stations) {
            double r = Double.parseDouble(s[2]) * 1e3;
            double phase = Double.parseDouble(s[3]);
            StateVector state = StubKeplerOrbit.circular(EARTH_MU, r, phase, 0, 1).stateAt(0, new StateVector());
            ids.add(sim.addStation(s[0], s[1], planet, state, 0.6));
        }
        for (int i = 0; i < 10; i++) {
            sim.addFreighter("Hauler " + (i + 1), 400, 20_000, ids.get(i % ids.size()));
        }
        return sim;
    }

    public static void main(String[] args) {
        double days = args.length > 0 ? Double.parseDouble(args[0]) : 30;
        GameEngine engine = new GameEngine();
        MacroSimulation sim = build(engine);
        System.out.println("Rings of Iron macro-sim demo: " + sim.stations().size() + " stations, "
                + sim.freighters().all().size() + " freighters, " + days + " days of rails warp.\n");

        double end = days * 86400;
        double nextReport = 0;
        for (double t = 0; t <= end; t += 60) {
            sim.advanceTo(t);
            if (t >= nextReport) {
                report(sim, t);
                nextReport += 5 * 86400;
            }
        }
        MockBubbleGateway g = (MockBubbleGateway) sim.gateway();
        System.out.printf("Bubble: %d handoffs into Box2D, %d back to rails (the player is parked away from"
                + " traffic; bubble handoffs are exercised in BubbleTransitionTest).%n", g.spawns, g.destroys);
    }

    static void report(MacroSimulation sim, double t) {
        System.out.printf("=== Day %.1f ===%n", t / 86400);
        StringBuilder header = new StringBuilder(String.format("%-22s", "station"));
        for (CommodityType c : sim.economy().catalog().commodities()) header.append(String.format("%17s", c.id));
        System.out.println(header);
        for (EconomyNode n : sim.stations()) {
            StringBuilder row = new StringBuilder(String.format("%-22s", n.displayName));
            for (CommodityType c : sim.economy().catalog().commodities()) {
                if (!n.prices.containsKey(c.id)) {
                    row.append(String.format("%17s", "-"));
                } else {
                    row.append(String.format("%9.0f @%6.1f", n.stock(c.id), n.price(c.id)));
                }
            }
            System.out.println(row);
        }
        int trips = 0;
        double credits = 0;
        StringBuilder states = new StringBuilder();
        for (Map.Entry<String, FreighterComponent> e : sim.freighters().all().entrySet()) {
            FreighterComponent f = e.getValue();
            trips += f.tripsCompleted;
            credits += f.credits;
            states.append(String.format("  %-10s %-13s %s%n", f.displayName, f.state,
                    f.cargoQuantity > 0 ? String.format("%.0f %s", f.cargoQuantity, f.cargoCommodity) : ""));
        }
        long open = sim.economy().orders().stream().filter(o -> o.claimedBy == null).count();
        System.out.printf("Freighters: %d trips done, fleet credits %.0f, %d open orders%n%s%n",
                trips, credits, open, states);
    }
}
