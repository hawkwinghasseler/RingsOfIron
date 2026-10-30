package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrewIndexTest {

    static TestShips ship() {
        return new TestShips(
                "#######",
                "#.....#",
                "#.....#",
                "#######");
    }

    /** What a scan of every crew member, skipping those on other ships, finds. */
    static List<Entity> scan(CrewService crew, Entity ship) {
        List<Entity> out = new ArrayList<>();
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) if (crew.position(all.get(i)).ship == ship) out.add(all.get(i));
        return out;
    }

    static List<Entity> list(ImmutableArray<Entity> a) {
        List<Entity> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) out.add(a.get(i));
        return out;
    }

    @Test
    void aShipsCrewInCrewOrderAsAScanFindsThem() {
        TestShips s = ship();
        Entity other = new Entity();
        other.add(new ShipGridComponent(s.grid.width, s.grid.height, s.grid.tileSize));
        s.engine.addEntity(other);
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        Entity b = CrewFactory.spawn(s.crew, other, "B", CrewRole.CREWMAN, 0, 1, 1);
        Entity c = s.spawn(CrewRole.MEDIC, 0, 2, 1);
        assertEquals(List.of(a, c), list(s.crew.aboard(s.ship)));
        assertEquals(List.of(b), list(s.crew.aboard(other)));
        assertEquals(0, s.crew.aboard(new Entity()).size(), "nobody aboard a ship nobody is on");
        assertEquals(scan(s.crew, s.ship), list(s.crew.aboard(s.ship)));
    }

    @Test
    void followsCrewChangingShipJoiningAndLeaving() {
        TestShips s = ship();
        Entity other = new Entity();
        other.add(new ShipGridComponent(s.grid.width, s.grid.height, s.grid.tileSize));
        s.engine.addEntity(other);
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        Entity b = s.spawn(CrewRole.CREWMAN, 0, 2, 1);
        ImmutableArray<Entity> before = s.crew.aboard(s.ship);
        s.p(a).setShip(other);
        assertEquals(List.of(b), list(s.crew.aboard(s.ship)));
        assertEquals(List.of(a), list(s.crew.aboard(other)));
        assertEquals(List.of(a, b), list(before), "an array a caller holds stays as it was");
        s.engine.removeEntity(b);
        assertEquals(0, s.crew.aboard(s.ship).size());
        Entity c = s.spawn(CrewRole.MEDIC, 0, 3, 1);
        assertEquals(List.of(c), list(s.crew.aboard(s.ship)));
        s.p(c).setShip(null);
        assertEquals(List.of(c), list(s.crew.aboard(null)), "crew aboard no ship");
    }

    @Test
    void matchesAScanThroughAShiftOfCrewWork() {
        TestShips s = ship();
        for (int i = 0; i < 4; i++) s.spawn(CrewRole.CREWMAN, 0, 1 + i, 1 + i % 2);
        for (int t = 0; t < 60; t++) {
            s.run(0.5f);
            assertEquals(scan(s.crew, s.ship), list(s.crew.aboard(s.ship)));
        }
    }
}
