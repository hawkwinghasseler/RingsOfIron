# World: Seeds, Stations, the Pirate Base and Sponsors

Round 3, category 5. Each career now starts from a seed that moves the stations, sets the traffic and the opening markets and reseeds the dice, so two careers differ while the same seed gives the same start. The system has four more stations, a third lawful faction (the Free Traders) and a hidden pirate base that grows the pirates while it stands. A new game may serve the Selene Combine instead of the Patrol, and the factions now grow strained with each other while grudges against the player fade.

## Implemented

### The new-game seed (`NewGame`)
The New game panel (`ShellMenus.showNewGame`) has a Seed field and a Serve choice. A number typed is that seed, any other text is hashed into one (case does not matter), and an empty field rolls a random seed from 1 to 999,999,999. Each part of the world draws from its own stream of the seed (SplitMix64 of the seed and a part number). A seeded game:

- Puts every station except Highport anywhere along its orbit.
- Adds 0 to 4 extra freighters (`EXTRA_FREIGHTERS`), each based at a station the dice pick, named Hauler Osprey, Hauler Tern, Free Trader Mira and Hauler Petrel. The world flies 10 to 14 freighters.
- Strays each freighter's starting credits (20,000) and each station's opening stock by up to a quarter either way (`STOCK_SPREAD` 0.25).
- Shuffles the order in which pirates lurk by the stations.
- Reseeds sensors, fire control, damage and the crew's dice (`GameWorld.reseed`; the combat-sys systems gained `reseed` methods).

`NewGame.designed()` is the world exactly as laid out with round 2's dice; First Play and the tests use it. The pause menu shows "Seed 1234" or "Designed start".

### The new stations (`GameScenario`)
Each has an interior and ports from its type's blueprint and one based freighter, so the designed fleet is ten.

| Station | Owner | Type | Orbit |
|---|---|---|---|
| Anvil Yards | Patrol | `shipyard` | Earth, circular at 20,000 km |
| Selene Forge | Selene | `foundry` | Moon, 400 km above the surface |
| Crisium Ore Works | Selene | `ore_mine` | Moon, 800 km above the surface |
| Freehold | Free Traders | `free_port` (new) | Earth, circular at 200,000 km, opposite the Moon in the designed start |

Their freighters are Yard Tender, Ore Barge and Free Trader Halcyon (the Forge has none of its own). The Patrol's patrols now also fly to Anvil Yards and Selene's guards to the Forge and the Ore Works. Pirates lurk by Deepwell, Shackleton and Highport first as before, then the Ore Works, Freehold, the Forge and the yard. A test checks that every leg between the seven trading stations plans inside the freighters' 30-day limit. Mission boards now list patrol jobs lowest tier first, so a newcomer sees one they may fly.

### The Free Traders
`[faction.traders]` in `factions.toml`, owning Freehold and at war only with the pirates. The `free_port` type (population 2,500) produces 12 food and consumes water, steel and machinery. Its id sorts after the others, so crew faction numbers keep their meaning. They fly no patrols.

### The pirate base (`PirateBase`)
Rustwake, a Void Raiders station of the new `pirate_den` type (no production, no orders, no mission board) on an orbit round Earth from 60,000 to 160,000 km. It is off the traffic map (`RailsTrafficMap`) until found: the player comes within 2,000 km of it round the same body, it enters the bubble, or something calls `reveal`. Its bubble marker is hostile, and `FactionStanding.refusal` turns the player away from any `hostile_to_all` station whatever their standing.

While it stands, `HostileTraffic` keeps one extra raider for every 14 days since the career began, up to 4, spawned at the base. It falls through `fall` (meant for a raid) or by itself once no living pirate is aboard while it is in the bubble; then the growth stops. Both post an important notice.

Round 5: the base fights back. A station is never a ship in the bubble, so the base counts as here when it is in the bubble or passes through it on the rails. When a player at war with the pirates comes within 15 km (`SCRAMBLE_RANGE`) it scrambles a wing of `defendersFor(difficulty)` raiders, 2 at difficulty 1 and one more for each step after, up to 4, launched 4 km round the base and hunting the player as one wing. Their names are saved. Once all of them are beaten (surrendered, disabled or gone) while the base is still in the bubble, the base strikes its colours and falls (round 7: at any range in the bubble; round 5 also asked the player to be within 20 km, and a real-window raid that drifted to 30 km never ended, with nothing on screen saying why). Its fall eases every pair's tension by 15 (`FALL_RELIEF`). The raid contract is in [Contracts.md](Contracts.md).

