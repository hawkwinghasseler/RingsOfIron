package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.RescueTask;

/**
 * Applies the environment to crew on the hazard tick ({@link GridHazardSystem#HAZARD_TICK}):
 * <ul>
 *   <li><b>Asphyxiation</b> below {@link CrewDef#asphyxiationThreshold} oxygen, faster as it falls.</li>
 *   <li><b>Burns</b> from hot tiles and from standing in fire.</li>
 *   <li><b>Recovery</b>: unconscious crew on a safe tile regain health (faster while a medic
 *       treats them) and wake above the threshold; conscious crew slowly heal.</li>
 *   <li><b>Rescue</b>: an unconscious crew member lying on an unsafe tile gets a
 *       {@link RescueTask} on their ship's board; one lying on a safe tile gets a
 *       {@link TreatTask} for a medic.</li>
 * </ul>
 * Dead crew are left alone.
 */
public class CrewHealthSystem extends FixedIteratingSystem {

    private final CrewService service;

    public CrewHealthSystem(CrewService service) {
        super(CrewService.CREW, 15, GridHazardSystem.HAZARD_TICK);
        this.service = service;
    }

    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = service.crew(e);
        if (c.state == CrewState.DEAD) return;
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid == null) return;
        int x = p.tileX(), y = p.tileY();
        Tile tile = grid.getTile(x, y);
        CrewDef d = c.def;

        float oxygen = grid.oxygenRatioAt(x, y);
        if (oxygen < d.asphyxiationThreshold) {
            float severity = 1f - oxygen / d.asphyxiationThreshold;
            service.applyDamage(e, d.asphyxiationRate * severity * dt, CrewDamageSink.DamageCause.ASPHYXIATION);
        }
        if (tile != null) {
            if (tile.temperature > d.burnTemperature) {
                service.applyDamage(e, (tile.temperature - d.burnTemperature) * d.burnRatePerKelvin * dt,
                        CrewDamageSink.DamageCause.FIRE);
            }
            if (tile.isBurning()) {
                service.applyDamage(e, tile.fireIntensity * d.fireDamageRate * dt, CrewDamageSink.DamageCause.FIRE);
            }
        }
        if (c.state == CrewState.DEAD) return;

        boolean safe = TaskRules.isSafe(grid, x, y);
        ShipTaskBoardComponent board = service.board(p.ship);
        boolean ownCrew = board != null && board.faction == c.faction;
        if (c.state == CrewState.UNCONSCIOUS) {
            if (safe && c.carriedBy == null) {
                float rate = d.recoveryRate * (c.treatedBy != null ? d.treatedRecoveryFactor : 1f);
                service.heal(e, rate * dt);
                if (c.state == CrewState.UNCONSCIOUS && ownCrew) board.post(new TreatTask(e, x, y));
            } else if (!safe && c.carriedBy == null && ownCrew) {
                board.post(new RescueTask(e, x, y));
            }
        } else if (safe && c.health < d.maxHealth) {
            service.heal(e, d.regenRate * dt);
        }
    }
}
