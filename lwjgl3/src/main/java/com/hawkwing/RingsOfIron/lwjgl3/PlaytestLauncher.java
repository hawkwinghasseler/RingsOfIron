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
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.ringsofiron.stationsdocking.DockingEvent;
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
 *   <li>{@code hull <x>}: wears every hull wall down to {@code x}, so the ship is beaten on its hull
 *       with its reactor and guns still up</li>
 *   <li>{@code latch}: latches the ship onto the raider's port</li>
 *   <li>{@code dock}: docks at Highport</li>
 *   <li>{@code strand}: empties the propellant tanks, so the ship can't fly home and the tug can be called</li>
 *   <li>{@code load <name>}: loads the save named {@code <name>}, as its Load button would, where
 *       finding the button in a list that autosaves keep adding to isn't repeatable</li>
 *   <li>{@code helm}: puts the captain at the helm for V to sit them, where walking there from wherever the save
 *       left them on deck isn't repeatable</li>
 *   <li>{@code docked}: says whether the ship is docked, so a script can wait for the helmsman
 *       to finish an approach without a guessed pause</li>
 *   <li>{@code target <name>}: makes the first station or craft whose name holds {@code <name>} the
 *       navigation target, where how many Q presses it takes changes from game to game</li>
 *   <li>{@code raid}: only reads how the raid stands (the base, the hull, the gun's and the navigation
 *       target, the helm's order, and each defender's range), so a script can fly it by keys</li>
 *   <li>{@code seat}: only reads the console the avatar sits at and where the weapons console is from it</li>
 *   <li>{@code boarding}: only reads who is boarding the player, how close, and what the last boarder took</li>
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
        // An 8-bit stencil buffer: the hulls' roof shadows are cut out with it (HullShading)
        config.setBackBufferConfig(8, 8, 8, 8, 16, 8, 0);
        config.disableAudio(true);
        new Lwjgl3Application(new Driven(shell, commands, saves), config);
    }

    private static final class Driven implements ApplicationListener {
        private final GameShell shell;
        private final Path commands;
        private final Path saves;
        private int done;
        /** The world the boarding watch listens to, and what its boarders last took. */
        private Object boardingWatched;
        private String robbed;

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
            watchDocking();
            shell.render();
        }

        private GameWorld watched;
        /** The player's rounds fired since the load, by weapon. */
        private final java.util.Map<String, Integer> playerShots = new java.util.TreeMap<>();

        /** Logs every docking event, and at a tear whatever the two craft are touching. */
        private void watchDocking() {
            RingsOfIronGame game = shell.getGame();
            GameWorld world = game == null ? null : game.getWorld();
            if (world == null || world == watched) return;
            watched = world;
            // Round 10: the player's rounds fired, by weapon, for the `shots` command
            playerShots.clear();
            world.combatEvents.shots.add((sig, shot) -> {
                if (shot.shooter == game.getScenario().player) playerShots.merge(shot.weaponDefId, 1, Integer::sum);
            });
            world.combatEvents.impacts.add((sig, e) -> System.out.println("PLAYTEST impact on " + label(e.target) + " from " + label(e.owner)
                    + (e.ram ? " (ram)" : "") + " mass " + e.mass + " at " + world.engine.getSimSeconds()));
            world.docking.events.add((sig, e) -> {
                System.out.println("PLAYTEST docking " + e.kind + " " + PlayerDocking.name(e.vessel) + " / " + PlayerDocking.name(e.other)
                        + " at " + world.engine.getSimSeconds());
                if (e.kind != DockingEvent.Kind.TORN) return;
                for (com.badlogic.gdx.physics.box2d.Contact c : world.physics.getWorld().getContactList()) {
                    if (!c.isTouching() || c.getFixtureA().isSensor() || c.getFixtureB().isSensor()) continue;
                    Object a = c.getFixtureA().getBody().getUserData(), b = c.getFixtureB().getBody().getUserData();
                    if (a != e.vessel && b != e.vessel && a != e.other && b != e.other) continue;
                    System.out.println("PLAYTEST   touching " + label(a) + " / " + label(b));
                }
            });
        }

        /** How many of a ship's systems are shorted out, 0 for none or no ship. */
        private static int shorted(Entity ship) {
            ShipSubsystemRegistryComponent r = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
            if (r == null) return 0;
            int n = 0;
            for (Entity e : r.subsystems) {
                SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
                if (sub != null && sub.shorted) n++;
            }
            return n;
        }

        private static String label(Object o) {
            return o instanceof Entity ? PlayerDocking.name((Entity) o) : String.valueOf(o);
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
                case "hull": {
                    // Every hull wall at this integrity: beaten on the hull with the reactor and guns
                    // still up, where losing a fight that way by hand isn't repeatable
                    ShipGridComponent grid = s.player.getComponent(ShipGridComponent.class);
                    float to = Float.parseFloat(cmd[1]);
                    for (int y = 0; y < grid.height; y++) {
                        for (int x = 0; x < grid.width; x++) {
                            com.ringsofiron.interiorgrid.Tile t = grid.getTile(x, y);
                            if (t != null && t.wallType == com.ringsofiron.interiorgrid.Tile.WallType.HULL) t.setIntegrity(Math.min(t.getIntegrity(), to));
                        }
                    }
                    grid.gridDirty = true;
                    return String.format(java.util.Locale.ROOT, "hull %.2f", com.hawkwing.RingsOfIron.game.ShipDisablement.hullIntegrity(grid));
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
                case "hostiles": {
                    // Every hostile craft: where it is and what it's doing, for a script's log
                    StringBuilder out = new StringBuilder();
                    Body ship = s.player.getComponent(PhysicsBodyComponent.class).body;
                    for (Entity e : world.engine.getEcsEngine().getEntitiesFor(com.badlogic.ashley.core.Family.all(
                            com.ringsofiron.macrosim.hostile.HostileComponent.class).get())) {
                        com.ringsofiron.macrosim.hostile.HostileComponent h = e.getComponent(com.ringsofiron.macrosim.hostile.HostileComponent.class);
                        PhysicsBodyComponent pb = e.getComponent(PhysicsBodyComponent.class);
                        String range = pb == null || pb.body == null || ship == null ? "rails"
                                : String.format(java.util.Locale.ROOT, "%.0f m", PhysicsManager.getPosition(pb.body, new Vector2())
                                        .dst(PhysicsManager.getPosition(ship, new Vector2())));
                        out.append(String.format("%n  %s %s target=%s %s %s", h.displayName, h.state, h.targetUuid,
                                com.hawkwing.RingsOfIron.game.HostileLabel.of(world, e), range));
                    }
                    return out.toString();
                }
                case "ports": {
                    // The ports of the station the ship is at, and what is at each, for a script's log
                    Entity at = world.docking.dockedTo(s.player);
                    if (at == null) return "not docked";
                    StringBuilder out = new StringBuilder(PlayerDocking.name(at));
                    com.ringsofiron.stationsdocking.DockingPortsComponent ports = at.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class);
                    for (int i = 0; i < ports.ports.length; i++) {
                        com.ringsofiron.stationsdocking.DockingPort p = ports.ports[i];
                        Entity other = p.partnerUuid == null ? null : world.engine.getEntity(p.partnerUuid);
                        out.append(String.format(java.util.Locale.ROOT, "%n  #%d %s at (%.1f, %.1f) facing (%.0f, %.0f) %s %s", i, p.size,
                                p.edgeX, p.edgeY, p.normalX, p.normalY, p.state, other == null ? "" : PlayerDocking.name(other)));
                    }
                    return out.toString();
                }
                case "raid": {
                    // How the raid stands, for a script to fight it to its end: one line, then a
                    // line per defender
                    Entity target = game.getGun() == null ? null : game.getGun().getTarget();
                    StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT,
                            "base %s, hull %.2f%s, target %s", world.pirateBase.standing() ? "standing" : "fallen",
                            com.hawkwing.RingsOfIron.game.ShipDisablement.hullIntegrity(s.player.getComponent(ShipGridComponent.class)),
                            com.hawkwing.RingsOfIron.game.ShipDisablement.isDisabled(s.player) ? ", we are beaten" : "",
                            target == null ? "none" : com.hawkwing.RingsOfIron.game.FirstPlay.beaten(world, target)
                                    ? "beaten" : PlayerDocking.name(target)));
                    // How far the base is, for the log
                    Entity base = world.pirateBase.uuid() == null ? null : world.engine.getEntity(world.pirateBase.uuid());
                    PhysicsBodyComponent bb = base == null ? null : base.getComponent(PhysicsBodyComponent.class);
                    Body ours = s.player.getComponent(PhysicsBodyComponent.class).body;
                    if (bb != null && bb.body != null && ours != null) out.append(String.format(java.util.Locale.ROOT, ", base at %.1f km",
                            PhysicsManager.getPosition(bb.body, new Vector2()).dst(PhysicsManager.getPosition(ours, new Vector2())) / 1000f));
                    out.append(", helm ").append(world.nav.helm.mode()).append(", nav ")
                            .append(world.nav.target.uuid() == null ? "none" : world.nav.target.name());
                    // Missiles aimed at us, and how many still steer (the rest coast as slugs)
                    int darts = 0, homing = 0;
                    for (Entity e : world.engine.getEcsEngine().getEntitiesFor(com.badlogic.ashley.core.Family.all(
                            com.ringsofiron.combatsys.components.MissileComponent.class).get())) {
                        com.ringsofiron.combatsys.components.MissileComponent m = e.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class);
                        if (m.target != s.player && m.seekerLock != s.player) continue;
                        darts++;
                        if (m.phase != com.ringsofiron.combatsys.components.MissileComponent.Phase.COAST) homing++;
                    }
                    out.append(", darts at us ").append(darts).append(" (").append(homing).append(" homing)");
                    out.append(String.format(java.util.Locale.ROOT, ", sim %.1f s", world.engine.getSimSeconds()));
                    // Each defender's range and whether it is beaten, so a script can Tab to the nearest
                    for (String name : world.pirateBase.defenders()) {
                        out.append("\n  defender ").append(name);
                        for (var h : world.macroSim.hostiles().all().entrySet()) {
                            if (!name.equals(h.getValue().displayName)) continue;
                            Entity e = world.engine.getEntity(h.getKey());
                            PhysicsBodyComponent pb = e == null ? null : e.getComponent(PhysicsBodyComponent.class);
                            if (pb != null && pb.body != null && ours != null) out.append(String.format(java.util.Locale.ROOT, " at %.1f km",
                                    PhysicsManager.getPosition(pb.body, new Vector2()).dst(PhysicsManager.getPosition(ours, new Vector2())) / 1000f));
                            if (e != null && com.hawkwing.RingsOfIron.game.FirstPlay.beaten(world, e)) out.append(" beaten");
                            // Its systems shorted out by our hits (round 9: do its crew still fire after one?)
                            int shorted = shorted(e);
                            if (shorted > 0) out.append(" shorted ").append(shorted);
                            if (e != null && e == target) out.append(" (target)");
                        }
                    }
                    return out.toString();
                }
                case "boarding": {
                    // Who is boarding the player and how close, and what the last boarder took
                    if (boardingWatched != world) {
                        boardingWatched = world;
                        robbed = null;
                        world.pirateBoarding.robbed.add((sig, t) -> robbed = t.pirateName + " took " + t.describe());
                    }
                    Entity boarder = world.pirateBoarding.boarder();
                    PhysicsBodyComponent pb = boarder == null ? null : boarder.getComponent(PhysicsBodyComponent.class);
                    Body ours = s.player.getComponent(PhysicsBodyComponent.class).body;
                    String at = pb == null || pb.body == null || ours == null ? "" : String.format(java.util.Locale.ROOT, " at %.0f m",
                            PhysicsManager.getPosition(pb.body, new Vector2()).dst(PhysicsManager.getPosition(ours, new Vector2())));
                    return (boarder == null ? "no boarder" : "boarder " + PlayerDocking.name(boarder) + at)
                            + (robbed == null ? "" : ", " + robbed);
                }
                case "crew": {
                    // Only reads: each of the player's crew aboard, living or dead, with their wound and suit
                    StringBuilder out = new StringBuilder();
                    com.badlogic.ashley.utils.ImmutableArray<Entity> all = world.crew.crewEntities();
                    for (int i = 0; i < all.size(); i++) {
                        Entity e = all.get(i);
                        com.ringsofiron.aicrew.CrewMemberComponent c = world.crew.crew(e);
                        if (c == null || world.crew.homeOf(e) != s.player) continue;
                        out.append(String.format(java.util.Locale.ROOT, "%n  %s: %s, %s", c.name,
                                com.hawkwing.RingsOfIron.game.CrewMedical.condition(c),
                                c.suited ? String.format(java.util.Locale.ROOT, "suited (%.0f s of air)", c.suitAir) : "unsuited"));
                    }
                    return out.length() == 0 ? "no crew" : out.toString();
                }
                case "shots":
                    // Only reads: the rounds the player's ship has fired since the load, by weapon
                    return playerShots.isEmpty() ? "none" : playerShots.toString();
                case "docked":
                    return world.docking.dockedTo(s.player) != null ? "yes" : "no";
                case "career":
                    // Only reads: the day, money, rank and ship, as the career script's snapshot gives them
                    return String.format(java.util.Locale.ROOT, "day %.2f, credits %.0f, rank %s, ship %s, hull %.2f",
                            world.engine.getSimSeconds() / 86_400,
                            s.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class).credits,
                            world.standing.describeRank(), PlayerDocking.name(s.player),
                            com.hawkwing.RingsOfIron.game.ShipDisablement.hullIntegrity(s.player.getComponent(ShipGridComponent.class)));
                case "captain": {
                    // Only reads: the captain's wound, suit and air, and which craft the figure stands in
                    Entity avatar = world.avatar.getAvatar();
                    com.ringsofiron.aicrew.CrewMemberComponent c = avatar == null ? null : world.crew.crew(avatar);
                    if (c == null) return "no captain";
                    com.ringsofiron.aicrew.GridPositionComponent pos = world.crew.position(avatar);
                    ShipGridComponent grid = pos == null ? null : world.crew.grid(pos.ship);
                    return String.format(java.util.Locale.ROOT, "%s, %s, %s, in %s (%s) at %s", com.hawkwing.RingsOfIron.game.CrewMedical.condition(c),
                            c.isAlive() ? (c.isConscious() ? "conscious" : "down") : "dead",
                            c.suited ? String.format(java.util.Locale.ROOT, "suited (%.0f s of air)", c.suitAir) : "unsuited",
                            pos == null ? "nowhere" : pos.ship == s.player ? "our ship" : PlayerDocking.name(pos.ship),
                            grid == null ? "?" : com.ringsofiron.aicrew.TaskRules.isBreathable(grid, pos.tileX(), pos.tileY()) ? "air" : "no air",
                            pos == null ? "-" : String.format(java.util.Locale.ROOT, "(%.1f, %.1f)", pos.x, pos.y));
                }
                case "raider": {
                    // A stand-in, like `hull`: one lone pirate raider brought in at rest <km> km out, hunting
                    // the player, armed with [fit] or as the difficulty picks (round 10: a lone raider v the frigate)
                    float km = cmd.length > 1 ? Float.parseFloat(cmd[1]) : 4f;
                    String fit = cmd.length > 2 ? cmd[2] : null;
                    List<String> ids = world.hostiles.spawnWing(com.hawkwing.RingsOfIron.game.HostileTraffic.RAIDER, 1, s.highport, fit);
                    if (ids.isEmpty()) return "no raider";
                    String uuid = ids.get(0);
                    com.ringsofiron.macrosim.hostile.HostileComponent h = world.macroSim.hostiles().get(uuid);
                    h.targetUuid = com.ringsofiron.macrosim.hostile.HostileComponent.PLAYER;
                    double now = world.macroSim.now();
                    com.ringsofiron.macrosim.rails.StateVector at = world.macroSim.gateway().anchorStateAt(now,
                            new com.ringsofiron.macrosim.rails.StateVector());
                    world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(),
                            at.set(at.x + km * 1000f, at.y, at.vx, at.vy), now);
                    return h.displayName + " (" + h.loadout + ") at " + km + " km";
                }
                case "seat": {
                    // Only reads: the console the avatar sits at, and the weapons console's offset from where
                    // it stands, in deck tiles, so a script can walk there by keys
                    Entity avatar = world.avatar.getAvatar();
                    com.ringsofiron.aicrew.GridPositionComponent pos = avatar == null ? null
                            : avatar.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class);
                    SubsystemComponent w = com.hawkwing.RingsOfIron.game.Bridge.console(s.player, SubsystemComponent.SubsystemType.WEAPONS);
                    if (pos == null || w == null) return "seat " + world.bridge.seat() + ", no avatar or no weapons console";
                    float cx = w.footprint.originX + w.footprint.width() / 2f, cy = w.footprint.originY + w.footprint.height() / 2f;
                    return String.format(java.util.Locale.ROOT, "seat %s, weapons %.2f %.2f off", world.bridge.seat(),
                            cx - pos.x, cy - pos.y);
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
