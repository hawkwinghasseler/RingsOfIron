package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.art.ExteriorArt;
import com.hawkwing.RingsOfIron.game.AbandonShip;
import com.hawkwing.RingsOfIron.game.NewGame;
import com.hawkwing.RingsOfIron.game.Bridge;
import com.hawkwing.RingsOfIron.game.OpeningGuide;
import com.hawkwing.RingsOfIron.game.BurnPlanner;
import com.hawkwing.RingsOfIron.game.Rescue;
import com.hawkwing.RingsOfIron.game.CombatTargetingProvider;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.game.FirstPlay;
import com.hawkwing.RingsOfIron.game.ShipDisablement;
import com.hawkwing.RingsOfIron.game.ShipRefit;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.hawkwing.RingsOfIron.game.DoorForcing;
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
import com.hawkwing.RingsOfIron.lighting.DeckLightRig;
import com.hawkwing.RingsOfIron.lighting.EmissiveLights;
import com.hawkwing.RingsOfIron.lighting.HullArt;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import com.hawkwing.RingsOfIron.shell.SaveSummary;
import com.ringsofiron.aicrew.CrewBody;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;

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
 * shoots down incoming missiles by itself. The raider fights back once you shoot at it.
 *
 * <p>The outside view is what you see seated at a console ({@link Bridge}): you start at the helm,
 * which flies the ship and fires it too. V stands you up into the deck view, where WASD walks your
 * character; V again beside the helm or the weapons console sits you there and looks outside. At the
 * weapons console only the guns, missiles, decoys and Tab answer, and the helm flies the ship: Bea, the
 * helmsman, holds the heading you left it on, and with nobody at the helm the ship drifts. Run with
 * {@code -Dview.anywhere=true} to switch views anywhere and fly from the deck, as before. In the deck
 * view E uses the console you stand at, and F or B
 * forces the shut door across a dock (pry or hack, or cut), and O turns the deck lighting off and
 * back on. P twice abandons ship: the crew make for the escape pods (your character walks there
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
    private static final ComponentMapper<ProjectileComponent> PROJECTILES = ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<MissileComponent> MISSILES = ComponentMapper.getFor(MissileComponent.class);
    private static final Family COMBAT_ROUNDS = Family.all(ProjectileComponent.class).get();
    private static final Family DECOYS = Family.all(DecoyComponent.class).get();
    private static final Family ARMED = Family.all(WeaponsComponent.class).get();
    private static final com.badlogic.ashley.utils.ImmutableArray<Entity> NO_ENTITIES =
            new com.badlogic.ashley.utils.ImmutableArray<>(new Array<Entity>());
    private static final float INTERIOR_VIEW = 40f;
    private static final float START_VIEW = 160f;
    private static final float STATUS_WIDTH = 420f;
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
    private GameHud gameHud;
    private UiHud hud;
    private ViewportManager viewports;
    private PlayerGunnery gun;
    private PlayerDocking docking;
    private com.hawkwing.RingsOfIron.game.NavOverlay navOverlay;
    /** Flashes for warheads and missiles shot down: {x, y, age}. */
    private final Array<float[]> explosions = new Array<>();

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    /** The art pack's atlas; empty when it was not built, and then everything is drawn in shapes. */
    private ArtLibrary art;
    private DeckArt deckArt;
    private ExteriorArt exteriorArt;
    private final Matrix4 pose = new Matrix4();
    private final Matrix4 identity = new Matrix4();
    /** Combat entities drawn with art this frame, which the shapes then skip. */
    private final Array<Entity> drawnWithArt = new Array<>();
    private BitmapFont font;
    /** Deck lights and shadows, and the glow of every lamp and screen, from the art's emissive layers. */
    private SceneLighting lighting;
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

    private final Array<Body> bodies = new Array<>();
    /** The craft whose decks the interior view draws. */
    private final Array<Entity> decks = new Array<>();
    private final Vector2 followAt = new Vector2();
    private final Vector2 tmp = new Vector2();
    private final Vector2 tmp2 = new Vector2();
    private final Color color = new Color();
    private final float[] polygon = new float[16];

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
        font = new BitmapFont();

        hud = new UiHud();
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
        game = GameScenario.start(world, assetsDir, modsDir, newGame);
        art = ArtLibrary.load(game.content.assets);
        deckArt = new DeckArt(art);
        exteriorArt = new ExteriorArt(art);
        gameHud = new GameHud(world, hud);
        lighting = new SceneLighting(EmissiveLights.load(assetsDir));
        hullArt = new HullArt(game.content.defs);

        gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.nextContact(null));
        hud.setTargeting(new CombatTargetingProvider(gun));
        docking = new PlayerDocking(world, () -> game.player, this::show);
        navOverlay = new com.hawkwing.RingsOfIron.game.NavOverlay(world, docking);
        world.nav.keyNames = this::key;
        // A click on the traffic map makes that station or ship the navigation target
        hud.getTrafficMap().setPickListener(uuid -> show(world.nav.target.select(uuid)));
        gameHud.yard.setLayoutEditor(this::openRefit);
        world.combatEvents.intercepts.add((signal, hit) -> {
            if (hit.destroyed && hit.missile != null) explosions.add(new float[]{hit.point.x, hit.point.y, 0f});
        });
        world.combatEvents.detonations.add((signal, boom) ->
                explosions.add(new float[]{boom.worldPoint.x, boom.worldPoint.y, 0f}));

        world.bridge.playerAlwaysAtHelm = VIEW_ANYWHERE;
        if (!VIEW_ANYWHERE) {
            hud.setViewKey(this::sitOrStand);
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
            // The new ship's own weapons
            gun = new PlayerGunnery(world, game.player);
            gun.setTarget(game.nextContact(null));
            hud.setTargeting(new CombatTargetingProvider(gun));
            matchViewToSeat();
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
            if (news != null) show(news, change.ship == game.player && change.now != null
                    ? NoticeLog.Kind.IMPORTANT : NoticeLog.Kind.INFO);
        });
        world.timeWarp.changes.add((signal, change) -> {
            if (change.blockedReason != null) show("Time warp refused: " + change.blockedReason);
            else if (change.reason != null) show("Dropped out of warp: " + change.reason);
        });

        world.hints.registerKey(HintRegistry.Context.ANY, "Esc", hosted ? "menu" : "quit", 90);
        Gdx.input.setInputProcessor(new InputMultiplexer(hud.getInputProcessor(), new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (logOpen) logScroll = Math.max(0, logScroll + Math.round(amountY));
                else viewports.zoom((float) Math.pow(1.15f, amountY));
                return true;
            }
        }));
        // A new career: docked at Highport with the board's first job on offer (First Play scripts the same start)
        if (firstPlayMode) firstPlay = FirstPlay.start(world, game);
        else game.beginCareer();
        if (!firstPlayMode && openingWanted) {
            opening = new OpeningGuide(world, () -> hud.getMissionWindow().isOpen(),
                    OpeningGuide.Step.OPEN_BOARD);
        }
        matchViewToSeat();
        haveAutosave = slots().latestAutosave() != null;
        show(game.content.report.hasErrors() ? "Content loaded with errors; see the log"
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
        if (!paused) {
            handleInput(delta);
            if (world.getCrash() == null) gun.update(delta);
            world.update(delta);
            String why = autosaves.due(world, game.player);
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
            drawInterior();
        } else {
            drawBodies();
        }
        gameHud.draw(delta);
        drawStatus();
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
        boolean consoleOpen = hud.getConsoleWindow().isOpen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && (logOpen || helpOpen)) {
            logOpen = false;
            helpOpen = false;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !consoleOpen && !hosted && !hudWindowOpen()) {
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
            if (just(Action.LIGHTS)) {
                lightsOn = !lightsOn;
                show(lightsOn ? "Deck lighting on" : "Deck lighting off");
            }
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
        if (held(Action.FIRE)) gun.fireGuns();
        if (just(Action.MISSILE) && !gun.launchMissile()) {
            show(gun.getTarget() == null ? "No target for the missile" : "Launcher reloading");
        }
        if (just(Action.DECOY) && gun.dropDecoy()) show("Decoy away");
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

    /** True while one of the HUD's windows or the traffic map is open; Esc closes it first. */
    private boolean hudWindowOpen() {
        return hud.getTrafficMap().isVisible() || hud.getTradeWindow().isOpen() || hud.getYardWindow().isOpen()
                || hud.getMissionWindow().isOpen() || hud.getBoardingWindow().isOpen() || hud.getConsoleWindow().isOpen();
    }

    /** True while the notice log or the controls panel is open; Esc closes it rather than pausing. */
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
        if (just(Action.NAV_TARGET)) show(nav.target.cycle(shift ? -1 : 1));
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
        font.getData().setScale(3f);
        font.setColor(Color.RED);
        font.draw(batch, crash != null ? "SHIP LOST" : "CAREER OVER", 0f, h / 2f + 40f, w, com.badlogic.gdx.utils.Align.center, false);
        font.getData().setScale(1.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, crash != null ? "You crashed into " + crash.bodyName + " on day " + (long) (crash.time / 86400) + "."
                        : "You were killed, and " + over + ".",
                0f, h / 2f - 10f, w, com.badlogic.gdx.utils.Align.center, false);
        font.getData().setScale(1f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, key(Action.QUICK_LOAD) + " loads the quick save   " + (haveAutosave ? "Enter loads the latest autosave   " : "")
                        + "Esc " + (hosted ? "menu" : "quits"), 0f, h / 2f - 50f, w, com.badlogic.gdx.utils.Align.center, false);
        batch.end();
    }

    private void warp(int direction) {
        if (!world.timeWarp.step(direction)) {
            String reason = world.engine.getRailsWarpBlockedReason();
            show("Time warp refused" + (reason == null ? "" : ": " + reason));
        }
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

    /** Replaces the game with the one saved in {@code file}. True when it loaded. */
    public boolean load(FileHandle file) {
        try {
            game.quickLoad(file);
            world.clearCrash();
            // The loaded ship's own weapons; its sensor lock comes back with the save.
            gun = new PlayerGunnery(world, game.player);
            if (gun.getTarget() == null) gun.setTarget(game.nextContact(null));
            hud.setTargeting(new CombatTargetingProvider(gun));
            matchViewToSeat();
            // A save made in First Play carries on there; any other leaves it
            firstPlay = FirstPlay.resume(world, game);
            opening = firstPlay != null ? null
                    : OpeningGuide.resume(world, () -> hud.getMissionWindow().isOpen(), openingWanted);
            autosaves.restart(world.playerStation() != null, world.engine.getSimSeconds());
            show("Game loaded");
            return true;
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Load failed", e);
            show("Load failed; see the log");
            return false;
        }
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
        world.physics.getWorld().getBodies(bodies);
        // Craft with hull art are drawn with it; everything else as its outline
        batch.setProjectionMatrix(viewports.getWorldCamera().combined);
        batch.begin();
        for (int i = bodies.size - 1; i >= 0; i--) {
            Body body = bodies.get(i);
            Object owner = body.getUserData();
            Entity e = owner instanceof Entity ? (Entity) owner : null;
            if (e != null && world.docking.bodyOf(e) == body && exteriorArt.draw(batch, e, body)) bodies.removeIndex(i);
        }
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Object owner = body.getUserData();
            Entity e = owner instanceof Entity ? (Entity) owner : null;
            HudMarkerComponent marker = e == null ? null : MARKERS.get(e);
            ShipGridComponent hull = e == null ? null : e.getComponent(ShipGridComponent.class);
            if (hull != null) {
                Vector2 at = PhysicsManager.getPosition(body, tmp2);
                lighting.addHull(hullArt.chassisOf(e), hull, at.x, at.y, body.getAngle());
            }
            if (e == game.player) shapes.setColor(Color.CYAN);
            else if (e != null && PROJECTILES.has(e)) shapes.setColor(Color.ORANGE);
            else if (marker != null) shapes.setColor(marker.color);
            else shapes.setColor(Color.LIGHT_GRAY);
            for (Fixture fixture : body.getFixtureList()) drawFixture(body, fixture.getShape());
        }
        shapes.end();
        bodies.clear();
        drawCombat(Gdx.graphics.getDeltaTime());
        // The next burn's direction, and the corridor into a docking port
        navOverlay.draw(shapes, viewports.getViewMeters() / 400f);
        // Navigation lights and the glow of running systems, a few pixels across at least
        lighting.renderGlow(batch, viewports.getWorldCamera(), 2.5f * viewports.getViewMeters() / 400f);
    }

    /**
     * Rounds as streaks along their last tick's flight, missiles as a nose line with a plume
     * while they burn, beams as lines from the gun to what they touch, decoys as small flares,
     * and a fading ring where a warhead went off or a missile was shot down. Sizes scale with
     * the zoom so they stay visible.
     */
    private void drawCombat(float delta) {
        float px = viewports.getViewMeters() / 400f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        boolean beamArt = drawCombatArt(px);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            if (drawnWithArt.contains(e, true)) continue;
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            if (m == null) {
                shapes.setColor(p.owner == game.player ? Color.YELLOW : Color.ORANGE);
                // A streak at least a few pixels long, behind the round.
                tmp.set(p.pos).sub(p.prevPos);
                float len = tmp.len();
                if (len < 3f * px) tmp.setLength(3f * px);
                shapes.line(p.pos.x - tmp.x, p.pos.y - tmp.y, p.pos.x, p.pos.y);
                continue;
            }
            float size = Math.max(m.def.hitRadius * 3f, 6f * px);
            float cos = (float) Math.cos(m.heading), sin = (float) Math.sin(m.heading);
            shapes.setColor(m.launcher == game.player ? Color.CYAN : Color.RED);
            shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x + cos * size, p.pos.y + sin * size);
            if (m.throttle > 0f) {
                shapes.setColor(Color.ORANGE);
                float plume = size * (1f + 2f * m.throttle);
                shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x - cos * plume, p.pos.y - sin * plume);
            }
        }
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(ARMED)) {
            WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
            for (WeaponMountComponent m : weapons.mounts) {
                if (!m.beamOn || beamArt) continue;
                shapes.setColor(e == game.player ? Color.SKY : Color.MAGENTA);
                shapes.line(m.muzzle.x, m.muzzle.y, m.beamEnd.x, m.beamEnd.y);
            }
        }
        shapes.setColor(Color.WHITE);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            if (drawnWithArt.contains(e, true)) continue;
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            shapes.circle(d.pos.x, d.pos.y, Math.max(1f, 3f * px), 8);
        }
        for (int i = explosions.size - 1; i >= 0; i--) {
            float[] x = explosions.get(i);
            x[2] += delta;
            if (x[2] > 1f) {
                explosions.removeIndex(i);
                continue;
            }
            if (art.region(ArtNames.explosion(0f)) != null) continue;
            shapes.setColor(1f, 0.6f, 0.2f, 1f - x[2]);
            shapes.circle(x[0], x[1], Math.max(5f, 20f * px) * (0.3f + x[2]), 20);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Rounds, missiles, decoys, beams and explosions that have art, in one batch; the rest are
     * left to the shapes. Each is drawn at least a few pixels long, as the shapes are. True when
     * the beams were drawn.
     */
    private boolean drawCombatArt(float px) {
        drawnWithArt.clear();
        batch.setProjectionMatrix(viewports.getWorldCamera().combined);
        batch.begin();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            boolean drawn;
            if (m != null) {
                drawn = exteriorArt.projectile(batch, ArtNames.projectile(
                        m.def.art, m.def.id, true), p.pos.x, p.pos.y, m.heading, Math.max(m.def.hitRadius * 6f, 12f * px));
            } else {
                drawn = p.def != null && exteriorArt.projectile(batch, ArtNames.projectile(
                        p.def.art, p.def.id, false), p.pos.x, p.pos.y, MathUtils.atan2(p.vel.y, p.vel.x), 10f * px);
            }
            if (drawn) drawnWithArt.add(e);
        }
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            if (exteriorArt.decoy(batch, d.pos.x, d.pos.y, d.vel.x, d.vel.y, 8f * px)) drawnWithArt.add(e);
        }
        boolean beams = art.region(ArtNames.BEAM) != null;
        if (beams) {
            // Beams glow: add their light to what is under them
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(ARMED)) {
                for (WeaponMountComponent m : e.getComponent(WeaponsComponent.class).mounts) {
                    if (m.beamOn) exteriorArt.beam(batch, m.muzzle.x, m.muzzle.y, m.beamEnd.x, m.beamEnd.y, Math.max(0.25f, 3f * px));
                }
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        for (float[] x : explosions) {
            exteriorArt.explosion(batch, x[0], x[1], 2f * Math.max(5f, 20f * px) * (0.3f + x[2]), x[2]);
        }
        batch.end();
        return beams;
    }

    private void drawFixture(Body body, Shape shape) {
        if (shape instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            shapes.circle(c.x, c.y, Math.max(0.5f, shape.getRadius() * PhysicsManager.METERS_PER_UNIT), 16);
        } else if (shape instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) shape;
            int n = PolygonSkin.outline(body, poly, polygon);
            shapes.polygon(polygon, 0, n * 2);
        }
    }

    /**
     * The deck plan of the craft the avatar is aboard, and of any craft docked to it, each at its
     * own pose, in the minimap's colors, with the cargo and everyone aboard.
     */
    private void drawInterior() {
        Entity aboard = world.avatar.getAvatarShip();
        if (aboard == null) aboard = game.player;
        ShipGridComponent grid = aboard.getComponent(ShipGridComponent.class);
        if (grid == null) return;
        decks.clear();
        decks.add(aboard);
        for (GridLink link : grid.links) {
            if (!decks.contains(link.otherShip, true)) decks.add(link.otherShip);
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        boolean blink = hud.getMinimap().blinkOn();
        boolean withArt = deckArt.available();
        for (Entity ship : decks) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            Body body = world.docking.bodyOf(ship);
            if (g == null || body == null) continue;
            Vector2 at = PhysicsManager.getPosition(body, tmp);
            float degrees = body.getAngle() * MathUtils.radiansToDegrees;
            CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
            if (withArt) {
                batch.setProjectionMatrix(viewports.getWorldCamera().combined);
                batch.setTransformMatrix(pose.idt().translate(at.x, at.y, 0f).rotate(0f, 0f, 1f, degrees));
                batch.begin();
                deckArt.draw(batch, g, hold);
                batch.end();
                batch.setTransformMatrix(identity);
            }
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, degrees);
            if (lightsOn) {
                lighting.addDeck(g, at.x, at.y, body.getAngle());
                float cos = MathUtils.cos(body.getAngle()), sin = MathUtils.sin(body.getAngle());
                for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship) continue;
                    Vector2 local = pos.toShipLocal(g, tmp2);
                    lighting.addCrew(at.x + cos * local.x - sin * local.y, at.y + sin * local.x + cos * local.y,
                            CrewBody.RADIUS * g.tileSize);
                }
            }
            float s = g.tileSize;
            if (withArt) {
                DeckView.atmosphere(shapes, g, blink);
                if (!deckArt.hasWalkways()) DeckView.walkways(shapes, g, color, 0.6f);
            } else {
                for (int y = 0; y < g.height; y++) {
                    for (int x = 0; x < g.width; x++) {
                        TileShade.fill(g.getTile(x, y), blink, color);
                        if (color.a <= 0f) continue;
                        shapes.setColor(color);
                        Vector2 p = g.tileToLocal(x, y);
                        shapes.rect(p.x - s / 2f + 0.03f, p.y - s / 2f + 0.03f, s - 0.06f, s - 0.06f);
                    }
                }
                DeckView.walkways(shapes, g, color, 1f);
                DeckView.walls(shapes, g, color);
                DeckView.cargo(shapes, g, hold, color);
            }
            boolean crewArt = withArt && deckArt.hasCrew();
            for (Entity e : crewArt ? NO_ENTITIES : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                if (pos.ship != ship) continue;
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                Color mark = e == game.avatar ? Color.WHITE : world.standing.factions().known(c.faction) ? DeckView.STATION_CREW : Color.GOLD;
                Vector2 local = pos.toShipLocal(g, tmp2);
                DeckView.crew(shapes, c, local.x, local.y, s, mark, color);
            }
            shapes.end();
            if (crewArt) {
                batch.setTransformMatrix(pose);
                batch.begin();
                for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship) continue;
                    CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                    ArtNames.Crew who = e == game.avatar ? ArtNames.Crew.PLAYER
                            : world.standing.factions().known(c.faction) ? ArtNames.Crew.STATION : ArtNames.Crew.SHIP;
                    Vector2 local = pos.toShipLocal(g, tmp2);
                    deckArt.crew(batch, e, who, c.load == null ? null : c.load.task.job.commodity(), local.x, local.y, s);
                }
                batch.end();
                batch.setTransformMatrix(identity);
            }
        }
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (!lightsOn) return;
        // The light map over the decks, dark when the craft aboard has no power; then the glow on top
        lighting.renderLights(viewports.getWorldCamera(), DeckLightRig.hasPower(grid));
        if (withArt) {
            // What gives off light shows lit whatever light falls on it
            batch.setProjectionMatrix(viewports.getWorldCamera().combined);
            for (Entity ship : decks) {
                ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
                Body body = world.docking.bodyOf(ship);
                if (g == null || body == null) continue;
                Vector2 at = PhysicsManager.getPosition(body, tmp);
                batch.setTransformMatrix(pose.idt().translate(at.x, at.y, 0f).rotate(0f, 0f, 1f, body.getAngle() * MathUtils.radiansToDegrees));
                batch.begin();
                deckArt.glow(batch, g);
                batch.end();
            }
            batch.setTransformMatrix(identity);
        }
        lighting.renderGlow(batch, viewports.getWorldCamera(), 0f);
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
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        // Top right: ui-hud's flight panel holds the top left.
        float left = viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH;
        float top = viewports.getUiViewport().getWorldHeight() - 10f;
        TimeWarpController warp = world.timeWarp;
        double t = world.engine.getSimSeconds();
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Day %d  %02d:%02d:%02d    Warp %dx%s", (long) (t / 86400),
                (long) (t / 3600) % 24, (long) (t / 60) % 60, (long) t % 60, warp.getWarp(),
                warp.isRailsWarp() ? " (rails)" : ""), left, top);
        Entity target = gun.getTarget();
        HudMarkerComponent marker = target == null ? null : MARKERS.get(target);
        font.draw(batch, "Target: " + (marker == null ? "none" : marker.label) + (gun.gunsReady() ? "" : "   (gun reloading)")
                + (target == game.raider && game.raiderHostile() ? "   FIRING ON YOU" : ""), left, top - 20f);
        float reload = gun.missileReloadTime();
        // Inbound missiles are ui-hud's missile warning.
        font.draw(batch, "Missile: " + (reload < 0f ? "offline" : reload > 0f ? String.format("%.0f s", reload) : "ready"),
                left, top - 40f);
        // Key hints for where the player is, from the registry, sit to the right of the ship panel,
        // which only shows outside
        Actor shipPanel = hud.getMinimapPanel();
        float hintX = shipPanel.isVisible() ? shipPanel.getX() + shipPanel.getWidth() + 12f : 10f;
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, world.hints.line(hintContexts(), this::key), hintX, 24f);
        // Rank, sponsor and the weekly budget
        font.draw(batch, world.budget.describe(), left, top - 60f);
        // On deck there is no threat overlay: the missile warning goes up here
        if (hud.getViewMode().getMode() == ViewMode.INTERIOR && world.warnings.inbound() > 0) {
            float impact = world.warnings.soonestImpact();
            font.setColor(Color.RED);
            font.getData().setScale(1.5f);
            font.draw(batch, String.format(java.util.Locale.ROOT, "MISSILE WARNING   %d inbound%s", world.warnings.inbound(),
                    Float.isNaN(impact) ? "" : String.format(java.util.Locale.ROOT, "   impact %.1f s", impact)),
                    0f, top - 30f, viewports.getUiViewport().getWorldWidth(), com.badlogic.gdx.utils.Align.center, false);
            font.getData().setScale(1f);
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
            font.draw(batch, "SHIP DISABLED: " + disabled.label + "   " + key(Action.ABANDON) + " twice abandons ship"
                    + (world.beacon.refusal() == null ? ", " + key(Action.CUT) + " twice calls a tow" : ""), left, y);
            y -= 20f;
        }
        for (NoticeLog.Notice n : world.notices.showing()) {
            font.setColor(noticeColor(n.kind, Math.min(1f, n.secondsLeft())));
            layout.setText(font, n.label(), font.getColor(), STATUS_WIDTH - 10f, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, left, y);
            y -= layout.height + 6f;
        }
        y -= 10f;
        font.setColor(Color.SKY);
        String[] dock = docking.readout();
        for (int i = 0; i < dock.length; i++) font.draw(batch, dock[i], left, y - 20f * i);
        float below = y - 20f * dock.length - 10f;
        // Navigation: the target and closest approach, the next burn, the helm's order, the fuel
        font.setColor(Color.VIOLET);
        for (String line : world.nav.readout()) {
            font.draw(batch, line, left, below);
            below -= 20f;
        }
        // The contracts held: what, how long left and how far
        font.setColor(Color.GOLD);
        for (com.hawkwing.RingsOfIron.game.Contract c : world.contracts.active()) {
            font.draw(batch, world.contracts.describe(c), left, below, STATUS_WIDTH - 10f, com.badlogic.gdx.utils.Align.left, true);
            below -= 20f;
        }
        if (firstPlay != null) {
            font.draw(batch, firstPlay.objective(this::key), left, below, STATUS_WIDTH - 10f,
                    com.badlogic.gdx.utils.Align.left, true);
            below -= 60f;
        }
        if (opening != null) {
            layout.setText(font, opening.text(this::key), Color.GOLD, STATUS_WIDTH - 10f, com.badlogic.gdx.utils.Align.left, true);
            font.draw(batch, layout, left, below);
            below -= layout.height + 10f;
        }
        String bridge = bridgeLine();
        if (bridge != null) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, bridge, left, below);
        }
        batch.end();
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
        shapes.setColor(0f, 0f, 0f, 0.8f);
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

    /** The controls panel (F1): every hint the game knows, by where it applies, with the player's keys. */
    private void drawHelp() {
        viewports.applyUi();
        float[] p = panel(900f, 640f);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Controls", p[0], p[1]);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Keys can be rebound in Settings   " + key(Action.HELP) + " or Esc closes", p[0], p[1],
                p[2], com.badlogic.gdx.utils.Align.right, false);
        // Two columns, filled top to bottom
        float colWidth = p[2] / 2f - 10f;
        float x = p[0], y = p[1] - 30f;
        for (Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : world.hints.byContext().entrySet()) {
            float need = 22f + 16f * e.getValue().size();
            if (y - need < p[3] && x == p[0]) {
                x += colWidth + 20f;
                y = p[1] - 30f;
            }
            font.setColor(Color.SKY);
            font.draw(batch, e.getKey().title, x, y);
            y -= 20f;
            for (HintRegistry.Hint h : e.getValue()) {
                font.setColor(Color.WHITE);
                font.draw(batch, h.key(this::key), x, y, 110f, com.badlogic.gdx.utils.Align.left, false);
                font.setColor(Color.LIGHT_GRAY);
                font.draw(batch, h.label, x + 115f, y);
                y -= 16f;
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
        if (refitEditor != null) refitEditor.dispose();
        gameHud.dispose();
        lighting.dispose();
        docking.dispose();
        world.dispose();
        shapes.dispose();
        batch.dispose();
        art.dispose();
        font.dispose();
    }
}
