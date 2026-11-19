#!/usr/bin/env bash
set -euo pipefail
: "${NDK_HOME:?Percorso NDK r27d completo}"
: "${WHISPER_SRC:?Sorgenti whisper.cpp v1.7.6 verificati}"
: "${ASR_BUILD_DIR:?Cartella build dedicata}"
: "${ASR_LIB_OUT:?Destinazione libstudio_asr.so}"
cmake_bin=${CMAKE_BIN:-cmake}
src=$(cd "$(dirname "$0")" && pwd)
"$cmake_bin" -S "$src" -B "$ASR_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 -DANDROID_STL=c++_static \
  -DWHISPER_SOURCE_DIR="$WHISPER_SRC"
"$cmake_bin" --build "$ASR_BUILD_DIR" --target studio_asr_jni --parallel "${BUILD_JOBS:-2}"
mkdir -p "$(dirname "$ASR_LIB_OUT")"
"$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
  "$ASR_BUILD_DIR/libstudio_asr.so" -o "$ASR_LIB_OUT"
