package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.Input;
import com.hawkwing.RingsOfIron.game.HintRegistry.Context;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Key hints by context, drawn from the player's bindings. */
class HintRegistryTest {

    @Test
    void theLineFollowsRebinding() {
        KeyBindings keys = new KeyBindings();
        HintRegistry r = HintRegistry.withDefaults();
        String before = r.line(EnumSet.of(Context.HELM, Context.ANY), HintRegistry.keyNames(keys));
        assertTrue(before.startsWith("W/S thrust   A/D turn   Space guns   M map"), before);
        assertTrue(before.endsWith("F1 controls"), before);
        keys.bind(Action.THRUST, Input.Keys.I);
        String after = r.line(EnumSet.of(Context.HELM, Context.ANY), HintRegistry.keyNames(keys));
        assertTrue(after.startsWith("I/S thrust"), after);
    }

    @Test
    void docked_theStationsKeysComeFirst() {
        HintRegistry r = HintRegistry.withDefaults();
        String line = r.line(EnumSet.of(Context.HELM, Context.DOCKED, Context.ANY), HintRegistry.keyNames(new KeyBindings()));
        assertTrue(line.startsWith("J mission board   U undock   T market   Y yard"), line);
    }

    @Test
    void theLineShowsAFewHintsAndEachActionOnce() {
        HintRegistry r = HintRegistry.withDefaults();
        String line = r.line(EnumSet.of(Context.DOCKED, Context.LATCHED, Context.ANY), HintRegistry.keyNames(new KeyBindings()));
        assertEquals(HintRegistry.LINE_HINTS + 1, line.split(" {3}").length, line);
        assertEquals(line.indexOf("U "), line.lastIndexOf("U "), "undock and cast off are one key: " + line);
    }

    @Test
    void aHintCanShowOnlySometimes() {
        HintRegistry r = new HintRegistry();
        boolean[] on = {false};
        r.register(Context.HELM, "tow", 1, Action.CUT).onlyWhen(() -> on[0]);
        assertTrue(r.forContexts(EnumSet.of(Context.HELM)).isEmpty());
        on[0] = true;
        assertEquals(1, r.forContexts(EnumSet.of(Context.HELM)).size());
    }

    @Test
    void theControlsPanelHasEveryContextAndTheKeysTheOldHintsLeftOut() {
        HintRegistry r = HintRegistry.withDefaults();
        Map<Context, List<HintRegistry.Hint>> all = r.byContext();
        for (Context c : new Context[]{Context.DECK, Context.HELM, Context.WEAPONS, Context.DOCKED, Context.MAP, Context.ANY}) {
            assertTrue(all.containsKey(c), c.name());
        }
        String helm = String.valueOf(describe(all.get(Context.HELM)));
        assertTrue(helm.contains("M map") && helm.contains("X rotation assist") && helm.contains("Wheel zoom"), helm);
        String map = String.valueOf(describe(all.get(Context.MAP)));
        assertTrue(map.contains("N plan a burn") && map.contains("Up/Down prograde"), map);
    }

    @Test
    void everyActionHasAHint() {
        HintRegistry r = HintRegistry.withDefaults();
        Navigation.registerHints(r, () -> true);
        EnumSet<Action> named = EnumSet.noneOf(Action.class);
        for (List<HintRegistry.Hint> l : r.byContext().values()) {
            for (HintRegistry.Hint h : l) java.util.Collections.addAll(named, h.actions);
        }
        // F1 itself is named on the line's end
        named.add(Action.HELP);
        for (Action a : Action.values()) assertTrue(named.contains(a), a + " has no hint");
    }

    private static StringBuilder describe(List<HintRegistry.Hint> hints) {
        StringBuilder sb = new StringBuilder();
        for (HintRegistry.Hint h : hints) sb.append(h.describe(HintRegistry.keyNames(new KeyBindings()))).append('|');
        return sb;
    }
}
