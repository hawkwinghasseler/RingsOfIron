package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.VfdScreens;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Mfd;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.EnumSet;
import java.util.Set;

/**
 * FILAMENT × Stations: the 90s VFD art (documentation/ui-overhaul/style-vfd.md) on the stations
 * functional spec (documentation/ui-overhaul/functional-stations.md), as the mockups in
 * documentation/ui-overhaul/vfd draw it. The top column for every presence; at the helm and the
 * weapons console the band of two MFDs, the seat's cluster and the station board; at a gun its plate;
 * on deck the suit readout; the prompt line; and the projector's symbology over the world while seated.
 *
 * <p>Keys (16.2): O acknowledges while a master lamp flashes (Lighting keeps it otherwise); seated,
 * Alt with 1 to 6 puts that station's page on the right MFD and again cycles its pages, Alt+0 pins and
 * Alt+minus folds it, and Shift does the same for the left. Clicks: a master lamp acknowledges, a tile
 * turns an MFD to its alert's page, a soft key picks its page, pins or folds, and a board tile puts
 * that station's page on the right MFD.
 */
public class VfdStationsHud implements HudVariant, Filament.Painter, FilamentActor.Clicks {
    private HudVariantHost host;
    private FilamentActor actor;
    final Mfds mfds = new Mfds();
    final Pages pages = new Pages();
    final Band band = new Band(mfds, pages);
    final TopColumn top = new TopColumn();
    final World world = new World();
    final Deck deck = new Deck();
    /** The menus in setup mode and the map's plot, the same under all three FILAMENT HUDs. */
    private final VfdScreens screens = new VfdScreens();
    /** The panels in place of the game's windows (10). */
    final Panels panels = new Panels(this);
    /** The grid's offset for the band; the top column's, left of a side panel while one shows (6.3 rule 2). */
    private float topX, colX, bandY, height = Geo.H;
    private boolean blackout;
    private final Rectangle column = new Rectangle(), base = new Rectangle();

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        host.layer().addActor(actor);
        panels.attach(host);
    }

    @Override
    public void update(float delta) {
        HudData d = host.data();
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        // The panel first: the top column and the world make room for it
        panels.update(delta);
        layout(d, w, h, delta);
        boxes(actor);
    }

    /** The boxes that take clicks this frame: the lamps and tiles, the soft keys and the board. */
    void boxes(FilamentActor a) {
        a.clearBoxes();
        top.boxes(a, colX);
        if (!panels.fullScreen()) band.boxes(a, topX, bandY);
    }

    /** Reads this frame's data into every part, on a {@code w} x {@code h} stage. */
    void layout(HudData d, float w, float h, float delta) {
        height = h;
        topX = Geo.topX(w);
        colX = panels.beside() ? Geo.columnLeftOf(Geo.panelX(w)) : topX;
        bandY = Geo.bandY(h);
        blackout = blackout(d);
        Presence seat = d.ctx.presence;
        boolean shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        mfds.update(seat, d.ctx.condition, d.ctx.docked, shields, d.ctx.warp > 1, d.alerts.all(), delta);
        band.blackout = blackout;
        band.update(d);
        pages.update(d, mfds);
        top.update(d, blackout, delta);
        deck.update(d, !top.emergency.isEmpty(), delta);
        float floor = seat == Presence.HELM || seat == Presence.WEAPONS ? bandY + Geo.BAND_Y - 10f
                : seat == Presence.GUN ? h - 16f - 56f - 6f : h;
        // The prompt line sits over the world just above the band or the gun's plate: marks stay clear of it
        if (deck.prompt) floor = Math.min(floor, bandY + deck.promptY - 6f);
        // Beside a side panel the world ends at its plate, and its labels go to their edge markers (11.2)
        world.column = colX;
        world.panelLeft = panels.beside() ? Geo.panelX(w) - Terminal.PAD : Float.POSITIVE_INFINITY;
        world.hidden = panels.fullScreen() || panels.lost;
        world.update(d, host == null ? null : host.viewports(), w, h, topX, floor, blackout);
    }

    /** No reactor running: the seat's instruments go dark and the overlay runs on the emergency bus (5.1). */
    static boolean blackout(HudData d) {
        PowerFrame p = d.powerFrame;
        return d.ctx.ship != null && p.circuits.size > 0 && p.outputWatts <= 0;
    }

    @Override
    public void paint(Filament f) {
        // The ship lost: every tube off, every ghost showing, under the lost card
        f.dark = panels.lost;
        world.paint(f);
        if (!panels.fullScreen()) {
            f.origin(topX, bandY);
            band.paint(f);
            f.origin(colX, bandY);
            deck.paintPrompt(f);
            f.origin(0f, bandY);
            deck.paintSuit(f);
        }
        // The top column last: nothing but the out-of-fiction layer covers it (rule 1 of 6.3)
        paintTop(f);
    }

    /** The top column, left of a side panel while one shows; the Plot puts it over the map again. */
    void paintTop(Filament f) {
        f.origin(colX, 0f);
        top.paint(f);
        f.origin(0f, 0f);
    }

    @Override
    public void clicked(int id) {
        HudData d = host.data();
        if (id == TopColumn.CLICK_WARN || id == TopColumn.CLICK_CAUT) {
            d.alerts.acknowledgeAll();
        } else if (id >= TopColumn.CLICK_TILE && id < TopColumn.CLICK_TILE + Annunciator.TILES) {
            Annunciator.Tile t = top.ann.slots[id - TopColumn.CLICK_TILE];
            if (t != null && t.alert != null && commandSeat(d)) {
                boolean shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
                mfds.show(Mfds.home(t.alert, shields));
            }
        } else if (id >= Band.CLICK_SOFT && id < Band.CLICK_SOFT + 20) {
            int side = (id - Band.CLICK_SOFT) / 10, slot = (id - Band.CLICK_SOFT) % 10;
            Mfd m = side == 0 ? mfds.left : mfds.right;
            if (slot == Band.PIN) mfds.pin(m);
            else if (slot == Band.FOLD) mfds.fold(m);
            else mfds.softKey(m, slot);
        } else if (id >= Band.CLICK_BOARD && id < Band.CLICK_BOARD + Mfds.STATIONS.length) {
            mfds.station(mfds.right, id - Band.CLICK_BOARD);
        }
    }

    private static boolean commandSeat(HudData d) {
        return d.ctx.presence == Presence.HELM || d.ctx.presence == Presence.WEAPONS;
    }

    @Override
    public boolean keyDown(int keycode) {
        // A panel's keys first: its pages, its lists and its soft keys
        if (panels.keyDown(keycode)) return true;
        HudData d = host.data();
        if (keycode == Keys.O && (top.ann.warnUnacked || top.ann.cautUnacked)) {
            d.alerts.acknowledgeAll();
            return true;
        }
        boolean alt = Gdx.input.isKeyPressed(Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Keys.ALT_RIGHT);
        if (!alt || !commandSeat(d)) return false;
        boolean shift = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);
        return mfdKey(keycode, shift);
    }

    /** Alt with {@code keycode}, seated: a station's page, pin or fold, on the right MFD or with Shift the left. */
    boolean mfdKey(int keycode, boolean shift) {
        Mfd m = shift ? mfds.left : mfds.right;
        if (keycode >= Keys.NUM_1 && keycode <= Keys.NUM_6) {
            mfds.station(m, keycode - Keys.NUM_1);
            return true;
        }
        if (keycode == Keys.NUM_0) {
            mfds.pin(m);
            return true;
        }
        if (keycode == Keys.MINUS) {
            mfds.fold(m);
            return true;
        }
        return false;
    }

    @Override
    public void detach() {
        panels.detach();
        if (actor != null) actor.dispose();
        actor = null;
        screens.dispose();
    }

    @Override
    public void panels(Array<Rectangle> out) {
        // The top column, and the band or the gun's plate under the world's floor (rule 4 of 6.3)
        world.panels(out, topX, height, column, base);
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
        // The optics belong to the seats (11.3): the lock's; the nav target's at the helm; the own
        // ship's once its hull is under 12 pixels
        if (!world.on) return false;
        switch (role) {
            case NAV:
                return world.seat == Presence.HELM;
            case OWN:
                return world.ownTiny;
            default:
                return true;
        }
    }

    @Override
    public Set<ClassicPart> keeps() {
        // Every classic piece has its place here: the panels in the band and its pages, the missile
        // banner and status column in the top column, markers and threats in the projected world,
        // key hints, the docked banner and the E prompt in the prompt line and the status strip
        return EnumSet.noneOf(ClassicPart.class);
    }

    @Override
    public boolean replaces(ClassicWindow window) {
        return panels.replaces(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return panels.open(window);
    }

    @Override
    public boolean showing(ClassicWindow window) {
        return panels.showing(window);
    }

    @Override
    public void close(ClassicWindow window) {
        panels.close(window);
    }

    @Override
    public ShellLook shellLook() {
        return screens.shellLook();
    }

    @Override
    public MapPainter mapPainter() {
        return screens.mapPainter();
    }

    /** The map in the Plot's terminal, which has its title and its help, or on its own with them over the plot. */
    void mapFramed(boolean on) {
        screens.framed(on, 0f);
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        screens.resize(uiScale);
    }
}
