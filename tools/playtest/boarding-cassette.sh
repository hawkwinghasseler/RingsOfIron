#!/usr/bin/env bash
# The Task spec's boarding panel (BRD) under the Cassette Task HUD, in a real window under Xvfb: out from
# Highport, a raider disabled and latched to; K at the helm hangs boarding down the right side, the
# outside view in sight beside it, the right column's units giving way and the view easing left; a part
# selected, then the first one marked, which takes the raider (nobody aboard defends it), and its strip
# under way. On deck (V stands up) the panel stays, the deck in sight beside it, with a walk aft; K closes
# it and opens it again; at the weapons console too; the ship's own reactor out, the crisis placard over
# it, the panel under the placard. Keyboard only.
# Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/boarding-cassette.sh [out-dir] [WxH] [ui-scale]
#   (default build/playtest-boarding-cassette, 1280x800, the automatic scale)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-boarding-cassette}
SIZE=${2:-1280x800}
UI_SCALE=${3:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=cassette.task"
. tools/playtest/lib.sh

seat_state() { cmd seat; pause 0.4; grep 'PLAYTEST seat:' "$OUT/game.log" | tail -1; }
offset() { seat_state | sed -n 's/.*weapons \(-*[0-9.]*\) \(-*[0-9.]*\) off.*/\1 \2/p'; }
walk() { local t; t=$(awk -v n="$3" 'BEGIN { n = n < 0 ? -n : n; printf "%.2f", n * 0.4 }')
  awk -v t="$t" 'BEGIN { exit !(t >= 0.05) }' || return 0
  case "$3" in -*) hold "$2" "$t";; *) hold "$1" "$t";; esac; }
# Walks the standing captain to the weapons console and sits there (as hud-cassette.sh does)
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

# New game: seed 2026, the Patrol, Start, by the keyboard (the menus' own focus order)
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3
# Away from the station, once the loaders are done (U waits for them)
for _ in $(seq 1 30); do key u; pause 2; cmd docked; pause 0.5; grep -q '^PLAYTEST docked: no$' "$OUT/game.log" && break; done
# A raider, disabled and latched to
cmd away 3500; pause 3
cmd disable raider; pause 3
cmd latch; pause 4; shot b00-latched
# At the helm: K hangs boarding down the right side as the view eases left
key k; pause 0.25; shot b01-sliding
pause 1.5; shot b02-helm
key Down; key Down; key Down; pause 0.4; shot b03-part-selected
# The first part marked: nobody aboard defends the raider, so the mark takes it, and the strip starts
key Up; key Up; pause 0.3; key Return; pause 1.5; shot b04-marked
pause 8; shot b05-stripping
# On deck: V stands up from the helm, and the panel stays, the deck in sight beside it; a walk aft
key v; pause 2; shot b06-deck
hold a 0.8; pause 0.6; shot b07-deck-walked
# K closes it, the view easing back; K opens it again
key k; pause 1.5; shot b08-deck-closed
key k; pause 1.5; shot b09-deck-again
key k; pause 1
# At the weapons console: gunnery, the guns giving way to the panel
if to_weapons; then pause 1.5; shot b10-gunnery; key k; pause 1.5; shot b11-gunnery-boarding; fi
# The ship's own reactor out: the crisis placard comes down, the panel under it. The crew mend a reactor
# in seconds, so each shot takes it out again first
cmd disable player; pause 3; shot b12-crisis
cmd disable player; key k; pause 1; key k; pause 1.5; shot b13-crisis-again
key k; pause 1.5; shot b14-closed
finish
