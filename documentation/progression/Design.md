# Progression Design: Factions, Rank, Contracts and Rescue

## Overview
The simulation under Rings of Iron is deep (flight, orbits, traffic, the economy, decks, crew, combat damage, docking and yards), but on its own it is not a game. This document covers what turns it into one. The player serves a **sponsoring faction**, earns **standing** and **rank** by doing **contracts** and fighting that faction's enemies, and draws a **budget** from that rank. A beaten enemy can be **boarded and stripped**. When the player's own ship is lost, they are **rescued** and **requisition** another rather than starting over.

The loop it describes:

**take a contract → fly → fight or trade → win, board and salvage, or lose the ship and be rescued → get paid, gain standing and rank → refit → take a bigger contract.**

Sources: the GDD §4 (mission boards driven by the economy), §3 (boarding and salvage) and §5 (sponsoring factions, rank and budget, net-worth scaling, survival over death), Technical Design Decisions §6 (rescue and requisition), and the round-2 plan (`planning/next-round-plan.md` in the project files).

Each part was built on its own round-2 branch:

| Part | Branch | PR |
|---|---|---|
| Factions, standing, rank, budget, difficulty | `claude/r2-factions-vuy5b6` | #87 |
| Pirates and patrols (they read standing and difficulty) | `claude/r2-roster-traffic-z8x44s` | #93 |
| Disabled ships, surrender, abandon ship, rescue | `claude/r2-disabled-rescue-u30h20` | #88 |
| Contracts and the mission board | `claude/r2-contracts-qf9mvv` | #91 |
| Boarding, taking ships and salvage | `claude/r2-boarding-salvage-mt3uei` | #92 |
| Wiring them into one loop | `claude/r2-playable-loop-blznr1` | the loop's draft PR |

All numbers below are the branches' placeholders, marked in the data and code for tuning in the playable loop.

## Implementation Dependencies
- **`core`** (`com.hawkwing.RingsOfIron.game`): everything here lives in the game layer, because it reads several modules at once: `Factions`, `RankLadder`, `ServiceRecord`, `FactionStanding`, `ServiceBudget`, `Difficulty`, `ContractBoard`, `ShipDisablement`, `AbandonShip`, `Rescue`, `Boarding` and `Salvage`.
- **`modding-data`**: `FactionDef` and `RankDef` (`assets/data/factions.toml`), and the `escape_pod` component.
- **`macro-sim`**: station ownership by name, the `TradeOrder`s that deliveries are drawn from, freighters for escorts, pirate and patrol traffic (`HostileTrafficController`), and the sim clock that pays the budget.
- **`stations-docking`**: clearance refused below a standing, ship-to-ship latching for boarding, and docking in place after a rescue.
- **`ai-crew`**: crew hostility by faction, `EvacuateTask` and `BoardTask`, held prisoners.
- **`combat-sys`**: the damage that disables a ship, and the firepower ratings pirates compare.
- **`ui-hud`**: the rank and budget line, the mission window (J), the boarding window (K), contract waypoints and greyed-out disabled markers.
- **`core-engine`**: saving the career and contracts as their own save modules, and everything else as components.

## Factions
Factions are data: `[faction.<id>]` in `assets/data/factions.toml` (`FactionDef`), with `name`, `color`, `sponsor`, `stations` (station names), `enemies` and `hostile_to_all`. `DefValidator` warns about an enemy id that doesn't exist.

| Id | Name | Owns | Notes |
|---|---|---|---|
| `patrol` | Terran Patrol Authority | Highport, Deepwell Depot | The sponsor; enemies: pirates |
| `selene` | Selene Mining Combine | Shackleton Ice | |
| `pirates` | Void Raiders | nothing | Hostile to all |

`Factions` turns ids into the crew-faction numbers that ai-crew's task boards use. The sponsor keeps 2 (`StationCrew.FACTION`), the others follow in id order, and 0 is the player's crew. A station that no faction lists belongs to the sponsor. Each station's crew get its owner's number (`StationCrew` overloads), and the scripted First Play raider flies for the pirates. Two factions are at war if either lists the other in `enemies`, or either is hostile to all. With no faction data, `Factions.none()` gives one sponsor, "Station authority", owning every station, as in round 1.

