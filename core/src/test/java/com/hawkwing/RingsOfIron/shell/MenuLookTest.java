package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.ShellScreen;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLookTest {
    /** A look that writes down what is asked of it. */
    static class Look implements ShellLook {
        final Skin skin;
        final List<Float> scales = new ArrayList<>();
        boolean disposed;

        Look(Skin skin) {
            this.skin = skin;
        }

        @Override
        public Skin skin() {
            return skin;
        }

        @Override
        public boolean rescale(float uiScale) {
            scales.add(uiScale);
            return true;
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    /** Variants by id, each made fresh when asked, as {@code HudVariants.find(id).create()} does; null for the classic HUD. */
    final Map<String, Supplier<ShellLook>> looks = new HashMap<>();
    final Map<String, Integer> made = new HashMap<>();
    final List<ShellLook> last = new ArrayList<>();

    MenuLook menuLook() {
        return new MenuLook(id -> {
            if (!looks.containsKey(id)) return null;
            made.merge(id, 1, Integer::sum);
            return new HudVariant() {
                @Override
                public void attach(HudVariantHost host) {
                    throw new AssertionError("the menus' variant is never attached");
                }

                @Override
                public void update(float delta) {
                    throw new AssertionError("the menus' variant is never updated");
                }

                @Override
                public ShellLook shellLook() {
                    ShellLook l = looks.get(id).get();
                    last.add(l);
                    return l;
                }
            };
        });
    }

    Look lastLook() {
        return (Look) last.get(last.size() - 1);
    }

    @Test
    void theClassicHudKeepsTheClassicMenus() {
        MenuLook look = menuLook();
        assertFalse(look.sync("classic", 1f), "the menus were classic already");
        assertNull(look.look());
        assertNull(look.skin());
        looks.put("plain", () -> null);
        assertFalse(look.sync("plain", 1f), "a variant without a look of its own for the menus");
        assertNull(look.look());
    }

    @Test
    void aVariantsLookIsMadeOnceWhenPickedAndScaled() {
        Skin skin = new Skin();
        looks.put("vfd.task", () -> new Look(skin));
        MenuLook look = menuLook();
        assertTrue(look.sync("vfd.task", 1.5f));
        Look made1 = lastLook();
        assertSame(made1, look.look());
        assertSame(skin, look.skin());
        assertEquals(List.of(1.5f), made1.scales, "scaled once, as soon as it is made");
        assertFalse(look.sync("vfd.task", 1.5f));
        assertEquals(1, made.get("vfd.task"), "made once while it stays picked");
        assertFalse(made1.disposed);
    }

    @Test
    void anotherPickDisposesTheLastLook() {
        looks.put("vfd.task", () -> new Look(new Skin()));
        looks.put("cassette.task", () -> new Look(new Skin()));
        MenuLook look = menuLook();
        look.sync("vfd.task", 1f);
        Look vfd = lastLook();
        assertTrue(look.sync("cassette.task", 1f));
        Look cassette = lastLook();
        assertTrue(vfd.disposed);
        assertSame(cassette, look.look());
        assertTrue(look.sync("classic", 1f), "back to the classic menus");
        assertTrue(cassette.disposed);
        assertNull(look.look());
        assertNull(look.skin());
    }

    @Test
    void aLookWithoutASkinIsDisposedAndTheClassicMenusStay() {
        Look bare = new Look(null);
        looks.put("nasapunk.task", () -> bare);
        MenuLook look = menuLook();
        assertFalse(look.sync("nasapunk.task", 1f));
        assertTrue(bare.disposed);
        assertNull(look.look());
    }

    @Test
    void aLookThatFailsToStartLeavesTheClassicMenusAndIsNotRetried() {
        Look broken = new Look(new Skin()) {
            @Override
            public boolean rescale(float uiScale) {
                throw new IllegalStateException("no font");
            }
        };
        looks.put("vfd.task", () -> broken);
        MenuLook look = menuLook();
        assertFalse(look.sync("vfd.task", 1f));
        assertTrue(broken.disposed);
        assertNull(look.look());
        assertFalse(look.sync("vfd.task", 1f));
        assertEquals(1, made.get("vfd.task"), "not made again every frame");
    }

    @Test
    void aLookThatFailsInUseIsDroppedAndDisposedOnceTheMenusAreBuiltWithoutIt() {
        looks.put("vfd.task", () -> new Look(new Skin()));
        MenuLook look = menuLook();
        look.sync("vfd.task", 1f);
        Look vfd = lastLook();
        look.fail("draw", new IllegalStateException("broken"));
        assertNull(look.look(), "the classic backdrop draws this frame");
        assertNull(look.skin());
        assertFalse(vfd.disposed, "the menus showing still use its skin");
        assertTrue(look.sync("vfd.task", 1f), "the menus are built again, classic");
        assertTrue(vfd.disposed);
        assertNull(look.look());
        assertFalse(look.sync("vfd.task", 1f));
        assertEquals(1, made.get("vfd.task"), "not made again until another pick");
        assertFalse(look.sync("classic", 1f), "the classic menus are up already");
        assertTrue(look.sync("vfd.task", 1f), "picked again, it is made again");
        assertEquals(2, made.get("vfd.task"));
    }

    @Test
    void disposingDisposesTheLooks() {
        looks.put("vfd.task", () -> new Look(new Skin()));
        MenuLook look = menuLook();
        look.sync("vfd.task", 1f);
        Look vfd = lastLook();
        look.dispose();
        assertTrue(vfd.disposed);
        assertNull(look.look());
    }

    @Test
    void everyMenuIsAScreenALookDresses() {
        for (ShellMenus.Panel p : ShellMenus.Panel.values()) {
            assertEquals(p.name(), GameShell.screen(p).name());
        }
        assertEquals(ShellMenus.Panel.values().length + 1, ShellScreen.values().length, "the menus and the mod scripts dialog");
    }
}
