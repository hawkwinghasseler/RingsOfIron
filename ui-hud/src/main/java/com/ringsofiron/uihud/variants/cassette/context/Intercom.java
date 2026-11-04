package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * P4, the intercom repeater at the top right: what just happened, briefly. An amber tube hung from the
 * rail, at most three lines, newest on top, each with its source reversed (STN, GUNS, CREW, LOG) and
 * older lines dimmer. Routine lines hold 5 s and notable ones 8 s; a repeat counts ("×3") instead of
 * stacking. In combat the routine ones wait in the log and come back as one line when it ends, except a
 * reply to the captain's own key, which always shows at once. What the other units already carry is left
 * out: the guide's words (the clipboard), missile warnings (the threat unit), low propellant (the caution
 * board) and the ship's disablement (the emergency placard).
 */
final class Intercom extends YardPart {
    static final int LINES = 3;
    static final float LINE_H = 20f, PAD = 6f;
    static final float ROUTINE = 5f, NOTABLE = 8f;
    /** A notice this soon after a key press is the game's answer to it. */
    static final float REPLY = 0.8f;
    private static final float TAG_W = 46f;
    /** Rows the newest line may take. */
    static final int NEWEST_ROWS = 2;

    static final class Line {
        String tag = "", text = "";
        float born, hold;
        int count = 1;
    }

    private final ContextFrame f;
    final Array<Line> lines = new Array<>();
    /** Each line's rows, as {@link #measure} wrapped them this frame. */
    private final java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
    private final ObjectSet<String> atTime = new ObjectSet<>();
    private double noticeTime = Double.NEGATIVE_INFINITY;
    private boolean primed;
    private float lastKey = -10f;
    /** Routine lines kept back during combat, shown as one when it ends. */
    private int waiting;
    private boolean wasCombat;

    Intercom(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    void keyPressed() {
        lastKey = f.time;
    }

    /** Reads the game's new notices, and lets lines past their hold go. */
    void update(HudData d) {
        if (!f.combat && wasCombat && waiting > 0) {
            say("LOG", waiting == 1 ? "1 message in the log" : waiting + " messages in the log", ROUTINE);
            waiting = 0;
        }
        wasCombat = f.combat;
        Array<Message> all = d.messages.all();
        if (!primed) {
            // What was said before the HUD came up stays in the log
            primed = true;
            Message n = d.messages.newest();
            if (n != null) {
                noticeTime = n.time;
                for (int i = 0; i < all.size; i++) if (all.get(i).time == noticeTime) atTime.add(all.get(i).text);
            }
        } else {
            for (int i = all.size - 1; i >= 0; i--) {
                Message m = all.get(i);
                if (m.time < noticeTime) continue;
                if (m.time > noticeTime) {
                    noticeTime = m.time;
                    atTime.clear();
                }
                if (!atTime.add(m.text)) {
                    repeat(m);
                    continue;
                }
                notice(d, m);
            }
        }
        for (int i = lines.size - 1; i >= 0; i--) {
            Line l = lines.get(i);
            if (f.time - l.born > l.hold) lines.removeIndex(i);
        }
    }

    private void repeat(Message m) {
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            if (m.text.endsWith(l.text) && m.count > l.count) {
                l.count = m.count;
                l.born = f.time;
                return;
            }
        }
    }

