#!/usr/bin/env bash
# RCS thrust in a real window under Xvfb: a new game, the ship put 300 m off Highport, a strafe each
# way at the helm with every block whole, then again with its starboard strafe block amidships
# wrecked (the speed off Highport read after each). Screenshots throughout. Needs Xvfb, xdotool and
# ImageMagick's import.
#   tools/playtest/rcs.sh [out-dir]     (default build/playtest-screens-rcs)
# Run from the repository root.
set -u
OUT=${1:-$(pwd)/build/playtest-screens-rcs}
. tools/playtest/lib.sh
V='com.badlogic.gdx.math.Vector2'
P='com.ringsofiron.physicsexterior.PhysicsManager'
G='com.ringsofiron.interiorgrid.ShipGridComponent'
# The ship's speed off Highport, m/s
speed() { cmd dev "$P.getVelocity(world.docking.bodyOf(player), new $V()).sub($P.getVelocity(world.docking.bodyOf(world.engine.getEntity(game.highport)), new $V())).len()"; }
still() { cmd away 300; pause 0.5; }

# A new game, docked at Highport and seated at the helm
key Down; key Return; pause 1
key 2 0 2 6; key Down Down; key Return
key Up; key Return; key Down Down; key Return
pause 40
# Undocked, and the view zoomed in on the ship
key u; pause 4
xdotool mousemove --window "$WIN" 640 400; for _ in 1 2 3 4 5 6 7 8 9 10; do xdotool click 4; pause 0.1; done
cmd target Highport
still; pause 1; shot r-01-whole

# Strafe each way with every block whole
xdotool keydown z; pause 1.5; shot r-02-strafe-z-whole; pause 1.5; xdotool keyup z; speed
still; xdotool keydown c; pause 1.5; shot r-03-strafe-c-whole; pause 1.5; xdotool keyup c; speed
# A turn: the corner quads fire, their jets a little over half the size of a linear block's
still; xdotool keydown a; pause 0.5; shot r-03-turn; xdotool keyup a; pause 2

# Wreck the hull tile under the starboard strafe block amidships (it fires up out of the top hull)
cmd dev "player.getComponent($G).getTile(10, 5).setIntegrity(0f); player.getComponent($G).gridDirty = true"
still; pause 1; shot r-04-wrecked
xdotool keydown z; pause 1.5; shot r-05-strafe-z-wrecked; pause 1.5; xdotool keyup z; speed
still; xdotool keydown c; pause 1.5; shot r-06-strafe-c-wrecked; pause 1.5; xdotool keyup c; speed

pause 1
grep 'PLAYTEST \(dev\|target\)' "$OUT/game.log"
finish
