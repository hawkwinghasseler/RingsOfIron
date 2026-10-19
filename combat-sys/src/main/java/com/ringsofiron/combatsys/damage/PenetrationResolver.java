package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.SpallDef;
import com.ringsofiron.combatsys.defs.WarheadDef;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Turns a hull hit into interior damage, entirely in the ship's local frame
 * (combat-sys/Design.md sections 5.2 to 5.5). No Box2D: {@code ImpactResolutionSystem} converts
 * to and from the world and applies momentum.
 *
 * <h2>Energy model</h2>
 * A round carries a penetration budget {@code P} in joules. Each solid piece it crosses first
 * strips {@code armor * integrity} (barely scratching the piece), then absorbs up to
 * {@code hitPoints * integrity}, losing integrity in proportion; at the entry face both are
 * divided by {@code max(cos(incidence), 0.2)}. A piece is destroyed exactly when the round still
 * has budget left after it. Open floor, machinery and crew take smaller bites. Energy is only
 * ever moved from the round into the ship or into secondary rays, never created.
 *
 * <h2>Shorts and bursts</h2>
 * Every hit on a system or a powered conduit rolls for a short circuit, and every hit that leaves
 * a tank below its burst threshold for a burst ({@link DamageStates}). Bursts go off once the
 * round and its secondaries are done: the tank vents, and a large one sets off a small blast of
 * its own, which can burst other tanks in turn. A burst's blast is the tank's energy, not the
 * round's, so it is not counted in {@link Result#absorbed}.
 */
public class PenetrationResolver {

    // --- Tuning (prototype values; candidates for the material table) ---

    /** Share of stripped armor energy that still wears the piece down. */
    public static final float ARMOR_WEAR = 0.1f;
    /** Floor of {@code cos(incidence)} when scaling entry resistance, so grazing hits stay finite. */
    public static final float MIN_INCIDENCE_COS = 0.2f;
    /** Share of a glancing round's budget the hull absorbs as a scrape. */
    public static final float RICOCHET_SCRAPE = 0.05f;
    /** Joules a round loses crossing one open floor tile (air, fittings, clutter). */
    public static final float FLOOR_RESISTANCE = 2e5f;
    /** Joules that cut a power conduit from intact to severed. */
    public static final float CONDUIT_HIT_POINTS = 4e5f;
    /** Joules a crew member's body takes out of a round. */
    public static final float CREW_RESISTANCE = 2e3f;
    /** Joules of absorbed energy per health point of crew injury. */
    public static final float CREW_JOULES_PER_HP = 20f;
    /** Overpressure on a tile, J, that kills an unprotected crew member outright (100 hp). */
    public static final float CREW_BLAST_LETHAL = 2e6f;
    /**
     * Machinery share of a system's hardness that one tile of it can absorb from a single ray: what
     * a round drilling through it can wreck. At a quarter, three railgun slugs through a corvette's
     * nose shut its reactor down and fights ended in seconds (round 2 playtest); at a tenth a ship
     * takes several, and its crew have fires, breaches and repairs to deal with meanwhile.
     */
    public static final float SUBSYSTEM_TILE_SHARE = 0.1f;
    /** Below this health a system stops working; at 0 it is destroyed. interior-grid owns the value. */
    public static final float DISABLE_THRESHOLD = SubsystemComponent.DISABLE_THRESHOLD;
    /** Edge walls are thin: this share of a full tile's hit points and armor. */
    public static final float EDGE_WALL_FRACTION = 0.25f;
    /** How far outside the impact point the traversal starts, tiles (section 5.2 step 3). */
    public static final float START_OUTSIDE_TILES = 1.5f;
    /** Share of the heat a beam puts into a wall that reaches the open tile behind it, for fires. */
    public static final float BEAM_HEAT_SOAK = 0.1f;

    /** Joules to destroy a whole system of each type. */
    public static float subsystemHardness(SubsystemComponent.SubsystemType type) {
        switch (type) {
            case REACTOR:        return 4e7f;
            case SHIELD_EMITTER: return 2e7f;
            case TURRET_MOUNT:   return 2.5e7f;
            case CARGO:          return 1e7f;
            case AIRLOCK:
            case ESCAPE_POD:     return 1.5e7f;
            default:             return 8e6f;
        }
    }

    /** One resolved ray. */
    public static class Result {
        public enum Outcome {
            /** Never reached the entry tile (missed the grid entirely). */
            MISSED,
            STOPPED,
            RICOCHET,
            PENETRATED,
            OVER_PENETRATED
        }

