package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * X1, the station panel on the right rail while docked: the station's card typed in a holder (its name
 * and who runs it), the port on a drum, the standing as the game's signed number with the sponsor's lens
 * (the game names no levels: +0 until the player has dealt with it), then a row for each service the
 * station offers, absent ones closed up. Market, yard and missions have their keys; the yard says the
 * repairs when there are any and the tanks; the shipyard and hiring are tabs in the terminal, so their
 * rows say so; the infirmary has a row only while it treats one of the crew (v19). UNDOCK sits at the
 * foot with its key.
 */
final class StationPanel extends YardPart {
    static final float W = 308f, CARD_H = 56f, ROW = 24f;

    /** One service row: its key ("" for a terminal tab), its name, and what it says on the right. */
    static final class Service {
        final String key, name, note;

        Service(String key, String name, String note) {
            this.key = key;
            this.name = name;
            this.note = note;
        }
    }

    /** Seconds between looks at the yard's and the crew's sheets while docked. */
    private static final float LOOK_EVERY = 1f;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");

    private final ContextFrame f;
    final List<Service> services = new ArrayList<>();
    private final YardBoard yard = new YardBoard();
    private final CrewSheet crew = new CrewSheet();
    private float lookedAt = -100f, clock;
    private String yardNote = "", infirmaryNote = "";

    StationPanel(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The station's services in the terminal's order, each with its key or none; returns the height. */
    float measure(HudData d, float delta) {
        services.clear();
        clock += delta;
        StationInfo s = d.navReadout.docked;
        if (!s.present && d.ctx.stationName.isEmpty()) return 0f;
        if (clock - lookedAt >= LOOK_EVERY) look(d, s);
        if (s.market) services.add(new Service(Kit.key(d, "TRADE", "T"), "MARKET", ""));
        if (s.yard) services.add(new Service(Kit.key(d, "YARD", "Y"), "YARD", yardNote));
        if (s.shipyard) services.add(new Service("", "SHIPYARD", ""));
        if (s.contracts) services.add(new Service(Kit.key(d, "MISSIONS", "J"), "MISSIONS", ""));
        if (s.hiring) services.add(new Service("", "HIRE CREW", ""));
        if (s.infirmary && !infirmaryNote.isEmpty()) services.add(new Service("", "INFIRMARY", infirmaryNote));
        return 8f + CARD_H + 8f + 26f + 6f + services.size() * ROW + 6f + 28f + 6f;
    }

    /** Reads the yard's sheet and the crew's, for the yard's and the infirmary's rows. */
    private void look(HudData d, StationInfo s) {
        lookedAt = clock;
        yardNote = "";
        if (s.yard && d.yard != null) {
            yard.clear();
            d.yard.collect(yard);
            yardNote = yardNote(yard.damaged, yard.tanks);
        }
        infirmaryNote = "";
        if (s.infirmary && d.crew != null && !d.orderState.battleStations) {
            crew.clear();
            d.crew.collect(crew);
            infirmaryNote = treating(crew);
        }
    }

    /** The yard's row: "2 REPAIRS · TANKS 72%", the repairs only when there are any; the lowest tank's figure. */
    static String yardNote(int damaged, String tanks) {
        int low = -1;
        Matcher m = PERCENT.matcher(tanks == null ? "" : tanks);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (low < 0 || v < low) low = v;
        }
        String t = low < 0 ? "" : "TANKS " + low + "%";
        if (damaged <= 0) return t;
        String r = damaged + (damaged == 1 ? " REPAIR" : " REPAIRS");
        return t.isEmpty() ? r : r + " · " + t;
    }

