package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.game.AbandonShip;
import com.hawkwing.RingsOfIron.game.FlightWarnings;
import com.hawkwing.RingsOfIron.game.NewGame;
import com.hawkwing.RingsOfIron.game.Bridge;
import com.hawkwing.RingsOfIron.game.OpeningGuide;
import com.hawkwing.RingsOfIron.game.BurnPlanner;
import com.hawkwing.RingsOfIron.game.Rescue;
import com.hawkwing.RingsOfIron.game.WarpNotices;
import com.hawkwing.RingsOfIron.game.CombatTargetingProvider;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.game.FirstPlay;
import com.hawkwing.RingsOfIron.game.FurnishingPlacement;
import com.hawkwing.RingsOfIron.game.ShipDisablement;
import com.hawkwing.RingsOfIron.game.ShipRefit;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.hawkwing.RingsOfIron.game.DoorForcing;
import com.hawkwing.RingsOfIron.game.ExteriorPick;
import com.hawkwing.RingsOfIron.game.FactionStanding;
import com.hawkwing.RingsOfIron.game.DisabledComponent;
import com.hawkwing.RingsOfIron.game.GameHud;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.HintRegistry;
import com.hawkwing.RingsOfIron.game.NoticeFeeds;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.PlayerGunnery;
import com.hawkwing.RingsOfIron.lighting.EmissiveLights;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.hawkwing.RingsOfIron.lighting.HullArt;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.hawkwing.RingsOfIron.render.ExteriorRenderer;
import com.hawkwing.RingsOfIron.render.HeatOverlay;
import com.hawkwing.RingsOfIron.render.HullShading;
import com.hawkwing.RingsOfIron.render.PictureInPicture;
import com.hawkwing.RingsOfIron.render.SpaceSnow;
import com.hawkwing.RingsOfIron.render.StarField;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import com.hawkwing.RingsOfIron.shell.SaveSummary;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The game: every module running together through {@link GameWorld}, starting from
 * {@link GameScenario}. Run with {@code gradlew :lwjgl3:runGame}, or {@code gradlew :lwjgl3:runFirstPlay}
 * for the scripted first play ({@link FirstPlay}): undock, fly, fight, dock for repair.
 *
 * <p>Controls, outside: W/S main engine, A/D turn, Z/C strafe, X rotation assist, Tab cycle
 * target, Space fire the guns and laser (the turrets lead the locked target themselves), R launch
 * a missile at it, F drop a decoy, comma/period time warp down/up, mouse wheel zoom. Point defense
 * shoots down incoming missiles by itself. The raider fights back once you shoot at it. A click on a
 * ship or station selects it: a ship in the bubble becomes the locked target, and either becomes the
 * navigation target. Zoomed out, the player's ship and its targets each show in a picture-in-picture
 * bubble ({@link PictureInPicture}), magnified, and a target off screen shows at the edge.
 *
 * <p>The outside view is what you see seated at a console ({@link Bridge}): you start at the helm,
 * which flies the ship and fires it too. V stands you up into the deck view, where WASD walks your
 * character; V again beside the helm or the weapons console sits you there and looks outside. At the
 * weapons console only the guns, missiles, decoys and Tab answer, and the helm flies the ship: Bea, the
 * helmsman, holds the heading you left it on, and with nobody at the helm the ship drifts. Run with
 * {@code -Dview.anywhere=true} to switch views anywhere and fly from the deck, as before. In the deck
 * view E uses the console you stand at, and F or B
 * forces the shut door across a dock (pry or hack, or cut). O turns the lighting off and back
 * on, inside and out. P twice abandons ship: the crew make for the escape pods (your character walks there
 * by itself unless you steer it), and P again launches them; you wake days later at a station in a
 * requisitioned ship. F5
 * quick-saves, F9 loads the quick save. L opens the notice log, F1 the controls. Esc quits, or opens
 * the pause menu when the game runs inside {@link com.hawkwing.RingsOfIron.shell.GameShell}.
 *
 * <p>Keys are read as {@link Action}s, so the player's bindings apply, and the hints on the bottom
 * line come from {@link GameWorld#hints}. Notices go through {@link GameWorld#notices}.
 *
 * <p>Docking: G arms the selected docking port and asks the nearest station for clearance (a
 * station that gives it reaches out with its collar and draws the ship in), or disarms it; H
 * selects the next port; U undocks. Docked at a station, T opens its market and Y its yard, where
 * Rebuild decks opens the ship editor on the ship's layout for the yard to rebuild it to (the game
 * waits meanwhile; Ctrl+S rebuilds, Esc comes back). The readout under the
 * notices shows how the port lines up with the nearest free port of its size.
 *
 * <p>Boarding: G with no station near latches the port onto a ship alongside. Latched to a disabled
 * ship, K opens the boarding window: send the boarding party across (they force the door first if
 * it is shut), and once the ship is taken, mark systems to strip and cargo to carry over. Parts
 * come home as crates in the cargo bays; stations buy them in the market, and a yard fits a salvaged
 * weapon for nothing.
 *
 * <p>Burn planner, shown on the orbit map: N plans a burn, B selects the next one, Up/Down add
 * prograde/retrograde delta-v, Left/Right radial in/out, [ and ] move it along the orbit (Shift for
 * ten times as much), Delete removes it. Burns fire on their own when due.
 *
 * <p>Navigation ({@link com.hawkwing.RingsOfIron.game.Navigation}), from either console: Q picks the
 * next navigation target (Shift+Q the one before; a click on the traffic map picks one too), ; plans
 * a transfer to it for Enter to accept or Backspace to discard, and / warps to the next burn. Helm
 * orders, flown by the helmsman while you are away from the helm: 1 fly the planned burns, 2 match
 * the target's velocity, 3 hold position on it, 4 approach it (4 again changes the distance), 0 cancel.
 *
 * <p>Every key named here is its {@link Action}'s default.
 */
public class RingsOfIronGame extends ApplicationAdapter {
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final float INTERIOR_VIEW = 40f;
    private static final float START_VIEW = 160f;
    /** The status column's panel, top right: its width, and the text's inside it once padded. */
    private static final float STATUS_WIDTH = 420f, STATUS_PAD = 8f, STATUS_TEXT = STATUS_WIDTH - 2f * STATUS_PAD;
    /** Where the status column's text ended last frame, UI y: off-screen marker names keep above or below it. */
    private float statusBottom = Float.MAX_VALUE;
    private static final float WALK_SPEED = 1f;
    /** {@code -Dview.anywhere=true}: V switches the view anywhere and the ship flies from the deck, for testing. */
    private static final boolean VIEW_ANYWHERE = Boolean.getBoolean("view.anywhere");

    private final Path assetsDir;
    private final Path modsDir;
    /** Start with the scripted first play ({@link FirstPlay}) rather than free play. */
    private final boolean firstPlayMode;
    private final NewGame newGame;
    private FirstPlay firstPlay;
    /** The light walkthrough for a new game, or null once it is done or off. */
    private OpeningGuide opening;
    /** Whether new games get the walkthrough; the settings turn it off. */
    private boolean openingWanted = true;

    private GameWorld world;
    private GameScenario game;
    /** Round 10: a fallen base's batteries greyed on the HUD. */
    private com.hawkwing.RingsOfIron.game.SilencedMarkers silencedMarkers;
    private GameHud gameHud;
    private UiHud hud;
    private ViewportManager viewports;
    private PlayerGunnery gun;
    private PlayerDocking docking;
    private com.hawkwing.RingsOfIron.game.NavOverlay navOverlay;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    /** The art pack's atlas; empty when it was not built, and then everything is drawn in shapes. */
    private ArtLibrary art;
    private DeckArt deckArt;
    private DeckRenderer deckRenderer;
    private ExteriorRenderer exterior;
    /** Speed shown against a circular orbit at the player's altitude. */
    private SpaceSnow snow;
    /** The distant stars behind it all. */
    private final StarField stars = new StarField();
    /** The HUD skin's body, banner and display fonts, rasterised for the UI scale. */
    private BitmapFont font, largeFont, displayFont;
    /** Deck lights and shadows, and the glow of every lamp and screen, from the art's emissive layers. */
    private SceneLighting lighting;
    /** Light over the hulls outside: starlight, the hulls' own lights, engines, guns and blasts. */
    private ExteriorLighting exteriorLighting;
    /** Bubbles magnifying the player's ship and its targets when the outside view shows them too small, and their own light. */
    private PictureInPicture pip;
    private ExteriorLighting pipLighting;
    private HullArt hullArt;
    private boolean lightsOn = true;
    private float exteriorViewMeters = START_VIEW;
    /** The ship editor, open for the yard to rebuild the ship's decks; the game waits while it is. */
    private ShipEditor refitEditor;
    private boolean refitDone;
    private InputProcessor gameInput;
    /** The keys the hints name; the shell gives the player's own. */
    private KeyBindings bindings = new KeyBindings();
    /** The notice log window (L): open, and how far it is scrolled back. */
    private boolean logOpen;
    private int logScroll;
    /** The controls panel (F1). */
    private boolean helpOpen;
    /** The developer console (~): Groovy against the world, acting on what was clicked. */
    private com.hawkwing.RingsOfIron.debug.DevConsole dev;
    private com.ringsofiron.uihud.widgets.DevConsoleWindow devWindow;
    /** The console has stopped the world ({@code pause()}); {@link #devStep} seconds are still to run. */
    private boolean devFrozen;
    private float devStep;
    /** The debug menu (F3): scenarios to load, overlays to toggle. */
    private com.hawkwing.RingsOfIron.debug.DebugMenu debugMenu;
    private final com.hawkwing.RingsOfIron.debug.BuiltInOverlays builtInOverlays = new com.hawkwing.RingsOfIron.debug.BuiltInOverlays();
    private final com.hawkwing.RingsOfIron.debug.OverlayView overlayView = new com.hawkwing.RingsOfIron.debug.OverlayView();
    /** The scenario this game was started as, and the one made from it; both null in a career. */
    private com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry scenarioEntry;
    private com.hawkwing.RingsOfIron.debug.DebugScenario scenario;
    /** Starts a new game with a scenario (null: a new career); the shell sets it. */
    private java.util.function.Consumer<com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry> scenarioLoader;
    /** When to autosave: docking, and every ten minutes of flight outside warp. */
    private final com.hawkwing.RingsOfIron.game.AutosaveClock autosaves = new com.hawkwing.RingsOfIron.game.AutosaveClock();
    /** Whether there is an autosave to offer when the ship is lost. */
    private boolean haveAutosave;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    /** Hosted by the game shell: Esc is its pause menu, and the game stands still while paused. */
    private boolean hosted;
    private boolean paused;
    /** Where F5 and F9 keep the quick save; the shell points it at its own saves folder. */
    private SaveSlots slots;

