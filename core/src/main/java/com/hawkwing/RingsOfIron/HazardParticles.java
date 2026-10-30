package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.hawkwing.RingsOfIron.render.GlPass;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Arrays;

/**
 * Particle effects for the interior grid demo: flames over burning tiles, smoke drifting
 * through the rooms that hold it, and wind streaks where air is moving fast.
 *
 * Particles are cosmetic and read the simulation without changing it.
 * <ul>
 *   <li><b>Airflow.</b> Each frame the particles build a local wind field from
 *       {@link ShipGridComponent#airflow}: inside a room, air heads toward the openings
 *       it is leaving through and away from the ones it is coming in through, faster
 *       close to the opening and the more air is moving. Flames, smoke and wind all
 *       ride it.</li>
 *   <li><b>Flames</b> come off each burning tile at a rate and size that grow with its
 *       fire intensity.</li>
 *   <li><b>Smoke</b> comes off fires, is topped up across each room in step with its smoke
 *       density, and appears at a door or gap where smoky air is coming in from the next
 *       room. A puff can only leave its room through an opening air is flowing through
 *       (it bounces off walls and closed doors), and it blows out into space through a
 *       breach.</li>
 *   <li><b>Wind streaks</b> show where air moves fast: they gather toward an opening the
 *       air is rushing through and carry on through it.</li>
 * </ul>
 * Smoke is clipped with the stencil buffer so it stops cleanly at walls and door frames
 * (open or shut). It is not clipped where two rooms meet without a wall or door, such as
 * the mouth of a corridor, so smoke flows naturally there. The window needs a stencil
 * buffer; without one smoke is drawn unclipped.
 *
 * All particles live in fixed arrays (struct of arrays) so nothing is allocated per frame.
 * Positions are in grid units (one tile = 1).
 */
public class HazardParticles {

    private static final int MAX_FIRE = 3000;
    private static final int MAX_SMOKE = 2500;
    private static final int MAX_WIND = 1500;

    /** Flame particles emitted per second by a fully involved tile. */
    private static final float FIRE_EMIT_RATE = 60f;
    /** Smoke puffs emitted per second by a fully involved tile. */
    private static final float FIRE_SMOKE_RATE = 6f;
    /** Ambient smoke puffs kept alive per tile, per kg/m³ of smoke density. */
    private static final float SMOKE_PER_DENSITY = 900f;
    /** Most ambient smoke puffs kept per tile, so thick smoke does not swamp the frame. */
    private static final float MAX_SMOKE_PER_TILE = 6f;
    /** Smoke puffs spawned at an opening per kg of smoke flowing in through it. */
    private static final float DOOR_SMOKE_PER_KG = 2500f;
    /** Most smoke puffs per second at one opening. */
    private static final float MAX_DOOR_SMOKE_RATE = 25f;

    /** Wind speed (tiles/s) per mol/s of flow, at one tile from the opening. */
    private static final float WIND_PER_FLOW = 0.02f;
    /** Fastest wind the particles are pushed with, tiles/s. */
    private static final float MAX_WIND_SPEED = 6f;
    /** Flow through an opening (mol/s) below which it gets no wind streaks. */
    private static final float WIND_MIN_FLOW = 4f;
    /** Wind streaks spawned per second per mol/s of flow. */
    private static final float WIND_STREAKS_PER_FLOW = 0.4f;
    /** Most wind streaks per second at one opening. */
    private static final float MAX_WIND_STREAK_RATE = 40f;

    private final Particles fire = new Particles(MAX_FIRE);
    private final Particles smoke = new Particles(MAX_SMOKE);
    private final Particles wind = new Particles(MAX_WIND);
    /** Fractional emissions carried between frames, so low rates still emit. */
    private float[] fireDebt = new float[0], smokeDebt = new float[0];
    /** Per tile side (index tile * 4 + side), for openings. */
    private float[] doorSmokeDebt = new float[0], windDebt = new float[0];
    /** Smoke puffs currently alive in each compartment. */
    private int[] smokeCount = new int[0];

    // Wind field: openings grouped by room, rebuilt every frame
    private int openingCount;
    private float[] openX = new float[64], openY = new float[64], openFlow = new float[64];
    private int[] openRoom = new int[64];
    private final float[] windOut = new float[2];

    /**
     * Smoke clipping regions: air tiles joined by edges with no wall or door at all.
     * Index y * width + x, -1 for tiles outside any region.
     */
    private int[] region = new int[0];
    private int regionCount;

