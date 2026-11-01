package com.hawkwing.RingsOfIron.art;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.render.HullShading;
import com.hawkwing.RingsOfIron.render.RcsNozzles;
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
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

/**
 * Draws craft from outside with the art pack: the hull of the chassis a craft was built from,
 * an engine for each engine system (over
 * its slot, outside the hull), each RCS block, and each weapon's turret on its hardpoint, turned to where it aims;
 * each active docking port's collar while it is out (its head, and the tunnel back to the port);
 * and the flames of the engines and RCS nozzles that fire, which {@code ThrusterFlames} draws in a
 * pass of their own. Exterior art is in the body frame (64 SVG units to the meter), so it
 * is drawn straight at the body's pose, in world meters.
 */
public final class ExteriorArt {
    private final ArtLibrary art;
    private final HullShading shading = new HullShading();
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
        if (!lit(batch, hull, px, py, angle)) hull(batch, hull, art.info(hull), px, py, angle);
        rcs(batch, craft, body);
        engines(batch, craft, body, angle);
        collars(batch, craft, body);
        turrets(batch, craft, body);
        return true;
    }

    /** True if {@code body} is the collar head of one of {@code craft}'s ports, which {@link #draw} draws with the craft. */
    public static boolean isCollarHead(Entity craft, Body body) {
        DockingPortsComponent ports = craft == null ? null : craft.getComponent(DockingPortsComponent.class);
        if (ports == null) return false;
        for (DockingPort p : ports.ports) {
            if (p.head == body || p.carriage == body) return true;
        }
        return false;
    }

    /**
     * Each active port's collar that is out: the head where it is (on its own body while it
     * reaches and draws in, or locked out from the port once latched), and the tunnel from the
     * collar on the hull out to the head, a tile of pleats for every meter or so, stretched to fit.
     */
    private void collars(Batch batch, Entity craft, Body body) {
        DockingPortsComponent ports = craft.getComponent(DockingPortsComponent.class);
        if (ports == null) return;
        for (DockingPort p : ports.ports) {
            boolean locked = p.head == null && p.lockedExtension > 0f && p.state == DockingPort.State.DOCKED;
            if (p.head == null && !locked) continue;
            AtlasRegion head = art.first(ArtNames.collarHead(p.size, p.armed || !p.isFree()), ArtNames.collarHead(p.size, true));
            AtlasRegion tunnel = art.region(ArtNames.collarTunnel(p.size));
            if (head == null) continue;
            // The head's middle and the way its face points, in the world
            float hx, hy, nx, ny;
            if (locked) {
                Vector2 c = world(body, p.edgeX + p.normalX * (p.lockedExtension + p.collarDepth * 0.5f),
                        p.edgeY + p.normalY * (p.lockedExtension + p.collarDepth * 0.5f));
                hx = c.x;
                hy = c.y;
                Vector2 n = body.getWorldVector(local.set(p.normalX, p.normalY));
                nx = n.x;
                ny = n.y;
            } else {
                Vector2 c = PhysicsManager.getPosition(p.head, at);
                hx = c.x;
                hy = c.y;
                Vector2 n = p.head.getWorldVector(local.set(p.normalX, p.normalY));
                nx = n.x;
                ny = n.y;
            }
            // From the collar's face on the hull to the back of the head
            Vector2 base = world(body, p.edgeX + p.normalX * p.collarDepth, p.edgeY + p.normalY * p.collarDepth);
            float bx = base.x, by = base.y;
            float tx = hx - nx * p.collarDepth * 0.5f - bx, ty = hy - ny * p.collarDepth * 0.5f - by;
            float length = (float) Math.sqrt(tx * tx + ty * ty);
            if (tunnel != null && length > 0.02f) {
                float degrees = MathUtils.atan2(ty, tx) * MathUtils.radiansToDegrees;
                int pieces = Math.max(1, Math.round(length));
                float piece = length / pieces;
                for (int i = 0; i < pieces; i++) {
                    float f = i * piece / length;
                    pivoted(batch, tunnel, bx + tx * f, by + ty * f, degrees, piece, 1f);
                }
            }
            pivoted(batch, head, hx, hy, MathUtils.atan2(ny, nx) * MathUtils.radiansToDegrees, 1f, 1f);
        }
    }

    /** The hull art {@code r} placed as {@code info} says. */
    private void hull(Batch batch, AtlasRegion r, ArtLibrary.Info info, float px, float py, float angle) {
        pivoted(batch, r, info, px, py, angle * MathUtils.radiansToDegrees, 1f, 1f);
    }

    /**
     * The lights for this frame that shade the lit hulls by their normal maps, world meters:
     * {@code count} of them in {@code xyrs} as x, y, reach, strength; {@code ambient} is how
     * bright the scene is without them. See {@link HullShading#setPointLights}.
     */
    public void setPointLights(float[] xyrs, int count, float ambient) {
        shading.setPointLights(xyrs, count, ambient);
    }

    /** The hull lit by the game ({@link HullShading}) when its mode asks for it; false to draw the baked art. */
    private boolean lit(Batch batch, AtlasRegion hull, float px, float py, float angle) {
        ArtLibrary.Info info = art.info(hull);
        return shading.draw(batch, art, hull, px, py, angle, r -> hull(batch, r, info, px, py, angle));
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
        pivoted(batch, r, art.info(r), x, y, degrees, length, width);
    }

    /** {@link #pivoted} placed as {@code info} says (art packed without an index entry of its own, as a hull's lit twins are). */
    private void pivoted(Batch batch, AtlasRegion r, ArtLibrary.Info info, float x, float y, float degrees, float length, float width) {
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
