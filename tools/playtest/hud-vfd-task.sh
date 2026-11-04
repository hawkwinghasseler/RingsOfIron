# FILAMENT x Task's own keys, for tools/playtest/hud-vfd.sh: Q picks a nav target, which brings TGT
# and FLT's frame onto it; 4 orders the helm to approach it, which puts HLM over FLT with the order
# waiting while the captain has the helm; 0 cancels it.
spec_keys() { key q; pause 2; shot v-04-nav; key 4; pause 2; shot v-04-order; key 0; pause 1; }
