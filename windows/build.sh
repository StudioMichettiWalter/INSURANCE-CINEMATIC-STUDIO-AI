#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
cc=${WIN_CC:-x86_64-w64-mingw32-gcc}
nsis=${MAKENSIS:-makensis}
mkdir -p build
"$cc" -std=c11 -O2 -Wall -Wextra -Werror -municode -mwindows launcher.c -o build/StudioM0.exe -luser32
"$cc" -std=c11 -O2 -Wall -Wextra -Werror ../core/scene.c ../core/render.c -o build/scene-frames.exe -lm
"$nsis" -V2 studio.nsi
sha256sum build/*.exe
