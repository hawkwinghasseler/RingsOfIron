#!/bin/sh
# Renders every FILAMENT mockup and writes the images the docs link to.
# Needs node with playwright (and its Chromium) and ImageMagick's convert. Run from this folder.
set -e
TMP=$(mktemp -d)
shot() { node render.js page.html "$1" "$TMP/$1.png" && convert "$TMP/$1.png" -resize 1920x -quality 85 -sampling-factor 4:2:0 -strip "$2"; }
shot specimen ../specimen.jpg
V=../../variations/vfd
mkdir -p $V
shot st-helm $V/stations-helm.jpg
shot st-weapons $V/stations-weapons.jpg
shot st-deck $V/stations-deck.jpg
shot cx-helm-cruise $V/context-helm-cruise.jpg
shot cx-helm-combat $V/context-helm-combat.jpg
shot cx-deck-emergency $V/context-deck-emergency.jpg
shot tk-helm-quiet $V/task-helm-quiet.jpg
shot tk-gunnery-combat $V/task-gunnery-combat.jpg
shot tk-deck-dc $V/task-deck-damage-control.jpg
rm -rf "$TMP"
