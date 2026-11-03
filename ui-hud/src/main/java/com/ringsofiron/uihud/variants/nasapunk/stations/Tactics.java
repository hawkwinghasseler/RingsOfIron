package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;

/**
 * A mount's state in the words the gun chips use (section 8.3), shared by the tactical cluster, the
 * gunner sight and the WEAPONS page so all three say it alike: READY, RELOAD and its seconds,
 * CHARGING, BURNING for a beam on its target, OUT OF ARC, NO POWER, SHORTED, STUNNED and its
 * seconds, DESTROYED; point defense ENGAGING or READY. And how it fires (AT WILL, YOURS, TRIGGER,
 * HOLD, PD AUTO).
 */
final class Tactics {
    private Tactics() {}

    /** The state's word, then its number or "": {"RELOAD", "2.1 S"}. */
    static String[] state(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return new String[] {"DESTROYED", ""};
            case REFIT: return new String[] {"REFIT", ""};
            case SHORTED: return new String[] {"SHORTED", ""};
            case STUNNED: return new String[] {"STUNNED", NpFormat.clock(m.cooldown)};
            case NO_POWER: return new String[] {"NO POWER", ""};
            case EMPTY: return new String[] {"EMPTY", ""};
            case RELOADING: return new String[] {"RELOAD", NpFormat.tenths(m.cooldown) + " S"};
            case CHARGING: return new String[] {"CHARGING", ""};
            default: break;
        }
        // point defense reads READY as its chip does (it fires AUTO, which is how, not state)
        if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE) return new String[] {m.defending || m.engaging ? "ENGAGING" : "READY", ""};
        if (m.kind == WeaponStatusProvider.Kind.BEAM && m.engaging) return new String[] {"BURNING", ""};
        if (locked && !m.inArc && m.kind != WeaponStatusProvider.Kind.LAUNCHER) return new String[] {"OUT OF ARC", ""};
        return new String[] {"READY", ""};
    }

    /** The state in one string: "RELOAD 2.1 S". */
    static String word(Mount m, boolean locked) {
        String[] s = state(m, locked);
        return s[1].isEmpty() ? s[0] : s[0] + " " + s[1];
    }

    static Color stateColor(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return NpColors.RED;
            case SHORTED:
            case STUNNED:
            case NO_POWER: return NpColors.AMBER;
            case EMPTY:
            case REFIT: return NpColors.DIM;
            default: break;
        }
        String w = state(m, locked)[0];
        if (w.equals("ENGAGING") || w.equals("BURNING")) return NpColors.GREEN;
        if (w.equals("OUT OF ARC")) return NpColors.DIM;
        return NpColors.WHITE;
    }

    static BoxState box(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return BoxState.WARNING;
            case SHORTED:
            case STUNNED:
            case NO_POWER: return BoxState.CAUTION;
            case EMPTY:
            case REFIT: return BoxState.OFF;
            default: break;
        }
        String w = state(m, locked)[0];
        if (w.equals("ENGAGING") || w.equals("BURNING")) return BoxState.ENGAGED;
        if (w.equals("OUT OF ARC")) return BoxState.OFF;
        return BoxState.NORMAL;
    }

    /**
     * How the mount fires while the player is elsewhere, or YOURS in hand (section 9.2's words). A
     * launcher waits for the player's word whoever works the console (GunCrews), so out of hand it is
     * TRIGGER, never AT WILL.
     */
    static String how(Mount m, boolean hold) {
        if (m.controller == WeaponStatusProvider.Controller.PLAYER) return "YOURS";
        if (m.kind == WeaponStatusProvider.Kind.LAUNCHER) return "TRIGGER";
        switch (m.controller) {
            case CREW:
            case CONSOLE: return hold ? "HOLD" : "AT WILL";
            case AUTO: return "PD AUTO";
            default: return "TRIGGER";
        }
    }
}
