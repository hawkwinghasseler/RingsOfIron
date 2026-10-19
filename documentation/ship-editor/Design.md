# Ship Editor Design

## Overview
The ship editor is a standalone libGDX tool for making and changing the game's ships. It edits three kinds of data, all of it the same data the game loads, so whatever is saved in the editor is what the game builds the next time it starts:

- **Ship layouts**: the `[chassis.*]` interior layouts in `assets/data/chassis.toml` (ships and station layouts alike).
- **Tile types**: the `[tile.*]` building blocks a layout is painted with (floors, conduits, hull, internal walls), in `assets/data/tiles.toml`.
- **Ship components**: the `[component.*]` systems a layout mounts (reactors, life support, cargo bays, docking ports...), in `assets/data/components.toml`.

Run it with `gradlew :lwjgl3:runShipEditor`, or `demos/Ship editor.bat` on Windows. It opens on the first chassis found.

## Implementation Dependencies
- **`modding-data`**: the definitions (`ChassisDef`, `TileTypeDef`, `ShipComponentDef`) and `DataFileParser`, so the editor reads data exactly as the game does.
- **`interior-grid`**: `ChassisBlueprintLoader` builds every edit into a real `ShipGridComponent`, so the editor shows and validates what the game will build. The editor's data model and its TOML writer (`com.ringsofiron.interiorgrid.editor`) live here, free of rendering, so they are unit tested.
- **`core`**: the editor screen (`ShipEditor`), drawn with `ShapeRenderer` and the default `BitmapFont` like the other demos. `lwjgl3` has its launcher.

## Data-Driven Tiles and Components
Until now the map characters and the systems they stand for were fixed in code (`Tile.WallType`, `Tile.FloorType`, `SubsystemComponent.SubsystemType`). To let a designer make new ones, each becomes a definition that names the built-in behavior it uses and the settings it changes. Code keeps the behaviors; data says which ones exist and what they are called.

### Tile types
```toml
[tile.armor_plate]
name = "Armor plate"
symbol = "X"              # one character, used in chassis rows
base = "HULL"             # VOID, FLOOR, CONDUIT, HULL or INTERNAL_WALL
weight_class = "HEAVY"    # walls: LIGHT, MEDIUM or HEAVY
material = "composite_armor"  # a [material.*] id
covering = "NONE"         # floors: NONE or CARPET, laid when the ship is built
art = "walls/composite_armor_heavy"  # art pack region or wall family (see documentation/art/Design.md)
color = "#7a8a9a"         # how the editor draws it without the art pack
```
`assets/data/tiles.toml` restates the built-in characters as definitions (`#`, `1`-`3`, `I`, `.`, `+` and space) so they can be edited like any other; a tile type a data file leaves out keeps its built-in meaning, so blueprints made in code still load.

### Ship components
```toml
[component.fusion_core]
name = "Fusion core"
symbol = "F"              # one letter, used in chassis rows
behavior = "REACTOR"      # a SubsystemType: REACTOR, SHIELD_EMITTER, TURRET_MOUNT, LIFE_SUPPORT,
                          # HELM, WEAPONS, CARGO, AIRLOCK or DOCKING_PORT
sizes = ["M", "L"]        # mount sizes it comes in (default: every size its mount kind has)
color = "#ff9a3c"
power_output = { M = 4.0e6, L = 1.4e7 }   # REACTOR: watts at full health, by size
```
The behavior fixes the mount kind (square primary, rectangular auxiliary or hull-edge port) and what the system does. The settings a component can change today are:

| Setting | Behavior | Meaning | Default |
|---|---|---|---|
| `power_output` | `REACTOR` | Watts by mount size | 1, 3, 10, 30 MW for S-XL |
| `throughput` | `LIFE_SUPPORT` | Multiplies scrubbing, air and oxygen supply | 1.0 |

Each installed system keeps its component id and these values on its `SubsystemComponent` (so a save restores them, even if the data changes later). Other systems read the behavior through `SubsystemComponent.type`, exactly as before, so a new turret component works wherever a turret mount does. More settings (shield strength, cargo stack height, crew console speed) are added the same way when the systems that would read them want them.

Symbols are one character and share one namespace: no two tile types or components may use the same one. The catalog that resolves them (`ShipPartCatalog`) is built from the definitions when data loads, and throws on a clash, a bad base or behavior, or a size a component's mount kind lacks.

### Chassis
Chassis rows draw components by their symbol, as before. An explicit mount may name a component instead of a behavior: `mounts = [{ component = "fusion_core", size = "M", x = 3, y = 2 }]` (`type = "REACTOR"` still means the built-in `reactor` component).

## The Editor

### Layout
The screen is the ship on a tile grid, with a panel on the right. Three tabs at the top of the panel (or Tab) switch what it edits: **Layout**, **Tiles** and **Components**. The mouse wheel zooms the grid (over the panel it scrolls the panel), right or middle drag pans, F fits the ship to the view. Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S (or the Save button) saves everything that changed, and Esc quits; with unsaved changes it warns first and quits on a second Esc (closing the window works the same way). The line at the bottom left describes the tile under the cursor, with its position on the body in meters.

