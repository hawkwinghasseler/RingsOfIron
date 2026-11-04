# FILAMENT x Stations' own keys, for tools/playtest/hud-vfd.sh: Alt with a key works the MFDs
# (functional-stations 16.2). Alt+3 puts TAC's first page on the right MFD, Alt+1 the helm's, and
# Shift+Alt+2 NAV on the left. Alt with a digit must not give the helm's order for that digit (#266).
alt() { xdotool keydown Alt_L; pause 0.1; key "$1"; xdotool keyup Alt_L; pause 0.2; }
spec_keys() { alt 3; alt 1; xdotool keydown Shift_L; alt 2; xdotool keyup Shift_L; pause 2; shot v-04-mfd-keys; }
