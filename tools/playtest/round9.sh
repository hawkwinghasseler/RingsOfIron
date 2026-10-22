#!/usr/bin/env bash
# Round 9's changes in a real window under Xvfb, from the round 5 career's seed-2026 saves, with a
# screenshot at each step. Needs Xvfb, xdotool and ImageMagick's import.
#   ./gradlew :core:slowTest --tests '*Round5CareerTest'  (writes core/build/playtest-saves/seed-2026)
#   tools/playtest/round9.sh [out-dir]                      (default build/playtest-screens-round9)
# ONLY=<part> plays one part:
#   posts    part 1: the raid's defenders still firing after a short (Tab, R and Space from the helm)
#   suits    part 2: "Gunship bounty fight" left unanswered, the crew panel (I) as the cabin vents
#   raider   part 3: the guided first play's lone raider, firing back after our first hit
#   prizes   part 4: the raid fought to the fall, then a minute of Space past the struck defenders
#   boarding part 4: `hull 0.4` at the raid; how close a boarder comes, then Tab to it and fire
#   ace      the crew-care fix: "First ace fight" fought with Tab, R and Space, then the crew afterwards
#   reload   R pressed through one reload: "Launcher reloading" once in the log (L)
# Part 5's sweep saves are loaded the same way with SAVES=core/build/playtest-saves/seed-<n>; the
# copy into the game's own Load list is tools/playtest/career-saves.sh. The raid by hand is
# raid-by-hand.sh. Commands to PlaytestLauncher only read state (`raid`, `crew`, `hostiles`,
# `boarding`), except `load` and the one stand-in `hull 0.4`.
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-round9}
SAVES=${SAVES:-$(pwd)/core/build/playtest-saves/seed-2026}
[ -f "$SAVES/Before_the_raid.json" ] || { echo "no career saves in $SAVES: run the round 5 career test first"; exit 1; }
. tools/playtest/lib.sh
want() { [ -z "${ONLY:-}" ] || [ "$ONLY" = "$1" ]; }

raid_state() { cmd raid; pause 0.4; awk '/^PLAYTEST raid:/ { block = $0; on = 1; next } on && /^  defender / { block = block "\n" $0; next } { on = 0 } END { print block }' "$OUT/game.log"; }
first() { echo "$1" | grep 'PLAYTEST raid:' | tail -1; }
sim() { first "$1" | sed -n 's/.*sim \([0-9.]*\) s.*/\1/p'; }
nearest() { echo "$1" | grep '^  defender .* at ' | grep -v ' beaten' | awk '{for (i = 1; i <= NF; i++) if ($i == "at") print $(i + 1), $0}' | sort -n | head -1 | cut -d' ' -f2- ; }
gun_target() { first "$(raid_state)" | sed -n 's/.*, target \([^,]*\),.*/\1/p'; }
crew_state() { cmd crew; pause 0.5; awk '/^PLAYTEST crew:/ { block = $0; on = 1; next } on && /^  / { block = block "\n" $0; next } { on = 0 } END { print block }' "$OUT/game.log"; }
# Shots fired at us by $1 after sim second $2, from the impact lines
hits_from_after() { grep "^PLAYTEST impact on .* from $1 " "$OUT/game.log" | awk -v t="$2" '$NF + 0 > t + 0' | wc -l; }
# One round of the raid from the helm: Tab to the nearest defender still fighting, R and $1 s of Space
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

