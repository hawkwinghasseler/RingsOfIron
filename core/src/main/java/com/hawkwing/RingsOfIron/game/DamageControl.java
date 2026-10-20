package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Locale;

/**
 * Tells the player about damage control aboard their ship, since a leak to space holds rails warp
 * back: as a leak starts venting, that the crew are patching it (or that there are no patches
 * left, and a yard must repair it); once the patches hold, that warp is clear and the hull still
 * wants a yard. The crew do the patching themselves ({@link LeakPatches}); hints name the crew
 * roster while they work, and the yard while docked with patches on the hull.
 */
public final class DamageControl {
    private final GameWorld world;
    /** A patchable leak was venting at the last update. */
    private boolean leaking;
    /** A leak was reported and has not been reported stopped yet. */
    private boolean episode;
    private boolean outWarned;

    public DamageControl(GameWorld world) {
        this.world = world;
        world.hints.register(HintRegistry.Context.ANY, "crew (patching a hull leak)", 4, Action.CREW)
                .onlyWhen(() -> leaking && kits() > 0);
        world.hints.register(HintRegistry.Context.ANY, "station yard (hull leaking, no patches left)", 4, Action.YARD)
                .onlyWhen(() -> leaking && kits() <= 0);
        world.hints.register(HintRegistry.Context.DOCKED, "repair the patched hull", 5, Action.YARD)
                .onlyWhen(() -> hasPatches(grid()));
    }

    /** Once a frame. */
    public void update() {
        ShipGridComponent grid = grid();
        if (grid == null) {
            leaking = episode = false;
            return;
        }
        boolean venting = grid.isVenting();
        int leaks = venting || episode ? LeakPatches.leaks(grid).size : 0;
        leaking = venting && leaks > 0;
        if (leaking && !episode) {
            world.notices.post(grid.patchKits > 0
                    ? String.format(Locale.ROOT, "Hull leaking: the crew are patching it (%d patch%s aboard). Warp waits until it holds",
                            grid.patchKits, grid.patchKits == 1 ? "" : "es")
                    : "Hull leaking and no patches left: warp is refused until a station yard repairs it",
                    NoticeLog.Kind.WARNING);
            episode = true;
            outWarned = grid.patchKits <= 0;
        } else if (leaking && grid.patchKits <= 0 && !outWarned) {
            world.notices.post("Out of hull patches: warp is refused until a station yard repairs the leak",
                    NoticeLog.Kind.WARNING);
            outWarned = true;
        } else if (episode && leaks == 0 && !venting) {
            // Every leak patched (or mended) and the air has stopped going: say so once
            world.notices.post("Leak patched, and warp is clear. The hull needs a yard for full repairs", NoticeLog.Kind.INFO);
            episode = false;
        }
        if (grid.patchKits > 0) outWarned = false;
    }

    /** True while a patchable leak aboard the player's ship vents to space. */
    public boolean leaking() {
        return leaking;
    }

    private ShipGridComponent grid() {
        Entity player = world.getPlayer();
        return player == null ? null : player.getComponent(ShipGridComponent.class);
    }

    private int kits() {
        ShipGridComponent grid = grid();
        return grid == null ? 0 : grid.patchKits;
    }

    /** True if any field patch still holds aboard. */
    public static boolean hasPatches(ShipGridComponent grid) {
        if (grid == null) return false;
        for (HullPiece p : grid.hullPieces) if (Tile.isPatched(p.patchedAt, p.integrity)) return true;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.piece == null && t.isPatched()) return true;
            }
        }
        for (EdgeWall[] row : grid.edgeH) for (EdgeWall e : row) if (e != null && e.isPatched()) return true;
        for (EdgeWall[] row : grid.edgeV) for (EdgeWall e : row) if (e != null && e.isPatched()) return true;
        return false;
    }
}
