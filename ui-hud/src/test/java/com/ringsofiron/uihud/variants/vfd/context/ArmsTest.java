package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F2's rows: whole mount names, and a launcher's missiles left in the column a gun's hit chance takes. */
class ArmsTest {

    @Test
    void aMountsNameIsWholeOrKeepsItsNumber() {
        assertEquals("FWD PORT PD", Arms.mountName("FWD PORT PD", Arms.NAME));
        assertEquals("FWD STBD BEAM 2", Arms.mountName("FWD STBD BEAM 2", Arms.NAME));
        assertEquals("FWD STBD BEAM", Arms.mountName("FWD STBD BEAM", Arms.NAME_AT_GUN));
        // Too long for a gun seat's rack: the two launchers on one side still read apart
        assertEquals("FWD STBD MS 2", Arms.mountName("FWD STBD MSL 2", Arms.NAME_AT_GUN));
        assertEquals("FWD STBD BEA", Arms.mountName("FWD STBD BEAM", 12));
    }

    @Test
    void aLaunchersMissilesLeftTakeItsHitColumnAndItsStateStaysWhole() {
        HudData d = new HudData();
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.WEAPONS;
        d.targetStatus.present = true;
        Mount gun = d.weaponFrame.add();
        gun.name = "NOSE GUN";
        gun.weapon = "Heavy cannon";
        gun.controller = Controller.CONSOLE;
        gun.inArc = true;
        gun.hitChance = 0.62f;
        Mount pd = d.weaponFrame.add();
        pd.name = "FWD PORT PD";
        pd.weapon = "Autocannon MK1";
        pd.kind = Kind.POINT_DEFENSE;
        Mount msl = d.weaponFrame.add();
        msl.name = "AFT PORT MSL";
        msl.weapon = "Harpoon launcher";
        msl.kind = Kind.LAUNCHER;
        msl.controller = Controller.CONSOLE;
        msl.state = State.RELOADING;
        msl.cooldown = 10.5f;
        msl.ammo = 3;

        Situations s = new Situations();
        s.update(d, false, false, false, 0.016f);
        assertEquals(Situations.Seat.FIRE_CONTROL, s.seat);
        Arms arms = new Arms();
        arms.update(d, s);
        assertTrue(arms.rack);
        assertEquals(3, arms.rows);

        assertEquals("NOSE GUN", arms.name[0]);
        assertEquals("62", arms.hit[0]);
        assertFalse(arms.left[0]);
        assertEquals("FWD PORT PD", arms.name[1]);
        assertEquals("", arms.hit[1]);
        assertFalse(arms.left[1]);
        assertEquals("AFT PORT MSL", arms.name[2]);
        assertEquals("RELOAD 10.5 S", arms.state[2]);
        assertTrue(arms.state[2].length() <= Arms.STATE);
        assertTrue(arms.left[2]);
        assertEquals("3", arms.hit[2]);
        assertEquals("", arms.word[2]);
    }
}