        public Outcome outcome = Outcome.MISSED;
        public int entryX = -1, entryY = -1;
        public float startBudget;
        /** Budget left when the round left the ship (over-penetration) or glanced off. */
        public float exitBudget;
        /** Energy the ship absorbed, J, including spall and fragments from this hit. */
        public float absorbed;
        /** Where an over-penetrating round left, in tile space. */
        public final Vector2 exitPoint = new Vector2();
        /** For a ricochet: the outgoing direction (local, unit) and speed scale. */
        public final Vector2 ricochetDir = new Vector2();
        public float ricochetSpeedScale;
        /** Tile where a fuse fired, or -1. */
        public int detonationX = -1, detonationY = -1;

        void reset() {
            outcome = Outcome.MISSED;
            entryX = entryY = -1;
            startBudget = exitBudget = absorbed = 0f;
            exitPoint.setZero();
            ricochetDir.setZero();
            ricochetSpeedScale = 0f;
            detonationX = detonationY = -1;
        }
    }

    private static final ComponentMapper<SubsystemComponent> subsystemMapper =
            ComponentMapper.getFor(SubsystemComponent.class);

    private MaterialTable materials;
    private CombatDefs defs;
    private CrewDamageBridge crew;

    // Per-call state (one call at a time).
    private ShipGridComponent grid;
    private Entity ship;
    private RandomXS128 rng;
    private DamageReport report;
    private final Array<Entity> occupants = new Array<>();
    private final Array<SecondaryRay> secondaries = new Array<>();
    private final Vector2 tmp = new Vector2();

    public PenetrationResolver(MaterialTable materials) {
        this.materials = materials;
    }

    public void setCrew(CrewDamageBridge crew) {
        this.crew = crew;
    }

    public MaterialTable getMaterials() {
        return materials;
    }

    /** Swaps in another material table, such as the one loaded from data. */
    public void setMaterials(MaterialTable materials) {
        this.materials = materials;
    }

    /**
     * Where each material's fire chance and ablation factor come from. Null (the default) treats
     * every material as non-flammable plain steel.
     */
    public void setDefs(CombatDefs defs) {
        this.defs = defs;
    }

    private float materialFireChance(String material) {
        return defs == null ? 0f : defs.fireChance(material);
    }