# Part 1: a defender shorted by our fire, and how often it fires at us after the short
if want posts; then
  cmd load Before the raid; pause 14; shot p1-01-raid-start
  shorted_name=""; shorted_at=""
  for i in $(seq 1 40); do
    state=$(raid_state)
    case "$(first "$state")" in *"base fallen"*|*"we are beaten"*) break;; esac
    if [ -z "$shorted_name" ]; then
      line=$(echo "$state" | grep '^  defender .* shorted ' | grep -v ' beaten' | head -1)
      if [ -n "$line" ]; then
        shorted_name=$(echo "$line" | sed -n 's/^  defender \(.*\) at .*/\1/p'); shorted_at=$(sim "$state")
        echo "posts: $shorted_name shorted at sim $shorted_at: $line"; shot p1-02-defender-shorted
      fi
    fi
    raid_round 4
    [ -n "$shorted_name" ] && [ "$(echo "$(sim "$(raid_state)") - $shorted_at > 40" | bc)" = 1 ] && break
  done
  shot p1-03-after-the-short
  [ -n "$shorted_name" ] && echo "posts: $shorted_name hit us $(hits_from_after "$shorted_name" "$shorted_at") times in the 40 s after its short"
  echo "posts: $(first "$(raid_state)")"
  key l; pause 1; shot p1-04-notices; key l; pause 1
fi

# Part 2: the ace's fight left unanswered, so it holes the cabin; the crew panel as it vents
if want suits; then
  cmd load Gunship bounty fight; pause 14; shot p2-01-ace-fight
  for i in $(seq 1 30); do
    pause 10
    c=$(crew_state); echo "suits $((i * 10)) s: $(echo "$c" | tr '\n' '|')"
    case "$c" in *suited\ \(*) [ -z "${suited_shot:-}" ] && { suited_shot=1; key i; pause 1; shot p2-02-crew-suiting-up; key i; pause 1; };; esac
    [ $((i % 6)) -eq 0 ] && { key i; pause 1; shot "p2-03-crew-$((i * 10))s"; key i; pause 1; }
  done
  key l; pause 1; shot p2-04-notices; key l; pause 1
  echo "suits: $(crew_state | tr '\n' '|')"
fi

# Part 3: the guided first play's lone raider (J and Enter take its bounty, U and W clear the station),
# then Tab, R and Space; whether it fires back after our first hit
if want raider; then
  click 640 433; pause 15
  key j; pause 1; key Return; pause 1; key Escape; pause 1; key u; pause 3
  hold w 6; pause 15; shot p3-01-first-raider-coming
  for i in $(seq 1 16); do
    for _ in 1 2 3 4 5; do case "$(gun_target)" in Raider*) break;; esac; key Tab; done
    key r; hold space 4
    [ "$i" = 2 ] && shot p3-02-first-raider-hit
    [ $((i % 8)) -eq 0 ] && shot "p3-03-first-raider-fight-$((i / 8))"
  done
  key l; pause 1; shot p3-04-first-raider-notices; key l; pause 1
  t=$(grep -m1 '^PLAYTEST impact on Raider' "$OUT/game.log" | awk '{print $NF}')
  echo "raider: first hit on it at sim ${t:-never}; it hit us $(hits_from_after Raider "${t:-0}") times after that, $(grep -c '^PLAYTEST impact on Raider' "$OUT/game.log") hits on it"
fi

# Part 4: the raid fought to the fall, then Space for a minute past the struck defenders
if want prizes; then
  cmd load Before the raid; pause 14
  for i in $(seq 1 60); do
    case "$(first "$(raid_state)")" in *"base fallen"*|*"we are beaten"*) break;; esac
    raid_round 5
  done
  echo "prizes: at the end $(first "$(raid_state)")"
  shot p4-01-after-the-fall
  cmd hostiles; pause 1
  for i in $(seq 1 12); do hold space 5; done
  cmd hostiles; pause 1
  shot p4-02-a-minute-of-fire-after-the-fall
  key l; pause 1; shot p4-03-notices-after-the-fall; key l; pause 1
  echo "prizes: defenders not struck after a minute of fire: $(awk '/^PLAYTEST hostiles:/ { last = NR } { line[NR] = $0 } END { for (i = last + 1; i <= NR && line[i] ~ /^  /; i++) print line[i] }' "$OUT/game.log" | grep 'IN_BUBBLE' | grep 'Pirate raider' | grep -v -c ' struck \| WRECKED ')"
fi

