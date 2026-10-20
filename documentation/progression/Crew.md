# Crew: Hiring, Veterancy and Prisoners

Round 3, category 4. The player's crew are people worth keeping: they are hired at stations, paid each week, get better at what they do, and lose heart when they are unpaid or see shipmates die. Crews of taken ships can be brought home as prisoners and handed over, ransomed, released or, for some outlaws, taken on.

## Implemented

### Crew records (`ai-crew`)
Every crew member has a `CrewRecord` on their `CrewMemberComponent`, saved with them:

- **Skills**, 0 (green) to 1: gunnery (with tracking alongside it), engineering, marine and medicine (round 5). `CrewRecord.applyTo` turns them into the `CrewDef` the crew AI already works from, so nothing in the AI changed: gunnery and tracking are the def's own skills, engineering scales repair rate, fire fighting and breach sealing, the marine skill scales small-arms hit chance, and medicine scales how fast they treat the wounded (`CrewMemberComponent.careRate`). A skill of 0.5 leaves the role's def exactly as it was, so crew who have learned nothing work as before (and the round-2 balance holds).
- **Morale**, 0 to 1, starting at 0.7. Below 0.25 work slows to 60%.
- **Wage** in credits a week; 0 for crew the service put aboard.
- **History**: ships beaten while aboard, ships boarded and taken, enemies downed, hours of engineering work.

A crew member saved before records existed gets one made from their def on first use (`CrewMemberComponent.record()`), so old saves load.

### Veterancy (`CrewCareer`)
Experience moves a skill a share of the way to the cap of 0.95, so it rises quickly at first and ever more slowly.

| Skill | Learned from | Per |
|---|---|---|
| Gunnery | each hit of the ship's guns, for whoever mans the weapons console | 0.004 |
| Engineering | each second of repairs, fire fighting, sealing breaches, forcing doors and yard or salvage work | 0.0008 |
| Marine | each small-arms hit / enemy downed / ship boarded and taken | 0.01 / 0.03 / 0.05 |
| Medicine | each second treating the wounded | 0.0008 |

The crew AI counts work and hits on the record as they happen (`pendingWork`, `pendingHits`, `pendingDowns`, `pendingCare`); `CrewCareer` drains them once a second.

### Morale
- Drifts back toward 0.7 by 0.05 an in-game day. The drift's clock is not saved: after a load it starts again from the loaded time (`CrewCareer.restartClock`), so loading a later save into a running game doesn't drift everyone at once.
- Wages paid: +0.02. Unpaid: -0.25.
- A shipmate killed: -0.1 to everyone else aboard. Crew lost with a ship in a rescue: -0.05 each to the survivors.
- A ship beaten or taken: +0.05.

