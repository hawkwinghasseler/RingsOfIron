package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.StubVariant;
import com.ringsofiron.uihud.variants.vfd.VfdScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * 90s VFD (FILAMENT) art on the task functional spec (documentation/ui-overhaul/functional-task.md).
 * Not built yet: shows the classic HUD, with the FILAMENT menus and map. Its own PR replaces this
 * class's body and keeps the screens.
 */
public class VfdTaskHud extends StubVariant {
    /** The menus in setup mode and the map's plot, the same under all three FILAMENT HUDs. */
    private final VfdScreens screens = new VfdScreens();

    public VfdTaskHud() {
        super(HudVariants.Style.VFD, HudVariants.Spec.TASK);
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
