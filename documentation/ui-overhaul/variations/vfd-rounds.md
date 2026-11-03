# FILAMENT round log

Each round, the three FILAMENT variations ([stations](vfd-stations.md), [context](vfd-context.md), [task](vfd-task.md)) are re-mocked against the latest functional specs, critiqued, and improved; functional critiques go to the spec's designer. The loop ends for a variation when no meaningful criticism remains.

| Variation | Spec version used | Status after the latest round |
|---|---|---|
| Ship stations | `functional-stations.md` round 5 | Round 1 done; round 2 open |
| Context-driven | `functional-context.md` version 2 | Round 1 done; round 2 open |
| Task-driven | `functional-task.md` first version | Round 1 done; round 2 open |

## Round 1

**Made.** Three screens per variation: stations (helm cruising, weapons in a fight, deck with fire and missiles), context (helm cruise, helm combat, deck emergency), task (helm at rest, gunnery in combat, deck damage control). Backgrounds are real deck captures and the game's hull art.

**Fixed during the round** (found by rendering the first drafts):

- Zoned bars lit their red and amber feet on every healthy bar, so a ship at 81% hull looked alarmed. Depleting bars are now zinc with notches, and their alarm moved to the row's leading pips; nominal rows sit at L1. The guide's 7.2 and 7.6 now say so.
- Inverse dot matrix (the master cue) was unreadable once bloomed; it became a cut-out legend, and the guide sets a minimum size for inverse text (7.8).
- The deck emergency showed the whole corvette, so its off-screen pointers pointed at things on screen. The deck is zoomed in on the captain.
- The emergency brownout dimmed the caution list; it now dims only the strip and the feed (guide 4.3, spotlight).
- The LCD backlight outshone every alarm; it is a dim teal now (guide 7.11).
- Data the game doesn't have: a decoy count (decoys have a 3 s cooldown), a hit-chance word scale (the game has a probability).
- Layout collisions in the task variation: weapons columns, objective title against its time, the helm status and hint line over the weapons pod, deck tags over lit conduits (scrims, guide 8).
- New icons for the tell-tales: drive, EVA.

**Open for round 2** (all three variations):

1. **Type to the minimums.** The mockups break the guide's own minimums (legends 10, segments 12, body pitch 2) and the specs' (body 13, annunciator 12, values 15). Every module is re-laid at those sizes; where it no longer fits, content is cut by priority, not shrunk.
2. **Inverse text through the bloom.** Cut letters are re-darkened after the bloom so warning tiles, the seat in use and active modes stay readable.
3. Variation items: folded MFD rails and the station board at L1 (stations); H6's state row as one word and the ship's real plan in H4 (context); the caution row hung from a ghost warning window, a narrower weapons table and 24-unit scope keys (task).

**Sent to the designers.** Stations: one annunciator row by default, SHIP callouts that don't restate tiles, a dark station board in Green. Context: keep P2 lit under X3, decoys have no stock, shields in kilograms, D3 never fires on the starter ship, 12.4 against the columns. Task: SCH missing at gunnery, WPN and the hint line overlap, orbit-frame speed at rest, decoys have no stock, cautions keep their row, room names from the chassis.
