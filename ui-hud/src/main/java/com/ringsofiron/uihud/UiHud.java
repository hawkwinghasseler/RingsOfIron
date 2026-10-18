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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.minimap.WireframeMinimap;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.systems.FlightReadoutSystem;
import com.ringsofiron.uihud.systems.HudRenderSystem;
import com.ringsofiron.uihud.systems.InteractPromptSystem;
import com.ringsofiron.uihud.systems.WireframeSyncSystem;
import com.ringsofiron.uihud.widgets.AlertList;
import com.ringsofiron.uihud.widgets.ConsoleWindow;
import com.ringsofiron.uihud.widgets.ThreatOverlay;
import com.ringsofiron.uihud.widgets.TradeWindow;
import com.ringsofiron.uihud.widgets.TrafficMap;

/**
 * Entry point to the ui-hud module: builds the Scene2D stage, the HUD widgets and the ECS
 * systems that keep them in sync with the game.
 *
 * <pre>
 *   UiHud hud = new UiHud();
 *   hud.addTo(engine);                       // after physics systems
 *   hud.setPhysics(physics);                 // optional: absolute speed
 *   hud.setTargeting(...); hud.setOrbitInfo(...); hud.setInteractor(...);
 *   inputMultiplexer.addProcessor(hud.getInputProcessor());
 *   // render(): position world camera, draw world, engine.update(dt), then hud.draw(dt)
 *   // resize(): hud.resize(w, h)
 * </pre>
 *
 * Keys handled by the HUD: E use console, Esc close console, map or market, V toggle exterior/interior view,
 * M toggle the traffic map (mouse wheel zooms it while open), T open the station market when in range.
 */
public class UiHud implements Disposable {
    private final ViewportManager viewports;
    private final Skin skin;
    private final Stage stage;
    private final ViewModeController viewMode = new ViewModeController();

    private final Group markerLayer = new Group();
    private final WireframeMinimap minimap;
    private final Table minimapPanel;
    private final ConsoleWindow consoleWindow;
    private final ObjectMap<String, ConsoleWindow.ContentBuilder> consoleLayouts = new ObjectMap<>();
    private final TrafficMap trafficMap;
    private final TradeWindow tradeWindow;
    private final ThreatOverlay threatOverlay;

    private final HudRenderSystem hudRenderSystem;
    private final WireframeSyncSystem wireframeSyncSystem;
    private final InteractPromptSystem interactPromptSystem;
    private final FlightReadoutSystem flightReadoutSystem;

    public UiHud() {
        this(new ViewportManager());
    }

