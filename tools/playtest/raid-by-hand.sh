#!/usr/bin/env bash
# The raid on Rustwake flown by hand from the career's "Before the raid" save, in a real window under
# Xvfb, with keys only: Tab until the gun's target is the nearest defender still fighting, R and a
# burst of Space, again and again until the base falls or the frigate is beaten. The `raid` command to
# PlaytestLauncher only reads how it stands (who is nearest, what the gun has, the hull); nothing is
# flown or fired for the player. Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/raid-by-hand.sh [out-dir] [hold]        (default build/playtest-raid-by-hand)
# Without `hold` the captain stays at the helm and the frigate coasts where the save left it, moving
# with the base (before round 8 the helm's hold order was dropped by the first shot fired at us). With
# `hold`, Q makes the base the navigation target and 3 tells the helm to hold position on it; the
# captain stands (V), walks to the weapons console and sits there (V) to fight. `seat` only reads
# where the weapons console is from where the captain stands.
# Run from the repository root. Prints one line at the end: won or lost, the hull, the fight's length.
set -u
OUT=${1:-$(pwd)/build/playtest-raid-by-hand}
MODE=${2:-helm}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Before_the_raid.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh

# The last `raid` answer: its line, and the defender lines straight after it
raid_state() { cmd raid; pause 0.4; awk '/^PLAYTEST raid:/ { block = $0; on = 1; next } on && /^  defender / { block = block "\n" $0; next } { on = 0 } END { print block }' "$OUT/game.log"; }
first() { echo "$1" | grep 'PLAYTEST raid:' | tail -1; }
sim() { first "$1" | sed -n 's/.*sim \([0-9.]*\) s.*/\1/p'; }
# The line of the nearest defender still fighting ("  defender <name> at <km> km", "(target)" if the gun has it)
nearest() { echo "$1" | grep '^  defender .* at ' | grep -v ' beaten' | awk '{for (i = 1; i <= NF; i++) if ($i == "at") print $(i + 1), $0}' | sort -n | head -1 | cut -d' ' -f2- ; }

# Walks the standing captain to the weapons console and sits there: measures how far one step of W
# and of D moves them on deck, then walks the offset as so many steps of each, and tries V
# Waits for this command's own answer: a slow frame left the last one in the log, a step behind
seat_state() { local n; n=$(grep -c 'PLAYTEST seat:' "$OUT/game.log"); cmd seat
  for _ in $(seq 1 20); do [ "$(grep -c 'PLAYTEST seat:' "$OUT/game.log")" -gt "$n" ] && break; pause 0.2; done
  grep 'PLAYTEST seat:' "$OUT/game.log" | tail -1; }
offset() { seat_state | sed -n 's/.*weapons \(-*[0-9.]*\) \(-*[0-9.]*\) off.*/\1 \2/p'; }
# Holds key $1 (or $2 for a negative count) for $3 steps of 0.4 s
walk() { local t; t=$(awk -v n="$3" 'BEGIN { n = n < 0 ? -n : n; printf "%.2f", n * 0.4 }')
  awk -v t="$t" 'BEGIN { exit !(t >= 0.05) }' || return 0
  case "$3" in -*) hold "$2" "$t";; *) hold "$1" "$t";; esac; }
