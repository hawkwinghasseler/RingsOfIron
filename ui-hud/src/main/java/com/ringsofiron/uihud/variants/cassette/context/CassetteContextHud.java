package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.StubVariant;
import com.ringsofiron.uihud.variants.cassette.YardScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * Cassette-futurism art on the context functional spec (documentation/ui-overhaul/functional-context.md).
 * Not built yet: shows the classic HUD, with the Cassette menus, map and windows. Its own PR replaces
 * this class's body and keeps the screens.
 */
public class CassetteContextHud extends StubVariant {
    /** The menus, the map and the windows in the Yard Standard, the same under all three Cassette HUDs. */
    private final YardScreens screens = new YardScreens();

    public CassetteContextHud() {
        super(HudVariants.Style.CASSETTE, HudVariants.Spec.CONTEXT);
    }

    @Override
    public void attach(HudVariantHost host) {
        super.attach(host);
        screens.attach(host, 0f);
    }

    @Override
    public void update(float delta) {
        screens.update(delta);
    }

    @Override
    public boolean replaces(ClassicWindow window) {
        return screens.replaces(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return screens.open(window);
    }

    @Override
    public boolean showing(ClassicWindow window) {
        return screens.showing(window);
    }

    @Override
    public void close(ClassicWindow window) {
        screens.close(window);
    }

    @Override
    public boolean keyDown(int keycode) {
        return screens.keyDown(keycode);
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
