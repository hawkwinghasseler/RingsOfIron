#!/usr/bin/env bash
# The particle-cloud shield in a real window under Xvfb: a new game, a gunship (its shield emitter up)
# parked beside the player's ship, its cloud at rest, an autocannon round fired from 2 km so the cloud
# gathers in its path before it lands, a railgun slug from 6 km that tears mass away, its shields
# lowered into the emitter and raised again, then its reactors off and a push, so it leaves its cloud
# adrift behind it. Screenshots throughout. Needs Xvfb, xdotool and ImageMagick's import.
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
cmd spawnship gunship_heavy "$METERS"; pause 2; shot h-01-at-rest
# 2 km at 1500 m/s: in sight of the emitters for over a second before it lands
cmd shoot autocannon_mk1 22 4 2000; shot h-02-gathering
pause 0.3; shot h-03-packed
for i in 1 2 3 4 5 6; do pause 0.08; shot h-04-hit-$i; done
pause 2; shot h-05-rejoining
pause 1
# Lowered, the cloud streams into the emitter; raised, back out of it
cmd shields off; pause 1; shot h-06-lowering
pause 3; shot h-07-lowered
cmd shields on; pause 1; shot h-08-raising
pause 3; shot h-09-raised
# 6 km at 6000 m/s: a second's warning, and a slug a light cloud barely slows
cmd shoot railgun_mk1 22 4 6000; pause 0.2; shot h-10-slug-gathering
for i in 1 2 3 4 5 6; do pause 0.08; shot h-11-slug-$i; done
pause 2
# Power gone (the slug may have done it already), the cloud is adrift: pushed off, the ship leaves it behind
cmd reactors; grep -q 'PLAYTEST reactors: reactors on' "$OUT/game.log" && cmd reactors
pause 0.3; cmd push 0 -12; pause 1.5; shot h-12-adrift
pause 2; shot h-13-left-behind
grep 'PLAYTEST \(spawnship\|shoot\|reactors\|shields\|push\)' "$OUT/game.log"
finish
