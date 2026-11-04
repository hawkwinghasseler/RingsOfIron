package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.DeckPlan;
import com.ringsofiron.uihud.variants.vfd.Filament;

/**
 * SCH, the ship schematic in the bottom left corner (functional-task 6, SCH): the deck plan on a
 * graphic dot panel with what is wrong drawn in its room (the systems that are down ringed among
 * the hazards), and the last hull hit flashing where it landed. No text. At the helm and gunnery
 * only, and only while one of HULL, FIRE, AIR, POWER, CREW or EVA is lit; on deck the captain is
 * looking at the ship itself.
 */
final class Schematic {
    final DeckPlan plan = new DeckPlan();
    boolean on;

    Schematic() {
        plan.systems = true;
    }

    void update(HudData d, Posts p, boolean lit) {
        plan.update(d);
        on = p.schematic(lit) && plan.present();
    }

    // ------------------------------------------------------------------ painting, from the bottom left origin

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.LX, y = Geo.SCH_Y, w = Geo.CW, h = Geo.SCH_H;
        f.plate(x, y, w, h, 6f, 8f);
        f.module(x, y, w, h, f.o().filaments(3));
        for (float yy = y + 4f; yy < y + h - 1f; yy += 4f) {
            for (float xx = x + 4f; xx < x + w - 1f; xx += 4f) f.dot(xx, yy, f.o().size(1.05f).ghostOnly().k(0.9f));
        }
        plan.paint(f, x + 10f, y + 10f, w - 20f, h - 20f);
    }
}
