package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/**
 * FILAMENT's composite widgets, built from {@link Filament}'s parts and shared by its three HUDs, as
 * the mockups' kit ({@code documentation/ui-overhaul/vfd/src/kit.js}) builds them: key caps, plates,
 * pips, annunciator tiles, value fields, bars with their notches, the sector scope, the projected
 * HUD's sprites and marks. Coordinates are the mockups': UI units from the stage's top left, y down.
 */
public final class Kit {
    private Kit() {
    }

    // ------------------------------------------------------------------ small parts

    /** A pip: a small fixed filtered lamp, a severity column beside a list row. */
    public static void pip(Filament f, float x, float y, Phosphor color, boolean on) {
        pip(f, x, y, color, on, 1.7f);
    }

    public static void pip(Filament f, float x, float y, Phosphor color, boolean on, float r) {
        if (on) f.dot(x, y, f.o().size(r * 2f).color(color).full());
        else f.dot(x, y, f.o().size(r * 2f).color(color).ghostOnly().k(2.2f));
    }

    /** A row of verbs, each a key cap and a word in dot matrix ("E", "USE"), on the glass or projected. Returns its width. */
    public static float verbs(Filament f, float x, float y, String[] keys, String[] words, float pitch, float level, boolean proj) {
        float cx = x;
        for (int i = 0; i < keys.length; i++) {
            cx += f.keycap(cx, y, keys[i], 7f * pitch + 2f, 12f, false) + 4f;
            cx += f.dm(cx, y + 1f, words[i], f.o().pitch(pitch).level(level).proj(proj).noGhost()) + 10f;
        }
        return cx - x;
    }

    /**
     * A row of state legends, each lit or its ghost: {@code on[i]} is the drive level, or NaN for
     * off. Returns the end x.
     */
    public static float states(Filament f, float x, float y, String[] words, float[] on, float size, float gap) {
        float cx = x;
        for (int i = 0; i < words.length; i++) {
            boolean lit = !Float.isNaN(on[i]);
            float w = f.legend(cx, y, words[i], f.o().size(size).lit(lit).level(lit ? on[i] : Drive.NORM));
            cx += w + gap;
        }
        return cx;
    }

    // ------------------------------------------------------------------ annunciator tiles

    /** Severity of an annunciator tile or a pip: off, caution (amber outline), warning (red block). */
    public enum Sev {
        OFF, ADVISORY, CAUTION, WARNING;

        public Phosphor color() {
            return this == WARNING ? Phosphor.RED : this == CAUTION ? Phosphor.AMBER : Phosphor.ZINC;
        }
    }

    /**
     * Dot-matrix text cut out of a lit block drawn under it (a warning tile's tag), pitch {@code p}:
     * {@code cells} wide, cut to fit.
     */
    public static void knock(Filament f, float x, float y, String text, int cells, float p) {
        f.dm(x, y, text, f.o().pitch(p).cells(cells).knock());
    }

    // ------------------------------------------------------------------ bars

    /** A depleting resource's zones: red under a fifth, amber under half. */
    public static final Filament.Zone DEPLETE = (i, n) -> i < n * 0.2f ? Phosphor.RED : i < n * 0.5f ? Phosphor.AMBER : Phosphor.ZINC;
    /** A rising one's (heat): amber past three fifths, red past four. */
    public static final Filament.Zone RISING = (i, n) -> i >= n * 0.8f ? Phosphor.RED : i >= n * 0.6f ? Phosphor.AMBER : Phosphor.ZINC;

    /**
     * A depleting resource (propellant, hull, air, shield mass): a full tank is not news, so the fill
     * is plain zinc; the thresholds are filtered notches under the bar, lit while the value is past
     * them (amber at half, red at a fifth). Returns the severity, for the row's pips.
     */
    public static Sev dbar(Filament f, float x, float y, float w, float h, int n, float frac, float level, float gap) {
        Filament.Opt o = f.o().level(level);
        if (!Float.isNaN(gap)) o.gap(gap);
        f.bar(x, y, w, h, n, frac, o);
        notch(f, x + w * 0.5f, y + h, frac <= 0.5f, Phosphor.AMBER);
        notch(f, x + w * 0.2f, y + h, frac <= 0.2f, Phosphor.RED);
        return dsev(frac);
    }

