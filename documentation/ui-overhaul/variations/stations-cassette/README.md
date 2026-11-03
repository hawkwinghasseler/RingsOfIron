# Ship Stations × Cassette-futurism

The **Ship Stations** functional design ([functional-stations.md](../../functional-stations.md), round 2, branch `claude/project-thread-b5jmze`) built in the **Yard Standard** design language ([style-cassette.md](../../style-cassette.md)).

**Status: round 1.** The critique of this round and what changes next are at the end. The round log for all three Cassette variations is [cassette-round-log.md](../cassette-round-log.md).

The spec decides what is shown, where and when. This variation decides what each element is made of. Where the hardware needs something the spec doesn't say, or the spec asks for something hardware does badly, it is listed under [Adaptations](#adaptations) and was sent to the stations designer.

## The screens

### C. Seated at the helm, cruising (Green)

![Helm, Green: both MFDs sunk into the deck, the cluster pod alone](C-helm-green.jpg)

The dark ship in Cassette is a deck with its tubes put away. Both MFDs have **sunk into the deck**, leaving only their soft-key rails (the spec's folded state, as a pop-up tube that lowers like a pop-up headlight). The cluster pod stands alone. Nothing on the board is lit except the player's own stations. The world carries the prograde and retrograde marks, the burn's hollow triangle with its countdown, and the nav target's bracket, all as amber reflector-sight projection.

### E. Seated at the weapons console, in a fight (Red)

![Weapons console, Red: annunciator down, both tubes up, the tactical cluster](E-weapons-red.jpg)

The overhead annunciator has **dropped down** with its master lamps lit. The intercom repeater hangs under it. The deck's **condition light bar** under the lip glows red, the only place the condition colours the band. Mount chips are small modules: a lamp and a split-flap state, the crew member on a Dymo strip (blue tape for YOU), and a WILL/HELD toggle engraved like a real panel. The right MFD is pinned to DEFENSE: its PIN latch is down and orange.

### B. On deck: fire (crew on it), breach (nobody on it), missiles inbound

![Deck: annunciator, intercom, suit unit, and the deck's own cues](B-deck-alert.jpg)

On deck there is no console in front of the captain, so the only hardware on screen is what hangs overhead and the **suit unit strapped to the captain's forearm** (bottom left). The deck itself does the rest: the reactor and life-support consoles glow red, door lights show fire and vacuum beyond, alarm beacons turn in the galley and cargo 2, and the unattended breach is outlined in red projection. The REACTOR prompt is an engraved nameplate with its key cap.

### G. Docked at Highport, Station link open, seated

![Docked: the station's own terminal patched into the deck](G-docked-link.jpg)

The Station link is **the station's equipment, not the ship's**: a terminal in the Terran Patrol Authority's grey-green livery with a red stripe, plugged into the deck by a visible cable. Its tabs are piano keys (one stays down). The quantity is a three-position rotary (1, 10, ALL), the total is on a drum counter, and CONFIRM is the one lit push-light. Condition stays GREEN and the layered DOCKED state is a lamp beside it, as round 2 of the spec separates them.

## The element kit

How each element in the spec's kit (section 17.5) is built.

| Spec element | Yard Standard object |
|---|---|
| Annunciator row | An overhead strip that hangs from the top edge and drops down when a tile lights; retracted when empty |
| Annunciator tile | A lens with the tag backlit (◆ warning red, ▲ caution amber) over a split-flap window for the value. Raised: lens glowing and flashing. Acknowledged: steady, no glow. Clearing: filament fade, 120 ms. "+n MORE": the last tile's flaps |
| Master lamps | Square push-lights at the ends of the strip; flash at 2 Hz until acknowledged |
| Emergency banner | A hazard-striped placard with a red-phosphor strip tube, dropping above the strip *(round 2)* |
| Warp strip | A strip tube under the annunciator *(round 2)* |
| Callout ticker | The intercom repeater: an amber strip tube hanging from the overhead. Station tag in reverse video, severity mark (◆ ▲) in its own column |
| Prompt line, response | Console prompts are engraved nameplates by the console. Responses are a short strip tube in Z7 *(round 2 mockup)* |
| Suit readout | The forearm unit: drum for suit air (m:ss), needle for room pressure, lamps for O2, FIRE and WOUND |
| Guide card | Out of fiction and looks it: a flat card in Inter with no hardware *(round 2 mockup)* |
| Helm cluster | A pod: a two-line strip tube (Reference, Next event), then VELOCITY (frame on split flaps, speed on drums, unit lamps), DELTA-V (drum, bar with an orange HOME bug and a PLAN bracket), DAY and time on drums |
| Mode chips | Physical state: ASSIST is a latching push-light with an OFF flag when off; the helm order is a split-flap word; THR and RCS are lamps |
| Docking approach | Closing speed against its limit as a bar with a red-line zone; alignment on two centre-zero needles *(round 2 mockup)* |
| Mount chip | Module: name, crew on Dymo tape, lamp and split-flap state, WILL/HELD (or AUTO/YOURS) toggle |
| Launcher, decoys | Drum counters with READY lamps and key caps |
| Shield | Mode on split flaps, mass on an edgewise bar, trim on a rose knob pointing BOW, PORT, STERN or STBD |
| MFD frame | A pop-up tube in a pod; the title is the tube's reverse-video top row |
| Soft keys | Bakelite keys whose faces carry split-flap legends, because the page names change with the station on show |
| Pin | A latching key: down and orange when pinned |
| Folded MFD | The tube sinks into the deck; the rail stays |
| Dead screen | The tube goes to unlit grey glass, with "NO SIGNAL" stencilled on the bezel |
| Radar scope, ship plan, shield ring | Green geometry on the same tube as amber words: a beam-penetration tube, so one MFD can show both |
| Station board tile | A lens with the station's number and name, and the occupant beside it. Lit white: you. Unlit with a name: crewed. Unlit with a dash: empty. Amber or red lit: caution or warning. A barber-pole bar across: struck |
| Condition word | A drum flag at the board's right end (GREEN, YELLOW, RED), with the DOCKED and EMERG state lamp beside it, and the light bar under the deck's lip |
| Side panel | A terminal in the owner's livery: the ship's own panels in enamel, a station's in its faction's colours, patched in by cable |
| Tabs | Piano keys, one down |
| Quantity control | A detented rotary |
| World symbology | Amber reflector-sight projection with glow; threats in red; shape carries identity (style guide 7.2) |
| In-world cues | Console screens glow in the alert's colour; door lights; alarm beacons with turning light cones; stencilled room names on the floor |

## Adaptations

Places where the hardware changes the spec's geometry or behaviour. These went to the stations designer as round 1 notes.

1. **The band is flush with the bottom edge.** Same 184 units, but at y 616..800 instead of y 600..784 with a 16 margin below. Hardware doesn't float.
2. **Rows don't fold, they go dark.** A cluster row with nothing to show keeps its place with its tube line dark, its needle parked or its drum flagged OFF. The layout never jumps, and dark is still the "nominal is silent" the spec wants.
3. **The unlit master lamps show whenever the strip is down.** A dark lens reads as "no warning". When nothing is lit at all, the whole strip retracts.
4. **The ticker is fixed under where the annunciator hangs**, whether or not the annunciator is down, so it never jumps when the first tile arrives.
5. **MFD fold is mechanical.** The tube sinks in 300 ms and rises in 300 ms. An auto-page raises it at once with no animation, because a warning shouldn't wait for a motor.

## Round 1 critique

What this round got wrong or left weak, in the style and in the combination.

**Style**

- The **world labels and the tube text are the same face and colour.** On space they read well, but the nav target's label and the cluster's Reference line look like the same object in two places. Round 2: world labels get a lighter weight and no glow halo beyond the projection colour, so the projection reads as light on glass and the tube as a screen.
- The **burn marker** (hollow triangle with a tail) reads as a pennant at a glance. Round 2: a hollow triangle with a countdown flag, apex along the burn direction, matching the PLAN page's symbol.
- In round 1 the **station board lit crewed stations green**, which broke the dark ship. Fixed during the round: crewed stations are unlit, and only YOU, cautions and warnings light.
- The **station link's tube has dead space** below six commodities. The real list is short (water, food, ore, steel, machinery, fuel), so the tube should be shorter and the trade line should sit closer to the rows.
- The **quantity rotary's legends crowd its label.** Round 2 spaces them.
- **Missing from the kit:** the emergency banner, warp strip, response line, guide card, docking approach, dead screen and gunner sight have no mockup yet. Round 2 draws D (docking approach), H (warp) and the emergency banner.

**Combination (sent to the designer)**

- The **same readout appears twice** while seated. The nav target's world label (name, range, closing) repeats the helm Reference row. In E the target label repeats the Target strip, and the missile countdown appears three times: tile, world label and SENSORS list. Fixing this is the spec's call (its rule 3.8).
- The **Reference row has three possible contents** (the reference, the pilot's trigger, live Ap and Pe) but fits one at 13 units. The spec needs a priority order, or the trigger needs its own row.
- **"No helm order"** has no defined label. Cassette shows MANUAL, dimmed.
- The **docked berth is in two places**: the cluster's Docked line and the DOCK page.
- **Where the layered states show** (DOCKED, EMERG) on the station board isn't defined. Cassette puts a lamp beside the condition flag.

## Files

- `mockups.html`: the four screens, drawn with the shared kit in `../../cassette/kit.js`.
- Render: `NODE_PATH=$(npm root -g) node ../../cassette/render.js mockups.html 1.5 jpeg`.
