package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IntervalIteratingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;

/**
 * Keeps each ship's task board in step with the grid: posts an {@link ExtinguishTask}
 * for every burning tile, a {@link SealBreachTask} for every hull breach that borders
 * the interior and a {@link RepairTask} for every damaged system, and cancels open
 * tasks that no longer need doing.
 *
 * Combat and the hazard simulation post the same tasks as they happen; posting is
 * idempotent, so this scan only catches what they miss (and lets the crew prototype
 * run before they post anything).
 */
public class ShipHazardScanSystem extends IntervalIteratingSystem {

    public static final float SCAN_INTERVAL = 0.5f;

    private final CrewService service;
    private final ComponentMapper<ShipGridComponent> gridM = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipTaskBoardComponent> boardM = ComponentMapper.getFor(ShipTaskBoardComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> regM = ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);

    public ShipHazardScanSystem(CrewService service) {
        super(Family.all(ShipGridComponent.class, ShipTaskBoardComponent.class).get(), SCAN_INTERVAL, 1);
        this.service = service;
    }

    @Override
    protected void processEntity(Entity ship) {
        scan(ship);
    }

    /** One scan of a ship; also callable directly. */
    public void scan(Entity ship) {
        ShipGridComponent grid = gridM.get(ship);
        ShipTaskBoardComponent board = boardM.get(ship);
        for (CrewTask t : board.tasks) {
            if (t.status == CrewTask.Status.OPEN && !TaskRules.isStillNeeded(t, grid, service)) board.cancel(t);
        }
        board.prune();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.isBurning()) board.post(new ExtinguishTask(x, y));
                else if (t.isHullBreach() && bordersInterior(grid, x, y)) board.post(new SealBreachTask(x, y));
            }
        }
        ShipSubsystemRegistryComponent reg = regM.get(ship);
        if (reg != null) {
            for (Entity sys : reg.subsystems) {
                SubsystemComponent s = sys.getComponent(SubsystemComponent.class);
                if (s != null && s.health < 1f) board.post(new RepairTask(sys, s.footprint.originX, s.footprint.originY));
            }
        }
    }

    private static boolean bordersInterior(ShipGridComponent grid, int x, int y) {
        for (Side s : Side.values()) {
            Tile n = grid.getTile(x + s.dx, y + s.dy);
            if (n != null && n.isOpen() && !grid.isEdgeBlocked(x, y, x + s.dx, y + s.dy)) return true;
        }
        return false;
    }
}
