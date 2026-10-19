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

### Choosing a sponsor
`[faction.*]` gains `sponsorable`, `home` and `rank_names` (`FactionDef`). Only the data's sponsor or a sponsorable faction can be served (`FactionStanding.serve`). The panel offers the Terran Patrol (home Highport) and the Selene Combine (home Shackleton Ice). A Selene career starts docked at Shackleton with the bubble anchored in low lunar orbit 8 km behind it, the raider's bounty on Shackleton's board, Selene's guards marked friendly, and its own rank names: Prospector, Surveyor, Foreman, Superintendent, Director. The sponsor is fixed for the career.

### Faction tension (`FactionTension`)
A value from 0 to 100 for each pair of the data's factions, pirates aside. Each hostile act of the player against a faction raises its tension with the player's sponsor by half the standing it cost; `raise` lets other systems move it. It relaxes by 2 a week. When a pair first reaches 50 a warning notice posts and each side gets one more patrol (`HostileTraffic.reinforce`, spawned after the update; only once until it falls below 50 again). While it is 50 or more, the rival's stations refuse a player of the other side whose standing with them is below 10.

### Standing recovery
In the same weekly step, negative standing with every faction moves 1 back towards zero. Positive standing never decays.

## Saves
- `newGame`: the seed, whether the game is seeded, and the sponsor. A load replaces the scenario's own and recomputes the pirates' lurk order from it.
- `pirateBase`: when it was established, found and fallen. A load finds the base by name.
- `tension`: every pair's value, which pairs have been reinforced, and when the next week falls.
- The sponsor is also in `ServiceRecord`, as before; a load reads the home from it.
- The stations, freighters and stock are saved as the world's entities. A load finds the new stations by name, and a save from before them loads without them.

Not saved: the state of the combat and crew dice. A load does not reseed them from the saved seed, so a loaded seeded career rolls from whatever the running world had (round 2's seeds when loaded from the main menu).

## Tests
- `WorldSeedTest`: same seed same start, another seed another; the designed start as laid out with 10 freighters; seeds give 10 to 14 freighters; every station within a freighter's reach for four seeds; a save keeps the seed and the lurk order; typed seeds.
- `WiderWorldTest`: the new stations' types and owners, the Free Traders at war only with pirates, reachability between all stations, patrol routes covering the new stations.
- `PirateBaseTest`: the base's orbit, trade and clearance; hidden until revealed; found by proximity; growth and gathering at the base; falling when its crew are dead; a save keeps found and fallen.
- `SponsorChoiceTest`: the Patrol by default; a Selene start, board and rank names; only sponsorable factions; flying and warping above the Moon; friendly guards; a save keeps the sponsor and home.
- `FactionTensionTest`: hostile acts raise tension, pirates excluded; high tension reinforces once and refuses rivals; the weekly drift; weeks pass on the sim clock; a save keeps the strain.

## Choices made without Joe
- Freehold sits at 200,000 km, not on the Moon's orbit as first built, because there it never met a transfer window with the lunar stations.
- A seed varies freighter credits, not their starting cargo.
- The designed start keeps round 2's layout and dice, and pirates still lurk by the first three stations first.
- Selene still does not name the pirates an enemy, so a Selene career climbs by contracts and bounties rather than kills.
- No one is cleared at a pirate station.
- All the numbers above are placeholders.

## Not built
- Raiding the base as a contract, and operations that reveal it (category 6 calls `fall` and `reveal`).
- World events that raise tension; only the player's deeds do.
- Patrols for the Free Traders, and a ship market at Anvil Yards.
- Any screen showing tension.