    private void notice(HudData d, Message m) {
        String text = m.text.trim();
        if (text.isEmpty() || leftOut(d, text)) return;
        String who = "", body = text;
        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= 20 && !text.substring(0, colon).matches(".*\\d.*")) {
            who = text.substring(0, colon);
            body = text.substring(colon + 2);
        }
        String tag = tag(who, body);
        boolean reply = f.time - lastKey <= REPLY;
        boolean notable = m.important || m.level != Level.ADVISORY;
        if (f.combat && !reply && !notable && !tag.equals("GUNS")) {
            waiting++;
            return;
        }
        // A speaker who isn't a source keeps their name in the line ("Highport: welcome aboard", under STN)
        String line = !who.isEmpty() && !isSource(who) ? who + ": " + body : body;
        say(tag, line, notable ? NOTABLE : ROUTINE);
        if (m.count > 1) lines.first().count = m.count;
    }

    /** What another unit already says. */
    static boolean leftOut(HudData d, String text) {
        String guide = d.guideText;
        if (!guide.isEmpty() && (text.equals(guide) || guide.endsWith(text) || text.endsWith(guide))) return true;
        return text.startsWith("MISSILE WARNING") || text.startsWith("Propellant low") || text.startsWith("Your ship is disabled");
    }

    private void say(String tag, String text, float hold) {
        Line l = new Line();
        l.tag = tag;
        l.text = text;
        l.born = f.time;
        l.hold = hold;
        lines.insert(0, l);
        while (lines.size > LINES) lines.pop();
    }

    private static boolean isSource(String who) {
        String w = who.toLowerCase(Locale.ROOT);
        return w.equals("helm") || w.equals("gunnery") || w.equals("shields") || w.equals("engineering") || w.equals("medical")
                || w.equals("crew") || w.equals("boarding") || w.equals("station") || w.equals("sponsor") || w.equals("comms") || w.equals("log");
    }

    /** The source tag, by who speaks or what it is about (P4's sources). */
    static String tag(String who, String body) {
        String w = who.toLowerCase(Locale.ROOT);
        switch (w) {
            case "helm": return "HELM";
            case "gunnery": return "GUNS";
            case "shields": return "SHLD";
            case "engineering": return "ENG";
            case "medical": return "MED";
            case "crew": return "CREW";
            case "boarding": return "BOARD";
            case "station": return "STN";
            case "sponsor": return "SPON";
            case "comms": return "COMMS";
            case "log": return "LOG";
            default: break;
        }
        String t = body.toLowerCase(Locale.ROOT);
        if (has(t, "saved", "autosave", "loaded")) return "LOG";
        if (has(t, "clearance", "docking", "docked", "undock", "berth", "welcome", "port ", "market", "yard")) return "STN";
        if (has(t, "sponsor", "payday", "contract", "reward", "bounty", "paid")) return "SPON";
        if (has(t, "boarding", "boarders", "salvage", "prisoner", "cast off", "latched")) return "BOARD";
        if (has(t, "shield", "emitter", "trim")) return "SHLD";
        if (has(t, "hit", "missile", "target", "gun", "decoy", "disabled", "destroyed", "struck", "surrender", "locked", "lock on")) return "GUNS";
        if (has(t, "fire", "breach", "patch", "repair", "power", "reactor", "venting", "oxygen", "hull", "short", "tank", "smoke")) return "ENG";
        if (has(t, "wound", "bleed", "medbay", "infirmary", "treated")) return "MED";
        if (has(t, "helm", "burn", "warp", "orbit", "autopilot", "course", "approach", "heading", "assist")) return "HELM";
        if (has(t, "crew", "suit", "eva", "aboard", "inside", "hired")) return "CREW";
        if (!who.isEmpty()) return "COMMS";
        return "LOG";
    }

    private static boolean has(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    /** The unit's height for {@code rows} rows of text, 0 with none. */
    static float height(int rows) {
        return rows == 0 ? 0f : PAD * 2f + rows * LINE_H + 4f;
    }

    /**
     * Each line's rows this frame, and the unit's height: the newest line wraps to a second row when it is
     * too long for one ("THE AIR HERE IS GOING: GET A SUIT ON" says what to do), the older ones keep one.
     */
    float measure(YardPaint p) {
        rows.clear();
        int budget = Kit.chars(p, ContextLayout.COL_W - 12f - TAG_W - 18f, 13f);
        int n = 0;
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            String text = l.text.toUpperCase(Locale.ROOT) + (l.count > 1 ? " ×" + l.count : "");
            java.util.List<String> r = i == 0 ? Kit.wrap(text, budget, NEWEST_ROWS) : java.util.List.of(YardText.cut(text, budget));
            rows.add(r);
            n += Math.max(1, r.size());
        }
        return height(n);
    }

    @Override
    protected void draw(YardPaint p) {
        Kit.rods(p, 0f, w, ContextLayout.RAIL_H - y, 0f);
        Kit.housing(p, 0f, 0f, w, h, false);
        float tx = 6f, ty = 5f, tw = w - 12f, th = h - 10f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        int row = 0;
        for (int i = 0; i < lines.size && i < rows.size(); i++) {
            Line l = lines.get(i);
            boolean dim = i > 0;
            Color ink = dim ? Yard.P_DIM : Yard.P_AMBER;
            Kit.rev(p, l.tag, tx + 8f, ty + 2f + LINE_H * row + 15f, 12.5f, TAG_W, ink);
            java.util.List<String> r = rows.get(i);
            for (int j = 0; j < r.size(); j++, row++) {
                float base = ty + 2f + LINE_H * row + 15f;
                if (base > ty + th) break;
                Kit.tubeFit(p, r.get(j), tx + TAG_W + 14f, base, 13f, ink, tw - TAG_W - 18f);
            }
            if (r.isEmpty()) row++;
        }
        p.tubeOver(tx, ty, tw, th);
    }
}
