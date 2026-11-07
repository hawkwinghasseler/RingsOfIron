package com.hawkwing.RingsOfIron.app;

import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.providers.GameOverProvider;

/**
 * ui-hud's {@link GameOverProvider}: the ship lost or the career over, worded as the classic
 * ship-lost screen words it ({@code GameHudText}), and the ways on with the keys {@code GameInput}
 * takes there: the quick-load key, Enter for the latest autosave while there is one, Esc for the menu.
 */
final class GameOverChoices implements GameOverProvider {
    private final GameSession session;
    private final SaveController saves;

    GameOverChoices(GameSession session, SaveController saves) {
        this.session = session;
        this.saves = saves;
    }

    @Override
    public void read(GameOver out) {
        GameWorld world = session.world();
        GameWorld.Crash crash = world.getCrash();
        String over = world.getCareerOver();
        if (crash == null && over == null) return;
        out.shown = true;
        out.shipLost = crash != null;
        out.title = title(crash != null);
        out.line = line(crash, over);
        out.add(session.key(Action.QUICK_LOAD), Action.QUICK_LOAD.name(), "load the quick save", saves::quickLoad);
        if (saves.haveAutosave()) out.add("Enter", "", "load the latest autosave", saves::loadLatestAutosave);
        out.add("Esc", "", session.options.hosted ? "menu" : "quit", null);
    }

    /** "SHIP LOST" or "CAREER OVER". */
    static String title(boolean shipLost) {
        return shipLost ? "SHIP LOST" : "CAREER OVER";
    }

    /** What happened: "You crashed into Luna on day 12." or "You were killed, and ...". */
    static String line(GameWorld.Crash crash, String over) {
        return crash != null ? "You crashed into " + crash.bodyName + " on day " + (long) (crash.time / 86400) + "."
                : "You were killed, and " + over + ".";
    }
}
