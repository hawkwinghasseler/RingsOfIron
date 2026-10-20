#!/usr/bin/env bash
# The raid on Rustwake flown by hand from the career's "Before the raid" save, in a real window under
# Xvfb, with keys only: Tab until the gun's target is the nearest defender still fighting, R and a
# burst of Space, again and again until the base falls or the frigate is beaten. The `raid` command to
# PlaytestLauncher only reads how it stands (who is nearest, what the gun has, the hull); nothing is
# flown or fired for the player. Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/raid-by-hand.sh [out-dir]               (default build/playtest-raid-by-hand)
# The captain stays at the helm and the frigate coasts where the save left it, moving with the base:
# the helm's hold order is dropped by the first shot fired at us, and the base's darts are inbound
# as the save loads.
# Run from the repository root. Prints one line at the end: won or lost, the hull, the fight's length.
set -u
OUT=${1:-$(pwd)/build/playtest-raid-by-hand}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Before_the_raid.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh

# The last `raid` answer: its line, and the defender lines straight after it
raid_state() { cmd raid; pause 0.4; awk '/^PLAYTEST raid:/ { block = $0; on = 1; next } on && /^  defender / { block = block "\n" $0; next } { on = 0 } END { print block }' "$OUT/game.log"; }
first() { echo "$1" | grep 'PLAYTEST raid:' | tail -1; }
sim() { first "$1" | sed -n 's/.*sim \([0-9.]*\) s.*/\1/p'; }
# The line of the nearest defender still fighting ("  defender <name> at <km> km", "(target)" if the gun has it)
nearest() { echo "$1" | grep '^  defender .* at ' | grep -v ' beaten' | awk '{for (i = 1; i <= NF; i++) if ($i == "at") print $(i + 1), $0}' | sort -n | head -1 | cut -d' ' -f2- ; }

cmd load Before the raid; pause 14; shot r01-before-the-raid
start=$(raid_state); t0=$(sim "$start")
echo "start: $(first "$start")"
for i in $(seq 1 120); do
  state=$(raid_state)
  case "$(first "$state")" in *"base fallen"*|*"we are beaten"*) break;; esac
  # Tab round the contacts until the gun has the nearest defender (the base's guns and other craft come round too)
  for _ in $(seq 1 10); do
    want=$(nearest "$state")
    case "$want" in ""|*"(target)"*) break;; esac
    key Tab; state=$(raid_state)
  done
  key r; hold space 5
  [ $((i % 10)) -eq 0 ] && shot "r03-raid-$((i / 10))"
done
end=$(raid_state); t1=$(sim "$end")
echo "end: $(first "$end")"
case "$(first "$end")" in *"base fallen"*) result=won;; *) result=lost;; esac
hull=$(first "$end" | sed -n 's/.*hull \([0-9.]*\).*/\1/p')
printf 'raid by hand: %s, hull %s, %.0f s\n' "$result" "$hull" "$(echo "$t1 - $t0" | bc)"
shot r04-raid-end
key l; pause 1; shot r05-raid-end-notices; key l; pause 1
key i; pause 1; shot r06-crew-after; key i; pause 1

# The missile warning after the fall: the darts still in flight, and whether any still homes on us
at=0
for s in 5 15 30 60; do
  pause $((s - at)); at=$s
  echo "${s} s after the end: $(first "$(raid_state)")"
  shot "r07-after-$s"
done

finish
