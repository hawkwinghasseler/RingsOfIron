package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.HullStripTask;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.SalvageOrdersComponent;
import com.ringsofiron.shipops.SalvagedComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.uihud.providers.BoardingProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stripping parts a crew can't walk to: latched onto a wreck, its engines (out on the hull, with no
 * way to them from inside) wait with a word why rather than sending the crew back and forth; standing
 * off alongside it instead, an engineer goes across on EVA, takes the engine off and carries its
 * crate back across and in to the hold. Parts inside the hull wait for a latch.
 */
class SalvageEvaTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

    GameWorld world;
    GameScenario game;
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
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        int[] t = floor.get(floor.size() - 1);
        raiderCrew.add(CrewFactory.spawn(world.crew, game.raider, "Raider", CrewRole.CREWMAN, RAIDERS, t[0], t[1]));
        // A wreck: reactors cold, its crew dead
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        for (Entity e : raiderCrew) world.crew.crew(e).state = CrewState.DEAD;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    /** The raider still beside the player's ship, {@code meters} off its side, drifting with it. */
    private void standOff(float meters) {
        Body ours = body(game.player), theirs = body(game.raider);
        Vector2 at = ours.getPosition(), v = ours.getLinearVelocity();
        float a = ours.getAngle();
        float off = meters / PhysicsManager.METERS_PER_UNIT;
        theirs.setTransform(at.x - (float) Math.sin(a) * off, at.y + (float) Math.cos(a) * off, a);
        theirs.setLinearVelocity(v);
        theirs.setAngularVelocity(0f);
    }

    /** An engine of the raider's that can come off. */
    private Salvage.Part engine() {
        for (Salvage.Part p : world.salvage.parts(game.raider)) {
            if (p.system.type == SubsystemComponent.SubsystemType.ENGINE) return p;
        }
        throw new AssertionError("the raider has no engine");
    }

    /** A system of the raider's inside its hull, that no one on EVA can get at; null if none. */
    private Salvage.Part inner() {
        for (Salvage.Part p : world.salvage.parts(game.raider)) {
            MountFootprint f = p.system.footprint;
            if (!world.salvage.onHull(game.raider, f.originX, f.originY)) return p;
        }
        return null;
    }

    private SalvageOrdersComponent.Strip strip(MountFootprint f) {
        return world.salvage.strip(game.player, game.raider, f.originX, f.originY);
    }

    private BoardingProvider.Part row(MountFootprint f) {
        BoardingProvider.BoardingBoard board = new BoardingProvider.BoardingBoard();
        new BoardingWork(world.context, world.docking, world.boarding, world.salvage).collect(board);
        for (BoardingProvider.Part p : board.parts) if (p.x == f.originX && p.y == f.originY) return p;
        return null;
    }

    private int ourCrewAboardRaider() {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (world.crew.homeOf(e) == game.player && world.crew.position(e).ship == game.raider) n++;
        }
        return n;
    }

    @Test
    void latchedOnAnEngineNoOneCanReachWaitsWithAWordWhy() {
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(2f);
        assertSame(game.raider, world.docking.dockedTo(game.player));
        assertTrue(ShipDisablement.isDisabled(game.raider));
        Salvage.Part engine = engine();
        MountFootprint f = engine.system.footprint;

        String said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.startsWith("Engineers ordered to strip"), said);
        run(1f);
        SalvageOrdersComponent.Strip s = strip(f);
        assertNotNull(s);
        assertTrue(s.unreachable, "no way to it from inside");
        assertTrue(s.task == null || !s.task.isActive(), "no one sent to work it");
        String status = world.salvage.status(game.player, game.raider, s);
        assertTrue(status.contains("stand off alongside"), status);
        assertTrue(world.salvage.notices.stream().anyMatch(n -> n.contains("from inside")), "said so");

        // Nobody walks back and forth to it
        for (int i = 0; i < 120 / FRAME; i++) {
            world.update(FRAME);
            assertFalse(s.task != null && s.task.isActive(), "posted again");
        }
        BoardingProvider.Part row = row(f);
        assertNotNull(row);
        assertEquals(status, row.status);
        assertTrue(row.toggleable, "the player can still call it off");
        assertFalse(game.raider.getComponent(SalvagedComponent.class) != null
                && game.raider.getComponent(SalvagedComponent.class).has(f.originX, f.originY));
    }

    @Test
    void standingOffAlongsideAnEngineerStripsAnEngineOnEvaAndCarriesItHome() {
        standOff(40f);
        run(1f);
        assertNull(world.docking.dockedTo(game.player), "no dock between");
        assertTrue(world.boarding.alongside(game.player, game.raider));
        assertSame(game.raider, world.salvage.craftFor(game.player));
        assertNull(world.salvage.refusal(game.player, game.raider));
        Salvage.Part engine = engine();
        MountFootprint f = engine.system.footprint;
        assertTrue(world.salvage.onHull(game.raider, f.originX, f.originY), "an engine sits on the hull");
        String part = engine.part;
        CargoHoldComponent hold = game.player.getComponent(CargoHoldComponent.class);
        double before = hold.amount(part);

        String said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.startsWith("Engineers ordered across on EVA"), said);
        assertTrue(world.boarding.takenBy(game.raider, game.player), "no one defends it: taken on the spot");

        boolean crossed = false, carried = false;
        for (int i = 0; i < 1500 / FRAME && hold.amount(part) < before + 1 - 1e-6; i++) {
            world.update(FRAME);
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                CrewMemberComponent c = world.crew.crew(e);
                if (world.crew.homeOf(e) != game.player) continue;
                crossed |= c.task instanceof HullStripTask && world.crew.position(e).ship == game.raider && EvaSystem.of(e) != null;
                carried |= c.crate != null && part.equals(c.crate.commodity) && EvaSystem.outside(e);
            }
        }
        SalvageOrdersComponent.Strip s = strip(f);
        assertTrue(crossed, "an engineer across on the raider's hull, on EVA");
        assertTrue(carried, "its crate carried home outside");
        assertEquals(before + 1, hold.amount(part), 1e-6, "in the hold: " + world.salvage.notices
                + (s == null ? "" : ", " + world.salvage.status(game.player, game.raider, s)));
        assertTrue(game.raider.getComponent(SalvagedComponent.class).has(f.originX, f.originY), "stripped");
        assertFalse(engine.system.isOperational, "the engine is dead");
        assertTrue(world.salvage.parts(game.raider).stream().noneMatch(p -> p.system == engine.system), "only stripped once");
        run(120f);
        assertEquals(0, ourCrewAboardRaider(), "everyone back home");
    }

    @Test
    void standingOffPartsInsideTheHullWaitForALatch() {
        standOff(40f);
        run(1f);
        Salvage.Part in = inner();
        if (in == null) return; // every system of this raider faces space
        MountFootprint f = in.system.footprint;
        String said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.contains("inside the hull"), said);
        assertNull(strip(f));
        BoardingProvider.Part row = row(f);
        assertNotNull(row);
        assertFalse(row.toggleable);
        assertTrue(row.status.contains("latch on"), row.status);
    }
}
