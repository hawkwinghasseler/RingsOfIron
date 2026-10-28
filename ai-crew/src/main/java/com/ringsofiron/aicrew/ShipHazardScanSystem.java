package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import com.ringsofiron.interiorgrid.crew.RcsRepairTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;

/**
 * Keeps each ship's task board in step with the grid: posts an {@link ExtinguishTask}
 * for every burning tile, a {@link SealBreachTask} for every hull breach that borders
 * the interior, a {@link PatchLeakTask} for every leak to space the ship's patch kits can stop
 * (see {@link LeakPatches}), a {@link ResetShortTask} for every shorted system or conduit, an
 * {@link IsolateTankTask} for every leaking tank (see {@link DamageStates}), a {@link RepairTask}
 * for every damaged system, an {@link RcsRepairTask} for every RCS block whose hull tile is wrecked
 * (off battle stations: it is EVA work) and a {@link ManConsoleTask}
 * for every helm, weapons console and turret mount still standing, and cancels open tasks that no
 * longer need doing.
 *
 * Combat and the hazard simulation post the same tasks as they happen; posting is
 * idempotent, so this scan only catches what they miss (and lets the crew prototype
 * run before they post anything).
 */
public class ShipHazardScanSystem extends FixedIteratingSystem {

    public static final float SCAN_INTERVAL = 0.5f;

    private final CrewService service;
    private final ComponentMapper<ShipGridComponent> gridM = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipTaskBoardComponent> boardM = ComponentMapper.getFor(ShipTaskBoardComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> regM = ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);

    public ShipHazardScanSystem(CrewService service) {
        super(Family.all(ShipGridComponent.class, ShipTaskBoardComponent.class).get(), 1, SCAN_INTERVAL);
        this.service = service;
    }

    @Override
    protected void processEntity(Entity ship, float dt) {
        scan(ship);
    }

    /** One scan of a ship; also callable directly. */
    public void scan(Entity ship) {
        ShipGridComponent grid = gridM.get(ship);
        ShipTaskBoardComponent board = boardM.get(ship);
        for (CrewTask t : board.tasks) {
            if (t.status == CrewTask.Status.OPEN && (!TaskRules.isStillNeeded(t, grid, service)
                    || t.kind == CrewTask.Kind.REPAIR_RCS && board.battleStations)) board.cancel(t);
        }
        board.prune();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.isBurning()) board.post(new ExtinguishTask(x, y));
                else if (t.isHullBreach() && bordersInterior(grid, x, y)) board.post(new SealBreachTask(x, y));
                if (t.shorted) board.post(new ResetShortTask(x, y));
            }
        }
        if (!board.battleStations) {
            for (int i = 0; i < grid.rcsThrusters.size; i++) {
                RcsThruster r = grid.rcsThrusters.get(i);
                if (RcsThruster.fieldRepairable(grid, r.x, r.y)) board.post(new RcsRepairTask(r.x, r.y));
            }
        }
        if (grid.patchKits > 0) {
            for (LeakPatches.Leak leak : LeakPatches.leaks(grid)) board.post(new PatchLeakTask(leak));
        }
        ShipSubsystemRegistryComponent reg = regM.get(ship);
        if (reg != null) {
            for (Entity sys : reg.subsystems) {
                SubsystemComponent s = sys.getComponent(SubsystemComponent.class);
                if (s == null) continue;
                if (s.shorted) board.post(new ResetShortTask(sys, s.footprint.originX, s.footprint.originY));
                if (DamageStates.needsIsolating(s)) board.post(new IsolateTankTask(sys, s.footprint.originX, s.footprint.originY));
                if (s.health < 1f) board.post(new RepairTask(sys, s.footprint.originX, s.footprint.originY));
                if (isConsole(s.type) && !s.isDestroyed()) {
                    board.post(new ManConsoleTask(sys, s.footprint.originX, s.footprint.originY));
                }
            }
        }
    }

    /** True for the systems crew man: the helm, the weapons console and each turret mount. */
    public static boolean isConsole(SubsystemComponent.SubsystemType type) {
        return type == SubsystemComponent.SubsystemType.HELM || type == SubsystemComponent.SubsystemType.WEAPONS
                || type == SubsystemComponent.SubsystemType.TURRET_MOUNT;
    }

    private static boolean bordersInterior(ShipGridComponent grid, int x, int y) {
        for (Side s : Side.values()) {
            Tile n = grid.getTile(x + s.dx, y + s.dy);
            if (n != null && n.isOpen() && !grid.isEdgeBlocked(x, y, x + s.dx, y + s.dy)) return true;
        }
        return false;
    }
}
