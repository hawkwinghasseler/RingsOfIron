package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;
import org.junit.jupiter.api.Test;

/**
 * Round 10: in a fight, the player's crews reset a short on what the ship fights with before they
 * seal breaches. Seed 9's career lost Iron Dagny with its helm and two gun mounts shorted at 16 s and
 * still dark 45 s later, while all four crew sealed breaches.
 */
class FightingSystemsFirstTest {

    private static Entity system(SubsystemComponent.SubsystemType type, int x, int y) {
        Entity e = new Entity();
        e.add(new SubsystemComponent(new MountFootprint(type.mountKind, MountFootprint.MountSize.S, x, y, 0), type));
        return e;
    }

    @Test
    void atBattleStationsOurCrewResetTheShortedHelmBeforeABreach() {
        ShipTaskBoardComponent board = new ShipTaskBoardComponent();
        board.faction = TaskRules.SUIT_CARE_FACTION;
        board.post(new SealBreachTask(3, 3));
        board.post(new ResetShortTask(system(SubsystemComponent.SubsystemType.HELM, 6, 2), 6, 2));
        assertEquals(CrewTask.Kind.SEAL_BREACH, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "the breach first, calm");
        board.battleStations = true;
        assertEquals(CrewTask.Kind.RESET_SHORT, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "the helm first in a fight");
        assertEquals(CrewTask.Kind.RESET_SHORT, board.best(CrewRole.ENGINEER, 3, 2, null).kind);
    }

    @Test
    void cargoWaitsItsTurnAndOtherCrewsKeepTheirOrder() {
        ShipTaskBoardComponent board = new ShipTaskBoardComponent();
        board.faction = TaskRules.SUIT_CARE_FACTION;
        board.battleStations = true;
        board.post(new SealBreachTask(3, 3));
        board.post(new ResetShortTask(system(SubsystemComponent.SubsystemType.CARGO, 6, 2), 6, 2));
        assertEquals(CrewTask.Kind.SEAL_BREACH, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "a shorted hold isn't fought with");

        ShipTaskBoardComponent theirs = new ShipTaskBoardComponent();
        theirs.faction = 7;
        theirs.battleStations = true;
        theirs.post(new SealBreachTask(3, 3));
        theirs.post(new ResetShortTask(system(SubsystemComponent.SubsystemType.TURRET_MOUNT, 6, 2), 6, 2));
        assertEquals(CrewTask.Kind.SEAL_BREACH, theirs.best(CrewRole.CREWMAN, 3, 2, null).kind, "another crew's order is theirs");
    }
}
