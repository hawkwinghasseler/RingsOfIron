#!/usr/bin/env bash
# The bars over crew at timed jobs, in a real window under Xvfb: a new game, the ship taken out from
# Highport and seen from its deck, fires started and the hull shot so the crew go to work, then the
# world stepped on a few seconds at a time, a screenshot at each. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/progress-bars.sh [out-dir]   (default build/playtest-screens-progress-bars)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-progress-bars}
. tools/playtest/lib.sh

# A new game. Building the world can take a minute in software GL
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause ${START_WAIT:-60}
key u; pause 5; cmd away 500; pause 3
key v; pause 2
cmd dev "pause()"
cmd dev "fire(player)"; cmd dev "fire(player)"
cmd dev "shoot 'autocannon_mk1', player, 60, 90"; cmd dev "step(1)"; pause 2
cmd dev "shoot 'railgun_mk1', player, 60, 270"; cmd dev "step(1)"; pause 2
for i in 1 2 3 4 5 6 7 8 9 10; do cmd dev "step(${STEP:-3})"; pause 3; shot "p-$i"; done
grep 'PLAYTEST \(away\|dev\)' "$OUT/game.log"
finish
