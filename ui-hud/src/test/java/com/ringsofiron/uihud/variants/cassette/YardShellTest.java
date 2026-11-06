package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.ringsofiron.uihud.FocusNav;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the Yard Standard's menus that need no window: the focus tint, the guard, a moved cell. */
class YardShellTest {

    @BeforeAll
    static void files() {
        // Scene2D's table cells look for Gdx.files once; any will do here
        if (com.badlogic.gdx.Gdx.files == null) {
            com.badlogic.gdx.Gdx.files = (com.badlogic.gdx.Files) java.lang.reflect.Proxy.newProxyInstance(
                    YardShellTest.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Files.class}, (o, m, a) -> null);
        }
    }

    @Test
    void theDrawablesKnowTheFocusTintTheMenusUse() throws Exception {
        // The menus tint the focused control; a Yard control shows its pointer tab for that tint instead
        Field f = FocusNav.class.getDeclaredField("FOCUS");
        f.setAccessible(true);
        Color tint = (Color) f.get(null);
        assertEquals(tint, YardDrawable.FOCUS);
        assertTrue(YardDrawable.isFocus(new Color(tint.r, tint.g, tint.b, 0.4f)), "at any alpha, as in a fade");
        assertFalse(YardDrawable.isFocus(Color.WHITE));
    }

    @Test
    void aGuardedButtonGivesItsOrderOnlyOnASecondPressWhileItsGuardIsUp() {
        Button b = new Button(new Button.ButtonStyle());
        int[] orders = {0};
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                orders[0]++;
            }
        });
        YardShellDress.underGuard(b);
        // A click toggles the button and tells its listeners, as Enter does through the menus' focus
        b.toggle();
        assertEquals(0, orders[0], "the first press only lifts the guard");
        assertTrue(YardShellDress.lifted(b));
        assertTrue(b.isChecked(), "the checked state draws the guard up");
        b.toggle();
        assertEquals(1, orders[0], "the second gives the order");
        assertFalse(YardShellDress.lifted(b));
        assertFalse(b.isChecked());
        // Left up too long, the guard drops back and the next press only lifts it again
        b.toggle();
        assertTrue(YardShellDress.lifted(b));
        b.act(YardShellDress.GUARD_UP + 0.1f);
        assertFalse(YardShellDress.lifted(b));
        assertFalse(b.isChecked());
        b.toggle();
        assertEquals(1, orders[0]);
        assertTrue(YardShellDress.lifted(b));
    }

    @Test
    void aCellMovedToAnotherTableKeepsItsPlacing() {
        Table from = new Table();
        Actor a = new Actor(), next = new Actor();
        from.add(a).padLeft(5f).padTop(3f).spaceBottom(7f).colspan(2).expandX().fillX().left().minWidth(100f).height(30f);
        from.row();
        from.add(next);
        YardShellDress.Placed placed = new YardShellDress.Placed(from.getCells().first());
        Table to = new Table();
        placed.into(to);
        to.add(new Actor());
        Cell<?> c = to.getCells().first();
        assertEquals(a, c.getActor());
        assertEquals(5f, c.getPadLeft());
        assertEquals(3f, c.getPadTop());
        assertEquals(7f, c.getSpaceBottom());
        assertEquals(2, c.getColspan());
        assertEquals(1, c.getExpandX());
        assertEquals(1f, c.getFillX());
        assertEquals(Align.left, c.getAlign() & Align.left);
        assertEquals(100f, c.getMinWidth());
        assertEquals(30f, c.getPrefHeight());
        assertEquals(1, to.getCells().get(1).getRow(), "it ended its row, so the next cell starts a new one");
    }
}
