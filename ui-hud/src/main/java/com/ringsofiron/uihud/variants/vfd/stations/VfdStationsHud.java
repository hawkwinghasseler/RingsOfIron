package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * 90s VFD (FILAMENT) art on the stations functional spec (documentation/ui-overhaul/functional-stations.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class VfdStationsHud extends StubVariant {
    public VfdStationsHud() {
        super(HudVariants.Style.VFD, HudVariants.Spec.STATIONS);
    }
}
