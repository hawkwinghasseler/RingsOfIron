#!/usr/bin/env bash
# The game's windows under FILAMENT × Stations, in a real window under Xvfb, for review against the
# mockups (documentation/ui-overhaul/vfd, the Stations screens): docked at Highport, the Station link
# on each of its pages (the market with a trade, the contract board with an offer taken); the slate
# on each of its pages and the log; the controls card on both sides; the console panels (the helm's,
# the Tactical board, a gun's, Engineering, then again with trouble aboard); away from the station,
# the crew's field refit and the contracts held; the Plot with a burn planned; boarding the raider;
# and the ship lost. Keyboard only, the consoles reached by the dev console's teleport. Needs Xvfb,
# xdotool and ImageMagick's import.
#   tools/playtest/windows-vfd-stations.sh [out-dir] [WxH] [ui-scale]
#   (default build/playtest-windows-vfd-stations, 1920x1200, the automatic scale)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-windows-vfd-stations}
SIZE=${2:-1920x1200}
UI_SCALE=${3:-}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=vfd.stations"
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
until_docked 180; pause 3; shot s00-docked
# The Station link: T opens it at the Market; a row down, Buy (Enter) takes one. Tab steps the other
# pages Highport offers: the Yard across the screen, the Board, Hiring, the Infirmary (it has no
# shipyard, so that key is blank)
key t; pause 1.2; shot s01-market
key Down; pause 0.3; key Return; pause 1; shot s01-market-bought
key Tab; pause 0.8; shot s02-yard
key Tab; pause 0.8; shot s03-board
key Tab; pause 0.8; shot s04-hiring
key Tab; pause 0.8; shot s05-infirmary
key Escape; pause 0.8; shot s05-closed
# J turns it to the Board; Enter takes the offer in focus; J closes it from there
key j; pause 1; shot s07-board
key Return; pause 1; shot s07-board-taken
key j; pause 0.8
# The slate: I opens it at the Crew, and Tab steps its pages (Medical, EVA, Contracts with the one
# taken, Career, the Log, the Ship); L turns to the Log and closes it from there
key i; pause 1.2; shot s10-crew
key Tab; pause 0.8; shot s11-medical
key Tab; pause 0.8; shot s12-eva
key Tab; pause 0.8; shot s13-contracts
key Tab; pause 0.8; shot s14-career
key Tab; pause 0.8; shot s15-log
key Tab; pause 0.8; shot s16-ship
key l; pause 0.8; shot s17-to-log
key l; pause 0.8; shot s17-closed
# The controls card: F1 lays it over the screen at the Keyboard, the keys for where the captain is
# first; Pg Dn turns to the rest, Tab to the Gamepad; F1 takes it away
key F1; pause 1.2; shot s20-controls
key Next; pause 0.6; shot s20-controls-more
key Prior; pause 0.3
key Tab; pause 0.8; shot s21-gamepad
key F1; pause 0.8
# The consoles, each reached by a teleport once V has stood the captain up from the helm: the helm's
# panel (Helm, Navigation, Comms), the Tactical board (Contacts, Weapons, Defense), a gun's panel,
# Engineering at the reactor. E opens each and closes it
key v; pause 1.5
beside helm; key e; pause 1.2; shot s30-helm
key Tab; pause 0.8; shot s30-helm-navigation
key e; pause 0.8
beside weapons; key e; pause 1.2; shot s31-tactical
key Tab; pause 0.8; shot s31-tactical-weapons
key Tab; pause 0.8; shot s31-tactical-defense
key e; pause 0.8
beside turret_mount; key e; pause 1.2; shot s32-gun
key e; pause 0.8
beside reactor; key e; pause 1.2; shot s33-engineering
key e; pause 0.8
# Trouble aboard: the hold's hull breached, and once its pressure falls (the PRESSURE caution) E at
# the reactor opens on the Damage page; everything is mended after
cmd dev "def g = player.getComponent(ShipGridComponent); def y = (0..<g.height).find { g.getTile(6, it)?.wallType == Tile.WallType.HULL }; actions.breach(player, 6, y)"
pause 6
key e; pause 1.2; shot s33-engineering-trouble
key e; pause 0.8
cmd dev "repair(player); air(player)"; pause 1
# Away from the station, back at the helm (V sits the captain there; U undocks only from it): Y is
# the crew's field refit on the link; J opens the slate at the Contracts, where there is no board
beside helm; key v; pause 1.5
key u; pause 5; cmd away 600; pause 3
key y; pause 1.2; shot s40-field-refit
key y; pause 0.8
key j; pause 1.2; shot s41-contracts-away
key j; pause 0.8
# The Plot: M opens it at the Plan round the game's orbit canvas; N plans a burn and ↑ adds
# prograde; Tab steps to the Targets and the Map; M closes it
key m; pause 1.5; shot s50-plot
key n; pause 1; key Up; key Up; key Up; pause 1; shot s50-plot-burn
key Tab; pause 0.8; shot s51-targets
key Tab; pause 0.8; shot s52-map
key m; pause 0.8; shot s52-closed
# Boarding: the raider's reactor knocked out and the ship latched on to it; K opens the slate at
# Boarding
cmd disable raider; pause 3; cmd latch; pause 4
key k; pause 1.5; shot s60-boarding
key k; pause 0.8
# The ship lost: the HUD goes dark under the lost card, which names the game's keys
cmd dev "def k = GameWorld.Crash.getDeclaredConstructor(String, Double.TYPE); k.accessible = true; def f = GameWorld.getDeclaredField('crash'); f.accessible = true; f.set(world, k.newInstance('Luna', world.engine.simSeconds as double)); 'crashed'"
pause 2; shot s70-lost
finish
