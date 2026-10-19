package com.hawkwing.RingsOfIron.game;

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
