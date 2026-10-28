#!/usr/bin/env bash
# The radar with contacts out past its 10 km range: two raiders beyond the rim, one of them locked.
# Only the locked one should be ringed; the other is a faded dot. Needs Xvfb, xdotool and
# ImageMagick's import.
#   tools/playtest/radar.sh [out-dir]   (default build/playtest-screens-radar)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-radar}
XVFB_SCREEN=1280x800
. tools/playtest/lib.sh

# New game: seed 2026, the first sponsor, Start; undock and stand 3 km off the station
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12
key u; pause 4
cmd away 3000; pause 3
# A second raider; the scenario's own is out there too, both beyond 10 km from the ship
cmd raider 6; pause 4
shot r-01-radar
finish
