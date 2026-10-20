# Contracts: New Jobs, Operations and World Events

Round 3, category 6. The mission boards get rescues, recoveries, convoys, station defences, recon and a raid on the pirates' base. Factions offer operations, short chains of contracts from the data, at rank milestones. Once a day the world may have something happen on its own (a shortage, a raid, a lost freighter, a rival flexing), and each such event posts jobs. A pirate that has robbed freighters says so in its bounty and is worth more. Being rescued now has a price. The rules from round 2 are in [Design.md](Design.md) ("Contracts and the Mission Board").

## Implemented

### Pods and wrecks (`Derelicts`)
A freighter that is lost, or robbed, can leave an escape pod and a wreck on the rails. Each is a plain saved record riding a Kepler orbit around a body, cast off from the ship drifting at 3 m/s. A pod's air lasts 4 days; a wreck is tracked for 10. Within 200 km of the player, around the same body, one gets a HUD marker, and the traffic map shows them all (`RailsTrafficMap`). A pod or wreck is reached with a slow close pass: within 2 km at under 30 m/s relative.

### New contract types (`Contract`, `ContractBoard`)
Jobs posted outside the daily boards (by events, pods and wrecks) go through `ContractBoard.postEvent` and stay on their station's board until taken or late. A job can carry a `reason`, shown under the offer in the mission window.

| Type | Pay and standing | How it is done |
|---|---|---|
| Rescue | 1,500 cr + 400 per survivor, +5 | Pick the pod up with a close pass, then dock at the station that posted it before the air runs out. From round 5 the survivors come aboard as people in free berths (`SurvivorComponent`), as many as the ship has room for; each one alive when they go ashore at the giver is paid for, and if none lived the rescue fails. Survivors still aboard when a rescue ends some other way go ashore at the next station the ship docks at. |
| Recovery | 30% of the cargo's price at the posting station, at least 1,000 cr, +4 | Hold alongside the wreck for 60 s while the crew carry the cargo into the hold, then hand it over docked. A hold that takes only part of it is paid for that part. Up to 5 days. |
| Convoy | 1,200 cr a freighter (×3 to another body's orbit), +3 + 2 a freighter | Two or more freighters hauling out of the station to the same buyer. Each that docks with the player within 50 km counts; one docking without the player (after 30 minutes' grace) or lost does not. Pay and standing are shared by how many counted; none fails it. |
| Defend | 4,000 cr a raider × difficulty, +4 + 3 a raider, 2 days | Posted by a raid event. Each raider beaten (or gone) counts; at the deadline those beaten are paid for, and none fails it. |
| Recon | 1,500 cr (×3 far), +3, 3 days (8 far) | Come within 100 km of the pirate where it lurks. |
| Raid | 40,000 cr × difficulty, +15, 6 days | Offered only by the sponsor, from Commander (`RAID_RANK` = 3; Lieutenant Commander in round 3), once the pirates' base is found and while it stands. The base scrambles its wing as the player closes (see [World.md](World.md)); done when the base falls. |

The board's daily mix now also draws convoys. From round 5 the raid heads the board just after the operations, so a full board never drops it.

