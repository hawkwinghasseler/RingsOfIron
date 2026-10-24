# Modding & Data Loaders Module Design

## Overview
The `modding-data` module is responsible for loading all external assets, configuration data, and third-party mod scripts. It ensures the game is data-driven, allowing easy content creation.

## Implementation Dependencies
- **`core-engine`**: Needs access to the Ashley ECS `Engine` to inject loaded Entity templates, and the UUID registry.
- **Independent Asset Loader:** This module largely stands alone as a pre-game initialization step.
- **External Libraries:** LibGDX `AssetManager`, a TOML parsing library (e.g., `toml4j`), and Groovy language libraries.

## Implementation Guide
To implement this module independently:

1. **Central Asset Registry:**
   - Create an `AssetRegistry` wrapping LibGDX's `AssetManager`.
   - Instead of referencing file paths directly in code, systems request assets via string keys (e.g., `registry.getTexture("tex_laser_red")`).
   - The registry parses a master `assets.toml` to map these keys to local file paths during boot.

2. **TOML Data Parsing & Entity Templates:**
   - Define POJO classes that mirror your TOML configurations (e.g., `ShipDef`, `WeaponDef`, `CommodityDef`).
   - Use `toml4j` to deserialize `.toml` files located in the `data/` and `mods/` directories into these POJOs.
   - Create an `EntityFactory` class. It holds a registry of `Map<String, BaseDef>`. When a system requests a new entity (e.g., `spawnShip("chassis_corvette")`), the factory reads the definition and constructs the ECS Entity, attaching the necessary components defined by the TOML data.

3. **Groovy Script Engine (Sandboxing):**
   - Import Groovy dependencies.
   - Instantiate a `GroovyShell`.
   - **Binding Context:** Create a `Binding` object and pass it to the shell. Inject vital game managers into this binding so scripts can access them (e.g., `binding.setVariable("engine", ecsEngine); binding.setVariable("factory", entityFactory);`).
   - **Execution:** Iterate through the `mods/` folder and call `groovyShell.evaluate(scriptFile)` on any `init.groovy` files found.
   - *Security Note:* Scripts run with unrestricted JVM access. Implement warning prompts in the UI alerting players to the risks of loading untrusted mods.

## Implemented Components
- `DefRefComponent`: the definition type and id an entity was spawned from. Everything else the loaders make is other modules' components, filled from data.

## Implemented Systems/Classes
- `TomlDataLoader` and `DataFileParser`: read `.toml` and `.json` files into one tree.
- `DefRegistry`: holds the merged definitions and converts them to any module's POJO.
- `GroovyModLoader`: Instantiates the Groovy environment and executes initialization scripts.
- `EntityFactory`: Constructs fully populated ECS Entities based on parsed definitions.
- `ModdingBootstrap`: the entry point that runs all of the above in order.

## Prototype Status
Package `com.ringsofiron.moddingdata`. Depends only on `core-engine`; other modules depend on it, never the reverse.

