package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * The Box2D simulation inside ships and stations: an {@link InteriorWorld} for each deck someone
 * is walking, and a {@link CrewBody} for each crew member on their feet on one. Worlds are made
 * when the first crew member steps onto a deck and dropped when the last one leaves it. Owned by
 * the {@link CrewService} and driven by the {@link CrewMovementSystem}.
 */
public final class CrewPhysics implements Disposable {

    /** In the order they were made, so each tick runs the same way. */
    private final Array<InteriorWorld> worlds = new Array<>();
    private final ObjectMap<Entity, CrewBody> bodies = new ObjectMap<>();

    /** The body of {@code crew}, or null while they are not on their feet on a deck. */
    public CrewBody body(Entity crew) {
        return bodies.get(crew);
    }

    /**
     * The body of {@code crew} on {@code nav}'s deck at (x, y), tiles: made there if they had none
     * or had one on another deck, and moved there if something else moved them since the last tick.
     */
    CrewBody attach(Entity crew, GridNavGraph nav, float x, float y) {
        ShipGridComponent grid = nav.grid;
        CrewBody b = bodies.get(crew);
        if (b != null && b.world.grid != grid) {
            release(crew);
            b = null;
        }
        if (b == null) {
            InteriorWorld w = world(nav);
            b = new CrewBody(crew, w, x, y);
            w.crew.add(b);
            bodies.put(crew, b);
        } else if (Math.abs(b.lastX - x) > 1e-4f || Math.abs(b.lastY - y) > 1e-4f) {
            b.teleport(x, y);
        }
        return b;
    }

    /** Takes {@code crew}'s body out of its world: they are down, carried, or off the deck. */
    void release(Entity crew) {
        CrewBody b = bodies.remove(crew);
        if (b == null) return;
        b.world.crew.removeValue(b, true);
        b.world.world.destroyBody(b.body);
    }

    /** The world inside {@code grid}, if anyone is walking it. */
    InteriorWorld worldOf(ShipGridComponent grid) {
        for (int i = 0; i < worlds.size; i++) {
            if (worlds.get(i).grid == grid) return worlds.get(i);
        }
        return null;
    }

    private InteriorWorld world(GridNavGraph nav) {
        InteriorWorld w = worldOf(nav.grid);
        if (w == null) {
            w = new InteriorWorld(nav);
            worlds.add(w);
        }
        return w;
    }

    /** Steps every deck with crew on it, and drops the decks no one is on. */
    void step(float dt) {
        for (int i = worlds.size - 1; i >= 0; i--) {
            InteriorWorld w = worlds.get(i);
            if (w.crew.size == 0) {
                worlds.removeIndex(i);
                w.dispose();
            }
        }
        for (int i = 0; i < worlds.size; i++) worlds.get(i).step(dt);
    }

    /** Drops every world and body, e.g. after a load replaced every grid. */
    public void clear() {
        for (InteriorWorld w : worlds) w.dispose();
        worlds.clear();
        bodies.clear();
    }

    @Override
    public void dispose() {
        clear();
    }
}
