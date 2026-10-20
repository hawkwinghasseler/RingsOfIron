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
- **Notices:** a crew member (or a prisoner aboard) newly wounded: light is news, serious a warning, critical important, with the time left to treat it, told again with the new time when another hit cuts a critical casualty's clock (round 8). The captain's own figure reads "You are". A death from wounds reads "Ada has died of wounds". While anyone aboard is critically wounded, the hints name the crew window.
- **Prisoners:** wounded enemy crew are held like any other once their ship is taken, so with hits wounding rather than killing there are more of them. A ship taken with critically wounded prisoners says so: they are treated once brought aboard the player's ship (the crew of the taken ship are all held and treat no one).
- **Boarders down** aboard a ship when it is taken or the boarding is recalled are carried back aboard their own ship.
- **A hostile breaking off under fire surrenders** once its own crew are mostly down, as one still fighting does (`ShipDisablement`): wounded crews are rarely all dead, so a raider whose crew are down no longer just runs.
- The crew window lists each crew member's and prisoner's condition ("Light wound 100%", "Critical wound, 4:10 to live", "Serious wound, being treated") and the medical stores aboard.

### The captain in a holed ship (round 6)
The player's own figure has no behaviour tree, so `CaptainSafety` does for it what the crew's tree does for them:

- **Battle stations:** while the ship is at battle stations with suits on (the crew window's toggle), the figure suits up too, in the crew's suit time, and keeps the suit on; calm again on air fit to breathe, it takes it off as the crew do.
- **Losing air:** on a tile whose air is no longer fit to breathe the figure pulls its suit on, the player hears "The air here is going: you pull your suit on. Get to a pressurised room", and a hint ("walk to air") stays up while it lasts. Walking to air is the player's to do; the suit's air runs out.
- **Down anyway:** the crew carry the figure to the nearest breathable tile like any casualty (`RescueTask`, priority 90, above fires and breaches), or put its suit on where it lies when nowhere aboard has air. Being carried home to a station stays the last resort, when the figure dies.
- The suit and its air are saved with the figure; a suit half on when the game is saved starts again after a load (a crew member's suit half on or off at work is saved, round 9), and a carry under way is set down and taken up again (as for any crew member).

### Railgun lethality (round 6 part 3)
The round 5 ace survey had the railgun ace killing 29 of 30 crew. Traced fight by fight (`CrewWoundSurveyTest -PwoundTrace`), the deaths were not untreated wounds on a ship without a medic:
- **Everyone went down together.** An engineer sealing a breach at the bow was hit; whoever came to help her stood on her tile, and every slug through that tile struck everyone on it for a critical wound. A round crossing a tile now strikes each person on it 40% of the time (`PenetrationResolver.CREW_HIT_CHANCE`), fragments and spall alike.
- **The ace went on firing** into the disabled corvette for the survey's ten minutes after the fight (300 to 500 hits). A pirate now leaves a beaten player drifting ("Raider leaves you drifting") and flies off with its guns cold (`HostileTraffic.fly`). Beaten means `FirstPlay.beaten`: a player dark only for an ion stun is not, and the fight goes on.
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
- **A pirate leaves a beaten player** rather than firing on. (Round 7 part 4: one in good shape boards first.)
- **Stabilise:** 15 s, one medical store, anyone; a medic in half the time.
- **Suit refill:** at 30% of the suit's air, 20 s, at an oxygen tank or working life support.
- **No medic in the starting crew, no medbay, no change to penetrator falloff past the first tile:** the target (8 or fewer of 30) was met without them. The plan's steps 2 to 4 are left for Joe.

### Crew care (round 7 part 4)
How it runs aboard is in `ai-crew/Design.md` section 5; on the game's side:
- **Light wounds are treated** once the ship is off battle stations: whoever has nothing else to do takes it, a medic keenest, at half the care rate and with no stores (`CrewDef.LIGHT_CARE`). It gives way to any other work, and crew at a console stay there. Alone and idle, the wounded see to it themselves. A light wound that took 30 minutes to heal now takes a medic 3 minutes.
- **Medbays:** the gunship and frigate each have four beds (`m` in `chassis.toml`, the `MEDBAY` floor covering in `tiles.toml`). Once a fight is over, the wounded are carried to a free bed, where treatment runs 1.5x (`CrewDef.MEDBAY_CARE`). The corvette keeps its crew room.
- **Hiring** shows a medic as "Medic (2.5x care)", and adds ", none aboard" when the crew has no medic. The starting corvette still has none (round 6 found it not needed).
- **Pirates board a beaten player** (`PirateBoarding`). A pirate that beats the player and still has 70% of its hull comes alongside with its guns cold and latches on within 300 m. After 60 s it has taken everything in the hold (into its own hold where it fits) and 10% of the credits, at most 5,000 cr, and it leaves. Notices say it is coming, that it has latched on, and what it took. There is no fight on the interior grid, and the crew aren't harmed. One pirate boards at a time; the rest, and any pirate below the line, leave the player drifting as before. A boarding in progress is saved.
- **Round 8 (part 4): boarders under fire, and none at a berth.** Any hit from the player's ship on the boarder after its boarding began ends it (`PirateBoarding.BREAK_OFF_HITS` 1): it casts off with nothing ("breaks off its boarding under fire") and doesn't board again until the player is back on their feet. A pirate never starts a boarding on a docked ship or one in tow, and one in progress ends when the player docks or is taken in tow, without moving the ship off its berth. Who was driven off, the hits and when the boarding began are saved with it.

### Crew care after a fight (round 8 part 5)
Round 7's wound survey left crew wounded ten minutes after a won ace fight. Traced with `CrewWoundSurveyTest -PwoundTrace`, the causes were:
- **Battle stations never ended.** The ship's guns fired on by themselves at the beaten ace every few seconds (once a ship's power comes back, `ShipDisablement` makes all its mounts weapons free), and each shot restarted the 60 s hold. Fire at a ship already disabled no longer holds battle stations (`CrewMedical`). The guns firing by themselves was left as it was here; the round 8 integration fixed it: power back, the player's main guns wait for the trigger again and only point defense fires by itself (`PlayerGunnery.holdMainGuns`; on seed 12 the frigate kept coming loose from Highport's berth after a loss, most likely from the recoil, and docks once this is in).
- **The one hand free sat at the guns.** A crew member at a console never leaves it for a light wound, so a lightly wounded gunner went untreated while the others saw to a worse patient. Off battle stations, a hand at a console now sees to their own light wound there.
- **The rate.** A light wound is first aid: anyone treats it at a medic's rate for their skill (`CrewMemberComponent.careRate(Wound)`), at the full rate unless a worse wound aboard waits with nobody treating it (then half, `CrewDef.LIGHT_CARE`). Still no stores, still only off battle stations, and still no medic on the corvette.
- **Suits.** A carer kept their battle stations suit on (care at 0.8) the whole ten minutes; once battle stations are over and the air is good, they take it off to work, as the idle crew already did.

