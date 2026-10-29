#!/usr/bin/env bash
# The EVA work beyond RCS blocks, in a real window under Xvfb, on the patrol corvette cast off from
# Highport and put 300 m off it:
#  1. A salvaged railgun in the hold, mounted on the nose from the field refit (Y): the engineer
#     lifts its crate out of the hold, carries it out and along the hull and sets to work. Called in
#     from the crew window (I), she leaves it clipped on there; let out again, she goes straight
#     back to it, mounts it, and brings the old coilgun in to the hold.
#  2. The crew held in from the crew window: a jammed docking collar waits till she is sent out to
#     free it; then they are let out again.
#  3. The collar jammed again and Ada out at it with her jetpack spent (from the console): a full
#     burn parts her line and leaves her adrift astern, in the way of the main engines' exhaust; a
#     short burn catches her in it. The ship comes back beside her (from the console), and a
#     shipmate goes out on a line, takes hold of her and brings her in. Ada doesn't go back out to
#     the collar on her empty pack.
# Screenshots from the deck view and from outside. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/eva-jobs.sh [out-dir]     (default build/playtest-screens-eva-jobs)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-eva-jobs}
SIZE=${SIZE:-1920x1080}
. tools/playtest/lib.sh
G='com.ringsofiron.interiorgrid.ShipGridComponent'
H='com.ringsofiron.interiorgrid.CargoHoldComponent'
E='com.ringsofiron.aicrew.EvaSystem'
F='com.hawkwing.RingsOfIron.game.FieldRefitComponent'
P='com.ringsofiron.stationsdocking.DockingPortsComponent'
B='com.ringsofiron.physicsexterior.components.PhysicsBodyComponent'
M='com.ringsofiron.physicsexterior.PhysicsManager'
ADA="world.crew.crewEntities().find { world.crew.crew(it).name == 'Ada' }"
# Prints a dev command's answer: what follows the ".join('; '): " that ends its echo in the log
answer() { cmd dev "$1"; pause 0.4; grep -F "PLAYTEST dev $2" "$OUT/game.log" | tail -1 | sed "s/.*join('; '): //"; }
# What the player's crew on EVA are doing, or "nobody"
eva() { answer "def out = world.spacewalks.onEva(); out.isEmpty() ? 'nobody' : out.collect { world.crew.crew(it).name + ' ' + $E.of(it).phase + ' ' + world.crew.crew(it).activity + ' line ' + String.format('%.1f', $E.of(it).lineOut) + ' m' + ($E.of(it).tethered ? '' : ' (parted)') + (world.crew.crew(it).crate == null ? '' : ' with ' + world.crew.crew(it).crate.name) }.join('; ')" "def out"; }
# What each of the ship's crew is doing
doing() { answer "world.crew.crewEntities().findAll { world.crew.position(it).ship == player }.collect { world.crew.crew(it).name + ' ' + world.crew.crew(it).activity + (world.crew.crew(it).crate == null ? '' : ' with ' + world.crew.crew(it).crate.name) }.join('; ')" "world.crew.crewEntities"; }
# Waits up to $2 polls (2 s apart) for $1 (eva, doing, status or refit) to answer something matching $3
until_() { for _ in $(seq 1 "$2"); do r=$($1); echo "  $r"; echo "$r" | grep -q "$3" && return 0; pause 1.6; done; echo "never saw: $3"; return 1; }
# As until_, polling as fast as the console answers: for a step that lasts only a second or two
until_fast() { for _ in $(seq 1 "$2"); do r=$($1); echo "  $r"; echo "$r" | grep -q "$3" && return 0; done; echo "never saw: $3"; return 1; }
notices() { answer "world.notices.history().collect { it.text }.take(8).join('; ')" "world.notices"; }
# The status panel's EVA line
status() { answer "[world.spacewalks.statusLine()].join('; ')" "[world.spacewalks"; }
# The crew's own refit orders, or "none"
refit() { answer "def r = player.getComponent($F); r == null || r.orders.isEmpty() ? 'none' : r.orders.collect { 'hardpoint ' + it.hardpoint + ' ' + it.stage + ', ' + Math.round(it.worked) + ' s in' }.join('; ')" "def r = player"; }
health() { answer "[Math.round(world.crew.crew($ADA).health)].join('; ')" "[Math.round"; }
# Physics warp: back to 1x, then $1 levels up (2x, 3x, 5x, 10x)
warp() { for _ in 1 2 3 4 5; do key comma; done; for _ in $(seq 1 "$1"); do key period; done; }
wheel() { xdotool mousemove --window "$WIN" 960 540; for _ in $(seq 1 "$2"); do xdotool click "$1"; pause 0.1; done; }
# Up from the helm to the deck view, zoomed in close; and back to the helm, the view outside as it was.
# Standing on deck the captain is jostled off the helm by crew passing it, so is put back where they
# sat (SEAT, read at the start) before sitting again
VIEW=helm
deck() { [ "$VIEW" = deck ] && return; key v; wheel 4 5; pause 1.5; VIEW=deck; }
helm() {
  [ "$VIEW" = helm ] && return
  cmd dev "def a = world.avatar.getAvatar().getComponent(com.ringsofiron.aicrew.GridPositionComponent); a.path.clear(); a.x = ${SEAT% *}f; a.y = ${SEAT#* }f; 'back beside the helm'"
  pause 0.5; key v; pause 1.5; VIEW=helm
}
# The crew window opened afresh, in the middle of the screen; and closed. With work outside for Ada,
# the button on her row (Send out, Call in, Let out) is at (1251, 502)
crew() { key i; pause 1.5; }
uncrew() { key i; pause 0.5; }
ADA_EVA="1251 502"

