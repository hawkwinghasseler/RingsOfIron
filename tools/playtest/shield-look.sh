#!/usr/bin/env bash
# How the shield looks, in a real window under Xvfb: a new game, the player's ship swapped for a
# gunship (one shield emitter) and undocked, so its shield comes up. Close in from outside (the
# bubble's smoothed outline, its top and bottom infill and the flecks), from the deck (the bottom
# infill and the flecks round the deck plan, no top infill), and zoomed far out (no flecks).
# Screenshots throughout. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/shield-look.sh [out-dir]   (default build/playtest-screens-shield-look)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-shield-look}
XVFB_SCREEN=1280x800
. tools/playtest/lib.sh
wheel() { for _ in $(seq 1 "$2"); do xdotool mousemove --window "$WIN" 640 400 click "$1"; pause 0.15; done; pause 1; }

# A new game, docked at Highport
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
# Start again by the mouse, should the key not have taken (the button sits there in a 1280x800 window)
pause 2; click 593 496
pause 25
cmd dev "flyShip 'gunship_heavy'"; pause 1
key u; pause 8
cmd away 200; pause 6
# Close in, outside
wheel 4 12; shot l-01-outside
wheel 4 4; shot l-02-outside-closer
# From the deck
key v; pause 2; shot l-03-deck
key v; pause 2
# Far out: the flecks are gone, the bubble stays
wheel 5 24; shot l-04-far
grep 'PLAYTEST \(dev\|away\)' "$OUT/game.log"
finish
