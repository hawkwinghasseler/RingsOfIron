package com.ringsofiron.uihud.providers;

/**
 * What the navigation map's mouse planning does to the player's planned burns, and the orders that
 * fly them. Points are meters in the map's frame, as {@link OrbitPathProvider.OrbitPath} gives them;
 * burns are counted as the map draws them, soonest first. Each order tells the player how it went on
 * its own (the game posts a notice). Implemented by the game's wiring layer (core), over its burn
 * planner and navigation.
 */
public interface ManeuverPlannerProvider {
    /**
     * Plans a new burn with no delta-v where the path the ship will fly passes nearest (x, y), and
     * selects it, if that is within {@code tolerance} meters. Returns whether one was planned.
     */
    boolean addBurnNear(double x, double y, double tolerance);

    /** Selects the {@code index}th burn the map draws. */
    void selectBurn(int index);

    /** The selected burn's prograde and radial delta-v, m/s, into {@code out}; false with none selected. */
    boolean selectedBurn(double[] out);

    /** Sets the selected burn's delta-v, m/s along the velocity and radially out. */
    void setSelectedBurn(double prograde, double radial);

    /** Moves the selected burn to where the orbit it is on passes nearest (x, y), never into the past. */
    void moveSelectedNear(double x, double y);

    void deleteSelected();

    void clearBurns();

    /** Rails warp to just before the next burn. */
    void warpToBurn();

    /** Orders the helmsman to fly the planned burns with the engines. */
    void helmFlyBurns();

    /** Hands the transfer to the navigation target to the autopilot, or stops it. */
    void autopilot();

    void nextTarget();

    /** Proposes a transfer to the navigation target, for {@link #acceptPlan} or {@link #discardPlan}. */
    void planToTarget();

    void acceptPlan();

    void discardPlan();

    boolean hasTarget();

    boolean hasProposal();

    /** Lines about the orbit now: the body, apsides, period and when the ship reaches each apsis. */
    String orbitText();

    /** Lines about the selected burn and where it leads; empty with none. */
    String burnText();

    /** Lines about the whole plan: delta-v against the tanks, the target, the helm and autopilot. */
    String planText();
}
