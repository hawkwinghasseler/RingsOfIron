package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.StubVariant;

/**
 * 90s VFD (FILAMENT) art on the task functional spec (documentation/ui-overhaul/functional-task.md).
 * Not built yet: shows the classic HUD. Its own PR replaces this class's body.
 */
public class VfdTaskHud extends StubVariant {
    public VfdTaskHud() {
        super(HudVariants.Style.VFD, HudVariants.Spec.TASK);
    }
}
