package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

/**
 * What the look lays into a menu once the game has built it, under the game's own rows: CDU-1's
 * furniture (documentation/ui-overhaul/variations/nasapunk-shell). On the main menu the unit itself,
 * set in the console under the game's name, with its MENU page title; on a column of prompts the
 * line-select keys either side of the glass, level with each prompt, the left one pressing the prompt
 * when clicked; a divider either side of each part's name; the scratchpad's rule along the glass's
 * foot, with a dim hint on the line while there is no message.
 *
 * <p>It fills the menu's table, behind its rows. It also takes the game's focus tint off the words
 * of the control the keyboard has reached ({@code FocusNav} tints a button's text as well as the
 * button): the control shows the computer's cyan cursor instead, and its word stays white.
 */
final class NpShellFrame extends Actor {
    private final NpCanvas cv;
    private final NpShellSkin skin;
    private final Table panel;
    /** On the main menu: the game's name and its subtitle, which the unit sits under; else null. */
    private final Label name, subtitle;
    /** The prompts down the page's edge, each with its key. */
    private final Array<TextButton> keyed;
    /** The parts' names, each between its divider's rules. */
    private final Array<Label> parts;
    /** The scratchpad's line, or null; and what it says while it is empty. */
    private final Label pad;
    private final String hint;
    /** The key held down under the pointer, or -1. */
    private int held = -1;

    NpShellFrame(NpCanvas cv, NpShellSkin skin, Table panel, Label name, Label subtitle, Array<TextButton> keyed,
                 Array<Label> parts, Label pad, String hint) {
        this.cv = cv;
        this.skin = skin;
        this.panel = panel;
        this.name = name;
        this.subtitle = subtitle;
        this.keyed = keyed;
        this.parts = parts;
        this.pad = pad;
        this.hint = hint;
        setTouchable(keyed.size > 0 ? Touchable.enabled : Touchable.disabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                held = keyAt(x, y);
                return held >= 0;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                int at = keyAt(x, y);
                if (at >= 0 && at == held) press(keyed.get(at));
                held = -1;
            }
        });
    }

    /** Presses a prompt as its key would: as the menus' own Enter does ({@code FocusNav}). */
    private static void press(TextButton b) {
        if (b.isDisabled() || !b.isVisible()) return;
        b.fire(new ChangeListener.ChangeEvent());
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Fill the menu's table, under its rows
        setBounds(0f, 0f, panel.getWidth(), panel.getHeight());
        untint(panel);
    }

    /** The focused control's words back to white: the cursor shows where the focus is. */
    private static void untint(Group g) {
        for (Actor a : g.getChildren()) {
            if (a instanceof Label && NpDrawable.isFocus(a.getColor())) a.setColor(Color.WHITE);
            else if (a instanceof Group) untint((Group) a);
        }
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() != Touchable.enabled) return null;
        return keyAt(x, y) >= 0 ? this : null;
    }

    /** Which prompt's key is under ({@code x}, {@code y}), the table's own units, y up; -1 for none. */
    private int keyAt(float x, float y) {
        float kx = unitX() + NpCdu.LSK_X;
        if (x < kx || x > kx + NpCdu.LSK_W) return -1;
        for (int i = 0; i < keyed.size; i++) {
            TextButton b = keyed.get(i);
            float cy = relY(b) + b.getHeight() / 2f;
            if (b.isVisible() && Math.abs(y - cy) <= NpCdu.LSK_H / 2f) return i;
        }
        return -1;
    }

    /** The unit's left edge in the table: in from the console's on the main menu. */
    private float unitX() {
        return name != null ? NpCdu.CONSOLE_MARGIN : 0f;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float w = panel.getWidth(), h = panel.getHeight();
        if (w <= 0f || h <= 0f) return;
        cv.begin(batch, getX(), getY() + h, parentAlpha * getColor().a);
        float ux = unitX(), uw = w - 2f * ux;
        if (name != null && subtitle != null) {
            // The console: the rule between the game's name and its subtitle, then the unit under them
            float nameBottom = h - relY(name), subTop = h - relY(subtitle) - subtitle.getHeight();
            float rule = subtitle.getGlyphLayout().width + 2f;
            cv.fill(w / 2f - rule / 2f, (nameBottom + subTop) / 2f, rule, 1f, NpCdu.RULE);
            float top = h - relY(subtitle) + 14f, bottom = h - NpCdu.CONSOLE_MARGIN;
            NpCdu.unit(cv, ux, top, uw, bottom - top, skin.plate());
            cv.text(w / 2f, top + NpCdu.BEZEL + 21f, "MENU", NpCdu.TITLE, true, NpColors.WHITE, NpCdu.TITLE_TRACK, NpCanvas.MIDDLE);
        }
        // The line-select keys either side of the glass, level with each prompt's word: the
        // computer's keys are there on every row, whether or not the page puts a field beside them
        for (int i = 0; i < keyed.size; i++) {
            TextButton b = keyed.get(i);
            if (!b.isVisible()) continue;
            float ky = h - relY(b) - b.getHeight() / 2f - NpCdu.LSK_H / 2f;
            NpCdu.lsk(cv, ux + NpCdu.LSK_X, ky, held == i);
            NpCdu.lsk(cv, ux + uw - NpCdu.LSK_X - NpCdu.LSK_W, ky, false);
        }
        float gx0 = ux + NpCdu.GLASS_X, gx1 = ux + uw - NpCdu.GLASS_X;
        // Each part's divider, through the middle of its name's capitals
        for (Label l : parts) {
            float tw = l.getGlyphLayout().width, lx = relX(l) + (l.getWidth() - tw) / 2f;
            float y = h - relY(l) - l.getHeight() / 2f;
            NpCdu.divider(cv, gx0 + 10f, gx1 - 10f, lx, lx + tw, y);
        }
        if (pad != null) {
            // The scratchpad: a rule over the glass's foot line (17 over a one-line message's
            // baseline, as screens.js draws it), and the hint on the line while there is no message
            float top = h - relY(pad) - pad.getHeight();
            cv.fill(gx0 + 6f, top - 6f, gx1 - gx0 - 12f, 1f, NpColors.GRATICULE);
            if (pad.getText().length() == 0 && hint != null) {
                float base = top + pad.getHeight() / 2f + cv.capHeight(NpCdu.LABEL, true) / 2f;
                cv.text(gx0 + 10f, base, hint, NpCdu.LABEL, true, NpColors.DIM, NpCdu.LABEL_TRACK, NpCanvas.START);
            }
        }
        cv.end();
    }

    /** Where {@code a} sits in the menu's table: its x, every parent's added, the table's own units. */
    private float relX(Actor a) {
        float x = 0f;
        for (Actor c = a; c != null && c != panel; c = c.getParent()) x += c.getX();
        return x;
    }

    private float relY(Actor a) {
        float y = 0f;
        for (Actor c = a; c != null && c != panel; c = c.getParent()) y += c.getY();
        return y;
    }
}
