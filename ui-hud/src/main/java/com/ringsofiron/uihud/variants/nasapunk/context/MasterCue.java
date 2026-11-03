package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * The master alert cue (section 6, P2): MASTER WARN and MASTER CAUT lenses, 24 tall since a click
 * acknowledges them. As a hood across the top of the seat's primary instrument (H1, H3, F2), in
 * the pilot's eyeline; on deck, docked or latched, standing alone at the bottom centre while one is
 * lit. A lit lens shows the newest code by its shape.
 */
final class MasterCue extends NpInstrument {
    static final float LENS_W = 132f, LENS_H = 24f, HOOD_H = 32f, ALONE_W = 2f * LENS_W + 22f, ALONE_H = 36f;

    boolean hood = true;
    /** What lights it: the newest code and its level; null level dark. */
    Level level;
    String code = "";
    /** Lit this long, s, for the first two seconds' flash. */
    float litFor;
    Runnable onAcknowledge;

    MasterCue(NpKit kit) {
        super(kit);
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (level == null || onAcknowledge == null) return false;
                onAcknowledge.run();
                return true;
            }
        });
    }

    /** Sizes it as a hood over an instrument {@code w} wide, or standing alone. */
    void shape(boolean asHood, float w) {
        hood = asHood;
        if (asHood) setSize(w - 8f, HOOD_H);
        else setSize(ALONE_W, ALONE_H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight(), ly = hood ? 3f : 6f;
        NpHardware.bezel(cv, 0f, 0f, w, h, 3f);
        // a warning flashes for its first 2 s, at twice a second
        boolean flashOff = level == Level.WARNING && litFor < 2f && (litFor * 2f) % 1f >= 0.5f;
        boolean warn = level == Level.WARNING && !flashOff, caut = level == Level.CAUTION;
        float lx = 7f, rx = w - 7f - LENS_W;
        if (warn) CtxDraw.sevLens(cv, lx, ly, LENS_W, LENS_H, CtxDraw.fit(cv, code, LENS_W - 26f, CtxDraw.LB, true, 0.2f), Level.WARNING);
        else CtxDraw.sevLens(cv, lx, ly, LENS_W, LENS_H, "MASTER WARN", null);
        if (caut) CtxDraw.sevLens(cv, rx, ly, LENS_W, LENS_H, CtxDraw.fit(cv, code, LENS_W - 26f, CtxDraw.LB, true, 0.2f), Level.CAUTION);
        else CtxDraw.sevLens(cv, rx, ly, LENS_W, LENS_H, "MASTER CAUT", null);
    }
}
