package com.hawkwing.RingsOfIron.game;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * How a new career starts: the seed that varies the world, and the faction the player serves.
 *
 * <p>A seeded game moves the stations along their orbits, sets how many freighters fly and what
 * they start with, which stations pirates lurk by first, each market's opening stock (within
 * {@link #STOCK_SPREAD}) and the combat and crew dice. The same seed gives the same start. The
 * {@link #designed()} start, which tests and First Play use, is the world exactly as laid out.
 */
public final class NewGame {
    /** How far a seeded station's opening stock strays from its target, either way. */
    public static final double STOCK_SPREAD = 0.25;
    /** Freighters a seeded game adds to the one based at each station, at most. */
    public static final int EXTRA_FREIGHTERS = 4;

    /**
     * The seed of the designed start's UUIDs. 2026, which the round 5 tests that fight in a designed
     * game were first seeded with by hand; with some other UUIDs, the first fight in
     * {@code PlayableLoopTest} holes the ship and the captain goes down while stripping the prize.
     */
    public static final long DESIGNED_IDS = 2026;

    public final long seed;
    /** False for the designed start: no variation, and the round-2 dice. */
    public final boolean seeded;
    /** The id of the faction the player serves, or null for the data's sponsor. */
    public final String sponsor;

    private NewGame(long seed, boolean seeded, String sponsor) {
        this.seed = seed;
        this.seeded = seeded;
        this.sponsor = sponsor;
    }

    /** The world as laid out, serving the data's sponsor. */
    public static NewGame designed() {
        return new NewGame(0, false, null);
    }

    public static NewGame seeded(long seed, String sponsor) {
        return new NewGame(seed, true, sponsor);
    }

    /** A fresh random seed. */
    public static NewGame random(String sponsor) {
        return seeded(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L), sponsor);
    }

    /**
     * The game a typed seed asks for: a number is that seed, any other text a seed made from it, and
     * nothing at all a random one.
     */
    public static NewGame fromText(String text, String sponsor) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return random(sponsor);
        try {
            return seeded(Long.parseLong(t), sponsor);
        } catch (NumberFormatException e) {
            return seeded(mix(t.toLowerCase(Locale.ROOT).hashCode(), 0x5EED) & 0x7fffffffffffL, sponsor);
        }
    }

    public NewGame withSponsor(String sponsor) {
        return new NewGame(seed, seeded, sponsor);
    }

    /** The seed of the dice for one part of the world, {@code part}, so each part varies on its own. */
    public long stream(int part) {
        return mix(seed, part);
    }

    /** The combat dice's seed: round 2's fixed one for the designed start. */
    public long combatSeed() {
        return seeded ? stream(1) : 1L;
    }

    /** The crew dice's seed. */
    public long crewSeed() {
        return seeded ? stream(2) : 0xC2E3L;
    }

    /** "Seed 1234", or "Designed start", for the screen. */
    public String describe() {
        return seeded ? "Seed " + seed : "Designed start";
    }

    /** SplitMix64 of {@code seed} and {@code part}. */
    static long mix(long seed, long part) {
        long z = seed + 0x9E3779B97F4A7C15L * (part + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
