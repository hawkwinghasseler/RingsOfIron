package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boarding a beaten ship, taking it, stripping it for parts and selling or fitting them. */
class BoardingSalvageTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

    GameWorld world;
    GameScenario game;
    /** A faction of the data that is no outlaw, so boarding its ships costs standing. */
    int owner;
    final List<Entity> raiderCrew = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        // The raider flies for a faction of its own, with two crewmen aboard
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = GameScenario.floorTiles(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            raiderCrew.add(CrewFactory.spawn(world.crew, game.raider, "Raider " + i, CrewRole.CREWMAN, RAIDERS, t[0], t[1]));
        }
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private boolean runUntil(int seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < 60 * seconds; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    /** The raider beaten (reactors cold) and latched to the player's ship. */
    private void beatAndLatch() {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(120);
        assertSame(game.raider, world.docking.dockedTo(game.player));
        assertTrue(ShipDisablement.isDisabled(game.raider));
    }

    /**
     * Hands the raider and its crew to the Selene Combine: boarding a faction's ship costs standing
     * with it, where a ship of no one's (or a pirate's) costs nothing. They are at peace with the
     * player, so only a surrendered crew can be taken this way.
     */
    private void ownedBySelene() {
        owner = world.standing.factions().index("selene");
        CrewFactory.ensureBoard(game.raider).faction = owner;
        for (Entity e : raiderCrew) world.crew.crew(e).faction = owner;
    }

    private Entity marine() {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (world.crew.crew(e).role == CrewRole.MARINE && world.crew.homeOf(e) == game.player) return e;
        }
        throw new AssertionError("no marine");
    }

    @Test
    void aShipFitToFightCantBeBoarded() {
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(120);
        assertTrue(world.boarding.order(game.player).contains("disable it first"));
        assertNull(Boarding.of(game.raider));
    }

    @Test
    void aSurrenderedCrewYieldsToTheFirstBoarderAndAreHeld() {
        ownedBySelene();
        beatAndLatch();
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard Raider"), said);
        assertEquals(-Boarding.STANDING_COST, world.standing.standing(owner), 1e-3, "boarding their ship costs standing");

        assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)),
                "taken; the marine is " + world.crew.crew(marine()).activity);
        for (Entity e : raiderCrew) assertTrue(world.crew.crew(e).held, "held");
        assertEquals(2, Boarding.of(game.raider).prisoners);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
        assertFalse(world.crew.hostile(world.crew.crew(marine()), world.crew.crew(raiderCrew.get(0))), "prisoners aren't fought");
        // The party heads home
        assertTrue(runUntil(120, () -> world.crew.position(marine()).ship == game.player), "the marine came back");
    }

    @Test
    void aShipIsTakenOnceNoneOfItsCrewStand() {
        beatAndLatch();
        world.boarding.order(game.player);
        run(60);
        assertFalse(world.boarding.takenBy(game.raider, game.player));
        for (Entity e : raiderCrew) {
            CrewMemberComponent c = world.crew.crew(e);
            c.health = 0.01f;
            c.state = CrewState.UNCONSCIOUS;
        }
        assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)),
                "taken; the marine is " + world.crew.crew(marine()).activity);
    }

    @Test
    void latchedOnWithoutLeaveTheDoorIsForcedBeforeTheBoardersGoIn() {
        ownedBySelene();
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.update(FRAME);
        parkAlongside(game.raider, 0.6f);
        PlayerDocking docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, s -> { });
        try {
            assertEquals("Port armed", docking.toggleArm());
            assertTrue(runUntil(30, () -> world.docking.dockedTo(game.player) == game.raider), "latched");
            assertTrue(runUntil(10, () -> world.docking.isRigid(game.player)), "welded, a walkway between");
            // Its crew knocked out, not struck: a struck ship would open its door
            for (Entity e : raiderCrew) {
                CrewMemberComponent c = world.crew.crew(e);
                c.health = 0.01f;
                c.state = CrewState.UNCONSCIOUS;
            }
            assertEquals(ForceMethod.DoorState.UNPOWERED, world.docking.doorState(game.raider, 0));
            String said = world.boarding.order(game.player);
            assertTrue(said.contains("door is being forced"), said);
            assertTrue(runUntil(300, () -> world.boarding.takenBy(game.raider, game.player)),
                    "taken; the marine is " + world.crew.crew(marine()).activity);
            assertEquals(-Boarding.STANDING_COST - FactionStanding.PRY_COST, world.standing.standing(owner), 1e-3,
                    "boarding and the door both cost standing");
        } finally {
            docking.dispose();
        }
    }

    @Test
    void aStruckShipOpensItsPortToThePlayerSoNoDoorIsForced() {
        ownedBySelene();
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.update(FRAME);
        parkAlongside(game.raider, 0.6f);
        PlayerDocking docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, s -> { });
        try {
            assertEquals("Raider (surrendered) opens its port to you; port armed", docking.toggleArm());
            assertTrue(runUntil(30, () -> world.docking.dockedTo(game.player) == game.raider), "latched");
            assertTrue(runUntil(10, () -> world.docking.isRigid(game.player)), "welded, a walkway between");
            assertTrue(world.docking.dockedWithClearance(game.player), "with its leave");
            assertEquals(ForceMethod.DoorState.OPEN, world.docking.doorState(game.raider, 0));
            String said = world.boarding.order(game.player);
            assertFalse(said.contains("forced"), said);
            assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)),
                    "taken; the marine is " + world.crew.crew(marine()).activity);
            assertEquals(-Boarding.STANDING_COST, world.standing.standing(owner), 1e-3, "boarding costs standing, no door does");
        } finally {
            docking.dispose();
        }
    }

    @Test
    void aShipThatStrikesWhileLatchedOpensItsDoor() {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.update(FRAME);
        parkAlongside(game.raider, 0.6f);
        PlayerDocking docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, s -> { });
        try {
            assertEquals("Port armed", docking.toggleArm());
            assertTrue(runUntil(30, () -> world.docking.dockedTo(game.player) == game.raider), "latched");
            assertTrue(runUntil(10, () -> world.docking.isRigid(game.player)), "welded, a walkway between");
            assertEquals(ForceMethod.DoorState.UNPOWERED, world.docking.doorState(game.raider, 0), "caught unarmed, shut");
            world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
            run(2);
            assertEquals(ForceMethod.DoorState.OPEN, world.docking.doorState(game.raider, 0));
        } finally {
            docking.dispose();
        }
    }

    /** Puts {@code other} beside the player's ship, its port facing the player's {@code gap} meters off. */
    private void parkAlongside(Entity other, float gap) {
        Body ship = body(game.player), ob = body(other);
        DockingPort pp = port(game.player), op = port(other);
        Vector2 n = ship.getWorldVector(new Vector2(pp.normalX, pp.normalY)).cpy();
        float angle = MathUtils.atan2(n.y, n.x) + MathUtils.PI
                - MathUtils.atan2(op.normalY, op.normalX);
        float u = PhysicsManager.UNITS_PER_METER;
        Vector2 face = new Vector2(ship.getWorldPoint(
                new Vector2(pp.faceX() * u, pp.faceY() * u))).scl(PhysicsManager.METERS_PER_UNIT);
        Vector2 target = face.mulAdd(n, gap);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        PhysicsManager.setPosition(ob, target.x - (c * op.faceX() - s * op.faceY()),
                target.y - (s * op.faceX() + c * op.faceY()), angle);
        Vector2 v = PhysicsManager.getVelocity(ship, new Vector2());
        PhysicsManager.setVelocity(ob, v.x, v.y);
        ob.setAngularVelocity(0f);
        ship.setAngularVelocity(0f);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).ports[0];
    }

    @Test
    void undockingCallsTheBoardingOff() {
        beatAndLatch();
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.boarding.order(game.player);
        run(10);
        world.docking.undock(game.player);
        run(120);
        assertNull(Boarding.of(game.raider));
    }

    @Test
    void aWreckNoOneDefendsIsStrippedAndItsPartsComeHomeAsCrates() {
        beatAndLatch();
        for (Entity e : raiderCrew) world.crew.crew(e).state = CrewState.DEAD;
        // A turret with a gun on it
        WeaponMountComponent gun = null;
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) {
            if (m.def != null && !m.def.pointDefense && m.subsystem != null) gun = m;
        }
        assertNotNull(gun);
        String part = Salvage.PART + gun.def.id;
        MountFootprint f = gun.subsystem.getComponent(SubsystemComponent.class).footprint;
        // Open the way in, as forcing it would
        world.boarding.order(game.player);

        String said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.startsWith("Engineers ordered to strip"), said);
        assertTrue(world.boarding.takenBy(game.raider, game.player), "no one defends it: taken on the spot");
        CargoHoldComponent hold = game.player.getComponent(CargoHoldComponent.class);
        assertTrue(runUntil(600, () -> hold.amount(part) >= 1), "the part came home: "
                + world.salvage.notices + ", engineer " + activityOf(CrewRole.ENGINEER));
        assertNull(gun.def, "the gun is gone from the raider");
        assertTrue(game.raider.getComponent(SalvagedComponent.class).has(f.originX, f.originY));
        assertTrue(world.salvage.parts(game.raider).stream().noneMatch(p -> p.system.footprint == f), "only stripped once");

        // Any station buys it for a quarter of its price
        TraderComponent trader = game.player.getComponent(TraderComponent.class);
        double before = trader.credits, price = world.salvage.price(part);
        assertTrue(price > 0);
        assertEquals(1, world.cargoSettlement.sellParts(game.player, part, 5), 1e-9);
        assertEquals(before + price * Salvage.SALE_SHARE, trader.credits, 1e-6);
        assertEquals(0, hold.amount(part), 1e-9);
    }

    @Test
    void askingWhetherAWreckCanBeStrippedTakesNothing() {
        ownedBySelene();
        beatAndLatch();
        for (Entity e : raiderCrew) world.crew.crew(e).state = CrewState.DEAD;
        float standing = world.standing.standing(owner);

        assertNull(world.salvage.refusal(game.player, game.raider), "no one defends it: it may be stripped");
        assertNull(Boarding.of(game.raider), "but checking is not boarding");
        assertFalse(world.boarding.takenBy(game.raider, game.player));
        assertEquals(standing, world.standing.standing(owner), 1e-6, "and costs no standing");

        // Ordering the work takes it, and only then is the boarding charged
        assertTrue(world.salvage.takeCargo(game.player, "ore").startsWith("No ore aboard"));
        assertTrue(world.boarding.takenBy(game.raider, game.player));
        assertEquals(standing - Boarding.STANDING_COST, world.standing.standing(owner), 1e-6);
    }

    @Test
    void cargoAboardATakenShipIsCarriedOver() {
        beatAndLatch();
        CargoHoldComponent theirs = game.raider.getComponent(CargoHoldComponent.class);
        assertNotNull(theirs);
        theirs.load("water", 4, 1, 1000);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.boarding.order(game.player);
        assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)));
        String said = world.salvage.takeCargo(game.player, "water");
        assertTrue(said.startsWith("Crew ordered to carry"), said);
        CargoHoldComponent ours = game.player.getComponent(CargoHoldComponent.class);
        assertTrue(runUntil(600, () -> ours.amount("water") >= 4 - 1e-6), "carried over: " + ours.amount("water"));
        assertEquals(0, theirs.amount("water"), 1e-6);
    }

    @Test
    void aYardFitsASalvagedWeaponForNothing() {
        Entity highport = world.engine.getEntity(game.highport);
        DockingPortsComponent ports = highport.getComponent(DockingPortsComponent.class);
        int small = -1;
        for (int i = 0; i < ports.ports.length && small < 0; i++) {
            if (ports.ports[i].size == MountFootprint.MountSize.S) small = i;
        }
        world.docking.dockInPlace(game.player, 0, highport, small);
        run(120);
        WeaponsComponent weapons = game.player.getComponent(WeaponsComponent.class);
        int hardpoint = -1;
        String other = null;
        for (int i = 0; i < weapons.mounts.size && hardpoint < 0; i++) {
            WeaponMountComponent m = weapons.mounts.get(i);
            if (m.def == null || m.def.pointDefense) continue;
            for (WeaponDef d : world.yard.offers()) {
                if (d != m.def && !d.pointDefense) {
                    hardpoint = i;
                    other = d.id;
                    break;
                }
            }
        }
        assertTrue(hardpoint >= 0);
        CargoHoldComponent hold = game.player.getComponent(CargoHoldComponent.class);
        hold.load(Salvage.PART + other, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
        TraderComponent trader = game.player.getComponent(TraderComponent.class);
        double credits = trader.credits;
        String said = world.yard.orderSalvagedRefit(game.player, highport, hardpoint, other);
        assertTrue(said.startsWith("The technicians are fitting the salvaged"), said);
        assertEquals(0, hold.amount(Salvage.PART + other), 1e-9, "out of the hold");
        final int h = hardpoint;
        final String id = other;
        assertTrue(runUntil(600, () -> weapons.mounts.get(h).def != null && weapons.mounts.get(h).def.id.equals(id)), "fitted");
        assertTrue(trader.credits >= credits, "nothing paid (the old gun traded in)");
        assertEquals(StationYard.USED_CONDITION,
                weapons.mounts.get(h).subsystem.getComponent(SubsystemComponent.class).health, 1e-3f, "fitted like a used one");
    }

    private String activityOf(CrewRole role) {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (world.crew.crew(e).role == role && world.crew.homeOf(e) == game.player) {
                return world.crew.crew(e).activity + " on " + (world.crew.position(e).ship == game.player ? "ours" : "theirs");
            }
        }
        return "none";
    }
}
