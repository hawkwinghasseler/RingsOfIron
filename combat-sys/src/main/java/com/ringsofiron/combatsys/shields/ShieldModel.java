package com.ringsofiron.combatsys.shields;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * The particle-cloud shield's rules (documentation/combat-sys/Design.md, 5.11): who holds the
 * cloud, which emitter answers for a point, how regions gather, what a hit absorbs and scatters,
 * and what it all costs in power and heat. {@link ShieldSystem} runs {@link #tick} on the fixed
 * tick; {@link com.ringsofiron.combatsys.damage.Shields} calls {@link #absorbRound} and
 * {@link #absorbBeam} as hits resolve.
 */
public final class ShieldModel {
    /** Stopping power of a packed cloud against a round: absorbed = 1 - exp(-K sigma / sectional density). */
    public static final float K = 23f;
    /** The same against a beam, per kg/m^2 (beams have no sectional density). */
    public static final float K_BEAM = 0.012f;
    /** Most of the cloud the emitters can pull into regions at once. */
    public static final float CONCENTRATION_BUDGET = 0.6f;
    /** Width of cloud a round plows through, meters: the mass it scatters is the density over this, through the depth. */
    public static final float HIT_WIDTH = 1f;
    /** Seconds for scattered mass to drift back (e-folding); four times that when recapture goes unpaid. */
    public static final float REJOIN = 5f;
    /** How far ahead the emitters look for rounds, seconds. */
    public static final float LOOKAHEAD = 2f;
    /** Holding the cloud, W per kg; holding a region, W per kg more. */
    public static final float HOLD_W_PER_KG = 1_000f, REGION_W_PER_KG = 500f;
    /** Recapturing scattered mass costs its kinetic energy (capped at the retention speed) this many times over. */
    public static final float RECAPTURE_COST = 2f;
    /** Seconds to pack the whole cloud into its emitters, or to put it back out. */
    public static final float STOW_TIME = 3f;
    /** Seconds (e-folding) for mass adrift to be lost, and for working emitters with room to pull it back. */
    public static final float ADRIFT_LIFE = 6f, ADRIFT_RECAPTURE = 1f;
    /** Seconds a round's region stands after the round has passed inside the perimeter. */
    static final float LINGER = 0.1f;
    /** Seconds a beam's region holds after the beam stops touching it. */
    public static final float BEAM_HOLD = 0.5f;
    /** Mass a beam burns off per joule the cloud takes from it, kg/J. */
    public static final float BEAM_ABLATION = 2e-7f;
    /** A beam's weight when the emitters share out their budget, as if a round of this many joules. */
    static final float BEAM_WEIGHT = 1e7f;
    /** Steel, for rounds whose data gives no caliber: a slug three calibers long. */
    static final float STEEL = 7_850f;
    /** An emitter trips at its heat limit and comes back once it has shed half. */
    static final float RESTART_AT = 0.5f;
    /** How often the curve is refitted to the hull, seconds. */
    static final float REFIT_EVERY = 2f;

    private ShieldModel() {
    }

    /** One round or missile headed for the cloud. */
    public static final class Threat {
        public Object key;
        /** Where it will cross, meters along the perimeter, and when, seconds. */
        public float s, eta;
        /** Its kinetic energy relative to the ship, J. */
        public float weight;
    }

    /**
     * {@code ship}'s shield, made on first sight and kept in step with its emitters: null when it
     * has no shield emitter. ({@code comX}, {@code comY}) is the ship's center of mass, body-local.
     */
    public static ShieldCloudComponent ensure(Entity ship) {
        if (ship == null) return null;
        ShieldCloudComponent cloud = ship.getComponent(ShieldCloudComponent.class);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (grid == null || registry == null) return cloud;
        if (cloud == null) {
            boolean any = false;
            for (Entity e : registry.subsystems) any |= isEmitter(e);
            if (!any) return null;
            cloud = new ShieldCloudComponent();
            ship.add(cloud);
        }
        if (cloud.perimeter == null || cloud.fittedTo != grid) {
            cloud.perimeter = ShieldPerimeter.fit(grid);
            cloud.fittedTo = grid;
            cloud.refitIn = REFIT_EVERY;
        }
        float capacity = 0f, holding = 0f;
        for (Entity e : registry.subsystems) {
            if (!isEmitter(e)) continue;
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            ShieldCloudComponent.Emitter em = cloud.emitters.get(e);
            if (em == null) cloud.emitters.put(e, em = new ShieldCloudComponent.Emitter());
            MountFootprint f = s.footprint;
            em.spec = EmitterSpec.of(f == null ? null : f.size);
            if (f != null) {
                em.x = grid.gridOriginLocal.x + (f.originX + 0.5f * f.width()) * grid.tileSize;
                em.y = grid.gridOriginLocal.y + (f.originY + 0.5f * f.height()) * grid.tileSize;
            }
            em.working = up(s) && !em.tripped && !em.starved;
            capacity += em.spec.capacity;
            if (em.working) holding += em.spec.capacity;
        }
        // Emitters taken out of the ship take their regions with them
        for (com.badlogic.gdx.utils.ObjectMap.Entries<Entity, ShieldCloudComponent.Emitter> it = cloud.emitters.entries(); it.hasNext; ) {
            Entity e = it.next().key;
            if (!registry.subsystems.contains(e, true)) it.remove();
        }
        cloud.capacity = capacity;
        if (cloud.stock < 0f) {
            // Filled packed away unless an emitter is working to hold it out: a ship that comes
            // into the world dark, or before its power is routed, doesn't throw its cloud away
            cloud.stock = capacity;
            if (!cloud.raised || holding <= 0f) cloud.stowed = capacity;
        }
        cloud.stock = Math.min(cloud.stock, capacity);
        cloud.stowed = Math.min(cloud.stowed, cloud.stock);
        cloud.adrift = Math.min(cloud.adrift, cloud.stock - cloud.stowed);
        cloud.scattered = Math.min(cloud.scattered, cloud.stock - cloud.stowed - cloud.adrift);
        cloud.holding = capacity > 0f ? holding / capacity : 0f;
        return cloud;
    }

    /**
     * Orders {@code ship}'s shields up or down: lowered, the emitters pack the cloud away inside
     * themselves, safe; raised, they put it back out. Returns the shield, or null if it has none.
     */
    public static ShieldCloudComponent raise(Entity ship, boolean up) {
        ShieldCloudComponent cloud = ensure(ship);
        if (cloud == null) return null;
        cloud.raised = up;
        cloud.loweredForDock = false;
        return cloud;
    }

    static boolean up(SubsystemComponent s) {
        return s != null && s.isOperational && s.circuit != null && !s.isDisabled();
    }

    static boolean isEmitter(Entity e) {
        SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemComponent.SubsystemType.SHIELD_EMITTER;
    }

    /**
     * The emitter that answers for the point {@code s} along the perimeter: of the working ones
     * whose radius reaches it, the closest in bearing from the center of mass; with
     * {@code freeSlot}, the closest that still has a region free ({@code used} counts them).
     * Null when none reaches it.
     */
    public static Entity owner(ShieldCloudComponent cloud, float s, float comX, float comY,
                               boolean freeSlot, com.badlogic.gdx.utils.ObjectIntMap<Entity> used) {
        float px, py;
        com.badlogic.gdx.math.Vector2 p = cloud.perimeter.pointAt(s, new com.badlogic.gdx.math.Vector2());
        px = p.x;
        py = p.y;
        float bearing = MathUtils.atan2(py - comY, px - comX);
        Entity best = null;
        float bestDiff = Float.MAX_VALUE;
        for (com.badlogic.gdx.utils.ObjectMap.Entry<Entity, ShieldCloudComponent.Emitter> e : cloud.emitters) {
            ShieldCloudComponent.Emitter em = e.value;
            if (!em.working) continue;
            float dx = px - em.x, dy = py - em.y;
            if (dx * dx + dy * dy > em.spec.radius * em.spec.radius) continue;
            if (freeSlot && used != null && used.get(e.key, 0) >= em.spec.regions) continue;
            float diff = Math.abs(angleBetween(bearing, MathUtils.atan2(em.y - comY, em.x - comX)));
            // An emitter right on the center of mass has no bearing: it answers last of those in range
            if (Math.abs(em.x - comX) < 1e-3f && Math.abs(em.y - comY) < 1e-3f) diff = MathUtils.PI;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = e.key;
            }
        }
        return best;
    }

    static float angleBetween(float a, float b) {
        float d = (b - a) % MathUtils.PI2;
        if (d > MathUtils.PI) d -= MathUtils.PI2;
        if (d < -MathUtils.PI) d += MathUtils.PI2;
        return d;
    }

    /**
     * One fixed tick of {@code cloud}: regions follow {@code threats}, gather and release; the
     * emitters pay to hold the cloud and their regions and to recapture what was scattered, and
     * heat up and trip; scattered mass drifts back.
     */
    public static void tick(ShieldCloudComponent cloud, Array<Threat> threats, float comX, float comY, float dt) {
        if (cloud.perimeter == null) return;
        if ((cloud.refitIn -= dt) <= 0f && cloud.fittedTo != null) {
            cloud.perimeter = ShieldPerimeter.fit(cloud.fittedTo);
            cloud.refitIn = REFIT_EVERY;
        }
        // Lowered, the emitters gather nothing: they are packing the cloud away
        if (!cloud.raised) threats.clear();
        updateRegions(cloud, threats, comX, comY, dt);
        payAndHeat(cloud, dt);
        flows(cloud, dt);
    }

    /**
     * Where the cloud's mass goes this tick, by what the working emitters can hold: lowered, it packs
     * into them and raised it comes back out, over {@link #STOW_TIME}; mass beyond their hold goes
     * adrift, is lost over {@link #ADRIFT_LIFE} and is pulled back in over {@link #ADRIFT_RECAPTURE}
     * while they have room.
     */
    private static void flows(ShieldCloudComponent cloud, float dt) {
        float held = 0f;
        for (ShieldCloudComponent.Emitter em : cloud.emitters.values()) if (em.working) held += em.spec.capacity;
        float rate = held / STOW_TIME * dt;
        if (!cloud.raised) {
            float move = Math.min(cloud.cloud(), rate);
            cloud.stowed += move;
            for (ShieldCloudComponent.Region r : cloud.regions) r.mass = 0f;
        } else if (cloud.stowed > 0f) {
            float room = Math.max(0f, held - cloud.cloud() - cloud.scattered);
            cloud.stowed -= Math.min(cloud.stowed, Math.min(room, rate));
        }
        // What the working emitters can't hold: the cloud first, then what is scattered
        float over = cloud.cloud() + cloud.scattered - held;
        if (over > 0f) {
            float fromCloud = Math.min(over, cloud.cloud());
            float fromScattered = Math.min(over - fromCloud, cloud.scattered);
            cloud.scattered -= fromScattered;
            cloud.adrift += fromCloud + fromScattered;
            float left = cloud.cloud(), in = cloud.concentrated();
            if (in > left && in > 0f) for (ShieldCloudComponent.Region r : cloud.regions) r.mass *= left / in;
        }
        if (cloud.adrift > 0f) {
            float gone = cloud.adrift * Math.min(1f, dt / ADRIFT_LIFE);
            cloud.adrift -= gone;
            cloud.stock = Math.max(0f, cloud.stock - gone);
            cloud.lost += gone;
            float room = Math.max(0f, held - cloud.cloud() - cloud.scattered);
            float back = Math.min(room, cloud.adrift * Math.min(1f, dt / ADRIFT_RECAPTURE));
            if (back > 0f) {
                cloud.adrift -= back;
                if (cloud.raised) cloud.scattered += back;
                else cloud.stowed += back;
            }
        }
    }

    private static void updateRegions(ShieldCloudComponent cloud, Array<Threat> threats, float comX, float comY, float dt) {
        Array<ShieldCloudComponent.Region> regions = cloud.regions;
        for (ShieldCloudComponent.Region r : regions) r.seen = false;
        // Beams' regions hold a little after the beam leaves; they come first, as they are already hitting
        com.badlogic.gdx.utils.ObjectIntMap<Entity> used = new com.badlogic.gdx.utils.ObjectIntMap<>();
        for (ShieldCloudComponent.Region r : regions) {
            if (!r.beam) continue;
            r.eta -= dt;
            ShieldCloudComponent.Emitter em = cloud.emitters.get(r.emitter);
            if (r.eta > 0f && em != null && em.working) {
                r.seen = true;
                used.getAndIncrement(r.emitter, 0, 1);
            }
        }
        // Heaviest and soonest first, so a busy emitter's regions go where they matter most
        threats.sort((a, b) -> a.weight != b.weight ? Float.compare(b.weight, a.weight) : Float.compare(a.eta, b.eta));
        for (Threat t : threats) {
            Entity owner = owner(cloud, t.s, comX, comY, true, used);
            if (owner == null) continue;
            ShieldCloudComponent.Region r = null;
            for (ShieldCloudComponent.Region q : regions) {
                if (q.threat == t.key && !q.beam) {
                    r = q;
                    break;
                }
            }
            if (r == null) {
                r = new ShieldCloudComponent.Region();
                r.threat = t.key;
                regions.add(r);
            }
            if (r.emitter != owner) {
                r.emitter = owner;
                r.spec = cloud.emitters.get(owner).spec;
            }
            r.s = t.s;
            r.eta = t.eta;
            r.target = t.weight;
            r.seen = true;
            used.getAndIncrement(owner, 0, 1);
        }
        // A round inside the bubble is no longer looked for, but its region stands until it lands
        for (int i = regions.size - 1; i >= 0; i--) {
            ShieldCloudComponent.Region r = regions.get(i);
            if (r.seen) continue;
            ShieldCloudComponent.Emitter em = cloud.emitters.get(r.emitter);
            if (!r.beam && em != null && em.working && (r.eta -= dt) > -LINGER) r.seen = true;
            else regions.removeIndex(i);
        }
        // Share the budget by weight, each region capped by its emitter's density
        float total = 0f;
        for (ShieldCloudComponent.Region r : regions) {
            if (r.beam) r.target = BEAM_WEIGHT;
            total += r.target;
        }
        float cloudMass = cloud.cloud();
        float budget = CONCENTRATION_BUDGET * cloudMass;
        float lambda = cloud.perimeter.length > 0f ? cloudMass / cloud.perimeter.length : 0f;
        for (ShieldCloudComponent.Region r : regions) {
            float want = total > 0f ? Math.min(r.spec.regionCap(), budget * r.target / total) : 0f;
            r.target = want;
            // Mass streams in along the perimeter from both sides at the emitter's cluster speed
            float rate = 2f * r.spec.clusterSpeed * lambda * dt;
            if (r.mass < want) r.mass = Math.min(want, r.mass + rate);
            else r.mass = Math.max(want, r.mass - rate);
        }
        // Never more in regions than the cloud holds (a hit or a trip can shrink it under them)
        float in = cloud.concentrated();
        if (in > budget && in > 0f) {
            float k = budget / in;
            for (ShieldCloudComponent.Region r : regions) r.mass *= k;
        }
    }

    private static void payAndHeat(ShieldCloudComponent cloud, float dt) {
        float pool = cloud.cloud();
        boolean recaptured = true;
        for (com.badlogic.gdx.utils.ObjectMap.Entry<Entity, ShieldCloudComponent.Emitter> e : cloud.emitters) {
            ShieldCloudComponent.Emitter em = e.value;
            SubsystemComponent s = e.key.getComponent(SubsystemComponent.class);
            if (up(s) && !em.tripped) {
                // Each emitter holds its own share of the pool
                float share = cloud.capacity > 0f ? em.spec.capacity / cloud.capacity : 0f;
                float regionMass = 0f;
                for (ShieldCloudComponent.Region r : cloud.regions) if (r.emitter == e.key) regionMass += r.mass;
                double hold = (HOLD_W_PER_KG * pool * share + REGION_W_PER_KG * regionMass) * dt;
                double recapture = em.recaptureOwed * Math.min(1f, dt / REJOIN);
                double paid = s.circuit.draw(hold + recapture);
                em.starved = paid < hold * 0.999;
                em.working = !em.starved;
                double paidRecapture = Math.max(0, paid - hold);
                em.recaptureOwed = (float) Math.max(0, em.recaptureOwed - paidRecapture);
                if (paidRecapture < recapture * 0.9) recaptured = false;
                em.heat += (float) paid;
                em.draw = (float) (paid / dt);
            } else {
                em.working = false;
                em.starved = false;
                em.draw = 0f;
            }
            em.heat = Math.max(0f, em.heat - em.spec.cooling * dt);
            if (!em.tripped && em.heat >= em.spec.heatTrip) {
                em.tripped = true;
                em.working = false;
            } else if (em.tripped && em.heat <= RESTART_AT * em.spec.heatTrip) {
                em.tripped = false;
            }
        }
        // Regions of emitters that stopped working let go
        for (int i = cloud.regions.size - 1; i >= 0; i--) {
            ShieldCloudComponent.Emitter em = cloud.emitters.get(cloud.regions.get(i).emitter);
            if (em == null || !em.working) cloud.regions.removeIndex(i);
        }
        float rejoin = recaptured ? REJOIN : 4f * REJOIN;
        float back = cloud.scattered * Math.min(1f, dt / rejoin);
        cloud.scattered = Math.max(0f, cloud.scattered - back);
        // Lowered, it comes back into the emitters
        if (!cloud.raised) cloud.stowed += back;
    }

    /** Mass per frontal area of a round, kg/m^2: from its caliber, or a steel slug three calibers long. */
    public static float sectionalDensity(ProjectileDef def, float mass) {
        float caliber = def != null ? def.caliber : 0f;
        if (!(caliber > 0f)) caliber = (float) Math.cbrt(4f * mass / (3f * MathUtils.PI * STEEL));
        float area = 0.25f * MathUtils.PI * caliber * caliber;
        return area > 0f ? mass / area : Float.MAX_VALUE;
    }

    /**
     * A round of {@code def} and {@code mass} carrying {@code energy} joules, that came in along
     * body-local unit ({@code dirX}, {@code dirY}) and reached the hull at ({@code lx}, {@code ly}),
     * meets the cloud where its path crossed the perimeter. Returns the joules the cloud took;
     * the mass it plowed through is scattered, some of it lost.
     */
    public static float absorbRound(ShieldCloudComponent cloud, ProjectileDef def, float mass, float energy,
                                     float lx, float ly, float dirX, float dirY, float comX, float comY) {
        if (cloud == null || cloud.perimeter == null || !(energy > 0f) || cloud.cloud() <= 0f) return 0f;
        float s = crossing(cloud.perimeter, lx, ly, dirX, dirY);
        float sigma = cloud.densityAt(s);
        if (!(sigma > 0f)) return 0f;
        float f = 1f - (float) Math.exp(-K * sigma / sectionalDensity(def, mass));
        float absorbed = f * energy;
        float plowed = plow(cloud, s, sigma);
        if (plowed > 0f) scatter(cloud, s, dirX, dirY, absorbed, plowed, comX, comY, false);
        return absorbed;
    }

    /**
     * One tick of a beam carrying {@code energy} joules into the hull at ({@code lx}, {@code ly})
     * along ({@code dirX}, {@code dirY}). The cloud can't see a beam coming: the first touch only
     * meets the base layer, and calls up a region there that thickens while the beam holds.
     */
    public static float absorbBeam(ShieldCloudComponent cloud, float energy, float lx, float ly, float dirX, float dirY,
                                   float comX, float comY, float dt) {
        if (cloud == null || cloud.perimeter == null || !(energy > 0f) || cloud.cloud() <= 0f) return 0f;
        float s = crossing(cloud.perimeter, lx, ly, dirX, dirY);
        float sigma = cloud.densityAt(s);
        float absorbed = (1f - (float) Math.exp(-K_BEAM * sigma)) * energy;
        // The beam's region: refreshed where it already holds, or called up here
        ShieldCloudComponent.Region region = null;
        for (ShieldCloudComponent.Region r : cloud.regions) {
            if (r.beam && Math.abs(cloud.perimeter.between(r.s, s)) < r.spec.width) region = r;
        }
        if (region == null) {
            Entity owner = owner(cloud, s, comX, comY, false, null);
            if (owner != null) {
                region = new ShieldCloudComponent.Region();
                region.beam = true;
                region.threat = region;
                region.emitter = owner;
                region.spec = cloud.emitters.get(owner).spec;
                cloud.regions.add(region);
            }
        }
        if (region != null) {
            region.s = s;
            region.eta = BEAM_HOLD;
        }
        // Burnt off, not thrown: a little mass a tick, from where the beam holds
        float burnt = Math.min(cloud.cloud(), absorbed * BEAM_ABLATION);
        if (region != null) region.mass = Math.max(0f, region.mass - burnt);
        cloud.stock = Math.max(0f, cloud.stock - burnt);
        cloud.lost += burnt;
        if ((cloud.beamLogIn -= dt) <= 0f && absorbed > 0f) {
            cloud.beamLogIn = 0.1f;
            ShieldCloudComponent.Hit h = new ShieldCloudComponent.Hit();
            h.s = s;
            h.dirX = dirX;
            h.dirY = dirY;
            h.absorbed = absorbed;
            h.lost = burnt;
            h.beam = true;
            cloud.log(h);
        }
        return absorbed;
    }

    /** Where a path into the hull at ({@code lx}, {@code ly}) along ({@code dx}, {@code dy}) crossed the perimeter on its way in. */
    static float crossing(ShieldPerimeter p, float lx, float ly, float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float[] out = new float[2];
        if (len > 0f && p.cross(lx, ly, -dx / len, -dy / len, 4f * p.reach + 100f, false, out)) return out[0];
        // Born inside the bubble (a fragment): the point of the curve on its bearing
        float bearing = MathUtils.atan2(ly - p.cy, lx - p.cx);
        int i = ShieldPerimeter.bin(bearing);
        return p.along[i];
    }

    /** Takes the mass in a round's path out of the cloud's regions and base layer; returns it, kg. */
    private static float plow(ShieldCloudComponent cloud, float s, float sigma) {
        float want = Math.min(cloud.cloud(), sigma * HIT_WIDTH * ShieldCloudComponent.DEPTH);
        float fromRegions = 0f;
        for (ShieldCloudComponent.Region r : cloud.regions) {
            float take = Math.min(r.mass, r.densityAt(cloud.perimeter.between(r.s, s)) * HIT_WIDTH * ShieldCloudComponent.DEPTH);
            r.mass -= take;
            fromRegions += take;
        }
        return Math.max(want, fromRegions);
    }

    /** {@code plowed} kg thrown by half of {@code absorbed} joules: what goes faster than the retention speed is lost. */
    private static void scatter(ShieldCloudComponent cloud, float s, float dirX, float dirY, float absorbed, float plowed,
                                float comX, float comY, boolean beam) {
        float speed = (float) Math.sqrt(absorbed / plowed);
        Entity owner = owner(cloud, s, comX, comY, false, null);
        ShieldCloudComponent.Emitter em = owner == null ? null : cloud.emitters.get(owner);
        float retention = em != null ? em.spec.retention : EmitterSpec.S.retention;
        float lost = speed > 0f ? plowed * (float) Math.exp(-retention / speed) : 0f;
        float kept = plowed - lost;
        cloud.stock = Math.max(0f, cloud.stock - lost);
        cloud.lost += lost;
        cloud.scattered += kept;
        if (em != null) {
            float v = Math.min(speed, retention);
            em.recaptureOwed += RECAPTURE_COST * 0.5f * kept * v * v;
        }
        ShieldCloudComponent.Hit h = new ShieldCloudComponent.Hit();
        h.s = s;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        h.dirX = len > 0f ? dirX / len : 0f;
        h.dirY = len > 0f ? dirY / len : 0f;
        h.absorbed = absorbed;
        h.scattered = kept;
        h.lost = lost;
        h.speed = speed;
        h.beam = beam;
        cloud.log(h);
    }
}
