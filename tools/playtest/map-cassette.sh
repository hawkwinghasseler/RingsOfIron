#!/usr/bin/env bash
# The map's panels in the Yard Standard (Context's M1) under a Cassette HUD, in a real window under
# Xvfb: undocked from Highport in low orbit, the map up with the plan, the target and traffic, the view
# row, the layers and the timeline round the game's map; a burn planned and raised (N, Up); a transfer
# to the lunar mine planned and accepted; then, at 1280x800, the panels' own controls by the mouse:
# CENTRE, ALL TRAFFIC, a contact made the nav target from the list, a layer switched off, the sort
# turned, the zoom, and every burn cleared under its guard. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/map-cassette.sh <task|stations|context> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-map-cassette-<spec>, 1280x800, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-context}
OUT=${2:-$(pwd)/build/playtest-map-cassette-$SPEC}
SIZE=${3:-1280x800}
UI_SCALE=${4:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=cassette.$SPEC"
. tools/playtest/lib.sh

# New game: seed 2026, the Patrol, Start, by the keyboard (the menus' own focus order)
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3
key u; pause 4
cmd away 3000; pause 3

# The map: low orbit, nothing planned
key m; pause 2; shot m01-map
# A burn planned ahead of the ship and raised prograde by the game's keys
key n; pause 1; shot m02-burn-planned
for _ in $(seq 1 12); do key Up; done
pause 1; shot m03-burn-raised
# A transfer to the lunar mine: planned, shown as a proposal, accepted
cmd target shackleton; pause 1
key semicolon; pause 2; shot m04-transfer-proposed
key Return; pause 2; shot m05-transfer-accepted

if [ "$SIZE" = "1280x800" ] && [ -z "$UI_SCALE" ]; then
  # The view row: centre on the ship, then everything in view
  click 356 15; pause 1; shot m06-centre-ship
  click 617 15; pause 1.5; shot m07-all-traffic
  # The first contact in the list (the nearest the ship) made the nav target
  click 1100 359; pause 1.5; shot m08-contact-picked
  # Stations off, the list by kind, the zoom in twice
  click 1029 680; pause 1; shot m09-stations-off
  click 1029 680; pause 0.5
  click 1222 305; pause 1; shot m10-sorted-by-kind
  click 949 15; click 949 15; pause 1; shot m11-zoomed
  # Clear all burns: the first press lifts the guard, the second clears
  click 228 680; pause 0.6; shot m12-guard-lifted
  click 228 680; pause 1.5; shot m13-cleared
fi

key Escape; pause 1; shot m14-closed
finish
