# EVA: suits, airlocks and work outside the hull

Crew leave the ship physically to do work that can only be done from outside: today, framing back
up the wrecked hull tile under an RCS block so the block fires again. Nothing about it is
abstracted. The suits are objects that hang in lockers, the docking ports are airlocks that pump
down and fill again, the crew member crawls round the outside of the hull hand over hand or hops
on a jetpack with a limited tank, and the tether is a rope that drapes, wraps round corners, goes
taut and can snap. What the ship does while they are out (a burn, a turn, a collision) acts on
them through their grip and their line.

| Where | What |
|---|---|
| `interior-grid` `crew/Suit`, `SuitLockers`, `SuitOutfit` | Suits as objects; lockers; fitting a ship out with them |
| `interior-grid` `Airlock`, `DockingPortLayout` | A docking port's chamber, inner hatch, outer door and their cycle |
| `interior-grid` `HullSurface` | The ring of empty tiles hugging the hull that crew crawl along |
| `interior-grid` `RcsThruster.fieldRepair` | What the work does: the tile back to `FIELD_REPAIR` |
| `ai-crew` `EvaSystem`, `EvaComponent`, `EvaEvent` | The simulation outside: grip, crawl, jetpack, rope, reel, falls, airlocks |
| `ai-crew` `btree/EvaTasks`, `btree/SuitErrand` | The behavior: who goes, which port, suiting up, the work, coming back |
| `core` `game/Spacewalks` | The game's side: floating origin, rails warp guard, notices, morale |
| `core` `render/Spacewalkers`, `DeckRenderer`, `art/DeckArt` | Drawing it: suits, crawl and float poses, tether, jetpack puffs, lockers |
| `tools/art/suit_svgs.py`, `furnishing_svgs.py` | The art |

## Suits are objects

A suit is a `Suit` (kind, seconds of air, jetpack delta-v). One not being worn hangs in a locker
(`Furnishing.suits`), so a crew member who wants one walks to a locker that has one, stands at its
front and takes the suit's time to get into it; taking it off, they walk to a locker with room
and hang it up. A locker tops up the suits in it from the ship's oxygen and the jetpacks from its
propellant (`SuitLockerSystem`).

| | Pressure suit (IVA) | EVA suit |
|---|---|---|
| Locker | `suit_locker`, 4 bays, 1 x 0.5 tiles | `eva_locker`, 2 bays, 1.25 x 0.6 tiles |
| Air | 600 s | 2,400 s (40 min) |
| Getting in | 5 s | 40 s (`EVA_DON`) |
| Walking, working | normal | 60% speed, 65% work pace inside (`EVA_SPEED`, `EVA_WORK`) |
| Jetpack | none | 200 m/s of delta-v (`JET_DV`) |
| Outside | no | yes |

Ships are fitted out when they are built, and older saves when they load (`SuitOutfit`): a
pressure suit for each crew member and a spare, in lockers beside the helm and weapons posts and
spread through the rooms; and an EVA locker with two EVA suits just inside each docking port.
Crew under threat of losing the air reach for the nearest pressure suit, and take an EVA suit only
when no pressure suit can be had and the air still holds long enough to get into one.

Drawn: suited crew wear the suit's art (the clear bubble helmet and air pack of the pressure suit;
the white EVA suit with its gold visor and jetpack), standing, down and dead; a locker shows each
suit hung in it, so an empty bay is an empty bay.

## Docking ports are the airlocks

A docking port's opening (`DockingPortLayout`) is its chamber: the outer door, a sealed bulkhead
facing space, and now an inner hatch across the opening's inner edge, open at rest. Saves from
before ports had hatches get them on load. A port docked to another craft is not an airlock.

`Airlock` runs one port's cycle:

```
IN --(someone going out)--> SEALING_HATCH -> PUMPING -> OPENING_DOOR -> OUT
OUT --(someone coming in)--> SEALING_DOOR -> FILLING -> OPENING_HATCH -> IN
```

