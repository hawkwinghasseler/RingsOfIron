package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * Nasa-punk art on the task functional spec (documentation/ui-overhaul/functional-task.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class NasaPunkTaskHud extends StubVariant {
    public NasaPunkTaskHud() {
        super(HudVariants.Style.NASAPUNK, HudVariants.Spec.TASK);
    }
}