    /** The craft whose decks the interior view draws. */
    private final Array<Entity> decks = new Array<>();
    private final Vector2 followAt = new Vector2();
    private final Vector2 tmp = new Vector2();
    /** Furniture being put down on the deck from the yard window, or null. */
    private FurnishingPlacement placing;
    private final Vector2 tmp2 = new Vector2();
    private final Color color = new Color();

    /** Base data from {@code assets/data} under the working directory, mods from {@code mods}. */
    public RingsOfIronGame() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath());
    }

    public RingsOfIronGame(Path assetsDir, Path modsDir) {
        this(assetsDir, modsDir, false);
    }

    /** With {@code firstPlay}, the game starts docked at Highport with {@link FirstPlay}'s objectives. */
    public RingsOfIronGame(Path assetsDir, Path modsDir, boolean firstPlay) {
        this(assetsDir, modsDir, firstPlay, NewGame.designed());
    }

    /** A new career with the seed and sponsor {@code newGame} asks for; First Play always takes the designed start. */
    public RingsOfIronGame(Path assetsDir, Path modsDir, boolean firstPlay, NewGame newGame) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
        this.firstPlayMode = firstPlay;
        this.newGame = firstPlay ? NewGame.designed() : newGame;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        hud = new UiHud();
        font = hud.getSkin().getFont("default-font");
        largeFont = hud.getSkin().getFont("large-font");
        displayFont = hud.getSkin().getFont("display-font");
        viewports = hud.getViewports();
        viewports.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        viewports.setViewMeters(START_VIEW);

        GameWorld.Modules modules = new GameWorld.Modules();
        // Content loads on this thread, so the dialog cannot block: scripts wait, and run once the
        // player allows them. "Remember" answers are kept between launches.
        modules.modTrust = new com.ringsofiron.uihud.modding.ModTrustDialog(hud.getStage(), hud.getSkin(),
                Gdx.app.getPreferences("RingsOfIron"))
                .onDecision(allowed -> {
                    if (allowed && world != null && world.getContent() != null) world.getContent().runDeferredScripts();
                });
        world = new GameWorld(modules);
        scenario = scenarioEntry == null ? null : scenarioEntry.create();
        game = GameScenario.start(world, assetsDir, modsDir, scenario != null ? scenario.newGame() : newGame);
        art = ArtLibrary.load(game.content.assets);
        deckArt = new DeckArt(art);
        gameHud = new GameHud(world, hud);
        silencedMarkers = new com.hawkwing.RingsOfIron.game.SilencedMarkers(world);
        EmissiveLights emissive = EmissiveLights.load(assetsDir);
        lighting = new SceneLighting(emissive);
        exteriorLighting = new ExteriorLighting(emissive);
        deckRenderer = new DeckRenderer(() -> world, shapes, batch, deckArt, lighting, () -> game.avatar);
        hullArt = new HullArt(game.content.defs);
        exterior = new ExteriorRenderer(() -> world, shapes, batch, art, lighting, hullArt, () -> game.player)
                .setLighting(exteriorLighting);
        pipLighting = new ExteriorLighting(emissive);
        pip = new PictureInPicture(() -> world,
                new ExteriorRenderer(() -> world, shapes, batch, art, null, hullArt, () -> game.player),
                pipLighting, shapes, batch, font);
        // The HUD's names of contacts off screen stack clear of the bubbles
        for (int i = 0; i < PictureInPicture.MAX_BUBBLES; i++) {
            int bubble = i;
            com.badlogic.gdx.math.Rectangle area = new com.badlogic.gdx.math.Rectangle();
            hud.getHudRenderSystem().keepClear(() -> pip.area(bubble, area));
        }
        snow = new SpaceSnow(() -> world, () -> game.player);

        gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.firstTarget());
        hud.setTargeting(new CombatTargetingProvider(gun));
        docking = new PlayerDocking(world, () -> game.player, this::show);
        navOverlay = new com.hawkwing.RingsOfIron.game.NavOverlay(world, docking);
        world.nav.keyNames = this::key;
        // A click on the traffic map makes that station or ship the navigation target
        // Off-screen marker names by the right edge keep clear of the status column, which is drawn
        // outside the stage: as far down as it reached last frame
        hud.getHudRenderSystem().keepClear(() -> new com.badlogic.gdx.math.Rectangle(
                viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH - 10f, statusBottom - 4f, STATUS_WIDTH,
                viewports.getUiViewport().getWorldHeight() - statusBottom + 4f));
        hud.setBannerRight(() -> viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH - 10f);
        hud.getTrafficMap().setPickListener(uuid -> world.notices.replace("Navigation target", world.nav.target.select(uuid)));
        // A click on a radar contact selects it as a click on the outside view does
        hud.getRadar().setPickListener(this::selectContact);
        gameHud.yard.setLayoutEditor(this::openRefit);
        gameHud.yard.setFurnisher(this::startFurnishing);
        world.combatEvents.intercepts.add((signal, hit) -> {
            if (hit.destroyed && hit.missile != null) {
                exterior.explode(hit.point.x, hit.point.y);
                pip.explode(hit.point.x, hit.point.y);
            }
        });
        world.combatEvents.detonations.add((signal, boom) -> {
            exterior.explode(boom.worldPoint.x, boom.worldPoint.y);
            pip.explode(boom.worldPoint.x, boom.worldPoint.y);
        });

        world.bridge.playerAlwaysAtHelm = VIEW_ANYWHERE;
        if (!VIEW_ANYWHERE) {
            hud.setViewKey(this::sitOrStand);
            // E opens a console's power panel; the helm and weapons consoles are flown from their seat
            hud.getInteractPromptSystem().setPromptExtra(c -> "helm".equals(c.consoleId) || "weapons".equals(c.consoleId)
                    ? "   [" + key(Action.VIEW) + "] sit and " + ("helm".equals(c.consoleId) ? "fly" : "fight") : null);
            // Pushed off, knocked out or the console shot away: back on deck
            world.bridge.unseated.add((signal, why) -> {
                show(why);
                hud.getViewMode().setMode(ViewMode.INTERIOR);
            });
        }
        hud.getViewMode().addListener((previous, current) -> {
            if (previous == current) return;
            if (current == ViewMode.INTERIOR) {
                exteriorViewMeters = viewports.getViewMeters();
                viewports.setViewMeters(INTERIOR_VIEW);
            } else {
                viewports.setViewMeters(exteriorViewMeters);
            }
        });
        world.rescue.rescues.add((signal, rescue) -> {
            rearm();
            show(rescueNotice(rescue));
        });
        world.rescue.tows.add((signal, tow) -> {
            matchViewToSeat();
            long days = Math.round(tow.skipped / 86_400);
            show("A tug brought you in to " + tow.stationName + (days > 0 ? " " + days + " days later" : ""), NoticeLog.Kind.IMPORTANT);
        });
        world.carriedHome.add((signal, tow) ->
                show("You were badly hurt; your crew got you home to " + tow.stationName, NoticeLog.Kind.IMPORTANT));
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
                show(news);
            }
        });
        WarpNotices.attach(world);

        world.hints.registerKey(HintRegistry.Context.ANY, "Esc", hosted ? "menu" : "quit", 90);
        createDevConsole();
        Gdx.input.setInputProcessor(new InputMultiplexer(devKeys, placingKeys, hud.getInputProcessor(), placingClicks, devClicks,
                scenarioClicks, exteriorClicks, new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (logOpen) logScroll = Math.max(0, logScroll + Math.round(amountY));
                else viewports.zoom((float) Math.pow(1.15f, amountY));
                return true;
            }
        }));
        // A new career: docked at Highport with the board's first job on offer (First Play scripts the same start).
        // A scenario from the debug menu sets the world up its own way instead.
        if (scenario != null) setUpScenario();
        else if (firstPlayMode) firstPlay = FirstPlay.start(world, game);
        else game.beginCareer();
        if (scenario == null && !firstPlayMode && openingWanted) {
            opening = new OpeningGuide(world, () -> hud.getMissionWindow().isOpen(),
                    OpeningGuide.Step.OPEN_BOARD);
        }
        if (scenario == null) matchViewToSeat();
        haveAutosave = slots().latestAutosave() != null;
        show(game.content.report.hasErrors() ? "Content loaded with errors; see the log"
                : scenario != null ? "Scenario: " + scenarioEntry.name + ". F3 for the debug menu"
                : "Welcome aboard, " + world.standing.describeRank() + "." + (opening != null || firstPlay != null ? ""
                        : " " + key(Action.MISSIONS) + " opens " + game.homeName() + "'s mission board"), NoticeLog.Kind.IMPORTANT);
        if (opening != null) show(opening.text(this::key));
        if (game.content.report.hasErrors()) Gdx.app.error("RingsOfIron", game.content.report.toString());
    }

    @Override
    public void render() {
        if (refitEditor != null && !refitDone) {
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            refitEditor.render();
            return;
        }
        if (refitEditor != null) closeRefit();
        float delta = paused ? 0f : Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        // Stopped from the console, the world runs only the seconds step() asks for
        if (devFrozen && !paused) {
            delta = Math.min(devStep, delta);
            devStep -= delta;
        }
        if (!paused && (!devFrozen || delta > 0f)) {
            handleInput(delta);
            updatePlacing();
            if (world.getCrash() == null) gun.update(delta);
            world.update(delta);
            if (scenario != null) scenario.update(delta);
            silencedMarkers.update();
            // A scenario is a test bench, not a career: its autosaves would push the career's out
            String why = scenario != null ? null : autosaves.due(world, game.player);
            if (why != null) autosave(why);
        }
        world.notices.update(delta);
        abandonAsked -= delta;
        towAsked -= delta;
        if (firstPlay != null && firstPlay.update() != null) {
            String next = firstPlay.objective(this::key);
            show(next.substring(next.indexOf(':') + 1).trim(), NoticeLog.Kind.IMPORTANT);
        }
        updateOpening();

        TransformComponent pt = TRANSFORMS.get(game.player);
        Vector2 walker = hud.getViewMode().getMode() == ViewMode.INTERIOR ? avatarWorld(followAt) : null;
        if (walker != null) viewports.follow(walker.x, walker.y);
        else if (pt != null) viewports.follow(pt.x, pt.y);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewports.applyWorld();
        shapes.setProjectionMatrix(viewports.getWorldCamera().combined);
        lighting.clear(delta);
        if (hud.getViewMode().getMode() == ViewMode.INTERIOR) {
            drawInterior(delta);
        } else {
            drawBodies();
        }
        drawOverlays(delta, true);
        // The status column first, so an open HUD window (the crew roster, the yard) covers it rather
        // than having the helm's order written across its rows (round 9, real window)
        drawDevSelection();
        if (hud.getViewMode().getMode() == ViewMode.EXTERIOR) drawPictureInPicture(delta);
        drawStatus();
        gameHud.draw(delta);
        drawOverlays(delta, false);
        if (world.getCrash() != null || world.getCareerOver() != null) drawCrash();
        else if (logOpen) drawLog();
        else if (helpOpen) drawHelp();
    }

    /** The action's key went down this frame. */
    private static boolean just(Action action) {
        return Gdx.input.isKeyJustPressed(action.defaultKey);
    }

    private static boolean held(Action action) {
        return Gdx.input.isKeyPressed(action.defaultKey);
    }

    private void handleInput(float delta) {
        if (devWindow.isTyping()) {
            // Keys typed into the developer console fly nothing and fire nothing
            FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
            if (controls != null) controls.forward = controls.turn = controls.strafe = 0f;
            PlayerAvatarComponent walker = game.avatar == null ? null : game.avatar.getComponent(PlayerAvatarComponent.class);
            if (walker != null) walker.moveX = walker.moveY = 0f;
            NoticeFeeds.collect(world);
            return;
        }
        boolean consoleOpen = hud.getConsoleWindow().isOpen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && (logOpen || helpOpen)) {
            logOpen = false;
            helpOpen = false;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !consoleOpen && !hosted && !hudWindowOpen()
                && !backOut()) {
            Gdx.app.exit();
        }
        if (just(Action.LOG)) {
            logOpen = !logOpen;
            logScroll = 0;
            if (logOpen && firstPlay != null) firstPlay.logOpened();
            helpOpen = false;
        }
        if (just(Action.HELP)) {
            helpOpen = !helpOpen;
            logOpen = false;
        }
        if (just(Action.QUICK_LOAD)) quickLoad();
        if (world.getCrash() != null || world.getCareerOver() != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || just(Action.USE)) loadLatestAutosave();
            return;
        }
        if (logOpen) {
            // The log has the arrow keys while it is open
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) logScroll++;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
                logScroll = Math.max(0, logScroll - 1);
            }
        }
        if (just(Action.WARP_UP)) warp(1);
        if (just(Action.WARP_DOWN)) warp(-1);
        if (just(Action.QUICK_SAVE)) quickSave();
        if (just(Action.ABANDON)) abandonShip();

        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        PlayerAvatarComponent walker = game.avatar == null ? null : game.avatar.getComponent(PlayerAvatarComponent.class);
        if (controls != null) {
            controls.forward = 0f;
            controls.turn = 0f;
            controls.strafe = 0f;
        }
        if (walker != null) {
            walker.moveX = 0f;
            walker.moveY = 0f;
        }
        NoticeFeeds.collect(world);
        if (consoleOpen || logOpen) return;

        if (hud.getViewMode().getMode() == ViewMode.INTERIOR) {
            if (walker == null) return;
            // Force the shut door across a dock: pry or hack it as its power allows (F), or cut it (B).
            // Standing anywhere else, the same keys order the crew to.
            if (just(Action.DECOY) || just(Action.CUT)) {
                boolean cut = just(Action.CUT);
                DoorForcing.Target t = world.forcing.target(game.avatar, new DoorForcing.Target());
                // Stranded and at no door, B calls the tug, as at the helm
                if (t.craft == null && cut && world.beacon.refusal() == null) callTow();
                else if (t.craft == null) show(world.forcing.order(game.player, cut));
                else show(world.forcing.start(game.avatar, cut ? ForceMethod.CUT
                        : t.state == ForceMethod.DoorState.UNPOWERED ? ForceMethod.PRY : ForceMethod.HACK));
            }
            // Undocking is a helm order: on deck, say where to give it rather than doing nothing
            if (just(Action.UNDOCK) && world.docking.dockedTo(game.player) != null) {
                show("Take the helm to undock: " + key(Action.VIEW) + " at the helm, then " + key(Action.UNDOCK));
            }
            if (just(Action.LIGHTS)) toggleLights();
            // Up the screen is W whichever way the craft the avatar is aboard is turned
            float sx = 0f, sy = 0f;
            if (held(Action.THRUST)) sy += 1f;
            if (held(Action.REVERSE)) sy -= 1f;
            if (held(Action.TURN_LEFT)) sx -= 1f;
            if (held(Action.TURN_RIGHT)) sx += 1f;
            Entity aboard = world.avatar.getAvatarShip();
            DeckView.walk(walker, aboard == null ? null : world.docking.bodyOf(aboard), sx, sy, WALK_SPEED);
            return;
        }
        // At the weapons console only the weapons answer; the helm flies the ship
        boolean helm = world.bridge.playerAtHelm();
        if (helm && controls != null) {
            if (just(Action.ROTATION_ASSIST)) controls.rotationAssist = !controls.rotationAssist;
            if (held(Action.THRUST)) controls.forward += 1f;
            if (held(Action.REVERSE)) controls.forward -= 1f;
            if (held(Action.TURN_LEFT)) controls.turn += 1f;
            if (held(Action.TURN_RIGHT)) controls.turn -= 1f;
            if (held(Action.STRAFE_LEFT)) controls.strafe += 1f;
            if (held(Action.STRAFE_RIGHT)) controls.strafe -= 1f;
        }
        if (just(Action.LIGHTS)) toggleLights();
        if (held(Action.FIRE)) gun.fireGuns();
        if (just(Action.MISSILE)) {
            // "Launcher reloading" once per reload, not once per press
            if (gun.launchMissile()) world.notices.allowAgain(NoticeLog.RELOADING);
            else if (gun.getTarget() == null) show("No target for the missile");
            else world.notices.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO);
        }
        if (just(Action.DECOY) && gun.dropDecoy()) show("Decoy away");
        if (just(Action.SHIELDS)) show(toggleShields());
        if (just(Action.NEXT_TARGET)) gun.setTarget(game.nextContact(gun.getTarget()));
        navigationKeys();
        if (!helm) return;
        if (just(Action.ARM_PORT)) show(docking.toggleArm());
        if (just(Action.NEXT_PORT)) show(docking.nextPort());
        if (just(Action.UNDOCK)) {
            // Saved just before letting go of a station
            if (world.playerStation() != null) autosave("undocking");
            show(docking.undock());
        }
        // An open window has the arrow keys
        if (hud.openWindow() == null) planBurns();
    }

    /** Raises the player's shields, or lowers them safely into the emitters; what to tell the player. */
    private String toggleShields() {
        com.ringsofiron.combatsys.shields.ShieldCloudComponent cloud = com.ringsofiron.combatsys.shields.ShieldModel.ensure(game.player);
        if (cloud == null) return "No shield emitters aboard";
        if (!cloud.raised && world.docking.dockedTo(game.player) != null) return "Shields stay down while docked";
        com.ringsofiron.combatsys.shields.ShieldModel.raise(game.player, !cloud.raised);
        return cloud.raised ? "Raising shields" : "Lowering shields";
    }

    /** True while one of the HUD's windows or the traffic map is open; Esc closes it first. */
    private boolean hudWindowOpen() {
        return hud.getTrafficMap().isVisible() || hud.getTradeWindow().isOpen() || hud.getYardWindow().isOpen()
                || hud.getMissionWindow().isOpen() || hud.getBoardingWindow().isOpen() || hud.getConsoleWindow().isOpen()
                || isDevConsoleOpen() || isDebugMenuOpen();
    }

    // --- The developer console ---------------------------------------------------------

    private InputProcessor devKeys, devClicks;

    private void createDevConsole() {
        java.nio.file.Path history = Paths.get("saves", "console-history.txt").toAbsolutePath();
        dev = new com.hawkwing.RingsOfIron.debug.DevConsole(world, game, null, history);
        dev.setHost(new com.hawkwing.RingsOfIron.debug.DevConsole.Host() {
            @Override
            public void setFrozen(boolean frozen) {
                devFrozen = frozen;
                devStep = 0f;
            }

            @Override
            public boolean isFrozen() {
                return devFrozen;
            }

            @Override
            public void step(float seconds) {
                devStep += seconds;
            }
        });
        dev.addOverlay(HeatOverlay.NAME, HeatOverlay.DESCRIPTION,
                deckRenderer::heatShown, deckRenderer::setHeatShown);
        builtInOverlays.install(dev.overlays);
        dev.addOverlay(SpaceSnow.NAME, SpaceSnow.DESCRIPTION, snow::isShown, snow::setShown);
        dev.addOverlay(StarField.NAME, StarField.DESCRIPTION, stars::isShown, stars::setShown);
        dev.setScenarioLoader(this::loadScenario);
        debugMenu = new com.hawkwing.RingsOfIron.debug.DebugMenu(hud.getSkin(), dev,
                () -> scenarioEntry == null ? null : scenarioEntry.name);
        dev.setMenuToggle(() -> debugMenu.toggle(hud.getStage()));
        devWindow = new com.ringsofiron.uihud.widgets.DevConsoleWindow(hud.getSkin(), dev.console,
                () -> (devFrozen ? "[stopped] " : "") + com.hawkwing.RingsOfIron.debug.DebugNames.describe(dev.selected()));
        // ~ opens and closes it; with it open, Esc lets go of the selection, then closes it
        devKeys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.GRAVE && refitEditor == null) {
                    devWindow.toggle(hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE && devWindow.isOpen()) {
                    if (!dev.deselect()) devWindow.setOpen(false, hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.F3 && refitEditor == null) {
                    debugMenu.toggle(hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.F7) {
                    // Prototype: how the hulls are lit, to compare the baked art with the game's lighting
                    HullShading.mode = HullShading.mode.next();
                    show(HullShading.mode.label());
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE && debugMenu.isOpen()) {
                    debugMenu.setOpen(false, hud.getStage());
                    return true;
                }
                return false;
            }
        };
        // A click on the game (not on a window) selects what is under it, again for the next thing there
        devClicks = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (!devWindow.isOpen() || button != Input.Buttons.LEFT) return false;
                Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
                boolean deck = hud.getViewMode().getMode() == ViewMode.INTERIOR;
                List<Entity> drawn = new java.util.ArrayList<>();
                for (Entity e : decks) drawn.add(e);
                dev.click(at.x, at.y, deck, viewports.metersPerPixel(), deck ? drawn : null);
                return true;
            }
        };
    }

    /** The developer console is open over the game. */
    public boolean isDevConsoleOpen() {
        return devWindow != null && devWindow.isOpen();
    }

    /** The developer console has the keyboard: keys are text, not the player's bindings. */
    public boolean isDevConsoleTyping() {
        return devWindow != null && devWindow.isTyping();
    }

    public com.hawkwing.RingsOfIron.debug.DevConsole getDevConsole() {
        return dev;
    }

    // --- The debug menu and its scenarios ------------------------------------------------

    /** The debug menu (F3) is open over the game. */
    public boolean isDebugMenuOpen() {
        return debugMenu != null && debugMenu.isOpen();
    }

    public com.hawkwing.RingsOfIron.debug.DebugMenu getDebugMenu() {
        return debugMenu;
    }

    /** Starts the game as {@code entry} rather than a career; before {@link #create}. */
    public void setScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry entry) {
        this.scenarioEntry = entry;
    }

    /** The scenario this game is, or null in a career. */
    public com.hawkwing.RingsOfIron.debug.DebugScenario getLoadedScenario() {
        return scenario;
    }

    /** How the debug menu's scenarios load: the shell throws this game away and starts another. */
    public void setScenarioLoader(java.util.function.Consumer<com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry> loader) {
        this.scenarioLoader = loader;
    }

    /** Throws this game away for a new one set up as {@code entry}, or a new career for null. */
    public void loadScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry entry) {
        if (scenarioLoader == null) {
            show("Scenarios load from the main menu's game (gradlew :lwjgl3:runGame)");
            return;
        }
        scenarioLoader.accept(entry);
    }

    /** The player's (new) ship's own weapons, and the view for where the avatar sits on it. */
    private void rearm() {
        gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.firstTarget());
        hud.setTargeting(new CombatTargetingProvider(gun));
        matchViewToSeat();
    }

    /** The loaded scenario's use of the mouse, or null; {@link #scenarioPressed} after a press it took. */
    private com.hawkwing.RingsOfIron.debug.ScenarioPointer scenarioPointer;
    private boolean scenarioPressed;

    private void setUpScenario() {
        com.hawkwing.RingsOfIron.debug.ScenarioSetup setup = new com.hawkwing.RingsOfIron.debug.ScenarioSetup(
                scenarioEntry, world, game, assetsDir, dev, dev.overlays, dev.menuActions,
                new com.hawkwing.RingsOfIron.debug.ScenarioSetup.Screen() {
                    @Override
                    public void setDeckView(boolean deck) {
                        hud.getViewMode().setMode(deck ? ViewMode.INTERIOR : ViewMode.EXTERIOR);
                    }

                    @Override
                    public void playerShipChanged() {
                        rearm();
                    }

                    @Override
                    public void setPointer(com.hawkwing.RingsOfIron.debug.ScenarioPointer pointer) {
                        scenarioPointer = pointer;
                        scenarioPressed = false;
                    }
                });
        matchViewToSeat();
        scenario.setUp(setup);
    }

    // --- Furnishings put down on the deck from the yard window ----------------------------

    /** Starts placing a new {@code furnishingId} on the player's deck, or with null arranging what is aboard. */
    private void startFurnishing(String furnishingId) {
        Entity station = world.docking.dockedTo(game.player);
        placing = furnishingId == null ? FurnishingPlacement.arrange(world, game.player, station)
                : FurnishingPlacement.buy(world, game.player, station, world.furnishings.catalog().get(furnishingId));
        // It goes down on the deck: stand up from the console to see it
        if (hud.getViewMode().getMode() == ViewMode.EXTERIOR) sitOrStand();
    }

    private void stopPlacing() {
        placing = null;
        deckRenderer.setGhost(null, null, false);
    }

    /** Each frame: the piece in hand follows the pointer over the deck, or placing ends once the ship leaves the yard. */
    private void updatePlacing() {
        if (placing == null) return;
        if (placing.isDone() || !placing.stillDocked() || hud.getViewMode().getMode() != ViewMode.INTERIOR) {
            stopPlacing();
            return;
        }
        ShipGridComponent grid = placing.ship().getComponent(ShipGridComponent.class);
        Body body = world.docking.bodyOf(placing.ship());
        if (grid == null || body == null) {
            stopPlacing();
            return;
        }
        Vector2 at = viewports.getWorldViewport().unproject(tmp.set(Gdx.input.getX(), Gdx.input.getY()));
        float[] p = FurnishingPlacement.deckPoint(grid, body, at.x, at.y);
        placing.point(p[0], p[1]);
        deckRenderer.setGhost(placing.ship(), placing.ghost(), placing.refusal() == null);
    }

    private float turnStep() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                ? FurnishingPlacement.FINE_STEP : FurnishingPlacement.TURN_STEP;
    }

    /** While placing: Q and E turn the piece, X hands one back to the yard. Ahead of the HUD, whose E uses consoles. */
    private final InputProcessor placingKeys = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (placing == null) return false;
            switch (keycode) {
                case Input.Keys.Q:
                    placing.turn(turnStep());
                    return true;
                case Input.Keys.E:
                    placing.turn(-turnStep());
                    return true;
                case Input.Keys.X:
                case Input.Keys.FORWARD_DEL:
                    String said = placing.sell();
                    if (said != null) show(said);
                    return true;
                default:
                    return false;
            }
        }
    };

    /** While placing, clicks on the deck (not on a window): left puts down or picks up, right lets go; the wheel turns. */
    private final InputProcessor placingClicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (placing == null) return false;
            updatePlacing();
            if (placing == null) return false;
            if (button == Input.Buttons.RIGHT) {
                backOut();
                return true;
            }
            if (button != Input.Buttons.LEFT) return false;
            String said = placing.click();
            if (said != null) show(said);
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (placing == null || amountY == 0f) return false;
            placing.turn(-Math.signum(amountY) * turnStep());
            return true;
        }
    };

    /**
     * Left clicks on the outside view (not on a window, and not taken by the developer console, the
     * placing or a scenario): select the ship or station under the cursor, in the view or in a
     * picture-in-picture bubble.
     */
    private final InputProcessor exteriorClicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || hud.getViewMode().getMode() != ViewMode.EXTERIOR
                    || placing != null || isOverlayOpen() || world.getCrash() != null) return false;
            return selectAt(screenX, screenY);
        }
    };

    /**
     * Selects ({@link #selectContact}) the craft under window pixel ({@code screenX}, {@code screenY})
     * (y down, as input has it), in the view or in a picture-in-picture bubble. False when nothing is there.
     */
    private boolean selectAt(int screenX, int screenY) {
        Vector2 at = tmp;
        float mpp = pip.unproject(screenX, Gdx.graphics.getHeight() - 1 - screenY, at);
        if (mpp < 0f) {
            viewports.getWorldViewport().unproject(at.set(screenX, screenY));
            mpp = viewports.metersPerPixel();
        }
        Entity picked = new ExteriorPick(world).at(at.x, at.y, mpp, game.player);
        if (picked == null) return false;
        selectContact(picked);
        return true;
    }

    /**
     * Selects {@code picked}, a ship or station: a ship in the bubble becomes the locked target, as
     * Tab would lock it, and either becomes the navigation target.
     */
    private void selectContact(Entity picked) {
        boolean lock = game.contacts.contains(picked, true);
        if (lock) gun.setTarget(picked);
        String uuid = world.universe.uuidOf(picked);
        String nav = uuid == null ? null : world.nav.target.select(uuid);
        HudMarkerComponent marker = MARKERS.get(picked);
        String name = marker != null && marker.label != null && !marker.label.isEmpty() ? marker.label : PlayerDocking.name(picked);
        if (lock) world.notices.replace("Target locked", "Target locked: " + name);
        else if (nav != null) world.notices.replace("Navigation target", nav);
    }

    /**
     * The bubbles for the player's ship, the locked target and the navigation target, as they need
     * them, clear of the HUD's panels and the status column.
     */
    private void drawPictureInPicture(float delta) {
        if (world.getCrash() != null) return;
        float uiW = viewports.getUiViewport().getWorldWidth(), uiH = viewports.getUiViewport().getWorldHeight();
        pip.keepClear(panelRect.set(uiW - STATUS_WIDTH - 10f, statusBottom, STATUS_WIDTH + 10f, uiH - statusBottom));
        keepClear(hud.getFlightPanel().getParent());
        keepClear(hud.getMinimapPanel());
        keepClear(hud.getOrbitMap().getParent());
        pip.draw(viewports, game.player, gun.getTarget(), world.nav.target.entity(), delta);
    }

    private final com.badlogic.gdx.math.Rectangle panelRect = new com.badlogic.gdx.math.Rectangle();

    /** Keeps the bubbles off {@code panel} while it shows. */
    private void keepClear(Actor panel) {
        if (panel == null || !panel.isVisible()) return;
        Vector2 at = panel.localToStageCoordinates(tmp2.set(0f, 0f));
        pip.keepClear(panelRect.set(at.x, at.y, panel.getWidth(), panel.getHeight()));
    }

    /** Clicks on the game (not a window) for the scenario's pointer, while the developer console is closed. */
    private final InputProcessor scenarioClicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (scenarioPointer == null || isDevConsoleOpen()) return false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPressed = scenarioPointer.down(at.x, at.y, button, hud.getViewMode().getMode() == ViewMode.INTERIOR);
            return scenarioPressed;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!scenarioPressed || scenarioPointer == null) return false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPointer.dragged(at.x, at.y);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!scenarioPressed || scenarioPointer == null) return false;
            scenarioPressed = false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPointer.up(at.x, at.y, button);
            return true;
        }
    };

    /** The overlays toggled on: their world half over the world ({@code world}), else their screen half over the HUD. */
    private void drawOverlays(float delta, boolean inWorld) {
        com.hawkwing.RingsOfIron.debug.OverlayView v = overlayView;
        v.world = world;
        v.game = game;
        v.shapes = shapes;
        v.batch = batch;
        v.font = font;
        v.delta = delta;
        v.deckView = hud.getViewMode().getMode() == ViewMode.INTERIOR;
        v.metersPerPixel = viewports.metersPerPixel();
        v.decks = deckList();
        v.uiWidth = viewports.getUiViewport().getWorldWidth();
        v.uiHeight = viewports.getUiViewport().getWorldHeight();
        if (v.projection == null) v.projection = viewports::worldToUi;
        if (inWorld) {
            viewports.applyWorld();
            com.badlogic.gdx.graphics.OrthographicCamera cam = viewports.getWorldCamera();
            float w = cam.viewportWidth * cam.zoom, h = cam.viewportHeight * cam.zoom;
            v.worldBounds.set(cam.position.x - w / 2f, cam.position.y - h / 2f, w, h);
            v.worldMatrix.set(cam.combined);
            shapes.setProjectionMatrix(cam.combined);
            batch.setProjectionMatrix(cam.combined);
            dev.overlays.drawWorld(v);
        } else {
            viewports.applyUi();
            shapes.setProjectionMatrix(viewports.getUiCamera().combined);
            batch.setProjectionMatrix(viewports.getUiCamera().combined);
            dev.overlays.drawScreen(v);
        }
    }

    private final List<Entity> deckList = new java.util.ArrayList<>();

    private List<Entity> deckList() {
        deckList.clear();
        for (Entity e : decks) deckList.add(e);
        return deckList;
    }

    /** A ring round what the developer console has selected, at least a few pixels across. */
    private void drawDevSelection() {
        if (dev == null || dev.selected() == null) return;
        float r = dev.picker.locate(dev.selected(), tmp);
        if (r < 0f) return;
        float px = viewports.metersPerPixel();
        r = Math.max(r, 6f * px);
        viewports.applyWorld();
        shapes.setProjectionMatrix(viewports.getWorldCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 0.3f, 1f, devWindow.isOpen() ? 1f : 0.5f);
        if (dev.selected().isTile()) {
            ShipGridComponent g = dev.selected().grid();
            Body body = world.docking.bodyOf(dev.selected().ship);
            float s = g.tileSize * 0.5f, a = body.getAngle();
            float cos = MathUtils.cos(a), sin = MathUtils.sin(a);
            // The tile's square, turned with its ship
            for (int i = 0; i < 4; i++) {
                float x0 = i == 0 || i == 3 ? -s : s, y0 = i < 2 ? -s : s;
                float x1 = i == 2 || i == 3 ? -s : s, y1 = i == 0 || i == 3 ? -s : s;
                shapes.line(tmp.x + cos * x0 - sin * y0, tmp.y + sin * x0 + cos * y0,
                        tmp.x + cos * x1 - sin * y1, tmp.y + sin * x1 + cos * y1);
            }
        } else {
            shapes.circle(tmp.x, tmp.y, r * 1.3f, 32);
            shapes.circle(tmp.x, tmp.y, r * 1.3f + 2f * px, 32);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** True while the notice log or the controls panel is open; Esc closes it rather than pausing. */
    /**
     * Esc in play, before it pauses: a transfer plan waiting on Enter or Delete is discarded, as a
     * player backing out of the planner expects. True when Esc was used up here.
     */
    public boolean backOut() {
        if (placing != null) {
            placing.back();
            if (placing.isDone()) stopPlacing();
            return true;
        }
        if (world.nav.transfer.proposal() == null) return false;
        show(world.nav.transfer.discard());
        return true;
    }

    public boolean isOverlayOpen() {
        return logOpen || helpOpen;
    }

    /** V: stand up from the console into the deck view, or sit at the console beside you and look outside. */
    private void sitOrStand() {
        if (hud.getViewMode().getMode() == ViewMode.EXTERIOR) {
            boolean leftHelm = world.bridge.playerAtHelm();
            world.bridge.stand();
            hud.getViewMode().setMode(ViewMode.INTERIOR);
            if (!leftHelm) show("You leave the weapons console");
            else if (world.bridge.helmsman(game.player) != null) show("You leave the helm; the helmsman holds your heading");
            else show("You leave the helm; nobody has it now");
            return;
        }
        String refused = world.bridge.sit();
        if (refused != null) {
            show(refused);
            return;
        }
        hud.getViewMode().setMode(ViewMode.EXTERIOR);
        String fault = world.bridge.helmFault(game.player);
        show(world.bridge.playerAtHelm() ? (fault == null ? "You take the helm" : "You take the helm, but " + fault)
                : "You take the weapons console");
    }

    /** Outside while seated at a console, on deck otherwise. */
    private void matchViewToSeat() {
        if (VIEW_ANYWHERE) return;
        hud.getViewMode().setMode(world.bridge.seat() != null ? ViewMode.EXTERIOR : ViewMode.INTERIOR);
    }

    /** Draws one line of the status column in the font's color, wrapped to the column; returns the y below it. */
    private float statusLine(String text, float x, float y) {
        layout.setText(font, text, font.getColor(), STATUS_TEXT, com.badlogic.gdx.utils.Align.left, true);
        font.draw(batch, layout, x, y);
        return y - Math.max(20f, layout.height + 6f);
    }

    private void planBurns() {
        BurnPlanner burns = world.burns;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        double dv = shift ? 10 : 1;
        int steps = shift ? 10 : 1;
        if (just(Action.PLAN_BURN)) burns.addNode();
        if (just(Action.CUT)) {
            // Stranded, B calls the tug instead
            if (world.beacon.refusal() == null) callTow();
            else burns.selectNext();
        }
        if (just(Action.BURN_DELETE)) burns.deleteSelected();
        if (just(Action.BURN_PROGRADE)) burns.adjust(dv, 0);
        if (just(Action.BURN_RETROGRADE)) burns.adjust(-dv, 0);
        if (just(Action.BURN_RADIAL_OUT)) burns.adjust(0, dv);
        if (just(Action.BURN_RADIAL_IN)) burns.adjust(0, -dv);
        if (just(Action.BURN_LATER)) burns.shiftTime(steps);
        if (just(Action.BURN_EARLIER)) burns.shiftTime(-steps);
    }

    /** Navigation target, plan to target, warp to burn and helm orders: from either console. */
    private void navigationKeys() {
        com.hawkwing.RingsOfIron.game.Navigation nav = world.nav;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (just(Action.NAV_TARGET)) world.notices.replace("Navigation target", nav.target.cycle(shift ? -1 : 1));
        if (just(Action.PLAN_TO_TARGET)) show(nav.transfer.plan());
        if (just(Action.ACCEPT_PLAN) && nav.transfer.proposal() != null) show(nav.transfer.accept(world.burns));
        if (just(Action.DISCARD_PLAN) && nav.transfer.proposal() != null) show(nav.transfer.discard());
        if (just(Action.WARP_TO_BURN)) show(nav.warpToNextBurn());
        if (just(Action.HELM_FLY_BURNS)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.FLY_BURNS));
        if (just(Action.HELM_MATCH_VELOCITY)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.MATCH_VELOCITY));
        if (just(Action.HELM_HOLD)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.HOLD_POSITION));
        if (just(Action.HELM_APPROACH)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.APPROACH));
        if (just(Action.HELM_DOCK)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.DOCK));
        if (just(Action.AUTOPILOT)) show(nav.autopilot.toggle());
        if (just(Action.HELM_CANCEL)) show(nav.helm.give(com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.NONE));
    }

    private void drawCrash() {
        GameWorld.Crash crash = world.getCrash();
        String over = world.getCareerOver();
        viewports.applyUi();
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        float w = viewports.getUiViewport().getWorldWidth();
        float h = viewports.getUiViewport().getWorldHeight();
        displayFont.setColor(Color.RED);
        displayFont.draw(batch, crash != null ? "SHIP LOST" : "CAREER OVER", 0f, h / 2f + 40f, w, com.badlogic.gdx.utils.Align.center, false);
        largeFont.setColor(Color.WHITE);
        largeFont.draw(batch, crash != null ? "You crashed into " + crash.bodyName + " on day " + (long) (crash.time / 86400) + "."
                        : "You were killed, and " + over + ".",
                0f, h / 2f - 10f, w, com.badlogic.gdx.utils.Align.center, false);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, key(Action.QUICK_LOAD) + " loads the quick save   " + (haveAutosave ? "Enter loads the latest autosave   " : "")
                        + "Esc " + (hosted ? "menu" : "quits"), 0f, h / 2f - 50f, w, com.badlogic.gdx.utils.Align.center, false);
        batch.end();
    }

    private void warp(int direction) {
        WarpNotices.step(world, direction);
    }

    private SaveSlots slots() {
        if (slots == null) slots = new SaveSlots(Gdx.files.local("saves"));
        return slots;
    }

    private FileHandle quickSaveFile() {
        return slots().quickSave();
    }

    /** Saves to the next of the rotating autosave slots; says so only if it failed. */
    private void autosave(String why) {
        int n = slots().nextAutosave();
        String failed = save(slots().autosave(n), SaveSlots.autosaveName(n));
        if (failed != null) show("Autosave " + why + " skipped: " + failed);
        else haveAutosave = true;
        autosaves.restart(world.playerStation() != null, world.engine.getSimSeconds());
    }

    private void loadLatestAutosave() {
        SaveSummary latest = slots().latestAutosave();
        if (latest == null) show("No autosave yet");
        else load(latest.file);
    }

    private void quickSave() {
        String failed = save(quickSaveFile(), SaveSlots.QUICK_SAVE_NAME);
        show(failed == null ? "Game saved" : failed);
    }

    /**
     * Saves the game to {@code file} with a {@link SaveSummary} named {@code name} beside it for the
     * load list. Null when saved, otherwise what to tell the player.
     */
    public String save(FileHandle file, String name) {
        try {
            String refused = game.quickSave(file);
            if (refused != null) return "Can't save: " + refused;
            SaveSummary.of(game, name).writeFor(file);
            return null;
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Save failed", e);
            return "Save failed; see the log";
        }
    }

    private void quickLoad() {
        FileHandle file = quickSaveFile();
        if (!file.exists()) {
            show("No quick save yet (" + key(Action.QUICK_SAVE) + " saves)");
            return;
        }
        load(file);
    }

    /**
     * Replaces the game with the one saved in {@code file}. True when it loaded. A save that fails
     * to load leaves the game as it was, and says why; one that loads with parts left out (a removed
     * mod's ships, say) says how many, and lists them in the log.
     */
    public boolean load(FileHandle file) {
        try {
            java.util.List<String> warnings = game.quickLoad(file);
            world.clearCrash();
            rebindAfterLoad();
            for (String warning : warnings) Gdx.app.error("RingsOfIron", "Load of " + file.name() + ": " + warning);
            show(warnings.isEmpty() ? "Game loaded"
                    : "Game loaded, but " + warnings.size() + (warnings.size() == 1 ? " part" : " parts")
                    + " of the save couldn't be; see the log");
            return true;
        } catch (GameScenario.LoadFailedException e) {
            Gdx.app.error("RingsOfIron", "Load of " + file.name() + " failed", e);
            // Put back from the snapshot, the entities are new ones: point everything at them
            if (e.replaced) rebindAfterLoad();
            show(e.restored ? "Load failed (" + e.getMessage() + "); the game is as it was"
                    : "Load failed and the game couldn't be put back; load another save");
            return false;
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Load failed", e);
            show("Load failed; see the log");
            return false;
        }
    }

    /** Points the gunnery, HUD, view and guides at the game's entities, after they were replaced. */
    private void rebindAfterLoad() {
        // The loaded ship's own weapons; its sensor lock comes back with the save.
        gun = new PlayerGunnery(world, game.player);
        if (gun.getTarget() == null) gun.setTarget(game.firstTarget());
        hud.setTargeting(new CombatTargetingProvider(gun));
        matchViewToSeat();
        // A save made in First Play carries on there; any other leaves it
        firstPlay = FirstPlay.resume(world, game);
        opening = firstPlay != null ? null
                : OpeningGuide.resume(world, () -> hud.getMissionWindow().isOpen(), openingWanted);
        autosaves.restart(world.playerStation() != null, world.engine.getSimSeconds());
    }

    /** Run by {@link com.hawkwing.RingsOfIron.shell.GameShell}: Esc no longer quits, and the shell pauses the game. */
    public void setHosted(boolean hosted) {
        this.hosted = hosted;
    }

    public void setSaveSlots(SaveSlots slots) {
        this.slots = slots;
    }

    /** Whether a new game gets the opening walkthrough; off also stops one under way. */
    public void setOpeningGuide(boolean wanted) {
        openingWanted = wanted;
        if (!wanted && opening != null) {
            opening.stop();
            opening = null;
        }
    }

    /** Moves the walkthrough on, says each new step, and lets it go once its last word is said. */
    private void updateOpening() {
        if (opening == null || paused) return;
        // Leaving the station before taking a job ends it quietly
        if (world.playerStation() == null && opening.step() != OpeningGuide.Step.DONE) {
            opening.stop();
            opening = null;
            return;
        }
        if (!opening.update()) return;
        show(opening.text(this::key), NoticeLog.Kind.IMPORTANT);
        if (opening.finished()) opening = null;
    }

    /** The player's key bindings, for the hints; the defaults until the shell sets them. */
    public void setBindings(KeyBindings bindings) {
        this.bindings = bindings;
    }

    private String key(Action action) {
        return KeyBindings.keyName(bindings.keyOf(action));
    }

    /** Paused, the game draws but neither reads input nor moves on. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** True while the ship editor is open for a yard rebuild; it has the keyboard then. */
    public boolean isRefitOpen() {
        return refitEditor != null;
    }

    public UiHud getHud() {
        return hud;
    }

    public GameWorld getWorld() {
        return world;
    }

    public GameScenario getScenario() {
        return game;
    }

    /** The player's fire control: what the guns are aimed at. */
    public PlayerGunnery getGun() {
        return gun;
    }

    /** "Seed 1234" or "Designed start": which world this career is, for the pause menu. */
    public String worldDescription() {
        return game == null ? "" : game.newGame().describe();
    }

    public void showNotice(String message) {
        show(message);
    }

    /** Opens the ship editor on the player's layout, for the docked station's yard to rebuild the decks to. */
    private void openRefit() {
        ChassisDef layout = ShipRefit.layoutOf(world, game.player);
        if (layout == null) {
            show("The yard has no plans of this ship");
            return;
        }
        gameInput = Gdx.input.getInputProcessor();
        refitDone = false;
        refitEditor = new ShipEditor(assetsDir, modsDir, new ShipEditor.Refit() {
            @Override
            public ChassisDef layout() {
                return layout;
            }

            @Override
            public String apply(ChassisDef rebuilt) {
                String why = ShipRefit.apply(world, game.player, rebuilt);
                if (why == null) show("The yard rebuilt the decks");
                return why;
            }

            @Override
            public String quote(ChassisDef rebuilt) {
                return ShipRefit.quote(world, game.player, rebuilt);
            }

            @Override
            public void close() {
                refitDone = true;
            }
        });
        refitEditor.create();
    }

    /** Back from the ship editor to the game. */
    private void closeRefit() {
        refitEditor.dispose();
        refitEditor = null;
        Gdx.input.setInputProcessor(gameInput);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** Seconds to press P again to confirm abandoning ship. */
    private static final float ABANDON_CONFIRM = 3f;
    private float abandonAsked;

    private void show(String message) {
        world.notices.post(message);
    }

    private void show(String message, NoticeLog.Kind kind) {
        world.notices.post(message, kind);
    }

    private float towAsked;

    /** B while stranded, or the pause menu: the first press asks and names the fee, a second calls the tug. */
    public void callTow() {
        String refused = world.beacon.refusal();
        if (refused != null) {
            show("Can't call a tow: " + refused);
            return;
        }
        if (towAsked <= 0f) {
            towAsked = ABANDON_CONFIRM;
            show(String.format(java.util.Locale.ROOT, "Call the sponsor's tug? %s again: %,.0f cr and %.0f standing, and days pass",
                    key(Action.CUT), world.beacon.fee(), com.hawkwing.RingsOfIron.game.DistressBeacon.STANDING_COST), NoticeLog.Kind.WARNING);
            return;
        }
        towAsked = 0f;
        show(world.beacon.call(), NoticeLog.Kind.IMPORTANT);
    }

    /** True when the beacon can be called now, for the pause menu. */
    public boolean canCallTow() {
        return world.beacon.refusal() == null;
    }

    /** From the pause menu, where the menu itself is the confirmation. */
    public String callTowNow() {
        return world.beacon.call();
    }

    /** O: the lighting off or back on, inside and out. */
    private void toggleLights() {
        lightsOn = !lightsOn;
        deckRenderer.setLightsOn(lightsOn);
        exterior.setLightsOn(lightsOn);
        pip.setLightsOn(lightsOn);
        show(lightsOn ? "Lighting on" : "Lighting off");
    }

    /**
     * P: the first press asks, a second within {@link #ABANDON_CONFIRM} seconds orders the crew to
     * the escape pods, and once they are going it launches the pods with whoever is aboard.
     */
    private void abandonShip() {
        if (AbandonShip.abandoning(game.player)) {
            world.abandon.launchNow(game.player);
            show("Launching the pods");
            return;
        }
        if (abandonAsked <= 0f) {
            abandonAsked = ABANDON_CONFIRM;
            show("Abandon ship? " + key(Action.ABANDON) + " again to send the crew to the escape pods", NoticeLog.Kind.WARNING);
            return;
        }
        abandonAsked = 0f;
        String refused = world.abandon.order(game.player);
        if (refused == null) show("Abandon ship! Crew to the escape pods", NoticeLog.Kind.IMPORTANT);
        else show("Can't abandon ship: " + refused);
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

    // -----------------------------------------------------------------------
    private void drawBodies() {
        float px = viewports.getViewMeters() / 400f;
        stars.draw(shapes, viewports.getWorldCamera(), viewports.metersPerPixel());
        snow.draw(shapes, viewports.getWorldCamera(), viewports.metersPerPixel());
        exterior.drawBodies(viewports.getWorldCamera());
        exterior.drawFlight(viewports.getWorldCamera(), px, Gdx.graphics.getDeltaTime());
        // Every planned burn's direction, and the corridor into a docking port
        navOverlay.draw(shapes, px);
        // Navigation lights and the glow of running systems, a few pixels across at least
        lighting.renderGlow(batch, viewports.getWorldCamera(), 2.5f * px);
    }

    /**
     * The deck plan of the craft the avatar is aboard, and of any craft docked to it, each at its
     * own pose, with its smoke and flames, the cargo and everyone aboard. {@code delta} moves the
     * smoke and flames on; nothing moves while paused.
     */
    private void drawInterior(float delta) {
        Entity aboard = world.avatar.getAvatarShip();
        deckRenderer.draw(DeckRenderer.decksAround(aboard == null ? game.player : aboard, decks),
                viewports.getWorldCamera(), delta);
    }

    /** Where the avatar stands, world meters, or null when it is aboard nothing in the bubble. */
    private Vector2 avatarWorld(Vector2 out) {
        Entity aboard = world.avatar.getAvatarShip();
        GridPositionComponent pos = game.avatar == null ? null : game.avatar.getComponent(GridPositionComponent.class);
        ShipGridComponent g = aboard == null ? null : aboard.getComponent(ShipGridComponent.class);
        Body body = aboard == null ? null : world.docking.bodyOf(aboard);
        if (pos == null || g == null || body == null) return null;
        Vector2 local = pos.toShipLocal(g, out).scl(PhysicsManager.UNITS_PER_METER);
        Vector2 w = body.getWorldPoint(local);
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    private void drawStatus() {
        viewports.applyUi();
        // Top right: ui-hud's flight panel holds the top left. The column sits on a panel like the
        // HUD's, 10 units in from the corner, as tall as its text reached last frame.
        float panelX = viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH - 10f;
        float panelTop = viewports.getUiViewport().getWorldHeight() - 10f;
        if (statusBottom < panelTop) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.setProjectionMatrix(viewports.getUiCamera().combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(HudSkin.PANEL);
            shapes.rect(panelX, statusBottom + 4f, STATUS_WIDTH, panelTop - statusBottom - 4f);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        float left = panelX + STATUS_PAD;
        float top = panelTop - STATUS_PAD;
        TimeWarpController warp = world.timeWarp;
        double t = world.engine.getSimSeconds();
        // Each planned burn's number and countdown, by its arrow on the outside view
        navOverlay.drawLabels(batch, font, (x, y) -> viewports.worldToUi(x, y, tmp2));
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Day %d  %02d:%02d:%02d    Warp %dx%s", (long) (t / 86400),
                (long) (t / 3600) % 24, (long) (t / 60) % 60, (long) t % 60, warp.getWarp(),
                warp.isRailsWarp() ? " (rails)" : ""), left, top);
        Entity target = gun.getTarget();
        HudMarkerComponent marker = target == null ? null : MARKERS.get(target);
        font.draw(batch, "Target: " + (target == null ? "none" : marker == null ? "unknown contact" : marker.label) + (gun.gunsReady() ? "" : "   (gun reloading)")
                + (target != null && target == game.raider && game.raiderHostile() ? "   FIRING ON YOU" : ""), left, top - 20f);
        float reload = gun.missileReloadTime();
        // Inbound missiles are ui-hud's missile warning.
        font.draw(batch, "Missile: " + (reload < 0f ? "offline" : reload > 0f ? String.format("%.0f s", reload) : "ready"),
                left, top - 40f);
        // Key hints for where the player is, from the registry, sit to the right of the ship panel,
        // which only shows outside
        Actor shipPanel = hud.getMinimapPanel();
        float hintX = shipPanel.isVisible() ? shipPanel.getX() + shipPanel.getWidth() + 12f : 10f;
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, placing != null ? placing.hint() : world.hints.line(hintContexts(), this::key), hintX, 24f);
        // Rank, sponsor and the weekly budget
        font.draw(batch, world.budget.describe(), left, top - 60f);
        // On deck there is no threat overlay: the missile warning goes up here, between the flight
        // readout and this column, wrapping rather than running under either
        if (hud.getViewMode().getMode() == ViewMode.INTERIOR && world.warnings.inbound() > 0) {
            float impact = world.warnings.soonestImpact();
            Actor flight = hud.getFlightPanel();
            float from = flight.localToStageCoordinates(tmp.set(flight.getWidth(), 0f)).x + 12f;
            // The same red as the banner outside
            largeFont.setColor(HudSkin.HUD_ALERT);
            largeFont.draw(batch, String.format(java.util.Locale.ROOT, "MISSILE WARNING   %d inbound%s", world.warnings.inbound(),
                    Float.isNaN(impact) ? "" : String.format(java.util.Locale.ROOT, "   impact %.1f s", impact)),
                    from, top - 30f, Math.max(left - 12f - from, 200f), com.badlogic.gdx.utils.Align.center, true);
            font.setColor(Color.LIGHT_GRAY);
        }
        float y = top - 80f;
        // What stays up while it lasts, then the newest notices
        DisabledComponent.Reason disabled = ShipDisablement.reason(game.player);
        if (AbandonShip.abandoning(game.player)) {
            font.setColor(Color.RED);
            font.draw(batch, "ABANDON SHIP: crew to the pods (" + key(Action.ABANDON) + " launches now)", left, y);
            y -= 20f;
        } else if (disabled != null) {
            font.setColor(Color.RED);
            // Wrapped to the column, as the notices are: with the tow offered it ran off the window's edge
            layout.setText(font, "SHIP DISABLED: " + disabled.label + "   " + key(Action.ABANDON) + " twice abandons ship"
                    + (world.beacon.refusal() == null ? ", " + key(Action.CUT) + " twice calls a tow" : ""),
                    font.getColor(), STATUS_TEXT, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, left, y);
            y -= layout.height + 6f;
        }
        for (NoticeLog.Notice n : world.notices.showing()) {
            if (FlightWarnings.isMissileWarning(n)) continue;
            font.setColor(noticeColor(n.kind, Math.min(1f, n.secondsLeft())));
            layout.setText(font, n.label(), font.getColor(), STATUS_TEXT, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, left, y);
            y -= layout.height + 6f;
        }
        y -= 10f;
        font.setColor(Color.SKY);
        // Long lines wrap inside the column rather than running off the window's right edge
        float below = y;
        for (String line : docking.readout()) below = statusLine(line, left, below);
        below -= 10f;
        // Navigation: the target and closest approach, the next burn, the helm's order, the fuel
        font.setColor(Color.VIOLET);
        for (String line : world.nav.readout()) below = statusLine(line, left, below);
        // The contracts held: what, how long left and how far
        font.setColor(Color.GOLD);
        for (com.hawkwing.RingsOfIron.game.Contract c : world.contracts.active()) below = statusLine(world.contracts.describe(c), left, below);
        // The objective stays up under the notices, once its own notice has gone from above them
        if (firstPlay != null) {
            String objective = firstPlay.objective(this::key);
            if (!noticeShowing(objective.substring(objective.indexOf(':') + 1).trim())) {
                font.draw(batch, objective, left, below, STATUS_TEXT, com.badlogic.gdx.utils.Align.left, true);
                below -= 60f;
            }
        }
        if (opening != null && !noticeShowing(opening.text(this::key))) {
            layout.setText(font, opening.text(this::key), Color.GOLD, STATUS_TEXT, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, left, below);
            below -= layout.height + 10f;
        }
        String bridge = bridgeLine();
        if (bridge != null) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, bridge, left, below);
            below -= 20f;
        }
        statusBottom = below;
        batch.end();
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
        boolean outside = hud.getViewMode().getMode() != ViewMode.INTERIOR;
        if (!outside) c.add(HintRegistry.Context.DECK);
        else if (world.bridge.playerAtWeapons()) c.add(HintRegistry.Context.WEAPONS);
        else c.add(HintRegistry.Context.HELM);
        if (outside && world.bridge.playerAtHelm()) c.add(HintRegistry.Context.MAP);
        if (hud.getTradeWindow().isDocked()) c.add(HintRegistry.Context.DOCKED);
        if (hud.getBoardingWindow().isAvailable()) c.add(HintRegistry.Context.LATCHED);
        if (ShipDisablement.reason(game.player) != null) c.add(HintRegistry.Context.DISABLED);
        return c;
    }

    /** A panel in the middle of the screen, dimming what is behind it; returns its {x, top, width}. */
    private float[] panel(float width, float height) {
        float w = viewports.getUiViewport().getWorldWidth();
        float h = viewports.getUiViewport().getWorldHeight();
        width = Math.min(width, w - 40f);
        height = Math.min(height, h - 40f);
        float x = (w - width) / 2f, y = (h - height) / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(viewports.getUiCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // The HUD round it dimmed, so its titles don't read as part of the panel
        shapes.setColor(0f, 0f, 0f, 0.5f);
        shapes.rect(0f, 0f, w, h);
        // Dark enough that the HUD under it doesn't read through the text
        shapes.setColor(0f, 0f, 0f, 0.92f);
        shapes.rect(x, y, width, height);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.4f, 0.6f, 0.8f, 1f);
        shapes.rect(x, y, width, height);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        return new float[]{x + 16f, y + height - 16f, width - 32f, y + 16f};
    }

    /** The notice log (L): the last fifty notices, newest first, with the day and time each came. */
    private void drawLog() {
        viewports.applyUi();
        float[] p = panel(720f, 560f);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Notice log", p[0], p[1]);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Up/Down or the wheel scroll   " + key(Action.LOG) + " or Esc closes", p[0], p[1],
                p[2], com.badlogic.gdx.utils.Align.right, false);
        List<NoticeLog.Notice> log = world.notices.history();
        logScroll = Math.min(logScroll, Math.max(0, log.size() - 1));
        float y = p[1] - 30f;
        if (log.isEmpty()) {
            font.draw(batch, "Nothing yet.", p[0], y);
        }
        for (int i = logScroll; i < log.size() && y > p[3] + 20f; i++) {
            NoticeLog.Notice n = log.get(i);
            long t = (long) n.time;
            String when = String.format(java.util.Locale.ROOT, "Day %d %02d:%02d", t / 86400, (t / 3600) % 24, (t / 60) % 60);
            font.setColor(Color.GRAY);
            font.draw(batch, when, p[0], y);
            font.setColor(noticeColor(n.kind, 1f));
            layout.setText(font, n.label(), font.getColor(), p[2] - 110f, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, p[0] + 110f, y);
            y -= layout.height + 8f;
        }
        batch.end();
    }

    /**
     * The controls panel (F1): every hint the game knows, by where it applies, with the player's keys.
     * As many columns as fit the window, filled top to bottom; a section carries on in the next column
     * when it runs out of room, and the key column is as wide as the longest key name.
     */
    private void drawHelp() {
        viewports.applyUi();
        float[] p = panel(1240f, 720f);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Controls", p[0], p[1]);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Keys can be rebound in Settings   " + key(Action.HELP) + " or Esc closes", p[0], p[1],
                p[2], com.badlogic.gdx.utils.Align.right, false);
        Map<HintRegistry.Context, List<HintRegistry.Hint>> all = world.hints.byContext();
        // Columns enough for every line at the window's height, as many as the widest key leaves room for
        float keyWidth = 0f;
        int lines = 0;
        for (Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : all.entrySet()) {
            lines += e.getValue().size() + 2;
            for (HintRegistry.Hint h : e.getValue()) {
                layout.setText(font, h.key(this::key));
                keyWidth = Math.max(keyWidth, layout.width);
            }
        }
        keyWidth += 12f;
        float top = p[1] - 30f, rows = Math.max(1f, (top - p[3]) / 16f);
        int columns = Math.max(1, Math.min((int) Math.ceil(lines / rows), (int) (p[2] / (keyWidth + 120f))));
        float colWidth = (p[2] - 20f * (columns - 1)) / columns;
        float x = p[0], y = top;
        int column = 0;
        outer:
        for (Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : all.entrySet()) {
            // A heading with no room for a line under it starts the next column
            if (y - 36f < p[3]) {
                if (++column >= columns) break;
                x += colWidth + 20f;
                y = top;
            }
            font.setColor(Color.SKY);
            font.draw(batch, e.getKey().title, x, y);
            y -= 20f;
            for (HintRegistry.Hint h : e.getValue()) {
                if (y - 16f < p[3]) {
                    if (++column >= columns) break outer;
                    x += colWidth + 20f;
                    y = top;
                }
                font.setColor(Color.WHITE);
                font.draw(batch, h.key(this::key), x, y, keyWidth, com.badlogic.gdx.utils.Align.left, false);
                // A long description wraps under itself rather than running into the next column
                layout.setText(font, h.label, Color.LIGHT_GRAY, colWidth - keyWidth, com.badlogic.gdx.utils.Align.left, true);
                font.draw(batch, layout, x + keyWidth, y);
                y -= Math.max(16f, layout.height + 4f);
            }
            y -= 6f;
        }
        batch.end();
    }

    /** Who has the helm, when it isn't simply the player at it. */
    private String bridgeLine() {
        if (VIEW_ANYWHERE) return null;
        String fault = world.bridge.helmFault(game.player);
        if (world.bridge.playerAtHelm()) return fault == null ? null : "Helm: " + fault;
        if (fault != null) return "Helm: " + fault + "; the ship drifts";
        // Flying an order, the helm's line in the navigation readout says what it is doing
        com.hawkwing.RingsOfIron.game.HelmOrderComponent order = game.player.getComponent(com.hawkwing.RingsOfIron.game.HelmOrderComponent.class);
        if (order != null && order.mode != com.hawkwing.RingsOfIron.game.HelmOrderComponent.Mode.NONE) return null;
        return world.bridge.helmsman(game.player) != null ? "Helm: the helmsman holds the heading"
                : "Helm: nobody; the ship drifts";
    }

    @Override
    public void resize(int width, int height) {
        gameHud.resize(width, height);
        if (refitEditor != null) refitEditor.resize(width, height);
    }

    @Override
    public void dispose() {
        if (scenario != null) scenario.dispose();
        builtInOverlays.dispose();
        if (refitEditor != null) refitEditor.dispose();
        gameHud.dispose();
        lighting.dispose();
        exteriorLighting.dispose();
        exterior.dispose();
        pipLighting.dispose();
        pip.dispose();
        docking.dispose();
        world.dispose();
        shapes.dispose();
        batch.dispose();
        art.dispose();
    }
}