    private float ablationFactor(String material) {
        float f = defs == null ? 1f : defs.ablationFactor(material);
        return f > 0f ? f : 1f;
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /**
     * Resolves a round striking the hull.
     *
     * @param localPoint    impact point, body-local meters
     * @param localNormal   outward hull normal at the impact, body-local, unit
     * @param localRelVel   round velocity relative to the hull surface, body-local m/s
     * @param budget        penetration budget, J
     * @param ricochetAngleDeg  incidence above which a round that cannot punch through glances
     * @param restitution   share of speed a glancing round keeps
     */
    public Result resolveHit(ShipGridComponent grid, Entity ship, Vector2 localPoint, Vector2 localNormal,
                             Vector2 localRelVel, float budget, DamageType damageType, Fuse fuse, SpallDef spall,
                             float ricochetAngleDeg, float restitution, RandomXS128 rng, DamageReport report,
                             Result out) {
        begin(grid, ship, rng, report);
        out.reset();
        out.startBudget = budget;
        float speed = localRelVel.len();
        if (speed <= 0f || budget <= 0f) return out;

        float dirX = localRelVel.x / speed, dirY = localRelVel.y / speed;
        float ex = (localPoint.x - grid.gridOriginLocal.x) / grid.tileSize;
        float ey = (localPoint.y - grid.gridOriginLocal.y) / grid.tileSize;

        Ray ray = new Ray();
        ray.budget = budget;
        ray.damageType = damageType;
        ray.cause = HitCause.PENETRATOR;
        ray.fuse = fuse;
        ray.spall = spall;
        ray.fromOutside = true;
        ray.dirX = dirX;
        ray.dirY = dirY;
        ray.normal.set(localNormal);
        ray.ricochetCos = MathUtils.cosDeg(ricochetAngleDeg);
        ray.restitution = restitution;
        ray.result = out;
        trace(ray, ex - dirX * START_OUTSIDE_TILES, ey - dirY * START_OUTSIDE_TILES);

        out.absorbed = ray.absorbed;
        out.exitBudget = out.outcome == Result.Outcome.OVER_PENETRATED || out.outcome == Result.Outcome.RICOCHET
                ? ray.budget : 0f;
        out.absorbed += runSecondaries(damageType);
        end();
        return out;
    }

    /**
     * Sets off a warhead inside the hull at tile (x, y): overpressure scaled by the air there,
     * plus fragments flung in every direction. Section 5.5.
     *
     * @return energy the ship absorbed, J
     */
    public float detonateInside(ShipGridComponent grid, Entity ship, int x, int y, WarheadDef warhead,
                                RandomXS128 rng, DamageReport report) {
        begin(grid, ship, rng, report);
        float absorbed = blast(x, y, warhead);
        float fragmentBudget = 0.5f * warhead.fragmentMass * warhead.fragmentSpeed * warhead.fragmentSpeed;
        for (int i = 0; i < warhead.fragmentCount; i++) {
            float angle = (i + rng.nextFloat()) * MathUtils.PI2 / warhead.fragmentCount;
            queueSecondary(x + 0.5f, y + 0.5f, MathUtils.cos(angle), MathUtils.sin(angle),
                    fragmentBudget, HitCause.FRAGMENT);
        }
        absorbed += runSecondaries(DamageType.EXPLOSIVE);
        end();
        return absorbed;
    }

    /** One tick of a beam on a ship. */
    public static class BurnResult {
        /** Whether the beam put energy into anything inside the grid. */
        public boolean touched;
        /** First tile the beam burned this tick, or -1. */
        public int tileX = -1, tileY = -1;
        /** Energy the ship took, J. */
        public float absorbed;
        /** A wall or hull tile gave way this tick. */
        public boolean burnedThrough;

        void reset() {
            touched = burnedThrough = false;
            tileX = tileY = -1;
            absorbed = 0f;
        }
    }

    /**
     * A beam holding on the hull for one tick (section 5.6). {@code energy} J go into the first
     * intact thing along the line, scaled by its material's {@code ablation_factor}: a wall
     * loses {@code energy * ablation / (hitPoints + armor)} of its integrity, with no armor
     * strip, so armor only slows a beam. Energy left over once a tile gives way burns on into
     * the next one; holes and open air let it through, and machinery or a person stops it. Heat
     * soaking through a wall can light the open tile behind it. Energy that leaves the far side
     * is lost.
     *
     * @param localPoint where the beam meets the hull, body-local meters
     * @param localDir   beam direction, body-local, unit
     */
    public BurnResult burn(ShipGridComponent grid, Entity ship, Vector2 localPoint, Vector2 localDir, float energy,
                           RandomXS128 rng, DamageReport report, final BurnResult out) {
        begin(grid, ship, rng, report);
        out.reset();
        if (energy <= 0f) {
            end();
            return out;
        }
        final float dirX = localDir.x, dirY = localDir.y;
        float ex = (localPoint.x - grid.gridOriginLocal.x) / grid.tileSize;
        float ey = (localPoint.y - grid.gridOriginLocal.y) / grid.tileSize;
        final float[] left = {energy};
        final float[] heat = {0f};
        final boolean[] entered = {false};
        float maxT = grid.width + grid.height + 2f * START_OUTSIDE_TILES + 2f;
        GridRayTraversal.traverse(ex - dirX * START_OUTSIDE_TILES, ey - dirY * START_OUTSIDE_TILES, dirX, dirY, maxT,
                new GridRayTraversal.Visitor() {
                    @Override
                    public boolean tile(int x, int y, float tEnter, float tExit) {
                        Tile tile = PenetrationResolver.this.grid.getTile(x, y);
                        if (tile == null || tile.isVoid()) return !entered[0];
                        entered[0] = true;
                        if (tile.isWall()) {
                            if (tile.getIntegrity() <= 0f) return true;
                            float took = burnWall(tile, x, y, left[0], out);
                            left[0] -= took;
                            heat[0] = took;
                            return true;
                        }
                        if (heat[0] > 0f) {
                            tryIgnite(tile, x, y, DamageType.ENERGY, heat[0] * BEAM_HEAT_SOAK / FLOOR_RESISTANCE);
                            heat[0] = 0f;
                        }
                        if (left[0] <= 0f) return false;
                        if (burnContents(tile, x, y, left[0], out)) {
                            left[0] = 0f;
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                        if (!entered[0] || left[0] <= 0f) return left[0] > 0f || heat[0] > 0f;
                        EdgeWall wall = PenetrationResolver.this.grid.getEdgeBetween(fromX, fromY, toX, toY);
                        if (wall == null || !wall.blocks()) return true;
                        MaterialTable.ClassStats stats = materials.get(wall.material, wall.weightClass);
                        float hardness = (stats.hitPoints + stats.armor) * EDGE_WALL_FRACTION;
                        float ablation = ablationFactor(wall.material);
                        float took = Math.min(left[0], hardness * wall.integrity / ablation);
                        wall.integrity = Math.max(0f, wall.integrity - took * ablation / hardness);
                        left[0] -= took;
                        heat[0] = took;
                        touch(out, fromX, fromY, took);
                        if (wall.isDestroyed()) {
                            out.burnedThrough = true;
                            if (report != null) report.structureChanged = true;
                        }
                        return true;
                    }
                });
        end();
        return out;
    }

    /** Burns into an intact wall tile. Returns the energy it took. */
    private float burnWall(Tile tile, int x, int y, float energy, BurnResult out) {
        if (energy <= 0f) return 0f;
        MaterialTable.ClassStats stats = materials.get(tile.material, tile.weightClass);
        float hardness = stats.hitPoints + stats.armor;
        float ablation = ablationFactor(tile.material);
        float integrity = tile.getIntegrity();
        float took = Math.min(energy, hardness * integrity / ablation);
        boolean wasBreach = tile.isHullBreach();
        damagePiece(tile, took * ablation / hardness);
        touch(out, x, y, took);
        if (tile.getIntegrity() <= 0f) {
            tile.setIntegrity(0f);
            out.burnedThrough = true;
            if (!wasBreach && tile.isHullBreach()) addBreach(tile, x, y);
            if (report != null) report.structureChanged = true;
        }
        return took;
    }

    /** A beam crossing an open tile. Returns true if machinery or a person there stopped it. */
    private boolean burnContents(Tile tile, int x, int y, float energy, BurnResult out) {
        Entity system = grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            damageSubsystem(system, sub, energy / subsystemHardness(sub.type), x, y);
            touch(out, x, y, energy);
            tryIgnite(tile, x, y, DamageType.ENERGY, energy / FLOOR_RESISTANCE);
            return true;
        }
        if (crew != null) {
            occupants.clear();
            crew.occupantsAt(ship, x, y, occupants);
            if (occupants.size > 0) {
                injure(occupants.first(), energy / CREW_JOULES_PER_HP, HitCause.FIRE, x, y);
                touch(out, x, y, energy);
                tryIgnite(tile, x, y, DamageType.ENERGY, energy / FLOOR_RESISTANCE);
                return true;
            }
        }
        if (tile.isPowerConduit() && tile.getIntegrity() > 0f) {
            // The beam crosses the deck and cuts any conduit in it.
            damagePiece(tile, energy / CONDUIT_HIT_POINTS);
            if (tile.getIntegrity() <= 0f) {
                grid.gridDirty = true;
                if (report != null) report.structureChanged = true;
            }
        }
        return false;
    }

    private static void touch(BurnResult out, int x, int y, float energy) {
        if (energy <= 0f) return;
        if (!out.touched) {
            out.touched = true;
            out.tileX = x;
            out.tileY = y;
        }
        out.absorbed += energy;
    }

    private void begin(ShipGridComponent grid, Entity ship, RandomXS128 rng, DamageReport report) {
        this.grid = grid;
        this.ship = ship;
        this.rng = rng;
        this.report = report;
        if (report != null && report.ship == null) report.ship = ship;
    }

    private void end() {
        if (grid != null) processBursts();
        grid = null;
        ship = null;
        rng = null;
        report = null;
        occupants.clear();
    }

    // ------------------------------------------------------------------
    // Traversal
    // ------------------------------------------------------------------

    private static class SecondaryRay {
        float x, y, dirX, dirY, budget;
        HitCause cause;
    }

    /** Mutable state of one ray as it walks the grid. */
    private static class Ray {
        float budget;
        float absorbed;
        DamageType damageType;
        HitCause cause;
        Fuse fuse;
        SpallDef spall;
        boolean fromOutside;
        float dirX, dirY;
        final Vector2 normal = new Vector2();
        float ricochetCos;
        float restitution;
        Result result;

        boolean entered;
        boolean pastHull;
        int depth;
        float originX, originY;
        int skipX = Integer.MIN_VALUE, skipY = Integer.MIN_VALUE;
        /** The hull tile the ray was last in, to tell a ray running along the hull's skin; MIN_VALUE if none. */
        int hullX = Integer.MIN_VALUE, hullY = Integer.MIN_VALUE;
    }

    private void trace(final Ray ray, float startX, float startY) {
        ray.originX = startX;
        ray.originY = startY;
        float maxT = grid.width + grid.height + 2f * START_OUTSIDE_TILES + 2f;
        GridRayTraversal.traverse(startX, startY, ray.dirX, ray.dirY, maxT, new GridRayTraversal.Visitor() {
            @Override
            public boolean tile(int x, int y, float tEnter, float tExit) {
                return visitTile(ray, x, y, tEnter);
            }

            @Override
            public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                if (!ray.entered) return true;
                return visitEdge(ray, fromX, fromY, toX, toY);
            }
        });
        if (ray.result != null && ray.entered && ray.result.outcome == Result.Outcome.MISSED) {
            // Ran off the end of the walk still inside: treat as spent.
            ray.result.outcome = ray.pastHull ? Result.Outcome.PENETRATED : Result.Outcome.STOPPED;
        }
    }