Doors take 2 s. Pumping draws the chamber's air back into the ship (time constant 4 s, done below
3% of a full atmosphere or after 30 s); filling bleeds it back in (1.5 s, done at 95% or 20 s). The
cabin keeps its air through a cycle. Anyone else in the chamber holds the hatch open; the outer
door stays open while anyone is out on the port's reel, so a crew member's line is never cut by it.
An airlock a load caught part way through a cycle brings itself back to rest. The pump runs on the
port's power, which reaches it through the opening; the hatch's frame carries it, so shutting the
hatch never cuts it (`PowerRoutingSystem`). A port with no power opens its door on the chamber's
air, which vents.

## Who goes out, and when

`ShipHazardScanSystem` posts an `RcsRepairTask` for every RCS block whose hull tile is wrecked
(integrity 0) but not crushed flat (that is yard work), and only off battle stations; going to
battle stations takes the open ones off the board. It is engineering work.

An engineer takes the job only when an EVA suit with at least half its air is aboard and a port
can cycle (`EvaTasks.suitReady`). They hang up any pressure suit they wear, get into an EVA suit,
and pick the port to go out by (`EvaTasks.plan`): one that can cycle, whose door opens onto the
hull's ring, and from which the hull runs round to the block within the line's reach (40 m less
2 m spare), choosing by the walk inside plus the crawl outside (a tile of hull costs as much as 3.5
tiles of deck). They queue for a busy airlock at its hatch.

