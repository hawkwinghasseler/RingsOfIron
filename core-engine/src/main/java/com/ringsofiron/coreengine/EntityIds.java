package com.ringsofiron.coreengine;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import java.util.UUID;

/**
 * Where new entity UUIDs come from. Random until {@link #seed(long)}; a seeded source hands out the
 * same UUIDs in the same order every run, so a seeded game names its stations and ships the same
 * each time, and anything keyed or rolled by UUID (hash map order, a dice roll per freighter)
 * replays. The seed and how many UUIDs were handed out are saved, so a loaded game goes on with
 * the ones it would have made. A save without them gets random UUIDs, as before.
 */
public final class EntityIds implements SaveParticipant {
    private boolean seeded;
    private long seed;
    private long count;

    /** Hands out UUIDs from {@code seed} from now on, starting again at the first. */
    public void seed(long seed) {
        this.seed = seed;
        this.seeded = true;
        this.count = 0;
    }

    /** Random UUIDs from now on. */
    public void unseed() {
        seeded = false;
        seed = 0;
        count = 0;
    }

    public boolean isSeeded() {
        return seeded;
    }

    /** A new UUID: the next one from the seed, or a random one. */
    public String next() {
        if (!seeded) return UUID.randomUUID().toString();
        long n = count++;
        long hi = mix(seed, 2 * n), lo = mix(seed, 2 * n + 1);
        // Marked as a version 4 (random) UUID, IETF variant, like UUID.randomUUID()
        hi = (hi & ~0xF000L) | 0x4000L;
        lo = (lo & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(hi, lo).toString();
    }

    /** SplitMix64 of {@code seed} and {@code n}. */
    private static long mix(long seed, long n) {
        long z = seed + 0x9E3779B97F4A7C15L * (n + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public String saveKey() {
        return "engine.ids";
    }

    @Override
    public void write(Json json) {
        json.writeValue("seeded", seeded);
        if (!seeded) return;
        json.writeValue("seed", seed);
        json.writeValue("count", count);
    }

    @Override
    public void read(Json json, JsonValue data) {
        if (data.getBoolean("seeded", false)) {
            seed(data.getLong("seed", 0));
            count = data.getLong("count", 0);
        } else {
            unseed();
        }
    }
}
