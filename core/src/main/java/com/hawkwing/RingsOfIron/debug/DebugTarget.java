package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Objects;

/**
 * Something the developer console can act on: a crew member, a deck tile (and the system
 * installed on it), a ship or station, or a round, missile or decoy in flight. {@link DebugPicker}
 * finds them under the cursor; {@link DebugActions} does things to them.
 */
public final class DebugTarget {
    public enum Kind {
        CREW("crew member"),
        /** A tile of a ship's deck plan, with the system on it if there is one. */
        TILE("tile"),
        SHIP("ship"),
        STATION("station"),
        PROJECTILE("round"),
        MISSILE("missile"),
        DECOY("decoy");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public final Kind kind;
    /** The crew member, ship, station, round, missile or decoy; for a tile, its ship. */
    public final Entity entity;
    /** The craft it is aboard or part of: the crew member's ship, the tile's ship, or the ship itself. Null in flight. */
    public final Entity ship;
    /** The tile, for {@link Kind#TILE}; -1 otherwise. */
    public final int tileX, tileY;

    private DebugTarget(Kind kind, Entity entity, Entity ship, int tileX, int tileY) {
        this.kind = kind;
        this.entity = entity;
        this.ship = ship;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public static DebugTarget crew(Entity crew, Entity ship) {
        return new DebugTarget(Kind.CREW, crew, ship, -1, -1);
    }

    public static DebugTarget tile(Entity ship, int x, int y) {
        return new DebugTarget(Kind.TILE, ship, ship, x, y);
    }

    public static DebugTarget craft(Entity ship, boolean station) {
        return new DebugTarget(station ? Kind.STATION : Kind.SHIP, ship, ship, -1, -1);
    }

    public static DebugTarget inFlight(Kind kind, Entity entity) {
        return new DebugTarget(kind, entity, null, -1, -1);
    }

    public boolean isCrew() {
        return kind == Kind.CREW;
    }

    public boolean isTile() {
        return kind == Kind.TILE;
    }

    /** A ship or station: something with a hull. */
    public boolean isCraft() {
        return kind == Kind.SHIP || kind == Kind.STATION;
    }

    /** The ship's deck plan, or null. */
    public ShipGridComponent grid() {
        return ship == null ? null : ship.getComponent(ShipGridComponent.class);
    }

    /** The tile, for a tile target; null otherwise. */
    public Tile getTile() {
        ShipGridComponent g = grid();
        return g == null || !isTile() ? null : g.getTile(tileX, tileY);
    }

    /** The system installed on the tile, or null. */
    public SubsystemComponent getSystem() {
        Tile t = getTile();
        if (t == null || t.mount == null || t.mount.installed == null) return null;
        return t.mount.installed.getComponent(SubsystemComponent.class);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DebugTarget)) return false;
        DebugTarget t = (DebugTarget) o;
        return kind == t.kind && entity == t.entity && ship == t.ship && tileX == t.tileX && tileY == t.tileY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, System.identityHashCode(entity), tileX, tileY);
    }

    @Override
    public String toString() {
        return DebugNames.describe(this);
    }
}
