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
Ranks are data: `[rank.<id>]` (`RankDef`) with `name`, `order`, `standing` (needed with the sponsor), `budget` (credits per in-game week), `max_chassis`, and from round 7 `min_days` and `ship_days` (time in grade, below).

| Rank | Standing | Budget (cr/week) | Largest chassis |
|---|---|---|---|
| Ensign | 0 | 2,000 | corvette |
| Lieutenant | 15 | 5,000 | corvette |
| Lieutenant Commander | 35 | 10,000 | gunship |
| Commander | 60 | 20,000 | frigate |
| Captain | 85 | 40,000 | frigate |

- **Promotion:** only standing with the sponsor promotes. `checkPromotion` climbs one rank at a time with a notice ("Promoted to Lieutenant of the Terran Patrol Authority: budget 5,000 cr a week"). Ranks are never taken back.
  - **Time in grade** (round 7): a rank's `min_days` is the least time the player holds the rank below before it is given, whatever their standing. Commander and Captain each have 5; the others none. A promotion held back says so once ("Standing enough for Captain: promoted after 3.2 more days as Commander") and is given as the days pass (`FactionStanding.promoteDue`, from `ServiceBudget`'s tick). `ServiceRecord.promotedAt` is saved with the career. Round 6's seed 7 made Commander and Captain on the same day.
  - **A new rank's ship** (round 7): a rank's `ship_days` is how long after it is given the sponsor's yards wait to sell a chassis no lower rank may fly, whatever rank the player has reached since (`ServiceRecord.promotions`, saved) (`FactionStanding.newChassisWait`; the yard says "Sold to a new Commander after 4.0 more days in rank"). A Commander waits 10 days for the first frigate; the Lieutenant Commander's gunship has no wait. Seed 7 bought the frigate a week after making Commander, on four ace bounties.
