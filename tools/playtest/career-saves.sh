#!/usr/bin/env bash
# Joe's way in: `runGame -PcareerSaves=<folder>` copies a career's saves into the game's own, then the
# main menu's Load lists them. Run twice, the second run replaces the first one's copies (and only
# those). Under Xvfb, with a shot of the menu and of Load each time. Needs Xvfb, xdotool and import.
#   tools/playtest/career-saves.sh [saves-folder] [out-dir]
# The folder defaults to core/build/playtest-saves/seed-2026 (./gradlew :core:slowTest --tests
# '*Round5CareerTest'); part 5's sweep writes one per seed. The copy goes into assets/saves, as Joe's
# does, and its console line ("copied ..., replaced ..., kept ...") is in game.log.
# Run from the repository root.
set -u
FROM=${1:-$(pwd)/core/build/playtest-saves/seed-2026}
OUT=${2:-$(pwd)/build/playtest-career-saves}
# The main menu's Load button, at 1280x800
LOAD_X=${LOAD_X:-640}; LOAD_Y=${LOAD_Y:-473}
mkdir -p "$OUT"
export DISPLAY=${DISPLAY:-:97}
if ! xdpyinfo >/dev/null 2>&1; then Xvfb "$DISPLAY" -screen 0 1920x1080x24 >/dev/null 2>&1 & XVFB=$!; sleep 2; fi
pause() { timeout "$1" tail -0f /dev/null; }

for run in 1 2; do
  ./gradlew :lwjgl3:runGame -PcareerSaves="$FROM" --console=plain -q > "$OUT/game-$run.log" 2>&1 &
  GAME=$!
  WIN=""
  for _ in $(seq 1 120); do WIN=$(xdotool search --name "^Rings of Iron$" 2>/dev/null | head -1); [ -n "$WIN" ] && break; pause 2; done
  [ -z "$WIN" ] && { echo "the game window never opened"; kill $GAME; exit 1; }
  pause 6
  grep "Career saves" "$OUT/game-$run.log"
  import -window "$WIN" "$OUT/s$run-01-menu.png"
  xdotool mousemove --window "$WIN" "$LOAD_X" "$LOAD_Y" click 1; pause 2
  import -window "$WIN" "$OUT/s$run-02-load.png"
  kill $GAME 2>/dev/null; pkill -f "[G]ameLauncher --career-saves" 2>/dev/null
  for _ in $(seq 1 30); do xdotool search --name "^Rings of Iron$" >/dev/null 2>&1 || break; pause 1; done
done
[ -n "${XVFB:-}" ] && kill "$XVFB"
