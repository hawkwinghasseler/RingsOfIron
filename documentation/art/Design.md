# Art Asset Pack: Style and Naming Guide

## Overview
The game's art is made as SVG in `assets/art`: interior tiles, edge walls and doors, ship components in every mount size, state effects, sub-tile decor and interactive objects, weapon turrets, and the exterior hulls of ships and stations. SVG is the source format; the game will load raster versions of these files (see **Using the art in the game**).

Open `assets/art/preview.html` in a browser to see every asset, and every chassis in `assets/data/chassis.toml` drawn with the tile and component art.

## Scale and orientation
- **One tile is 64 SVG units.** A tile is one meter in game (`tile_size = 1.0`), so 64 units is one meter everywhere, exterior art included.
- **Sub-tile objects sit on a quarter-tile grid of 16 units.** Their canvas is their footprint rounded up to that grid (16x16, 32x16, 32x32, 32x64...).
- **Everything is drawn at rotation 0.** SVG up is the grid's +y. A rotation in data is counter-clockwise quarter turns, so code rotates the art the same way.
  - Auxiliary components are drawn in their unrotated size (helm M is 2x3 tiles, 128x192); a rotated mount turns the art 90°.
  - Primary components face +y; the turret mount's breech points up.
  - Docking ports face +y: frame tiles at both ends, the opening between them, the port door along the top edge.
  - Hull wedges at rotation 0 have their legs on the left and bottom edges and the sloped face toward +x/+y (`HullPiece.Shape.WEDGE`).
  - Edge walls and doors lie along a horizontal tile edge, which runs along the middle of their 16-unit-tall canvas; a vertical edge turns them 90°.
  - Wall-mounted decor (lockers, panels, lights, pipes) has the wall at the top of its canvas.
- **Exterior art is in the body frame.** Ship and station hulls use body coordinates times 64 with y flipped, so the SVG origin is the body origin and the bow points to +x, matching `hull` in `ships.toml` and the hardpoint positions. Turrets have their pivot at the SVG origin and the barrel toward +x. Engines and port collars may stick out past the hull polygon.

## Style
Top-down, orthographic, flat-shaded vector art with dark outlines. Light comes from the top-left: a lighter line along the top and left inside edges of a raised part, a darker one along the bottom and right. No perspective, no drop shadows outside a footprint, so tiles butt together cleanly.

- **Outlines:** `#10141a`, 2 units on tiles and components, 1-1.5 on small details, 3 on exterior hulls.
- **Detail density:** read clearly at 32 px a tile (half size). Rivets, seams and screens are details; the silhouette and the accent color must carry the meaning.
- **Glow:** emissive parts (reactor core, shield emitter, screens, lights) use the accent color with a white-hot center or a soft radial gradient. Gradients are the only place colors blend.

### Palette
| Role | Color |
|---|---|
| Outline | `#10141a` |
| Deck base / plate / seam / highlight | `#3a434f` / `#444e5b` / `#2a3039` / `#566170` |
| Steel light / mid / dark / highlight | `#5b6675` / `#4b5563` / `#373f4b` / `#7d8898` |
| Machinery housing / highlight | `#2f3641` / `#465060` |
| Screen glass | `#0c1a26` |
| Power (conduits, junctions) | `#f2b134` |
| Hazard stripe (doors, airlocks, ports, seals) | `#f2c230` on outline black |
| Status lights | sealed or off `#ff4d4d`, open or on `#4dff88` |

Each system has an accent, used for its glow, screens and status marks, and nowhere else:

| System | Accent |
|---|---|
| Reactor | `#ff9a3c` |
| Shield emitter | `#4fd1ff` |
| Turret mount | `#ff5a5a` |
| Life support | `#5ee08a` |
| Helm | `#6aa8ff` |
| Weapons console | `#c58cff` |
| Cargo | `#c8a26a` |
| Airlock, docking port | `#f2c230` (hazard) |

These match the `color` a tile type or component carries in data (see the ship editor design) where one is set, so the editor's flat colors and the art agree.

## Naming
File names are lowercase `snake_case` and match the ids in data, so code can find the art from a definition without a lookup table.

| Folder | Name | Example |
|---|---|---|
| `tiles/` | the tile type's id; hull weight classes as `hull_<weight>`; wedges as `hull_<weight>_wedge_<w>x<h>`; floor coverings as `covering_<covering>` | `floor`, `conduit`, `hull_heavy`, `hull_medium_wedge_1x2`, `covering_carpet` |
| `edges/` | `edge_wall_<weight>`, `bulkhead_door_<state>` | `edge_wall_light`, `bulkhead_door_sealed` |
| `components/` | `<component id>_<size>`, size `s`, `m`, `l` or `xl`; a state is a suffix | `reactor_xl`, `helm_m`, `docking_port_l_open`, `docking_collar_s` |
| `effects/` | the state it shows | `fire_medium`, `breach`, `damage_heavy`, `bulkhead_seal`, `unpowered` |
| `decor/` | the object | `crate_small`, `pipe_run`, `bunk` |
| `interactive/` | the object; levels as a number suffix | `fire_extinguisher`, `cargo_stack_2` |
| `turrets/` | the weapon id in `weapons.toml` | `railgun_mk1`, `harpoon_launcher` |
| `hulls/` | the chassis id in `chassis.toml` | `corvette`, `station_hub`, `thruster_plume` |

