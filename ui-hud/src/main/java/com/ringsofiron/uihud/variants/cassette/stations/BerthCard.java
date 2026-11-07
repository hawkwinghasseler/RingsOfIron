package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The berth card (spec 10.3): on docking, a typed slip under the ticker for 8 s, saying where the ship is
 * berthed and the keys for what the station offers; then it folds into a badge on the COMMS tile. It
 * replaces the permanent "Docked at ..." label.
 */
final class BerthCard extends YardPart {
    static final float X = 430f, Y = 176f, W = StationsLayout.BERTH_W, H = StationsLayout.BERTH_H;
    /** How long the card stays up after docking. */
    static final float SECONDS = 8f;

    private final StationsFrame f;
    private boolean wasDocked;
    private float dockedAt = -100f;

    BerthCard(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** Notes the moment of docking; true while the card is up. */
    boolean update() {
        boolean docked = f.docked;
        if (docked && !wasDocked) dockedAt = f.time;
        wasDocked = docked;
        return docked && f.time - dockedAt < SECONDS;
    }

    /** Docked and the card has folded: the badge on COMMS. */
    boolean folded() {
        return f.docked && f.time - dockedAt >= SECONDS;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float x = X, y = Y;
        p.paper(x, y, W, H);
        p.dashed(x + 4f, y + 3f, x + W - 4f, y + 3f, 1f, 4f, 3f, Yard.PAPER_INK, 0.3f);
        StationInfo st = d.navReadout.docked;
        String name = st.present && !st.name.isEmpty() ? st.name : d.ctx.stationName;
        float tx = x + 14f, base = y + 26f;
        tx += p.typed("Docked at " + YardText.cut(name, 22) + ".", tx, base, 14f, Yard.PAPER_INK) + 16f;
        boolean all = !st.present;
        if (all || st.market) tx = service(p, tx, y, "TRADE", "T", "Market");
        if (all || st.yard) tx = service(p, tx, y, "YARD", "Y", "Yard");
        if (all || st.contracts) service(p, tx, y, "MISSIONS", "J", "Board");
    }

    private float service(YardPaint p, float x, float y, String action, String fallback, String word) {
        if (x > X + W - 60f) return x;
        float kw = p.key(x, y + 13f, Hardware.key(f.data, action, fallback));
        float w = p.typed(word, x + kw + 4f, y + 26f, 13f, Yard.PAPER_INK);
        return x + kw + 4f + w + 12f;
    }
}
