package com.ringsofiron.uihud;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.minimap.WireframeMinimap;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.systems.FlightReadoutSystem;
import com.ringsofiron.uihud.systems.HudRenderSystem;
import com.ringsofiron.uihud.systems.InteractPromptSystem;
import com.ringsofiron.uihud.systems.CameraFollowSystem;
import com.ringsofiron.uihud.systems.NavMarkerSystem;
import com.ringsofiron.uihud.systems.RadarSystem;
import com.ringsofiron.uihud.systems.WireframeSyncSystem;
import com.ringsofiron.uihud.widgets.AlertList;
import com.ringsofiron.uihud.widgets.ConsoleWindow;
import com.ringsofiron.uihud.widgets.ManeuverMap;
import com.ringsofiron.uihud.widgets.NavMarkers;
import com.ringsofiron.uihud.widgets.OrbitMap;
import com.ringsofiron.uihud.widgets.RadarScope;
import com.ringsofiron.uihud.widgets.ShipGauges;
import com.ringsofiron.uihud.widgets.ThreatOverlay;
import com.ringsofiron.uihud.widgets.CrewWindow;
import com.ringsofiron.uihud.widgets.MissionWindow;
import com.ringsofiron.uihud.widgets.ShipyardWindow;
import com.ringsofiron.uihud.widgets.TradeWindow;
import com.ringsofiron.uihud.widgets.BoardingWindow;
import com.ringsofiron.uihud.widgets.YardWindow;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.HudVariants;
import com.ringsofiron.uihud.variants.Screens;

/**
 * Entry point to the ui-hud module: builds the Scene2D stage, the HUD widgets and the ECS
 * systems that keep them in sync with the game.
 *
 * <pre>
 *   UiHud hud = new UiHud();
 *   hud.addTo(engine);                       // after physics systems
 *   hud.setPhysics(physics);                 // optional: absolute speed
 *   hud.setTargeting(...); hud.setOrbitInfo(...); hud.setOrbitPath(...); hud.setInteractor(...);
 *   inputMultiplexer.addProcessor(hud.getInputProcessor());
 *   // render(): position world camera, draw world, engine.update(dt), then hud.draw(dt)
 *   // resize(): hud.resize(w, h)
 * </pre>
 *
 * Keys handled by the HUD: E use console, Esc close console, map or market, V toggle exterior/interior view,
 * M toggle the map ({@link ManeuverMap}: orbits, burns and traffic in one; the mouse wheel zooms it while open),
 * T open the station market while docked, J the mission board (while docked, or holding a contract), I the
 * crew roster, Y the station yard (while docked at one) or, away from a yard, the crew's field refit. The shipyard opens from
 * the yard window's Ships for sale (while docked where ships are sold), or from Y with a prize in tow
 * near a station.
 *
 * <p>The HUD is drawn by the classic panels or by one of the UI overhaul variants ({@link HudVariants},
 * picked in Settings, Interface, HUD). A variant draws in its own layer from {@link #getData()}, the
 * common data layer, and hides the classic panels it replaces ({@link ClassicPart}). It may also show
 * its own screens in place of the windows, the map's panels and the game's text panels
 * ({@link ClassicWindow}): {@link #open}, {@link #close}, {@link #toggle} and {@link #isOpen} work on
 * a window whoever shows it, and the keys above open the variant's screens the same way.
 */
public class UiHud implements Disposable {
    private final ViewportManager viewports;
    private final Skin skin;
    private final Stage stage;
    private final ViewModeController viewMode = new ViewModeController();
    /** What V does instead of switching the view, when a game decides when the view may switch. */
    private Runnable viewKey;

    private final Group markerLayer = new Group();
    /** Keys the variant used this frame, which the game's own bindings leave alone ({@link #keyUsedByVariant}). */
    private final IntSet variantKeys = new IntSet();
    /** The HUD variant's layer: above the classic panels, below the windows and the map. */
    private final Group variantLayer = new Group();
    /** The variant's screens in place of the classic windows: above the windows and the map. */
    private final Group screenLayer = new Group();
    /** Who shows each window, the map's panels and the text panels: the classic HUD or the variant. */
    private final Screens screens = new Screens();
    /** The game's own actions at a console by console id, for a variant's console screen ({@link #registerConsoleAction}). */
    private final ObjectMap<String, com.badlogic.gdx.utils.Array<HudData.ConsoleAction>> consoleActions = new ObjectMap<>();
    private final java.util.EnumSet<ClassicWindow> wasOpen = java.util.EnumSet.noneOf(ClassicWindow.class);
    private final HudData data = new HudData();
    /** The variant drawing now, or null for the classic HUD, and its id. */
    private HudVariant variant;
    private String variantId = HudVariants.CLASSIC;
    /** Classic parts hidden by the variant, enforced each frame. */
    private final java.util.EnumSet<ClassicPart> hidden = java.util.EnumSet.noneOf(ClassicPart.class);
    private Table promptRowRef;
    private Table dockRowRef;
    private Label dockHintRef;
    private final WireframeMinimap minimap;
    private final Table minimapPanel;
    private final Table flightPanel;
    private final OrbitMap orbitMap;
    private final Table orbitPanel;
    private final ConsoleWindow consoleWindow;
    private final ObjectMap<String, ConsoleWindow.ContentBuilder> consoleLayouts = new ObjectMap<>();
    private final ObjectMap<String, ConsoleWindow.ContentBuilder> consoleExtras = new ObjectMap<>();
    private final ManeuverMap maneuverMap;
    private final TradeWindow tradeWindow;
    private final YardWindow yardWindow;
    private final MissionWindow missionWindow;
    private final ShipyardWindow shipyardWindow;
    private final BoardingWindow boardingWindow;
    private final CrewWindow crewWindow;
    private final ThreatOverlay threatOverlay;
    private final ShipGauges gauges;
    private final RadarScope radar;
    private final Table radarPanel;
    private final NavMarkers navMarkers;
    /** The missile warning's row and label: set between the flight panel and {@link #bannerRight}. */
    private final Table bannerRow;
    private final Label threatBanner;
    /** Where the space for the banner ends on the right, UI x; null for the window's edge. */
    private java.util.function.DoubleSupplier bannerRight;

