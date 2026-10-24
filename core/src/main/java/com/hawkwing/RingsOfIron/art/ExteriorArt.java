package com.hawkwing.RingsOfIron.art;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.render.RcsNozzles;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.EngineLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Draws craft from outside with the art pack: the hull of the chassis a craft was built from
 * (warped where collisions have dented and crushed it), an engine for each engine system (over
 * its slot, outside the hull), each RCS block, and each weapon's turret on its hardpoint, turned to where it aims;
 * and the flames of the engines and RCS nozzles that fire, which {@code ThrusterFlames} draws in a
 * pass of their own. Exterior art is in the body frame (64 SVG units to the meter), so it
 * is drawn straight at the body's pose, in world meters.
 */
public final class ExteriorArt {
    private final ArtLibrary art;
    private final RcsNozzles nozzles = new RcsNozzles();
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
     * The hull, its RCS blocks, engines and turrets, in a batch already begun in world meters.
     * False when the craft has no hull art, so the caller draws its outline instead. The flames
     * of its engines and thrusters are not drawn here but by {@link #flame}, after the light map,
     * since they give off their own light.
     */
    public boolean draw(Batch batch, Entity craft, Body body) {
        AtlasRegion hull = hullOf(craft);
        if (hull == null) return false;
        Vector2 p = PhysicsManager.getPosition(body, at);
        float px = p.x, py = p.y, angle = body.getAngle();
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (grid.dents == null || hull.rotate || !dented(batch, hull, grid, px, py, angle)) {
            pivoted(batch, hull, px, py, angle * MathUtils.radiansToDegrees, 1f, 1f);
        }
        rcs(batch, craft, body);
        engines(batch, craft, body, angle);
        turrets(batch, craft, body);
        return true;
    }

    /** The craft's RCS blocks on its hull; a block whose hull tile is gone is drawn wrecked. */
    private void rcs(Batch batch, Entity craft, Body body) {
        nozzles.blocks(craft, body, (r, works, x, y, degrees) -> {
            AtlasRegion block = art.first(ArtNames.rcs(r.kind.name(), works), ArtNames.rcs(r.kind.name(), true));
            if (block != null) pivoted(batch, block, x, y, degrees - 180f, 1f, 1f);
        });
    }

    /**
     * The art of a main engine's plume of {@code size} ({@link ArtNames#engineSize}), its model's own
     * for {@code component} when it has one, or of an RCS nozzle's jet when {@code size} is null;
     * null when the art pack has none.
     */
    public AtlasRegion flameArt(String size, String component) {
        if (size == null) return art.region(ArtNames.RCS_PUFF);
        AtlasRegion r = art.first(ArtNames.exhaust(component, size));
        if (r == null && ArtNames.ENGINE_SIZE.equals(size)) return art.region("hulls/thruster_plume");
        return r;
    }

    /**
     * A flame's art (a plume or a jet, pivot on the nozzle, exhaust toward its -x) with its
     * nozzle at ({@code x}, {@code y}) world meters and its exhaust going toward {@code exhaust}
     * radians, stretched {@code length} times along the exhaust and {@code width} times across it.
     */
    public void flame(Batch batch, AtlasRegion r, float x, float y, float exhaust, float length, float width) {
        pivoted(batch, r, x, y, exhaust * MathUtils.radiansToDegrees - 180f, length, width);
    }

    /** Where each of a craft's main engines lets its exhaust out. */
    public interface NozzleSink {
        /**
         * An engine of {@code size} ({@link ArtNames#engineSize}) and {@code component} (its id in
         * components.toml; null for one the hull art draws) whose nozzle is at ({@code x},
         * {@code y}) world meters, its exhaust going toward {@code exhaust} radians,
         * {@code width} meters across, burning at {@code throttle} (0-1; 0 when it is not).
         */
        void nozzle(String size, String component, float x, float y, float exhaust, float width, float throttle);
    }

    /**
     * Each main engine of {@code craft} that can burn, where its plume is drawn, and how hard it
     * burns now: one for each engine system that is running, switched on and not disabled, at
     * the throttle scaled by its health, as {@code EngineThrust} gives it thrust; or, on a craft
     * with no engine systems, one for each engine mount its hull art draws. All of them at 0 with
     * the main engine off or the tanks dry. Nothing for a craft without hull art.
     */
    public void engineNozzles(Entity craft, Body body, NozzleSink out) {
        if (hullOf(craft) == null) return;
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || grid == null) return;
        ThrusterComponent thruster = craft.getComponent(ThrusterComponent.class);
        boolean dry = thruster != null && thruster.isOutOfFuel();
        float throttle = dry ? 0f : MathUtils.clamp(controls.forward, 0f, 1f), angle = body.getAngle();
        if (hasEngines(grid)) {
            for (MountFootprint m : grid.mounts) {
                SubsystemComponent sub = engineIn(m);
                if (sub == null) continue;
                socket(grid, m);
                boolean works = sub.isOperational && sub.switchedOn && !sub.isDisabled();
                float burn = works ? throttle * MathUtils.clamp(sub.health, 0f, 1f) : 0f;
                Vector2 w = world(body, socket.x, socket.y);
                float exhaust = angle + (socketDegrees + 180f) * MathUtils.degreesToRadians;
                out.nozzle(ArtNames.engineSize(m.size), sub.component, w.x, w.y, exhaust, Math.min(m.width(), m.height()) * grid.tileSize, burn);
            }
            return;
        }
        for (int k = 0; k < engineCount(grid); k++) {
            Vector2 w = engineMount(body, grid, k, LEGACY_EXIT);
            out.nozzle(ArtNames.ENGINE_SIZE, null, w.x, w.y, angle + MathUtils.PI, grid.tileSize, throttle);
        }
    }