### Hiring (`CrewHiring`)
- Each station of a faction that is no outlaw has a pool of 3 to 5 people looking for a berth, posted each in-game day and seeded by the station and the day like the mission board, so it is the same all day.
- Candidates are crewmen, engineers, marines or medics. Their main skill (a medic's is medicine) runs 0.2 to 0.8, the others 0.25 to 0.6. Wage = role base (crewman 150, marine 220, engineer and medic 250 cr a week) × (0.5 + main skill), to the nearest 10.
- Hiring needs the player docked with clearance, a berth free and a week's wage up front. The hire walks aboard at once. Who has been hired from today's pools is saved (`"crew-hiring"`); a load clears it first, so a save from before hiring shows the day's pools whole.
- Crew can be let go while docked; they leave the game (the entity is removed).
- **Berths:** twice the ship's escape pod seats, at least 4. The player and prisoners take berths too. The corvette (one 4-seat pod) berths 8, so a full crew can't all get away in the pod: that is the player's call.

### Wages
Each payday, right after the budget is paid in (`ServiceBudget.paydays`), every hired crew member's wage comes out of the ship's credits in roster order, as far as they go. Those not paid lose morale.

### Prisoners (`Prisoners`)
- Latched to a ship the player has taken, "Bring prisoners aboard" moves its held crew onto the player's ship, as berths allow. They stay held: they neither work nor fight.
- Docked with clearance:
  - At a sponsor station, prisoners of a faction the sponsor fights (outlaws included) are **handed over**: 400 cr and +1 standing with the sponsor each.
  - At a station of the prisoner's own faction they are **ransomed**: 600 cr and +2 standing with that faction each.
  - Anywhere else they are **released**, for nothing. (The code also has +1 standing for a release at their own station, but there they are always ransomed, so it never happens.)
- One outlaw in three **offers to join**. A recruit joins with every skill at 0.3, morale 0.5 and a wage to match.

### Rescue
Crew who reach a pod keep their record: skills, wage and history come aboard the new ship with them (`AbandonShip.Survivor.record`). Those lost can be replaced by hiring.

### The crew window (I)
`CrewWindow` in ui-hud, fed by `CrewRoster`: berths and the week's wages; each crew member's role, skills, condition, morale, wage and history, with "Let go" while docked; the prisoners aboard with what can be done with them here and "Recruit" for those who offer; "Bring prisoners aboard" while latched to a taken ship; and, docked at a station that hires, who is looking for a berth with "Hire". Its key hints go through the hint registry: "crew, hire" while docked, "prisoners" while latched to a ship the player has taken, and "crew" anywhere.

## Choices made without Joe
- **Who pays the starting crew:** the service. Ada, Bea and Cole (and crew requisitioned with a ship) draw no wage from the player; only hired crew do. This keeps the round-2 budget balance.
- **A week's wage up front** to hire, so hiring and letting go at once costs something.
- **Berths are twice the pod seats**, at least 4, counting the player and prisoners.
- **Hires walk aboard at once**, rather than walking over from the station.
- **Prisoners travel by being moved aboard** the player's ship, not walked across.
- **Low morale only slows work.** No one deserts or mutinies yet.
- **Skill at 0.5 is the role's def unchanged**, so existing balance and tests hold.
- All the numbers above are placeholders.

Crew news goes to the notice log (`world.notices`): a death is important, unpaid wages a warning, the rest news.

### Wounds, medics and suits (round 5)
How wounds work aboard is in `ai-crew/Design.md` section 5. On the game's side (`CrewMedical`):
- **Medics** have their own skill, medicine, and their wage follows it like any other role's main skill.
- **Battle stations:** a ship is at battle stations while an NPC captain has it as their target, while it fights one itself, and for 60 s after any shot it fires or takes. Its crew suit up then. The crew window shows "Suits at battle stations: on" with a button to change it for the player's ship.
- **Notices:** a crew member (or a prisoner aboard) newly wounded: light is news, serious a warning, critical important, with the time left to treat it. A death from wounds reads "Ada has died of wounds". While anyone aboard is critically wounded, the hints name the crew window.
- **Prisoners:** wounded enemy crew are held like any other once their ship is taken, so with hits wounding rather than killing there are more of them. A ship taken with critically wounded prisoners says so: they are treated once brought aboard the player's ship (the crew of the taken ship are all held and treat no one).
- **Boarders down** aboard a ship when it is taken or the boarding is recalled are carried back aboard their own ship.
- **A hostile breaking off under fire surrenders** once its own crew are mostly down, as one still fighting does (`ShipDisablement`): wounded crews are rarely all dead, so a raider whose crew are down no longer just runs.
- The crew window lists each crew member's and prisoner's condition ("Light wound 100%", "Critical wound, 4:10 to live", "Serious wound, being treated") and the medical stores aboard.

### The captain in a holed ship (round 6)
The player's own figure has no behaviour tree, so `CaptainSafety` does for it what the crew's tree does for them:

- **Battle stations:** while the ship is at battle stations with suits on (the crew window's toggle), the figure suits up too, in the crew's suit time, and keeps the suit on; calm again on air fit to breathe, it takes it off as the crew do.
- **Losing air:** on a tile whose air is no longer fit to breathe the figure pulls its suit on, the player hears "The air here is going: you pull your suit on. Get to a pressurised room", and a hint ("walk to air") stays up while it lasts. Walking to air is the player's to do; the suit's air runs out.
- **Down anyway:** the crew carry the figure to the nearest breathable tile like any casualty (`RescueTask`, priority 90, above fires and breaches), or put its suit on where it lies when nowhere aboard has air. Being carried home to a station stays the last resort, when the figure dies.
- The suit and its air are saved with the figure; a suit half on when the game is saved starts again after a load, and a carry under way is set down and taken up again (as for any crew member).

### Railgun lethality (round 6 part 3)
The round 5 ace survey had the railgun ace killing 29 of 30 crew. Traced fight by fight (`CrewWoundSurveyTest -PwoundTrace`), the deaths were not untreated wounds on a ship without a medic:
- **Everyone went down together.** An engineer sealing a breach at the bow was hit; whoever came to help her stood on her tile, and every slug through that tile struck everyone on it for a critical wound. A round crossing a tile now strikes each person on it 40% of the time (`PenetrationResolver.CREW_HIT_CHANCE`), fragments and spall alike.
- **The ace went on firing** into the disabled corvette for the survey's ten minutes after the fight (300 to 500 hits). A pirate now leaves a beaten player drifting ("Raider leaves you drifting") and flies off with its guns cold (`HostileTraffic.fly`).
- **Nobody stopped the bleeding.** Anyone now stabilises a critical wound (ai-crew Design.md, section 5), before a rescue that may have nowhere to go.
- **The suits ran dry.** Holed past its patch kits, the beaten corvette held no air anywhere; its crew now fill their suits at an oxygen tank or the life support.

| Ace refit (10 fights each) | Beaten | Our crew dead | Wounded | Before (round 5) |
|---|---|---|---|---|
| Chaingun | 10 | 4 of 30 (0 with part 1's breach sealing) | 3 | 6 of 30 |
| Railgun | 2 | 3 of 30 (5 with part 1) | 9 | 29 of 30, beaten 1 |

The fights stay a risk: the railgun ace still beats the corvette 8 of 10, with our hull at 0.44 to 0.77 as before. Part 4's missile fix (#122) leaves these numbers where they were: 29 of 30 railgun deaths on it alone, 3 of 30 with this part, 5 of 30 chaingun. Round 3's seed 2026 career loses no crew (4 aboard throughout).

Found on the way: a deck plate shot down to nothing (no hull wall, so no breach to seal) took patch after patch, since a patch put on at condition 0 read as none, until the stores were empty and the ship vented at its berth for good. A patch now holds there (`Tile.patchMark`).

Choices made without Joe:
- **A round strikes 40% of the people on a tile it crosses.** A body fills well under half of a 1 m tile. It lowers every kind of hit on crew, not only the railgun's.
- **A pirate leaves a beaten player** rather than boarding them or firing on. Pirates boarding the player is not built.
- **Stabilise:** 15 s, one medical store, anyone; a medic in half the time.
- **Suit refill:** at 30% of the suit's air, 20 s, at an oxygen tank or working life support.
- **No medic in the starting crew, no medbay, no change to penetrator falloff past the first tile:** the target (8 or fewer of 30) was met without them. The plan's steps 2 to 4 are left for Joe.

## Not done here
- No medbay or berth tiles exist in the ship data yet, so the wounded are treated where they lie once on a safe tile, rather than carried to a medbay.
- Light wounds are not treated, only left to heal.
- The starting corvette has no medic (Ada, Bea and Cole are an engineer, a crewman and a marine).
