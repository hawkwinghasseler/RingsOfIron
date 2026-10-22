# Developer Console

## Overview
A console over the game for poking at the world while it runs. **~** opens it in the game and in
the interior demo. Each line typed is Groovy. Commands act on whatever was last clicked, so the
usual use is: open the console, click a crew member, type `kill()`.

## Using it
- **~** opens and closes it. It is a window over the game in the same application window: drag
  it by its title bar and resize it from any edge or corner. The mouse wheel over it scrolls the
  scrollback.
- **Left click** on the game, with the console open, selects what is under the cursor. Clicking
  the same spot again selects the next thing under it, round and round. On deck the order is the
  crew member, then the tile they stand on (with the system mounted there), then the ship. Outside
  it is rounds, missiles and decoys, then ships and stations. The selection is ringed in magenta
  and named in the console's title bar.
- **Esc** lets go of the selection. With nothing selected, it closes the console.
- **Enter** runs the line. **Up / Down** go through the history, which is kept in
  `assets/saves/console-history.txt` between runs. **Tab** finishes a command, variable or member
  name (`selected.ti` gives `selected.tile`), and a second Tab lists everything that fits.
  **Page Up / Down** scroll.
- While the console is open its line has the keyboard: no key typed there flies the ship, fires,
  or opens a HUD window, and the player's key bindings don't apply to it. The world keeps running
  unless `pause()` stops it.

## Commands
`help()` lists them all and `help kill` explains one. Brackets are optional. With no target given,
a command acts on the selection; a crew member, ship or target can be passed instead
(`heal(player)`, `kill(aboard()[2])`).

| Group | Commands |
|---|---|
| General | `help`, `clear`, `history`, `vars`, `run(name, args...)`, `scripts`, `select(thing)`, `under`, `pause` (stop or start the world), `step(seconds)`, `notice(text)` |
| Crew | `kill` (on a ship: everyone aboard; in flight: removes it), `heal`, `revive`, `wound(points, [cause])` through the game's own wounding (a second serious hit on someone critical halves their clock, as in play), `hurt(points)` (health, as fire takes it), `ko`, `suit`, `crew()` (the `CrewMemberComponent` to read or set), `aboard`, `spawn([role], [name])`, `boarder([role])`, `remove`, `teleport(x, y)` |
| Decks | `fire`, `extinguish`, `damage(amount)`, `breach`, `repair`, `air`, `reactors`, `doors`, `tile()` |
| Hits | `shoot([weapon])` (a real round, missile or beam from straight outboard), `blast([warhead])` (inside the hull), `ram([km/s], [tonnes])` (under 1 km/s a Box2D rock that crashes as combat-sys 5.12 describes), `shortOut`, `weapons`, `warheads` |
| Ships | `ships`, `spawnShip(id)` |
| View | `overlay([name], [on])`: shows or hides a developer overlay; `overlay()` lists them |

### Overlays
Overlays are developer views drawn over the game, not part of its normal look. The screen hosting
the console lends them with `addOverlay(name, description, shown, show)`, so the game and every
demo toggle the same drawing the same way. There is one so far:

- **`heat`** (`render.HeatOverlay`, drawn by `DeckRenderer`): each tile's heat as a red wash from
  350 K to the ignition point, and walls marked as they are worn down. The game itself shows heat
  as light (tiles hot enough glow as black bodies, see `documentation/lighting/Design.md`); this
  shows it before it glows. Hidden in the game; shown in the interior demo, which has no light map.

A command typed on its own runs (`heal`), and Groovy's command syntax works (`wound 95, 'SPALL'`).
`selected`, `player` and `avatar` are read fresh each time a line uses them. `world` and `game` are
the `GameWorld` and `GameScenario`, so anything the game can reach, a line can too
(`world.crew.crewEntities().size()`). A variable set without `def` stays for later lines.

## Scripts
Every `.groovy` file in `scripts/` at the top of the project can be run from the console (see
`scripts/README.md`). A file in that folder itself runs by typing its name, with arguments after it
(`fire_drill 3`). One in a subfolder runs with `run 'sub/name'`. Scripts run in the console's
binding, take their arguments in `args`, and can add commands with
`console.define(name, usage, help, closure)`. `scripts/autoexec.groovy`, which is not checked in,
runs whenever a console is made. The game finds the folder as `../scripts` from `assets/`, and
`-Dconsole.scripts=<folder>` points it elsewhere.