## Standing
The player's career is a `ServiceRecord`, saved as its own save module (`"factions"`) rather than on the ship, so it outlives a ship lost to rescue. It holds the sponsor's id, the rank, and standing per faction id, which is clamped to -100..100.

- **Changing it:** `FactionStanding.reward` and `penalize` (by faction id or number) are the one way contracts, combat and boarding change standing. `hostileAct` costs standing with the victim and gives each faction sworn against it half that (`ENEMY_SHARE`). Sworn means listed in `enemies` either way (`Factions.swornEnemiesOf`): being at war with a `hostile_to_all` faction is not enough, so the Patrol, which lists the pirates, gains from beating them and the Selene Combine does not.
- **Hostile acts:**

  | Act | Standing cost |
  |---|---|
  | Prying or hacking a door | 10 |
  | Cutting a door | 25 |
  | Destroying a faction's ship (`onShipDestroyed`, `KILL_COST`) | 30, and 5 to its sworn enemies (`KILL_SHARE`, a sixth) |
  | Boarding a faction's ship (`Boarding.STANDING_COST`) | 20 |
- **Thresholds:** below -15 the faction's stations refuse clearance, with the reason from the last grievance ("you forced their doors") or "your standing with X is too low". At -50 or below its crew fight the player's (`FactionStanding.hostile`).
- **Crew hostility:** factions at war fight each other, and a hostile-to-all faction fights everyone. A known faction and an unaligned crew don't fight; two unaligned crews always do. Held prisoners fight no one.
- **Old saves:** a save without the `"factions"` module loads as rank 0 with the sponsor. `migrateLegacy` folds round 1's `FactionStandingComponent` from the player's ship into the record and removes it.

## Rank and Budget
Ranks are data: `[rank.<id>]` (`RankDef`) with `name`, `order`, `standing` (needed with the sponsor), `budget` (credits per in-game week) and `max_chassis`.

| Rank | Standing | Budget (cr/week) | Largest chassis |
|---|---|---|---|
| Ensign | 0 | 2,000 | corvette |
| Lieutenant | 15 | 5,000 | corvette |
| Lieutenant Commander | 35 | 10,000 | gunship |
| Commander | 60 | 20,000 | frigate |
| Captain | 85 | 40,000 | frigate |

- **Promotion:** only standing with the sponsor promotes. `checkPromotion` climbs one rank at a time with a notice ("Promoted to Lieutenant of the Terran Patrol Authority: budget 5,000 cr a week"). Ranks are never taken back.
- **Budget:** `ServiceBudget` pays the budget into the player ship's credits every `WEEK` (604,800 sim seconds). It catches up on every payday passed during time warp, at the rank held when paying. The first payday is a week after the career starts. Paydays wait while the player has no ship with credits. The HUD shows "Ensign   Budget 2,000 cr/wk in 6d 23h".
- **What a rank flies:** `RankLadder.mayFly` compares a ship's chassis size in tiles with the rank's `max_chassis`, for a requisition after a rescue (`mayRequisition`) and a purchase at a yard alike. Sizes: light raider 75, corvette 96, gunship 160, frigate 240, freighter 260, so no rank flies a freighter. (Round 2 named the freighter at Lieutenant Commander and above, which let the gunship in by size alone; round 3 names the gunship and the frigate.)
- **Difficulty** (GDD §5): `Difficulty.of(rank, worth) = 1 + 0.25·rank + 0.5·log2(worth / 300,000)`, where the worth term counts only above 300,000. Net worth is credits plus the ship's value: 1,000 a hull tile plus its weapons' prices.
- **Pirate numbers:** `HostileTraffic` sizes the pirate population from a difficulty value: `1 + round(d)` raiders and `floor(d)` gunships.

## Pirates and Patrols
Hostile traffic (`claude/r2-roster-traffic-z8x44s`) is what the player fights. It is documented with macro-sim, since it lives on the rails like freighters. The progression side:
- **Pirates** fly for the Void Raiders. They lie in wait near trade lanes and rob loaded freighters. A pirate hunts the player only when it is bold enough: firepower at least 0.8 of the player's, and only after the first 6 hours of a game. A pirate breaks off below 65% of its hull, before it is beaten badly enough to surrender (55%), so a losing pirate runs first and gives up only if it is caught and hit again.
- **Patrols** fly for the sponsor and for Selene between their stations and engage pirates. A pirate won't strike at a station guarded by an equal or stronger patrol.
- **Warp:** a hostile moving to intercept the player stops time warp.

