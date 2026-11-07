package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

/**
 * A console's panel (functional-task 7, CON), in place of the game's console window: a small terminal
 * anchored beside the console in the deck view, a dotted leader from its edge to a bracket on the
 * console, on the side away from the captain where the deck between the side columns has the room, and
 * never over the band or the helm's line. Its one page is the console's ({@link ConsoleTab}). E at a
 * system's console opens it; E or Esc closes it, and it closes itself when the captain walks away
 * (a walking key shuts it and the walk goes on, since the game holds the captain still while a
 * console is in use), sits down or leaves the ship. The helm and the weapons console open no panel: E
 * sits there, as V does (the game's own order at the weapons console, V's seat at the helm).
 */
final class ConsolePanel extends TerminalScreen {
    /** The housing round the page's rows: the header over them, the response line and the keys under. */
    static final float CHROME = 16f + Terminal.HEAD_H + Terminal.BOT + 7f * Glass.P + 38f + 6f;
    /** The bracket on the console, and the leader's dots. */
    static final float BRACKET = 9f;

    final ConsoleTab face = new ConsoleTab();
    /** Where the console's middle is on the stage, y down, NaN off it; how far one tile spans there. */
    float anchorX = Float.NaN, anchorY = Float.NaN, tile = 24f;
    /** The captain on the stage, y down, NaN unknown. */
    float captainX = Float.NaN;
    /** The side it stands on, 1 right of the console and -1 left, chosen as it opens; NaN until then. */
    float side = Float.NaN;
    private final Vector2 v = new Vector2(), avatar = new Vector2();

    ConsolePanel() {
        frame.closeCap = "E";
        frame.keySlots = 3;
        frame.titleH = 12f;
    }

    @Override
    Tab[] tabs() {
        return new Tab[] {face};
    }

    /** While a console is in use and the captain is on foot by it. */
    @Override
    boolean available(Boards b, HudData d) {
        return d.console.isOpen() && (d.ctx.presence == Presence.DECK || d.ctx.presence == Presence.ABOARD_OTHER);
    }

