package com.ringsofiron.uihud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Arrow keys and Enter moving between a window's buttons. */
class FocusNavTest {

    @org.junit.jupiter.api.BeforeAll
    static void files() {
        // Scene2D's table cells look for Gdx.files once; any will do here
        if (com.badlogic.gdx.Gdx.files == null) {
            com.badlogic.gdx.Gdx.files = (com.badlogic.gdx.Files) java.lang.reflect.Proxy.newProxyInstance(
                    FocusNavTest.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Files.class}, (o, m, a) -> null);
        }
    }

    private static Button button(int[] pressed, int id) {
        Button b = new Button(new Button.ButtonStyle());
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                pressed[0] = id;
            }
        });
        return b;
    }

    @Test
    void downMovesThroughTheButtonsInOrderAndEnterPressesOne() {
        int[] pressed = {0};
        Group root = new Group();
        Group inner = new Group();
        Button a = button(pressed, 1), b = button(pressed, 2), c = button(pressed, 3);
        root.addActor(a);
        inner.addActor(b);
        root.addActor(inner);
        Button off = button(pressed, 9);
        off.setDisabled(true);
        root.addActor(off);
        root.addActor(c);
        FocusNav nav = new FocusNav();
        assertTrue(nav.key(root, Input.Keys.DOWN));
        assertSame(a, nav.focused());
        nav.key(root, Input.Keys.DOWN);
        assertSame(b, nav.focused(), "into nested tables");
        nav.key(root, Input.Keys.DOWN);
        assertSame(c, nav.focused(), "past a disabled button");
        nav.key(root, Input.Keys.DOWN);
        assertSame(a, nav.focused(), "round again");
        nav.key(root, Input.Keys.UP);
        assertSame(c, nav.focused());
        nav.key(root, Input.Keys.ENTER);
        assertEquals(3, pressed[0]);
        assertFalse(nav.key(root, Input.Keys.W), "other keys are left alone");
    }

    @Test
    void aRebuiltWindowKeepsThePlace() {
        int[] pressed = {0};
        Group root = new Group();
        root.addActor(button(pressed, 1));
        root.addActor(button(pressed, 2));
        FocusNav nav = new FocusNav();
        nav.key(root, Input.Keys.DOWN);
        nav.key(root, Input.Keys.DOWN);
        root.clearChildren();
        root.addActor(button(pressed, 3));
        root.addActor(button(pressed, 4));
        nav.key(root, Input.Keys.ENTER);
        assertEquals(4, pressed[0], "the second row still");
    }

    @Test
    void enterPicksAChoiceInAGroup() {
        int[] pressed = {0};
        Group root = new Group();
        Button first = button(pressed, 1), second = button(pressed, 2);
        com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup<Button> group = new com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup<>(first, second);
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        first.setChecked(true);
        root.addActor(first);
        root.addActor(second);
        FocusNav nav = new FocusNav();
        nav.key(root, Input.Keys.DOWN);
        nav.key(root, Input.Keys.DOWN);
        nav.key(root, Input.Keys.ENTER);
        assertTrue(second.isChecked(), "the focused choice is picked");
        assertFalse(first.isChecked(), "and the other let go");
        assertEquals(2, pressed[0]);
    }
}
