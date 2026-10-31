package com.hawkwing.RingsOfIron.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.debug.DebugScenario;
import com.hawkwing.RingsOfIron.render.FireArcOverlay;
import com.hawkwing.RingsOfIron.game.FirstPlay;
import com.hawkwing.RingsOfIron.game.GameHud;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.uihud.HintRegistry;
import com.hawkwing.RingsOfIron.render.NavOverlay;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.OpeningGuide;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.Rescue;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.DisabledComponent;
import com.hawkwing.RingsOfIron.game.SilencedMarkers;
import com.hawkwing.RingsOfIron.game.WarpNotices;
import com.hawkwing.RingsOfIron.render.PictureInPicture;
import com.ringsofiron.coreengine.input.Action;
import com.badlogic.ashley.core.ComponentMapper;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.modding.ModTrustDialog;
import com.ringsofiron.uihud.text.HudText;

/**
 * Creates the game for {@link GameOptions}: the viewports and HUD, the world and its scenario, the
 * renderers, the player's fire control and docking, the HUD's and the world's listeners, the
 * developer tools and the input, then starts the career, First Play or the scenario. What it makes
 * is kept here for {@link com.hawkwing.RingsOfIron.RingsOfIronGame} to run.
 */
public final class GameBootstrap {
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final float START_VIEW = 160f;

    private final GameOptions options;
    private GameWorld world;
    private GameScenario game;
    private GameSession session;
    private SaveController saves;
    private RenderPipeline pipeline;
    private FurniturePlacing placing;
    private GameInput input;
    private DevTools dev;
    private YardRefit refit;

    public GameBootstrap(GameOptions options) {
        this.options = options;
    }

    public GameSession session() {
        return session;
    }

    public SaveController saves() {
        return saves;
    }

    public GameInput input() {
        return input;
    }

    public DevTools dev() {
        return dev;
    }

    /** Builds and starts the game; {@code resize} sizes everything to the window again, after the ship editor. */
    public GameBootstrap create(Runnable resize) {
        ShapeRenderer shapes = new ShapeRenderer();
        SpriteBatch batch = new SpriteBatch();
        UiHud hud = new UiHud();
        ViewportManager viewports = hud.getViewports();
        viewports.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        viewports.setViewMeters(START_VIEW);

        GameWorld.Modules modules = new GameWorld.Modules();
        // Content loads on this thread, so the dialog cannot block: scripts wait, and run once the
        // player allows them. "Remember" answers are kept between launches.
        modules.modTrust = new ModTrustDialog(hud.getStage(), hud.getSkin(),
                Gdx.app.getPreferences("RingsOfIron"))
                .onDecision(allowed -> {
                    if (allowed && world != null && world.getContent() != null) world.getContent().runDeferredScripts();
                });
        world = new GameWorld(modules);
        DebugScenario scenario = options.scenarioEntry == null ? null : options.scenarioEntry.create();
        game = GameScenario.start(world, options.assetsDir, options.modsDir, scenario != null ? scenario.newGame() : options.newGame);
        ArtLibrary art = ArtLibrary.load(game.content.assets);
        session = new GameSession(options, game, hud);
        saves = new SaveController(options, game, session::rebindAfterLoad);
        session.gameHud = new GameHud(world, hud);
        // The camera on the ship before the HUD places its markers, not after (flickering prograde markers)
        hud.setCameraFollow(() -> pipeline.follow());
        session.silencedMarkers = new SilencedMarkers(world.engine, world.macroSim, world.pirateBase);
        pipeline = new RenderPipeline(session, shapes, batch, art, options.assetsDir);
        refit = new YardRefit(session, resize);
        placing = new FurniturePlacing(session, pipeline.deckRenderer(), () -> input.sitOrStand());
        input = new GameInput(session, saves, pipeline, placing, START_VIEW);
        HudText text = new HudText(shapes, batch, viewports, hud.getSkin());
        pipeline.text = new GameHudText(session, text, pipeline, placing, saves);
        PictureInPicture pip = pipeline.pip();
        // The HUD's names of contacts off screen stack clear of the bubbles
        for (int i = 0; i < PictureInPicture.MAX_BUBBLES; i++) {
            int bubble = i;
            Rectangle area = new Rectangle();
            hud.getHudRenderSystem().keepClear(() -> pip.area(bubble, area));
        }
        // and hide the names under them, which a bubble lists instead
        hud.getHudRenderSystem().setNameCover(pip);
        // A name picked from the list under a marker ("[+] 3 others") is selected as a click on it would be
        hud.getHudRenderSystem().setPickListener(input::selectContact);

        session.arm();
        session.docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, session::show);
        pipeline.setNavigation(new NavOverlay(world.context, world.docking, world.nav, session.docking),
                new FireArcOverlay(world.context, world.bridge, world.docking, world.sensors, world.weaponControl));
        world.nav.keyNames = session::key;
        // Off-screen marker names by the right edge keep clear of the status column, which is drawn
        // outside the stage: as far down as it reached last frame
        hud.getHudRenderSystem().keepClear(() -> new Rectangle(
                viewports.getUiViewport().getWorldWidth() - HudText.STATUS_WIDTH - 10f, text.statusBottom() - 4f, HudText.STATUS_WIDTH,
                viewports.getUiViewport().getWorldHeight() - text.statusBottom() + 4f));
        hud.setBannerRight(() -> viewports.getUiViewport().getWorldWidth() - HudText.STATUS_WIDTH - 10f);
        // A click on the traffic map makes that station or ship the navigation target
        hud.getManeuverMap().setPickListener(uuid -> world.notices.replace("Navigation target", world.nav.target.select(uuid)));
        // A click on a radar contact selects it as a click on the outside view does
        hud.getRadar().setPickListener(input::selectContact);
        session.gameHud.yard.setLayoutEditor(refit::open);
        session.gameHud.yard.setFurnisher(placing::start);
        world.combatEvents.intercepts.add((signal, hit) -> {
            if (hit.destroyed && hit.missile != null) pipeline.explode(hit.point.x, hit.point.y);
        });
        world.combatEvents.detonations.add((signal, boom) -> pipeline.explode(boom.worldPoint.x, boom.worldPoint.y));

