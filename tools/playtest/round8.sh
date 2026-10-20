#!/usr/bin/env bash
# Round 8's changes in a real window under Xvfb, from the round 5 career's seed-2026 saves, with a
# screenshot at each step: the cabin's air after the yard and light wounds closing (part 5), an ace
# bounty (part 3), and a fight from its start (part 2). Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/round8.sh [out-dir]                      (default build/playtest-screens-round8)
# Run from the repository root. The raid by hand is tools/playtest/raid-by-hand.sh, the rest of the
# career mid-late.sh. Commands to PlaytestLauncher only load saves and read state (`hostiles`).
set -u
OUT=${1:-$(pwd)/build/playtest-screens-round8}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Gunship_bounty_fight.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh

# Part 5: the corvette at Highport after the yard, its cabin's air on the gauges and the deck
cmd load Before the Moon trip; pause 14; shot p01-moon-trip-air
key v; pause 1; shot p02-moon-trip-deck; key v; pause 1

# Part 3: the ace bounty, Black Sal, as a player fights it: Tab, R, Space
cmd load Gunship bounty fight; pause 14
cmd hostiles; pause 1
shot p03-ace-bounty
key Tab; key r; hold space 4; shot p04-ace-bounty-fight
for i in 1 2 3 4 5 6; do key r; hold space 4; done
shot p05-ace-bounty-later
key l; pause 1; shot p06-ace-bounty-notices; key l; pause 1
cmd hostiles; pause 1

# Part 5: the crew panel just after the fight, then five and ten minutes on (at 1x: a leaking hull
# holds warp back)
key i; pause 1; shot p07-crew-after-the-fight; key i; pause 1
pause 300; key i; pause 1; shot p08-crew-five-minutes-on; key i; pause 1
pause 300; key i; pause 1; shot p08b-crew-ten-minutes-on; key i; pause 1
key l; pause 1; shot p09-notices-ten-minutes-on; key l; pause 1

finish
