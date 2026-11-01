package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;
import org.junit.jupiter.api.Test;

/**
 * Round 10: pirate crews get the power back first ({@code crews_reset_power_first}). A lone raider
 * shorted at 4 km by a frigate's railguns had its crewman stop a bleed or seal a breach instead of
 * resetting the reactor, and it sat dark until it struck without firing a shot.
 */
class PirateCrewOrderTest {

    /**
     * Runs a hall with a shorted reactor at its far end and a crewmate bleeding beside a crewman, at battle
     * stations, until the reactor is reset or the bleed stopped: "power" or "first aid".
     */
    static String firstDone(int faction, boolean powerFirst) {
        TestShips s = new TestShips(
                new ChassisBlueprint("################",
                "#..............#",
                "#..............#",
                "#..............#",
                "#..............#",
                "################")
                .mount("reactor", MountFootprint.MountSize.M, 11, 2));
        s.board.faction = faction;
        s.board.keepsClearOfFire = false; // the bleed lies where the fire fell: only the order differs
        s.board.resetsPowerFirst = powerFirst;
        s.board.battleStations = true;
        ShortsAndTanksTest.system(s, SubsystemComponent.SubsystemType.REACTOR).shorted = true;
        Entity down = s.spawn(CrewRole.CREWMAN, faction, 2, 2);
        s.spawn(CrewRole.CREWMAN, faction, 3, 2);
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        assertEquals(Wound.CRITICAL, s.c(down).wound);
        for (int i = 0; i < 60 * 30; i++) {
            s.run(1f / 30f);
            if (!ShortsAndTanksTest.system(s, SubsystemComponent.SubsystemType.REACTOR).shorted) return "power";
            if (s.c(down).stabilised) return "first aid";
        }
        return "neither";
    }

    @Test
    void aPirateCrewmanResetsTheReactorBeforeStoppingABleedWhileOursKeepTheirOrder() {
        assertEquals("first aid", firstDone(0, false), "our crewman keeps the usual order");
        assertEquals("power", firstDone(7, true), "the pirate gets the power back first");
    }

    @Test
    void theBoardOffersThePowerAheadOfFirstAidAndBreachesOnlyWhenItsCrewResetFirst() {
        ShipTaskBoardComponent board = new ShipTaskBoardComponent();
        Entity reactor = new Entity();
        reactor.add(new SubsystemComponent(new MountFootprint(SubsystemComponent.SubsystemType.REACTOR.mountKind,
                MountFootprint.MountSize.S, 12, 2, 0), SubsystemComponent.SubsystemType.REACTOR));
        board.post(new StabiliseTask(new Entity(), 2, 2));
        board.post(new SealBreachTask(3, 3));
        board.post(new ResetShortTask(reactor, 12, 2));
        assertEquals(CrewTask.Kind.STABILISE, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "first aid first in the usual order");
        assertNull(board.powerReset(CrewRole.CREWMAN, 3, 2, null));
        board.resetsPowerFirst = true;
        assertEquals(CrewTask.Kind.RESET_SHORT, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "the power first");
        assertEquals(CrewTask.Kind.STABILISE, board.best(CrewRole.CREWMAN, 3, 2, t -> t.kind != CrewTask.Kind.RESET_SHORT).kind,
                "a reset they won't take leaves the usual order");
        assertNull(board.powerReset(CrewRole.MARINE, 3, 2, null), "a marine never resets a short");
        board.post(new ResetShortTask(5, 2));
        assertEquals(12, board.best(CrewRole.ENGINEER, 3, 2, null).x, "the reactor ahead of a nearer conduit");
        board.claim(board.best(CrewRole.ENGINEER, 3, 2, null), new Entity());
        assertEquals(5, board.best(CrewRole.CREWMAN, 3, 2, null).x, "then the conduit, ahead of first aid");
        Entity turret = new Entity();
        turret.add(new SubsystemComponent(new MountFootprint(SubsystemComponent.SubsystemType.TURRET_MOUNT.mountKind,
                MountFootprint.MountSize.S, 8, 2, 0), SubsystemComponent.SubsystemType.TURRET_MOUNT));
        board.claim(board.best(CrewRole.CREWMAN, 3, 2, null), new Entity());
        board.post(new ResetShortTask(turret, 8, 2));
        assertEquals(8, board.best(CrewRole.CREWMAN, 3, 2, null).x, "and a gun's short, ahead of first aid");
        board.post(new EvacuateTask(null, 1, 1));
        assertEquals(CrewTask.Kind.EVACUATE, board.best(CrewRole.CREWMAN, 3, 2, null).kind, "abandoning ship comes first all the same");
    }
}
