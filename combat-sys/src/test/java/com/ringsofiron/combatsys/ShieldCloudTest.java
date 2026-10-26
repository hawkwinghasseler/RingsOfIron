package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.damage.Shields;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.shields.EmitterSpec;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;
import com.ringsofiron.combatsys.shields.ShieldSystem;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The particle-cloud shield (Design.md 5.11): its perimeter, a base layer that stops little,
 * regions gathered ahead of predicted hits, mass scattered and lost, which emitter answers,
 * beams, and power and heat.
 */
class ShieldCloudTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float TILE = 2f;
    /** A 40 m by 10 m hull, so a bow and a stern emitter each cover only their own end. */
    private static final float[] HULL = {-20, -5, 20, -5, 20, 5, -20, 5};

    private PhysicsManager physics;
    private Engine engine;
    private CombatDefs defs;
    private BallisticsSystem ballistics;
    private final List<HullHitEvent> hits = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        CombatEvents events = new CombatEvents();
        defs = CombatDefs.createDefault();
        engine.addSystem(new PhysicsUpdateSystem(physics));
        ballistics = new BallisticsSystem(physics, events);
        engine.addSystem(ballistics);
        engine.addSystem(new ShieldSystem(physics));
        engine.addSystem(new ImpactResolutionSystem(physics, events, defs, ballistics, MaterialTable.createDefault(), 42L));
        events.hullHits.add((signal, hit) -> {
            HullHitEvent copy = new HullHitEvent();
            copy.ship = hit.ship;
            copy.shielded = hit.shielded;
            copy.energyAbsorbed = hit.energyAbsorbed;
            hits.add(copy);
        });
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnShip(float x, float y) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, 0f, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        ship.add(TestShips.box(20, 5, TILE, WeightClass.MEDIUM));
        ship.add(new ShipSubsystemRegistryComponent(ship));
        engine.addEntity(ship);
        return ship;
    }

    private SubsystemComponent emitter(Entity ship, MountFootprint.MountSize size, int x, int y, double stored) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        Entity e = TestShips.install(grid, SubsystemComponent.SubsystemType.SHIELD_EMITTER, size, x, y);
        ship.getComponent(ShipSubsystemRegistryComponent.class).registerSubsystem(e);
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        PowerCircuit c = new PowerCircuit();
        c.capacity = Math.max(stored, 1);
        c.stored = stored;
        s.circuit = c;
        s.powered = true;
        s.isOperational = true;
        return s;
    }

    private void run(float seconds) {
        int ticks = Math.round(seconds / DT);
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    private ShieldCloudComponent cloud(Entity ship) {
        return ShieldModel.ensure(ship);
    }

    /** Fires {@code def} along +x from {@code range} meters off the ship's stern, at height {@code y}. */
    private void fire(String id, float range, float y) {
        ProjectileDef def = defs.projectile(id);
        ballistics.spawn(def, def.mass, -range, y, def.muzzleSpeed, 0f, null);
    }

    @Test
    void thePerimeterSitsBetweenTheEllipseAndATwoMeterOffsetAndClearsTheHull() {
        ShipGridComponent grid = TestShips.box(20, 5, TILE, WeightClass.MEDIUM);
        ShieldPerimeter p = ShieldPerimeter.fit(grid);
        assertEquals(0f, p.cx, 1e-4f);
        assertEquals(20f, p.halfW, 1e-4f);
        Vector2 at = new Vector2();
        for (int i = 0; i < 360; i += 5) {
            float th = i * MathUtils.degreesToRadians;
            float[] out = new float[2];
            assertTrue(p.cross(0f, 0f, MathUtils.cos(th), MathUtils.sin(th), 100f, true, out), "a ray out at " + i + "°");
            p.pointAt(out[0], at);
            // Where the box ends along this bearing
            float hull = Math.min(20f / Math.max(1e-6f, Math.abs(MathUtils.cos(th))), 5f / Math.max(1e-6f, Math.abs(MathUtils.sin(th))));
            assertTrue(at.len() >= hull + 0.99f, "clear of the hull at " + i + "°: " + at.len() + " vs " + hull);
        }
        // Off the bow, halfway between the ellipse (20 sqrt 2 = 28.3 m) and the offset (22 m)
        float[] out = new float[2];
        p.cross(0f, 0f, 1f, 0f, 100f, true, out);
        assertEquals(25.1f, out[1], 1.0f);
        // Off the beam it hugs the hull far closer than the ellipse (7.1 m)
        p.cross(0f, 0f, 0f, 1f, 100f, true, out);
        assertTrue(out[1] < 7.1f && out[1] > 6f, "beam: " + out[1]);
        assertTrue(p.contains(0f, 0f));
        assertFalse(p.contains(0f, 30f));
    }

    @Test
    void anUnpredictedHitMeetsOnlyTheThinBaseLayer() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 10, 2, 1e9);
        ShieldCloudComponent c = cloud(ship);
        assertEquals(300f, c.stock, 1e-3f, "filled to its capacity");
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        ProjectileDef round = defs.projectile("autocannon_30mm");
        float taken = Shields.absorb(ship, body, round, round.mass, 4.5e5f, -20f, 0f, 1f, 0f);
        assertTrue(taken > 0f && taken < 0.06f * 4.5e5f, "a few percent: " + taken);
    }

    @Test
    void aPredictedRoundMeetsAGatheredRegionAndLosesMostOfItsEnergy() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        // 2.5 km out: inside the two-second lookahead for well over a second
        fire("autocannon_30mm", 2500f, 0.5f);
        run(1f);
        ShieldCloudComponent c = cloud(ship);
        assertEquals(1, c.regions.size, "a region where the round will cross");
        run(1f);
        HullHitEvent hit = hits.stream().filter(h -> h.ship == ship).findFirst().orElseThrow();
        float energy = 0.5f * 0.4f * 1500f * 1500f * 0.8f;
        assertTrue(hit.shielded > 0.5f * energy, "most of the round taken: " + hit.shielded + " of " + energy);
        assertTrue(c.scattered > 10f, "mass knocked loose: " + c.scattered);
        assertTrue(c.lost < 1f, "next to none lost to a light round: " + c.lost);
        float scattered = c.scattered;
        run(5f);
        assertTrue(c.scattered < 0.45f * scattered, "it drifts back: " + c.scattered);
    }

    @Test
    void aRailgunSlugPunchesThroughAndTearsMassAway() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        fire("slug_tungsten_20kg", 6000f, 0.5f);
        run(1.2f);
        HullHitEvent hit = hits.stream().filter(h -> h.ship == ship).findFirst().orElseThrow();
        float energy = 0.5f * 20f * 6000f * 6000f;
        assertTrue(hit.shielded < 0.3f * energy, "most of the slug gets through: " + hit.shielded / energy);
        ShieldCloudComponent c = cloud(ship);
        assertTrue(c.lost > 10f, "a slug costs real mass: " + c.lost);
        assertEquals(300f - c.lost, c.stock, 1e-2f);
    }

    @Test
    void theClosestEmitterInBearingAnswersAndABusyOneHandsOn() {
        Entity ship = spawnShip(0, 0);
        // Bow and stern emitters: tiles 18 and 1, at +17 m and -17 m
        emitter(ship, MountFootprint.MountSize.S, 18, 2, 1e9);
        SubsystemComponent stern = emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        Entity sternEntity = stern.footprint.installed;
        ShieldCloudComponent c = cloud(ship);
        // A point off the port beam aft of center is the stern emitter's
        float[] out = new float[2];
        c.perimeter.cross(-10f, 0f, 0f, -1f, 50f, true, out);
        assertSame(sternEntity, ShieldModel.owner(c, out[0], 0f, 0f, false, null));
        // The bow end is out of the stern emitter's reach: only the bow emitter can answer there
        c.perimeter.cross(0f, 0f, 1f, 0f, 50f, true, out);
        assertNotSame(sternEntity, ShieldModel.owner(c, out[0], 0f, 0f, false, null));
        // Two threats aft: the stern emitter's one region takes the heavier, the other falls to the bow emitter
        Array<ShieldModel.Threat> threats = new Array<>();
        float[] a = new float[2], b = new float[2];
        // Both in reach of both emitters, and both closer in bearing to the stern one
        c.perimeter.cross(-2f, 0f, 0f, -1f, 50f, true, a);
        c.perimeter.cross(-1f, 0f, 0f, 1f, 50f, true, b);
        threats.add(threat("heavy", a[0], 1e8f));
        threats.add(threat("light", b[0], 1e5f));
        ShieldModel.tick(c, threats, 0f, 0f, DT);
        assertEquals(2, c.regions.size);
        for (ShieldCloudComponent.Region r : c.regions) {
            if (r.threat.equals("heavy")) assertSame(sternEntity, r.emitter);
            else assertNotSame(sternEntity, r.emitter, "the busy stern emitter handed the light one on");
        }
    }

    private static ShieldModel.Threat threat(Object key, float s, float weight) {
        ShieldModel.Threat t = new ShieldModel.Threat();
        t.key = key;
        t.s = s;
        t.eta = 1f;
        t.weight = weight;
        return t;
    }

    @Test
    void aNarrowRegionIsDenserOnTargetButNothingTwoWidthsOff() {
        ShieldCloudComponent.Region r = new ShieldCloudComponent.Region();
        r.spec = EmitterSpec.S;
        r.mass = 150f;
        float peak = 150f / (EmitterSpec.S.width * ShieldCloudComponent.DEPTH);
        assertEquals(peak, r.densityAt(0f), 1e-4f);
        assertEquals(0.5f * peak, r.densityAt(0.5f * EmitterSpec.S.width), 1e-4f);
        assertEquals(0f, r.densityAt(EmitterSpec.S.width), 1e-6f);
    }

    @Test
    void aBeamMeetsOnlyTheBaseLayerAtFirstThenARegionThatGathers() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        float tick = 4e6f * DT;
        float first = Shields.absorbBeam(ship, body, tick, -20f, 0f, 1f, 0f, DT);
        float later = 0f;
        for (int i = 0; i < 60; i++) {
            later = Shields.absorbBeam(ship, body, tick, -20f, 0f, 1f, 0f, DT);
            engine.update(DT);
        }
        assertTrue(first < 0.05f * tick, "the first touch meets the base layer: " + first / tick);
        assertTrue(later > 3f * first, "a second on, a region has gathered: " + later / tick);
    }

    @Test
    void anEmitterThatOverheatsTripsAndLetsItsShareGo() {
        Entity ship = spawnShip(0, 0);
        SubsystemComponent s = emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        ShieldCloudComponent c = cloud(ship);
        run(0.1f);
        assertTrue(Shields.up(ship));
        assertTrue(c.emitters.get(s.footprint.installed).draw > 2.5e5f, "holding 300 kg costs about 300 kW");
        // Over the limit: at rest its radiators outrun the holding draw, so it only trips from a hot start
        c.emitters.get(s.footprint.installed).heat = EmitterSpec.S.heatTrip + 1e6f;
        run(0.1f);
        assertTrue(c.emitters.get(s.footprint.installed).tripped);
        assertEquals(0f, c.cloud(), 1e-6f, "its only emitter tripped: no cloud");
        assertFalse(Shields.up(ship));
    }

    @Test
    void anUnpoweredEmitterCannotHoldTheCloud() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e3);
        run(0.2f);
        assertFalse(Shields.up(ship), "1 kJ in the store doesn't hold 300 kg");
    }

    @Test
    void aShipsOwnRoundsAreNoThreatToItsCloud() {
        Entity ship = spawnShip(0, 0);
        emitter(ship, MountFootprint.MountSize.S, 1, 2, 1e9);
        ProjectileDef def = defs.projectile("autocannon_30mm");
        // Fired from inside the bubble outward, and from outside toward it, both its own
        ballistics.spawn(def, def.mass, -400f, 0.5f, def.muzzleSpeed, 0f, ship);
        run(0.05f);
        assertEquals(0, cloud(ship).regions.size);
    }
}
