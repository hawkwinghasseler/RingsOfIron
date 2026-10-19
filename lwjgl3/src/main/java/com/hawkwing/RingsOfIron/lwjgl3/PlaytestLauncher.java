package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.shell.GameShell;
import com.hawkwing.RingsOfIron.shell.SaveSummary;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The game at its main menu in a real window, for playtests driven by injected key and mouse
 * events (xdotool under Xvfb; see {@code tools/playtest}). Keys and clicks play the game; a few
 * stand-ins for flying by hand come through a command file, one command a line, read each frame:
 * <ul>
 *   <li>{@code away <m>}: puts the ship that far out from Highport, moving with it</li>
 *   <li>{@code disable raider} / {@code disable player}: knocks out that ship's reactor</li>
 *   <li>{@code damage}: holes the first deck tile, as battle damage</li>
 *   <li>{@code latch}: latches the ship onto the raider's port</li>
 *   <li>{@code dock}: docks at Highport</li>
 *   <li>{@code strand}: empties the propellant tanks, so the ship can't fly home and the tug can be called</li>
 *   <li>{@code load <name>}: loads the save named {@code <name>}, as its Load button would, where
 *       finding the button in a list that autosaves keep adding to isn't repeatable</li>
 *   <li>{@code target <name>}: makes the first station or craft whose name holds {@code <name>} the
 *       navigation target, where how many Q presses it takes changes from game to game</li>
 * </ul>
 * Run with {@code gradlew :lwjgl3:runPlaytest -Pcommands=<file> [-Psaves=<folder>]}. Saves go to a
 * temporary folder, into which the saves in {@code <folder>} (the career test's
 * {@code core/build/playtest-saves/seed-2026}, say) are copied first so the load menu lists them.
 */
public class PlaytestLauncher {
    public static void main(String[] args) throws IOException {
        Path commands = Paths.get(args.length > 0 ? args[0] : "playtest-commands.txt").toAbsolutePath();
        Path saves = Files.createTempDirectory("ringsofiron-playtest-saves");
        if (args.length > 1 && !args[1].isEmpty()) {
            try (java.util.stream.Stream<Path> from = Files.list(Paths.get(args[1]).toAbsolutePath())) {
                for (Path f : (Iterable<Path>) from::iterator) {
                    if (f.toString().endsWith(".json")) Files.copy(f, saves.resolve(f.getFileName()));
                }
            }
        }
        GameShell shell = new GameShell(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath(),
                new FileHandle(saves.toFile()));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rings of Iron");
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowedMode(Integer.getInteger("playtest.width", 1280), Integer.getInteger("playtest.height", 800));
        config.disableAudio(true);
        new Lwjgl3Application(new Driven(shell, commands, saves), config);
    }

    private static final class Driven implements ApplicationListener {
        private final GameShell shell;
        private final Path commands;
        private final Path saves;
        private int done;

        Driven(GameShell shell, Path commands, Path saves) {
            this.shell = shell;
            this.commands = commands;
            this.saves = saves;
        }

        @Override public void create() { shell.create(); }
        @Override public void resize(int width, int height) { shell.resize(width, height); }
        @Override public void pause() { shell.pause(); }
        @Override public void resume() { shell.resume(); }
        @Override public void dispose() { shell.dispose(); }

        @Override
        public void render() {
            readCommands();
            shell.render();
        }

        private void readCommands() {
            if (!Files.exists(commands)) return;
            List<String> lines;
            try {
                lines = Files.readAllLines(commands);
            } catch (IOException e) {
                return;
            }
            while (done < lines.size()) {
                String line = lines.get(done++).trim();
                if (line.isEmpty()) continue;
                String said = run(line.split("\\s+"));
                System.out.println("PLAYTEST " + line + ": " + said);
            }
        }

        private String run(String[] cmd) {
            if ("load".equals(cmd[0])) {
                String name = String.join(" ", java.util.Arrays.copyOfRange(cmd, 1, cmd.length));
                FileHandle file = new FileHandle(saves.resolve(name.replaceAll("[^A-Za-z0-9_-]+", "_") + ".json").toFile());
                if (!file.exists()) return "no such save";
                shell.load(SaveSummary.readFor(file, name));
                return "loading";
            }
            RingsOfIronGame game = shell.getGame();
            if (game == null) return "no game";
            GameWorld world = game.getWorld();
            GameScenario s = game.getScenario();
            switch (cmd[0]) {
                case "away": {
                    Body station = world.docking.bodyOf(world.engine.getEntity(s.highport));
                    Vector2 at = PhysicsManager.getPosition(station, new Vector2());
                    Vector2 v = PhysicsManager.getVelocity(station, new Vector2());
                    Body ship = s.player.getComponent(PhysicsBodyComponent.class).body;
                    PhysicsManager.setPosition(ship, at.x, at.y + Float.parseFloat(cmd[1]), 0f);
                    PhysicsManager.setVelocity(ship, v.x, v.y);
                    return "moved";
                }
                case "disable": {
                    Entity ship = "player".equals(cmd[1]) ? s.player : s.raider;
                    for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
                        SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
                        if (sub.type == SubsystemComponent.SubsystemType.REACTOR) sub.health = 0f;
                    }
                    return "reactor out";
                }
                case "damage": {
                    ShipGridComponent grid = s.player.getComponent(ShipGridComponent.class);
                    grid.getTile(0, 0).setIntegrity(0.5f);
                    grid.gridDirty = true;
                    return "holed";
                }
                case "latch":
                    world.docking.dockInPlace(s.player, 0, s.raider, 0);
                    return "latched";
                case "dock":
                    return s.dockAtHighport() ? "docked" : "could not dock";
                case "strand": {
                    ShipSubsystemRegistryComponent r = s.player.getComponent(ShipSubsystemRegistryComponent.class);
                    for (SubsystemComponent t : TankSupply.tanks(r, Fluid.PROPELLANT)) t.contents = 0;
                    return "tanks empty";
                }
                case "target": {
                    String want = String.join(" ", java.util.Arrays.copyOfRange(cmd, 1, cmd.length)).toLowerCase(java.util.Locale.ROOT);
                    for (String uuid : world.nav.target.candidates()) {
                        String name = world.nav.target.name(uuid);
                        if (name.toLowerCase(java.util.Locale.ROOT).contains(want)) {
                            world.nav.target.select(uuid);
                            return "target " + name;
                        }
                    }
                    return "no such target";
                }
                default:
                    return "unknown command";
            }
        }
    }
}
