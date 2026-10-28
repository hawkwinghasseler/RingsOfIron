# Rings of Iron

A top-down 2D space combat sim with Newtonian flight and orbital mechanics. You fly a ship at real scale around a planet and its moons, fight with railguns, lasers and guided missiles, and walk its decks while the crew fight fires, seal breaches and repair what the hits broke. Stations trade with each other through freighters flying real transfer orbits, and you can dock, trade and refit there.

Built in Java on [libGDX](https://libgdx.com/), with Box2D for physics inside a bubble around the player, analytic Kepler orbits ("rails") for everything outside it, and Ashley as the entity-component-system.

## Running it

You need a JDK 17 or newer to start the Gradle wrapper, which is included. The build itself runs on Java 21 and downloads it if your machine lacks it. On Windows, double-click a launcher; elsewhere, run the Gradle task beside it.

| Launcher | Gradle task | What it shows |
|---|---|---|
| `Play Rings of Iron.bat` | `./gradlew :lwjgl3:runGame` | The game, from its main menu: flight, combat, crew, docking, trading and time warp together |
| | `./gradlew :lwjgl3:runFirstPlay` | The guided first play, straight in with no menu (also on the main menu) |
| `demos/Flight demo.bat` | `./gradlew :lwjgl3:runGame -Pscenario=flight` | The game's Flight scenario (F3 debug menu): Newtonian flight among asteroids, the floating origin and fast passes |
| `demos/Orbit demo.bat` | `./gradlew :lwjgl3:runGame -Pscenario=orbit` | The game's Orbit scenario (F3 debug menu): rails orbits at real scale, sphere-of-influence changes and time warp |
| `demos/Interior demo.bat` | `./gradlew :lwjgl3:run` | Any of the game's hulls with its crew, in the game's own world: hurt crew and ship with weapon fire, rams, blasts, fire, breaches and an attacking ship, spawn more crew, and watch wounds, bleed-out clocks and medics at work |
| `demos/Crew demo.bat` | `./gradlew :lwjgl3:runCrewDemo` | AI crew fighting fires, repairing, rescuing and repelling boarders |
| `demos/Docking demo.bat` | `./gradlew :lwjgl3:runDockingDemo` | Docking at a station, walking across the dock and carrying cargo |
| `demos/Audio demo.bat` | `./gradlew :lwjgl3:runAudioDemo` | The game's audio scenario (F3 menu): the audio demo ship, where walls, doors and thin air muffle each sound |
| `demos/Ship editor.bat` | `./gradlew :lwjgl3:runShipEditor` | The ship editor, which edits the game's own layout, tile and component data |

Some modules also have headless demos that print to the console: `./gradlew :ai-crew:runCrewConsoleDemo`, `./gradlew :macro-sim:runMacroDemo` (add `-Pdays=60` for a longer run) and `./gradlew :modding-data:runModdingDemo`.

### Game controls

| Key | Action |
|---|---|
| W / S, A / D, Z / C | Thrust forward and back, turn, strafe (seated at the helm) |
| X | Toggle rotation assist |
| Space | Fire the guns now, at any range. Guns fire on ships attacking you by themselves, once they have a fair chance of a hit, while a crew member mans them or the weapons console; at the weapons console or a gun, the mouse aims and Space or a left click fires. For the guns in hand (or, flying with a target locked, every gun) the firing arc's edges are drawn, and two lines either side of the target where it could dodge to, colored by the chance to hit: green, yellow, red |
| R | Launch a salvo of missiles at the target (each launcher holds a few; a station restocks it while docked) |
| F | Drop a decoy (on deck: force the door across a dock) |
| 8 | Shield trim: lean the resting cloud toward the bow, port, stern or starboard, or even |
| 9 | Raise or lower the shields (lowered, the cloud packs safely into the emitters; docking lowers them on its own) |
| Tab | Cycle the target |
| V | Stand up from your console into the deck view; on deck beside the helm, the weapons console or a gun, sit there and look outside (`gradlew :lwjgl3:runGame -PviewAnywhere` switches anywhere) |
| M | Traffic map |
| T | Trade, when in range of a station |
| Y | Yard: repairs and refits, while docked |
| J | Mission board: the station's contracts while docked, and the ones you hold |
| K | Boarding: board the ship you are latched to, strip its parts and take its cargo |
| P | Abandon ship (P again within 3 s to confirm; P while abandoning launches the pods at once) |
| I | Crew roster: your crew, the station's hires and any prisoners |
| L | Notice log: the last 50 notices |
| O | Lighting, inside and out |
| B (twice) | Call a tow from your sponsor when disabled or low on propellant (at the helm) |
| F1 | Every control for where you are |
| G / H / U | Arm docking, pick the next port, undock |
| , / . | Time warp down and up |
| N, B, arrows, [ / ] | Plan a burn: add a node, select the next, change its Δv, move it in time |
| Q | Cycle the navigation target (or click a contact on the traffic map) |
| ; (semicolon), Enter / Backspace | Plan a transfer to the target; accept or discard the plan |
| / | Warp to the next burn, stopping a minute before it |
| 1 / 2 / 3 / 4 / 5 / 0 | Helm orders while you are away from the helm: fly the planned burns, match the target's velocity, hold position, approach the target (again to change the distance), dock at the target, cancel |
| 6 | Autopilot: fly the transfer to the navigation target under time warp and dock on arrival (again to go on after a hold, or to stop it) |
| F5 / F9 | Quick save and quick load |
| Esc | Pause menu: resume, save to a named slot, load, settings, quit to the main menu |
| ~ | Developer console: Groovy against the running world. Click to select a crew member, tile or ship (again to cycle what is under the cursor), then `kill()`, `heal()`, `wound 95`, `shoot 'railgun_mk1'` ...; Esc deselects. Every `.groovy` in `scripts/` runs by name. See `documentation/dev-console/Design.md` |

Keys can be rebound in Settings, which also has the volumes, HRTF, the window, the UI scale (auto follows the screen: 125% at 1440p, 200% at 4K) and the gamepad. A gamepad flies and walks with the left stick and fires with A or the right trigger; the full mapping is in `documentation/ui-hud/Design.md`. Saves and `settings.json` are kept in `assets/saves/`.

Each demo lists its own controls on screen.

### Trying a round's changes

The scripted career saves the game at the moments each round's changes show best. To play from them in your own game:

1. Write the saves (a few minutes): `./gradlew :core:slowTest --tests '*Round5CareerTest'`. They go to `core/build/playtest-saves/seed-2026`.
2. Run the game with them: `./gradlew :lwjgl3:runGame -PcareerSaves=core/build/playtest-saves/seed-2026`. The saves are copied into `assets/saves/`, so Load lists them beside your own, and they stay there for later runs. A relative folder is read from the repository root.
   - The copy lists what it put there in `assets/saves/career-saves.txt`. The next run with `-PcareerSaves` replaces those saves, and only those, so a new round's saves take the place of the last round's. The console says what it copied, replaced and kept.
   - A save you made is never touched, and neither is a career save you have saved over since (a save under the same name): it is yours now, and kept. To take the new copy instead, delete it from Load (or from `assets/saves/`) and run again.
   - Saves copied before round 9 aren't on the list, so the first run keeps them. Delete them from Load once, and later runs replace them for you.
3. Pick one from Load. Continue loads the newest save, which after a fresh copy is usually the career's last.

| Save | Where it starts | What it shows |
|---|---|---|
| First ace fight | Day 1, the corvette 3 km from the ace Red Kestrel, its bounty taken | The corvette's first ace fight: Tab, Space, R. Lost, the tug (B twice) brings you to Highport, and the crew panel (I) shows how the wounded fare at the berth |
| Before the Moon trip | Day 1, the corvette docked at Highport, the captain at the helm | Undocking (U) and the transfer autopilot (6) to the Moon; the cabin's air |
| Gunship | Day 26, the gunship just bought | The helm's dock order (5); the crew panel (I) |
| Gunship bounty fight | Day 30, the gunship in a bounty fight | A fight: Tab, Space, R; wounds on the crew panel (I) |
| Commander | Day 30, just promoted | The mission board (J) and what a Commander is offered |
| Frigate | Day 44, the frigate at Anvil Yards | The yard (Y) and refits |
| Before the raid | Day 44, the frigate 8 km from Rustwake, the pirates' base | The raid: Tab picks a defender, Space and R fight; the base strikes once its defenders are beaten |

Other seeds write the same saves: `./gradlew :core:test --tests '*CareerSweepTest' -PcareerSweep=<n>` puts seed n's in `core/build/playtest-saves/seed-<n>`, loaded with `-PcareerSaves=core/build/playtest-saves/seed-<n>`. Every seed's saves share names, so loading one seed's replaces the last seed's. Two are worth a look in round 10:
- **Seed 4:** "Before the raid" draws a railgun raider and a railgun battery, the raid that marks the frigate. The scripted career lost its first two tries there at about hull 0.45.
- **Seed 9:** the late frigate. On round 9 its "Frigate" save is on day 55, past the day-52 aim, after a lost fight with the ace Iron Dagny.

Each round's pull request names the save that shows its change, under "Try it in the game".

## Building and testing

```
./gradlew build        # compile everything and run the tests, except the slow ones
./gradlew test         # tests only, except the slow ones
./gradlew :combat-sys:test   # one module's tests
./gradlew slowTest     # only the slow tests: whole careers, long autopilot and warp runs
./gradlew test -PallTests    # every test, slow ones included
./gradlew :core:test --tests '*CareerSeedsTest' -PcareerSeeds=2026,7,12   # the scripted career on each seed, twice
./gradlew :core:playtestBenchmark   # the balance benchmarks, for a human tester (never part of the build)
```

CI runs only `./gradlew build test` and the smoke tests. A test there loads or builds the game state it needs and checks one thing in a few seconds; it never plays a long stretch of the sandbox to get somewhere, which is what human playtesting is for. A bug found in play gets a test of that one moment. On CI each test has 30 seconds (JUnit's default timeout, set when `CI` is in the environment); one that needs longer belongs with the slow tests.

Tests that play long stretches (a career played through, a replay played twice, a trip flown at every warp rate) are tagged `@Tag("slow")` and are tools for a person to run by hand, like the scripted career seeds (`-PcareerSeeds`): neither `build`, `test` nor CI runs them.

Balance targets (how long a fight lasts, what a prize pays, whether the career earns the gunship in play or wins the raid) are not checked by the build or CI, since they were tuned on the scripted career rather than on people. They are tagged `@Tag("playtest")` and run only with `./gradlew :core:playtestBenchmark` (or `tests/Balance benchmarks.bat`), which writes a table of each target against what the game gave to `core/build/playtest/`. `documentation/playtest/Balance benchmarks.md` has the checklist a tester works through when playing the same moments by hand.

The art in `assets/art` is SVG. The `art-pipeline` module rasterizes it into a texture atlas when a run task needs it, so nothing generated is committed. See `documentation/art/Design.md`.

## Layout

| Module | What it holds |
|---|---|
| `core-engine` | The ECS engine, the fixed 60 Hz sim clock, time warp, UUID references and save/load |
| `physics-exterior` | Box2D flight in the bubble, the floating origin and its free-falling orbital anchor, tidal gravity, fast passes |
| `orbital-mechanics` | Kepler orbits, sphere-of-influence changes, the orbital event queue and time warp control |
| `interior-grid` | Ship decks: tiles, walls, doors, air, heat, fire, life support and power |
| `ai-crew` | Crew pathfinding, behavior trees, health, interior physics, and NPC pilots |
| `combat-sys` | Sensors, fire control, ballistics, missiles and hull penetration into the deck |
| `macro-sim` | The economy, freighters on rails, and moving ships in and out of the bubble |
| `stations-docking` | Stations, docking ports and the walkable link between docked craft |
| `ui-hud` | The HUD, minimap, consoles, trade window and traffic map |
| `audio-spatial` | Positional sound with wall and door muffling |
| `modding-data` | TOML/JSON data loading, mods and Groovy mod scripts |
| `art-pipeline` | Rasterizes the SVG art into the game's texture atlas |
| `core` | The game itself, the demos and the ship editor, wiring the modules together |
| `lwjgl3` | Desktop launchers |

Game data lives in `assets/data` (ships, chassis layouts, tiles, components, weapons, economy). Each module's design is in `documentation/<module>/Design.md`, and `documentation/Technical Design Decisions.md` records the decisions that cut across modules. `documentation/progression/Design.md` covers factions, rank, contracts and rescue, which span several modules.
