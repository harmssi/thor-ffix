#!/bin/bash
# Entfernt Memoria wieder aus einer FF IX-Installation auf dem Gerät (Gegenstück zu install_device.sh):
# löscht die Memoria-Dateien laut thor-memoria-files.txt und stellt die *.thor-bak-Originale her.
# Spielstände (liegen im Proton-Prefix) werden nicht angefasst.
set -euo pipefail
GAME=${1:?Spielordner auf dem Gerät}
adb shell "cd '$GAME' && test -f thor-memoria-files.txt" || { echo "no Memoria file list found"; exit 1; }
adb shell "cd '$GAME' && while read f; do [ -f \"\$f.thor-bak\" ] || rm -f \"\$f\"; done < thor-memoria-files.txt"
adb shell "cd '$GAME' && for b in \$(find . -name '*.thor-bak'); do mv \"\$b\" \"\${b%.thor-bak}\"; done; rm -f thor-memoria-files.txt Memoria.ini"
echo "Memoria removed (originals restored)"
