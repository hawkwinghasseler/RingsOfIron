package com.hawkwing.RingsOfIron.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.hawkwing.RingsOfIron.ShipEditor;
import com.hawkwing.RingsOfIron.game.ShipRefit;
import com.ringsofiron.moddingdata.defs.ChassisDef;

/**
 * The ship editor opened from the yard window on the player's layout, for the docked station's yard
 * to rebuild the decks to. The game waits while it is open: it has the screen and the keys, Ctrl+S
 * rebuilds and Esc comes back.
 */
final class YardRefit {
    private final GameSession session;
    /** Back in the game: everything sized to the window again. */
    private final Runnable resize;
    private ShipEditor editor;
    private boolean done;
    /** The game's input, given back when the editor closes. */
    private InputProcessor gameInput;

    YardRefit(GameSession session, Runnable resize) {
        this.session = session;
        this.resize = resize;
    }

    /** True while the ship editor is open for a yard rebuild; it has the keyboard then. */
    boolean isOpen() {
        return editor != null;
    }

    /** Opens the ship editor on the player's layout, for the docked station's yard to rebuild the decks to. */
    void open() {
        ChassisDef layout = ShipRefit.layoutOf(session.world, session.game.player);
        if (layout == null) {
            session.show("The yard has no plans of this ship");
            return;
        }
        gameInput = Gdx.input.getInputProcessor();
        done = false;
        editor = new ShipEditor(session.options.assetsDir, session.options.modsDir, new ShipEditor.Refit() {
            @Override
            public ChassisDef layout() {
                return layout;
            }

            @Override
            public String apply(ChassisDef rebuilt) {
                String why = ShipRefit.apply(session.world, session.game.player, rebuilt);
                if (why == null) session.show("The yard rebuilt the decks");
                return why;
            }

            @Override
            public String quote(ChassisDef rebuilt) {
                return ShipRefit.quote(session.world, session.game.player, rebuilt);
            }

            @Override
            public void close() {
                done = true;
            }
        });
        editor.create();
    }

    /**
     * The editor's frame while it is open, true when it drew it; once it has been closed, back to
     * the game, and false so the game draws.
     */
    boolean render() {
        if (editor != null && !done) {
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            editor.render();
            return true;
        }
        if (editor != null) close();
        return false;
    }

    /** Back from the ship editor to the game. */
    private void close() {
        editor.dispose();
        editor = null;
        Gdx.input.setInputProcessor(gameInput);
        resize.run();
    }

    void resize(int width, int height) {
        if (editor != null) editor.resize(width, height);
    }

    void dispose() {
        if (editor != null) editor.dispose();
    }
}
