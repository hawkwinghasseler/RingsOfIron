package com.hawkwing.RingsOfIron.art;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;

/**
 * Draws craft from outside with the art pack: the hull of the chassis a craft was built from, a
 * plume from each engine while the main engine burns, and each weapon's turret on its hardpoint,
 * turned to where it aims. Exterior art is in the body frame (64 SVG units to the meter), so it
 * is drawn straight at the body's pose, in world meters.
 */
public final class ExteriorArt {
    private final ArtLibrary art;
    private final Vector2 at = new Vector2();
    private final Vector2 local = new Vector2();

    public ExteriorArt(ArtLibrary art) {
        this.art = art;
    }

    /** The hull art of a craft, or null when it has none (and is drawn as its outline). */
    public AtlasRegion hullOf(Entity craft) {
        ShipGridComponent grid = craft == null ? null : craft.getComponent(ShipGridComponent.class);
        return grid == null ? null : art.region(ArtNames.hull(grid.chassis));
    }

    /**
     * Engine plumes, the hull and its turrets, in a batch already begun in world meters. False
     * when the craft has no hull art, so the caller draws its outline instead.
     */
    public boolean draw(Batch batch, Entity craft, Body body) {
        AtlasRegion hull = hullOf(craft);
        if (hull == null) return false;
        Vector2 p = PhysicsManager.getPosition(body, at);
        float px = p.x, py = p.y, angle = body.getAngle();
        plumes(batch, craft, body, px, py, angle);
        pivoted(batch, hull, px, py, angle * MathUtils.radiansToDegrees, 1f);
        turrets(batch, craft, body);
        return true;
    }

    private void plumes(Batch batch, Entity craft, Body body, float px, float py, float angle) {
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || controls.forward <= 0f) return;
        AtlasRegion plume = art.region("hulls/thruster_plume");
        if (plume == null) return;
        // An engine on the aft edge for every third row, as the hull art draws them
        float s = grid.tileSize;
        for (int k = 0; k < grid.height / 3; k++) {
            float row = k * 3 + (grid.height % 3) / 2f + 1.5f;
            float lx = grid.gridOriginLocal.x - 0.25f * s;
            float ly = grid.gridOriginLocal.y + (grid.height - row) * s;
            Vector2 w = world(body, lx, ly);
            pivoted(batch, plume, w.x, w.y, angle * MathUtils.radiansToDegrees, Math.min(1f, controls.forward));
        }
    }

    private void turrets(Batch batch, Entity craft, Body body) {
        WeaponsComponent weapons = craft.getComponent(WeaponsComponent.class);
        if (weapons == null) return;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def == null || m.def.id == null) continue;
            SubsystemComponent sub = m.subsystem == null ? null : m.subsystem.getComponent(SubsystemComponent.class);
            boolean powered = sub == null || sub.isOperational;
            AtlasRegion r = art.first(ArtNames.turret(m.def.id, powered));
            if (r == null) continue;
            Vector2 w = world(body, m.hardpoint.x, m.hardpoint.y);
            pivoted(batch, r, w.x, w.y, (body.getAngle() + m.aim) * MathUtils.radiansToDegrees, 1f);
        }
    }

    private Vector2 world(Body body, float x, float y) {
        Vector2 w = body.getWorldPoint(local.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER));
        return local.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    /**
     * Art whose SVG origin is its pivot (a hull's body origin, a turret's bearing, a plume's
     * nozzle), drawn with that origin at ({@code x}, {@code y}) and turned {@code degrees};
     * {@code length} scales it along its own x.
     */
    private void pivoted(Batch batch, AtlasRegion r, float x, float y, float degrees, float length) {
        ArtLibrary.Info info = art.info(r);
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        float left = info == null ? -r.originalWidth / 2f * u : info.x * u;
        float top = info == null ? r.originalHeight / 2f * u : -info.y * u;
        float w = (info == null ? r.originalWidth : info.w) * u;
        float h = (info == null ? r.originalHeight : info.h) * u;
        // SVG y runs down, so the region's bottom edge is at body y = -(viewBox y + height)
        float bottom = top - h;
        batch.draw(r, x + left, y + bottom, -left, -bottom, w, h, length, 1f, degrees);
    }
}
