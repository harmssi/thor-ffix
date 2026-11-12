#!/bin/bash
# Spielt Memoria (Nutzlast eines Memoria.Patcher) plus die eigene Assembly-CSharp.dll per adb
# in eine FF IX-Installation auf einem Android-Gerät (z. B. DroiDeck/Steam auf dem AYN Thor).
#
#   thor/install_device.sh <Spielordner auf dem Gerät> <entpackte Nutzlast> [Assembly-CSharp.dll]
#
# - Originale, die Memoria überschreibt, werden einmalig als *.thor-bak gesichert.
# - Memoria.ini/Settings.ini: vorhandene Dateien werden angepasst, nicht ersetzt.
# - Rückbau: thor/uninstall_device.sh
# adb nutzt ANDROID_SERIAL, falls gesetzt.
set -euo pipefail
GAME=${1:?Spielordner auf dem Gerät}; PAYLOAD=${2:?Nutzlast-Verzeichnis}; DLL=${3:-}
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
REMOTE_TMP=/data/local/tmp/thor-memoria

adb shell "test -d '$GAME/x64/FF9_Data/Managed'" || { echo "Kein FF IX in $GAME"; exit 1; }

# 1) Originale sichern (nur beim ersten Mal)
for f in FF9_Launcher.exe x64/FF9_Data/Managed/Assembly-CSharp.dll x64/FF9_Data/Managed/UnityEngine.UI.dll x64/FF9_Data/Managed/XInputDotNetPure.dll; do
  adb shell "cd '$GAME' && if [ -f '$f' ] && [ ! -f '$f.thor-bak' ]; then cp -p '$f' '$f.thor-bak' && echo 'gesichert: $f'; fi"
done

# 2) Nutzlast übertragen (ohne Memoria.ini/Settings.ini, die werden unten zusammengeführt)
(cd "$PAYLOAD" && find . -type f ! -name Memoria.ini ! -name Settings.ini | sed 's#^\./##' | sort > "$TMP/files.txt")
(cd "$PAYLOAD" && COPYFILE_DISABLE=1 tar -cf "$TMP/payload.tar" -T "$TMP/files.txt")
if [ -n "$DLL" ]; then
  mkdir -p "$TMP/own/x64/FF9_Data/Managed" && cp "$DLL" "$TMP/own/x64/FF9_Data/Managed/Assembly-CSharp.dll"
  (cd "$TMP/own" && COPYFILE_DISABLE=1 tar -cf "$TMP/own.tar" x64)
fi
adb shell "rm -rf $REMOTE_TMP && mkdir -p $REMOTE_TMP"
adb push -q "$TMP/payload.tar" $REMOTE_TMP/ >/dev/null
adb shell "cd '$GAME' && tar -xf $REMOTE_TMP/payload.tar"
if [ -n "$DLL" ]; then
  adb push -q "$TMP/own.tar" $REMOTE_TMP/ >/dev/null
  adb shell "cd '$GAME' && tar -xf $REMOTE_TMP/own.tar"
fi
# Liste der Memoria-Dateien für den Rückbau
adb push -q "$TMP/files.txt" "$GAME/thor-memoria-files.txt" >/dev/null

# 3) INIs zusammenführen
if adb shell "test -f '$GAME/Memoria.ini'"; then adb pull -q "$GAME/Memoria.ini" "$TMP/Memoria.ini" >/dev/null; else cp "$PAYLOAD/Memoria.ini" "$TMP/Memoria.ini"; fi
python3 -I "$HERE/iniset.py" "$TMP/Memoria.ini" \
  Graphics.WidescreenSupport=1 Graphics.BattleFPS=60 Graphics.FieldFPS=60 Graphics.WorldFPS=60 Graphics.MenuFPS=60 \
  Graphics.VSync=1 Graphics.SkipIntros=3 Control.DisableMouse=1 \
  ThorStatus.Enabled=1 ThorStatus.BindAddress=127.0.0.1 ThorStatus.Port=8766 ThorStatus.IntervalMs=150
adb push -q "$TMP/Memoria.ini" "$GAME/Memoria.ini" >/dev/null
if adb shell "test -f '$GAME/Settings.ini'"; then adb pull -q "$GAME/Settings.ini" "$TMP/Settings.ini" >/dev/null; else cp "$PAYLOAD/Settings.ini" "$TMP/Settings.ini"; fi
python3 -I "$HERE/iniset.py" "$TMP/Settings.ini" Settings.WindowMode=0 Settings.ScreenResolution=1920x1080 \
  Memoria.AutoRunGame=true Memoria.CheckUpdates=false
adb push -q "$TMP/Settings.ini" "$GAME/Settings.ini" >/dev/null

# 4) Rechte: per adb geschriebene Dateien gehören "shell" -> für die App beschreibbar machen
adb shell "cd '$GAME' && chmod -R a+rwX . 2>/dev/null; rm -rf $REMOTE_TMP"
echo "Memoria installiert in $GAME"
