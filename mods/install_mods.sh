#!/bin/bash
# Downloads the mods the FF IX community recommends from their own sources and puts them into a
# FINAL FANTASY IX folder on an Android device (DroidDeck/Steam on the AYN Thor) over adb.
# Nothing is redistributed here: every archive comes from its author's own download link, the same
# links Memoria's Mod Manager uses (ModCatalog.xml).
#
#   mods/install_mods.sh "<game folder on the device>" [mod ...]
#   mods:  moguri video sounds soundtrack psxbuttons german echos     (default: moguri video sounds)
#
# Needs: adb (ANDROID_SERIAL if several devices), curl, unzip, 7zz (brew install sevenzip), python3.
# About 6 GB of downloads and 9 GB on the device for the default set.
set -euo pipefail
GAME=${1:?game folder on the device}; shift || true
MODS=("$@"); [ ${#MODS[@]} -gt 0 ] || MODS=(moguri video sounds)
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK=${THOR_FFIX_WORK:-$HOME/.cache/thor-ffix-mods}
mkdir -p "$WORK/dl" "$WORK/mods"
adb shell "test -f '$GAME/Memoria.ini'" || { echo "No Memoria in $GAME (run memoria/install_device.sh first)"; exit 1; }

# name | folder in the game directory | archive | URL
entry() {
  case "$1" in
    moguri)     echo "MoguriMain|MoguriMain.7z|https://modcdn.win/MoguriMain.7z" ;;
    video)      echo "MoguriVideo|MoguriVideo.zip|https://modcdn.win/MoguriVideo.zip" ;;
    soundtrack) echo "MoguriSoundtrack|MoguriSoundtrack.zip|https://modcdn.win/MoguriSoundtrack.zip" ;;
    sounds)     echo "PlaystationSounds|PlaystationSounds.zip|https://files.dv-modding.fr/FF9_Sounds_Fix%20%28by%20DV%29.zip" ;;
    psxbuttons) echo "PSXButtons|PSXButtons.zip|https://www.dropbox.com/s/815zeapy32xp821/PC_PSXButtons.zip?dl=1" ;;
    german)     echo "ModernGerman|ModernGerman.zip|https://www.dropbox.com/scl/fi/br6xjl22auu284caaxd8n/PC_ModernGerman.zip?rlkey=2pl7a5cycqg4pgiy53u5lwewp&dl=1" ;;
    echos)      echo "Echo-S 9|EchoS9.7z|https://download.tsunamods.com/?id=6" ;;
    *) echo "unknown mod: $1" >&2; exit 1 ;;
  esac
}

FOLDERS=()
for m in "${MODS[@]}"; do
  IFS='|' read -r folder archive url <<<"$(entry "$m")"
  FOLDERS+=("$folder")
  if adb shell "test -d '$GAME/$folder'"; then echo "$folder: already on the device"; continue; fi
  [ -s "$WORK/dl/$archive" ] || { echo "$folder: downloading"; curl -fL -A 'Mozilla/5.0' -C - -o "$WORK/dl/$archive.part" "$url" && mv "$WORK/dl/$archive.part" "$WORK/dl/$archive"; }
  rm -rf "$WORK/mods/$folder" "$WORK/x"; mkdir -p "$WORK/x"
  case "$archive" in *.7z) 7zz x -y -o"$WORK/x" "$WORK/dl/$archive" >/dev/null ;; *) unzip -q -o "$WORK/dl/$archive" -d "$WORK/x" ;; esac
  # some archives hold the mod folder itself, some hold its content
  if [ -d "$WORK/x/$folder" ]; then mv "$WORK/x/$folder" "$WORK/mods/$folder"; else mv "$WORK/x" "$WORK/mods/$folder"; fi
  echo "$folder: copying to the device"
  adb push "$WORK/mods/$folder" "$GAME/" | tail -1
done

# Memoria.ini: the mods on top of the list win. Order as in Memoria's catalog: small fixes above the big packs.
ORDER=("ModernGerman" "PSXButtons" "Echo-S 9" "PlaystationSounds" "MoguriSoundtrack" "MoguriVideo" "MoguriMain")
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
adb pull -q "$GAME/Memoria.ini" "$TMP/Memoria.ini" >/dev/null
LIST=""
for f in "${ORDER[@]}"; do
  if adb shell "test -d '$GAME/$f'"; then LIST="$LIST${LIST:+, }\"$f\""; fi
done
python3 -I "$HERE/../memoria/iniset.py" "$TMP/Memoria.ini" "Mod.FolderNames=$LIST"
case " ${FOLDERS[*]} " in *"Echo-S 9"*) python3 -I "$HERE/../memoria/iniset.py" "$TMP/Memoria.ini" VoiceActing.Enabled=1 ;; esac
adb push -q "$TMP/Memoria.ini" "$GAME/Memoria.ini" >/dev/null
adb shell "cd '$GAME' && chmod -R a+rwX . 2>/dev/null" || true
echo "Mods active: $LIST"
