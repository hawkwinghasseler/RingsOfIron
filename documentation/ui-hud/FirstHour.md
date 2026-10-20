# First Hour: Notices, Hints, Input and No Soft Locks

Round 3, category 1. This branch covers what a new player meets first. The game now tells the player things through a stacked notice log, names keys from the player's own bindings, can be driven from the arrow keys and a gamepad, saves on its own, and warns of danger. A stranded ship, a dead captain or a crash with the crew in the pods no longer ends the game for nothing.

## Implemented

### Notice log (`NoticeLog`, L)
- Everything goes through `GameWorld.notices`: `post(text)` or `post(text, kind)`. Kinds are `INFO` (held 5 s, orange), `IMPORTANT` (8 s, green) and `WARNING` (8 s, red).
- The newest 4 (`STACK`) are on screen, newest on top. The same text posted while still on screen is counted ("(x3)") and held again, not stacked.
- The log keeps the last 50 (`KEEP`), stamped with sim time. L opens it as a panel with "Day N hh:mm" per line; Up/Down, Page Up/Down or the wheel scroll it, and L or Esc closes it.
- `NoticeFeeds.collect` brings in the systems' own lists once a frame: door forcing, hostile acts (a warning when stations will refuse clearance), contracts (done, failed or called off are important; taking one is info), promotions (important), paydays, the yard, boarding, salvage and pirates ("...intercept you" is a warning).
- `NoticeAudio` plays `AudioEventCatalog.UI_NOTICE` for important notices and `UI_WARNING` for warnings, once per notice, through the new `AudioManager.playUi`.

### Hint registry and the F1 controls panel (`HintRegistry`)
- Hints are registered on `GameWorld.hints` by `Context` (`DECK`, `HELM`, `WEAPONS`, `DOCKED`, `LATCHED`, `DISABLED`, `MAP`, `ANY`, plus `GAMEPAD` for the panel only) with an `Action`, a label and a priority. `registerKey` takes a fixed key such as "Esc" or "Wheel". `onlyWhen` hides a hint unless a condition holds.
- The bottom line shows the 5 (`LINE_HINTS`) lowest-priority hints for where the player is, each action once, then "F1 controls". Docked, the station's keys come first.
- F1 opens a panel of every hint by context, including M, X, wheel zoom, the burn planner keys and the pad's buttons.
- `HintRegistry.withKeys` replaces `{ACTION}` in guide text with the bound key. First Play's objectives and the opening walkthrough use it.

### Input fixes (`KeyBindings`)
- New actions: `MISSIONS` (J), `BOARD` (K), `ABANDON` (P), `LOG` (L), `HELP` (F1). Deck lights moved from L to O. The game reads every key as an action, so rebinding applies everywhere.
- Burn delete is only the Delete key now; Backspace no longer deletes a burn.
- Rebinding onto a key another action uses names that action and waits: the same key again swaps the two, another key is tried instead, and Esc cancels. Reserved keys say they can't be bound.
- Esc closes the mission and boarding windows, the notice log and the controls panel before it opens the pause menu (`GameShell.hudWindowOpen`, `RingsOfIronGame.isOverlayOpen`).

### Gamepad and focus navigation (`GamepadKeys`, `FocusNav`)
- Holding Back gives a second layer: A/B/X/Y open the mission board, boarding, market and yard; the d-pad steers the planned burn; LB/RB move it earlier/later; LT plans a burn, RT selects the next, R3 deletes it; L3 quick-saves; Start abandons ship. Back tapped alone opens the map on release.
- `FocusNav` in ui-hud moves focus with Up/Down through buttons, check boxes, sliders and drop-downs in layout order, skips disabled ones, wraps, adjusts a slider or drop-down with Left/Right, and presses with Enter.
- It runs in the main and pause menus and settings (`GameShell.menuNav`) and in the HUD's console, market, yard, mission and boarding windows (`UiHud.openWindow`). While one is open the pad is in menu mode: d-pad or left stick move, A presses, B, Start or Back back out. The burn planner leaves the arrow keys to an open window.

### Autosave (`AutosaveClock`, `SaveSlots`)
- Autosaves come on docking at a station, just before undocking with a station (on the undock key), and every 600 sim seconds (`FLIGHT_INTERVAL`) of flight outside time warp. None is made while the ship is disabled or being abandoned, or after a crash or career end.
- They go into `autosave-1` to `autosave-3` (`AUTOSAVES`): an empty slot first, else the oldest. They show in the load list as "Autosave N". A named save can never take an autosave file.
- The clock restarts after every autosave and every load, so a load never autosaves at once.

### Warnings (`FlightWarnings`)
- Propellant below 20% (`LOW_PROPELLANT`) posts a warning once; it is armed again only when the tanks pass 25%. `returnPropellantKg` is the hook for navigation's fuel estimate: when set, low also means below what the trip home needs. Nothing sets it yet.
- A missile warning is posted whenever the number inbound rises, with the time to impact. On deck a large red "MISSILE WARNING" line also goes up at the top of the screen.

### Distress beacon and tow (`DistressBeacon`, `Rescue.tow`)
- A ship is stranded when disabled or below 20% propellant. The beacon is refused while docked, after a crash, with the pods away, when the ship can still fly home, or with missiles inbound.
- B (`CUT`) at the helm, or on deck away from a door, asks first and names the fee; B again within 3 s calls the tug. The pause menu shows "Call a tow" when allowed and calls at once. A `DISABLED` hint "twice: call a tow" shows when it is allowed.
- The tow lets go of anything latched, stands the crew down from the pods, skips `Rescue.TIME_SKIP` (3 days) on the rails, and docks the same ship at the sponsor's nearest station with its crew, cargo and damage. It costs 10% of `Difficulty.shipValue` (`FEE_SHARE`, the account can go negative) and 3 standing with the sponsor (`STANDING_COST`).

