package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;

/**
 * Applies the environment to crew on the hazard tick ({@link GridHazardSystem#HAZARD_TICK}):
 * <ul>
 *   <li><b>Asphyxiation</b> below {@link CrewDef#asphyxiationThreshold} oxygen, faster as it falls.</li>
 *   <li><b>Burns</b> from hot tiles and from standing in fire.</li>
 *   <li><b>Recovery</b>: unconscious crew on a safe tile regain health (faster while a medic
 *       treats them) and wake above the threshold; conscious crew slowly heal.</li>
 *   <li><b>Wounds</b> ({@link Wound}): a critical wound bleeds out over {@link CrewDef#bleedOutTime}
 *       while nobody treats it, and kills when the time runs out. Treatment brings a wound down a
 *       level per {@link CrewDef#careTime} seconds of a baseline medic's care, faster or slower by
 *       the carer's {@link CrewMemberComponent#careRate}, and uses a unit of the ship's medical
 *       stores ({@link ShipGridComponent#medicalStores}) per level; with none left care is slow
 *       ({@link CrewDef#NO_STORES_CARE}). Untreated on a safe tile, a serious wound eases to light
 *       over {@link CrewDef#seriousHealTime}, and a light wound heals over
 *       {@link CrewDef#lightHealTime}. A critical wound someone has stabilised
 *       ({@link CrewMemberComponent#stabilised}) no longer bleeds out.</li>
 *   <li><b>Rescue</b>: a crew member down on an unsafe tile (once any critical wound is
 *       stabilised) gets a {@link RescueTask} on their
 *       ship's board; one down on a safe tile gets a {@link TreatTask}, and once the ship is off
 *       battle stations a {@link MedbayCarryTask} to a free medbay bed if it has one. A critical
 *       wound not yet stabilised gets a {@link StabiliseTask} wherever it lies. Prisoners held aboard
 *       get the same from the crew of the ship that holds them.</li>
 *   <li><b>Light wounds</b> (round 7): a crew member on their feet with a light wound, on a safe
 *       tile and off battle stations, gets a {@link TreatTask} at {@link TreatTask#LIGHT_PRIORITY},
 *       taken by whoever else has nothing to do; alone and idle, they see to it themselves. It is first aid: anyone treats it at a
 *       medic's rate for their skill ({@link CrewMemberComponent#careRate(Wound)}), at
 *       {@link CrewDef#LIGHT_CARE} of that while anyone aboard is seriously or critically wounded
 *       with nobody treating them (round 8), and it uses no medical stores. Sat at a console off battle stations, they
 *       see to themselves there. Any treatment runs {@link CrewDef#MEDBAY_CARE}
 *       times faster with the patient lying in a medbay ({@link TaskRules#isMedbay}).</li>
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
        if (c.breathesFromSuit()) {
            // On suit air wherever the air outside isn't fit to breathe
            if (!TaskRules.isBreathable(grid, x, y)) c.suitAir = Math.max(0f, c.suitAir - dt);
        } else if (oxygen < d.asphyxiationThreshold) {
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
        // A craft's own crew care for their own and their prisoners, and for a guest sheltering aboard
        // (a ship's crew carried onto the station it is docked to while it could not be breathed)
        boolean cared = board != null && (board.faction == c.faction || c.held
                || c.home != null && c.home != p.ship && service.friendlyTo(c.faction, p.ship));
        // First aid for a light wound needs no good air while the patient breathes from a suit (round 8:
        // the cabin slow to come back on emergency oxygen left a graze open for ten minutes and more)
        boolean firstAid = safe || c.breathesFromSuit() && tile != null && tile.holdsAir() && !tile.isBurning()
                && tile.temperature <= TaskRules.SAFE_TEMPERATURE;
        if (c.wound != Wound.NONE) {
            tend(e, c, grid, safe, firstAid, dt);
            if (c.state == CrewState.DEAD) return;
            if (c.wound == Wound.CRITICAL && !c.stabilised && c.treatedBy == null && c.carriedBy == null && cared) {
                board.post(new StabiliseTask(e, x, y));
            }
            if (c.wound == Wound.LIGHT && c.isConscious() && firstAid && cared && !board.battleStations) {
                board.post(new TreatTask(e, x, y, TreatTask.LIGHT_PRIORITY));
            }
        }
        if (c.state == CrewState.UNCONSCIOUS) {
            if (safe && c.carriedBy == null) {
                float rate = d.recoveryRate * (c.treatedBy != null ? d.treatedRecoveryFactor : 1f);
                service.heal(e, rate * dt);
                if (c.state == CrewState.UNCONSCIOUS && cared) {
                    board.post(new TreatTask(e, x, y));
                    // The fight over, to a bed in the medbay
                    if (!board.battleStations && !bleeding(c) && !TaskRules.isMedbay(grid, x, y)
                            && TaskRules.freeBed(grid, service, p.ship, e) != null) {
                        board.post(new MedbayCarryTask(e, x, y));
                    }
                }
            } else if (!safe && c.carriedBy == null && cared && !bleeding(c)) {
                // A critical wound's bleeding is stopped where they lie before anyone moves them:
                // with nowhere safe to carry them, a rescue first left them to bleed out
                board.post(new RescueTask(e, x, y));
            }
        } else if (safe && c.health < d.maxHealth) {
            service.heal(e, d.regenRate * dt);
        }
    }

    private boolean atBattleStations(Entity e) {
        ShipTaskBoardComponent board = service.board(service.position(e).ship);
        return board != null && board.battleStations;
    }

    /** True while anyone alive aboard {@code ship} is seriously or critically wounded, with nobody treating them. */
    private boolean worseWaiting(Entity ship) {
        for (Entity other : service.crewEntities()) {
            CrewMemberComponent o = service.crew(other);
            if (o.isAlive() && o.wound.compareTo(Wound.SERIOUS) >= 0 && o.treatedBy == null
                    && service.position(other).ship == ship) return true;
        }
        return false;
    }

    /** A critical wound nobody has stabilised yet, while someone could still stabilise it. */
    private boolean bleeding(CrewMemberComponent c) {
        return c.wound == Wound.CRITICAL && !c.stabilised && c.treatedBy == null;
    }

    /**
     * A wound's course: bleeding out untreated, treatment by whoever tends it, a light wound healing.
     * {@code firstAid}: where the patient lies, they can see to their own light wound.
     */
    private void tend(Entity e, CrewMemberComponent c, ShipGridComponent grid, boolean safe, boolean firstAid, float dt) {
        CrewMemberComponent carer = c.treatedBy == null ? null : service.crew(c.treatedBy);
        if (carer != null && !carer.isConscious()) carer = null;
        // Idle, or sat at a console, with nobody to treat them, the lightly wounded see to themselves
        if (carer == null && c.wound == Wound.LIGHT && c.isConscious() && (c.task == null || c.task.kind == CrewTask.Kind.MAN_CONSOLE)
                && firstAid && !atBattleStations(e)) carer = c;
        if (carer == null) {
            if (c.wound == Wound.CRITICAL) {
                if (c.stabilised) return;
                c.bleedOut -= dt;
                if (c.bleedOut <= 0f) service.dieOfWounds(e);
            } else if (safe && c.carriedBy == null) {
                c.healing += dt;
                float time = c.wound == Wound.LIGHT ? c.def.lightHealTime : c.def.seriousHealTime;
                if (c.healing >= time) service.easeWound(e);
            }
            return;
        }
        carer.record().pendingCare += dt;
        // A light wound uses no stores, at a slower pace while a worse one waits aboard with nobody
        // seeing to it; a medbay bed speeds any care
        boolean light = c.wound == Wound.LIGHT;
        float rate = light ? (worseWaiting(service.position(e).ship) ? CrewDef.LIGHT_CARE : 1f)
                : grid.medicalStores > 0 ? 1f : CrewDef.NO_STORES_CARE;
        GridPositionComponent p = service.position(e);
        if (c.carriedBy == null && TaskRules.isMedbay(grid, p.tileX(), p.tileY())) rate *= CrewDef.MEDBAY_CARE;
        c.care += dt * carer.careRate(c.wound) * rate;
        if (c.care >= CrewDef.careTime(c.wound)) {
            if (!light && grid.medicalStores > 0) grid.medicalStores--;
            service.easeWound(e);
        }
    }
}
