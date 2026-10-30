package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.DeckTools;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.InteriorScene;
import com.hawkwing.RingsOfIron.game.WorldDebug;
import com.hawkwing.RingsOfIron.game.WoundWatch;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;

import java.util.List;

/**
 * The old interior demo's controls, for a scenario built on {@link InteriorScene}: the deck tools
 * ({@link DeckTools}) armed from the debug menu and used with a left click on the deck (drag in from
 * outside the hull to aim a shot or a ram), the hull, attacker and ship buttons, and an overlay with
 * the tool, what the last use did, the tile under the cursor and the wound account.
 */
final class InteriorControls implements ScenarioPointer {
    /** Who the attacker comes from: the bearing from the bow, degrees. */
    static final float ATTACK_BEARING = 90f;
    /** Lines of the wound account the overlay shows. */
    static final int ACCOUNT_LINES = 8;
    /** Where the panel starts above the screen's bottom edge, clear of the game's hint lines, pixels. */
    static final float PANEL_BOTTOM = 100f;
    private static final Family PROJECTILES = Family.all(ProjectileComponent.class).get();
    private static final Family ROCKS = Family.all(KinematicTransientComponent.class).get();

    final ScenarioSetup setup;
    final InteriorScene scene;
    final DeckTools tools;
    /** Whether a left click on the deck uses the tool; off, the deck's clicks are the game's. */
    boolean armed = true;
    String message = "";
    private int attackerHull;
    /** Where a left-button press started, ship-local meters, or null. */
    private Vector2 pressedAt;
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();

    InteriorControls(ScenarioSetup setup, InteriorScene scene) {
        this.setup = setup;
        this.scene = scene;
        this.tools = new DeckTools(setup.world.crew, scene.debug);
        scene.shipChanged = ship -> {
            setup.world.bridge.stand();
            setup.playerShipChanged();
            setup.deckView();
        };
        setup.pointer(this);
    }

    /** Puts the player on deck, standing, to watch. */
    void onDeck() {
        setup.world.bridge.stand();
        setup.deckView();
    }

    /** The hull, ship and tool buttons, in the debug menu's scenario section. */
    void addActions() {
        setup.action("Next hull", "The player in the next hull in the data, crew and all", () -> say(scene.cycle(1)));
        setup.action("Previous hull", "The player in the hull before", () -> say(scene.cycle(-1)));
        setup.action("Fresh hull", "This hull again, as good as new", () -> say(scene.setHull(scene.hull)));
        setup.action("Attacker", "An armed ship opens fire from the side, or goes away", () -> {
            if (scene.attacker != null) {
                scene.ceaseFire();
                say("Attacker gone");
            } else {
                say(scene.attack(attackerHull(), ATTACK_BEARING));
            }
        });
        setup.action("Next attacker hull", "The attacker in the next hull", () -> {
            attackerHull++;
            say(scene.attack(attackerHull(), ATTACK_BEARING));
        });
        setup.action("Pin or drift", "Hold the ship still where it is, or let hits knock it about",
                () -> say(scene.togglePinned() ? "Ship pinned in place" : "Ship free to drift"));
        setup.action("Heal all", "Everyone aboard unhurt", () -> {
            for (Entity e : scene.debug.crewAboard(scene.ship())) scene.debug.heal(e);
            say("Everyone healed");
        });
        setup.action("Fires out", "Every fire aboard put out", () -> {
            scene.debug.extinguish(scene.ship());
            say("Fires out");
        });
        setup.action("Fresh air", "Every room refilled and cooled", () -> {
            scene.debug.freshAir(scene.ship());
            say("Fresh air");
        });
        setup.action("Doors", "Seal every door, or open them all",
                () -> say(scene.debug.toggleDoors(scene.ship()) ? "Doors sealed" : "Doors open"));
        setup.action("Reactors", "Every reactor off, or back on",
                () -> say(scene.debug.toggleReactors(scene.ship()) ? "Reactors on" : "Reactors off"));
        for (DeckTools.Tool t : DeckTools.Tool.values()) {
            setup.action("Tool: " + t.title, "Left click on the deck uses it", () -> {
                tools.tool = t;
                armed = true;
                say("Tool: " + t.title + tools.setting());
            });
        }
        setup.action("Tool setting +", "The tool's amount, weapon, warhead, speed or role, up one", () -> {
            tools.step(1);
            say(tools.tool.title + tools.setting());
        });
        setup.action("Tool setting -", "... down one", () -> {
            tools.step(-1);
            say(tools.tool.title + tools.setting());
        });
        setup.action("Tool second setting", "The wound's cause, the ram's mass, crew or boarder", () -> {
            tools.alternate();
            say(tools.tool.title + tools.setting());
        });
        setup.action("Tools off", "Clicks on the deck are the game's again (a tool button arms them)", () -> {
            armed = !armed;
            say(armed ? "Tool armed: " + tools.tool.title : "Tools off");
        });
        setup.overlay("interior-tools", "The interior tool, its last use, the tile under the cursor and the wound account",
                new Panel(), true);
        setup.overlay("rounds", "Rounds, missiles, rocks and beams in flight", new Flight(), true);
    }