    private final HudRenderSystem hudRenderSystem;
    private final WireframeSyncSystem wireframeSyncSystem;
    private final InteractPromptSystem interactPromptSystem;
    private final FlightReadoutSystem flightReadoutSystem;
    private final RadarSystem radarSystem;
    private final NavMarkerSystem navMarkerSystem;
    private final CameraFollowSystem cameraFollowSystem = new CameraFollowSystem();

    public UiHud() {
        this(new ViewportManager());
    }

    public UiHud(ViewportManager viewports) {
        this.viewports = viewports;
        skin = HudSkin.create();
        stage = new Stage(viewports.getUiViewport());

        markerLayer.setTouchable(Touchable.childrenOnly);
        stage.addActor(markerLayer);
        // Prograde and retrograde around the ship, with the markers in the exterior view
        navMarkers = new NavMarkers(skin);
        markerLayer.addActor(navMarkers);

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        stage.addActor(root);

        Table flight = new Table();
        flightPanel = flight;
        flight.setBackground(skin.getDrawable("panel"));
        flight.pad(8f);
        // Top left, in one column: the flight readout, the ship's gauges and a compact proximity scanner,
        // so the scanner stays out of the middle of the view and the top centre stays clear for the dock
        // and threat banners.
        gauges = new ShipGauges(skin);
        gauges.setBackground(skin.getDrawable("panel"));
        gauges.pad(8f);
        radar = new RadarScope(skin);
        radarPanel = new Table();
        radarPanel.setBackground(skin.getDrawable("panel"));
        radarPanel.pad(6f, 8f, 6f, 8f);
        radarPanel.add(new Label("RADAR", skin, "title")).left().row();
        radarPanel.add(radar);
        Table topLeft = new Table();
        topLeft.add(flight).left().top().fillX().row();
        topLeft.add(gauges).left().top().fillX().padTop(6f).row();
        topLeft.add(radarPanel).left().top().padTop(6f);
        root.add(topLeft).top().left().pad(10f).expand().colspan(2).row();

        minimap = new WireframeMinimap(skin.getRegion("white"));
        AlertList alerts = new AlertList(skin);
        minimapPanel = new Table();
        minimapPanel.setBackground(skin.getDrawable("panel"));
        minimapPanel.pad(8f);
        minimapPanel.add(new Label("SHIP", skin, "title")).left().colspan(2).row();
        minimapPanel.add(minimap).left().bottom();
        minimapPanel.add(alerts).top().left().padLeft(10f).minWidth(150f);
        root.add(minimapPanel).bottom().left().pad(10f);

        // Bottom right: the orbit map, with the burn planner's readout under it.
        orbitMap = new OrbitMap(skin.getRegion("white"));
        orbitMap.setFont(skin.getFont("default-font"));
        Label orbitCaption = new Label("No orbit", skin, "dim");
        orbitMap.setCaption(orbitCaption);
        orbitPanel = new Table();
        orbitPanel.setBackground(skin.getDrawable("panel"));
        orbitPanel.pad(8f);
        orbitPanel.add(new Label("ORBIT", skin, "title")).left().row();
        orbitPanel.add(orbitMap).row();
        orbitPanel.add(orbitCaption).left().width(orbitMap.getPrefWidth());
        orbitCaption.setWrap(true);
        // Raised clear of the game's key hint line, which runs right of the ship panel. In a layer of
        // its own: with the burn planner's lines under it the panel grows tall, and in the root table
        // that pushed the top-left panels off the top of a 1280x800 window.
        Table orbitLayer = new Table();
        orbitLayer.setFillParent(true);
        orbitLayer.setTouchable(Touchable.childrenOnly);
        orbitLayer.bottom().right();
        orbitLayer.add(orbitPanel).pad(10f).padBottom(36f);
        stage.addActor(orbitLayer);

        Label prompt = new Label("", skin, "prompt");
        Table promptRow = new Table();
        promptRow.setFillParent(true);
        promptRow.setTouchable(Touchable.disabled);
        promptRow.bottom().padBottom(70f);
        promptRow.add(prompt);
        stage.addActor(promptRow);

        // Missile paths and point-defense hits over the world, with a warning banner at the top.
        Label threatBanner = new Label("", skin, "banner");
        Table bannerRow = new Table();
        bannerRow.setFillParent(true);
        bannerRow.setTouchable(Touchable.disabled);
        bannerRow.top().left().padTop(60f);
        bannerRow.add(threatBanner);
        threatBanner.setAlignment(com.badlogic.gdx.utils.Align.center);
        threatBanner.setWrap(true);
        this.bannerRow = bannerRow;
        this.threatBanner = threatBanner;
        threatOverlay = new ThreatOverlay(viewports, skin, threatBanner);
        stage.addActor(threatOverlay);
        stage.addActor(bannerRow);

        // Top center, under the clock: shown while a station is in trading range.
        Label dockHint = new Label("", skin, "prompt");
        Table dockRow = new Table();
        dockRow.setFillParent(true);
        dockRow.setTouchable(Touchable.disabled);
        dockRow.top().padTop(100f);
        dockRow.add(dockHint);
        stage.addActor(dockRow);
        dockRowRef = dockRow;
        dockHintRef = dockHint;
        variantLayer.setTouchable(Touchable.childrenOnly);
        stage.addActor(variantLayer);
        tradeWindow = new TradeWindow(skin, dockHint);
        yardWindow = new YardWindow(skin);
        missionWindow = new MissionWindow(skin);
        crewWindow = new CrewWindow(skin);
        shipyardWindow = new ShipyardWindow(skin);
        tradeWindow.setHintSuffix(() -> (yardWindow.isAvailable() && !yardWindow.isField() && !yardWindow.isOpen() ? "   [Y] Yard" : "")
                + (!missionWindow.getBoard().stationName.isEmpty() && !missionWindow.isOpen() ? "   [J] Missions" : "")
                + ((!yardWindow.isAvailable() || yardWindow.isField()) && shipyardWindow.isAvailable() && !shipyardWindow.isOpen()
                        ? "   [Y] Shipyard" : ""));
        // Ships for sale is a yard service: the yard window's button opens the shipyard window
        yardWindow.setShipyard(() -> {
            if (shipyardWindow.setOpen(true)) yardWindow.setOpen(false);
        }, () -> !shipyardWindow.getYard().stationName.isEmpty());
        boardingWindow = new BoardingWindow(skin);

        maneuverMap = new ManeuverMap(skin);
        maneuverMap.setFillParent(true);
        stage.addActor(maneuverMap);

        consoleWindow = new ConsoleWindow(skin, () -> interactPromptSystem().setPromptsEnabled(true));
        stage.addActor(consoleWindow);
        stage.addActor(tradeWindow);
        stage.addActor(yardWindow);
        stage.addActor(missionWindow);
        stage.addActor(shipyardWindow);
        stage.addActor(boardingWindow);
        stage.addActor(crewWindow);
        screenLayer.setTouchable(Touchable.childrenOnly);
        stage.addActor(screenLayer);
        registerWindows();

        hudRenderSystem = new HudRenderSystem(viewports, markerLayer, skin);
        hudRenderSystem.keepClear(threatBanner, () -> threatOverlay.isVisible() && threatBanner.getText().length() > 0);
        hudRenderSystem.keepClear(dockHint, () -> dockHint.isVisible() && dockRow.isVisible());
        // Names by the bottom edge rise clear of the ship and orbit panels rather than writing over them,
        // and names by the left edge clear the flight readout, the gauges and the radar
        hudRenderSystem.keepClear(() -> stageBounds(minimapPanel));
        hudRenderSystem.keepClear(() -> stageBounds(orbitPanel));
        hudRenderSystem.keepClear(() -> stageBounds(flightPanel));
        hudRenderSystem.keepClear(() -> stageBounds(gauges));
        hudRenderSystem.keepClear(() -> stageBounds(radarPanel));
        wireframeSyncSystem = new WireframeSyncSystem(minimap, alerts);
        interactPromptSystem = new InteractPromptSystem(prompt, this::openConsole);
        flightReadoutSystem = new FlightReadoutSystem(flight, skin, viewMode);
        radarSystem = new RadarSystem(radar);
        navMarkerSystem = new NavMarkerSystem(viewports, navMarkers);

        promptRowRef = promptRow;
        viewMode.addListener((previous, current) -> applyViewMode(current));

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKey(keycode);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (!maneuverMap.isVisible()) return false;
                maneuverMap.zoomAt(event.getStageX(), event.getStageY(), (float) Math.pow(1.25f, -amountY));
                return true;
            }
        });
    }

    private InteractPromptSystem interactPromptSystem() {
        return interactPromptSystem;
    }

    /** Shows the classic panels the view {@code mode} shows. */
    private void applyViewMode(ViewMode mode) {
        boolean exterior = mode == ViewMode.EXTERIOR;
        markerLayer.setVisible(exterior);
        // In the interior view the full deck plan is on screen, so the minimap is redundant.
        minimapPanel.setVisible(exterior);
        orbitPanel.setVisible(exterior);
        threatOverlay.setVisible(exterior);
        radarPanel.setVisible(exterior);
        // Seated and looking outside there is no deck to walk: the console prompt only confused
        promptRowRef.setVisible(!exterior);
        applyHidden();
    }

    /** Handles HUD keys. Public so games that route input themselves can forward keys. */
    public boolean handleKey(int keycode) {
        if (handleClassicKey(keycode)) return true;
        // Keys the HUD did not use go to the variant, unless a classic window or the map has the
        // keys; the variant's own screens, the map's panels among them, have them first
        boolean used = variant != null && openWindow() == null && (!isMapOpen() || screens.replaced(ClassicWindow.MAP))
                && variant.keyDown(keycode);
        if (used) variantKeys.add(keycode);
        return used;
    }

    /** A window's key (T, J, K, I): closes it if it is open, else opens it if it can; false when it can't, so the key goes on. */
    private boolean keyToggles(ClassicWindow window) {
        if (!isOpen(window) && !available(window)) return false;
        toggle(window);
        return true;
    }

    /**
     * Whether the HUD variant used {@code keycode} this frame ({@link HudVariant#keyDown}), or Esc
     * closed a window: the game reads its bindings by polling, so it asks here and leaves a key the
     * HUD took alone (Alt with a digit picks an MFD page, not a helm order; Esc that closed the map
     * neither pauses nor quits).
     */
    public boolean keyUsedByVariant(int keycode) {
        return variantKeys.contains(keycode);
    }

    private boolean handleClassicKey(int keycode) {
        // The open window has the arrow keys and Enter, for the keyboard and the pad
        com.badlogic.gdx.scenes.scene2d.Group window = openWindow();
        if (window != focusWindow) {
            focusNav.clear();
            focusWindow = window;
        }
        if (window != null && focusNav.key(window, keycode)) return true;
        switch (keycode) {
            case Input.Keys.E:
                if (isOpen(ClassicWindow.CONSOLE)) return false;
                return interactPromptSystem.interact();
            case Input.Keys.ESCAPE:
                // The topmost window, whoever shows it; the game leaves an Esc used here alone
                if (!screens.back()) return false;
                variantKeys.add(keycode);
                return true;
            case Input.Keys.T:
                return keyToggles(ClassicWindow.TRADE);
            case Input.Keys.Y:
                // The station yard; the shipyard window is one of its services, and has the key to
                // itself where there is no yard, as when handing over a prize off a station
                if (isOpen(ClassicWindow.SHIPYARD)) {
                    close(ClassicWindow.SHIPYARD);
                    return true;
                }
                if (isOpen(ClassicWindow.YARD)) {
                    close(ClassicWindow.YARD);
                    return true;
                }
                if (yardWindow.isAvailable() && !yardWindow.isField()) return open(ClassicWindow.YARD);
                // No yard: a prize to hand over first, else the crew's own field refit
                if (shipyardWindow.isAvailable() && open(ClassicWindow.SHIPYARD)) return true;
                return yardWindow.isAvailable() && open(ClassicWindow.YARD);
            case Input.Keys.J:
                return keyToggles(ClassicWindow.MISSIONS);
            case Input.Keys.K:
                return keyToggles(ClassicWindow.BOARDING);
            case Input.Keys.I:
                return keyToggles(ClassicWindow.CREW);
            case Input.Keys.M:
                return toggle(ClassicWindow.MAP);
            case Input.Keys.V:
                if (viewKey != null) viewKey.run();
                else viewMode.toggle();
                return true;
            default:
                return false;
        }
    }

    private final FocusNav focusNav = new FocusNav();
    private com.badlogic.gdx.scenes.scene2d.Group focusWindow;

    /**
     * The classic window that has the keys, or null: the console, market, yard, mission board or
     * boarding window. A variant's own screens are not among them: see {@link #windowHasKeys}.
     */
    public com.badlogic.gdx.scenes.scene2d.Group openWindow() {
        if (consoleWindow.isOpen()) return consoleWindow;
        if (tradeWindow.isOpen()) return tradeWindow;
        if (yardWindow.isOpen()) return yardWindow;
        if (missionWindow.isOpen()) return missionWindow;
        if (boardingWindow.isOpen()) return boardingWindow;
        return null;
    }

    private void openConsole(Entity console, InteractableConsoleComponent data) {
        interactPromptSystem.setPromptsEnabled(false);
        if (screens.replaced(ClassicWindow.CONSOLE)) {
            // The variant's console screen reads the console from the data layer
            this.data.console.set(console, data, consoleActions.get(data.consoleId));
            if (!screens.open(ClassicWindow.CONSOLE)) consoleClosed();
            return;
        }
        consoleWindow.open(console, data, consoleLayouts.get(data.consoleId), consoleExtras.get(data.consoleId));
    }

    /** The console screen is shut: the prompts come back, as when the classic window closes. */
    private void consoleClosed() {
        data.console.clear();
        interactPromptSystem.setPromptsEnabled(true);
    }

    /**
     * What a variant's console screen offers at consoles with this id beside the system's switch, as
     * {@link #registerConsoleExtra} adds a button to the classic window ("Take fire control" at the
     * weapons console): its words, the {@code Action} name of its key, and what it does. Read into
     * {@code HudData#console} when the console opens.
     */
    public void registerConsoleAction(String consoleId, String label, String action, Runnable run) {
        com.badlogic.gdx.utils.Array<HudData.ConsoleAction> list = consoleActions.get(consoleId);
        if (list == null) consoleActions.put(consoleId, list = new com.badlogic.gdx.utils.Array<>());
        list.add(new HudData.ConsoleAction(label, action, run));
    }

    // --- Windows, whoever shows them ---------------------------------------------------------

    /** The classic HUD's own windows and the map, as the router sees them. The game adds its text panels ({@link #registerWindow}). */
    private void registerWindows() {
        screens.register(ClassicWindow.MAP, new Screens.Classic() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean isOpen() {
                return maneuverMap.isVisible();
            }

            @Override
            public boolean setOpen(boolean open) {
                showMap(open);
                return open;
            }
        });
        screens.register(ClassicWindow.CONSOLE, new Screens.Classic() {
            @Override
            public boolean available() {
                return consoleWindow.isOpen() || data.console.isOpen();
            }

            @Override
            public boolean isOpen() {
                return consoleWindow.isOpen();
            }

            @Override
            public boolean setOpen(boolean open) {
                // A console opens from E at it (openConsole): only closing works from here
                if (!open) consoleWindow.close();
                return consoleWindow.isOpen();
            }
        });
        screens.register(ClassicWindow.TRADE, classic(tradeWindow::isDocked, tradeWindow::isOpen, tradeWindow::setOpen));
        screens.register(ClassicWindow.YARD, classic(yardWindow::isAvailable, yardWindow::isOpen, yardWindow::setOpen));
        screens.register(ClassicWindow.SHIPYARD, classic(shipyardWindow::isAvailable, shipyardWindow::isOpen, shipyardWindow::setOpen));
        screens.register(ClassicWindow.MISSIONS, classic(missionWindow::isAvailable, missionWindow::isOpen, missionWindow::setOpen));
        screens.register(ClassicWindow.BOARDING, classic(boardingWindow::isAvailable, boardingWindow::isOpen, boardingWindow::setOpen));
        screens.register(ClassicWindow.CREW, classic(crewWindow::isAvailable, crewWindow::isOpen, crewWindow::setOpen));
    }

    private static Screens.Classic classic(java.util.function.BooleanSupplier available, java.util.function.BooleanSupplier isOpen,
                                           java.util.function.Predicate<Boolean> setOpen) {
        return new Screens.Classic() {
            @Override
            public boolean available() {
                return available.getAsBoolean();
            }

            @Override
            public boolean isOpen() {
                return isOpen.getAsBoolean();
            }

            @Override
            public boolean setOpen(boolean open) {
                return setOpen.test(open);
            }
        };
    }

    /**
     * What the game does for a window it draws itself: the notice log, the controls panel and the
     * ship-lost screen ({@link ClassicWindow#LOG}, {@link ClassicWindow#HELP},
     * {@link ClassicWindow#GAME_OVER}). With it registered, their keys and Esc go through
     * {@link #toggle} and {@link #close}, and a variant that replaces one shows its screen instead.
     */
    public void registerWindow(ClassicWindow window, Screens.Classic classic) {
        screens.register(window, classic);
    }

    /** Whether the variant drawing now shows its own screen for {@code window}; the game then leaves it to the variant. */
    public boolean replaces(ClassicWindow window) {
        return screens.replaced(window);
    }

    /** Whether {@code window} can open now: the market while docked, boarding while latched, a console while one is in use. */
    public boolean available(ClassicWindow window) {
        if (window == ClassicWindow.CONSOLE && screens.replaced(window)) return data.console.isOpen();
        return screens.available(window);
    }

    /** Whether {@code window} is open, the classic window or the variant's screen for it. */
    public boolean isOpen(ClassicWindow window) {
        return screens.isOpen(window);
    }

    /** Opens {@code window} if it can open now, the classic window or the variant's screen for it; whether it is open. */
    public boolean open(ClassicWindow window) {
        return screens.open(window);
    }

    /** Closes {@code window}, whoever shows it. */
    public void close(ClassicWindow window) {
        screens.close(window);
        if (window == ClassicWindow.CONSOLE && screens.replaced(window) && !screens.isOpen(window) && data.console.isOpen()) consoleClosed();
    }

    /** Its key: closes {@code window} if it is open, else opens it if it can; true when it did either. */
    public boolean toggle(ClassicWindow window) {
        return screens.toggle(window);
    }

    /**
     * Whether Esc would close something here: the map, a window, the notice log or the controls,
     * whoever shows it. The game asks before pausing or quitting on Esc. Not the ship-lost screen.
     */
    public boolean windowOpen() {
        return screens.anyOpen();
    }

    /**
     * Whether a window has the arrow keys and Enter (and the pad, which sends them): a classic window,
     * or any screen of the variant's but the map's panels. The game leaves those keys alone then.
     */
    public boolean windowHasKeys() {
        if (openWindow() != null) return true;
        if (variant == null) return false;
        for (ClassicWindow w : ClassicWindow.values()) {
            if (w != ClassicWindow.MAP && w != ClassicWindow.GAME_OVER && screens.replaced(w) && screens.isOpen(w)) return true;
        }
        return false;
    }

    /**
     * Makes V run {@code action} instead of switching the view, for a game where the view only
     * switches when the player sits down at a console or stands up from it; null switches it again.
     */
    public void setViewKey(Runnable action) {
        viewKey = action;
    }

    /** Adds rows under the window of consoles with this id (e.g. "weapons"), whatever their layout. */
    public void registerConsoleExtra(String consoleId, ConsoleWindow.ContentBuilder builder) {
        consoleExtras.put(consoleId, builder);
    }

    /** Registers a custom window layout for consoles with this id (e.g. "helm"). */
    public void registerConsoleLayout(String consoleId, ConsoleWindow.ContentBuilder builder) {
        consoleLayouts.put(consoleId, builder);
    }

    /** Adds the HUD systems to an engine. Their priorities run them after physics and game logic. */
    public void addTo(Engine engine) {
        engine.addSystem(cameraFollowSystem);
        engine.addSystem(hudRenderSystem);
        engine.addSystem(wireframeSyncSystem);
        engine.addSystem(interactPromptSystem);
        engine.addSystem(flightReadoutSystem);
        engine.addSystem(radarSystem);
        engine.addSystem(navMarkerSystem);
    }

    public void removeFrom(Engine engine) {
        engine.removeSystem(cameraFollowSystem);
        engine.removeSystem(hudRenderSystem);
        engine.removeSystem(wireframeSyncSystem);
        engine.removeSystem(interactPromptSystem);
        engine.removeSystem(flightReadoutSystem);
        engine.removeSystem(radarSystem);
        engine.removeSystem(navMarkerSystem);
    }

    /**
     * Centers the world camera; run in the frame pass before the HUD systems place anything on
     * screen, so they project with the camera this frame is drawn with. See {@link CameraFollowSystem}.
     */
    public void setCameraFollow(Runnable follow) {
        cameraFollowSystem.setFollow(follow);
    }

    public void setPhysics(PhysicsManager physics) {
        data.physics = physics;
        flightReadoutSystem.setPhysics(physics);
        navMarkerSystem.setPhysics(physics);
    }

    public void setTargeting(TargetingProvider targeting) {
        data.targeting = targeting;
        hudRenderSystem.setTargeting(targeting);
        flightReadoutSystem.setTargeting(targeting);
        radarSystem.setTargeting(targeting);
        navMarkerSystem.setTargeting(targeting);
    }

    /** Feeds the propellant, structure, power and oxygen gauges; null leaves them reading "--". */
    public void setShipStatus(ShipStatusProvider status) {
        data.ship = status;
        gauges.setProvider(status);
    }

    public ShipGauges getGauges() {
        return gauges;
    }

    public RadarScope getRadar() {
        return radar;
    }

    public NavMarkers getNavMarkers() {
        return navMarkers;
    }

    public void setOrbitInfo(OrbitInfoProvider orbitInfo) {
        data.orbitInfo = orbitInfo;
        flightReadoutSystem.setOrbitInfo(orbitInfo);
    }

    /** Feeds the orbit map, small and full screen; null leaves them empty. */
    public void setOrbitPath(OrbitPathProvider orbitPath) {
        data.orbitPath = orbitPath;
        orbitMap.setProvider(orbitPath);
        maneuverMap.setProvider(orbitPath);
    }

    /** What the full-screen orbit map's mouse planning and buttons do; null leaves them doing nothing. */
    public void setManeuverPlanner(ManeuverPlannerProvider planner) {
        data.planner = planner;
        maneuverMap.setPlanner(planner);
    }

    public ManeuverMap getManeuverMap() {
        return maneuverMap;
    }

    public void setInteractor(InteractorProvider interactor) {
        data.interactor = interactor;
        interactPromptSystem.setInteractor(interactor);
        wireframeSyncSystem.setInteractor(interactor);
    }

    /** Feeds the missile overlay and warning banner; null clears them. */
    public void setThreats(ThreatProvider threats) {
        data.threats = threats;
        threatOverlay.setProvider(threats);
    }

    /** Marks a point-defense hit on a missile; call from combat-sys's intercept event. */
    public void flashIntercept(float worldX, float worldY, boolean destroyed) {
        threatOverlay.flashIntercept(worldX, worldY, destroyed);
    }

    public ThreatOverlay getThreatOverlay() {
        return threatOverlay;
    }

    /** Feeds the station market window ([T] while docked at a station); null hides it. */
    public void setTrade(TradeProvider trade) {
        data.trade = trade;
        tradeWindow.setProvider(trade);
    }

    public TradeWindow getTradeWindow() {
        return tradeWindow;
    }

    /** Feeds the station yard window ([Y] while docked where a yard can work on the ship); null hides it. */
    public void setYard(YardProvider yard) {
        data.yard = yard;
        yardWindow.setProvider(yard);
    }

    /** Feeds the boarding and salvage window ([K] while latched to another ship); null hides it. */
    public void setBoarding(BoardingProvider boarding) {
        data.boarding = boarding;
        boardingWindow.setProvider(boarding);
    }

    public BoardingWindow getBoardingWindow() {
        return boardingWindow;
    }

    public YardWindow getYardWindow() {
        return yardWindow;
    }

    /** Feeds the mission board ([J] while docked at a station, or holding a contract); null hides it. */
    public void setMissions(MissionProvider missions) {
        data.missions = missions;
        missionWindow.setProvider(missions);
    }

    public MissionWindow getMissionWindow() {
        return missionWindow;
    }

    /** Feeds the crew roster ([I] whenever the player has a ship); null hides it. */
    public void setCrew(CrewProvider crew) {
        data.crew = crew;
        crewWindow.setProvider(crew);
    }

    public CrewWindow getCrewWindow() {
        return crewWindow;
    }

    /** Feeds the shipyard window (the yard window's Ships for sale, or [Y] with a prize to hand over and no yard); null hides it. */
    public void setShipyard(ShipyardProvider shipyard) {
        data.shipyard = shipyard;
        shipyardWindow.setProvider(shipyard);
    }

    public ShipyardWindow getShipyardWindow() {
        return shipyardWindow;
    }

    /** Off-bubble traffic for the map (M), drawn over the orbits. Without one the map shows only orbits. */
    public void setTrafficMap(TrafficMapProvider provider) {
        data.trafficMap = provider;
        maneuverMap.setTraffic(provider);
    }

    /** Opens the map (orbits, burns and traffic), or closes it: the game's canvas, with the variant's panels round it when it shows them. */
    public void setMapVisible(boolean visible) {
        if (visible) open(ClassicWindow.MAP);
        else close(ClassicWindow.MAP);
    }

    /** The map's canvas (and its own panel, unless the variant shows the panels) up or down. */
    private void showMap(boolean visible) {
        if (visible) {
            maneuverMap.getTraffic().refresh();
            maneuverMap.refresh();
            maneuverMap.toFront();
        }
        maneuverMap.setVisible(visible);
    }

    public boolean isMapOpen() {
        return maneuverMap.isVisible();
    }

    /** Briefly highlights a tile on the minimap, e.g. from combat-sys's hull-hit event. */
    public void flashHullHit(int tileX, int tileY) {
        minimap.flashTile(tileX, tileY);
        data.hullHit(tileX, tileY);
    }

    /**
     * Keeps the missile warning left of {@code right} (UI x), as of a status column the game draws
     * top right: centred in the space between that and the flight panel, wrapping when it is too
     * narrow, rather than written across either.
     */
    public void setBannerRight(java.util.function.DoubleSupplier right) {
        bannerRight = right;
    }

    private void placeBanner() {
        float left = flightPanel.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(flightPanel.getWidth(), 0f)).x + 12f;
        float right = (bannerRight == null ? viewports.getUiWidth() : (float) bannerRight.getAsDouble()) - 12f;
        float width = Math.max(200f, right - left);
        com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cell = bannerRow.getCell(threatBanner);
        if (cell.getPrefWidthValue() == null || Math.abs(cell.getMinWidth() - width) > 0.5f || bannerRow.getPadLeft() != left) {
            cell.width(width);
            bannerRow.padLeft(left);
            bannerRow.invalidate();
        }
    }

    /** Acts and draws the stage. Call after the world is drawn and the engine has updated. */
    public void draw(float delta) {
        viewports.applyUi();
        placeBanner();
        syncVariant();
        data.age(delta);
        if (variant != null) {
            data.promptAction = interactPromptSystem.getAction();
            data.dockHint = dockHintRef.isVisible() ? dockHintRef.getText().toString() : "";
            data.refresh();
            screens.update();
            variant.update(delta);
            // The variant closed its console screen itself: the prompts come back
            if (data.console.isOpen() && !screens.isOpen(ClassicWindow.CONSOLE)) consoleClosed();
            // Over the windows and the map, which come to the front when they open
            if (screenLayer.getZIndex() != stage.getRoot().getChildren().size - 1) screenLayer.toFront();
        }
        applyHidden();
        stage.act(delta);
        stage.draw();
        // The game has read this frame's keys by now: the next frame's are its own
        variantKeys.clear();
    }

    public void resize(int width, int height) {
        viewports.resize(width, height);
        // A new UI scale: the text is drawn again for it, and everything laid out around the new sizes
        if (HudSkin.rescale(skin, viewports.getUiScale())) relayout(stage.getRoot());
        if (variant != null) variant.resize(width, height, viewports.getUiScale());
    }

    // --- HUD variants -----------------------------------------------------------------------

    /** The common data layer the variants read. The game sets its providers and raises its alerts. */
    public HudData getData() {
        return data;
    }

    /** The id of the variant drawing now ({@link HudVariants}). */
    public String getVariantId() {
        return variantId;
    }

    /** The variant drawing now, or null for the classic HUD. */
    public HudVariant getVariant() {
        return variant;
    }

    /** Whether the classic HUD's {@code part} is up under the variant drawing now. */
    public boolean keepsClassic(ClassicPart part) {
        return !hidden.contains(part);
    }

    /** Switches to {@link HudVariants#getChosen()} if that is not the variant drawing now. */
    private void syncVariant() {
        String chosen = HudVariants.getChosen();
        if (!chosen.equals(variantId)) setVariant(chosen);
    }

    /**
     * Draws the HUD with the variant {@code id} ({@link HudVariants}) from now on, or the classic HUD
     * for "classic" or an id that is unknown. The game normally sets {@link HudVariants#setChosen}
     * and the HUD follows on its next frame.
     */
    public void setVariant(String id) {
        HudVariants.Entry entry = HudVariants.find(id);
        // What is open now opens again under the next variant, whoever shows it there
        wasOpen.clear();
        screens.opened(wasOpen);
        if (data.console.isOpen()) {
            consoleClosed();
            consoleWindow.close();
        }
        if (variant != null) {
            variant.detach();
            variantLayer.clearChildren();
            screenLayer.clearChildren();
            variant = null;
        }
        // The map as the classic HUD has it, until the next variant gives it its own look and insets
        maneuverMap.setPainter(null);
        maneuverMap.setCanvasInsets(0f, 0f, 0f, 0f);
        maneuverMap.getTraffic().showAll();
        variantId = entry.id;
        hidden.clear();
        HudVariant next = entry.create();
        if (next != null) {
            try {
                next.attach(new HudVariantHost(this, variantLayer, screenLayer));
                variant = next;
                for (ClassicPart p : ClassicPart.values()) if (!next.keeps().contains(p)) hidden.add(p);
                variant.resize(com.badlogic.gdx.Gdx.graphics == null ? 0 : com.badlogic.gdx.Gdx.graphics.getWidth(),
                        com.badlogic.gdx.Gdx.graphics == null ? 0 : com.badlogic.gdx.Gdx.graphics.getHeight(), viewports.getUiScale());
                maneuverMap.setPainter(next.mapPainter());
            } catch (RuntimeException e) {
                // A variant that cannot start leaves the classic HUD up rather than none
                variantLayer.clearChildren();
                screenLayer.clearChildren();
                maneuverMap.setPainter(null);
                maneuverMap.setCanvasInsets(0f, 0f, 0f, 0f);
                hidden.clear();
                variant = null;
                variantId = HudVariants.CLASSIC;
                if (com.badlogic.gdx.Gdx.app != null) com.badlogic.gdx.Gdx.app.error("UiHud", "HUD variant " + entry.id + " failed to start", e);
            }
        }
        screens.setVariant(variant, wasOpen);
        maneuverMap.setOwnPanel(!screens.replaced(ClassicWindow.MAP));
        // Everything the last variant hid comes back as the view shows it, then this one's hiding applies
        flightPanel.setVisible(true);
        gauges.setVisible(true);
        bannerRow.setVisible(true);
        dockRowRef.setVisible(true);
        applyViewMode(viewMode.getMode());
    }

    /** Hides the classic parts the variant does not keep. Each frame, since the view mode shows some again. */
    private void applyHidden() {
        if (hidden.isEmpty()) return;
        if (hidden.contains(ClassicPart.FLIGHT_PANEL)) flightPanel.setVisible(false);
        if (hidden.contains(ClassicPart.GAUGES)) gauges.setVisible(false);
        if (hidden.contains(ClassicPart.RADAR)) radarPanel.setVisible(false);
        if (hidden.contains(ClassicPart.SHIP_PANEL)) minimapPanel.setVisible(false);
        if (hidden.contains(ClassicPart.ORBIT_PANEL)) orbitPanel.setVisible(false);
        if (hidden.contains(ClassicPart.MISSILE_BANNER)) bannerRow.setVisible(false);
        if (hidden.contains(ClassicPart.THREAT_OVERLAY)) threatOverlay.setVisible(false);
        if (hidden.contains(ClassicPart.WORLD_MARKERS)) markerLayer.setVisible(false);
        if (hidden.contains(ClassicPart.DOCK_BANNER)) dockRowRef.setVisible(false);
        if (hidden.contains(ClassicPart.PROMPT)) promptRowRef.setVisible(false);
    }

    private static void relayout(com.badlogic.gdx.scenes.scene2d.Actor a) {
        if (a instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout) ((com.badlogic.gdx.scenes.scene2d.utils.Layout) a).invalidate();
        if (a instanceof Group) for (com.badlogic.gdx.scenes.scene2d.Actor c : ((Group) a).getChildren()) relayout(c);
    }

    public InputProcessor getInputProcessor() {
        return stage;
    }

    public ViewportManager getViewports() {
        return viewports;
    }

    public ViewModeController getViewMode() {
        return viewMode;
    }

    public Stage getStage() {
        return stage;
    }

    public Skin getSkin() {
        return skin;
    }

    /** Where {@code a} is drawn on the stage, or an empty box while it is hidden. */
    private static com.badlogic.gdx.math.Rectangle stageBounds(com.badlogic.gdx.scenes.scene2d.Actor a) {
        if (a.getStage() == null || !a.ascendantsVisible()) return new com.badlogic.gdx.math.Rectangle();
        com.badlogic.gdx.math.Vector2 at = a.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
        return new com.badlogic.gdx.math.Rectangle(at.x, at.y, a.getWidth(), a.getHeight());
    }

    /** The flight readout at the top left, for laying out around it. */
    public Table getFlightPanel() {
        return flightPanel;
    }

    /** The ship panel at the bottom left (hidden in interior view), for laying out around it. */
    public Table getMinimapPanel() {
        return minimapPanel;
    }

    public WireframeMinimap getMinimap() {
        return minimap;
    }

    public OrbitMap getOrbitMap() {
        return orbitMap;
    }

    public ConsoleWindow getConsoleWindow() {
        return consoleWindow;
    }

    public WireframeSyncSystem getWireframeSyncSystem() {
        return wireframeSyncSystem;
    }

    public InteractPromptSystem getInteractPromptSystem() {
        return interactPromptSystem;
    }

    public HudRenderSystem getHudRenderSystem() {
        return hudRenderSystem;
    }

    @Override
    public void dispose() {
        if (variant != null) {
            variant.detach();
            variant = null;
        }
        stage.dispose();
        skin.dispose();
    }
}