- **Budget:** `ServiceBudget` pays the budget into the player ship's credits every `WEEK` (604,800 sim seconds). It catches up on every payday passed during time warp, at the rank held when paying. The first payday is a week after the career starts. Paydays wait while the player has no ship with credits. The HUD shows "Ensign   Budget 2,000 cr/wk in 6d 23h".
- **What a rank flies:** `RankLadder.mayFly` compares a ship's chassis size in tiles with the rank's `max_chassis`, for a requisition after a rescue (`mayRequisition`) and a purchase at a yard alike. Sizes: light raider 75, picket 90, interceptor 91, corvette 96, courier 99, corsair 126, salvage tug 144, gunship 160, surveyor 170, ore barge 213, marauder 215, frigate 240, freighter 260, so no rank flies a freighter. The larger hulls (hospital ship 310, propellant tanker 362, destroyer 453, reaver 550, heavy cruiser 592, bulk carrier 612, passenger liner 674, carrier 1,122) are past every rank's limit: no yard sells them and they are spawned by name. (Round 2 named the freighter at Lieutenant Commander and above, which let the gunship in by size alone; round 3 names the gunship and the frigate.)
- **Difficulty** (GDD §5): `Difficulty.of(rank, worth) = 1 + 0.25·rank + 0.5·log2(worth / 300,000)`, where the worth term counts only above 300,000. Net worth is credits plus the ship's value: 1,000 a hull tile plus its weapons' prices.
- **Pirate numbers:** `HostileTraffic` sizes the pirate population from a difficulty value (`Difficulty.raiders`, `Difficulty.gunships`): `1 + round(d)` raiders and `floor(d)` gunships.
- **What the pirates fly** (round 5): `Difficulty.wingSize` puts raiders in wings by the difficulty: alone below 1.25 (a new ensign), pairs from 1.25 (a Lieutenant, or a rich ensign), threes from 2. The loadouts bring the heavier guns with the difficulty (railgun raiders from 1.5, the pirate gunship's railgun fits from 1.75 and 2). Once the player flies a gunship or bigger (`Difficulty.fliesAtLeast`), an ace takes one of the pirates' gunships.

## Pirates and Patrols
Hostile traffic (`claude/r2-roster-traffic-z8x44s`) is what the player fights. It is documented with macro-sim, since it lives on the rails like freighters. The progression side:
- **Pirates** fly for the Void Raiders. They lie in wait near trade lanes and rob loaded freighters. A pirate hunts the player only when it is bold enough: firepower at least 0.8 of the player's, and only after the first 6 hours of a game. A pirate breaks off below 65% of its hull, before it is beaten badly enough to surrender (55%), so a losing pirate runs first and gives up only if it is caught and hit again.
- **Patrols** fly for the sponsor and for Selene between their stations and engage pirates. A pirate won't strike at a station guarded by an equal or stronger patrol.
- **Warp:** a hostile moving to intercept the player stops time warp.
- **Loadouts** (round 3): each pirate is armed from a weighted list for its hull and the difficulty (`loadouts.toml`). Raiders carry a chaingun or darts, and from difficulty 1 an ion gun, from 1.5 a railgun. Gunships range from the stock fit to flak, brawler, ion-and-darts and, from 1.5, the heavy railgun. Patrols and the First Play raider keep their stock weapons. Round 6: the pick is drawn from the career seed with the ship and how many have spawned (`HostileTraffic.loadoutDice`), so the same seed meets the same fits.
- **Wings** (round 3 pairs, round 5 sizes): a new raider joins the wing of a raider leading fewer than `Difficulty.wingSize` (the one with fewest wingmen first), at its lurk, and in the bubble fights whatever its leader has its guns on. A wing breaks off together: when its leader breaks off, runs or is beaten in the bubble, its wingmen break off too. Round 7: not a wingman still on its run in (no round fired yet, beyond its guns' reach, hull 80% or more), nor one defending a standing base: it leaves the wing and fights on alone ("fights on without its leader"), so it fires before it goes. `HostileTraffic.spawnWing` puts a wing of any hull and size by a station (for a base's defence, say).
- **Tactics** (round 3, `Tactics`): each look at the bubble (1 s), a captain picks a plan from its weapons and the odds (its firepower times its hull, with its wing's, over the enemy's):
  - **Run** (round 6: never while the enemy is still beyond its guns' reach, so it fires before it runs): a pirate that has been hit (hull below 80%; round 4's 90% sent raiders off at the first hit, before they were in their own guns' range, so they never fired) runs when outgunned (odds below 0.5), or when it is in a crossfire and not winning. Aces and patrols never run.
  - **Holding to fire** (round 10, `HostileTraffic.BREAK_OFF_HOLD`): a plain pirate in its guns' reach, or within 1.25 of it, that would break off below its hull line or run on the odds holds for 10 s first, so it fires before it turns away. Not under crossfire, and not an ace. A lone raider worn to 0.65 by a frigate's railguns just as it crossed its chaingun's 3 km used to turn away unfired.
  - **Firing as it goes:** a ship that runs or breaks off keeps its guns on whatever it was fighting as it flies off.
  - **Standoff:** a ship with launchers and long guns that isn't clearly winning (odds below 1.2), or with nothing but launchers, keeps 5 km off. One whose guns are all short closes to use them.
  - **Flank as a wingman** (round 5): a wingman whose leader fights the same enemy swings round astern of it, circling the other way, so the wing has it in a crossfire.
  - **Kite** (round 5): a ship whose guns all reach past 3.5 km keeps 4.5 km off (or 85% of its shortest gun's reach), nose on, and backs straight off as the enemy closes, unless it clearly outguns it; an ace with long guns always kites.
  - **Rush:** a ship with only short guns (3.5 km or less) closes to 600 m, fast. Round 6: it closes at up to 240 m/s (160 before) and dodges only missiles within 1 km, not 3 km, so a raider reaches its guns' range and comes in firing.
  - **Flank:** a ship that clearly outguns its enemy (odds 1.5 or more) swings round to 1 km astern, to hit its engines.
  - **Hold:** otherwise it holds 1.5 km and circles, as before.
- **Crossfire** (round 3): a hostile fired on lately (20 s) by attackers 60° or more apart in bearing breaks off below 80% of its hull, not 65%. It doesn't hold to fire first.
- **The pirate gunship** (round 5, `gunship_raider` in `ships.toml`): the gunship hull rearmed by the pirates, with a chaingun in the nose, a dart rack on each beam and point defence forward, crew 4. The patrols keep `gunship_heavy`. On its stock fit it robs freighters and leaves the starter corvette alone (it is under the 0.8 nerve); its railgun fits (`gunship_line` from 1.75, `gunship_heavy_railgun` from 2) dare the player.
- **Aces** (round 3, `PirateAces`): a pirate that gets away from a fight with the player (it fought them and left the bubble unbeaten), or that has robbed 3 freighters, becomes a named ace, if fewer than 3 are about. Round 5: once the player flies a gunship, the hourly top-up makes the first plain pirate gunship an ace when no ace flies one (no grudge; it hunts by the odds), and says so.
  - It takes a callsign, its crew's gunnery and tracking rise to at least 0.85, and it is refitted from its ship's ace loadouts, which carry its hull's best guns.
  - One that got away from the player keeps a grudge and hunts them whatever the odds; one that gets away again keeps it.
  - Its bounty is 4 × a pirate's, with 3 more standing for each step of the bounty's tier counting from 1 (round 7; it was 6 for any ace, and seed 7 rose from Lieutenant Commander to Captain in a week on four light aces), and ace bounties come first on a board. Round 5: the bounty is posted the day the ace appears on every board that would list it (`PirateAces.postBounty`, through `ContractBoard.postEvent`) until the day turns, when the day's boards take it up; round 4 waited for the next day's boards.
  - Beaten, it leaves a crate of its most valuable weapon in its hold for whoever boards it, forgets its grudge and frees its place.
  - An ace beaten once (`FirstPlay.aceBeaten`) is done with as far as contracts go, even after its crew get the ship flying again out on the rails: no recon, bounty or operation step names it again (round 6: Hunt the Hunters sent the frigate to find Iron Dagny, beaten a week before).
  - Every step is a notice in the log (L).
- **Pirates' upkeep** (round 8, `PirateUpkeep`): a pirate out on the rails, or idle in the bubble (not fighting, not stalking, not beaten), refuels its propellant tanks, full from empty in 2 days (`RAILS_REFUEL_DAYS`), in whole 60 s steps of rails time (`STEP`, so a save and load refuel the same). A robbery tops it up first: from the prey's own tanks in the bubble, and all it lacks on the rails (`RAILS_ROBBERY_SHARE`). Round 7 had aces meet their bounty with dry tanks and strike in the first second. A beaten ace whose bounty paid stays struck when fired on again (`HostileTraffic.onShotAt`). The last step is saved (`upkeepAt`); what the tanks hold is saved with the ship.

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
| Escort | A trade order already claimed by a freighter | 1,500 | 4 + 2·tier | 2 or 8 days after the freighter's arrival at the buyer; for one still flying in to load, its arrival there plus one more such leg, and 8 days more while it is crossing into another body's sphere (round 9) | The freighter arrives with the player within 50 km of its destination; it fails if the player is not there within 30 minutes of its arrival, or if the freighter is lost |
| Bounty | A ship of a faction at war with the giver | 5,000 × (1 + tier) × difficulty; ×4 for an ace | 6 + 3·tier; +6 for an ace | 3 days | The target is gone, or beaten (`FirstPlay.beaten`: disabled, reactor out or guns silenced; never while only dark for an ion stun) |
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
- **A surrender holds** (round 6): a hostile that surrenders lets go of the player (`HostileComponent.surrendered`, saved). It never hunts them again, on the rails or back in the bubble, where it comes in still surrendered and holds its fire. Only the player firing on it again, 10 s or more after it gave up (`HostileTraffic.SURRENDER_GRACE`), lifts it. A hit counts from when its shot was fired (`ImpactEvent.sinceFired`; a warhead's fragments keep their missile's launch), so a missile launched before the surrender never lifts it however long it flew (round 6 integration: on seed 12 a heavy missile burst 15 s after a surrender, and the prize then tore the boarding latch). Before this, a surrendered raider fought again once its guns were mended and followed the player through a tow. Round 9: stray fire never lifts it (`HostileTraffic.STRAY_FIRE_REVIVES` false). A point-defense round, or a round or missile aimed at another ship, that hits the prize doesn't count (`ImpactEvent.pointDefense`, `ImpactEvent.aimedAt`), and neither does a shot down the barrel at no target; fire aimed at the prize itself after the grace still does. The player's guns firing at will never take a struck ship as their target (`WeaponControlSystem.setSpared`). In a headless replay of `tools/playtest/mid-late.sh`'s raid on round 8's seed 2026 save, a railgun slug aimed at Pirate raider 21 hit the struck raider 19 at 2.2 km, 75 s after it struck: it was back in action, then running 13 s later, as Pirate raider 22 was in the real window. A point-defense round aimed at a dart lifted a struck battery's surrender the same way.
- **What stays:** disabled ships and wrecks stay in the world as targets for boarding and salvage.
- **First Play:** a disabled raider counts as beaten.

## Abandon Ship
- **Escape pods:** `ESCAPE_POD` is a walkable grid component, `B` in chassis maps. Its seats are 4, 8, 20 or 48 for sizes S to XL. The corvette carries one Small pod and the freighter two.
- **Ordering it:** P asks for confirmation, and a second P within 3 seconds orders abandon ship (`AbandonShip.order`). It is refused while docked ("walk off instead"), with no usable pod, or with nobody aboard. P again while abandoning launches the pods now.
- **Getting to the pods:** the order posts an `EvacuateTask` (priority 1000) per seat, and every crew member drops what they are doing for it. The avatar is never walked to a pod: the player walks there themselves, or stays aboard.
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

**Crashing** ends the game ("SHIP LOST", F9 to reload) unless the crew were ordered to the pods before impact and the player reached one: then the pods launch and it is a rescue (`GameWorld.crash`, `AbandonShip`).

## Boarding and Salvage
Boarding is crew work across a ship-to-ship dock: the corvette's port is active and can latch onto another ship's port (`claude/r2-bridge-helm-1buzuu`). The details are in `ai-crew` and `stations-docking`. The progression side:

- **Boarding:** only a disabled ship can be boarded (`Boarding.refusal`: "disable it first"). K opens the boarding window, and Send orders a party of marines (or crewmen if there are no marines) across. They sweep the ship room by room, forcing a shut door first if needed.
- **Taking a ship:** the ship is taken once the party is aboard and no defenders are standing, or at once if it had surrendered. Its crew are held as prisoners (`CrewMemberComponent.held`): they neither fight nor work. An undefended disabled ship is taken the first time the player marks something on it or orders its cargo across. Asking whether it may be stripped (`Salvage.refusal`) only checks (`Boarding.canTakeUndefended`); it takes nothing and costs no standing.
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

A new game (the main menu's New game, and First Play) starts docked at Highport as an ensign of the sponsor (`GameScenario.beginCareer`). The raider outside flies `corvette_raider`: the player's corvette refitted by pirates, a chaingun where the railgun was, with the laser, launcher and point defense kept. As the player's twin it won or lost on who fired first. The board's first bounty is on the raider outside: bounties on ships in the bubble are offered before those on pirates out on the rails. First Play's steps are: take the bounty, undock, fly clear, beat the raider, board it, dock, repair, open the notice log, and plan a transfer (see [FirstHour.md](../ui-hud/FirstHour.md#first-plays-last-two-objectives)).

`PlayableLoopTest` plays the loop headless: take the bounty, undock, fight the raider with the real guns and missiles until it gives up, board it, strip a gun, dock and sell it, and come away with the bounty's standing and the kill's. `Round2FixesTest` plays the round 2 playtest's fixes that are rules: beating a pirate pleases only the Patrol, and a ship latched to a holed one keeps its air while its boarding party still crosses. Its balance targets are the playtest benchmark `FirstHourBalanceBenchmark` (`documentation/playtest/`): the raider woken as First Play wakes it comes in firing, lands hits, and takes the player's guns over 20 s to beat (combat-sys's grazing rounds and smaller machinery bites; it was 7 s), a stripped laser pays under half the bounty, and one fight is not a promotion while two are. A second test abandons ship as a Lieutenant Commander and wakes at a sponsor station seated at a gunship's helm.

## Ships for Sale and Prizes (round 3)
`Shipyard` (core) answers GDD §5's "ship procurement and outfitting": something to spend a career's credits on, and a way to bring a beaten ship home whole. Built on `claude/r3-shipyard-8gjsst`.

- **Where ships are sold:** `[shipyard.<id>]` in `assets/data/shipyard.toml` (`ShipyardDef`) names a faction's yards: every station it owns of the listed `station_types`, and the listed `stations` by name. The Patrol's are its `shipyard` stations (Anvil Yards in high Earth orbit; Highport's yard only repairs and refits); the Selene Combine's are Shackleton Ice.
- **What is sold:** `[ship_sale.<id>]` (`ShipSaleDef`): `ship`, `seller`, `price`, an optional `rank` on the sponsor's ladder, and `standing` with the seller. `DefValidator` warns about unknown ships, factions, ranks and station types.

  | Sale | Ship | Seller | Price | Needs |
  |---|---|---|---|---|
  | `patrol_corvette` | Patrol Corvette | Patrol | 280,000 | |
  | `patrol_gunship` | Gunship | Patrol | 360,000 | Lieutenant Commander |
  | `patrol_frigate` | Frigate | Patrol | 455,000 (720,000 before round 5: a Commander could not reach it) | Commander |
  | `selene_raider` | Light Raider | Selene | 110,000 | 10 standing with Selene |
  | `patrol_picket` | Picket | Patrol | 170,000 | |
  | `selene_tug` | Salvage Tug | Selene | 165,000 | 10 standing with Selene |
  | `patrol_interceptor` | Interceptor | Patrol | 175,000 | |
  | `selene_courier` | Courier | Selene | 125,000 | 10 standing with Selene (and Lieutenant Commander, by size) |
  | `selene_surveyor` | Surveyor | Selene | 190,000 | 10 standing with Selene (and Commander, by size) |
  | `selene_ore_barge` | Ore Barge | Selene | 245,000 | 10 standing with Selene (and Commander, by size) |
- **Buying:** docked with clearance at a yard, the yard window's **Ships for sale** button ([Y]) opens the shipyard window, which lists the ships with their price and what they cost after the trade-in, or why they are locked: the rank (the sale's own, or the first rank the ladder lets fly it), the standing, the credits, yard work under way, cargo that won't fit the new hold, or more people aboard than its pods seat.
  - **Trade-in:** the ship flown goes to the yard for 60% (`TRADE_IN_SHARE`) of its value (`Difficulty.shipValue`: hull tiles and weapons) times its condition (mean hull integrity times mean system health).
  - **The swap:** the new ship takes the old one's place at the station, latched to a free port of its size. Credits, cargo and every crew member who calls the old ship home (prisoners aboard too) move across; the avatar is seated at the new helm, as after a rescue. A station's own technicians or loaders still aboard (walking back from the yard's work) step back aboard their station instead (round 6: seed 2026's frigate carried off Highport's technicians, and its yard could do no more work). The old ship, its systems and anyone left aboard are removed.
  - **Net worth:** the ship's value counts in `Difficulty`'s net worth, so at equal credits a bigger ship means harder encounters. A purchase itself spends more credits than the ship's value adds, so difficulty dips at the moment of buying and climbs again as credits come back.
- **Ships the player can fly:** the light raider (one Small pod, 4 seats), the picket (one Small pod), the interceptor (one Small pod), the courier, surveyor and ore barge (Selene's), the corvette, the salvage tug (one Small pod), the gunship (two Small pods) and the new frigate. The frigate (`[chassis.frigate]`, 24 x 10) has two Medium bow turrets for its railguns, a missile launcher and point defense on each beam, a Large reactor, two gunship drives, a marine bay under its active dorsal Small port, and two Small pods. Its hull art comes from `tools/art/generate_svgs.py`; it collides on its layout. `LoopWiring.REQUISITION` still issues the gunship at most: a frigate is bought, never requisitioned.
- **Prizes (towing):** a ship the player has taken (`Boarding.takenBy`) and stays latched to is the prize in tow.
  - The weld never tears while towing (`DockingSystem.towLocked`), and the player's thrust goes through the pair's centre of mass (`FlightControlComponent.towing`), so the pair flies as one body with the combined mass and does not spin about the weld. Rotation assist works against the pair's spin with the combined inertia. The pair goes onto the rails and back with time warp like any latch.
  - Within 2 km (`PRIZE_RANGE`) of a station that deals with the player (not hostile to all, not refusing clearance), [Y] opens the shipyard window, which offers it to the station's tugs: **sold** for 25% of its value (`PRIZE_SHARE`), 10% once anything has been stripped off it (`STRIPPED_PRIZE_SHARE`), or **turned in** at a sponsor station for standing: that of a bounty the player took on it, else what a bounty on it would give (6 + 3 x tier).
  - Refused while any of the player's crew are still aboard it. Prisoners held aboard go with it: the notice counts them as handed over, but they are only removed with the ship (`Shipyard.retire`), for no pay or standing of their own.
  - The player tows; they never command two ships.
- **Saves:** a purchase or a hand-over is done at once, so nothing new is saved: the new ship is saved as the player's ship always is.

`ShipyardTest` buys the gunship as a Lieutenant Commander (credits, cargo and crew across, docked in the old ship's place, saved and loaded; a yard technician aboard at the sale stays with the yard), checks the refusals, and flies the light raider, the gunship and the frigate as the player's ship (helm, weapons console, pods for everyone, the player's crew, thrust, abandon ship). `PrizeTowingTest` takes the raider, tows it under full thrust (the pair gains what the combined mass allows and the weld holds), through time warp, and sells or turns it in at Highport (a prize is sold to any station that deals with the player; only ships for sale moved to Anvil Yards).

## Saves
- **Save modules:** the career (`"factions"`), contracts (`"contracts"`) and hostile traffic's own state (`"hostile-traffic"`: the next top-up, the counter that names new pirates, an intercept alert holding warp off, the player's opening grace, and each hostile's idle and plunder timers in the bubble).
- **Saved on `HostileComponent`** (round 6): whether it has surrendered to the player, and a gun platform's mooring.
- **Saved as components:** disabled state, morale, abandon orders, boarding state, prisoners, salvage orders and stripped mounts, all on their entities.
- **Not saved:** a rescue between the pod launch and the new ship latching, which is in memory only.
- **Not saved:** who last shot at whom (`HostileTraffic.provoked`, which holds live entities). A provoked hostile keeps fighting from its locked target; one that had not locked on yet forgets.
- **Saves from before round 3** have no `"hostile-traffic"` entry. Loading one, top-ups start again an hour after the load and new pirates are numbered on from the highest loaded name, so no name repeats.
- **After a load,** `GameScenario.relink()` finds Highport, the depot and the lunar mine again by name (a new game gives them new uuids) and points hostile traffic's lurks at them.
- **The combined round trip:** `Round2SaveRoundTripTest` saves one game holding all of these at once and loads it into a fresh one: a Lieutenant Commander's career, the raider surrendered and taken with two prisoners, the player's old ship abandoned as a derelict, the gunship requisitioned at rescue and docked at the sponsor station, and a contract taken there. Each feature's own save edges are in `CareerEdgesTest`, `ContractEdgesTest`, `BoardingSaveTest`, `RescueEdgesTest` and `HostileEdgesTest`.

## Open Questions
Round 3 (its plan was kept outside the repository) answers most of these with defaults, chosen without Joe while he was away and marked so here. Each stays open to his call; the branch named builds it.
- **Choosing a sponsor** (default, chosen without Joe): a New game offers the Terran Patrol or the Selene Combine, each with its own ladder names and stations. The sponsor is fixed for the career; changing sides stays open. Category 5, `claude/r3-world`.
- **Standing recovering** (default, chosen without Joe): negative standing drifts toward zero by 1 a week; positive standing doesn't decay. Buying it back stays open. Category 5.
- **What a rescue costs** (default, chosen without Joe): 5 standing with the sponsor, and any active contracts fail, on top of the lost ship, cargo and crew. Category 6, `claude/r3-contracts-events`.
- **Crashing** (default, chosen without Joe): if the crew were ordered to the pods before impact and reached them, a crash is a rescue, not "SHIP LOST". Category 1, `claude/r3-first-hour`.
- **Towing** (default, chosen without Joe): a taken ship that stays latched can be towed home as one body and sold for 25% of its value (10% stripped), or turned in to the sponsor for its bounty's standing. The player tows, never commands two ships. Category 3, `claude/r3-shipyard`.
- **Prisoners** (default, chosen without Joe): handed over at a sponsor station for credits and standing, ransomed back to their faction at its station, or released. A pirate prisoner may offer to join at 0.3 skill. Category 4, `claude/r3-crew`.
- **Stranded or pod-less** (new, default chosen without Joe): a distress beacon calls a sponsor tug that tows the ship to the nearest sponsor station after 3 days, for 10% of the ship's value and 3 standing, keeping ship, cargo and crew. Category 1.
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
| 8 | Ship market, prize ships, rank gates that agree | r3-shipyard | Built |
| 9 | Hiring, veterancy, prisoners' fates | r3-crew | Built |
| 10 | Seeded worlds, more stations, choosing a sponsor, standing recovery | r3-world | Built |
| 11 | New contract types, operations, world events, rescue cost | r3-contracts-events | Built |
| 12 | Distress beacon, crash through rescue, autosave | r3-first-hour | Built |
