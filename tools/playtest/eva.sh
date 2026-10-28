#!/usr/bin/env bash
# EVA in a real window under Xvfb: a new game, cast off from Highport and put 300 m off it, and the
# hull tile under the RCS quad at the corvette's starboard bow wrecked. An engineer gets into an EVA
# suit at the locker by the docking port, cycles out and crawls round the bow to the block, and
# starts framing it back up. A burn astern throws them off the hull to swing on their tether round
# the bow, and they jet back to it; then a full burn ahead parts the line, and they jet back to the
# ship untethered and come in. Screenshots from the deck view and from outside. Needs Xvfb, xdotool
# and ImageMagick's import.
#   tools/playtest/eva.sh [out-dir]     (default build/playtest-screens-eva)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-eva}
SIZE=${SIZE:-1920x1080}
. tools/playtest/lib.sh
G='com.ringsofiron.interiorgrid.ShipGridComponent'
E='com.ringsofiron.aicrew.EvaSystem'
# Prints a dev command's answer: what follows the ".join('; '): " that ends its echo in the log
answer() { cmd dev "$1"; pause 0.4; grep -F "PLAYTEST dev $2" "$OUT/game.log" | tail -1 | sed "s/.*join('; '): //"; }
# What the player's crew on EVA are doing, or "nobody"
eva() { answer "def out = world.spacewalks.onEva(); out.isEmpty() ? 'nobody' : out.collect { world.crew.crew(it).name + ' ' + $E.of(it).phase + ' ' + world.crew.crew(it).activity + ' line ' + String.format('%.1f', $E.of(it).lineOut) + ' m' + ($E.of(it).tethered ? '' : ' (parted)') }.join('; ')" "def out"; }
# What each of the ship's crew is doing
doing() { answer "world.crew.crewEntities().findAll { world.crew.position(it).ship == player }.collect { world.crew.crew(it).name + ' ' + world.crew.crew(it).activity }.join('; ')" "world.crew.crewEntities"; }
# Waits up to $2 polls (2 s apart) for $1 (eva or doing) to answer something matching $3
until_() { for _ in $(seq 1 "$2"); do r=$($1); echo "  $r"; echo "$r" | grep -q "$3" && return 0; pause 1.6; done; echo "never saw: $3"; return 1; }
notices() { answer "world.notices.history().collect { it.text }.take(8).join('; ')" "world.notices"; }
# Physics warp: back to 1x, then $1 levels up (2x, 3x, 5x, 10x)
warp() { for _ in 1 2 3 4 5; do key comma; done; for _ in $(seq 1 "$1"); do key period; done; }
wheel() { xdotool mousemove --window "$WIN" 960 540; for _ in $(seq 1 "$2"); do xdotool click "$1"; pause 0.1; done; }
# Up from the helm to the deck view, zoomed in close; and back to the helm, the view outside as it was
deck() { key v; wheel 4 5; pause 1.5; }
helm() { key v; pause 1.5; }

# A new game, docked at Highport and seated at the helm
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 40
# Undocked, 300 m off Highport, the view zoomed in close on the ship
key u; pause 4
wheel 4 14
cmd target Highport
cmd away 300; pause 2
shot e-01-cast-off

# The starboard bow quad: its hull tile wrecked
cmd dev "player.getComponent($G).getTile(16, 0).setIntegrity(0f); player.getComponent($G).gridDirty = true"
pause 1
deck; shot e-02-deck-wrecked
# The engineer suits up at the EVA locker by the port, and cycles out
warp 3
until_ doing 90 "getting into an EVA suit"
warp 0; pause 1; shot e-03-suiting-up
warp 1
until_ eva 60 "LEAVING"
warp 0; pause 1; eva; shot e-04-airlock
warp 1
until_ eva 40 "ON_HULL"
warp 0; pause 2; shot e-05-out-deck
helm; shot e-06-out-outside
# Round the bow to the block
warp 1; pause 6; warp 0; eva; shot e-07-crawling-outside
deck; shot e-08-crawling-deck
warp 2
until_ eva 60 "repairing"
warp 0; pause 1; shot e-09-at-the-block-deck
helm; shot e-10-at-the-block-outside

# A burn astern, 5 m/s2: thrown off the bow, swinging round it on the line
xdotool keydown s; pause 1; shot e-11-thrown; pause 0.6; shot e-12-swinging; xdotool keyup s
pause 0.5; eva; shot e-13-swinging-after
deck; shot e-14-swinging-deck
until_ eva 40 "ON_HULL"
eva; notices; shot e-15-back-on-the-hull-deck
helm; shot e-16-back-on-the-hull-outside
until_ eva 60 "repairing"

# A full burn ahead, 12 m/s2: the line parts and they drift aft
xdotool keydown w; pause 2; xdotool keyup w
pause 0.3; eva; shot e-17-line-parted
wheel 5 6; pause 2; eva; shot e-18-adrift
pause 4; eva; shot e-19-jetting-back
until_ eva 90 "ON_HULL"
wheel 4 6; pause 1; eva; notices; shot e-20-caught-hold
# Back in, untethered, by the airlock
warp 2
until_ eva 90 "nobody"
warp 0
deck; doing; notices; shot e-21-back-in

pause 1
grep 'PLAYTEST \(dev\|target\)' "$OUT/game.log" | sed "s/.*join('; '): /  /" | tail -60
finish