    /** @return false to stop the ray. */
    private boolean visitTile(Ray ray, int x, int y, float tEnter) {
        // Secondaries start inside the tile that spawned them, which has already been resolved.
        if (x == ray.skipX && y == ray.skipY) return true;
        Tile tile = grid.getTile(x, y);
        boolean outside = tile == null || tile.isVoid();

        if (!ray.entered) {
            if (outside) return true;
            ray.entered = true;
            if (ray.result != null) {
                ray.result.entryX = x;
                ray.result.entryY = y;
            }
            if (ray.fromOutside && tile.isWall()) return enterHull(ray, tile, x, y);
        }

        if (outside) {
            // Out the far side.
            if (ray.result != null) {
                ray.result.outcome = ray.budget > 0f ? Result.Outcome.OVER_PENETRATED : Result.Outcome.PENETRATED;
                ray.result.exitPoint.set(ray.originX + ray.dirX * tEnter, ray.originY + ray.dirY * tEnter);
            }
            return false;
        }

        boolean wasInHull = ray.hullX != Integer.MIN_VALUE;
        int fromX = ray.hullX, fromY = ray.hullY;
        ray.hullX = ray.hullY = Integer.MIN_VALUE;
        if (tile.isWall()) {
            boolean hull = tile.wallType == Tile.WallType.HULL;
            if (hull && wasInHull && alongSkin(fromX, fromY, x, y)) return glanceOut(ray, tEnter);
            if (hull) {
                ray.hullX = x;
                ray.hullY = y;
            }
            if (tile.getIntegrity() <= 0f) {
                // A hole.
                if (hull) ray.pastHull = true;
                return advance(ray, x, y, tile);
            }
            boolean wasBreach = tile.isHullBreach();
            hitStructure(ray, tile, x, y, 1f);
            if (tile.getIntegrity() <= 0f) {
                if (hull && !wasBreach && tile.isHullBreach()) addBreach(tile, x, y);
                if (report != null) report.structureChanged = true;
            }
            if (hull && ray.budget > 0f && !ray.pastHull) penetratedHull(ray, x, y);
            return advance(ray, x, y, tile);
        }

        // Open floor: fittings, machinery and people.
        hitContents(ray, tile, x, y);
        return advance(ray, x, y, tile);
    }

