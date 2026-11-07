#!/usr/bin/env bash
# The game's windows under a Nasa-punk HUD, in a real window, for review against the mockups
# (documentation/ui-overhaul/variations/nasapunk-<spec>): docked at Highport, the station's market
# with a buy, its yard, missions and hire, then the yard's own key, the contracts, the crew, the
# notice log and the controls; away from the station the crew's field refit; latched to a raider
# that has surrendered, boarding before and after the party is sent; and the career over. Needs
# Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/windows-nasapunk.sh <context|task|stations> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-windows-nasapunk-<spec>, 1280x800, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-context}
OUT=${2:-$(pwd)/build/playtest-windows-nasapunk-$SPEC}
SIZE=${3:-1280x800}
UI_SCALE=${4:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=nasapunk.$SPEC"
. tools/playtest/lib.sh

# New game: seed 2026, the first sponsor, Start
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
until_docked 180; pause 3
# The station: the market and a buy on its first row, then each tab on its number
key t; pause 1.5; shot w-01-market
key Return; pause 2; shot w-02-market-bought
key 2; pause 1; shot w-03-tab-2
key 3; pause 1; shot w-04-tab-3
key 4; pause 1; shot w-05-tab-4
key Escape; pause 1
# Each window's own key
key y; pause 1.5; shot w-06-yard; key Escape; pause 1
key j; pause 1.5; shot w-07-contracts; key Escape; pause 1
key i; pause 1.5; shot w-08-crew; key Escape; pause 1
key l; pause 1.5; shot w-09-log; key Escape; pause 1
key F1; pause 1.5; shot w-10-controls; key Escape; pause 1
# Away from the station: the crew's field refit
key u; pause 8
cmd away 3000; await 30 "PLAYTEST away"; pause 3
key y; pause 1.5; shot w-11-field-refit; key Escape; pause 1
# Latched to a raider that has surrendered: boarding, then the party sent across
cmd raider 2 raider_darts; await 30 "PLAYTEST raider"; pause 2
cmd disable raider; pause 3; cmd latch; pause 4
cmd dev "world.disablement.set(game.raider, com.ringsofiron.shipops.DisabledComponent.Reason.SURRENDERED)"; pause 2
key k; pause 1.5; shot w-12-boarding
cmd dev "world.boarding.order(player)"; pause 4; shot w-13-boarding-party
key Escape; pause 1
# The career over: the ship-lost card over the deck's displays
cmd dev "world.@careerOver = 'nobody was left to bring you home'"; pause 3; shot w-14-career-over
finish
