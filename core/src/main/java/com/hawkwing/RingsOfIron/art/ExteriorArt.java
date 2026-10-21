package com.hawkwing.RingsOfIron.art;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;

/**
 * Draws craft from outside with the art pack: the hull of the chassis a craft was built from, a
 * engine for each engine system (sized to its slot) with its plume while the main engine burns, and each weapon's turret on its hardpoint,
 * turned to where it aims. Exterior art is in the body frame (64 SVG units to the meter), so it
 * is drawn straight at the body's pose, in world meters.
 */
public final class ExteriorArt {
    private final ArtLibrary art;
    private final Vector2 at = new Vector2();
    private final Vector2 local = new Vector2();
    final Vector2 socket = new Vector2();
    float socketDegrees;

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
        pivoted(batch, hull, px, py, angle * MathUtils.radiansToDegrees, 1f, 1f);
        engines(batch, craft, body, angle);
        turrets(batch, craft, body);
        return true;
    }

    private void plumes(Batch batch, Entity craft, Body body, float px, float py, float angle) {
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || grid == null || controls.forward <= 0f) return;
        float throttle = Math.min(1f, controls.forward);
        if (hasEngines(grid)) {
            for (MountFootprint m : grid.mounts) {
                SubsystemComponent sub = engineIn(m);
                if (sub == null || !sub.isOperational || !socket(grid, m)) continue;
                AtlasRegion plume = art.region(ArtNames.exhaust(ArtNames.engineSize(m.size)));
                if (plume == null) continue;
                Vector2 w = world(body, socket.x, socket.y);
                pivoted(batch, plume, w.x, w.y, (angle * MathUtils.radiansToDegrees) + socketDegrees, throttle, 1f);
            }
            return;
        }
        AtlasRegion plume = art.first(ArtNames.exhaust(ArtNames.ENGINE_SIZE), "hulls/thruster_plume");
        if (plume == null) return;
        for (int k = 0; k < engineCount(grid); k++) {
            Vector2 w = engineMount(body, grid, k);
            pivoted(batch, plume, w.x, w.y, angle * MathUtils.radiansToDegrees, throttle, 1f);
        }
    }

    /** Where a burning engine's exhaust leaves the craft. */
    public interface NozzleSink {
        /**
         * A nozzle at ({@code x}, {@code y}) world meters, its exhaust going toward
         * {@code exhaust} radians, {@code width} meters across, burning at {@code throttle} (0-1).
         */
        void nozzle(float x, float y, float exhaust, float width, float throttle);
    }

    /**
     * Each engine of {@code craft} burning now, where its plume is drawn: one for each running
     * engine system, or on a craft with no engine systems one for each engine mount its hull art
     * draws. Nothing for a craft without hull art, or with its main engine off.
     */
    public void burningNozzles(Entity craft, Body body, NozzleSink out) {
        if (hullOf(craft) == null) return;
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || grid == null || controls.forward <= 0f) return;
        float throttle = Math.min(1f, controls.forward), angle = body.getAngle();
        if (hasEngines(grid)) {
            for (MountFootprint m : grid.mounts) {
                SubsystemComponent sub = engineIn(m);
                if (sub == null || !sub.isOperational || !socket(grid, m)) continue;
                Vector2 w = world(body, socket.x, socket.y);
                float exhaust = angle + (socketDegrees + 180f) * MathUtils.degreesToRadians;
                out.nozzle(w.x, w.y, exhaust, Math.min(m.width(), m.height()) * grid.tileSize, throttle);
            }
            return;
        }
        for (int k = 0; k < engineCount(grid); k++) {
            Vector2 w = engineMount(body, grid, k);
            out.nozzle(w.x, w.y, angle + MathUtils.PI, grid.tileSize, throttle);
        }
    }

    /**
     * The craft's engines, their throats hot while the main engine burns: one for each engine
     * system, sized to its slot, where its nozzle meets space; or, on a craft with no engine
     * systems, a small one in each engine mount the hull art draws.
     */
    private void engines(Batch batch, Entity craft, Body body, float angle) {
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || grid == null) return;
        boolean burning = controls.forward > 0f;
        if (hasEngines(grid)) {
            for (MountFootprint m : grid.mounts) {
                SubsystemComponent sub = engineIn(m);
                if (sub == null || !socket(grid, m)) continue;
                AtlasRegion engine = art.first(ArtNames.engine(ArtNames.engineSize(m.size), burning && sub.isOperational));
                if (engine == null) continue;
                Vector2 w = world(body, socket.x, socket.y);
                pivoted(batch, engine, w.x, w.y, (angle * MathUtils.radiansToDegrees) + socketDegrees, 1f, 1f);
            }
            return;
        }
        AtlasRegion engine = art.first(ArtNames.engine(ArtNames.ENGINE_SIZE, burning));
        if (engine == null) return;
        for (int k = 0; k < engineCount(grid); k++) {
            Vector2 w = engineMount(body, grid, k);
            pivoted(batch, engine, w.x, w.y, angle * MathUtils.radiansToDegrees, 1f, 1f);
        }
    }

    private static boolean hasEngines(ShipGridComponent grid) {
        for (MountFootprint m : grid.mounts) {
            if (engineIn(m) != null) return true;
        }
        return false;
    }

    private static SubsystemComponent engineIn(MountFootprint m) {
        SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
        return sub != null && sub.type == SubsystemComponent.SubsystemType.ENGINE ? sub : null;
    }

    /**
     * Finds where an engine's nozzle meets space: from the end of its slot the nozzle faces,
     * out through the walls behind it to the first open space or the grid's edge. Sets
     * {@link #socket} (grid local meters) and {@link #socketDegrees} (the art's turn from
     * nozzle -x); false when the nozzle faces into the ship.
     */
    boolean socket(ShipGridComponent grid, MountFootprint m) {
        int r = m.rotation;
        int dx = r == 1 ? -1 : r == 3 ? 1 : 0, dy = r == 0 ? 1 : r == 2 ? -1 : 0;
        // the middle line of the slot, and the first tile past the end it faces
        float midX = m.originX + m.width() / 2f, midY = m.originY + m.height() / 2f;
        int x = dx == 0 ? (int) Math.floor(midX - 0.5f) : dx < 0 ? m.originX - 1 : m.originX + m.width();
        int y = dy == 0 ? (int) Math.floor(midY - 0.5f) : dy < 0 ? m.originY - 1 : m.originY + m.height();
        while (grid.inBounds(x, y) && grid.getTile(x, y).isWall()) {
            x += dx;
            y += dy;
        }
        if (grid.inBounds(x, y) && !grid.getTile(x, y).isVoid()) return false;
        float s = grid.tileSize;
        float edgeX = dx == 0 ? midX : dx < 0 ? x + 1 : x;
        float edgeY = dy == 0 ? midY : dy < 0 ? y + 1 : y;
        socket.set(grid.gridOriginLocal.x + edgeX * s, grid.gridOriginLocal.y + edgeY * s);
        socketDegrees = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 180f;
        return true;
    }

    /** An engine mount on the aft edge for every third row, as the hull art draws them. */
    private static int engineCount(ShipGridComponent grid) {
        return grid.height / 3;
    }

    /** World position of engine mount {@code k}'s socket. */
    private Vector2 engineMount(Body body, ShipGridComponent grid, int k) {
        float s = grid.tileSize;
        float row = k * 3 + (grid.height % 3) / 2f + 1.5f;
        return world(body, grid.gridOriginLocal.x - 0.25f * s, grid.gridOriginLocal.y + (grid.height - row) * s);
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
            pivoted(batch, r, w.x, w.y, (body.getAngle() + m.aim) * MathUtils.radiansToDegrees, 1f, 1f);
        }
    }

    private Vector2 world(Body body, float x, float y) {
        Vector2 w = body.getWorldPoint(local.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER));
        return local.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    /**
     * Art whose SVG origin is its pivot (a hull's body origin, a turret's bearing, a plume's
     * nozzle), drawn with that origin at ({@code x}, {@code y}) and turned {@code degrees};
     * {@code length} and {@code width} scale it along its own x and y.
     */
    private void pivoted(Batch batch, AtlasRegion r, float x, float y, float degrees, float length, float width) {
        ArtLibrary.Info info = art.info(r);
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        float left = info == null ? -r.originalWidth / 2f * u : info.x * u;
        float top = info == null ? r.originalHeight / 2f * u : -info.y * u;
        float w = (info == null ? r.originalWidth : info.w) * u;
        float h = (info == null ? r.originalHeight : info.h) * u;
        // SVG y runs down, so the region's bottom edge is at body y = -(viewBox y + height)
        float bottom = top - h;
        batch.draw(r, x + left, y + bottom, -left, -bottom, w, h, length, width, degrees);
    }

    // ---- Combat: rounds, missiles, decoys, beams and explosions, in world meters ----

    /**
     * A round or missile with its nose at ({@code x}, {@code y}) heading {@code radians}, grown
     * so it is at least {@code minLength} meters long and stays visible zoomed out. False when
     * there is no art for it.
     */
    public boolean projectile(Batch batch, String[] names, float x, float y, float radians, float minLength) {
        AtlasRegion r = art.first(names);
        if (r == null) return false;
        ArtLibrary.Info info = art.info(r);
        float length = (info == null ? r.originalWidth : info.w) / ArtLibrary.UNITS_PER_TILE;
        float scale = Math.max(1f, minLength / length);
        pivoted(batch, r, x, y, radians * MathUtils.radiansToDegrees, scale, scale);
        return true;
    }

    /** A decoy flare at ({@code x}, {@code y}), moving along ({@code vx}, {@code vy}). */
    public boolean decoy(Batch batch, float x, float y, float vx, float vy, float minSize) {
        return projectile(batch, new String[]{ArtNames.DECOY}, x, y, MathUtils.atan2(vy, vx), minSize);
    }

    /** The laser strip stretched from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), {@code width} meters wide. */
    public boolean beam(Batch batch, float x0, float y0, float x1, float y1, float width) {
        AtlasRegion r = art.region(ArtNames.BEAM);
        if (r == null) return false;
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        batch.draw(r, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
        return true;
    }

    /** An explosion {@code size} meters across at ({@code x}, {@code y}), {@code progress} of the way through. */
    public boolean explosion(Batch batch, float x, float y, float size, float progress) {
        AtlasRegion r = art.region(ArtNames.explosion(progress));
        if (r == null) return false;
        batch.draw(r, x - size / 2f, y - size / 2f, size, size);
        return true;
    }
}
