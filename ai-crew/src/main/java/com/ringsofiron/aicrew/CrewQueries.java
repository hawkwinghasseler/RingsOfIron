package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * Read-only lookups other modules use without touching crew internals: the player
 * avatar and its ship-local position (for HUD interact prompts and the audio listener),
 * the skill the crew bring to a ship's guns and sensors, and who mans which console.
 */
public final class CrewQueries {

    private static final Family AVATAR = Family.all(PlayerAvatarComponent.class, GridPositionComponent.class).get();

    /** A ship station whose skill the crew aboard set. */
    public enum Station {
        GUNNERY,
        TRACKING
    }

    /** Returned by {@link #bestSkill} for a ship with no crew aboard. */
    public static final float NO_CREW = -1f;

    private CrewQueries() {}

    /**
     * The best skill at {@code station} among the conscious crew of {@code ship}'s own faction
     * (boarders don't man its guns). On a ship with a weapons console, both stations are worked
     * from it, so only crew manning it count (see {@link #isManning}); with no one at it the guns
     * and sensors run as a green crew's would. On a ship without one, anyone aboard counts. The
     * player's avatar always counts: the player is at the controls.
     *
     * @return the skill, 0 .. 1; 0 when every crew member aboard is down or no one mans the
     *     console; {@link #NO_CREW} when the ship has no crew aboard at all, e.g. an NPC ship whose
     *     crew is not simulated
     */
    public static float bestSkill(Engine engine, Entity ship, Station station) {
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        int faction = board == null ? 0 : board.faction;
        boolean console = hasConsole(ship, SubsystemComponent.SubsystemType.WEAPONS);
        ImmutableArray<Entity> crew = engine.getEntitiesFor(CrewService.CREW);
        float best = NO_CREW;
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (p.ship != ship || c.faction != faction) continue;
            boolean counts = c.isConscious() && (!console || e.getComponent(PlayerAvatarComponent.class) != null
                    || isManning(e, SubsystemComponent.SubsystemType.WEAPONS));
            float skill = !counts ? 0f
                    : station == Station.GUNNERY ? c.def.gunnerySkill : c.def.trackingSkill;
            best = Math.max(best, skill);
        }
        return best;
    }

    /**
     * True if {@code crew} is at a console of {@code type}: holding the task to man it, standing
     * still beside it.
     */
    public static boolean isManning(Entity crew, SubsystemComponent.SubsystemType type) {
        CrewMemberComponent c = crew.getComponent(CrewMemberComponent.class);
        GridPositionComponent p = crew.getComponent(GridPositionComponent.class);
        if (c == null || p == null || !c.isConscious() || !(c.task instanceof ManConsoleTask)) return false;
        ManConsoleTask t = (ManConsoleTask) c.task;
        if (t.claimedBy != crew || t.type() != type || p.isMoving() || p.walkway != null) return false;
        SubsystemComponent s = t.console.getComponent(SubsystemComponent.class);
        int x = p.tileX(), y = p.tileY();
        return s != null && (s.covers(x + 1, y) || s.covers(x - 1, y) || s.covers(x, y + 1) || s.covers(x, y - 1));
    }

    /** True if {@code ship} has a system of {@code type} that is not destroyed. */
    static boolean hasConsole(Entity ship, SubsystemComponent.SubsystemType type) {
        ShipSubsystemRegistryComponent reg = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (reg == null) return false;
        for (Entity sys : reg.subsystems) {
            SubsystemComponent s = sys.getComponent(SubsystemComponent.class);
            if (s != null && s.type == type && !s.isDestroyed()) return true;
        }
        return false;
    }

    /** The player's avatar entity, or null if the player is not aboard as crew. */
    public static Entity playerAvatar(Engine engine) {
        ImmutableArray<Entity> found = engine.getEntitiesFor(AVATAR);
        return found.size() == 0 ? null : found.first();
    }

    /**
     * Writes the avatar's ship-local position (meters, ship body frame) into {@code out}.
     *
     * @return false if there is no avatar or its ship has no grid
     */
    public static boolean playerShipLocal(Engine engine, Vector2 out) {
        Entity e = playerAvatar(engine);
        if (e == null) return false;
        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
        ShipGridComponent grid = p.ship == null ? null : p.ship.getComponent(ShipGridComponent.class);
        if (grid == null) return false;
        p.toShipLocal(grid, out);
        return true;
    }
}