## Implemented Classes
- **`ScriptConsole`** (modding-data, `moddingdata.console`): the console without any drawing. It
  holds the Groovy shell and its lasting binding, the commands (closures in the binding, with usage
  and help), live variables, the scrollback (2,000 lines), the history (300 lines), Tab completion,
  script files, and one-line errors that suggest the nearest command name.
- **`DevConsole`** (core, `debug`): a `ScriptConsole` for a `GameWorld`. It registers the commands
  above and turns clicks into selections. Through `Host`, the game lends it pause and step, and
  through `addOverlay` the overlays it can toggle.
  A demo that makes a new world for each hull passes the old `ScriptConsole` on, so the
  scrollback, history and variables carry over.
- **`DebugPicker`**: what is under a world point, in deck or outside view, and the click-to-cycle
  selection. `locate` says where a target is now, for drawing its ring.
- **`DebugTarget`**: a crew member, tile, ship, station, round, missile or decoy.
- **`DebugActions`**: the console's actions on targets. Most go through the interior demo's
  `WorldDebug`, so the demo's keys and the console's commands do the same thing. Killing, reviving,
  repairs and ships are its own.
- **`DebugNames`**: how the console names things.
- **`DevConsoleWindow`** (ui-hud, `widgets`): the scene2d window. The host decides which keys open
  it and what Esc does, because those keys have to reach it before the stage does.

## The debug menu (F3)
A window by the right edge of the game, beside the console and working through the same
`DevConsole`, so anything it does the console can do by name.
- **Scenarios**: each loads into a new game. The game running is thrown away (the shell starts a new
  `RingsOfIronGame`), `GameScenario.start` builds the world as for a new career, and the scenario's
  `setUp` runs instead of `beginCareer`. There is no walkthrough and no autosaving in a scenario.
  "New career" goes back to a normal game. In the console: `scenarios()`, `scenario("id")`,
  `scenario("career")`. At launch: `gradlew :lwjgl3:runGame -Pscenario=<id>`.
- **Overlays**: a check box each, the same toggles as `overlay("id")`. Built in (`BuiltInOverlays`):
  `grid` (the physics bubble's frame in meters, lines every 10^n m), `velocity` (every moving body's
  velocity as an arrow, 5 s long), `bodies` (Box2D's debug renderer) and `stats` (frame rate,
  entities, bodies, clock, bubble origin, the player's local motion).
- **This scenario**: the loaded scenario's own buttons (`action("name")` in the console).
- **World**: stop, and step 1/60 s or 1 s, as `pause()` and `step()`.

### Writing a scenario
Implement `DebugScenario` and add one line to `DebugScenarios.BUILT_IN`:

```java
new Entry("flight", "Flight", "The corvette adrift among asteroids", FlightScenario::new),
```

A new instance is made for each load. `setUp(ScenarioSetup)` gets the world, the career
(`game.player`, `game.avatar` ...), the console and `tools()` (the `WorldDebug`), and adds to the menu:
`action(name, description, Runnable)` for a button, `overlay(id, description, DebugOverlay, on)` for
a check box (`drawWorld` in meters under the world camera, `drawScreen` in UI pixels), and
`console().define(...)` for a command. `deckView()` / `exteriorView()` pick the starting view,
`pointer(ScenarioPointer)` takes clicks on the game while the console is closed (world meters), and
`playerShipChanged()` re-arms the guns and the view after giving the player another ship. `update`
runs after each frame of the world; `dispose` when the game is thrown away. `FreeFlightScenario` is
the smallest one.

Box2D bodies are in units of `PhysicsManager.METERS_PER_UNIT` (10 m), the cameras in meters: an
overlay reading a body directly scales by it.

## Testing
- `ScriptConsoleTest` (modding-data) covers commands with and without brackets, variables, print
  output, errors, history and its file, completion, script files run by name with arguments,
  help, and the scrollback limit.
- `DevConsoleTest` (core) runs on a real career. It covers click cycling crew to tile to ship, Esc,
  outside picks, kill/heal/revive, a second hit halving a critical clock, fires, breaches and
  repairs, spawns and boarders, weapons, blasts and rams, and the checked-in scripts.
- `tools/playtest/dev-console.sh` plays it in a real window under Xvfb, taking a screenshot at each
  step.
- `DebugMenuTest` (core) covers the scenario list, loading from the console, overlay toggles drawn
  here or lent by the screen, and a scenario's buttons, overlays and commands.
- `tools/playtest/debug-menu.sh` opens the menu in a real window, turns each overlay on, loads a
  scenario from the console and goes back to a career from the menu's button.
