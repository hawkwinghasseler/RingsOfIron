package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * The deck's own parts (functional-task 6, INT and OVL's deck overlays). INT: beside what the captain
 * stands at, a small slab with its name once in legend type, then at most two keys, each with its verb
 * ("HELM  E OPEN  V SIT AND FLY"). The hazard tags: one on each hazard in view, its name ("FIRE
 * (MEDIUM)", "BREACH, VENTING", "SHORT: REACTOR"), then who is on it ("1 ON IT") or NOBODY; and for
 * hazards out of sight at most five pointers at the edge of the view, warnings first.
 */
final class Deck {
    static final int TAGS = 12, POINTERS = 5;
    static final float PROMPT_H = 24f;

    static final class Tag {
        float x, y;
        String text = "", who = "", room = "";
        Kit.Sev sev = Kit.Sev.CAUTION;
        boolean pointer, nobody;
        char dir;
    }

    final Tag[] tags = new Tag[TAGS];
    int tagCount;
    // INT
    boolean prompt;
    String name = "";
    final String[] keys = new String[2], verbs = new String[2];
    int verbCount;
    float promptX, promptY;
    private final Vector2 v = new Vector2(), avatar = new Vector2();

    Deck() {
        for (int i = 0; i < TAGS; i++) tags[i] = new Tag();
    }

    /**
     * Reads this frame's prompt and hazards on a {@code width} x {@code height} stage; tags stay inside
     * {@code l, t, r, b} and the rest point from its edge. {@code console} is what the prompt is for, or null.
     */
    void update(HudData d, Posts p, ViewportManager views, Entity console, float width, float height, float l, float t, float r, float b) {
        tagCount = 0;
        prompt = false;
        if (!p.deck() || views == null || d.ctx.ship == null) return;
        ShipGridComponent g = d.ctx.ship.getComponent(ShipGridComponent.class);
        TransformComponent tx = d.ctx.ship.getComponent(TransformComponent.class);
        if (g == null || tx == null) return;
        boolean own = d.ctx.presence != Presence.ABOARD_OTHER && d.ctx.presence != Presence.EVA;
        prompt(d, views, g, tx, console, height);
        if (own) tags(d, views, g, tx, height, l, t, r, b);
        if (prompt) {
            // On the stage, clear of the edges
            promptX = MathUtils.clamp(promptX, l, Math.max(l, r - 360f));
            promptY = MathUtils.clamp(promptY, t, Math.max(t, b - PROMPT_H));
        }
    }

    // ------------------------------------------------------------------ INT

    private void prompt(HudData d, ViewportManager views, ShipGridComponent g, TransformComponent tx, Entity console, float height) {
        verbCount = 0;
        name = "";
        String act = d.promptAction == null ? "" : d.promptAction.trim();
        if (act.isEmpty()) return;
        String key = d.promptKey == null || d.promptKey.isEmpty() ? "E" : d.promptKey.toUpperCase(Locale.ROOT);
        // A console's prompt names it and can offer its seat: "for the Helm panel  [V] sit and fly"
        String extraKey = "", extraVerb = "";
        int open = act.indexOf('['), close = open < 0 ? -1 : act.indexOf(']', open);
        if (open > 0 && close > open + 1) {
            extraKey = act.substring(open + 1, close).trim().toUpperCase(Locale.ROOT);
            extraVerb = act.substring(close + 1).trim().toUpperCase(Locale.ROOT);
            act = act.substring(0, open).trim();
        }
        String lower = act.toLowerCase(Locale.ROOT);
        InteractableConsoleComponent c = console == null ? null : console.getComponent(InteractableConsoleComponent.class);
        if (lower.startsWith("for the ") && c != null) {
            name = c.title.toUpperCase(Locale.ROOT);
            verb(key, "OPEN");
        } else if (lower.startsWith("for the ")) {
            String s = act.substring(8);
            if (s.toLowerCase(Locale.ROOT).endsWith(" panel")) s = s.substring(0, s.length() - 6);
            name = s.toUpperCase(Locale.ROOT);
            verb(key, "OPEN");
        } else {
            verb(key, (lower.startsWith("to ") ? act.substring(3) : act).toUpperCase(Locale.ROOT));
        }
        if (!extraKey.isEmpty() && !extraVerb.isEmpty()) verb(extraKey, extraVerb);
        // Beside the console, else beside the captain
        float tileX, tileY;
        if (c != null) {
            tileX = c.tileX + 0.5f;
            tileY = c.tileY + 0.5f;
        } else if (d.interactor != null && d.interactor.getAvatarTile(avatar)) {
            tileX = avatar.x;
            tileY = avatar.y;
        } else {
            return;
        }
        screen(views, g, tx, tileX, tileY, height);
        promptX = v.x + 28f;
        promptY = v.y + 18f;
        prompt = true;
    }

    private void verb(String key, String verb) {
        if (verbCount == keys.length) return;
        keys[verbCount] = key;
        verbs[verbCount] = Words.cut(verb, 28);
        verbCount++;
    }

    /** The keys the prompt shows, for the key strip to leave out. */
    void shownKeys(Array<String> out) {
        if (!prompt) return;
        for (int i = 0; i < verbCount; i++) out.add(keys[i]);
    }

    /** A tile's middle on the stage, y down, into {@link #v}. */
    private void screen(ViewportManager views, ShipGridComponent g, TransformComponent tx, float tileX, float tileY, float height) {
        float cos = MathUtils.cosDeg(tx.rotation), sin = MathUtils.sinDeg(tx.rotation);
        float lx = g.gridOriginLocal.x + tileX * g.tileSize, ly = g.gridOriginLocal.y + tileY * g.tileSize;
        views.worldToUi(tx.x + lx * cos - ly * sin, tx.y + lx * sin + ly * cos, v);
        v.y = height - v.y;
    }

    // ------------------------------------------------------------------ the hazard tags

    private void tags(HudData d, ViewportManager views, ShipGridComponent g, TransformComponent tx, float height, float l, float t, float r, float b) {
        int pointers = 0;
        Array<Alert> all = d.alerts.all();
        // Warnings first, so the five pointers go to the worst
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.size && tagCount < TAGS; i++) {
                Alert a = all.get(i);
                if (a.tileX < 0 || (a.level == Level.WARNING) != (pass == 0)) continue;
                String text = text(a, g);
                if (text.isEmpty() || seen(text, a.room)) continue;
                pointers += place(views, g, tx, a.tileX, a.tileY, height, l, t, r, b, pointers, text, a.room,
                        a.level == Level.WARNING ? Kit.Sev.WARNING : Kit.Sev.CAUTION, a.handling);
            }
        }
        // Shorts, which have a tile but no alert of their own on the deck
        for (SystemRow s : d.powerFrame.faults) {
            if (tagCount == TAGS) break;
            if (s.fault != Fault.SHORTED || s.tileX < 0) continue;
            String text = "SHORT: " + s.system.toUpperCase(Locale.ROOT);
            if (seen(text, s.room)) continue;
            Alert a = d.alerts.get("sys:" + s.system + ":" + s.tileX + "," + s.tileY);
            pointers += place(views, g, tx, s.tileX, s.tileY, height, l, t, r, b, pointers, text, s.room, Kit.Sev.CAUTION,
                    a == null ? -1 : a.handling);
        }
    }

    /** Places one tag, or a pointer when it is out of sight; returns the pointers it took. */
    private int place(ViewportManager views, ShipGridComponent g, TransformComponent tx, int tileX, int tileY, float height,
            float l, float t, float r, float b, int pointers, String text, String room, Kit.Sev sev, int handling) {
        screen(views, g, tx, tileX + 0.5f, tileY + 0.5f, height);
        float sx = v.x, sy = v.y;
        boolean in = sx > l && sx < r && sy > t && sy < b;
        if (!in && pointers == POINTERS) return 0;
        Tag tag = tags[tagCount++];
        tag.text = text;
        tag.room = room == null ? "" : room;
        tag.sev = sev;
        tag.who = handling < 0 ? "" : handling == 0 ? "NOBODY" : handling + " ON IT";
        tag.nobody = handling == 0;
        tag.pointer = !in;
        if (in) {
            tag.x = sx;
            tag.y = sy - 40f;
            return 0;
        }
        pointer(tag, sx, sy, l, t, r, b);
        return 1;
    }

    /** One tag per room and name. */
    private boolean seen(String text, String room) {
        String rm = room == null ? "" : room;
        for (int i = 0; i < tagCount; i++) if (tags[i].text.equals(text) && tags[i].room.equals(rm)) return true;
        return false;
    }

    /** A hazard's tag: "FIRE (MEDIUM)", "BREACH, VENTING", "SMOKE", "THIN AIR", "VACUUM"; "" for what has no tag. */
    static String text(Alert a, ShipGridComponent g) {
        switch (a.code) {
            case "FIRE": {
                String size = Catalogue.fireSize(g, a);
                return size.isEmpty() ? "FIRE" : "FIRE (" + size + ")";
            }
            case "BREACH": return "BREACH, VENTING";
            case "VACUUM": return "VACUUM";
            case "SMOKE": return "SMOKE";
            case "PRESSURE": return "THIN AIR";
            default: return "";
        }
    }

    /** A pointer where the ray from the region's middle towards ({@code x, y}) leaves it. */
    private static void pointer(Tag tag, float x, float y, float l, float t, float r, float b) {
        float cx = (l + r) / 2f, cy = (t + b) / 2f, dx = x - cx, dy = y - cy;
        if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) dx = 1f;
        float kx = dx != 0f ? ((dx > 0 ? r - 40f : l + 40f) - cx) / dx : Float.MAX_VALUE;
        float ky = dy != 0f ? ((dy > 0 ? b - 14f : t + 14f) - cy) / dy : Float.MAX_VALUE;
        float k = Math.min(kx, ky);
        tag.x = cx + dx * k;
        tag.y = cy + dy * k;
        tag.dir = kx <= ky ? (dx > 0 ? 'r' : 'l') : (dy > 0 ? 'd' : 'u');
    }

    // ------------------------------------------------------------------ painting, in stage units

    void paintTags(Filament f) {
        for (int i = 0; i < tagCount; i++) {
            Tag t = tags[i];
            if (t.pointer) {
                Kit.edge(f, t.x, t.y, t.dir, t.text, t.sev.color(), Drive.NORM);
                continue;
            }
            Kit.deckTag(f, t.x, t.y, t.text, t.sev, Drive.NORM);
            if (t.who.isEmpty()) continue;
            float w = f.dmWidth(t.who.length(), f.o().pitch(2f)), ly = t.y + 16f;
            f.scrim(t.x - w / 2f - 6f, ly - 5f, w + 12f, 24f, 4f, 0.62f);
            f.dm(t.x - w / 2f, ly, t.who, f.o().pitch(2f).proj().color(t.nobody ? Phosphor.AMBER : Phosphor.ZINC).level(t.nobody ? Drive.NORM : Drive.DIM).noGhost());
        }
    }

    float promptWidth(Filament f) {
        float w = 8f + f.legendWidth(name, f.o()) + (name.isEmpty() ? 0f : 12f);
        for (int i = 0; i < verbCount; i++) w += f.keycapWidth(keys[i], 16f, 12f) + 4f + f.dmWidth(verbs[i].length(), f.o().pitch(Geo.P)) + 10f;
        return w - 10f + 8f;
    }

    void paintPrompt(Filament f) {
        if (!prompt) return;
        float x = promptX, y = promptY, w = promptWidth(f);
        f.slab(x, y, w, PROMPT_H, 4f, 1, false);
        float cx = x + 8f;
        if (!name.isEmpty()) cx += f.legend(cx, y + 17f, name, f.o().dim()) + 12f;
        for (int i = 0; i < verbCount; i++) {
            cx += f.keycap(cx, y + 4f, keys[i], 16f, 12f, i == 0) + 4f;
            cx += f.dm(cx, y + 6.75f, verbs[i], f.o().pitch(Geo.P).full().noGhost()) + 10f;
        }
    }
}
