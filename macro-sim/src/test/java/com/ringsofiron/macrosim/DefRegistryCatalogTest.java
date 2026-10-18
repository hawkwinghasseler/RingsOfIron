package com.ringsofiron.macrosim;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.economy.DefRegistryCommodityCatalog;
import com.ringsofiron.macrosim.economy.StationType;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import com.ringsofiron.moddingdata.DataFileParser;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.LoadReport;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefRegistryCatalogTest {

    private static DefRegistryCommodityCatalog loadBaseEconomy() throws Exception {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        Path file = Path.of("..", "assets", "data", "economy.json");
        defs.mergeFile(DataFileParser.parse(file), file.toString(), new LoadReport());
        return new DefRegistryCommodityCatalog(defs);
    }

    @Test
    void baseEconomyFileMapsOntoCommoditiesAndStationTypes() throws Exception {
        DefRegistryCommodityCatalog catalog = loadBaseEconomy();
        assertEquals(12.0, catalog.commodity("water").basePrice, 1e-6);
        StationType foundry = catalog.stationType("foundry");
        assertEquals(60.0, foundry.inputsPerCycle.get("ore"), 1e-6);
        assertEquals(30.0, foundry.outputsPerCycle.get("steel"), 1e-6);
        assertTrue(foundry.consumes("fuel") && foundry.produces("steel"));
    }

    @Test
    void freightersTradeInAnEconomyBuiltFromTheDataFiles() throws Exception {
        DefRegistryCommodityCatalog catalog = loadBaseEconomy();
        double mu = MacroSimDemo.EARTH_MU;
        StubRailsModel rails = new StubRailsModel();
        rails.addBody("p", mu);
        MacroSimulation sim = new MacroSimulation(new GameEngine(), rails, null, catalog, null, 0);
        List<String> stations = new ArrayList<>();
        double r = 8_000e3;
        for (StationType type : catalog.stationTypes()) {
            StateVector s = StubKeplerOrbit.circular(mu, r, r / 1e6, 0, 1).stateAt(0, new StateVector());
            stations.add(sim.addStation(type.id, type.id, "p", s, 0.6));
            r *= 1.4;
        }
        for (int i = 0; i < 8; i++) sim.addFreighter("Hauler " + i, 400, 50_000, stations.get(i % stations.size()));
        sim.advanceTo(20 * 86400);
        int trips = 0;
        for (FreighterComponent f : sim.freighters().all().values()) trips += f.tripsCompleted;
        assertTrue(trips > 0, "trips " + trips);
    }
}