## Contracts and the Mission Board
`ContractBoard` keeps each station's offers, the player's active contracts and a history of the last 20. It is saved as its own module (`"contracts"`). J opens the mission window while docked at a station, or anywhere while holding a contract.

- **Offers:**
  - A station posts up to 5 offers a day, rebuilt on the first look after each day boundary and seeded by station and day, so a day's board is stable.
  - The giver is the station's owner. A station of a hostile-to-all faction, or with no market, posts nothing.
  - The four kinds are spread round-robin, and at least one offer is always tier 0.
- **Locks:** tier 0 is open to anyone. For the sponsor, tier *n* needs rank *n*. For another faction it needs standing of 20 × tier with that faction.
- **Holding:** the player may hold 3 contracts at once.

| Type | Taken from | Reward | Standing | Deadline | Done when |
|---|---|---|---|---|---|
| Delivery | An unclaimed trade order out of the station, claimed in the player's name (`claimedBy = "player"`) | 15% of the cargo's value (at least 500) on top of the sale, with the sale paid at least the agreed price | 3 + 2·tier | 2 days, or 8 if the buyer orbits another body | The goods are sold at the buyer, docked with clearance; partial deliveries count up |
| Escort | A trade order already claimed by a freighter | 1,500 | 4 + 2·tier | 2 or 8 days after the freighter's arrival | The freighter arrives with the player within 50 km of its destination; it fails if the player is not there within 30 minutes of its arrival, or if the freighter is lost |
| Bounty | A ship of a faction at war with the giver | 5,000 × (1 + tier) × difficulty | 6 + 3·tier | 3 days | The target is gone, or beaten (`FirstPlay.beaten`: disabled, reactor out or guns silenced) |
| Patrol | Another station whose owner is not at war with the giver | 1,000, ×1.5 for another faction's station | 3 + 2·tier | 2 days, or 10 if far | The player passes within 20 km of the station, then comes back within 20 km of the giver's |

- **Far jobs:** a job to a station orbiting another body is tier 1 and pays 3×.
- **Bounty tiers:** by the target's value against the player's ship (≤ 1.2×, ≤ 2×, more).
- **Endings:**
  - Success pays the credits and `reward`s the giver with the standing.
  - Failure or abandoning a contract costs half that standing (`FAIL_SHARE`).
  - A job the world takes away (the order or target station gone) is cancelled at no cost.
  - A delivery that ends gives its trade order back to the economy.
- **Tracking:** a held contract is drawn under the dock readout. Its waypoint gets a gold " [contract]" HUD marker and a gold ring on the traffic map. On a patrol's return leg the waypoint is the home station.
- **Old saves:** a save without the `"contracts"` module loads with nothing on offer or held.

## Disabled Ships
`ShipDisablement` judges every ship (not stations) every half second and marks a disabled one with a `DisabledComponent` holding the reason and when it started. Checks run in this order:

| Reason | When | Lasts |
|---|---|---|
| WRECKED | Mean hull integrity below 0.25 | For good; reactors switched off |
| (any sticky reason) | Already SURRENDERED, ABANDONED or WRECKED | Stays |
| HULL | Mean hull integrity below 0.45 | Until repaired |
| NO_POWER | Has reactors and none is running | Until one runs |
| NO_CREW | Own crew aboard, none conscious | Until one wakes |
| SURRENDERED | An NPC losing its fight: hull below 0.55, fewer than half its crew conscious, or every gun silenced | For good |

- **What happens to a disabled NPC:** its pilot and gunnery stop, its weapons go cold, and its HUD marker turns grey with the reason ("Raider (reactor down)"). Recovering puts all of that back.
- **Who surrenders:** the player's ship never does. An NPC's `MoraleComponent` can turn surrender off or change its thresholds.
- **What stays:** disabled ships and wrecks stay in the world as targets for boarding and salvage.
- **First Play:** a disabled raider counts as beaten.

