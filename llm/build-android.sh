#!/bin/sh
# Downloads explicit developer dependencies only. Never invoked from the application.
set -eu
: "${ANDROID_NDK_ROOT:?Indicare NDK r27d}"
out=${1:?Indicare cartella lavoro build}
mkdir -p "$out"
out=$(cd "$out" && pwd)
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cmake_bin=${CMAKE_BIN:-cmake}
archive="$out/llama.cpp-b6000.tar.gz"
if [ ! -f "$archive" ]; then
  curl -fL --retry 2 'https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/b6000' -o "$archive.part"
  mv "$archive.part" "$archive"
fi
echo "9be103102d597a9820a525158f91d349ba84e19e12a9177bd591b67c82d2fc0f  $archive" | sha256sum -c -
tar -xzf "$archive" -C "$out"
"$cmake_bin" -S "$out/llama.cpp-b6000" -B "$out/arm-build" \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=OFF \
  -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_ARM_ARCH=armv8-a \
  -DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_CURL=OFF
"$cmake_bin" --build "$out/arm-build" --target llama -j "${BUILD_JOBS:-2}"
ndk="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
"$ndk/bin/clang++" --target=aarch64-linux-android27 --sysroot="$ndk/sysroot" \
  -std=c++17 -O2 -fPIC -shared -static-libstdc++ -Wl,-z,max-page-size=16384 -Wl,-z,defs \
  -Wl,-soname,libstudio_llm.so -I "$out/llama.cpp-b6000/include" -I "$out/llama.cpp-b6000/ggml/include" \
  "$src/native.cpp" -Wl,--start-group "$out/arm-build/src/libllama.a" \
  "$out/arm-build/ggml/src/libggml.a" "$out/arm-build/ggml/src/libggml-cpu.a" \
  "$out/arm-build/ggml/src/libggml-base.a" -Wl,--end-group -ldl -llog -lm -o "$out/libstudio_llm.so"
"$ndk/bin/llvm-strip" --strip-unneeded "$out/libstudio_llm.so"
