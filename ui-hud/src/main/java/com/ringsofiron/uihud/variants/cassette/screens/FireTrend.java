package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;

/**
 * Whether each fire aboard is spreading or shrinking (Task spec v16, the damage table's progress
 * for a fire): its tiles burning ({@code Alert.count}) against 10 s before. Watched every frame by
 * sim time, so the trend is known when a console opens.
 */
final class FireTrend {
    /** How far back a fire's tiles are set against, and how often they are taken, in sim seconds. */
    static final double BACK = 10.0, EVERY = 1.0;
    private static final int KEEP = (int) (BACK / EVERY) + 2;

    /** A fire's tiles as taken, oldest first. */
    private static final class Fire {
        final double[] at = new double[KEEP];
        final int[] tiles = new int[KEEP];
        int n;
        boolean seen;
    }

    private final ObjectMap<String, Fire> fires = new ObjectMap<>();
    private final Array<Fire> pool = new Array<>();
    private final Array<String> gone = new Array<>();

    /** Takes each fire's tiles from {@code alerts} at sim time {@code now}: call each frame. */
    void update(AlertRegistry alerts, double now) {
        if (alerts == null) return;
        for (ObjectMap.Entry<String, Fire> e : fires.entries()) e.value.seen = false;
        Array<Alert> all = alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (!a.id.startsWith("fire:")) continue;
            Fire f = fires.get(a.id);
            if (f == null) {
                f = pool.size > 0 ? pool.pop() : new Fire();
                f.n = 0;
                fires.put(a.id, f);
            }
            f.seen = true;
            // A load or a new career runs the clock back: the fire starts its record again
            if (f.n > 0 && now < f.at[f.n - 1]) f.n = 0;
            if (f.n == 0 || now - f.at[f.n - 1] >= EVERY) add(f, now, a.count);
        }
        gone.clear();
        for (ObjectMap.Entry<String, Fire> e : fires.entries()) if (!e.value.seen) gone.add(e.key);
        for (int i = 0; i < gone.size; i++) pool.add(fires.remove(gone.get(i)));
    }

    private static void add(Fire f, double now, int tiles) {
        if (f.n == KEEP) {
            System.arraycopy(f.at, 1, f.at, 0, KEEP - 1);
            System.arraycopy(f.tiles, 1, f.tiles, 0, KEEP - 1);
            f.n--;
        }
        f.at[f.n] = now;
        f.tiles[f.n] = tiles;
        f.n++;
    }

    /**
     * Fire {@code id}'s course at sim time {@code now} with {@code tiles} burning: "spreading" or
     * "shrinking" against 10 s before, or against its first count while it is younger; "" while it
     * holds, or with nothing to go by.
     */
    String trend(String id, int tiles, double now) {
        Fire f = fires.get(id);
        if (f == null || f.n == 0) return "";
        int then = f.tiles[0];
        for (int i = f.n - 1; i >= 0; i--) {
            if (now - f.at[i] >= BACK) {
                then = f.tiles[i];
                break;
            }
        }
        return tiles > then ? "spreading" : tiles < then ? "shrinking" : "";
    }
}
