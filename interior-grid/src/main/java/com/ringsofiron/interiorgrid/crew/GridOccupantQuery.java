package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Answers which crew stand on a tile of a ship's interior grid. Implemented by
 * {@code ai-crew}; used by combat to find who a penetrating round, spall or blast hits.
 *
 * Unconscious and dead crew still occupy their tile and are returned too, so further
 * hits can kill the unconscious.
 */
public interface GridOccupantQuery {

    /**
     * Adds every crew entity standing on tile (x, y) of {@code ship}'s interior grid to
     * {@code out}. Crew being carried count as standing on their carrier's tile.
     *
     * @param ship the entity holding the ShipGridComponent
     */
    void occupantsAt(Entity ship, int x, int y, Array<Entity> out);
}