## Abandon Ship
- **Escape pods:** `ESCAPE_POD` is a walkable grid component, `B` in chassis maps. Its seats are 4, 8, 20 or 48 for sizes S to XL. The corvette carries one Small pod and the freighter two.
- **Ordering it:** P asks for confirmation, and a second P within 3 seconds orders abandon ship (`AbandonShip.order`). It is refused while docked ("walk off instead"), with no usable pod, or with nobody aboard. P again while abandoning launches the pods now.
- **Getting to the pods:** the order posts an `EvacuateTask` (priority 1000) per seat, and every crew member drops what they are doing for it. The avatar is walked to a pod unless the player steers.
- **Launch:** a pod launches when it is full, when everyone is in a pod, or 30 seconds after the order. A ship with none of its own crew left aboard becomes ABANDONED.

## Rescue and Requisition
When the player's pod launches, `Rescue` takes over:

1. Every other pod launches. The old ship becomes ABANDONED, loses the player's controls, and is marked "Your old ship (abandoned)". Crew left aboard are lost.
2. **Time skip:** 3 days on the rails.
3. **Where the player wakes:** the nearest sponsor station (`Rescue.sponsorStation`; for now any station counts).
4. **The new ship** is requisitioned there and latched to a free port of its size (`Rescue.requisition`). The starter corvette is always free and is the fallback whenever the player can't pay. Rank limits through `mayRequisition` are for the loop to wire.
5. **What carries over:** the escaped crew come aboard the new ship with the player's avatar, and the credits move to it. Cargo is lost.
6. **Standing, rank and contracts** are save modules, not on the ship, so they carry over once the factions branch is combined. On the rescue branch alone, the old ship kept its round-1 standing component.

A notice tells the player where they woke and how long they were out.

**Crashing** still ends the game ("SHIP LOST", F9 to reload); the rescue path does not yet cover it.

## Boarding and Salvage
Boarding is crew work across a ship-to-ship dock: the corvette's port is active and can latch onto another ship's port (`claude/r2-bridge-helm-1buzuu`). The details are in `ai-crew` and `stations-docking`. The progression side:

- **Boarding:** only a disabled ship can be boarded (`Boarding.refusal`: "disable it first"). K opens the boarding window, and Send orders a party of marines (or crewmen if there are no marines) across. They sweep the ship room by room, forcing a shut door first if needed.
- **Taking a ship:** the ship is taken once the party is aboard and no defenders are standing, or at once if it had surrendered. Its crew are held as prisoners (`CrewMemberComponent.held`): they neither fight nor work. An undefended disabled ship is taken the first time the player marks something on it.
- **Standing:** boarding a faction's ship costs 20 standing with it once (`Boarding.STANDING_COST`), on top of any forced door. Boarding a pirate is meant to cost nothing, through the `outlaw` test, which is for the loop to wire.
- **Salvage:**
  - From the boarding window, each component can be marked to strip. Engineers strip it at one unit of work per mount tile, and loaders carry it home as a `part:<component>` crate (1 m³, 250 kg) to the player's hold. "Take all" carries cargo across too.
  - A stripped system is dead for good.
- **Selling salvage:** stations buy parts at a twentieth of their price (`Salvage.SALE_SHARE`), listed as "Salvaged ..." in the trade window. Station markets never sell parts. A stripped laser sells for 2,250 cr, under half the first bounty, and the whole First Play raider for about two bounties (at a quarter, one laser paid 11,250 cr, over twice the bounty).
- **Refitting with salvage:** a salvaged weapon can be fitted at a yard for free, like a used one, from the yard's "Salvaged" button.

## The Loop in the Main Game
`LoopWiring` (core) points the hooks each branch left at the career:
- **Encounters** scale with `Difficulty.of(world)` (`HostileTraffic.difficulty`).
- **Outlaws:** ships of a faction `hostile_to_all` cost nothing to board (`Boarding.outlaw`).
- **Rescue** wakes the player at the nearest station the sponsor owns (`Rescue.sponsorStation`), in the best ship of `LoopWiring.REQUISITION` (the gunship, then the starter corvette) that `RankLadder.mayRequisition` allows, free. Freighters are never requisitioned. The new ship gets a helm and the player is seated at it.
- **Ships beaten:** a ship disabled, surrendered or wrecked within 120 s of the player's last hit on it counts once as destroyed (`FactionStanding.onShipDestroyed`): 30 standing off its owners, 5 to each faction sworn against them. Beating a pirate raises standing with the sponsor only.

