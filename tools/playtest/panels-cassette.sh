#!/usr/bin/env bash
# The Stations spec's own panels (section 10) under the Cassette Stations HUD, in a real window under
# Xvfb: docked at Highport and seated at the helm, the Station link on its market (a row down, the
# quantity stepped to 10 and bought), its yard across the screen and the map, the guide card over them
# at the top right, the contract board (an offer taken) and the hiring hall; the slate on the crew, the
# hold, the contracts held and the log; undocked once the loaders are done, the contracts and the
# refit; a raider disabled and latched to, the slate's boarding tab; then on deck, the slate the full
# height, I opening it on its last tab. Keyboard only.
# Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/panels-cassette.sh [out-dir] [WxH]
#   (default build/playtest-panels-cassette, 1280x800)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-panels-cassette}
SIZE=${2:-1280x800}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=cassette.stations"
. tools/playtest/lib.sh

# New game: seed 2026, the Patrol, Start, by the keyboard (the menus' own focus order)
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3; shot p00-docked
# The Station link: the market, a row down, the quantity stepped to 10 (Left twice from BUY) and bought
key t; pause 1.2; shot p01-market
key Down; pause 0.3; shot p01-market-row
key Left; pause 0.3; key Left; pause 0.3; shot p01-market-qty-focus
key Return; pause 0.4; shot p01-market-qty10
key Return; pause 1.2; shot p01-market-bought
# Its other tabs by the game's keys and Tab: the yard (across the screen, as the map is), the board, the
# hiring hall
key y; pause 1.2; shot p02-yard
key Escape; pause 1; key m; pause 1.5; shot p02-map
key Escape; pause 1
key j; pause 1.2; shot p03-board
key Return; pause 1; shot p03-board-taken
key Tab; pause 0.6; shot p04-hiring
key Down; pause 0.3; shot p04-hiring-row
key Escape; pause 1
# The slate: the crew, a row down, the hold, the contracts held, the log
key i; pause 1.2; shot p05-crew
key Down; pause 0.3; shot p05-crew-row
key Tab; pause 0.6; shot p05-cargo
key Tab; pause 0.6; shot p05-contracts
key l; pause 1.2; shot p06-log
key Escape; pause 1
# Away from the station, once the loaders are done (U waits for them): the contracts and the refit
for _ in $(seq 1 30); do key u; pause 2; cmd docked; pause 0.5; grep -q '^PLAYTEST docked: no$' "$OUT/game.log" && break; done
pause 2; shot p09-undocked
key j; pause 1.2; shot p10-contracts-away
key y; pause 1.2; shot p10-refit
key Escape; pause 1
# A raider, disabled and latched to: the slate's boarding tab, an engine the link can't reach selected
cmd away 3500; pause 3
cmd disable raider; pause 3
cmd latch; pause 4; shot p12-latched
key k; pause 1.5; shot p13-boarding
key Down; key Down; key Down; pause 0.4; shot p13-boarding-row
key Escape; pause 1
# On deck: V stands up from the helm, and the slate runs the full height. I opens it on its last tab
# (boarding); Shift+Tab twice goes back to the crew
key v; pause 2; key k; pause 1.5; shot p14-deck-boarding
key Escape; pause 1; key i; pause 1.2; shot p14-deck-last-tab
shifted Tab; pause 0.5; shifted Tab; pause 0.8; shot p14-deck-crew
key l; pause 1.2; shot p14-deck-log
key Escape; pause 1; shot p15-closed
finish
