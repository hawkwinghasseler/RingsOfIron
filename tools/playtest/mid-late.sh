#!/usr/bin/env bash
# The middle and late career, played in a real window under Xvfb from saves the scripted career
# writes, with a screenshot at each step. Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:test --tests '*Round5CareerTest'      (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/mid-late.sh [out-dir]                    (default build/playtest-screens-late)
# Run from the repository root. Keys and clicks play it; three things come through commands to
# PlaytestLauncher instead: `load` picks a save by name (the load list's order shifts as autosaves
# land), `target` picks a navigation target by name (how many Q presses that takes changes from
# save to save), and `strand` empties the tanks so the tug can be called.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-late}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Gunship.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh

# The career's saves in the load list (the newest, the raid, is Continue's)
shot 01-menu-with-saves
click 640 472; pause 1; shot 02-load-list
xdotool mousemove --window "$WIN" 640 450; for _ in 1 2 3 4 5 6; do xdotool click 5; pause 0.2; done; pause 0.5
shot 02b-load-list-wheel
key Escape; pause 1

# The Moon trip on the transfer autopilot, from Highport
cmd load Before the Moon trip; pause 14; shot 03-docked-at-highport
key u; pause 1; shot 03b-undock-on-deck
key v; pause 1; key u; pause 3; shot 04-undocked
cmd target Shackleton; key 6; pause 3; shot 05-autopilot-engaged
pause 20; shot 06-autopilot-at-the-moon
pause 25; shot 07-arrived-helm-waits
key v; pause 40; key v; pause 1; shot 08-docked-at-shackleton

# Stranded with dry tanks and no threat: the tow
key u; pause 2; hold s 2; pause 8
cmd strand; pause 2; shot 09-stranded
key Escape; pause 1; shot 10-pause-call-a-tow; key Escape; pause 1
key b; pause 1; shot 11-tow-asked
key b; pause 6; shot 12-towed-in
key l; pause 1; shot 13-notice-log; key l; pause 1

# The gunship: the helm's dock order
cmd load Gunship; pause 14; shot 14-gunship
cmd target Crisium; key 5; pause 1; shot 15-dock-order
key v; pause 45; key v; pause 1; shot 16-docked-by-the-helm
key i; pause 1; shot 17-gunship-crew; key i; pause 1

# A bounty fight in the gunship
cmd load Gunship bounty fight; pause 14
key Tab; hold space 4; shot 18-gunship-fight
pause 20; shot 19-gunship-fight-later
key i; pause 1; shot 20-crew-in-a-fight; key i; pause 1

# Commander, and the frigate at Anvil Yards
cmd load Commander; pause 14; shot 21-commander
key j; pause 1; shot 22-commander-board; key Escape; pause 1
cmd load Frigate; pause 14; shot 23-frigate
key y; pause 1; shot 24-frigate-yard; key Escape; pause 1

# The raid on the pirates' base, in the frigate
cmd load Before the raid; pause 14; shot 25-raid
key Tab; hold space 5; pause 20; shot 26-raid-fight
key i; pause 1; shot 27-crew-in-the-raid; key i; pause 1
key m; pause 1; shot 28-traffic-map; key m; pause 1
pause 30; shot 29-raid-later
key l; pause 1; shot 30-raid-notices; key l; pause 1

finish