A new game (the main menu's New game, and First Play) starts docked at Highport as an ensign of the sponsor (`GameScenario.beginCareer`). The raider outside flies `corvette_raider`: the player's corvette refitted by pirates, a chaingun where the railgun was, with the laser, launcher and point defense kept. As the player's twin it won or lost on who fired first. The board's first bounty is on the raider outside: bounties on ships in the bubble are offered before those on pirates out on the rails. First Play's steps are: take the bounty, undock, fly clear, beat the raider, board it, dock, repair.

`PlayableLoopTest` plays the loop headless: take the bounty, undock, fight the raider with the real guns and missiles until it gives up, board it, strip a gun, dock and sell it, and come away 11 standing up (the bounty's 6 and the kill's 5), one more such fight from Lieutenant's 15. `BalancePacingTest` plays the round 2 playtest's fixes with their numbers: the raider woken as First Play wakes it comes in firing, lands hits, and takes the player's guns over 20 s to beat (combat-sys's grazing rounds and smaller machinery bites; it was 7 s), a stripped laser pays under half the bounty, one fight is not a promotion and two are, beating a pirate pleases only the Patrol, and a ship latched to a holed one keeps its air while its boarding party still crosses. A second test abandons ship as a Lieutenant Commander and wakes at a sponsor station seated at a gunship's helm.

## Ships for Sale and Prizes (round 3)
`Shipyard` (core) answers GDD §5's "ship procurement and outfitting": something to spend a career's credits on, and a way to bring a beaten ship home whole. Built on `claude/r3-shipyard-8gjsst`.

- **Where ships are sold:** `[shipyard.<id>]` in `assets/data/shipyard.toml` (`ShipyardDef`) names a faction's yards: every station it owns of the listed `station_types`, and the listed `stations` by name. The Patrol's are any `shipyard` station and, until one is in the world, Highport; the Selene Combine's are Shackleton Ice.
- **What is sold:** `[ship_sale.<id>]` (`ShipSaleDef`): `ship`, `seller`, `price`, an optional `rank` on the sponsor's ladder, and `standing` with the seller. `DefValidator` warns about unknown ships, factions, ranks and station types.

  | Sale | Ship | Seller | Price | Needs |
  |---|---|---|---|---|
  | `patrol_corvette` | Patrol Corvette | Patrol | 280,000 | |
  | `patrol_gunship` | Gunship | Patrol | 360,000 | Lieutenant Commander |
  | `patrol_frigate` | Frigate | Patrol | 720,000 | Commander |
  | `selene_raider` | Light Raider | Selene | 110,000 | 10 standing with Selene |
- **Buying:** docked with clearance at a yard, the yard window's **Ships for sale** button ([Y]) opens the shipyard window, which lists the ships with their price and what they cost after the trade-in, or why they are locked: the rank (the sale's own, or the first rank the ladder lets fly it), the standing, the credits, yard work under way, cargo that won't fit the new hold, or more people aboard than its pods seat.
  - **Trade-in:** the ship flown goes to the yard for 60% (`TRADE_IN_SHARE`) of its value (`Difficulty.shipValue`: hull tiles and weapons) times its condition (mean hull integrity times mean system health).
  - **The swap:** the new ship takes the old one's place at the station, latched to a free port of its size. Credits, cargo and every crew member who calls the old ship home (prisoners aboard too) move across; the avatar is seated at the new helm, as after a rescue. The old ship, its systems and anyone left aboard are removed.
  - **Net worth:** the ship's value counts in `Difficulty`'s net worth, so at equal credits a bigger ship means harder encounters. A purchase itself spends more credits than the ship's value adds, so difficulty dips at the moment of buying and climbs again as credits come back.
