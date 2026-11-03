# HUD variants: the shared framework

The UI overhaul is nine HUDs: three functional specs by three art directions. The player picks one
in **Settings → Interface → HUD**, or keeps the HUD the game has shipped with so far ("Classic",
the default). This document is the contract between the framework and the nine builds.

The functional specs live beside this file:

| Spec | Document | Package |
|---|---|---|
| Ship stations, diegetic consoles | `documentation/ui-overhaul/functional-stations.md` | `…variants.<style>.stations` |
| By player context (seat, situation, mode) | `documentation/ui-overhaul/functional-context.md` | `…variants.<style>.context` |
| By the player's tasks and decisions | `documentation/ui-overhaul/functional-task.md` | `…variants.<style>.task` |

Styles are `nasapunk`, `vfd` and `cassette`, so the nine packages are
`com.ringsofiron.uihud.variants.<style>.<spec>`, each holding one class listed in
`HudVariants`. Until a combination is built its class extends `StubVariant`: the classic HUD with a
small tag saying which combination is picked.

## Writing a variant

```java
public class VfdTaskHud implements HudVariant {
    private HudVariantHost host;

    @Override public void attach(HudVariantHost host) {
        this.host = host;           // build your actors in host.layer()
    }

    @Override public void update(float delta) {
        HudData d = host.data();    // this frame's snapshot; lay out from it
    }

    @Override public void resize(int width, int height, float uiScale) { }  // rebuild fonts here

    @Override public boolean keyDown(int keycode) { return false; }         // soft keys

    @Override public Set<ClassicPart> keeps() { return EnumSet.noneOf(ClassicPart.class); }
}
```

Life cycle, all on the render thread:

1. `attach` when the variant is picked, or the game starts with it. Everything you add to the stage
   goes in `host.layer()`, a `Group` filling the stage above the classic panels and the world
   markers and below the windows and the map. The host clears it on detach.
2. `update(delta)` once a frame, after the game has updated and the data layer has been refreshed,
   before the stage acts and draws.
3. `resize(width, height, uiScale)` when the window or the UI scale changes. The stage's units are
   already resized; this is where a variant that generates its own fonts rebuilds them.
4. `keyDown` for keys the classic HUD did not use, before the game's own bindings see them. Not
   called while a window or the map has the keys. Return true for a key you used and the game's
   binding for it does nothing that frame (`UiHud.keyUsedByVariant`): Alt with 1 picks your page
   and gives no helm order. The game reads its bindings whatever modifier is held, so take a key
   only when it means yours (with Alt held, say); one you take is gone from the game that frame.
5. `detach` to let go of fonts and textures.

A variant that throws from `attach` leaves the classic HUD up rather than none, and says so in the log.

### What the variant hides

`keeps()` returns the classic pieces that stay up under the variant (`ClassicPart`): the flight
panel, the gauges, the radar, the ship panel, the orbit panel, the missile banner, the threat
overlay, the world markers, the game's status column, the key hint line, the docked banner and the
E prompt. Everything not in the set is hidden for you, each frame. Windows (market, yard, missions,
crew, boarding, the console window and the map) and the world overlays the game draws under the HUD (burn
arrows, fire arcs, picture-in-picture) stay until a variant replaces them in its own PR.

## The common data layer

`host.data()` is one `HudData`, refreshed once a frame before `update`. A variant reads it and never
reaches into the game. Each snapshot is valid for the frame it was read in: hold the objects, not
their contents.