# Part 4: beaten on the hull at the raid (`hull 0.4`), how close a boarder comes; once it is inside
# 1 km, Tab to it and fire, and whether it breaks off
if want boarding; then
  cmd load Before the raid; pause 14
  for _ in 1 2 3 4 5 6 7 8 9 10; do cmd hull 0.4; grep 'PLAYTEST hull' "$OUT/game.log" | tail -1 | grep -q "no game" || break; pause 2; done
  pause 2; shot p4-04-beaten-on-the-hull
  boarding_state() { cmd boarding; pause 0.5; grep 'PLAYTEST boarding:' "$OUT/game.log" | tail -1; }
  closest=99999; closest_by=""
  for i in $(seq 1 120); do
    state=$(boarding_state)
    case "$state" in *" took "*) break;; esac
    range=$(echo "$state" | sed -n 's/.* at \([0-9]*\) m.*/\1/p')
    if [ -n "$range" ] && [ "$range" -lt "$closest" ]; then
      closest=$range; closest_by=$(echo "$state" | sed -n 's/.*boarder \(.*\) at .*/\1/p')
      [ "$range" -lt 1000 ] && [ -z "${close_shot:-}" ] && { close_shot=1; shot p4-05-boarder-within-1km
        for _ in $(seq 1 10); do [ "$(gun_target)" = "$closest_by" ] && break; key Tab; done
        hold space 4; }
    fi
    [ -n "$closest_by" ] && [ -z "$range" ] && case "$state" in *"no boarder"*) break;; esac
    pause 2
  done
  echo "prizes: boarder ${closest_by:-none} came within $closest m; $(boarding_state)"
  shot p4-06-boarding-end
  key l; pause 1; shot p4-07-boarding-notices; key l; pause 1
fi

# The crew-care fix: the corvette's first ace fight, fought by hand; the crew after it, and at the
# berth if it was lost and towed home
if want ace; then
  cmd load First ace fight; pause 14; shot c-01-first-ace-fight
  cmd hostiles; pause 1
  for i in $(seq 1 16); do
    case "$(first "$(raid_state)")" in *"we are beaten"*) break;; esac
    key Tab; key r; hold space 4
    [ "$i" = 4 ] && shot c-02-first-ace-fight-on
  done
  cmd hostiles; pause 1
  shot c-03-first-ace-fight-end
  key l; pause 1; shot c-04-notices; key l; pause 1
  echo "ace: $(first "$(raid_state)")"
  echo "ace crew after: $(crew_state | tr '\n' '|')"
  key i; pause 1; shot c-05-crew-after-the-fight; key i; pause 1
  # Beaten: the tow (B twice) brings it to a berth; the wounded there over a few days of warp
  case "$(first "$(raid_state)")" in *"we are beaten"*)
    key b; pause 1; key b; pause 1
    for _ in $(seq 1 60); do cmd docked; pause 1; [ "$(grep 'PLAYTEST docked:' "$OUT/game.log" | tail -1)" = "PLAYTEST docked: yes" ] && break; done
    shot c-06-towed-to-the-berth
    echo "ace crew at the berth: $(crew_state | tr '\n' '|')"
    key i; pause 1; shot c-07-crew-at-the-berth; key i; pause 1
    key period; key period; key period; key period; pause 60; key comma; key comma; key comma; key comma; pause 2
    echo "ace crew at the berth, later: $(crew_state | tr '\n' '|')"
    key i; pause 1; shot c-08-crew-at-the-berth-later; key i; pause 1
    key l; pause 1; shot c-09-notices-at-the-berth; key l; pause 1;;
  esac
fi

# "Launcher reloading" once per reload: R pressed ten times while the launcher reloads
if want reload; then
  cmd load Before the raid; pause 14
  state=$(raid_state)
  for _ in $(seq 1 10); do case "$(nearest "$state")" in ""|*"(target)"*) break;; esac; key Tab; state=$(raid_state); done
  for _ in $(seq 1 10); do key r; pause 1; done
  echo "reload: $(first "$(raid_state)")"
  key l; pause 1; shot r-01-reloading-once-in-the-log; key l; pause 1
fi

finish
