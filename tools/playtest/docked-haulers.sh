#!/usr/bin/env bash
# Haulers docked at a station, in a real window under Xvfb: a new game docked at Highport, where
# Hauler Kestrel is based. Outside, the haulers at its ports are Haulers, not green box outlines;
# on deck (V), the station and every craft docked to it show as decks, so the loaders and the
# haulers' crews can be watched carrying a hauler's cargo across as it buys and sells.
# Screenshots throughout. Needs Xvfb, xdotool and ImageMagick's import.
# A trade is staged (the haul command) and the game log prints the haulers' loads with each deck shot.
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
# On deck: the station and the haulers docked to it, their cargo crossing by hand
key v; pause 2
shot h-03-deck
wheel 5 10; shot h-04-deck-wider
# A new game's haulers sit idle a while, so a trade is staged: each has bought food to load
cmd haul food 40
cmd loads
for i in 1 2 3 4 5 6; do pause 20; shot h-05-deck-$i; cmd loads; done
key v; pause 1
finish
