#!/usr/bin/env bash
# Bootstrap host Linux x64. Esegue in spazio utente; prerequisiti bash, curl, tar, unzip, Python3,
# CMake >= 3.20 e make. Richiede spazio per archivi, NDK e build C++.
set -euo pipefail
src=$(cd "$(dirname "$0")" && pwd)
root=$(dirname "$src")
tools_dir=${1:?Indicare una nuova cartella per dipendenze e APK}
mkdir -p "$tools_dir"
tools_dir=$(cd "$tools_dir" && pwd)
cd "$tools_dir"
fetch() {
    local file=$1 url=$2 sha=$3
    if [[ ! -f "$file" ]]; then
        curl --retry 2 -fL "$url" -o "$file.part"
        mv "$file.part" "$file"
    fi
    echo "$sha  $file" | sha256sum -c -
}
fetch platform.zip https://dl.google.com/android/repository/platform-35_r02.zip 0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0
fetch build.zip https://dl.google.com/android/repository/build-tools_r35_linux.zip bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88
fetch ndk.zip https://dl.google.com/android/repository/android-ndk-r27d-linux.zip 601246087a682d1944e1e16dd85bc6e49560fe8b6d61255be2829178c8ed15d9
fetch jdk.tgz https://cdn.azul.com/zulu/bin/zulu21.40.17-ca-jdk21.0.6-linux_x64.tar.gz 5daff61d307d18305a4022c56013cbaa8987a7dd103e310ebbeb75e0f3091a03
fetch whisper.tar.gz https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.7.6 166140e9a6d8a36f787a2bd77f8f44dd64874f12dd8359ff7c1f4f9acb86202e
unzip -qo platform.zip
unzip -qo build.zip
unzip -qo ndk.zip
tar xf jdk.tgz
tar xf whisper.tar.gz
export JAVA_HOME="$tools_dir/zulu21.40.17-ca-jdk21.0.6-linux_x64" ANDROID_JAR="$tools_dir/android-35/android.jar"
export BUILD_TOOLS="$tools_dir/android-15" NDK_HOME="$tools_dir/android-ndk-r27d"
export ANDROID_NDK_ROOT="$NDK_HOME" WHISPER_SRC="$tools_dir/whisper.cpp-1.7.6"
export BUILD_DIR="$tools_dir/build" ASR_BUILD_DIR="$BUILD_DIR/whisper"
export ANDROID_ASR_LIB="$tools_dir/arm64/libstudio_asr.so" ANDROID_LLM_LIB="$tools_dir/llama/libstudio_llm.so"
export ASR_LIB_OUT="$ANDROID_ASR_LIB"
"$root/inference-android/build.sh"
"$root/llm/build-android.sh" "$tools_dir/llama"
export APK_OUT=${APK_OUT:-"$tools_dir/Studio-M0-arm64-test.apk"}
"$src/build.sh"
