package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.VfdScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * FILAMENT × Task: the 90s VFD art (documentation/ui-overhaul/style-vfd.md) on the task-driven
 * functional spec (documentation/ui-overhaul/functional-task.md), as the mockups in
 * documentation/ui-overhaul/vfd draw it: a late-90s digital dash with no racks across the view. The
 * cluster (chronometer over the tell-tales) top left with the captain's LCD under it on deck; the
 * message centre top middle, a red window for the one warning over an amber row of cautions, with the
 * crisis panel under it; the trip computer (objectives, then news) top right, the target card under
 * it; the schematic bottom left; the flight instrument and the helm's line bottom middle; the weapons
 * pod and the scope bottom right; the key strip along the bottom; and the overlays in the world.
 *
 * <p>Clicks: the message centre acknowledges; the scope's − and + set its range and a contact on it is
 * picked as on the classic scope; the helm's line lists the helm's orders. Keys: backslash
 * acknowledges, as a click on the message centre does; every other key goes on to the game, the key
 * strip counting the uses of the keys it shows.
 */
public class VfdTaskHud implements HudVariant, Filament.Painter, FilamentActor.Clicks {
    static final int CLICK_BAND = 1;

    private HudVariantHost host;
    private FilamentActor actor;
    final Posts posts = new Posts();
    final Catalogue catalogue = new Catalogue();
    final Cluster cluster = new Cluster();
    final Personal personal = new Personal();
    final Trip trip = new Trip();
    final TargetCard target = new TargetCard();
    final Weapons weapons = new Weapons();
    final Scope scope = new Scope();
    final Schematic schematic = new Schematic();
    final Flight flight = new Flight();
    final Helm helm = new Helm();
    final KeyStrip keys = new KeyStrip();
    final Deck deck = new Deck();
    final Crisis crisis = new Crisis();
    final World world = new World();
    /** The menus in setup mode and the map's plot, the same under all three FILAMENT HUDs. */
    private final VfdScreens screens = new VfdScreens();

