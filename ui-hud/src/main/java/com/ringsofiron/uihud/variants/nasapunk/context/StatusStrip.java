package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.data.AlertRegistry.Level;

/**
 * P1, the status strip (section 6): one long glass across the top. Each field has its place and
 * its width reserved (section 5.3), so a field that empties leaves dark glass and nothing beside it
 * moves: the seat, who flies, BATTLE STATIONS as an amber lens, the caution and warning chip while
 * a full-screen mode is open, PAUSED, the clock and the warp. The clock and warp keep to the right
 * edge on a wider screen.
 */
final class StatusStrip extends NpInstrument {
    static final float H = 26f;
    private static final Color UNDER = NpColors.rgb(0x0B0C0E);

    String seat = "", authority = "", clock = "", warp = "";
    boolean authorityHeld, battle, paused;
    /** The chip's counts, shown while a full-screen mode is open (chip true). */
    boolean chip;
    int warnings, cautions;
    Level chipLevel;
    /** A click on the chip closes the mode. */
    Runnable onChip;

    StatusStrip(NpKit kit) {
        super(kit);
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                float yy = down(y);
                if (!chip || onChip == null || yy > H || x < CHIP_X || x > CHIP_X + CHIP_W) return false;
                onChip.run();
                return true;
            }
        });
    }

    private static final float CHIP_X = 640f, CHIP_W = 100f;

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), y = 17.7f;
        NpHardware.bezel(cv, 0f, 0f, w, H, 0f);
        cv.fill(0f, H - 1f, w, 1f, UNDER);
        cv.fillRound(4f, 3f, w - 8f, 20f, 1f, NpColors.GLASS, 1f);
        // seat: 14 characters; helm authority: 36, to x 474
        cv.text(14f, y, seat, CtxDraw.B, true, NpColors.WHITE, 0.6f, NpCanvas.START);
        if (!authority.isEmpty()) {
            String a = CtxDraw.fit(cv, authority, 474f - 158f, CtxDraw.B, false, 0.3f);
            cv.text(158f, y, a, CtxDraw.B, false, authorityHeld ? NpColors.DIM : NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        if (battle) NpHardware.lens(cv, 486f, 5f, 142f, 16f, "BATTLE STATIONS", NpColors.AMBER, false, CtxDraw.LB);
        if (chip && (warnings > 0 || cautions > 0)) chip(cv, CHIP_X, 5f);
        if (paused) NpHardware.lens(cv, w - 528f, 5f, 64f, 16f, "PAUSED", null, true, CtxDraw.LB);
        cv.text(w - 314f, y, clock, CtxDraw.B, true, NpColors.WHITE, 0.4f, NpCanvas.END);
        cv.text(w - 14f, y, CtxDraw.fit(cv, warp, 287f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, NpColors.WHITE, 0.4f, NpCanvas.END);
    }

    /** The chip: the warnings and cautions as counts by their shapes, framed in the worst one's colour. */
    private void chip(NpCanvas cv, float x, float y) {
        Color c = CtxDraw.color(chipLevel == null ? Level.CAUTION : chipLevel);
        cv.stroke(x, y, CHIP_W, 16f, 1.2f, c);
        float px = x + 12f, base = y + 12.5f;
        if (warnings > 0) {
            CtxDraw.severity(cv, px, base, Level.WARNING, NpColors.RED);
            px += 8f + cv.text(px + 8f, base, Integer.toString(warnings), CtxDraw.LB, true, NpColors.WHITE, 0f, NpCanvas.START) + 10f;
        }
        if (warnings > 0 && cautions > 0) {
            cv.text(px - 4f, base, "·", CtxDraw.LB, true, NpColors.DIM, 0f, NpCanvas.MIDDLE);
            px += 8f;
        }
        if (cautions > 0) {
            CtxDraw.severity(cv, px, base + 0.5f, Level.CAUTION, NpColors.AMBER);
            cv.text(px + 8f, base, Integer.toString(cautions), CtxDraw.LB, true, NpColors.WHITE, 0f, NpCanvas.START);
        }
    }
}
