# Memoria with the Thor status server

`thor-status.patch` applies to [Memoria](https://github.com/Albeoris/Memoria) at commit
`77ecee871f0a452352367b45b2e6fc265b08bfda` (2 Nov 2026). It adds `Assembly-CSharp/Memoria/Thor/`
(a small HTTP server on 127.0.0.1, main-thread snapshots) and the `[ThorStatus]` section in `Memoria.ini`.
It is off unless `Enabled = 1`.

```ini
[ThorStatus]
Enabled = 1
BindAddress = 127.0.0.1
Port = 8766
IntervalMs = 150
```

| Endpoint | Content |
|---|---|
| `GET /events` | Server-Sent Events, one JSON snapshot per change (party with gear and AP, enemies with weaknesses, steal and drop items, world position, items, boosters, counters) |
| `GET /state` | The current snapshot |
| `GET /map/world.png`, `/map/mist.png` | The world map, rendered at runtime from the player's game data |
| `GET /ui/<atlas>/<sprite>.png` | One sprite of the game's UI atlases (`window`, `gray`, `blue`, `icon`, `general`, `face`), cut at runtime; `GET /ui/list/<atlas>` lists the names |
| `GET /cmd/booster/<speed\|noenc\|assist\|dmg>` | Presses the game's booster button (the game's own checks apply) |

## Build without Windows

```sh
git clone https://github.com/Albeoris/Memoria && cd Memoria
git checkout 77ecee871f0a452352367b45b2e6fc265b08bfda
git apply <this repo>/memoria/thor-status.patch
mkdir thor && cp <this repo>/memoria/*.sh <this repo>/memoria/*.py thor/
export FF9_MANAGED=<FF9>/x64/FF9_Data/Managed            # your own, unmodified installation
export MEMORIA_MANAGED=<payload>/x64/FF9_Data/Managed    # thor/memoria_extract.py Memoria.Patcher-… <payload>
export THOR_BUILD_HOST=user@host-with-docker
thor/build.sh                                            # -> thor/out/Assembly-CSharp.dll
```

Only `Assembly-CSharp.dll` is built (Roslyn from `mcr.microsoft.com/dotnet/sdk:9.0`); everything else comes
unchanged from the payload of the official patcher. Game DLLs are only used as references and never leave
your machine.

## Install on the device

```sh
python3 memoria_extract.py Memoria.Patcher-osx-x64 /tmp/payload      # the patcher is not run
./install_device.sh "<game folder on the device>" /tmp/payload Assembly-CSharp.dll
./uninstall_device.sh "<game folder on the device>"                  # undo
```
