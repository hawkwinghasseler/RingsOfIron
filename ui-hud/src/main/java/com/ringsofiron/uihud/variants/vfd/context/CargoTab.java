package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent.CargoStack;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The ship's Cargo (functional-context 10.2): the hold's contents by commodity, with the units, the
 * volume and the mass and how many bays each takes, and the hold used of its capacity. It reads the
 * hold itself, so it shows in flight as well as docked.
 */
final class CargoTab extends SectionTab {
    static final int STACK = ITEM;

    private final Array<String> names = new Array<>();
    private final ObjectIntMap<String> bays = new ObjectIntMap<>();

    CargoTab() {
        super("CARGO", ClassicWindow.CREW, "I");
        keys.define(0, 6);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return d.ctx.ship != null;
    }

    @Override
    void build(Boards b, HudData d) {
        CargoHoldComponent hold = d.ctx.ship == null ? null : d.ctx.ship.getComponent(CargoHoldComponent.class);
        if (hold == null || hold.bayTileCount() == 0) {
            head("HOLD");
            note("THIS SHIP HAS NO CARGO HOLD");
            return;
        }
        head(String.format(Locale.ROOT, "HOLD: %s OF %s M3, %d BAYS", Glass.units(hold.usedVolume()), Glass.units(hold.capacity()), hold.bayTileCount()));
        note(cols.clear().left("COMMODITY", 30).right("UNITS", 12).right("VOLUME", 14).right("MASS", 14).right("BAYS", 14).toString());
        // One row a commodity, however many bays it fills
        names.clear();
        bays.clear();
        for (CargoStack s : hold.stacks) {
            if (s == null || s.commodity == null || s.units <= 1e-6) continue;
            if (!names.contains(s.commodity, false)) names.add(s.commodity);
            bays.getAndIncrement(s.commodity, 0, 1);
        }
        for (int i = 0; i < names.size; i++) {
            String c = names.get(i);
            double units = hold.amount(c), volume = 0, mass = 0;
            for (CargoStack s : hold.stacks) {
                if (s == null || !c.equals(s.commodity)) continue;
                volume += s.volume();
                mass += s.mass();
            }
            add(STACK, i, cols.clear().left(c.replace('_', ' ').toUpperCase(Locale.ROOT), 30).right(Glass.units(units), 12)
                    .right(Glass.units(volume) + " M3", 14).right(Glass.units(mass / 1000.0) + " T", 14).right(bays.get(c, 0) + " BAYS", 14).toString());
        }
        if (names.size == 0) note("THE HOLD IS EMPTY");
    }

    @Override
    void keysFor(int focus) {
    }

    @Override
    void press(int k, int focus) {
    }
}
