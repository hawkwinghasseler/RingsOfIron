package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A station yard rebuilding the player's ship to a layout from the ship editor. */
class ShipRefitTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    Entity highport;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        highport = world.engine.getEntity(game.highport);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void dock() {
        DockingPortsComponent ports = highport.getComponent(DockingPortsComponent.class);
        int small = -1;
        for (int i = 0; i < ports.ports.length && small < 0; i++) {
            if (ports.ports[i].size == MountFootprint.MountSize.S) small = i;
        }
        world.docking.dockInPlace(game.player, 0, highport, small);
        run(120);
        assertSame(highport, world.docking.dockedTo(game.player));
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** The player's layout as built. */
    private ChassisDef copy() {
        ChassisDef def = ShipRefit.layoutOf(world, game.player);
        assertNotNull(def);
        ChassisDef c = new ChassisDef();
        c.id = def.id;
        c.name = def.name;
        c.tileSize = def.tileSize;
        c.originX = def.originX;
        c.originY = def.originY;
        c.rows = def.rows.clone();
        c.edgeWalls.addAll(def.edgeWalls);
        c.edgeConduits.addAll(def.edgeConduits);
        c.pipes.addAll(def.pipes);
        c.hullPieces.addAll(def.hullPieces);
        c.mounts.addAll(def.mounts);
        c.coverings.addAll(def.coverings);
        c.ports.addAll(def.ports);
        c.lights = def.lights;
        return c;
    }

    /** The layout with the turret mount under the laser (map row 4, column 10) taken out for deck. */
    private ChassisDef withoutLaserTurret() {
        ChassisDef c = copy();
        char[] row = c.rows[4].toCharArray();
        assertEquals('T', row[10], "the corvette's laser turret mount");
        row[10] = '.';
        c.rows[4] = new String(row);
        return c;
    }

