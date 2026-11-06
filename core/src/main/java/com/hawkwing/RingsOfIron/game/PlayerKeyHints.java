package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.uihud.providers.KeyHintProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ui-hud's {@link KeyHintProvider}: the {@link HintRegistry}'s hints for where the player is, with
 * their bindings, most useful first and each action once as the classic bottom line takes them, but
 * all of them, then help. While furniture is being placed, its one line instead.
 */
public class PlayerKeyHints implements KeyHintProvider {
    private final HintRegistry hints;
    private final Supplier<Set<HintRegistry.Context>> contexts;
    private final Function<Action, String> keys;
    private final Supplier<String> placing;
    private final List<Action> seen = new ArrayList<>();

    /**
     * @param contexts where the player is, as the bottom line works it out
     * @param keys     the key the player has on an action
     * @param placing  the placing hint while furniture is placed, else null
     */
    public PlayerKeyHints(HintRegistry hints, Supplier<Set<HintRegistry.Context>> contexts, Function<Action, String> keys,
                          Supplier<String> placing) {
        this.hints = hints;
        this.contexts = contexts;
        this.keys = keys;
        this.placing = placing;
    }

    @Override
    public void collect(KeyHintFrame out) {
        String place = placing == null ? null : placing.get();
        if (place != null) {
            out.placing = true;
            out.placingText = place;
            return;
        }
        seen.clear();
        for (HintRegistry.Hint h : hints.forContexts(contexts.get())) {
            if (h.actions.length > 0) {
                if (seen.contains(h.actions[0]) || h.actions[0] == Action.HELP) continue;
                for (Action a : h.actions) seen.add(a);
            }
            KeyHint k = out.add();
            k.key = h.key(keys);
            k.label = h.label;
            k.action = h.actions.length == 0 ? "" : h.actions[0].name();
        }
        KeyHint help = out.add();
        help.key = keys.apply(Action.HELP);
        help.label = "controls";
        help.action = Action.HELP.name();
    }

    /** The controls panel's sections, as the classic F1 panel lists them: every hint by where it applies, with the player's keys. */
    @Override
    public void collectAll(HelpFrame out) {
        Set<HintRegistry.Context> now = contexts.get();
        for (Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : hints.byContext().entrySet()) {
            HelpSection section = out.add(e.getKey().title);
            HintRegistry.Context c = e.getKey();
            section.here = c != HintRegistry.Context.ANY && now != null && now.contains(c);
            section.gamepad = c == HintRegistry.Context.GAMEPAD;
            for (HintRegistry.Hint h : e.getValue()) {
                section.add(h.key(keys), h.label, h.actions.length == 0 ? "" : h.actions[0].name());
            }
        }
    }
}
