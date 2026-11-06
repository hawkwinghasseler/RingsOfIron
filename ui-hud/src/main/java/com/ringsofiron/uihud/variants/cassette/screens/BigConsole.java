package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The big console a full-screen mode raises (style guide 9.2): the head with the mode's nameplate
 * (or, at a station, its badge and name), its tabs as interlocked piano keys and where the player is
 * on split flaps; the body, where the tab's page draws; and the foot, with the newest notice on a
 * one-line feed tube and the keys that close the mode. A station's console is the station's own
 * equipment, in its livery, with the rail of credits, standing, hold and tanks on every tab and the
 * jack that patches it into the ship.
 *
 * <p>Drawn in its own units, (0, 0) its top left: the head is 30 tall, the foot 27, the body between.
 */
final class BigConsole extends YardPart implements Hits.Target {
    static final float HEAD_H = 30f, FOOT_H = 27f, RAIL_W = 316f;
    /** Seconds the console takes to rise into place. */
    static final float RISE = 0.45f;

    enum Livery {
        /** A station's: the Patrol's grey-green with a navy stripe and light legends. */
        STATION,
        /** The ship's own: yard enamel on the chassis. */
        SHIP
    }

    // The station's livery (style guide 4.4)
    static final Color LIV_TOP = Yard.hex("727563"), LIV_FOOT = Yard.hex("5D604F");
    static final Color LIV_DEEP_TOP = Yard.hex("43463A"), LIV_DEEP_FOOT = Yard.hex("33352C");
    static final Color LIV_INK = Yard.hex("E6E2D2"), LIV_INK2 = Yard.hex("CFD2C0"), LIV_STRIPE = Yard.hex("2F4767");
    static final Color NAME_INK = Yard.hex("F0EBDD"), BADGE = Yard.hex("2B2D26");
    static final Color SHIP_STRIPE = Yard.hex("8F846C"), SEAM = Yard.hex("0D0B0A");
    static final Color PLATE_HI = Yard.hex("F4EEDD"), PLATE_LO = Yard.hex("7D735E");
    static final Color KEY_UP = Yard.hex("ECE4CE"), KEY_DOWN = Yard.hex("CFC4A8");

    final Livery livery;
    /** The nameplate: SHIP, BOARDING, RECORD, CONTROLS; a station's console shows the station instead. */
    final String nameplate;
    /** The mode's own key, which closes it with Esc: for the foot. */
    final Array<Page> pages = new Array<>();
    private final Array<Page> tabs = new Array<>();
    private Page current;
    private final Hits hits = new Hits();
    private final StationRail rail;
    private final Boards boards;
    /** What shows on split flaps beside the tabs: the ship's name on the ship's console. */
    String flaps = "";
    /** Whether the flaps show the player's ship's name. */
    private boolean shipFlaps;
    /** The keys that close the mode, for the foot: the open tab's own key and Esc. */
    private final Array<String> closeKeys = new Array<>();
    private float riseT = RISE;
    private boolean closing;
    private float sinceRefresh;
    private HudData data;
    /** The foot's feed: the newest notice, or a page's answer for a while. */
    private final FeedTube feed = new FeedTube();

    BigConsole(Livery livery, String nameplate, Boards boards) {
        super(0f, 0f);
        this.livery = livery;
        this.nameplate = nameplate;
        this.boards = boards;
        this.rail = livery == Livery.STATION ? new StationRail(boards) : null;
    }

    /** Shows the player's ship's name on the flaps beside the tabs, as the ship's console does. */
    BigConsole shipName() {
        shipFlaps = true;
        return this;
    }

    BigConsole add(Page p) {
        p.console = this;
        pages.add(p);
        return this;
    }

    Page current() {
        return current;
    }

    boolean up() {
        return visible;
    }

