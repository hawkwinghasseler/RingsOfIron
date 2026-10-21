#!/usr/bin/env bash
# The developer console in a real window under Xvfb, with a screenshot at each step: ~ to open it,
# typing, Tab, history, clicks that select and cycle, Esc, dragging and resizing the window, and the
# commands acting on the selection. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/dev-console.sh [out-dir]     (default build/playtest-screens-console)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-console}
. tools/playtest/lib.sh
type_line() { xdotool type --delay 40 "$1"; pause 0.3; }
run_line() { type_line "$1"; key Return; pause 0.6; }
drag() { xdotool mousemove --window "$WIN" "$1" "$2"; pause 0.2; xdotool mousedown 1; pause 0.2
  xdotool mousemove --window "$WIN" "$3" "$4"; pause 0.3; xdotool mousemove --window "$WIN" "$5" "$6"; pause 0.2
  xdotool mouseup 1; pause 0.4; }

# A new game, docked at Highport and seated at the helm
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 12; shot c-01-start

# ~ opens the console over the game; help lists the commands
key grave; pause 1; shot c-02-open
run_line "help()"; shot c-03-help
# Typing "m" goes to the console, not the traffic map
run_line "1 + 2 * 20"; shot c-04-result
# Tab finishes a name, twice lists what fits; Up brings the last line back
type_line "rev"; key Tab; pause 0.4; shot c-05-tab
key ctrl+a BackSpace; type_line "he"; key Tab Tab; pause 0.4; shot c-06-tab-list
key ctrl+a BackSpace; key Up; pause 0.3; shot c-07-history
key ctrl+a BackSpace

# Outside, a click on the middle of the screen selects our own ship
click 640 400; pause 0.5; shot c-08-ship-selected
run_line "aboard()"; shot c-09-aboard
# One of the crew by script, then a critical hit and a second one
run_line "select(aboard(player).find { crew(it).name == 'Ada' })"
run_line "wound 95"; run_line "wound 80"; shot c-10-crit-twice
run_line "kill()"; shot c-11-killed
run_line "revive()"; run_line "heal()"

# Esc lets go of the selection, Esc again closes the console; on deck, the console again
key Escape; pause 0.5; shot c-12-deselected
key Escape; pause 0.5; key v; pause 1.5; key grave; pause 1; shot c-13-deck
# Clicks on the avatar cycle: the avatar, the tile under them, the ship
click 640 400; shot c-14-click-crew
click 640 400; shot c-15-click-tile
run_line "fire()"; pause 2; shot c-16-tile-fire
click 640 400; shot c-17-click-ship
run_line "fire_drill 2"; run_line "spawn 'medic', 'Doc'"; pause 1; shot c-18-script-and-spawn
run_line "scripts()"; run_line "pause()"; pause 1; shot c-19-paused
run_line "pause()"

# Drag the window by its title, then resize it from its lower right corner (640 x 320, 12 px in
# from the top left, so after the drag its corner is at 952, 654)
drag 300 28 450 200 600 350; shot c-20-dragged
drag 949 651 1050 700 1150 760; shot c-21-resized
run_line "help 'wound'"; shot c-22-after-resize
finish
