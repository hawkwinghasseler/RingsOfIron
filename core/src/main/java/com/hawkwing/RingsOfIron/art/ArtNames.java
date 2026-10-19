package com.hawkwing.RingsOfIron.art;

import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

import java.util.Locale;

/**
 * The region names of the art for game state, following the naming in documentation/art/Design.md:
 * folder, data id, then size, state, {@code unpowered} and orientation. Pure functions, so they
 * are tested without a GL context.
 */
public final class ArtNames {
    /** Neighbor bits for wall joins, in the {@code nesw} order the file names use. */
    public static final int N = 1, E = 2, S = 4, W = 8;

    private ArtNames() {
    }

    /** {@code n}, {@code es}, {@code nesw}... for a join mask, or {@code 0} for none. */
    public static String sides(int mask) {
        if (mask == 0) return "0";
        StringBuilder sb = new StringBuilder(4);
        if ((mask & N) != 0) sb.append('n');
        if ((mask & E) != 0) sb.append('e');
        if ((mask & S) != 0) sb.append('s');
        if ((mask & W) != 0) sb.append('w');
        return sb.toString();
    }

    public static String weight(WeightClass w) {
        return (w == null ? WeightClass.MEDIUM : w).name().toLowerCase(Locale.ROOT);
    }

    /** The floor art of an open tile: steel deck, or a conduit (dark without power). */
    public static String floor(Tile t) {
        if (t.isPowerConduit()) return t.isPowered ? "tiles/conduit" : "tiles/conduit_unpowered";
        return "tiles/floor";
    }

    /** One arm of a conduit, drawn toward each neighboring conduit. */
    public static String conduitArm(boolean powered) {
        return powered ? "tiles/conduit_arm" : "tiles/conduit_arm_unpowered";
    }

