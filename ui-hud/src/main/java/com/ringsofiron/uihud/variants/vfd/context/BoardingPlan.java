package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.vfd.DeckPlan;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The plan of the ship boarded (functional-context 10.4; mockup 11.13), on the dot panel as the ship's
 * own plan is: its rooms' walls and hazards, every system's place a faint box of dots, the parts marked
 * to strip in corner brackets with what is being done to each, a part already off an empty box where
 * it was, the latch lit in the hull with where it leads, our boarding party where they stand (a tick
 * each side at work), and how many of its crew surrendered.
 */
final class BoardingPlan {
    /** How many of the parts being worked on are named beside their marks. */
    static final int LABELS = 4;
    /** How much of a part's status its label shows. */
    static final int LABEL_CELLS = 24;

    final DeckPlan deck = new DeckPlan();
    private final float[] fit = new float[3];
    private Entity craft, ours;
    private ShipGridComponent grid;
    private final float[] labelBox = new float[4 * LABELS];

    void update(HudData d, BoardingBoard b) {
        craft = b.craft;
        ours = d.ctx.ship;
        deck.update(d, craft);
        grid = craft == null ? null : craft.getComponent(ShipGridComponent.class);
    }

    /**
     * The plan in the pane {@code x, y, w, h}: the latch's line over the hull, the surrendered under it.
     * {@code focus} is the part in the list's focus, or null.
     */
    void paint(Filament f, float x, float y, float w, float h, BoardingBoard b, Array<Member> crew, Part focus, String ourShip) {
        if (grid == null || !deck.present()) {
            Glass.text(f, x + 12f, y + 40f, "NO PLAN OF IT", Drive.DIM);
            return;
        }
        float px = x + 44f, py = y + 28f, pw = w - 88f, ph = h - 28f - 30f;
        deck.fit(px, py, pw, ph, fit);
        deck.paint(f, px, py, pw, ph);
        // Every system's place, faint
        ShipSubsystemRegistryComponent registry = craft.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e.getComponent(SubsystemComponent.class);
                if (s == null || s.footprint == null) continue;
                PlanMarks.box(f, deck, fit, s.footprint.originX, s.footprint.originY, s.footprint.width(), s.footprint.height(), Drive.DIM, 5f);
            }
        }
        // The parts: marked ones bracketed, one already off an empty box at L2 where it was; the few being
        // worked on named beside their marks, the one in focus first
        int labels = 0;
        if (focus != null && focus.marked) labels = label(f, focus, registry, labels);
        for (int i = 0; i < b.parts.size; i++) {
            Part p = b.parts.get(i);
            SubsystemComponent s = p.off ? null : systemAt(registry, p.x, p.y);
            int tw = s == null ? 1 : s.footprint.width(), th = s == null ? 1 : s.footprint.height();
            if (p.off) PlanMarks.box(f, deck, fit, p.x, p.y, tw, th, Drive.NORM, 3f);
            if (p.marked || p == focus) PlanMarks.brackets(f, deck, fit, p.x, p.y, tw, th);
            if (p.marked && p != focus && !p.status.isEmpty() && labels < LABELS) labels = label(f, p, registry, labels);
        }
        latch(f, x, y, w, ourShip);
        // Part labels go right of their marks, so the names go left of their people
        PlanMarks.crew(f, deck, fit, crew, craft, true);
        if (b.taken && b.prisoners > 0) {
            Glass.dim(f, px, py + ph + 12f, b.prisoners + " SURRENDERED, HELD ABOARD IT");
        } else if (!b.taken && b.defenders > 0) {
            Glass.dim(f, px, py + ph + 12f, b.defenders + (b.defenders == 1 ? " OF ITS CREW STILL STANDS" : " OF ITS CREW STILL STAND"));
        }
    }

    private static SubsystemComponent systemAt(ShipSubsystemRegistryComponent registry, int x, int y) {
        if (registry == null) return null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.footprint != null && s.footprint.originX == x && s.footprint.originY == y) return s;
        }
        return null;
    }

    /** A part's name and what is being done to it beside its mark, clear of the labels already set; the count after. */
    private int label(Filament f, Part p, ShipSubsystemRegistryComponent registry, int n) {
        SubsystemComponent s = p.off ? null : systemAt(registry, p.x, p.y);
        int tw = s == null ? 1 : s.footprint.width(), th = s == null ? 1 : s.footprint.height();
        String name = p.name.toUpperCase(Locale.ROOT), status = Glass.cut(p.status, LABEL_CELLS);
        float lw = Glass.width(Math.max(name.length(), status.length()));
        float lx = deck.left(p.x + tw, fit) + 8f, ly = deck.top(p.y + th - 1, fit) + 4f;
        for (int i = 0; i < n; i++) {
            float bx = labelBox[4 * i], by = labelBox[4 * i + 1], bw = labelBox[4 * i + 2], bh = labelBox[4 * i + 3];
            if (lx < bx + bw && bx < lx + lw && ly < by + bh && by < ly + 2f * Glass.RH) return n;
        }
        Glass.text(f, lx, ly, name, Drive.NORM);
        if (!status.isEmpty()) Glass.text(f, lx, ly + Glass.RH, status, Drive.DIM);
        labelBox[4 * n] = lx;
        labelBox[4 * n + 1] = ly;
        labelBox[4 * n + 2] = lw;
        labelBox[4 * n + 3] = 2f * Glass.RH;
        return n + 1;
    }

    /** The latch, lit on the hull where our ship lies over it, and which side that is, in the pane's head row. */
    private void latch(Filament f, float x, float y, float w, String ourShip) {
        GridLink link = null;
        for (int i = 0; i < grid.links.size && link == null; i++) if (grid.links.get(i).otherShip == ours) link = grid.links.get(i);
        if (link == null) {
            Glass.right(f, x + w - 12f, y + 9f, "NOT LATCHED: THE CREW CROSS ON EVA", Drive.DIM);
            return;
        }
        float t = fit[0], lx = deck.left(link.x, fit), ly = deck.top(link.y, fit);
        switch (link.side) {
            case TOP:
                f.rectLit(lx + 3f, ly - 1.5f, t - 6f, 3f, f.o().full());
                break;
            case BOTTOM:
                f.rectLit(lx + 3f, ly + t - 1.5f, t - 6f, 3f, f.o().full());
                break;
            case LEFT:
                f.rectLit(lx - 1.5f, ly + 3f, 3f, t - 6f, f.o().full());
                break;
            default:
                f.rectLit(lx + t - 1.5f, ly + 3f, 3f, t - 6f, f.o().full());
                break;
        }
        String side = link.side == Side.TOP ? "PORT SIDE" : link.side == Side.BOTTOM ? "STARBOARD SIDE" : link.side == Side.RIGHT ? "BOW" : "STERN";
        String who = ourShip.isEmpty() ? "OUR SHIP" : "THE " + ourShip.toUpperCase(Locale.ROOT);
        Glass.right(f, x + w - 12f, y + 9f, "LATCH · " + who + " LIES OVER ITS " + side, Drive.DIM);
    }
}
