package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;

/**
 * ui-hud's {@link ShieldStatusProvider}: the player's shield cloud from combat-sys, its mass, its four
 * sectors (the perimeter's 96 samples by bearing off the bow) and its emitters, each with its heat and
 * when a tripped one starts again.
 */
public class PlayerShieldStatus implements ShieldStatusProvider {
    private final GameContext ctx;
    private final float[] sum = new float[4];
    private final int[] samples = new int[4];

    public PlayerShieldStatus(GameContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void read(ShieldStatus out) {
        Entity ship = ctx.player();
        ShieldCloudComponent c = ship == null ? null : ship.getComponent(ShieldCloudComponent.class);
        if (c == null || c.emitters.size == 0) return;
        out.present = true;
        out.raised = c.raised;
        out.loweredForDock = c.loweredForDock;
        out.stock = Math.max(0f, c.stock);
        out.capacity = c.capacity;
        out.cloud = c.cloud();
        out.scattered = c.scattered;
        out.adrift = c.adrift;
        out.stowed = c.stowed;
        out.trim = c.trim == null ? "even" : c.trim.label;
        out.powerStunned = c.powerStunned;
        for (ObjectMap.Entry<Entity, ShieldCloudComponent.Emitter> en : c.emitters) {
            ShieldCloudComponent.Emitter e = en.value;
            out.emitters++;
            if (e.working) out.emittersWorking++;
            if (e.tripped) out.emittersTripped++;
            float heat = e.spec != null && e.spec.heatTrip > 0f ? Math.min(1f, e.heat / e.spec.heatTrip) : 0f;
            out.hottest = Math.max(out.hottest, heat);
            ShieldStatusProvider.Emitter row = out.add();
            row.system = en.key;
            row.heat = heat;
            row.working = e.working;
            row.tripped = e.tripped;
            row.starved = e.starved;
            // Tripped, it draws nothing and sheds its heat until it is down to the restart share
            if (e.tripped && e.spec != null && e.spec.cooling > 0f) {
                row.restartIn = Math.max(0f, e.heat - ShieldModel.RESTART_AT * e.spec.heatTrip) / e.spec.cooling;
            }
        }
        sectors(c, out.sectors);
    }

    /** Each sector's mean density against the whole perimeter's mean, into {@code out}. */
    void sectors(ShieldCloudComponent c, float[] out) {
        ShieldPerimeter p = c.perimeter;
        if (p == null || p.length <= 0f) return;
        java.util.Arrays.fill(sum, 0f);
        java.util.Arrays.fill(samples, 0);
        float total = 0f;
        for (int i = 0; i < ShieldPerimeter.SAMPLES; i++) {
            float d = c.densityAt(p.along[i]);
            float bearing = i * 360f / ShieldPerimeter.SAMPLES;
            int sector = MathUtils.floor(((bearing + 45f) % 360f) / 90f);
            sum[sector] += d;
            samples[sector]++;
            total += d;
        }
        float mean = total / ShieldPerimeter.SAMPLES;
        for (int k = 0; k < 4; k++) out[k] = mean > 0f && samples[k] > 0 ? sum[k] / samples[k] / mean : 0f;
    }
}