Outside, `Spacewalk` holds the task: along the hull to the tile beside the block, the work there
(`CrewDef.rcsRepairTime`, 40 s, at the EVA suit's pace), then on to another wrecked block within
reach, or back in. They come back in:

- as soon as battle stations sound;
- once their line has parted: nobody works untethered, so the job waits for a trip out on a new one;
- once their air is down to what the way back takes, the airlock cycle and 300 s more;
- when the job is gone (done by someone else, or no longer needed), or out of reach;
- after a load (the task is not saved; they finish the way back).

Back in, they hang the suit up in a locker with room.

## Out on the hull

`HullSurface` is the ring of empty tiles that touch the hull. A crew member clinging to the hull
(`ON_HULL`) is at a hug point on that ring and crawls from tile to tile at 0.4 m/s, or, when the
straight way is clear and saves at least 4 tiles of crawling and the jetpack has fuel to spare
(40 m/s kept back), hops at 2 m/s.

**Holding on.** Each tick, the force that keeps them moving with the hull point they hold is their
mass (180 kg with suit and pack) times that point's acceleration: the ship's thrust, its turn and
spin, a collision. Their line's pull is taken off it. The hull itself gives the part along its
outward normal by pushing on them: a burn that drives the hull into them only presses them to it.
What is left, a pull off the hull or a drag along it, their grip has to give: 600 N, about a third
of a g. The pull past it adds up as strain (newton-seconds), and once it reaches 15% of their grip
over a second (90 N·s) the hold breaks and they come off with the hull point's velocity. So the
corvette's RCS strafe away from them (2.5 m/s², 450 N) they hold through; its burn astern (5 m/s²)
or its main engine (12 m/s²) away from them throws them off; a collision's jolt can too.

## Off the hull

Off it (`FREE`), they are a point mass in the physics world's frame, moved by their line, their
jetpack and the hull they hit. The jetpack (90 N) flies them to where they are going or, after a
fall, back to the nearest point of the hull, and they catch hold once within 0.3 m and slower than
0.8 m/s relative to it (not in the half second after letting go), so long as the hull isn't
dragging past them harder than their grip alone could hold: through a hard burn they hang on the
line until it eases, rather than catching hold and being thrown off again. (Their line doesn't
count toward the hold: it pulls one way and then another as they swing.) Caught hold short of the
ring tile's hug point, they pull themselves over to it at crawling speed. With no fuel left they
pull themselves in along the line, 0.3 m/s. Hitting the hull stops their speed into it and some
along it; faster than 3 m/s it wounds them (12 hit points per m/s over).

## The tether

The line runs from a reel beside the airlock's outer door, 40 m at most. It is a rope of 20
segments (Verlet integration, length constraints only, 16 iterations a tick sweeping both ways,
each point pushed out of the hull): it floats slack, drapes, wraps round the hull's corners and
goes taut.

- **The reel** takes up slack on its spring at 2 m/s, so a loose line doesn't hang about in loops,
  and pays the line out as it is drawn, but locks like a seatbelt when it is drawn out faster than
  1.5 m/s, and frees again once the line has gone slack.
- **The line** stretches when it is drawn past what is paid out, locked or run out, and pulls
  back: 4,000 N per meter of stretch plus 1,200 N·s/m of damping on how fast it stretches (the
  stretch smoothed over 0.05 s, since the rope's shape jitters as it slides over the hull). It
  pulls the crew member and, equally and opposite, the ship at the reel.
- **It parts** under a pull it can't bear for long. It holds 1,750 N, a little under a g on a crew
  member, for as long as it is asked to; past that it gives, and once the pull past 1,750 N adds up
  to 100 N·s it parts, as a hold on the hull gives way past the grip. The corvette's full burn
  (12 m/s²) parts the line of anyone it throws off after about a second and a half. A burn astern
  (5 m/s²), however long, swings them round the hull's corner with the line's pull peaking near
  1,900 N, but each peak passes too quickly to part it. It parts at their clip: the loose line
  whips back and the reel's spring winds it in. Untethered and adrift, they jet
  back to the hull while the pack has fuel, and crawl back to the airlock.
- **Left behind**: adrift with no line more than 500 m from their ship, a crew member is gone:
  a `LEFT_BEHIND` event, and taken out of the world.
- **Winched in**: one who can't climb (out cold, or dead) is wound in by the reel at 0.5 m/s while
  their weight on the line is under the motor's 400 N, and pulled into the airlock at the door.

A burn of 4 m/s² away from a crew member on the side of the hull throws them off: the reel locks, and they
swing on the line like a pendulum round the hull's corner, the line's pull peaking at the bottom of
each swing above their 720 N weight in the burn. When the burn ends they jet back to the hull.

## In the game

`Spacewalks` (core) hooks the crew module's EVA to the game:

- The exterior world's floating origin shifts carry crew off the hull along with it, rope and all.
  A ship's body made again (the bubble rebuilt) or new (come into the bubble) re-anchors them from
  where they are relative to the ship.
- **Rails warp waits** while any of the player's crew are on an EVA, in the airlock or outside: "Crew
  are out on EVA: Ada is outside". Other ships' crew outside at a rails jump are taken to be back
  in by its end (their job put down), or left behind if their port is gone.
- **Notices**: out on EVA, back in, thrown off the hull, caught hold again, the tether parted, left
  behind. Someone left behind costs the rest of the crew morale as a death does.
- **The status panel** says who is out and where while anyone is ("EVA: Ada on the hull, 16 m of
  line out. A hard burn can throw them off the hull"; off it, "A hard burn can part the line"), so
  the helm knows before it burns.
- Saved: the phase, where they are and how fast they move relative to the ship, the line paid out
  and the port. The world-frame state and the rope's shape are rebuilt after a load.

Drawn, in the deck view and from outside: the figure in the crawl's two alternating frames, turned
along the hull, or floating free, seen from behind with the jetpack on their back; the tether as a
yellow line with a dark edge from the reel, curved through the rope's points; the jetpack's puffs
blown against its push; and in the deck view, where the light map leaves the outside of the hull
dark, the suit's lamps lighting the plating round them.

`tools/playtest/eva.sh` plays it through in a real window and takes the screenshots.

## Boarding across on EVA

A boarding no longer needs a dock (`Boarding`, core; `EvaTasks.Departure`, `EvaSystem.cross`, ai-crew).
With the player's ship latched to nothing, K's window offers the ship standing off within
`EvaSystem.CROSS_RANGE` (300 m, hull to hull near enough) and drifting less than `CROSS_DRIFT`
(2 m/s) from it: the navigation target if it is such a ship, else the nearest. Send boarders on EVA
works as a boarding across a dock does (a beaten ship, standing cost, the sweep room by room, taken
once no one aboard fights), but the party goes over on their jetpacks:

- **Who goes:** as many of the party as there are EVA suits aboard with half their air or more, worn
  or hanging in lockers. The corvette's two EVA suits send two.
- **Out:** into an EVA suit (out of a pressure suit first), to the airlock whose door is the shortest
  walk and crawl from the side of the hull facing the other ship, and out with no line clipped on:
  40 m of line doesn't reach. Over the hull to the ring tile nearest the other ship, and off it.
- **Across:** from the push off they count as aboard the ship they make for (`CROSSING`): its
  frame is theirs and its hull the one they catch hold of. They jet to the nearest point of it at up
  to `CROSS_SPEED` (3 m/s), catch hold, and crawl (or hop) to the port they go in by: the one not
  latched to anything whose door is nearest their own ship (`EvaTasks.entryPort`).
- **The airlock door, from outside** (`BoardTask.Job.breach`): unpowered, it is pried (8 s);
  powered, hacked (45 s), and the airlock is then theirs: it cycles them in and the cabin keeps its
  air. Storming, a powered door is cut instead (20 s). A door pried or cut stands open on a chamber
  whose hatch is open at rest, so the cabin behind it vents, and they go straight in (`ABOARD`).
  Two boarders at one door work it together. Each is a hostile act, costed as a door forced across
  a dock (`FactionStanding.onForcedEntry`).
- **Back:** once the ship is taken (or the boarding called off), the party, idle aboard a ship with
  no dock to their own for 3 s, go back the same way: out of its airlock, across, and in through
  their own, cycled for them as from any EVA.
- **Called off** if the two ships drift more than `LOST_RANGE` (500 m) apart. While any of the
  player's crew are aboard a ship their own isn't docked to, rails warp waits ("Crew are across on
  another ship").

## Storming: forcing a way in

Force a way in, in K's window, boards a ship whether or not it is beaten, latched to it or standing
off on EVA (`Boarding.storm`). The boarders get no free run of its doors: each shut door in their
way is forced (`CrewService.DoorRights.forcing`, `CrewDoors.Forcing`), pried in 8 s while the ship
has no reactor running, cut in 20 s while it has (left at 0.4 integrity), and left open behind them.
The docked port's door is forced first as the existing order does it, cut if it is powered. Any
boarder, storming or not, forces an airlock door or hatch held shut for its cycle rather than wait
on it. While the storm lasts, the two crews fight whatever the player's standing with the other
ship's owners (`Boarding.storming`); it is taken as any boarding is, once no one aboard still
fights for it.

## Screenshots

From `tools/playtest/eva.sh` on the patrol corvette, with the hull tile under its starboard bow
RCS block wrecked.

The engineer at the EVA locker by the bridge, getting into one of its two suits:

![Suiting up](screens/suiting-up.png)

In the docking port's chamber with the inner hatch shut behind them, while the pump draws the air
back into the ship:

![The airlock](screens/airlock.png)

Out on the hull in the deck view, the line running back to the reel by the door; one suit is left
in the locker:

![On the hull](screens/on-the-hull-deck.png)

From outside, at work at the starboard bow block, the line round the bow:

![At the block](screens/at-the-block.png)

A burn astern, the bow blocks firing, throws them off ahead of the bow; they hang on the line round
its corner and jet back:

![Thrown off](screens/thrown-off.png)

A second after the burn, swung up over the bow on the line, which holds; the status panel warns the
helm:

![Swinging round the bow](screens/swinging-round-the-bow.png)

A full burn ahead parts the line at their clip and the reel winds it in, while they fall astern:

![The line parted](screens/line-parted.png)

Well astern with no line, jetting after the ship. They catch hold, go in, come out again on a new
line and finish the block:

![Adrift](screens/adrift.png)

## Judgment calls

- **The docking ports are the airlocks.** The chassis data has an `airlock` auxiliary part, but no
  ship uses it and it has no layout of its own; every ship has a docking port, and a port's opening
  is already a chamber between the deck and space. Adding the inner hatch made it an airlock.
- **Tiles are 1 m**, so the corvette's 18 x 6 hull is 48 m round: the 40 m line reaches most of
  it from the one port, and the crew pick the shortest way round.
- **The hull they crawl on is the deck plan's outline**, the one the RCS blocks are bolted to, not
  the hull art's. Seen from outside, the corvette's nose stands proud of it, so a crawl round the
  bow crosses the nose's plating, as the bow's RCS blocks already sit on it; and at the stern the
  outline runs about a meter behind the plating between the engine bells, so crew holding on
  there look to be hanging off it.
- **Grip and line numbers.** The grip is a suited human's: a third of a g to hold on with. The
  line is weaker than a real one, so that a tether parting is something the ship the player flies
  can do: the corvette's full burn parts it, and the swing from a burn astern doesn't. Two
  playtests set it. With a line that simply snapped at 2 kN, the hull's rub on the crew member
  took enough of a full burn that the line held. At 1.6 kN, a 3 s burn astern swung one round the
  bow hard enough to part it. Measuring the line through each manoeuvre showed a full burn's pull
  stays high while a swing's only peaks, hence a line that bears its rated pull indefinitely and
  parts on how long it is pulled past it.
- **The knocked-out are winched in** rather than left: a reel motor is the obvious safety feature,
  and the alternative is a body on a line until someone else suits up.
- **Not at battle stations**, and called back in when they sound, as the original ask said.
- **The airlock chamber is lit from the deck it opens off.** With the hatch it became a room of its
  own with no wall to hang a light on; the stock chassis keep their lights.

## Tests

- `AirlockTest` (interior-grid): the cycle both ways, the cabin's air, a crowded chamber, the door
  held open on a line, a load mid-cycle, the port keeping its power with the hatch shut.
- `EvaRepairTest` (ai-crew): the whole job on a ship standing still, called back by a fight, never
  at battle stations, crewmen leave it to engineers, no suit aboard, a port that can't cycle, a
  crushed tile, one knocked out on the hull winched in.
- `EvaPhysicsTest` (ai-crew): the ship as a Box2D body: a gentle burn held through, a hard one into
  them only pressing them on, one past their grip throwing them off to swing on the line and jet
  back, one along the hull too hard to catch hold through (once thrown off, they stay off on the
  line until it ends), a full one along the hull parting the line and the reel winding the loose
  line in, a full burn snapping it and leaving them behind, a short one parting it so they come in
  and go out again on a new line, a floating-origin shift.
- `EvaBoardingTest` (ai-crew): a marine across on EVA to a craft standing off, round its hull,
  its airlock door forced open (the cabin vents) and aboard, then home again; an airlock hacked
  cycling them in with the cabin's air kept; a craft out of range not crossed to.
- `CrewDoorTest` (ai-crew): boarders who force their way work at a door for its time and leave it open.
- `BoardingEvaTest` (core): the surrendered raider standing off 40 m boarded on EVA, taken, and the
  party back aboard with rails warp waiting meanwhile; storming a latched raider that can still fight.
- `SpacewalksTest` (core): the patrol corvette's engineer mending a block with rails warp waiting,
  the notices and the status line, the corvette's full burn parting the line of one at its bow
  block while the swing from a long burn astern doesn't, and a save made while they are outside
  loading with them still out there.
- `ArtNamesTest`: every suited, crawling and floating figure and every furnishing has its art.

## Not yet

- Crew left behind are gone at once; they could stay in the world adrift until their air ran
  out, for the ship to come back for them.
- Only RCS blocks are EVA work. Hull patches from outside, a jammed docking collar, mounting
  external parts are natural next jobs.
- The player can't order an EVA, or call one back, by hand (but for a boarding).
- Boarders over on EVA who are knocked out aboard the other ship are carried home when the party
  stands down, as across a dock, rather than towed across.
- A ship with no airlock left to go out by (its doors all blown) strands boarders aboard it until a
  dock is made.
- Only the player boards on EVA; pirates still board only by latching on.
