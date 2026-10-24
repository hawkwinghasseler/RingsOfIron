#!/usr/bin/env bash
# A tour of the HUD and menus for reviewing the UI: the main menu, settings, the docked start, the
# mission board, open flight, a raider's missiles inbound, the traffic map, the controls and the
# notice log, and the deck view. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/ui-tour.sh [out-dir] [WxH] [ui-scale]   (default build/playtest-screens-ui, 1280x800, auto)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-ui}
SIZE=${2:-}
UI_SCALE=${3:-}
# A screen big enough for the window, when Xvfb has to be started
[ -n "$SIZE" ] && XVFB_SCREEN=$SIZE
. tools/playtest/lib.sh

shot u-01-main-menu
key Down Down Down; key Return; pause 1; shot u-02-settings
key Escape; pause 1
# New game: seed 2026, the first sponsor, Start
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12; shot u-03-docked-start
key j; pause 1; shot u-04-mission-board; key Escape; pause 1
key u; pause 4; shot u-05-undocked
cmd away 3000; pause 3; shot u-06-flight
# A raider with dart racks 2 km out, hunting us, until its darts are in the air
cmd raider 2 raider_darts; pause 2; key Tab; pause 1; shot u-07-raider
pause 14; shot u-08-missiles
pause 4; shot u-09-missiles-later
key m; pause 1; shot u-10-traffic-map; key m; pause 1
key F1; pause 1; shot u-11-controls; key Escape; pause 1
key l; pause 1; shot u-12-notice-log; key Escape; pause 1
key v; pause 1; shot u-13-deck; key v; pause 1
finish
