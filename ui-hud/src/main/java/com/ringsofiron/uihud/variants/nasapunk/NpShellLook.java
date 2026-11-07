package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.UiScale;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.ShellScreen;

/**
 * The game's menus under the Nasa-punk HUDs, the same under all three
 * (documentation/ui-overhaul/variations/nasapunk-shell): every menu is a page of one flight
 * computer, CDU-1. Out of a game it is set in a console with the game's name engraved on it, over
 * the stars and the Earth's limb; in a game it stands alone in front of the deck, which keeps 40 per
 * cent of its light.
 *
 * <p>The game builds each menu as ever ({@code ShellMenus}), with {@link NpShellSkin}'s styles; once
 * built, {@link #dress} lays the page out as the computer's: the page title at the top of the glass,
 * the prompts down its left edge each with a line-select key on the bezel beside it, the dim lines
 * under them as label lines, a part's name between divider rules, and the game's message line as
 * the scratchpad along the glass's foot. What the menus hold and do, and their keys, stay the game's.
 */
public final class NpShellLook implements ShellLook {
    /** The width of the unit on the pause page, and of the console on the main menu (shell.js). */
    static final float UNIT_W = 468f, CONSOLE_W = 496f;
    /** The mockups' screen, which the backdrop's planet is laid out on (shell.js {@code backdrop}). */
    private static final float MOCK_W = 1280f, MOCK_H = 800f;
    private static final float PLANET_R = 1258f, PLANET_TOP = 595f;
    private static final Color SPACE = NpColors.rgb(0x010204), PLANET = NpColors.rgb(0x07101A), LIMB = NpColors.rgb(0x1B3550),
            STAR = NpColors.rgb(0xDCE4F0);

    /** The room a page keeps above and below it in the window. */
    private static final float WINDOW_MARGIN = 12f;

    private final NpKit kit = new NpKit();
    private final NpShellSkin skin;
    /** The backdrop's stars: x and y as shares of the screen, then the radius and alpha of each. */
    private final float[] stars = new float[4 * 240];

