#!/usr/bin/env bash
# The debug menu (F3) in a real window under Xvfb, with a screenshot at each step: opening it over a new
# game, each built-in overlay on, a scenario loaded from its button, and the console's commands for the
# same. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/debug-menu.sh [out-dir]     (default build/playtest-screens-debug-menu)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-debug-menu}
. tools/playtest/lib.sh
type_line() { xdotool type --delay 40 "$1"; pause 0.3; }
run_line() { type_line "$1"; key Return; pause 0.6; }

# A new game, docked at Highport and seated at the helm
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12; shot d-01-start

# F3 opens the menu by the right edge: scenarios, overlays, the world
key F3; pause 1; shot d-02-menu
# Each overlay from the console, which the menu's boxes follow
key grave; pause 1
run_line "overlay 'grid'"; run_line "overlay 'velocity'"; run_line "overlay 'stats'"
key Escape; pause 0.5; shot d-03-grid-velocity-stats
key grave; pause 1; run_line "overlay 'bodies'"; key Escape; pause 0.5; shot d-04-bodies
key grave; pause 1; run_line "overlay 'grid', 'off'"; run_line "overlay 'velocity', 'off'"; run_line "overlay 'bodies', 'off'"
run_line "scenarios()"; shot d-05-scenarios
# A scenario from the console: the game is thrown away and the scenario starts
run_line "scenario 'free-flight'"; pause 12; shot d-06-free-flight
key F3; pause 1; shot d-07-menu-in-scenario
key grave; pause 1; run_line "overlay 'velocity'"; run_line "overlay 'bodies'"; key Escape; pause 0.5
key F3; pause 0.5; key F3; pause 0.5; shot d-08-velocity-bodies-in-scenario
# Its button in the menu: New career, under the scenarios
click 940 194; pause 12; shot d-09-new-career
finish