# A new game, docked at Highport and seated at the helm
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 40
SEAT=$(answer "def p = world.avatar.getAvatar().getComponent(com.ringsofiron.aicrew.GridPositionComponent); [p.x + ' ' + p.y].join('; ')" "def p = world.avatar")
echo "seated at the helm at $SEAT"
# Undocked, 300 m off Highport, the view zoomed in close on the ship
key u; pause 4
wheel 4 14
cmd target Highport
cmd away 300; pause 2

# 1. A railgun stripped off a raider, in its crate in the hold
cmd dev "player.getComponent($H).load('part:railgun_mk1', 1, 1.0d, 250.0d)"
deck
# Away from a yard, Y opens the field refit: Mount on EVA on the nose's row
key y; pause 1.5; shot j-01-field-refit
click 1091 495; pause 1; refit; shot j-02-mount-ordered
key y; pause 0.3
# The hold is beside the engineer's post: she has the crate out of it in a few seconds, and suits up
until_fast doing 20 "EVA suit"
pause 3; doing; shot j-03-suiting-up-with-the-crate
warp 1
until_ doing 40 "to the airlock\|cycling out"
warp 0; doing; shot j-04-carrying-it-to-the-airlock
warp 1
until_ eva 40 "ON_HULL"
warp 0; pause 1.5; eva; shot j-05-out-with-the-crate-deck
helm; shot j-06-out-with-the-crate-outside
warp 1
until_ eva 40 "mounting"
warp 0; pause 1; eva; refit; shot j-07-mounting-outside
deck; shot j-08-mounting-deck
key y; pause 1.5; shot j-09-field-refit-progress; key y; pause 1
# Called in from the crew window: she leaves the crate clipped on at the nose and comes in
crew; shot j-10-crew-call-in
click $ADA_EVA; pause 1; shot j-11-called-in
uncrew
warp 1
until_ eva 60 "nobody"
warp 0; pause 1; doing; refit; shot j-12-in-the-crate-left-at-the-nose
# Let out again: straight out to the nose, nothing to fetch
crew; shot j-13-crew-let-out
click $ADA_EVA; pause 1; uncrew
warp 1
until_ eva 60 "ON_HULL"
until_ eva 40 "mounting"
warp 0; pause 1; eva; refit; shot j-14-back-at-it
until_ eva 60 "the old"
pause 1; eva; notices; shot j-15-bringing-the-old-one-in-deck
helm; shot j-16-bringing-the-old-one-in-outside
deck
warp 1
until_ eva 60 "cycling back in"
warp 0
until_fast doing 30 "stowing the old\|to the hold"
shot j-17-stowing-the-old-one
until_ refit 20 "none"
key y; pause 1.5; shot j-18-field-refit-after; key y; pause 1