    public UiHud(ViewportManager viewports) {
        this.viewports = viewports;
        skin = HudSkin.create();
        stage = new Stage(viewports.getUiViewport());

        markerLayer.setTouchable(Touchable.childrenOnly);
        stage.addActor(markerLayer);

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        stage.addActor(root);

        Table flight = new Table();
        flight.setBackground(skin.getDrawable("panel"));
        flight.pad(8f);
        root.add(flight).top().left().pad(10f).expand().row();

        minimap = new WireframeMinimap(skin.getRegion("white"));
        AlertList alerts = new AlertList(skin);
        minimapPanel = new Table();
        minimapPanel.setBackground(skin.getDrawable("panel"));
        minimapPanel.pad(8f);
        minimapPanel.add(new Label("SHIP", skin, "title")).left().colspan(2).row();
        minimapPanel.add(minimap).left().bottom();
        minimapPanel.add(alerts).top().left().padLeft(10f).minWidth(150f);
        root.add(minimapPanel).bottom().left().pad(10f);

        Label prompt = new Label("", skin, "prompt");
        Table promptRow = new Table();
        promptRow.setFillParent(true);
        promptRow.setTouchable(Touchable.disabled);
        promptRow.bottom().padBottom(70f);
        promptRow.add(prompt);
        stage.addActor(promptRow);

        // Missile paths and point-defense hits over the world, with a warning banner at the top.
        Label threatBanner = new Label("", skin, "alert");
        Table bannerRow = new Table();
        bannerRow.setFillParent(true);
        bannerRow.setTouchable(Touchable.disabled);
        bannerRow.top().padTop(70f);
        bannerRow.add(threatBanner);
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

        trafficMap = new TrafficMap(skin);
        trafficMap.setFillParent(true);
        trafficMap.setVisible(false);
        stage.addActor(trafficMap);

        consoleWindow = new ConsoleWindow(skin, () -> interactPromptSystem().setPromptsEnabled(true));
        stage.addActor(consoleWindow);
        stage.addActor(tradeWindow);

        hudRenderSystem = new HudRenderSystem(viewports, markerLayer, skin);
        wireframeSyncSystem = new WireframeSyncSystem(minimap, alerts);
        interactPromptSystem = new InteractPromptSystem(prompt, this::openConsole);
        flightReadoutSystem = new FlightReadoutSystem(flight, skin, viewMode);

        viewMode.addListener((previous, current) -> {
            boolean exterior = current == ViewMode.EXTERIOR;
            markerLayer.setVisible(exterior);
            // In the interior view the full deck plan is on screen, so the minimap is redundant.
            minimapPanel.setVisible(exterior);
            threatOverlay.setVisible(exterior);
        });

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKey(keycode);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (!trafficMap.isVisible()) return false;
                trafficMap.zoom((float) Math.pow(1.25f, -amountY));
                return true;
            }
        });
    }

    private InteractPromptSystem interactPromptSystem() {
        return interactPromptSystem;
    }

    /** Handles HUD keys. Public so games that route input themselves can forward keys. */
    public boolean handleKey(int keycode) {
        switch (keycode) {
            case Input.Keys.E:
                if (consoleWindow.isOpen()) return false;
                return interactPromptSystem.interact();
            case Input.Keys.ESCAPE:
                if (trafficMap.isVisible()) {
                    setTrafficMapVisible(false);
                    return true;
                }
                if (tradeWindow.isOpen()) {
                    tradeWindow.setOpen(false);
                    return true;
                }
                if (!consoleWindow.isOpen()) return false;
                consoleWindow.close();
                return true;
            case Input.Keys.T:
                if (!tradeWindow.isOpen() && !tradeWindow.isDocked()) return false;
                tradeWindow.setOpen(!tradeWindow.isOpen());
                return true;
            case Input.Keys.M:
                setTrafficMapVisible(!trafficMap.isVisible());
                return true;
            case Input.Keys.V:
                viewMode.toggle();
                return true;
            default:
                return false;
        }
    }

    private void openConsole(Entity console, InteractableConsoleComponent data) {
        interactPromptSystem.setPromptsEnabled(false);
        consoleWindow.open(console, data, consoleLayouts.get(data.consoleId));
    }

    /** Registers a custom window layout for consoles with this id (e.g. "helm"). */
    public void registerConsoleLayout(String consoleId, ConsoleWindow.ContentBuilder builder) {
        consoleLayouts.put(consoleId, builder);
    }

    /** Adds the HUD systems to an engine. Their priorities run them after physics and game logic. */
    public void addTo(Engine engine) {
        engine.addSystem(hudRenderSystem);
        engine.addSystem(wireframeSyncSystem);
        engine.addSystem(interactPromptSystem);
        engine.addSystem(flightReadoutSystem);
    }

    public void removeFrom(Engine engine) {
        engine.removeSystem(hudRenderSystem);
        engine.removeSystem(wireframeSyncSystem);
        engine.removeSystem(interactPromptSystem);
        engine.removeSystem(flightReadoutSystem);
    }

    public void setPhysics(PhysicsManager physics) {
        flightReadoutSystem.setPhysics(physics);
    }

    public void setTargeting(TargetingProvider targeting) {
        hudRenderSystem.setTargeting(targeting);
        flightReadoutSystem.setTargeting(targeting);
    }

    public void setOrbitInfo(OrbitInfoProvider orbitInfo) {
        flightReadoutSystem.setOrbitInfo(orbitInfo);
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

    /** Feeds the station market window ([T] while in range of a station); null hides it. */
    public void setTrade(TradeProvider trade) {
        tradeWindow.setProvider(trade);
    }

    public TradeWindow getTradeWindow() {
        return tradeWindow;
    }

    /** Off-bubble traffic for the map view (M). Without one the map says there is no data. */
    public void setTrafficMap(TrafficMapProvider provider) {
        trafficMap.setProvider(provider);
    }

    public void setTrafficMapVisible(boolean visible) {
        if (visible) trafficMap.refresh();
        trafficMap.setVisible(visible);
        if (visible) trafficMap.toFront();
    }

    public TrafficMap getTrafficMap() {
        return trafficMap;
    }

    /** Briefly highlights a tile on the minimap, e.g. from combat-sys's hull-hit event. */
    public void flashHullHit(int tileX, int tileY) {
        minimap.flashTile(tileX, tileY);
    }

    /** Acts and draws the stage. Call after the world is drawn and the engine has updated. */
    public void draw(float delta) {
        viewports.applyUi();
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        viewports.resize(width, height);
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

    public WireframeMinimap getMinimap() {
        return minimap;
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
