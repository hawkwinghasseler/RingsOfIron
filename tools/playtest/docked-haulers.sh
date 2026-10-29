#!/usr/bin/env bash
# Haulers docked at a station, in a real window under Xvfb: a new game docked at Highport, where
# Hauler Kestrel is based, undocked and backed off, then zoomed out so the station and the haulers
# at its ports show. They should wear the Hauler's hull and engines, not a green box outline.
# Screenshots throughout. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/docked-haulers.sh [out-dir]   (default build/playtest-screens-docked-haulers)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-docked-haulers}
XVFB_SCREEN=1280x800
. tools/playtest/lib.sh
wheel() { for _ in $(seq 1 "$2"); do xdotool mousemove --window "$WIN" 640 400 click "$1"; pause 0.15; done; pause 1; }

# A new game, docked at Highport
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 2; click 593 496
pause 25
shot h-01-docked
wheel 5 6; shot h-02-docked-wider
key u; pause 8
cmd away 300; pause 6
shot h-03-undocked
wheel 5 6; shot h-04-wider
wheel 5 6; shot h-05-widest
wheel 4 14; shot h-06-closer
finish
