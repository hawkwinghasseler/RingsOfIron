#!/usr/bin/env bash
# The orbit map (M): low orbit with its apsides, a burn planned with a click on the path and dragged out
# by its prograde handle, then a transfer to the lunar mine drawn through the Moon's sphere of influence.
# Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/orbit-map.sh [out-dir]   (default build/playtest-screens-orbit-map, 1280x800)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-orbit-map}
SIZE=1280x800
XVFB_SCREEN=$SIZE
. tools/playtest/lib.sh

# New game: seed 2026, the first sponsor, Start
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12
key u; pause 4
cmd away 3000; pause 3
key m; pause 2; shot o-01-orbit-map
# The map's middle is the body; low orbit fills it, so its right-hand edge is about 340 px out
click 825 400; pause 1; shot o-02-burn-planned
# Drag the prograde handle (46 px along the velocity, up the screen there) 150 px out
xdotool mousemove --window "$WIN" 825 354; pause 0.3
xdotool mousedown 1; pause 0.2
for y in 330 300 270 240 220 204; do xdotool mousemove --window "$WIN" 825 $y; pause 0.15; done
pause 0.3; shot o-03-handle-dragged
xdotool mouseup 1; pause 1; shot o-04-burn-raised
# A transfer to the lunar mine through the Moon's sphere of influence
cmd target shackleton
key semicolon; pause 2; key Return; pause 2
# Zoom out about a point right of Earth, so Earth slides right and the way to the Moon fills the map
xdotool mousemove --window "$WIN" 780 400; pause 0.3
for _ in $(seq 1 9); do xdotool click 5; pause 0.1; done
pause 1; shot o-05-moon-transfer
# Centre on where the path meets the Moon (body, ship, target, encounter) and zoom in on it
for _ in 1 2 3; do click 1033 636; done
xdotool mousemove --window "$WIN" 485 400; pause 0.3
for _ in $(seq 1 6); do xdotool click 4; pause 0.1; done
pause 1; shot o-06-moon-encounter
key m; pause 1
finish