### Avatar death (`GameWorld.checkAvatar`, `Rescue.carryHome`)
- If the player's avatar dies and any of the ship's own crew are alive aboard, they bring the captain home by tow at no fee: the avatar is conscious at half health and back aboard.
- With nobody left, `getCareerOver()` gives the reason and the world stops. A "CAREER OVER" screen offers the quick save (F9) and, when one exists, the latest autosave (Enter or E). "SHIP LOST" offers the same.

### Crash through rescue (`GameWorld.crash`)
If the crew were ordered to the pods before impact, the pods with anyone aboard launch at impact. If the captain was in one, it is a rescue, not SHIP LOST.

### Opening walkthrough (`OpeningGuide`)
A new game (not First Play) opens with gold guide text: open the board, take a job, then a last word on undocking, thrust, turning, the map and F1. It stops after a contract is held, or quietly if the player leaves the station first. "Walkthrough at the start of a new game" in the settings (`GameSettings.openingGuide`, on by default) turns it off, which also stops one under way.

### First Play's last two objectives
After the yard, First Play (`FirstPlay`, now nine objectives) asks the player to open the notice log (L) and then to plan a transfer: pick a navigation target (Q), plan to it (;), and accept or discard the plan. Opening the log is reported by `FirstPlay.logOpened`, which `RingsOfIronGame` calls when the log opens; it is not saved, so a game loaded at that objective waits for the log to be opened again. Any proposed or accepted plan meets the last objective.

### Small fixes
- `Rescue.skipTo` calls `orbitalEvents.orbitChanged`, so a rescue from a ship falling into a planet no longer crashes the new ship on arrival.
- `AbandonShip.launchNow` sets the deadline a second past due.
- The window icon is the corvette hull on a dark disc at 128, 64, 32 and 16 px, drawn by `WindowIcons` (`gradlew :art-pipeline:windowIcons`) into lwjgl3's resources.

## Saves
- `GuideProgress` is a new save participant (`game.guides`) holding First Play's step and the walkthrough's step, by name. `GameScenario.quickLoad` resets it first, so a save without it leaves both. `FirstPlay.resume` and `OpeningGuide.resume` pick up from it on load.
- Autosaves are ordinary save files with a `SaveSummary`.
- `GameSettings.openingGuide` and the new key bindings are in the settings file, not the save.
- Not saved: the notice log, the autosave flight clock, the warnings' armed state and the tow confirmation. A load clears the log and the on-screen stack (`NoticeLog.clear`), so the old game's notices don't carry over.
- A lost game can't be saved: `GameScenario.quickSave` refuses after a crash ("the ship is lost") and once the career is over ("the career is over"), and autosave goes through it. It also refuses while a tug is still bringing the ship in to dock ("the tug is still bringing you in"), since `Rescue` keeps that docking in memory only. Docking happens on the first frame both ships are in the bubble, so the window is short.
- With the walkthrough turned off in settings, a load drops a saved walkthrough step (`OpeningGuide.resume(world, boardOpen, wanted)`), so later saves stop carrying it.

## Tests
- `NoticeLogTest`: hold times, stack of 4 and log of 50, counted repeats, sim-time stamps, contract kinds and the feeds.
- `HintRegistryTest`: rebinding, docked order, line length and one key per action, `onlyWhen`, the panel's contexts and keys, and every action having a hint.
- `GamepadKeysTest`: sticks, buttons, the Back layer, Back tapped alone, and menu mode. `FocusNavTest`: order, nesting, disabled controls, wrapping, Enter and a rebuilt window.
- `AutosaveAndWarningsTest`: the autosave clock, low propellant and the return-trip hook, and First Play saved and resumed. `SaveSlotsAutosaveTest`: slot rotation, the latest autosave and named saves.
- `NoSoftLocksTest`: refusal when the ship can fly home, the tow (same ship, docked, 3 days, fee, standing, crew), a negative account, the captain carried home, career over, and a crash with and without the captain in a pod.
- `OpeningGuideTest`: the steps, resume and stop, and key names in the text.
- `AutosaveGapsTest`: no autosave while disabled, abandoning, crashed or career-over (`AutosaveClock.due`, which render calls), and Continue loading the newest autosave's state.
- `FirstPlayTest`: all nine objectives in order, including waiting for the log to be opened and a transfer planned to Shackleton Ice from Highport's dock (picking the target alone doesn't count).

## Choices made without Joe
- The tow's 10% fee, 3 standing and 3 days are the brief's defaults. The tow is refused with missiles inbound.
- A carried-home captain wakes at half health, and the crew's tow is free.
- Autosave skips a disabled or abandoning ship so the older slots stay good.
- The low-propellant warning re-arms only past 25%.
- B is the tow key because it is already `CUT`; it confirms with a second press within 3 s, like abandon ship.
- Deck lights moved to O so the log can have L. Backspace no longer deletes a burn.

## Not built
- No system sets `FlightWarnings.returnPropellantKg`, so low propellant is 20% alone until category 2 lands.
- The brief's "pod-less" case: a ship with no pods that is neither disabled nor below 20% propellant can't call a tow.
