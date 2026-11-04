package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.List;

/**
 * X1, the station panel on the right rail while docked: the station's card typed in a holder (its name
 * and who runs it), the port on a drum, the standing on flaps and a drum with the sponsor's lens, then a
 * row for each service the station offers, absent ones closed up. Market, yard and missions have their
 * keys; the shipyard, hiring and the infirmary are tabs in the terminal, so their rows say so. UNDOCK
 * sits at the foot with its key.
 */
final class StationPanel extends YardPart {
    static final float W = 308f, CARD_H = 56f, ROW = 24f;

    /** One service row: its key ("" for a terminal tab) and its name. */
    static final class Service {
        final String key, name;

        Service(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    private final ContextFrame f;
    final List<Service> services = new ArrayList<>();

    StationPanel(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The station's services in the terminal's order, each with its key or none; returns the height. */
    float measure(HudData d) {
        services.clear();
        StationInfo s = d.navReadout.docked;
        if (!s.present && d.ctx.stationName.isEmpty()) return 0f;
        if (s.market) services.add(new Service(Kit.key(d, "TRADE", "T"), "MARKET"));
        if (s.yard) services.add(new Service(Kit.key(d, "YARD", "Y"), "YARD"));
        if (s.shipyard) services.add(new Service("", "SHIPYARD"));
        if (s.contracts) services.add(new Service(Kit.key(d, "MISSIONS", "J"), "MISSIONS"));
        if (s.hiring) services.add(new Service("", "HIRE CREW"));
        if (s.infirmary) services.add(new Service("", "INFIRMARY"));
        return 8f + CARD_H + 8f + 26f + 6f + services.size() * ROW + 6f + 28f + 6f;
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
        // The standing
        float y = cy + CARD_H + 10f;
        p.face(8f, y - 2f, w - 16f, 26f, Yard.Finish.YARD, false);
        p.legend("STANDING", 16f, y + 15f, Yard.INK);
        p.flaps(82f, y + 2f, YardText.fit(s.standingName, 9), 9, 8.4f, 17f, 12.5f, Yard.FLAP_TEXT);
        if (!Float.isNaN(s.standing)) {
            int v = Math.round(s.standing);
            p.legend(v < 0 ? "−" : "+", 166f, y + 15f, Yard.INK);
            p.drums(176f, y + 1f, String.valueOf(Math.min(100, Math.abs(v))), 10f, 18f, 14f, false);
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
            if (sv.key.isEmpty()) Kit.legendR(p, "IN THE TERMINAL", w - 18f, base, Kit.DIM_INK);
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
            return sv.key.isEmpty() ? sv.name.charAt(0) + sv.name.substring(1).toLowerCase(java.util.Locale.ROOT) + ": a tab in the station's terminal ("
                    + Kit.key(d, "TRADE", "T") + ")" : null;
        }
        return null;
    }
}
