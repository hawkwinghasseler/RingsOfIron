#!/usr/bin/env bash
# Round 10's changes in a real window under Xvfb, from a career's saves, with a screenshot at each
# step. Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/round10.sh [out-dir]                     (default build/playtest-screens-round10)
# ONLY=<part> plays one part:
#   frigate part 1: the "Frigate" save, and the day, money and rank on it (SAVES=.../seed-9 for seed 9)
#   lone    part 3: from "Frigate", undocked, one lone raider brought in at 4 km (`raider 4`), fought
#           with Tab, R and Space; whether it fires on the frigate. RAIDER_FIT=<loadout> arms it
#   captain part 4: "First ace fight" by hand; if lost, the tow (B twice) and a minute of warp x4 at
#           the berth, with the captain's air and where the figure stands before and after
#   beaten  part 5: the raid fought to the fall, counting the Tabs that land on a beaten defender, then
#           a minute of Space with no Tab (hits on the base's batteries, and on us), then where Tab lands
# Part 2's raid is flown by raid-by-hand.sh, which counts the hits on us by kind of defender.
# Commands to PlaytestLauncher only read state (`raid`, `crew`, `captain`, `career`, `hostiles`),
# except `load` and the one stand-in `raider`.
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-round10}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/First_ace_fight.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh
want() { [ -z "${ONLY:-}" ] || [ "$ONLY" = "$1" ]; }

raid_state() { cmd raid; pause 0.4; awk '/^PLAYTEST raid:/ { block = $0; on = 1; next } on && /^  defender / { block = block "\n" $0; next } { on = 0 } END { print block }' "$OUT/game.log"; }
first() { echo "$1" | grep 'PLAYTEST raid:' | tail -1; }
sim() { first "$1" | sed -n 's/.*sim \([0-9.]*\) s.*/\1/p'; }
nearest() { echo "$1" | grep '^  defender .* at ' | grep -v ' beaten' | awk '{for (i = 1; i <= NF; i++) if ($i == "at") print $(i + 1), $0}' | sort -n | head -1 | cut -d' ' -f2- ; }
gun_target() { first "$(raid_state)" | sed -n 's/.*, target \([^,]*\),.*/\1/p'; }
last_line() { cmd "$1"; pause 0.5; grep "^PLAYTEST $1:" "$OUT/game.log" | tail -1 | sed "s/^PLAYTEST $1: //"; }
hostiles() { cmd hostiles; pause 1; awk '/^PLAYTEST hostiles:/ { last = NR } { line[NR] = $0 } END { for (i = last + 1; i <= NR && line[i] ~ /^  /; i++) print line[i] }' "$OUT/game.log"; }
# The sim time now, from a `raid` read
now() { sim "$(raid_state)"; }
# Hits on $1 (a name pattern) from $2 (a name pattern) after sim second $3, from the impact lines
hits() { grep "^PLAYTEST impact on $1 from $2 " "$OUT/game.log" | awk -v t="$3" '$NF + 0 > t + 0' | wc -l; }
raid_round() {
  local state want
  state=$(raid_state)
  for _ in $(seq 1 10); do
    want=$(nearest "$state")
    case "$want" in ""|*"(target)"*) break;; esac
    key Tab; state=$(raid_state)
  done
  key r; hold space "$1"
}

# Part 1: the frigate's day on this seed's "Frigate" save
if want frigate; then
  cmd load Frigate; pause 14; shot f-01-frigate
  echo "frigate: $(last_line career)"
  key l; pause 1; shot f-02-notices; key l; pause 1
fi

# Part 3: a lone raider brought in at 4 km on the frigate, fought from the helm
if want lone; then
  cmd load Frigate; pause 14
  ship=$(last_line career | sed -n 's/.*ship \(.*\), hull.*/\1/p')
  key u; pause 3; hold w 6; pause 20
  echo "lone: undocked: $(last_line career)"
  # RAIDER_FIT picks its loadout (raider_chaingun, raider_railgun...); by default the difficulty does
  said=$(last_line "raider 4 ${RAIDER_FIT:-}"); echo "lone: $said"
  raider=$(echo "$said" | sed -n 's/^\(.*\) (.*/\1/p')
  pause 8; shot l-01-raider-coming
  t0=$(now)
  for i in $(seq 1 24); do
    for _ in 1 2 3 4 5 6; do [ "$(gun_target)" = "$raider" ] && break; key Tab; done
    key r; hold space 4
    [ "$i" = 3 ] && shot l-02-raider-fight
    [ $((i % 8)) -eq 0 ] && shot "l-03-raider-fight-$((i / 8))"
    hostiles | grep -q "$raider .* \(struck\|NO_POWER\|NO_CREW\|HULL\|WRECKED\|ABANDONED\|running\) " && break
  done
  echo "lone: after $(echo "$(now) - $t0" | bc) s: $raider hit us $(hits "$ship" "$raider" "$t0") times, we hit it $(hits "$raider" "$ship" "$t0") times"
  echo "lone: $(hostiles | grep "$raider")"
  echo "lone: $(last_line career)"
  key l; pause 1; shot l-04-notices; key l; pause 1
fi

