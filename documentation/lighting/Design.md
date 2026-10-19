# Lighting: Box2D lights from the art's emissive layers

## What it does
- **Inside a craft** (deck view), most of a room's light comes from **wall lights** (`decor/wall_light`), each washing the half of the room in front of its wall with broad light while the craft has power. Every glowing part of the other art drawn on a deck is a light too: each mounted system's lamps, screens and glow, live power conduits, and fires. Wall lights and the bigger parts cast [box2dlights](https://github.com/libgdx/box2dlights) rays, so walls, hull and shut doors throw shadows; the small ones (status lamps, conduit studs) only glow.
- **Crew** cast shadows: each crew member in view is a small circle in the shadow world (half their size, so the light's soft edge still falls across them and they are not left in their own shadow).
- **Bulkhead doors** light the floor either side of them in their lamps' color, red shut and green open, a short way (1.6 tiles, at 0.35), while the craft has power. Door lamps are added after every other light on a deck, so when a scene has more lights than can cast at once (`SceneLighting.MAX_LIGHTS`) they are the first left out.
- **Power** decides what is lit. A system's lights are on while it runs (`SubsystemComponent.isOperational`), so a wrecked, unpowered or switched-off system goes dark. With no reactor running, the ambient light drops from cabin light to near darkness.
- **Outside**, a hull's emissive layer (navigation lights, bridge glazing, reactor and shield glow) is drawn as a glow on the craft. A light over a system's footprint follows that system; navigation lights follow the craft's power.
- **L** in the deck view turns the lighting off and on.

## Where the lights come from
`tools/art/extract_lights.py` reads the `<g id="emissive">` group of every SVG in `assets/art` and writes `assets/art/lights.json`. Shapes that touch are one light (a lamp's white center, colored body and halo), its color the most saturated one among them (the accent), its size the group's bounding box. Re-run it whenever the art changes; a test fails when `lights.json` and the SVGs disagree about which art glows.

Entries are keyed `<folder>/<name>`, in SVG units with y up and the SVG origin at (0, 0); `canvas` gives the art's bottom-left corner and size, so art placed from its corner (components, tiles) subtracts it.

## How bright a light is
The art says, with attributes on any shape of the light or on a `<g>` around its shapes inside the emissive group:

| Attribute | Meaning | Without it |
|---|---|---|
| `data-light` | `glow` (lights nothing around it), `point` or `cone` | Judged by size: parts of at least 8 SVG units can light the room |
| `data-reach` | How far its light reaches, tiles (meters outside) | 0.6 + 2.2 per tile of the part's size, up to 7 |
| `data-intensity` | How strong it is, 0-1 | 0.35 + 0.65 x the part's opacity |
| `data-direction` | A cone's direction, degrees, 0 = +x, 90 = up the canvas | -90 (away from a wall at the top) |
| `data-cone` | A cone's half-angle, degrees | 45 |

A system lights the room from at most 3 of its parts, those marked `point` or `cone` first, then the biggest; the rest only glow. So a designer tunes a light where it is drawn (in `tools/art/generate_svgs.py` for the base art), and the extractor copies the settings into `lights.json`. The wall light, for one, is a cone 90° each side, so a half circle against its wall, reaching 6 tiles at 0.7. The game turns the settings into a light the same way everywhere, and the room's ambient light (`SceneLighting`) sets how dark the unlit corners are.

## Wall lights
A wall light hangs on one edge of a floor tile, shining into that tile; it takes no room, so the tile stays free for a system, a door or anything else. Its art's top edge sits against the wall, and the art turns with the wall it hangs on.

`DeckArt` draws `decor/wall_light` (or `wall_light_unpowered` when the craft has no power) at each one. A layout lists them in `chassis.toml`:
```toml
lights = [
  { x = 3, y = 2, side = "TOP" },   # the tile, and which of its edges the light hangs on
]
```
`lights = []` means none. A layout without the key gets lights worked out from its rooms (`WallLightLayout`, in `interior-grid`): each room gets lights on its walls until every floor tile is within 3.5 tiles in front of one, the wall lighting the most dark tiles first, never two within 3 tiles, and never behind a system. The ship editor's **Auto-place lights** writes exactly that into the layout, so it can be adjusted by hand from there, and its **Light** tool hangs or takes down one at a time (see `documentation/ship-editor/Design.md`). The base layouts carry their auto-placed lights. A saved game keeps a deck's lights with its grid. `WallLightLayout.lightsOf(grid)` gives the lights a deck has either way, for anything that draws or lights them.

## Code
All in `core`, package `com.hawkwing.RingsOfIron.lighting`:

| Class | Does |
|---|---|
| `EmissiveLights` | Loads `lights.json`. |
| `DeckLightRig` | Places a deck's lights in its body frame: wall lights at their wall, systems at their mount and rotation, conduits, fires (which flicker). |
| `HullLightRig` | Places a hull's lights in its body frame. |
| `HullArt` | Which chassis (and so which hull art) a craft wears. |
| `LightBarriers` | The deck edges that stop light, as merged segments. |
| `SceneLighting` | Owns the shadow world and the `RayHandler`; each frame takes the decks and hulls in view, draws the light map, then the glow pass. |

Shadows use a Box2D world of their own in world meters, apart from the crew's interior worlds (tile units, one per deck) and the flight world (10 m units): one static body per deck in view, its barrier edges as fixtures, moved to the craft's pose each frame and rebuilt when a door or wall changes, and one small circle per crew member in view, moved onto them each frame. Mounted systems cast no shadows, since their lights sit inside them.

Lighting uses diffuse mode: the light map multiplies the scene, so art under full light shows as drawn. Anything drawn from an emissive layer belongs **after** the light map (between `renderLights` and `renderGlow`), or the light map darkens what should be a light. So after the light map, `DeckArt.glow` draws the `glow/` region (the emissive layer alone) of everything on the deck that is lit: running systems, live conduits, powered doors and wall lights, and fire. A system without power is skipped, so its dark art stays dark.

## Open questions
- Sunlight and shadows outside: a directional light over the flight world would shadow each hull's own art, since box2dlights darkens everything past an occluder's edge.
- Doors carry no power state of their own: their lamps follow the craft's power.
- `extract_lights.py` ignores a transform on the emissive group, so the vertical door art's lamps are listed where the horizontal art has them; the rig takes the lamps' color from the horizontal art for both.
