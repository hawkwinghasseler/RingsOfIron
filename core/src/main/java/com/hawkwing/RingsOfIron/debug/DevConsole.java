package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.WorldDebug;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.console.ScriptConsole;
import com.ringsofiron.moddingdata.console.ScriptConsole.ConsoleException;
import com.ringsofiron.stationsdocking.StationComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The developer console for a running {@link GameWorld}: a {@link ScriptConsole} with the world's
 * commands registered, the selection that clicks make ({@link DebugPicker}), and the actions they
 * take ({@link DebugActions}). The game opens it with ~ (ui-hud's {@code DevConsoleWindow} draws
 * it); a demo built on a {@code GameWorld} can host it the same way.
 *
 * <p>Commands act on the selection unless given a target: click a crew member and type
 * {@code kill()}, or {@code wound(95)} twice to see a second hit on someone critical halve their
 * time. A ship, crew member or tile can be passed instead ({@code heal(player)}), and the
 * selection is {@code selected} in a line. Script files in {@code scripts/} at the top of the
 * project run by name; {@code scripts/autoexec.groovy} runs when the console is made.
 */
public final class DevConsole {
    /** What the game hosting the console lets it do to the game itself, rather than the world. */
    public interface Host {
        /** Stops the world, or starts it again; the game still draws and the console still works. */
        void setFrozen(boolean frozen);

        boolean isFrozen();

        /** Runs the world on by {@code seconds} while frozen. */
        void step(float seconds);
    }

    public final GameWorld world;
    /** The career being played, or null for a demo with only a world. */
    public final GameScenario game;
    public final ScriptConsole console;
    public final DebugPicker picker;
    public final DebugActions actions;
    /** What the screen hosting the console can draw over the game for a developer: the debug menu's check boxes. */
    public final DebugOverlays overlays = new DebugOverlays();
    /** The loaded scenario's buttons in the debug menu. */
    public final DebugMenuActions menuActions = new DebugMenuActions();
    private Host host;
    /** Loads a scenario into a new game (null: a new career); null when the screen can't. */
    private Consumer<DebugScenarios.Entry> scenarioLoader;
    /** Opens or closes the debug menu; null when the screen has none. */
    private Runnable menuToggle;
    /** What the last click found under the cursor, in the order clicks cycle through it. */
    private List<DebugTarget> lastUnder = List.of();

    public DevConsole(GameWorld world, GameScenario game) {
        this(world, game, null, null);
    }

    /**
     * @param scriptDir   where {@code run "name"} finds scripts; null for {@link #defaultScriptDir}
     * @param historyFile where the history is kept between runs, or null to keep none
     */
    public DevConsole(GameWorld world, GameScenario game, Path scriptDir, Path historyFile) {
        this(world, game, scriptDir, historyFile, null);
    }

    /** @param tools the world's {@link WorldDebug}, when a demo already has one; null makes one */
    public DevConsole(GameWorld world, GameScenario game, Path scriptDir, Path historyFile, WorldDebug tools) {
        this(world, game, scriptDir, historyFile, tools, null);
    }

