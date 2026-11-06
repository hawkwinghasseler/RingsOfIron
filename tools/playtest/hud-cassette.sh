#!/usr/bin/env bash
# A Cassette (Yard Standard) HUD in a real window under Xvfb, a screenshot at each post and situation
# the specs' visibility rules turn on: docked, cruising near Highport (the at-rest test), the map, a
# raider and its darts at the helm, the deck, gunnery, hull damage, a disabled ship at gunnery and
# on deck, and abandon ship and the tow asked for. Needs Xvfb, xdotool and ImageMagick's import.
# Waits on the game, not the clock, where it can: making the world takes a while under Xvfb.
#   tools/playtest/hud-cassette.sh <task|stations|context> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-screens-cassette-<spec>, 1280x800, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-task}
OUT=${2:-$(pwd)/build/playtest-screens-cassette-$SPEC}
SIZE=${3:-1280x800}
UI_SCALE=${4:-}
XVFB_SCREEN=$SIZE
# The game reads the HUD from -Dhud.variant (documentation/ui-hud/Variants.md)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=cassette.$SPEC"
. tools/playtest/lib.sh

seat_state() { cmd seat; pause 0.4; grep 'PLAYTEST seat:' "$OUT/game.log" | tail -1; }
# The ship the gun is locked on, from the raid line; Tab round the contacts until it is the raider
gun_target() { cmd raid; pause 0.4; grep '^PLAYTEST raid:' "$OUT/game.log" | tail -1 | sed -n 's/.*, target \([^,]*\),.*/\1/p'; }
lock_raider() { for _ in 1 2 3 4 5 6; do case "$(gun_target)" in "Pirate raider"*) return 0;; esac; key Tab; done
  echo "never locked the raider: $(gun_target)"; return 1; }
offset() { seat_state | sed -n 's/.*weapons \(-*[0-9.]*\) \(-*[0-9.]*\) off.*/\1 \2/p'; }
walk() { local t; t=$(awk -v n="$3" 'BEGIN { n = n < 0 ? -n : n; printf "%.2f", n * 0.4 }')
  awk -v t="$t" 'BEGIN { exit !(t >= 0.05) }' || return 0
  case "$3" in -*) hold "$2" "$t";; *) hold "$1" "$t";; esac; }
# Walks the standing captain to the weapons console and sits there (as raid-by-hand.sh does)
to_weapons() {
  local o0 ow od steps
  for _ in 1 2 3 4 5 6; do
    o0=$(offset); hold w 0.4; ow=$(offset); hold d 0.4; od=$(offset)
    steps=$(echo "$o0 $ow $od" | awk '{ wx = $1 - $3; wy = $2 - $4; dx = $3 - $5; dy = $4 - $6; x = $5; y = $6
      det = wx * dy - wy * dx; if (det == 0) { print "0 0"; exit }
      printf "%.1f %.1f", (x * dy - y * dx) / det, (wx * y - wy * x) / det }')
    set -- $steps
    walk w s "$1"; walk d a "$2"
    key v; pause 0.5
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *"seat HELM"*) key v; pause 0.5;; *) key v; pause 0.5;; esac
    case "$(seat_state)" in *"seat WEAPONS"*) return 0;; *"seat HELM"*) key v; pause 0.5;; esac
  done
  echo "never reached the weapons console: $(seat_state)"; return 1
}

# New game: seed 2026, the Patrol, Start, by the pointer: the menus' places at 1280 x 800, kept
# about the centre (the menus are centred, at UI scale 1)
W=${SIZE%x*}; H=${SIZE#*x}
at() { click $((W / 2 + $1 - 640)) $((H / 2 + $2 - 400)); }
at 640 366; pause 1.5
at 663 314; key ctrl+a; key 2 0 2 6
at 585 400; at 593 496
# The world takes a while to make: wait for the game to answer before the first shot
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3; shot t01-docked
key u; pause 4; shot t02-undocked
cmd away 3000; pause 4; shot t03-cruise
pause 12; shot t04-cruise-quiet
key m; pause 1.5; shot t05-map; key m; pause 1
# A raider with dart racks 7 km out (inside its darts' 8 km, outside our guns'), hunting us, locked
# with Tab, until its darts are in the air
cmd raider 7 raider_darts; pause 3; lock_raider; pause 1; shot t06-raider
pause 8; shot t07-missiles
pause 5; shot t08-missiles-later
# Stand up from the helm: the deck
key v; pause 1.5; shot t09-deck
# Walk to the weapons console: gunnery
if to_weapons; then pause 1.5; shot t10-gunnery; pause 6; shot t11-gunnery-later; fi
# The hull beaten in and a hole: the schematic comes up outside
cmd hull 0.45; cmd damage; pause 2; shot t12-damage
# No reactor: the ship is disabled, the crisis placard comes down (at the guns the tow key does
# nothing, so the placard says where to call it from). The crew mend a reactor in seconds, so each
# shot takes it out again first
cmd disable player; pause 3; shot t13-disabled
cmd disable player; key p; pause 0.8; shot t14-abandon-asked
# Stand up: the placard and the suit unit on deck, then the tow asked for (B at no door)
cmd disable player; key v; pause 1.5; shot t15-disabled-deck
cmd disable player; key b; pause 0.8; shot t16-tow-asked
pause 3
finish
