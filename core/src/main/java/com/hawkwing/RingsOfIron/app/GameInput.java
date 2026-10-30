package com.hawkwing.RingsOfIron.app;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.ShipNames;
import com.hawkwing.RingsOfIron.game.BurnPlanner;
import com.hawkwing.RingsOfIron.render.DeckView;
import com.hawkwing.RingsOfIron.game.DistressBeacon;
import com.ringsofiron.shipops.DoorForcing;
import com.hawkwing.RingsOfIron.game.ExteriorPick;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.HelmOrderComponent;
import com.hawkwing.RingsOfIron.game.Navigation;
import com.hawkwing.RingsOfIron.game.NoticeFeeds;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.PlayerGunnery;
import com.hawkwing.RingsOfIron.game.WarpNotices;
import com.ringsofiron.coreengine.input.Action;
import com.hawkwing.RingsOfIron.shell.RemappedInput;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.Locale;

/**
 * The player's keys and clicks in play, read each frame as {@link Action}s so the player's bindings
 * apply: flying and walking, the guns, shields, docking, burns and navigation, the console seats,
 * the press-twice confirmations (abandon ship, call a tow), the notice log and controls panels, and
 * a click selecting a ship or station outside.
 */
public final class GameInput {
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final float INTERIOR_VIEW = 40f;
    /** A contact this far from the pointer, as a share of the view's width, is the one the guns in hand lead. */
    private static final float HAND_PICK = 0.02f;
    private static final float WALK_SPEED = 1f;
    /** Seconds to press P again to confirm abandoning ship, or B to confirm calling a tow. */
    private static final float ABANDON_CONFIRM = 3f;

    private final GameSession session;
    private final GameWorld world;
    private final GameScenario game;
    private final UiHud hud;
    private final ViewportManager viewports;
    private final SaveController saves;
    private final RenderPipeline pipeline;
    private final FurniturePlacing placing;
    /** Set once the developer tools exist, before the first frame. */
    DevTools dev;
    private float abandonAsked, towAsked;
    private float exteriorViewMeters;
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();

    GameInput(GameSession session, SaveController saves, RenderPipeline pipeline, FurniturePlacing placing, float startView) {
        this.session = session;
        this.world = session.world;
        this.game = session.game;
        this.hud = session.hud;
        this.viewports = session.viewports;
        this.saves = saves;
        this.pipeline = pipeline;
        this.placing = placing;
        this.exteriorViewMeters = startView;
    }

    /** The action's key went down this frame. */
    private static boolean just(Action action) {
        return Gdx.input.isKeyJustPressed(action.defaultKey);
    }

    private static boolean held(Action action) {
        return Gdx.input.isKeyPressed(action.defaultKey);
    }

    /** How far the action is held, 0 to 1: a key all or nothing, a gamepad stick as far as it is pushed. */
    private static float amount(Action action) {
        if (Gdx.input instanceof RemappedInput) return ((RemappedInput) Gdx.input).amount(action.defaultKey);
        return held(action) ? 1f : 0f;
    }

    /** The confirmations asked for run out {@code delta} seconds on. */
    void countDown(float delta) {
        abandonAsked -= delta;
        towAsked -= delta;
    }