    private static void notch(Filament f, float nx, float y, boolean past, Phosphor color) {
        if (past) f.rectLit(nx - 0.6f, y + 1.4f, 1.2f, 3.4f, f.o().color(color).norm());
        else f.rectLit(nx - 0.6f, y + 1.4f, 1.2f, 3.4f, f.o().color(color).ghostOnly().k(3f));
    }

    /** A depleting resource's severity at {@code frac}: red at a fifth or less, amber at half. */
    public static Sev dsev(float frac) {
        return frac <= 0.2f ? Sev.WARNING : frac <= 0.5f ? Sev.CAUTION : Sev.OFF;
    }

    // ------------------------------------------------------------------ the sector scope

    /** A contact on the {@link #sectorScope}: its bearing (degrees from the nose, clockwise) and range ring. */
    public static final class ScopeContact {
        public float bearing;
        public int ring;
        public boolean hostile, lock, missile;
    }

    /**
     * The sector scope: 16 bearings by 4 range rings, a thin zinc ring for the lock's bearing and a red
     * threat rim; the ship in the middle, its nose up.
     */
    public static void sectorScope(Filament f, float cx, float cy, float r, ScopeContact[] contacts, int count) {
        final int rings = 4, n = 16;
        float inner = r * 0.16f, rimW = r * 0.08f, rimGap = r * 0.04f;
        float cellOuter = r - rimW - rimGap * 2f - r * 0.05f;
        float ringW = (cellOuter - inner) / rings, start = -360f / n / 2f;
        for (int k = 0; k < rings; k++) {
            final int ring = k;
            float r0 = inner + k * ringW + 0.8f, r1 = inner + (k + 1) * ringW - 0.8f;
            f.ring(cx, cy, r0, r1, n, (i, m) -> {
                ScopeContact c = at(contacts, count, ring, i, n);
                if (c == null) return Float.NaN;
                return c.lock ? Drive.FULL : c.hostile || c.missile ? Drive.NORM : Drive.DIM;
            }, f.o().start(start).gapDeg(2.4f));
        }
        f.ring(cx, cy, cellOuter + rimGap, cellOuter + rimGap + r * 0.035f, n, (i, m) -> {
            for (int j = 0; j < count; j++) if (contacts[j].lock && sector(contacts[j].bearing, n) == i) return Drive.FULL;
            return Float.NaN;
        }, f.o().start(start).gapDeg(3f));
        f.ring(cx, cy, r - rimW, r, n, (i, m) -> {
            for (int j = 0; j < count; j++) {
                if ((contacts[j].hostile || contacts[j].missile) && sector(contacts[j].bearing, n) == i) return Drive.FULL;
            }
            return Float.NaN;
        }, f.o().start(start).gapDeg(3f).red());
        f.icon("ship", cx - inner * 0.75f, cy - inner * 0.75f, inner * 1.5f, f.o());
    }

    private static ScopeContact at(ScopeContact[] contacts, int count, int ring, int sector, int n) {
        ScopeContact found = null;
        for (int j = 0; j < count; j++) {
            ScopeContact c = contacts[j];
            if (c.ring == ring && sector(c.bearing, n) == sector) found = c;
        }
        return found;
    }

    /** The sector a bearing falls in: sector 0 is centred on the nose, as the scope draws it. */
    static int sector(float bearing, int n) {
        float b = ((bearing % 360f) + 360f) % 360f;
        return (int) Math.floor(b / (360f / n) + 0.5f) % n;
    }

    // ------------------------------------------------------------------ the projected HUD

    public static final String[] PROGRADE = {"..###..", ".#...#.", "#.....#", "#..#..#", "#.....#", ".#...#.", "..###.."};
    public static final String[] IMPACT = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
    public static final String[] NEUTRAL = {"..#..", ".#.#.", "#...#", ".#.#.", "..#.."};
    public static final String[] STATION = {"#####", "#...#", "#.#.#", "#...#", "#####"};
    public static final String[] CHEVRON_R = {"#...", ".#..", "..#.", "...#", "..#.", ".#..", "#..."};
    public static final String[] CHEVRON_L = {"...#", "..#.", ".#..", "#...", ".#..", "..#.", "...#"};
    public static final String[] CHEVRON_U = {"...#...", "..#.#..", ".#...#.", "#.....#"};
    public static final String[] CHEVRON_D = {"#.....#", ".#...#.", "..#.#..", "...#..."};
    public static final String[] MISSILE = {"..#..", ".###.", "##.##", ".###.", "..#.."};

