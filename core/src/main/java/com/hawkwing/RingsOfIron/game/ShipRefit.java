package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.modding.ShipWeaponsAssembler;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A station yard rebuilds a docked ship to a new interior layout, as drawn in the ship editor:
 * new grid, systems, cargo bays and docking ports on the same body and weapons, the body taking
 * the new layout's shape. The crew
 * aboard are put on the nearest open deck of the new layout, the tanks keep what they held (as far
 * as the new tanks take it), the hardpoints are wired to the nearest weapon systems again, and
 * the ship latches back onto the port it was docked at. The layout is kept on the ship
 * ({@link ShipLayoutComponent}), so a save reloads it and the editor opens it next time.
 *
 * <p>The yard does it at once and for free; it refuses while cargo is aboard or other yard work is
 * under way, when the game can't build the layout, and when it has no port that fits the one the
 * ship is docked at.
 */
public final class ShipRefit {
    private ShipRefit() {}

    /**
     * The layout {@code ship} is built to: the one a refit gave it, or its chassis from the data,
     * or null when it has neither.
     */
    public static ChassisDef layoutOf(GameWorld world, Entity ship) {
        ShipLayoutComponent layout = ship.getComponent(ShipLayoutComponent.class);
        if (layout != null && layout.layout != null) return layout.layout;
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null || grid.chassis == null || world.getContent() == null) return null;
        if (!world.getContent().defs.has("chassis", grid.chassis)) return null;
        return world.getContent().defs.require("chassis", grid.chassis, ChassisDef.class);
    }

    /** Why the yard can't rebuild {@code ship} right now, or null when it can. */
    public static String refused(GameWorld world, Entity ship) {
        Entity station = ship == null ? null : world.docking.dockedTo(ship);
        if (station == null || !world.yard.canWork(ship, station)) return "Dock at a station's yard first";
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (hold != null && hold.usedVolume() > 1e-6) return "Unload the cargo before the yard rebuilds the decks";
        YardOrdersComponent orders = ship.getComponent(YardOrdersComponent.class);
        if (orders != null && !orders.isEmpty()) return "Wait for the yard's other work on the ship to finish";
        return null;
    }

    /**
     * Rebuilds {@code ship} to {@code layout}. Returns null when done, or why the yard refused (and
     * then nothing changed).
     */
    public static String apply(GameWorld world, Entity ship, ChassisDef layout) {
        String no = refused(world, ship);
        if (no != null) return no;
        ShipGridComponent oldGrid = ship.getComponent(ShipGridComponent.class);
        DockingPortsComponent oldPorts = ship.getComponent(DockingPortsComponent.class);
        if (oldGrid == null || oldPorts == null) return "This ship has no decks to rebuild";
        DockingPort docked = null;
        for (DockingPort p : oldPorts.ports) {
            if (p.state == DockingPort.State.DOCKED) docked = p;
        }
        Entity station = world.docking.dockedTo(ship);
        if (docked == null || docked.partnerPort < 0) return "Dock at a station's yard first";

        // Check the new layout builds, and has a port to latch back on with, before touching the ship
        ChassisDef built = copyWithId(layout, oldGrid.chassis);
        ChassisBlueprintLoader.LoadResult plan;
        try {
            plan = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(built, InteriorGridData.catalog()));
        } catch (RuntimeException e) {
            return "The yard can't build that layout: " + e.getMessage();
        }
        if (portToRelatch(DockingData.portsFor(plan.grid, built), docked) < 0) {
            return "The new layout needs a " + docked.size + " docking port to stay latched to the station";
        }

        int stationPort = docked.partnerPort;
        Map<Fluid, Double> fluids = fluids(ship);
        Vector2 velocity = new Vector2();
        PhysicsBodyComponent body = ship.getComponent(PhysicsBodyComponent.class);
        Entity aboard = null;

        // Everyone off the old deck plan: tasks given back, carried goods put down
        Array<Entity> crew = new Array<>();
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent pos = world.crew.position(e);
            if (pos == null) continue;
            if (pos.ship == ship || pos.crossing != null || pos.walkway != null) {
                world.crew.releaseTask(e);
                pos.stop();
                pos.crossing = null;
                if (pos.walkway != null) {
                    // Out on the walkway: back onto the craft they stepped out of
                    pos.setTile(pos.walkway.x, pos.walkway.y);
                }
            }
            if (pos.ship == ship) crew.add(e);
        }

        if (body != null && body.body != null) velocity.set(PhysicsManager.getVelocity(body.body, new Vector2()));
        world.docking.undock(ship);
        if (body != null && body.body != null) PhysicsManager.setVelocity(body.body, velocity.x, velocity.y);

        // Out with the old interior
        ShipSubsystemRegistryComponent oldRegistry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (oldRegistry != null) {
            for (Entity system : oldRegistry.subsystems) world.engine.getEcsEngine().removeEntity(system);
        }
        ship.remove(ShipSubsystemRegistryComponent.class);
        ship.remove(CargoHoldComponent.class);
        ship.remove(DockingPortsComponent.class);
        ship.remove(ShipGridComponent.class);
        ShipTaskBoardComponent board = world.crew.board(ship);
        if (board != null) board.tasks.clear();

        // In with the new
        InteriorGridData.installInterior(ship, built, world.engine);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        DockingPortsComponent ports = DockingData.portsFor(grid, built);
        if (ports.ports.length > 0) ship.add(ports);
        ShipConsoles.install(ship);
        ShipCargo.attach(ship);
        fill(ship, fluids);
        rewireWeapons(ship);
        ShipLayoutComponent kept = ship.getComponent(ShipLayoutComponent.class);
        if (kept == null) ship.add(kept = new ShipLayoutComponent());
        kept.layout = built;
        // The body takes the new layout's shape, then its mass
        world.rebuildHull(ship);
        ShipGridComponent.MassProperties mass = world.shipMass(ship);
        if (body != null && body.body != null && mass != null && mass.mass > 0f) {
            PhysicsManager.setMassData(body.body, mass.mass, mass.center.x, mass.center.y, mass.inertia);
        }
        world.crew.clearPathfinders();
        List<int[]> floor = GameScenario.floorTiles(grid);
        for (Entity e : crew) {
            GridPositionComponent pos = world.crew.position(e);
            int[] at = nearest(floor, pos.tileX(), pos.tileY());
            if (at != null) pos.setTile(at[0], at[1]);
        }

        // Back onto the station, at the port that fits best
        world.docking.dockInPlace(ship, portToRelatch(ports, docked), station, stationPort);
        return null;
    }

    /** The port of the new layout to latch with: the one where the old one was, else any of its size. */
    static int portToRelatch(DockingPortsComponent ports, DockingPort old) {
        int any = -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.size != old.size) continue;
            if (p.gridX == old.gridX && p.gridY == old.gridY) return i;
            if (any < 0) any = i;
        }
        return any;
    }

    /** {@code def} as the yard builds it: the same layout under the chassis id the ship keeps. */
    private static ChassisDef copyWithId(ChassisDef def, String id) {
        ChassisDef c = new ChassisDef();
        c.id = id != null ? id : def.id;
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
        c.station = def.station;
        c.ports.addAll(def.ports);
        c.lights = def.lights == null ? null : new java.util.ArrayList<>(def.lights);
        return c;
    }

    private static Map<Fluid, Double> fluids(Entity ship) {
        Map<Fluid, Double> out = new EnumMap<>(Fluid.class);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return out;
        for (Fluid f : Fluid.values()) {
            double have = 0;
            for (SubsystemComponent t : TankSupply.tanks(registry, f)) have += t.contents;
            if (have > 0) out.put(f, have);
        }
        return out;
    }

    /** Pours what the old tanks held into the new ones, each fluid into its own tanks, until they are full. */
    private static void fill(Entity ship, Map<Fluid, Double> fluids) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return;
        for (Map.Entry<Fluid, Double> f : fluids.entrySet()) {
            Array<SubsystemComponent> tanks = TankSupply.tanks(registry, f.getKey());
            double left = f.getValue();
            for (SubsystemComponent t : tanks) t.contents = 0;
            for (SubsystemComponent t : tanks) {
                double in = Math.min(left, t.capacity);
                t.contents = in;
                left -= in;
            }
        }
    }

    private static void rewireWeapons(Entity ship) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return;
        for (WeaponMountComponent m : weapons.mounts) {
            m.subsystem = ShipWeaponsAssembler.nearestWeaponSystem(ship, m.hardpoint.x, m.hardpoint.y);
        }
    }

    private static int[] nearest(List<int[]> tiles, int x, int y) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int[] t : tiles) {
            int d = (t[0] - x) * (t[0] - x) + (t[1] - y) * (t[1] - y);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }
}
