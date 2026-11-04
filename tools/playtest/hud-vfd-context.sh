# FILAMENT x Context's own keys, for tools/playtest/hud-vfd.sh: Q picks a nav target, which brings
# H2's navigation rows; 7 opens the orders menu (C1) over the world, and 7 again closes it. 7 gives
# no order of its own, so the helm must not change.
spec_keys() { key q; pause 2; shot v-04-nav; key 7; pause 2; shot v-04-orders; key 7; pause 1; }
