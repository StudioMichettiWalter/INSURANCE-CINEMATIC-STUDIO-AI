#!/usr/bin/env bash
set -euo pipefail
# Build host Linux x64. Installare SDK da Android Studio o bootstrap.sh.
src=$(cd "$(dirname "$0")" && pwd)
root=$(dirname "$src")
: "${JAVA_HOME:?Specificare JDK 21}"
: "${ANDROID_JAR:?Specificare platforms/android-35/android.jar}"
: "${BUILD_TOOLS:?Specificare build-tools/35.0.0}"
: "${NDK_HOME:?Specificare NDK r27d}"
: "${BUILD_DIR:?Specificare una directory di build esterna}"
: "${APK_OUT:?Specificare il nuovo APK}"
[[ ! -e "$APK_OUT" ]] || { echo "Il file di destinazione esiste già" >&2; exit 1; }
mkdir -p "$BUILD_DIR" "$(dirname "$APK_OUT")"
build=$(mktemp -d "$BUILD_DIR/apk.XXXXXX")
trap 'rm -rf "$build"' EXIT
mkdir -p "$build"/{classes,dex,zip/lib/arm64-v8a,assets/regole}
export PATH="$JAVA_HOME/bin:$PATH"
"$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --target=aarch64-linux-android27 \
    -O2 -ffp-contract=off -fPIC -shared -Wall -Wextra -Wno-unused-parameter -Werror -I"$root/core" \
    "$src/bridge.c" "$root/core/scene.c" -Wl,-z,max-page-size=16384 -lm -ljnigraphics -o "$build/zip/lib/arm64-v8a/libstudio.so"
: "${ANDROID_ASR_LIB:?Specificare libstudio_asr.so ARM64}"
: "${ANDROID_LLM_LIB:?Specificare libstudio_llm.so ARM64}"
cp "$ANDROID_ASR_LIB" "$build/zip/lib/arm64-v8a/libstudio_asr.so"
cp "$ANDROID_LLM_LIB" "$build/zip/lib/arm64-v8a/libstudio_llm.so"
cp "$root/llm/prompt.it.txt" "$build/assets/llm-prompt.it.txt"
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR:$BUILD_TOOLS/core-lambda-stubs.jar" -encoding UTF-8 \
    -d "$build/classes" "$src"/src/it/michetti/studio/*.java "$root"/inference-android/java/it/michetti/studio/*.java
jar cf "$build/classes.jar" -C "$build/classes" .
"$BUILD_TOOLS/d8" --min-api 27 --lib "$ANDROID_JAR" --output "$build/dex" "$build/classes.jar"
mkdir -p "$build/assets/licenze"
cp "$root/inference-android/NDK-LLVM-NOTICE.txt" "$root/llm/llama-LICENSE.txt" "$root/llm/qwen-LICENSE.txt" "$root"/asr/licenses/*.txt "$build/assets/licenze/"
cp "$root/brand/Inter.ttf" "$root/brand/OFL.txt" "$build/assets/"
cp "$root/docs/regole-prodotto.json" "$root/source/insurance-cinematic-studio/SKILL.md" "$root/source/PROMPT_MASTER_CODEX.md" "$build/assets/regole/"
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$src/AndroidManifest.xml" -A "$build/assets" -o "$build/app.apk"
cp "$build/dex/classes.dex" "$build/zip/"
python3 - "$build" <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import sys
base=Path(sys.argv[1])
with ZipFile(base/'app.apk','a',ZIP_DEFLATED) as z:
    for p in (base/'zip').rglob('*'):
        if p.is_file(): z.write(p,p.relative_to(base/'zip'))
PY
"$BUILD_TOOLS/zipalign" -P 16 -f 4 "$build/app.apk" "$build/aligned.apk"
if [[ -z "${STUDIO_KEYSTORE:-}" ]]; then
    STUDIO_KEYSTORE="$build/test.p12"; STUDIO_ALIAS=test
    export STUDIO_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(20))')"
    keytool -genkeypair -keystore "$STUDIO_KEYSTORE" -storepass:env STUDIO_PASSWORD -alias test \
        -keyalg RSA -keysize 3072 -validity 30 -dname 'CN=Studio M0 TEST ONLY'
fi
: "${STUDIO_ALIAS:?Alias richiesto}"; : "${STUDIO_PASSWORD:?Password richiesta in ambiente}"
"$BUILD_TOOLS/apksigner" sign --ks "$STUDIO_KEYSTORE" --ks-key-alias "$STUDIO_ALIAS" \
    --ks-pass env:STUDIO_PASSWORD --out "$build/signed.apk" "$build/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$build/signed.apk"
cp "$build/signed.apk" "$APK_OUT"
sha256sum "$APK_OUT"
