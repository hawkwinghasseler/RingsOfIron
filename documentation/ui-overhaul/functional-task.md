# UI Overhaul: Task-Driven Functional Spec

Functional designer 2 of 3. Round 13.

This spec rebuilds the game's interface from the decisions a player makes, not from the panels the game has today. It says what each widget and screen contains, where it sits, and when it is visible. It says nothing about how anything looks: three artist threads (Nasa-punk, 90s VFD, Cassette-futurism) skin it, and section 12 lists what they are free to change and what they must keep.

**The result in one paragraph.** The screen follows where the captain is. On foot, the HUD is the ship around you and what is wrong with it. Seated at the helm, it is your motion. Seated at the weapons, it is the fight. Every screen-fixed widget has one fixed zone: time and alerts across the top, your own ship on the left, other ships on the right, your motion and your hands at the bottom centre, and the middle of the screen left to the world. Nothing shows while it has nothing to say: at rest in flight the HUD is a clock, a row of dark status lamps, one flight instrument and a scope. Urgent things speak through one alert band, ranked, and each alert names what to do. The windows the game has grown (market, yard, shipyard, missions, crew, boarding, notice log, controls) fold into three screens: the **Map** for getting somewhere, the **Station terminal** for everything a station sells, and the **Ship's book** for your ship, crew, contracts, career and log.