    /** Needs GL: the skin's fonts are rasterised now, at the window's UI scale. */
    public NpShellLook() {
        float scale = Gdx.graphics == null ? 1f : UiScale.effective(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        kit.setScale(scale);
        skin = new NpShellSkin(kit, scale);
        // The mockups' own stars (lib.js stars(), seed 11), so the backdrop is the one drawn
        long r = 11;
        for (int i = 0; i < stars.length; i += 4) {
            r = (r * 16807L) % 2147483647L;
            float a = 0.25f + r / 2147483647f * 0.6f;
            r = (r * 16807L) % 2147483647L;
            stars[i] = r / 2147483647f;
            r = (r * 16807L) % 2147483647L;
            stars[i + 1] = r / 2147483647f;
            r = (r * 16807L) % 2147483647L;
            stars[i + 2] = 0.4f + r / 2147483647f * 0.8f;
            stars[i + 3] = a;
        }
    }

    @Override
    public Skin skin() {
        return skin.skin();
    }

    @Override
    public boolean rescale(float uiScale) {
        kit.setScale(uiScale);
        return skin.rescale(uiScale);
    }

    @Override
    public boolean backdrop(Batch batch, float width, float height, float delta) {
        NpCanvas cv = kit.canvas().begin(batch, 0f, height, 1f);
        cv.fill(0f, 0f, width, height, SPACE);
        // The Earth's limb along the foot of the screen, as wide as the mockups' at any shape
        float k = width / MOCK_W, r = PLANET_R * k, top = height - (MOCK_H - PLANET_TOP) * k;
        cv.disc(width / 2f, top + r, 2f * r, PLANET);
        cv.ring(width / 2f, top + r, r, 2f, LIMB, 0.7f);
        for (int i = 0; i < stars.length; i += 4) {
            cv.disc(stars[i] * width, stars[i + 1] * height, 2f * stars[i + 2], STAR, stars[i + 3]);
        }
        cv.end();
        return true;
    }

    @Override
    public boolean pauseBackdrop(Batch batch, float width, float height, float delta) {
        // The deck keeps 40 per cent of its light (screens.js dimmed())
        NpCanvas cv = kit.canvas().begin(batch, 0f, height, 1f);
        cv.fill(0f, 0f, width, height, NpColors.INK, 0.6f);
        cv.end();
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes") // Table.getCells() is a raw Array<Cell>
    public void dress(ShellScreen screen, Table panel, Table root) {
        Skin s = skin.skin();
        if (screen == ShellScreen.MOD_SCRIPTS && panel instanceof Window) {
            dressDialog((Window) panel);
            return;
        }
        boolean main = screen == ShellScreen.MAIN, column = main || screen == ShellScreen.PAUSE;
        restyle(panel, false);
        Array<Cell> cells = panel.getCells();
        Label name = null, subtitle = null;
        float m = main ? NpCdu.CONSOLE_MARGIN : 0f;
        // On the main menu the game's name sits at the console's top as the page title sits at the glass's
        panel.pad(NpCdu.PAD_TOP, m + NpCdu.PAD_SIDE, m + NpCdu.PAD_BOTTOM, m + NpCdu.PAD_SIDE);
        if (main) {
            // The console carries the game's name and its subtitle; the unit sits under them
            panel.setBackground(s.getDrawable("np-console"));
            if (cells.size > 1 && cells.get(0).getActor() instanceof Label && cells.get(1).getActor() instanceof Label) {
                name = (Label) cells.get(0).getActor();
                subtitle = (Label) cells.get(1).getActor();
                subtitle.setStyle(s.get(NpShellSkin.SUBTITLE, Label.LabelStyle.class));
                // Room for the unit's top bezel and its MENU title between the subtitle and the first prompt
                cells.get(1).padBottom(56f);
            }
            takeBuild(root, panel);
        }
        if (column) {
            Cell<Table> at = root.getCell(panel);
            if (at != null) at.minWidth(main ? CONSOLE_W : UNIT_W);
        }
        Array<TextButton> keyed = new Array<>();
        Array<Label> parts = new Array<>();
        Label pad = null;
        Label.LabelStyle title = s.get("title", Label.LabelStyle.class), dim = s.get("dim", Label.LabelStyle.class),
                warn = s.get("warn", Label.LabelStyle.class);
        float indent = 5f + kit.canvas().width("<", NpCdu.DATA, true, NpCdu.DATA_TRACK);
        for (int i = 0; i < cells.size; i++) {
            Cell<?> c = cells.get(i);
            Actor a = c.getActor();
            if (column) c.expandX();
            if (i == cells.size - 1 && a instanceof Label && ((Label) a).getStyle() == warn) {
                // The game's message line is the scratchpad along the glass's foot
                pad = (Label) a;
                pad.setWrap(true);
                pad.setAlignment(Align.left);
                c.fillX().left().padLeft(5f).padRight(5f).minHeight(lineHeight(warn));
                continue;
            }
            if (a instanceof Label && ((Label) a).getStyle() == title) {
                ((Label) a).setAlignment(Align.center);
                c.fillX();
                parts.add((Label) a);
                continue;
            }
            if (!column || a == name || a == subtitle) continue;
            if (a instanceof TextButton && !(a instanceof CheckBox)) {
                // A prompt down the page's left edge, with its key on the bezel beside it
                c.width(Value.prefWidth).left();
                keyed.add((TextButton) a);
            } else if (a instanceof Label && ((Label) a).getStyle() == dim) {
                // A label line, its words under the prompt's
                ((Label) a).setWrap(true);
                ((Label) a).setAlignment(Align.left);
                c.fillX().left().padLeft(indent);
            }
        }
        String hint = main ? "ARROWS OR THE PAD CHOOSE · ENTER SELECTS"
                : screen == ShellScreen.PAUSE ? null : "ESC RETURNS · ARROWS OR THE PAD CHOOSE · ENTER SELECTS";
        if (pad != null && hint != null) {
            // The page is wide enough for its hint
            Cell<?> c = panel.getCell(pad);
            float hw = kit.canvas().width(hint, NpCdu.LABEL, true, NpCdu.LABEL_TRACK) + 10f;
            if (c != null) c.minWidth(hw);
        }
        if (root != null && root.getStage() != null && panel.getPrefHeight() + 2f * WINDOW_MARGIN > root.getStage().getHeight()) {
            closeUp(panel);
        }
        NpShellFrame frame = new NpShellFrame(kit.canvas(), skin, panel, name, subtitle, keyed, parts, pad, hint);
        panel.addActorAt(0, frame);
    }

    /**
     * Brings a page's rows closer together, for a page too tall for the window as it is (the settings
     * in an 800-line window): the gaps between rows go to 4, the one under the page title and the one
     * over the scratchpad to 8.
     */
    @SuppressWarnings("rawtypes") // Table.getCells() is a raw Array<Cell>
    private static void closeUp(Table panel) {
        Array<Cell> cells = panel.getCells();
        for (int i = 0; i < cells.size; i++) {
            Cell<?> c = cells.get(i);
            float top = i == cells.size - 1 ? 8f : 2f, bottom = i == 0 ? 8f : 2f;
            c.padTop(Math.min(c.getPadTop(), top)).padBottom(Math.min(c.getPadBottom(), bottom));
        }
        panel.invalidateHierarchy();
    }

    /** The mod scripts question as a page of its own: its title at the top of the glass, its buttons prompts. */
    private void dressDialog(Window dialog) {
        restyle(dialog, false);
        // The title band: the bezel, then the title on the glass at the page title's place
        dialog.pad(55f, NpCdu.PAD_SIDE, NpCdu.PAD_BOTTOM, NpCdu.PAD_SIDE);
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.addActorAt(0, new NpShellFrame(kit.canvas(), skin, dialog, null, null, new Array<>(), new Array<>(), null, null));
    }

    /**
     * Gives each button the room its style's own drawable leaves, in place of the classic padding the
     * game set, with its word from the left after the caret; a button in a list becomes a field.
     */
    private void restyle(Group g, boolean inList) {
        Skin s = skin.skin();
        TextButton.TextButtonStyle toggle = s.get("toggle", TextButton.TextButtonStyle.class);
        for (Actor a : g.getChildren()) {
            if (a instanceof CheckBox) continue;
            if (a instanceof TextButton) {
                TextButton b = (TextButton) a;
                if (inList && b.getStyle() != toggle) b.setStyle(s.get(NpShellSkin.FIELD, TextButton.TextButtonStyle.class));
                b.pad(Table.backgroundTop, Table.backgroundLeft, Table.backgroundBottom, Table.backgroundRight);
                if (b.getStyle() != toggle) b.getLabel().setAlignment(Align.left);
                continue;
            }
            if (a instanceof Group) restyle((Group) a, inList || a instanceof ScrollPane);
        }
    }

    /** Takes the build off the main menu's corner and puts it on the unit's plate, as its part number. */
    private void takeBuild(Table root, Table panel) {
        if (root == null) return;
        for (Actor a : root.getChildren()) {
            if (a == panel || !(a instanceof Label)) continue;
            skin.setBuild(plate(((Label) a).getText().toString()));
            a.setVisible(false);
        }
    }

    /** "v1.0.0 (a1b2c3d)" as the plate prints it: "v1.0.0 · a1b2c3d". */
    static String plate(String build) {
        String b = build == null ? "" : build.trim();
        int open = b.lastIndexOf(" ("), close = b.lastIndexOf(')');
        if (open > 0 && close == b.length() - 1) return b.substring(0, open) + " · " + b.substring(open + 2, close);
        return b;
    }

    /** One line of a label style's text, as Scene2D sizes it. */
    private static float lineHeight(Label.LabelStyle s) {
        return s.font.getCapHeight() - 2f * s.font.getDescent();
    }

    @Override
    public void dispose() {
        skin.dispose();
        kit.dispose();
    }
}