    /** The first solid tile from outside: incidence, ricochet, then the normal wall hit. */
    private boolean enterHull(Ray ray, Tile tile, int x, int y) {
        Vector2 n = ray.normal;
        HullPiece piece = tile.piece;
        if (piece != null && piece.hasSlopedFace()) n = piece.faceNormal();
        // cos of the angle between the incoming direction reversed and the outward normal
        float cos = -(ray.dirX * n.x + ray.dirY * n.y);
        float incidence = Math.max(cos, MIN_INCIDENCE_COS);
        float scale = 1f / incidence;

        MaterialTable.ClassStats stats = materials.get(tile.material, tile.weightClass);
        float integrity = tile.getIntegrity();
        float resistance = (stats.armor + stats.hitPoints) * integrity * scale;

        if (integrity > 0f && cos < ray.ricochetCos && ray.budget < resistance) {
            // Glance: scrape the hull and reflect off the surface.
            float scrape = ray.budget * RICOCHET_SCRAPE;
            damagePiece(tile, scrape / (stats.hitPoints * scale));
            ray.budget -= scrape;
            ray.absorbed += scrape;
            if (ray.result != null) {
                float dot = ray.dirX * n.x + ray.dirY * n.y;
                ray.result.ricochetDir.set(ray.dirX - 2f * dot * n.x, ray.dirY - 2f * dot * n.y).nor();
                ray.result.ricochetSpeedScale = ray.restitution;
                ray.result.outcome = Result.Outcome.RICOCHET;
            }
            return false;
        }

        if (integrity <= 0f) {
            // Straight through an existing hole.
            ray.pastHull = true;
            if (tile.wallType == Tile.WallType.HULL) {
                ray.hullX = x;
                ray.hullY = y;
            }
            return advance(ray, x, y, tile);
        }

        boolean hull = tile.wallType == Tile.WallType.HULL;
        if (hull) {
            ray.hullX = x;
            ray.hullY = y;
        }
        boolean wasBreach = tile.isHullBreach();
        hitStructure(ray, tile, x, y, scale);
        if (tile.getIntegrity() <= 0f) {
            if (hull && !wasBreach && tile.isHullBreach()) addBreach(tile, x, y);
            if (report != null) report.structureChanged = true;
        }
        if (ray.budget > 0f) penetratedHull(ray, x, y);
        return advance(ray, x, y, tile);
    }

    /**
     * True if the step from hull tile (fx, fy) to hull tile (x, y) runs along the hull's skin: both
     * face open space on the same side, square to the step. The hull is a thin skin drawn a tile
     * thick, so a round going that way is grazing the plating, not boring along inside it (round 2
     * playtest: railgun slugs down a corvette's length took out a whole row of hull each).
     */
    private boolean alongSkin(int fx, int fy, int x, int y) {
        int dx = x - fx, dy = y - fy;
        if (Math.abs(dx) + Math.abs(dy) != 1) return false;
        for (int side = -1; side <= 1; side += 2) {
            int sx = dy * side, sy = dx * side;
            if (open(fx + sx, fy + sy) && open(x + sx, y + sy)) return true;
        }
        return false;
    }