    /** Raises the console on {@code page} (or turns it to that tab if it is up already). */
    void show(Page page, HudData data) {
        this.data = data;
        boolean rising = !visible;
        visible = true;
        if (rising) riseT = 0f;
        // A page comes up fresh as the console rises: a guard left lifted is down, the log at its newest
        if (current != page || rising) {
            current = page;
            page.shown();
        }
        refreshNow();
    }

    void hide() {
        visible = false;
        feed.clear();
        closing = false;
    }

    /** Puts the console back up on the tab it had, settled, without rising afresh: what stood over it has gone. */
    void resume(HudData data) {
        this.data = data;
        visible = true;
        riseT = RISE;
        refreshNow();
    }

    /** Asks to close at the next frame: an order handed the player to the deck (placing furniture, the ship editor). */
    void closeSoon() {
        closing = true;
    }

    /** Whether an order asked to close it. */
    boolean closing() {
        return closing;
    }

    /** Reads every page's provider now: after an order, or as the console comes up. */
    void refreshNow() {
        sinceRefresh = 0f;
        if (data == null) return;
        boards.refresh(data);
        if (shipFlaps) flaps = boards.shipCaps;
        for (int i = 0; i < pages.size; i++) pages.get(i).refresh(boards);
        tabs.clear();
        for (int i = 0; i < pages.size; i++) if (pages.get(i).offered()) tabs.add(pages.get(i));
    }

    /** Once a frame while it shows. */
    void update(float delta) {
        riseT = Math.min(RISE, riseT + delta);
        sinceRefresh += delta;
        if (sinceRefresh >= 0.5f) refreshNow();
        feed.update(delta);
    }

    /** A page's answer to an order, in the feed tube for a while. */
    void say(String tag, String text) {
        feed.say(tag, text);
    }

    /** Turns to the tab with key {@code n} (1 for the first shown). */
    boolean tab(int n) {
        if (n < 1 || n > tabs.size) return false;
        Page p = tabs.get(n - 1);
        if (p != current) {
            current = p;
            p.shown();
        }
        return true;
    }

    /** Turns to the tab {@code d} along from the open one, round the row. */
    boolean flip(int d) {
        if (tabs.size == 0) return false;
        int i = Math.max(0, tabs.indexOf(current, true));
        return tab((i + d + tabs.size) % tabs.size + 1);
    }

    /** The tab with {@code page} on it, if it is offered now. */
    boolean offers(Page page) {
        return tabs.contains(page, true);
    }