- **Ships the player can fly:** the light raider (one Small pod, 4 seats), the corvette, the gunship (two Small pods) and the new frigate. The frigate (`[chassis.frigate]`, 24 x 10) has two Medium bow turrets for its railguns, a missile launcher and point defense on each beam, a Large reactor, two gunship drives, a marine bay under its active dorsal Small port, and two Small pods. Its hull art comes from `tools/art/generate_svgs.py`; it collides on its layout. `LoopWiring.REQUISITION` still issues the gunship at most: a frigate is bought, never requisitioned.
- **Prizes (towing):** a ship the player has taken (`Boarding.takenBy`) and stays latched to is the prize in tow.
  - The weld never tears while towing (`DockingSystem.towLocked`), and the player's thrust goes through the pair's centre of mass (`FlightControlComponent.towing`), so the pair flies as one body with the combined mass and does not spin about the weld. Rotation assist works against the pair's spin with the combined inertia. The pair goes onto the rails and back with time warp like any latch.
  - Within 2 km (`PRIZE_RANGE`) of a station that deals with the player (not hostile to all, not refusing clearance), [Y] opens the shipyard window, which offers it to the station's tugs: **sold** for 25% of its value (`PRIZE_SHARE`), 10% once anything has been stripped off it (`STRIPPED_PRIZE_SHARE`), or **turned in** at a sponsor station for standing: that of a bounty the player took on it, else what a bounty on it would give (6 + 3 x tier).
  - Refused while any of the player's crew are still aboard it. Prisoners held aboard are handed over with it.
  - The player tows; they never command two ships.
- **Saves:** a purchase or a hand-over is done at once, so nothing new is saved: the new ship is saved as the player's ship always is.

`ShipyardTest` buys the gunship as a Lieutenant Commander (credits, cargo and crew across, docked in the old ship's place, saved and loaded), checks the refusals, and flies the light raider, the gunship and the frigate as the player's ship (helm, weapons console, pods for everyone, the player's crew, thrust, abandon ship). `PrizeTowingTest` takes the raider, tows it under full thrust (the pair gains what the combined mass allows and the weld holds), through time warp, and sells or turns it in at Highport.

## Saves
- **Save modules:** the career (`"factions"`) and contracts (`"contracts"`).
- **Saved as components:** disabled state, morale, abandon orders, boarding state, prisoners, salvage orders and stripped mounts, all on their entities.
- **Not saved:** a rescue between the pod launch and the new ship latching, which is in memory only.
- **Not saved yet:** hostile traffic's own state (each hostile's timers, and the counter that names new ones), so after a load a pirate's hunt and rest start over and a new pirate can repeat a name.
- **After a load,** `GameScenario.relink()` finds Highport, the depot and the lunar mine again by name (a new game gives them new uuids) and points hostile traffic's lurks at them.
- **The combined round trip:** `Round2SaveRoundTripTest` saves one game holding all of these at once and loads it into a fresh one: a Lieutenant Commander's career, the raider surrendered and taken with two prisoners, the player's old ship abandoned as a derelict, the gunship requisitioned at rescue and docked at the sponsor station, and a contract taken there. Each feature's own save edges are in `CareerEdgesTest`, `ContractEdgesTest`, `BoardingSaveTest`, `RescueEdgesTest` and `HostileEdgesTest`.

## Open Questions
- **Choosing a sponsor** at the start of a new game, and whether the player can ever change sides.
- **Standing recovering** over time, or being bought back (also open in `stations-docking`).
- **What a rescue costs** beyond the lost ship, cargo and crew: a standing penalty, a fee, or a lower-tier ship.
- **Crashing** going through rescue when the crew had time to get away.
- **Towing** a taken ship home instead of stripping it: answered in round 3 (Ships for Sale and Prizes), with placeholder shares chosen without Joe.
- **Prisoners:** what becomes of them (ransom, hand-over for standing, release).
- **All numbers** (pay, budgets, thresholds, prices) are placeholders until playtesting.

## Stages
| Stage | What | Branch | State |
|---|---|---|---|
| 1 | Factions as data, per-station ownership, standing per faction, the standing API | factions | Built |
| 2 | Rank ladder, promotions, budget, difficulty | factions | Built |
| 3 | Disabled state and NPC surrender | disabled-rescue | Built |
| 4 | Escape pods, abandon ship, rescue and requisition | disabled-rescue, playable loop | Built |
| 5 | Contracts and the mission board | contracts | Built |
| 6 | Boarding, taking ships and salvage | boarding-salvage, playable loop | Built |
| 7 | The loop in the main game, scripted end to end | playable loop | Built |
| 8 | Ships for sale, trade-ins, the frigate, prizes towed home | r3-shipyard | Built |
