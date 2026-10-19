# Crew: Hiring, Veterancy and Prisoners

Round 3, category 4. The player's crew are people worth keeping: they are hired at stations, paid each week, get better at what they do, and lose heart when they are unpaid or see shipmates die. Crews of taken ships can be brought home as prisoners and handed over, ransomed, released or, for some outlaws, taken on.

## Implemented

### Crew records (`ai-crew`)
Every crew member has a `CrewRecord` on their `CrewMemberComponent`, saved with them:

- **Skills**, 0 (green) to 1: gunnery (with tracking alongside it), engineering and marine. `CrewRecord.applyTo` turns them into the `CrewDef` the crew AI already works from, so nothing in the AI changed: gunnery and tracking are the def's own skills, engineering scales repair rate, fire fighting and breach sealing, and the marine skill scales small-arms hit chance. A skill of 0.5 leaves the role's def exactly as it was, so crew who have learned nothing work as before (and the round-2 balance holds).
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

The crew AI counts work and hits on the record as they happen (`pendingWork`, `pendingHits`, `pendingDowns`); `CrewCareer` drains them once a second.

### Morale
- Drifts back toward 0.7 by 0.05 an in-game day. The drift's clock is not saved: after a load it starts again from the loaded time (`CrewCareer.restartClock`), so loading a later save into a running game doesn't drift everyone at once.
- Wages paid: +0.02. Unpaid: -0.25.
- A shipmate killed: -0.1 to everyone else aboard. Crew lost with a ship in a rescue: -0.05 each to the survivors.
- A ship beaten or taken: +0.05.

### Hiring (`CrewHiring`)
- Each station of a faction that is no outlaw has a pool of 3 to 5 people looking for a berth, posted each in-game day and seeded by the station and the day like the mission board, so it is the same all day.
- Candidates are crewmen, engineers, marines or medics. Their main skill runs 0.2 to 0.8, the others 0.25 to 0.6. Wage = role base (crewman 150, marine 220, engineer and medic 250 cr a week) × (0.5 + main skill), to the nearest 10.
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

## Not done here
- Medics have no skill of their own; their wage follows the average of their skills.
