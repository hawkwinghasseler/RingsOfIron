package com.ringsofiron.moddingdata.defs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a kind of station makes and uses each economy cycle. {@code station_type.<id>}.
 * <pre>
 * [station_type.agri_dome]
 * name = "Agricultural Dome"
 * population = 20000
 * produces = { food = 50.0 }       # units per cycle, keyed by commodity id
 * consumes = { water = 30.0, machinery = 1.0 }
 * storage = 5000.0                 # units of each good it can hold
 * </pre>
 */
public class StationTypeDef {
    public String id;
    public String name;
    public int population;
    public Map<String, Float> produces = new LinkedHashMap<>();
    public Map<String, Float> consumes = new LinkedHashMap<>();
    public float storage = 1000f;
}
