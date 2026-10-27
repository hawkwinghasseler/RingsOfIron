#!/usr/bin/env bash
# Hits on the player's hull seen from the deck view, in a real window under Xvfb: a new game, the
# ship taken out from Highport and seen from its deck, the world stopped, then each kind of round fired at the hull and
# run on a few frames at a time, a screenshot at each. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/deck-hits.sh [out-dir]   (default build/playtest-screens-deck-hits)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-deck-hits}
. tools/playtest/lib.sh

# A new game. Building the world can take a minute in software GL
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause ${START_WAIT:-60}
# Undocked and out where no other craft is in the line of fire, then on deck
key u; pause 5; cmd away 500; pause 3
key v; pause 2; shot d-00-deck
cmd dev "pause()"
# fire <name> <weapon> <range> <bearing>: shoots the player's ship and steps it on a frame at a time
fire() {
  cmd dev "shoot '$2', player, $3, $4"; pause 0.5
  [ -n "${LEAD:-}" ] && { cmd dev "step($LEAD)"; pause 2; }
  for i in 1 2 3 4 5 6 7 8; do cmd dev "step(${STEP:-0.04})"; pause 0.6; shot "$1-$i"; done
  cmd dev "step(3)"; pause 3.5
}
fire d-01-autocannon autocannon_mk1 60 90
fire d-02-railgun railgun_mk1 60 270
fire d-03-chaingun chaingun_mk2 60 120
STEP=0.08 fire d-04-laser laser_mk1 60 180
STEP=0.08 fire d-05-ion ion_mk1 60 90
STEP=0.05 fire d-07-flak flak_mk1 60 300
STEP=0.08 fire d-08-railgun-heavy railgun_heavy 60 45
# A warhead that got inside, set off from the console (the harpoon's, in the main hold)
cmd dev "actions.blast(player, 16, 3, 'frag_heavy')"; pause 0.5
for i in 1 2 3 4 5 6 7 8; do cmd dev "step(0.08)"; pause 0.6; shot "d-06-blast-$i"; done
grep 'PLAYTEST \(away\|dev\|hull hit\)' "$OUT/game.log"
finish
