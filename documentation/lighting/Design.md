# Lighting: Box2D lights from the art's emissive layers

## What it does
- **Inside a craft** (deck view), most of a room's light comes from **wall lights** (`decor/wall_light`), each shining a cone into the room from its wall while the craft has power. Every glowing part of the other art drawn on a deck is a light too: each mounted system's lamps, screens and glow, live power conduits, and fires. Wall lights and the bigger parts cast [box2dlights](https://github.com/libgdx/box2dlights) rays, so walls, hull and shut doors throw shadows; the small ones (status lamps, conduit studs) only glow.
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
| `data-light` | `glow` (lights nothing around it), `point` or `cone` | Judged by size: parts of at least 8 SVG units light the room, at most the 3 biggest of a system; the rest only glow |
| `data-reach` | How far its light reaches, tiles (meters outside) | 0.6 + 2.2 per tile of the part's size, up to 7 |
| `data-intensity` | How strong it is, 0-1 | 0.35 + 0.65 x the part's opacity |
| `data-direction` | A cone's direction, degrees, 0 = +x, 90 = up the canvas | -90 (away from a wall at the top) |
| `data-cone` | A cone's half-angle, degrees | 45 |

So a designer tunes a light where it is drawn, and the extractor copies the settings into `lights.json`. Until the art carries them, `DEFAULTS` in the script gives the same settings by art name (the wall light: a 60° cone reaching 5 tiles at 0.85); the SVG's own attributes win. The game turns the settings into a light the same way everywhere, and the room's ambient light (`SceneLighting`) sets how dark the unlit corners are.

## Wall lights
A wall light hangs on one edge of a floor tile, shining into that tile; it takes no room, so the tile stays free for a system, a door or anything else. Its art's top edge sits against the wall, and the art turns with the wall it hangs on.

A layout lists them in `chassis.toml`:
```toml
lights = [
  { x = 3, y = 2, side = "TOP" },   # the tile, and which of its edges the light hangs on
]
```
`lights = []` means none. A layout without the key gets lights worked out from its rooms (`WallLightLayout`, in `interior-grid`): each room gets lights on its walls until every floor tile is within 3.5 tiles in front of one, the wall lighting the most dark tiles first, never two within 3 tiles, and never behind a system. The ship editor's **Auto-place lights** writes exactly that into the layout, so it can be adjusted by hand from there, and its **Light** tool hangs or takes down one at a time (see `documentation/ship-editor/Design.md`). The base layouts carry their auto-placed lights. A saved game keeps a deck's lights with its grid.

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

Shadows use a Box2D world of their own in world meters, apart from the crew's interior worlds (tile units, one per deck) and the flight world (10 m units): one static body per deck in view, its barrier edges as fixtures, moved to the craft's pose each frame and rebuilt when a door or wall changes. Mounted systems cast no shadows, since their lights sit inside them.

Lighting uses diffuse mode: the light map multiplies the scene, so art under full light shows as drawn. Anything drawn from an emissive layer belongs **after** the light map (between `renderLights` and `renderGlow`), or the light map darkens what should be a light.

## Open questions
- Sunlight and shadows outside: a directional light over the flight world would shadow each hull's own art, since box2dlights darkens everything past an occluder's edge.
- Crew do not cast shadows.
- Bulkhead door lamps are not lit yet; doors carry no power state of their own.
- The wall light's art is not drawn on decks yet: its light shines, and drawing the fitting itself at each `grid.wallLights` spot belongs with the deck art.
