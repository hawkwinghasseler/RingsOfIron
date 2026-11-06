package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The ship's Loadout (functional-context 10.2): the ship's figures (its mass, the Δv left, the
 * berths), each hardpoint and what it carries, and the furnishings aboard. Away from a yard it is the
 * crew's field refit (Y), as the yard's list has it: a salvaged weapon from the hold mounted on EVA.
 */
final class LoadoutTab extends YardTab {

    LoadoutTab() {
        super("LOADOUT", ClassicWindow.YARD, "Y", true);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return d.ctx.ship != null;
    }

    @Override
    void build(Boards b, HudData d) {
        board = b.yard;
        if (board.field && refitting >= 0 && refitting < board.hardpoints.size) {
            super.build(b, d);
            return;
        }
        refitting = -1;
        head("SHIP");
        StringBuilder sb = new StringBuilder();
        if (!Float.isNaN(d.flightStatus.mass)) sb.append("MASS ").append(Glass.thousands(d.flightStatus.mass / 1000f)).append(" T");
        if (!Double.isNaN(d.flightStatus.deltaVLeft)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("ΔV LEFT ").append(Glass.thousands(d.flightStatus.deltaVLeft)).append(" M/S");
        }
        if (sb.length() > 0) note(sb.toString());
        if (!b.crew.summary.isEmpty()) note(b.crew.summary.toUpperCase(Locale.ROOT));
        if (board.field) {
            whole();
        } else {
            head("HARDPOINTS");
            for (int i = 0; i < d.weaponFrame.mounts.size; i++) {
                Mount m = d.weaponFrame.mounts.get(i);
                note(cols.clear().left(m.name.toUpperCase(Locale.ROOT), 22).left(m.weapon.toUpperCase(Locale.ROOT), 30).toString());
            }
            if (d.weaponFrame.mounts.size == 0) note("NO WEAPONS MOUNTED");
            note(b.yard.stationName.isEmpty() ? "A YARD REFITS THEM; AWAY FROM ONE THE CREW MOUNT SALVAGED WEAPONS ON EVA"
                    : "THE YARD HERE REFITS THEM: Y OPENS IT");
        }
        if (!board.furnished.isEmpty()) {
            head("FURNISHINGS");
            note(board.furnished.toUpperCase(Locale.ROOT));
        }
    }
}
