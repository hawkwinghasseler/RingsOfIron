package com.ringsofiron.moddingdata.defs;

/**
 * How the strain between the factions changes the world around the player (round 6).
 * {@code tension.<id>}; the game reads {@code tension.default}.
 * <pre>
 * [tension.default]
 * price_rise = 0.25                # share added to a station's yard prices at full tension, in proportion below it
 * combat_jobs_at = 50.0            # tension at a station's owner from which its board posts extra combat jobs
 * combat_jobs = 1                  # how many
 * distrust_per_job = 25.0          # a rival's board posts one job fewer for each this much tension with the player's sponsor
 * fewest_offers = 2                # but never fewer than this many
 * trusted_standing = 10.0          # standing with the rival at which it no longer holds the sponsor against the player
 * base_rises_after_days = 20.0     # days at high tension after the pirates' base falls before a new one rises
 * </pre>
 */
public class TensionDef {
    public String id;
    public float priceRise = 0.25f;
    public float combatJobsAt = 50f;
    public int combatJobs = 1;
    public float distrustPerJob = 25f;
    public int fewestOffers = 2;
    public float trustedStanding = 10f;
    public double baseRisesAfterDays = 20;
}
