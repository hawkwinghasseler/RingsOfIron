package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W7, the inset views: the game's magnified insets in the kit's rings, clear of every widget, never doubled. */
class InsetTest {
    /** 10 m a unit, the ship at the middle of a 1280 x 800 stage, y up as the UI camera has it. */
    private static final class View extends ViewportManager {
        @Override
        public Vector2 worldToUi(float worldX, float worldY, Vector2 out) {
            return out.set(640f + worldX / 10f, 400f + worldY / 10f);
        }

        @Override
        public float metersPerUiUnit() {
            return 10f;
        }

        @Override
        public float metersPerPixel() {
            return 10f;
        }
    }

    /** The bottom rack's top, y down. */
    private static final float FLOOR = 700f;

    private final HudData d = new HudData();
    private final VfdContextHud hud = new VfdContextHud();
    private final World w = hud.world;
    private final View view = new View();
    /** The centre's box (l, t, r, b) the edge markers keep to. */
    private final float[] centre = {330f, 120f, 950f, 640f};
    private boolean lockShown;

    @BeforeEach
    void atTheHelmOutside() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
        d.flightStatus.present = true;
    }

    /** The world laid out as the HUD lays it, over a screen or not. */
    private void frame(boolean fullScreen) {
        hud.sit.update(d, false, false, fullScreen, 0.016f);
        w.update(d, hud.sit, view, 1280f, 800f, FLOOR, centre, lockShown, false, false, false, Flight.ORBIT);
    }

    private Contact contact(String label, Relation relation, float x, float y) {
        Contact c = d.contactFrame.add();
        c.entity = new Entity();
        c.label = label;
        c.kind = Kind.SHIP;
        c.relation = relation;
        c.x = x;
        c.y = y;
        c.range = (float) Math.hypot(x, y);
        c.radius = 20f;
        return c;
    }

    /** The game's inset onto {@code c}, its middle at stage ({@code x}, {@code y}), y down. */
    private Insets.Inset inset(Contact c, float x, float y) {
        Insets.Inset in = d.insets.add();
        in.entity = c.entity;
        in.locked = c.locked;
        in.nav = c.nav;
        in.label = c.label;
        in.radius = 56f;
        in.x = x;
        in.y = 800f - y;
        in.craftX = 640f + c.x / 10f;
        in.craftY = 400f + c.y / 10f;
        in.onScreen = in.craftX >= 0f && in.craftX <= 1280f && in.craftY >= 0f && in.craftY <= 800f;
        in.centered = in.onScreen && Math.abs(in.craftX - x) < 1f && Math.abs(in.craftY - in.y) < 1f;
        return in;
    }

    @Test
    void aCraftWithAnInsetIsMarkedAndLabelledThereAsW1WouldAndTheMarksUnderItAreCounted() {
        Contact raider = contact("Raider 6 (surrendered)", Relation.HOSTILE, 3_000f, 1_000f);
        raider.locked = true;
        // A freighter's glyph a few units off, under the raider's inset
        contact("Freighter", Relation.NEUTRAL, 3_100f, 900f);
        // and one well clear of it
        contact("Tug", Relation.NEUTRAL, -2_000f, -1_000f);
        inset(raider, 940f, 300f);
        frame(false);
        assertEquals(1, w.insetCount);
        Optic.Look look = w.looks[0];
        assertEquals("RAIDER 6", look.name, "the game's tag left off");
        assertEquals(Fmt.rangeText(raider.range), look.sub, "its range, while the Lock group is down");
        assertEquals(Phosphor.RED, look.color);
        assertEquals(Phosphor.ZINC, look.mark, "the lock's brackets zinc round a hostile's red ring");
        assertEquals(Phosphor.ZINC, look.text);
        assertTrue(look.lock);
        assertFalse(look.nav);
        assertEquals(1, look.others, "the freighter under it");
        // Neither the raider's brackets nor the freighter's glyph in the view: only the tug's
        assertEquals(1, w.markCount);
        assertEquals(440f, w.marks[0].x, 1e-3f);
        // With the Lock group up its range is there, not on the inset
        lockShown = true;
        frame(false);
        assertEquals("", w.looks[0].sub);
    }

    @Test
    void aCraftOutOfViewWithAnInsetHasNoEdgeMarker() {
        Contact nav = contact("Highport", Relation.NEUTRAL, 9_000f, 0f);
        nav.kind = Kind.STATION;
        nav.nav = true;
        contact("Raider 7", Relation.HOSTILE, -9_000f, 0f);
        frame(false);
        assertEquals(2, w.edgeCount, "both off screen, each with its edge marker");
        inset(nav, 1150f, 400f);
        frame(false);
        assertEquals(1, w.edgeCount, "the inset at the edge points the way instead");
        assertTrue(w.edges[0].text.startsWith("RAIDER 7"), w.edges[0].text);
        assertTrue(w.looks[0].nav);
        assertFalse(w.looks[0].lock);
        assertEquals("HIGHPORT", w.looks[0].name);
    }

    @Test
    void theOwnShipsInsetGoesUnnamedAndQuietAndTheVelocityMarkersStandOutsideIt() {
        Insets.Inset own = d.insets.add();
        own.entity = d.ctx.ship;
        own.own = true;
        own.radius = 56f;
        own.x = 640f;
        own.y = 400f;
        own.craftX = 640f;
        own.craftY = 400f;
        own.onScreen = own.centered = true;
        d.flightStatus.vx = 30f;
        frame(false);
        assertEquals(1, w.insetCount);
        assertEquals("", w.looks[0].name);
        assertEquals(Drive.DIM, w.looks[0].level);
        assertTrue(w.vel);
        assertTrue(w.proShown && w.retroShown);
        assertTrue(w.proX - 640f >= 56f + Optic.RING_GAP + 7.5f + 4f, "prograde clear of the ring: " + w.proX);
        assertTrue(640f - w.retroX >= 56f + Optic.RING_GAP + 7.5f + 4f, "retrograde clear of the ring: " + w.retroX);
    }

    @Test
    void aVelocityMarkerOnAnotherCraftsInsetIsLeftOut() {
        Contact freighter = contact("Freighter", Relation.NEUTRAL, 3_000f, 0f);
        freighter.locked = true;
        // Prograde 70 units to the right of the ship, on the freighter's inset there
        inset(freighter, 720f, 400f);
        d.flightStatus.vx = 30f;
        frame(false);
        assertTrue(w.vel);
        assertFalse(w.proShown, "prograde at " + w.proX + " is on the freighter's picture");
        assertTrue(w.retroShown);
    }

    @Test
    void theInsetsKeepOffEveryWidgetTheStripAndTheRackAndAreAskedForOnlyWhileTheWorldShows() {
        w.clearBoxes();
        w.box(Geo.LX, 40f, Geo.CW, 200f);
        frame(false);
        assertTrue(hud.drawsInsets());
        assertTrue(hud.wantsInset(Insets.Role.OWN));
        assertTrue(hud.wantsInset(Insets.Role.LOCK));
        assertTrue(hud.wantsInset(Insets.Role.NAV));
        Insets.Room room = new Insets.Room();
        hud.insetRoom(room);
        assertEquals(Optic.SIDE, room.side);
        assertEquals(Optic.ABOVE, room.above);
        assertEquals(Optic.BELOW, room.below);
        Array<Rectangle> panels = new Array<>();
        w.panels(panels, 1280f, 800f);
        assertEquals(3, panels.size, "the widget, the status strip and the rack");
        Rectangle widget = panels.get(0), strip = panels.get(1), rack = panels.get(2);
        // The widget padded and turned over into the UI camera's y up
        assertEquals(Geo.LX - 6f, widget.x, 1e-3f);
        assertEquals(800f - 240f - 6f, widget.y, 1e-3f);
        assertEquals(Geo.CW + 12f, widget.width, 1e-3f);
        assertEquals(212f, widget.height, 1e-3f);
        assertTrue(strip.contains(640f, 800f - Geo.STRIP_B / 2f), "the status strip, y up");
        assertTrue(rack.contains(640f, (800f - FLOOR) / 2f), "the rack");
        assertFalse(rack.contains(640f, 400f));
        // At a gun the world shows the Lock and hostiles only
        d.ctx.presence = Presence.GUN;
        frame(false);
        assertTrue(hud.wantsInset(Insets.Role.LOCK));
        assertFalse(hud.wantsInset(Insets.Role.NAV));
        d.ctx.presence = Presence.HELM;
        // Over a screen the world's overlays are down, and so are the insets
        frame(true);
        assertFalse(hud.wantsInset(Insets.Role.LOCK));
        assertFalse(hud.wantsInset(Insets.Role.OWN));
        panels.clear();
        w.panels(panels, 1280f, 800f);
        assertEquals(0, panels.size);
        // as on deck
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        frame(false);
        assertFalse(hud.wantsInset(Insets.Role.NAV));
    }
}
