#!/usr/bin/env bash
# Round 8's changes in a real window under Xvfb, from the round 5 career's seed-2026 saves, with a
# screenshot at each step: the cabin's air after the yard and light wounds closing (part 5), an ace
# bounty (part 3), a fight from its start (part 2) and a boarding broken off by fire (part 4). Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/round8.sh [out-dir]                      (default build/playtest-screens-round8)
# Run from the repository root. The raid by hand is tools/playtest/raid-by-hand.sh, the rest of the
# career mid-late.sh. Commands to PlaytestLauncher load saves and read state (`hostiles`, `raid`,
# `boarding`); one stand-in, `hull 0.4`, beats the frigate on its hull with its guns still up, since
# losing that way by hand isn't repeatable.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-round8}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Gunship_bounty_fight.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh
# ONLY=air, ace or boarding plays just that part
want() { [ -z "${ONLY:-}" ] || [ "$ONLY" = "$1" ]; }

# Part 5: the corvette at Highport after the yard, its cabin's air on the gauges and the deck
if want air; then
cmd load Before the Moon trip; pause 14; shot p01-moon-trip-air
key v; pause 1; shot p02-moon-trip-deck; key v; pause 1
fi

# Part 3: the ace bounty, Black Sal, as a player fights it: Tab, R, Space
if want ace; then
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
fi

# Part 4: a boarding broken off by fire. The frigate is beaten on its hull at the raid with power and
# guns left; once a pirate comes alongside, Tab to it and fire
if want boarding; then
cmd load Before the raid; pause 14
cmd hull 0.4; pause 2; shot p10-beaten-on-the-hull
boarding_state() { cmd boarding; pause 0.5; grep 'PLAYTEST boarding:' "$OUT/game.log" | tail -1; }
target() { cmd raid; pause 0.5; grep 'PLAYTEST raid:' "$OUT/game.log" | tail -1 | sed -n 's/.*, target \([^,]*\),.*/\1/p'; }
boarder=""
for i in $(seq 1 90); do
  state=$(boarding_state)
  case "$state" in *" took "*) break;; esac
  boarder=$(echo "$state" | sed -n 's/.*boarder \(.*\) at \([0-9]*\) m.*/\1/p')
  range=$(echo "$state" | sed -n 's/.* at \([0-9]*\) m.*/\1/p')
  # Close enough to hit: Tab round to it and fire
  if [ -n "$boarder" ] && [ "${range:-99999}" -lt 1500 ]; then
    for _ in $(seq 1 10); do [ "$(target)" = "$boarder" ] && break; key Tab; done
    shot p11-boarder-alongside
    hold space 4
    break
  fi
  pause 2
done
echo "boarding: $(boarding_state)"
for i in $(seq 1 10); do hold space 3; case "$(boarding_state)" in *"no boarder"*|*" took "*) break;; esac; done
echo "boarding after fire: $(boarding_state)"
shot p12-boarder-under-fire
key l; pause 1; shot p13-boarding-notices; key l; pause 1
fi

finish