    @Override
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return w == ClassicWindow.CONSOLE && available(b, d);
    }

    /** E at a console: the panel, or at the helm and the weapons console the seat. */
    @Override
    boolean open(ClassicWindow w, Boards b, HudData d) {
        if (!opens(w, b, d)) return false;
        String id = d.console.data == null ? "" : d.console.data.consoleId;
        if (sits(id)) {
            sit(d);
            return false;
        }
        side = Float.NaN;
        return super.open(w, b, d);
    }

    /** Whether E at console {@code id} sits the captain there rather than opening a panel. */
    static boolean sits(String id) {
        return "helm".equals(id) || "weapons".equals(id);
    }

    /** Sits the captain at the console in use: the game's own order there on V, else the game's V. */
    private void sit(HudData d) {
        for (int i = 0; i < d.console.actions.size; i++) {
            ConsoleAction a = d.console.actions.get(i);
            if ("VIEW".equals(a.action)) {
                a.run.run();
                return;
            }
        }
        UiHud h = windows.gameHud();
        if (h != null) h.handleKey(Keys.V);
    }

    @Override
    boolean split() {
        return false;
    }

    @Override
    Cover cover() {
        return Cover.NONE;
    }

    /** The page reads the console first: the header is its system's. */
    @Override
    void refresh(Boards b, HudData d) {
        offer(b, d);
        if (tab != null) tab.refresh(b, d);
        title(b, d);
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = face.title;
        frame.owner = face.room;
        frame.noFigures();
        if (!Float.isNaN(face.health)) frame.figure("HEALTH", Integer.toString(Math.round(face.health * 100f)), 3, "%", null, 0);
    }

    /** E closes it, as Esc does; a walking key closes it and walks on. */
    @Override
    boolean key(int keycode) {
        if (keycode == Keys.E) {
            windows.closeAll();
            return true;
        }
        if (walks(keycode)) {
            windows.closeAll();
            return false;
        }
        return super.key(keycode);
    }

    /** Whether {@code keycode} walks the captain: the game's keys come to the HUD as their default keys. */
    static boolean walks(int keycode) {
        return keycode == Action.THRUST.defaultKey || keycode == Action.REVERSE.defaultKey
                || keycode == Action.TURN_LEFT.defaultKey || keycode == Action.TURN_RIGHT.defaultKey;
    }

    // ------------------------------------------------------------------ where it stands

    @Override
    void update(Boards b, HudData d, float delta) {
        super.update(b, d, delta);
        anchor(d);
    }

    /** The console's middle and the captain on the stage, as the deck's tags project a tile. */
    void anchor(HudData d) {
        anchorX = anchorY = captainX = Float.NaN;
        HudVariantHost host = windows.host();
        InteractableConsoleComponent c = d.console.data;
        ViewportManager views = host == null ? null : host.viewports();
        if (c == null || views == null) return;
        Entity ship = c.ship != null ? c.ship : d.ctx.ship;
        ShipGridComponent g = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        TransformComponent tx = ship == null ? null : ship.getComponent(TransformComponent.class);
        if (g == null || tx == null) return;
        float height = host.height();
        project(views, g, tx, c.tileX + 1.5f, c.tileY + 0.5f, height);
        float ox = v.x, oy = v.y;
        project(views, g, tx, c.tileX + 0.5f, c.tileY + 0.5f, height);
        anchorX = v.x;
        anchorY = v.y;
        tile = Math.max(4f, Vector2.len(ox - v.x, oy - v.y));
        if (d.interactor != null && d.interactor.getAvatarTile(avatar)) {
            project(views, g, tx, avatar.x, avatar.y, height);
            captainX = v.x;
        }
    }

    /** A point on the grid, in tiles, on the stage, y down, into {@link #v}. */
    private void project(ViewportManager views, ShipGridComponent g, TransformComponent tx, float tileX, float tileY, float height) {
        float cos = MathUtils.cosDeg(tx.rotation), sin = MathUtils.sinDeg(tx.rotation);
        float lx = g.gridOriginLocal.x + tileX * g.tileSize, ly = g.gridOriginLocal.y + tileY * g.tileSize;
        views.worldToUi(tx.x + lx * cos - ly * sin, tx.y + lx * sin + ly * cos, v);
        v.y = height - v.y;
    }

    /**
     * Beside the console, at least {@link Geo#CON_GAP} and a tile and a half from its middle: on the
     * side away from the captain, unless only the other has the room between the side columns. The
     * side holds while it shows, so the panel doesn't hop as the captain shifts.
     */
    @Override
    void bounds(float stageW, float stageH) {
        w = Geo.CON_W;
        float top = Geo.SCREEN_Y, floor = Geo.bottomY(stageH) + Geo.HLM_Y - 10f;
        h = Math.min(floor - top, CHROME + Math.max(face.rowsHeight, 3f * Glass.ROW));
        if (Float.isNaN(anchorX)) {
            // No console on the view: beside the middle, where the captain stands
            x = stageW / 2f + Geo.CON_GAP;
            y = Math.max(top, Math.min(floor - h, stageH / 2f - h / 2f));
            return;
        }
        float gap = Math.max(Geo.CON_GAP, tile * 1.5f);
        float left = Geo.LX + Geo.CW + 8f, right = Geo.rightX(stageW) + Geo.RX - 8f;
        float roomR = right - (anchorX + gap), roomL = anchorX - gap - left;
        if (Float.isNaN(side)) {
            boolean away = Float.isNaN(captainX) ? roomR >= roomL : captainX <= anchorX;
            side = away ? 1f : -1f;
        }
        // Flip only when the side it stands on runs out of room and the other has it
        if (side > 0f && roomR < w && roomL >= w) side = -1f;
        else if (side < 0f && roomL < w && roomR >= w) side = 1f;
        x = side > 0f ? anchorX + gap : anchorX - gap - w;
        x = Math.max(Terminal.PAD + 2f, Math.min(stageW - w - Terminal.PAD - 2f, x));
        y = Math.max(top, Math.min(floor - h, anchorY - h * 0.4f));
    }

    // ------------------------------------------------------------------ paint

    @Override
    void paint(Filament f, float time) {
        leader(f);
        super.paint(f, time);
    }

    /** A dotted leader from the panel's near edge to a bracket on the console, while it is on the view and clear of the panel. */
    private void leader(Filament f) {
        if (Float.isNaN(anchorX)) return;
        boolean inside = anchorX > x - Terminal.PAD && anchorX < x + w + Terminal.PAD && anchorY > y - Terminal.PAD && anchorY < y + h + Terminal.PAD;
        if (inside) return;
        float ex = anchorX < x ? x - Terminal.PAD : x + w + Terminal.PAD;
        float ey = Math.max(y + 24f, Math.min(y + h - 24f, anchorY));
        float bx = anchorX < x ? anchorX + BRACKET + 3f : anchorX - BRACKET - 3f;
        f.dotLine(ex, ey, bx, anchorY, f.o().proj().step(4f).size(1.4f).norm());
        // The bracket's four corners round the console's middle
        float r = BRACKET, k = 4f, lw = 1.2f;
        Filament.Opt o = f.o().proj().norm();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float cx = anchorX + sx * r, cy = anchorY + sy * r;
                f.lineLit(cx, cy, cx - sx * k, cy, lw, o);
                f.lineLit(cx, cy, cx, cy - sy * k, lw, o);
            }
        }
    }
}
