#!/usr/bin/env bash
# OIW-ROM offline verification harness.
#
# WHY THIS EXISTS: Google's Maven (AGP + Android SDK) is unreachable in some CI/dev environments,
# so `./gradlew assembleDebug` cannot run there — which previously meant 64% of production classes
# had never been compiled by ANY compiler. This harness type-checks them offline using the real
# Android framework classes from Robolectric's `android-all` artifact (Maven Central), so type
# errors are caught without the Android SDK.
#
# WHAT IT PROVES / DOES NOT PROVE:
#   proves      — the code compiles against real Android API 34 signatures.
#   DOES NOT    — prove runtime behavior. `./gradlew assembleDebug` on a machine with the Android
#                 SDK, and then a real on-device take, remain the true acceptance gates.
#
# TIERS (decreasing strength of evidence):
#   1 pure-JVM   — no Android at all; compiled AND unit-tested (strongest).
#   2 android-all— real android.* framework classes; compiled (no execution).
#   3 shimmed    — androidx + AGP-generated types (ViewBinding/R) are hand-written stubs because
#                  androidx is Google-Maven-only. Weakest tier: a stub signature that is wrong
#                  would hide a real error. Treat tier-3 passes as "plausible", not "verified".
#
# Usage: tools/verify_compile.sh [--quick]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${OIW_VERIFY_WORK:-${TMPDIR:-/tmp}/oiw_verify}"
LIB="$WORK/lib"
MAVEN=https://repo1.maven.org/maven2

KOTLIN_VERSION=1.9.24
ANDROID_ALL=android-all-14-robolectric-10818077.jar

mkdir -p "$LIB"
fetch() { # path filename
  [ -f "$LIB/$2" ] && return 0
  echo "  fetching $2"
  curl -sSL -m 900 -o "$LIB/$2" "$MAVEN/$1"
}

echo "==> [1/5] dependencies"
fetch "org/jetbrains/kotlin/kotlin-compiler-embeddable/$KOTLIN_VERSION/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar" kotlin-compiler-embeddable.jar
fetch "org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" kotlin-stdlib.jar
fetch "org/jetbrains/kotlin/kotlin-reflect/$KOTLIN_VERSION/kotlin-reflect-$KOTLIN_VERSION.jar" kotlin-reflect.jar
fetch "org/jetbrains/kotlin/kotlin-script-runtime/$KOTLIN_VERSION/kotlin-script-runtime-$KOTLIN_VERSION.jar" kotlin-script-runtime.jar
fetch "org/jetbrains/kotlin/kotlin-daemon-embeddable/$KOTLIN_VERSION/kotlin-daemon-embeddable-$KOTLIN_VERSION.jar" kotlin-daemon-embeddable.jar
fetch "org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar" annotations.jar
fetch "org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar" trove4j.jar
fetch "com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" gson.jar
fetch "junit/junit/4.13.2/junit-4.13.2.jar" junit.jar
fetch "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar" hamcrest.jar
fetch "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar" coroutines-core.jar
fetch "org/robolectric/android-all/14-robolectric-10818077/$ANDROID_ALL" android-all.jar

KC="$LIB/kotlin-compiler-embeddable.jar:$LIB/kotlin-stdlib.jar:$LIB/kotlin-reflect.jar:$LIB/kotlin-script-runtime.jar:$LIB/kotlin-daemon-embeddable.jar:$LIB/annotations.jar:$LIB/trove4j.jar"
kotlinc() { java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"; }

CAM_SRC="$REPO_ROOT/packages/apps/OIWCamera/app/src"
LAU_SRC="$REPO_ROOT/packages/apps/OIWLauncher/app/src"
ANDROID_CP="$LIB/kotlin-stdlib.jar:$LIB/gson.jar:$LIB/coroutines-core.jar:$LIB/android-all.jar"

rm -rf "$WORK/out"; mkdir -p "$WORK/out"/{t2,t3}

echo "==> [2/5] tier 2 — compile against REAL Android API 34 (android-all)"
mkdir -p "$WORK/t2src"; rm -f "$WORK/t2src"/*.kt
# Everything except the androidx-dependent classes (handled in tier 3).
find "$CAM_SRC/main/java" -name '*.kt' \
  ! -name 'CameraActivity.kt' ! -name 'RecordingService.kt' -exec cp {} "$WORK/t2src/" \;
kotlinc -jvm-target 17 -nowarn -cp "$ANDROID_CP" -d "$WORK/out/t2" "$WORK/t2src" 2>&1 | grep -vE '^warning:' || true
[ -n "$(find "$WORK/out/t2" -name '*.class' -print -quit)" ] || { echo "FAIL: tier 2 produced no classes"; exit 1; }
echo "    OK  $(find "$WORK/out/t2" -name '*.class' | wc -l) classes"

echo "==> [3/5] tier 3 — androidx/AGP-stubbed classes (weakest tier)"
STUBS="$REPO_ROOT/tools/verify_stubs"
mkdir -p "$WORK/t3src"; rm -f "$WORK/t3src"/*.kt
cp "$CAM_SRC/main/java/com/oiw/camera/ui/CameraActivity.kt" \
   "$CAM_SRC/main/java/com/oiw/camera/record/RecordingService.kt" "$WORK/t3src/"
find "$LAU_SRC/main/java" -name '*.kt' -exec cp {} "$WORK/t3src/" \;
kotlinc -jvm-target 17 -nowarn -cp "$ANDROID_CP:$WORK/out/t2" -d "$WORK/out/t3" \
  "$STUBS" "$WORK/t3src" 2>&1 | grep -vE '^warning:' || true
[ -n "$(find "$WORK/out/t3" -name '*.class' -print -quit)" ] || { echo "FAIL: tier 3 produced no classes"; exit 1; }
echo "    OK  $(find "$WORK/out/t3" -name '*.class' | wc -l) classes"

if [ "${1:-}" = "--quick" ]; then echo "==> quick mode: skipping tests"; exit 0; fi

echo "==> [4/5] tier 1 — pure-JVM unit tests (compiled AND executed)"
mkdir -p "$WORK/out/tests"
# android-all is on the test classpath because some tests reference framework CONSTANTS
# (e.g. PowerManager.THERMAL_STATUS_*). Only constants and pure logic are exercised at runtime —
# android-all method bodies throw "Stub!", so anything calling real framework behavior belongs in
# tier 2 (compile-only), not here.
TEST_CP="$LIB/kotlin-stdlib.jar:$LIB/gson.jar:$LIB/junit.jar:$LIB/hamcrest.jar:$LIB/android-all.jar:$WORK/out/t2"
kotlinc -jvm-target 17 -nowarn -cp "$TEST_CP" -d "$WORK/out/tests" "$CAM_SRC/test/java" 2>&1 | grep -vE '^warning:' || true
TESTS=$(find "$CAM_SRC/test/java" -name '*Test.kt' -exec basename {} .kt \; | while read -r t; do
  find "$CAM_SRC/test/java" -name "$t.kt" -exec grep -h '^package ' {} \; | awk -v t="$t" '{print $2"."t}'
done)
# shellcheck disable=SC2086
java -cp "$TEST_CP:$WORK/out/tests" org.junit.runner.JUnitCore $TESTS 2>&1 | tail -6

echo "==> [5/5] wiring guard"
python3 "$REPO_ROOT/tools/check_wiring.py" || exit 1

echo
echo "VERIFY OK — reminder: this proves compilation, not runtime behavior."
