package com.hawkwing.RingsOfIron.game;

import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Key hints by context, named by action rather than key, so they follow the player's bindings.
 * The bottom line of the screen shows the most useful few for where the player is
 * ({@link #line}); F1 shows them all ({@link #byContext}). Systems register their own hints here
 * instead of editing the hint text in the game.
 *
 * <p>A hint carries an {@link Action}, or a fixed key name for keys that are not rebindable
 * (Esc, the mouse wheel). Lower {@code priority} comes first.
 */
public final class HintRegistry {
    /** Where the player is. More than one can hold at once, such as the helm while docked. */
    public enum Context {
        /** Walking the decks. */
        DECK("On deck"),
        /** Seated at the helm, looking outside. */
        HELM("At the helm"),
        /** Seated at the weapons console or a gun, looking outside. */
        WEAPONS("At the weapons console"),
        /** Docked at a station. */
        DOCKED("Docked"),
        /** Latched to another ship. */
        LATCHED("Latched to a ship"),
        /** The player's ship is disabled. */
        DISABLED("Ship disabled"),
        /** The orbit map and burn planner, at the helm. */
        MAP("Orbit map and burn planner"),
        /** Anywhere. */
        ANY("Anywhere"),
        /** The gamepad's buttons, for the controls panel only. */
        GAMEPAD("Gamepad");

        public final String title;

        Context(String title) {
            this.title = title;
        }
    }

    public static final class Hint {
        public final Context context;
        /** The actions whose keys it names, joined with "/"; empty for a fixed key. */
        public final Action[] actions;
        /** The key's name when there is no action. */
        public final String fixedKey;
        public final String label;
        public final int priority;
        /** Shown only while this holds; null for always. */
        private BooleanSupplier when;

        Hint(Context context, Action[] actions, String fixedKey, String label, int priority) {
            this.context = context;
            this.actions = actions;
            this.fixedKey = fixedKey;
            this.label = label;
            this.priority = priority;
        }

        /** Shows the hint only while {@code when} holds. */
        public Hint onlyWhen(BooleanSupplier when) {
            this.when = when;
            return this;
        }

        public boolean shown() {
            return when == null || when.getAsBoolean();
        }

        /** "W/S thrust", with the keys the player bound. */
        public String describe(Function<Action, String> keyName) {
            return key(keyName) + " " + label;
        }

        public String key(Function<Action, String> keyName) {
            if (actions.length == 0) return fixedKey;
            StringBuilder sb = new StringBuilder();
            for (Action a : actions) {
                if (sb.length() > 0) sb.append('/');
                sb.append(keyName.apply(a));
            }
            return sb.toString();
        }
    }

    /** How many hints the bottom line shows, before "F1 controls". */
    public static final int LINE_HINTS = 5;

    private final List<Hint> hints = new ArrayList<>();

    /** A hint naming the keys of {@code actions}. */
    public Hint register(Context context, String label, int priority, Action... actions) {
        if (actions.length == 0) throw new IllegalArgumentException("a hint needs an action: " + label);
        return add(new Hint(context, actions, null, label, priority));
    }

    /** A hint on a key that is not rebindable, such as "Esc" or "Wheel". */
    public Hint registerKey(Context context, String key, String label, int priority) {
        return add(new Hint(context, new Action[0], key, label, priority));
    }

    private Hint add(Hint h) {
        hints.add(h);
        return h;
    }

    public void remove(Hint h) {
        hints.remove(h);
    }

    /** Every hint for {@code active} contexts that is shown now, most useful first. */
    public List<Hint> forContexts(Set<Context> active) {
        List<Hint> out = new ArrayList<>();
        for (Hint h : hints) if (active.contains(h.context) && h.shown()) out.add(h);
        out.sort(Comparator.comparingInt(h -> h.priority));
        return out;
    }

    /**
     * The bottom line: the {@link #LINE_HINTS} most useful hints for {@code active}, each action
     * once, then F1 for the rest.
     */
    public String line(Set<Context> active, Function<Action, String> keyName) {
        StringBuilder sb = new StringBuilder();
        List<Action> seen = new ArrayList<>();
        int n = 0;
        for (Hint h : forContexts(active)) {
            if (n == LINE_HINTS) break;
            if (h.actions.length > 0) {
                if (seen.contains(h.actions[0])) continue;
                Collections.addAll(seen, h.actions);
            }
            if (sb.length() > 0) sb.append("   ");
            sb.append(h.describe(keyName));
            n++;
        }
        if (sb.length() > 0) sb.append("   ");
        sb.append(keyName.apply(Action.HELP)).append(" controls");
        return sb.toString();
    }

    /** Every hint, shown or not, grouped by context in the context's order, for the controls panel. */
    public Map<Context, List<Hint>> byContext() {
        Map<Context, List<Hint>> out = new EnumMap<>(Context.class);
        for (Hint h : hints) out.computeIfAbsent(h.context, c -> new ArrayList<>()).add(h);
        for (List<Hint> l : out.values()) l.sort(Comparator.comparingInt(h -> h.priority));
        return Collections.unmodifiableMap(out);
    }

    /**
     * {@code text} with each {@code {ACTION}} (an {@link Action}'s name in braces) replaced by the
     * key the player has for it, as guide text that names keys is written.
     */
    public static String withKeys(String text, Function<Action, String> keyName) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            int close = open < 0 ? -1 : text.indexOf('}', open);
            if (close < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, open);
            String name = text.substring(open + 1, close);
            Action a = null;
            for (Action x : Action.values()) if (x.name().equals(name)) a = x;
            out.append(a == null ? text.substring(open, close + 1) : keyName.apply(a));
            i = close + 1;
        }
        return out.toString();
    }

    /** Key names from {@code bindings}. */
    public static Function<Action, String> keyNames(KeyBindings bindings) {
        return a -> KeyBindings.keyName(bindings.keyOf(a));
    }

    /** The game's own hints; the screen adds Esc, which opens a menu or quits depending on how it runs. */
    public static HintRegistry withDefaults() {
        HintRegistry r = new HintRegistry();
        // On deck
        r.register(Context.DECK, "walk", 10, Action.THRUST, Action.TURN_LEFT, Action.REVERSE, Action.TURN_RIGHT);
        r.register(Context.DECK, "use console", 20, Action.USE);
        r.register(Context.DECK, "sit at the console", 30, Action.VIEW);
        r.register(Context.DECK, "force a shut door", 60, Action.DECOY);
        r.register(Context.DECK, "cut a shut door", 61, Action.CUT);
        r.register(Context.DECK, "deck lights", 80, Action.LIGHTS);
        // At the helm
        r.register(Context.HELM, "thrust", 10, Action.THRUST, Action.REVERSE);
        r.register(Context.HELM, "turn", 11, Action.TURN_LEFT, Action.TURN_RIGHT);
        r.register(Context.HELM, "guns", 20, Action.FIRE);
        r.register(Context.HELM, "map", 25, Action.MAP);
        r.register(Context.HELM, "stand up", 40, Action.VIEW);
        r.register(Context.HELM, "strafe", 50, Action.STRAFE_LEFT, Action.STRAFE_RIGHT);
        r.register(Context.HELM, "rotation assist", 51, Action.ROTATION_ASSIST);
        r.register(Context.HELM, "missile", 52, Action.MISSILE);
        r.register(Context.HELM, "decoy", 53, Action.DECOY);
        r.register(Context.HELM, "next target", 54, Action.NEXT_TARGET);
        r.register(Context.HELM, "arm port, ask clearance", 55, Action.ARM_PORT);
        r.register(Context.HELM, "next port", 56, Action.NEXT_PORT);
        r.register(Context.HELM, "time warp down / up", 57, Action.WARP_DOWN, Action.WARP_UP);
        r.registerKey(Context.HELM, "Wheel", "zoom", 59);
        // At the weapons console
        r.registerKey(Context.WEAPONS, "Mouse", "aim", 9);
        r.register(Context.WEAPONS, "guns", 10, Action.FIRE);
        r.register(Context.WEAPONS, "missile", 11, Action.MISSILE);
        r.register(Context.WEAPONS, "next target", 12, Action.NEXT_TARGET);
        r.register(Context.WEAPONS, "decoy", 13, Action.DECOY);
        r.register(Context.WEAPONS, "stand up", 40, Action.VIEW);
        r.registerKey(Context.WEAPONS, "Wheel", "zoom", 59);
        // Docked: what the station offers comes first
        r.register(Context.DOCKED, "mission board", 1, Action.MISSIONS);
        r.register(Context.DOCKED, "undock", 2, Action.UNDOCK);
        r.register(Context.DOCKED, "market", 3, Action.TRADE);
        r.register(Context.DOCKED, "yard", 4, Action.YARD);
        r.register(Context.DOCKED, "crew, hire", 5, Action.CREW);
        // Latched to a ship
        r.register(Context.LATCHED, "boarding", 1, Action.BOARD);
        r.register(Context.LATCHED, "cast off", 2, Action.UNDOCK);
        // Disabled
        r.register(Context.DISABLED, "twice: abandon ship", 0, Action.ABANDON);
        // The orbit map and burn planner
        r.register(Context.MAP, "plan a burn", 70, Action.PLAN_BURN);
        r.register(Context.MAP, "next burn", 71, Action.CUT);
        r.register(Context.MAP, "prograde / retrograde", 72, Action.BURN_PROGRADE, Action.BURN_RETROGRADE);
        r.register(Context.MAP, "radial out / in", 73, Action.BURN_RADIAL_OUT, Action.BURN_RADIAL_IN);
        r.register(Context.MAP, "burn later / earlier (Shift x10)", 74, Action.BURN_LATER, Action.BURN_EARLIER);
        r.register(Context.MAP, "delete burn", 75, Action.BURN_DELETE);
        // Anywhere
        r.register(Context.ANY, "crew", 84, Action.CREW);
        r.register(Context.ANY, "missions held", 85, Action.MISSIONS);
        r.register(Context.ANY, "notice log", 86, Action.LOG);
        r.register(Context.ANY, "twice: abandon ship", 87, Action.ABANDON);
        r.register(Context.ANY, "quick save", 88, Action.QUICK_SAVE);
        r.register(Context.ANY, "quick load", 89, Action.QUICK_LOAD);
        // The pad (see GamepadKeys); never on the bottom line, which shows no GAMEPAD context
        r.registerKey(Context.GAMEPAD, "Left stick", "fly / walk", 1);
        r.registerKey(Context.GAMEPAD, "A / RT", "fire; A uses a console", 2);
        r.registerKey(Context.GAMEPAD, "B / X / Y", "missile / decoy, force door / view", 3);
        r.registerKey(Context.GAMEPAD, "LT", "next target", 4);
        r.registerKey(Context.GAMEPAD, "D-pad", "warp up/down, arm port, undock", 5);
        r.registerKey(Context.GAMEPAD, "Back", "tap: map; hold for the second layer", 6);
        r.registerKey(Context.GAMEPAD, "Back+A/B/X/Y", "missions / boarding / market / yard", 7);
        r.registerKey(Context.GAMEPAD, "Back+D-pad", "steer the planned burn", 8);
        r.registerKey(Context.GAMEPAD, "Back+LT/RT", "plan a burn / next burn", 9);
        r.registerKey(Context.GAMEPAD, "Back+Start", "abandon ship (twice)", 10);
        r.registerKey(Context.GAMEPAD, "Back+L3", "quick save", 11);
        r.registerKey(Context.GAMEPAD, "In menus", "D-pad moves, A picks, B backs out", 12);
        return r;
    }
}
