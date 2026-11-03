package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The weapons fit's second unit at gunnery, in gun-metal: the decoy (READY, or seconds until it is;
 * decoys never run out) and, on a ship with an emitter, the shields: up, down or tripped, the cloud
 * as a share, the trim, and the four sectors by thickness. It stands above the helm line, beside the
 * weapons panel, so the panel keeps a row for every mount and stays out of the clear zone.
 */
final class DefencePod extends YardPart {
    static final float W = 380f, H_DECOY = 33f, H_SHIELDS = 56f;
    private static final float X = 312f, Y = 657f;
    private static final Color DIM = Yard.Finish.GUN.dim, INK = Yard.Finish.GUN.ink;

    private final TaskFrame f;

    DefencePod(TaskFrame f) {
        super(X, Y);
        this.f = f;
    }

    boolean shields() {
        ShieldStatus s = f.data.shieldStatus;
        return s.present && s.emitters > 0;
    }

    float height() {
        return shields() ? H_SHIELDS : H_DECOY;
    }

    @Override
    protected void draw(YardPaint p) {
        WeaponFrame wf = f.data.weaponFrame;
        p.unit(X, Y, W, h, Yard.Finish.GUN, "");
        boolean ready = wf.decoyCooldown <= 0f;
        p.legend("DECOY", X + 12f, Y + 21f, INK);
        p.lamp(X + 66f, Y + 16.5f, 4f, Yard.GREEN_LAMP, ready);
        p.legend("READY", X + 74f, Y + 21f, INK);
        if (!ready) {
            String s = String.format(Locale.ROOT, "%.1f", Math.min(9.9f, wf.decoyCooldown));
            float sw = p.drums(X + 120f, Y + 7f, s, 11f, 17f, 15f, false);
            p.legend("S", X + 124f + sw, Y + 21f, INK);
        }
        if (shields()) shieldRow(p, f.data.shieldStatus, Y + 30f);
    }

    private static void shieldRow(YardPaint p, ShieldStatus s, float y) {
        p.legend("SHIELDS", X + 12f, y + 14f, INK);
        p.lamp(X + 74f, y + 9.5f, 4f, s.emittersTripped > 0 ? Yard.AMBER_LAMP : Yard.GREEN_LAMP, s.raised || s.emittersTripped > 0);
        p.legend(s.emittersTripped > 0 ? "TRIPPED" : s.raised ? "UP" : "DOWN", X + 82f, y + 14f, INK);
        String share = String.valueOf(Math.min(100, Math.round(s.share() * 100f)));
        float sw = p.drums(X + 140f, y, share, 11f, 17f, 15f, false);
        p.legend("%", X + 144f + sw, y + 14f, INK);
        p.flaps(X + 192f, y, YardText.fit(s.trim, 6), 6, 6.8f, 17f, 12.5f, Yard.FLAP_TEXT);
        // The four sectors, bow, port, stern, starboard, by thickness against the mean
        String[] names = {"B", "P", "S", "SB"};
        for (int i = 0; i < 4; i++) {
            float x = X + 248f + i * 30f, v = Math.max(0f, Math.min(2f, s.sectors[i]));
            p.legend(names[i], x, y + 14f, DIM);
            float bx = x + (names[i].length() > 1 ? 15f : 9f);
            p.rrect(bx, y + 3f, 6f, 12f, 1f, Yard.BAKELITE);
            p.rect(bx + 1f, y + 4f + 10f * (1f - v / 2f), 4f, 10f * v / 2f, Yard.GREEN_LAMP.on);
        }
    }

    @Override
    public String hover(float px, float py) {
        ShieldStatus s = f.data.shieldStatus;
        if (!shields() || localY(py) < Y + 28f) return null;
        String heat = Float.isNaN(s.hottest) ? "" : String.format(Locale.ROOT, ", the hottest at %.0f%% heat", s.hottest * 100f);
        return String.format(Locale.ROOT, "Shields: %d of %d emitters working%s", s.emittersWorking, s.emitters, heat);
    }
}