    private boolean open(int x, int y) {
        Tile t = grid.getTile(x, y);
        return t == null || t.isVoid();
    }

    /** A round grazing along the hull's skin leaves it with what it has left. */
    private boolean glanceOut(Ray ray, float t) {
        if (ray.result != null) {
            ray.result.outcome = Result.Outcome.OVER_PENETRATED;
            ray.result.exitPoint.set(ray.originX + ray.dirX * t, ray.originY + ray.dirY * t);
        }
        return false;
    }

    private void penetratedHull(Ray ray, int x, int y) {
        ray.pastHull = true;
        SpallDef spall = ray.spall;
        if (spall == null || spall.count <= 0 || spall.fraction <= 0f) return;
        // Spall is driven by what the hull tile absorbed, paid for out of the round's budget.
        float pool = Math.min(ray.budget, spall.fraction * lastAbsorbed);
        if (pool <= 0f) return;
        ray.budget -= pool;
        float each = pool / spall.count;
        float baseAngle = MathUtils.atan2(ray.dirY, ray.dirX);
        float half = spall.angleDeg * MathUtils.degreesToRadians;
        // Start from the tile the round is in, just past the point it entered.
        float sx = x + 0.5f, sy = y + 0.5f;
        for (int i = 0; i < spall.count; i++) {
            float a = baseAngle + (rng.nextFloat() * 2f - 1f) * half;
            queueSecondary(sx, sy, MathUtils.cos(a), MathUtils.sin(a), each, HitCause.SPALL);
        }
    }

    /**
     * Called after a tile has been resolved. Stops a spent ray and fires delayed and contact
     * fuses once the round is inside.
     */
    private boolean advance(Ray ray, int x, int y, Tile tile) {
        if (ray.pastHull && !tile.isVoid()) ray.depth++;
        Fuse fuse = ray.fuse;
        if (fuse != null && ray.pastHull && fuse.type == Fuse.Type.DELAYED && ray.depth > fuse.depthTiles) {
            detonateHere(ray, x, y);
            return false;
        }
        if (ray.budget <= 0f) {
            ray.budget = 0f;
            if (ray.result != null) {
                ray.result.outcome = ray.pastHull ? Result.Outcome.PENETRATED : Result.Outcome.STOPPED;
                if (fuse != null && fuse.type != Fuse.Type.PROXIMITY) detonateHere(ray, x, y);
            }
            return false;
        }
        return true;
    }

    private void detonateHere(Ray ray, int x, int y) {
        if (ray.result == null) return;
        ray.result.detonationX = x;
        ray.result.detonationY = y;
        if (ray.result.outcome == Result.Outcome.MISSED) ray.result.outcome = Result.Outcome.PENETRATED;
    }