    /**
     * The craft's engines, their throats hot while the main engine burns: one for each engine
     * system, drawn over its slot outside the hull; or, on a craft with no engine systems, a small
     * one in each engine mount the hull art draws.
     */
    private void engines(Batch batch, Entity craft, Body body, float angle) {
        FlightControlComponent controls = craft.getComponent(FlightControlComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (controls == null || grid == null) return;
        boolean burning = controls.forward > 0f;
        if (hasEngines(grid)) {
            float s = grid.tileSize;
            for (MountFootprint m : grid.mounts) {
                SubsystemComponent sub = engineIn(m);
                if (sub == null) continue;
                String size = ArtNames.engineSize(m.size);
                AtlasRegion engine = art.first(ArtNames.nacelle(sub.component, size, burning && sub.isOperational));
                if (engine == null) continue;
                // The art is the slot unturned, nozzle up the canvas; turned with the slot about its middle
                float bw = m.size.baseWidth(m.kind) * s, bh = m.size.baseHeight(m.kind) * s;
                Vector2 c = world(body, grid.gridOriginLocal.x + (m.originX + m.width() / 2f) * s,
                        grid.gridOriginLocal.y + (m.originY + m.height() / 2f) * s);
                batch.draw(engine, c.x - bw / 2f, c.y - bh / 2f, bw / 2f, bh / 2f, bw, bh, 1f, 1f,
                        angle * MathUtils.radiansToDegrees + m.rotation * 90f);
            }
            return;
        }
        AtlasRegion engine = art.first(ArtNames.engine(ArtNames.ENGINE_SIZE, burning));
        if (engine == null) return;
        for (int k = 0; k < engineCount(grid); k++) {
            Vector2 w = engineMount(body, grid, k, 0f);
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
     * Finds where an engine's exhaust leaves it: the middle of the end of its slot the nozzle faces
     * ({@link EngineLayout#nozzle}), out in space. Sets {@link #socket} (grid local meters) and
     * {@link #socketDegrees} (the plume art's turn from exhaust toward -x).
     */
    void socket(ShipGridComponent grid, MountFootprint m) {
        Side out = EngineLayout.nozzle(m);
        float midX = m.originX + m.width() / 2f, midY = m.originY + m.height() / 2f;
        float edgeX = out.dx == 0 ? midX : out.dx < 0 ? m.originX : m.originX + m.width();
        float edgeY = out.dy == 0 ? midY : out.dy < 0 ? m.originY : m.originY + m.height();
        float s = grid.tileSize;
        socket.set(grid.gridOriginLocal.x + edgeX * s, grid.gridOriginLocal.y + edgeY * s);
        socketDegrees = MathUtils.atan2(out.dy, out.dx) * MathUtils.radiansToDegrees - 180f;
    }

    /** An engine mount on the aft edge for every third row, as the hull art draws them. */
    private static int engineCount(ShipGridComponent grid) {
        return grid.height / 3;
    }

    /** Tiles aft of a hull art engine mount's socket that its engine's nozzle ends, where the plume starts. */
    private static final float LEGACY_EXIT = 0.86f;

    /** World position of engine mount {@code k}'s socket, or {@code aft} tiles behind it. */
    private Vector2 engineMount(Body body, ShipGridComponent grid, int k, float aft) {
        float s = grid.tileSize;
        float row = k * 3 + (grid.height % 3) / 2f + 1.5f;
        return world(body, grid.gridOriginLocal.x - (0.25f + aft) * s, grid.gridOriginLocal.y + (grid.height - row) * s);
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

    /** Tile corners this far out of place, in tiles, are drawn at their darkest. */
    private static final float DARKEST_DENT = 1.5f;
    /** How dark the most crumpled metal is drawn: this share of the art's own color. */
    private static final float DENT_SHADE = 0.55f;
    private float[] mesh = new float[0];
    private final FloatArray breaksX = new FloatArray();
    private final FloatArray breaksY = new FloatArray();

    /**
     * The hull art warped by the collisions it has taken ({@link ShipGridComponent#dents}): cut
     * along the grid's tile lines into a mesh whose corners are pushed where the dents put them,
     * and darkened where they are pushed furthest. Art past the grid's edge moves with the
     * nearest edge corner. False if the art has no viewBox to place it by.
     */
    private boolean dented(Batch batch, AtlasRegion r, ShipGridComponent grid, float px, float py, float angle) {
        ArtLibrary.Info info = art.info(r);
        if (info == null) return false;
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        float left = info.x * u, top = -info.y * u, w = info.w * u, h = info.h * u;
        float right = left + w, bottom = top - h;
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        breaks(breaksX, left, right, ox, s, grid.width);
        breaks(breaksY, bottom, top, oy, s, grid.height);
        int quads = (breaksX.size - 1) * (breaksY.size - 1);
        if (mesh.length < quads * 20) mesh = new float[quads * 20];
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        float u0 = r.getU(), v0 = r.getV(), du = r.getU2() - u0, dv = r.getV2() - v0;
        Color tint = batch.getColor();
        int k = 0;
        for (int j = 0; j < breaksY.size - 1; j++) {
            for (int i = 0; i < breaksX.size - 1; i++) {
                // Round the cell: bottom-left, top-left, top-right, bottom-right
                for (int c = 0; c < 4; c++) {
                    float lx = breaksX.get(c >= 2 ? i + 1 : i), ly = breaksY.get(c == 1 || c == 2 ? j + 1 : j);
                    int cx = MathUtils.clamp(Math.round((lx - ox) / s), 0, grid.width);
                    int cy = MathUtils.clamp(Math.round((ly - oy) / s), 0, grid.height);
                    float dx = grid.dentX(cx, cy), dy = grid.dentY(cx, cy);
                    float bx = lx + dx * s, by = ly + dy * s;
                    float shade = 1f - (1f - DENT_SHADE) * Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / DARKEST_DENT);
                    mesh[k++] = px + bx * cos - by * sin;
                    mesh[k++] = py + bx * sin + by * cos;
                    mesh[k++] = Color.toFloatBits(tint.r * shade, tint.g * shade, tint.b * shade, tint.a);
                    mesh[k++] = u0 + (lx - left) / w * du;
                    mesh[k++] = v0 + (top - ly) / h * dv;
                }
            }
        }
        batch.draw(r.getTexture(), mesh, 0, k);
        return true;
    }

    /** {@code from}, every tile line of the grid strictly between, and {@code to}, in body meters. */
    private static void breaks(FloatArray out, float from, float to, float origin, float tile, int tiles) {
        out.clear();
        out.add(from);
        for (int i = 0; i <= tiles; i++) {
            float at = origin + i * tile;
            if (at > from + 1e-4f && at < to - 1e-4f) out.add(at);
        }
        out.add(to);
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
