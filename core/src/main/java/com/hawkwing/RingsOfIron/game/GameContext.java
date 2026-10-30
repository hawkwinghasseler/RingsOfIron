package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.moddingdata.ModContext;
import java.util.function.Supplier;

/**
 * What most of the game's managers read, and nothing else: the engine (its entities and sim clock),
 * the ship the player flies, the loaded content, and the notices and key hints that speak to the player. Pass this, and
 * the few services a class uses besides, instead of the whole {@link GameWorld}.
 */
public final class GameContext {
    public final GameEngine engine;
    /** What the game tells the player. */
    public final NoticeLog notices;
    /** Key hints by context, for the bottom line and the F1 controls panel. */
    public final HintRegistry hints;
    private final Supplier<Entity> player;
    private final Supplier<ModContext> content;

    public GameContext(GameEngine engine, Supplier<Entity> player, Supplier<ModContext> content, NoticeLog notices,
                       HintRegistry hints) {
        this.engine = engine;
        this.player = player;
        this.content = content;
        this.notices = notices;
        this.hints = hints;
    }

    /** The player's ship, or null. */
    public Entity player() {
        return player.get();
    }

    /** What {@link GameWorld#loadContent} loaded: defs, assets, entity factory; null before it ran. */
    public ModContext content() {
        return content.get();
    }
}
