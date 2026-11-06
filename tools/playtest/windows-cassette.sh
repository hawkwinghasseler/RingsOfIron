#!/usr/bin/env bash
# The game's windows in the Yard Standard (documentation/ui-overhaul/cassette) under a Cassette HUD,
# in a real window under Xvfb: docked at Highport, the station's console on each of its tabs (the
# market with a trade, the yard, the missions with an offer taken, the hire), the ship's (the crew),
# the record (the log and the contract held) and the controls cards; undocked, the loadout and the
# contract; then out to a raider, disabled and latched, the boarding console and the latched card;
# the map with a console opened over it; and the career over, killed aboard, as the telex. Keyboard only. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/windows-cassette.sh <task|stations|context> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-windows-cassette-<spec>, 1280x800, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-context}
OUT=${2:-$(pwd)/build/playtest-windows-cassette-$SPEC}
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
pause 3; shot w00-docked
# The station's console: the market, a row down, Buy 10 on it
key t; pause 1.2; shot w01-market
key Down; pause 0.3; key Right; key Right; pause 0.3; shot w01-market-keys
key Return; pause 1; shot w01-market-bought
# Its other tabs by the game's keys (Y, J) and a number key: the yard, the missions with the first
# offer taken, the hire (Highport sells no ships: its tabs are the market, yard, missions and hire)
key y; pause 1; shot w02-yard
key Down; key Down; key Down; pause 0.4; shot w02-yard-row
key j; pause 1; shot w03-missions
key Return; pause 1; shot w03-mission-taken
key 4; pause 1; shot w04-hire
key Escape; pause 1; shot w04-closed
# The ship's console (the crew), the record (the log, its cautions, the contract held), the controls cards
key i; pause 1.2; shot w05-crew
key Down; key Down; pause 0.4; shot w05-crew-row
key Escape; pause 1
key l; pause 1.2; shot w06-log
key Right; pause 0.5; shot w06-log-cautions
key 1; pause 1; shot w07-contracts
key Escape; pause 1
key F1; pause 1.2; shot w08-controls-docked
key Right; pause 0.5; shot w08-controls-next
key 1; pause 0.5; shot w08-controls-helm
key Escape; pause 1
# Away from the station: the loadout in the field and the contract held
key u; pause 4; shot w09-undocked
key y; pause 1.2; shot w10-loadout
key Escape; pause 1
key j; pause 1.2; shot w11-contracts-away
key Escape; pause 1
# A raider, disabled and latched to: the boarding console, then the latched controls card
cmd away 3500; pause 3
cmd disable raider; pause 3
cmd latch; pause 4; shot w12-latched
key k; pause 1.5; shot w13-boarding
key Down; key Down; key Down; key Down; key Down; pause 0.4; shot w13-boarding-row
key Escape; pause 1
key F1; pause 1.2; shot w14-controls-latched
key Escape; pause 1
# The map up, then the crew's console over it: the console takes the screen
key m; pause 1.5; shot w15-map
key i; pause 1.2; shot w15-crew-over-map
key Escape; pause 1
# The career over: everyone aboard killed, the telex prints
cmd dev "kill(player)"; pause 2; shot w16-telex-printing
pause 3; shot w16-telex
finish
