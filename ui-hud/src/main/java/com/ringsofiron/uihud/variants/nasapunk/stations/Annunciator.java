package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The annunciator (section 7.1), one row 720 x 36: the master lamps at its ends, MASTER WARN left
 * and MASTER CAUT right, each lit only while it flashes for an alert of its kind not yet
 * acknowledged, and between them six two-line lens tiles of 94, filled from the left. The lamps are
 * the row's fixed hardware: both come up with it, so an unlit lamp is one waiting, not one gone.
 * A click on a tile shows its home page; a click on a lamp acknowledges.
 */
final class Annunciator extends NpInstrument {
    static final float W = 720f, H = 36f;
    private static final float LAMP = 56f, TILE = 94f, GAP = 6f;

    final List<Annunciation.Tile> tiles = new ArrayList<>();
    /** Each lamp lit this frame: flashing is the HUD's to time. */
    boolean warnLit, cautLit;
    Consumer<Annunciation.Tile> onTile;
    Runnable onAcknowledge;

    Annunciator(NpKit kit) {
        super(kit);
        setSize(W, H);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (x < LAMP || x > W - LAMP) {
                    if (onAcknowledge != null) onAcknowledge.run();
                    return true;
                }
                int i = (int) ((x - LAMP - GAP) / (TILE + GAP));
                if (i < 0 || i >= tiles.size() || x - LAMP - GAP - i * (TILE + GAP) > TILE) return false;
                if (onTile != null) onTile.accept(tiles.get(i));
                return true;
            }
        });
    }

    @Override
    protected void paint(NpCanvas cv) {
        float x = LAMP + GAP;
        for (int i = 0; i < tiles.size() && i < Annunciation.ROW; i++) {
            Annunciation.Tile t = tiles.get(i);
            Color fill = t.level == Level.WARNING ? NpColors.RED : NpColors.AMBER;
            cv.fillRound(x - 1.5f, -1.5f, TILE + 3f, H + 3f, 2.5f, StDraw.RIM, 1f);
            cv.fillRound(x, 0f, TILE, H, 1.5f, fill, 1f);
            cv.fillRound(x, 0f, TILE, 13f, 1.5f, NpColors.WHITE, 0.14f);
            cv.text(x + TILE / 2f, 15.5f, t.tag, 12f, true, NpColors.INK, 0.8f, NpCanvas.MIDDLE);
            cv.text(x + TILE / 2f, 30f, t.value, 12f, true, NpColors.INK, 0.5f, NpCanvas.MIDDLE);
            x += TILE + GAP;
        }
        NpHardware.lens(cv, 0f, 0f, LAMP, H, "MASTER\nWARN", warnLit ? NpColors.RED : null, false, 12f);
        NpHardware.lens(cv, W - LAMP, 0f, LAMP, H, "MASTER\nCAUT", cautLit ? NpColors.AMBER : null, false, 12f);
    }
}
