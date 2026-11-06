package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * F2, the weapons rack standing on the coaming at fire control: every mount at a glance, a module each.
 * A white lamp marks the mounts in the gunner's hand; the mount and its weapon are Dymo tape, since a
 * refit changes them; the state is a lamp and flaps with its seconds on drums. Then each module carries
 * what its kind has: a gun its chance to hit on the lock with the word lit in three windows, a launcher
 * its LEFT counter, point defense neither, so its gap is a blanking plate. Last, who fires it. At a gun
 * the rack shrinks to that gun and the launchers.
 */
final class WeaponsRack extends YardPart {
    static final float W = 620f, PAD = 8f, ROW = 26f, ROW_TIGHT = 21f;
    /** Rows the rack holds; more mounts than this share the last row as "+n more". */
    static final int MOST = 6;
    /** Where the weapon's tape starts, and where it must end: before the state's lamp. */
    static final float WEAPON_X = 110f, WEAPON_END = 236f;
    /** The longest weapon name the rack carries (v19). */
    static final int WEAPON_CHARS = 16;

    private final ContextFrame f;
    final Array<Mount> rows = new Array<>();
    private int hidden;
    private float rowH = ROW;

    WeaponsRack(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The rows for this frame: every mount, or at a gun that gun and the launchers. Returns the height. */
    float measure(HudData d, boolean gunSeat) {
        rows.clear();
        hidden = 0;
        for (int i = 0; i < d.weaponFrame.mounts.size; i++) {
            Mount m = d.weaponFrame.mounts.get(i);
            if (gunSeat && m.kind != Kind.LAUNCHER && m.controller != Controller.PLAYER) continue;
            rows.add(m);
        }
        if (rows.size == 0) return 0f;
        if (rows.size > MOST) {
            hidden = rows.size - (MOST - 1);
            rows.truncate(MOST - 1);
        }
        int n = rows.size + (hidden > 0 ? 1 : 0);
        rowH = n <= 5 ? ROW : ROW_TIGHT;
        return PAD * 2f + n * rowH + 2f;
    }

    /**
     * Point defense out of hand: it picks its own targets and has no chance on the lock. In hand (at its
     * gun) it shoots where the captain aims, like any gun, and hunts no missile until let go (v19).
     */
    static boolean pdFree(Mount m) {
        return m.kind == Kind.POINT_DEFENSE && m.controller != Controller.PLAYER;
    }

    /** A mount's state on its flaps (F2): READY, RELOAD, NO POWER, DAMAGED, STUNNED, OUT OF ARC; ENGAGING for point defense. */
    static String state(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return "DAMAGED";
            case REFIT: return "REFIT";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case RELOADING: return "RELOAD";
            case CHARGING: return "CHARGING";
            default:
                if (pdFree(m)) return m.engaging || m.defending ? "ENGAGING" : "READY";
                if (locked && m.kind != Kind.LAUNCHER && !m.inArc) return "OUT OF ARC";
                return m.engaging ? "FIRING" : "READY";
        }
    }

    /** The weapon's name for its tape: the game's, cut at 16 and then to the room before the state. */
    static String weaponTape(YardPaint p, String weapon) {
        String t = YardText.fit(weapon, WEAPON_CHARS);
        for (int n = WEAPON_CHARS - 1; n > 4 && p.width(YardFonts.Face.DYMO, 12f, t) + 9f > WEAPON_END - WEAPON_X; n--) {
            t = YardText.fit(weapon, n);
        }
        return t;
    }

    /** Its seconds count down on the drums: reloading, shorted or stunned with a time. */
    static boolean timed(Mount m) {
        return m.cooldown > 0.05f && (m.state == State.RELOADING || m.state == State.SHORTED || m.state == State.STUNNED || m.state == State.CHARGING);
    }

    /** Who fires it: YOU, CREW (a gunner, or crew at the console), PD by itself, or nobody. */
    static String who(HudData d, Mount m) {
        if (m.controller == Controller.PLAYER) return "YOU";
        if (m.kind == Kind.POINT_DEFENSE || m.controller == Controller.AUTO) return "PD";
        if (m.controller == Controller.CREW || m.controller == Controller.CONSOLE) return gunner(d, m) != null ? YardText.fit(gunner(d, m), 5) : "CREW";
        return "";
    }

    /** The crew member seated at that gun, or null. */
    static String gunner(HudData d, Mount m) {
        for (int i = 0; i < d.stations.seats.size; i++) {
            Seat s = d.stations.seats.get(i);
            if (s.post == Post.GUN && s.hand == Hand.CREW && s.name.equalsIgnoreCase(m.name) && !s.who.isEmpty()) return s.who;
        }
        return null;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Kit.pod(p, 0f, 0f, w, h);
        boolean locked = d.targetStatus.present;
        float y = PAD + 2f;
        for (int i = 0; i < rows.size; i++, y += rowH) module(p, d, rows.get(i), y, locked);
        if (hidden > 0) {
            p.face(8f, y + 1f, w - 16f, rowH - 3f, Yard.Finish.GUN, false);
            p.legend("+" + hidden + " MORE MOUNTS", 22f, y + rowH / 2f + 4.5f, Yard.INK_LIGHT);
        }
    }

