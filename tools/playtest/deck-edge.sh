#!/usr/bin/env bash
# How the deck view's light inside gives way to the light outside at the hull, in each deck edge
# mode (deckEdge in the dev console): a new game, the player's ship swapped for a gunship and
# undocked so its shield is up, then the deck view at two zooms in every mode. Needs Xvfb,
# xdotool and ImageMagick's import.
#   tools/playtest/deck-edge.sh [out-dir]   (default build/playtest-screens-deck-edge)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-deck-edge}
XVFB_SCREEN=1280x800
. tools/playtest/lib.sh
wheel() { for _ in $(seq 1 "$2"); do xdotool mousemove --window "$WIN" 640 400 click "$1"; pause 0.15; done; pause 1; }

# A new game, docked at Highport
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 2; click 593 496
pause 25
cmd dev "flyShip 'gunship_heavy'"; pause 1
key u; pause 8
cmd away 200; pause 6
wheel 4 12
key v; pause 2
for mode in hard half tile wide inset; do
  cmd dev "deckEdge '$mode'"; pause 1
  shot "e-$mode"
done
wheel 4 6
for mode in hard half tile wide inset; do
  cmd dev "deckEdge '$mode'"; pause 1
  shot "e-$mode-close"
done
grep 'PLAYTEST \(dev\|away\)' "$OUT/game.log"
finish
