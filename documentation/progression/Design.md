# Progression Design: Factions, Rank, Contracts and Rescue

## Overview
The simulation under Rings of Iron is deep (flight, orbits, traffic, the economy, decks, crew, combat damage, docking and yards), but on its own it is not a game. This document covers what turns it into one: the player serves a **sponsoring faction**, earns **standing** and **rank** by doing **contracts** and fighting that faction's enemies, draws a **budget** from that rank, and when a ship is lost is **rescued** and **requisitions** another rather than starting over.

The loop it describes:

**take a contract → fly → fight or trade → win, board and salvage, or lose the ship and be rescued → get paid, gain standing and rank → refit → take a bigger contract.**

Sources: the GDD §4 (mission boards driven by the economy) and §5 (sponsoring factions, rank and budget, net-worth scaling, survival over death), Technical Design Decisions §6 (rescue and requisition), and the round-2 plan, which splits the work into categories 1 (factions, rank and budget), 3 (disabled ships, abandon ship and rescue), 6 (contracts) and 7 (boarding and salvage), tied together by category 9 (the playable loop).

This is a living document. Each section says what is built; where nothing is yet, it records the design the round-2 work is building to, and the defaults chosen while Joe was away. When a category lands, its section moves from **Planned** to **Implemented** in the same shape as the module docs.