- **Data format:** `assets/data/*.toml|*.json` and `mods/<id>/data/`, as `[type.id]` tables (the combat-sys section 6 shape). TOML and JSON produce the same tree. Keys are snake_case and fill camelCase fields.
- **`DefRegistry`:** keeps each definition as a merged raw tree and converts on demand, so any module's POJO works (`defs.get("weapon", "railgun_mk1", WeaponDef.class)`) and `registerType` can happen before or after loading. Later sources deep-merge over earlier ones field by field; `_replace = true` and `_remove = true` override that.
- **Built-in def classes:** `ShipDef`, `ChassisDef` (mirrors interior-grid's `ChassisBlueprint`, enums as strings; `station = true` marks a station layout and `ports` lists the active docking ports), `MaterialDef`, `CommodityDef`, `StationTypeDef` (its `blueprint` names the station's chassis; the validator warns when it names an unknown chassis or one not marked as a station; a type with none gets the default outpost layout). Weapon/projectile/missile/warhead classes belong to combat-sys.
- **Factions and ranks** (round 2): `FactionDef` (`[faction.<id>]`: `name`, `color`, `sponsor`, `stations` by name, `enemies`, `hostile_to_all`) and `RankDef` (`[rank.<id>]`: `name`, `order`, `standing`, `budget` per week, `max_chassis`, `min_days`, `ship_days`), both in `assets/data/factions.toml`. The validator warns when a faction's enemy or a rank's `max_chassis` names something that doesn't exist. How the game uses them is in `documentation/progression/Design.md`.
- **Engines and escape pods** (round 2): a ship component's `thrust` table gives the newtons an ENGINE of each size adds (for example `thrust = { M = 1.5e6 }`). Sizes it leaves out take the standard 1.2, 3.6, 12 and 36 MN, and a ship with no engines falls back on its `max_thrust`. An ENGINE's `efficiency` multiplies the ship's exhaust velocity (mixed engines average by thrust), its `casing` names the weight class of its hull (LIGHT if left out), and any component's `price` table is what a station yard charges to fit one of each size when it rebuilds the decks (half back for one it takes out); `maker` and `description` are shown in the editor. The engines come from three makers: Halvorsen (`engine` HR-40, `freight_drive` HR-25F, symbol `Q`), Imbrium (`gunship_drive` Rampart, `imbrium_bulwark` Bulwark, symbol `K`) and Aster (`aster_lyra` Lyra 7, symbol `Y`). `escape_pod` (symbol `B`) is the `ESCAPE_POD` component.
- **Round 2 ships:** `raider_light` (chassis `raider`, one `chaingun_mk1`, crew 3) and `gunship_heavy` (chassis `gunship`, a railgun, two harpoon launchers and two autocannon, crew 5).
- **Round 9:** the raider's Medium reactor sits amidships behind its helm and weapons consoles, not right behind the nose turret, so a railgun slug from ahead no longer darkens it at 3 to 4 km; the aces fly the same chassis. Each ship's `mass` in `ships.toml` is the mass it flies with full tanks (the game flies a gridded ship's own mass and reads the figure only for a ship without a layout); `ShipMassDataTest` fails on more than 5% drift.
- **Sounds:** `[sound_event.<id>]` (`SoundEventData`: `path`, `looping`, `duration`, `min_distance`, `max_distance`, `volume`) gives playback rules for an audio event path. `GameWorld.loadContent` registers each one with audio-spatial's `AudioEventCatalog`, so a mod that points an `[audio_event]` key at a new path also says how that path plays.
- **`AssetRegistry`:** `assets.toml` key tables per kind (`texture`, `atlas`, `sound`, `music`, `font` load via `AssetManager`; anything else, like `audio_event`, is a string lookup). Mod paths resolve inside the mod folder.
- **`EntityFactory`:** `spawn(type, id, x, y, angle)` adds `UuidComponent`, `TransformComponent`, `DefRefComponent`, then each `ComponentAssembler` registered for that type.
- **Mods:** `ModDiscovery` reads `mod.toml` (`id`, `name`, `version`, `depends`, `load_after`, `enabled`) and orders mods by dependency. `GroovyModLoader` runs `init.groovy` with `engine`, `factory`, `defs`, `assets`, `hooks`, `mod`, `log` bound; the mod folder is on the script classpath. `ModTrustPolicy` gates scripts (ui-hud should supply the player prompt). Broken mods are recorded in `LoadReport` and skipped.
- **Entry point:** `new ModdingBootstrap(assetsDir, modsDir).trustPolicy(p).beforeScripts(ctx -> ...).load(gameEngine)` returns a `ModContext`. The desktop launcher runs from `assets/`, so it would pass `Paths.get(".")` and `Paths.get("../mods")`.
- **Try it:** `./gradlew :modding-data:test` and `./gradlew :modding-data:runModdingDemo` (loads base data plus `modding-data/examples/mods`). `InteriorGridDataTest` holds the `ChassisDef -> ChassisBlueprint` and `MaterialDef -> MaterialTable` adapters, run against interior-grid's real loader.