    private String attackerHull() {
        List<String> hulls = scene.hulls();
        return hulls.get(Math.floorMod(attackerHull + hulls.indexOf(GameScenario.RAIDER_SHIP), hulls.size()));
    }

    void say(String text) {
        message = text;
        // The scene notes its own (a new hull, an attacker), so the account doesn't get it twice
        List<WoundWatch.Line> lines = scene.watch.lines();
        if (lines.isEmpty() || !lines.get(lines.size() - 1).text.equals(text)) scene.watch.note(text);
    }

    // --- The mouse -------------------------------------------------------------------

    private Body body() {
        return scene.world.docking.bodyOf(scene.ship());
    }

    @Override
    public boolean down(float worldX, float worldY, int button, boolean deckView) {
        if (!armed || !deckView || button != Input.Buttons.LEFT || body() == null) return false;
        pressedAt = WorldDebug.toLocal(body(), worldX, worldY, new Vector2());
        return true;
    }

    @Override
    public void up(float worldX, float worldY, int button) {
        if (pressedAt == null || body() == null) return;
        Vector2 from = pressedAt;
        pressedAt = null;
        say(tools.apply(scene.ship(), WorldDebug.toLocal(body(), worldX, worldY, new Vector2()), from));
    }

    // --- The overlays ----------------------------------------------------------------

    /** The tool and its setting, the last thing done, the tile under the cursor, and the newest wound lines. */
    private final class Panel implements DebugOverlay {
        @Override
        public void drawScreen(OverlayView view) {
            List<WoundWatch.Line> lines = scene.watch.lines();
            float line = 17f, x = 12f, y = PANEL_BOTTOM + line * (ACCOUNT_LINES + 4);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            view.shapes.begin(ShapeRenderer.ShapeType.Filled);
            view.shapes.setColor(0f, 0f, 0f, 0.6f);
            view.shapes.rect(0f, PANEL_BOTTOM - 8f, Math.min(560f, view.uiWidth), y - PANEL_BOTTOM + 24f);
            view.shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
            view.batch.begin();
            view.font.setColor(Color.GOLD);
            view.font.draw(view.batch, (armed ? "> " + tools.tool.title + tools.setting() : "Tools off")
                    + "   " + scene.name(scene.hull) + (scene.pinned ? "" : ", drifting"), x, y);
            view.font.setColor(Color.WHITE);
            view.font.draw(view.batch, message, x, y - line);
            view.font.setColor(Color.LIGHT_GRAY);
            view.font.draw(view.batch, hover(view), x, y - 2 * line);
            float ly = y - 3.5f * line;
            for (int i = Math.max(0, lines.size() - ACCOUNT_LINES); i < lines.size(); i++) {
                WoundWatch.Line l = lines.get(i);
                view.font.setColor(Color.WHITE);
                view.font.draw(view.batch, l.toString(), x, ly);
                ly -= line;
            }
            view.batch.end();
        }

