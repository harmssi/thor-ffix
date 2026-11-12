#!/bin/bash
# Baut nur Assembly-CSharp.dll (Memoria + Thor-Statusserver) ohne Windows/Visual Studio:
# Roslyn (csc) aus dem offiziellen .NET-SDK-Docker-Image auf einem Build-Host.
#
# Benötigt (nicht im Repo, da Spieldaten bzw. Binärdateien):
#   FF9_MANAGED      = x64/FF9_Data/Managed der eigenen (unveränderten) Steam-Installation
#                      (Assembly-CSharp-firstpass, UnityEngine, mscorlib, System, System.Core)
#   MEMORIA_MANAGED  = x64/FF9_Data/Managed aus der Memoria-Patcher-Nutzlast desselben Stands
#                      (Memoria.Prime, UnityEngine.UI, XInputDotNetPure, Newtonsoft.Json, System.Runtime.Serialization)
#                      -> thor/memoria_extract.py entpackt die Nutzlast eines Memoria.Patcher
#   THOR_BUILD_HOST  = ssh-Ziel mit Docker (Standard: aus ~/.config/thorconquer/env)
# Ausgabe: thor/out/Assembly-CSharp.dll
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f ~/.config/thorconquer/env ] && source ~/.config/thorconquer/env
: "${FF9_MANAGED:?FF9_MANAGED setzen}"; : "${MEMORIA_MANAGED:?MEMORIA_MANAGED setzen}"; : "${THOR_BUILD_HOST:?THOR_BUILD_HOST setzen}"
IMG=${DOTNET_IMAGE:-mcr.microsoft.com/dotnet/sdk:9.0}
STAGE=thor/.stage
rm -rf "$STAGE"; mkdir -p "$STAGE/refs" thor/out
for f in Assembly-CSharp-firstpass UnityEngine mscorlib System System.Core; do cp "$FF9_MANAGED/$f.dll" "$STAGE/refs/"; done
for f in Memoria.Prime UnityEngine.UI XInputDotNetPure Newtonsoft.Json System.Runtime.Serialization; do cp "$MEMORIA_MANAGED/$f.dll" "$STAGE/refs/"; done

# Quelldateien wie im csproj
python3 -I -c '
import re, sys
t = open(sys.argv[1], encoding="utf-8-sig").read()
for m in re.findall(r"<Compile Include=\"([^\"]+)\"", t):
    print("\"Assembly-CSharp/" + m.replace("\\", "/") + "\"")
' Assembly-CSharp/Assembly-CSharp.csproj > "$STAGE/sources.rsp"

R=/w/thor/.stage/refs
cat > "$STAGE/csc.rsp" <<EOR
-nostdlib -target:library -unsafe -optimize+ -debug- -langversion:latest
-define:TRACE -nowarn:CS0414,CS0649,CS0169,CS0108,CS0618,CS8632,CS1701,CS1702 -warn:0
-out:/w/thor/out/Assembly-CSharp.dll
-resource:/w/Assembly-CSharp/Memoria/Configuration/Memoria.ini,Assembly-CSharp.Memoria.Configuration.Memoria.ini
-r:$R/mscorlib.dll -r:$R/System.dll -r:$R/System.Core.dll -r:$R/UnityEngine.dll -r:$R/Assembly-CSharp-firstpass.dll
-r:$R/Memoria.Prime.dll -r:$R/UnityEngine.UI.dll -r:$R/XInputDotNetPure.dll -r:$R/Newtonsoft.Json.dll -r:$R/System.Runtime.Serialization.dll
EOR

REMOTE=thor-memoria-build
rsync -a --delete --exclude .git --exclude thor/out --exclude /References --exclude '/Memoria.Launcher*' --exclude /SaXAudio \
  ./ "$THOR_BUILD_HOST:$REMOTE/"
ssh "$THOR_BUILD_HOST" "cd $REMOTE && mkdir -p thor/out && docker run --rm -v \$PWD:/w -w /w $IMG sh -c 'dotnet /usr/share/dotnet/sdk/*/Roslyn/bincore/csc.dll @thor/.stage/csc.rsp @thor/.stage/sources.rsp'"
scp -q "$THOR_BUILD_HOST:$REMOTE/thor/out/Assembly-CSharp.dll" thor/out/
ls -l thor/out/Assembly-CSharp.dll
