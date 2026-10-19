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

## Not done here
- No medbay or berth tiles exist in the ship data yet, so the wounded are treated where they lie once on a safe tile, rather than carried to a medbay.
- Light wounds are not treated, only left to heal.