    @Test
    void theYardRebuildsTheDockedShipToANewLayout() {
        dock();
        ShipGridComponent before = game.player.getComponent(ShipGridComponent.class);
        int systemsBefore = game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems.size;
        double propellant = TankSupply.available(
                TankSupply.tanks(game.player.getComponent(ShipSubsystemRegistryComponent.class), Fluid.PROPELLANT),
                Fluid.PROPELLANT);
        double credits = game.player.getComponent(TraderComponent.class).credits;

        ChassisDef layout = withoutLaserTurret();
        assertNull(ShipRefit.apply(world, game.player, layout));

        ShipGridComponent after = game.player.getComponent(ShipGridComponent.class);
        assertNotSame(before, after, "a new deck plan");
        assertEquals(before.chassis, after.chassis, "keeps its chassis, so its hull art and save stay");
        assertEquals(systemsBefore - 1, game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems.size,
                "one system fewer, as drawn");
        assertEquals(credits, game.player.getComponent(TraderComponent.class).credits, 1e-9);
        assertEquals(propellant, TankSupply.available(
                TankSupply.tanks(game.player.getComponent(ShipSubsystemRegistryComponent.class), Fluid.PROPELLANT),
                Fluid.PROPELLANT), 1e-6, "the tanks keep their propellant");
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) {
            if (m.subsystem != null) {
                assertTrue(game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems
                        .contains(m.subsystem, true), "guns wired to systems of the new layout");
            }
        }
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent pos = world.crew.position(e);
            if (pos.ship != game.player) continue;
            Tile t = after.getTile(pos.tileX(), pos.tileY());
            assertTrue(t != null && t.isOpen(), "crew stand on the new deck");
        }

        run(240);
        assertSame(highport, world.docking.dockedTo(game.player), "latched back on");
        assertTrue(world.docking.isRigid(game.player), "and welded");
        assertTrue(after.links.size > 0 && after.links.first().canCross(), "the walkway joins the new deck");

        // Saved and loaded, the ship keeps the new layout
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        game.player = world.getPlayer();
        ShipLayoutComponent kept = game.player.getComponent(ShipLayoutComponent.class);
        assertNotNull(kept);
        assertEquals(layout.rows[4], kept.layout.rows[4]);
        assertEquals(systemsBefore - 1, game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems.size);
        run(120);
        assertSame(world.engine.getEntity(game.highport), world.docking.dockedTo(game.player));
    }

    /** Whether any of the ship's solid fixtures covers the body-local point (x, y) in meters. */
    private boolean collidesAt(float x, float y) {
        com.badlogic.gdx.physics.box2d.Body body = game.player
                .getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body;
        float u = com.ringsofiron.physicsexterior.PhysicsManager.UNITS_PER_METER;
        com.badlogic.gdx.math.Vector2 p = body.getWorldPoint(new com.badlogic.gdx.math.Vector2(x * u, y * u));
        for (com.badlogic.gdx.physics.box2d.Fixture f : body.getFixtureList()) {
            if (!f.isSensor() && f.testPoint(p)) return true;
        }
        return false;
    }

    @Test
    void aRebuiltShipCollidesOnItsNewLayout() {
        dock();
        ChassisDef layout = copy();
        // Cut the hull's aft port corner tile (map row 0, column 1; the engines stand aft of it) out of the hull
        char[] top = layout.rows[0].toCharArray();
        assertEquals('#', top[1]);
        top[1] = ' ';
        layout.rows[0] = new String(top);
        float cornerX = layout.originX + 1.5f, cornerY = layout.originY + layout.rows.length - 0.5f;
        assertTrue(collidesAt(cornerX, cornerY), "the corner collides as built");
        float massBefore = com.ringsofiron.physicsexterior.PhysicsManager.getMass(game.player
                .getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body);

        assertNull(ShipRefit.apply(world, game.player, layout));

        assertFalse(collidesAt(cornerX, cornerY), "the cut corner no longer collides");
        assertTrue(collidesAt(cornerX, cornerY - 1f), "the tile below it still does");
        assertTrue(collidesAt(cornerX + 1f, cornerY), "and the one beside it");
        float massAfter = com.ringsofiron.physicsexterior.PhysicsManager.getMass(game.player
                .getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body);
        assertEquals(world.shipMass(game.player).mass, massAfter, massAfter * 1e-3f, "mass still from the grid");
        assertTrue(massAfter < massBefore, "one hull tile lighter");

        run(240);
        assertSame(highport, world.docking.dockedTo(game.player), "latched back on");
    }

    @Test
    void theYardRefusesAShipThatIsNotDockedOrHasCargo() {
        assertNotNull(ShipRefit.apply(world, game.player, withoutLaserTurret()), "not docked");
        dock();
        assertNull(ShipRefit.refused(world, game.player));
        com.ringsofiron.interiorgrid.CargoHoldComponent hold =
                game.player.getComponent(com.ringsofiron.interiorgrid.CargoHoldComponent.class);
        assertTrue(hold.capacity() > 0);
        hold.load("water", 1.0, 1.0, 1000.0);
        String said = ShipRefit.refused(world, game.player);
        assertNotNull(said);
        assertTrue(said.contains("cargo"), said);
    }

    @Test
    void aLayoutWithoutAFittingPortIsRefused() {
        dock();
        ChassisDef layout = copy();
        for (int i = 0; i < layout.rows.length; i++) layout.rows[i] = layout.rows[i].replace('D', '#');
        layout.ports.clear();
        ShipGridComponent before = game.player.getComponent(ShipGridComponent.class);
        String said = ShipRefit.apply(world, game.player, layout);
        assertNotNull(said);
        assertTrue(said.contains("port"), said);
        assertSame(before, game.player.getComponent(ShipGridComponent.class), "nothing rebuilt");
        run(60);
        assertSame(highport, world.docking.dockedTo(game.player), "still docked");
    }

    @Test
    void anUnbuildableLayoutChangesNothing() {
        dock();
        ShipGridComponent before = game.player.getComponent(ShipGridComponent.class);
        ChassisDef broken = copy();
        broken.rows = new String[]{"#Q#"};
        assertNotNull(ShipRefit.apply(world, game.player, broken));
        assertSame(before, game.player.getComponent(ShipGridComponent.class));
        assertSame(highport, world.docking.dockedTo(game.player));
    }
}
