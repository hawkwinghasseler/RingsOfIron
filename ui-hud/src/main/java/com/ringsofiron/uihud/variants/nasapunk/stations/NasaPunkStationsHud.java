package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.StubVariant;
import com.ringsofiron.uihud.variants.nasapunk.NpScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * Nasa-punk art on the stations functional spec (documentation/ui-overhaul/functional-stations.md).
 * Not built yet: shows the classic HUD, with the Nasa-punk menus and map. Its own PR replaces this
 * class's body and keeps the screens.
 */
public class NasaPunkStationsHud extends StubVariant {
    /** The menus as pages of CDU-1 and the map's system plot, the same under all three Nasa-punk HUDs. */
    private final NpScreens screens = new NpScreens();

    public NasaPunkStationsHud() {
        super(HudVariants.Style.NASAPUNK, HudVariants.Spec.STATIONS);
    }

    @Override
    public ShellLook shellLook() {
        return screens.shellLook();
    }

    @Override
    public MapPainter mapPainter() {
        return screens.mapPainter();
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        screens.resize(uiScale);
    }

    @Override
    public void detach() {
        screens.dispose();
    }
}
