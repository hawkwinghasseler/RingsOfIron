package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The suit readout (spec 7.4), strapped to the captain's forearm, on deck only: the avatar's own survival,
 * as their suit's display shows it. The suit's air on drums while suited in bad air, NO SUIT in the warning
 * role when unsuited in it; the room's pressure on a gauge; lamps for low oxygen, fire and a wound, the
 * wound in words under them. Up only while the room's air is bad or falling, it burns, or the captain is hurt.
 */
final class SuitReadout extends YardPart {
    static final float X = 16f, Y = 704f, W = StationsLayout.SUIT_W, H = StationsLayout.SUIT_H;
    /** A normal cabin's pressure, kPa. */
    static final float CABIN_KPA = 101.325f;

    private final StationsFrame f;
    private float lastPressure = Float.NaN, falling;

    SuitReadout(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** The spec's trigger: the room's air not breathable or falling, a fire in it, the captain hurt. */
    boolean wanted() {
        HudData d = f.data;
        CaptainStatus c = d.captainStatus;
        if (!c.present || "dead".equals(c.state)) return false;
        return bad(c) || falling > 0f || fire(d) || hurt(c);
    }

    /** Watches the room's pressure, so a falling one brings the readout up before it is bad. */
    void watch(float delta) {
        CaptainStatus c = f.data.captainStatus;
        float pr = c.present ? c.roomPressure : Float.NaN;
        // Falling faster than a hundredth of cabin pressure a second: up for 3 s more each time
        if (!Float.isNaN(pr) && !Float.isNaN(lastPressure) && delta > 0f && (lastPressure - pr) / delta > 0.01f) falling = 3f;
        else falling = Math.max(0f, falling - delta);
        lastPressure = pr;
    }

    static boolean bad(CaptainStatus c) {
        if (Float.isNaN(c.roomPressure)) return true;
        if (c.roomPressure < ShipAlerts.LOW_PRESSURE) return true;
        if (!Float.isNaN(c.roomOxygen) && c.roomOxygen < ShipAlerts.LOW_PRESSURE) return true;
        return !Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.3f;
    }

    static boolean fire(HudData d) {
        String room = d.captainStatus.room;
        return !room.isEmpty() && d.alerts.get("fire:" + room) != null;
    }

    static boolean hurt(CaptainStatus c) {
        return !c.wound.isEmpty() || !Float.isNaN(c.bleedOut) || c.health < 0.95f || !"conscious".equals(c.state);
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        CaptainStatus c = d.captainStatus;
        float x = X, y = Y;
        p.rrect(x + 30f, y - 6f, 34f, H + 12f, 5f, Yard.RUBBER);
        p.rrect(x + 136f, y - 6f, 34f, H + 12f, 5f, Yard.RUBBER);
        Hardware.plate(p, x, y, W, H, true);
        boolean badAir = bad(c);
        if (!c.suited && badAir) {
            p.legend("SUIT", x + 12f, y + 18f, Yard.INK);
            Hardware.lens(p, x + 12f, y + 28f, 68f, 26f, "NO SUIT", Yard.RED_LAMP, true, true);
        } else if (c.suited && !Float.isNaN(c.suitAir)) {
            p.legend("SUIT AIR", x + 12f, y + 18f, Yard.INK);
            p.drums(x + 12f, y + 27f, YardText.clock(c.suitAir), 15f, 26f, 19f, false);
        } else {
            p.legend("SUIT AIR", x + 12f, y + 18f, Hardware.DIM_INK);
            p.drums(x + 12f, y + 27f, "    ", 15f, 26f, 19f, false);
        }
        String wound = wound(c);
        if (!wound.isEmpty()) p.legendFit(wound, x + 12f, y + 72f, 76f, Yard.PAPER_RED);
        float kpa = Float.isNaN(c.roomPressure) ? 0f : c.roomPressure * CABIN_KPA;
        Hardware.gauge(p, x + 102f, y + 38f, 20f, 0f, 101f, kpa, new float[][]{{0f, 50f}}, new com.badlogic.gdx.graphics.Color[]{Yard.RED});
        Hardware.legendC(p, Float.isNaN(c.roomPressure) ? "OUTSIDE" : Math.round(kpa) + " KPA", x + 102f, y + 74f, Yard.INK);
        boolean o2 = !Float.isNaN(c.roomOxygen) && c.roomOxygen < ShipAlerts.LOW_PRESSURE || kpa < 70f;
        p.lamp(x + 140f, y + 18f, 4f, Yard.AMBER_LAMP, o2);
        p.legend("O2 LOW", x + 149f, y + 22.5f, Yard.INK);
        p.lamp(x + 140f, y + 39f, 4f, Yard.RED_LAMP, fire(d));
        p.legend("FIRE", x + 149f, y + 43.5f, Yard.INK);
        boolean critical = !Float.isNaN(c.bleedOut) || c.health < 0.3f;
        p.lamp(x + 140f, y + 60f, 4f, critical ? Yard.RED_LAMP : Yard.AMBER_LAMP, hurt(c));
        p.legend("WOUND", x + 149f, y + 64.5f, Yard.INK);
    }

    /** The captain's condition in words: "LIGHT WOUND", "CRIT 4:10" while bleeding out. */
    static String wound(CaptainStatus c) {
        if (!Float.isNaN(c.bleedOut)) return "CRIT " + YardText.clock(c.bleedOut);
        if ("unconscious".equals(c.state)) return "OUT COLD";
        if (!hurt(c)) return "";
        if (c.health >= 0.6f) return "LIGHT WOUND";
        if (c.health >= 0.3f) return "SERIOUS";
        return "CRITICAL";
    }

    @Override
    public String hover(float px, float py) {
        CaptainStatus c = f.data.captainStatus;
        if (!c.wound.isEmpty()) return "You: " + c.wound.toLowerCase(java.util.Locale.ROOT);
        if (!c.room.isEmpty()) return "The air in the " + c.room.toLowerCase(java.util.Locale.ROOT);
        return null;
    }
}
