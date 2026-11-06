package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.StubVariant;
import com.ringsofiron.uihud.variants.cassette.YardScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * Cassette-futurism art on the task functional spec (documentation/ui-overhaul/functional-task.md).
 * Not built yet: shows the classic HUD, with the Cassette menus and map. Its own PR replaces this
 * class's body and keeps the screens.
 */
public class CassetteTaskHud extends StubVariant {
    /** The menus and the map in the Yard Standard, the same under all three Cassette HUDs. */
    private final YardScreens screens = new YardScreens();

    public CassetteTaskHud() {
        super(HudVariants.Style.CASSETTE, HudVariants.Spec.TASK);
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