    /**
     * Who the infirmary treats: the crew down or seriously or critically wounded, while docked at a
     * station that has one ("TREATING ADA", "TREATING ADA +1"); "" for nobody. What it has billed since
     * docking is not on the crew sheet.
     */
    static String treating(CrewSheet sheet) {
        String first = null;
        int n = 0;
        for (int i = 0; i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            String c = m.condition;
            if (c.startsWith("Critical") || c.startsWith("Serious") || c.startsWith("Unconscious")) {
                if (first == null) first = m.name;
                n++;
            }
        }
        return first == null ? "" : "TREATING " + first.toUpperCase(Locale.ROOT) + (n > 1 ? " +" + (n - 1) : "");
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        StationInfo s = d.navReadout.docked;
        String name = s.present && !s.name.isEmpty() ? s.name : d.ctx.stationName;
        Kit.slideRail(p, f.screenW - x, false, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        // The card in its holder
        float cx = 10f, cy = 8f, cw = w - 92f;
        p.rrect(cx - 3f, cy - 3f, cw + 6f, CARD_H + 6f, 3f, Yard.BAKELITE);
        p.paper(cx, cy, cw, CARD_H);
        p.typed(YardText.cut(name.toUpperCase(java.util.Locale.ROOT), 20), cx + 8f, cy + 20f, 15f, Yard.PAPER_INK);
        p.typed(YardText.cut(s.owner, 26), cx + 8f, cy + 40f, 13f, Yard.PAPER_INK);
        // The holder's corner tabs
        p.rect(cx - 3f, cy + CARD_H - 8f, 10f, 11f, Yard.BAKELITE);
        p.rect(cx + cw - 7f, cy + CARD_H - 8f, 10f, 11f, Yard.BAKELITE);
        // The port on a drum
        float px = w - 74f;
        p.face(px, cy - 2f, 64f, CARD_H + 4f, Yard.Finish.YARD, false);
        Kit.legendC(p, "PORT", px + 32f, cy + 16f, Yard.INK);
        int port = d.docking.otherPort;
        p.drums(px + 32f - (port >= 10 ? 13f : 6.5f), cy + 22f, port > 0 ? String.valueOf(port) : " ", 13f, 24f, 20f, false);
        // The standing: the game's signed number, +0 until the player has dealt with the faction
        float y = cy + CARD_H + 10f;
        p.face(8f, y - 2f, w - 16f, 26f, Yard.Finish.YARD, false);
        p.legend("STANDING", 16f, y + 15f, Yard.INK);
        if (!Float.isNaN(s.standing)) {
            int v = Math.round(s.standing);
            p.legend(v < 0 ? "−" : "+", 92f, y + 15f, Yard.INK);
            p.drums(102f, y + 1f, String.valueOf(Math.min(100, Math.abs(v))), 10f, 18f, 14f, false);
        }
        p.push(222f, y + 3f, 68f, 15f, new String[]{"SPONSOR"}, Yard.WHITE_LAMP, s.sponsor, YardPaint.Kind.INDICATOR, false, false);
        // A row per service
        y += 32f;
        for (int i = 0; i < services.size(); i++, y += ROW) {
            Service sv = services.get(i);
            p.face(8f, y, w - 16f, ROW - 3f, Yard.Finish.YARD, false);
            float base = y + 15f;
            if (!sv.key.isEmpty()) p.key(16f, y + 3f, sv.key);
            p.legend(sv.name, 44f, base, Yard.INK);
            if (!sv.note.isEmpty()) Kit.legendR(p, sv.note, w - 18f, base, Yard.INK);
            else if (sv.key.isEmpty()) Kit.legendR(p, "IN THE TERMINAL", w - 18f, base, Kit.DIM_INK);
        }
        // UNDOCK at the foot
        y += 6f;
        Color ink = Yard.INK_LIGHT;
        p.key(16f, y + 6f, Kit.key(d, "UNDOCK", "U"));
        p.legend("UNDOCK", 44f, y + 18f, ink);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        StationInfo s = d.navReadout.docked;
        float ly = localY(py);
        if (ly < 8f + CARD_H + 4f) return s.sponsor ? s.owner + " sponsors your career" : null;
        int i = (int) ((ly - (8f + CARD_H + 10f + 32f)) / ROW);
        if (i >= 0 && i < services.size()) {
            Service sv = services.get(i);
            if (sv.name.equals("INFIRMARY")) return "The station's medics treat the crew who are down or badly hurt while you're docked, and bill you for it";
            return sv.key.isEmpty() ? sv.name.charAt(0) + sv.name.substring(1).toLowerCase(java.util.Locale.ROOT) + ": a tab in the station's terminal ("
                    + Kit.key(d, "TRADE", "T") + ")" : null;
        }
        return null;
    }
}
