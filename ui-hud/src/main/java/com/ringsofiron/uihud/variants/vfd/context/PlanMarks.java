package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.vfd.DeckPlan;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The marks the modes' plans share, as the mockups draw them (planBox, planMark, person): a system's
 * box of dots in its footprint, corner brackets round a footprint, and a person, with a tick each
 * side while at work or hollow for someone who surrendered. Each takes the plan and its fit
 * ({@link DeckPlan#fit}), so a footprint lands on its tiles.
 */
final class PlanMarks {
    private PlanMarks() {
    }

    /** A footprint's box of dots, inset 4 from its tiles, a dot every {@code step}. */
    static void box(Filament f, DeckPlan deck, float[] fit, int gx, int gy, int tw, int th, float level, float step) {
        float bx = deck.left(gx, fit) + 4f, by = deck.top(gy + th - 1, fit) + 4f, bw = tw * fit[0] - 8f, bh = th * fit[0] - 8f;
        if (bw <= 0f || bh <= 0f) return;
        int nx = Math.max(1, Math.round(bw / step)), ny = Math.max(1, Math.round(bh / step));
        for (int i = 0; i <= nx; i++) {
            f.dot(bx + i * bw / nx, by, f.o().size(1f).level(level));
            f.dot(bx + i * bw / nx, by + bh, f.o().size(1f).level(level));
        }
        for (int i = 1; i < ny; i++) {
            f.dot(bx, by + i * bh / ny, f.o().size(1f).level(level));
            f.dot(bx + bw, by + i * bh / ny, f.o().size(1f).level(level));
        }
    }

    /** Corner brackets at L3 round a footprint: a part marked to strip, or the systems in a list's focus. */
    static void brackets(Filament f, DeckPlan deck, float[] fit, int gx, int gy, int tw, int th) {
        float bx = deck.left(gx, fit), by = deck.top(gy + th - 1, fit), bw = tw * fit[0], bh = th * fit[0];
        float arm = Math.min(10f, Math.min(bw, bh) / 2f);
        for (int k = 0; k < 4; k++) {
            float cx = k % 2 == 0 ? bx : bx + bw, cy = k < 2 ? by : by + bh, dx = k % 2 == 0 ? 1f : -1f, dy = k < 2 ? 1f : -1f;
            f.dotLine(cx + dx * 2f, cy + dy * 2f, cx + dx * (2f + arm), cy + dy * 2f, f.o().size(1.6f).step(2.2f).full());
            f.dotLine(cx + dx * 2f, cy + dy * 2f, cx + dx * 2f, cy + dy * (2f + arm), f.o().size(1.6f).step(2.2f).full());
        }
    }

    /**
     * A person centred at ({@code cx}, {@code cy}): five by five dots with the corners off, about a
     * quarter of a tile, and a tick each side while at work; hollow and dim, someone who surrendered.
     */
    static void person(Filament f, float cx, float cy, boolean working, boolean hollow) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                boolean body = Math.abs(i) < 2 || Math.abs(j) < 2;
                boolean inner = Math.abs(i) < 2 && Math.abs(j) < 2 && !(Math.abs(i) == 1 && Math.abs(j) == 1);
                if (!body || hollow && inner) continue;
                f.dot(cx + i * 2.4f, cy + j * 2.4f, f.o().size(hollow ? 1.6f : 2f).level(hollow ? Drive.DIM : Drive.FULL));
            }
        }
        if (working) for (int sx = -1; sx <= 1; sx += 2) f.dot(cx + sx * 10f, cy, f.o().size(1.8f).norm());
    }

    /**
     * The crew standing on {@code craft}, each a person with their name beside it: on the side
     * {@code left} says, or the other where someone else stands there. Returns how many were drawn.
     */
    static int crew(Filament f, DeckPlan deck, float[] fit, Array<Member> crew, Entity craft, boolean left) {
        int n = 0;
        for (int i = 0; i < crew.size; i++) {
            Member m = crew.get(i);
            if (m.craft != craft || Float.isNaN(m.x)) continue;
            float px = deck.screenX(m.x, fit), py = deck.screenY(m.y, fit);
            person(f, px, py, m.working, false);
            String name = m.name.toUpperCase(Locale.ROOT);
            float nw = Glass.width(name.length());
            boolean flip = false;
            for (int k = 0; k < crew.size && !flip; k++) {
                Member o = crew.get(k);
                if (k == i || o.craft != craft || Float.isNaN(o.x)) continue;
                float ox = deck.screenX(o.x, fit), oy = deck.screenY(o.y, fit);
                flip = (left ? ox < px && ox > px - 16f - nw : ox > px && ox < px + 16f + nw) && Math.abs(oy - py) < Glass.RH;
            }
            boolean onLeft = left != flip;
            Glass.text(f, onLeft ? px - 14f - nw : px + 14f, py - 5f, name, Drive.NORM);
            n++;
        }
        return n;
    }

    /** A key's square of dots and its word along a plan's foot, from {@code kx}; returns where the next key starts. */
    static float keyBox(Filament f, float kx, float y, float level, float step, String word) {
        float bx = kx, by = y - 9f, s = 18f;
        int n = Math.max(1, Math.round(s / step));
        for (int i = 0; i <= n; i++) {
            f.dot(bx + i * s / n, by, f.o().size(1f).level(level));
            f.dot(bx + i * s / n, by + s, f.o().size(1f).level(level));
            if (i > 0 && i < n) {
                f.dot(bx, by + i * s / n, f.o().size(1f).level(level));
                f.dot(bx + s, by + i * s / n, f.o().size(1f).level(level));
            }
        }
        kx += 26f;
        return kx + Glass.dim(f, kx, y - 5f, word) + 22f;
    }

    /** A person in a plan's key, then its word; returns where the next key starts. */
    static float keyPerson(Filament f, float kx, float y, boolean working, boolean hollow, String word) {
        person(f, kx + 12f, y, working, hollow);
        kx += 30f;
        return kx + Glass.dim(f, kx, y - 5f, word) + 22f;
    }
}