    /**
     * A key while the console shows. The number keys turn the tabs, and are the console's even past
     * its last tab, so a helm order on them waits until it closes (Context, section 10).
     */
    boolean key(int keycode) {
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            tab(keycode - Input.Keys.NUM_0);
            return true;
        }
        if (keycode >= Input.Keys.NUMPAD_1 && keycode <= Input.Keys.NUMPAD_9) {
            tab(keycode - Input.Keys.NUMPAD_0);
            return true;
        }
        return current != null && current.key(keycode);
    }

    void scroll(float amount) {
        if (current != null) current.scroll(amount);
    }

    /** The console's own key for the foot, as the mode's window has it; Esc is always there. */
    void closeKeys(String... keys) {
        closeKeys.clear();
        for (String k : keys) if (k != null && !k.isEmpty()) closeKeys.add(k);
        closeKeys.add("ESC");
    }

    /** How far it still has to rise: a 2-unit bump at the top as it settles. */
    float riseOffset() {
        float t = MathUtils.clamp(riseT / RISE, 0f, 1f);
        if (t >= 1f) return 0f;
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        return (1f - e) * h * 0.35f - MathUtils.sin(t * MathUtils.PI) * 2f;
    }

    @Override
    public boolean pressable(float px, float py) {
        return hits.any(px, py);
    }

    @Override
    public boolean press(float px, float py) {
        return hits.press(px, py);
    }

    @Override
    public void hit(int code) {
        // The tabs' piano keys
        tab(code + 1);
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void draw(YardPaint p) {
        hits.clear();
        boolean liv = livery == Livery.STATION;
        float bodyTop = HEAD_H, footTop = h - FOOT_H;
        // The body's ground, then the head
        if (liv) p.vgrad(0f, 0f, w, h, LIV_DEEP_TOP, LIV_DEEP_FOOT);
        else p.vgrad(0f, 0f, w, h, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        if (liv) p.vgrad(0f, 0f, w, HEAD_H, LIV_TOP, LIV_FOOT);
        else p.vgrad(0f, 0f, w, HEAD_H, Yard.ENAMEL_TOP, Yard.ENAMEL_BOTTOM);
        p.rect(0f, 0f, w, 1f, Color.WHITE, 0.18f);
        p.rect(0f, bodyTop - 3f, w, 3f, liv ? LIV_STRIPE : SHIP_STRIPE);
        p.rect(0f, bodyTop, w, 1.5f, SEAM);
        float x = head(p, liv);
        x = piano(p, x, liv);
        if (!flaps.isEmpty()) {
            String f = flaps.length() > 26 ? flaps.substring(0, 26) : flaps;
            p.flaps(x + 30f, 5f, f, f.length(), 10f, 20f, 14f, Yard.FLAP_TEXT);
        }
        // The body: the rail on a station's console, the page beside it
        float pw = w;
        if (rail != null) {
            pw = w - RAIL_W - 8f;
            rail.draw(p, pw, bodyTop + 6f, RAIL_W, footTop - bodyTop - 12f);
        }
        if (current != null) current.draw(p, hits, 0f, bodyTop, pw, footTop - bodyTop);
        if (liv) jack(p, 24f, footTop - 59f);
        foot(p, footTop);
    }

    /** The head's left: the station's badge and name, or the mode's nameplate. Returns where the tabs go. */
    private float head(YardPaint p, boolean liv) {
        if (liv) {
            float nx = 14f;
            if (!boards.badge.isEmpty()) {
                p.rrect(10f, 4f, 32f, 22f, 2f, BADGE);
                p.textC(YardFonts.Face.LEGEND, 12f, boards.badge, 26f, 19.5f, LIV_INK);
                nx = 50f;
            }
            float nw = p.text(YardFonts.Face.LEGEND, 18f, boards.stationCaps, nx, 22.5f, NAME_INK);
            float ow = boards.ownerCaps.isEmpty() ? 0f : p.text(YardFonts.Face.LEGEND, 12f, boards.ownerCaps, nx + nw + 14f, 21f, LIV_INK2) + 14f;
            return Math.max(340f, nx + nw + ow + 24f);
        }
        float nw = p.text(YardFonts.Face.LEGEND, 20f, nameplate, 14f, 22f, Yard.INK);
        return Math.max(84f, 14f + nw + 24f);
    }

    /** The tabs as interlocked piano keys, the open one down with its orange foot. Returns their right. */
    private float piano(YardPaint p, float x, boolean liv) {
        int n = tabs.size;
        if (n == 0) return x;
        float kw = liv ? 104f : 100f, kh = 24f, gap = 3f, y = 3f;
        p.rrect(x - 3f, y - 3f, n * (kw + gap) - gap + 6f, kh + 6f, 2.5f, Yard.BAKELITE);
        for (int i = 0; i < n; i++) {
            Page pg = tabs.get(i);
            float kx = x + i * (kw + gap);
            boolean dn = pg == current, jewel = pg.jewel();
            float ky = y + (dn ? 2f : 0f);
            if (!dn) p.rrect(kx + 0.5f, ky + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
            p.rrect(kx, ky, kw, kh - 2f, 2f, dn ? KEY_DOWN : KEY_UP);
            p.rect(kx + 2f, ky + 0.8f, kw - 4f, 1f, Color.WHITE, 0.5f);
            if (dn) p.rect(kx, y + kh - 4f, kw, 3f, Yard.ORANGE);
            p.key(kx + 6f, ky + 3.5f, DIGITS[Math.min(i + 1, 9)]);
            float jw = jewel ? 14f : 0f, lx = kx + 21f + (kw - 21f - jw) / 2f - 2f;
            p.textC(YardFonts.Face.LEGEND, 12f, pg.label, lx, y + kh / 2f + 12f * 0.36f + (dn ? 1f : -1f), Yard.INK);
            if (jewel) p.lamp(kx + kw - 12f, ky + 10f, 2.4f, Yard.AMBER_LAMP, true);
            hits.add(p, kx, y, kw, kh, this, i);
        }
        return x + n * (kw + gap) - gap;
    }

    private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    /** The foot: the newest notice, or a page's answer, on the feed tube; the close keys from the right. */
    private void foot(YardPaint p, float y) {
        p.rect(0f, y, w, FOOT_H, Yard.CHASSIS_DEEP);
        p.rect(0f, y, w, 1.5f, Yard.hex("0B0908"));
        feed.draw(p, data, 14f, y + 5f, Math.min(560f, w * 0.44f));
        float x = w - 12f;
        x -= p.width(YardFonts.Face.LEGEND, 12f, "CLOSE");
        p.legend("CLOSE", x, y + 18.5f, Yard.INK_LIGHT);
        x -= 7f;
        for (int i = closeKeys.size - 1; i >= 0; i--) {
            String k = closeKeys.get(i);
            x -= p.keyWidth(k);
            p.key(x, y + 6.5f, k);
            x -= 3f;
        }
    }

    /** The jack the ship's cable is plugged into: the station's equipment patched into the deck. */
    private static void jack(YardPaint p, float x, float y) {
        p.rrect(x, y, 58f, 36f, 3f, Yard.ALU);
        p.glass(x, y, 58f, 36f);
        p.screw(x + 7f, y + 7f);
        p.screw(x + 51f, y + 7f);
        p.screw(x + 7f, y + 29f);
        p.screw(x + 51f, y + 29f);
        p.circle(x + 29f, y + 18f, 10f, Yard.hex("1A1715"));
        // The cable, falling away out of the console's edge
        float px = x + 29f, py = y + 18f;
        for (int i = 0; i < 14; i++) {
            float t0 = i / 14f, t1 = (i + 1) / 14f;
            float ax = bez(px, px, x - 6f, x - 30f, t0), ay = bez(py, y + 44f, y + 30f, y + 52f, t0);
            float bx = bez(px, px, x - 6f, x - 30f, t1), by = bez(py, y + 44f, y + 30f, y + 52f, t1);
            p.line(ax, ay, bx, by, 7f, Yard.hex("141210"));
            p.line(ax, ay - 1.5f, bx, by - 1.5f, 1.2f, Yard.hex("4A4641"));
        }
        p.rrect(x + 21f, y + 10f, 16f, 16f, 2.5f, Yard.hex("2B2723"));
        p.frame(x + 21f, y + 10f, 16f, 16f, 1f, Yard.hex("5A524B"), 1f);
    }

    private static float bez(float a, float b, float c, float d, float t) {
        float u = 1f - t;
        return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d;
    }

    /** A bay: a plate let into the console with its engraved title, in the console's livery. */
    static void bay(YardPaint p, float x, float y, float w, float h, String title, boolean liv) {
        p.rrect(x + 1.5f, y + 2.5f, w, h, 3f, Yard.SHADOW, 0.6f);
        if (liv) p.vgrad(x, y, w, h, LIV_TOP, LIV_FOOT);
        else p.vgrad(x, y, w, h, Yard.ENAMEL_TOP, Yard.ENAMEL_BOTTOM);
        p.rect(x + 3f, y + 0.2f, w - 6f, 1.1f, PLATE_HI, 0.9f);
        p.rect(x + 3f, y + h - 1.2f, w - 6f, 1.1f, PLATE_LO, 0.8f);
        p.rect(x + w - 1.2f, y + 3f, 1.1f, h - 6f, PLATE_LO, 0.8f);
        if (title != null) p.legend(title, x + 14f, y + 16f, liv ? LIV_INK : Yard.INK);
    }
}
