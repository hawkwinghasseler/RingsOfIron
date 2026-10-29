package com.ringsofiron.combatsys.shields;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * A ship's shield: one cloud of charged particles shared by all its emitters, on the curve
 * {@link #perimeter}, and the regions the emitters have pulled mass into where they expect hits.
 * {@link ShieldSystem} keeps it each tick and {@link com.ringsofiron.combatsys.damage.Shields}
 * resolves hits against it.
 *
 * <p>Mass: {@link #stock} is everything aboard. Of it, {@link #stowed} is packed safe inside the
 * emitters (shields lowered); {@link #scattered} is knocked loose by hits and being pulled back;
 * {@link #adrift} is what no working emitter holds (they lost power, broke, were stunned or
 * tripped), drifting away and being lost unless an emitter comes back in time. The rest,
 * {@link #cloud}, protects the ship. Lowering the shields ({@link #raised} false) packs the cloud
 * into the emitters, where it costs nothing and is not lost; docking does it on its own. Emitters,
 * regions and heat start afresh on load.
 *
 * <p>The cloud is one pool, but its resting mass is not spread evenly: {@link #line} holds how much
 * lies along each stretch of the curve, thicker where more emitters reach and toward the
 * {@link #trim}. Each stretch is anchored to the emitters that reach it ({@link #anchored}); when
 * one stops working, the mass only it held goes adrift first, so a dead emitter opens a weak spot
 * where it was instead of thinning the whole bubble.
 */
public class ShieldCloudComponent implements Component {
    /** How deep the cloud is, meters: mass per meter of perimeter over this is its density. */
    public static final float DEPTH = 2f;

    /** Particle mass aboard, kg; negative until first filled to capacity. */
    public float stock = -1f;
    /** What the installed emitters can hold together, kg. */
    public float capacity;
    /** Mass knocked out of the cloud by hits that has not rejoined yet, kg. */
    public float scattered;
    /** Mass lost out of the bubble since the shield was fitted, kg. */
    public float lost;
    /** Packed inside the emitters, kg: safe, costing nothing, not protecting the ship. */
    public float stowed;
    /** Out of the emitters' hold, drifting away, kg: lost over time unless recaptured. */
    public float adrift;
    /** The crew's order: shields up (the cloud out round the hull) or down (packed into the emitters). */
    public boolean raised = true;
    /** Lowered on its own for docking, and raised again on undocking. */
    public boolean loweredForDock;
    /** Share of the emitters' capacity that is working now, 0 to 1. */
    public float holding;
    /** A reactor aboard is stunned: emitters it cuts off keep their share as scattered mass. */
    public transient boolean powerStunned;
    /** Which way the crew lean the resting cloud. */
    public Trim trim = Trim.EVEN;

    /** The resting layer's mass along each segment of the curve, kg; kept summing to it each tick. */
    public final transient float[] line = new float[ShieldPerimeter.SAMPLES];
    /** Each segment's share held by working emitters, 0 to 1: the rest is no working emitter's. */
    public final transient float[] anchored = new float[ShieldPerimeter.SAMPLES];
    /** Where the emitters want the resting layer, each segment's weight (any scale). */
    final transient float[] want = new float[ShieldPerimeter.SAMPLES];

    public transient ShieldPerimeter perimeter;
    transient ShipGridComponent fittedTo;
    transient float refitIn;

    public final transient Array<Region> regions = new Array<>();
    public final transient ObjectMap<Entity, Emitter> emitters = new ObjectMap<>();
    /** The latest hits, oldest first, for the particles drawn; at most {@link #HIT_LOG} kept. */
    public final transient Array<Hit> hits = new Array<>();
    /** Counts every hit logged, so a reader can tell which it has seen. */
    public transient int hitCount;
    static final int HIT_LOG = 32;
    transient float beamLogIn;

    /** Mass protecting the ship now, kg. */
    public float cloud() {
        return Math.max(0f, stock - stowed - scattered - adrift);
    }

    /** Mass in regions now, kg. */
    public float concentrated() {
        float m = 0f;
        for (Region r : regions) m += r.mass;
        return m;
    }

    /** The thin layer outside the regions, kg per meter of perimeter. */
    public float baseLineDensity() {
        if (perimeter == null || perimeter.length <= 0f) return 0f;
        return Math.max(0f, cloud() - concentrated()) / perimeter.length;
    }

    /**
     * The thin layer outside the regions {@code s} meters along the perimeter, kg per meter:
     * {@link #line}'s share of it there, or the even spread before it has one.
     */
    public float baseLineDensityAt(float s) {
        if (perimeter == null || perimeter.length <= 0f) return 0f;
        float sum = 0f;
        for (float m : line) sum += m;
        if (!(sum > 0f)) return baseLineDensity();
        int i = perimeter.segmentAt(s);
        float len = perimeter.along[i + 1] - perimeter.along[i];
        if (len < 1e-4f) return baseLineDensity();
        float base = Math.max(0f, cloud() - concentrated());
        return base * line[i] / sum / len;
    }

    /** Areal density of the cloud {@code s} meters along the perimeter, kg/m^2. */
    public float densityAt(float s) {
        float sigma = baseLineDensityAt(s) / DEPTH;
        for (Region r : regions) sigma += r.densityAt(perimeter.between(r.s, s));
        return sigma;
    }

    /** One emitter's state. */
    public static class Emitter {
        public EmitterSpec spec;
        /** Its footprint's center, body-local meters. */
        public float x, y;
        /** Heat it holds, J. */
        public float heat;
        public boolean tripped;
        /** Its circuit couldn't pay to hold its share last tick. */
        public boolean starved;
        /** Switched on, powered, not broken, stunned or tripped, and its holding draw was paid. */
        public boolean working;
        /** Recapture work owed for mass scattered from its regions, J. */
        public float recaptureOwed;
        /** Power drawn last tick, W. */
        public float draw;
    }

    /** Mass an emitter has pulled toward one expected hit. */
    public static class Region {
        /** What it was gathered for: a round in flight, or a beam's spot. */
        public Object threat;
        public Entity emitter;
        public EmitterSpec spec;
        /** Its center along the perimeter, meters, and the mass it is gathering toward, kg. */
        public float s, target;
        /** Mass in it now, kg. */
        public float mass;
        /** Seconds until the threat arrives (a beam's region: seconds it is held after the beam stops). */
        public float eta;
        public boolean beam;
        boolean seen;

        /** Peak areal density, kg/m^2. */
        public float peak() {
            return mass / (spec.width * DEPTH);
        }

        /** Areal density {@code x} meters along the perimeter from its center: a raised cosine one width each way. */
        public float densityAt(float x) {
            float w = spec.width;
            if (x <= -w || x >= w) return 0f;
            return peak() * 0.5f * (1f + (float) Math.cos(Math.PI * x / w));
        }
    }

    /** A hit the cloud took part of, as the particles show it. */
    public static class Hit {
        public int index;
        /** Where along the perimeter, and the body-local direction the threat was travelling. */
        public float s, dirX, dirY;
        /** Energy absorbed, J; mass knocked loose and mass lost out of the bubble, kg; how fast it was thrown, m/s. */
        public float absorbed, scattered, lost, speed;
        public boolean beam;
    }

    void log(Hit h) {
        h.index = hitCount++;
        if (hits.size >= HIT_LOG) hits.removeIndex(0);
        hits.add(h);
    }
}
