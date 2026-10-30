package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.CrewBody;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds what is under the cursor for the developer console, and keeps the selection. A click
 * selects the nearest thing there; another click on the same spot selects the next thing under
 * it (a crew member, then the tile they stand on, then the ship), round and round.
 *
 * <p>In the deck view the things are the crew, the tiles and the craft whose decks are drawn; in
 * the outside view, rounds, missiles and decoys in flight and every ship and station.
 * Positions are world meters, as the game's world camera has them.
 */
public final class DebugPicker {
    /** How far from a small thing, in screen pixels, a click still picks it. */
    public static final float SLACK_PIXELS = 8f;

    private static final Family ROUNDS = Family.all(ProjectileComponent.class).get();
    private static final Family DECOYS = Family.all(DecoyComponent.class).get();

    private final GameWorld world;
    private DebugTarget selected;
    private final Array<Body> bodies = new Array<>();
    private final Vector2 tmp = new Vector2();

    public DebugPicker(GameWorld world) {
        this.world = world;
    }

    public DebugTarget selected() {
        return selected;
    }

    public void select(DebugTarget target) {
        selected = target;
    }

    public void clear() {
        selected = null;
    }

    /**
     * Selects what is under ({@code x}, {@code y}): the first thing there, or the one after the
     * selection when the selection is there too. Null when nothing is.
     */
    public DebugTarget click(float x, float y, boolean deckView, float metersPerPixel, Collection<Entity> decks) {
        List<DebugTarget> under = under(x, y, deckView, metersPerPixel, decks);
        if (under.isEmpty()) {
            selected = null;
            return null;
        }
        int at = selected == null ? -1 : under.indexOf(selected);
        selected = under.get((at + 1) % under.size());
        return selected;
    }

    /**
     * Everything under ({@code x}, {@code y}), the smallest first. {@code decks} are the craft
     * whose decks the deck view draws; null takes every craft with a deck plan.
     */
    public List<DebugTarget> under(float x, float y, boolean deckView, float metersPerPixel, Collection<Entity> decks) {
        List<DebugTarget> out = new ArrayList<>();
        float slack = SLACK_PIXELS * metersPerPixel;
        if (deckView) {
            deckThings(x, y, slack, decks, out);
        } else {
            inFlight(x, y, slack, out);
            craft(x, y, slack, out);
        }
        return out;
    }

    /**
     * Where {@code t} is now, in world meters, into {@code out}, and how far it reaches from
     * there; -1 when it has left the bubble or the world (a round that hit, a ship gone to the rails).
     */
    public float locate(DebugTarget t, Vector2 out) {
        if (t == null) return -1f;
        switch (t.kind) {
            case CREW: {
                GridPositionComponent p = t.entity.getComponent(GridPositionComponent.class);
                ShipGridComponent g = p == null || p.ship == null ? null : p.ship.getComponent(ShipGridComponent.class);
                Body body = g == null ? null : world.docking.bodyOf(p.ship);
                if (body == null) return -1f;
                toWorld(body, p.toShipLocal(g, out), out);
                return CrewBody.RADIUS * g.tileSize;
            }
            case TILE: {
                ShipGridComponent g = t.grid();
                Body body = g == null ? null : world.docking.bodyOf(t.ship);
                if (body == null) return -1f;
                toWorld(body, out.set(g.tileToLocal(t.tileX, t.tileY)), out);
                return g.tileSize * 0.5f;
            }
            case SHIP:
            case STATION: {
                Body body = world.docking.bodyOf(t.entity);
                if (body == null) return -1f;
                ShipGridComponent g = t.grid();
                PhysicsManager.getPosition(body, out);
                return g == null ? 20f : 0.5f * (float) Math.hypot(g.width * g.tileSize, g.height * g.tileSize);
            }
            case DECOY: {
                DecoyComponent d = t.entity.getComponent(DecoyComponent.class);
                if (d == null) return -1f;
                out.set(d.pos);
                return 1f;
            }
            default: {
                ProjectileComponent p = t.entity.getComponent(ProjectileComponent.class);
                if (p == null) return -1f;
                out.set(p.pos);
                MissileComponent m = t.entity.getComponent(MissileComponent.class);
                return m != null ? m.def.hitRadius * 3f : 1f;
            }
        }
    }