# Part 4: the first ace fight by hand; lost, the tow and the berth, and the captain there
if want captain; then
  # ACE_WAIT=0 fires as soon as the game answers after the load, as the career does; round 9 waited 14 s
  cmd load First ace fight
  if [ "${ACE_WAIT:-14}" = 0 ]; then
    for _ in $(seq 1 60); do pause 0.5; case "$(last_line career)" in day*) break;; esac; done
  else
    pause "${ACE_WAIT:-14}"
  fi
  echo "captain: start: $(last_line career), sim $(now) (saved at $(sed -n 's/.*"gameSeconds": *\([0-9.]*\).*/\1/p' "$SAVES/First_ace_fight.meta.json"))"
  for i in $(seq 1 16); do
    case "$(first "$(raid_state)")" in *"we are beaten"*) break;; esac
    # Tab until the gun has the ace (round 9's script pressed Tab once a round, blind, which could
    # move the gun off it)
    ace=$(hostiles | grep ' IN_BUBBLE target=@player ' | head -1 | sed 's/^  \(.*\) IN_BUBBLE .*/\1/')
    for _ in 1 2 3 4 5 6; do [ -n "$ace" ] && [ "$(gun_target)" = "$ace" ] && break; key Tab; done
    key r; hold space 4
    [ "$i" = 1 ] && shot c-01-first-ace-fight
    [ "$i" = 4 ] && shot c-02-first-ace-fight-on
  done
  echo "captain: fight: $(first "$(raid_state)")"
  echo "captain: $(last_line captain)"
  shot c-03-first-ace-fight-end
  key l; pause 1; shot c-04-notices; key l; pause 1
  case "$(first "$(raid_state)")" in *"we are beaten"*)
    key b; pause 1; key b; pause 1
    until_docked 60
    shot c-05-towed-to-the-berth
    echo "captain: at the berth: $(last_line captain); $(last_line seat | cut -d, -f1)"
    echo "captain: crew at the berth: $(cmd crew; pause 0.5; awk '/^PLAYTEST crew:/ { block = $0; on = 1; next } on && /^  / { block = block "|" $0; next } { on = 0 } END { print block }' "$OUT/game.log")"
    key i; pause 1; shot c-06-crew-at-the-berth; key i; pause 1
    # Warp x4 in 20 s steps until the captain is out of suit air, dead, aboard the station or after
    # five minutes; part 4 drops the warp while the figure is walked out of thin air
    for i in $(seq 1 15); do
      key period; key period; key period; key period; pause 20
      c=$(last_line captain); echo "captain: warp step $i: $c; $(last_line seat | cut -d, -f1)"
      case "$c" in *dead*|*"in Highport"*|*"(0 s of air)"*) break;; esac
      [ "$i" = 3 ] && shot c-07-warp-at-the-berth
    done
    key comma; key comma; key comma; key comma; pause 20
    echo "captain: after the warp: $(last_line captain)"
    echo "captain: $(last_line career)"
    shot c-07-after-warp
    key i; pause 1; shot c-08-crew-after-warp; key i; pause 1
    key l; pause 1; shot c-09-notices-at-the-berth; key l; pause 1;;
  esac
fi

# Part 5: Tab while the raid is fought (does it land on a struck defender?), then at the fall a minute
# of Space with no Tab (part 5: after the fall every Tab lands on a prize, and aimed fire on one brings
# it back), then Tab round the contacts to show where it lands once nothing fights
if want beaten; then
  cmd load Before the raid; pause 14
  ship=$(last_line career | sed -n 's/.*ship \(.*\), hull.*/\1/p')
  tabs=0; on_struck=0
  for i in $(seq 1 60); do
    state=$(raid_state)
    case "$(first "$state")" in *"base fallen"*|*"we are beaten"*) break;; esac
    for _ in $(seq 1 10); do
      case "$(nearest "$state")" in ""|*"(target)"*) break;; esac
      key Tab; tabs=$((tabs + 1)); state=$(raid_state)
      echo "$state" | grep -q '^  defender .* beaten.*(target)' && on_struck=$((on_struck + 1))
    done
    key r; hold space 5
  done
  fall=$(now)
  echo "beaten: during the raid, $tabs Tabs, $on_struck of them onto a beaten defender"
  echo "beaten: at the fall (sim $fall): $(first "$(raid_state)")"
  hostiles | grep -v ' rails$' | sed 's/^/beaten: /'
  shot b-01-the-fall
  # Half a minute with no key (the guns at will only), then a minute of Space (with no target locked,
  # Space pulls every main gun's trigger)
  pause 30; quiet=$(now)
  echo "beaten: 30 s after the fall, no keys: $(hits 'Den battery [0-9]*' "$ship" "$fall") hits on the batteries, $(hits 'Pirate raider [0-9]*' "$ship" "$fall") on raiders"
  for i in $(seq 1 12); do hold space 5; done
  shot b-02-a-minute-of-space
  echo "beaten: then a minute of Space: $(hits 'Den battery [0-9]*' "$ship" "$quiet") hits on the batteries, $(hits 'Pirate raider [0-9]*' "$ship" "$quiet") on raiders"
  echo "beaten: since the fall: $(hits "$ship" 'Pirate raider [0-9]*' "$fall") hits on us from raiders, $(hits "$ship" 'Den battery [0-9]*' "$fall") from batteries"
  hostiles | grep -v ' rails$' | sed 's/^/beaten: after: /'
  key l; pause 1; shot b-03-notices; key l; pause 1
  # Where Tab lands once nothing fights (no Space after it)
  for i in $(seq 1 8); do
    key Tab; t=$(gun_target)
    echo "beaten: Tab $i after the fall: ${t:-none} $(hostiles | grep -F "  $t " | awk '{for (i = 1; i <= NF; i++) if ($i ~ /^target=/) { print $(i + 1); exit }}')"
    [ "$i" = 1 ] && shot b-04-tab-after-the-fall
  done
fi

finish
