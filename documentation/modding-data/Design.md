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

## Proposed Components
- *None specific to this module, as it primarily generates templates and components for other modules based on external data.*

## Proposed Systems/Classes
- `TomlDataLoader`: Deserializes `.toml` files into Java POJOs.
- `GroovyModLoader`: Instantiates the Groovy environment and executes initialization scripts.
- `EntityFactory`: Constructs fully populated ECS Entities based on parsed definitions.