        /** What is on the tile under the cursor, and who stands there. */
        private String hover(OverlayView view) {
            if (!view.deckView || body() == null) return "";
            ShipGridComponent g = scene.ship().getComponent(ShipGridComponent.class);
            float wx = view.worldBounds.x + Gdx.input.getX() / (float) Gdx.graphics.getWidth() * view.worldBounds.width;
            float wy = view.worldBounds.y + (1f - Gdx.input.getY() / (float) Gdx.graphics.getHeight()) * view.worldBounds.height;
            Vector2 local = WorldDebug.toLocal(body(), wx, wy, tmp);
            float tx = (local.x - g.gridOriginLocal.x) / g.tileSize, ty = (local.y - g.gridOriginLocal.y) / g.tileSize;
            int hx = (int) Math.floor(tx), hy = (int) Math.floor(ty);
            if (!g.inBounds(hx, hy)) return "";
            Tile t = g.getTile(hx, hy);
            if (t.isVoid()) return hx + "," + hy + "  space";
            StringBuilder b = new StringBuilder().append(hx).append(',').append(hy).append("  ")
                    .append(t.isWall() ? (t.isHullBreach() ? "hull breach" : "wall") : t.isFloor() ? "deck" : "open")
                    .append(String.format("  %.0f%%  %.0f K", t.getIntegrity() * 100f, t.temperature));
            if (t.holdsAir()) b.append(String.format("  O2 %.0f%%", g.oxygenRatioAt(hx, hy) * 100f));
            if (t.isBurning()) b.append("  BURNING");
            if (t.mount != null && t.mount.installed != null) {
                SubsystemComponent s = t.mount.installed.getComponent(SubsystemComponent.class);
                if (s != null) b.append(String.format("  %s %.0f%%%s", s.type, s.health * 100f, s.powered ? "" : " unpowered"));
            }
            Entity crew = scene.debug.crewAt(scene.ship(), tx, ty, 0.8f);
            if (crew != null) {
                CrewMemberComponent c = scene.world.crew.crew(crew);
                b.append("  ").append(c.name).append(", ").append(c.state == CrewState.DEAD ? "dead" : c.wound.label.toLowerCase());
                if (c.wound == Wound.CRITICAL && c.state != CrewState.DEAD)
                    b.append(c.stabilised ? ", stabilised" : String.format(", bleeds out %.0fs", c.bleedOut));
            }
            return b.toString();
        }
    }

    /** Rounds and missiles as streaks, rocks as discs, beams as red lines in, and a drag's line of aim. */
    private final class Flight implements DebugOverlay {
        @Override
        public void drawWorld(OverlayView view) {
            float px = view.metersPerPixel;
            Gdx.gl.glEnable(GL20.GL_BLEND);
            view.shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (Entity e : scene.world.engine.getEcsEngine().getEntitiesFor(PROJECTILES)) {
                ProjectileComponent p = e.getComponent(ProjectileComponent.class);
                boolean missile = e.getComponent(MissileComponent.class) != null;
                view.shapes.setColor(missile ? Color.ORANGE : Color.YELLOW);
                view.shapes.rectLine(p.pos.x, p.pos.y, p.pos.x - p.vel.x * 0.01f, p.pos.y - p.vel.y * 0.01f,
                        Math.max(missile ? 0.5f : 0.15f, 2f * px));
            }
            for (Entity e : scene.world.engine.getEcsEngine().getEntitiesFor(ROCKS)) {
                KinematicTransientComponent k = e.getComponent(KinematicTransientComponent.class);
                if (k.radius > 5f) continue;
                view.shapes.setColor(Color.LIGHT_GRAY);
                view.shapes.circle(k.x, k.y, Math.max(k.radius, 3f * px), 12);
            }
            for (Body rock : scene.debug.crashRocks()) {
                Vector2 at = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(rock, tmp);
                view.shapes.setColor(Color.LIGHT_GRAY);
                view.shapes.circle(at.x, at.y, Math.max(WorldDebug.RAM_RADIUS, 3f * px), 12);
            }
            for (WorldDebug.Beam beam : scene.debug.beams()) {
                Vector2 to = scene.debug.tileWorld(beam.ship, beam.tileX, beam.tileY, tmp);
                view.shapes.setColor(1f, 0.2f, 0.2f, 0.8f);
                view.shapes.rectLine(to.x, to.y, to.x - beam.dirX * 200f, to.y - beam.dirY * 200f, Math.max(0.2f, 2f * px));
            }
            if (pressedAt != null && body() != null) {
                Vector2 from = WorldDebug.toWorld(body(), pressedAt.x, pressedAt.y, tmp);
                float fx = from.x, fy = from.y;
                float wx = view.worldBounds.x + Gdx.input.getX() / (float) Gdx.graphics.getWidth() * view.worldBounds.width;
                float wy = view.worldBounds.y + (1f - Gdx.input.getY() / (float) Gdx.graphics.getHeight()) * view.worldBounds.height;
                view.shapes.setColor(1f, 1f, 1f, 0.5f);
                view.shapes.rectLine(fx, fy, wx, wy, 2f * px);
            }
            view.shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }
}
