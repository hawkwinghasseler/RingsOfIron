package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * F4, the threat list on the left rail at fire control and a gun: what is shooting at us, in order of
 * danger. One tube: the missiles inbound in red, soonest first, with who fired each and whether point
 * defense is on them or the decoy is the answer; then the hostile ships in the fight other than the lock
 * (the lock card has the lock) in amber, nearest first, with their range and what they are doing.
 */
final class ThreatList extends YardPart {
    static final float W = 308f, MAX_H = 160f, LINE = 18f, PAD = 7f;

    /** One line: its kind tag, the time or the name, the shooter or the range, the last word. */
    static final class Line {
        String tag = "", a = "", b = "", c = "";
        boolean missile;
    }

    private final ContextFrame f;
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    private final Array<MissileTrack> missiles = new Array<>();
    private final Array<Contact> ships = new Array<>();

    ThreatList(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** This frame's lines; returns the height, 0 with nothing inbound and no other hostile in the fight. */
    float measure(HudData d) {
        pool.addAll(lines);
        lines.clear();
        missiles.clear();
        for (int i = 0; i < d.threatFrame.missiles.size; i++) {
            MissileTrack m = d.threatFrame.missiles.get(i);
            if (m.side == Side.INCOMING) missiles.add(m);
        }
        missiles.sort(ThreatList::soonest);
        ships.clear();
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            Contact c = d.contactFrame.contacts.get(i);
            if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE && !c.locked && fighting(c)) ships.add(c);
        }
        int most = (int) ((MAX_H - PAD * 2f) / LINE);
        boolean pd = d.weaponFrame.pointDefenseActive, decoy = d.weaponFrame.decoyCooldown <= 0f;
        for (int i = 0; i < missiles.size && lines.size < most; i++) {
            MissileTrack m = missiles.get(i);
            Line l = line();
            l.missile = true;
            l.tag = "MSL";
            l.a = Float.isNaN(m.timeToImpact) ? "--" : Kit.tMinus(m.timeToImpact).toUpperCase(Locale.ROOT);
            l.b = m.shooter.isEmpty() ? "" : m.shooter;
            l.c = pd ? "PD ON" : decoy ? "DECOY" : "";
        }
        for (int i = 0; i < ships.size && lines.size < most; i++) {
            Contact c = ships.get(i);
            Line l = line();
            l.tag = "SHIP";
            l.a = c.label;
            l.b = Kit.dist(c.range).toUpperCase(Locale.ROOT);
            l.c = doing(c);
        }
        return lines.size == 0 ? 0f : PAD * 2f + lines.size * LINE;
    }

    private Line line() {
        Line l = pool.size > 0 ? pool.pop() : new Line();
        l.tag = l.a = l.b = l.c = "";
        l.missile = false;
        lines.add(l);
        return l;
    }

    private static int soonest(MissileTrack a, MissileTrack b) {
        float ta = Float.isNaN(a.timeToImpact) ? Float.MAX_VALUE : a.timeToImpact;
        float tb = Float.isNaN(b.timeToImpact) ? Float.MAX_VALUE : b.timeToImpact;
        return Float.compare(ta, tb);
    }

    /** Still in the fight: not struck, silenced, disabled or wrecked. */
    static boolean fighting(Contact c) {
        return c.state == ContactProvider.State.FIT || c.state == ContactProvider.State.RUNNING;
    }

    /** What a hostile ship is doing, in a word: FIRING (it has us), RUNNING, CLOSING, OPENING. */
    static String doing(Contact c) {
        if (c.threat) return "FIRING";
        if (c.state == ContactProvider.State.RUNNING) return "RUNNING";
        if (Float.isNaN(c.closing) || Math.abs(c.closing) < 1f) return "";
        return c.closing > 0f ? "CLOSING" : "OPENING";
    }

    @Override
    protected void draw(YardPaint p) {
        Kit.slideRail(p, -x, true, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        float tx = 6f, ty = 5f, tw = w - 12f, th = h - 10f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            Color c = l.missile ? Yard.P_RED : Yard.P_AMBER;
            float base = ty + PAD - 2f + i * LINE + 13f;
            p.tube(l.tag, tx + 8f, base, 13f, l.missile ? c : Yard.P_DIM, false);
            if (l.missile) {
                p.tube(l.a, tx + 50f, base, 13f, c, true);
                Kit.tubeFit(p, l.b, tx + 128f, base, 13f, c, 96f);
            } else {
                Kit.tubeFit(p, YardText.cut(l.a, 12), tx + 50f, base, 13f, c, 112f);
                Kit.tubeFit(p, l.b, tx + 166f, base, 13f, c, 62f);
            }
            if (!l.c.isEmpty()) {
                float cw = p.width(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.TUBE, 13f, l.c);
                p.tube(l.c, tx + tw - 8f - cw, base, 13f, l.missile ? Yard.P_AMBER : Yard.P_DIM, false);
            }
        }
        p.tubeOver(tx, ty, tw, th);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        int i = (int) ((localY(py) - 5f - PAD + 2f) / LINE);
        if (i < 0 || i >= lines.size) return null;
        Line l = lines.get(i);
        if (l.missile) {
            if (l.c.equals("DECOY")) return "Point defense isn't on it: " + Kit.key(d, "DECOY", "F") + " throws the decoy";
            if (l.c.equals("PD ON")) return "Point defense is engaging";
            return null;
        }
        return "Lock it with " + Kit.key(d, "NEXT_TARGET", "Tab") + " to see it on the lock card";
    }
}
