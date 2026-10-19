package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.utils.Array;

/** A list of {@link LightSpot}s whose objects are kept and reused when it is cleared. */
public final class LightSpots {
    private final Array<LightSpot> spots = new Array<>();
    private int size;

    public void clear() {
        size = 0;
    }

    /** A fresh spot at the end of the list. */
    public LightSpot add() {
        if (size == spots.size) spots.add(new LightSpot());
        return spots.get(size++).reset();
    }

    public int size() {
        return size;
    }

    public LightSpot get(int i) {
        if (i >= size) throw new IndexOutOfBoundsException(i + " >= " + size);
        return spots.get(i);
    }
}
