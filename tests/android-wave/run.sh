#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
: "${JAVA_HOME:?Specificare JDK per javac e java}"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
"$JAVA_HOME/bin/javac" -source 8 -target 8 -Xlint:-options -d "$classes" \
    "$root/android/src/it/michetti/studio/WavPcm.java" "$root/tests/android-wave/WavPcmTest.java"
"$JAVA_HOME/bin/java" -cp "$classes" it.michetti.studio.WavPcmTest