    /**
     * @param reuse the console of an earlier world to carry on in, its scrollback, history and
     *              variables kept and its commands pointed at this world (a demo that starts a new
     *              world for each hull); null starts a new one
     */
    public DevConsole(GameWorld world, GameScenario game, Path scriptDir, Path historyFile, WorldDebug tools,
                      ScriptConsole reuse) {
        this.world = world;
        this.game = game;
        this.picker = new DebugPicker(world);
        this.actions = new DebugActions(world, tools != null ? tools : new WorldDebug(world));
        this.console = reuse != null ? reuse : new ScriptConsole(DevConsole.class.getClassLoader());
        console.addStarImports("com.badlogic.ashley.core", "com.hawkwing.RingsOfIron.game", "com.hawkwing.RingsOfIron.debug",
                "com.hawkwing.RingsOfIron.app",
                "com.ringsofiron.aicrew", "com.ringsofiron.interiorgrid");
        console.addImports("com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause");
        console.setFormatter(DevConsole::format);
        console.setScriptDir(scriptDir != null ? scriptDir : defaultScriptDir());
        if (reuse == null) console.setHistoryFile(historyFile);
        console.set("world", world, "the GameWorld: world.crew, world.engine, world.notices ...");
        console.set("game", game, "the GameScenario: game.player, game.contacts ...");
        console.set("actions", actions, "DebugActions, what the commands below call");
        console.set("picker", picker, "the DebugPicker that keeps the selection");
        console.live("selected", picker::selected, "what was last clicked: a DebugTarget (.entity, .ship, .tile, .system)");
        console.live("player", world::getPlayer, "the player's ship");
        console.live("avatar", () -> game == null ? null : game.avatar, "the player's own crew member");
        register();
        if (reuse != null) return;
        console.info("Developer console. Click to select (again to cycle what is under the cursor), Esc deselects,"
                + " help() lists the commands.");
        console.runIfPresent("autoexec");
    }

