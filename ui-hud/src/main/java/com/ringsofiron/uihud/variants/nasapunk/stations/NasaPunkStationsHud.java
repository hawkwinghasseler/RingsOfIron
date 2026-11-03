package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * Nasa-punk art on the stations functional spec (documentation/ui-overhaul/functional-stations.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class NasaPunkStationsHud extends StubVariant {
    public NasaPunkStationsHud() {
        super(HudVariants.Style.NASAPUNK, HudVariants.Spec.STATIONS);
    }
}
