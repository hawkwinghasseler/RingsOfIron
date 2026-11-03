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
import java.util.function.Consumer;

/**
 * The station board (section 8.5), 1088 x 20 under the band: six lens keys of 150, then the
 * chronometer. A tile says who works its station (YOU on a cyan chip, a crew member's name, or --)
 * and lights in its alert's role; occupancy and alert both show. It is struck through while its
 * console doesn't work. CMD has no occupancy, only its alerts and LATCHED. In Green with nothing
 * alerting the whole board is dim, YOU included. A click on a tile puts its station on the right MFD.
 * At a gun only the gun's own tile shows. In a blackout the board goes dark with the seat: its lenses
 * unlit and blank, the chronometer too (section 5.1).
 */
final class StationBoard extends NpInstrument {
    static final float W = StationsLayout.BAND_W, H = StationsLayout.BOARD_H;
    private static final float TILE = 146f, STEP = 150f;

    enum Hand { YOU, CREW, EMPTY, NONE }

    static final class Tile {
        Hand hand = Hand.EMPTY;
        String who = "";
        Level alert;
        boolean struck;
        /** CMD's badge: LATCHED, or "". */
        String badge = "";
    }

    final Tile[] tiles = new Tile[Station.values().length];
    boolean dim, dark;
    String day = "", time = "";
    /** At a gun: the one tile to show, by station, and its name there ("GUN · NOSE"); null for the whole board. */
    Station only;
    String onlyName = "";
    Consumer<Station> onStation;

    StationBoard(NpKit kit) {
        super(kit);
        setSize(W, H);
        for (int i = 0; i < tiles.length; i++) tiles[i] = new Tile();
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (only != null || dark || onStation == null) return false;
                int i = (int) ((x - 4f) / STEP);
                if (x < 4f || i < 0 || i >= tiles.length || x - 4f - i * STEP > TILE) return false;
                onStation.accept(Station.values()[i]);
                return true;
            }
        });
    }

    @Override
    protected void paint(NpCanvas cv) {
        NpHardware.bezel(cv, 0f, 0f, W, H, 3f);
        float ty = 3f, th = H - 6f;
        Station[] all = Station.values();
        for (int i = 0; i < all.length; i++) {
            if (only != null && all[i] != only) continue;
            float tx = only != null ? 4f : 4f + i * STEP;
            if (dark) cv.fillRound(tx, ty, TILE, th, 1.5f, NpColors.LENS_OFF, 1f);
            else tile(cv, tx, ty, th, all[i], tiles[i], only != null ? onlyName : all[i].tag);
        }
        float rx = 4f + all.length * STEP, rw = W - 8f - all.length * STEP;
        cv.fillRound(rx, ty, rw, th, 1f, NpColors.GLASS, 1f);
        if (dark) return;
        cv.text(rx + 8f, ty + 11.5f, day, 12f, true, dim ? StDraw.LOW : NpColors.DIM, 0.4f, NpCanvas.START);
        cv.text(rx + rw - 8f, ty + 11.5f, time, 12f, true, dim ? NpColors.DIM : NpColors.WHITE, 0f, NpCanvas.END);
    }

    private void tile(NpCanvas cv, float tx, float ty, float th, Station s, Tile t, String name) {
        Color lit = t.alert == Level.WARNING ? NpColors.RED : t.alert == Level.CAUTION ? NpColors.AMBER : null;
        cv.fillRound(tx, ty, TILE, th, 1.5f, lit != null ? lit : NpColors.LENS_OFF, 1f);
        if (lit != null) cv.fillRound(tx, ty, TILE, th * 0.4f, 1.5f, NpColors.WHITE, 0.14f);
        boolean low = dim && lit == null;
        Color ink = lit != null ? NpColors.INK : low ? StDraw.LOW : t.hand == Hand.EMPTY || t.struck ? NpColors.LENS_LEGEND : NpColors.STENCIL;
        cv.text(tx + 7f, ty + 11.5f, Integer.toString(s.number()), 12f, true, lit != null ? NpColors.INK : low ? StDraw.LOW : NpColors.LENS_LEGEND, 0f, NpCanvas.START);
        float nx = tx + 22f;
        if (lit != null) {
            StDraw.severity(cv, nx + 4f, ty + 11.5f, t.alert, NpColors.INK);
            nx += 13f;
        }
        float whoX = tx + TILE - 4f;
        float whoW = 0f;
        if (t.hand == Hand.YOU && !low) {
            cv.fillRound(whoX - 34f, ty + 1f, 34f, th - 2f, 1f, NpColors.CYAN, 1f);
            cv.text(whoX - 17f, ty + 11f, "YOU", 12f, true, NpColors.GLASS, 0.3f, NpCanvas.MIDDLE);
            whoW = 38f;
        } else {
            String who = t.hand == Hand.YOU ? "YOU" : t.hand == Hand.NONE ? t.badge : t.hand == Hand.EMPTY ? "--" : t.who;
            if (!who.isEmpty()) {
                who = StDraw.fitName(cv, who, 64f, 12f, true, 0.3f);
                whoW = cv.text(whoX - 3f, ty + 11.5f, who, 12f, true, ink, 0.3f, NpCanvas.END) + 8f;
            }
        }
        cv.text(nx, ty + 11.5f, StDraw.fit(cv, name, whoX - whoW - nx - 4f, 12f, true, 0.6f), 12f, true, ink, 0.6f, NpCanvas.START);
        if (t.struck) cv.line(tx + 4f, ty + th / 2f, tx + TILE - 4f, ty + th / 2f, 1.5f, lit != null ? NpColors.INK : NpColors.LENS_LEGEND);
    }
}