    /**
     * {@code scripts/} at the top of the project. The game runs from {@code assets/}, so that is
     * {@code ../scripts}; {@code -Dconsole.scripts=path} puts it elsewhere.
     */
    public static Path defaultScriptDir() {
        String property = System.getProperty("console.scripts");
        if (property != null && !property.isEmpty()) return Path.of(property).toAbsolutePath();
        Path here = Path.of("").toAbsolutePath();
        Path own = here.resolve("scripts");
        if (Files.isDirectory(own)) return own;
        Path parent = here.getParent();
        return parent != null ? parent.resolve("scripts") : own;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    /** True while the host has the world stopped ({@code pause()}). */
    public boolean isFrozen() {
        return host != null && host.isFrozen();
    }

    /** How {@code scenario("id")} loads one: the game screen starts a new game with it. */
    public void setScenarioLoader(Consumer<DebugScenarios.Entry> loader) {
        this.scenarioLoader = loader;
    }

    /** What {@code menu()} does: opens or closes the debug menu. */
    public void setMenuToggle(Runnable toggle) {
        this.menuToggle = toggle;
    }

    /**
     * Loads {@code entry} into a new game, or a new career for null. False when this screen can't load scenarios
     * (a demo hosting the console), and says so in the scrollback.
     */
    public boolean loadScenario(DebugScenarios.Entry entry) {
        if (scenarioLoader == null) {
            console.error("Scenarios load in the game (gradlew :lwjgl3:runGame), not here");
            return false;
        }
        scenarioLoader.accept(entry);
        return true;
    }

    /**
     * Lets {@code overlay(name)} and the debug menu show or hide something the hosting screen draws over the game
     * for a developer: {@code shown} says whether it is drawn, {@code show} turns it on or off.
     */
    public void addOverlay(String name, String description, BooleanSupplier shown, Consumer<Boolean> show) {
        overlays.add(name, description, shown, show);
    }

    /** Whether the overlay {@code name} is drawn; false when there is none by that name. */
    public boolean overlayShown(String name) {
        return overlays.isShown(name);
    }

    // --- Selecting -------------------------------------------------------------------

    /**
     * A click at world ({@code x}, {@code y}): selects what is there, cycling on a second click,
     * and says what in the scrollback. {@code decks} are the craft the deck view draws, or null.
     */
    public DebugTarget click(float x, float y, boolean deckView, float metersPerPixel, Collection<Entity> decks) {
        List<DebugTarget> under = picker.under(x, y, deckView, metersPerPixel, decks);
        lastUnder = under;
        DebugTarget t = picker.click(x, y, deckView, metersPerPixel, decks);
        if (t == null) {
            console.info("Nothing there");
        } else {
            int at = under.indexOf(t) + 1;
            console.info("Selected " + t + (under.size() > 1 ? "   [" + at + "/" + under.size() + ", click again for the next]" : ""));
        }
        return t;
    }

    /** Esc: lets go of the selection. False when there was none. */
    public boolean deselect() {
        if (picker.selected() == null) return false;
        picker.clear();
        console.info("Deselected");
        return true;
    }

    public DebugTarget selected() {
        return picker.selected();
    }

    // --- The commands ----------------------------------------------------------------

    private void register() {
        console.define("select", "select(thing)", "selects a crew member, ship or DebugTarget; select() deselects", args -> {
            if (args.length == 0 || args[0] == null) {
                picker.clear();
                return null;
            }
            picker.select(targetOf(args[0]));
            return picker.selected();
        });
        console.define("under", "under()", "what the last click could have selected (the cycle order)", args -> lastUnder);
        console.define("pause", "pause()", "stops the world (the console keeps working); pause() again starts it", args -> {
            Host h = host();
            h.setFrozen(!h.isFrozen());
            return h.isFrozen() ? "World stopped" : "World running";
        });
        console.define("step", "step([seconds])", "runs a stopped world on by seconds (one frame)", args -> {
            Host h = host();
            float s = args.length > 0 ? number(args[0]) : 1f / 60f;
            h.step(s);
            return "Ran " + s + " s";
        });
        console.define("overlay", "overlay([name], [on])", "shows or hides a developer overlay (F3 has them too);"
                + " overlay() lists them", args -> {
            if (args.length == 0 || args[0] == null) return overlayList();
            String name = String.valueOf(args[0]);
            DebugOverlays.Toggle o = overlays.get(name);
            if (o == null) {
                throw new ConsoleException(overlays.all().isEmpty() ? "No overlays here"
                        : "No overlay \"" + name + "\"; there is " + String.join(", ", overlays.ids()));
            }
            boolean on = args.length > 1 && args[1] != null ? Boolean.TRUE.equals(args[1]) || "on".equals(String.valueOf(args[1]))
                    : !o.isShown();
            o.setShown(on);
            return name + (o.isShown() ? " shown" : " hidden");
        });
        console.define("scenarios", "scenarios()", "the scenarios scenario() loads", args -> {
            StringBuilder out = new StringBuilder();
            for (DebugScenarios.Entry e : DebugScenarios.all()) {
                if (out.length() > 0) out.append('\n');
                out.append(e);
            }
            return out.length() == 0 ? "No scenarios" : out.toString();
        });
        console.define("scenario", "scenario(id)", "throws this game away and loads the scenario; scenario(\"career\")"
                + " starts a new career", args -> {
            if (args.length == 0 || args[0] == null) throw new ConsoleException("scenario(id): which? scenarios() lists them");
            String id = String.valueOf(args[0]);
            DebugScenarios.Entry e = DebugScenarios.find(id);
            if (e == null && !"career".equalsIgnoreCase(id)) {
                throw new ConsoleException("No scenario \"" + id + "\"; scenarios() lists them");
            }
            return loadScenario(e) ? "Loading " + (e == null ? "a new career" : e.name) : null;
        });
        console.define("action", "action([name])", "presses one of the scenario's debug menu buttons; action() lists them", args -> {
            if (args.length == 0 || args[0] == null) {
                if (menuActions.all().isEmpty()) return "No scenario buttons here";
                StringBuilder out = new StringBuilder();
                for (DebugMenuActions.Action a : menuActions.all()) {
                    if (out.length() > 0) out.append('\n');
                    out.append(a);
                }
                return out.toString();
            }
            DebugMenuActions.Action a = menuActions.find(String.valueOf(args[0]));
            if (a == null) throw new ConsoleException("No button \"" + args[0] + "\"; action() lists them");
            a.run.run();
            return null;
        });
        console.define("menu", "menu()", "opens or closes the debug menu (F3)", args -> {
            if (menuToggle == null) throw new ConsoleException("No debug menu here");
            menuToggle.run();
            return null;
        });
        console.define("notice", "notice(text)", "posts a notice, as the game does", args -> {
            world.notices.post(String.valueOf(args.length > 0 ? args[0] : ""));
            return null;
        });

        String crew = "Crew";
        console.define("kill", "kill([target])", "kills a crew member; on a ship, everyone aboard; removes a round or missile", crew, args -> {
            DebugTarget t = target(args, 0);
            if (t.isCrew()) return actions.kill(t.entity);
            if (t.isCraft()) return actions.killAll(t.entity);
            if (t.isTile()) throw new ConsoleException("kill() takes a crew member or ship; click again to cycle off the tile");
            return actions.remove(t.entity);
        });
        console.define("heal", "heal([target])", "heals a crew member fully; on a ship, everyone aboard", crew, args -> {
            DebugTarget t = target(args, 0);
            if (t.isCrew()) return actions.heal(t.entity);
            if (t.ship != null) return actions.healAll(t.ship);
            throw new ConsoleException("heal() takes a crew member or ship");
        });
        console.define("revive", "revive([target])", "brings a dead crew member back", crew, args ->
                actions.revive(crewTarget(args, 0)));
        console.define("wound", "wound(points, [cause], [target])", "a hit through the game's wounding (cause PENETRATOR;"
                + " SPALL, FRAGMENT, BLAST, BURN wound too); 200 kills", crew, args -> {
            float points = args.length > 0 ? number(args[0]) : 40f;
            String cause = args.length > 1 && args[1] != null && !(args[1] instanceof Entity) && !(args[1] instanceof DebugTarget)
                    ? String.valueOf(args[1]) : null;
            int targetAt = cause != null ? 2 : 1;
            return actions.wound(crewTarget(args, targetAt), points, cause);
        });
        console.define("hurt", "hurt(points, [target])", "takes health as fire does, without wounding", crew, args ->
                actions.wound(crewTarget(args, 1), args.length > 0 ? number(args[0]) : 20f, "FIRE"));
        console.define("ko", "ko([target])", "knocks a crew member out", crew, args -> actions.knockOut(crewTarget(args, 0)));
        console.define("suit", "suit([target])", "puts a crew member's space suit on, or takes it off", crew, args ->
                actions.suit(crewTarget(args, 0)));
        console.define("crew", "crew([target])", "the crew member's CrewMemberComponent, to read or change (crew().bleedOut = 10)", crew, args ->
                DebugActions.crewOf(crewTarget(args, 0)));
        console.define("aboard", "aboard([ship])", "everyone aboard the ship", crew, args -> {
            List<DebugTarget> out = new ArrayList<>();
            Entity ship = shipTarget(args, 0);
            for (Entity e : actions.crewAboard(ship)) out.add(DebugTarget.crew(e, ship));
            return out;
        });
        console.define("spawn", "spawn([role], [name])", "a new crew member on the selected tile, or anywhere free aboard"
                + " the selected ship; selects them. Roles: crewman, engineer, marine, medic", crew, args -> {
            String role = args.length > 0 ? String.valueOf(args[0]) : null;
            String name = args.length > 1 ? String.valueOf(args[1]) : null;
            DebugTarget at = picker.selected();
            Entity ship = at != null && at.ship != null ? at.ship : world.getPlayer();
            boolean onTile = at != null && at.isTile() && at.getTile() != null && at.getTile().isOpen();
            Entity e = actions.spawnCrew(ship, role, name, -1, onTile ? at.tileX : -1, onTile ? at.tileY : -1);
            picker.select(DebugTarget.crew(e, ship));
            return picker.selected();
        });
        console.define("boarder", "boarder([role], [name])", "spawn(), but hostile to the ship's crew (a marine unless"
                + " told otherwise); selects them", crew, args -> {
            String role = args.length > 0 ? String.valueOf(args[0]) : "marine";
            String name = args.length > 1 ? String.valueOf(args[1]) : null;
            DebugTarget at = picker.selected();
            Entity ship = at != null && at.ship != null ? at.ship : world.getPlayer();
            boolean onTile = at != null && at.isTile() && at.getTile() != null && at.getTile().isOpen();
            Entity e = actions.spawnCrew(ship, role, name, actions.boarderFaction(ship), onTile ? at.tileX : -1, onTile ? at.tileY : -1);
            picker.select(DebugTarget.crew(e, ship));
            return picker.selected();
        });
        console.define("remove", "remove([target])", "takes a crew member, round, missile or decoy out of the world", crew, args -> {
            DebugTarget t = target(args, 0);
            if (t.isCrew()) {
                if (t.equals(picker.selected())) picker.clear();
                return actions.removeCrew(t.entity);
            }
            if (t.isTile() || t.isCraft()) throw new ConsoleException("remove() takes a crew member or something in flight");
            if (t.equals(picker.selected())) picker.clear();
            return actions.remove(t.entity);
        });
        console.define("teleport", "teleport(x, y, [target])", "puts a crew member on another tile of the ship they are on", crew, args -> {
            if (args.length < 2) throw new ConsoleException("teleport(x, y): which tile?");
            Entity c = crewTarget(args, 2);
            return actions.teleport(c, c.getComponent(GridPositionComponent.class).ship, (int) number(args[0]), (int) number(args[1]));
        });

        String decks = "Decks";
        console.define("fire", "fire([target])", "sets the tile alight (a crew member's tile; on a ship, somewhere aboard)", decks, args -> {
            DebugTarget t = target(args, 0);
            if (t.isTile()) return actions.ignite(t.ship, t.tileX, t.tileY);
            if (t.isCrew()) {
                GridPositionComponent p = t.entity.getComponent(GridPositionComponent.class);
                return actions.ignite(p.ship, p.tileX(), p.tileY());
            }
            if (t.isCraft()) return actions.igniteSomewhere(t.entity);
            throw new ConsoleException("fire() takes a tile, crew member or ship");
        });
        console.define("extinguish", "extinguish([target])", "puts out the tile's fire; on a ship or crew member, every fire aboard", decks, args -> {
            DebugTarget t = target(args, 0);
            if (t.ship == null) throw new ConsoleException("extinguish() takes a tile, crew member or ship");
            return t.isTile() ? actions.extinguish(t.ship, t.tileX, t.tileY) : actions.extinguish(t.ship, -1, -1);
        });
        console.define("damage", "damage(amount, [target])", "a tile: takes amount (0.25 or 25%) of its condition and its system's;"
                + " a crew member: wound(amount)", decks, args -> {
            float amount = args.length > 0 ? number(args[0]) : 0.25f;
            DebugTarget t = target(args, 1);
            if (t.isCrew()) return actions.wound(t.entity, amount, null);
            if (t.isTile()) return actions.damageTile(t.ship, t.tileX, t.tileY, amount > 1f ? amount / 100f : amount);
            throw new ConsoleException("damage() takes a tile or crew member; click again to cycle to the tile");
        });
        console.define("breach", "breach([target])", "knocks the tile out: a hull breach if it faces space, else a hole", decks, args -> {
            int[] t = tileTarget(args, 0);
            return actions.breach(tileShip, t[0], t[1]);
        });
        console.define("repair", "repair([target])", "mends a tile and its system; a ship or crew member's ship, all of it", decks, args -> {
            DebugTarget t = target(args, 0);
            if (t.isTile()) return actions.repairTile(t.ship, t.tileX, t.tileY);
            if (t.ship != null) return actions.repairShip(t.ship);
            throw new ConsoleException("repair() takes a tile, crew member or ship");
        });
        console.define("air", "air([target])", "fills every room of the ship with fresh air and cools it", decks, args ->
                actions.freshAir(shipTarget(args, 0)));
        console.define("tile", "tile([target])", "the Tile, to read or change (tile().fuel = 5)", decks, args -> {
            Tile t = target(args, 0).getTile();
            if (t == null) throw new ConsoleException("Select a tile first");
            return t;
        });

        String hits = "Hits";
        console.define("shoot", "shoot([weapon], [target], [range], [bearing])", "fires a weapon once: a real round, missile"
                + " or beam. The target is a tile, a crew member's tile, or a whole ship (aimed at its middle). range: meters out"
                + " it starts (60 for a round, 600 for a missile). bearing: degrees off the target ship's bow, counterclockwise"
                + " (0 ahead, 90 port, 180 astern); a tile is shot from straight outboard, a ship from your ship's side."
                + " shoot 'autocannon_mk1', player, 2000, 90. weapons() lists them", hits, args -> {
            int i = 0;
            String weapon = args.length > i && args[i] instanceof CharSequence ? String.valueOf(args[i++]) : null;
            Object target = args.length > i && !(args[i] instanceof Number) ? args[i++] : null;
            float range = args.length > i && args[i] instanceof Number ? ((Number) args[i++]).floatValue() : Float.NaN;
            float bearing = args.length > i && args[i] instanceof Number ? ((Number) args[i++]).floatValue() : Float.NaN;
            DebugTarget t = target(target == null ? new Object[0] : new Object[]{target}, 0);
            int[] tile;
            Entity ship;
            if (t.isTile() || t.isCrew()) {
                tile = tileTarget(new Object[]{t}, 0);
                ship = tileShip;
            } else {
                ship = t.ship != null ? t.ship : t.entity;
                tile = ship == null ? null : actions.tools.centerTile(ship);
                if (tile == null) throw new ConsoleException("Select a ship, tile or crew member first (" + t + " is selected)");
                if (Float.isNaN(bearing)) bearing = bearingFromPlayer(ship);
            }
            return actions.shoot(ship, tile[0], tile[1], weapon, bearing, range);
        });
        console.define("blast", "blast([warhead], [target])", "sets off a warhead inside the hull at the tile. warheads() lists them", hits, args -> {
            String warhead = args.length > 0 && args[0] instanceof CharSequence ? String.valueOf(args[0]) : null;
            int[] t = tileTarget(args, warhead != null ? 1 : 0);
            return actions.blast(tileShip, t[0], t[1], warhead);
        });
        console.define("ram", "ram([km/s], [tonnes], [target])", "a rock at the tile from straight outboard (2 km/s, 2 t)."
                + " Under 1 km/s it crashes into the hull as ships do: nothing at 0.025 and under, damages from 0.05", hits, args -> {
            int numbers = 0;
            while (numbers < args.length && numbers < 2 && args[numbers] instanceof Number) numbers++;
            float speed = numbers > 0 ? number(args[0]) * 1000f : WorldDebug.RAM_SPEED;
            float mass = numbers > 1 ? number(args[1]) * 1000f : WorldDebug.RAM_MASS;
            int[] t = tileTarget(args, numbers);
            return actions.ram(tileShip, t[0], t[1], mass, speed);
        });
        console.define("shortOut", "shortOut([target])", "shorts the system or conduit on the tile; it may light a fire", hits, args -> {
            int[] t = tileTarget(args, 0);
            return actions.shortOut(tileShip, t[0], t[1]);
        });
        console.define("weapons", "weapons()", "the weapon ids shoot() takes", hits, args -> actions.tools.weapons());
        console.define("warheads", "warheads()", "the warhead ids blast() takes", hits, args -> actions.tools.warheads());
        console.define("reactors", "reactors([ship])", "switches the ship's reactors off, or back on", decks, args ->
                actions.tools.toggleReactors(shipTarget(args, 0)) ? "Reactors on" : "Reactors off");
        console.define("shields", "shields([on], [ship])", "raises the ship's shields (true), or lowers them safely into its"
                + " emitters (false); shields() says how its cloud stands", decks, args -> {
            Boolean on = args.length > 0 && args[0] instanceof Boolean ? (Boolean) args[0] : null;
            Entity ship = shipTarget(args, on == null ? 0 : 1);
            com.ringsofiron.combatsys.shields.ShieldCloudComponent c = on == null
                    ? com.ringsofiron.combatsys.shields.ShieldModel.ensure(ship)
                    : com.ringsofiron.combatsys.shields.ShieldModel.raise(ship, on);
            if (c == null) return "No shield emitters aboard";
            return String.format(java.util.Locale.ROOT, "Shields %s, trimmed %s: %.0f kg out, %.0f stowed, %.0f scattered, %.0f adrift, %.0f lost of %.0f",
                    c.raised ? "up" : "down", c.trim.label, c.cloud(), c.stowed, c.scattered, c.adrift, c.lost, c.capacity);
        });
        console.define("trim", "trim([side], [ship])", "leans the ship's resting shield cloud toward 'bow', 'port', 'stern' or"
                + " 'starboard', or 'even'; trim() says which way it leans", decks, args -> {
            com.ringsofiron.combatsys.shields.Trim side = null;
            if (args.length > 0 && args[0] instanceof String) {
                side = com.ringsofiron.combatsys.shields.Trim.named((String) args[0]);
                if (side == null) throw new ConsoleException("trim(side): 'even', 'bow', 'port', 'stern' or 'starboard'");
            }
            Entity ship = shipTarget(args, side == null ? 0 : 1);
            com.ringsofiron.combatsys.shields.ShieldCloudComponent c = side == null
                    ? com.ringsofiron.combatsys.shields.ShieldModel.ensure(ship)
                    : com.ringsofiron.combatsys.shields.ShieldModel.trim(ship, side);
            if (c == null) return "No shield emitters aboard";
            return "Shields trimmed " + c.trim.label;
        });
        console.define("doors", "doors([ship])", "seals every door aboard, or opens them all", decks, args ->
                actions.tools.toggleDoors(shipTarget(args, 0)) ? "Doors sealed" : "Doors open");

        String ships = "Ships";
        console.define("ships", "ships()", "the ship ids spawnShip() takes", ships, args ->
                world.getContent() == null ? List.of() : world.getContent().defs.ids("ship"));
        console.define("flyShip", "flyShip(id)", "swaps the player's ship for a new one from ships.toml where it is, free,"
                + " crew, cargo and credits aboard (flyShip 'gunship_heavy')", ships, args -> {
            if (args.length == 0) throw new ConsoleException("flyShip(id): which ship? ships() lists them");
            return world.shipyard.swap(String.valueOf(args[0]));
        });
        console.define("spawnShip", "spawnShip(id)", "a new ship from ships.toml ahead of the player, uncrewed; selects it", ships, args -> {
            if (args.length == 0) throw new ConsoleException("spawnShip(id): which ship? ships() lists them");
            Entity ship = actions.spawnShip(String.valueOf(args[0]), world.getPlayer());
            picker.select(DebugTarget.craft(ship, false));
            return picker.selected();
        });
    }

    /** Degrees off {@code ship}'s bow, counterclockwise, that the player's ship lies at; dead ahead if it is the player's. */
    private float bearingFromPlayer(Entity ship) {
        Entity player = world.getPlayer();
        com.badlogic.gdx.physics.box2d.Body body = world.docking.bodyOf(ship);
        com.badlogic.gdx.physics.box2d.Body from = player == null || player == ship ? null : world.docking.bodyOf(player);
        if (body == null || from == null) return 0f;
        float dx = from.getPosition().x - body.getPosition().x, dy = from.getPosition().y - body.getPosition().y;
        if (dx * dx + dy * dy < 1e-6f) return 0f;
        return (com.badlogic.gdx.math.MathUtils.atan2(dy, dx) - body.getAngle()) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
    }

    /** Each overlay, whether it is shown, and what it draws. */
    private String overlayList() {
        if (overlays.all().isEmpty()) return "No overlays here";
        StringBuilder out = new StringBuilder();
        for (DebugOverlays.Toggle t : overlays.all()) {
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        return out.toString();
    }

    private Host host() {
        if (host == null) throw new ConsoleException("This game can't be paused from the console");
        return host;
    }

    /** The target in {@code args[index]}, or the selection; refuses when there is neither. */
    private DebugTarget target(Object[] args, int index) {
        if (args.length > index && args[index] != null) return targetOf(args[index]);
        DebugTarget t = picker.selected();
        if (t == null) throw new ConsoleException("Nothing selected: click something with the console open, or pass a target");
        return t;
    }

    private Entity crewTarget(Object[] args, int index) {
        DebugTarget t = target(args, index);
        if (!t.isCrew()) throw new ConsoleException("Select a crew member first (" + t + " is selected)");
        return t.entity;
    }

    /** The ship of the last {@link #tileTarget}. */
    private Entity tileShip;

    /** The tile in {@code args[index]} or the selection: a tile as itself, a crew member as the tile they stand on. */
    private int[] tileTarget(Object[] args, int index) {
        DebugTarget t = target(args, index);
        if (t.isTile()) {
            tileShip = t.ship;
            return new int[]{t.tileX, t.tileY};
        }
        if (t.isCrew()) {
            GridPositionComponent p = t.entity.getComponent(GridPositionComponent.class);
            tileShip = p.ship;
            return new int[]{p.tileX(), p.tileY()};
        }
        throw new ConsoleException("Select a tile or crew member first (" + t + " is selected)");
    }

    private Entity shipTarget(Object[] args, int index) {
        DebugTarget t = args.length > index || picker.selected() != null ? target(args, index) : null;
        Entity ship = t != null ? t.ship : world.getPlayer();
        if (ship == null) throw new ConsoleException("Select a ship first");
        return ship;
    }

    /** A crew member, ship or station entity as a target; a target as itself. */
    DebugTarget targetOf(Object o) {
        if (o instanceof DebugTarget) return (DebugTarget) o;
        if (o instanceof Entity) {
            Entity e = (Entity) o;
            if (e.getComponent(CrewMemberComponent.class) != null) {
                GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                return DebugTarget.crew(e, p == null ? null : p.ship);
            }
            if (e.getComponent(StationComponent.class) != null) return DebugTarget.craft(e, true);
            if (e.getComponent(ShipGridComponent.class) != null) return DebugTarget.craft(e, false);
            if (e.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class) != null) {
                return DebugTarget.inFlight(DebugTarget.Kind.MISSILE, e);
            }
            if (e.getComponent(com.ringsofiron.combatsys.components.ProjectileComponent.class) != null) {
                return DebugTarget.inFlight(DebugTarget.Kind.PROJECTILE, e);
            }
        }
        throw new ConsoleException(nameOf(o) + " isn't something the console can act on");
    }

    private static float number(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        try {
            return Float.parseFloat(String.valueOf(o).replace("%", ""));
        } catch (NumberFormatException e) {
            throw new ConsoleException("Expected a number, not " + o);
        }
    }

    /** Results as the console writes them: entities and targets by name, everything else as Groovy would. */
    static String format(Object o) {
        if (o instanceof Entity) {
            Entity e = (Entity) o;
            if (e.getComponent(CrewMemberComponent.class) != null) {
                GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                return DebugNames.describe(DebugTarget.crew(e, p == null ? null : p.ship));
            }
            boolean station = e.getComponent(StationComponent.class) != null;
            if (station || e.getComponent(ShipGridComponent.class) != null) return DebugNames.describe(DebugTarget.craft(e, station));
            return DebugNames.name(e);
        }
        if (o instanceof CrewMemberComponent) {
            CrewMemberComponent c = (CrewMemberComponent) o;
            return c.name + ": " + c.role + ", " + c.state + ", " + Math.round(c.health) + " hp, wound " + c.wound
                    + (c.bleedOut > 0f ? ", bleeds out in " + Math.round(c.bleedOut) + " s" : "") + ", " + c.activity;
        }
        if (o instanceof DebugTarget || o instanceof Tile || o instanceof CharSequence) return String.valueOf(o);
        return org.codehaus.groovy.runtime.InvokerHelper.inspect(o);
    }

    /** A value's name for an error line. */
    private static String nameOf(Object o) {
        if (o == null) return "null";
        if (o instanceof Object[]) return Arrays.toString((Object[]) o);
        return o instanceof String ? "\"" + o + "\"" : o.getClass().getSimpleName() + " " + o;
    }
}
