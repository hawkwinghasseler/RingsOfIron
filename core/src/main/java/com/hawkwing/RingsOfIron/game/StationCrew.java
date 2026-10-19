package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * A station's own hands: loaders who carry traded goods across the dock ({@link CargoLoading}).
 * They are of their own faction, {@link #FACTION}, which fights the player's only once the
 * player has crossed it ({@link FactionStanding}).
 */
public final class StationCrew {
    /** The faction of every station's crew. */
    public static final int FACTION = 2;
    /** Fewest loaders a station keeps, however few ports it has. */
    static final int MIN_LOADERS = 2;
    private static final String[] NAMES = {"Dock hand Ito", "Dock hand Reyes", "Dock hand Okafor", "Dock hand Lund",
            "Dock hand Moss", "Dock hand Qadir", "Dock hand Vey", "Dock hand Brann"};

    private StationCrew() {
    }

    /**
     * Gives {@code station} its loaders, one a docking port (at least {@link #MIN_LOADERS}),
     * standing by its cargo bays. Returns them; none if it has no bays.
     */
    public static List<Entity> spawnLoaders(CrewService crew, Entity station) {
        List<Entity> out = new ArrayList<>();
        ShipGridComponent grid = station.getComponent(ShipGridComponent.class);
        CargoHoldComponent hold = station.getComponent(CargoHoldComponent.class);
        if (grid == null || hold == null || hold.bayTileCount() == 0) return out;
        DockingPortsComponent ports = station.getComponent(DockingPortsComponent.class);
        int count = Math.max(MIN_LOADERS, ports == null ? 0 : ports.ports.length);
        List<int[]> spots = besideBays(crew, grid, hold);
        if (spots.isEmpty()) return out;
        CrewFactory.ensureBoard(station).faction = FACTION;
        for (int i = 0; i < count; i++) {
            int[] t = spots.get(i * spots.size() / count);
            Entity e = CrewFactory.spawn(crew, station, NAMES[i % NAMES.length], CrewRole.LOADER, FACTION, t[0], t[1]);
            e.getComponent(CrewMemberComponent.class).setHome(station);
            out.add(e);
        }
        return out;
    }

    /** Walkable tiles next to a cargo bay, where a loader waits for work. */
    static List<int[]> besideBays(CrewService crew, ShipGridComponent grid, CargoHoldComponent hold) {
        GridNavGraph nav = crew.pathfinder(grid).graph;
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (!nav.isWalkable(x, y) || !nextToBay(hold, x, y)) continue;
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    private static boolean nextToBay(CargoHoldComponent hold, int x, int y) {
        for (Side side : Side.values()) {
            for (int i = 0; i < hold.bayTileCount(); i++) {
                if (hold.bayX[i] == x + side.dx && hold.bayY[i] == y + side.dy) return true;
            }
        }
        return false;
    }
}
