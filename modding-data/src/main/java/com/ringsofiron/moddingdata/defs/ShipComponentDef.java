package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ship component: a system a chassis mounts, built on one of interior-grid's subsystem
 * behaviors with its own name, map letter, sizes and settings.
 *
 * <pre>
 * [component.fusion_core]
 * name = "Fusion core"
 * symbol = "F"
 * behavior = "REACTOR"
 * sizes = ["M", "L"]
 * color = "#ff9a3c"
 * power_output = { M = 4.0e6, L = 1.4e7 }
 * </pre>
 */
public class ShipComponentDef {
    public String id;
    public String name;
    /** The one letter chassis rows draw this component with. */
    public String symbol;
    /** A SubsystemType name, e.g. REACTOR. It fixes the mount kind and what the system does. */
    public String behavior;
    /** Mount sizes it comes in (S, M, L, XL); empty means every size its mount kind has. */
    public List<String> sizes = new ArrayList<>();
    /** How the editor draws it, "#rrggbb". */
    public String color;
    /** REACTOR: watts at full health, by mount size. Sizes left out use the standard output. */
    public Map<String, Double> powerOutput = new LinkedHashMap<>();
    /** LIFE_SUPPORT: multiplies scrubbing, air and oxygen supply. */
    public float throughput = 1f;
    /** TANK: what it holds: OXYGEN, NITROGEN or PROPELLANT (the default). */
    public String fluid;
    /** TANK: kilograms it holds, by mount size. Sizes left out hold the fluid's standard amount a tile. */
    public Map<String, Double> capacity = new LinkedHashMap<>();
    /** ENGINE: newtons of main-engine thrust at full health, by mount size. Sizes left out use the standard thrust. */
    public Map<String, Double> thrust = new LinkedHashMap<>();
}
