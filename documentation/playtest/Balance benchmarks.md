# Balance benchmarks

The game's balance targets (how long the first fight lasts, what a prize pays, how many weeks of pay the gunship costs, whether the frigate wins the raid) were tuned against the scripted career: a bot that flies, fights and trades headless. Its game is not a person's, so these targets are no longer checks in the build. They are optional benchmarks: a tester runs one, reads the table it writes, then plays the same moment by hand and works through its checklist.

Nothing here runs in `./gradlew build`, `test`, `slowTest`, `-PallTests` or CI. The build still checks everything that is a rule or a regression: the careers still play through on CI's seeds, replay exactly and never get stuck. Only whether they hit the tuned numbers moved here.

## Running them

```
./gradlew :core:playtestBenchmark                                          # every benchmark (the careers take a while)
./gradlew :core:playtestBenchmark --tests '*FirstHourBalanceBenchmark'     # one benchmark, about a minute
./gradlew :core:playtestBenchmark --tests '*MidCareerBalanceBenchmark'
./gradlew :core:playtestBenchmark --tests '*CareerPacingBenchmark'         # seed 2026's careers, up to an hour
./gradlew :core:playtestBenchmark --tests '*WeaponDuelBenchmark'           # each Small gun against the raider, a few minutes
./gradlew :core:playtestBenchmark --tests '*CareerPacingBenchmark.round3*' -PcareerSeeds=2026,7,12
```

On Windows, `tests/Balance benchmarks.bat` runs all of them.

Each benchmark prints a table and writes it to `core/build/playtest/<name>.md`: every target, what the game gave, what was wanted, and **on target** or **OFF TARGET**. A benchmark off target is listed as a failed test in the report (`core/build/reports/tests/playtestBenchmark/`), but the run carries on and the task still succeeds. Off target is not a bug: it is a number for the tester to weigh against what they felt in play. If play by hand feels right and the bot is off, the target may be what needs changing.

The career benchmarks also write each career's journal to `core/build/pacing-*.txt`, the bot's account of the whole run.

The surveys are deeper benchmarks of the same kind, run only when asked: the enemy survey (`./gradlew :core:test --tests '*EnemySurveyTest' -PenemySurvey`), the wound survey (`--tests '*CrewWoundSurveyTest' -PwoundSurvey=10`) and the career sweep over many seeds (`--tests '*CareerSweepTest' -PcareerSweep=1-10`).

## Playing the moment by hand

- **The first fight:** `./gradlew :lwjgl3:runFirstPlay`, or First Play on the main menu.
- **Later in the career:** write the scripted career's saves and load them in the game, as the README's "Trying a round's changes" describes (`./gradlew :core:slowTest --tests '*Round5CareerTest'`, then `./gradlew :lwjgl3:runGame -PcareerSaves=core/build/playtest-saves/seed-2026`). Each section below names the save to start from.

Write down what you saw beside the benchmark's table, with the commit you played, so the next tuning pass can compare.

---

## First hour (`FirstHourBalanceBenchmark`)

From round 2's playtest: a 7 s first fight the raider never answered, one laser selling for over twice the bounty, and a promotion after one fight.

| Target | Bot's target |
|---|---|
| The raider is outgunned by the starting corvette | yes |
| The player's ship holds out, and the raider is beaten inside 4 minutes | yes |
| The raider shoots first, and lands hits on the hull | yes, at least 1 hit |
| From the player's first shot to the raider beaten | at least 20 s |
| The raider's laser, stripped and sold | under half the bounty |
| The whole raider stripped and sold | 1 to 3 bounties |
| One bounty and its kill make a Lieutenant | no; two do |

Play First Play through its first fight, then strip and sell the raider.

- [ ] The raider opens fire before you do, and you feel under fire for the whole fight, not only at the start.
- [ ] The fight takes long enough to matter (the bot's is over 20 s from your first shot) but you are never at real risk of losing the corvette.
- [ ] Your hull shows hits afterwards. Note the hull left, beside the bot's.
- [ ] Missiles help but don't end it on their own.
- [ ] Stripping one gun and selling it is clearly worth less than the bounty; stripping the whole raider feels worth the time it takes, but not more than taking bounties.
- [ ] After one fight you are still an Ensign, and the notice or the HUD makes it clear one more will promote you.
- [ ] Note anything that felt off: a fight over too fast, a raider that never closes, a price that makes one choice obviously right.

## Mid career (`MidCareerBalanceBenchmark`)

From round 4's balance pass (a prize paid ten bounties, the gunship was forty weeks of pay away, the ace's gun barely hurt), round 5's frigate price, and round 10's raid against a base with no railgun.

