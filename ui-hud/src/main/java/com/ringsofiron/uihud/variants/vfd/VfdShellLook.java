package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.UiScale;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.ShellScreen;

/**
 * The game's menus under the FILAMENT HUDs (style-vfd.md, section 18.5), the same under all three:
 * before a game starts nothing aboard is live but the computer, in setup mode. The racks are dark, every
 * annunciator and the clock a ghost, and the top rack shows SETUP in fourteen segments; the window
 * between the racks shows the stars and the lit limb of the planet below; the bottom rack's window
 * carries the legend of keys. Over the paused game the world goes under a dark scrim and every tube on
 * the dimmer bus, so the menu is the only thing at full drive.
 *
 * <p>Each menu keeps the classic one's rows, buttons and order: it is built from {@link SetupSkin},
 * whose styles draw nothing, and painted over through the kit by {@link SetupPaint}. If the painter
 * ever fails, the next backdrop says so, and the game goes back to its classic menus.
 */
public final class VfdShellLook implements ShellLook {
    private final SetupSkin skin;
    private final TextureRegion white;
    /** Made on the first frame drawn: it holds GL resources. */
    private Filament filament;
    private float uiScale = -1f;
    /** Whether the last backdrop drawn was the paused game's. */
    private boolean overGame;
    private RuntimeException failure;
    private boolean disposed;
    private final float[] stars = new float[3 * 300];
    private float drift;
    private final Filament.Painter cockpit = this::cockpit;
    private float frameW, frameH;
    /** The menu last painted on the shell's own stage, y down: a tall one comes up over the top rack's windows. */
    private float menuX, menuY, menuW, menuH;