The yard puts the air back: a repair order ends with every room at station pressure and the standard oxygen share, from the station's air, at the refill's prices (oxygen 2 cr/kg, nitrogen 1 cr/kg) inside the repair bill (`StationYard.topUpAir`). Round 7's "Before the Moon trip" save loaded at 4 kPa because the yard mended the hull and left life support to refill the cabin. `YardAirTest` holes the corvette at Highport and checks every room is breathable when the yard finishes.

| Ace refit (10 fights each) | Our crew dead | Still wounded 10 min on | Light wounds open 10 min after a win |
|---|---|---|---|
| raider_ace_chaingun, round 7 | 3 of 30 | 4 | 2 |
| raider_ace_chaingun, round 8 | 3 of 30 | 0 | 0 |
| raider_ace_railgun, round 7 | 3 of 30 | 12 | 1 |
| raider_ace_railgun, round 8 | 3 of 30 | 10 | 0 |

The railgun ace wins 8 of its 10 fights, and its boarders and the beaten corvette's vacuum keep the wounds after a loss open.

Later in part 5, from the careers and part 6's playtest:
- **Nobody dies of a leak at a berth.** With nowhere aboard safe, the crew pull their suits on, then go aboard the docked station, carrying the wounded; the captain's figure is walked across too once its suit is down to four minutes (`CaptainSafety.ASHORE_AT`, when the air warning shows), and home again once the ship can be breathed, unless the player steers it themselves; station hands (loaders and technicians, first aid at half a medic's rate) see to them there. Once the ship holds air again, anyone left down ashore is carried back aboard (`BerthVenting`). While a yard works on a ship, its berth clamps hold it however hard it vents (`StationYard.holds`): seed 2026's holed gunship tore loose again and again, and each tear called the repairs off.
- **A burst oxygen tank no longer leaves the ship unbreathable for good.** Each life support unit keeps 60 kg of its own oxygen (`LifeSupportSystem.EMERGENCY_OXYGEN_KG`, per unit of throughput), drawn once its tanks are dry: enough to bring a corvette's cabin back from 0% and fill the suits many times. It is saved, and a refill makes it good.
- **"Air running out" notices** (`AirWatch`): one when the oxygen tanks are dry, with what life support has left, and one, kept up to date, when a suit has four minutes or less and nothing aboard can fill it. With the tanks dry, the ship's oxygen gauge shows life support's emergency oxygen in red ("EMERGENCY 42 kg") instead of 0%.
- **First aid in thin air.** A light wound gets first aid wherever the patient breathes from a suit on a tile that holds air and isn't burning or hot, not only on safe deck: part 6's cabin, slow to come back on emergency oxygen, left a graze open for ten minutes.
- **The crew keep out of where the fire fell.** At battle stations, a crew member's weapon wound marks the tiles within two of it, and nobody takes or keeps work there until the ship stands down, except a casualty with two minutes or less to live, bleeding out or down in bad air with that much suit air or none (`TaskRules.waitsOutFire`). Seed 7's ace raked one hull section and all four hands went in after each other and bled out. Consoles, orders and damage control on what the ship fights with (resetting a short, repairing the reactor, shields, guns, helm, weapons console or engines: `TaskRules.vital`) never wait: kept clear of the corvette's shorted power, seed 2026's crew lost Red Kestrel in 69 s, a fight they win in 37 s with it.
- **A station's infirmary treats the wounded at its berth.** Docked at a station whose crew are no enemies of the player's, off battle stations, the player's crew who are down or seriously or critically wounded are treated by the station's medics at a medbay's pace, a critical wound stabilised at once, until they are on their feet; each wound level is billed at 300 cr (critical), 150 cr (serious) or 50 cr (light) times the station's prices (`StationInfirmary`). It runs on sim time, so time warp at the berth treats them too: the crew's own care stops in warp, and seed 2026's crew, towed home from a lost ace fight with one hand left up and no stores, lay untreated at Highport for days and two of them died.

Round 9 (part 2), from a trace of every asphyxiation in the wound survey:
- **A hand at a console suits up there.** At battle stations, or as their room loses air below 50 kPa (`TaskRules.SUIT_AT_POST_KPA`), a hand at a console puts their suit on where they sit and keeps the console manned. The gunner had sat at the weapons since before the fight began, never suited up for it, and suffocated there on four seeds.
- **A casualty's suit is filled for them.** Down in a suit in a ship with nowhere left to breathe, a casualty is carried to an oxygen tank or the life support once their suit is low, and it is filled there. Six of the survey's dead lay suited beside full oxygen tanks until their suits ran dry.
- A fight saved part way through keeps its raked tiles, and a suit half on or off stays so.
- Both are for the player's crew only (`TaskRules.SUIT_CARE_FACTION`): the aces' crews given the same care won the railgun ace four more of twenty fights.

## Not done here
- A boarding fight on the interior grid: the pirates only take the cargo and credits (round 7 plan, "Still parked").
- The starting corvette has no medic (Ada, Bea and Cole are an engineer, a crewman and a marine), and no medbay.