    /** A covering laid over the deck, or null for bare steel. */
    public static String covering(FloorCovering c) {
        return c == null || c == FloorCovering.NONE ? null : "tiles/covering_" + c.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The tile type id a wall tile's art goes by: {@code internal_wall}, or {@code hull_<weight>}.
     * Null for a tile that is not a wall. Walls join neighbors with the same id.
     */
    public static String wallId(Tile t) {
        if (t == null || !t.isWall()) return null;
        if (t.wallType == Tile.WallType.INTERNAL) return "internal_wall";
        return "hull_" + weight(t.weightClass);
    }

    /** A full-tile wall joined to the neighbors in {@code mask}. */
    public static String wall(String wallId, int mask) {
        return "walls/" + wallId + "_" + sides(mask);
    }

    /** A wedge hull piece, at its unrotated size. */
    public static String wedge(HullPiece p) {
        return "walls/hull_" + weight(p.weightClass) + "_wedge_" + p.baseWidth + "x" + p.baseHeight;
    }

    /** A thin wall along one tile edge. */
    public static String edgeWall(WeightClass w, boolean horizontal) {
        return "edges/edge_wall_" + weight(w) + (horizontal ? "_h" : "_v");
    }

    /** The joint at a grid vertex where the edges in {@code mask} carry a wall; null for none. */
    public static String edgeJoint(WeightClass w, int mask) {
        return mask == 0 ? null : "edges/edge_wall_" + weight(w) + "_joint_" + sides(mask);
    }

    public static String door(boolean sealed, boolean powered, boolean horizontal) {
        return "edges/bulkhead_door_" + (sealed ? "sealed" : "open") + (powered ? "" : "_unpowered")
                + (horizontal ? "_h" : "_v");
    }

    /** The built-in component a behavior falls back to: {@code REACTOR} is {@code reactor}. */
    public static String builtInComponent(SubsystemComponent.SubsystemType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Where to look for a system's art, best first: its own component's art, then its behavior's
     * built-in one; each in the power state asked for, then the plain file (passive systems such
     * as cargo have no {@code _unpowered} variant). {@code state} is null or a state such as
     * {@code open}.
     */
    public static String[] component(String componentId, SubsystemComponent.SubsystemType type,
                                     MountFootprint.MountSize size, String state, boolean powered) {
        String builtIn = builtInComponent(type);
        String suffix = "_" + size.name().toLowerCase(Locale.ROOT) + (state == null ? "" : "_" + state);
        String own = componentId == null || componentId.equals(builtIn) ? null : "components/" + componentId + suffix;
        String base = "components/" + builtIn + suffix;
        if (own == null) return powered ? new String[]{base} : new String[]{base + "_unpowered", base};
        return powered ? new String[]{own, base}
                : new String[]{own + "_unpowered", own, base + "_unpowered", base};
    }

    /** A docking port's latch collar, half a tile outside its outer edge. */
    public static String collar(MountFootprint.MountSize size, boolean powered) {
        return "components/docking_collar_" + size.name().toLowerCase(Locale.ROOT) + (powered ? "" : "_unpowered");
    }

    public static String fire(Tile.FireSize size) {
        return size == Tile.FireSize.NONE ? null : "effects/fire_" + size.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Damage showing on a tile at {@code integrity}: light below half (where leaks start), heavy
     * below a quarter; null above.
     */
    public static String damage(float integrity) {
        if (integrity < Tile.LEAK_CAP_CONDITION) return "effects/damage_heavy";
        if (integrity < Tile.LEAK_START) return "effects/damage_light";
        return null;
    }

    /** A bay tile's cargo stack at {@code fill} (0-1) of its height: a third, two thirds or full. */
    public static String cargoStack(float fill) {
        if (fill <= 0f) return null;
        int level = fill > 2f / 3f ? 3 : fill > 1f / 3f ? 2 : 1;
        return "interactive/cargo_stack_" + level;
    }

    /** The exterior of a chassis. */
    public static String hull(String chassisId) {
        return chassisId == null ? null : "hulls/" + chassisId;
    }

    /** A weapon's turret, by its id in weapons.toml. */
    public static String[] turret(String weaponId, boolean powered) {
        String base = "turrets/" + weaponId;
        return powered ? new String[]{base} : new String[]{base + "_unpowered", base};
    }

    /**
     * A round or missile's art, best first: the file its definition names ({@code art}), one named
     * like its id, then for a missile the generic {@code missile}.
     */
    public static String[] projectile(String art, String id, boolean missile) {
        java.util.List<String> out = new java.util.ArrayList<>(3);
        if (art != null) out.add("projectiles/" + art);
        if (id != null) out.add("projectiles/" + id);
        if (missile) out.add("projectiles/missile");
        return out.toArray(new String[0]);
    }

    public static final String DECOY = "projectiles/decoy_flare";
    /** The laser strip, stretched along a beam. */
    public static final String BEAM = "effects/beam";
    /** The gangway across two latched collars, stretched along the crossing. */
    public static final String WALKWAY = "tiles/dock_walkway";

    /** Frames of {@link #explosion}. */
    public static final int EXPLOSION_FRAMES = 4;

    /** The explosion frame at {@code progress} (0 at the flash, 1 when it has faded). */
    public static String explosion(float progress) {
        int frame = 1 + (int) (Math.max(0f, Math.min(0.999f, progress)) * EXPLOSION_FRAMES);
        return "effects/explosion_" + frame;
    }

    /** Whose look a crew member has. */
    public enum Crew { SHIP, STATION, PLAYER }

    /** A crew member, holding a crate while they carry a load. */
    public static String crew(Crew who, boolean carrying) {
        return "crew/crew_" + who.name().toLowerCase(Locale.ROOT) + (carrying ? "_carrying" : "");
    }

    /** A light on a wall, dark without power. */
    public static String wallLight(boolean powered) {
        return powered ? "decor/wall_light" : "decor/wall_light_unpowered";
    }

    /** The emissive layer of a region, for a glow pass. */
    public static String glow(String region) {
        return "glow/" + region;
    }
}
