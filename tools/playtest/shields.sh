#!/usr/bin/env bash
# Shield bubbles in a real window under Xvfb: a new game, a gunship (its shield emitter up) parked
# beside the player's ship, an autocannon round into it, then a railgun slug that drains its store so
# its shields collapse, with a screenshot at each. `reactors` toggles its reactors too. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/shields.sh [out-dir] [meters]   (default build/playtest-screens-shields, 60 m)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-shields}
METERS=${2:-60}
. tools/playtest/lib.sh

# A new game, docked at Highport
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12
cmd spawnship gunship_heavy "$METERS"; pause 2; shot h-01-shields-up
cmd shoot autocannon_mk1 22 4; pause 0.1; shot h-02-autocannon
pause 0.2; shot h-03-autocannon-spreading
pause 1.5
# A slug costs the emitter more than the gunship's store holds: its shields fall as it lands
cmd shoot railgun_mk1 22 4; pause 0.05; shot h-04-railgun
pause 1.5; shot h-05-down
grep 'PLAYTEST \(spawnship\|shoot\|reactors\)' "$OUT/game.log"
finish