to_weapons() {
  local o0 ow od steps
  for try in 1 2; do
    # A slow frame can show a step late, so each reading waits a moment after the key
    o0=$(offset); hold w 0.4; pause 0.5; ow=$(offset); hold d 0.4; pause 0.5; od=$(offset)
    # Solve offset = a * (W step) + b * (D step); a negative count walks S or A. A step shorter
    # than 0.6 was blocked (by the helm, say), and #151's frigate's own step stands in for it
    steps=$(echo "$o0 $ow $od" | awk '{ wx = $1 - $3; wy = $2 - $4; dx = $3 - $5; dy = $4 - $6; x = $5; y = $6
      if (wx * wx + wy * wy < 0.36) { wx = -0.16; wy = -0.87 }
      if (dx * dx + dy * dy < 0.36) { dx = -0.90; dy = 0.17 }
      det = wx * dy - wy * dx; if (det == 0) { print "0 0"; exit }
      printf "%.1f %.1f", (x * dy - y * dx) / det, (wx * y - wy * x) / det }')
    set -- $steps
    walk w s "$1"; walk d a "$2"
    # V sits at the nearer console: at the helm, stand and walk on; refused, try once more
    key v; pause 0.5
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *"seat HELM"*) key v; pause 0.5;; *) key v; pause 0.5;; esac
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *"seat HELM"*) key v; pause 0.5;; esac
  done
  # Missiles striking the hull shove the figure about, and a measured step can be wrong: walk one
  # axis at a time towards the console instead (on #151's frigate W raises the offset's y, D its x)
  local prev="" other=0
  for _ in $(seq 1 60); do
    set -- $(offset)
    # Against a wall the long axis doesn't move: take the other one for a while
    [ "$1 $2" = "$prev" ] && other=4; prev="$1 $2"
    if awk -v x="$1" -v y="$2" 'BEGIN { exit !(x * x + y * y < 0.09) }'; then
      key v; pause 0.5; case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *"seat HELM"*) key v; pause 0.5;; esac
    fi
    if awk -v x="$1" -v y="$2" -v o="$other" 'BEGIN { l = (x < 0 ? -x : x) > (y < 0 ? -y : y); exit !(o > 0 ? !l : l) }'; then walk a d "$(awk -v x="$1" 'BEGIN { n = x * 0.4; print (n < 0 ? (n > -0.15 ? -0.15 : n) : (n < 0.15 ? 0.15 : n)) }')"
    else walk s w "$(awk -v y="$2" 'BEGIN { n = y * 0.4; print (n < 0 ? (n > -0.15 ? -0.15 : n) : (n < 0.15 ? 0.15 : n)) }')"; fi
    [ "$other" -gt 0 ] && other=$((other - 1))
  done
  echo "never reached the weapons console: $(seat_state)"; return 1
}

cmd load Before the raid; pause 14; shot r01-before-the-raid
start=$(raid_state); t0=$(sim "$start")
echo "start: $(first "$start")"
if [ "$MODE" = hold ]; then
  for _ in $(seq 1 12); do case "$(first "$(raid_state)")" in *"nav Rustwake"*) break;; esac; key q; done
  # V stands the captain from the helm; a save made with the captain already up (part 2's seed 2026) skips it
  # W/A/S/D walk the figure in screen terms, and the hold turns the ship towards Rustwake: the walk
  # comes first, so the steps measured hold, and the hold is given from the weapons console
  case "$(seat_state)" in *"seat null"*) ;; *) key v; pause 0.5;; esac
  to_weapons || { finish; exit 1; }
  key 3; pause 0.5
  echo "seated: $(first "$(raid_state)")"
  shot r02-helm-holds
fi
for i in $(seq 1 120); do
  state=$(raid_state)
  case "$(first "$state")" in *"base fallen"*|*"we are beaten"*) break;; esac
  # Tab round the contacts until the gun has the nearest defender (the base's guns and other craft come round too)
  for _ in $(seq 1 10); do
    want=$(nearest "$state")
    case "$want" in ""|*"(target)"*) break;; esac
    key Tab; state=$(raid_state)
  done
  # A hold dropped (by the helmsman going down, say) is given again from the weapons console
  if [ "$MODE" = hold ]; then case "$(first "$state")" in *"helm NONE"*) key 3;; esac; fi
  key r; hold space 5
  [ $((i % 10)) -eq 0 ] && shot "r03-raid-$((i / 10))"
done
end=$(raid_state); t1=$(sim "$end")
echo "end: $(first "$end")"
case "$(first "$end")" in *"base fallen"*) result=won;; *) result=lost;; esac
hull=$(first "$end" | sed -n 's/.*hull \([0-9.]*\).*/\1/p')
printf 'raid by hand (%s): %s, hull %s, %.0f s\n' "$MODE" "$result" "$hull" "$(echo "$t1 - $t0" | bc)"
# Round 10: the hits on us in the fight, by kind of defender (the impact lines name our ship "the other ship")
between() { grep "^PLAYTEST impact on the other ship from $1 " "$OUT/game.log" | awk -v a="$t0" -v b="$t1" '$NF + 0 >= a + 0 && $NF + 0 <= b + 0' | wc -l; }
echo "hits on us: $(between 'Pirate raider [0-9]*') from raiders, $(between 'Den battery [0-9]*') from batteries"
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
