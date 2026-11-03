package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * Nasa-punk art on the context functional spec (documentation/ui-overhaul/functional-context.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class NasaPunkContextHud extends StubVariant {
    public NasaPunkContextHud() {
        super(HudVariants.Style.NASAPUNK, HudVariants.Spec.CONTEXT);
    }
}
