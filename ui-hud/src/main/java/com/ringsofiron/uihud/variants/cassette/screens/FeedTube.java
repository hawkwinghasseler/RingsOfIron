package com.ringsofiron.uihud.variants.cassette.screens;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The one-line feed tube in a big console's foot (style guide 9.2): the newest notice under its LOG
 * tag, or an order's answer under the page's tag for a while.
 */
final class FeedTube {
    /** Seconds an order's answer stays up. */
    static final float SAID_FOR = 8f;
    private String feedText = "";
    private String saidTag, saidText;
    private float saidFor;
    private String lastNewestText;
    private int lastNewestCount;

    /** An order's answer, in the tube for a while. */
    void say(String tag, String text) {
        if (text == null || text.isEmpty()) return;
        saidTag = tag;
        saidText = text.toUpperCase(Locale.ROOT);
        saidFor = SAID_FOR;
    }

    /** Back to the newest notice. */
    void clear() {
        saidText = null;
    }

    void update(float delta) {
        if (saidText != null && (saidFor -= delta) <= 0f) saidText = null;
    }

    /** The tube, {@code w} wide and 18 tall, from ({@code x}, {@code y}). */
    void draw(YardPaint p, HudData data, float x, float y, float w) {
        read(data);
        p.tube(x, y, w, 18f, 2f, 3f, false);
        String tag = saidText != null ? saidTag : "LOG", text = saidText != null ? saidText : feedText;
        p.rect(x + 5f, y + 2.5f, 46f, 15f, Yard.P_AMBER);
        p.text(YardFonts.Face.TUBE, 12.5f, tag, x + 8f, y + 13.5f, Yard.CRT_BLACK);
        int room = (int) ((w - 70f) / 7.8f);
        p.tube(text.length() > room ? Ledger.cut(text, room) : text, x + 62f, y + 14f, 13f, Yard.P_AMBER, false);
        p.tubeOver(x, y, w, 18f);
    }

    /**
     * Reads the newest notice, upper-cased once when it changes: by what it says, as the feed's
     * messages are pooled and one entry carries another notice after a refill.
     */
    private void read(HudData data) {
        MessageFeed.Message m = data == null ? null : data.messages.newest();
        if (m == null) {
            feedText = "";
            lastNewestText = null;
            return;
        }
        if (!m.text.equals(lastNewestText) || m.count != lastNewestCount) {
            lastNewestText = m.text;
            lastNewestCount = m.count;
            feedText = m.label().toUpperCase(Locale.ROOT);
        }
    }
}
