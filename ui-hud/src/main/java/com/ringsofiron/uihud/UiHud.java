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
 * M toggle the map: the orbit map ({@link ManeuverMap}) or the traffic map, whichever was shown last, with
 * tabs between them (the mouse wheel zooms either while open), T open the station market while docked,
 * J the mission board (while docked, or holding a contract), I the crew roster. The shipyard opens from
 * the yard window's Ships for sale (while docked where ships are sold), or from Y with a prize in tow
 * near a station.
 */
public class UiHud implements Disposable {
    private final ViewportManager viewports;
    private final Skin skin;
    private final Stage stage;
    private final ViewModeController viewMode = new ViewModeController();
    /** What V does instead of switching the view, when a game decides when the view may switch. */
    private Runnable viewKey;

    private final Group markerLayer = new Group();
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
        tradeWindow = new TradeWindow(skin, dockHint);
        yardWindow = new YardWindow(skin);
        missionWindow = new MissionWindow(skin);
        crewWindow = new CrewWindow(skin);
        shipyardWindow = new ShipyardWindow(skin);
        tradeWindow.setHintSuffix(() -> (yardWindow.isAvailable() && !yardWindow.isOpen() ? "   [Y] Yard" : "")
                + (!missionWindow.getBoard().stationName.isEmpty() && !missionWindow.isOpen() ? "   [J] Missions" : "")
                + (!yardWindow.isAvailable() && shipyardWindow.isAvailable() && !shipyardWindow.isOpen() ? "   [Y] Shipyard" : ""));
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

        hudRenderSystem = new HudRenderSystem(viewports, markerLayer, skin);
        hudRenderSystem.keepClear(threatBanner, () -> threatOverlay.isVisible() && threatBanner.getText().length() > 0);
        hudRenderSystem.keepClear(dockHint, dockHint::isVisible);
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

        viewMode.addListener((previous, current) -> {
            boolean exterior = current == ViewMode.EXTERIOR;
            markerLayer.setVisible(exterior);
            // In the interior view the full deck plan is on screen, so the minimap is redundant.
            minimapPanel.setVisible(exterior);
            orbitPanel.setVisible(exterior);
            threatOverlay.setVisible(exterior);
            radarPanel.setVisible(exterior);
            // Seated and looking outside there is no deck to walk: the console prompt only confused
            promptRow.setVisible(!exterior);
        });

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

