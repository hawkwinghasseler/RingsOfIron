#!/usr/bin/env bash
# Crew out on the walkway between two docked craft, seen from the deck view, in a real window under
# Xvfb: a new game docked at Highport, the world stopped, then the avatar and a hand stood at points
# along the collars, a screenshot at each. Needs Xvfb, xdotool and ImageMagick's import.
#   tools/playtest/dock-crossing.sh [out-dir]   (default build/playtest-screens-dock-crossing)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-dock-crossing}
. tools/playtest/lib.sh

# A new game, docked at Highport. Building the world can take a minute in software GL
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause ${START_WAIT:-60}
key v; pause 2
# Zoomed in on the dock
xdotool mousemove --window "$WIN" 400 300; for _ in $(seq 1 ${ZOOM:-6}); do xdotool click 4; pause 0.3; done; pause 2
shot x-00-deck
cmd dev "pause()"
cmd dev "G = com.ringsofiron.aicrew.GridPositionComponent; S = com.ringsofiron.interiorgrid.ShipGridComponent; link = player.getComponent(S).links.first(); \"\${link.x},\${link.y} \${link.side} gap \${link.gap} open \${link.canCross()}\""
# out <name> <past> near|far: stands the avatar <past> tiles out from the player's port edge (near),
# or from the station's (far, aboard the station as walking past halfway puts them), and steps on a
# frame to draw it
out() {
  if [ "$3" = near ]; then
    cmd dev "p = avatar.getComponent(G); o = new float[2]; link.point($2 as float, 0f, o); p.setShip(player); p.x = o[0]; p.y = o[1]; p.walkway = link; 'out ' + p.x + ',' + p.y"
  else
    cmd dev "p = avatar.getComponent(G); o = new float[2]; link.mate.point($2 as float, 0f, o); p.setShip(link.otherShip); p.x = o[0]; p.y = o[1]; p.walkway = link.mate; 'out ' + p.x + ',' + p.y"
  fi
  cmd dev "step(0.02)"; pause 2; shot "$1"
}
out x-01-near 0.15 near
out x-02-quarter 0.3 near
out x-03-middle 0.48 near
out x-04-over 0.4 far
out x-05-far 0.2 far
grep 'PLAYTEST dev' "$OUT/game.log"
finish
