package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The tactical cluster (spec 8.3), at the weapons console. The seat is the mode: every gun and launcher is
 * the player's here and point defence keeps to itself, so the slot labels say YOURS and AUTO once rather
 * than every chip. TARGET on a strip tube; a chip per gun in hand (a full row each for one or two, compact
 * four to a row past that), the decoy beside them; then the defence rail of three bays of fixed width,
 * point defence, the launcher, the shields, a bay blanked on a hull without its system.
 */
final class TacticalCluster extends YardPart {
    static final float X = 400f, Y = 616f, W = StationsLayout.CLUSTER_W, H = StationsLayout.UNIT_H;
    /** The defence rail's bays: their widths are the same on every hull. */
    static final float PD_W = 186f, LAUNCHER_W = 144f, SHIELDS_W = 126f;
    private static final Color CHIP = Yard.hex("4A4038"), CHIP_EDGE = Yard.hex("6A5C50");

    private final StationsFrame f;
    private final Array<Mount> guns = new Array<>();
    private final Array<Mount> launchers = new Array<>();

    TacticalCluster(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float x = X, y = Y;
        Hardware.pod(p, x, y, W, H, true);
        Hardware.plate(p, x + 5f, y + 10f, W - 10f, H - 14f, false);
        boolean dead = !f.live(Station.TAC);
        p.legend("TARGET", x + 16f, y + 33f, Yard.INK);
        p.tube(x + 66f, y + 19f, 396f, 20f, 3f, 4f, dead);
        if (dead) p.tube("TAC: NO POWER", x + 74f, y + 33.5f, 13f, Yard.P_DIM, false);
        else {
            String t = target(d.targetStatus, Hardware.chars(p, 380f, 13f));
            Hardware.tubeFit(p, t.isEmpty() ? "No target locked." : t, x + 74f, y + 33.5f, 13f,
                    t.isEmpty() ? Yard.P_DIM : d.targetStatus.firingOnUs ? Yard.P_RED : Yard.P_AMBER, 380f);
        }
        p.tubeOver(x + 66f, y + 19f, 396f, 20f);
        Hardware.engrave(p, x + 12f, y + 47f, x + 468f, y + 47f);

        guns.clear();
        launchers.clear();
        for (int mi = 0; mi < d.weaponFrame.mounts.size; mi++) {
            Mount m = d.weaponFrame.mounts.get(mi);
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER) launchers.add(m);
            else if (m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) guns.add(m);
        }
        p.legend("GUNS IN HAND", x + 16f, y + 61f, guns.size > 0 ? Yard.INK : Hardware.DIM_INK);
        Hardware.lens(p, x + 104f, y + 51f, 50f, 13f, "YOURS", Yard.WHITE_LAMP, !dead && guns.size > 0, false);
        if (guns.size <= 2) {
            for (int i = 0; i < guns.size; i++) chip(p, d, x + 14f + i * 192f, y + 67f, 188f, guns.get(i), dead);
        } else {
            int shown = Math.min(guns.size, 8);
            boolean over = guns.size > 8;
            for (int i = 0; i < shown; i++) {
                float cx = x + 14f + (i % 4) * 95f, cy = y + 67f + (i / 4) * 19f;
                if (over && i == 7) more(p, cx, cy, guns.size - 7);
                else compact(p, d, cx, cy, guns.get(i), dead);
            }
        }
        decoy(p, d, x + 400f, y + 67f, dead);
        Hardware.engrave(p, x + 12f, y + 109f, x + 468f, y + 109f);

