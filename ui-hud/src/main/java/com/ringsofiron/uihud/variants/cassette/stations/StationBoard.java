package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The station board (spec 8.5) on its sill under the band: six tiles, one a station, each a soft key, and
 * the chronometer at the right end. A tile has two parts that both show: who works the station (YOU, a
 * name, nobody) on its lens and beside it, and its jewel, lit red or amber while the station has a warning
 * or a caution. In Green with nothing alerting the whole board is dim; in Yellow and Red YOU lights. A
 * station whose console is out is struck through. At a gun the board is only that gun's tile.
 */
final class StationBoard extends YardPart {
    static final float X = 88f, Y = 776f;
    private static final float TILE_W = StationsLayout.TILE_W, TILE_H = StationsLayout.TILE_H, LENS_W = 62f;
    private static final Color TILE = Yard.hex("2F2924"), JEWEL_RING = Yard.hex("1F1B18"), JEWEL_OFF = Yard.hex("3B2C22");
    private static final Color LEGEND_DIM = new Color(240 / 255f, 230 / 255f, 210 / 255f, 0.55f), STRUCK_INK = Yard.hex("7D7466");
    private static final Color GLYPH = Yard.hex("241406");

    private final StationsFrame f;
    private final Actions actions;
    /** At a gun: only the gun's own tile, TAC. */
    boolean gun;
    /** Docked and the berth card has folded: its badge rides on the COMMS tile. */
    boolean berthBadge;

    StationBoard(StationsFrame f, Actions actions) {
        super(X, Y);
        this.f = f;
        this.actions = actions;
    }

    @Override
    protected void draw(YardPaint p) {
        float w = gun ? TILE_W + 16f : StationsLayout.BAND_W + 16f;
        // The sill: the units stand on its back edge, 8 above where its own place starts
        Hardware.pod(p, X, Y - 8f, w, StationsLayout.SILL_H, false);
        boolean dim = !f.alerted() && !alerting();
        boolean dark = f.blackout;
        if (gun) {
            tile(p, X + 8f, Y, Station.TAC, dim || dark);
            if (dim || dark) p.rect(X + 8f, Y - 2f, TILE_W, TILE_H + 4f, Color.BLACK, dark ? 0.45f : 0.36f);
            jewel(p, X + 8f, Y, Station.TAC);
            return;
        }
        Station[] all = Station.values();
        for (int i = 0; i < all.length; i++) tile(p, X + 10f + i * 150f, Y, all[i], dim || dark);
        chronometer(p, X + 912f, Y, dim);
        if (dim || dark) p.rect(X + 8f, Y - 2f, StationsLayout.BAND_W, TILE_H + 4f, Color.BLACK, dark ? 0.45f : 0.36f);
        // The jewels light in their role in any condition, over the dimming
        for (int i = 0; i < all.length; i++) jewel(p, X + 10f + i * 150f, Y, all[i]);
    }

    private boolean alerting() {
        for (Level l : f.alerts.station) if (l != null) return true;
        return false;
    }

    private void tile(YardPaint p, float x, float y, Station s, boolean dim) {
        HudData d = f.data;
        p.rrect(x, y, TILE_W, TILE_H, 2f, TILE);
        p.rrect(x + 2f, y + 2f, LENS_W, TILE_H - 4f, 1.5f, Yard.ALU);
        boolean you = you(s) && !dim;
        if (you) p.glowRect(x + 4f, y + 4f, LENS_W - 4f, TILE_H - 8f, 5f, Yard.WHITE_LAMP.on, 0.6f);
        p.rrect(x + 4f, y + 4f, LENS_W - 4f, TILE_H - 8f, 1f, you ? Yard.WHITE_LAMP.on : Yard.WHITE_LAMP.off);
        p.glass(x + 4f, y + 4f, LENS_W - 4f, TILE_H - 8f);
        p.textC(Face.LEGEND, 12f, s.number + " " + s.tag, x + 4f + (LENS_W - 4f) / 2f, y + TILE_H / 2f + 4.3f, you ? GLYPH : LEGEND_DIM);
        boolean struck = f.struck(s);
        String occ = occupant(d, s);
        p.legendFit(occ, x + LENS_W + 9f, y + TILE_H / 2f + 4.3f, 50f, struck ? STRUCK_INK : Yard.INK_LIGHT);
        boolean badge = s == Station.COMMS && berthBadge || s == Station.CMD && f.latched;
        if (badge) {
            float ax = x + TILE_W - 22f;
            p.rect(ax - 22f, y + 4f, 14f, 14f, Yard.PAPER);
            p.fillConvex(new float[]{ax - 8f, y + 8f, ax - 8f, y + 4f, ax - 4f, y + 8f}, Yard.PAPER, 1f);
            p.rect(ax - 8f, y + 8f, 4f, 10f, Yard.PAPER);
            p.line(ax - 19f, y + 11f, ax - 9f, y + 11f, 1f, Yard.hex("6B6254"));
            p.line(ax - 19f, y + 14.5f, ax - 11f, y + 14.5f, 1f, Yard.hex("6B6254"));
        }
        if (struck) p.barber(x + 3f, y + TILE_H / 2f - 1.5f, TILE_W - 6f, 3f);
    }

