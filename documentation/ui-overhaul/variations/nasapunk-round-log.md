# Nasa-punk round log

The Nasa-punk artist's share of the nine-variation loop: the Nasa-punk style
(`../nasapunk/style-nasapunk.md`) over each of the three functional specs. Each round draws or
updates the three variations, critiques each, sends the functional findings to the spec's
designer, and carries the rest into the next round. A variation is done when a round finds nothing
meaningful left in it: no claim the game's code contradicts, every spec rule met or a deviation
argued, the style's own rules kept (type sizes, separators, colour semantics), and no collisions
at 1280 x 800.

| Variation | Folder | Spec version drawn |
|---|---|---|
| Nasa-punk × Ship Stations | [`nasapunk-stations/`](nasapunk-stations/README.md) | round 3 |
| Nasa-punk × Context-driven | [`nasapunk-context/`](nasapunk-context/README.md) | version 2 |
| Nasa-punk × Task-driven | [`nasapunk-task/`](nasapunk-task/README.md) | round 1 |

## Round 1 (3 November 2026)

**Drawn:** three screens per variation, nine in all: a quiet helm, a fight at the weapons
console, and trouble on deck, for each spec.

**Findings sent to the designers**, each checked against the game's code:

| Spec | Notes | Outcome |
|---|---|---|
| Ship Stations | 9: the power model (supply and a 2 s store, no demand or brownout), no galley subsystem, shorts reclosing after 45 s, stun countdowns, decoys uncounted, room names, and layout points | All adopted in the spec's rounds 4 and 5, with an audit of the rest (gun modes, no gun heat, ammunition only where limited, a status strip, battle lighting, the camera shift) |
| Context-driven | 12: supply and store, a SYS DOWN caution, decoys, a room naming rule, P2 codes and ranking, zone sizes, the latched master cue, P5 in two lines, insets, the F3 ring as trim | All adopted for version 3 (pending) |
| Task-driven | 14: the weapons loadout, one lead reticle, decoys, the store's flicker, a reactor short as NO POWER, the 5 s reset, a missing VENTING caution, room names, the velocity frame near a station, label rules, the key count, a colour word, zone overlaps | Sent; awaiting the designer's round 2 |

**Found in my own screens**, carried to round 2:

- S2 and C2 draw the corvette's guns wrongly. The game's Patrol Corvette (`ships.toml`) has a
  coilgun in the nose, an autocannon each side, a harpoon launcher aft to port and a laser aft to
  starboard; the autocannons are point defence and stay automatic at the weapons console
  (`PlayerGunnery.inHand`), and the laser reaches 3 km. T2 has it right; S2 and C2 get the same
  rows. S2 also counts decoys (`DECOY 6`), and both draw 56° fire arcs for guns whose arcs are 300°.
- Labels at 9.5 to 10.5 u in all three variations sit under the style guide's 11 and 12, and
  under the task spec's 12-unit floor.
- Thousands are written with commas; the guide says a thin space.
- The style guide promised an abbreviations table with the first skinned spec; it isn't written.
- Room names come from the kit's stand-in deck; each spec now has, or needs, a naming rule.

**Style guide changes this round:** the fixed slots became the style's default layout, with a
spec's own zones kept and mapped (section 9); a console face may stand as a side panel (section
6); a slot's fixed key may be printed on its cap where a spec keeps bindings in view (section 6);
deck hazard tags and prompts are the one boxed thing allowed over the world (section 9).

## Round 2 (next)

- Stations: update to spec rounds 4 and 5 (status strip at y 16, the board's right end, battle
  lighting, the camera shift, gun modes AT WILL / YOURS / TRIGGER, "charging" chips, NO SUIT, one
  impact mark per side with a count), and fix S2's mounts, decoys and arcs.
- Context: update to version 3 once it lands, and fix C2's rack and arcs.
- Task: update to the designer's round 2 answers.
- All three: the type floor, thin-space separators, the abbreviations table, room names by rule.
- Then critique again.