Round 6: the base has guns, and its wing comes out in order. At the first scramble it mans `BATTERIES` (2) gun platforms (`den_battery` in `ships.toml`, the `battery` chassis, loadouts `battery_*` in `loadouts.toml`), moored 1.5 km and 3 km behind it (`HostileComponent.mooring`; round 10: 2.35 km and 4.7 km, back along the base's path). A moored ship has no schedule on the rails and nothing flies it: in the bubble it only keeps its guns locked on the player, and out of it it rides the base's orbit. It is never counted as raider traffic, a sighting, a recon or bounty target, or an ace. The guns are manned once and kept; a later raid meets the same platforms. The base falls only once its wing is beaten or driven off (a defender that has broken off and runs, as a wingman does when its leader is beaten, no longer defends it); the guns need not be. The wing launches lightest first (`launchOrder`: by firepower, then name), and a railgun raider never leads unless every defender carries one. The first launches alone; the rest launch as one wing behind the second, once the first is beaten or after 90 s (`SECOND_WAVE_SECONDS`).

Round 7: the raid hurts again. The second wave launches after 45 s at the latest (was 90). `defendersFor` adds one defender for each 0.6 of difficulty past 1 (`DIFFICULTY_PER_DEFENDER`, was 1.0), so a Commander's gunship meets 3 and a Captain's frigate 4 (`MAX_DEFENDERS`). While the base stands, a defender whose leader is beaten fights on alone rather than break off with it (`PirateBase.defending`). `loadout.battery_railgun` lets one platform carry a railgun (`HostileTraffic.moorGuns` keeps it to one), but it ships at weight 0: the raid met its targets without it. Survey and steps: `planning/round7-raid.md` in the project folder.

Round 8: one raid, flown the same way by the enemy survey and the scripted career (`RaidRoutine` in core's tests): the player jumps in 8 km from the base, the helm holds position on it, each defender is fought as the base launches it, and nothing jumps once the fight is on. A lost raid is flown again after the yard, up to three tries. The helm's hold position and match velocity now carry on under fire (`HelmOrders.survivesThreat`); before, the first dart dropped the hold. With a real hold the frigate lost too often, so `DIFFICULTY_PER_DEFENDER` is 0.8 (a Commander's gunship still meets 3 defenders, a Captain's frigate 3 until difficulty 2.6, then 4) and `battery_railgun` has weight 5, so about half a frigate's raids meet a railgun platform. When the base strikes its colours its defenders' and platforms' missiles in flight go ballistic (`MissileGuidanceSystem.goBallistic`), so the missile warning clears with the fall, and its platforms strike with it: one the player had hit no longer locks on (`PirateBase.silenced`; part 6's window saw two batteries launch for a minute after the fall). Survey and steps: `planning/round8-raid.md` in the project folder.

Spawn loadouts and an ace's refit are drawn from the career seed (`NewGame.mix(world.events.seed, …)`) with the ship and its spawn count, so a seed meets the same fits and another seed other fits.

### Choosing a sponsor
`[faction.*]` gains `sponsorable`, `home` and `rank_names` (`FactionDef`). Only the data's sponsor or a sponsorable faction can be served (`FactionStanding.serve`). The panel offers the Terran Patrol (home Highport) and the Selene Combine (home Shackleton Ice). A Selene career starts docked at Shackleton with the bubble anchored in low lunar orbit 8 km behind it, the raider's bounty on Shackleton's board, Selene's guards marked friendly, and its own rank names: Prospector, Surveyor, Foreman, Superintendent, Director. The sponsor is fixed for the career.

### Faction tension (`FactionTension`)
A value from 0 to 100 for each pair of the data's factions, pirates aside. Each hostile act of the player against a faction raises its tension with the player's sponsor by half the standing it cost; `raise` lets other systems move it. World events strain it too (round 5, see [Contracts.md](Contracts.md)). It relaxes by 5 a week (2 in round 3; with events adding to it, 2 left every pair pinned near 100 by day 40). When a pair first reaches 50 a warning notice posts and each side gets one more patrol (`HostileTraffic.reinforce`, spawned after the update; only once until it falls below 50 again). From round 5 the Free Traders, who fly no patrols of their own, then send an escort to watch the lanes between the two sides' first stations (by name), with a notice (`HostileTraffic.patrol`). While it is 50 or more, the rival's stations refuse a player of the other side whose standing with them is below 10.

The mission window shows a tension line under the career: each faction's tension with the player's sponsor, HIGH past 50 (`FactionTension.describe`).

### The world reacts to tension (round 6)
`[tension.default]` in `factions.toml` (`TensionDef`) tunes it. A station's tension is its owner's highest with any other lawful faction (`FactionTension.regional`).

- **Prices.** A station's yard charges more for repairs and for filling the tanks (propellant, oxygen, nitrogen): +25% at full tension (`price_rise`), in proportion below it, so +10% at 40 (`priceFactor`, applied by `StationYard`). The yard window shows the strained price. Freighters refuel at the base price.
- **Combat jobs.** From 50 (`combat_jobs_at`) a board posts one more job (`combat_jobs`) on top of its five: the next bounty it had, else an escort, with the note "Tension runs high here".
- **A rival's board.** A faction strained with the player's sponsor posts one job fewer for each 25 of that tension (`distrust_per_job`), never fewer than 2 (`fewest_offers`), unless the player's standing with it is 10 or more (`trusted_standing`).
- **The traffic map** shows a TENSION readout in its bottom left corner, a line per faction with stations: "Selene Mining Combine: 60 HIGH, yards +15%", in red from 50 (`FactionTension.regions`, `RailsTrafficMap`).

### After the base falls (round 6)
The sponsor offers a Captain's operation, Scatter the Remnants (recon, bounty, patrol; `requires = "base_fallen"` in `operations.toml`), ahead of its rank's others. A fallen base does not stay empty: once any pair of lawful factions has spent 20 days at 50 or more since it fell (`base_rises_after_days`, counted on the sim clock and saved), the raiders move back in (`PirateBase.rise`). It stands again where it was, still on the map, crewed anew if its pirates died, growing from that day, and the sponsor's raid contract comes back.

### Standing recovery
In the same weekly step, negative standing with every faction moves 1 back towards zero. Positive standing never decays.

## Saves
- `newGame`: the seed, whether the game is seeded, and the sponsor. A load replaces the scenario's own and recomputes the pirates' lurk order from it.
- `pirateBase`: when it was established, found and fallen, and the names of the wing it scrambled. A load finds the base by name. Round 6 adds the names still waiting to launch, when they launch, the names of its guns (the guns themselves save as hostiles, with their mooring), and the days it has lain fallen with the factions strained.
- `tension`: every pair's value, which pairs have been reinforced, and when the next week falls.
- The sponsor is also in `ServiceRecord`, as before; a load reads the home from it.
- The stations, freighters and stock are saved as the world's entities. A load finds the new stations by name, and a save from before them loads without them.

A load reseeds the combat and crew dice from a seeded save's seed (`world.reseed`), as a new game does. Their positions in the stream are not saved, so a loaded career rolls from the start of its seed's streams, not where the save left off. A designed save keeps the loading world's dice.

## Tests
- `WorldSeedTest`: same seed same start, another seed another; the designed start as laid out with 10 freighters; seeds give 10 to 14 freighters; every station within a freighter's reach for four seeds; a save keeps the seed and the lurk order; typed seeds.
- `WiderWorldTest`: the new stations' types and owners, the Free Traders at war only with pirates, reachability between all stations, patrol routes covering the new stations.
- `PirateBaseTest`: the base's orbit, trade and clearance; hidden until revealed; found by proximity; growth and gathering at the base; falling when its crew are dead; a save keeps found and fallen.
- `SponsorChoiceTest`: the Patrol by default; a Selene start, board and rank names; only sponsorable factions; flying and warping above the Moon; friendly guards; a save keeps the sponsor and home.
- `Round5LateCareerTest`: the base's wing, its fall and the tension it eases, a save mid-raid, world events' strain, the Free Trader patrol and the board's tension line.
- `Round6WorldTest`: the data's tuning, prices up to a quarter (refill and repairs charged at the strained price), a combat job added at high tension, a rival's shorter board, the traffic map's readout, survivors hurt by days in the pod and tended, the Captain's operation after the fall, and a new base rising after 20 strained days (with a save between).
- `BaseDefenceTest`: launch order, a railgun raider never leading; the guns manned at the first scramble, moored and locked on the player, kept through a save; the guns are no bounty, recon or ace; spawn loadouts follow the seed; the base's darts in flight go ballistic when it strikes (round 8); a platform without a railgun reaches and hits a ship holding where the raid holds, and (slow) a defence with no railgun marks a frigate (round 10).
- `EnemySurveyTest` (round 6): the base's defence against the corvette, and with `-PenemySurvey` the survey of each enemy against the corvette, gunship and frigate.
- `FactionTensionTest`: hostile acts raise tension, pirates excluded; high tension reinforces once and refuses rivals; the weekly drift; weeks pass on the sim clock; a save keeps the strain.

## Choices made without Joe
- Freehold sits at 200,000 km, not on the Moon's orbit as first built, because there it never met a transfer window with the lunar stations.
- A seed varies freighter credits, not their starting cargo.
- The designed start keeps round 2's layout and dice, and pirates still lurk by the first three stations first.
- Selene still does not name the pirates an enemy, so a Selene career climbs by contracts and bounties rather than kills.
- No one is cleared at a pirate station.
- Round 5: the base scrambles 2 to 4 raiders by difficulty and falls once they are beaten with the player close; its fall eases tension by 15.
- Round 5: the Free Trader escort flies between the two strained sides' alphabetically first stations, so a replayed career sends it the same way.
- Round 6: a station's tension is its owner's highest with any lawful faction, not only with the player's sponsor, so both sides of a strain pay more. Prices rise in proportion from 0, not only once tension runs high, so a strain is felt before it is HIGH. Only the yard's prices move; the commodity markets do not.
- Round 6: the extra combat job is the board's next bounty, else an escort, so it is work the world already has rather than a made-up target.
- Round 6: a new base rises at Rustwake itself, known to the player, rather than somewhere new and hidden: the hunt's intel step is never offered twice, so a hidden base would be hard to find again.
- Round 6: two gun platforms, moored 1.5 km and 3 km behind the base and manned at the first scramble, never a railgun among their fits; their hull art is generated from the chassis grid (`tools/art/generate_svgs.py`) and they borrow the raider's texture.
- Round 6: the second wave launches when the first defender is beaten or after 90 s; ties in firepower launch by name; when every defender carries a railgun the order stands.
- Round 7: the frigate's raid is tuned to win 6 to 8 of 10 in the enemy survey with mean hull 0.50 to 0.75: it wins 7, at 0.74. The career seeds 2026, 7 and 12 drew no railgun raider for the raid and still end it at hull 0.89 or better.
- Round 8: the career flies the survey's raid. The survey's frigate wins 7 of 10 at mean hull 0.73 (worst 0.43). On the career seeds the raid is a draw of the same fight: seed 2026 meets a railgun raider and wins at 0.50, seed 12 meets a railgun platform and wins at 0.79, and seed 7 meets neither and wins at 1.00.
- Round 10: the raid marks the frigate without a railgun. The base's two gun platforms moored 1.5 km and 3 km behind it by stepping back round Earth, while a ship coming up behind the base (the raid's jump) holds back along the base's path; on Rustwake's eccentric orbit the two lines part by up to the flight-path angle (about 26° on career seed 6), and the platforms stood 4 to 6.5 km from the frigate, out of their chainguns' 3 to 3.5 km. Only a railgun ever landed, and the frigate's armour sheds the 30 mm rounds of the raiders that close, so raids without a railgun took 0.00 to 0.04 of hull off. Moored platforms now hold back along the base's path (`HostileTrafficController.alongTrack`), 2.35 km apart (`PirateBase.BATTERY_SPACING`), so the far one stands about 3.3 km from where the raid holds; a railgun platform takes the mooring nearest the base (`HostileTraffic.moorGuns`). `battery_stock` and `battery_darts` each swap a Mk1 chaingun for the 40 mm Mk2, and `battery_heavy` comes from difficulty 2.2. The enemy survey gains a row, "The base's defence, no railgun": the frigate at difficulty 2.45 (the middle of the career's raids), with any railgun fit the base draws drawn again without one. Steps and numbers: `planning/round10-raid.md` in the project folder.
- Round 9: stray fire never brings a prize back (`HostileTraffic.STRAY_FIRE_REVIVES` false): point-defense rounds and rounds or missiles aimed at another ship don't count, nor a shot down the barrel at no target. Fire aimed at the prize itself after the 10 s grace still does, so locking a struck raider and firing on it brings it back. The player's point defense and crewed guns, firing at will, never take a struck ship as their target.
- Round 9: hits on a boarder count towards breaking it off only within 1,000 m of the player's ship (`PirateBoarding.BREAK_OFF_RANGE`); further out a hit works as in any fight, so a boarding begins close in. One hit inside that range still ends it (`BREAK_OFF_HITS` 1), and a pirate driven off doesn't board again until the player recovers.
- All the numbers above are placeholders.

## Not built
- The guns have only generated hull art, not a hand-drawn platform, and the base itself still cannot be shot.
- Tension does not move commodity prices at the markets, only the yard's.