        listenToTheWorld(hud);

        world.hints.registerKey(HintRegistry.Context.ANY, "Esc", options.hosted ? "menu" : "quit", 90);
        dev = new DevTools(session, pipeline, scenario, refit::isOpen);
        pipeline.dev = dev;
        input.dev = dev;
        Gdx.input.setInputProcessor(new InputMultiplexer(dev.keys, placing.keys, hud.getInputProcessor(), placing.clicks,
                dev.clicks, dev.scenarioClicks, input.exteriorClicks, input.wheel));
        start(scenario);
        return this;
    }

    /** The seats, the view, and what the world tells the player: rescues, tows, ships bought and disabled. */
    private void listenToTheWorld(UiHud hud) {
        // E at a suit locker: a suit on, or hung back up
        hud.getInteractPromptSystem().setInteraction(world.captain.lockerUse());
        world.bridge.playerAlwaysAtHelm = GameSession.VIEW_ANYWHERE;
        if (!GameSession.VIEW_ANYWHERE) {
            hud.setViewKey(input::sitOrStand);
            // E opens a console's power panel; the helm, weapons console and guns are worked from their seat
            hud.getInteractPromptSystem().setPromptExtra(c -> "helm".equals(c.consoleId) ? "   [" + session.key(Action.VIEW) + "] sit and fly"
                    : "weapons".equals(c.consoleId) ? "   [" + session.key(Action.VIEW) + "] sit and fight"
                    : "turret_mount".equals(c.consoleId) ? "   [" + session.key(Action.VIEW) + "] take the gun" : null);
            // ... and their windows offer the same seat
            hud.registerConsoleExtra("weapons", (body, skin, console, data) ->
                    input.takeControlButton(body, skin, "Take fire control: every gun", SubsystemType.WEAPONS));
            hud.registerConsoleExtra("turret_mount", (body, skin, console, data) ->
                    input.takeControlButton(body, skin, "Take the gun", SubsystemType.TURRET_MOUNT));
            // Pushed off, knocked out or the console shot away: back on deck
            world.bridge.unseated.add((signal, why) -> {
                session.show(why);
                hud.getViewMode().setMode(ViewMode.INTERIOR);
            });
        }
        input.easeViewChanges();
        world.rescue.rescues.add((signal, rescue) -> {
            session.rearm();
            session.show(rescueNotice(rescue));
        });
        // A ship bought at a yard (or swapped in by flyShip): the guns, launchers and HUD on the new
        // hull, not the one traded in. GameScenario's listener has made it game.player by now.
        world.shipyard.deals.add((signal, deal) -> {
            if (deal.newShip != null) session.rearm();
        });
        world.rescue.tows.add((signal, tow) -> {
            session.matchViewToSeat();
            long days = Math.round(tow.skipped / 86_400);
            session.show("A tug brought you in to " + tow.stationName + (days > 0 ? " " + days + " days later" : ""), NoticeLog.Kind.IMPORTANT);
        });
        world.carriedHome.add((signal, tow) ->
                session.show("You were badly hurt; your crew got you home to " + tow.stationName, NoticeLog.Kind.IMPORTANT));
        world.hints.register(HintRegistry.Context.DISABLED, "twice: call a tow", 1, Action.CUT)
                .onlyWhen(() -> world.beacon.refusal() == null);
        world.disablement.changes.add((signal, change) -> {
            String news = disabledNotice(change);
            if (news == null) return;
            // The player's own ship: the newest state replaces the last, so "back in action" doesn't
            // sit above a "disabled" it has just undone
            if (change.ship == game.player) {
                world.notices.replace("Your ship is", news, change.now != null ? NoticeLog.Kind.WARNING : NoticeLog.Kind.INFO);
            } else {
                session.show(news);
            }
        });
        WarpNotices.attach(world);
    }

    /**
     * A new career: docked at Highport with the board's first job on offer (First Play scripts the
     * same start). A scenario from the debug menu sets the world up its own way instead.
     */
    private void start(DebugScenario scenario) {
        if (scenario != null) dev.setUpScenario();
        else if (options.firstPlay) session.firstPlay = FirstPlay.start(world, game);
        else game.beginCareer();
        if (scenario == null && !options.firstPlay && options.openingWanted) {
            session.opening = new OpeningGuide(world.contracts, world.guides, () -> session.hud.getMissionWindow().isOpen(),
                    OpeningGuide.Step.OPEN_BOARD);
        }
        if (scenario == null) session.matchViewToSeat();
        saves.start();
        session.show(game.content.report.hasErrors() ? "Content loaded with errors; see the log"
                : scenario != null ? "Scenario: " + options.scenarioEntry.name + ". F3 for the debug menu"
                : "Welcome aboard, " + world.standing.describeRank() + "." + (session.opening != null || session.firstPlay != null ? ""
                        : " " + session.key(Action.MISSIONS) + " opens " + game.homeName() + "'s mission board"), NoticeLog.Kind.IMPORTANT);
        if (session.opening != null) session.show(session.opening.text(session::key));
        if (game.content.report.hasErrors()) Gdx.app.error("RingsOfIron", game.content.report.toString());
    }

    /** "Rescued: you wake at Highport three days later ..." */
    private static String rescueNotice(Rescue.Result r) {
        long days = Math.round(r.skipped / 86_400);
        String crew = r.survivors.isEmpty() ? "no crew came through" : r.survivors.size() + " crew came through";
        return "Rescued: you wake at " + r.stationName + (days > 0 ? " " + days + " days later" : "")
                + " in a requisitioned ship; " + crew + (r.lost > 0 ? ", " + r.lost + " lost" : "");
    }

    /** What to tell the player about a ship disabled, surrendering, breaking up or recovering; null for nothing. */
    private String disabledNotice(ShipDisablement.Change change) {
        boolean mine = change.ship == game.player;
        DisabledComponent d = change.ship.getComponent(DisabledComponent.class);
        HudMarkerComponent marker = MARKERS.get(change.ship);
        String name = mine ? "Your ship" : d != null && d.markerLabel != null ? d.markerLabel
                : marker != null ? marker.label : null;
        if (name == null) return null;
        if (change.now == null) return name + " is back in action";
        switch (change.now) {
            case SURRENDERED: return name + " cuts thrust and surrenders";
            case WRECKED: return name + " breaks up: a wreck";
            case ABANDONED: return name + " is abandoned";
            default: return name + " is disabled: " + change.now.label;
        }
    }

    /** One frame, as {@link com.hawkwing.RingsOfIron.RingsOfIronGame#render} runs it. */
    public void render() {
        if (refit.render()) return;
        boolean paused = options.paused;
        float delta = paused ? 0f : Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        delta = dev.run(delta, paused);
        if (!paused && (!dev.isFrozen() || delta > 0f)) {
            input.handle(delta);
            placing.update();
            if (world.getCrash() == null) session.gun.update(delta);
            world.update(delta);
            dev.updateScenario(delta);
            session.silencedMarkers.update();
            // A scenario is a test bench, not a career: its autosaves would push the career's out
            if (dev.scenario() == null) saves.autosaveIfDue();
        }
        world.notices.update(delta);
        input.countDown(delta);
        session.updateGuides();
        pipeline.render(delta);
    }

    public boolean isRefitOpen() {
        return refit.isOpen();
    }

    public void resize(int width, int height) {
        session.gameHud.resize(width, height);
        refit.resize(width, height);
    }

    public void dispose() {
        dev.dispose();
        refit.dispose();
        session.gameHud.dispose();
        pipeline.disposeRenderers();
        session.docking.dispose();
        world.dispose();
        pipeline.disposeGraphics();
    }
}