        float bx0 = x + 12f, bx1 = bx0 + PD_W, bx2 = bx1 + LAUNCHER_W, by = y + 112f;
        pd(p, d, bx0, by, dead);
        Hardware.engrave(p, bx1, by, bx1, by + 40f);
        launcher(p, d, bx1, by, dead);
        Hardware.engrave(p, bx2, by, bx2, by + 40f);
        shields(p, d, bx2, by, dead);
    }

    /** TARGET: name, side, range, closing and its state where known. Hit chances are each gun's own. */
    static String target(TargetStatus t, int budget) {
        if (!t.present) return "";
        String side = t.relation.name().toLowerCase(Locale.ROOT);
        String rel = side.equals("unknown") ? "" : "  " + side;
        String range = Float.isNaN(t.range) ? "" : "  " + Pages.dist(t.range);
        String closing = Float.isNaN(t.closing) || Math.abs(t.closing) < 0.5f ? ""
                : (t.closing > 0f ? "  closing " : "  opening ") + Math.round(Math.abs(t.closing)) + " m/s";
        String hull = Float.isNaN(t.hull) ? "" : "  hull " + Math.round(t.hull * 100f) + "%";
        String state = t.state.isEmpty() || t.state.equals("fit") ? "" : "  " + t.state;
        // Short of room, after the name's letters the relation goes (the tube's colour and the world's
        // marker say it), then the hull; the range, the closing and the state stay
        String rest = rel + range + closing + hull + state;
        if (rest.length() + NAME_AT_LEAST > budget) rest = range + closing + hull + state;
        if (rest.length() + NAME_AT_LEAST > budget) rest = range + closing + state;
        return Hardware.squeeze(t.label, budget, rest);
    }

    /** The letters of the target's name kept before anything after it gives way. */
    private static final int NAME_AT_LEAST = 9;

    /** A gun's state on its chip (8.3): the word, the seconds where it counts down, and how its lamp shows. */
    static String chipState(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return "DESTROYED";
            case REFIT: return "REFIT";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case RELOADING: return "RELOADING";
            case CHARGING: return "CHARGING";
            default:
                if (locked && !m.inArc) return "OUT OF ARC";
                if (m.engaging && m.kind == WeaponStatusProvider.Kind.BEAM) return "BURNING";
                return "READY";
        }
    }

    /** Whether the chip's state counts down: its seconds go on a drum. */
    private static boolean timed(Mount m) {
        return m.cooldown > 0.05f && (m.state == WeaponStatusProvider.State.RELOADING || m.state == WeaponStatusProvider.State.SHORTED
                || m.state == WeaponStatusProvider.State.STUNNED);
    }

    /** Out: wrecked, unpowered, shorted or stunned, shown with the striped flag rather than a lamp. */
    private static boolean out(Mount m) {
        return m.state == WeaponStatusProvider.State.DESTROYED || m.state == WeaponStatusProvider.State.NO_POWER
                || m.state == WeaponStatusProvider.State.SHORTED || m.state == WeaponStatusProvider.State.STUNNED
                || m.state == WeaponStatusProvider.State.REFIT || m.state == WeaponStatusProvider.State.EMPTY;
    }

    /** The crew member seated at that gun, whose skill it shoots with; null with nobody there. */
    static String gunner(HudData d, Mount m) {
        for (int si = 0; si < d.stations.seats.size; si++) {
            Seat s = d.stations.seats.get(si);
            if (s.post == Post.GUN && s.hand == Hand.CREW && s.name.equalsIgnoreCase(m.name) && !s.who.isEmpty()) return s.who;
        }
        return null;
    }

    private void chipBase(YardPaint p, float x, float y, float w, float h) {
        p.rrect(x, y, w, h, 3f, CHIP);
        p.frame(x + 1f, y + 1f, w - 2f, h - 2f, 0.8f, CHIP_EDGE, 1f);
    }

    /** A full-row gun chip: where it sits and its gunner, the state on flaps with its seconds, its own chance to hit. */
    private void chip(YardPaint p, HudData d, float x, float y, float w, Mount m, boolean dead) {
        chipBase(p, x, y, w, 36f);
        float nw = p.legendFit(YardText.fit(m.name, 14), x + 8f, y + 14f, 96f, Yard.INK_LIGHT);
        String who = gunner(d, m);
        if (who != null) p.dymo(x + 12f + nw, y + 3f, YardText.fit(who, 8));
        p.textR(Face.LEGEND, 12f, "HIT", x + w - 8f, y + 14f, Yard.INK_LIGHT);
        if (dead) {
            p.barberTab(x + 6f, y + 20f, 12f, 12f);
            p.flaps(x + 24f, y + 19f, "NO POWER", 10, 8.2f, 14f, 12.5f, Yard.P_RED);
            p.drums(x + w - 40f, y + 18f, "  ", 12f, 15f, 13.5f, false);
            return;
        }
        boolean locked = d.targetStatus.present;
        String st = chipState(m, locked);
        boolean x_ = out(m);
        if (x_) p.barberTab(x + 6f, y + 20f, 12f, 12f);
        else {
            boolean green = st.equals("READY") || st.equals("BURNING");
            p.lamp(x + 12f, y + 26f, 3.6f, green ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, true);
        }
        boolean secs = timed(m);
        int cells = secs ? 9 : 10;
        p.flaps(x + 24f, y + 19f, st, cells, 8.2f, 14f, 12.5f, x_ ? Yard.P_RED : Yard.FLAP_TEXT);
        if (secs) {
            float dx = x + 28f + cells * 8.2f;
            String s = m.cooldown < 10f ? String.format(Locale.ROOT, "%.1f", m.cooldown) : String.valueOf(Math.min(99, Math.round(m.cooldown)));
            float dw = p.drums(dx, y + 18f, s, 11f, 15f, 13.5f, false);
            p.legend("S", dx + dw + 4f, y + 30f, Yard.INK_LIGHT);
        }
        String hit = Float.isNaN(m.hitChance) || !locked ? "  " : YardText.count(Math.min(99f, m.hitChance * 100f), 2);
        p.drums(x + w - 40f, y + 18f, hit, 12f, 15f, 13.5f, false);
        p.legend("%", x + w - 13f, y + 30f, Yard.INK_LIGHT);
    }

    /** A compact gun chip, four to a row: a lamp, where it sits, and its hit chance or what stops it. */
    private void compact(YardPaint p, HudData d, float x, float y, Mount m, boolean dead) {
        chipBase(p, x, y, 93f, 17f);
        boolean locked = d.targetStatus.present;
        String st = dead ? "NO POWER" : chipState(m, locked);
        boolean x_ = dead || out(m);
        if (x_) p.barber(x + 4f, y + 4f, 8f, 9f);
        else p.lamp(x + 8f, y + 8.5f, 2.8f, st.equals("READY") || st.equals("BURNING") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, true);
        String name = YardText.fit(m.name, 9);
        p.legendFit(name, x + 15f, y + 12.5f, 46f, Yard.INK_LIGHT);
        String right;
        if (x_ || !st.equals("READY")) right = shortState(m, st);
        else right = locked && !Float.isNaN(m.hitChance) ? Math.round(m.hitChance * 100f) + "%" : "";
        p.textR(Face.LEGEND, 12f, right, x + 89f, y + 12.5f, x_ ? Yard.hex("FF8A6B") : Yard.INK_LIGHT);
    }

    private static String shortState(Mount m, String st) {
        switch (st) {
            case "RELOADING": return String.format(Locale.ROOT, "%.0fS", Math.max(1f, m.cooldown));
            case "SHORTED": return "SHORT";
            case "STUNNED": return "STUN";
            case "NO POWER": return "NO PWR";
            case "DESTROYED": return "WRECK";
            case "OUT OF ARC": return "ARC";
            case "CHARGING": return "CHRG";
            default: return YardText.fit(st, 5);
        }
    }

    private void more(YardPaint p, float x, float y, int n) {
        chipBase(p, x, y, 93f, 17f);
        p.legendFit("+" + n + " ON WEAPONS", x + 6f, y + 12.5f, 82f, Yard.INK_LIGHT);
    }

    /** The decoy: the player's too, so it rides with the guns. Ready, or the seconds to ready. */
    private void decoy(YardPaint p, HudData d, float x, float y, boolean dead) {
        chipBase(p, x, y, 68f, 36f);
        float lw = p.legend("DECOY", x + 7f, y + 14f, Yard.INK_LIGHT);
        p.key(x + 11f + lw, y + 1.5f, Hardware.key(d, "DECOY", "F"));
        float cd = d.weaponFrame.decoyCooldown;
        p.lamp(x + 13f, y + 26f, 3.6f, cd > 0f ? Yard.AMBER_LAMP : Yard.GREEN_LAMP, !dead);
        p.flaps(x + 22f, y + 19f, dead ? "" : cd > 0f ? String.format(Locale.ROOT, "%.1fS", cd) : "READY", 5, 8.2f, 14f, 12.5f, Yard.FLAP_TEXT);
    }

    /** Point defence: AUTO said once; ENGAGING while the mounts fire; how many work, and the one that doesn't. */
    private void pd(YardPaint p, HudData d, float x, float y, boolean dead) {
        int n = 0, up = 0;
        boolean engaging = false;
        Mount down = null;
        for (int mi = 0; mi < d.weaponFrame.mounts.size; mi++) {
            Mount m = d.weaponFrame.mounts.get(mi);
            if (m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) continue;
            n++;
            if (m.working()) up++;
            else if (down == null) down = m;
            engaging |= m.engaging || m.defending;
        }
        if (n == 0) {
            Hardware.blankBay(p, x + 4f, y + 1f, PD_W - 6f, 40f);
            return;
        }
        p.legend("PD", x + 7f, y + 12f, Yard.INK);
        Hardware.lens(p, x + 25f, y + 2f, 36f, 13f, "AUTO", Yard.WHITE_LAMP, !dead, false);
        Hardware.lens(p, x + 66f, y + 2f, 62f, 13f, "ENGAGING", Yard.GREEN_LAMP, !dead && engaging, false);
        p.drums(x + 139f, y + 3f, dead ? " " : String.valueOf(Math.min(9, up)), 11f, 13f, 12f, false);
        p.legend("OF " + n, x + 156f, y + 12f, Yard.INK);
        String at = down == null || dead ? "" : YardText.fit(down.name, 8);
        String st = down == null || dead ? "" : chipState(down, false);
        p.flaps(x + 8f, y + 21f, at, 8, 7.4f, 14f, 12f, Yard.FLAP_TEXT);
        p.flaps(x + 73f, y + 21f, st, 9, 7.4f, 14f, 12f, Yard.P_RED);
        String secs = down != null && !dead && timed(down) ? String.valueOf(Math.min(9, Math.round(down.cooldown))) : " ";
        p.drums(x + 147f, y + 20f, secs, 11f, 15f, 13.5f, false);
        p.legend("S", x + 163f, y + 32.5f, Yard.INK);
    }

    /** The launchers: missiles left of the magazine, ready or seconds, how many one press throws. */
    private void launcher(YardPaint p, HudData d, float x, float y, boolean dead) {
        if (launchers.size == 0) {
            Hardware.blankBay(p, x + 4f, y + 1f, LAUNCHER_W - 6f, 40f);
            return;
        }
        WeaponStatusProvider.WeaponFrame w = d.weaponFrame;
        if (launchers.size == 1) {
            Mount m = launchers.first();
            float lw = p.legendFit(YardText.fit(m.name.replace(" MSL", ""), 10), x + 8f, y + 12f, 62f, Yard.INK);
            p.key(x + 12f + lw, y + 0.5f, Hardware.key(d, "MISSILE", "R"));
            p.textR(Face.LEGEND, 12f, "SALVO", x + 123f, y + 12f, Yard.INK);
            int left = m.ammo >= 0 ? m.ammo : w.missilesLeft;
            p.drums(x + 127f, y + 3f, dead ? " " : String.valueOf(Math.min(9, Math.min(4, Math.max(0, left)))), 11f, 13f, 12f, false);
            p.legend("MSL", x + 8f, y + 32.5f, Yard.INK);
            p.drums(x + 33f, y + 20f, dead ? " " : String.valueOf(Math.min(9, Math.max(0, left))), 12f, 15f, 13.5f, false);
            if (m.magazine > 0) p.legend("OF " + m.magazine, x + 51f, y + 32.5f, Yard.INK);
            row(p, m, w, x + 81f, y + 21f, dead, 6);
        } else {
            for (int i = 0; i < Math.min(2, launchers.size); i++) {
                Mount m = launchers.get(i);
                float ry = y + 2f + i * 20f;
                p.legendFit(YardText.fit(m.name.replace(" MSL", ""), 9), x + 8f, ry + 11f, 48f, Yard.INK);
                int left = m.ammo >= 0 ? m.ammo : w.missilesLeft;
                p.drums(x + 60f, ry + 1f, dead ? " " : String.valueOf(Math.min(9, Math.max(0, left))), 11f, 13f, 12f, false);
                row(p, m, w, x + 76f, ry, dead, 6);
            }
        }
    }

    /** A launcher's lamp (the striped flag when empty) and its state on flaps. */
    private static void row(YardPaint p, Mount m, WeaponStatusProvider.WeaponFrame w, float x, float y, boolean dead, int cells) {
        String st = dead ? "" : HelmCluster.launcherWord(m, w);
        boolean empty = st.equals("EMPTY");
        if (empty) p.barberTab(x, y + 1f, 12f, 12f);
        else p.lamp(x + 6f, y + 7f, 3.6f, st.equals("READY") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, !dead);
        p.flaps(x + 16f, y, st, cells, 7.4f, 14f, 12f, empty ? Yard.P_RED : Yard.FLAP_TEXT);
    }

    /** Shields: mode, the cloud's mass as a share of capacity, the trim on a rose. Blank with no emitter. */
    private void shields(YardPaint p, HudData d, float x, float y, boolean dead) {
        if (!f.emitter) {
            Hardware.blankBay(p, x + 4f, y + 1f, SHIELDS_W - 6f, 40f);
            return;
        }
        ShieldStatus s = d.shieldStatus;
        float lw = p.legend("SHIELDS", x + 7f, y + 12f, Yard.INK);
        p.key(x + 10f + lw, y + 0.5f, Hardware.key(d, "SHIELDS", "9"));
        float tw = p.legend("TRIM", x + 78f, y + 12f, Yard.INK);
        p.key(x + 81f + tw, y + 0.5f, Hardware.key(d, "SHIELD_TRIM", "8"));
        p.flaps(x + 8f, y + 21f, dead ? "" : Pages.shieldMode(s), 7, 6.6f, 14f, 11.5f, Yard.FLAP_TEXT);
        String mass = dead || s.capacity <= 0f ? "   " : YardText.count(Math.min(100f, s.cloud / s.capacity * 100f), 3);
        p.drums(x + 59f, y + 20f, mass, 9f, 15f, 13f, false);
        p.legend("%", x + 91f, y + 32.5f, Yard.INK);
        float rx = x + 113f, ry = y + 28f, k = 6.5f;
        String trim = s.trim.toUpperCase(Locale.ROOT);
        p.lamp(rx, ry, 2f, Yard.WHITE_LAMP, !dead && trim.equals("EVEN"));
        p.lamp(rx, ry - k, 2f, Yard.WHITE_LAMP, !dead && trim.equals("BOW"));
        p.lamp(rx + k, ry, 2f, Yard.WHITE_LAMP, !dead && (trim.equals("STARBOARD") || trim.equals("STBD")));
        p.lamp(rx, ry + k, 2f, Yard.WHITE_LAMP, !dead && trim.equals("STERN"));
        p.lamp(rx - k, ry, 2f, Yard.WHITE_LAMP, !dead && trim.equals("PORT"));
    }

    @Override
    public String hover(float px, float py) {
        float lx = localX(px), ly = localY(py);
        HudData d = f.data;
        if (ly >= Y + 67f && ly < Y + 105f && lx < X + 396f) {
            if (guns.size <= 2) {
                int i = (int) ((lx - X - 14f) / 192f);
                if (i >= 0 && i < guns.size) return describe(d, guns.get(i));
            } else {
                int i = (int) ((lx - X - 14f) / 95f) + 4 * (int) ((ly - Y - 67f) / 19f);
                if (i >= 0 && i < guns.size && i < 8) return describe(d, guns.get(i));
            }
        }
        if (ly >= Y + 112f && lx >= X + 12f + PD_W + LAUNCHER_W && !f.emitter) return "No shield emitter on this hull";
        return null;
    }

    private static String describe(HudData d, Mount m) {
        String who = gunner(d, m);
        return m.name + ": " + m.weapon + (who == null ? ", fired from here at half your skill" : ", " + who + " at the gun");
    }
}
