package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;

/** The systems stripped off a craft for salvage ({@link Salvage}), by mount origin as "x,y". Saved with it. */
public class SalvagedComponent implements Component {
    public ArrayList<String> stripped = new ArrayList<>();

    public boolean has(int x, int y) {
        return stripped.contains(x + "," + y);
    }

    public void add(int x, int y) {
        if (!has(x, y)) stripped.add(x + "," + y);
    }
}
