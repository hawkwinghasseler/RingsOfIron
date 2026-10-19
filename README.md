# Rings of Iron

A top-down 2D space combat sim with Newtonian flight and orbital mechanics. You fly a ship at real scale around a planet and its moons, fight with railguns, lasers and guided missiles, and walk its decks while the crew fight fires, seal breaches and repair what the hits broke. Stations trade with each other through freighters flying real transfer orbits, and you can dock, trade and refit there.

Built in Java on [libGDX](https://libgdx.com/), with Box2D for physics inside a bubble around the player, analytic Kepler orbits ("rails") for everything outside it, and Ashley as the entity-component-system.

## Running it

You need a JDK 17 or newer to start the Gradle wrapper, which is included. The build itself runs on Java 21 and downloads it if your machine lacks it. On Windows, double-click a launcher; elsewhere, run the Gradle task beside it.

| Launcher | Gradle task | What it shows |
|---|---|---|
| `Play Rings of Iron.bat` | `./gradlew :lwjgl3:runGame` | The game, from its main menu: flight, combat, crew, docking, trading and time warp together |
| | `./gradlew :lwjgl3:runFirstPlay` | The guided first play, straight in with no menu (also on the main menu) |
| `demos/Flight demo.bat` | `./gradlew :lwjgl3:runFlightDemo` | Newtonian flight, the floating origin, orbit around a planet and fast passes |
| `demos/Orbit demo.bat` | `./gradlew :lwjgl3:runOrbitDemo` | Rails orbits, sphere-of-influence changes and time warp |
| `demos/Interior Grid Demo.bat` | `./gradlew :lwjgl3:run` | One ship's deck: air, fire, breaches, doors and power routing |
| `demos/Crew demo.bat` | `./gradlew :lwjgl3:runCrewDemo` | AI crew fighting fires, repairing, rescuing and repelling boarders |
| `demos/Docking demo.bat` | `./gradlew :lwjgl3:runDockingDemo` | Docking at a station, walking across the dock and carrying cargo |
| `demos/HUD demo.bat` | `./gradlew :lwjgl3:runHudDemo` | Target brackets, lead reticle, minimap, consoles and the traffic map |
| `demos/Audio demo.bat` | `./gradlew :lwjgl3:runAudioDemo` | Sound muffled by walls and doors, and interior versus exterior listening |
| `demos/Ship editor.bat` | `./gradlew :lwjgl3:runShipEditor` | The ship editor, which edits the game's own layout, tile and component data |

Some modules also have headless demos that print to the console: `./gradlew :ai-crew:runCrewConsoleDemo`, `./gradlew :macro-sim:runMacroDemo` (add `-Pdays=60` for a longer run) and `./gradlew :modding-data:runModdingDemo`.

### Game controls

| Key | Action |
|---|---|
| W / S, A / D, Z / C | Thrust forward and back, turn, strafe (seated at the helm) |
| X | Toggle rotation assist |
| Space | Fire guns |
| R | Launch a missile at the target |
| F | Drop a decoy (on deck: force the door across a dock) |
| Tab | Cycle the target |
| V | Stand up from your console into the deck view; on deck beside the helm or weapons console, sit there and look outside (`gradlew :lwjgl3:runGame -PviewAnywhere` switches anywhere) |
| M | Traffic map |
| T | Trade, when in range of a station |
| Y | Yard: repairs and refits, while docked |
| J | Mission board: the station's contracts while docked, and the ones you hold |
| K | Boarding: board the ship you are latched to, strip its parts and take its cargo |
| P | Abandon ship (P again within 3 s to confirm; P while abandoning launches the pods at once) |
| I | Crew roster: your crew, the station's hires and any prisoners |
| L | Notice log: the last 50 notices |
| O | Deck lights |
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

Keys can be rebound in Settings, which also has the volumes, HRTF, the window and the gamepad. A gamepad flies and walks with the left stick and fires with A or the right trigger; the full mapping is in `documentation/ui-hud/Design.md`. Saves and `settings.json` are kept in `assets/saves/`.

Each demo lists its own controls on screen.

## Building and testing

```
./gradlew build        # compile everything and run every test
./gradlew test         # tests only
./gradlew :combat-sys:test   # one module's tests
./gradlew :core:test --tests '*CareerSeedsTest' -PcareerSeeds=2026,7,12   # the scripted career on each seed, twice
```

CI plays the scripted career on seeds 2026, 7 and 12, each twice, in its "Career seed" jobs: both runs must write the same journal, and the career must reach the gunship unless `CareerSeedsTest.STOPS` lists the seed as stopping at a known place.

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