Round 5 bounty rules:
- A bounty whose ship was beaten, is gone or is already being hunted (by an operation's step, say) leaves the board, and taking it is refused.
- A named ace's bounty no longer adds the robbery premium on top of the ace's own (the two stacked to twelve times a bounty); its record is still shown.
- A ship worth less than a third of the player's (`WEAK_RATIO`) pays half the standing.
- From Commander (`SENIOR_RANK`), every job but an operation or a raid pays half its standing (`SENIOR_STANDING`): a senior officer climbs on operations and raids. An operation's own reward is untouched.

### Operations (`assets/data/operations.toml`, `OperationDef`)
`[operation.<id>]` has `name`, `text`, `giver` (empty means the sponsor), `rank`, `reward`, `standing` and `steps`, a list of `patrol`, `recon`, `bounty`, `escort`, `convoy`, `delivery`, `intel` or `raid`. `DefValidator` warns of an unknown giver, rank or step, or no steps.

| Operation | Rank | Steps | Reward |
|---|---|---|---|
| Clear the Lanes | Lieutenant | patrol, recon, bounty | 8,000 cr, +6 |
| Lifeline | Lieutenant Commander | escort, recon, bounty | 15,000 cr, +8 |
| Supply Line | Lieutenant | delivery, convoy | 6,000 cr, +5 |
| Safe Harbour | Lieutenant Commander | convoy, patrol, delivery | 14,000 cr, +8 |
| Hunt the Hunters | Commander | recon, bounty, intel, raid | 30,000 cr, +10 |
| Iron Escort | Commander | convoy, recon, bounty, convoy | 28,000 cr, +10 |
| The Long Watch | Captain | patrol, recon, bounty, recon, bounty, delivery | 45,000 cr, +12 |

All are given by the sponsor (`patrol`, or empty for whoever is served). From round 5 a board shows at most 2 operations (`OPERATIONS_SHOWN`), the newest rank's first, and a promotion replaces those it showed with the new rank's; none shows while one runs. An operation the player has the rank for heads the giver's boards each day, until done. A board remembers the rank it was posted at, so a promotion adds the operations it opens to that day's boards at once. Only one runs at a time. Taking it posts its first step, already taken; each step is a contract paid as usual, chosen from the nearest jobs of its kind, and its steps do not count against the limit of 3 held contracts. A bounty step is on the pirate the recon before it found, if it is still out there. An intel step is a recon whose pirate leads the player to the base (`PirateBase.reveal`); it is skipped once the base is known. A convoy step falls back to an escort. Finishing the last step pays the reward and standing. Failing or abandoning a step fails the operation at half its standing; a step called off, or no job of a step's kind to be had, calls it off at no cost. A done operation is never offered again.

### World events (`WorldEvents`)
Rolled once a day as the day turns, from `new Random(NewGame.mix(seed, day))` (a SplitMix of the two; plain `seed * K + day` left some careers without a single event): a 60% chance of an event, of a kind tried in shuffled order until one the world can give. The first day of a career is quiet, and days skipped in warp bring one roll. A seeded new game sets the seed from its own stream 6; otherwise it is `0x5EED0006`.

- **Shortage:** a friendly station loses 85% of a commodity it buys. Every other friendly station holding 10 or more posts an urgent delivery there: as much as the player's hold takes, at 1.5× the short station's price, with a fee of 15% of the value (at least 500 cr), +5, 3 days. It needs no trade order.
- **Raid:** 1 or 2 raiders join the pirates lurking by a friendly station (`HostileTraffic.raid`), and it posts their defence.
- **Freighter lost:** a hauler under way and not under contract is destroyed. It leaves a pod of 2 to 5 survivors and, with cargo aboard, a wreck holding what the player's hold could carry. The station it was heading to (or another friendly one) posts the rescue and recovery, and a new hauler of the same name is back on the lanes 3 days later.
- **Tension:** a rival faction with stations flexes, and its tension with the sponsor rises by 6 (`FLEX_STRAIN`). Each of the sponsor's stations posts a patrol out to one of the rival's: 6,000 cr, +8, 11 days. If the two are at war, bounties go up on the rival's ships: 10,000 cr × difficulty, +9, 3 days.

Apart from the roll, a freighter a pirate robs puts 1 to 3 of its crew in a pod one time in three, and a rescue is posted. Each event goes in a log of the last 50 and is posted as a notice.

From round 5 events strain the factions ([World.md](World.md), "Faction tension"): a raid on a station raises its owner's tension with every other lawful faction that has stations by 2 (`RAID_STRAIN`), a lost freighter by 2 (`LOST_STRAIN`) and a robbed one by 0.5 (`ROBBED_STRAIN`), each side suspecting the other of letting the pirates through.

### Bounty records
A pirate's bounty names what it has done from the robbery count the sim keeps on `HostileComponent` ("robbed 2 freighters, last of Water"). Each robbery adds 50% to the pay and 1 to the standing, counting up to 4.

### What a rescue costs (`LoopWiring`)
Being rescued costs 5 standing with the sponsor (`RESCUE_COST`) and fails every contract held, a running operation's step with it.

## Saves
Saved: posted event jobs, the running operation (its step and the pirate its recon found), the operations done, and every new contract field (convoy and defence marks, convoy start trips and arrivals, recovery time alongside, the intel flag, reason and record). `Derelicts` saves each pod and wreck. `WorldEvents` saves its seed, the last day rolled, the log and the haulers due to replace lost ones. Raiders a raid brings are saved as pirates. A save from before world events loads with the default events seed (`WorldEvents.DEFAULT_SEED`), not the loading world's. Not saved: contract news not yet shown, and derelict orbits and markers, which are rebuilt.

## Tests
`Round5LateCareerTest` covers the base raid end to end, survivors aboard (berths, partly dead, all dead, a failed rescue going ashore, a save), the new operations, the operations cap, a delivery step and the frigate's price. `Round5CareerTest` plays round 3's career to the gunship and on through an operation, bounties, the frigate and the raid; its table is in `planning/round5-career.md`.

`Round3ContractsTest` covers rescue (done and out of air), recovery, derelict markers, a half-paid convoy, defence (done and out of time), Clear the Lanes start to finish, abandoning a step, the raid, Hunt the Hunters' intel step, bounty records, the rescue cost, the offer's note, a save and load of pods, an operation and posted jobs, and valid operations data. `WorldEventsTest` covers seeding, the quiet first day, shortages, urgent deliveries, a lost freighter and its replacement, a robbed freighter's pod, and tension.

## Choices made without Joe
- **At most one event a day, at 60%,** with a quiet first day and one roll after a long warp.
- **One operation at a time,** its steps outside the 3-contract limit, each posted already taken.
- **A failed operation costs half its standing** on top of the step's own; a step called off costs nothing.
- **Intel is skipped once the base is known,** and a convoy step becomes an escort when there is no convoy.
- **The raid needs Commander** (round 5; Lieutenant Commander in round 3) and comes only from the sponsor; an operation's raid step needs no rank of its own.
- **Urgent deliveries have no trade order** behind them; the giver pays the agreed price.
- **A lost hauler is replaced after 3 days.**
- **A rescue costs 5 sponsor standing** and fails all contracts, as the plan proposed.
- Round 5: survivors take free berths and only as many board as there is room for; the rest stay in the pod.
- Round 5: at most 2 operations on a board, the senior halving from Commander, the weak-target halving below a third of the player's ship value, and the operations' rewards above.
- All the numbers above are placeholders.

## Not built
- Wreck cargo crosses on a 60 s timer, not by crew walking it over as salvage does.
- Survivors aboard do no work and need no care; they are passengers.
