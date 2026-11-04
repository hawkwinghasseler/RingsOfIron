package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The gunner sight (spec 8.4), seated at a gun: a deliberately narrow view. The gun in hand by where it
 * sits, its state on flaps and its time to ready on drums, ammunition where it is limited; under the rule,
 * the target on a strip tube with its range and this gun's chance to hit it. The arc and the lead reticle
 * are the world's.
 */
final class GunnerSight extends YardPart {
    static final float X = 495f, Y = 680f, W = StationsLayout.SIGHT_W, H = StationsLayout.SIGHT_H;

    private final StationsFrame f;

    GunnerSight(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** The gun the player has in hand: the mount the player works, else the first gun. */
    static Mount gun(HudData d) {
        Mount first = null;
        for (int mi = 0; mi < d.weaponFrame.mounts.size; mi++) {
            Mount m = d.weaponFrame.mounts.get(mi);
            if (m.controller == Controller.PLAYER && m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) return m;
            if (first == null && m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) first = m;
        }
        return first;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float x = X, y = Y;
        Hardware.pod(p, x, y, W, H, true);
        Hardware.plate(p, x + 5f, y + 10f, W - 10f, 82f, false);
        Mount m = gun(d);
        boolean dead = f.blackout;
        if (m == null) {
            p.legend("NO GUN", x + 14f, y + 28f, Yard.INK);
        } else {
            String name = YardText.fit(m.name, 12) + (m.weapon.isEmpty() ? "" : "  " + YardText.fit(m.weapon, 12));
            p.legendFit(name, x + 14f, y + 28f, 130f, Yard.INK);
            String st = dead ? "NO POWER" : TacticalCluster.chipState(m, d.targetStatus.present);
            boolean ok = st.equals("READY") || st.equals("BURNING");
            boolean out = dead || m.state == WeaponStatusProvider.State.DESTROYED || m.state == WeaponStatusProvider.State.NO_POWER
                    || m.state == WeaponStatusProvider.State.SHORTED || m.state == WeaponStatusProvider.State.STUNNED;
            if (out) p.barberTab(x + 14f, y + 38f, 12f, 12f);
            else p.lamp(x + 20f, y + 44f, 3.6f, ok ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, true);
            p.flaps(x + 30f, y + 37f, st, 10, 8.2f, 14f, 12.5f, out ? Yard.P_RED : Yard.FLAP_TEXT);
            p.legend("READY IN", x + 150f, y + 28f, Yard.INK);
            String secs = dead || m.cooldown <= 0.05f ? " . " : String.format(Locale.ROOT, "%.1f", Math.min(m.cooldown, 99.9f));
            float dw = p.drums(x + 150f, y + 33f, secs, 12f, 16f, 13.5f, false);
            p.legend("S", x + 156f + dw, y + 46f, Yard.INK);
            if (m.ammo >= 0) {
                p.legend(m.kind == WeaponStatusProvider.Kind.LAUNCHER ? "MSL" : "AMMO", x + 214f, y + 28f, Yard.INK);
                String left = String.valueOf(Math.min(99, m.ammo));
                float aw = p.drums(x + 214f, y + 33f, left, 12f, 16f, 13.5f, false);
                if (m.magazine > 0) p.legend("OF " + m.magazine, x + 220f + aw, y + 46f, Yard.INK);
            }
        }
        Hardware.engrave(p, x + 12f, y + 56f, x + 278f, y + 56f);
        p.tube(x + 12f, y + 62f, 266f, 22f, 3f, 4f, dead);
        TargetStatus t = d.targetStatus;
        if (dead) p.tube("GUN: NO POWER", x + 20f, y + 77f, 12.5f, Yard.P_DIM, false);
        else if (t.present) {
            StringBuilder b = new StringBuilder();
            if (!Float.isNaN(t.range)) b.append("  ").append(Pages.dist(t.range));
            // Out of the arc the chance to hit means nothing: the arc is the news
            if (m != null && !m.inArc) b.append("  OUT OF ARC");
            else if (m != null && !Float.isNaN(m.hitChance)) b.append("  HIT ").append(Math.round(m.hitChance * 100f)).append('%');
            String line = Hardware.squeeze(t.label, Hardware.chars(p, 250f, 12.5f), b.toString());
            Hardware.tubeFit(p, line, x + 20f, y + 77f, 12.5f, t.firingOnUs ? Yard.P_RED : Yard.P_AMBER, 250f);
        } else p.tube("No target locked.", x + 20f, y + 77f, 12.5f, Yard.P_DIM, false);
        p.tubeOver(x + 12f, y + 62f, 266f, 22f);
    }
}