    /** Structure-of-arrays particle pool. */
    private static final class Particles {
        final float[] x, y, vx, vy, age, life, size, heat;
        /** Compartment id the particle is in, or -1 in space. */
        final int[] room;
        int count;

        Particles(int max) {
            x = new float[max]; y = new float[max]; vx = new float[max]; vy = new float[max];
            age = new float[max]; life = new float[max]; size = new float[max]; heat = new float[max];
            room = new int[max];
        }

        boolean full() {
            return count >= x.length;
        }

        int add() {
            return count++;
        }

        void remove(int i) {
            int last = --count;
            x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last];
            age[i] = age[last]; life[i] = life[last]; size[i] = size[last]; heat[i] = heat[last];
            room[i] = room[last];
        }
    }

    public void clear() {
        fire.count = 0;
        smoke.count = 0;
        wind.count = 0;
    }

    public void update(ShipGridComponent grid, float dt) {
        buildRegions(grid); // the drawing needs these even on a frame with no time step
        if (dt <= 0f) return;
        dt = Math.min(dt, 0.1f);
        int tiles = grid.width * grid.height;
        if (fireDebt.length != tiles) {
            fireDebt = new float[tiles];
            smokeDebt = new float[tiles];
            doorSmokeDebt = new float[tiles * 4];
            windDebt = new float[tiles * 4];
        }
        buildWindField(grid);
        countSmoke(grid);
        emit(grid, dt);
        emitAtOpenings(grid, dt);
        stepFire(grid, dt);
        stepSmoke(grid, dt);
        stepWind(grid, dt);
    }

    // -----------------------------------------------------------------------
    // Clipping regions
    // -----------------------------------------------------------------------

    /** Flood-fills air tiles across edges that carry no edge wall or door. */
    private void buildRegions(ShipGridComponent grid) {
        int w = grid.width, n = w * grid.height;
        if (region.length != n) region = new int[n];
        Arrays.fill(region, -1);
        regionCount = 0;
        int[] stack = new int[n];
        for (int start = 0; start < n; start++) {
            if (region[start] >= 0 || grid.compartmentAt(start % w, start / w) == null) continue;
            int id = regionCount++;
            int top = 0;
            stack[top++] = start;
            region[start] = id;
            while (top > 0) {
                int c = stack[--top];
                int x = c % w, y = c / w;
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    int ni = ny * w + nx;
                    if (region[ni] >= 0 || !grid.canFlow(x, y, nx, ny)) continue;
                    if (grid.getEdge(x, y, s) != null) continue; // a door frame clips smoke even when open
                    region[ni] = id;
                    stack[top++] = ni;
                }
            }
        }
    }

    private int regionAt(ShipGridComponent grid, float x, float y) {
        int tx = MathUtils.floor(x), ty = MathUtils.floor(y);
        return grid.inBounds(tx, ty) ? region[ty * grid.width + tx] : -1;
    }

    // -----------------------------------------------------------------------
    // Wind field
    // -----------------------------------------------------------------------

    private void buildWindField(ShipGridComponent grid) {
        openingCount = 0;
        for (AirflowEdge e : grid.airflow) {
            if (e.room == null || e.flow == 0f) continue;
            if (openingCount == openX.length) {
                int n = openingCount * 2;
                openX = Arrays.copyOf(openX, n);
                openY = Arrays.copyOf(openY, n);
                openFlow = Arrays.copyOf(openFlow, n);
                openRoom = Arrays.copyOf(openRoom, n);
            }
            // Aim at a point just beyond the opening so air streams through it
            float px = e.x + 0.5f, py = e.y + 0.5f;
            if (e.side != null) {
                px += e.side.dx;
                py += e.side.dy;
            }
            openX[openingCount] = px;
            openY[openingCount] = py;
            openFlow[openingCount] = e.flow;
            openRoom[openingCount] = e.room.id;
            openingCount++;
        }
    }

    /** Local wind at (x, y) in room {@code room}, tiles/s, written to {@link #windOut}. */
    private float[] windAt(int room, float x, float y) {
        float vx = 0f, vy = 0f;
        if (room >= 0) {
            for (int i = 0; i < openingCount; i++) {
                if (openRoom[i] != room) continue;
                float dx = openX[i] - x, dy = openY[i] - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1e-4f) continue;
                // Toward an opening air leaves by, away from one it comes in by
                float speed = WIND_PER_FLOW * openFlow[i] / (d + 0.5f) * 1.5f;
                vx += dx / d * speed;
                vy += dy / d * speed;
            }
        }
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len > MAX_WIND_SPEED) {
            vx *= MAX_WIND_SPEED / len;
            vy *= MAX_WIND_SPEED / len;
        }
        windOut[0] = vx;
        windOut[1] = vy;
        return windOut;
    }

    // -----------------------------------------------------------------------
    // Emission
    // -----------------------------------------------------------------------

    private void countSmoke(ShipGridComponent grid) {
        if (smokeCount.length < grid.compartments.size) smokeCount = new int[grid.compartments.size * 2];
        Arrays.fill(smokeCount, 0);
        for (int i = 0; i < smoke.count; i++) {
            int r = smoke.room[i];
            if (r >= 0 && r < smokeCount.length) smokeCount[r]++;
        }
    }

    private void emit(ShipGridComponent grid, float dt) {
        for (int ty = 0; ty < grid.height; ty++) {
            for (int tx = 0; tx < grid.width; tx++) {
                Tile t = grid.getTile(tx, ty);
                int idx = ty * grid.width + tx;
                if (!t.isBurning()) {
                    fireDebt[idx] = 0f;
                    continue;
                }
                float intensity = t.fireIntensity;
                fireDebt[idx] += FIRE_EMIT_RATE * (0.2f + 0.8f * intensity) * dt;
                while (fireDebt[idx] >= 1f && !fire.full()) {
                    fireDebt[idx] -= 1f;
                    spawnFlame(tx, ty, intensity);
                }
                smokeDebt[idx] += FIRE_SMOKE_RATE * intensity * dt;
                while (smokeDebt[idx] >= 1f && !smoke.full() && t.compartment != null) {
                    smokeDebt[idx] -= 1f;
                    spawnSmoke(tx + 0.5f + MathUtils.random(-0.3f, 0.3f), ty + 0.5f + MathUtils.random(-0.3f, 0.3f),
                            t.compartment.id, 0.6f);
                }
            }
        }

        // Keep each room's ambient smoke in step with its density
        for (Compartment c : grid.compartments) {
            if (c.tiles.size == 0) continue;
            float perTile = Math.min(MAX_SMOKE_PER_TILE, c.smokeDensity() * SMOKE_PER_DENSITY);
            int target = (int) (perTile * c.tiles.size);
            int have = c.id < smokeCount.length ? smokeCount[c.id] : 0;
            // Fill gradually so a new cloud rolls in instead of popping into existence
            int spawn = Math.min(target - have, Math.max(1, c.tiles.size / 4));
            for (int i = 0; i < spawn && !smoke.full(); i++) {
                int packed = c.tiles.get(MathUtils.random(c.tiles.size - 1));
                float x = packed % grid.width + MathUtils.random();
                float y = packed / grid.width + MathUtils.random();
                spawnSmoke(x, y, c.id, 0f);
            }
        }
    }

    /** Smoke where smoky air comes in through an opening, and wind streaks where flow is strong. */
    private void emitAtOpenings(ShipGridComponent grid, float dt) {
        for (AirflowEdge e : grid.airflow) {
            if (e.room == null || e.side == null) continue;
            int slot = (e.y * grid.width + e.x) * 4 + e.side.ordinal();
            // Edge midpoint, and a unit vector along the edge for spreading spawns
            float ex = e.x + 0.5f + e.side.dx * 0.45f, ey = e.y + 0.5f + e.side.dy * 0.45f;
            float ax = e.side.dy, ay = e.side.dx;

            if (e.smokeFlow < 0f) {
                doorSmokeDebt[slot] += Math.min(MAX_DOOR_SMOKE_RATE, -e.smokeFlow * DOOR_SMOKE_PER_KG) * dt;
                while (doorSmokeDebt[slot] >= 1f && !smoke.full()) {
                    doorSmokeDebt[slot] -= 1f;
                    float along = MathUtils.random(-0.4f, 0.4f) * e.width;
                    int i = spawnSmoke(ex + ax * along, ey + ay * along, e.room.id, 0f);
                    // Pushed in through the opening
                    smoke.vx[i] = -e.side.dx * 0.6f;
                    smoke.vy[i] = -e.side.dy * 0.6f;
                    smoke.size[i] *= 0.6f;
                }
            } else {
                doorSmokeDebt[slot] = 0f;
            }

            float flow = Math.abs(e.flow);
            if (e.flow > 0f && flow >= WIND_MIN_FLOW) {
                // Streaks start upstream in the room and gather toward the opening
                windDebt[slot] += Math.min(MAX_WIND_STREAK_RATE, flow * WIND_STREAKS_PER_FLOW) * dt;
                while (windDebt[slot] >= 1f && !wind.full()) {
                    windDebt[slot] -= 1f;
                    float r = MathUtils.random(0.3f, 2.5f);
                    float angle = MathUtils.atan2(-e.side.dy, -e.side.dx) + MathUtils.random(-1.2f, 1.2f);
                    float x = ex + MathUtils.cos(angle) * r, y = ey + MathUtils.sin(angle) * r;
                    if (roomAt(grid, x, y) != e.room) continue;
                    spawnWind(x, y, e.room.id);
                }
            } else {
                windDebt[slot] = 0f;
            }
        }
    }

    private void spawnFlame(int tx, int ty, float intensity) {
        int i = fire.add();
        // Bigger fires fill more of their tile
        float spread = 0.15f + 0.3f * intensity;
        fire.x[i] = tx + 0.5f + MathUtils.random(-spread, spread);
        fire.y[i] = ty + 0.5f + MathUtils.random(-spread, spread);
        float angle = MathUtils.random(MathUtils.PI2);
        float speed = MathUtils.random(0.1f, 0.5f) * (0.5f + intensity);
        fire.vx[i] = MathUtils.cos(angle) * speed;
        fire.vy[i] = MathUtils.sin(angle) * speed;
        fire.age[i] = 0f;
        fire.life[i] = MathUtils.random(0.35f, 0.9f);
        fire.size[i] = MathUtils.random(0.06f, 0.13f) * (0.6f + intensity);
        fire.heat[i] = MathUtils.random(0.7f, 1f);
        fire.room[i] = -1;
    }

    /** @param push initial outward speed, for puffs leaving a fire */
    private int spawnSmoke(float x, float y, int room, float push) {
        int i = smoke.add();
        smoke.x[i] = x;
        smoke.y[i] = y;
        float angle = MathUtils.random(MathUtils.PI2);
        float speed = MathUtils.random(0.05f, 0.2f) + push * MathUtils.random(0.5f, 1f);
        smoke.vx[i] = MathUtils.cos(angle) * speed;
        smoke.vy[i] = MathUtils.sin(angle) * speed;
        smoke.age[i] = 0f;
        smoke.life[i] = MathUtils.random(3f, 7f);
        smoke.size[i] = MathUtils.random(0.3f, 0.5f);
        smoke.heat[i] = MathUtils.random(0.3f, 0.5f); // grey level
        smoke.room[i] = room;
        return i;
    }

    private void spawnWind(float x, float y, int room) {
        int i = wind.add();
        wind.x[i] = x;
        wind.y[i] = y;
        float[] v = windAt(room, x, y);
        wind.vx[i] = v[0];
        wind.vy[i] = v[1];
        wind.age[i] = 0f;
        wind.life[i] = MathUtils.random(0.5f, 1.1f);
        wind.room[i] = room;
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    private void stepFire(ShipGridComponent grid, float dt) {
        for (int i = fire.count - 1; i >= 0; i--) {
            fire.age[i] += dt;
            if (fire.age[i] >= fire.life[i]) {
                fire.remove(i);
                continue;
            }
            // Flames lean with the draught
            Compartment room = roomAt(grid, fire.x[i], fire.y[i]);
            float[] v = windAt(room == null ? -1 : room.id, fire.x[i], fire.y[i]);
            fire.x[i] += (fire.vx[i] + v[0] * 0.8f) * dt;
            fire.y[i] += (fire.vy[i] + v[1] * 0.8f) * dt;
            // Licking flames: a little turbulence, slowing as they burn out
            fire.vx[i] = fire.vx[i] * 0.97f + MathUtils.random(-1f, 1f) * dt;
            fire.vy[i] = fire.vy[i] * 0.97f + MathUtils.random(-1f, 1f) * dt;
        }
    }

    private void stepSmoke(ShipGridComponent grid, float dt) {
        float pull = Math.min(1f, 2f * dt);
        for (int i = smoke.count - 1; i >= 0; i--) {
            smoke.age[i] += dt;
            if (smoke.age[i] >= smoke.life[i]) {
                smoke.remove(i);
                continue;
            }
            if (smoke.room[i] < 0) {
                // Blown out into space: keep going and thin out fast
                smoke.age[i] += dt * 2f;
                smoke.x[i] += smoke.vx[i] * dt;
                smoke.y[i] += smoke.vy[i] * dt;
                smoke.size[i] += 0.3f * dt;
                continue;
            }
            // Rooms are renumbered when walls or doors change: a puff joins whatever room it is in now
            Compartment room = roomAt(grid, smoke.x[i], smoke.y[i]);
            if (room == null) {
                smoke.remove(i);
                continue;
            }
            smoke.room[i] = room.id;
            // Carried by the local airflow, with a slow random drift on top
            float[] v = windAt(room.id, smoke.x[i], smoke.y[i]);
            smoke.vx[i] += (v[0] - smoke.vx[i]) * pull + MathUtils.random(-0.15f, 0.15f) * dt;
            smoke.vy[i] += (v[1] - smoke.vy[i]) * pull + MathUtils.random(-0.15f, 0.15f) * dt;
            move(grid, smoke, i, dt, false);
            // Puffs swell as they spread
            smoke.size[i] += 0.04f * dt;
        }
    }

    private void stepWind(ShipGridComponent grid, float dt) {
        for (int i = wind.count - 1; i >= 0; i--) {
            wind.age[i] += dt;
            if (wind.age[i] >= wind.life[i]) {
                wind.remove(i);
                continue;
            }
            if (wind.room[i] >= 0) {
                Compartment room = roomAt(grid, wind.x[i], wind.y[i]);
                if (room == null) {
                    wind.remove(i);
                    continue;
                }
                wind.room[i] = room.id;
                float[] v = windAt(room.id, wind.x[i], wind.y[i]);
                wind.vx[i] += (v[0] - wind.vx[i]) * Math.min(1f, 6f * dt);
                wind.vy[i] += (v[1] - wind.vy[i]) * Math.min(1f, 6f * dt);
            }
            // Out in space a streak keeps the speed it left with
            if (!move(grid, wind, i, dt, true)) wind.remove(i);
        }
    }

    /**
     * Moves particle i one step, one axis at a time. It may cross into a neighbouring room
     * only through an opening air can flow through, and out to space only through a
     * breach or gap; anything else stops it.
     *
     * @param dieOnWall true to report a blocked move (the caller removes the particle),
     *                  false to bounce off instead
     * @return false if the particle was blocked and {@code dieOnWall} is set
     */
    private static boolean move(ShipGridComponent grid, Particles p, int i, float dt, boolean dieOnWall) {
        if (p.room[i] < 0) {
            p.x[i] += p.vx[i] * dt;
            p.y[i] += p.vy[i] * dt;
            return true;
        }
        for (int axis = 0; axis < 2; axis++) {
            float ox = p.x[i], oy = p.y[i];
            float nx = axis == 0 ? ox + p.vx[i] * dt : ox;
            float ny = axis == 1 ? oy + p.vy[i] * dt : oy;
            int tx = MathUtils.floor(ox), ty = MathUtils.floor(oy);
            int ntx = MathUtils.floor(nx), nty = MathUtils.floor(ny);
            if (tx == ntx && ty == nty) {
                p.x[i] = nx;
                p.y[i] = ny;
                continue;
            }
            int pass = crossing(grid, tx, ty, ntx, nty);
            if (pass > 0) {
                p.x[i] = nx;
                p.y[i] = ny;
                if (pass == 2) p.room[i] = -1;
            } else if (dieOnWall) {
                return false;
            } else if (axis == 0) {
                p.vx[i] = -p.vx[i];
            } else {
                p.vy[i] = -p.vy[i];
            }
        }
        return true;
    }

    /** 0 if blocked, 1 if the move stays inside the ship's air, 2 if it goes out to space. */
    private static int crossing(ShipGridComponent grid, int x, int y, int nx, int ny) {
        if (grid.canFlow(x, y, nx, ny)) return 1;
        Tile to = grid.getTile(nx, ny);
        if ((to == null || to.isSpaceExposed()) && !grid.isEdgeBlocked(x, y, nx, ny)
                && Side.between(x, y, nx, ny) != null) return 2;
        return 0;
    }

    private static Compartment roomAt(ShipGridComponent grid, float x, float y) {
        return grid.compartmentAt(MathUtils.floor(x), MathUtils.floor(y));
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    /** Stencil value marking walls and sealed tiles; smoke regions use their id + 1, space 0. */
    private static final int STENCIL_WALL = 255;

    /**
     * Draws smoke clipped to its room, wind streaks, then flames. Call outside any
     * ShapeRenderer batch; this opens its own and leaves blending enabled with the standard
     * function.
     *
     * @param offX screen x of the grid's left edge
     * @param offY screen y of the grid's bottom edge
     * @param tilePx screen size of one tile
     */
    public void draw(ShipGridComponent grid, ShapeRenderer shapes, float offX, float offY, float tilePx) {
        GlPass.alphaBlend();
        boolean clip = Gdx.graphics.getBufferFormat().stencil > 0;
        if (clip) writeStencil(grid, shapes, offX, offY, tilePx);

        // Smoke: soft grey puffs, each drawn only over its own region (or space)
        int regions = Math.min(regionCount, STENCIL_WALL - 1);
        for (int r = -1; r < (clip ? regions : 0); r++) {
            if (clip) Gdx.gl.glStencilFunc(GL20.GL_EQUAL, r + 1, 0xFF);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < smoke.count; i++) {
                if (clip && (smoke.room[i] < 0 ? -1 : regionAt(grid, smoke.x[i], smoke.y[i])) != r) continue;
                float t = smoke.age[i] / smoke.life[i];
                float alpha = 0.22f * Math.min(1f, Math.min(t * 4f, (1f - t) * 3f));
                float g = smoke.heat[i];
                shapes.setColor(g, g, g, alpha);
                shapes.circle(offX + smoke.x[i] * tilePx, offY + smoke.y[i] * tilePx, smoke.size[i] * tilePx, 10);
            }
            shapes.end();
        }

        // Wind and flames go anywhere except over walls
        if (clip) Gdx.gl.glStencilFunc(GL20.GL_NOTEQUAL, STENCIL_WALL, 0xFF);

        // Wind: pale streaks trailing behind each particle, longer the faster it moves
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < wind.count; i++) {
            float t = wind.age[i] / wind.life[i];
            float speed = (float) Math.sqrt(wind.vx[i] * wind.vx[i] + wind.vy[i] * wind.vy[i]);
            float alpha = Math.min(0.8f, 0.2f + speed * 0.2f) * Math.min(1f, Math.min(t * 5f, (1f - t) * 3f));
            shapes.setColor(0.75f, 0.9f, 1f, alpha);
            float x = offX + wind.x[i] * tilePx, y = offY + wind.y[i] * tilePx;
            float trail = 0.08f * tilePx;
            shapes.rectLine(x, y, x - wind.vx[i] * trail, y - wind.vy[i] * trail, 0.05f * tilePx);
        }
        shapes.end();

        // Flames: additive, white-yellow when fresh, cooling through orange to dull red
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < fire.count; i++) {
            float t = fire.age[i] / fire.life[i];
            float h = fire.heat[i] * (1f - t);
            float g = MathUtils.clamp(0.15f + 0.85f * h, 0f, 1f);
            float b = MathUtils.clamp((h - 0.7f) * 1.5f, 0f, 1f);
            float alpha = 0.55f * (1f - t);
            float size = fire.size[i] * (1f - 0.5f * t) * tilePx;
            shapes.setColor(1f, g, b, alpha);
            shapes.circle(offX + fire.x[i] * tilePx, offY + fire.y[i] * tilePx, size, 8);
        }
        shapes.end();

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (clip) GlPass.endStencil();
    }

    /** Marks every tile in the stencil buffer with its smoke region + 1, walls with 255, space 0. */
    private void writeStencil(ShipGridComponent grid, ShapeRenderer shapes, float offX, float offY, float tilePx) {
        GlPass.clearStencil();
        // One batch per stencil value, since the reference value cannot change mid-batch
        int regions = Math.min(regionCount, STENCIL_WALL - 1);
        for (int value = 1; value <= regions + 1; value++) {
            int target = value <= regions ? value : STENCIL_WALL;
            GlPass.stencilWrite(GL20.GL_ALWAYS, target, GL20.GL_REPLACE);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    Tile t = grid.getTile(x, y);
                    int r = region[y * grid.width + x];
                    int v = r >= 0 ? Math.min(r + 1, STENCIL_WALL - 1)
                            : t.isSpaceExposed() ? 0 : STENCIL_WALL;
                    if (v != target) continue;
                    shapes.rect(offX + x * tilePx, offY + y * tilePx, tilePx, tilePx);
                }
            }
            shapes.end();
        }
        // Space first: draw() keeps its smoke to the 0s
        GlPass.keepWhere(GL20.GL_EQUAL, 0);
    }
}
