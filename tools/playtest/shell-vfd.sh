#!/usr/bin/env bash
# The menus in setup mode (documentation/ui-overhaul/vfd/shell-*.jpg) and the map on its dot panel
# under a FILAMENT HUD, in a real window under Xvfb: the main menu with no saves, new game, settings,
# then a game paused, its save and load menus, the map, the main menu again with the save to continue,
# and the save continued with a script mod put in place, so the game asks about its scripts. Keyboard
# only, so the menus' own layout doesn't matter. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/shell-vfd.sh <task|stations|context> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-shell-vfd-<spec>, 1280x800, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-context}
OUT=${2:-$(pwd)/build/playtest-shell-vfd-$SPEC}
SIZE=${3:-1280x800}
UI_SCALE=${4:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=vfd.$SPEC"
# A mod with a script, put in place for the last game and taken away after the run: the game asks
# before it runs it (the shell's Esc pauses rather than answering, so the dialog is the run's last shot)
MODS=assets/mods
MOD=$MODS/playtest-filament-script
[ -d "$MODS" ] && MODS_WERE=1 || MODS_WERE=
mod() {
  mkdir -p "$MOD"
  printf 'id = "playtest_filament"\nname = "Playtest scripts"\nversion = "1.0.0"\nauthor = "the playtest"\n' > "$MOD/mod.toml"
  printf '// Never run: the run ends with the game still asking\n' > "$MOD/init.groovy"
}
unmod() { rm -rf "$MOD"; [ -z "$MODS_WERE" ] && rmdir "$MODS" 2>/dev/null; return 0; }
trap unmod EXIT
. tools/playtest/lib.sh

pause 2; shot s01-main
key Down; pause 0.5; shot s01-main-focus
# New game, then back
key Return; pause 1; shot s02-new-game
key Down; pause 0.4; shot s02-new-game-focus
key Escape; pause 1
# Settings: New game, First play, Settings (Continue and Load are barred, so the focus skips them)
key Down; key Down; key Down; pause 0.3; key Return; pause 1.2; shot s06-settings
key Down; key Down; pause 0.4; shot s06-settings-focus
key Down; key Down; key Down; key Down; pause 0.4; shot s06-settings-choice
key Down; key Down; key Down; key Down; pause 0.4; shot s06-settings-keys
key Escape; pause 1
# A new career: New game, seed 2026, Start (the seed takes the typing; Down past the two sponsors)
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; shot s02-new-game-typed; key Return
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3
key Escape; pause 1.2; shot s03-pause
# Save: Down to Save (Resume is first), Enter; the name field takes the typing, Down to the Save button
key Down; key Down; pause 0.3; key Return; pause 1; shot s05-save
key Down; pause 0.3; key Return; pause 1.2; shot s03-pause-saved
# Load: the saves on the list
key Down; key Down; key Down; pause 0.3; key Return; pause 1; shot s04-load
key Down; pause 0.4; shot s04-load-focus
key Escape; pause 1
# Back to the game, the map, then quit to the menu: Continue lit now
key Escape; pause 1.5
key m; pause 2; shot s09-map; key m; pause 1
key Escape; pause 1
key Up; key Up; pause 0.3; shot s03-pause-quit-focus
key Return; pause 2; shot s01-main-saves
# Continue, with a script mod in place now: the game asks over itself before it runs the scripts
mod
answers() { grep -c '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log"; }
was=$(answers)
key Down; pause 0.3; key Return
for _ in $(seq 1 60); do cmd docked; [ "$(answers)" -gt "$was" ] && break; pause 1; done
pause 3; shot s07-mods
finish