    /** Handles HUD keys. Public so games that route input themselves can forward keys. */
    public boolean handleKey(int keycode) {
        // The open window has the arrow keys and Enter, for the keyboard and the pad
        com.badlogic.gdx.scenes.scene2d.Group window = openWindow();
        if (window != focusWindow) {
            focusNav.clear();
            focusWindow = window;
        }
        if (window != null && focusNav.key(window, keycode)) return true;
        switch (keycode) {
            case Input.Keys.E:
                if (consoleWindow.isOpen()) return false;
                return interactPromptSystem.interact();
            case Input.Keys.ESCAPE:
                if (isMapOpen()) {
                    setMapVisible(false);
                    return true;
                }
                if (tradeWindow.isOpen()) {
                    tradeWindow.setOpen(false);
                    return true;
                }
                if (yardWindow.isOpen()) {
                    yardWindow.setOpen(false);
                    return true;
                }
                if (missionWindow.isOpen()) {
                    missionWindow.setOpen(false);
                    return true;
                }
                if (shipyardWindow.isOpen()) {
                    shipyardWindow.setOpen(false);
                    return true;
                }
                if (boardingWindow.isOpen()) {
                    boardingWindow.setOpen(false);
                    return true;
                }
                if (crewWindow.isOpen()) {
                    crewWindow.setOpen(false);
                    return true;
                }
                if (!consoleWindow.isOpen()) return false;
                consoleWindow.close();
                return true;
            case Input.Keys.T:
                if (!tradeWindow.isOpen() && !tradeWindow.isDocked()) return false;
                tradeWindow.setOpen(!tradeWindow.isOpen());
                return true;
            case Input.Keys.Y:
                // The station yard; the shipyard window is one of its services, and has the key to
                // itself where there is no yard, as when handing over a prize off a station
                if (shipyardWindow.isOpen()) {
                    shipyardWindow.setOpen(false);
                    return true;
                }
                if (yardWindow.isOpen() || yardWindow.isAvailable()) {
                    yardWindow.setOpen(!yardWindow.isOpen());
                    return true;
                }
                return shipyardWindow.isAvailable() && shipyardWindow.setOpen(true);
            case Input.Keys.J:
                if (!missionWindow.isOpen() && !missionWindow.isAvailable()) return false;
                missionWindow.setOpen(!missionWindow.isOpen());
                return true;
            case Input.Keys.K:
                if (!boardingWindow.isOpen() && !boardingWindow.isAvailable()) return false;
                boardingWindow.setOpen(!boardingWindow.isOpen());
                return true;
            case Input.Keys.I:
                if (!crewWindow.isOpen() && !crewWindow.isAvailable()) return false;
                crewWindow.setOpen(!crewWindow.isOpen());
                return true;
            case Input.Keys.M:
                setMapVisible(!isMapOpen());
                return true;
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

    /** The window that has the keys, or null: the console, market, yard, mission board or boarding window. */
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
        consoleWindow.open(console, data, consoleLayouts.get(data.consoleId), consoleExtras.get(data.consoleId));
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
        flightReadoutSystem.setPhysics(physics);
        navMarkerSystem.setPhysics(physics);
    }

    public void setTargeting(TargetingProvider targeting) {
        hudRenderSystem.setTargeting(targeting);
        flightReadoutSystem.setTargeting(targeting);
        radarSystem.setTargeting(targeting);
        navMarkerSystem.setTargeting(targeting);
    }

    /** Feeds the propellant, structure, power and oxygen gauges; null leaves them reading "--". */
    public void setShipStatus(ShipStatusProvider status) {
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
        flightReadoutSystem.setOrbitInfo(orbitInfo);
    }

    /** Feeds the orbit map, small and full screen; null leaves them empty. */
    public void setOrbitPath(OrbitPathProvider orbitPath) {
        orbitMap.setProvider(orbitPath);
        maneuverMap.setProvider(orbitPath);
    }

    /** What the full-screen orbit map's mouse planning and buttons do; null leaves them doing nothing. */
    public void setManeuverPlanner(ManeuverPlannerProvider planner) {
        maneuverMap.setPlanner(planner);
    }

    public ManeuverMap getManeuverMap() {
        return maneuverMap;
    }

    public void setInteractor(InteractorProvider interactor) {
        interactPromptSystem.setInteractor(interactor);
        wireframeSyncSystem.setInteractor(interactor);
    }

    /** Feeds the missile overlay and warning banner; null clears them. */
    public void setThreats(ThreatProvider threats) {
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
        tradeWindow.setProvider(trade);
    }

    public TradeWindow getTradeWindow() {
        return tradeWindow;
    }

    /** Feeds the station yard window ([Y] while docked where a yard can work on the ship); null hides it. */
    public void setYard(YardProvider yard) {
        yardWindow.setProvider(yard);
    }

    /** Feeds the boarding and salvage window ([K] while latched to another ship); null hides it. */
    public void setBoarding(BoardingProvider boarding) {
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
        missionWindow.setProvider(missions);
    }

    public MissionWindow getMissionWindow() {
        return missionWindow;
    }

    /** Feeds the crew roster ([I] whenever the player has a ship); null hides it. */
    public void setCrew(CrewProvider crew) {
        crewWindow.setProvider(crew);
    }

    public CrewWindow getCrewWindow() {
        return crewWindow;
    }

    /** Feeds the shipyard window (the yard window's Ships for sale, or [Y] with a prize to hand over and no yard); null hides it. */
    public void setShipyard(ShipyardProvider shipyard) {
        shipyardWindow.setProvider(shipyard);
    }

    public ShipyardWindow getShipyardWindow() {
        return shipyardWindow;
    }

    /** Off-bubble traffic for the map (M), drawn over the orbits. Without one the map shows only orbits. */
    public void setTrafficMap(TrafficMapProvider provider) {
        maneuverMap.setTraffic(provider);
    }

    /** Opens the map (orbits, burns and traffic), or closes it. */
    public void setMapVisible(boolean visible) {
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
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        viewports.resize(width, height);
        // A new UI scale: the text is drawn again for it, and everything laid out around the new sizes
        if (HudSkin.rescale(skin, viewports.getUiScale())) relayout(stage.getRoot());
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
        if (a.getStage() == null || !a.ancestorsVisible()) return new com.badlogic.gdx.math.Rectangle();
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
        stage.dispose();
        skin.dispose();
    }
}
