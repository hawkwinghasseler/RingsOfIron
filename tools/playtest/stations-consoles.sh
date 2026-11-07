#!/usr/bin/env bash
# The Stations HUD's console panels (spec 10.2) on the starter corvette docked at Highport: stood up on
# deck, the captain put at each console in turn and E pressed. The reactor opens Engineering on Power, the
# reactor selected; Tab steps the tabs; walking off shuts it. Life support opens it on Atmos; E shuts it.
# A gun opens the gun panel; Enter works its switch; its second key takes the gun in hand. The helm
# opens the helm panel, the weapons console the tactical board, where Enter holds fire.
# Run from the repository root: stations-console-check.sh <out-dir> [size]
set -u
OUT=${1:?out dir}
SIZE=${2:-1920x1080}
XVFB_SCREEN=$SIZE
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhud.variant=cassette.stations"
. tools/playtest/lib.sh
pause 2
key Down; pause 0.3; key Return; pause 1
key ctrl+a; key 2 0 2 6; key Down; key Down; key Down; pause 0.3; key Return
for _ in $(seq 1 60); do cmd docked; grep -q '^PLAYTEST docked: \(yes\|no\)$' "$OUT/game.log" && break; pause 1; done
pause 3
cmd seat; cmd captain; pause 0.5
key v; pause 2.5; shot s00-deck
# The reactor: Engineering on Power, the reactor's row selected
cmd dev teleport\(13, 3, avatar\); pause 1.2; hold d 0.5; pause 0.8; cmd captain; shot s01-reactor-prompt
key e; pause 1.5; shot s02-power
key Return; pause 0.6; shot s03-power-guard
key Down; pause 0.5
key Tab; pause 0.8; shot s04-atmos
key Tab; pause 0.8; shot s05-damage
key Tab; pause 0.8; shot s06-systems
key Tab; pause 0.8; shot s07-tanks
key Tab; pause 0.8; shot s08-round
# Walking off shuts it
hold a 1.2; pause 1; cmd captain; shot s09-walked-off
# Life support: Atmos, its unit selected; E shuts it
cmd dev teleport\(9, 2, avatar\); pause 1.2; hold s 0.2; pause 0.8; cmd captain; shot s10-life-prompt
key e; pause 1.5; shot s11-atmos-unit
key e; pause 1; shot s12-e-shut
# The port gun: the gun panel; Enter its switch; then take it
cmd dev teleport\(13, 4, avatar\); pause 1.2; cmd captain; shot s13-gun-prompt
key e; pause 1.5; shot s14-gun-panel
key Return; pause 0.8; shot s15-gun-off
key Return; pause 0.8
key Right; key Return; pause 2.5; cmd seat; shot s16-gun-taken
key v; pause 2.5; cmd seat
# The helm: the helm panel on Helm; Tab through Navigation, Comms, Cargo; E shuts it
cmd dev teleport\(10, 4, avatar\); pause 1.2; hold a 0.15; pause 0.8; cmd captain; shot s17-helm-prompt
key e; pause 1.5; shot s18-helm
key Tab; pause 0.8; shot s19-nav
key Tab; pause 0.8; shot s20-comms
key Tab; pause 0.8; shot s21-cargo
key e; pause 1
# The weapons console: the tactical board on Weapons; Enter holds fire; Tab to Defense and Contacts
cmd dev teleport\(10, 3, avatar\); pause 1.2; hold a 0.15; pause 0.8; cmd captain; shot s22-weapons-prompt
key e; pause 1.5; shot s23-weapons
key Return; pause 0.8; shot s24-hold-fire
key Return; pause 0.5
key Tab; pause 0.8; shot s25-defense
key Tab; pause 0.8; shot s26-contacts
key e; pause 1
pause 1
finish