    /** {@code local}, body-local meters, as world meters into {@code out}. */
    private static void toWorld(Body body, Vector2 local, Vector2 out) {
        Vector2 w = body.getWorldPoint(local.scl(PhysicsManager.UNITS_PER_METER));
        out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    private void deckThings(float x, float y, float slack, Collection<Entity> decks, List<DebugTarget> out) {
        List<Entity> ships = new ArrayList<>();
        if (decks != null) {
            ships.addAll(decks);
        } else {
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(ShipGridComponent.class).get())) ships.add(e);
        }
        List<DebugTarget> tiles = new ArrayList<>(), craft = new ArrayList<>();
        for (Entity ship : ships) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            Body body = world.docking.bodyOf(ship);
            if (g == null || body == null) continue;
            Vector2 local = body.getLocalPoint(tmp.set(x, y).scl(PhysicsManager.UNITS_PER_METER)).scl(PhysicsManager.METERS_PER_UNIT);
            float lx = local.x, ly = local.y;
            // The crew first: they stand on the tile, and are what a click there most likely means
            float reach = CrewBody.RADIUS * g.tileSize + slack;
            for (Entity e : world.crew.aboard(ship)) {
                GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                Vector2 at = p.toShipLocal(g, tmp);
                if (at.dst2(lx, ly) <= reach * reach) out.add(DebugTarget.crew(e, ship));
            }
            int[] t = g.localToTile(lx, ly);
            if (t[0] < 0) continue;
            Tile tile = g.getTile(t[0], t[1]);
            if (tile == null || tile.isVoid()) continue;
            tiles.add(DebugTarget.tile(ship, t[0], t[1]));
            craft.add(DebugTarget.craft(ship, ship.getComponent(StationComponent.class) != null));
        }
        out.addAll(tiles);
        out.addAll(craft);
    }

    private void inFlight(float x, float y, float slack, List<DebugTarget> out) {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(ROUNDS)) {
            ProjectileComponent p = e.getComponent(ProjectileComponent.class);
            MissileComponent m = e.getComponent(MissileComponent.class);
            float reach = slack + (m != null ? m.def.hitRadius * 3f : 0f);
            if (p.pos.dst2(x, y) <= reach * reach) {
                out.add(DebugTarget.inFlight(m != null ? DebugTarget.Kind.MISSILE : DebugTarget.Kind.PROJECTILE, e));
            }
        }
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            if (d.pos.dst2(x, y) <= slack * slack) out.add(DebugTarget.inFlight(DebugTarget.Kind.DECOY, e));
        }
    }

    private void craft(float x, float y, float slack, List<DebugTarget> out) {
        world.physics.getWorld().getBodies(bodies);
        Vector2 point = new Vector2(x, y).scl(PhysicsManager.UNITS_PER_METER);
        List<Entity> seen = new ArrayList<>();
        for (Body body : bodies) {
            Object owner = body.getUserData();
            if (!(owner instanceof Entity)) continue;
            Entity e = (Entity) owner;
            boolean station = e.getComponent(StationComponent.class) != null;
            if (!station && e.getComponent(ShipGridComponent.class) == null) continue;
            if (seen.contains(e)) continue;
            boolean hit = false;
            for (Fixture f : body.getFixtureList()) {
                if (f.testPoint(point)) {
                    hit = true;
                    break;
                }
            }
            // A ship drawn a few pixels across is hard to hit: its middle will do
            if (!hit) hit = PhysicsManager.getPosition(body, tmp).dst2(x, y) <= slack * slack;
            if (!hit) continue;
            seen.add(e);
            out.add(DebugTarget.craft(e, station));
        }
        bodies.clear();
    }
}