| Target | Bot's target |
|---|---|
| The first raider towed home as a prize | 1 to 2 of its bounties |
| Stripping a prize first pays less than taking it whole | yes |
| The gunship after the corvette's trade-in | 4 to 8 weeks of a Lieutenant Commander's pay |
| The gunship's list price | over its 314,000 cr value (304,000 before its heavy railgun) |
| An ace raider below difficulty 1.5 carries the chaingun Mk2 | yes |
| The Mk2's aim spread and slew against the Mk1's (it fires the same round since Oct 2026) | at most 0.8 the spread, at least 1.25 the slew |
| The frigate's list price against its value | 0 to 5% over |
| A base with no railgun, against the frigate holding where the raid holds | takes at least 0.05 of hull |

Play the career from the start (or from **First ace fight**, then **Gunship**, **Frigate** and **Before the raid**).

- [ ] Towing a prize home is worth the trouble against taking a bounty and leaving the wreck, without being the only sensible choice.
- [ ] Saving for the gunship as a Lieutenant Commander takes a few weeks, and those weeks have things to do. Note the day you could afford it.
- [ ] The ace is frightening: its chaingun lands more of its rounds than a plain raider's, and its crew lead you better.
- [ ] Buying the frigate as a Commander feels like a step up, priced about like the gunship was for its rank.
- [ ] In the raid, the base's guns are a threat to the frigate even without a railgun: the hull drops, and you have to think about where you hold.
- [ ] Note anything that felt off: money piling up with nothing to buy, a ship out of reach for too long, a fight that is a formality.

## Career pacing (`CareerPacingBenchmark`)

Round 3's career, start to the gunship, and round 5's, on to the raid on the pirates' base. CI still plays both on seeds 2026, 7 and 12, and checks they replay and never get stuck; this benchmark checks the pacing.

| Target | Bot's target |
|---|---|
| Round 3: the career reaches the gunship | yes, no fights lost |
| Round 3: standing the script had to grant for a rank | none |
| Round 3: credits the script made up for the gunship | at most 1,060 cr (seed 7: 15,000 cr, see `CareerPacingBenchmark.SHORTFALL_ALLOWED`) |
| Round 5: the career plays through | yes, no fights lost |
| Round 5: the base falls to the raid | yes |

The table also lists, with no target, the day the gunship was bought, the credits prizes brought in, and round 5's milestones (Gunship, Commander, Frigate, Captain) with their day, rank, credits, ship and difficulty. Round 9 aimed for the frigate by day 52.

Play a career from a new game, at your own pace, taking the contracts you would take.

- [ ] Each promotion arrives from what you did in play. Note the day of each, beside the bot's milestones.
- [ ] You can afford the gunship when your rank allows it, or soon after; you never wait weeks docked with nothing to do.
- [ ] Note the day you bought the gunship and the frigate, beside the bot's days.
- [ ] Commander to frigate takes the rank's waiting time and a few contracts, not a grind.
- [ ] The raid on the base is a real fight that the frigate can win: note your hull afterwards, and how many tries it took.
- [ ] Losing a fight sets you back without ending the career.
- [ ] Note where the career dragged or rushed, by day, and what you were doing then.

## Weapons (`WeaponDuelBenchmark`)

From Joe's speed rule (Oct 2026): within a mount size, a faster round means less damage a second, so a railgun is the gun to have at range and a cannon the gun to have in a brawl. The First Play corvette keeps only its nose mount, fitted with each Small gun in turn, and fights the First Play raider from a held 4 km and 1.5 km. The raider fights back with its chaingun, which reaches 3 km.

| Target | Bot's target |
|---|---|
| From 4 km, who beats the raider soonest | the railgun, then the coilgun, then the chaingun |
| From 1.5 km, who beats the raider soonest | the chaingun, then the coilgun, then the railgun |
| From 4 km, the share of rounds that land | the railgun's above the chaingun's |

The table also lists each duel: beaten or not, how long it took, both hulls, and rounds landed of fired.

Refit the corvette's nose at Highport with each gun in turn (or load a later save and fit the gunship), then pick a fight with a raider at range and then up close.

- [ ] At range the railgun is the gun you want: its slugs land, and a cannon's rounds spray past.
- [ ] Once a brawl closes in, you wish you had the cannon: the railgun's slow fire can't keep up.
- [ ] The coilgun feels like the all-rounder it is meant to be, without being the obvious buy.
- [ ] A Mk2 feels like a better mount for the same round: it gets onto a target faster and holds it tighter.
- [ ] A heavy shell's hit shreds the room behind the hull; a railgun slug drills a neat line.
- [ ] Note any weapon you never wanted to buy, or never wanted to sell.

