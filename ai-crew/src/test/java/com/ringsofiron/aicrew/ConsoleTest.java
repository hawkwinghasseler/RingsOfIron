package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.MountFootprint;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.Tile;
import org.junit.jupiter.api.Test;

class ConsoleTest {

    /** A bridge with a helm (H) and a weapons console (W). */
    static TestShips bridge() {
        return new TestShips(
                new ChassisBlueprint("##########",
                "#........#",
                "#........#",
                "#........#",
                "#........#",
                "##########")
                .mount("helm", MountFootprint.MountSize.S, 2, 3, 1)
                .mount("weapons", MountFootprint.MountSize.S, 6, 3, 1));
    }

    static Entity console(TestShips s, SubsystemType type) {
        for (Entity sys : s.registry.subsystems) {
            if (sys.getComponent(SubsystemComponent.class).type == type) return sys;
        }
        throw new AssertionError("no " + type);
    }

    @Test
    void crewMansTheHelmAndMarinesTheGuns() {
        TestShips s = bridge();
        Entity crewman = s.spawn(CrewRole.CREWMAN, 0, 1, 4);
        Entity marine = s.spawn(CrewRole.MARINE, 0, 8, 4);
        Entity engineer = s.spawn(CrewRole.ENGINEER, 0, 4, 4);
        s.run(8f);
        assertTrue(CrewQueries.isManning(crewman, SubsystemType.HELM), s.c(crewman).activity);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), s.c(marine).activity);
        assertNull(s.c(engineer).task, "engineers don't man consoles");
        assertEquals(0.75f, CrewQueries.bestSkill(s.crew, s.ship, CrewQueries.Station.GUNNERY), 1e-6f);
    }

    @Test
    void anUnmannedWeaponsConsoleMeansGreenGunnery() {
        TestShips s = bridge();
        s.spawn(CrewRole.ENGINEER, 0, 1, 4);
        s.run(1f);
        assertEquals(0f, CrewQueries.bestSkill(s.crew, s.ship, CrewQueries.Station.GUNNERY), 1e-6f,
                "crew aboard, but no one at the console");
    }

    @Test
    void aFireCallsTheCrewOffTheConsoleAndBackAfter() {
        TestShips s = bridge();
        Entity crewman = s.spawn(CrewRole.CREWMAN, 0, 1, 4);
        s.run(6f);
        assertTrue(CrewQueries.isManning(crewman, SubsystemType.HELM));
        Tile t = s.grid.getTile(8, 4);
        t.setCovering(FloorCovering.CARPET, 1f);
        t.ignite();
        s.run(1f);
        assertFalse(CrewQueries.isManning(crewman, SubsystemType.HELM), "left for the fire");
        s.run(20f);
        assertFalse(t.isBurning());
        assertTrue(CrewQueries.isManning(crewman, SubsystemType.HELM), "back at the helm: " + s.c(crewman).activity);
    }

    @Test
    void aDestroyedConsoleIsNotManned() {
        TestShips s = bridge();
        Entity marine = s.spawn(CrewRole.MARINE, 0, 8, 4);
        s.run(5f);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS));
        console(s, SubsystemType.WEAPONS).getComponent(SubsystemComponent.class).health = 0f;
        s.run(1f);
        assertFalse(CrewQueries.isManning(marine, SubsystemType.WEAPONS));
        assertEquals(0.75f, CrewQueries.bestSkill(s.crew, s.ship, CrewQueries.Station.GUNNERY), 1e-6f,
                "with no console left, anyone aboard counts again");
    }
}