    /** A projected sprite centred on {@code x, y}. */
    public static void sprite(Filament f, String[] rows, float x, float y, float pitch, Phosphor color, float level) {
        f.sprite(x, y, rows, f.o().proj().centred().pitch(pitch).color(color).level(level));
    }

    /** Corner brackets round a box: {@code SQUARE} (friendly, the lock), {@code TRI} (hostile), {@code DIAMOND} (the nav target). */
    public enum Bracket { SQUARE, TRI, DIAMOND }

    public static void brackets(Filament f, float cx, float cy, float half, Bracket kind, float pitch, int len, Phosphor color,
                                float level, boolean ticks) {
        float p = pitch, size = p * 0.8f;
        if (kind == Bracket.DIAMOND) {
            for (int s = 0; s < 4; s++) {
                int sx = s == 1 ? 1 : s == 3 ? -1 : 0, sy = s == 0 ? -1 : s == 2 ? 1 : 0;
                float ax = cx + sx * half * 1.25f, ay = cy + sy * half * 1.25f;
                for (int i = 0; i < len; i++) {
                    float k = i * p * 0.9f;
                    if (sx != 0) {
                        dot(f, ax - sx * k, ay - k, size, color, level);
                        dot(f, ax - sx * k, ay + k, size, color, level);
                    } else {
                        dot(f, ax - k, ay - sy * k, size, color, level);
                        dot(f, ax + k, ay - sy * k, size, color, level);
                    }
                }
            }
            return;
        }
        for (int s = 0; s < 4; s++) {
            int sx = s == 0 || s == 3 ? -1 : 1, sy = s < 2 ? -1 : 1;
            float ax = cx + sx * half, ay = cy + sy * half;
            for (int i = 0; i < len; i++) {
                dot(f, ax - sx * i * p, ay, size, color, level);
                if (i > 0) dot(f, ax, ay - sy * i * p, size, color, level);
            }
            if (kind == Bracket.TRI) for (int i = 1; i < len - 1; i++) dot(f, ax - sx * i * p, ay - sy * (len - 1 - i) * p, size, color, level);
        }
        if (ticks) {
            for (int s = 0; s < 4; s++) {
                int sx = s == 1 ? 1 : s == 3 ? -1 : 0, sy = s == 0 ? -1 : s == 2 ? 1 : 0;
                for (int i = 0; i < 3; i++) dot(f, cx + sx * (half + 3 * p + i * p), cy + sy * (half + 3 * p + i * p), size, color, level);
            }
        }
    }

    private static void dot(Filament f, float x, float y, float size, Phosphor color, float level) {
        f.dot(x, y, f.o().proj().size(size).color(color).level(level));
    }

    /** A projected label: dot matrix at {@code pitch}, the first line at {@code level}, the rest dim. */
    public static void label(Filament f, float x, float y, String[] lines, float pitch, Phosphor color, float level) {
        for (int i = 0; i < lines.length; i++) label(f, x, y, i, lines[i], pitch, color, i == 0 ? level : Drive.DIM);
    }

    /** Line {@code line} of a projected label. */
    public static void label(Filament f, float x, float y, int line, String text, float pitch, Phosphor color, float level) {
        if (text == null || text.isEmpty()) return;
        f.dm(x, y + line * 7f * pitch * 1.35f, text, f.o().pitch(pitch).proj().color(color).level(level).noGhost());
    }

    /** A projected label centred under a point. */
    public static void nameplate(Filament f, float x, float y, String text, float level) {
        nameplate(f, x, y, text, Phosphor.ZINC, level);
    }

    public static void nameplate(Filament f, float x, float y, String text, Phosphor color, float level) {
        label(f, x - (text.length() * 12f - 2f) / 2f, y, 0, text, 2f, color, level);
    }

    /** Dots round a circle, about every {@code step} units. */
    public static void ringDots(Filament f, float cx, float cy, float r, float step, float size, Phosphor color, float level) {
        int n = Math.max(8, Math.round(MathUtils.PI2 * r / step));
        for (int i = 0; i < n; i++) {
            float a = i / (float) n * MathUtils.PI2;
            f.dot(cx + MathUtils.cos(a) * r, cy + MathUtils.sin(a) * r, f.o().proj().size(size).color(color).level(level));
        }
    }