    private boolean visitEdge(Ray ray, int fromX, int fromY, int toX, int toY) {
        EdgeWall wall = grid.getEdgeBetween(fromX, fromY, toX, toY);
        // An open bulkhead door is a gap the round flies through.
        if (wall == null || !wall.blocks()) return true;
        MaterialTable.ClassStats stats = materials.get(wall.material, wall.weightClass);
        float armor = stats.armor * EDGE_WALL_FRACTION * wall.integrity;
        float hp = stats.hitPoints * EDGE_WALL_FRACTION;
        float stripped = Math.min(ray.budget, armor);
        ray.budget -= stripped;
        float absorbed = Math.min(ray.budget, hp * wall.integrity);
        ray.budget -= absorbed;
        wall.integrity = Math.max(0f, wall.integrity - (absorbed + stripped * ARMOR_WEAR) / hp);
        ray.absorbed += stripped + absorbed;
        if (wall.isDestroyed() && report != null) report.structureChanged = true;
        if (ray.budget <= 0f) {
            ray.budget = 0f;
            if (ray.result != null) {
                ray.result.outcome = ray.pastHull ? Result.Outcome.PENETRATED : Result.Outcome.STOPPED;
                Fuse fuse = ray.fuse;
                if (fuse != null && fuse.type != Fuse.Type.PROXIMITY) detonateHere(ray, fromX, fromY);
            }
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Damage to pieces and contents
    // ------------------------------------------------------------------

    private float lastAbsorbed;

    /** Armor strip then absorb, for a wall tile or hull piece. {@code scale} is the incidence factor. */
    private void hitStructure(Ray ray, Tile tile, int x, int y, float scale) {
        MaterialTable.ClassStats stats = materials.get(tile.material, tile.weightClass);
        float integrity = tile.getIntegrity();
        float hp = stats.hitPoints * scale;
        float stripped = Math.min(ray.budget, stats.armor * integrity * scale);
        ray.budget -= stripped;
        float absorbed = Math.min(ray.budget, hp * integrity);
        ray.budget -= absorbed;
        damagePiece(tile, (absorbed + stripped * ARMOR_WEAR) / hp);
        lastAbsorbed = stripped + absorbed;
        ray.absorbed += lastAbsorbed;
    }

    private void damagePiece(Tile tile, float integrityLoss) {
        tile.setIntegrity(tile.getIntegrity() - integrityLoss);
    }

    /** A ray crossing an open floor tile. */
    private void hitContents(Ray ray, Tile tile, int x, int y) {
        float before = ray.budget;

        // Machinery.
        Entity system = grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            float hardness = subsystemHardness(sub.type);
            float take = Math.min(ray.budget, hardness * SUBSYSTEM_TILE_SHARE * sub.health);
            ray.budget -= take;
            damageSubsystem(system, sub, take / hardness, x, y);
        }

        // People. They barely slow a round down.
        if (crew != null && ray.budget > 0f) {
            occupants.clear();
            crew.occupantsAt(ship, x, y, occupants);
            for (int i = 0; i < occupants.size && ray.budget > 0f; i++) {
                float take = Math.min(ray.budget, CREW_RESISTANCE);
                ray.budget -= take;
                injure(occupants.get(i), take / CREW_JOULES_PER_HP, ray.cause, x, y);
            }
        }

        // Deck fittings and conduit.
        float take = Math.min(ray.budget, FLOOR_RESISTANCE);
        ray.budget -= take;
        if (tile.isPowerConduit()) {
            float was = tile.getIntegrity();
            damagePiece(tile, take / CONDUIT_HIT_POINTS);
            if (was > 0f && tile.getIntegrity() <= 0f) {
                grid.gridDirty = true;
                if (report != null) report.structureChanged = true;
            } else if (DamageStates.maybeShortConduit(grid, x, y, was - tile.getIntegrity(), rng) && report != null) {
                report.shorts.add(pack(x, y));
            }
        } else {
            damagePiece(tile, take / materials.get(tile.material, tile.weightClass).hitPoints);
        }

        float spent = before - ray.budget;
        ray.absorbed += spent;
        tryIgnite(tile, x, y, ray.damageType, spent / FLOOR_RESISTANCE);
    }

    private void damageSubsystem(Entity system, SubsystemComponent sub, float healthLoss, int x, int y) {
        if (healthLoss <= 0f) return;
        sub.health = Math.max(0f, sub.health - healthLoss);
        if (sub.health < DISABLE_THRESHOLD) sub.isOperational = false;
        if (DamageStates.rollBurst(sub, healthLoss, rng)) {
            if (!pendingBursts.contains(sub, true)) pendingBursts.add(sub);
        } else if (DamageStates.maybeShort(grid, sub, healthLoss, x, y, rng) && report != null) {
            report.shorts.add(pack(x, y));
        }
        if (report != null && !report.damagedSubsystems.contains(system, true)) {
            report.damagedSubsystems.add(system);
            report.subsystemTiles.add(pack(x, y));
        }
    }

    private void injure(Entity person, float healthPoints, HitCause cause, int x, int y) {
        crew.applyDamage(person, healthPoints, cause);
        if (report != null && !report.injuredCrew.contains(person, true)) {
            report.injuredCrew.add(person);
            report.crewTiles.add(pack(x, y));
        }
    }

    /**
     * Lights a flammable tile with chance {@code (fireChance(type) + material fire_chance) * intensity},
     * intensity clamped to 1.
     */
    private void tryIgnite(Tile tile, int x, int y, DamageType type, float intensity) {
        if (!tile.isFlammable() || tile.isBurning()) return;
        float chance = (type.fireChance() + materialFireChance(tile.material)) * Math.min(1f, intensity);
        if (chance <= 0f || rng.nextFloat() >= chance) return;
        if (tile.ignite() && report != null) report.fires.add(pack(x, y));
    }

    private void addBreach(Tile tile, int x, int y) {
        if (report != null) report.breaches.add(pack(x, y));
    }

    private int pack(int x, int y) {
        return y * grid.width + x;
    }

    // ------------------------------------------------------------------
    // Blast and secondaries
    // ------------------------------------------------------------------

    private final IntArray frontier = new IntArray();
    private final IntArray nextFrontier = new IntArray();

    /**
     * Overpressure flood fill out to the blast radius, stopped by intact walls and closed doors.
     * Air carries the blast, so damage scales with pressure at the detonation tile.
     */
    private float blast(int cx, int cy, WarheadDef warhead) {
        return blast(cx, cy, warhead.blastRadiusTiles, warhead.blastDamage);
    }

    /** {@link #blast(int, int, WarheadDef)} with {@code blastDamage} J at the middle, out to {@code radius} tiles. */
    private float blast(int cx, int cy, int radius, float blastDamage) {
        float pressure = grid.pressureRatioAt(cx, cy);
        if (pressure <= 0f || radius <= 0 || blastDamage <= 0f || !grid.inBounds(cx, cy)) return 0f;

        boolean[] seen = new boolean[grid.width * grid.height];
        frontier.clear();
        frontier.add(pack(cx, cy));
        seen[pack(cx, cy)] = true;
        float total = 0f;
        for (int d = 0; d <= radius && frontier.size > 0; d++) {
            float energy = blastDamage * pressure * (1f - d / (radius + 1f));
            nextFrontier.clear();
            for (int i = 0; i < frontier.size; i++) {
                int idx = frontier.get(i);
                int x = idx % grid.width, y = idx / grid.width;
                Tile tile = grid.getTile(x, y);
                total += blastTile(tile, x, y, energy);
                // Only open space carries the blast onward.
                if (!tile.isOpen() || d == radius) continue;
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    int n = pack(nx, ny);
                    if (seen[n] || grid.isEdgeBlocked(x, y, nx, ny)) continue;
                    Tile nt = grid.getTile(nx, ny);
                    if (nt.isVoid()) continue;
                    seen[n] = true;
                    nextFrontier.add(n);
                }
            }
            frontier.clear();
            frontier.addAll(nextFrontier);
        }
        return total;
    }

