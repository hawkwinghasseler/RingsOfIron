package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * What a hit on a hull looks like from inside, in the deck view: drawn from each
 * {@link HullHitEvent} where it struck, by what struck and what it did.
 * <ul>
 *   <li><b>A round stopped by the hull</b> rings it: a flash and sparks off the outside, the struck
 *       plate glowing for a moment and, from a hard hit, spall off its inner face.</li>
 *   <li><b>A glancing round</b> skips off in a spray of sparks along its new heading.</li>
 *   <li><b>A round that gets in</b> bursts through: a flash that lights the room, sparks and
 *       torn plating thrown on along its path, and dust. One that goes clean through leaves a
 *       streak across the deck and bursts out of the far side too.</li>
 *   <li><b>A warhead going off inside</b>: a fireball out to its blast radius, a shock ring, and
 *       sparks, debris and smoke flung every way.</li>
 *   <li><b>Lasers and beams</b> melt rather than shatter: the plate glows white hot and cools
 *       through orange, and molten droplets drift off it.</li>
 *   <li><b>An ion bolt</b> breaks nothing: blue arcs crawl over the systems round where it landed
 *       for a second.</li>
 *   <li><b>A ram</b> (a fast pass or a crash) is mostly wreckage: heavy debris and dust.</li>
 * </ul>
 * A hit the shields took entirely shows nothing inside. Particles thrown into the ship bounce off
 * walls and closed doors. The flashes light the deck through {@link #flashes}, shadowed by its walls.
 *
 * <p>Cosmetic only, with a random source of its own, so the simulation never sees it. Positions are
 * in each grid's tile space (one tile = 1), drawn with the grid's local frame already applied, as
 * {@code HazardParticles} draws. Everything lives in fixed arrays; nothing is allocated per hit.
 */
public final class DeckImpacts {
    /** Receives a flash's light, in its grid's tile space. */
    public interface FlashSink {
        void flash(float x, float y, Color color, float strength, float reachTiles, float glowTiles);
    }

    private static final int MAX_HITS = 96;
    private static final int MAX_SPARKS = 2400, MAX_EMBERS = 900, MAX_DEBRIS = 700, MAX_SMOKE = 500;

    /** Hit colors: a round's flash, a laser's, an ion bolt's, a warhead's. */
    private static final Color KINETIC = new Color(1f, 0.92f, 0.7f, 1f);
    private static final Color ENERGY = new Color(1f, 0.6f, 0.25f, 1f);
    private static final Color ION = new Color(0.45f, 0.8f, 1f, 1f);
    private static final Color BLAST = new Color(1f, 0.55f, 0.18f, 1f);

    /** Particles slow by this fraction of their speed each second: drag in the air, none outside. */
    private static final float DRAG_INSIDE = 2.2f, DRAG_OUTSIDE = 0.4f;
    /** Speed kept by a particle bouncing off a wall. */
    private static final float BOUNCE = 0.45f;
    /** How long an ion bolt's arcs crawl, seconds. */
    private static final float ARC_LIFE = 1.1f;

    private enum Kind { KINETIC, ENERGY, ION, BLAST, RAM }

    /** One hit: its flash, glow, streak, fireball or arcs, each drawn from its age. */
    private final ShipGridComponent[] hitGrid = new ShipGridComponent[MAX_HITS];
    private final Kind[] hitKind = new Kind[MAX_HITS];
    private final HullHitEvent.Outcome[] hitOutcome = new HullHitEvent.Outcome[MAX_HITS];
    private final float[] hitX = new float[MAX_HITS], hitY = new float[MAX_HITS];
    /** Where the flash is: inside for a round that got in, outside for one that didn't. */
    private final float[] flashX = new float[MAX_HITS], flashY = new float[MAX_HITS];
    /** The struck hull tile's middle, for its glow. */
    private final float[] plateX = new float[MAX_HITS], plateY = new float[MAX_HITS];
    private final float[] exitX = new float[MAX_HITS], exitY = new float[MAX_HITS];
    private final float[] radius = new float[MAX_HITS], severity = new float[MAX_HITS];
    private final float[] hitAge = new float[MAX_HITS], hitLife = new float[MAX_HITS];
    private final boolean[] passedThrough = new boolean[MAX_HITS], gotIn = new boolean[MAX_HITS];
    /** The hull's outward normal where it struck. */
    private final float[] nX = new float[MAX_HITS], nY = new float[MAX_HITS];
    /** Droplets owed a burned plate, carried between frames. */
    private final float[] sizzleDebt = new float[MAX_HITS];
    private int hits;

    final Particles sparks = new Particles(MAX_SPARKS);
    final Particles embers = new Particles(MAX_EMBERS);
    final Particles debris = new Particles(MAX_DEBRIS);
    final Particles smoke = new Particles(MAX_SMOKE);

    private final RandomXS128 random = new RandomXS128(0x1A9C7E);
    private final Color color = new Color();
    private Signal<HullHitEvent> listening;
    private final Listener<HullHitEvent> listener = (signal, hit) -> add(hit);

    /** Structure-of-arrays particle pool. */
    static final class Particles {
        final float[] x, y, vx, vy, age, life, size, spin, angle;
        final ShipGridComponent[] grid;
        /** Thrown into the ship: it bounces off walls and slows in the air. */
        final boolean[] inside;
        int count;

        Particles(int max) {
            x = new float[max]; y = new float[max]; vx = new float[max]; vy = new float[max];
            age = new float[max]; life = new float[max]; size = new float[max]; spin = new float[max];
            angle = new float[max];
            grid = new ShipGridComponent[max];
            inside = new boolean[max];
        }

        int add(ShipGridComponent g, float px, float py, float pvx, float pvy, float plife, float psize, boolean in) {
            if (count == x.length) return -1;
            int i = count++;
            x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
            age[i] = 0f; life[i] = plife; size[i] = psize; spin[i] = 0f; angle[i] = 0f;
            grid[i] = g;
            inside[i] = in;
            return i;
        }

        void remove(int i) {
            int last = --count;
            x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last];
            age[i] = age[last]; life[i] = life[last]; size[i] = size[last]; spin[i] = spin[last];
            angle[i] = angle[last]; grid[i] = grid[last]; inside[i] = inside[last];
            grid[last] = null;
        }
    }

    /** Shows the hits {@code hullHits} reports from now on, and no longer those of the signal before. */
    public void listen(Signal<HullHitEvent> hullHits) {
        if (hullHits == listening) return;
        if (listening != null) listening.remove(listener);
        listening = hullHits;
        clear();
        if (hullHits != null) hullHits.add(listener);
    }

    public void clear() {
        for (int i = 0; i < hits; i++) hitGrid[i] = null;
        hits = 0;
        for (Particles p : new Particles[]{sparks, embers, debris, smoke}) {
            for (int i = 0; i < p.count; i++) p.grid[i] = null;
            p.count = 0;
        }
    }

    /** Hits still showing, for tests. */
    public int hitsShowing() {
        return hits;
    }

    /** Particles still flying, for tests. */
    public int particles() {
        return sparks.count + embers.count + debris.count + smoke.count;
    }

    // -----------------------------------------------------------------------
    // A hit
    // -----------------------------------------------------------------------

    /** Starts showing {@code hit} on its ship's deck. */
    public void add(HullHitEvent hit) {
        ShipGridComponent g = hit.ship == null ? null : hit.ship.getComponent(ShipGridComponent.class);
        if (g == null) return;
        // Shields took it all: nothing reached the hull to see from inside
        if (!hit.ion && hit.shielded > 0f && hit.energyAbsorbed <= 0f) return;
        if (hits == MAX_HITS) removeHit(0);
        int h = hits++;
        Kind kind = kindOf(hit);
        float sev = Math.max(0.12f, hit.severity);
        float dx = hit.direction.x, dy = hit.direction.y, nx = hit.normal.x, ny = hit.normal.y;
        if (dx == 0f && dy == 0f) { dx = -nx; dy = -ny; }
        if (nx == 0f && ny == 0f) { nx = -dx; ny = -dy; }
        boolean in = hit.outcome == HullHitEvent.Outcome.PENETRATED || hit.outcome == HullHitEvent.Outcome.OVER_PENETRATED;
        hitGrid[h] = g;
        hitKind[h] = kind;
        hitOutcome[h] = hit.outcome;
        hitX[h] = hit.entry.x;
        hitY[h] = hit.entry.y;
        plateX[h] = hit.tileX + 0.5f;
        plateY[h] = hit.tileY + 0.5f;
        // The plate's inner face: half a tile in from the struck tile's middle, against the normal
        float innerX = plateX[h] - nx * 0.55f, innerY = plateY[h] - ny * 0.55f;
        boolean flashInside = in || kind == Kind.ION;
        flashX[h] = flashInside ? innerX : hit.entry.x + nx * 0.25f;
        flashY[h] = flashInside ? innerY : hit.entry.y + ny * 0.25f;
        exitX[h] = hit.exit.x;
        exitY[h] = hit.exit.y;
        passedThrough[h] = hit.outcome == HullHitEvent.Outcome.OVER_PENETRATED;
        gotIn[h] = in;
        nX[h] = nx;
        nY[h] = ny;
        sizzleDebt[h] = 0f;
        severity[h] = sev;
        hitAge[h] = 0f;
        radius[h] = 0.5f + 1.3f * sev;
        hitLife[h] = 0.25f + 0.25f * sev;
        if (kind == Kind.ENERGY) hitLife[h] = 1.6f + 2f * sev; // the plate cools
        else if (kind == Kind.KINETIC && hit.outcome == HullHitEvent.Outcome.STOPPED) hitLife[h] = 0.6f + 1.2f * sev;
        else if (kind == Kind.ION) {
            hitLife[h] = ARC_LIFE;
            radius[h] = 2f;
        }
        if (hit.detonationX >= 0) {
            // The fireball fills the blast, at the tile the fuse fired in
            kind = hitKind[h] = Kind.BLAST;
            flashX[h] = hit.detonationX + 0.5f;
            flashY[h] = hit.detonationY + 0.5f;
            radius[h] = Math.max(1.2f, hit.blastRadius + 0.5f);
            hitLife[h] = 0.9f;
        } else if (kind == Kind.BLAST) {
            // Went off on the outside: a burst against the hull, its fragments their own hits
            flashX[h] = hit.entry.x + nx * 0.6f;
            flashY[h] = hit.entry.y + ny * 0.6f;
            radius[h] = 1.5f;
            hitLife[h] = 0.7f;
        }

        switch (kind) {
            case ION:
                burst(sparks, g, flashX[h], flashY[h], 0f, 0f, MathUtils.PI2, 8, 2f, 5f, 0.15f, 0.35f, 0.06f, true);
                break;
            case BLAST:
                if (hit.detonationX < 0) {
                    burst(sparks, g, flashX[h], flashY[h], nx, ny, 1.4f, 40, 4f, 12f, 0.2f, 0.5f, 0.07f, false);
                    burst(smoke, g, flashX[h], flashY[h], nx, ny, 1.4f, 6, 0.4f, 1.4f, 1.2f, 2.2f, 0.3f, false);
                    break;
                }
                burst(sparks, g, flashX[h], flashY[h], 0f, 0f, MathUtils.PI2, 70, 4f, 14f, 0.25f, 0.7f, 0.07f, true);
                burst(debris, g, flashX[h], flashY[h], 0f, 0f, MathUtils.PI2, 26, 2f, 7f, 1.2f, 2.6f, 0.192f, true);
                burst(smoke, g, flashX[h], flashY[h], 0f, 0f, MathUtils.PI2, 14, 0.4f, 1.6f, 1.6f, 3f, 0.35f, true);
                burst(embers, g, flashX[h], flashY[h], 0f, 0f, MathUtils.PI2, 24, 0.8f, 3f, 0.8f, 1.8f, 0.1f, true);
                // And the round's own way in
                if (in) burst(debris, g, innerX, innerY, dx, dy, 0.6f, 6, 2f, 5f, 1f, 2f, 0.16f, true);
                break;
            default:
                kineticOrEnergy(h, g, kind, hit.outcome, sev, dx, dy, nx, ny, innerX, innerY);
        }
    }

    private static Kind kindOf(HullHitEvent hit) {
        if (hit.ion || hit.damageType == DamageType.ION) return Kind.ION;
        if (hit.beam || hit.damageType == DamageType.ENERGY) return Kind.ENERGY;
        if (hit.ram) return Kind.RAM;
        if (hit.damageType == DamageType.EXPLOSIVE) return Kind.BLAST;
        return Kind.KINETIC;
    }

    private void kineticOrEnergy(int h, ShipGridComponent g, Kind kind, HullHitEvent.Outcome outcome, float sev,
                                 float dx, float dy, float nx, float ny, float innerX, float innerY) {
        boolean energy = kind == Kind.ENERGY, ram = kind == Kind.RAM;
        int n = Math.round(6 + 34 * sev);
        float ox = hitX[h] + nx * 0.1f, oy = hitY[h] + ny * 0.1f;
        switch (outcome) {
            case RICOCHET: {
                // Off along the round's reflection from the plate
                float dot = dx * nx + dy * ny;
                float rx = dx - 2f * dot * nx, ry = dy - 2f * dot * ny;
                burst(sparks, g, ox, oy, rx, ry, 0.5f, n, 4f, 12f, 0.2f, 0.45f, 0.06f, false);
                break;
            }
            case STOPPED:
                // Off the outside, back the way it came
                if (energy) burst(embers, g, ox, oy, nx, ny, 1.2f, n, 0.5f, 2.5f, 0.6f, 1.4f, 0.11f, false);
                else burst(sparks, g, ox, oy, nx, ny, 1.1f, n, 3f, 9f, 0.15f, 0.4f, 0.06f, false);
                if (ram) burst(debris, g, ox, oy, nx, ny, 1.3f, Math.round(4 + 10 * sev), 1f, 3f, 1.2f, 2.4f, 0.192f, false);
                // The plate rings: a few flakes of spall off its inner face, a shower from a hard hit
                if (!energy) {
                    burst(sparks, g, innerX, innerY, dx, dy, 0.9f, 2 + Math.round(n * sev), 1.5f, 5f, 0.12f, 0.3f, 0.05f, true);
                    if (sev > 0.3f) burst(debris, g, innerX, innerY, dx, dy, 0.9f, Math.round(4 * sev), 1f, 3f, 0.8f, 1.6f, 0.112f, true);
                }
                break;
            default: {
                // In through the plate, carrying on along its path
                float spread = ram ? 1.1f : 0.6f;
                if (energy) {
                    burst(embers, g, innerX, innerY, dx, dy, 0.9f, n, 0.8f, 3f, 0.8f, 1.8f, 0.1f, true);
                    burst(sparks, g, innerX, innerY, dx, dy, 0.6f, n / 2, 3f, 8f, 0.15f, 0.35f, 0.05f, true);
                } else {
                    burst(sparks, g, innerX, innerY, dx, dy, spread, n, 4f, 13f, 0.2f, 0.5f, 0.064f, true);
                    burst(embers, g, innerX, innerY, dx, dy, spread, n / 4, 1f, 3f, 0.4f, 1f, 0.08f, true);
                }
                burst(debris, g, innerX, innerY, dx, dy, spread, Math.round((ram ? 14 : 5) + 16 * sev), 1.5f, 6f, 1.2f, 2.6f, ram ? 0.22f : 0.16f, true);
                burst(smoke, g, innerX, innerY, dx, dy, 1.2f, Math.round((ram ? 6 : 2) + 5 * sev), 0.3f, 1.2f, 1.4f, 2.8f,
                        ram ? 0.4f : 0.3f, true);
                if (outcome == HullHitEvent.Outcome.OVER_PENETRATED) {
                    // And out of the far side, into space
                    burst(sparks, g, exitX[h], exitY[h], dx, dy, 0.5f, n / 2 + 4, 4f, 12f, 0.2f, 0.45f, 0.06f, false);
                    burst(debris, g, exitX[h], exitY[h], dx, dy, 0.5f, Math.round(3 + 8 * sev), 2f, 6f, 1.5f, 3f, 0.16f, false);
                }
            }
        }
    }

    /**
     * {@code n} particles from ({@code x}, {@code y}) toward ({@code dx}, {@code dy}) (or every way
     * with a zero direction), within {@code spread} radians each side, at {@code speedMin} to
     * {@code speedMax} tiles a second, living {@code lifeMin} to {@code lifeMax} seconds, about
     * {@code size} tiles across.
     */
    private void burst(Particles p, ShipGridComponent g, float x, float y, float dx, float dy, float spread, int n,
                       float speedMin, float speedMax, float lifeMin, float lifeMax, float size, boolean in) {
        float heading = dx == 0f && dy == 0f ? 0f : MathUtils.atan2(dy, dx);
        for (int k = 0; k < n; k++) {
            float a = heading + (random.nextFloat() * 2f - 1f) * spread;
            // Faster near the middle of the cone
            float speed = MathUtils.lerp(speedMin, speedMax, random.nextFloat() * random.nextFloat() * 0.5f + random.nextFloat() * 0.5f);
            float j = 0.1f;
            int i = p.add(g, x + (random.nextFloat() - 0.5f) * j, y + (random.nextFloat() - 0.5f) * j,
                    MathUtils.cos(a) * speed, MathUtils.sin(a) * speed,
                    MathUtils.lerp(lifeMin, lifeMax, random.nextFloat()), size * (0.6f + 0.8f * random.nextFloat()), in);
            if (i < 0) return;
            p.spin[i] = (random.nextFloat() * 2f - 1f) * 12f;
            p.angle[i] = random.nextFloat() * 360f;
        }
    }

    /** Molten droplets coming off a burned plate while it is still white hot, fewer as it cools. */
    private void sizzle(int h, float dt) {
        float hot = 1f - hitAge[h] / (hitLife[h] * 0.6f);
        if (hot <= 0f) return;
        sizzleDebt[h] += dt * hot * (10f + 30f * severity[h]);
        int n = (int) sizzleDebt[h];
        if (n == 0) return;
        sizzleDebt[h] -= n;
        // Off the plate's outside, and inside too once it is through
        burst(embers, hitGrid[h], plateX[h] + nX[h] * 0.5f, plateY[h] + nY[h] * 0.5f, nX[h], nY[h], 1.3f, n, 0.4f, 1.8f,
                0.5f, 1.2f, 0.1f, false);
        if (gotIn[h]) burst(embers, hitGrid[h], flashX[h], flashY[h], -nX[h], -nY[h], 1.1f, n, 0.4f, 1.8f, 0.5f, 1.2f, 0.1f, true);
    }

    private void removeHit(int h) {
        int last = --hits;
        hitGrid[h] = hitGrid[last]; hitKind[h] = hitKind[last]; hitOutcome[h] = hitOutcome[last];
        hitX[h] = hitX[last]; hitY[h] = hitY[last]; flashX[h] = flashX[last]; flashY[h] = flashY[last];
        plateX[h] = plateX[last]; plateY[h] = plateY[last]; exitX[h] = exitX[last]; exitY[h] = exitY[last];
        radius[h] = radius[last]; severity[h] = severity[last]; hitAge[h] = hitAge[last]; hitLife[h] = hitLife[last];
        passedThrough[h] = passedThrough[last]; gotIn[h] = gotIn[last];
        nX[h] = nX[last]; nY[h] = nY[last]; sizzleDebt[h] = sizzleDebt[last];
        hitGrid[last] = null;
    }

    // -----------------------------------------------------------------------
    // Moving on
    // -----------------------------------------------------------------------

    /** Moves everything on {@code dt} seconds; 0 holds it. */
    public void update(float dt) {
        if (dt <= 0f) return;
        dt = Math.min(dt, 0.1f);
        for (int h = hits - 1; h >= 0; h--) {
            hitAge[h] += dt;
            if (hitAge[h] >= hitLife[h]) {
                removeHit(h);
                continue;
            }
            if (hitKind[h] == Kind.ENERGY) sizzle(h, dt);
        }
        step(sparks, dt, 1f);
        step(embers, dt, 0.6f);
        step(debris, dt, 1f);
        step(smoke, dt, 1.5f);
    }

    private void step(Particles p, float dt, float drag) {
        for (int i = p.count - 1; i >= 0; i--) {
            p.age[i] += dt;
            if (p.age[i] >= p.life[i]) {
                p.remove(i);
                continue;
            }
            float slow = Math.max(0f, 1f - (p.inside[i] ? DRAG_INSIDE : DRAG_OUTSIDE) * drag * dt);
            p.vx[i] *= slow;
            p.vy[i] *= slow;
            p.angle[i] += p.spin[i] * slow * dt * MathUtils.radiansToDegrees;
            p.spin[i] *= slow;
            if (p.inside[i]) {
                moveInside(p, i, dt);
            } else {
                p.x[i] += p.vx[i] * dt;
                p.y[i] += p.vy[i] * dt;
            }
        }
    }

    /** Moves particle {@code i} on, bouncing it off any wall or closed door it would cross. */
    private static void moveInside(Particles p, int i, float dt) {
        ShipGridComponent g = p.grid[i];
        int tx = MathUtils.floor(p.x[i]), ty = MathUtils.floor(p.y[i]);
        float nx = p.x[i] + p.vx[i] * dt;
        int ntx = MathUtils.floor(nx);
        if (ntx != tx && blocked(g, tx, ty, ntx, ty)) {
            p.vx[i] = -p.vx[i] * BOUNCE;
            p.vy[i] *= BOUNCE;
        } else {
            p.x[i] = nx;
            tx = ntx;
        }
        float ny = p.y[i] + p.vy[i] * dt;
        int nty = MathUtils.floor(ny);
        if (nty != ty && blocked(g, tx, ty, tx, nty)) {
            p.vy[i] = -p.vy[i] * BOUNCE;
            p.vx[i] *= BOUNCE;
        } else {
            p.y[i] = ny;
        }
    }

    /** Whether a particle in tile (x, y) is stopped going into the next tile (nx, ny). Out through a breach it goes. */
    private static boolean blocked(ShipGridComponent g, int x, int y, int nx, int ny) {
        Tile from = g.getTile(x, y), to = g.getTile(nx, ny);
        // Still in the plate it came through, or out in space: nothing to bounce off yet
        if (from == null || !from.isOpen()) return false;
        if (to == null) return false;
        return to.isIntactWall() || g.isEdgeBlocked(x, y, nx, ny);
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    /**
     * The flashes on {@code grid} still lighting the deck, to {@code sink}: in its tile space, fading
     * as they age. Call before the light map is drawn.
     */
    public void flashes(ShipGridComponent grid, FlashSink sink) {
        for (int h = 0; h < hits; h++) {
            if (hitGrid[h] != grid) continue;
            float t = hitAge[h], k;
            Color c = colorOf(hitKind[h]);
            switch (hitKind[h]) {
                case ION:
                    k = flickerOf(h) * (1f - t / hitLife[h]);
                    sink.flash(flashX[h], flashY[h], c, 0.7f * k, 3.5f, 0.6f * k);
                    break;
                case BLAST:
                    k = Math.max(0f, 1f - t / 0.6f);
                    if (k > 0f) sink.flash(flashX[h], flashY[h], c, k, 3f + radius[h] * 3f, radius[h] * 1.4f * k);
                    break;
                case ENERGY:
                    k = Math.max(0f, 1f - t / 0.35f);
                    float cool = 1f - t / hitLife[h];
                    sink.flash(gotIn[h] ? flashX[h] : plateX[h], gotIn[h] ? flashY[h] : plateY[h], c,
                            Math.max(0.9f * k, 0.25f * cool * cool), 2f + 3f * severity[h], 0.5f * cool);
                    break;
                default:
                    k = Math.max(0f, 1f - t / Math.min(hitLife[h], 0.3f));
                    if (k <= 0f) break;
                    // A round that got in lights the room; one that didn't, only the hull outside
                    sink.flash(flashX[h], flashY[h], c, (gotIn[h] ? 1f : 0.6f) * k * k,
                            gotIn[h] ? 3f + 5f * severity[h] : 1.5f + 2f * severity[h], 0.6f * k);
            }
        }
    }

    /**
     * The dust and wreckage on {@code grid}: lit like the deck, so drawn with it, before the light
     * map. {@code shapes} is not begun; ({@code offX}, {@code offY}) is the grid's origin and
     * {@code tile} its tile size, in the local frame {@code shapes} already has.
     */
    public void drawDebris(ShipGridComponent grid, ShapeRenderer shapes, float offX, float offY, float tile) {
        if (debris.count == 0 && smoke.count == 0) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < smoke.count; i++) {
            if (smoke.grid[i] != grid) continue;
            float t = smoke.age[i] / smoke.life[i];
            float a = 0.45f * Math.min(1f, Math.min(t * 6f, (1f - t) * 1.6f));
            float r = smoke.size[i] * (0.6f + 1.4f * t) * tile;
            shapes.setColor(0.42f, 0.4f, 0.38f, a);
            shapes.circle(offX + smoke.x[i] * tile, offY + smoke.y[i] * tile, r, 12);
        }
        for (int i = 0; i < debris.count; i++) {
            if (debris.grid[i] != grid) continue;
            float t = debris.age[i] / debris.life[i];
            float a = Math.min(1f, (1f - t) * 3f);
            // Hot from the hit at first, then the plating's own grey
            float hot = Math.max(0f, 1f - debris.age[i] * 2.5f);
            float s = debris.size[i] * tile;
            shapes.setColor(0.62f + 0.38f * hot, 0.6f + 0.15f * hot, 0.56f, a);
            shapes.rect(offX + debris.x[i] * tile - s / 2f, offY + debris.y[i] * tile - s / 2f, s / 2f, s / 2f, s,
                    s * 0.6f, 1f, 1f, debris.angle[i]);
        }
        shapes.end();
    }

    /**
     * What gives off its own light on {@code grid}: flashes, glowing plate, sparks, molten droplets,
     * fireballs, streaks and arcs, drawn additively after the light map so it does not darken them.
     */
    public void drawGlowing(ShipGridComponent grid, ShapeRenderer shapes, float offX, float offY, float tile) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int h = 0; h < hits; h++) {
            if (hitGrid[h] == grid) drawHit(h, shapes, offX, offY, tile);
        }
        for (int i = 0; i < debris.count; i++) {
            // Torn plating glows for a moment as it flies
            float hot = 1f - debris.age[i] * 2.5f;
            if (debris.grid[i] != grid || hot <= 0f) continue;
            float d = debris.size[i] * tile;
            shapes.setColor(1f, 0.55f, 0.2f, 0.8f * hot);
            shapes.rect(offX + debris.x[i] * tile - d / 2f, offY + debris.y[i] * tile - d / 2f, d / 2f, d / 2f, d, d * 0.6f,
                    1f, 1f, debris.angle[i]);
        }
        for (int i = 0; i < embers.count; i++) {
            if (embers.grid[i] != grid) continue;
            float t = embers.age[i] / embers.life[i];
            float flicker = 0.75f + 0.25f * MathUtils.sin(embers.age[i] * 40f + i);
            shapes.setColor(1f, 0.45f + 0.45f * (1f - t), 0.15f * (1f - t), (1f - t) * flicker);
            shapes.circle(offX + embers.x[i] * tile, offY + embers.y[i] * tile, embers.size[i] * (1f - 0.5f * t) * tile, 6);
        }
        for (int i = 0; i < sparks.count; i++) {
            if (sparks.grid[i] != grid) continue;
            float t = sparks.age[i] / sparks.life[i];
            // White hot, through yellow, to orange as it dies
            shapes.setColor(1f, 0.95f - 0.5f * t, 0.8f * (1f - t) * (1f - t), 1f - t * t);
            float x = offX + sparks.x[i] * tile, y = offY + sparks.y[i] * tile;
            float trail = 0.035f * tile;
            shapes.rectLine(x, y, x - sparks.vx[i] * trail, y - sparks.vy[i] * trail, sparks.size[i] * tile);
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawHit(int h, ShapeRenderer shapes, float offX, float offY, float tile) {
        float t = hitAge[h], life = hitLife[h], sev = severity[h];
        Kind kind = hitKind[h];
        Color c = colorOf(kind);
        float fx = offX + flashX[h] * tile, fy = offY + flashY[h] * tile;
        switch (kind) {
            case ION: {
                float k = 1f - t / life;
                glowDisc(shapes, fx, fy, 0.7f * tile * (0.6f + 0.4f * flickerOf(h)), c, 0.35f * k);
                // Arcs crawling out over the systems the bolt stunned, new every frame
                int arcs = 3 + random.nextInt(3);
                for (int a = 0; a < arcs; a++) {
                    float ang = random.nextFloat() * MathUtils.PI2, len = radius[h] * (0.4f + 0.6f * random.nextFloat()) * tile;
                    arc(shapes, fx, fy, fx + MathUtils.cos(ang) * len, fy + MathUtils.sin(ang) * len, 0.18f * tile, 0.03f * tile,
                            0.55f, 0.9f, 1f, 0.9f * k);
                }
                break;
            }
            case BLAST: {
                float grow = Math.min(1f, t / 0.25f), k = 1f - t / life;
                float r = radius[h] * tile * (0.35f + 0.65f * grow);
                // The fireball, white at its heart while it is young
                glowDisc(shapes, fx, fy, r, c, 0.55f * k * k);
                glowDisc(shapes, fx, fy, r * 0.55f, color.set(1f, 0.85f, 0.55f, 1f), 0.6f * Math.max(0f, 1f - t / 0.35f));
                // The shock ring, running on past the fireball
                float ring = Math.min(1f, t / 0.4f);
                if (ring < 1f) ring(shapes, fx, fy, (radius[h] + 1f) * ring * tile, 0.08f * tile, 1f, 0.8f, 0.6f, 0.6f * (1f - ring));
                break;
            }
            case ENERGY: {
                // The plate glows where it was burned, white hot and cooling to dull red
                float cool = 1f - t / life;
                float px = offX + plateX[h] * tile, py = offY + plateY[h] * tile;
                shapes.setColor(1f, 0.25f + 0.7f * cool * cool, 0.1f + 0.6f * cool * cool * cool, 0.75f * cool);
                shapes.circle(px, py, (0.25f + 0.25f * sev) * tile, 14);
                glowDisc(shapes, px, py, (0.6f + 0.6f * sev) * tile, c, 0.3f * cool);
                float k = Math.max(0f, 1f - t / 0.3f);
                if (k > 0f) glowDisc(shapes, fx, fy, radius[h] * tile, c, 0.6f * k);
                break;
            }
            default: {
                float k = Math.max(0f, 1f - t / Math.min(life, 0.35f));
                if (k > 0f) {
                    float r = radius[h] * tile * (gotIn[h] ? 1f : 0.8f) * (0.5f + 0.5f * Math.min(1f, t / 0.05f));
                    glowDisc(shapes, fx, fy, r, c, 0.9f * k);
                    glowDisc(shapes, fx, fy, r * 0.35f, Color.WHITE, k);
                }
                if (passedThrough[h]) {
                    // The streak it left across the deck
                    float s = Math.max(0f, 1f - t / 0.25f);
                    if (s > 0f) {
                        shapes.setColor(1f, 0.9f, 0.6f, 0.8f * s);
                        shapes.rectLine(offX + hitX[h] * tile, offY + hitY[h] * tile, offX + exitX[h] * tile,
                                offY + exitY[h] * tile, 0.06f * tile * (0.5f + sev));
                        glowDisc(shapes, offX + exitX[h] * tile, offY + exitY[h] * tile, 0.5f * radius[h] * tile, c, 0.6f * s);
                    }
                }
                if (hitOutcome[h] == HullHitEvent.Outcome.STOPPED && kind == Kind.KINETIC) {
                    // The plate, struck hot, cooling
                    float cool = 1f - t / life;
                    glowDisc(shapes, offX + plateX[h] * tile, offY + plateY[h] * tile, (0.5f + 0.4f * sev) * tile,
                            color.set(1f, 0.45f, 0.15f, 1f), 0.7f * cool * cool);
                }
            }
        }
    }

    private float flickerOf(int h) {
        return 0.55f + 0.45f * MathUtils.sin(hitAge[h] * 47f + h * 1.7f) * MathUtils.sin(hitAge[h] * 23f + h);
    }

    private static Color colorOf(Kind kind) {
        switch (kind) {
            case ENERGY: return ENERGY;
            case ION: return ION;
            case BLAST: return BLAST;
            default: return KINETIC;
        }
    }

    /** A soft additive disc: brighter at the middle, three rings fading out to {@code r}. */
    private static void glowDisc(ShapeRenderer shapes, float x, float y, float r, Color c, float alpha) {
        if (alpha <= 0f || r <= 0f) return;
        shapes.setColor(c.r, c.g, c.b, alpha * 0.25f);
        shapes.circle(x, y, r, 24);
        shapes.setColor(c.r, c.g, c.b, alpha * 0.35f);
        shapes.circle(x, y, r * 0.62f, 20);
        shapes.setColor(c.r, c.g, c.b, alpha * 0.5f);
        shapes.circle(x, y, r * 0.3f, 16);
    }

    private static void ring(ShapeRenderer shapes, float x, float y, float r, float width, float red, float green,
                             float blue, float alpha) {
        if (r <= 0f || alpha <= 0f) return;
        shapes.setColor(red, green, blue, alpha);
        int n = 32;
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(x + MathUtils.cos(a0) * r, y + MathUtils.sin(a0) * r, x + MathUtils.cos(a1) * r,
                    y + MathUtils.sin(a1) * r, width);
        }
    }

    /** A jagged electric arc from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}). */
    private void arc(ShapeRenderer shapes, float x0, float y0, float x1, float y1, float jag, float width, float red,
                     float green, float blue, float alpha) {
        int n = 6;
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0f) return;
        float px = -dy / len, py = dx / len;
        float lx = x0, ly = y0;
        for (int i = 1; i <= n; i++) {
            float f = (float) i / n;
            float off = i == n ? 0f : (random.nextFloat() * 2f - 1f) * jag;
            float x = x0 + dx * f + px * off, y = y0 + dy * f + py * off;
            shapes.setColor(red, green, blue, alpha * (1f - 0.6f * f));
            shapes.rectLine(lx, ly, x, y, width * (1.4f - f));
            lx = x;
            ly = y;
        }
    }
}