    /** One mount's module, on gun-metal like the weapons fit. */
    private void module(YardPaint p, HudData d, Mount m, float y, boolean locked) {
        float mh = rowH - 3f, cy = y + mh / 2f;
        p.face(8f, y + 1f, w - 16f, mh, Yard.Finish.GUN, false);
        boolean pd = pdFree(m);
        // In hand: the white lamp, point defense too at its own gun
        p.lamp(20f, cy + 1f, 3f, Yard.WHITE_LAMP, m.controller == Controller.PLAYER);
        float fy = cy + 1f - 7.5f;
        p.dymo(32f, fy, YardText.fit(Kit.place(m.name), 9));
        // The weapon by the game's name, mark and all (v19: 16 at most), on its tape before the state
        p.dymo(WEAPON_X, fy, weaponTape(p, m.weapon));
        // The state: a lamp and its word, the seconds where it counts down
        String st = state(m, locked);
        boolean ok = st.equals("READY") || st.equals("FIRING") || st.equals("ENGAGING");
        boolean dead = m.state == State.DESTROYED || m.state == State.NO_POWER || m.state == State.EMPTY || m.state == State.REFIT;
        if (dead) p.barberTab(240f, cy - 5f, 10f, 12f);
        else p.lamp(245f, cy + 1f, 3f, ok ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, true);
        Color ink = st.equals("OUT OF ARC") || st.equals("STUNNED") || st.equals("SHORTED") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT;
        p.flaps(258f, cy - 7.5f, st, 10, 8.2f, 16f, 12.5f, ink);
        if (timed(m)) {
            p.drumsR(372f, cy - 8.5f, String.format(Locale.ROOT, "%.1f", Math.min(99.9f, m.cooldown)), 10f, 17f, 14f, 40f, false);
            p.legend("S", 375f, cy + 5f, Yard.INK_LIGHT);
        }
        // The module's own kind
        float mx = 392f, mw = 160f;
        if (m.kind == Kind.LAUNCHER) {
            p.legend("LEFT", mx, cy + 5f, Yard.INK_LIGHT);
            String left = m.ammo < 0 ? "--" : String.valueOf(Math.min(99, m.ammo));
            float dw = p.drums(mx + 34f, cy - 8.5f, left.length() < 2 ? " " + left : left, 11f, 17f, 14f, false);
            if (m.magazine > 0) p.legend("OF " + m.magazine, mx + 34f + dw + 6f, cy + 5f, Yard.INK_LIGHT);
        } else if (pd) {
            Kit.blankBay(p, mx - 2f, cy - 8f, mw, 17f);
        } else {
            p.legend("HIT", mx, cy + 5f, Yard.INK_LIGHT);
            String pct = Float.isNaN(m.hitChance) ? "  " : String.valueOf(Math.round(m.hitChance * 100f));
            p.drumsR(mx + 46f, cy - 8.5f, pct, 11f, 17f, 14f, 34f, false);
            p.legend("%", mx + 49f, cy + 5f, Yard.INK_LIGHT);
            Kit.windows(p, mx + 60f, cy - 6.5f, 34f, 13f, LockCard.HIT_WORDS, LockCard.hitWord(m.hitChance), Yard.hex("E8E0C8"));
        }
        // Who fires it
        p.flaps(566f, cy - 7.5f, who(d, m), 5, 8.2f, 16f, 12.5f, Yard.FLAP_TEXT);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        float ly = localY(py), lx = localX(px);
        int i = (int) ((ly - PAD - 2f) / rowH);
        if (i < 0 || i >= rows.size) return null;
        Mount m = rows.get(i);
        if (lx >= 392f && lx < 556f && m.kind != Kind.LAUNCHER && m.kind != Kind.POINT_DEFENSE) {
            int word = LockCard.hitWord(m.hitChance);
            return word < 0 ? "No solution on the lock" : "Chance to hit while it dodges: " + LockCard.HIT_WORDS[word].toLowerCase(Locale.ROOT);
        }
        if (pdFree(m)) return Kit.place(m.name) + ": point defense picks its own targets";
        if (m.kind == Kind.POINT_DEFENSE) return Kit.place(m.name) + ": in hand, it shoots where you aim and hunts no missile until let go";
        String st = state(m, d.targetStatus.present);
        if (st.equals("OUT OF ARC")) return Kit.gun(m) + " doesn't bear on the lock";
        if (st.equals("NO POWER")) return Kit.gun(m) + ": no circuit feeds it";
        return null;
    }
}
