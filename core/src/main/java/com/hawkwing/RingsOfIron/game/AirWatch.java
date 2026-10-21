package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import java.util.Locale;

/**
 * Warns before the player's people run out of air (round 8 part 5). A burst oxygen tank left a
 * gunship's crew to die as their suits ran dry, and the only warning had been the captain's "the
 * air here is going":
 * <ul>
 *   <li><b>Tanks dry.</b> Once the ship's oxygen tanks hold nothing, a warning that life support is
 *       on its own emergency oxygen, and how much it has left.</li>
 *   <li><b>Air running out.</b> While anyone aboard the player's ship is out of breathable air in a
 *       suit with less than {@link #WARN_AT} seconds of air, and nothing aboard can fill it, a warning
 *       naming who and how long they have, revised in place every {@link #REFRESH} seconds while
 *       it holds.</li>
 * </ul>
 */
public final class AirWatch {
    /** Seconds of suit air left at which the warning comes, before anyone goes down. */
    public static final float WARN_AT = 240f;
    /** Start of the tanks-dry warning. */
    public static final String TANKS_DRY = "Oxygen tanks dry: life support is on its own emergency oxygen";
    /** Start of the warning that suits are running out. */
    public static final String RUNNING_OUT = "Air running out";
    /** Sim seconds between updates of the running-out warning's time. */
    static final double REFRESH = 15;

    private final GameWorld world;
    private boolean dryWarned;
    private double lastWarned = Double.NEGATIVE_INFINITY;
    /** The running-out warning while it holds, revised in place; null otherwise. */
    private NoticeLog.Notice warning;

    public AirWatch(GameWorld world) {
        this.world = world;
    }

    /** Once a frame. */
    public void update() {
        Entity ship = world.getPlayer();
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return;
        double tanks = 0, reserve = 0;
        boolean anyTank = false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            if (s.type == SubsystemComponent.SubsystemType.TANK && s.fluid == Fluid.OXYGEN) {
                anyTank = true;
                if (s.canSupply()) tanks += Math.max(0, s.contents);
            } else if (s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT && s.isOperational && !s.isDisabled()) {
                reserve += TankSupply.reserve(s);
            }
        }
        boolean dry = anyTank && tanks <= CrewDef.SUIT_OXYGEN_KG;
        if (dry && !dryWarned && reserve > 0) {
            world.notices.post(String.format(Locale.ROOT, "%s (%.0f kg). A station's yard refills both", TANKS_DRY, reserve),
                    NoticeLog.Kind.WARNING);
        }
        dryWarned = dry;
        boolean fillable = !anyTank || tanks + reserve > CrewDef.SUIT_OXYGEN_KG;

        String who = null;
        float least = Float.MAX_VALUE;
        if (!fillable) {
            Entity avatar = world.captain.avatar();
            for (Entity e : world.crew.crewEntities()) {
                CrewMemberComponent c = world.crew.crew(e);
                GridPositionComponent p = world.crew.position(e);
                if (p.ship != ship || !c.isAlive() || !c.suited || c.held) continue;
                ShipGridComponent grid = world.crew.grid(p.ship);
                if (grid == null || TaskRules.isBreathable(grid, p.tileX(), p.tileY())) continue;
                if (c.suitAir < least) {
                    least = c.suitAir;
                    who = e == avatar ? "your" : c.name + "'s";
                }
            }
        }
        double now = world.engine.getSimSeconds();
        if (who == null || least >= WARN_AT) {
            warning = null;
            return;
        }
        if (warning != null && now - lastWarned < REFRESH) return;
        lastWarned = now;
        int s = Math.max(0, (int) least);
        String text = String.format(Locale.ROOT,
                "%s: %s suit has %d:%02d of air left and nothing aboard can fill it. Get to a station or pressurised air",
                RUNNING_OUT, who, s / 60, s % 60);
        // One line kept up to date, not a new one every few seconds
        warning = warning == null ? world.notices.post(text, NoticeLog.Kind.WARNING) : world.notices.revise(warning, text);
    }
}
