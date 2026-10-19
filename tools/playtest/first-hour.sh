#!/usr/bin/env bash
# The first hour of a career, played in a real window under Xvfb with injected keys and clicks,
# with a screenshot at each step. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/first-hour.sh [out-dir]     (default build/playtest-screens)
# Run from the repository root. Flying out to the raider, knocking it out, latching on and
# docking again are stood in for by commands to PlaytestLauncher; everything else is keys.
set -u
ROOT=$(pwd)
OUT=${1:-$ROOT/build/playtest-screens}
mkdir -p "$OUT"
CMDS=$(mktemp)
export DISPLAY=${DISPLAY:-:97}
if ! xdpyinfo >/dev/null 2>&1; then
  Xvfb "$DISPLAY" -screen 0 1920x1080x24 >/dev/null 2>&1 &
  XVFB=$!
  sleep 2
fi
./gradlew :lwjgl3:runPlaytest -Pcommands="$CMDS" --console=plain -q > "$OUT/game.log" 2>&1 &
GAME=$!

pause() { timeout "$1" tail -0f /dev/null; }
for _ in $(seq 1 120); do WIN=$(xdotool search --name "^Rings of Iron$" 2>/dev/null | head -1); [ -n "$WIN" ] && break; pause 2; done
[ -z "${WIN:-}" ] && { echo "the game window never opened"; exit 1; }
pause 6
xdotool windowfocus "$WIN" 2>/dev/null

key() { for k in "$@"; do xdotool keydown "$k"; pause 0.12; xdotool keyup "$k"; pause 0.3; done; }
shifted() { xdotool keydown Shift_L; pause 0.1; key "$1"; xdotool keyup Shift_L; }
click() { xdotool mousemove --window "$WIN" "$1" "$2" click 1; pause 0.5; }
shot() { xdotool getwindowname "$WIN" >/dev/null 2>&1 || { echo "the game window closed before $1"; exit 1; }
  timeout 20 import -window "$WIN" "$OUT/$1.png"; echo "shot $1"; }
cmd() { echo "$*" >> "$CMDS"; pause 0.5; }

# Main menu, keyboard focus, settings (Continue and Load are greyed out with no saves)
shot 01-main-menu
key Down; shot 02-menu-focus
key Down Down; key Return; pause 1; shot 03-settings
key Escape; pause 1
# New game: type a seed, arrow down to the sponsors and pick one with Enter, then Start
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return; shot 04-new-game-combine
key Up; key Return; key Down Down; shot 04b-new-game-start; key Return
pause 12; shot 05-docked-start
key F1; pause 1; shot 06-controls; key Escape; pause 1
key j; pause 1; shot 07-mission-board
key Down; key Return; pause 1; shot 08-contract-taken; key Escape; pause 1
key y; pause 1; shot 09-yard; key Escape; pause 1
key i; pause 1; shot 10-crew; key Escape; pause 1
key t; pause 1; shot 11-market; key Escape; pause 1
key u; pause 3; shot 12-undocked
key e; pause 1; shot 13-helm-console; key Escape; pause 1
key v; pause 1; shot 13b-on-deck; key v; pause 1
key m; pause 1; shot 14-traffic-map; key m; pause 1
for _ in 1 2 3; do key q; done; pause 1; shot 15-nav-targets
key n; pause 1; shot 16-burn-planner; key Delete; pause 1
for _ in 1 2 3; do key q; done; key semicolon; pause 1; shot 16b-transfer-plan; key Escape; pause 1; shot 16c-plan-discarded-by-esc
# The fight: out to the raider, guns, then its reactor knocked out
cmd away 3500; pause 4; shot 17-fight
xdotool keydown space; pause 3; xdotool keyup space; shot 18-firing
cmd disable raider; pause 3; shot 19-raider-disabled
cmd latch; pause 4; shot 20-latched
key k; pause 2; shot 21-boarding
key Escape; pause 1
key l; pause 1; shot 22-notice-log; key Escape; pause 1
key u; pause 2
# Stranded: the tow from the pause menu
cmd disable player; pause 3; shot 23-disabled
key Escape; pause 1; shot 24-pause-tow; key Escape; pause 1
key b; pause 1; shot 25-tow-asked; key b; pause 4; shot 26-towed
# Docking again and the yard
cmd dock; pause 3; shot 27-redocked
key y; pause 1; shot 28-yard-repairs; key Escape; pause 1
key Escape; pause 1; key Down Down; key Return; pause 1; shot 29-save-panel; key Escape; key Escape; pause 1

kill $GAME 2>/dev/null; pkill -f PlaytestLauncher 2>/dev/null
[ -n "${XVFB:-}" ] && kill "$XVFB"
rm -f "$CMDS"