    /** The jewel: the station's worst caution or warning, a lamp in a dark bezel so unlit it still reads as one. */
    private void jewel(YardPaint p, float x, float y, Station s) {
        float jx = x + TILE_W - 13f, jy = y + TILE_H / 2f, r = 7f;
        Level l = f.alerts.station[s.ordinal()];
        p.circle(jx, jy, r + 2.6f, JEWEL_RING);
        if (l != null) {
            Yard.Lamp lamp = l == Level.WARNING ? Yard.RED_LAMP : Yard.AMBER_LAMP;
            p.glow(jx, jy, r * 2.2f, lamp.on, 0.55f);
            p.circle(jx, jy, r, lamp.on);
            Hardware.mark(p, l == Level.WARNING, jx, jy + 0.3f, 3.6f, GLYPH);
        } else p.circle(jx, jy, r, JEWEL_OFF);
        p.circle(jx - 2f, jy - 2.6f, 2f, Color.WHITE, l != null ? 0.35f : 0.16f);
    }

    /** The player is at a console hosting the station: the helm hosts Helm, Navigation and Comms; the weapons console Tactical. */
    private boolean you(Station s) {
        switch (s) {
            case HELM: case NAV: case COMMS: return f.seat == StationsFrame.Seat.HELM;
            case TAC: return f.seat == StationsFrame.Seat.WEAPONS || f.seat == StationsFrame.Seat.GUN;
            default: return false;
        }
    }

    /**
     * Who does the station's work: the helm seat's for Helm and Navigation (the helmsman flies the plan), the
     * weapons console's for Tactical, whoever is at the reactor or life support for Engineering. Nobody works
     * Comms but the player at the helm; Command has no occupancy part.
     */
    static String occupant(HudData d, Station s) {
        switch (s) {
            case HELM: case NAV: return who(d.stations.at(Post.HELM));
            case TAC: return who(d.stations.at(Post.WEAPONS));
            case ENG: {
                String a = who(d.stations.at(Post.REACTOR));
                return !a.equals("—") ? a : who(d.stations.at(Post.LIFE_SUPPORT));
            }
            case COMMS: {
                Seat h = d.stations.at(Post.HELM);
                return h != null && h.hand == Hand.PLAYER ? "YOU" : "—";
            }
            default: return "";
        }
    }

    private static String who(Seat s) {
        if (s == null) return "—";
        switch (s.hand) {
            case PLAYER: return "YOU";
            case CREW: return YardText.fit(s.who, 8);
            case COMING: return YardText.fit(s.who, 7) + "…";
            default: return "—";
        }
    }

    /** The chronometer: the day and the time of day, the same at every seat. */
    private void chronometer(YardPaint p, float x, float y, boolean dim) {
        double t = f.data.ctx.simSeconds;
        long day = (long) Math.floor(t / 86400.0) + 1, s = (long) Math.floor(t % 86400.0);
        p.legend("DAY", x + 6f, y + 15.5f, dim ? Hardware.DIM_INK : Yard.INK_LIGHT);
        String ds = String.valueOf(Math.min(999, day));
        float dw = p.drums(x + 34f, y + 1f, ds, 11f, 17f, 13.5f, false);
        String time = String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        p.drums(x + 34f + dw + 12f, y + 1f, time, 11f, 17f, 13.5f, false);
    }

    // --- Presses -------------------------------------------------------------------------------------

    private Station at(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (ly < Y || ly > Y + TILE_H) return null;
        if (gun) return lx >= X + 8f && lx <= X + 8f + TILE_W ? Station.TAC : null;
        Station[] all = Station.values();
        for (int i = 0; i < all.length; i++) {
            float tx = X + 10f + i * 150f;
            if (lx >= tx && lx <= tx + TILE_W) return all[i];
        }
        return null;
    }

    @Override
    public boolean pressable(float px, float py) {
        return !gun && at(px, py) != null;
    }

    /** A tile is a soft key: its station's first page on the right MFD, again its next. */
    @Override
    public boolean press(float px, float py) {
        Station s = at(px, py);
        if (s == null || gun) return false;
        actions.station(s);
        return true;
    }

    @Override
    public String hover(float px, float py) {
        Station s = at(px, py);
        if (s == null) return null;
        Alert worst = null;
        for (int ai = 0; ai < f.data.alerts.all().size; ai++) {
            Alert a = f.data.alerts.all().get(ai);
            if (a.level == Level.ADVISORY || Station.of(a) != s) continue;
            worst = a;
            break;
        }
        String name = name(s);
        if (worst != null) return name + ": " + worst.text;
        if (f.struck(s)) return name + ": its console is out";
        return gun ? name : name + ": its pages on the right display";
    }

    static String name(Station s) {
        switch (s) {
            case NAV: return "Navigation";
            case TAC: return "Tactical";
            case ENG: return "Engineering";
            case COMMS: return "Comms";
            case CMD: return "Command";
            default: return "Helm";
        }
    }
}
