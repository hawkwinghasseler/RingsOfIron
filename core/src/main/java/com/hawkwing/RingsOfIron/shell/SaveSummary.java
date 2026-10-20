package com.hawkwing.RingsOfIron.shell;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;

import java.util.Locale;

/**
 * What the load list shows about a save: its name, when it was made, the game time, the ship and
 * the credits. Written beside the save as {@code <save>.meta.json}, so the list never reads the
 * saves themselves and the save format (core-engine's) stays as it is.
 */
public class SaveSummary {
    public static final String META_SUFFIX = ".meta.json";

    public String name = "";
    /** Wall-clock time of the save, milliseconds since 1970. */
    public long savedAt;
    /** Game time, seconds since the game began. */
    public double gameSeconds;
    public String ship = "";
    public double credits;
    /** The save file this describes; not written. */
    public transient FileHandle file;

    /** A summary of the game {@code game} as it is now. */
    public static SaveSummary of(GameScenario game, String name) {
        SaveSummary s = new SaveSummary();
        s.name = name;
        s.savedAt = System.currentTimeMillis();
        s.gameSeconds = game.world.engine.getSimSeconds();
        Entity player = game.player;
        if (player != null) {
            s.ship = shipName(game, player.getComponent(DefRefComponent.class));
            TraderComponent trader = player.getComponent(TraderComponent.class);
            if (trader != null) s.credits = trader.credits;
        }
        return s;
    }

    private static String shipName(GameScenario game, DefRefComponent ref) {
        if (ref == null || ref.defId == null) return "";
        try {
            ShipDef def = game.content.defs.require(ref.defType, ref.defId, ShipDef.class);
            if (def.name != null && !def.name.isEmpty()) return def.name;
        } catch (RuntimeException e) {
            // A ship type the content no longer has: its id will do
        }
        return ref.defId;
    }

    public static FileHandle metaFor(FileHandle save) {
        return save.sibling(save.nameWithoutExtension() + META_SUFFIX);
    }

    /** Writes this summary beside {@code save}. */
    public void writeFor(FileHandle save) {
        Json json = new Json(JsonWriter.OutputType.json);
        metaFor(save).writeString(json.prettyPrint(this), false, "UTF-8");
    }

    /**
     * The summary beside {@code save}, or one made from the file alone (its name and date) when
     * there is none or it can't be read, as for saves from before summaries.
     */
    public static SaveSummary readFor(FileHandle save, String fallbackName) {
        SaveSummary s = null;
        FileHandle meta = metaFor(save);
        if (meta.exists()) {
            try {
                s = new Json().fromJson(SaveSummary.class, meta);
            } catch (RuntimeException e) {
                s = null;
            }
        }
        if (s == null) {
            s = new SaveSummary();
            s.name = fallbackName;
            s.savedAt = save.lastModified();
            s.gameSeconds = Double.NaN;
        }
        if (s.name == null || s.name.isEmpty()) s.name = fallbackName;
        s.file = save;
        return s;
    }

    /** "Day 3 14:05", or "" when the game time is unknown. */
    public String gameTime() {
        if (Double.isNaN(gameSeconds)) return "";
        long t = (long) gameSeconds;
        return String.format(Locale.ROOT, "Day %d %02d:%02d", t / 86400, (t / 3600) % 24, (t / 60) % 60);
    }

    /** The line under the save's name in the load list. */
    public String details() {
        StringBuilder b = new StringBuilder(gameTime());
        if (ship != null && !ship.isEmpty()) b.append(b.length() > 0 ? "   " : "").append(ship);
        if (!Double.isNaN(gameSeconds)) b.append(String.format(Locale.ROOT, "   %,.0f cr", credits));
        return b.toString();
    }
}
