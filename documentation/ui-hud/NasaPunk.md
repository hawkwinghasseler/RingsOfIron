# Nasa-punk HUDs

The three Nasa-punk HUD variants (`nasapunk.task`, `nasapunk.stations`, `nasapunk.context`) are drawn
with one kit in `ui-hud/.../variants/nasapunk`: `NpKit` holds the fonts (B612) and `NpCanvas`, which
draws the mockups' shapes, text and hardware (`NpHardware`: bezels, fasteners, glass, plates, soft
keys) in UI units with y down, so a number in the mockups (`documentation/ui-overhaul/nasapunk/mockups`)
is the same number in the code. `NpColors` is the palette.

## The menus and the map

The three HUDs give the game's menus and its map one look between them, through the shared screens
hook (`Variants.md`): `NpScreens` holds what each hands on, and frees it on `detach`. The mockups are
`documentation/ui-overhaul/variations/nasapunk-shell` (the menus) and the map screens C5, T4 and S4.

**The menus** (`NpShellLook`) are pages of one flight computer, CDU-1. Before a game the unit sits in
a console with the game's name engraved on it, over the stars and the Earth's limb; over a paused
game the deck keeps 40 per cent of its light. Each menu keeps the game's rows, buttons, keys and
order: it is built from `NpShellSkin`, whose styles carry the classic names and draw the computer's
parts (`NpDrawable` paints each through the canvas), and `dress` lays the built page out as the
computer's, with `NpShellFrame` drawing the furniture behind the rows:

| The game's | On CDU-1 |
|---|---|
| Menu | A page: the unit (`NpCdu`) with FLIGHT OPERATIONS on its bezel, the title at the top of the glass, the build on its plate as the part number |
| Button | A prompt, `<` and its word; on the main and pause menus down the glass's left edge, a line-select key either side of each, the left one pressing it when clicked |
| A button that cannot be used | Its word dim, with no caret |
| Focus | The cyan cursor box round the field (the game's focus tint is read, and kept off the words) |
| Held down | Reverse video, the field shaded |
| One of a group (a sponsor) | Dim, cyan when picked |
| A section's name (`title`) | Between dashed dividers |
| Dim line | A label line, under the prompt's words |
| Text field, choice, list | Entry fields on a shade over the glass: typed text cyan, the choice cyan with a caret, a list's pick in the cursor |
| Check box, slider | A green square lit or a hollow one; a white bar with a soft key for its knob |
| The message line | The scratchpad along the glass's foot, over a rule, with a dim hint of the keys while it is empty |
| Mod scripts dialog | A page of its own, its title at the top of the glass |

The fonts carry the capitals and the letter spacing themselves (`NpFonts.create` with caps and
track), so Scene2D measures each word as it is drawn. A page too tall for the window (the settings
in an 800-line window) closes its rows up until it fits.

**The map** (`NpMapPainter`) is the system plot: glass, the orbit now white, the planned path magenta,
a proposed one white and dashed until it is taken, burns as diamonds (the selected one cyan, with
cyan handles), the Earth a dark disc ringed blue, a moon grey, contacts in their affiliation's shapes
and the target a magenta ring. The title and the row of keys sit over the canvas's top-left, the keys
on caps; a body or the target pulled in to the map's top edge comes down clear of them, its name
with it. Circles far bigger than the canvas are drawn only where they cross it.

`tools/playtest/shell-nasapunk.sh <task|stations|context>` drives the menus, a paused game, the map
and the mod scripts dialog in a real window.
