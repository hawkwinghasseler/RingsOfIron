package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.stationsdocking.DockingEvent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Round 6's real-window tow, headless: round 3's career on seed 2026 to the Moon, stranded there,
 * and towed to Deepwell Depot. About 3.5 s after the tug docked it, a pirate gunship rammed the ship
 * at 1.2 km/s and tore Free Trader Mira off the depot's other port: the burn home from its ambush 5 km
 * behind the depot, planned days before during the tow's skip, fired only at the skip's end, from
 * the hold it was snapped to on the side its burn carried it through the depot from.
 *
 * <p>Plays the career to get there, so it is slow, run by hand: the fix's own quick tests are
 * {@code HostileTrafficTest}'s (burns planned in a long jump fire when due; a pirate never flies
 * through the station it lays an ambush by) and {@link TowPursuitTest}.
 */
@Tag("slow")
class TowedInTest extends CareerScript {
    @Override
    String journalName() {
        return "towed-in";
    }

    @Test
    void aShipTowedInIsLeftAloneAtTheStation() {
        start(seed);
        game.beginCareer();
        run(120);
        hireAVeteran();
        fightTheRaiderAndTowItHome();
        fightASecondRaider();
        fightAnAce();
        flyToTheMoon();
        world.docking.undock(game.player);
        run(600);
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent t = e.getComponent(SubsystemComponent.class);
            if (t != null && t.type == SubsystemComponent.SubsystemType.TANK && t.fluid == Fluid.PROPELLANT) t.contents = 0;
        }
        run(60);
        List<String> hits = new ArrayList<>(), torn = new ArrayList<>();
        world.combatEvents.impacts.add((sig, e) -> {
            if (e.target == game.player) hits.add(String.format("%s%s, mass %.0f", PlayerDocking.name(e.owner), e.ram ? " (ram)" : "", e.mass));
        });
        world.docking.events.add((sig, e) -> {
            if (e.kind == DockingEvent.Kind.TORN) torn.add(PlayerDocking.name(e.vessel) + " from " + PlayerDocking.name(e.other));
        });
        String said = world.beacon.call();
        log("%s", said);
        assertTrue(said.startsWith("Towed in"), said);
        assertTrue(runUntil(10f, () -> !world.rescue.bringingIn()));
        Entity station = world.docking.dockedTo(game.player);
        assertNotNull(station, "docked by the tug");
        float hull = hull(game.player);
        run(30 * 60);
        List<String> berthed = new ArrayList<>();
        for (DockingPort p : station.getComponent(DockingPortsComponent.class).ports) {
            Entity other = p.partnerUuid == null ? null : world.engine.getEntity(p.partnerUuid);
            if (other != null && other != game.player) berthed.add(PlayerDocking.name(other));
        }
        log("Towed in to %s; 30 s later docked beside %s. Hits: %s. Torn: %s.", PlayerDocking.name(station), berthed, hits, torn);
        assertEquals(List.of(), hits, "nothing hit the ship at the station");
        assertEquals(List.of(), torn, "nobody was torn off it (Free Trader Mira was, from the other port)");
        assertEquals(hull, hull(game.player), 1e-6f, "no hull lost");
        assertSame(station, world.docking.dockedTo(game.player), "still docked");
    }
}