    /** Reads this frame's keys, {@code delta} seconds after the last. */
    void handle(float delta) {
        if (dev.isTyping()) {
            // Keys typed into the developer console fly nothing and fire nothing
            FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
            if (controls != null) controls.forward = controls.turn = controls.strafe = 0f;
            PlayerAvatarComponent walker = game.avatar == null ? null : game.avatar.getComponent(PlayerAvatarComponent.class);
            if (walker != null) walker.moveX = walker.moveY = 0f;
            NoticeFeeds.collect(world);
            return;
        }
        boolean consoleOpen = hud.getConsoleWindow().isOpen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && (session.logOpen || session.helpOpen)) {
            session.logOpen = false;
            session.helpOpen = false;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !consoleOpen && !session.options.hosted && !hudWindowOpen()
                && !backOut()) {
            Gdx.app.exit();
        }
        if (just(Action.LOG)) {
            session.logOpen = !session.logOpen;
            session.logScroll = 0;
            if (session.logOpen && session.firstPlay != null) session.firstPlay.logOpened();
            session.helpOpen = false;
        }
        if (just(Action.HELP)) {
            session.helpOpen = !session.helpOpen;
            session.logOpen = false;
        }
        if (just(Action.QUICK_LOAD)) saves.quickLoad();
        if (world.getCrash() != null || world.getCareerOver() != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || just(Action.USE)) saves.loadLatestAutosave();
            return;
        }
        if (session.logOpen) {
            // The log has the arrow keys while it is open
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) session.logScroll++;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
                session.logScroll = Math.max(0, session.logScroll - 1);
            }
        }
        if (just(Action.WARP_UP)) WarpNotices.step(world, 1);
        if (just(Action.WARP_DOWN)) WarpNotices.step(world, -1);
        if (just(Action.QUICK_SAVE)) saves.quickSave();
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
        if (consoleOpen || session.logOpen) return;

        if (session.deckView()) {
            if (walker == null) return;
            // Force the shut door across a dock: pry or hack it as its power allows (F), or cut it (B).
            // Standing anywhere else, the same keys order the crew to.
            if (just(Action.DECOY) || just(Action.CUT)) {
                boolean cut = just(Action.CUT);
                DoorForcing.Target t = world.forcing.target(game.avatar, new DoorForcing.Target());
                // Stranded and at no door, B calls the tug, as at the helm
                if (t.craft == null && cut && world.beacon.refusal() == null) callTow();
                else if (t.craft == null) session.show(world.forcing.order(game.player, cut));
                else session.show(world.forcing.start(game.avatar, cut ? ForceMethod.CUT
                        : t.state == ForceMethod.DoorState.UNPOWERED ? ForceMethod.PRY : ForceMethod.HACK));
            }
            // Undocking is a helm order: on deck, say where to give it rather than doing nothing
            if (just(Action.UNDOCK) && world.docking.dockedTo(game.player) != null) {
                session.show("Take the helm to undock: " + session.key(Action.VIEW) + " at the helm, then " + session.key(Action.UNDOCK));
            }
            if (just(Action.LIGHTS)) toggleLights();
            // Up the screen is W whichever way the craft the avatar is aboard is turned
            // A pad's stick walks as fast as it is pushed
            float sy = amount(Action.THRUST) - amount(Action.REVERSE);
            float sx = amount(Action.TURN_RIGHT) - amount(Action.TURN_LEFT);
            Entity aboard = world.avatar.getAvatarShip();
            DeckView.walk(walker, aboard == null ? null : world.docking.bodyOf(aboard), sx, sy, WALK_SPEED);
            return;
        }
        // At the weapons console only the weapons answer; the helm flies the ship
        boolean helm = world.bridge.playerAtHelm();
        if (helm && controls != null) {
            if (just(Action.ROTATION_ASSIST)) controls.rotationAssist = !controls.rotationAssist;
            // Keys fly at full thrust; a pad's sticks throttle the engines and RCS as far as they are pushed
            controls.forward = amount(Action.THRUST) - amount(Action.REVERSE);
            controls.turn = amount(Action.TURN_LEFT) - amount(Action.TURN_RIGHT);
            controls.strafe = amount(Action.STRAFE_LEFT) - amount(Action.STRAFE_RIGHT);
        }
        if (just(Action.LIGHTS)) toggleLights();
        handAim();
        if (held(Action.FIRE)) session.gun.fireGuns();
        if (just(Action.MISSILE)) {
            // "Launcher reloading" once per reload, not once per press
            if (session.gun.launchMissile()) world.notices.allowAgain(NoticeLog.RELOADING);
            else if (session.gun.getTarget() == null) session.show("No target for the missile");
            else world.notices.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO);
        }
        if (just(Action.DECOY) && session.gun.dropDecoy()) session.show("Decoy away");
        if (just(Action.SHIELDS)) session.show(toggleShields());
        if (just(Action.SHIELD_TRIM)) session.show(trimShields());
        if (just(Action.NEXT_TARGET)) session.gun.setTarget(game.nextContact(session.gun.getTarget()));
        navigationKeys();
        if (!helm) return;
        PlayerDocking docking = session.docking;
        if (just(Action.ARM_PORT)) session.show(docking.toggleArm());
        if (just(Action.NEXT_PORT)) session.show(docking.nextPort());
        if (just(Action.UNDOCK)) {
            // Saved just before letting go of a station
            if (world.playerStation() != null) saves.autosave("undocking");
            session.show(docking.undock());
        }
        // An open window has the arrow keys
        if (hud.openWindow() == null) planBurns();
    }

    /** Raises the player's shields, or lowers them safely into the emitters; what to tell the player. */
    private String toggleShields() {
        ShieldCloudComponent cloud = ShieldModel.ensure(game.player);
        if (cloud == null) return "No shield emitters aboard";
        if (!cloud.raised && world.docking.dockedTo(game.player) != null) return "Shields stay down while docked";
        ShieldModel.raise(game.player, !cloud.raised);
        return cloud.raised ? "Raising shields" : "Lowering shields";
    }

    /** Leans the player's resting shield cloud the next way round; what to tell the player. */
    private String trimShields() {
        ShieldCloudComponent cloud = ShieldModel.ensure(game.player);
        if (cloud == null) return "No shield emitters aboard";
        ShieldModel.trim(game.player, cloud.trim.next());
        return "Shields trimmed " + cloud.trim.label;
    }

    /** O: the lighting off or back on, inside and out. */
    private void toggleLights() {
        session.show(pipeline.toggleLights() ? "Lighting on" : "Lighting off");
    }

    /** True while one of the HUD's windows or the map is open; Esc closes it first. */
    private boolean hudWindowOpen() {
        return hud.isMapOpen() || hud.getTradeWindow().isOpen() || hud.getYardWindow().isOpen()
                || hud.getMissionWindow().isOpen() || hud.getBoardingWindow().isOpen() || hud.getConsoleWindow().isOpen()
                || dev.isConsoleOpen() || dev.isMenuOpen();
    }

    /**
     * Esc in play, before it pauses: a piece being placed is let go, and a transfer plan waiting on
     * Enter or Delete is discarded, as a player backing out of the planner expects. True when Esc
     * was used up here.
     */
    public boolean backOut() {
        if (placing.back()) return true;
        if (world.nav.transfer.proposal() == null) return false;
        session.show(world.nav.transfer.discard());
        return true;
    }

    // --- The console seats --------------------------------------------------------------

    /** The camera eases between the deck's zoom and the outside one the player left, as the view changes. */
    void easeViewChanges() {
        hud.getViewMode().addListener((previous, current) -> {
            if (previous == current) return;
            // Eased, and the camera slides between the ship and the avatar as it goes (render)
            if (current == ViewMode.INTERIOR) {
                exteriorViewMeters = viewports.getTargetViewMeters();
                viewports.easeViewMeters(INTERIOR_VIEW);
            } else {
                viewports.easeViewMeters(exteriorViewMeters);
            }
        });
    }

    /** V: stand up from the console into the deck view, or sit at the console beside you and look outside. */
    void sitOrStand() {
        if (hud.getViewMode().getMode() == ViewMode.EXTERIOR) {
            boolean leftHelm = world.bridge.playerAtHelm(), leftGun = world.bridge.playerAtGun();
            world.bridge.stand();
            session.gun.releaseAim();
            hud.getViewMode().setMode(ViewMode.INTERIOR);
            if (leftGun) session.show("You leave the gun");
            else if (!leftHelm) session.show("You leave the weapons console");
            else if (world.bridge.helmsman(game.player) != null) session.show("You leave the helm; the helmsman holds your heading");
            else session.show("You leave the helm; nobody has it now");
            return;
        }
        sitAt(null);
    }

    /** Sits at the console of {@code type} beside the avatar (null: the nearest) and looks outside. */
    private void sitAt(SubsystemType type) {
        String refused = world.bridge.sit(type);
        if (refused != null) {
            session.show(refused);
            return;
        }
        hud.getViewMode().setMode(ViewMode.EXTERIOR);
        String fault = world.bridge.helmFault(game.player);
        session.show(world.bridge.playerAtHelm() ? (fault == null ? "You take the helm" : "You take the helm, but " + fault)
                : world.bridge.playerAtGun() ? "You take the gun: the pointer aims it, " + session.key(Action.FIRE) + " or a click fires"
                : "You take the weapons console: the pointer aims every gun, " + session.key(Action.FIRE) + " or a click fires");
    }

    /** A console window's button that sits the player at it, guns in hand. */
    void takeControlButton(Table body, Skin skin, String label, SubsystemType type) {
        TextButton take = new TextButton(label + " [" + session.key(Action.VIEW) + "]", skin);
        take.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hud.getConsoleWindow().close();
                sitAt(type);
            }
        });
        body.add(take).left().padTop(6f).row();
    }

    /**
     * Seated at the guns, outside: the guns in hand follow the pointer, and the fire key or the left
     * button (off the HUD's windows and panels) fires them. Anywhere else, the crew have them back.
     */
    private void handAim() {
        PlayerGunnery gun = session.gun;
        if (!world.bridge.playerOnGuns() || hud.getViewMode().getMode() != ViewMode.EXTERIOR) {
            gun.releaseAim();
            return;
        }
        int sx = Gdx.input.getX(), sy = Gdx.input.getY();
        Vector2 at = viewports.getWorldViewport().unproject(tmp.set(sx, sy));
        gun.aim(at.x, at.y, viewports.getViewMeters() * HAND_PICK);
        Vector2 ui = hud.getStage().screenToStageCoordinates(tmp2.set(sx, sy));
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && hud.getStage().hit(ui.x, ui.y, true) == null) gun.fireGuns();
    }

    // --- Burns and navigation ------------------------------------------------------------

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
        Navigation nav = world.nav;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (just(Action.NAV_TARGET)) world.notices.replace("Navigation target", nav.target.cycle(shift ? -1 : 1));
        if (just(Action.PLAN_TO_TARGET)) session.show(nav.transfer.plan());
        if (just(Action.ACCEPT_PLAN) && nav.transfer.proposal() != null) session.show(nav.transfer.accept(world.burns));
        if (just(Action.DISCARD_PLAN) && nav.transfer.proposal() != null) session.show(nav.transfer.discard());
        if (just(Action.WARP_TO_BURN)) session.show(nav.warpToNextBurn());
        if (just(Action.HELM_FLY_BURNS)) session.show(nav.helm.give(HelmOrderComponent.Mode.FLY_BURNS));
        if (just(Action.HELM_MATCH_VELOCITY)) session.show(nav.helm.give(HelmOrderComponent.Mode.MATCH_VELOCITY));
        if (just(Action.HELM_HOLD)) session.show(nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION));
        if (just(Action.HELM_APPROACH)) session.show(nav.helm.give(HelmOrderComponent.Mode.APPROACH));
        if (just(Action.HELM_DOCK)) session.show(nav.helm.give(HelmOrderComponent.Mode.DOCK));
        if (just(Action.AUTOPILOT)) session.show(nav.autopilot.toggle());
        if (just(Action.HELM_CANCEL)) session.show(nav.helm.give(HelmOrderComponent.Mode.NONE));
    }

    // --- Press twice: the tow and abandoning ship ----------------------------------------

    /** B while stranded, or the pause menu: the first press asks and names the fee, a second calls the tug. */
    public void callTow() {
        String refused = world.beacon.refusal();
        if (refused != null) {
            session.show("Can't call a tow: " + refused);
            return;
        }
        if (towAsked <= 0f) {
            towAsked = ABANDON_CONFIRM;
            session.show(String.format(Locale.ROOT, "Call the sponsor's tug? %s again: %,.0f cr and %.0f standing, and days pass",
                    session.key(Action.CUT), world.beacon.fee(), DistressBeacon.STANDING_COST), NoticeLog.Kind.WARNING);
            return;
        }
        towAsked = 0f;
        session.show(world.beacon.call(), NoticeLog.Kind.IMPORTANT);
    }

    /**
     * P: the first press asks, a second within {@link #ABANDON_CONFIRM} seconds orders the crew to
     * the escape pods, and once they are going it launches the pods with whoever is aboard.
     */
    private void abandonShip() {
        if (AbandonShip.abandoning(game.player)) {
            world.abandon.launchNow(game.player);
            session.show("Launching the pods");
            return;
        }
        if (abandonAsked <= 0f) {
            abandonAsked = ABANDON_CONFIRM;
            session.show("Abandon ship? " + session.key(Action.ABANDON) + " again to send the crew to the escape pods", NoticeLog.Kind.WARNING);
            return;
        }
        abandonAsked = 0f;
        String refused = world.abandon.order(game.player);
        if (refused == null) session.show("Abandon ship! Crew to the escape pods", NoticeLog.Kind.IMPORTANT);
        else session.show("Can't abandon ship: " + refused);
    }

    // --- The mouse ------------------------------------------------------------------------

    /** The wheel scrolls the notice log while it is open, and zooms the view otherwise. */
    final InputProcessor wheel = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (session.logOpen) session.logScroll = Math.max(0, session.logScroll + Math.round(amountY));
            else viewports.zoom((float) Math.pow(1.15f, amountY));
            return true;
        }
    };

    /**
     * Left clicks on the outside view (not on a window, and not taken by the developer console, the
     * placing or a scenario): select the ship or station under the cursor, in the view or in a
     * picture-in-picture bubble.
     */
    final InputProcessor exteriorClicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT || hud.getViewMode().getMode() != ViewMode.EXTERIOR
                    || placing.isPlacing() || session.isOverlayOpen() || world.getCrash() != null) return false;
            return selectAt(screenX, screenY);
        }
    };

    /**
     * Selects ({@link #selectContact}) the craft under window pixel ({@code screenX}, {@code screenY})
     * (y down, as input has it), in the view or in a picture-in-picture bubble. False when nothing is there.
     */
    private boolean selectAt(int screenX, int screenY) {
        Vector2 at = tmp;
        // The names listed under a bubble, and the "[+] 3 others" that opens the list
        viewports.getUiViewport().unproject(at.set(screenX, screenY));
        if (pipeline.pip().click(at.x, at.y, this::selectContact)) return true;
        float mpp = pipeline.pip().unproject(screenX, Gdx.graphics.getHeight() - 1 - screenY, at);
        if (mpp < 0f) {
            viewports.getWorldViewport().unproject(at.set(screenX, screenY));
            mpp = viewports.metersPerPixel();
        }
        Entity picked = new ExteriorPick(world.engine, world.physics, world.docking).at(at.x, at.y, mpp, game.player);
        if (picked == null) return false;
        selectContact(picked);
        return true;
    }

    /**
     * Selects {@code picked}, a ship or station: a ship in the bubble becomes the locked target, as
     * Tab would lock it, and either becomes the navigation target.
     */
    void selectContact(Entity picked) {
        // Sensors hold a lock only on what they can see; a freighter latched to a port they cannot
        boolean lock = game.contacts.contains(picked, true)
                && picked.getComponent(SignatureComponent.class) != null;
        if (lock) session.gun.setTarget(picked);
        String uuid = world.universe.uuidOf(picked);
        String nav = uuid == null ? null : world.nav.target.select(uuid);
        HudMarkerComponent marker = MARKERS.get(picked);
        String name = marker != null && marker.label != null && !marker.label.isEmpty() ? marker.label : ShipNames.name(picked);
        if (lock) world.notices.replace("Target locked", "Target locked: " + name);
        else if (nav != null) world.notices.replace("Navigation target", nav);
    }
}