    /**
     * A velocity marker: prograde a ring with a centre dot and three wings; retrograde a ring with an
     * X and the same wings. {@code square} draws the ring as a square, for a station or target frame.
     */
    public static void velMark(Filament f, boolean prograde, float x, float y, boolean square, float level) {
        float r = 7.5f;
        if (square) {
            int n = Math.round(2f * r / 2.9f);
            for (int i = 0; i < n; i++) {
                float t = -r + 2f * r * i / n;
                dot(f, x + t, y - r, 1.7f, Phosphor.ZINC, level);
                dot(f, x + r, y + t, 1.7f, Phosphor.ZINC, level);
                dot(f, x - t, y + r, 1.7f, Phosphor.ZINC, level);
                dot(f, x - r, y - t, 1.7f, Phosphor.ZINC, level);
            }
        } else {
            ringDots(f, x, y, r, 2.9f, 1.7f, Phosphor.ZINC, level);
        }
        if (prograde) {
            dot(f, x, y, 2.6f, Phosphor.ZINC, level);
        } else {
            for (int s = -1; s <= 1; s += 2) {
                for (int i = -1; i <= 1; i += 2) {
                    dot(f, x + i * 2.6f, y + s * i * 2.6f, 1.7f, Phosphor.ZINC, level);
                    dot(f, x + i * 4.4f, y + s * i * 4.4f, 1.4f, Phosphor.ZINC, level);
                }
            }
        }
        for (int i = 1; i <= 2; i++) {
            dot(f, x - (r + i * 2.9f), y, 1.7f, Phosphor.ZINC, level);
            dot(f, x + (r + i * 2.9f), y, 1.7f, Phosphor.ZINC, level);
            dot(f, x, y - (r + i * 2.9f), 1.7f, Phosphor.ZINC, level);
        }
    }

    /**
     * The lead reticle: a ring broken on the diagonals, with a centre pip; on a target's hull the glass
     * under it is darkened so its dots don't read as the hull's own lights.
     */
    public static void reticle(Filament f, float x, float y, boolean onTarget, float level) {
        float r = 8f;
        if (onTarget) f.scrim(x - r - 5f, y - r - 5f, 2f * r + 10f, 2f * r + 10f, r + 5f, 0.62f);
        int n = Math.max(8, Math.round(MathUtils.PI2 * r / 2.6f));
        for (int i = 0; i < n; i++) {
            float a = i / (float) n * MathUtils.PI2;
            if (Math.abs(MathUtils.sin(2f * a)) > 0.72f) continue;
            dot(f, x + MathUtils.cos(a) * r, y + MathUtils.sin(a) * r, 1.7f, Phosphor.ZINC, level);
        }
        dot(f, x, y, 2.2f, Phosphor.ZINC, level);
    }

    /** The impact cross: red, where missiles will land. */
    public static void cross(Filament f, float x, float y) {
        for (int i = -3; i <= 3; i++) {
            dot(f, x + i * 2.4f, y + i * 2.4f, 1.8f, Phosphor.RED, Drive.FULL);
            if (i != 0) dot(f, x + i * 2.4f, y - i * 2.4f, 1.8f, Phosphor.RED, Drive.FULL);
        }
    }

    /** A missile's glyph. */
    public static void missile(Filament f, float x, float y, Phosphor color) {
        f.sprite(x, y, MISSILE, f.o().proj().centred().pitch(1.6f).color(color).full());
    }

    /** A fire arc: two radial dot lines and the arc between them, degrees clockwise from +x (screen). */
    public static void arc(Filament f, float cx, float cy, float r0, float r1, float a0, float a1, float level, float step) {
        float d = MathUtils.degreesToRadians;
        radial(f, cx, cy, r0, r1, a0 * d, level, step);
        radial(f, cx, cy, r0, r1, a1 * d, level, step);
        int n = Math.max(2, Math.round((a1 - a0) * d * r1 / step));
        for (int i = 0; i <= n; i++) {
            float a = (a0 + (a1 - a0) * i / n) * d;
            f.dot(cx + MathUtils.cos(a) * r1, cy + MathUtils.sin(a) * r1, f.o().proj().size(1.3f).level(level));
        }
    }

    private static void radial(Filament f, float cx, float cy, float r0, float r1, float a, float level, float step) {
        float c = MathUtils.cos(a), s = MathUtils.sin(a);
        f.dotLine(cx + c * r0, cy + s * r0, cx + c * r1, cy + s * r1, f.o().proj().level(level).step(step).size(1.3f));
    }

