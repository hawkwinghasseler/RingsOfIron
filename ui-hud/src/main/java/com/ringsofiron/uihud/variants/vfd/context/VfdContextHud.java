package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * 90s VFD (FILAMENT) art on the context functional spec (documentation/ui-overhaul/functional-context.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class VfdContextHud extends StubVariant {
    public VfdContextHud() {
        super(HudVariants.Style.VFD, HudVariants.Spec.CONTEXT);
    }
}
