package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * The slate's Ship (functional-stations 10.4): the station board, each station with who holds it and
 * its worst live alert; and under it a page viewer, a repeater of the station in focus's MFD pages (the
 * same pages as the band's), read-only: ↑ and ↓ pick the station, ← and → its page.
 */
final class ShipPage extends SectionPage {
    static final int STATION = ITEM;
    static final int PREV = 0, NEXT = 1;
    /** The viewer: its title row, the MFD's page area under it, and a margin. */
    static final float VIEW_H = 24f + Geo.PAGE_H + 6f, VIEW_GAP = 12f;

    private final Pages viewer = new Pages();
    /** The page each station's viewer shows, by its place in {@link Mfds#STATIONS}. */
    private final int[] showing = new int[Mfds.STATIONS.length];
    private final String[] who = new String[Mfds.STATIONS.length];
    private final Level[] alert = new Level[Mfds.STATIONS.length];
    private float viewY;
    private boolean read;

    ShipPage() {
        super("SHIP", ClassicWindow.CREW, "");
        keys.define(2, 2).set(PREV, "PAGE BACK", true).set(NEXT, "NEXT PAGE", true).cap(PREV, "←").cap(NEXT, "→");
    }

    @Override
    void build(Boards b, HudData d) {
        board(d);
        int w = cells - 1;
        head("STATIONS");
        for (int s = 0; s < Mfds.STATIONS.length; s++) {
            String state = alert[s] == Level.WARNING ? "WARNING" : alert[s] == Level.CAUTION ? "CAUTION" : "";
            add(STATION, s, cols.clear().left((s + 1) + " " + Mfds.TAGS[s], 8).left(who[s].isEmpty() ? "-" : who[s], w - 16).right(state, 8)
                    .toString());
        }
    }

    /** Who holds each station and its worst live alert, as the band's board has them. */
    private void board(HudData d) {
        Seat helm = d.stations.at(Post.HELM), weapons = d.stations.at(Post.WEAPONS);
        Seat reactor = d.stations.at(Post.REACTOR), life = d.stations.at(Post.LIFE_SUPPORT);
        Presence seat = d.ctx.presence;
        who[0] = holder(helm);
        who[1] = who[0];
        who[2] = holder(weapons);
        String eng = holder(reactor);
        who[3] = eng.isEmpty() ? holder(life) : eng;
        who[4] = seat == Presence.HELM ? "YOU" : "";
        who[5] = "YOU";
        for (int i = 0; i < alert.length; i++) alert[i] = null;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.level == Level.ADVISORY) continue;
            int s = Band.station(a.category);
            if (alert[s] == null || a.level.ordinal() > alert[s].ordinal()) alert[s] = a.level;
        }
    }

    private static String holder(Seat s) {
        if (s == null) return "";
        if (s.hand == Hand.PLAYER) return "YOU";
        if (s.hand == Hand.CREW) return Fmt.cut(s.who, 8);
        return "";
    }

    /** The station in focus, by the board's numbering. */
    int station() {
        int f = pages.focus();
        return kindAt(f) == STATION ? ref.get(f) : 0;
    }

    /** The page the viewer shows. */
    Mfds.Page page() {
        int s = station();
        Mfds.Page[] pp = Mfds.STATIONS[s];
        return pp[Math.max(0, Math.min(pp.length - 1, showing[s]))];
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        // The viewer reads its page each frame, as the band reads the MFDs'
        viewer.read(page(), d);
        read = true;
    }

    @Override
    void place(Page.Place p) {
        // The list stands over the viewer
        float bottom = p.bottom;
        viewY = bottom - VIEW_H;
        p.bottom = viewY - VIEW_GAP;
        super.place(p);
        p.bottom = bottom;
    }

    @Override
    void keysFor(int focus) {
        boolean many = Mfds.STATIONS[station()].length > 1;
        keys.set(PREV, "PAGE BACK", many).set(NEXT, "NEXT PAGE", many);
    }

    @Override
    void press(int k, int focus) {
        int s = station(), n = Mfds.STATIONS[s].length;
        showing[s] = ((showing[s] + (k == NEXT ? 1 : -1)) % n + n) % n;
        HudData d = panels.data();
        if (d != null) viewer.read(page(), d);
    }

    @Override
    void paint(Filament f, float time) {
        super.paint(f, time);
        if (!read) return;
        float x = at.left - 4f, w = Geo.MFD_W, y = viewY;
        // A repeater of the MFD: its glass in the slate, its title row, its page
        f.module(x, y, w, VIEW_H, f.o().filaments(2));
        Mfds.Page p = page();
        float tw = f.dm(x + 8f, y + 5f, p.name(), f.o().pitch(1.5f).norm().noGhost());
        String setting = viewer.setting(p);
        if (!setting.isEmpty()) f.legend(x + 8f + tw + 10f, y + 15f, setting, f.o().dim());
        String owner = Mfds.TAGS[p.station()];
        f.dm(x + w - 8f - f.dmWidth(owner.length(), f.o().pitch(1.5f)), y + 5f, owner, f.o().pitch(1.5f).level(Drive.DIM).noGhost());
        f.rectLit(x + 6f, y + 20f, w - 12f, 0.7f, f.o().ghostOnly().k(2f));
        viewer.paint(f, p, x, y + 24f, w, Geo.PAGE_H);
    }
}
