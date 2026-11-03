#!/usr/bin/env bash
# Screens of a FILAMENT (VFD) HUD in the running game, for review against its mockups
# (documentation/ui-overhaul/vfd): the docked start at the helm, cruising, the HUD's own keys (as
# tools/playtest/hud-vfd-<spec>.sh gives them in spec_keys), a raider locked, its missiles inbound,
# the deck, the weapons console, and damage. Needs Xvfb, xdotool and ImageMagick's import. Waits on
# the game rather than the clock where it can, since a software-rendered game under Xvfb runs its
# sim well below real time.
#   tools/playtest/hud-vfd.sh <stations|context|task> [out-dir] [WxH] [ui-scale]
# Run from the repository root.
set -u
SPEC=${1:-stations}
OUT=${2:-$(pwd)/build/playtest-screens-vfd-$SPEC}
SIZE=${3:-}
UI_SCALE=${4:-}
[ -n "$SIZE" ] && XVFB_SCREEN=$SIZE
# The game reads the HUD from -Dhud.variant (documentation/ui-hud/Variants.md)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=vfd.$SPEC"
. tools/playtest/lib.sh

# The HUD's own keys, shot after cruising: its file gives spec_keys, else there are none
spec_keys() { :; }
if [ -f "tools/playtest/hud-vfd-$SPEC.sh" ]; then . "tools/playtest/hud-vfd-$SPEC.sh"; fi

# Walks the standing captain to the weapons console and sits there, as raid-by-hand.sh does:
# measures one step of W and of D on deck, walks the offset `seat` reads, then tries V
seat_state() { cmd seat; pause 0.4; grep 'PLAYTEST seat:' "$OUT/game.log" | tail -1; }
offset() { seat_state | sed -n 's/.*weapons \(-*[0-9.]*\) \(-*[0-9.]*\) off.*/\1 \2/p'; }
walk() { local t; t=$(awk -v n="$3" 'BEGIN { n = n < 0 ? -n : n; printf "%.2f", n * 0.4 }')
  awk -v t="$t" 'BEGIN { exit !(t >= 0.05) }' || return 0
  case "$3" in -*) hold "$2" "$t";; *) hold "$1" "$t";; esac; }
to_weapons() {
  local o0 ow od steps
  for _ in 1 2 3 4 5 6; do
    o0=$(offset); hold w 0.4; ow=$(offset); hold d 0.4; od=$(offset)
    steps=$(echo "$o0 $ow $od" | awk '{ wx = $1 - $3; wy = $2 - $4; dx = $3 - $5; dy = $4 - $6; x = $5; y = $6
      det = wx * dy - wy * dx; if (det == 0) { print "0 0"; exit }
      printf "%.1f %.1f", (x * dy - y * dx) / det, (wx * y - wy * x) / det }')
    set -- $steps
    walk w s "$1"; walk d a "$2"
    key v; pause 0.8
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *) key v; pause 0.8;; esac
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; esac
  done
  echo "never reached the weapons console: $(seat_state)"; return 1
}

# New game: seed 2026, the first sponsor, Start
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
until_docked 180; pause 3; shot v-01-docked
key u; pause 8; shot v-02-undocked
cmd away 3000; await 30 "PLAYTEST away"; pause 5; shot v-03-cruise
spec_keys
# A raider with dart racks 2 km out, hunting us; locked
cmd raider 2 raider_darts; await 30 "PLAYTEST raider"; pause 3; key Tab; pause 2; shot v-05-raider
pause 30; shot v-06-missiles
pause 10; shot v-07-missiles-later
key v; pause 3; shot v-08-deck
to_weapons && { pause 3; shot v-09-weapons; }
# Damage for the annunciator and the lamps: a holed tile, the hull beaten to 60%, the reactor out.
# Shot soon after, since the crew have the reactor back within seconds
cmd damage; cmd hull 0.6; cmd disable player; pause 2; shot v-10-damage
pause 4; shot v-11-damage-later
finish
