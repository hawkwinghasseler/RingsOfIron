package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
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

/** The optics (11.3): the game's magnified insets in the kit's rings, by seat, never doubled. */
class InsetTest {
    /** {@code mpu} m a unit and a pixel, the ship at the middle of a 1280 x 800 stage, y up as the UI camera has it. */
    private static final class View extends ViewportManager {
        float mpu = 10f;

        @Override
        public Vector2 worldToUi(float worldX, float worldY, Vector2 out) {
            return out.set(640f + worldX / mpu, 400f + worldY / mpu);
        }

        @Override
        public float metersPerUiUnit() {
            return mpu;
        }

        @Override
        public float metersPerPixel() {
            return mpu;
        }
    }

    private final HudData d = new HudData();
    private final VfdStationsHud hud = new VfdStationsHud();
    private final View view = new View();

    @BeforeEach
    void atTheHelm() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        // A 40 m hull: 4 pixels at 10 m a pixel
        ship.add(new ShipGridComponent(20, 8, 2f));
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
    }

    /** The world laid out as the HUD lays it at the helm, over the band. */
    private void frame() {
        float floor = Geo.bandY(800f) + Geo.BAND_Y - 10f;
        hud.world.update(d, view, 1280f, 800f, Geo.topX(1280f), floor, false);
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
        in.onScreen = true;
        in.centered = Math.abs(in.craftX - x) < 1f && Math.abs(in.craftY - in.y) < 1f;
        return in;
    }

    @Test
    void theLocksOpticCarriesItsMarkAndNameAndTheMarksUnderItAreCounted() {
        Contact raider = contact("Raider 6 (surrendered)", Relation.HOSTILE, 3_000f, 1_000f);
        raider.locked = true;
        contact("Pirate raider 7", Relation.HOSTILE, 3_100f, 900f);
        contact("Tug", Relation.NEUTRAL, -2_000f, -1_000f);
        inset(raider, 940f, 300f);
        frame();
        World w = hud.world;
        assertEquals(1, w.insetCount);
        Optic.Look look = w.looks[0];
        assertEquals("RAIDER 6", look.name);
        assertEquals("", look.sub, "the lock by name only: its numbers are in the cluster");
        assertEquals(Phosphor.RED, look.color);
        assertEquals(Phosphor.ZINC, look.mark);
        assertEquals(Phosphor.ZINC, look.text, "names in zinc, as the view writes them");
        assertTrue(look.lock);
        assertEquals(1, look.others, "the other raider under it");
        assertEquals(1, w.markCount, "only the tug in the view");
        assertEquals(440f, w.marks[0].x, 1e-3f);
    }

    @Test
    void theNavTargetsOpticIsTheHelmsAndTheOwnShipsOnlyOnceItsHullIsTiny() {
        frame();
        assertTrue(hud.wantsInset(Insets.Role.LOCK));
        assertTrue(hud.wantsInset(Insets.Role.NAV));
        assertTrue(hud.wantsInset(Insets.Role.OWN), "a 40 m hull is 4 pixels at 10 m a pixel");
        // Closer in, 40 m is 40 pixels
        view.mpu = 1f;
        frame();
        assertFalse(hud.wantsInset(Insets.Role.OWN));
        assertFalse(hud.world.ownTiny);
        d.ctx.presence = Presence.WEAPONS;
        frame();
        assertTrue(hud.wantsInset(Insets.Role.LOCK));
        assertFalse(hud.wantsInset(Insets.Role.NAV), "the weapons seat's optics show the target only");
        // On deck the projector is off, and so are the optics
        d.ctx.presence = Presence.DECK;
        frame();
        assertFalse(hud.wantsInset(Insets.Role.LOCK));
    }

    @Test
    void theOpticsKeepOffTheTopColumnAndTheBand() {
        frame();
        Array<Rectangle> panels = new Array<>();
        hud.panels(panels);
        assertEquals(3, panels.size, "the top column, the band and the ship");
        Rectangle column = panels.get(0), band = panels.get(1), ship = panels.get(2);
        // A 40 x 16 m hull at 10 m a unit, and a margin
        assertTrue(ship.contains(640f, 400f) && ship.contains(640f + 8f, 400f - 8f) && ship.width < 20f, "the ship: " + ship);
        assertTrue(column.contains(640f, 800f - 60f), "the top column, y up");
        assertTrue(band.contains(640f, 100f), "the band");
        assertFalse(band.contains(640f, 400f));
        assertFalse(column.contains(640f, 400f));
        // and off the edge markers: the nav target off to the right
        contact("Raider 7", Relation.HOSTILE, 9_000f, 0f).nav = true;
        frame();
        panels.clear();
        hud.panels(panels);
        assertEquals(1, hud.world.edgeCount);
        World.Edge e = hud.world.edges[0];
        assertEquals(4, panels.size, "the column, the band, the ship and the marker");
        assertTrue(panels.get(3).contains(e.x, 800f - e.y), "the marker's chevron, y up");
        assertTrue(panels.get(3).contains(e.x - 60f, 800f - e.y), "its label");
    }
}
