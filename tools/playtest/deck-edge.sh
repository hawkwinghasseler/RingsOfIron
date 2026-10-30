#!/usr/bin/env bash
# How the deck view's light inside gives way to the light outside at the hull: a new game, the
# player's ship swapped for a gunship (or SHIP, e.g. surveyor_selene for sloped armor) and
# undocked so its shield is up, then the deck view at two zooms, and close each fade lit and as its light mask. Needs Xvfb, xdotool and ImageMagick's import.
#   [SHIP=id] tools/playtest/deck-edge.sh [out-dir]   (default build/playtest-screens-deck-edge)
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
cmd dev "flyShip '${SHIP:-gunship_heavy}'"; pause 1
key u; pause 8
cmd away 200; pause 6
wheel 4 12
key v; pause 2
shot deck-edge
wheel 4 6
# Each fade (deckFade), lit and as the light mask overlay: red where the decks' light is used,
# blue the light outside
for fade in ${FADES:-linear eased soft soft_eased hybrid}; do
  cmd dev "deckFade '$fade'"; pause 1
  shot "deck-edge-close-$fade"
  cmd dev "overlay 'lightMask' true"; pause 1
  shot "deck-edge-mask-$fade"
  cmd dev "overlay 'lightMask' false"; pause 1
done
grep 'PLAYTEST \(dev\|away\)' "$OUT/game.log"
finish
