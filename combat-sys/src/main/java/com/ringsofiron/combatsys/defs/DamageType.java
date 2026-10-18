package com.ringsofiron.combatsys.defs;

import com.google.gson.annotations.SerializedName;

/** How a round or warhead does its damage. Sets fire chance and blast behaviour. */
public enum DamageType {
    @SerializedName(value = "kinetic", alternate = "KINETIC") KINETIC,
    @SerializedName(value = "explosive", alternate = "EXPLOSIVE") EXPLOSIVE,
    @SerializedName(value = "energy", alternate = "ENERGY") ENERGY;

    /** Chance per joule-of-resistance that damage of this type lights a flammable tile. */
    public float fireChance() {
        switch (this) {
            case ENERGY:    return 0.6f;
            case EXPLOSIVE: return 0.35f;
            default:        return 0.08f;
        }
    }
}