    private float blastTile(Tile tile, int x, int y, float energy) {
        if (energy <= 0f) return 0f;
        if (tile.isWall()) {
            if (tile.getIntegrity() <= 0f) return 0f;
            MaterialTable.ClassStats stats = materials.get(tile.material, tile.weightClass);
            boolean wasBreach = tile.isHullBreach();
            float take = Math.min(energy, stats.hitPoints * tile.getIntegrity());
            damagePiece(tile, take / stats.hitPoints);
            if (tile.getIntegrity() <= 0f) {
                if (!wasBreach && tile.isHullBreach()) addBreach(tile, x, y);
                if (report != null) report.structureChanged = true;
            }
            return take;
        }
        float spent = 0f;
        Entity system = grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            float hardness = subsystemHardness(sub.type);
            float take = Math.min(energy, hardness * sub.health);
            damageSubsystem(system, sub, take / hardness, x, y);
            spent += take;
        }
        if (crew != null) {
            occupants.clear();
            crew.occupantsAt(ship, x, y, occupants);
            for (int i = 0; i < occupants.size; i++) {
                // Overpressure hits everyone on the tile without being used up by them.
                injure(occupants.get(i), 100f * energy / CREW_BLAST_LETHAL, HitCause.BLAST, x, y);
            }
        }
        tryIgnite(tile, x, y, DamageType.EXPLOSIVE, energy / FLOOR_RESISTANCE);
        return spent;
    }

    /** Tanks this call's hits burst, set off once the rays are done. */
    private final Array<SubsystemComponent> pendingBursts = new Array<>();
    /** Most bursts one hit can chain into. */
    private static final int MAX_CHAINED_BURSTS = 8;

    /** Bursts every tank the hits left bursting; a large burst's blast can burst others. */
    private void processBursts() {
        for (int n = 0; pendingBursts.size > 0 && n < MAX_CHAINED_BURSTS; n++) {
            SubsystemComponent tank = pendingBursts.removeIndex(0);
            if (tank.contents <= 0) continue;
            DamageStates.Incident burst = DamageStates.burst(grid, tank, rng);
            if (report != null) {
                report.bursts.add(pack(burst.x, burst.y));
                if (burst.fire) report.fires.add(pack(burst.x, burst.y));
                report.structureChanged = true;
            }
            if (burst.blastEnergy > 0f) blast(burst.x, burst.y, DamageStates.BLAST_RADIUS, burst.blastEnergy);
        }
        pendingBursts.clear();
    }

    private void queueSecondary(float x, float y, float dirX, float dirY, float budget, HitCause cause) {
        SecondaryRay s = new SecondaryRay();
        s.x = x;
        s.y = y;
        s.dirX = dirX;
        s.dirY = dirY;
        s.budget = budget;
        s.cause = cause;
        secondaries.add(s);
    }

    /** Runs queued spall and fragment rays from inside the hull. They can't spawn more. */
    private float runSecondaries(DamageType damageType) {
        float total = 0f;
        while (secondaries.size > 0) {
            SecondaryRay s = secondaries.pop();
            Ray ray = new Ray();
            ray.budget = s.budget;
            ray.damageType = damageType;
            ray.cause = s.cause;
            ray.dirX = s.dirX;
            ray.dirY = s.dirY;
            ray.entered = true;
            ray.pastHull = true;
            ray.skipX = (int) Math.floor(s.x);
            ray.skipY = (int) Math.floor(s.y);
            trace(ray, s.x, s.y);
            total += ray.absorbed;
        }
        return total;
    }
}