# 2. Everyone held in; then a hit jams the docking collar
crew; click 1034 455; pause 1; shot j-19-held-in
uncrew
cmd dev "player.getComponent($P).ports[0].jammed = true; 'jammed'"
warp 2; pause 8; warp 0
doing; eva; notices; shot j-20-collar-jammed-nobody-out
crew; shot j-21-crew-send-out
click $ADA_EVA; pause 1; shot j-22-sent-out
uncrew
warp 1
until_ eva 60 "ON_HULL"
until_ eva 30 "freeing"
warp 0; pause 1; eva; shot j-23-freeing-the-collar-deck
helm; shot j-24-freeing-the-collar-outside
warp 2
until_ eva 60 "nobody"
warp 0; notices; deck; shot j-25-collar-freed
# Let out again: the crew go out for work as it comes
crew; click 976 452; pause 1; shot j-26-let-out-again; uncrew

# 3. The collar jammed again: Ada goes out to it
helm
cmd dev "player.getComponent($P).ports[0].jammed = true; 'jammed'"
warp 2
until_ eva 90 "ON_HULL"
until_ eva 60 "freeing"
warp 0
# Her jetpack spent
cmd dev "world.crew.crew($ADA).suitJet = 0f; 'pack spent'"
pause 1; eva; status; shot j-27-at-the-collar-pack-spent
# Zoomed out, a full burn ahead throws her off the hull, and her line parts after a second and a
# quarter. With nothing in her pack she falls astern, no way back alone, through where the main
# engines' exhaust would burn her: the status panel warns of her before a burn
wheel 5 8; pause 1
hold w 2
until_fast status 15 "in the way of the main engines"
shot j-28-in-the-way-of-the-exhaust
# A short burn all the same: the exhaust catches her
health; hold w 0.5
pause 0.3; notices; status; health; shot j-29-caught-in-the-exhaust
# Further out, the HUD marks her
wheel 5 6; pause 1; status; shot j-30-adrift-marker
# The ship comes back beside her: 20 m off the docking port, her speed matched
cmd dev "def a = $ADA; def e = $E.of(a); def b = player.getComponent($B).body; def g = player.getComponent($G); def th = b.getAngle(); def lx = g.gridOriginLocal.x + 8.5f; def ly = g.gridOriginLocal.y + 26f; $M.setPosition(b, (e.wx - (Math.cos(th) * lx - Math.sin(th) * ly)) as float, (e.wy - (Math.sin(th) * lx + Math.cos(th) * ly)) as float, th as float); $M.setVelocity(b, e.vx, e.vy); b.setAngularVelocity(0f); 'beside her'"
wheel 4 7; pause 2; status; notices; shot j-31-back-for-her
# A shipmate suits up, goes out on a line and jets out to her from the hull beside the port
deck
until_ doing 60 "EVA suit"
pause 0.5; doing; shot j-32-suiting-up-to-fetch-her
helm
warp 1
until_ eva 90 "cycling out\|jetting out"
warp 0
until_fast eva 60 "jetting out to Ada"
pause 0.5; eva; shot j-33-jetting-out-to-her
until_fast eva 60 "bringing Ada"
pause 0.5; eva; status; notices; shot j-34-taken-in-tow
pause 3; eva; shot j-35-bringing-her-in
# Both back in: Ada hangs up her spent suit to fill, rather than go back out to the collar on it
until_ eva 60 "nobody"
deck; pause 4; doing; notices; shot j-36-both-back-in

pause 1
grep 'PLAYTEST \(dev\|target\)' "$OUT/game.log" | sed "s/.*join('; '): /  /" | tail -80
finish
