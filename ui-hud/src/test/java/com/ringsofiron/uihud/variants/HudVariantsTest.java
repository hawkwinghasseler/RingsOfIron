package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.Insets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudVariantsTest {
    @AfterEach
    void reset() {
        HudVariants.setChosen(HudVariants.CLASSIC);
    }

    @Test
    void theClassicHudAndEveryCombinationAreListedOnce() {
        assertEquals(1 + HudVariants.Style.values().length * HudVariants.Spec.values().length, HudVariants.all().size);
        assertEquals(HudVariants.CLASSIC, HudVariants.all().first().id);
        Set<String> ids = new HashSet<>();
        for (HudVariants.Entry e : HudVariants.all()) assertTrue(ids.add(e.id), e.id);
        for (HudVariants.Style style : HudVariants.Style.values()) {
            for (HudVariants.Spec spec : HudVariants.Spec.values()) {
                HudVariants.Entry e = HudVariants.find(style.id + "." + spec.id);
                assertEquals(style, e.style);
                assertEquals(spec, e.spec);
            }
        }
    }

    @Test
    void eachCombinationLivesInItsOwnPackage() {
        for (HudVariants.Entry e : HudVariants.all()) {
            HudVariant v = e.create();
            if (e.style == null) {
                assertNull(v);
                continue;
            }
            assertNotNull(v, e.id);
            assertEquals("com.ringsofiron.uihud.variants." + e.style.id + "." + e.spec.id, v.getClass().getPackage().getName());
        }
    }

    @Test
    void aVariantLeavesTheGamesInsetsAsTheClassicHudHasThemUnlessItSaysOtherwise() {
        HudVariant v = new HudVariant() {
            @Override
            public void attach(HudVariantHost host) {
            }

            @Override
            public void update(float delta) {
            }
        };
        Array<Rectangle> panels = new Array<>();
        v.panels(panels);
        assertEquals(0, panels.size);
        assertFalse(v.drawsInsets());
        Insets.Room room = new Insets.Room().set(0f, 0f, 0f);
        v.insetRoom(room);
        assertEquals(0f, room.side + room.above + room.below);
        for (Insets.Role role : Insets.Role.values()) assertTrue(v.wantsInset(role), role.name());
    }

    @Test
    void anUnknownChoiceIsTheClassicHud() {
        HudVariants.setChosen("nonsense.thing");
        assertEquals(HudVariants.CLASSIC, HudVariants.getChosen());
        assertEquals(HudVariants.CLASSIC, HudVariants.find(null).id);
        HudVariants.setChosen("vfd.task");
        assertEquals("vfd.task", HudVariants.getChosen());
    }
}