Contents:
1. [Method](#1-method)
2. [Where the player can be](#2-where-the-player-can-be)
3. [The decisions](#3-the-decisions)
4. [What the decisions say about information](#4-what-the-decisions-say-about-information)
5. [Screen zones](#5-screen-zones)
6. [Widgets](#6-widgets)
7. [Screens and panels](#7-screens-and-panels)
8. [Visibility rules](#8-visibility-rules)
9. [Alerts, lamps and news](#9-alerts-lamps-and-news)
10. [Input, focus and closing things](#10-input-focus-and-closing-things)
11. [Audit of the current UI](#11-audit-of-the-current-ui)
12. [Notes for the artist threads](#12-notes-for-the-artist-threads)
13. [What the game needs to expose](#13-what-the-game-needs-to-expose)
14. [Open questions for Joe](#14-open-questions-for-joe)
15. [Revision log](#15-revision-log)

---

## 1. Method

1. Read every design document under `documentation/` and list the contexts a player can be in (section 2).
2. List the decisions a player makes in each context, moment to moment and over a career. For each, write the question the player is really asking and the least information that answers it (section 3).
3. Draw the principles out of that list (section 4), then design widgets from the minimum information (section 6) and screens for the slow decisions (section 7).
4. Place the widgets in zones (section 5) and write when each is visible (section 8) and how urgency is handled (section 9).
5. Only then compare with the current UI, element by element (section 11).

The current UI was deliberately ignored until step 5. Where a widget here resembles one in the game, it is because a decision needs it, not because it exists.

Wireframes are grey boxes in the game's reference units (1280 x 800, the units `UiScale` scales). Solid boxes are always shown in that context, dotted ones only while their trigger holds, dashed ones are drawn in the world. Sample numbers come from the starter career (Highport, the corvette, the raider). `functional-task/wireframes.py` draws them (`python3 wireframes.py <out dir>`), so a revision changes the geometry in one place.

---

## 2. Where the player can be

The game's own rule decides the first cut: the captain must be seated at a console to see outside (GDD section 6). So the captain's body is always somewhere, and that place decides what they can act on. This spec calls it the **post**.

| Post | How the player gets there | What they can act on |
|---|---|---|
| **Deck** | Walking aboard their ship, a docked station or a latched ship | Their body, doors, consoles, suit lockers, furniture; giving orders that don't need a seat (helm orders, crew orders, map plans) |
| **Helm** | V at the helm console | Flying: thrust, turn, strafe, docking ports, undocking; firing the guns on the trigger |
| **Gunnery** | V at the weapons console (every gun and launcher in hand) or at one turret mount (that gun) | Aiming and firing, missiles, decoys, shields; the helmsman flies |

Over any post the player can open a **screen** (full screen, the game keeps running) or a **panel** (beside the view):

| Screen or panel | Opened from | For |
|---|---|---|
| Map | Any post | Getting somewhere: destinations, plans, burns, warp |
| Station terminal | Any post, while docked at a station with its clearance (as the market is today) | Everything a station sells: contracts, market, yard, shipyard, hiring |
| Ship's book | Any post | Your ship in detail, your crew, the contracts you hold, your career, the log |
| Boarding panel | Any post, while latched to a ship or within EVA reach of a beaten one | Boarding, salvage, prisoners, prizes |
| Console panel | Deck, at a system console (E) | That one system: its switch and its numbers |

And five **conditions** change what a post shows:

| Condition | Comes from the game's own state |
|---|---|
| **Docked** | `DockingSystem` has the player's ship latched to a station |
| **Latched** | Latched to another ship |
| **In combat** | The ship is at battle stations (`CrewMedical.atBattleStations`: a hostile captain has it as their target, it fights one, or a shot was fired or taken in the last 60 s) |
| **Warping** | Rails warp. Physics warp (the bubble still simulated, up to 10x) changes only the chronometer: the ship can still be flown and fought |
| **Crisis** | The ship is disabled, being abandoned, or stranded with a tow on offer (`DistressBeacon.refusal() == null`) |

Outside the game sits the **shell**: main menu, new career, load and save, settings, controls, and the end screens.

---

## 3. The decisions

Every row is a decision the game asks of the player. "Minimum information" is what answers the question and nothing more; anything a widget shows must trace back to a cell in this section. Tempo is how fast the decision has to be made: **s** seconds (in the eye line), **min** minutes (glanceable at the edges), **h/d** hours and days (behind a key).

### F. Flying (helm)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| F1 | Point and thrust | Which way am I moving relative to what matters, and how fast? | Velocity in the frame that matters (the locked target, the navigation target or a station within 5 km, else the orbit; FLT gives the order): direction on screen, speed as a number | W/S/A/D, Z/C, X | s |
| F2 | Avoid a collision | Will I hit something, and when? | The object and the time to contact, only when it is close | Steer, brake | s |
| F3 | Dock | Which port, am I cleared, am I lined up and slow enough? | My port and its state, the port I'm given, clearance, range, sideways offset, angle off, closing speed against the safe speed | G, H, steering, or helm order 5 | s |
| F4 | Leave a station | Is the ship ready to go? | Delta-v left, repairs needed, crew aboard, missiles, the contracts held and how far their destinations are | U | min |
| F5 | Fly a planned burn | When do I start, where do I point, when do I stop? | Countdown, pointing error, delta-v still to go, burn time, who is flying it | Turn, thrust, or hands off | s |
| F6 | Skip time | How fast can I warp, and what will stop me? | Warp rate, the next stop and when, what is holding warp back | , . / | min |
| F7 | Hand over the helm | Can someone else fly this while I do something else? | Is there a helmsman, what order stands, is it being flown or waiting and why, which orders are possible here | 1 to 6, 0 | min |
| F8 | Stay in orbit | Am I going to hit the planet or leave this body's sphere? | Periapsis against the surface and the time to impact; the next change of sphere of influence | Burn, plan | min |
| F9 | Burn with crew outside | Is anyone on the hull who a burn could throw off? | Who is out and where, the tether | Hold the burn, call them in | s |

### C. Fighting (helm or gunnery, in combat)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| C1 | See the threats | Who is hostile, who is shooting at me, where are they? | Each contact's relation, range, closing speed; whether it has me locked or is firing | Look, Tab, click | s |
| C2 | Choose a target | Which one matters most? | For the candidates: firing on me, range, closing, state (beaten, struck), a bounty on it | Tab, click | s |
| C3 | Shoot | Will a shot hit now? | Lead point, each gun's readiness, whether the target is in its arc and effective range, time of flight | Space, pointer | s |
| C4 | Launch a missile | Is a launch worth it now? | Launcher ready, missiles left, target in the missile's reach | R | s |
| C5 | Survive missiles | How long till impact, from where, what can I do? | How many, and the time to the first impact; where each comes from, drawn in the world (its path, or an edge arrow) rather than given as a bearing; the decoy ready, or how long until it is (decoys are unlimited, one every 3 s); point defense engaging | F, manoeuvre, shields | s |
| C6 | Shields | Up or down, and which side thick? | Up, down or stowing; strength by side; trim; heat or tripped | 9, 8 | s |
| C7 | Judge the fight | Am I winning? Is it beaten? | Target state and a coarse hull estimate; my own hull, power and guns as states, not numbers | Keep firing, cease, run, board | s |
| C8 | Stop firing on a prize | Has it struck? Will firing again provoke it? | Struck or surrendered, and that firing lifts the surrender after the grace | Cease fire, next target | s |
| C9 | Man the guns | Who is working each gun? | Per gun: in my hand, a named gunner, the weapons console, or nobody | Sit at a gun or the console | min |
| C10 | Break off | Can I get away? | Relative velocity and range, delta-v left, the nearest friendly station's cost | Burn, plan | min |

### S. Keeping the ship alive (any post, mostly the deck)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| S1 | Notice trouble | Is anything wrong aboard, and how bad? | Which category (hull, fire, air, power, propellant, drive, guns, crew, EVA, shield) and how bad (caution or warning) | Look | s |
| S2 | Find it | Where is it? | The room; on deck, its direction from me | Walk, look | s |
| S3 | Step in or not | Is the crew already handling it? | Who is on it and how far along, or that nobody can reach it and why | Walk there, force doors, suit up, or leave it | min |
| S4 | Power | Is there enough power for what is running? | Reactor output against draw, the store, shorts, what is unpowered | Switch systems at their consoles | min |
| S5 | Air | Is the air safe where people are? | Rooms losing air or thin, smoke, tanks left; what is venting | Suit up, shut doors, move people | min |
| S6 | My own body | Am I, the captain, safe? | My health and wounds, my suit and its air, the air in my room | Put on a suit, leave the room | s |
| S7 | The wounded | Who is hurt, and is anyone treating them? | Wounded crew by severity and whether care is under way | Usually none: medics work alone | min |
| S8 | Work outside | Who is on EVA, and should they come in? | Who is out, doing what, where; whether EVA is held | Send out, call in, hold all | min |
| S9 | Abandon or call for help | Is the ship lost? Can I get home? | Why it is disabled, whether repairs are under way, the tow's fee and terms or why it is refused, pod seats against crew | B twice, P twice, P | min |

### N. Getting somewhere (map, any post)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| N1 | Choose a destination | Where do I need to go next? | Contract destinations first, then stations with what they offer, how far and how friendly | Q, click | min |
| N2 | Plan the trip | What will it cost, how long, will I make the deadline? | Delta-v needed against left, what is left after and what home takes from there; arrival against the deadline | ; Enter Backspace | min |
| N3 | Shape a burn | What does this burn do to my path? | The path after it: periapsis, apoapsis, encounters; the burn's delta-v and time | Drag handles, [ ], arrows, Delete | min |
| N4 | Carry it out | Who flies the burns, and when can I warp? | Next burn countdown, helmsman available, autopilot ready or why not | /, 6 | min |
| N5 | Mind the route | Are pirates about? | Known pirates and when last seen; tension if it matters | Plan around, arm up | h/d |

### E. Earning and growing (docked)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| E1 | Take a contract | Which job pays best for its risk and time, and can I do it? | Kind, pay, standing, deadline, destination with travel time and delta-v from here, why it is locked; how many I hold of 3 | Take | h/d |
| E2 | Trade | What do I buy here, and what do I sell? | Stock, buy and sell price, what I carry and what I paid, hold space, credits, what a delivery contract needs | Buy, sell | h/d |
| E3 | Repair and resupply | What does being ready cost? | Damage and its price, tanks and missiles and their price, credits | Repair all, fill | h/d |
| E4 | Refit a gun | Which gun should go on this hardpoint? | The candidates against the fitted gun: damage, rate, effective range, power draw against the reactor, price new, used or salvaged | Fit | h/d |
| E5 | Buy a ship, sell a prize | Can I fly it and afford it, and what do I gain? | Price after the trade-in, rank lock and wait, its numbers against mine; a prize's sale and turn-in terms | Buy, sell, turn in | h/d |
| E6 | Crew the ship | Do I have the hands I need, and can I pay them? | Berths, roles aboard against roles needed, wages against the budget, candidates' skills | Hire, let go | h/d |
| E7 | Steer the career | How close is promotion, how do the factions see me? | Rank, standing against the next rank, time in grade, budget and payday, standing with each faction | Choose jobs | h/d |
| E8 | Fit out the decks | What goes where aboard? | The editor and the placement tool's own information | Their own tools | h/d |

### B. Taking ships (latched, or within EVA reach)

| ID | Decision | The question | Minimum information | Acted with | Tempo |
|---|---|---|---|---|---|
| B1 | Board or not | Is it beaten, what will boarding cost, who goes? | Its state, the standing it costs, who is free to go, how they would cross (dock or EVA) | Send, send on EVA, force a way in | min |
| B2 | Watch the boarding | How is the party doing? | Each boarder's state and room, defenders seen, rooms swept | Recall | s |
| B3 | Strip and carry | What is worth taking, and does it fit? | Each part's sale value, work time and volume; cargo; hold space after | Strip, carry, take all | min |
| B4 | Prisoners and the prize | What do I do with the crew and the hull? | Prisoners against free berths; the prize's terms | Bring aboard, hand over | min |

### G. The game around the game (shell)

| ID | Decision | The question | Minimum information | Acted with |
|---|---|---|---|---|
| G1 | Continue or load | Which save? | Each save's name, when it was saved, game day, ship, credits, where | Load |
| G2 | Start a career | Whom do I serve? | Each sponsor's home, what it offers, a line of who it is | Choose |
| G3 | Set up the game | How should it play and sound? | Settings grouped by what they change | Apply |
| G4 | Learn the controls | What can I do here, and with which key? | Keys for where I am, from my own bindings | Read |
| G5 | Catch up | What happened while I looked away? | The notices, timestamped, filterable | Read |
| G6 | After a loss | Where do I pick up? | The latest autosave and quick save | Load |

---

## 4. What the decisions say about information

Reading section 3 across its rows gives ten rules. Every later section applies them.

1. **The post decides the HUD.** On foot the player can't fly, so nothing about flying is on screen; at the weapons the helmsman flies, so the flight instrument gives way to a line saying who flies and to what order. (F, C and S decisions barely overlap by post.)
2. **Show the answer, not the inputs.** F4, F8, N2 and C10 all ask "is there enough?", never "how many kilograms". Propellant is shown as delta-v left against delta-v needed and delta-v home; docking as closing speed against the safe speed; a plan's arrival against its deadline.
3. **Silence means all is well.** S1 is a question about exceptions. No widget shows an empty or nominal state as text: no "Target: none", no "ALL SYSTEMS NOMINAL", no gauges at 100%.
4. **One fact, one place.** Each number has one home on a given screen. The world may repeat a name (a marker's label) but not a number. A comparison may quote the number it compares against (arrival against the deadline); nothing else repeats.
5. **Where things are goes in the world; how much goes at the edges.** Directions, positions, paths and rooms (F1, F3, C1, C3, C5, S2) are drawn on the view; numbers live in fixed instruments.
6. **Urgency has one voice.** C5, F2, F8, S6 and S9 need the eye now. They all go through one alert band, ranked by time to harm. News (contracts done, paydays, promotions) never shares it.
7. **Tempo sets distance from the eye.** Seconds-scale information sits in the world or at the bottom centre near the ship; minutes-scale at the corners; hours-and-days-scale behind a key.
8. **Every warning names its action.** S9 and C5 are decisions, not facts. An alert without a key or choice is a notice.
9. **Delegation is visible.** F7, C9, S3 and B2 are about the crew acting for the captain. Whenever the crew flies, shoots, repairs or boards for you, the interface says who and how it is going.
10. **Teach, then get out of the way.** G4 matters most in the first hour. Key hints follow the post and drop each key once the player has used it.

---

## 5. Screen zones

![Screen zones](functional-task/zones.svg)

| Zone | Holds | Why there |
|---|---|---|
| Z1 Time (top left) | Chronometer | Time is the slowest-changing, always-needed number; top left is where the eye starts |
| Z2 Attention (top centre, 640 x 76) | Alert band: a warning row over a caution row | The one place urgency appears, above the ship, never covering it |
| Z3 Incoming (top right) | Objectives, news | Hours-and-days information and messages from outside |
| Z4 Self (left column) | Annunciator, personal status | Your own ship and body, at a glance |
| Z5 Self, spatial (bottom left, 300 x 150) | Ship schematic, outside view only | The interior when you can't see it; empty on deck, where the deck itself shows it |
| Z6 Others (right column) | Target card | What the world can't show about another ship: its state, hull, intent |
| Z7 Others, spatial (bottom right) | Weapons cluster, sensor scope | Acting on others; the scope in the corner as a wider-than-the-screen view |
| Z8 Motion and hands (bottom centre) | Flight instrument, helm status | Seconds-scale numbers, near the ship and the eye line |
| Z9 Keys (a full-width strip along the bottom edge, 24 units) | Hint line | Read once, then ignored; fades as the player learns |
| Clear zone (centre ellipse, 600 x 400 units) | Nothing screen-fixed | The ship, the target and every world overlay |

Rules for zones:

- A widget lives in one zone and never moves between posts. Zones may be empty; nothing slides in to fill them.
- Changing post swaps the post's widgets at once. Widgets common to both posts (CHR, ALR, OBJ, NTF, ANN, HNT) stay exactly where they were, so the eye has fixed points through the change.
- When two widgets in a zone are both visible, the higher one in the zone's list keeps its size and the other collapses to its compact form.
- Screens (Map, terminal, book) cover zones 3 to 9 but leave Z1 and Z2 visible: time and danger never go away. A screen starts below the band's second row (100 units down in the wireframes, lower when a style's band is taller), so an alert arriving never covers a screen's header.
- Zones anchor to their screen edges and corners. On a wider or taller window the extra space goes to the clear zone; widgets are never stretched.
- Z9 runs the full width of the bottom edge and every other zone ends above it (at 760 units), so the key line never runs under a widget.
- The crisis panel is the only widget that may enter the clear zone (section 6, CRS).
- Sizes in the wireframes are starting points. An artist may change any size by up to a quarter and move a zone's edge, but not swap sides (section 12).

---

## 6. Widgets

Each widget gives what it serves (decision IDs), what it shows, its states, when it is visible, what the player can do with it, and what it replaces today. Field names in `code` are the game's own where they exist.

### CHR: Chronometer (Z1)

Serves F6, N4, E1.

- **Shows:** game day and time (`Day 3  14:22:05`); warp rate and kind (`1x`, `10x physics`, `10,000x rails`); the **next stop**, the event warp will drop out for, with its countdown (a minute before the helm turns onto the next burn, a sphere-of-influence change, a surface impact, the autopilot's arrival, the next craft forecast to come within 20 km); and, when warp is refused or held, **why** in a few words, from the same reason the game's warp guards give: `held: fire aboard`, `held: Ada is outside`, `held: air venting`, `held: burn coming up`. No guard holds warp for hostiles: a craft crossing into 20 km drops it, which is a next stop, and one already inside does not; a pirate moving to intercept drops it once, at once, and the INTERCEPT caution says who (`HostileTraffic` is a drop-out source until warp next changes).
- **States:** normal; warping (the rate is the loudest part); held (the reason replaces the next stop).
- **One home for each time:** while FLT shows its BURN mode, it owns the burn's countdown and the chronometer shows the stop after it, if any; otherwise (another post, or FLT in DOCK) the chronometer keeps it. HLM never shows a countdown. Over the map, the chronometer shows only the clock and warp; the plan's times are the map's.
- **Visible:** always in the game, over every screen.
- **Acts:** comma and period step warp; slash warps to the next burn; clicking the next stop opens the map with the plan timeline on that event.
- **Replaces:** the status column's clock line and the warp refusal notices.

### ALR: Alert band (Z2)

Serves F2, F8, F9, C5, S1, S6, S9. Full rules in section 9.

- **Shows:** at most one **warning** (large: title, the one number that matters, the action) and up to three **cautions** in three slots, then `+n` in a narrow fourth slot at the row's end when there are more. The `+n` slot is reserved, so no caution moves or shrinks when it appears.
- **States:** empty (invisible); warning new (pulsing for 3 s); warning held; caution; acknowledged (cautions leave the band and live on as lamps). Each row keeps its place (section 9.1).
- **Visible:** whenever an alert stands, in every context, over every screen.
- **Acts:** the alert's own key does its action; clicking the band acknowledges it (section 9.3); clicking one alert opens the book on its system. No key is needed: cautions collapse by themselves.
- **Replaces:** the missile warning banner, the deck's large missile line, the red disabled and abandon lines, the EVA status line, and the warning notices (9.2 says where each goes).

### OBJ: Objective tracker (Z3)

Serves N1, E1, G4.

- **Shows:** up to four rows. First the guide's current objective (First Play or the opening walkthrough), then each contract held, by the title the game gives it (`Contract.title`: kind, what and where), and its time left. Z3 is 240 wide, so a row too long for one line takes a second: it breaks before where the job goes or whose ship it is, or else at a word, and the time stays on the first line (`Deliver 40 Food   2d 9h` over `to Shackleton Ice`; `Bounty: Raider   2d 21h` over `of the Void Raiders`, since a bounty's title names the target's faction). A row never takes a third line; past two it is cut with an ellipsis. The contract that is the navigation target gets one more line, only when it has a next step (`return leg: Highport`, `step 2 of 3` for an operation). A contract whose planned arrival falls after its deadline is marked late.
- **States:** a row is normal, the navigation target, or late. A contract leaves the tracker the moment it ends: the game pays it then (`ContractBoard.end`), a bounty the moment its ship is beaten, so nothing waits to be collected, and the pay is news.
- **Visible:** while a guide runs or a contract is held; on every post. Hidden on screens: the map's destination list puts contracts first, and its plan owns the deadline.
- **Acts:** clicking a row makes its waypoint the navigation target (Q already cycles them first).
- **Replaces:** the gold contract lines, the First Play objective, the walkthrough text and the welcome notice in the status column.

### NTF: News (Z3, under OBJ)

Serves G5.

- **Shows:** the three newest notices of kinds INFO and IMPORTANT (`NoticeLog`), each a slip of one or two lines, since few of the game's notices fit 240 units beside their tag (an ensign's payday, `Budget from the Terran Patrol Authority: +2,000 cr`, runs to 50 characters). A slip never takes a third line; past two it is cut with an ellipsis, and the whole text is on hover and in the log. Repeats are counted (`x3`); INFO shows for 5 s and IMPORTANT for 8 s, as today.
- **States:** info, important.
- **Visible:** while a notice is fresh; on every post; not over screens. In combat, INFO news waits until combat ends; IMPORTANT shows at once.
- **Not news:** feedback to something the player just did shows on the widget the action changed, not here: a missile launched with no target flashes `no target` on WPN, a decoy away starts WPN's decoy countdown, shields raised change their row.
- **Acts:** none; L opens the book's log.
- **Replaces:** the notice stack in the status column. WARNING notices become the alerts or news that 9.2's table names; an alert is logged as an alert, not as a notice.

### ANN: Annunciator (Z4)

Serves S1, S2, C7.

Ten lamps in two rows of five, one per thing that can go wrong aboard; a ship without shields has no SHIELD lamp, so the starter corvette shows nine. A lamp has four states: dark when its system is well; **advisory**, lit softly, for something worth knowing that asks nothing yet (crew outside, morale low), which never enters the alert band; lit for a caution; lit harder for a warning. It blinks only while its alert is new. The lamps are the ship's standing state; the alert band is what calls for attention. An acknowledged caution leaves the band but keeps its lamp lit. Every condition below that lights a lamp at caution or warning raises the alert named beside it (9.2), at the same level, so a lamp never says more than the band did; the alert may be another lamp's, as a shorted engine's is POWER's.

| Lamp | Advisory when | Caution when | Warning when |
|---|---|---|---|
| HULL | A patched breach | Mean hull integrity below 75% (`ShipAlerts.HULL_DAMAGED`): HULL | Below 45%, the `HULL` disable line: DISABLED |
| FIRE | - | Any tile burning: FIRE | A large fire, or fire in the captain's room: FIRE |
| AIR | An airlock cycling (its chamber is left out, as `ShipAlerts.chamber` does) | A room below 70% pressure (`LOW_PRESSURE`) or smoky, an oxygen or nitrogen tank below 25% or dry, or life support damaged or stunned: AIR; venting: VENTING; a suit with under 4 minutes of air that nothing aboard can fill: SUIT AIR | Anyone without a suit in air that hurts them, under 30% oxygen (`CrewDef.asphyxiationThreshold`): AIR, or CAPTAIN AIR for the captain; a suit under 60 s: SUIT AIR, or CAPTAIN AIR for the captain's own |
| POWER | A system switched off by hand | A reactor below full output from damage; a short on a system or conduit; a system cut off from every reactor: POWER | No reactor feeding anything: every reactor off, shorted, stunned or broken. A one-reactor ship whose reactor is shorted is here, although the game does not call it disabled: POWER |
| PROP | Delta-v left below 1.5 times what home takes | Below 1.25 times (the `FlightWarnings` low-propellant rule), or below what the accepted plan needs: PROPELLANT | - (too little to fly home is the crisis panel's: the tow) |
| DRIVE | - | The main engine, an RCS block or the helm console damaged, stunned or shorted (with the helm console down, nobody can fly): DRIVE, or POWER for a short. With no main engine working this is still a caution: nothing is harmed within a minute, and a burn it costs is BURN MISSED | - |
| ARMS | - | A mount or the weapons console not working (damaged, cut off from power, stunned, shorted): ARMS, or POWER for a short or a cut | None working while in combat: ARMS |
| CREW | Morale low; a light wound (treated once the fight is over, `TaskRules`); any wound being treated | Someone seriously wounded or unconscious, or critically wounded and stabilised, with nobody treating them: WOUNDED | Someone critically wounded whose clock to bleed out runs: not stabilised, and nobody treating them (treatment holds the clock, `CrewHealthSystem`; stabilising does not until it is done): CRITICAL, or CAPTAIN HURT for the captain |
| EVA | Anyone outside | Anyone outside thrown off the hull or adrift, a tether parted, or suit air under 5 minutes: EVA | Anyone outside with a burn under way or due in 30 s: EVA BURN |
| SHIELD | Shields lowered outside combat, or the cloud below half | Shields down in combat, an emitter tripped, damaged or stunned, or the cloud below a quarter: SHIELD | - |

- **The power store never lights a lamp.** It holds 2 s of output (`PowerRoutingSystem.STORE_SECONDS`) and empties on every laser pulse, so a lamp on it would flicker through every fight. A gun waiting on the store shows `CHARGING` in WPN instead.
- **Visible:** on every post. On screens, no.
- **Acts:** hover (or focus) lists what lit it (`FIRE: Hold (medium), Cole on it`). Clicking opens the book's Ship tab on that system. On deck, selecting a lamp also points the off-screen arrow at the nearest cause.
- **Replaces:** the ship status gauges (propellant, structure, power, oxygen), the ship panel's alert list and its `HULL 100%` and `ALL SYSTEMS NOMINAL`.

### PER: Personal status (Z4, under ANN)

Serves S6.

- **Shows:** the captain's health as a state, in the game's words (`unhurt`, `light wound`, `serious wound`, `critical, 4:10 to live`, `critical, stabilised`, and `being treated` after a serious or critical wound while someone treats it); their suit (none, pressure, EVA) and its air left as time; the room they stand in and its air (`breathable`, `thin`, `vacuum`, `smoke`, `hot`).
- **Visible:** on deck only, while any of: suited, hurt, or the room is not breathable, smoky or burning.
- **Acts:** none; it is read, and its warnings come through the band (air under 60 s, critical wound).
- **Replaces:** nothing; the game shows none of this today.

### SCH: Ship schematic (Z5)

Serves S1, S2, S3, C7.

- **Shows:** the ship's deck plan with what is wrong drawn in its room: fire, smoke, thin air and vacuum, breaches, shorts, damaged and stunned systems; the crew as dots, the captain marked; hull hits flash where they land (`UiHud.flashHullHit`). No text but room names on hover.
- **Visible:** helm and gunnery only (on deck you are looking at the ship itself), and only while any of HULL, FIRE, AIR, POWER, CREW or EVA is lit. The book's Ship tab shows it at any time.
- **Acts:** clicking a room opens the book's Ship tab on that room.
- **Replaces:** the wireframe minimap panel, which is always up today.

### FLT: Flight instrument (Z8)

Serves F1 to F8, C10.

![The flight instrument's four modes](functional-task/flight-modes.svg)

One instrument, four modes, hidden in rails warp. It shows the first mode that applies, in this order, with one exception: a burn being flown (the turn onto it, from 2 minutes before its start, then the burn) beats DOCK. The helmsman flies a planned burn ahead of any order, docking included (`HelmOrders.flyPlannedBurn`), and so does the flight computer while the captain at the helm keeps their hands off, so the ship is doing the burn and FLT must say so. A burn's countdown does not beat DOCK: a captain docking by hand keeps the docking instrument, and the chronometer keeps the countdown.

| Mode | Applies when | Shows | Keys shown in the mode |
|---|---|---|---|
| DEPARTURE | Docked at a station | Where you are berthed; undock clearance; four readiness checks, each `ok` or what is short with a link to the terminal tab that fixes it: delta-v left against the furthest held contract's need, repairs, the whole roster aboard (or who is still ashore, and why), missiles against the launcher. The contracts themselves are OBJ's | U undock, T terminal |
| DOCK | The navigation target can be docked with, it is within 5 km (`PlayerDocking.READOUT_RANGE`), and a port is armed, clearance asked, or the helm ordered to dock | Your port (n of N, size, active or passive, jammed); the port you were given and clearance (given, asked, refused, none); range; closing speed on a bar banded safe (up to the safe speed), caution (to 1.5 times) and warning (beyond), the bands the corridor uses today; sideways offset; angle off | G arm and ask, H next port (with two ports or more), 5 helm docks |
| BURN | A planned burn starts within 3 minutes (the helmsman's 120 s turn lead plus a minute), or one is being flown | Burn n of N; countdown to the start of the turn and of the burn; delta-v still to go and burn time; pointing error (and the align marker in the world); who flies it when that is you or the flight computer (hands off), since a helmsman flying it is HLM's line | / warp to burn |
| CRUISE | Otherwise | The frame and the speed in it; delta-v left on a bar marked with what home takes and what the plan needs; rotation assist only when it is off; main engine and RCS as indicators, lit while firing | none: the hint line has them |

- **The frame** is the first of: the locked target, when it has a body in the bubble; the navigation target, within 5 km (`PlayerDocking.READOUT_RANGE`); the nearest station within 5 km; else the orbit about the body you are in. "Near" is always that 5 km. FLT names the frame (`Highport, 3.0 km`, `orbit, Earth`). The prograde and retrograde markers follow the same frame; today they follow the orbit unless a target is locked, so this is a change.
- **Rails warp:** FLT hides. The chronometer already carries the rate, the next stop (its countdown allows for the drop-out lead) and what holds warp back, and at 10,000x there is nothing to steer.
- **Gunnery:** FLT is hidden and HLM takes its place, because the helmsman flies. The gunner's motion numbers (range, closing, closest approach) are the target card's.
- **Speed against closing:** FLT gives the speed in the frame (how fast I move relative to the target: what matching velocity zeroes); the target card gives range and closing (how fast the gap shrinks: what a fight turns on). They are different numbers with one home each.
- **Not shown in any mode:** the ship's heading in degrees (its nose is on screen; BURN gives the pointing error instead), the view mode (the view shows it), orbital speed in CRUISE unless the frame is the orbit, apoapsis and periapsis (map).
- **Replaces:** the FLIGHT panel's speed, heading, markers and view lines; the docking readout lines; the navigation readout's next burn, helm order and delta-v lines; the station banner `Docked at Highport [T] Trade [Y] Yard [J] Missions`.

### HLM: Helm status (Z8, above FLT)

Serves F7, F9.

- **Shows:** who has the helm (`you`, a named helmsman, or `nobody`) and the standing order with its state: `holding heading`, `flying burn 1, 412 m/s to go`, `matching Raider`, `holding 1,000 m off Highport`, `approaching to 500 m`, `docking: lining up on port 2`, `autopilot to Shackleton Ice, holding: pirates about`. A waiting order says why (`waiting: no helmsman`, `waiting: you are at the helm`).
- **Burns are flown whatever the order.** A helmsman flies every planned burn ahead of any order (`HelmOrders.flyPlannedBurn`), so while one is flown HLM says so, with or without order 1. Today's bridge line says the helmsman holds the heading then, while the ship turns and burns.
- **Under fire.** A shot at the ship or a missile launched at it drops every order but hold position and match velocity (`HelmOrders.threat`). In a fight the line reads `holding heading` unless one of those two stands, and the HELM caution says what was dropped.
- **Visible:** on deck and gunnery whenever the ship is undocked (someone else is flying it); at the helm only while an order or the autopilot stands, as FLT's first line.
- **Acts:** clicking it opens a short list of the orders with their number keys, each greyed with its reason when it can't be given (`approach: no target`, `dock: beyond 50 km`). The number keys stay the way to give them.
- **Replaces:** the status column's bridge line and helm notices.

### SNS: Sensor scope (Z7, corner)

Serves C1, C2, F2.

- **Shows:** contacts around the ship, north up like the view; shape for what it is (ship, station, missile, derelict, pod), treatment for relation (section 9.4); the locked target and the navigation target marked differently; your heading as a line; contacts beyond range on the rim; the range. Below it, the count of hostiles when there are any (missiles are counted by the alert band).
- **Range:** automatic by default: the smallest step (0.5, 2, 5, 10, 50, 250, 1,000 km; the 5 km step is new, because fights reach 5 km: the coilgun's `max_range`, with a kiting ship holding 4.5 km, `Tactics.KITE_RANGE`) that holds, with room to spare, every inbound missile and the locked target; with neither, the nearest hostile; else the nearest contact. A raider at 3.2 km and the two harpoons it has just thrown (impact in about 14 s, by 9.2's formula) give 5 km, where both read well clear of the own-ship mark. The minus and plus buttons override it until the post changes. The automatic range and one set by hand look different (a style may mark either), since a range set by hand no longer follows the threats.
- **Visible:** helm and gunnery, undocked, not warping. Docked, only while a hostile is within 50 km.
- **Acts:** clicking a contact locks it (a sensor contact) or makes it the navigation target (a station or other object), as today.
- **Replaces:** the radar panel and its title.

### TGT: Target card (Z6)

Serves C1, C2, C7, C8, N1, F3.

Shows one subject: the combat target (Tab) when one is locked, else the navigation target (Q). With both set, the navigation target gets one line under the combat target's card.

- **A ship:** name and class; owner and relation. Names are the game's own, never shortened: the pirate corvette near Highport at the start is `Raider`, a pirate from the lanes `Pirate raider 3` or `Pirate gunship 2` (`HostileSpawns`), an ace its callsign (`Black Sal`, `PirateAces`). Then state (`fighting`, `running`, `disabled: reactor down`, `struck`, `surrendered`, `wrecked`, `derelict`); `FIRING ON YOU` or `has you locked` when true; hull as a sensor estimate in four bands (`intact`, `damaged`, `heavily damaged`, `crippled`); range and closing; closest approach (distance and time) when it is passing; the contract it belongs to. A struck ship carries `firing on it lifts the surrender`.
- **A station:** name, owner and relation, your standing with the owner, services (market, yard, shipyard, contracts, hiring), clearance when within 50 km, range and closing. While FLT is in DOCK mode the card keeps only the identity lines; the range and speeds are FLT's.
- **Visible:** helm and gunnery while a target or navigation target is set; on the Map as its destination section. Hidden on deck: the deck can't see outside.
- **Replaces:** the FLIGHT panel's TARGET block, the status column's `Target:` line, and the navigation readout's target and closest approach lines.

### WPN: Weapons cluster (Z7)

Serves C3, C4, C5, C6, C9.

Every weapon mount gets one row, launchers and point defense included: point defense is not a separate system but the mounts whose weapon has `point_defense` set (the starter corvette's two autocannons).

- **Compact (helm):** one pip per mount (ready, waiting, not working), the launcher's rounds left and ready, the decoy ready or its countdown, shields up or down. The pip row is enough to know whether pulling the trigger does anything.
- **Expanded (gunnery):** a header saying what is in hand (`guns in hand, point defense automatic` at the weapons console; `this gun` at a turret mount). Then one row per mount:
  - where it is and what it is (`NOSE coilgun`, `FWD PORT autocannon`, `AFT STBD laser`);
  - who works it: `HAND` (in the captain's hand); a gunner's name; `CONSOLE` (a crew member at the weapons console works it at half skill); `TRIGGER` (nobody works it: it aims at the locked target and waits for the captain's trigger); `AUTO` (point defense, which stays automatic at the weapons console and goes in hand only when the captain sits at that mount, as `PlayerGunnery.inHand` has it);
  - its state: `READY`, `RELOAD 0.8 s`, `CHARGING` (the power store can't yet pay its next shot, which takes `power_draw` times its cooldown all at once), `NO POWER`, `STUNNED 12 s`, `SHORTED`, `DAMAGED`, `OUT OF ARC`, `OUT OF RANGE` (past `max_range`, which is a guide for guns and a hard limit for beams), `NO TARGET`, `HOLD` while docked; for point defense `ENGAGING 2` or `IDLE`;
  - for a gun with a target, its time of flight (`tof 1.1 s`);
  - for a launcher, rounds left of its magazine (`3/4`) and the seeker's lock.
- Then the **decoy**: `READY`, or seconds until it is. Decoys are unlimited, one every 3 s (`PlayerGunnery.DECOY_COOLDOWN`), so there is no count.
- Then, on a ship with an emitter, the **shields**: up, down or stowing; the cloud as a share of capacity; trim; four sectors (bow, port, stern, starboard) by thickness; heat, or tripped and for how long.
- **Visible:** compact at the helm while the ship has weapons and is in combat or has a hostile locked; expanded at gunnery always. Hidden docked and while warping.
- **Acts:** the existing keys (Space, R, F, 9, 8). New: clicking rows picks which of the guns in hand the trigger fires (all of them by default), the "selected weapon" the lead reticle follows.
- **Replaces:** the status column's `Missile: ready (4 left)` and `(gun reloading)`.

### HNT: Hint line (Z9)

Serves G4.

- **Shows:** at most four keys for what can be done here and now, from `HintRegistry` contexts, then `F1 controls`: five items in all. A key the player has used three times in that context drops off; setting `Hints: full / learning / off` (default learning). A key already on screen never repeats here: a mode's keys (FLT, CRS, BRD), the action an alert names, and the interaction prompt's keys.
- **Visible:** every post; fades to a dim state after 10 s without a change of context. Not over screens: a screen's parts show their own keys and its header names the close key.
- **Replaces:** the hint line and every key named outside it: `[V]` and `[Tab]` in the FLIGHT panel, `N plans a burn / Q picks a navigation target` in the orbit panel, the station banner's keys.

### INT: Interaction prompt (in the world)

Serves S3, S6, E8.

- **Shows:** beside the object the captain stands at, its name once and then at most two keys, each with a verb: `Reactor console   E open`, `Helm   V sit`, `Suit locker   E put on a pressure suit`, `Door   F force (a hostile act)`.
- **Visible:** on deck within `INTERACT_RADIUS` of something usable.
- **Replaces:** the `Press [E] for the Helm panel  [V] sit and fly` line at the bottom centre.

### OVL: World overlays

Serve F1, F3, F5, C1 to C5, S2, B2. These already exist; the spec sets rules for them.

- **Contact markers.** Shape says what a thing is; treatment says its relation (section 9.4). Labels show only for: stations (few, large, and the landmarks a pilot steers by), the locked target, the navigation target, hostiles within 50 km, contract-related objects, and whatever the pointer is over. Everything else, passing freighters included, is a glyph. Glyphs within 24 px of each other merge into one with a count; ships docked at a station fold into its label (`Highport  2 docked`).
- **Edge arrows** only for hostiles, inbound missiles, the locked target, the navigation target and contract waypoints. Markers and arrows carry names, never numbers: the time to impact is the band's (principle 4).
- **Velocity markers.** Prograde and retrograde in the frame FLT names, the frame told apart by shape, not colour alone.
- **Burn arrows.** The next burn in full with its label; later burns faint and labelled only on hover.
- **Lead reticle:** one, for the selected gun in hand that needs the most lead (not beams, not point defense). At fight ranges the lead is metres to tens of metres, so the reticle sits on or just beside the target's hull, inside its lock mark: the gunnery sample's raider crosses the line of sight at about 8 m/s, and the coilgun's 1.1 s of flight gives about 9 m of lead on an 18 m hull. Reticles for two muzzle speeds would sit a few units apart there and read as neither. The starter corvette's is its coilgun's.
- **Missile paths** and impact marks, incoming and outgoing told apart; the point defense kill ring.
- **Docking corridor** from the given port, banded by safe speed.
- **Deck overlays:** fire, smoke, thin air, vacuum, breaches and shorts drawn in their tiles; a tag on each hazard with the progress of whoever is working it (the bar crew already show over their work, `showProgress`), or `nobody`; the worker is the figure standing there, and the alert band names them; off-screen arrows at the view's edge for hazards out of sight; crew names and jobs on hover; boarders on a latched ship.
- **EVA:** crew outside and their tethers, in both views.

### Room names

Alerts, the personal status, the schematic and the book all name rooms, and the game's compartments have no names of their own. Every widget uses one rule, in this order:

1. A name given in the chassis data: new, an optional list of names each pinned to a tile, naming whichever room holds that tile.
2. The room holding the helm is the **Bridge**; a small room holding nothing but a docking port or an airlock is the **Airlock** (with the port's number when there are two or more). A port that opens straight into a bigger room doesn't rename it.
3. Otherwise the system that fills the most tiles in it: Reactor room, Life support, Weapons room, Engine room, Shield room, Tank room, Pod bay, Hold (cargo bays), Medbay (mostly medbay deck), or a gun's position for a turret mount alone (`Fwd port gun`).
4. Otherwise its place in the hull from the ship's middle: Fore, Mid or Aft, with port or starboard when the hull splits there.

Two rooms with one name are numbered fore to aft (`Hold 1`, `Hold 2`). When damage joins two rooms (a wall blown out), the joined room keeps the larger one's name; when a wall splits one, each part is named afresh. On the starter corvette the rule gives two rooms: the **Bridge** forward (helm, weapons console, life support, reactor, the guns' mounts) and the **Hold** aft of the wall (cargo bays, tanks, engines). The samples and wireframes in this spec use that ship.

The game now has rules 2 to 4 and the numbering (`RoomNames`, added with the HUD framework), in capitals of at most 10 characters (`LIFE SUP`, `GUNS`, `FWD STBD`). Where its names differ from the list above (`GUNS` for a weapons room), its names stand; this spec writes them in ordinary case (Hold, Bridge), and a style may set them either way. Rule 1 is still to add.

### CRS: Crisis panel (centre top, below the band)

Serves S9.

- **Disabled:** why (`no reactor running`, `hull broken`, `no conscious crew`); what the crew is doing about it and how long (`Ada resetting the reactor, about 20 s`, and the breaker's own reclose time); then the choices, each with its terms: wait; call a tow (B twice: the fee, the standing, the days, where it takes you, or why it is refused); abandon ship (P twice: pods, seats against crew). Air aboard as time at this crew.
- **Stranded:** not disabled but the tow is offered (`DistressBeacon.refusal()` is null): the same panel with only the tow and the delta-v short. The band's PROPELLANT caution is its title.
- **Abandoning:** the countdown to launch, seats filled per pod, and where the captain is against the nearest pod with a free seat (`you are 12 m from the pod`); P launches now.
- **Visible:** whenever one of the three holds, on every post and over screens (it is a crisis).
- **No repeats:** the alert band carries the title and the reason (`SHIP DISABLED  no reactor running`); the panel starts at what the crew is doing and the choices.
- **Size:** disabled is the full panel, and the one widget allowed into the top of the clear zone: a disabled ship can't be flown, so the centre has nothing to steer by. Stranded and abandoning are three lines and stay above the clear zone, since the ship can still move.
- **Replaces:** the red `SHIP DISABLED` and `ABANDON SHIP` lines, and the pause menu's `Call a tow`.

### Wireframes for the posts

![Helm at rest](functional-task/helm-quiet.svg)

![Gunnery in combat](functional-task/gunnery-combat.svg)

![Deck during damage control](functional-task/deck.svg)

![Crisis panel](functional-task/crisis.svg)

---

## 7. Screens and panels

All three screens share one frame: Z1 and Z2 stay visible above it, a header row says where you are, tabs run under the header, and the body is a list on the left with the selected item's detail on the right. The game does not pause behind a screen (open question 1). Esc closes a screen and returns to the post.

### MAP: The map

Serves N1 to N5, F6, F8. Opened with M from any post.

![The map](functional-task/map.svg)

| Part | Shows | Acts |
|---|---|---|
| Orbit canvas | As today: paths after the plan's last burn, patched conics through spheres of influence, apsides, encounters, burns with their handles, the traffic layer | Click the path to plan a burn, drag handles, drag a burn along its orbit, click a contact to make it the destination |
| MAP.1 Destination | The navigation target as the target card shows a station or ship, plus its distance and the contract it serves, by name (the time left is MAP.2's comparison) | Q, Shift+Q; `list` opens every candidate grouped (contracts first with their time left, then stations, known pirates, craft in the bubble, freighters) |
| MAP.2 Plan | The proposal or the planned burns: count; delta-v needed against left; left after the plan and what home takes from the arrival; arrival time against any deadline, `LATE by 1d 9h 40m` when it is; one row per burn | ; plan, Enter accept, Backspace discard |
| MAP.3 Fly it | Who will fly the next burn: the helmsman by name, you at the helm hands off, or nobody, so it will be missed (a helmsman flies every planned burn, order or none, so order 1 only names it); warp to the burn; the autopilot; each greyed with its reason when it can't be given | /, 6 |
| MAP.4 Selected burn | The selected burn's components and time | Drag, Shift finer, Delete |
| MAP.5 Plan timeline | A strip from now to arrival on a log scale, with every event the plan meets: burns, sphere changes, closest approach, arrival, contract deadlines, payday | Click centres the map on the event; double-click warps to it |
| MAP.6 Layers | Stations, traffic, pirates, derelicts and pods, faction tension, each on or off | Toggle |

The map is the only place for the orbit's numbers (altitude, apoapsis, periapsis, period) and for faction tension. The instruction line along its top today becomes the keys each part shows (MAP.1 to MAP.4), with the close key in the header.

### TRM: Station terminal

Serves E1 to E6. Opened while docked: T on the Market tab, J on Contracts, Y on Yard; or from FLT's DEPARTURE mode. A tab is hidden where the station doesn't offer it: Highport repairs and refits but sells no ships (the Patrol's shipyard is Anvil Yards, `shipyard.toml`), so its terminal has four tabs.

![Station terminal](functional-task/terminal.svg)

**Header:** the station, its owner and your relation, your standing with the owner, credits, hold used of total, contracts held of 3.

| Tab | List | Detail | Additions over today |
|---|---|---|---|
| CONTRACTS | The day's offers, operations first, with any a world event posted here (an urgent delivery for a shortage elsewhere, given by the short station's owner, `WorldEvents`): kind, title, pay, standing (naming the giver when it isn't your sponsor, since only the sponsor's counts toward rank), deadline, **reach** (travel time and delta-v from here, `ok` or what is short); locked offers with their reason. A delivery's pay is its fee plus what the buyer pays for the cargo, less what the cargo costs here, so it compares with the other kinds. Sort by pay, time or distance | Full terms: why it was posted (a world event's reason), giver, cargo and its price here, pay as fee and cargo, standing on success and failure and with whom, deadline as a date, the route with arrival and spare time, whether the cargo fits; Take; Show route on map (opens the map with the transfer proposed) | Reach and spare time; route preview |
| MARKET | Each commodity: stock, buy, sell, what you carry, what you paid for it, what a held delivery needs | Buy and sell in steps, max affordable, max that fits; the crew's carry jobs across the dock with progress (cargo crosses physically) | What you paid; carry progress |
| YARD | Three groups: Repair (damage, price, time; repair all), Resupply (oxygen, nitrogen, propellant, missiles, each with the price to fill), Refit (each hardpoint, its gun) | A refit candidate against the fitted gun: damage, rate, effective range, muzzle speed, power draw against the reactor's output, mass, price new, used and salvaged. Furnish (buy and place) and Rebuild decks stay as buttons that open their tools | Gun numbers compared; power check |
| SHIPYARD | Ships for sale: class, price after the trade-in, locked by rank or wait | Its numbers against your ship's: hull, berths, hold, delta-v, hardpoints, reactor. A prize in tow: its sale and turn-in terms | Comparison |
| CREW | Who is looking for a berth: role, skills, wage | Your crew by role against your berths and the week's wages against the budget; Hire. (The book's Crew tab links here while docked at a station that hires.) | Gaps by role |

Off a station, Y keeps today's two uses as a terminal with only the tab that applies: the field refit (the yard tab with only the hold's salvaged guns and Mount on EVA), or handing over a prize (the shipyard tab).

### BOOK: Ship's book

Serves S2 to S8, E6, E7, G5. Opened from any post: I on Crew, J on Contracts while undocked, L on Log; a lamp or the schematic opens it on Ship.

![Ship's book](functional-task/book.svg)

| Tab | Shows | Acts |
|---|---|---|
| SHIP | The deck plan full size with one layer at a time (damage, power circuits, air, crew); systems with state, health, power and switch position; reactor output against draw and the store; tanks in kg and propellant as delta-v; shields; ports; the hold's manifest; a damage-and-work table: what, where, who is on it, progress | Click a room or system: what is there and what is wrong. Switches stay at the consoles (open question 2): the book marks the console to walk to |
| CREW | Each person: role, skills, condition, morale, job now and where, suit, wage, history; EVA held or not; suits at battle stations; prisoners with what can be done with them here | Send out, call in, let out; let go (docked); recruit; bring prisoners aboard |
| CONTRACTS | Held contracts with full terms, progress and deadline; the last 20 finished | Give up; set as destination |
| CAREER | Rank and sponsor; standing with the sponsor against the next rank and any time in grade still owed; budget and next payday; standing with every faction; faction tension; net worth; ships beaten and taken | None |
| LOG | Every notice and every alert, newest first, stamped with game time. An alert is logged once, as it is raised, in full text and marked with its level; when it clears, its entry gains the time (`cleared 14:31`) rather than a second entry, and one that worsens is revised in place, as `NoticeLog.revise` does for a growing salvo. Filters: alerts, important, info. It keeps the last 500 entries and is saved with the game: the game keeps 50 and saves none, and one day of play fills about 20 | Scroll, filter |

### BRD: Boarding panel

Serves B1 to B4. Opened with K. A panel down the right side, so the deck (or the outside view) stays in sight while the boarders work.

![Boarding panel](functional-task/boarding.svg)

It follows the boarding through its phases:

1. **Before:** the ship's state; how it is linked (latched by port, or standing off within 300 m and drifting under 2 m/s for an EVA crossing, or why neither); the standing it costs (none for outlaws); who is free to go. Choices: send the party, send them on EVA, force a way in (with its consequence: boards whether beaten or not, every shut door forced).
2. **During:** each boarder's state and room, defenders seen, rooms swept of total. Recall all.
3. **Taken:** salvage (each part's value, work time, volume; strip or marked or under way), cargo (carry, take all), the hold after; prisoners against free berths (bring aboard, with how many won't fit); the prize's terms at a shipyard.

It closes when the ships part. On the latched ship's deck, boarders and their rooms are drawn as world overlays.

### CON: Console panel

Serves S3, S4, S5, C9. Opened with E at a system's console; anchored beside the console in the deck view, not in the middle of the screen; closes when the captain walks away.

- Every console: the system's name, health, power (circuit or none), state (on, off, shorted, stunned) and its one switch.
- Reactor: output, the circuit's draw, the store.
- Life support: the rooms it serves of the ship's rooms, oxygen and nitrogen tanks.
- A gun: `V  take the gun`.
- Helm and weapons consoles open no panel: E and V both sit.

### Shell

- **Main menu:** Continue (with the newest save's summary), New career (then the sponsor choice), First play, Load, Settings, Quit.
- **Load and save:** each save's name, saved date, game day, ship, credits and where (the location is new).
- **Pause:** Resume, Save, Load, Settings, Controls, Quit to menu, Quit game. The tow moves to the crisis panel.
- **Settings:** Audio; Display (window, UI scale); Interface (hints full, learning or off; reduced motion; the colour set for relation and severity, which each artist provides with a colour-blind-safe variant); Controls (bindings, gamepad); Gameplay (the walkthrough).
- **Controls (F1):** every action grouped by post, then screens, with the player's bindings and the pad's buttons.
- **Career over and ship lost:** what happened in one line, then load the latest autosave or the quick save.

The ship editor and the furnishing placement tool keep their own layouts. While placing, every widget but the alert band hides and the hint line carries the tool's keys, as now.

---

## 8. Visibility rules

What each post shows. `on` always; `when` while its trigger (section 6) holds; `small` compact form; `-` never.

| Widget | Deck | Helm | Gunnery | Map | Terminal | Book |
|---|---|---|---|---|---|---|
| CHR chronometer | on | on | on | on | on | on |
| ALR alert band | when | when | when | when | when | when |
| OBJ objectives | when | when | when | - | - | - |
| NTF news | when | when | when | - | - | - |
| ANN annunciator | on | on | on | - | - | - |
| PER personal | when | - | - | - | - | - |
| SCH schematic | - | when | when | - | - | (Ship tab) |
| FLT flight | - | on | - | - | - | - |
| HLM helm status | when | when | when | (MAP.3) | - | - |
| SNS scope | - | when | when | - | - | - |
| TGT target card | - | when | when | (MAP.1) | - | - |
| WPN weapons | - | small, when | on | - | - | - |
| HNT hint line | on | on | on | - | - | - |
| INT prompt | when | - | - | - | - | - |
| OVL overlays | deck set | outside set | outside set | - | - | - |
| CRS crisis | when | when | when | when | when | when |

How the conditions change it:

| Condition | Changes |
|---|---|
| Docked | FLT is in DEPARTURE. WPN hidden (guns hold while docked). SNS hidden unless a hostile is within 50 km. TGT hidden unless a target other than the station is set. The terminal can be opened. |
| Latched | The boarding panel can be opened; the hint line offers K. |
| In combat | WPN appears at the helm. SNS ranges on the threats. NTF holds INFO notices back until combat ends (IMPORTANT still show). The hint line shows fight keys. |
| Warping (rails) | FLT hidden (the chronometer has the warp). SNS, WPN and the overlays other than markers hidden. TGT shows only range and closest approach. |
| Crisis | CRS shown everywhere, carrying the crisis keys; the hint line drops to F1 alone, so nothing competes with the panel. |

The at-rest test: flying near Highport with nothing targeted, nothing planned and nothing wrong, the screen shows CHR (two lines), nine dark lamps, FLT in CRUISE (three lines), the scope, and up to four keys that fade. That is about ten lines of text, against about thirty in six panels today (section 11).

---

## 9. Alerts, lamps and news

### 9.1 Three levels

| Level | For | Where | Sound | Lasts |
|---|---|---|---|---|
| Warning | Harm within about a minute, or the ship or captain lost without action | The band's top row, one at a time | `UI_WARNING`, at most three times (9.3) | Until the condition clears |
| Caution | Something wrong that needs a decision, not a reflex | The band's second row, up to three | `UI_NOTICE` once | Until it clears or is acknowledged; then as a lit lamp |
| Advisory | Worth knowing, with nothing to decide yet (crew outside, morale low, a wound being treated, a system switched off, delta-v getting thin) | A softly lit lamp only | None | While it holds |
| News | Something happened | NTF and the log | `UI_NOTICE` for important | 5 or 8 s, then the log |

Warnings are ordered by time to harm (a missile in 6 s beats air in 50 s). A new warning more urgent than the one shown pushes it into the caution row, where it goes first. Cautions follow by severity, then age; severity is the order of 9.2's list: what can hurt someone first, then what the ship can't do, then the job (a fire outranks a damaged engine, which outranks a late delivery). Every alert has an id; the same condition never shows twice.

**The rows keep their places.** The warning row is always the top row and the caution row always the second, whether or not a warning stands: with no warning the top row is simply empty, so a warning arriving never shoves the cautions down, and a style may give each row a window of its own.

Two rules keep the band from repeating the rest of the HUD:

- **The action fits the post.** An alert names the action the player can take where they are. On deck, `F decoy` means nothing (F forces doors there), so the missile warning says `point defense engaging; V at a console to fight`; at gunnery it says `F decoy`.
- **No caution for what a visible widget already shows in full.** At gunnery a damaged gun is WPN's row (`DAMAGED`), so it lights ARMS but takes no caution slot; at the helm, where WPN is compact, it takes one. Warnings always show.

### 9.2 The alert list

Every alert has a full text, which names its action, and a short form of about 22 characters in two parts joined by a double space: first what, and where or the one number; then who is on it, the key, or the time. A style may break the line at the double space, so each part stays within 19 characters. The warning row always shows the full text. Whether the caution slots are fixed or sized to their text is the style's choice; a caution shows its full text when it fits its slot and the short form when it doesn't. The full text is always on hover or focus, in the book and in the log, so nothing is lost by the short form. A warning pushed down into the caution row takes its short form there. The list runs in order of severity, which ranks the caution row (9.1).

| Id | Level | When | Full text, with the action | Short form | Lamp |
|---|---|---|---|---|---|
| MISSILE | Warning under 20 s to impact, else caution (the time includes the missile's burn, below) | Missiles inbound (`FlightWarnings.inbound`) | `MISSILE  2 inbound  impact 14 s   F decoy` | `MISSILE 2  38 s` | - |
| COLLISION | Warning | Time to contact under 10 s at more than the safe speed | `COLLISION  Highport in 6 s   brake` | `COLLISION  6 s` | - |
| DISABLED | Warning | The ship is disabled | `SHIP DISABLED  no reactor running   options below` (opens CRS) | `DISABLED  no reactor` | as the cause |
| ABANDON | Warning | Abandon ship ordered | `ABANDON SHIP  pods launch in 22 s   P now` | `ABANDON 22 s  P` | - |
| CAPTAIN AIR | Warning | The captain's suit air under 60 s, or the captain without a suit in air that hurts them (under 30% oxygen, `CrewDef.asphyxiationThreshold`) | `YOUR AIR  48 s   E at a locker, or leave the room` (without a suit, `YOUR AIR  Bridge 20% oxygen   E at a locker, or leave the room`) | `YOUR AIR  48 s` | AIR |
| CAPTAIN HURT | Warning | The captain critically wounded, their clock running as CRITICAL's does (`CrewMedical`) | `YOU ARE CRITICAL  4:10 to live   Ada on the way` (or `nobody free`) | `YOU CRITICAL  4:10` | CREW |
| CRITICAL | Warning | Someone aboard critically wounded whose clock to bleed out runs: not stabilised, and nobody treating them (`CrewMedical`; ANN's CREW row) | `CRITICAL  Cole, 4:10 to live   Ada stabilising` (or `nobody free`) | `CRITICAL Cole  4:10` | CREW |
| BOARDERS | Warning | A pirate comes alongside the beaten ship to board it (`PirateBoarding`): latched within 300 m, it takes the hold and a tenth of the credits after 60 s, and one hit from within 1 km before then drives it off | `BOARDERS  Pirate raider 3 takes the hold in 48 s   Space: one hit drives it off` (before it latches, `coming alongside`; on deck, `V at a gun`; with no gun working, `no gun to drive it off`) | `BOARDERS  48 s` | - |
| EVA BURN | Warning | Crew outside and a burn under way or due in 30 s | `CREW OUTSIDE  Ada on the hull   hold the burn; I: call them in` | `CREW OUTSIDE  Ada` | EVA |
| FIRE | Caution; warning when large or in the captain's room | Any fire | `FIRE  Hold   Cole on it` (or `nobody on it`) | `FIRE Hold  Cole` | FIRE |
| VENTING | Caution | Air venting to space | `VENTING  Bridge   nobody: no patches left (Y at a yard)` | `VENTING Bridge  nobody` | AIR |
| AIR | Caution; warning while anyone without a suit is in air that hurts them (under 30% oxygen; the captain's own is CAPTAIN AIR) | A room below 70% pressure or smoky while it is not venting (VENTING has that), an oxygen or nitrogen tank below 25% (the gauges' `ShipGauges.CRITICAL`) or dry (`AirWatch`), or life support damaged or stunned (a short or a cut is POWER's) | `THIN AIR  Hold 55%   life support refilling` (or `no life support there`); `O2 TANKS DRY  12 kg reserve   a yard refills them`; `LIFE SUPPORT DAMAGED   Ada repairing`; `VACUUM  Hold   Cole there without a suit` | `THIN AIR Hold  55%` | AIR |
| SUIT AIR | Caution; warning under 60 s | Someone aboard, the captain included, in bad air with under 4 minutes left in their suit and nothing aboard to fill it (`AirWatch.WARN_AT`); under 60 s the captain's own is CAPTAIN AIR | `SUIT AIR  Ada 3:20 left, nothing aboard fills it   get to a station or good air` | `SUIT AIR Ada  3:20` | AIR |
| EVA | Caution | Someone outside thrown off the hull or adrift, their tether parted, or under 5 minutes of suit air (`Spacewalks`) | `ADRIFT  Ada 340 m off   bring the ship to them` (or `Bea bringing them in`: nobody goes out at battle stations or while EVA is held); `TETHER PARTED  Ada, on the hull   I: call them in`; `EVA AIR  Ada 4:20 left   I: call them in` | `ADRIFT Ada  340 m` | EVA |
| WOUNDED | Caution | Someone seriously wounded or unconscious, or critically wounded and stabilised, with nobody treating them (any of the crew can treat, a medic fastest) | `WOUNDED  Cole down   nobody free to treat` (or `Ada on the way`) | `WOUNDED Cole  nobody` | CREW |
| LOCKED ON | Caution | A hostile ship has this ship locked, so its guns are on it (`SensorComponent.lockedTarget`), and it is not the target TGT shows: on deck every such ship, at the helm and the guns any but the target | `LOCKED ON  Pirate raider 3, 3.2 km   V at a console to fight` (at the helm or the guns, `Tab to target it`) | `LOCKED ON  Pirate raider 3` | - |
| IMPACT | Warning under 10 min, else caution | Periapsis below the surface | `SURFACE IMPACT in 8:20   raise periapsis (M)` | `IMPACT 1:20:00  M` | - |
| INTERCEPT | Caution | A pirate on the rails moves to intercept the ship (`HostileTraffic.intercepting`), until it reaches the bubble, breaks off or is beaten. It drops warp once and holds the autopilot (`pirates about`) | `INTERCEPT  Pirate raider 3 coming for you   M to see its path` | `INTERCEPT  Pirate raider 3` | - |
| POWER | Caution; warning when no reactor feeds anything | As the POWER lamp (never the store's level) | `POWER  Bridge conduit short   Ada resetting`; `NO POWER  reactor shorted   Ada resetting; breaker recloses in 41 s` | `POWER Bridge  Ada` | POWER |
| DRIVE | Caution | The main engine, an RCS block or the helm console damaged or stunned (a short is POWER's) | `DRIVE  main engine damaged   Ada repairing`; `HELM CONSOLE DAMAGED   nobody can fly; Ada repairing`; `RCS BLOCK WRECKED  aft   nobody: EVA held` (the crew frame its tile back up on EVA, never at battle stations) | `DRIVE main  Ada` | DRIVE |
| ARMS | Caution; warning when none works in combat | A mount or the weapons console damaged or stunned (power and shorts are POWER's) | `NOSE COILGUN DAMAGED   Ada repairing`; `WEAPONS CONSOLE DAMAGED   gunners and the trigger only; Ada repairing`; `NO GUNS WORKING   break off` | `NOSE GUN  Ada` | ARMS |
| SHIELD | Caution | Shields down in combat, an emitter tripped, damaged or stunned, or the cloud below a quarter | `SHIELD TRIPPED  back in 8 s` | `SHIELD TRIPPED  8 s` | SHIELD |
| HULL | Caution | Mean hull integrity below 75% (`ShipAlerts.HULL_DAMAGED`); below 45% the ship is disabled, which is DISABLED | `HULL DAMAGED   a yard repairs it` | `HULL DAMAGED  yard` | HULL |
| PROPELLANT | Caution | Below 1.25 times home, or short of the plan | `PROPELLANT  500 m/s left, home takes 410` | `PROP 500 m/s  home 410` | PROP |
| HELM | Caution | An order waits with no helmsman, or was dropped: nobody took the helm in time, its target is gone, or a shot dropped it (every order but hold position and match velocity, `HelmOrders`); or the autopilot stopped short: its destination gone, or the transfer flown with it still far off (`Autopilot`) | `HELM ORDER DROPPED  approach, shot at   4 when clear; 3 holds under fire`; `AUTOPILOT OFF  Shackleton Ice 40 km off   ; plans to it again` | `HELM DROPPED  4` | - |
| BURN MISSED | Caution | A planned burn missed or cut short | `BURN 1 MISSED   replan (M)` | `BURN 1 MISSED  M` | - |
| COLLAR | Caution | The selected port's collar jammed (the crew free it on EVA, but at battle stations only someone sent out; a yard refits it) | `PORT 1 JAMMED   Ada freeing it on EVA` (at battle stations `nobody   I: send someone out`; with a second port, `H next port`) | `PORT 1 JAMMED  Ada` | - |
| CONTRACT | Caution | A contract's arrival is after its deadline, or under 6 h left with no plan | `LATE  Deliver 40 Food to Shackleton Ice, 1d 9h late   M` | `LATE Shackleton Ice  M` | - |

**Time to impact.** Every impact countdown (the band, the 20 s threshold) includes the missile's own acceleration, as the game now works it (`ImpactTime`, which `CombatThreats` uses): while a missile still burns, `t = (−v + √(v² + 2ad)) / a` for closing speed v, range d and its closing burn a (`MissileDef.closingBurn`, 30 m/s² unless `weapons.toml` sets it), then a coast. The closest approach at the present speed, which the game used before, ran long and then fell faster than the clock (from 7.7 km it read 20 s with 12 s left); with the burn in it, 20 s means 20 s.

**Where the game's warning notices go.** Today every notice of kind WARNING sounds and joins the notice stack (`NoticeLog.Kind.WARNING`). Here each becomes the alert that carries its condition, a line of the panel that asked for it, or news when nothing is left to decide. This is every notice the game posts as a warning, the debug menu's crew drill (`CrewScene`) aside and the helm's included (`Navigation` posts the helm's notices as warnings unless they start `Helm:`):

| Today's warning notice | Becomes |
|---|---|
| The missile salvo (`FlightWarnings`) | MISSILE |
| `Propellant low: ... left`, with how it stands against the trip home (`FlightWarnings`) | PROPELLANT |
| `Your ship is disabled: no reactor running` | DISABLED |
| `Your ship is abandoned`, `Your ship breaks up: a wreck` | IMPORTANT news, and the end screen's line if the career ends there (section 7, Shell) |
| `Call the sponsor's tug? B again ...`, `Abandon ship? P again ...` (the first press of each) | The crisis panel's own line, asking for the second press |
| `Highport: your ship is venting at the berth. Time warp waits ...` (`BerthVenting`); `Hull leaking: the crew are patching it ...`, `Hull leaking and no patches left: ...`, `Out of hull patches: ...` (`DamageControl`) | VENTING, and the chronometer's `held: air venting` |
| `Oxygen tanks dry: ...`, `Air running out: ...` (`AirWatch`) | AIR; SUIT AIR, which becomes CAPTAIN AIR once the captain's own suit is under 60 s |
| `The air here is going: get to a pressurised room` (and, without a suit, `... or a suit locker to put on a suit`) (`CaptainSafety`, as the captain's air stops being fit to breathe) | PER's room line (`thin`), the room's AIR or VENTING, and CAPTAIN AIR once the air hurts the captain or the suit is under 60 s |
| `Ada is thrown off the hull`, `Ada's tether has parted`, `Ada is adrift 340 m off with no way back alone: bring the ship to them`, `Ada's suit is out of air` (`Spacewalks`) | EVA |
| `Ada is caught in the main engines' exhaust`, `Ada's tether has burned through in the main engines' exhaust` | EVA BURN, then EVA |
| `Cole is critically wounded: treat within 4:10 or they die` | CRITICAL, or CAPTAIN HURT for the captain |
| `Cole is seriously wounded and down until treated` | WOUNDED |
| `Pirate raider 3 is moving to intercept you` | INTERCEPT |
| `Pirate raider 3 is coming alongside to board you`, `Pirate raider 3 has latched on: pirates are aboard` (`PirateBoarding`) | BOARDERS |
| `Port 1's docking collar is jammed: it won't arm till the crew free it on EVA` (`JammedCollars`) | COLLAR for the selected port; IMPORTANT news for another |
| `Burn missed: nobody flew the planned 120 m/s burn in time`, `Burn cut short: nobody at the helm, ...`, `Out of propellant: burn cut short, ...` (`HelmOrders`) | BURN MISSED |
| `Helm order dropped: ...` (`HelmOrders`), `Autopilot off: ...`, and `Autopilot: arrived off ...` when the helm can't take over | HELM |
| `Autopilot holds: pirates about. 6 or raising warp goes on` | HLM's line (`autopilot to Shackleton Ice, holding: pirates about`) and the log, not the band: HLM already shows it |
| `Ada is left behind, adrift`; `Cole has died of wounds`, `Cole is dead`; `1 crew member went unpaid: morale falls` (`CrewCareer`) | IMPORTANT news: it has happened, and nothing in the band could undo it |
| `Pirate raider 3 got away from you: it flies as the ace Black Sal now, and it will come looking for you`; `Word on the lanes: the ace ... flies a pirate gunship now` (`HostileSpawns`); `The boarders from Pirate raider 3 took ..., and cast off`; `... scrambles 2 raiders against you, and its guns open up`, `... launches 2 more raiders` (`PirateBase`: each raider's lock then raises LOCKED ON) | IMPORTANT news |
| `... takes that as a hostile act: ... its stations will refuse you clearance`; `Tension between the ... runs high ...`; `2 of the prisoners are critically wounded: bring them aboard to treat them` (BRD shows them too); a field refit's part lost, or its mounting called off (`FieldRefits`); a tank burst, or a short that started a fire (`DamageControl`) | IMPORTANT news, with the alert for whatever it leaves (FIRE, POWER, AIR, PROPELLANT) |
| `The couch burned` (a furnishing lost to fire) | INFO news |

A system that posts a new warning notice adds its row here: an alert if the player has something to decide, news if not.

### 9.3 Acknowledging

Nothing needs a key. A caution stays in the band for 10 s, then leaves it and lives on as its lit lamp. A warning stays until its condition clears; its sound repeats at most three times. Clicking the band acknowledges at once: cautions leave, warnings stop sounding and pulsing. An action `Acknowledge alerts`, unbound by default, does the same for players who want a key. A caution that worsens (a fire grows to large, a second room vents) comes back.

### 9.4 Relations and states (markers, scope, target card)

Every contact belongs to one relation class and carries one state. Artists choose the treatment; the spec requires that classes differ by more than colour (shape, outline, fill or pattern too).

| Relation | Who |
|---|---|
| Hostile | At war with your sponsor, outlaws, anyone firing on you |
| Neutral | Everyone else with a faction |
| Friendly | Your sponsor and its allies |
| Own | Your ship, your prize, your crew outside |
| Inert | Derelicts, wrecks, pods, debris |

| State | Meaning |
|---|---|
| Active | Flying and able |
| Disabled | `DisabledComponent` with its reason |
| Struck | Surrendered, or silenced and spared (`Struck`) |
| Wrecked | For good |

Contract involvement is a tag over any class, not a class of its own.

---

## 10. Input, focus and closing things

**Where the existing keys go.** No new letter keys are needed; every key keeps its action and gets a single destination.

| Action (default key) | Opens or does |
|---|---|
| Map (M) | The map, any post |
| Station market (T) | The terminal on Market, while docked |
| Station yard (Y) | The terminal on Yard while docked; off a station, the field refit or the prize handover |
| Mission board (J) | The terminal on Contracts while docked; else the book on Contracts |
| Crew roster (I) | The book on Crew, so an alert's `I:` lands where its action is |
| Notice log (L) | The book on Log |
| Boarding window (K) | The boarding panel |
| Controls (F1) | The controls reference |
| Use console (E) | A console panel, a suit locker, the interaction under the prompt |
| Helm orders (1 to 6, 0) | As now; clicking HLM lists them with their keys |
| Acknowledge alerts (new, unbound) | Acknowledges the alert band, as clicking it does |
| Esc | Closes the top layer (below) |

**Closing order.** Esc closes the top layer, one per press: a confirmation; then the controls reference, which opens over everything, screens included, so F1 opened over the map closes before the map; then the placement tool; then a screen (map, terminal, book); then a panel (boarding, console); and with nothing open, it opens the pause menu. The pad's B in a screen and Start at a post do the same.

**One at a time.** One screen at a time; opening another replaces it. Panels close when a screen opens, and reopen when it closes if their condition still holds.

**Gamepad.** The pad keeps today's layout (`GamepadKeys`): the left stick flies and walks, the right stick strafes, A fires and uses, B launches, X decoys, Y changes view, L2 cycles targets, the d-pad steps warp and docks, Start pauses, and Back tapped opens the map and held gives a second layer. Screens and panels use `FocusNav` as now: in a screen the bumpers change tab and B goes back, and the Back layer's A, B, X and Y open the terminal's Contracts, the boarding panel, and the terminal's Market and Yard, as they do today. Back with R2 sends B, also as today: on deck it cuts the door the captain stands at, or orders the crew to cut one, and stranded it calls the tow; on the map it selects the next burn. The controls reference names it on the pad as `Back + R2`. Today the pad can't reach the shields, zoom, lights, the next port, the map's own keys, the book's Crew and Log, the controls or quick load, and at the guns it can't aim. These close the gaps:

| What | On the pad | Why there |
|---|---|---|
| Aim the guns in hand (at the weapons console or a gun) | The right stick moves the aim point the guns follow, as the pointer would (`PlayerGunnery.aim`); it starts on the locked target, and R3 puts it back there. The aim point is drawn, since a pad has no pointer | Only the weapons answer at gunnery, so both sticks are free |
| Shields (9 up or down, 8 trim) | R1 and L1, at the helm and the guns | The bumpers only doubled the right stick's strafe, which stays |
| Zoom, the view's and the map's | Back held, right stick up or down | The one input the Back layer leaves free |
| Lights (O) | R3 on deck | Rotation assist means nothing on foot |
| Next port (H), the scope's range, which guns the trigger fires, the helm orders (1 to 6, 0), a contract as destination, the next stop | HUD focus, then A on the item | Each is what a click on the HUD does |
| The map's Q ; / Enter Backspace, and warp | The pad works the map as a screen: the d-pad and left stick move focus through the buttons MAP.1 to MAP.4 show (every key they show is one), A presses, B closes, the bumpers step the destination (Q, Shift+Q) and the triggers step warp (, .); Back held edits burns as today | A screen's keys are its buttons, and nothing fires from the map |
| The book's Crew and Log (I, L) | Back and A opens the book on Contracts off a station (J), and the bumpers step its tabs; docked, A on a lamp in HUD focus opens it on Ship | J already opens the book off a station |
| Controls (F1) and quick load (F9) | The pause menu (Start): Controls, and Load, which lists the quick save | Neither is wanted in a hurry |

**HUD focus.** L3, the one button free in the flying layer, puts focus on the HUD; the d-pad then moves through the items of what is shown (CHR, ALR, ANN, HLM, OBJ, FLT, WPN, SNS), A does what a click would, and B or L3 again leaves. While the pad is the last thing used, every key named on screen (hints, alerts, a mode's keys) is named as its pad button, or by its route when it has none (`L3, then A`).

**Pointer.** Everything in the world can be hovered for its details and clicked to select. Hover never shows a number that is not also somewhere a keyboard player can reach.

**Scale and text.** Layouts are in reference units scaled by `UiScale`. Any number read under time pressure (FLT, WPN, ALR, the target card's range and closing) is at least body size (15 units today); nothing the player must read is under 12 units.

**Motion.** Nothing blinks faster than 3 times a second; with reduced motion on, pulsing becomes a steady mark.

---

## 11. Audit of the current UI

Done last, against the design above. The current HUD in flight near Highport with no target locked and nothing planned ([the flight screenshot](../ui-hud/polish/flight-1280x800.jpg), after) shows, besides the missile banner, about thirty lines of text in six framed panels, a six-key hint line and about ten world labels.

### 11.1 Element by element

| Today | Fate | Where it goes, and why |
|---|---|---|
| FLIGHT panel: speed (orbital, 7,672 m/s) | Rework | FLT CRUISE shows speed in the frame that matters; orbital speed only in the orbit frame or on the map. Near anything, the orbital speed answers no question (F1) |
| FLIGHT panel: heading in degrees | Remove | The ship's nose is on screen; BURN mode gives the pointing error (F5) |
| FLIGHT panel: view `EXTERIOR [V]` | Remove | The view shows it; V is a hint |
| FLIGHT panel: markers frame | Merge | FLT's frame label |
| FLIGHT panel: TARGET block | Move | TGT |
| FLIGHT panel: ORBIT block | Move | The map; IMPACT alert when it matters (F8) |
| SHIP STATUS gauges | Rework | Lamps (ANN) for state; delta-v in FLT; kilograms and percentages in the book (principle 2) |
| RADAR panel | Keep, rework | SNS with automatic range, hidden on deck and docked |
| SHIP panel (minimap, `HULL 100%`, `ALL SYSTEMS NOMINAL`, alert list) | Rework | SCH only outside and only when something is wrong; the alert list becomes lamps and the band (principle 3) |
| ORBIT mini map and its two hints | Remove | The map is one key away; the hints go to the hint line |
| Status column: clock and warp | Move | CHR, with the next stop and what holds warp |
| Status column: `Target:` line, `FIRING ON YOU`, `(gun reloading)` | Move | TGT and WPN |
| Status column: `Missile: ready (4 left)` | Move | WPN |
| Status column: rank and budget line | Move | The book's Career tab; paydays are news |
| Status column: missile warning (and the deck's large line) | Merge | ALR MISSILE |
| Status column: disabled and abandon lines | Move | CRS and ALR |
| Status column: notices | Split | Warnings to ALR, the rest to NTF |
| Status column: docking lines (`Dock port 1/1...`, `To Highport #2: 3,015 m off -2.2 m turn -0 deg in -0.08 m/s`, safe speed, `Docked at Highport strain 0%`) | Rework | FLT DOCK as an instrument, not a sentence; DEPARTURE when docked; strain only as a caution when high |
| Status column: navigation lines (target, closest approach, next burn, helm order, delta-v) | Split | TGT, FLT BURN, HLM, FLT CRUISE |
| Status column: contract lines | Move | OBJ |
| Status column: First Play objective, walkthrough text | Move | OBJ |
| Status column: bridge line | Move | HLM |
| Status column: EVA line | Move | ANN EVA lamp, ALR EVA BURN |
| Missile banner (top centre) | Merge | ALR |
| Station banner `Docked at Highport [T] Trade [Y] Yard [J] Missions` (the market's dock hint) | Remove | FLT DEPARTURE names the berth and offers U and T (a mode's keys are not repeated on the hint line) |
| Hint line | Keep, rework | HNT, four keys, learning |
| `Press [E] for the ... panel` at the bottom | Move | INT beside the object |
| Contact markers, edge arrows | Keep, rework | Label and arrow rules (OVL) |
| Prograde markers, burn arrows, lead reticle, missile paths, PD rings, docking corridor | Keep | OVL, with the label rules |
| Trade, yard, shipyard and mission windows | Merge | TRM tabs, with reach, cost basis and gun numbers added |
| Crew window | Split | Roster to the book's Crew tab; hiring to the terminal's Crew tab |
| Boarding window | Rework | BRD, phased, beside the view |
| Console window | Rework | CON, anchored at the console |
| Notice log (L) | Move | The book's Log tab, filterable |
| Controls panel (F1) | Keep, rework | Grouped by post |
| Map (M) | Keep, rework | Destination, plan, fly-it panels; the plan timeline; layers |
| Tension readout on the map | Move | A map layer and the book's Career tab |
| Pause menu's `Call a tow` | Move | CRS |
| Main menu, settings, load and save | Keep | Load list adds where the save was made; settings gain the interface group |
| Mod trust dialog, developer console, debug menu (F3) | Unchanged | Out of scope; the developer tools stay out of player builds' hints |

### 11.2 Things shown twice or more today

- The target: FLIGHT's TARGET block, the status column's `Target:` line and its world marker.
- The orbit: FLIGHT's ORBIT block, the orbit mini map, and the map.
- Docked: the station banner and the status column's `Docked at Highport`.
- Hull: the structure gauge and the ship panel's `HULL 100%`.
- The missile warning: the banner and (before the last polish) a status column line; on deck a third, large line.
- Keys: the hint line, `[V]` and `[Tab]` in FLIGHT, the orbit panel's two hints, the station banner, and notices such as `J opens its mission board`.

### 11.3 Decisions the current UI leaves unanswered

| Decision | What is missing today |
|---|---|
| F3 Dock | The approach is a sentence of signed numbers; nothing graphic says left or right, or how far over the safe speed |
| F6 Skip time | What stops warp is only learned by being refused |
| F7 Hand over the helm | Which orders can be given here is only in F1; their state is one line among many |
| C3, C9 Shoot, man the guns | One `(gun reloading)` for all guns; nothing says which guns are crewed, powered or in arc |
| C5 Survive missiles | Whether the decoy is ready is never shown (decoys are unlimited, one every 3 s) |
| C6 Shields | No gauge at all (combat-sys: "Not built yet: the HUD gauge and coverage overlay") |
| S3 Step in or not | The alert list says what is wrong, never who is on it |
| S6 My own body | The captain's health, suit and air are shown nowhere |
| N2, E1 Plan, take a contract | No arrival against deadline; the board gives no distance or delta-v |
| E2 Trade | No record of what you paid |
| E4 Refit | Gun names and prices only, no numbers to compare |
| E7 Career | Budget shown always; standing against the next rank never |

---

## 12. Notes for the artist threads

**Fixed by this spec** (change only through a revision of it):

- The posts and conditions (section 2) and the visibility rules (section 8).
- Which widget is in which zone, and the sides: self left, others right, motion bottom centre, time and attention top, the centre clear.
- What each widget shows and its states (section 6), the screens' parts (section 7).
- The alert levels, their order and acknowledgement (section 9).

**Free for each style:**

- Every shape, frame, font, colour, texture, sound and motion.
- How a widget draws its content: a bar, a dial, a segment readout, a printed strip, a physical-looking switch. FLT's DOCK mode could be crosshairs, a moving tape or two needles, as long as it shows the same quantities.
- Sizes within a quarter of the wireframes, and the shape of each zone, so long as the clear zone stays clear and sides don't swap.
- Diegetic framing: whether the HUD reads as a helmet, a bezel, a console, a CRT or a printout. A style can tie a widget's look to the post (the helm looks like the helm console) since posts already change the HUD.
- Whether screens are full-bleed or framed windows.

**Every widget needs these states drawn:** hidden, compact (where it has one), normal, caution, warning, unavailable with its reason, keyboard or pad focus, hover, pressed. Relation classes need five distinct treatments; lamps need four states (dark, advisory, caution, warning) and the band two levels (caution, warning). All of them need a colour-blind-safe variant that differs by more than hue.

**Text room.** Names come from data (ships, stations, crew, mods), so every text field must take a name half again as long as the samples here, and say what it does when one is longer (cut with an ellipsis, never wrap into the next row; OBJ rows and NTF slips alone may take a second line, section 6). Numbers keep their width as they change (fixed-width digits), so a countdown doesn't jitter.

**Suggested reference screens** for comparing the nine combinations: helm at rest; gunnery in combat; deck in damage control; docked at the departure panel with the terminal open; the map with a late plan; the crisis panel. The wireframes in this spec are those screens.

---

## 13. What the game needs to expose

Data the widgets need that the UI can't read yet. The HUD framework (PR #265) gave the HUD a common data layer, `HudData`: the alert registry, and snapshots of the post (`UiContext`: where the captain is, combat, who flies and the standing order, warp's next stop in seconds and what holds warp), the captain (health, wound and bleed-out clock, suit and its air, the room's air), every mount (state, who works it, reload, magazine, arc, range, the decoy), the shields (cloud, sectors, trim, emitters and heat), power (circuits and every fault) and the target (relation, state, range, closest approach, hull, its lock on you), with room names (`RoomNames`). Each line below is what that still lacks, with where the data lives, where it does.

| Need | For | Source today |
|---|---|---|
| The alerts the registry doesn't raise yet, and 9.2's levels | ALR, ANN | Below the table |
| Each wounded or unconscious person aboard, the captain included: who treats them, who is stabilising them or on the way, and whether a critical wound is stabilised | CREW lamp, CRITICAL, CAPTAIN HURT, WOUNDED, PER, BOOK Crew | `CrewMemberComponent.treatedBy` and `stabilised`, and the claims on each `TreatTask` and `StabiliseTask` (stabilising sets no `treatedBy`). `CrewMedical.condition` folds being treated and stabilised into its string for serious and critical wounds, without the name, and says neither for the unconscious |
| Hostile ships with this ship locked, beyond the target | LOCKED ON | Each ship's `SensorComponent.lockedTarget`; `TargetStatus.firingOnUs` reads it for the target alone |
| Pirates hunting the ship beyond the bubble | INTERCEPT | `HostileTraffic.intercepting`, `HostileComponent.huntingPlayer` |
| The log: 500 entries, each alert's clear time, saved with the game | BOOK Log | `NoticeLog.KEEP` and `MessageFeed.KEEP` are 50, and neither is saved |
| An aim point the pad's right stick moves at gunnery | WPN, OVL | `PlayerGunnery.aim` takes the pointer's position only |
| Per mount: the gunner's name, the time of flight, `max_range` (for `OUT OF RANGE`), a launcher's seeker lock | WPN | `GunCrews`, the weapon's definition, the missile's seeker |
| When a tripped shield emitter comes back | WPN, SHIELD | `ShieldModel` restarts it once its heat falls to `RESTART_AT` of its `heatTrip`, at its `cooling` rate |
| A target's class, owner and contract; a station's services, standing and clearance | TGT, markers | `HudMarkerComponent`, `ContractBoard`, the station's own data |
| Scope range that holds inbound missiles and the target | SNS | New: an automatic range in `RadarScope`, and a 5 km step in `RadarScope.RANGES` |
| Reach of a destination: delta-v and travel time from here, arrival against deadline | TRM Contracts, MAP.2, OBJ, CONTRACT alert | `RoutePlanner.estimate`, `Navigation.returnDeltaV`, `PlayerTransfer` |
| What warp's next stop is (`UiContext.warpNextStop` has only its time) | CHR | `Navigation.nextDropOutTime`, `MacroSimulation.nextDropOutTime` (the encounter forecast), `AutopilotComponent` |
| Who will fly the next planned burn (`UiContext` has who flies now) | MAP.3 | `HelmOrderComponent.burning` and `Bridge.helmsman` |
| Who is working each hazard, by name, and progress | Deck overlays, ALR, BOOK Ship | `ShipTaskBoardComponent` claims, the crew's `showProgress`; `Alert.handling` holds a count, which nothing sets yet |
| Room names from chassis data | Every widget that names a room | New: an optional name per room in chassis data (`RoomNames` has the rest of the rule) |
| Departure readiness | FLT DEPARTURE | Aggregate of the above and `YardProvider` |
| What the player paid for cargo | TRM Market | New: a cost basis on the player's `TraderComponent` |
| Time to contact with bubble bodies | COLLISION | New |
| Hints learned per action and context | HNT | New, in settings |
| The save's location | Load list | New field in `SaveSummary` |

**The alert registry.** `AlertRegistry` holds what 9.2 needs (id, level, text, room, tile, timer, count, and who is on it in `handling`), and `GameAlerts` raises the game's conditions into it each frame. Its levels are the game's, shared by all nine variants, so a Task variant reads it through one mapping to 9.2, the same for the three styles: the registry's ids gathered into 9.2's alerts (a `sys:` row is POWER's, DRIVE's, ARMS's, SHIELD's or AIR's by its system, and a short or a cut is POWER's; an airlock's or a docking port's lights nothing, since FLT's DOCK mode and COLLAR show the port), each at 9.2's level so a lamp and its alert agree (ANN), with 9.2's text, action and short form. Where the levels differ today:

| `GameAlerts` raises | At | Here |
|---|---|---|
| `propellant`, below what takes you home | Warning | PROPELLANT, a caution: the tow is the crisis panel's |
| `sys:`, a wrecked engine, gun or shield emitter | Warning | DRIVE, ARMS or SHIELD, cautions; ARMS is a warning only when no gun works in combat |
| `sys:`, a reactor wrecked, damaged, shorted or stunned | Warning | POWER, a caution while another reactor feeds the ship |
| `fire:`, any fire | Warning | FIRE, a caution until large or in the captain's room |
| `breach:` | Warning | VENTING, a caution |
| `air:` for a room in vacuum, `oxygen` with the tanks dry | Warning | AIR, a caution until someone without a suit is in air that hurts them |
| `captain:air`, no suit below 70% pressure | Warning | The room's AIR caution, until under 30% oxygen: then CAPTAIN AIR |
| `captain:suit`, by share of a full suit | Caution, then warning | SUIT AIR by time: under 4 minutes with nothing aboard to fill it, then CAPTAIN AIR under 60 s |
| `hull`, below 40% | Warning | HULL stays a caution; DISABLED is the warning, from 45% |
| `casualty:` and `captain:bleeding` while someone treats them | Warning | Advisory: treatment holds the clock |
| `missile` | Warning | A caution until 20 s to impact |
| `helm`, the helm console wrecked, switched off or unpowered | Caution | DRIVE when damaged; POWER when cut off, an advisory when switched off. HELM is about orders |
| `hostile`, the target has you locked | Caution | LOCKED ON, which takes no slot for the target TGT shows |
| `hostile` as `Hostile contact`; `empty:`, a launcher out | Advisory | Dropped: no lamp carries them (WPN shows `0/4`) |

Not raised yet: COLLISION, whose time to contact is new, and from data the game has: IMPACT, BOARDERS (`PirateBoarding`), EVA BURN and EVA (`Spacewalks`' events), SUIT AIR for the crew (`AirWatch` finds them; only the captain's is raised), WOUNDED, LOCKED ON beyond the target, INTERCEPT (`HostileTraffic`), BURN MISSED, HELM's dropped orders and autopilot stops (`HelmOrders`, `Autopilot`), CONTRACT, COLLAR, and SHIELD's cloud below a quarter.

The current widgets map onto the new ones as follows, which may help whoever builds it: `FlightReadoutSystem` into FLT and TGT; `ShipGauges` into ANN and the book; `WireframeMinimap` and `WireframeSyncSystem` into SCH and the book; `RadarScope` into SNS; `ThreatOverlay` into OVL and ALR; `TradeWindow`, `YardWindow`, `ShipyardWindow`, `MissionWindow` into TRM; `CrewWindow` into BOOK and TRM; `BoardingWindow` into BRD; `ConsoleWindow` into CON; `GameHudText`'s status column into CHR, ALR, OBJ, NTF, FLT, HLM and CRS; `HintRegistry` into HNT and F1; `NoticeLog` into NTF and BOOK.

---

## 14. Open questions for Joe

Each has the default this spec assumes until he answers.

1. **Pause behind screens.** The game runs on behind the map, terminal and book, as it does behind windows today. Should a screen pause the game when the ship is not in combat? Default: keeps running; a setting `Pause in screens outside combat`, off.
2. **Switching systems from the book.** Systems are switched at their consoles, which keeps walking the ship meaningful. Should the book switch them remotely? Default: no; the book marks the console to walk to.
3. **Rules of engagement.** Crewed guns fire at will on whoever fights the player. The game has an order to hold fire (`GunCrews.holdFire`, which the HUD's data carries), but no key or console gives it. The decisions around prizes and neutral traffic (C8) suggest a hold-fire / return-fire / fire-at-will switch on WPN. Default: not in this spec; flagged. If the order gets a key, WPN's header says `hold fire` while it stands.
4. **What the deck knows about outside.** On deck, threats reach the captain only as alerts (no scope, no target card). Default: alerts only, so taking a seat means something.
5. **Price memory.** Trading well needs the prices last seen at other stations. Default: not shown; the market adds what you paid, which needs only a cost basis.
6. **Target hull precision.** Sensors give four bands, not a percentage. Default: bands.

---

## 15. Revision log

| Round | Changes |
|---|---|
| 1 | First version |
| 2 | From the Nasa-punk thread's round 1 notes, checked against the code: WPN has one row per mount, point defense included (it is the mounts with `point_defense`, automatic at the weapons console), with `CHARGING` and `OUT OF RANGE` states, launcher magazines and the decoy as a countdown (decoys are unlimited); the lead reticle is only for guns that need lead. The power store no longer lights POWER (it empties on every laser pulse); POWER's warning is "no reactor feeds anything", which covers a one-reactor ship with its reactor shorted. A rule for naming rooms, which the game doesn't. The flight frame is defined ("near" is 5 km) and the markers follow it. Stations always get labels. Hazard tags show progress, not seconds. Z9 is a full-width strip and every zone ends above it; Z5 is 300 x 150. Colour words left the spec. From this designer's own review: lamps gain an advisory state, so normal things (crew outside) stop looking like trouble; the map hides OBJ and owns the deadline comparison; comparisons may quote what they compare against; the crisis panel doesn't repeat the band; IMPACT and COLLAR light no lamp; the pad reaches the HUD through L3. Every sample now follows the starter corvette as the game builds it: two rooms (Bridge and Hold), a 40 m3 hold, no shield emitter, a launcher magazine of 4, eight RCS blocks. Wireframes redrawn to match. |
| 3 | From the VFD thread's round 1 notes: SCH at gunnery, WPN running under the key strip, the speed at rest in the orbit frame, decoy counts and the corvette's rooms were already answered by round 2. New: the caution row keeps its place when no warning stands (Z2 is 76 units tall), and screens start below both rows (100 units down), so an arriving alert never covers a screen's header. From this designer's own review: the hint line is hidden over screens, whose parts show their own keys; the corvette shows nine lamps, not ten; chassis room names are pinned to tiles and survive damage; F1's frame matches FLT's order; the departure readout folds the contract check into its delta-v line; the station banner's T moves to the DEPARTURE mode; the book's header names its close key. |
| 4 | From the Cassette thread's round 1 notes, checked against the code: FLT has four modes and hides in rails warp, since the chronometer already carries the rate, the next stop and what holds warp. HLM never shows a countdown, and BURN names who flies only when it is you or the flight computer. The BURN and CRUISE samples lost their apoapsis and closing speed (the map's and the target card's). The crisis hint line is F1 alone. No key repeats on the hint line when a mode, an alert or the interaction prompt already shows it. Every alert has a short form of about 22 characters for the caution row, with the full text on hover, in the book and in the log. The departure check is the whole roster aboard, not berths (the corvette berths 8). Samples use the game's starter crew: Ada the engineer, Bea the helmsman, Cole the marine. Markers carry names, never numbers, so a missile's time to impact is the band's alone. From this designer's own review: the chronometer at rest no longer says `nothing planned`, and rotation assist shows only when it is off (principle 3). |
| 5 | From the VFD thread's round 2 notes: every short form is two parts joined by a double space, which a style may break into two lines of up to 19 characters; `+n` gets a reserved narrow slot at the end of the caution row, so no caution moves when it appears; the band is 640 wide to make room for it; a screen starts below the band wherever a style puts it, not at a fixed 100 units; the interaction prompt names its object once, then each key with a verb (`Helm   V sit`). Whether caution slots are fixed or sized to their text is now the style's choice, so the VFD's fixed windows and the Cassette's sized cells both hold. |
| 6 | From the Nasa-punk thread's round 3 notes, checked against the code: every impact countdown includes the missile's burn, since today's constant-speed estimate reads 20 s with 12 s left from 7.7 km, so the 20 s warning now means 20 s; the scope's automatic range holds every inbound missile and the locked target, with a new 5 km step for the corvette's 4 km reach; the lamp is SHIELD, as the alert and the prose are; point defense is spelled as the game spells it. |
| 7 | From the VFD thread's round 3 notes: the gunnery samples now agree with 9.2's time-to-impact formula (harpoons just thrown from 3.2 km land in about 14 s; at 1.4 km they would be 4 s away). The missile alert no longer gives a bearing, which had no stated reference (north-up view, a ship's bow, or the console's counterclockwise convention could each be meant): direction is drawn in the world, by the missile's path or an edge arrow, and on the scope (principle 5), so the band carries the count, the time and the action. |
| 8 | From the Nasa-punk thread's round 4 notes, checked against the code: the scope's minus and plus buttons are back (round 7 cut that sentence by mistake); the 5 km step's reason is corrected, since fights reach 5 km (the coilgun's `max_range` 5,000 m, a kiting ship holding 4,500 m), not 4 km; one lead reticle, for the selected gun needing the most lead, since two would overlap at fight ranges. From this designer's own check: the gunnery card's closest approach reads 1:15, which is what 3.2 km closing at 41 m/s with 0.6 km at closest gives, and the helm line reads `approaching Raider to 2,000 m`, which fits a closing range. |
| 9 | From the Nasa-punk thread's round 5 note: a ship is named in full wherever it is named (`Raider 'Vex'`, not `Raider`). From this designer's own review against the code: a shot at the ship, or a missile launched at it, drops every helm order but hold position and match velocity (`HelmOrders.threat`), so round 8's gunnery helm line could not stand and reads `holding heading`; a helmsman flies every planned burn whatever the order, so HLM shows a burn being flown with or without order 1, MAP.3 says who will fly the next burn, and a burn being flown beats FLT's DOCK mode (its countdown does not); the HELM caution's sample is an approach, whose drop matters, and names what carries on under fire; the lead reticle is drawn on the target's hull inside its lock mark (about 9 m of lead at 3.2 km); the jammed-collar caution names who frees it on EVA, since the corvette's one port has no next port, and DOCK offers H only with two ports or more; the chronometer's hold reasons are the game's warp guards' (none holds warp for hostiles), so the gunnery and deck samples read `held: fire aboard`, and its next stops gain the encounter forecast and lose the closest approach; I opens the book on Crew, so an alert's `I:` lands where its action is; the CONTRACT sample matches the map's late plan; the at-rest scope reads 5 km, the step its rule gives for Highport at 3.0 km; the wireframes' chronometer had clipped its third line since round 1 and now shows it, which also removed a next stop from the map's chronometer that the map owns. |
| 10 | From the Cassette thread's second notes, checked against the data: Highport sells no ships (`shipyard.toml`: the Patrol's shipyard is Anvil Yards), so its terminal shows four tabs; the corvette's S port docks at one of Highport's S ports (1 and 2; 5 and 6 are M), so the DOCK sample and the helm's docking line use port 2; Shackleton Ice's services add its shipyard (Selene's) and hiring, which every station of a faction that isn't an outlaw's offers (`CrewHiring`). From the Nasa-punk thread's round 9 drawing: the scope's automatic range and one set by hand must look different, since a range set by hand stops following the threats; a style may mark either. From this designer's own check of the same screens: Highport is an agricultural dome that exports food (water is mined only at the Moon), and Freehold lies half way to the Moon, not in Highport's orbit band, so the terminal's delivery is food to Shackleton Ice, the same late delivery the map plans (3,912 m/s, 3.8 days, 22h 40m late), and the deck's objective is that delivery; the escort is Hauler Kestrel, the freighter that loads at Highport; the patrol to Deepwell Depot, 2,000 km higher, costs about 1,860 m/s there and back. |
| 11 | From the Nasa-punk thread's notes on the log and the controls reference, checked against the code: a delivery to another body is due 8 days after it is posted (`ContractBoard.FAR_DAYS`), so the late delivery is now the urgent one Shackleton Ice's food shortage posts at Highport (`WorldEvents`): given by the Selene Mining Combine for +5 standing, due 3 days after day 3's roll (Day 6 00:00, 2d 9h away at 14:22), and 1d 9h 40m late on the 3.8-day transfer. The terminal, the map, the CONTRACT caution and the deck's objective follow it; the list's pay is net of what the cargo costs here, and its standing names a giver who isn't the sponsor. OBJ loses its done state, since the game pays every contract as it ends. The log keeps each alert once, adding the time it clears, holds 500 entries and is saved (the game keeps 50 and saves none). The intercept gets a caution, INTERCEPT. Esc closes the controls reference before the screen under it. The pad reaches everything: the right stick aims at the guns, the bumpers work the shields, Back with the right stick zooms, R3 on deck works the lights, and HUD focus, the map's buttons and the pause menu cover the rest. OBJ rows and NTF slips may take two lines in Z3's 240 units. From this designer's own review, prompted by the intercept: every warning notice the game posts has a stated destination (9.2's new table); the alert list gains HULL, AIR, SUIT AIR, DRIVE, ARMS, CRITICAL, EVA and BOARDERS, so each lamp level has its alert, and the lamps lose the levels no alert had (the PROP, DRIVE and SHIELD warnings); WOUNDED and CAPTAIN HURT follow the game's wounds (anyone aboard can treat, and a critical wound has a clock); the autopilot sample uses the game's own hold reason (`pirates about`); and the deck's payday, impossible on day 3 since the first comes a week in, is now Bea's light wound. |
| 12 | From the Cassette thread's notes on round 11, checked against main now that the HUD framework (PR #265) is merged: the band shows PROP, DRIVE and SHIELD as cautions, at the lamps' levels, and section 13 lists each place where the registry's levels (`GameAlerts`) differ from 9.2's, so the Task variants map them by id; `CrewMedical.condition` already tells whether a serious or critical wound is being treated or stabilised, and section 13 asks for who, and for the unconscious; treatment holds a critical wound's clock but stabilising does not until it is done, so a wound being treated is the CREW lamp's advisory, CRITICAL and CAPTAIN HURT stand only while the clock runs, and WOUNDED can say `Ada on the way`. From this designer's own review against main: the severity that ranks the caution row was never defined and is now 9.2's order (people, then the ship, then the job); bad air starts to hurt under 30% oxygen (`CrewDef.asphyxiationThreshold`), so the AIR and CAPTAIN AIR warnings start there, not at 10% pressure or at air merely unfit to breathe; CAPTAIN AIR lights AIR and ABANDON no lamp, as the lamp table has it; the helm console, the weapons console, life support and a damaged emitter now light DRIVE, ARMS, AIR and SHIELD, where they lit nothing; a new caution, LOCKED ON, tells a captain on deck, who has no target card, that a hostile has the ship locked, which today's status column says and round 11 had lost; the warning notices round 11 missed now have homes: the helm's missed and cut-short burns and dropped orders (`Navigation` posts them as warnings), hull leaks and patches, the captain's air, a jammed collar, boarders latched and gone, deaths, unpaid wages, the pirate base's raiders, the gunship ace and burst tanks; the burn-aware time to impact, the captain's, guns', shields' and target's data and room names exist now, so section 13 keeps only what is still missing; the hold-fire order exists (`GunCrews.holdFire`) but nothing gives it. |
| 13 | From the Nasa-punk thread's notes on rounds 11 and 12, checked against the code: the samples use the game's own names. The pirate corvette near Highport at the start is `Raider` (`GameScenario`), of the Void Raiders, armed as BRD's salvage lists it (a laser and the harpoon launcher among its guns), so the bounty, the gunnery card, the helm line and the boarding panel call it that; a pirate from the lanes is `Pirate raider 3` (`HostileSpawns`), which the alert samples now use, and an ace goes by its callsign (`Black Sal`, `PirateAces`), as the game's escape notice says. TGT never shortens a name. A bounty's title names the target's faction (`ContractPostings`), so OBJ breaks that row before `of the Void Raiders`. The first bounty pays 7,500 (`ContractBoard.BOUNTY_PAY` at tier 0), not 5,000. The map's plan timeline shows the first payday, which comes a week into the career (`ServiceBudget`, Day 7 00:00), at 3.4 days, between the Moon's sphere of influence and the second burn. The pad can already cut a door, call the tow and select the next burn: Back with R2 sends B (`GamepadKeys`), so section 10 says so, and the controls reference names it `Back + R2`. |