Component ids are the built-in components the ship editor design names (`reactor`, `shield_emitter`, `turret_mount`, `life_support`, `helm`, `weapons`, `cargo`, `airlock`, `docking_port`), which are `SubsystemType` in lowercase. Tile ids follow the built-in map characters: `floor` (`.`), `conduit` (`+`), `hull_light`/`hull_medium`/`hull_heavy` (`1`/`2`/`3`, and `#` is medium), `internal_wall` (`I`). If `tiles.toml` settles on different ids, rename the files to match.

A new component made in data (say `fusion_core` with behavior `REACTOR`) gets its own `fusion_core_<size>.svg`; until it has one, code can fall back to the art of its behavior's built-in component.

Asset keys in `assets.toml` follow the existing `tex_` prefix with the folder and name: `tex_tile_floor`, `tex_component_reactor_xl`, `tex_hull_corvette`.

## How the parts fit together
- **Conduits** autotile: draw `conduit` on the tile, then `conduit_arm` once for each neighboring conduit tile, turned toward it (0 up, 1 left, 2 down, 3 right). A lone conduit shows only its junction.
- **Components** cover their whole footprint and are drawn over the floor. Helm, weapons and cargo art include the deck, so the room reads as floor with furniture on it; the others are machinery housings.
- **Docking ports**: `docking_port_<size>` has the door sealed, `docking_port_<size>_open` open. The latch collar is not on the grid: `docking_collar_<size>` sits half a tile (32 units) outside the outer edge, centered on the port.
- **Effects** are overlays: fire by `Tile.FireSize`, `scorch` once a fire burns out, `breach` on a hull tile at integrity 0, `damage_light` below half integrity (where leaks start) and `damage_heavy` below a quarter, `bulkhead_seal` on a sealed tile, `unpowered` stretched over a system with no power.
- **Cargo stacks**: `cargo_stack_1` to `_3` are one bay tile's stack at a third, two thirds and full of `CargoHoldComponent.STACK_HEIGHT`.

## Colliders and interaction points
Decor and interactive objects that block movement or can be used carry two invisible elements, so code can read them straight from the SVG:

- `id="collider"`: a `<rect>` or `<circle>` giving the collision shape in the same units as the art.
- `id="interact"`: a `<circle>` whose center is where crew stand or reach to use the object.

Both have `fill="none" stroke="none"`, so they never draw. Flat decor (grates, stripes, arrows, wall lights) has neither.

## Using the art in the game
libGDX does not draw SVG, so the game will load PNGs rasterized from these files: 64 px a tile at 1x, 128 at 2x, packed into a texture atlas. That build step is not part of this pack yet; until it is, keys in `assets.toml` still point at placeholders.

## Regenerating
The first pass was drawn by `tools/art/generate_svgs.py`, which keeps the many sizes of each component consistent. The SVGs are the source: edit them in any vector editor (Inkscape, Illustrator, Figma). Re-running the script overwrites every file it writes, so once a file is edited by hand, take it out of the script.

`tools/art/make_preview.py` rebuilds `assets/art/preview.html`; `--inline` embeds the SVGs so the page is one shareable file.

## Asset list
- **Tiles (14):** floor, conduit and conduit arm, carpet covering, internal wall, hull in three weight classes, and 1x1 and 1x2 wedges in each.
- **Edges (5):** edge walls in three weight classes, bulkhead door sealed and open.
- **Components (41):** reactor, shield emitter and turret mount (primary, S-XL); helm, weapons console, life support, cargo bay and airlock (auxiliary, S-XL); docking port S-L sealed and open, and the collar for each size.
- **Effects (9):** fire small, medium and large, scorch, breach, light and heavy damage, bulkhead seal, unpowered.
- **Decor (17):** vent grate, floor hatch, pipe run, pipe valve, cable tray, wall light, hazard stripe, floor arrow, small and large crates, barrel, locker, bunk, table, mess table, chair, plant.
- **Interactive (10):** fire extinguisher, bulkhead panel, breaker, terminal, emergency locker, repair kit, med bed, cargo stacks at three levels.
- **Turrets (4):** railgun, autocannon, laser, harpoon launcher.
- **Hulls (5):** corvette, freighter, outpost and hub stations, thruster plume.

## Open Questions
- Crew sprites are not in this pack.
- Should material (hull steel vs composite armor) change a hull tile's art, or only its tint?
- Damaged and destroyed variants of each component, beyond the shared damage overlays.
