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
 * <p>Mass: {@link #stock} is everything aboard; {@link #scattered} is knocked loose by hits and
 * drifting back; the share of what is left that belongs to emitters not working now (broken,
 * off, unpowered, tripped) drifts loose too. The rest, {@link #cloud}, protects the ship. Only
 * the mass is saved; emitters, regions and heat start afresh on load.
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
    /** Share of the pool held by working emitters, 0 to 1. */
    public float holding;

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
        return Math.max(0f, (stock - scattered) * holding);
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

    /** Areal density of the cloud {@code s} meters along the perimeter, kg/m^2. */
    public float densityAt(float s) {
        float sigma = baseLineDensity() / DEPTH;
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
