#!/usr/bin/env bash
# The game's windows under FILAMENT × Task, in a real window under Xvfb, for review against the
# mockups (documentation/ui-overhaul/vfd, the Task screens): docked at Highport, the station terminal
# on each of its tabs (the market with a trade, the contracts with an offer taken); the ship's book on
# each of its tabs; the controls card on both sides and over the book; the consoles' panels (the
# reactor's, life support's, a gun's), one closed by walking away, the helm and the weapons console
# sat at, life support again with the hold breached; away from the station, the crew's field refit
# and the contracts held; the map with a burn planned and the card over it; the boarding panel; and
# the ship lost. Keyboard only, the consoles reached by the dev console's teleport. Needs Xvfb,
# xdotool and ImageMagick's import.
#   tools/playtest/windows-vfd-task.sh [out-dir] [WxH] [ui-scale]
#   (default build/playtest-windows-vfd-task, 1920x1200, the automatic scale)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-windows-vfd-task}
SIZE=${2:-1920x1200}
UI_SCALE=${3:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=vfd.task"
. tools/playtest/lib.sh

# Puts the captain at the console with id $1 (helm, weapons, turret_mount, reactor, life_support):
# on its own tile, where E reaches it before any other console and before a suit locker (E is a
# locker's when the locker is as near: the helm is by the airlock's); else on an open tile round it
# where it is the nearest console, out of a locker's reach if there is one
beside() {
  cmd dev "def r = null; try { def cs = player.getComponent(ShipSubsystemRegistryComponent).subsystems.collect { it.getComponent(com.ringsofiron.uihud.components.InteractableConsoleComponent) }.findAll { it != null }; def me = cs.find { it.consoleId == '$1' }; def g = player.getComponent(ShipGridComponent); def lockers = com.ringsofiron.interiorgrid.SuitLockers.all(g).toArray().toList(); def d2 = { c, int x, int y -> (c.tileX - x) * (c.tileX - x) + (c.tileY - y) * (c.tileY - y) }; def ok = { int x, int y -> def t = g.getTile(x, y); t != null && t.isOpen() && cs.every { it.is(me) || d2(it, x, y) > d2(me, x, y) } }; def free = { int x, int y -> lockers.every { !com.ringsofiron.interiorgrid.SuitLockers.reaches(it, (float) (x + 0.5), (float) (y + 0.5)) } }; def ps = [[0, 1], [0, -1], [1, 0], [-1, 0], [1, 1], [1, -1], [-1, 1], [-1, -1]].collect { [me.tileX + it[0], me.tileY + it[1]] }; def o = ok(me.tileX, me.tileY) ? [me.tileX, me.tileY] : ps.find { ok(it[0], it[1]) && free(it[0], it[1]) }; if (o == null) o = ps.find { ok(it[0], it[1]) }; r = o == null ? 'nowhere beside $1' : teleport(o[0], o[1], avatar) } catch (e) { r = 'beside $1 failed: ' + e }; r"
  pause 1.2
}

# New game: seed 2026, the Patrol, Start, by the keyboard (the menus' own focus order)
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
until_docked 180; pause 3; shot t00-docked
# The station terminal: T opens it at the Market; a row down, Buy (Enter) takes one. Tab steps the
# other tabs Highport offers: the Yard, the Crew for hire, the Contracts (it has no shipyard, so that
# key is blank and Tab passes it)
key t; pause 1.2; shot t01-market
key Down; pause 0.3; key Return; pause 1; shot t01-market-bought
key Tab; pause 0.8; shot t02-yard
key Tab; pause 0.8; shot t03-hire
key Tab; pause 0.8; shot t04-contracts
key Escape; pause 0.8; shot t04-closed
# J turns it to the Contracts; Enter takes the offer in focus; J closes it from there
key j; pause 1; shot t05-contracts
key Return; pause 1; shot t05-contracts-taken
key j; pause 0.8
# The ship's book: I opens it at the Crew, and Tab steps its tabs (the Contracts with the one taken,
# the Career, the Log, the Ship); L turns to the Log and closes it from there
key i; pause 1.2; shot t10-crew
key Tab; pause 0.8; shot t11-contracts
key Tab; pause 0.8; shot t12-career
key Tab; pause 0.8; shot t13-log
key Tab; pause 0.8; shot t14-ship
key l; pause 0.8; shot t15-to-log
key l; pause 0.8; shot t15-closed
# The controls card: F1 lays it over the screen at the Keyboard, the keys for where the captain is
# first; Pg Dn turns to the rest, Tab to the Gamepad; F1 takes it away. Over the book, Esc takes the
# card away and leaves the book, which I closes
key F1; pause 1.2; shot t20-controls
key Next; pause 0.6; shot t20-controls-more
key Prior; pause 0.3
key Tab; pause 0.8; shot t21-gamepad
key F1; pause 0.8
key i; pause 1; key F1; pause 1.2; shot t22-controls-over-book
key Escape; pause 0.8; shot t22-back-to-book
key i; pause 0.8
# The consoles' panels, each reached by a teleport once V has stood the captain up from the helm:
# E opens the reactor's beside it, life support's and a gun's; E closes each
key v; pause 1.5
beside reactor; key e; pause 2.2; shot t30-reactor
key e; pause 0.8
beside life_support; key e; pause 1.2; shot t31-life-support
key e; pause 0.8
beside turret_mount; key e; pause 1.2; shot t32-gun
key e; pause 0.8
# A walking key shuts the panel and the walk goes on
beside reactor; key e; pause 1.2; hold d 0.6; pause 0.8; shot t33-walked-away
# E at the helm and the weapons console sits the captain there, with no panel; V stands
beside helm; key e; pause 1.5; shot t34-helm-sits
key v; pause 1.5
beside weapons; key e; pause 1.5; shot t35-weapons-sits
key v; pause 1.5
# Trouble aboard: the hold's hull breached; once its air goes, life support's panel says which room
# is venting; everything is mended after
cmd dev "def g = player.getComponent(ShipGridComponent); def y = (0..<g.height).find { g.getTile(6, it)?.wallType == Tile.WallType.HULL }; actions.breach(player, 6, y)"
pause 6
beside life_support; key e; pause 1.2; shot t36-life-support-venting
key e; pause 0.8
cmd dev "repair(player); air(player)"; pause 1
# Away from the station, back at the helm (V sits the captain there; U undocks only from it): Y is
# the crew's field refit on the terminal; J opens the book at the Contracts, where there is no board
beside helm; key v; pause 1.5
key u; pause 5; cmd away 600; pause 3
key y; pause 1.2; shot t40-field-refit
key y; pause 0.8
key j; pause 1.2; shot t41-contracts-away
key j; pause 0.8
# The map: M opens it round the game's orbit canvas; N plans a burn and ↑ adds prograde; F1 lays the
# card over it and Esc takes the card away; M closes it
key m; pause 1.5; shot t50-map
key n; pause 1; key Up; key Up; key Up; pause 1; shot t50-map-burn
key F1; pause 1.2; shot t51-controls-over-map
key Escape; pause 0.8; shot t51-back-to-map
key m; pause 0.8; shot t51-closed
# Boarding: the raider's reactor knocked out and the ship latched on to it; K opens the boarding
# panel down the right
cmd disable raider; pause 3; cmd latch; pause 4
key k; pause 1.5; shot t60-boarding
key k; pause 0.8
# The ship lost: the HUD goes dark under the lost card, which names the game's keys
cmd dev "def k = GameWorld.Crash.getDeclaredConstructor(String, Double.TYPE); k.accessible = true; def f = GameWorld.getDeclaredField('crash'); f.accessible = true; f.set(world, k.newInstance('Luna', world.engine.simSeconds as double)); 'crashed'"
pause 2; shot t70-lost
finish
