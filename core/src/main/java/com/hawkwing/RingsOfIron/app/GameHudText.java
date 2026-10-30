package com.hawkwing.RingsOfIron.app;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.hawkwing.RingsOfIron.game.AbandonShip;
import com.hawkwing.RingsOfIron.game.Contract;
import com.hawkwing.RingsOfIron.game.DisabledComponent;
import com.hawkwing.RingsOfIron.game.FlightWarnings;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.HelmOrderComponent;
import com.hawkwing.RingsOfIron.game.HintRegistry;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.ShipDisablement;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.text.HudText;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the HUD's text says, drawn by ui-hud's {@link HudText}: the status column (the clock, the
 * target and missiles, the notices, docking, navigation, contracts, the guides and the helm), the key
 * hints along the bottom, and the panel over the game when one is open (crash, notice log, controls).
 */
final class GameHudText {
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);

    private final GameSession session;
    private final GameWorld world;
    private final GameScenario game;
    private final ViewportManager viewports;
    private final HudText text;
    private final RenderPipeline pipeline;
    private final FurniturePlacing placing;
    private final SaveController saves;
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();
    private final Color color = new Color();

    GameHudText(GameSession session, HudText text, RenderPipeline pipeline, FurniturePlacing placing, SaveController saves) {
        this.session = session;
        this.world = session.world;
        this.game = session.game;
        this.viewports = session.viewports;
        this.text = text;
        this.pipeline = pipeline;
        this.placing = placing;
        this.saves = saves;
    }

    HudText hudText() {
        return text;
    }

    void drawStatus() {
        text.beginStatus();
        SpriteBatch batch = text.batch();
        BitmapFont font = text.font();
        float left = text.statusLeft();
        float top = text.statusTop();
        TimeWarpController warp = world.timeWarp;
        double t = world.engine.getSimSeconds();
        // Each planned burn's number and countdown, by its arrow on the outside view
        pipeline.navOverlay().drawLabels(batch, font, (x, y) -> viewports.worldToUi(x, y, tmp2));
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Day %d  %02d:%02d:%02d    Warp %dx%s", (long) (t / 86400),
                (long) (t / 3600) % 24, (long) (t / 60) % 60, (long) t % 60, warp.getWarp(),
                warp.isRailsWarp() ? " (rails)" : ""), left, top);
        Entity target = session.gun.getTarget();
        HudMarkerComponent marker = target == null ? null : MARKERS.get(target);
        font.draw(batch, "Target: " + (target == null ? "none" : marker == null ? "unknown contact" : marker.label) + (session.gun.gunsReady() ? "" : "   (gun reloading)")
                + (target != null && target == game.raider && game.raiderHostile() ? "   FIRING ON YOU" : ""), left, top - 20f);
        float reload = session.gun.missileReloadTime();
        int missiles = session.gun.missilesLeft();
        // Inbound missiles are ui-hud's missile warning.
        font.draw(batch, "Missile: " + (missiles == 0 ? "none left" : reload < 0f ? "offline"
                        : reload > 0f ? String.format("%.0f s", reload) : "ready")
                        + (missiles > 0 ? "   (" + missiles + " left)" : ""),
                left, top - 40f);
        // Key hints for where the player is, from the registry, sit to the right of the ship panel,
        // which only shows outside
        Actor shipPanel = session.hud.getMinimapPanel();
        float hintX = shipPanel.isVisible() ? shipPanel.getX() + shipPanel.getWidth() + 12f : 10f;
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, placing.isPlacing() ? placing.hint() : world.hints.line(hintContexts(), session::key), hintX, 24f);
        // Rank, sponsor and the weekly budget
        font.draw(batch, world.budget.describe(), left, top - 60f);
        // On deck there is no threat overlay: the missile warning goes up here, between the flight
        // readout and this column, wrapping rather than running under either
        if (session.deckView() && world.warnings.inbound() > 0) {
            float impact = world.warnings.soonestImpact();
            Actor flight = session.hud.getFlightPanel();
            float from = flight.localToStageCoordinates(tmp.set(flight.getWidth(), 0f)).x + 12f;
            // The same red as the banner outside
            BitmapFont largeFont = text.largeFont();
            largeFont.setColor(HudSkin.HUD_ALERT);
            largeFont.draw(batch, String.format(Locale.ROOT, "MISSILE WARNING   %d inbound%s", world.warnings.inbound(),
                    Float.isNaN(impact) ? "" : String.format(Locale.ROOT, "   impact %.1f s", impact)),
                    from, top - 30f, Math.max(left - 12f - from, 200f), Align.center, true);
            font.setColor(Color.LIGHT_GRAY);
        }
        float y = top - 80f;
        // What stays up while it lasts, then the newest notices
        DisabledComponent.Reason disabled = ShipDisablement.reason(game.player);
        if (AbandonShip.abandoning(game.player)) {
            font.setColor(Color.RED);
            font.draw(batch, "ABANDON SHIP: crew to the pods (" + session.key(Action.ABANDON) + " launches now)", left, y);
            y -= 20f;
        } else if (disabled != null) {
            font.setColor(Color.RED);
            // Wrapped to the column, as the notices are: with the tow offered it ran off the window's edge
            y -= text.wrapped("SHIP DISABLED: " + disabled.label + "   " + session.key(Action.ABANDON) + " twice abandons ship"
                    + (world.beacon.refusal() == null ? ", " + session.key(Action.CUT) + " twice calls a tow" : ""),
                    font.getColor(), left, y) + 6f;
        }
        for (NoticeLog.Notice n : world.notices.showing()) {
            if (FlightWarnings.isMissileWarning(n)) continue;
            font.setColor(noticeColor(n.kind, Math.min(1f, n.secondsLeft())));
            y -= text.wrapped(n.label(), font.getColor(), left, y) + 6f;
        }
        y -= 10f;
        font.setColor(Color.SKY);
        // Long lines wrap inside the column rather than running off the window's right edge
        float below = y;
        for (String line : session.docking.readout()) below = text.statusLine(line, left, below);
        below -= 10f;
        // Navigation: the target and closest approach, the next burn, the helm's order, the fuel
        font.setColor(Color.VIOLET);
        for (String line : world.nav.readout()) below = text.statusLine(line, left, below);
        // The contracts held: what, how long left and how far
        font.setColor(Color.GOLD);
        for (Contract c : world.contracts.active()) below = text.statusLine(world.contracts.describe(c), left, below);
        // The objective stays up under the notices, once its own notice has gone from above them
        if (session.firstPlay != null) {
            String objective = session.firstPlay.objective(session::key);
            if (!noticeShowing(objective.substring(objective.indexOf(':') + 1).trim())) {
                font.draw(batch, objective, left, below, HudText.STATUS_TEXT, Align.left, true);
                below -= 60f;
            }
        }
        if (session.opening != null && !noticeShowing(session.opening.text(session::key))) {
            below -= text.wrapped(session.opening.text(session::key), Color.GOLD, left, below) + 10f;
        }
        String bridge = bridgeLine();
        if (bridge != null) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, bridge, left, below);
            below -= 20f;
        }
        // Crew outside: a burn can throw them off, so the helm needs to know
        String eva = world.spacewalks.statusLine();
        if (eva != null) {
            font.setColor(Color.ORANGE);
            below = text.statusLine(eva, left, below);
        }
        text.endStatus(below);
    }

    /** The panel over the game, if one is up: the crash screen, else the notice log, else the controls. */
    void drawPanels() {
        if (world.getCrash() != null || world.getCareerOver() != null) drawCrash();
        else if (session.logOpen) drawLog();
        else if (session.helpOpen) drawHelp();
    }

    private void drawCrash() {
        GameWorld.Crash crash = world.getCrash();
        String over = world.getCareerOver();
        text.drawCrash(crash != null ? "SHIP LOST" : "CAREER OVER",
                crash != null ? "You crashed into " + crash.bodyName + " on day " + (long) (crash.time / 86400) + "."
                        : "You were killed, and " + over + ".",
                session.key(Action.QUICK_LOAD) + " loads the quick save   " + (saves.haveAutosave() ? "Enter loads the latest autosave   " : "")
                        + "Esc " + (session.options.hosted ? "menu" : "quits"));
    }

    /** The notice log (L): the last fifty notices, newest first, with the day and time each came. */
    private void drawLog() {
        session.logScroll = text.drawLog("Up/Down or the wheel scroll   " + session.key(Action.LOG) + " or Esc closes",
                world.notices.history(), session.logScroll, n -> {
                    long t = (long) n.time;
                    return String.format(Locale.ROOT, "Day %d %02d:%02d", t / 86400, (t / 3600) % 24, (t / 60) % 60);
                }, n -> noticeColor(n.kind, 1f), NoticeLog.Notice::label);
    }

    /** The controls panel (F1): every hint the game knows, by where it applies, with the player's keys. */
    private void drawHelp() {
        Map<String, List<String[]>> sections = new LinkedHashMap<>();
        for (Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : world.hints.byContext().entrySet()) {
            List<String[]> lines = new ArrayList<>();
            for (HintRegistry.Hint h : e.getValue()) lines.add(new String[]{h.key(session::key), h.label});
            sections.put(e.getKey().title, lines);
        }
        text.drawHelp("Keys can be rebound in Settings   " + session.key(Action.HELP) + " or Esc closes", sections);
    }

    /** True while {@code text} is among the notices on screen. */
    private boolean noticeShowing(String text) {
        for (NoticeLog.Notice n : world.notices.showing()) if (n.text.equals(text)) return true;
        return false;
    }

    /** Info orange, important green, warnings red; fading out over the last second. */
    private Color noticeColor(NoticeLog.Kind kind, float alpha) {
        switch (kind) {
            case IMPORTANT: color.set(0.55f, 1f, 0.45f, alpha); break;
            case WARNING: color.set(1f, 0.3f, 0.25f, alpha); break;
            default: color.set(Color.ORANGE).a = alpha;
        }
        return color;
    }

    /** Where the player is, for the hints. */
    private EnumSet<HintRegistry.Context> hintContexts() {
        EnumSet<HintRegistry.Context> c = EnumSet.of(HintRegistry.Context.ANY);
        boolean outside = session.hud.getViewMode().getMode() != ViewMode.INTERIOR;
        if (!outside) c.add(HintRegistry.Context.DECK);
        else if (world.bridge.playerOnGuns()) c.add(HintRegistry.Context.WEAPONS);
        else c.add(HintRegistry.Context.HELM);
        if (outside && world.bridge.playerAtHelm()) c.add(HintRegistry.Context.MAP);
        if (session.hud.getTradeWindow().isDocked()) c.add(HintRegistry.Context.DOCKED);
        if (session.hud.getBoardingWindow().isAvailable()) c.add(HintRegistry.Context.LATCHED);
        if (ShipDisablement.reason(game.player) != null) c.add(HintRegistry.Context.DISABLED);
        return c;
    }

    /** Who has the helm, when it isn't simply the player at it. */
    private String bridgeLine() {
        if (GameSession.VIEW_ANYWHERE) return null;
        String fault = world.bridge.helmFault(game.player);
        if (world.bridge.playerAtHelm()) return fault == null ? null : "Helm: " + fault;
        if (fault != null) return "Helm: " + fault + "; the ship drifts";
        // Flying an order, the helm's line in the navigation readout says what it is doing
        HelmOrderComponent order = game.player.getComponent(HelmOrderComponent.class);
        if (order != null && order.mode != HelmOrderComponent.Mode.NONE) return null;
        return world.bridge.helmsman(game.player) != null ? "Helm: the helmsman holds the heading"
                : "Helm: nobody; the ship drifts";
    }
}
