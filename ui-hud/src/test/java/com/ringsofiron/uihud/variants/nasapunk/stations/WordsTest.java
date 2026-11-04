package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The words the pages and chips put on mounts and contacts. */
class WordsTest {
    private static Mount mount(Kind kind, Controller controller) {
        Mount m = new Mount();
        m.kind = kind;
        m.controller = controller;
        return m;
    }

    @Test
    void mountsAreNamedByWhereTheySit() {
        assertEquals("FWD PORT", StDraw.place("Fwd Port PD"));
        assertEquals("FWD", StDraw.place("Fwd Gun"));
        assertEquals("PD 2", StDraw.place("PD 2"), "a name that is only its kind keeps it");
        assertEquals("COILGUN", StDraw.weaponName("Coilgun Mk1"));
        assertEquals("HARPOON LAUNCHER", StDraw.weaponName("Harpoon Launcher"));
    }

    @Test
    void aMarkersStateComesOffItsName() {
        assertEquals("Pirate raider 6", StDraw.bare("Pirate raider 6 (surrendered)"));
        assertEquals("Den battery 23", StDraw.bare("Den battery 23 (silenced)"));
        assertEquals("Highport", StDraw.bare("Highport"));
        assertEquals("Freighter [contract]", StDraw.bare("Freighter [contract]"), "a contract's tag stays");
        assertEquals("", StDraw.bare(null));
    }

    @Test
    void howAMountFires() {
        assertEquals("YOURS", Tactics.how(mount(Kind.GUN, Controller.PLAYER), false));
        assertEquals("AT WILL", Tactics.how(mount(Kind.GUN, Controller.CREW), false));
        assertEquals("AT WILL", Tactics.how(mount(Kind.BEAM, Controller.CONSOLE), false));
        assertEquals("HOLD", Tactics.how(mount(Kind.GUN, Controller.CONSOLE), true));
        assertEquals("TRIGGER", Tactics.how(mount(Kind.GUN, Controller.NONE), false));
        assertEquals("PD AUTO", Tactics.how(mount(Kind.POINT_DEFENSE, Controller.AUTO), true), "point defense fires through hold fire");
    }

    @Test
    void aLauncherWaitsForThePlayer() {
        // crews never fire a launcher (GunCrews), though the console's crew stand behind it
        assertEquals("TRIGGER", Tactics.how(mount(Kind.LAUNCHER, Controller.CONSOLE), false));
        assertEquals("TRIGGER", Tactics.how(mount(Kind.LAUNCHER, Controller.CONSOLE), true));
        assertEquals("YOURS", Tactics.how(mount(Kind.LAUNCHER, Controller.PLAYER), false));
    }

    @Test
    void pointDefenseReadsReadyUntilItEngages() {
        Mount pd = mount(Kind.POINT_DEFENSE, Controller.AUTO);
        assertEquals("READY", Tactics.word(pd, true));
        pd.defending = true;
        assertEquals("ENGAGING", Tactics.word(pd, true));
    }

    @Test
    void aGunThatCannotBearIsOutOfArcButALauncherIsNot() {
        Mount gun = mount(Kind.GUN, Controller.CREW), launcher = mount(Kind.LAUNCHER, Controller.CONSOLE);
        gun.inArc = launcher.inArc = false;
        assertEquals("OUT OF ARC", Tactics.word(gun, true));
        assertEquals("READY", Tactics.word(gun, false), "with nothing locked there is no arc to be out of");
        assertEquals("READY", Tactics.word(launcher, true), "a missile turns to its target");
    }
}
