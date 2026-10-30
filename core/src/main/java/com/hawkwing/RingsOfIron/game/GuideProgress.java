package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.SaveParticipant;

/**
 * How far the player has got through the guided parts of the game, saved with it: the scripted
 * {@link FirstPlay}'s objective, and the opening walkthrough's step. A save made in First Play
 * loads back into First Play at the same objective, from whichever menu it is loaded.
 */
public final class GuideProgress implements SaveParticipant, Resettable {
    /** {@link FirstPlay.Step}'s name, or null when this game is not a first play. */
    public String firstPlayStep;
    /** {@link OpeningGuide.Step}'s name, or null when the walkthrough is not running. */
    public String openingStep;

    @Override
    public void reset() {
        firstPlayStep = null;
        openingStep = null;
    }

    @Override
    public String saveKey() {
        return "game.guides";
    }

    @Override
    public void write(Json json) {
        if (firstPlayStep != null) json.writeValue("firstPlay", firstPlayStep);
        if (openingStep != null) json.writeValue("opening", openingStep);
    }

    @Override
    public void read(Json json, JsonValue data) {
        firstPlayStep = data.getString("firstPlay", null);
        openingStep = data.getString("opening", null);
    }
}
