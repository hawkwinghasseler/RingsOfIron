#!/usr/bin/env bash
# The game's windows under a FILAMENT (VFD) HUD, in a real window under Xvfb, for review against the
# mockups (documentation/ui-overhaul/vfd): docked at Highport, the station's terminal on each of its
# tabs (the market with a trade, the yard and a hardpoint's weapons on offer, the missions with an
# offer taken, the hire); under Context, the ship on each of its tabs, with trouble aboard, the field
# refit away from the station, and boarding the raider. Keyboard only. Needs Xvfb, xdotool and
# ImageMagick's import.
#   tools/playtest/windows-vfd.sh <context|stations|task> [out-dir] [WxH] [ui-scale]
#   (default build/playtest-windows-vfd-<spec>, 1920x1200, the automatic scale)
# Run from the repository root.
set -u
SPEC=${1:-context}
OUT=${2:-$(pwd)/build/playtest-windows-vfd-$SPEC}
SIZE=${3:-1920x1200}
UI_SCALE=${4:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=vfd.$SPEC"
. tools/playtest/lib.sh

# New game: seed 2026, the Patrol, Start, by the keyboard (the menus' own focus order)
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
until_docked 180; pause 3; shot w00-docked
# The station's terminal: the market, a row down, Buy 10 walked to and pressed
key t; pause 1.2; shot w01-market
key Down; pause 0.3; key Right; pause 0.3; shot w01-market-keys
key Return; pause 1; shot w01-market-bought
# Its other tabs by the game's keys (Y, J) and a number key: the yard and a hardpoint's weapons on
# offer, the missions with the first offer taken, the hire
key y; pause 1; shot w02-yard
key Down; key Down; key Down; pause 0.4; shot w02-yard-row
key Return; pause 1; shot w02-yard-offers
key BackSpace; pause 0.6
key j; pause 1; shot w03-missions
key Return; pause 1; shot w03-mission-taken
key 4; pause 1; shot w04-hire
key Escape; pause 1; shot w04-closed
if [ "$SPEC" = context ]; then
  # The ship (M2): I opens it at the Crew the first time, and the digits are its tabs. On the
  # Systems, a gun switched off and on again, the reactor's guard armed, the engines' group opened
  key i; pause 1.2; shot w05-crew
  key 1; pause 1; shot w06-systems
  key Down; key Down; key Down; pause 0.4; key Return; pause 1; shot w06-gun-off
  key Return; pause 0.6
  key Home; pause 0.3; key Return; pause 0.5; shot w06-reactor-armed
  pause 3.5
  key Down; pause 0.3; key Right; pause 0.6; shot w06-group-open
  key Left; pause 0.4
  key 3; pause 1; shot w07-cargo
  key 4; pause 1; shot w08-loadout
  key i; pause 0.8; shot w08-closed
  # Trouble aboard: where each system is, then a gun knocked to 20%, another's tile shorted, the
  # hold's hull breached and a fire in the hold; I opens the ship at the Loadout, its last tab
  SUBS="player.getComponent(ShipSubsystemRegistryComponent).subsystems.collect { it.getComponent(SubsystemComponent) }"
  cmd dev "$SUBS.collect { it.type.name() + ' ' + it.footprint.originX + ',' + it.footprint.originY }.join('; ')"
  cmd dev "def guns = $SUBS.findAll { it.type == SubsystemComponent.SubsystemType.TURRET_MOUNT }; def a = guns[1].footprint; def b = guns[2].footprint; actions.damageTile(player, a.originX, a.originY, 0.8f) + ' / ' + actions.shortOut(player, b.originX, b.originY)"
  cmd dev "def g = player.getComponent(ShipGridComponent); def y = (0..<g.height).find { g.getTile(6, it)?.wallType == Tile.WallType.HULL }; actions.breach(player, 6, y) + ' / ' + actions.ignite(player, 6, 2)"
  pause 2
  key i; pause 1.2; shot w09-trouble-loadout
  key 1; pause 1; shot w09-trouble
  pause 6; shot w09-trouble-later
  key i; pause 0.6
  # Away from the station: Y is the crew's field refit, in the Loadout, and closes it from there
  key u; pause 5; cmd away 600; pause 3
  key y; pause 1.2; shot w10-field-refit
  key y; pause 0.8; shot w10-closed
  # Boarding (M4): the raider's reactor knocked out and the ship latched on to it. K opens it; Enter
  # strips the part in focus (the walk is on its key), a second one two rows down; ← walks the keys
  cmd disable raider; pause 3; cmd latch; pause 4
  key k; pause 1.5; shot w11-boarding
  key Return; pause 1.5; shot w11-boarding-marked
  key Down; key Down; pause 0.4; key Return; pause 1; shot w11-boarding-two
  key Left; pause 0.4; shot w11-boarding-walk
  pause 12; shot w11-boarding-across
  key k; pause 0.8; shot w11-closed
fi
finish