    /** An edge marker: a chevron at the screen's edge pointing out, its label inside. {@code dir} is 'r', 'l', 'u' or 'd'. */
    public static void edge(Filament f, float x, float y, char dir, String text, Phosphor color, float level) {
        String[] spr = dir == 'r' ? CHEVRON_R : dir == 'l' ? CHEVRON_L : dir == 'u' ? CHEVRON_U : CHEVRON_D;
        f.sprite(x, y, spr, f.o().proj().centred().pitch(2f).color(color).level(level));
        float w = text.length() * 12f;
        float lx = dir == 'r' ? x - 10f - w : dir == 'l' ? x + 10f : x - w / 2f;
        float ly = dir == 'd' ? y - 14f - 14f * 1.35f : dir == 'u' ? y + 12f : y - 5f;
        label(f, lx, ly, 0, text, 2f, color, level);
    }

    /**
     * The box an edge marker at {@code x, y} takes on the stage, y down, as {@link #edge} draws it: its
     * chevron and its label, into {@code out}. A HUD hands it to the game with its panels, so the
     * magnified insets keep off it.
     */
    public static Rectangle edgeBox(float x, float y, char dir, String text, Rectangle out) {
        float w = text.length() * 12f;
        switch (dir) {
            case 'r':
                return out.set(x - 10f - w, y - 7f, w + 14f, 16f);
            case 'l':
                return out.set(x - 4f, y - 7f, w + 14f, 16f);
            case 'd': {
                float half = Math.max(7f, w / 2f);
                return out.set(x - half, y - 14f - 14f * 1.35f, 2f * half, 14f + 14f * 1.35f + 4f);
            }
            default: {
                float half = Math.max(7f, w / 2f);
                return out.set(x - half, y - 4f, 2f * half, 30f);
            }
        }
    }

    /** How far an edge marker's label reaches either side of it along its edge, for {@link #spread}. */
    public static float edgeHalf(char dir, String text) {
        return dir == 'u' || dir == 'd' ? text.length() * 6f : 11f;
    }

    /**
     * Moves the first {@code n} markers on one edge along it so none overlaps the next: {@code pos} are
     * their centres along the edge, {@code half} how far each reaches either side, all kept within
     * {@code lo..hi} with {@code gap} between. Markers keep their order, and only those in each other's
     * way move. When they can't all fit they overlap at the far end.
     */
    public static void spread(float[] pos, float[] half, int n, float lo, float hi, float gap) {
        // Into order along the edge (a dozen at most: an insertion sort)
        for (int i = 1; i < n; i++) {
            float p = pos[i], h = half[i];
            int j = i - 1;
            while (j >= 0 && pos[j] > p) {
                pos[j + 1] = pos[j];
                half[j + 1] = half[j];
                j--;
            }
            pos[j + 1] = p;
            half[j + 1] = h;
        }
        float next = lo;
        for (int i = 0; i < n; i++) {
            pos[i] = Math.max(pos[i], next + half[i]);
            next = pos[i] + half[i] + gap;
        }
        float prev = hi;
        for (int i = n - 1; i >= 0; i--) {
            pos[i] = Math.min(pos[i], prev - half[i]);
            prev = pos[i] - half[i] - gap;
        }
    }

    /**
     * A hazard tag on the deck: the severity mark and the short code, 5x7 at pitch 2 over a darkened
     * pill. Warnings are red with a diamond, cautions amber with a triangle: the shape is the
     * colour-free channel. Returns its width.
     */
    public static float deckTag(Filament f, float x, float y, String text, Sev sev, float level) {
        float p = 2f;
        Phosphor color = sev.color();
        String glyph = sev == Sev.WARNING ? "♦ " : sev == Sev.CAUTION ? "▲ " : "";
        String str = glyph + text;
        float w = str.length() * 6f * p - p, lx = x - w / 2f, ly = y - 3.5f * p;
        f.scrim(lx - 6f, ly - 5f, w + 12f, 7f * p + 10f, 4f, 0.62f);
        f.dm(lx, ly, str, f.o().pitch(p).proj().color(color).level(level).noGhost());
        return w + 12f;
    }

    /** Reports, while developing, a row of a hand-made layout that ran past its limit. */
    public static float fits(float x, float limit, String what) {
        if (x > limit + 0.5f && com.badlogic.gdx.Gdx.app != null) {
            com.badlogic.gdx.Gdx.app.debug("Filament", "overflow: " + what + " ends at " + x + ", limit " + limit);
        }
        return x;
    }
}
