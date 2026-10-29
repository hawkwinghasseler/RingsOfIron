#!/usr/bin/env bash
# Several shield emitters on one ship, in a real window under Xvfb: a new game, a gunship parked
# beside the player's ship with two more Small emitters fitted (bow and stern) beside its own, its
# cloud settled round them; the stern emitter knocked out, so its share goes adrift from astern and
# leaves a weak spot there; back on; then the cloud trimmed to the bow and back to even.
# Screenshots throughout. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/shield-emitters.sh [out-dir] [meters]   (default build/playtest-screens-shield-emitters, 60 m)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-shield-emitters}
METERS=${2:-60}
XVFB_SCREEN=1280x800
. tools/playtest/lib.sh

# A new game, docked at Highport
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
# Start again by the mouse, should the key not have taken (the button sits there in a 1280x800 window)
pause 2; click 593 496
pause 25
cmd spawnship gunship_heavy "$METERS"
# Its own emitter is amidships at (14, 5); one in the bow and one aft of the reactor
cmd fit 21 6; cmd fit 6 4
pause 4; shot e-01-three-emitters
# The stern emitter out: what only it held goes adrift astern
cmd knock 6 4; pause 0.4; shot e-02-stern-out
pause 1.5; shot e-03-adrift-astern
pause 3; shot e-04-weak-spot
cmd knock 6 4; pause 4; shot e-05-stern-back
# Trimmed to the bow, then even again
cmd trim bow; pause 5; shot e-06-trim-bow
cmd trim even; pause 5; shot e-07-even
grep 'PLAYTEST \(spawnship\|fit\|knock\|trim\)' "$OUT/game.log"
finish
