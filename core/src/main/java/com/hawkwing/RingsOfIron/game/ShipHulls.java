package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.art.CollisionLayer;
import com.ringsofiron.interiorgrid.HullShape;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.DefEntry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.ModManifest;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a ship collides on, for the physics assembler: the collision layer of its chassis's hull art
 * ({@code art/hulls/<chassis>.svg}, the last mod that has one winning over the base game's) when
 * that file has one, otherwise its layout's tiles ({@link HullShape}). A ship without a grid keeps
 * the {@code hull} in its def.
 *
 * The SVG is read from the art's source, not the rasterized atlas, so a ship collides the same
 * whether or not the art pack was built. Each chassis is read once.
 */
final class ShipHulls implements ShipPhysicsAssembler.HullSource {
    private final Path assetsDir;
    private final ModContext content;
    /** Collision layers by chassis id; null when the art has none. */
    private final Map<String, List<float[]>> layers = new HashMap<>();

    ShipHulls(Path assetsDir, ModContext content) {
        this.assetsDir = assetsDir;
        this.content = content;
    }

    @Override
    public List<float[]> hullFor(Entity entity, DefEntry def) {
        ShipGridComponent grid = entity.getComponent(ShipGridComponent.class);
        if (grid == null) return null;
        if (grid.chassis != null) {
            if (!layers.containsKey(grid.chassis)) layers.put(grid.chassis, readLayer(grid.chassis));
            List<float[]> drawn = layers.get(grid.chassis);
            if (drawn != null && !drawn.isEmpty()) return drawn;
        }
        return HullShape.pieces(grid);
    }

    private List<float[]> readLayer(String chassis) {
        Path svg = null;
        String file = "art/hulls/" + chassis + ".svg";
        for (ModManifest mod : content.mods) {
            if (mod.directory != null && mod.directory.resolve(file).toFile().isFile()) svg = mod.directory.resolve(file);
        }
        if (svg == null && assetsDir != null) svg = assetsDir.resolve(file);
        if (svg == null) return null;
        try {
            return CollisionLayer.read(svg);
        } catch (IllegalArgumentException e) {
            content.report.warn(file, "Collision layer ignored, colliding on the layout instead: " + e.getMessage());
            return null;
        }
    }
}
