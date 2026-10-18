package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;

/**
 * Makes an entity visible to sensors and seekers: ships, missiles and decoys
 * (combat-sys/Design.md 4.4). Strengths are relative; a decoy that outshines the ship it
 * protects can steal a seeker's lock.
 */
public class SignatureComponent implements Component {
    public float radar = 1f;
    public float thermal = 1f;
    /** Rough size, meters, used for the fire decision and point-defense aim. */
    public float radius = 5f;

    public SignatureComponent() {
    }

    public SignatureComponent(float radar, float thermal, float radius) {
        this.radar = radar;
        this.thermal = thermal;
        this.radius = radius;
    }
}
