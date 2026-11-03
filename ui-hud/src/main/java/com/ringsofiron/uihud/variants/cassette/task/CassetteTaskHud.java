package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * Cassette-futurism art on the task functional spec (documentation/ui-overhaul/functional-task.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class CassetteTaskHud extends StubVariant {
    public CassetteTaskHud() {
        super(HudVariants.Style.CASSETTE, HudVariants.Spec.TASK);
    }
}
