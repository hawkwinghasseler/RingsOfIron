package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * Cassette-futurism art on the context functional spec (documentation/ui-overhaul/functional-context.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class CassetteContextHud extends StubVariant {
    public CassetteContextHud() {
        super(HudVariants.Style.CASSETTE, HudVariants.Spec.CONTEXT);
    }
}
