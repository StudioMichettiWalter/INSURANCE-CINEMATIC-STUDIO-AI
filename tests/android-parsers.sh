#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${JAVA_HOME:?Impostare JAVA_HOME su un JDK}"
mkdir -p "$root/build"
classes=$(mktemp -d "$root/build/java-check.XXXXXXXX")
trap 'rm -rf "$classes"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$classes" \
    "$root/android/src/it/michetti/studio/CaptionCue.java" \
    "$root/android/src/it/michetti/studio/CaptionSrt.java" \
    "$root/android/src/it/michetti/studio/WavPcm.java" \
    "$root/tests/android-captions/CaptionCueTest.java" \
    "$root/tests/android-srt/CaptionSrtTest.java" \
    "$root/tests/android-wave/WavPcmTest.java"
for suite in CaptionCueTest CaptionSrtTest WavPcmTest; do
    "$JAVA_HOME/bin/java" -cp "$classes" "it.michetti.studio.$suite"
done
