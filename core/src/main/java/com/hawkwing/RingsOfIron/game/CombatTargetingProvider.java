package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.uihud.providers.TargetingProvider;

/**
 * ui-hud's targeting readout from combat-sys: the player's sensor lock, and the primary gun's
 * firing solution on it, worked from the same noisy track the turret aims with. So the reticle
 * jitters at long range and is late on a target that changes its burn, as the guns are.
 *
 * <p>The lead point is drawn where the target appears to be from the moving ship: the muzzle plus
 * the aim direction times the round's flight, which is where to point the nose or the turret.
 */
public class CombatTargetingProvider implements TargetingProvider {
    private final PlayerGunnery gun;
    private float timeToImpact = Float.NaN;

    public CombatTargetingProvider(PlayerGunnery gun) {
        this.gun = gun;
    }

    @Override
    public Entity getLockedTarget() {
        return gun.getTarget();
    }

    @Override
    public boolean getLeadPoint(Vector2 out) {
        timeToImpact = Float.NaN;
        WeaponMountComponent m = gun.primary();
        if (m == null || gun.getTarget() == null || m.engaged != gun.getTarget() || !m.solution.valid) return false;
        timeToImpact = m.solution.timeToImpact;
        float reach = gun.muzzleSpeed() * timeToImpact;
        out.set(m.muzzle).mulAdd(m.solution.aimDir, reach);
        return true;
    }

    @Override
    public float getTimeToImpact() {
        return timeToImpact;
    }
}