## Implementation Dependencies
- **`core`** (`com.hawkwing.RingsOfIron.game`): `FactionStanding` and `FactionStandingComponent` today; the faction model, rank ladder, budget, contracts and rescue flow will sit here beside them, since they read several modules at once.
- **`modding-data`**: faction, rank and contract tables in `assets/data`, loaded like ships and chassis.
- **`macro-sim`**: station ownership (each `EconomyNode`'s faction), `TradeOrder`s that delivery contracts are drawn from, the sim clock that pays the budget, and pirate and patrol traffic on the rails.
- **`stations-docking`**: clearance (refused below a standing), the docked windows a mission board sits beside, and where a rescued player wakes.
- **`ai-crew`**: crew hostility by faction, surrender, escape pods and boarding parties.
- **`combat-sys`**: who disabled or destroyed what, for standing and for bounty contracts.
- **`ui-hud`**: the standing and rank readout, budget, contract tracker and waypoints.
- **`core-engine`**: saving all of the above with the game.

## What Exists Today
From round 1 (`stations-docking` stage 4, see `documentation/stations-docking/Design.md`):
- **One faction of stations.** Every station's crew share one faction, `StationCrew.FACTION`; ships carry theirs on their task board (`ShipTaskBoardComponent.faction`). `FactionStanding.factionOf` gives a craft's faction, -1 for a craft of no one's.
- **Standing from forced doors only.** Forcing a docked craft's door costs standing with its faction: 10 for prying or hacking, 25 for cutting. Standing runs from -100 to 100. Below -15 the faction's stations refuse docking clearance; at -50 or below its crew fight the player's.
- **Kept on the ship.** Standing is stored in a `FactionStandingComponent` on the player's ship and saved with it. Rescue and requisition replace the ship, so the standing has to move to something that outlives it (the player, or the game); see Open Questions.
- **Credits** live on the ship's `TraderComponent` and come only from trading (`MarketTrade`, `MarketCargo`). Yards charge for repairs, refits and refills from the same purse.
- **Losing** is only crashing into a body: "SHIP LOST", and F9 reloads. A ship shot to pieces drifts on.

Nothing yet exists of ranks, budgets, contracts, a mission board, disabled ships, escape pods, rescue or requisition.

## Factions (planned, category 1)
- Factions are data: a `[faction.<id>]` table in `assets/data` with a name, a color, the stations it owns, and the factions it is at war with.
- Stations take their faction from that data instead of all sharing one; `StationCrew` and `FactionStanding` read it per station.
- Standing is kept per faction for the player.
- Three to start: the player's sponsor (owns Highport and the depot), a rival power (owns the lunar mine), and pirates (own nothing and are hostile to everyone). The sponsor is fixed for now; choosing one could become a new-game option in the main menu.

## Standing (planned, category 1)
- `FactionStanding.reward` and `penalize` are the one way contracts, combat and boarding change standing.
- Killing or disabling a faction's ship costs standing with it and gains standing with its enemies.
- Forcing doors keeps its round-1 costs and thresholds.
- Boarding a pirate costs nothing; boarding a faction's ship costs standing with it (category 7).

## Rank and Budget (planned, category 1)
- A rank ladder as data: each rank names the standing with the sponsor it needs, the budget it pays per in-game week, and the largest chassis it may requisition.
- Promotion happens when standing with the sponsor crosses the next rank's threshold, with a message on screen.
- The budget is paid into the player's credits on a timer from the sim clock and shown on the HUD.
- **Difficulty** (GDD §5): a value from rank and net worth (credits plus the ship's value) that sizes pirate encounters (category 2). Until category 1 lands it is a constant.
- An old save loads as rank 0 with the sponsor faction.

## Contracts and the Mission Board (planned, category 6)
- **Types:** cargo delivery (drawn from the economy's real `TradeOrder`s, so taking one takes that order from the freighters), escorting a freighter to its next station, a bounty on a named pirate, and patrolling a lane.
- Each has a giver faction, a reward, a deadline, and the standing gained on success.
- **Board:** a mission window at a docked station beside trade and the yard. Offers are limited by rank and by standing with that station's faction; up to five per station, refreshed each in-game day.
- **Tracking:** active contracts on the HUD with a waypoint marker (`HudMarkerComponent`) and on the traffic map.
- **Completion** is read from the world: the cargo delivered, the escorted freighter docked, the target disabled or destroyed. Success pays credits and standing; failing or abandoning a contract costs half the standing it would have earned.
- Saved with the game.

## Disabled Ships, Abandon Ship and Rescue (planned, category 3)
- **Disabled:** a ship with no running reactor, no conscious crew, or hull integrity below a threshold gets a `DisabledComponent` with the reason. Its NPC pilot and gunners stop and the HUD marks it. Further damage can break it into a wreck, which stays as a salvage target.
- **Surrender:** an NPC ship that is losing (below a hull or crew threshold) may cut thrust and surrender instead of fighting on.
- **Abandon ship:** escape pods are a grid component, one 4-person pod per corvette and two per freighter. The crew are sent to the pods (by the player or an order from the bridge); whoever reaches one escapes. Crew who don't are lost.
- **Rescue:** after a 3-day time skip the player wakes at the nearest station of their sponsor.
- **Requisition:** there, a new ship of the best chassis the player's rank allows, paid from budget or credits. A ship of the starter class is always free, so the player is never stuck.
- **Crashing** stays a loss but goes through the same rescue when the crew got away in time.

## Boarding and Salvage (planned, category 7)
Mostly a combat and crew matter, built from round-1 pieces (ship-to-ship latching from category 4, door forcing, crew combat across a dock, crates carried by loaders). The progression side:
- A taken ship's crew surrender and are held.
- Salvaged parts come home as `part:<component>` crates and sell at a quarter of their price; a salvaged weapon can be fitted at a yard like a used one.
- Standing as above.

## Saves
Everything here is saved with the game through core-engine's save format: standing per faction, rank, the budget timer, the contracts on offer and in hand, disabled and surrendered state, and wrecks. An old round-1 save loads with rank 0, the sponsor faction, no contracts, and its forced-door standing kept.

The round-2 test plan includes one save and load round trip holding all of these at once: an active contract, a disabled ship, a boarded ship and a requisitioned ship, in the style of `FullSaveRoundTripTest`.

## Open Questions
- **Where standing lives.** Round 1 keeps it on the player's ship. Rescue replaces the ship, so standing, rank, budget and contracts need a home that outlives it (a player or captain entity, or game-level state). Category 1 or 3 decides; this document will record which.
- **Choosing a sponsor** at the start of a new game, and whether the player can ever change sides.
- **Standing recovering** over time, or being bought back (also open in `stations-docking`).
- **What a rescue costs** beyond the lost ship and crew: lost cargo, a standing penalty, a fee.
- **Towing** a taken ship home instead of stripping it.

## Stages
| Stage | What | Category | State |
|---|---|---|---|
| 1 | Factions as data, per-station ownership, standing per faction, the standing API | 1 | Planned |
| 2 | Rank ladder, promotions, budget, difficulty | 1 | Planned |
| 3 | Disabled state and NPC surrender | 3 | Planned |
| 4 | Escape pods, abandon ship, rescue and requisition | 3 | Planned |
| 5 | Contracts and the mission board | 6 | Planned |
| 6 | Boarding, taking ships and salvage | 7 | Planned |
| 7 | The loop in the main game, scripted end to end | 9 | Planned |