### Layout tab
- **Chassis list**: pick a chassis to edit, or **New** (asks for an id; it starts as a 16 x 9 hull around steel floor). **Duplicate** copies the open one under a new id, **Delete** removes it (from its file on the next save).
- **Tools** (number keys or click):
  1. **Paint**: left-click or drag paints the selected tile type; drag with Shift fills a rectangle.
  2. **Component**: places the selected component at the cursor with the selected size; R rotates. The ghost is green where the game would accept it and red (with the reason) where it would not. Every component shows an arrow for the way it faces (up, +y, at rotation 0).
  3. **Edge wall**: click near a tile edge to add or remove a thin wall there, with the weight class and bulkhead door chosen in the panel.
  4. **Hull piece**: places a wedge or block of the size chosen in the panel, centered on the cursor; R rotates and X (or the Flip button) mirrors it left to right. A mirrored wedge is stored as the same wedge with width and height swapped, turned one step the other way, so the data has no flip of its own. A new piece replaces any piece it overlaps; the ghost is green or red like a component's, and a piece that would leave the grid is not placed.
  5. **Covering**: drag a rectangle to lay carpet.
  6. **Erase**: removes the wall light nearest the cursor's edge, else an edge conduit on the edge the cursor is close to, else the component, hull piece, edge wall or covering under the cursor, or paints void.
  7. **Port**: click a docking port to toggle it active or passive.
  8. **Light**: click near a floor tile's edge to hang or take down a wall light there, shining into the tile. A light takes no room: the tile stays free for anything else. **Auto-place lights** in the panel replaces the layout's lights with the ones the game would work out (see `documentation/lighting/Design.md`), as one undoable edit; **Clear** leaves the layout with none. Lights are saved under `lights = [{ x = 3, y = 2, side = "TOP" }]`; a layout without the key gets the auto-placed ones when the game builds it.
  9. **Conduit**: click near a tile edge to run a power conduit along it, or take up the one there. An edge conduit may run inside a thin wall or along a full-tile wall, so power can cross walls (see Power in `documentation/interior-grid/Design.md`); runs join where their edges meet. Click the middle of a floor tile to lay a conduit across it (the conduit tile type) or take it up. Edge conduits are saved under `edge_conduits = [{ x = 3, y = 2, side = "TOP" }]`.
- **Rotating placed components**: with any tool but Hull piece, R over a placed component turns it a quarter turn counter-clockwise about its middle (a rectangular slot swaps its width and height) and undoes like any edit; the next one placed faces the same way. It refuses, with the reason, when the turned slot would leave the grid or overlap another mount. Docking ports are not turned: they face out of the hull edge they sit in. A turned square mount is saved under `mounts` with its `rotation`, since the rows cannot show which way it faces.
- **Grid**: buttons add or remove a row or column on any side (everything listed shifts to match, and the origin moves so the rest of the ship stays put on the body); fields set the tile size and the origin, and **Center origin** puts the body origin in the middle of the grid (drawn as a blue cross).
- **Properties**: name, `station` flag.
- **Check**: every edit is rebuilt through `ChassisBlueprintLoader`; the panel shows the result (tile count, mounts, cargo volume, rooms, power reach) or the loader's error, and the problem tile is outlined in red. A chassis with an error can still be saved, so work in progress is never lost, but the panel warns that the game will refuse it.

### Tiles and Components tabs
Each lists every definition with its symbol and color. **New from selected** makes one as a copy of the selected one (so a new turret starts as a copy of the turret mount) under an id you type, with a free symbol; every field is an editable row: text fields take typing (Enter keeps, Esc cancels), choices are buttons. A component's sizes are toggles; its behavior can change freely while no chassis mounts it, and only within the same mount shape while one does. A definition that chassis use cannot be deleted until they stop using it; changing its symbol rewrites those chassis rows to match.

### Saving
The editor writes TOML by hand rather than through the parser, so the files stay readable and keep their comments:
- A definition that came from a file is rewritten **in place**: its `[type.id]` section is replaced and the rest of the file is left untouched. Comment lines inside the section stay with the key they sat above.
- A new definition is appended to the file its type lives in (`chassis.toml`, `tiles.toml`, `components.toml`).
- Rows are written as a column of quoted strings. A mount is drawn in the rows when the loader would read it back exactly (no touching neighbor with the same symbol, and the rotation the loader would infer); otherwise it goes in `mounts`. Mounts that came from `mounts` stay there.
- Saving only touches base data in `assets/data`. Mods are read so their parts show up, but not written.

The ship's exterior (`hull` polygon, hardpoints, mass) in `ships.toml` is not edited here; the panel shows the grid's extent in body coordinates so the hull polygon can be matched to it. The hull polygon is to be generated from the layout instead (see below).

## Decided
- **Hull polygon from the layout** (2026-10-19): a ship's `hull` polygon in `ships.toml` is generated from the layout's outer hull tiles. A `collider` shape in the `meta` layer of the ship's hull SVG still wins when it has one; without it, the tile geometry is used.
- **Tanks and pipes** (2026-10-19): tanks for life support consumables and propellant are placed in the layout, and pipes are laid in tile centers or along tile edges like conduits. Placing both belongs in this editor (see Life Support in `documentation/interior-grid/Design.md`).

## Open Questions
None right now.
