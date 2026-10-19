# Lighting: Box2D lights from the art's emissive layers

## What it does
- **Inside a craft** (deck view), every glowing part of the art drawn on a deck is a light: each mounted system's lamps, screens and glow, live power conduits, and fires. The bigger parts cast [box2dlights](https://github.com/libgdx/box2dlights) rays, so walls, hull and shut doors throw shadows; the small ones (status lamps, conduit studs) only glow.
- **Power** decides what is lit. A system's lights are on while it runs (`SubsystemComponent.isOperational`), so a wrecked, unpowered or switched-off system goes dark. With no reactor running, the ambient light drops from cabin light to near darkness.
- **Outside**, a hull's emissive layer (navigation lights, bridge glazing, reactor and shield glow) is drawn as a glow on the craft. A light over a system's footprint follows that system; navigation lights follow the craft's power.
- **L** in the deck view turns the lighting off and on.

## Where the lights come from
`tools/art/extract_lights.py` reads the `<g id="emissive">` group of every SVG in `assets/art` and writes `assets/art/lights.json`. Shapes that touch are one light (a lamp's white center, colored body and halo), its color the most saturated one among them (the accent), its size the group's bounding box. Re-run it whenever the art changes; a test fails when `lights.json` and the SVGs disagree about which art glows.

Entries are keyed `<folder>/<name>`, in SVG units with y up and the SVG origin at (0, 0); `canvas` gives the art's bottom-left corner and size, so art placed from its corner (components, tiles) subtracts it. Wall-mounted decor shines as a cone away from its wall (`CONES` in the script).

## Code
All in `core`, package `com.hawkwing.RingsOfIron.lighting`:

| Class | Does |
|---|---|
| `EmissiveLights` | Loads `lights.json`. |
| `DeckLightRig` | Places a deck's lights in its body frame: systems at their mount and rotation, conduits, fires (which flicker). |
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
- Decor is not placed on decks yet, so the wall light's cone is ready but nothing uses it.
