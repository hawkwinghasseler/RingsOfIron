package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Particle effects for the interior grid demo: flames over burning tiles and smoke
 * drifting through the rooms that hold it.
 *
 * Particles are cosmetic and read the simulation without changing it. Flames are emitted
 * from each burning tile at a rate and size that grow with its fire intensity. Smoke is
 * emitted from fires and topped up across each compartment in proportion to its smoke
 * density, and it stays inside its compartment: a puff that drifts into a wall, a sealed
 * tile or another room bounces back.
 *
 * All particles live in fixed arrays (struct of arrays) so nothing is allocated per frame.
 * Positions are in grid units (one tile = 1).
 */
public class HazardParticles {

    private static final int MAX_FIRE = 3000;
    private static final int MAX_SMOKE = 2500;

    /** Flame particles emitted per second by a fully involved tile. */
    private static final float FIRE_EMIT_RATE = 60f;
    /** Smoke puffs emitted per second by a fully involved tile. */
    private static final float FIRE_SMOKE_RATE = 6f;
    /** Ambient smoke puffs kept alive per tile, per kg/m³ of smoke density. */
    private static final float SMOKE_PER_DENSITY = 900f;
    /** Most ambient smoke puffs kept per tile, so thick smoke does not swamp the frame. */
    private static final float MAX_SMOKE_PER_TILE = 6f;

    private final Particles fire = new Particles(MAX_FIRE);
    private final Particles smoke = new Particles(MAX_SMOKE);
    /** Fractional emissions carried between frames, per tile, so low rates still emit. */
    private float[] fireDebt = new float[0], smokeDebt = new float[0];
    /** Smoke puffs currently alive in each compartment. */
    private int[] smokeCount = new int[0];

    /** Structure-of-arrays particle pool. */
    private static final class Particles {
        final float[] x, y, vx, vy, age, life, size, heat;
        /** Compartment id a smoke puff belongs to (unused for fire). */
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
    }

    public void update(ShipGridComponent grid, float dt) {
        if (dt <= 0f) return;
        dt = Math.min(dt, 0.1f);
        int tiles = grid.width * grid.height;
        if (fireDebt.length != tiles) {
            fireDebt = new float[tiles];
            smokeDebt = new float[tiles];
        }
        countSmoke(grid);
        emit(grid, dt);
        stepFire(dt);
        stepSmoke(grid, dt);
    }

    // -----------------------------------------------------------------------
    // Emission
    // -----------------------------------------------------------------------

    private void countSmoke(ShipGridComponent grid) {
        if (smokeCount.length < grid.compartments.size) smokeCount = new int[grid.compartments.size * 2];
        java.util.Arrays.fill(smokeCount, 0);
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
    }

    /** @param push initial outward speed, for puffs leaving a fire */
    private void spawnSmoke(float x, float y, int room, float push) {
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
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    private void stepFire(float dt) {
        for (int i = fire.count - 1; i >= 0; i--) {
            fire.age[i] += dt;
            if (fire.age[i] >= fire.life[i]) {
                fire.remove(i);
                continue;
            }
            fire.x[i] += fire.vx[i] * dt;
            fire.y[i] += fire.vy[i] * dt;
            // Licking flames: a little turbulence, slowing as they burn out
            fire.vx[i] = fire.vx[i] * 0.97f + MathUtils.random(-1f, 1f) * dt;
            fire.vy[i] = fire.vy[i] * 0.97f + MathUtils.random(-1f, 1f) * dt;
        }
    }

    private void stepSmoke(ShipGridComponent grid, float dt) {
        for (int i = smoke.count - 1; i >= 0; i--) {
            smoke.age[i] += dt;
            // Rooms are renumbered when walls or doors change: a puff joins whatever room it is in now
            Compartment room = roomAt(grid, smoke.x[i], smoke.y[i]);
            if (smoke.age[i] >= smoke.life[i] || room == null) {
                smoke.remove(i);
                continue;
            }
            smoke.room[i] = room.id;
            // Slow random drift, damped
            smoke.vx[i] = smoke.vx[i] * (1f - 0.6f * dt) + MathUtils.random(-0.15f, 0.15f) * dt;
            smoke.vy[i] = smoke.vy[i] * (1f - 0.6f * dt) + MathUtils.random(-0.15f, 0.15f) * dt;
            // Stay inside the room: bounce off anything that is not part of it
            float nx = smoke.x[i] + smoke.vx[i] * dt;
            if (roomAt(grid, nx, smoke.y[i]) != room) smoke.vx[i] = -smoke.vx[i];
            else smoke.x[i] = nx;
            float ny = smoke.y[i] + smoke.vy[i] * dt;
            if (roomAt(grid, smoke.x[i], ny) != room) smoke.vy[i] = -smoke.vy[i];
            else smoke.y[i] = ny;
            // Puffs swell as they spread
            smoke.size[i] += 0.04f * dt;
        }
    }

    private static Compartment roomAt(ShipGridComponent grid, float x, float y) {
        return grid.compartmentAt(MathUtils.floor(x), MathUtils.floor(y));
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    /**
     * Draws smoke, then flames over it. Call outside any ShapeRenderer batch; this opens
     * its own and leaves blending enabled with the standard function.
     *
     * @param offX screen x of the grid's left edge
     * @param offY screen y of the grid's bottom edge
     * @param tilePx screen size of one tile
     */
    public void draw(ShapeRenderer shapes, float offX, float offY, float tilePx) {
        Gdx.gl.glEnable(GL20.GL_BLEND);

        // Smoke: soft grey puffs, fading in and out over their life
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < smoke.count; i++) {
            float t = smoke.age[i] / smoke.life[i];
            float alpha = 0.22f * Math.min(1f, Math.min(t * 4f, (1f - t) * 3f));
            float g = smoke.heat[i];
            shapes.setColor(g, g, g, alpha);
            shapes.circle(offX + smoke.x[i] * tilePx, offY + smoke.y[i] * tilePx, smoke.size[i] * tilePx, 10);
        }
        shapes.end();

        // Flames: additive, white-yellow when fresh, cooling through orange to dull red
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < fire.count; i++) {
            float t = fire.age[i] / fire.life[i];
            float h = fire.heat[i] * (1f - t);
            float r = 1f;
            float g = MathUtils.clamp(0.15f + 0.85f * h, 0f, 1f);
            float b = MathUtils.clamp((h - 0.7f) * 1.5f, 0f, 1f);
            float alpha = 0.55f * (1f - t);
            float size = fire.size[i] * (1f - 0.5f * t) * tilePx;
            shapes.setColor(r, g, b, alpha);
            shapes.circle(offX + fire.x[i] * tilePx, offY + fire.y[i] * tilePx, size, 8);
        }
        shapes.end();

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
