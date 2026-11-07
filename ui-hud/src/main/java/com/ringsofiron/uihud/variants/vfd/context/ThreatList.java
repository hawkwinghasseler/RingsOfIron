package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * F4, the threat list (functional-context 7, F4), in the left column at fire control and a gun: the
 * inbound missiles soonest first, each with its time to impact, the ship that fired it, and point
 * defense engaging or the decoy advised; then the hostile ships in the fight other than the Lock,
 * each with its range and what it is doing.
 */
final class ThreatList {
    static final int ROWS = 7;
    /** The decoy is advised for a missile this close that point defense is not on, s. */
    static final float DECOY_AT = 8f;

    boolean on;
    final boolean[] missile = new boolean[ROWS], pd = new boolean[ROWS], decoy = new boolean[ROWS];
    final String[] time = new String[ROWS], name = new String[ROWS], range = new String[ROWS], unit = new String[ROWS],
            doing = new String[ROWS];
    final boolean[] firing = new boolean[ROWS];
    int count;
    private final Array<MissileTrack> inbound = new Array<>();

    void update(HudData d, Situations s) {
        count = 0;
        on = false;
        if ((s.seat != Situations.Seat.FIRE_CONTROL && s.seat != Situations.Seat.GUN) || s.fullScreen) return;
        inbound.clear();
        for (MissileTrack t : d.threatFrame.missiles) if (t.side == Side.INCOMING) inbound.add(t);
        inbound.sort((a, b) -> Float.compare(nan(a.timeToImpact), nan(b.timeToImpact)));
        boolean pdOn = d.weaponFrame.pointDefenseActive;
        boolean decoyReady = d.weaponFrame.decoyCooldown <= 0f;
        for (int i = 0; i < inbound.size && count < ROWS; i++) {
            MissileTrack t = inbound.get(i);
            int r = count++;
            missile[r] = true;
            time[r] = Fmt.secs(Math.min(nan(t.timeToImpact), 99f));
            if (Float.isNaN(t.timeToImpact)) time[r] = "";
            name[r] = Fmt.name(t.shooter, 10);
            // Point defense works the soonest first
            pd[r] = pdOn && i == 0;
            decoy[r] = !pd[r] && decoyReady && !Float.isNaN(t.timeToImpact) && t.timeToImpact <= DECOY_AT;
        }
        Array<Contact> all = d.contactFrame.contacts;
        for (int i = 0; i < all.size && count < ROWS; i++) {
            Contact c = all.get(i);
            if (c.kind != Kind.SHIP || c.relation != Relation.HOSTILE || c.locked) continue;
            int r = count++;
            missile[r] = false;
            name[r] = Fmt.name(c.label, 10);
            range[r] = Fmt.range(c.range, 4);
            unit[r] = Fmt.km(c.range) ? "KM" : "M";
            firing[r] = c.threat;
            doing[r] = c.threat ? "FIRING" : Float.isNaN(c.closing) ? "" : c.closing > 0.5f ? "CLOSING" : c.closing < -0.5f ? "OPENING" : "HOLDING";
        }
        on = count > 0;
    }

    private static float nan(float v) {
        return Float.isNaN(v) ? Float.MAX_VALUE : v;
    }

    float height() {
        return on ? count * Geo.ROW + 6f : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    void paint(Filament f, float y) {
        if (!on) return;
        float x = Geo.LX;
        f.slab(x, y, Geo.CW, height(), 5f, 1, true);
        float ry = y + 3f;
        for (int i = 0; i < count; i++) {
            float b = Geo.base(ry), cx;
            if (missile[i]) {
                f.legend(x + 6f, b, "MSL", f.o().red().norm());
                cx = Rows.lab(f, x + 46f, b, "T-") - 2f;
                cx = Rows.num(f, cx, b, time[i], 2, Phosphor.RED, Drive.FULL) - 2f;
                cx = Rows.lab(f, cx, b, "S") + 4f;
                cx = Rows.txt(f, cx, b, name[i], 10);
                if (pd[i]) cx = Rows.state(f, cx, b, "PD ON", true, Phosphor.ZINC);
                else cx = Rows.state(f, cx, b, "DECOY", decoy[i], Phosphor.AMBER);
            } else {
                f.legend(x + 6f, b, "SHIP", f.o().dim());
                cx = Rows.txt(f, x + 46f, b, name[i], 10);
                cx = Rows.num(f, cx, b, range[i], 4) - 2f;
                cx = Rows.lab(f, cx, b, unit[i]) + 4f;
                cx = Rows.txt(f, cx, b, doing[i], 7, firing[i] ? Phosphor.RED : Phosphor.ZINC, firing[i] ? Drive.FULL : Drive.NORM);
            }
            Kit.fits(cx, x + Geo.CW - 2f, "F4 row");
            ry += Geo.ROW;
        }
    }
}
