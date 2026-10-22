package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.minimap.TileShade;

/**
 * A developer overlay over a deck, not part of the normal view: each tile's heat as a red wash,
 * dull from 350 K to bright at the ignition point, and walls marked as they are worn down. The
 * game shows heat as light instead (hot tiles glow as black bodies, see
 * {@code lighting.BlackBody}); this shows it where it is still too cool to glow. {@link
 * DeckRenderer} draws it when it is shown, which the developer console's {@code overlay("heat")}
 * toggles.
 */
public final class HeatOverlay {
    /** The overlay's name in the developer console. */
    public static final String NAME = "heat";
    /** What it draws, for the console's list. */
    public static final String DESCRIPTION = "tile heat and worn walls on the decks";

    /** Where heat starts to show, kelvin. */
    private static final float WARM = 350f;

    private HeatOverlay() {
    }

    /** Draws it in the grid's local meters, inside a filled batch already moved to the craft's pose. */
    public static void draw(ShapeRenderer shapes, ShipGridComponent g, Color tmp) {
        float s = g.tileSize;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t.isVoid()) continue;
                Vector2 p = g.tileToLocal(x, y);
                float heat = (t.temperature - WARM) / (GridHazardSystem.IGNITION_TEMPERATURE - WARM);
                if (heat > 0f) {
                    shapes.setColor(1f, 0.25f, 0.05f, Math.min(0.6f, heat * 0.6f));
                    shapes.rect(p.x - s / 2f, p.y - s / 2f, s, s);
                }
                if (t.isWall() && t.getIntegrity() < 1f) {
                    shapes.setColor(t.getIntegrity() <= 0f ? TileShade.BREACH : TileShade.wall(t.getIntegrity(), tmp));
                    float m = s * 0.35f;
                    shapes.rect(p.x - s / 2f + m, p.y - s / 2f + m, s - 2 * m, s - 2 * m);
                }
            }
        }
    }
}