| Field | What it holds |
|---|---|
| `ctx` | `UiContextProvider.UiContext`: presence (helm, weapons, gun, deck, aboard another ship, EVA), condition (green, yellow, red), docked and the station's name, latched, warp rate and rails, next stop and hold reason, combat, missiles inbound, emergency, disabled reason, abandoning, crew outside, hold fire, helmsman, helm order, helm fault, burn being flown, sim seconds |
| `shipStatus` | propellant, structure, power, oxygen, emergency oxygen, worst cabin pressure |
| `captainStatus` | health, state, wound, bleed-out, suit and its air, the room's name, pressure, oxygen and smoke, what is carried |
| `weaponFrame` | one row per mount: name by place ("FWD GUN", "PORT PD 2"), weapon, kind, state (destroyed, refit, shorted, stunned, no power, empty, reloading, charging, ready), controller (player, crew, console, auto, nobody), cooldown and reload, ammunition and magazine, engaging, defending, weapons free, in arc, hit chance on the lock, range, hardpoint and arc, health, tile; plus missiles left, the decoy's countdown, point defense active, hold fire, the target's label |
| `shieldStatus` | raised, lowered for dock, stock, capacity, cloud, scattered, adrift, stowed, trim, the four sectors' density against the mean, emitters fitted, holding and tripped, the hottest one, power stunned |
| `powerFrame` | per circuit: reactors, rated and actual output, store and capacity, systems fed; plus every system that needs power and is not working, with its fault, timer (a short's reclose, a stun), health, room and tile |
| `targetStatus` | the locked target: label, relation, state, range, closing, relative speed, closest approach and its time, bearing, aspect, firing on us, hull, track age |
| `threatFrame` | missiles in flight: side, who launched it, position, path, **time to impact with the missile's closing burn in it**, closest approach, range, closing speed, burning |
| `flightStatus` | the speed against the frame that matters, first that applies: the locked target, the nav target within 5 km, the nearest station within 20 km, else the body orbited (its name and the altitude); closing and lateral against that object, velocity for a marker; heading and spin; delta-v left, home (to the nearest station that would take the ship) and planned; mass and full-thrust acceleration; the stick, main engine and RCS firing, rotation assist and whether it is held, out of propellant, autopilot (`PlayerFlightStatus`) |
| `navReadout` | the nav target: name, kind (station, ship, wreck, body, point), relation, distance and closing, in the bubble and its bearing, the closest approach along the plan, the contract whose waypoint it is, and a station's `StationInfo`; the plan: burns left, **burn n of N (counting burns already flown)**, the next burn's countdown to its centre, to the engine lighting and to the turn onto it, its delta-v; the burn being flown with its delta-v **to go**; for either, the burn time, its direction and the pointing error; the plan's delta-v and a proposal up; and the station docked at. `StationInfo` is the owner, sponsor or not, standing and its word (Hostile, Unwelcome, Neutral, Friendly, Trusted), why it would refuse clearance, and its services (market, yard, shipyard, contracts, hiring, infirmary) (`PlayerNavigation`) |
| `docking` | the selected port: number of how many, size, armed, active, collar torn or jammed; free, lining up, captured or docked; the craft and port it lines up with or is latched to, and that port's size; clearance (none, granted with the seconds it has left, denied and why, no free port of the size, a struck ship that yields), read from the station's ports without asking; range, lateral (+ to port), turn still needed, closing and the safe closing speed; docked, the latch's strain and whether it has settled (`PlayerDockingApproach`) |
| `contactFrame` | every marked craft in the bubble and every missile in flight, nearest first: label, kind (ship, station, missile, derelict, waypoint, crew adrift), relation, state (fit, running, struck, silenced, disabled, wrecked) and the disabled reason, position in bubble metres, range, bearing from the nose (port +), closing, size, locked, nav target, a threat to the ship (`PlayerContacts`) |
| `objectiveFrame` | the contracts held, soonest deadline first: title, kind, giver and station, state (under way, homeward, done, failed), time left and late, where it sends the ship next and whether that is the nav target, progress of how many, pay; and the operation running with its step (`PlayerObjectives`) |
| `stations` | each post aboard (helm, weapons, each gun, life support, reactor), named by place ("HELM", "FWD GUN", "PORT GUN 2"): who works it (you, a crew member by name, one on the way, nobody), whether the console works, its tile; and the crew aboard and how many are free for a post (`PlayerStationBoard`) |
| `keyHints` | the hints the classic bottom line draws from, for where the player is, with the player's keys: every one, most useful first, each action once, then help; or, while placing furniture, the placing line instead. Set by the game screen (`PlayerKeyHints`) |
| `orderState` | the orders that stand and why any can't be given now: the helm's order, who it flies relative to, an approach's distance and the helm's own line, and for each of keys 0 to 5 why the helm would refuse it, in its words; the autopilot (off, on, held), where to and why it holds, and why it can't be engaged; whether the player is at a seat, where those are taken; the crew's battle stations, suits for them and the EVA hold; abandoning ship, with the seconds until the pods go anyway, the crew aboard and in a pod, the pods and their free seats, and the captain's straight-line distance to the nearest pod with a seat free and its tile; why the crew can't be sent to the pods; the seconds left on a first press of abandon ship and of the tow; the tow's fee, standing cost and refusal; paused. Set by the game screen (`PlayerOrders`) |
| `lastHullHit` | the last hit on the player's hull: the deck tile it struck, and seconds since (NaN before any), from `UiHud.flashHullHit` |
| `alerts` | `AlertRegistry`: everything wrong now, as rows |
| `messages` | `MessageFeed`: what the game has told the player lately |
| `roomAt(x, y)`, `roomNames()` | the player's rooms' names (`RoomNames`) |
| `key("FIRE")` | the key bound to an action, for a prompt |
| `promptAction`, `promptKey` | what E does here, as the classic prompt words it after the key ("to put on a pressure suit", "for the Helm panel"), or ""; and the key |
| `dockHint` | the docked banner's text, or "" when not docked |

`targeting`, `orbitInfo`, `interactor` and `physics` are the classic providers, set on `UiHud` as
before and mirrored here.

### Alerts

`AlertRegistry` is the one list of what is wrong: the annunciator, the alert band, the caution panel
and the ticker all read it. The game raises every condition that holds once a frame; one not raised
has cleared. An alert keeps `since` and `acknowledged` while it holds, so it does not flash again
each frame; a worse level clears the acknowledgement.

Each row has an id naming the condition and the instance (`fire:BRIDGE`, `missile`,
`sys:REACTOR:3,5`), a level (advisory, caution, warning), a category (flight, nav, tactical,
engineering, life support, crew, captain, comms, ship), a short code in capitals, one line of text,
a room and tile where it has one, a countdown and whether that countdown is to harm or to recovery,
a count, and the crew working on it where the game can say. `all()` is worst first then oldest,
`of(category, out)` filters, `master()` is the worst level not yet acknowledged (the master lamp),
`acknowledgeAll()` puts it out.

Raised today: missiles inbound (with the burn-aware impact time), propellant against the trip home,
each room's fire, breach, pressure and smoke, the hull, every system that needs power and is not
working, the ship disabled or abandoning, the captain bleeding or low on suit air, crew bleeding
out, crew outside, the helm's fault, a hostile with you locked, the shield's emitters and a shield
down in combat, oxygen, and empty launchers.

### Room names

`RoomNames` (interior-grid) names a room in capitals, at most 10 characters, by the first rule that
applies: the room with the helm is BRIDGE; else the kind of system that fills most of its tiles
(HOLD, REACTOR, ENGINES, TANKS, LIFE SUP, SHIELDS, GUNS, AIRLOCK, PODS, MEDBAY); else its place in
the hull (FWD, MID, AFT, with PORT or STBD off the centreline); and two rooms with the same name are
numbered from the bow. Names are worked out from the layout and kept with it, so they do not change
under the player in a fight. Weapon mounts are named the same way, by the hardpoint's place.

### Time to impact

The closest approach at the present speed runs long while a missile's motor burns: a harpoon from
7.7 km reads 37 s five seconds after launch when it lands in about 17. `ImpactTime` (combat-sys)
solves `t = (−v + √(v² + 2ad)) / a` with `a` the missile's `closingBurn` while its fuel above the
terminal reserve lasts, then a coast at the speed it ends on. `ThreatProvider.MissileTrack` carries
both: `timeToImpact` (burn counted) and `closestApproach` (the old number), with the range and
closing speed they came from.

## The setting

`GameSettings.hudVariant` keeps the id ("classic", or "`<style>.<spec>`"). The shell sets
`HudVariants.setChosen` on start and on apply, as it does `UiScale`; `UiHud` switches on its next
frame. `-Dhud.variant=vfd.task` overrides it for a run, for playtests and screenshots. An unknown id
falls back to the classic HUD.

## Adding a combination

1. Replace your class's body in `com.ringsofiron.uihud.variants.<style>.<spec>`: implement
   `HudVariant` instead of extending `StubVariant`, and return from `keeps()` the classic pieces you
   still want.
2. Needed data that no provider gives: add the interface in `com.ringsofiron.uihud.providers`, the
   implementation in core's `game` or `wiring` package, set it on `HudData` in `GameHud`, and
   document it here so the other eight can use it.
3. Keep a layout test in the shape of `UiScaleTest`: nothing overlaps at 1280x800 or at the largest
   scale, and the centre of the view stays clear.