    /** Needs GL: the skin's fonts are the ROM on a texture. */
    public VfdShellLook() {
        skin = new SetupSkin();
        white = new TextureRegion(skin.white());
        RandomXS128 random = new RandomXS128(5);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = random.nextFloat();
            stars[i + 1] = random.nextFloat();
            stars[i + 2] = random.nextFloat();
        }
    }

    @Override
    public Skin skin() {
        return skin.skin();
    }

    @Override
    public boolean rescale(float uiScale) {
        this.uiScale = uiScale;
        // The ROM's cells are in UI units at every scale, and the kit draws at the screen's own
        return false;
    }

    /** Whether the menus are over the paused game rather than in setup mode. */
    boolean overGame() {
        return overGame;
    }

    @Override
    public boolean backdrop(Batch batch, float width, float height, float delta) {
        failed();
        overGame = false;
        drift += delta * 0.004f;
        // The window between the racks: the stars, and the lit limb of the planet below
        for (int i = 0; i < stars.length; i += 3) {
            float b = stars[i + 2], x = ((stars[i] + drift * b) % 1f) * width, s = b < 0.8f ? 1f : 2f;
            batch.setColor(0.7f + 0.3f * b, 0.8f + 0.2f * b, 1f, b);
            batch.draw(white, x, stars[i + 1] * height, s, s);
        }
        float r = width * 0.9f, cx = width * 0.5f, cy = -r * 0.82f;
        for (int k = 0; k < 6; k++) {
            batch.setColor(0.25f, 0.5f, 0.9f, 0.10f - k * 0.015f);
            ring(batch, cx, cy, r + k * 3f, width);
        }
        batch.setColor(Color.WHITE);
        render(batch, width, height, unitsPx(width), cockpit);
        return true;
    }

    @Override
    public boolean pauseBackdrop(Batch batch, float width, float height, float delta) {
        failed();
        overGame = true;
        batch.setColor(0f, 0f, 0f, 0.58f);
        batch.draw(white, 0f, 0f, width, height);
        batch.setColor(Color.WHITE);
        return true;
    }

    @Override
    public void dress(ShellScreen screen, Table panel, Table root) {
        SetupPaint paint = new SetupPaint(this, skin, screen, panel);
        // A button is a field of cells: room for the cursor's column and a cell after its word
        for (TextButton b : all(panel, TextButton.class)) {
            if (!(b instanceof CheckBox)) b.pad(2f, 14f, 2f, 14f);
        }
        // A display does not glide or slide sideways: a list steps to where it goes, as wide as its
        // window, and a row too long for it is cut short
        for (ScrollPane s : all(panel, ScrollPane.class)) {
            s.setSmoothScrolling(false);
            s.setScrollingDisabled(true, false);
            if (!(s.getActor() instanceof Group)) continue;
            Group rows = (Group) s.getActor();
            // A row's buttons are their cursor and their word, so its text keeps the room; one with
            // more words than room shows what fits (SetupPaint scrolls it while it has the focus)
            for (TextButton b : all(rows, TextButton.class)) {
                if (b instanceof CheckBox) continue;
                b.pad(2f, 8f, 2f, 8f);
                b.getLabelCell().minWidth(0f);
            }
            for (Label l : all(rows, Label.class)) {
                if (!(l.getParent() instanceof TextButton)) cutShort(l);
            }
        }
        Screen layer = new Screen(paint);
        if (root != null) {
            // The build's version sits at the right of the rack's legend
            if (screen == ShellScreen.MAIN) {
                for (Actor a : root.getChildren()) if (a instanceof Label && a != panel) paint.inRack((Label) a);
            }
            root.addActor(layer);
        } else {
            // The dialog floats over the game: painted over it once it is shown on its stage, its
            // words a line clear of the strip its title is on (SetupPaint#titleStrip)
            if (panel instanceof Window) panel.padTop(44f);
            panel.addActor(new Lift(layer));
        }
    }

    /** Where the menu painted on the shell's own stage is, y down, so the racks know what it covers. */
    void menuAt(float x, float y, float w, float h) {
        menuX = x;
        menuY = y;
        menuW = w;
        menuH = h;
    }

    /** The cockpit in setup mode, round the menus: dark racks, and the computer's one live tube. */
    private void cockpit(Filament f) {
        float w = frameW, h = frameH;
        f.rack(-8f, -8f, w + 16f, 40f, f.o().radius(6f).shadowY(2f));
        f.rack(-8f, h - 46f, w + 16f, 54f, f.o().radius(6f));
        // The top rack's windows, unpowered: every ghost shows what each would hold. A menu too tall
        // for the window between the racks comes up over the top one, in front of the windows it covers
        f.dark = true;
        if (!covered(16f, 6f, 300f, 20f)) {
            f.module(16f, 6f, 300f, 20f, f.o().filaments(1));
            String[] lamps = {"WARN", "CAUT", "FIRE", "HULL", "AIR", "POWER"};
            for (int i = 0; i < lamps.length; i++) f.legend(26f + i * 48f, 21f, lamps[i], f.o().size(12f));
        }
        if (!covered(w - 316f, 6f, 300f, 20f)) {
            f.module(w - 316f, 6f, 300f, 20f, f.o().filaments(1));
            f.seg7(w - 302f, 9f, 14f, "88:88:88", f.o().cells(6));
            f.seg7(w - 160f, 9f, 14f, "888.8", f.o().cells(4));
        }
        f.dark = false;
        // The computer's own window: SETUP
        if (!covered(w / 2f - 80f, 6f, 160f, 20f)) {
            f.module(w / 2f - 80f, 6f, 160f, 20f, f.o().filaments(1));
            float sw = f.seg14Width(12f, 5, f.o());
            f.seg14(w / 2f - sw / 2f, 10f, 12f, "SETUP", f.o().norm());
        }
    }

    /** Whether the menu, with its plate, comes over the window at {@code x, y} (y down). */
    private boolean covered(float x, float y, float w, float h) {
        float m = 8f;
        return menuW > 0f && x < menuX + menuW + m && x + w > menuX - m && y < menuY + menuH + m && y + h > menuY - m;
    }

    /** Paints {@code paint}'s menu over its widgets, on the stage it is on. */
    void paint(Batch batch, SetupPaint paint) {
        if (failure != null || disposed) return;
        Stage stage = paint.stage();
        if (stage == null) return;
        float w = stage.getWidth(), h = stage.getHeight();
        render(batch, w, h, Math.max(0.25f, stage.getViewport().getScreenWidth() / w), paint);
    }

    private void render(Batch batch, float width, float height, float unitsPx, Filament.Painter painter) {
        if (failure != null || disposed) return;
        try {
            if (filament == null) filament = new Filament();
            frameW = width;
            frameH = height;
            filament.render(batch, width, height, unitsPx, painter);
            batch.setColor(Color.WHITE);
        } catch (RuntimeException e) {
            // The menus stay usable: their own text shows, and the next backdrop hands them back to the game's look
            failure = e;
            skin.reveal();
            FrameBuffer.unbind();
            if (!batch.isDrawing()) batch.begin();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(Color.WHITE);
            if (Gdx.app != null) Gdx.app.error("RingsOfIron", "The FILAMENT menus failed to draw", e);
        }
    }

    /** A painter that failed throws here, on the game's side of a backdrop, so the game takes the menus back. */
    private void failed() {
        if (failure != null) throw new IllegalStateException("the FILAMENT menus failed to draw", failure);
    }

    private float unitsPx(float width) {
        if (uiScale > 0f) return uiScale;
        if (Gdx.graphics == null) return 1f;
        return Math.max(0.25f, UiScale.effective(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
    }

    /** The top of a circle as short segments, only what is over the screen. */
    private void ring(Batch batch, float cx, float cy, float r, float width) {
        int n = 180;
        float seg = MathUtils.PI2 * r / n + 1f;
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float x = cx + MathUtils.cos(a) * r, y = cy + MathUtils.sin(a) * r;
            if (y < 0f || x < -seg || x > width + seg) continue;
            batch.draw(white, x, y, 0f, 0f, seg, 3f, 1f, 1f, a * MathUtils.radiansToDegrees + 90f);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        if (filament != null) filament.dispose();
        filament = null;
        skin.dispose();
    }

    /** Lets a list's row give up room it has not got: its text is cut, with dots, rather than the list sliding sideways. */
    private static void cutShort(Label l) {
        if (l.getWrap()) return;
        l.setEllipsis(true);
        if (l.getParent() instanceof Table) {
            Cell<?> c = ((Table) l.getParent()).getCell(l);
            if (c != null) c.minWidth(0f);
        }
    }

    /** Every {@code type} under {@code g}. */
    static <T extends Actor> Array<T> all(Group g, Class<T> type) {
        Array<T> out = new Array<>();
        collect(g, type, out);
        return out;
    }

    private static <T extends Actor> void collect(Group g, Class<T> type, Array<T> out) {
        for (Actor a : g.getChildren()) {
            if (type.isInstance(a)) out.add(type.cast(a));
            if (a instanceof Group) collect((Group) a, type, out);
        }
    }

    /** Over a menu, the layer its painter draws on: takes no clicks, draws the whole stage. */
    final class Screen extends Actor {
        final SetupPaint paint;

        Screen(SetupPaint paint) {
            this.paint = paint;
            setTouchable(Touchable.disabled);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            paint.time += delta;
            // The dialog it was painting has gone
            if (paint.stage() == null && getStage() != null && getParent() == getStage().getRoot()) remove();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            paint(batch, paint);
        }
    }

    /** Rides in the dialog until it is shown, then puts its painter's layer on the stage over it. */
    static final class Lift extends Actor {
        private final Screen layer;

        Lift(Screen layer) {
            this.layer = layer;
            setTouchable(Touchable.disabled);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            Stage stage = getStage();
            if (stage != null && layer.getStage() == null) stage.addActor(layer);
        }
    }
}
