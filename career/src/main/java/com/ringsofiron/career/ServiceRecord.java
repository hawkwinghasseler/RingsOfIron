package com.ringsofiron.career;

/**
 * The player's career, saved with the game by {@link FactionStanding} rather than on a ship, so it
 * outlives any one ship: the faction they serve, their rank on its ladder, their standing with each
 * faction by id (0 until they have dealt with it), and when the next budget is paid.
 */
public class ServiceRecord {
    /** Id of the faction the player serves. */
    public String sponsor;
    /** Place on the sponsor's rank ladder, 0 the lowest. */
    public int rank;
    /** Faction ids, matched with {@link #standing}. */
    public String[] factions = new String[0];
    public float[] standing = new float[0];
    /** Sim seconds of the next budget payday; negative until the first is set. */
    public double nextPay = -1;
    /** Credits the budget has paid in all. */
    public double budgetPaid;
    /** Sim seconds of the last promotion (0 for the career's start), for the next rank's minimum time in grade. */
    public double promotedAt;
    /** Sim seconds each rank was given, by its place on the ladder (0 for the start, or a save from before round 7). */
    public double[] promotions = new double[0];

    /** Sim seconds rank {@code i} was given; 0 if never recorded. */
    public double promotedTo(int i) {
        return i >= 0 && i < promotions.length ? promotions[i] : 0;
    }

    public float get(String faction) {
        for (int i = 0; i < factions.length; i++) {
            if (factions[i].equals(faction)) return standing[i];
        }
        return 0f;
    }

    /** Sets the standing with {@code faction}, kept within {@link FactionStanding#MIN} and {@link FactionStanding#MAX}. */
    public void set(String faction, float value) {
        value = Math.max(FactionStanding.MIN, Math.min(FactionStanding.MAX, value));
        for (int i = 0; i < factions.length; i++) {
            if (factions[i].equals(faction)) {
                standing[i] = value;
                return;
            }
        }
        factions = java.util.Arrays.copyOf(factions, factions.length + 1);
        standing = java.util.Arrays.copyOf(standing, standing.length + 1);
        factions[factions.length - 1] = faction;
        standing[standing.length - 1] = value;
    }
}
