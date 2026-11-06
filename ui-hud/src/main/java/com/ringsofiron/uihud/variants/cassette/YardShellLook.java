package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.ringsofiron.uihud.UiScale;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.ShellScreen;

/**
 * The game's menus in the Yard Standard (style guide, section 9; documentation/ui-overhaul/cassette/shell),
 * the same under each of the three Cassette HUDs: the shell stands outside the fiction, so it is no
 * one's ship but a yard office at night, its console under a big window. The menus are built from
 * {@link YardSkin}, dressed by {@link YardShellDress}, in front of {@link YardOffice}. Pause is the one
 * exception: it opens in the ship's own console, over the game.
 */
public final class YardShellLook implements ShellLook {
    private final YardPaint paint;
    private final YardSkin skin;
    private final YardShellDress dresser;
    private final YardOffice office = new YardOffice();
    private float time;

    /** At the UI scale the screen has now; needs GL. */
    public YardShellLook() {
        this(Gdx.graphics == null ? 1f : UiScale.effective(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
    }

    public YardShellLook(float scale) {
        paint = new YardPaint();
        paint.fonts.setScale(scale);
        skin = new YardSkin(paint, scale);
        dresser = new YardShellDress(skin.skin(), paint);
    }

    @Override
    public Skin skin() {
        return skin.skin();
    }

    @Override
    public boolean rescale(float uiScale) {
        paint.fonts.setScale(uiScale);
        return skin.rescale(uiScale);
    }

    @Override
    public boolean backdrop(Batch batch, float width, float height, float delta) {
        time += delta;
        paint.begin(batch, height, 1f, time);
        office.night(paint, width, height, time);
        batch.setColor(1f, 1f, 1f, 1f);
        return true;
    }

    @Override
    public boolean pauseBackdrop(Batch batch, float width, float height, float delta) {
        time += delta;
        paint.begin(batch, height, 1f, time);
        office.paused(paint, width, height);
        batch.setColor(1f, 1f, 1f, 1f);
        return true;
    }

    @Override
    public void dress(ShellScreen screen, Table panel, Table root) {
        dresser.dress(screen, panel, root);
    }

    @Override
    public void dispose() {
        skin.dispose();
        paint.dispose();
    }
}
