package com.ringsofiron.uihud.variants.vfd.task;

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
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game's magnified insets under OVL: drawn in FILAMENT's style, kept off the widgets, never doubled. */
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
    }

    private final HudData d = new HudData();
    private final VfdTaskHud hud = new VfdTaskHud();
    private final View view = new View();

    @BeforeEach
    void atTheHelmOutside() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
        d.flightStatus.present = true;
    }

    private void frame(boolean screen) {
        hud.layout(d, 1280f, 800f, 0.016f, screen, view, null);
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
    void aCraftWithAnInsetIsMarkedAndNamedThereAndTheMarksUnderItAreCounted() {
        Contact raider = contact("Raider 6 (surrendered)", Relation.HOSTILE, 3_000f, 1_000f);
        raider.locked = true;
        // A freighter's glyph a few units off, under the raider's inset
        contact("Freighter", Relation.NEUTRAL, 3_100f, 900f);
        // and one well clear of it
        contact("Tug", Relation.NEUTRAL, -2_000f, -1_000f);
        inset(raider, 940f, 300f);
        frame(false);
        World w = hud.world;
        assertEquals(1, w.insetCount);
        Optic.Look look = w.looks[0];
        assertEquals("RAIDER 6", look.name, "the game's tag left off");
        assertEquals(Phosphor.RED, look.color);
        assertEquals(Phosphor.ZINC, look.mark, "the lock's brackets zinc round a hostile's red ring");
        assertTrue(look.lock);
        assertFalse(look.nav);
        assertEquals(1, look.others, "the freighter under it");
        // Neither the raider's brackets nor the freighter's glyph in the view: only the tug's
        assertEquals(1, w.markCount);
        assertEquals(440f, w.marks[0].x, 1e-3f);
    }

    @Test
    void aCraftOutOfViewWithAnInsetHasNoEdgeArrow() {
        Contact nav = contact("Highport", Relation.NEUTRAL, 9_000f, 0f);
        nav.kind = Kind.STATION;
        nav.nav = true;
        Contact raider = contact("Raider 7", Relation.HOSTILE, -9_000f, 0f);
        frame(false);
        assertEquals(2, hud.world.edgeCount, "both off screen, each with its arrow");
        inset(nav, 1150f, 400f);
        frame(false);
        assertEquals(1, hud.world.edgeCount, "the inset at the edge points the way instead");
        assertEquals("RAIDER 7", hud.world.edges[0].text);
        assertTrue(hud.world.looks[0].nav);
        assertEquals("HIGHPORT", hud.world.looks[0].name);
        assertTrue(raider.range > 0f);
    }

    @Test
    void theOwnShipsInsetGoesUnnamedAndTheVelocityMarkersStandOutsideIt() {
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
        World w = hud.world;
        assertEquals(1, w.insetCount);
        assertEquals("", w.looks[0].name);
        assertTrue(w.vel);
        assertTrue(w.proX - 640f >= 56f + Optic.RING_GAP + 7.5f + 4f, "prograde clear of the ring: " + w.proX);
    }

    @Test
    void aVelocityMarkerOnAnotherCraftsInsetIsLeftOut() {
        Contact freighter = contact("Freighter", Relation.NEUTRAL, 3_000f, 0f);
        freighter.locked = true;
        // Prograde 70 units to the right of the ship, on the freighter's inset there
        inset(freighter, 720f, 400f);
        d.flightStatus.vx = 30f;
        frame(false);
        World w = hud.world;
        assertTrue(w.vel);
        assertFalse(w.proShown, "prograde at " + w.proX + " is on the freighter's picture");
        assertTrue(w.retroShown);
    }

    @Test
    void theGameKeepsTheInsetsOffEveryWidgetAndAsksForThemOnlyWhileOvlShows() {
        frame(false);
        assertTrue(hud.wantsInset(Insets.Role.LOCK));
        assertTrue(hud.drawsInsets());
        Insets.Room room = new Insets.Room();
        hud.insetRoom(room);
        assertEquals(Optic.BELOW, room.below);
        Array<Rectangle> panels = new Array<>();
        hud.panels(panels);
        // FLT at the helm, turned over into the UI camera's y up
        boolean flt = false;
        for (Rectangle r : panels) {
            if (Math.abs(r.x - (Geo.FLT_X - 6f)) < 0.01f && Math.abs(r.y - (800f - (Geo.FLT_Y + Geo.FLT_H + 6f))) < 0.01f) flt = true;
        }
        assertTrue(flt, "FLT among " + panels);
        // Over a screen OVL is down, and so are the insets
        frame(true);
        assertFalse(hud.wantsInset(Insets.Role.LOCK));
        assertFalse(hud.wantsInset(Insets.Role.OWN));
    }
}
