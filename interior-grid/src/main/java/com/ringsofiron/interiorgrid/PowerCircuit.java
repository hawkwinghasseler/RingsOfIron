package com.ringsofiron.interiorgrid;

/**
 * One electrically connected part of a ship's power grid: the floor and conduit tiles that
 * {@link PowerRoutingSystem} reaches from one or more working reactors, and every system
 * standing on them.
 *
 * The circuit's reactors produce {@link #output} watts, which charge a small energy store
 * holding {@link PowerRoutingSystem#STORE_SECONDS} of output. Systems that need bursts of
 * energy, such as weapons, take it from the store with {@link #tryDraw(double)} or
 * {@link #draw(double)}. A damaged reactor produces less, so the store refills more slowly
 * and the guns on that circuit fire less often; a cut conduit that leaves a gun on a circuit
 * with no reactor leaves it with no power at all.
 *
 * Circuits are rebuilt whenever power is rerouted; the stored energy carries over through
 * the reactors that feed it. They are not saved: a loaded game starts with full stores.
 */
public class PowerCircuit {
    /** Watts the circuit's reactors produce. */
    public double output;
    /** Joules held in the store now. */
    public double stored;
    /** Joules the store holds when full. */
    public double capacity;
    /** Joules drawn since the last routing tick, for displays. */
    public double drawnThisTick;

    /** Takes {@code joules} from the store if it holds that much. Returns whether it did. */
    public boolean tryDraw(double joules) {
        if (joules <= 0) return true;
        if (stored < joules) return false;
        stored -= joules;
        drawnThisTick += joules;
        return true;
    }

    /** Takes up to {@code joules} from the store. Returns how much it gave, 0 to {@code joules}. */
    public double draw(double joules) {
        double given = Math.max(0, Math.min(joules, stored));
        stored -= given;
        drawnThisTick += given;
        return given;
    }

    /** Share of the store that is charged, 0 to 1. */
    public float charge() {
        return capacity <= 0 ? 0f : (float) (stored / capacity);
    }

    void recharge(double seconds) {
        stored = Math.min(capacity, stored + output * seconds);
    }
}
