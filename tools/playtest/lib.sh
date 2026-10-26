# Shared by the playtest scripts: starts Xvfb and the game, and gives key, click, shot and cmd.
# Source from the repository root with OUT set (the screenshot folder) and SAVES optionally set
# (a folder of saves the load menu should list).
ROOT=$(pwd)
mkdir -p "$OUT"
CMDS=$(mktemp)
export DISPLAY=${DISPLAY:-:97}
if ! xdpyinfo >/dev/null 2>&1; then
  Xvfb "$DISPLAY" -screen 0 ${XVFB_SCREEN:-1920x1080}x24 >/dev/null 2>&1 &
  XVFB=$!
  sleep 2
fi
# A window left from the last run would be taken for this one's: wait for it to close
for _ in $(seq 1 30); do xdotool search --name "^Rings of Iron$" >/dev/null 2>&1 || break; timeout 1 tail -0f /dev/null; done
xdotool search --name "^Rings of Iron$" >/dev/null 2>&1 && { echo "another game's window is open on $DISPLAY: close it first"; exit 1; }
# SIZE (2560x1440) and UI_SCALE (1.5, or 0 for auto) set the window and the UI scale
./gradlew :lwjgl3:runPlaytest -Pcommands="$CMDS" -Psaves="${SAVES:-}" ${SIZE:+-PplaytestSize=$SIZE} ${UI_SCALE:+-PuiScale=$UI_SCALE} --console=plain -q > "$OUT/game.log" 2>&1 &
GAME=$!

pause() { timeout "$1" tail -0f /dev/null; }
for _ in $(seq 1 120); do WIN=$(xdotool search --name "^Rings of Iron$" 2>/dev/null | head -1); [ -n "$WIN" ] && break; pause 2; done
[ -z "${WIN:-}" ] && { echo "the game window never opened"; exit 1; }
pause 6
xdotool windowfocus "$WIN" 2>/dev/null

key() { for k in "$@"; do xdotool keydown "$k"; pause 0.12; xdotool keyup "$k"; pause 0.3; done; }
hold() { xdotool keydown "$1"; pause "$2"; xdotool keyup "$1"; pause 0.3; }
shifted() { xdotool keydown Shift_L; pause 0.1; key "$1"; xdotool keyup Shift_L; }
click() { xdotool mousemove --window "$WIN" "$1" "$2" click 1; pause 0.5; }
shot() { xdotool getwindowname "$WIN" >/dev/null 2>&1 || { echo "the game window closed before $1"; finish; exit 1; }
  timeout 20 import -window "$WIN" "$OUT/$1.png"; echo "shot $1"; }
cmd() { echo "$*" >> "$CMDS"; pause 0.5; }
# Waits up to $1 seconds for the game log to print a line matching $2
await() { for _ in $(seq 1 "$1"); do grep -q "$2" "$OUT/game.log" && return 0; pause 1; done; echo "never saw: $2"; return 1; }
# Waits up to $1 seconds for the ship to be docked
until_docked() { for _ in $(seq 1 "$1"); do cmd docked; [ "$(grep 'PLAYTEST docked:' "$OUT/game.log" | tail -1)" = "PLAYTEST docked: yes" ] && return 0; pause 0.5; done; echo "never docked"; return 1; }
finish() {
  # Only this run's game: its command line names this run's command file
  kill $GAME 2>/dev/null; pkill -f "[P]laytestLauncher $CMDS" 2>/dev/null
  [ -n "${XVFB:-}" ] && kill "$XVFB"
  rm -f "$CMDS"
}