    /** The groups' origins on the stage. */
    float width = Geo.W, height = Geo.H, midX, rightX, bottomY;
    float time;
    private final Array<String> elsewhere = new Array<>();

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        host.layer().addActor(actor);
    }

    @Override
    public void update(float delta) {
        HudData d = host.data();
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        UiHud hud = host.hud();
        boolean screen = hud != null && (hud.openWindow() != null || hud.isMapOpen());
        Entity console = hud == null || hud.getInteractPromptSystem() == null ? null : hud.getInteractPromptSystem().getNearest();
        layout(d, w, h, delta, screen, host.viewports(), console);
        boxes(actor);
    }

    /** Reads this frame's data into every part and places them, on a {@code w} x {@code h} stage. */
    void layout(HudData d, float w, float h, float delta, boolean screen, ViewportManager views, Entity console) {
        width = w;
        height = h;
        midX = Geo.midX(w);
        rightX = Geo.rightX(w);
        bottomY = Geo.bottomY(h);
        time += delta;
        posts.update(d, screen);
        engines(d);
        catalogue.update(d, posts, time);
        flight.update(d, posts);
        boolean burnShown = flight.on && flight.mode == Flight.Mode.BURN;
        cluster.update(d, burnShown);
        personal.update(d, posts, fireHere(d));
        trip.update(d, posts, delta);
        target.update(d, posts, flight.on && flight.mode == Flight.Mode.DOCK);
        weapons.update(d, posts);
        scope.update(d, posts);
        schematic.update(d, posts, catalogue.schematicLit());
        helm.update(d, posts, flight.on, time);
        crisis.update(d, posts);
        // The deck's tags keep between the side columns, under the band and over the helm's line
        float l = Geo.LX + Geo.CW + 8f, t = Geo.BAND_B + 8f, r = rightX + Geo.RX - 8f, b = bottomY + Geo.HLM_Y - 10f;
        // The prompt keeps to the bottom edge, just over the key strip and short of HLM
        deck.slot(Geo.LX, bottomY + Geo.HNT_Y - 6f - Deck.PROMPT_H, midX + Geo.FLT_X - 8f - Geo.LX);
        deck.update(d, posts, views, console, w, h, l, t, r, b);
        // A key on screen elsewhere is not repeated on the strip
        elsewhere.clear();
        for (int i = 0; i < flight.keyCount; i++) elsewhere.add(flight.keys[i]);
        if (catalogue.warning != null && !catalogue.warning.key.isEmpty()) elsewhere.add(catalogue.warning.key.toUpperCase(Locale.ROOT));
        crisis.shownKeys(elsewhere);
        deck.shownKeys(elsewhere);
        keys.update(d, posts, time, elsewhere);
        worldLayout(d, views, burnShown);
    }

    /** The engines and how many work, for DRIVE's warning: the game's own subsystems. */
    private void engines(HudData d) {
        catalogue.engines = catalogue.enginesWorking = -1;
        ShipSubsystemRegistryComponent reg = d.ctx.ship == null ? null : d.ctx.ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (reg == null) return;
        int n = 0, working = 0;
        for (Entity e : reg.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null || s.type != SubsystemComponent.SubsystemType.ENGINE) continue;
            n++;
            if (s.isOperational && !s.isDestroyed() && !s.isDisabled() && !s.shorted && !s.isStunned()) working++;
        }
        catalogue.engines = n;
        catalogue.enginesWorking = working;
    }

    /** A fire in the captain's room: PER's air reads HOT. */
    private static boolean fireHere(HudData d) {
        String room = d.captainStatus.room;
        if (room == null || room.isEmpty()) return false;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if ("FIRE".equals(a.code) && room.equalsIgnoreCase(a.room)) return true;
        }
        return false;
    }

    /** The world's overlays, clear of every widget this frame. */
    private void worldLayout(HudData d, ViewportManager views, boolean burnShown) {
        world.clearBoxes();
        world.box(Geo.LX - 6f, 0f, Geo.CW + 12f, Geo.ANN_Y + Geo.ANN_H + 6f);
        world.box(midX + Geo.BX - 6f, 0f, Geo.BW + 12f, Geo.BAND_B);
        if (crisis.kind != Crisis.Kind.NONE) world.box(midX + Geo.CRS_X - 6f, Geo.CRS_Y - 6f, Geo.CRS_W + 12f, crisis.height() + 12f);
        float tripB = trip.on ? trip.newsY() + trip.newsHeight() : 0f;
        if (tripB > 0f) world.box(rightX + Geo.RX - 6f, 0f, Geo.CW + 12f, tripB + 6f);
        if (target.on) world.box(rightX + Geo.RX - 6f, Geo.TGT_Y - 6f, Geo.CW + 12f, target.height() + 12f);
        if (schematic.on) world.box(Geo.LX - 6f, bottomY + Geo.SCH_Y - 6f, Geo.CW + 12f, Geo.SCH_H + 12f);
        if (flight.on) world.box(midX + Geo.FLT_X - 6f, bottomY + Geo.FLT_Y - 6f, Geo.FLT_W + 12f, Geo.FLT_H + 12f);
        if (helm.on) world.box(midX + helm.x - 4f, bottomY + helm.y - 4f, helm.w + 8f, Geo.HLM_H + 8f);
        if (scope.on || weapons.on) {
            float top = Math.min(scope.on ? Geo.SCOPE_POD_Y : Geo.POD_B, weapons.on ? weapons.top() - 8f : Geo.POD_B);
            float left = weapons.on ? (weapons.expanded ? Geo.POD_X : Weapons.COMPACT_X - 8f) : Geo.POD_STEP_X;
            world.box(rightX + left, bottomY + top, Geo.POD_R - left, Geo.POD_B - top);
        }
        world.box(Geo.LX - 3f, bottomY + Geo.HNT_Y - 3f, width, Geo.HNT_H + 6f);
        float l = Geo.LX + Geo.CW + 20f, t = Geo.BAND_B + 14f, r = rightX + Geo.RX - 20f;
        float b = bottomY + (flight.on ? Geo.FLT_Y - (helm.on ? Geo.HLM_H + 12f : 0f) : Geo.HLM_Y) - 20f;
        // The gun table stands taller than HLM beside it: the bottom edge's arrows stay above both
        if (weapons.on && weapons.expanded) b = Math.min(b, bottomY + weapons.top() - 20f);
        world.update(d, posts, views, width, height, l, t, r, b, burnShown);
    }

    /** The boxes that take clicks this frame: the message centre, the scope's keys and contacts, the helm's line. */
    void boxes(FilamentActor a) {
        a.clearBoxes();
        if (catalogue.warning != null || catalogue.row.size > 0) {
            a.box(CLICK_BAND, midX + Geo.BX, Geo.WARN_Y, Geo.BW, Geo.CAUT_Y + Geo.CAUT_H - Geo.WARN_Y);
        }
        if (posts.screen) return;
        scope.boxes(a, rightX, bottomY);
        helm.boxes(a, midX, bottomY);
    }

    @Override
    public void paint(Filament f) {
        // The projected world first: every widget covers it
        f.origin(0f, 0f);
        world.paint(f);
        deck.paintTags(f);
        deck.paintPrompt(f);
        // Top left: the cluster, CHR over ANN, and PER under it on deck
        cluster.paintChr(f, !posts.screen && posts.common());
        if (!posts.screen && posts.common()) {
            Cluster.paintAnn(f, catalogue, time);
            personal.paint(f);
        }
        // Top middle: the message centre, whenever there is something in it, and the crisis panel
        f.origin(midX, 0f);
        if (catalogue.warning != null || catalogue.row.size > 0) Band.paint(f, catalogue, time);
        crisis.paint(f, time);
        if (posts.screen || !posts.common()) return;
        // Top right: the trip computer and the target card
        f.origin(rightX, 0f);
        trip.paint(f);
        target.paint(f);
        // Bottom left: the schematic and the key strip
        f.origin(0f, bottomY);
        schematic.paint(f);
        keys.paint(f);
        // Bottom middle: FLT, and the helm's line over it or in its place
        f.origin(midX, bottomY);
        flight.paint(f);
        helm.paint(f, time);
        // Bottom right: the pod, the weapons and the scope
        f.origin(rightX, bottomY);
        pod(f);
        weapons.paint(f);
        scope.paint(f);
    }

    /** The bottom right pod: the scope's column, and the weapons' beside it stepping down to their module's top. */
    private void pod(Filament f) {
        if (!scope.on && !weapons.on) return;
        float bottom = Geo.POD_B, radius = 8f;
        if (weapons.on) {
            float left = weapons.expanded ? Geo.POD_X : Weapons.COMPACT_X - 8f, wTop = weapons.top() - 8f;
            if (scope.on) f.rackColumns(new float[] {left, Geo.POD_STEP_X, Geo.POD_R}, new float[] {wTop, Geo.SCOPE_POD_Y}, bottom, radius);
            else f.rackColumns(new float[] {left, Geo.POD_STEP_X}, new float[] {wTop}, bottom, radius);
        } else {
            f.rackColumns(new float[] {Geo.POD_STEP_X, Geo.POD_R}, new float[] {Geo.SCOPE_POD_Y}, bottom, radius);
        }
    }

    @Override
    public void clicked(int id) {
        if (host != null) click(host.data(), id);
    }

    /** A click on box {@code id}. */
    void click(HudData d, int id) {
        switch (id) {
            case CLICK_BAND:
                acknowledge(d);
                return;
            case Scope.CLICK_OUT:
                scope.zoom(1);
                return;
            case Scope.CLICK_IN:
                scope.zoom(-1);
                return;
            case Helm.CLICK:
                helm.toggle();
                return;
            case Helm.CLICK_LIST:
                helm.open = false;
                return;
            default:
        }
        Entity contact = scope.picked(id);
        UiHud hud = host == null ? null : host.hud();
        if (contact != null && hud != null && hud.getRadar() != null) hud.getRadar().pick(contact);
    }

    void acknowledge(HudData d) {
        catalogue.acknowledge();
        d.alerts.acknowledgeAll();
    }

    @Override
    public boolean keyDown(int keycode) {
        return host != null && key(host.data(), keycode);
    }

    /** A key the classic HUD did not use: true for backslash, which acknowledges; the rest go on to the game. */
    boolean key(HudData d, int keycode) {
        if (keycode == Keys.BACKSLASH) {
            acknowledge(d);
            return true;
        }
        keys.pressed(keycode);
        // A helm order's own key closes the list, and the order still goes to the game
        if (helm.open) {
            for (int i = 0; i < Helm.ACTIONS.length; i++) {
                if (helm.keys[i] != null && helm.keys[i].equalsIgnoreCase(com.ringsofiron.coreengine.input.Action.keyName(keycode))) helm.open = false;
            }
        }
        return false;
    }

    @Override
    public void detach() {
        if (actor != null) actor.dispose();
        actor = null;
        host = null;
        screens.dispose();
    }

    @Override
    public void panels(Array<Rectangle> out) {
        // Every widget's box, as OVL keeps clear of them
        world.panels(out, height);
    }

    @Override
    public boolean drawsInsets() {
        return true;
    }

    @Override
    public void insetRoom(Insets.Room out) {
        out.set(Optic.SIDE, Optic.ABOVE, Optic.BELOW);
    }

    @Override
    public boolean wantsInset(Insets.Role role) {
        // Only while OVL shows to draw their rings: not over a screen, nor on deck
        return world.on;
    }

    @Override
    public Set<ClassicPart> keeps() {
        // Every classic piece has its place here: the flight panel and gauges in FLT, CHR and ANN; the
        // radar in SNS; the ship panel in SCH and the band; the orbit panel's burn in FLT and CHR; the
        // missile banner in the band; the threat overlay and markers in OVL; the status column across
        // CHR, OBJ, NTF, TGT and HLM; the key hints in HNT; the docked banner in FLT's DEPARTURE; the
        // E prompt in INT
        return EnumSet.noneOf(ClassicPart.class);
    }

    @Override
    public ShellLook shellLook() {
        return screens.shellLook();
    }

    @Override
    public MapPainter mapPainter() {
        return screens.mapPainter();
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        screens.resize(uiScale);
    }
}
