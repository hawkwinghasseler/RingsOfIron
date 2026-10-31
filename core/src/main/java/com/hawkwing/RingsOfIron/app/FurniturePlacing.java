package com.hawkwing.RingsOfIron.app;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.FurnishingPlacement;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.ViewMode;

/**
 * Furnishings put down on the deck from the yard window: the piece in hand follows the pointer,
 * Q and E (or the wheel) turn it, a left click puts it down or picks one up, a right click or Esc lets
 * go, and X hands one back to the yard.
 */
final class FurniturePlacing {
    private final GameSession session;
    private final GameWorld world;
    private final DeckRenderer deckRenderer;
    /** Stands the player up from the console, so the deck shows. */
    private final Runnable standUp;
    /** Furniture being put down on the deck, or null. */
    private FurnishingPlacement placing;
    private final Vector2 tmp = new Vector2();

    FurniturePlacing(GameSession session, DeckRenderer deckRenderer, Runnable standUp) {
        this.session = session;
        this.world = session.world;
        this.deckRenderer = deckRenderer;
        this.standUp = standUp;
    }

    boolean isPlacing() {
        return placing != null;
    }

    /** What the keys do while placing, for the hint line. */
    String hint() {
        return placing.hint();
    }

    /** Starts placing a new {@code furnishingId} on the player's deck, or with null arranging what is aboard. */
    void start(String furnishingId) {
        Entity station = world.docking.dockedTo(session.game.player);
        placing = furnishingId == null ? FurnishingPlacement.arrange(world, session.game.player, station)
                : FurnishingPlacement.buy(world, session.game.player, station, world.furnishings.catalog().get(furnishingId));
        // It goes down on the deck: stand up from the console to see it
        if (session.hud.getViewMode().getMode() == ViewMode.EXTERIOR) standUp.run();
    }

    private void stop() {
        placing = null;
        deckRenderer.setGhost(null, null, false);
    }

    /** Esc or a right click while placing: a step back, and done once nothing is in hand. False when not placing. */
    boolean back() {
        if (placing == null) return false;
        placing.back();
        if (placing.isDone()) stop();
        return true;
    }

    /** Each frame: the piece in hand follows the pointer over the deck, or placing ends once the ship leaves the yard. */
    void update() {
        if (placing == null) return;
        if (placing.isDone() || !placing.stillDocked() || session.hud.getViewMode().getMode() != ViewMode.INTERIOR) {
            stop();
            return;
        }
        ShipGridComponent grid = placing.ship().getComponent(ShipGridComponent.class);
        Body body = world.docking.bodyOf(placing.ship());
        if (grid == null || body == null) {
            stop();
            return;
        }
        Vector2 at = session.viewports.getWorldViewport().unproject(tmp.set(Gdx.input.getX(), Gdx.input.getY()));
        float[] p = FurnishingPlacement.deckPoint(grid, body, at.x, at.y);
        placing.point(p[0], p[1]);
        deckRenderer.setGhost(placing.ship(), placing.ghost(), placing.refusal() == null);
    }

    private float turnStep() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                ? FurnishingPlacement.FINE_STEP : FurnishingPlacement.TURN_STEP;
    }

    /** While placing: Q and E turn the piece, X hands one back to the yard. Ahead of the HUD, whose E uses consoles. */
    final InputProcessor keys = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (placing == null) return false;
            switch (keycode) {
                case Input.Keys.Q:
                    placing.turn(turnStep());
                    return true;
                case Input.Keys.E:
                    placing.turn(-turnStep());
                    return true;
                case Input.Keys.X:
                case Input.Keys.FORWARD_DEL:
                    String said = placing.sell();
                    if (said != null) session.show(said);
                    return true;
                default:
                    return false;
            }
        }
    };

    /** While placing, clicks on the deck (not on a window): left puts down or picks up, right lets go; the wheel turns. */
    final InputProcessor clicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (placing == null) return false;
            update();
            if (placing == null) return false;
            if (button == Input.Buttons.RIGHT) {
                back();
                return true;
            }
            if (button != Input.Buttons.LEFT) return false;
            String said = placing.click();
            if (said != null) session.show(said);
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (placing == null || amountY == 0f) return false;
            placing.turn(-Math.signum(amountY) * turnStep());
            return true;
        }
    };
}
