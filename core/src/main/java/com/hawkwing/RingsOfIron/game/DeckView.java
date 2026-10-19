package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewBody;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.minimap.TileShade;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What the screens share for walking about a deck drawn at its craft's pose: the walls between
 * tiles and the systems mounted on the floor (both stop the avatar, so both are drawn), and
 * turning screen directions into the deck's own axes, so W is always up the screen whichever
 * way the craft the avatar is aboard is turned.
 */
public final class DeckView {
    /** A mounted system: crew walk over it. */
    public static final Color SYSTEM = new Color(0.36f, 0.42f, 0.48f, 0.9f);

    private DeckView() {
    }

    /**
     * Sets the avatar's walk from a direction on screen ({@code screenX}, {@code screenY}, each
     * -1 to 1), turned into the axes of {@code deck}, the body of the craft it is aboard.
     */
    public static void walk(PlayerAvatarComponent walker, Body deck, float screenX, float screenY, float speed) {
        float angle = deck == null ? 0f : deck.getAngle();
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        walker.moveX = (c * screenX + s * screenY) * speed;
        walker.moveY = (-s * screenX + c * screenY) * speed;
    }

    /**
     * The floor of each open walkway out of this grid's docked ports: its half of the way across the
     * collars to the other craft, which draws the other half. Shaded like the tile it leads out of,
     * at {@code alpha} of its opacity; in the grid's local meters, like {@link #walls}.
     */
    public static void walkways(ShapeRenderer shapes, ShipGridComponent grid, Color tmp, float alpha) {
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        for (GridLink link : grid.links) {
            if (!link.canCross()) continue;
            TileShade.fill(grid.getTile(link.x, link.y), false, tmp);
            if (tmp.a <= 0f) continue;
            tmp.a *= alpha;
            shapes.setColor(tmp);
            Side side = link.side;
            float half = link.gap * 0.5f * s;
            // From the tile's edge out, the tile's width across
            float ex = ox + (link.x + 0.5f + side.dx * 0.5f) * s, ey = oy + (link.y + 0.5f + side.dy * 0.5f) * s;
            float w = side.dx != 0 ? half : s, h = side.dy != 0 ? half : s;
            float x = side.dx > 0 ? ex : side.dx < 0 ? ex - half : ex - s / 2f;
            float y = side.dy > 0 ? ey : side.dy < 0 ? ey - half : ey - s / 2f;
            shapes.rect(x, y, w, h);
        }
    }

    /**
     * Mounted systems as solid blocks and the walls between tiles as lines, in the grid's local
     * meters. Inside a filled batch already moved and turned to the craft's pose.
     */
    public static void walls(ShapeRenderer shapes, ShipGridComponent grid, Color tmp) {
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        Map<MountFootprint, Boolean> seen = new IdentityHashMap<>();
        shapes.setColor(SYSTEM);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                MountFootprint m = t == null ? null : t.mount;
                if (m == null || m.kind == MountFootprint.MountKind.PORT || seen.put(m, Boolean.TRUE) != null) continue;
                float inset = s * 0.12f;
                shapes.rect(ox + m.originX * s + inset, oy + m.originY * s + inset,
                        m.width() * s - 2 * inset, m.height() * s - 2 * inset);
            }
        }
        float line = s * 0.14f;
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (!edge(grid.edgeH[y][x], tmp)) continue;
                shapes.setColor(tmp);
                shapes.rect(ox + x * s, oy + y * s - line / 2f, s, line);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                if (!edge(grid.edgeV[y][x], tmp)) continue;
                shapes.setColor(tmp);
                shapes.rect(ox + x * s - line / 2f, oy + y * s, line, s);
            }
        }
    }

    /**
     * A crate on each loaded bay tile, one color per commodity, filled from the tile's aft edge as
     * far as the stack fills the tile. In the grid's local meters, inside a filled batch.
     */
    public static void cargo(ShapeRenderer shapes, ShipGridComponent grid, CargoHoldComponent hold, Color tmp) {
        if (hold == null) return;
        float s = grid.tileSize;
        float inset = s * 0.12f;
        for (int i = 0; i < hold.bayTileCount(); i++) {
            CargoHoldComponent.CargoStack stack = hold.stacks[i];
            if (stack == null) continue;
            float fill = hold.fillAt(hold.bayX[i], hold.bayY[i]);
            Vector2 p = grid.tileToLocal(hold.bayX[i], hold.bayY[i]);
            float x0 = p.x - s / 2f + inset, y0 = p.y - s / 2f + inset, side = s - 2f * inset;
            shapes.setColor(0.08f, 0.08f, 0.1f, 0.9f);
            shapes.rect(x0, y0, side, side);
            shapes.setColor(commodityColor(stack.commodity, tmp));
            shapes.rect(x0, y0, Math.max(0.05f * side, side * fill), side);
        }
    }

    /**
     * A crew member at ({@code x}, {@code y}): a dot in {@code color}, with a crate in the
     * commodity's color on it while they carry a load.
     */
    public static void crew(ShapeRenderer shapes, CrewMemberComponent crew, float x, float y, float tileSize,
                            Color color, Color tmp) {
        shapes.setColor(color);
        shapes.circle(x, y, CrewBody.RADIUS * tileSize, 12);
        if (crew == null || crew.load == null) return;
        float half = 0.17f * tileSize;
        shapes.setColor(0.08f, 0.08f, 0.1f, 1f);
        shapes.rect(x - half - 0.03f, y - half - 0.03f, 2f * half + 0.06f, 2f * half + 0.06f);
        shapes.setColor(commodityColor(crew.load.task.job.commodity(), tmp));
        shapes.rect(x - half, y - half, 2f * half, 2f * half);
    }

    /** The color a station's own crew are drawn in, apart from a ship's. */
    public static final Color STATION_CREW = new Color(1f, 0.6f, 0.2f, 1f);

    /** A steady, distinct color for each commodity id, into {@code out}. */
    public static Color commodityColor(String commodity, Color out) {
        float hue = ((commodity.hashCode() & 0x7fffffff) % 360);
        out.fromHsv(hue, 0.55f, 0.85f).a = 1f;
        return out;
    }

    private static boolean edge(EdgeWall wall, Color out) {
        if (wall == null) return false;
        TileShade.edge(wall, out);
        return out.a > 0f;
    }
}
