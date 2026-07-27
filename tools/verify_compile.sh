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
#   1   pure-JVM   — no Android at all; compiled AND unit-tested (strongest).
#   1.5 Robolectric— real Android framework code EXECUTED on the JVM (files, Context, StatFs,
#                    ContentProvider). Stronger than compiling; still a simulated runtime, NOT the
#                    device. Never describe a tier-1.5 pass as "works on the phone".
#   2   android-all— real android.* framework classes; compiled (no execution).
#   3   shimmed    — androidx + AGP-generated types (ViewBinding/R) are hand-written stubs because
#                    androidx is Google-Maven-only. Weakest tier: a stub signature that is wrong
#                    would hide a real error. Treat tier-3 passes as "plausible", not "verified".
#
# Usage: tools/verify_compile.sh [--quick]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${OIW_VERIFY_WORK:-${TMPDIR:-/tmp}/oiw_verify}"
LIB="$WORK/lib"
MAVEN=https://repo1.maven.org/maven2

KOTLIN_VERSION=1.9.24
ANDROID_ALL=android-all-14-robolectric-10818077.jar
# Robolectric loads a pre-instrumented android-all of its own; the suffix is tied to the
# Robolectric version (4.12.2 -> i6) and must match tools/robolectric_deps.txt.
ANDROID_ALL_INSTRUMENTED=android-all-instrumented-14-robolectric-10818077-i6.jar
ROBO_DEPS_DIR="$WORK/robolectric-deps"

mkdir -p "$LIB" "$ROBO_DEPS_DIR"
fetch() { # path filename
  [ -f "$LIB/$2" ] && return 0
  echo "  fetching $2"
  curl -sSL -m 900 -o "$LIB/$2" "$MAVEN/$1"
}

echo "==> [1/6] dependencies"
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

# Tier 1.5 runtime. Skippable: OIW_SKIP_ROBOLECTRIC=1 keeps the other tiers usable on a slow or
# metered link (the two android-all jars are ~290 MB combined).
ROBO_CP=""
if [ "${OIW_SKIP_ROBOLECTRIC:-0}" != "1" ]; then
  while read -r path name; do
    case "$path" in ''|'#'*) continue;; esac
    fetch "$path" "$name"
    ROBO_CP="$ROBO_CP:$LIB/$name"
  done < "$REPO_ROOT/tools/robolectric_deps.txt"
  if [ ! -f "$ROBO_DEPS_DIR/$ANDROID_ALL_INSTRUMENTED" ]; then
    echo "  fetching $ANDROID_ALL_INSTRUMENTED (~150 MB, cached after first run)"
    curl -sSL -m 1800 -o "$ROBO_DEPS_DIR/$ANDROID_ALL_INSTRUMENTED" \
      "$MAVEN/org/robolectric/android-all-instrumented/14-robolectric-10818077-i6/$ANDROID_ALL_INSTRUMENTED"
  fi
fi

KC="$LIB/kotlin-compiler-embeddable.jar:$LIB/kotlin-stdlib.jar:$LIB/kotlin-reflect.jar:$LIB/kotlin-script-runtime.jar:$LIB/kotlin-daemon-embeddable.jar:$LIB/annotations.jar:$LIB/trove4j.jar"
kotlinc() { java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"; }

CAM_SRC="$REPO_ROOT/packages/apps/OIWCamera/app/src"
LAU_SRC="$REPO_ROOT/packages/apps/OIWLauncher/app/src"
ANDROID_CP="$LIB/kotlin-stdlib.jar:$LIB/gson.jar:$LIB/coroutines-core.jar:$LIB/android-all.jar"

rm -rf "$WORK/out"; mkdir -p "$WORK/out"/{t2,t3}

echo "==> [2/6] tier 2 — compile against REAL Android API 34 (android-all)"
mkdir -p "$WORK/t2src"; rm -f "$WORK/t2src"/*.kt
# Everything except the androidx-dependent classes, which go to tier 3.
#
# The split is derived from the source rather than a hardcoded filename list: the list version
# silently sent each new androidx-using Activity to tier 2, where it failed to compile against
# android-all and took the whole run down. Anything importing androidx.* or referencing the
# AGP-generated R belongs in tier 3 by definition.
find "$CAM_SRC/main/java" -name '*.kt' | while read -r f; do
  if grep -qE '^import androidx\.|com\.oiw\.camera\.R$|[^A-Za-z]R\.(layout|id|string|drawable)\.' "$f"; then
    continue
  fi
  cp "$f" "$WORK/t2src/"
done
kotlinc -jvm-target 17 -nowarn -cp "$ANDROID_CP" -d "$WORK/out/t2" "$WORK/t2src" 2>&1 | grep -vE '^warning:' || true
[ -n "$(find "$WORK/out/t2" -name '*.class' -print -quit)" ] || { echo "FAIL: tier 2 produced no classes"; exit 1; }
echo "    OK  $(find "$WORK/out/t2" -name '*.class' | wc -l) classes"

echo "==> [3/6] tier 3 — androidx/AGP-stubbed classes (weakest tier)"
STUBS="$REPO_ROOT/tools/verify_stubs"
mkdir -p "$WORK/t3src"; rm -f "$WORK/t3src"/*.kt
# The complement of tier 2's filter, derived the same way — every camera-app file tier 2 skipped.
find "$CAM_SRC/main/java" -name '*.kt' | while read -r f; do
  if grep -qE '^import androidx\.|com\.oiw\.camera\.R$|[^A-Za-z]R\.(layout|id|string|drawable)\.' "$f"; then
    cp "$f" "$WORK/t3src/"
  fi
done
find "$LAU_SRC/main/java" -name '*.kt' -exec cp {} "$WORK/t3src/" \;
[ -n "$(find "$WORK/t3src" -name '*.kt' -print -quit)" ] || { echo "FAIL: tier 3 has no sources"; exit 1; }
kotlinc -jvm-target 17 -nowarn -cp "$ANDROID_CP:$WORK/out/t2" -d "$WORK/out/t3" \
  "$STUBS" "$WORK/t3src" 2>&1 | grep -vE '^warning:' || true
[ -n "$(find "$WORK/out/t3" -name '*.class' -print -quit)" ] || { echo "FAIL: tier 3 produced no classes"; exit 1; }
echo "    OK  $(find "$WORK/out/t3" -name '*.class' | wc -l) classes"

if [ "${1:-}" = "--quick" ]; then echo "==> quick mode: skipping tests"; exit 0; fi

# Collects the fully-qualified names of every *Test.kt under a source root.
test_classes() { # srcRoot
  find "$1" -name '*Test.kt' | while read -r f; do
    printf '%s.%s\n' "$(awk '/^package /{print $2; exit}' "$f")" "$(basename "$f" .kt)"
  done | sort
}

echo "==> [4/6] tier 1 — pure-JVM unit tests (compiled AND executed)"
mkdir -p "$WORK/out/tests"
# android-all is on the test classpath because some tests reference framework CONSTANTS
# (e.g. PowerManager.THERMAL_STATUS_*). Only constants and pure logic are exercised at runtime —
# android-all method bodies throw "Stub!", so anything calling real framework behavior belongs in
# tier 2 (compile-only), not here.
TEST_CP="$LIB/kotlin-stdlib.jar:$LIB/gson.jar:$LIB/junit.jar:$LIB/hamcrest.jar:$LIB/android-all.jar:$WORK/out/t2"
kotlinc -jvm-target 17 -nowarn -cp "$TEST_CP" -d "$WORK/out/tests" "$CAM_SRC/test/java" 2>&1 | grep -vE '^warning:' || true
TESTS=$(test_classes "$CAM_SRC/test/java")
# Run from the app module dir so `user.dir` matches Gradle's, which is how BundledProfileAssetsTest
# locates src/main/assets. Exit status is checked explicitly: piping straight into `tail` used to
# mask a failing suite behind tail's always-zero status (audit finding S).
set +e
( cd "$REPO_ROOT/packages/apps/OIWCamera/app" \
  && java -cp "$TEST_CP:$WORK/out/tests" org.junit.runner.JUnitCore $TESTS ) > "$WORK/tier1.log" 2>&1
TIER1_STATUS=$?
set -e
tail -6 "$WORK/tier1.log"
[ $TIER1_STATUS -eq 0 ] || { echo "FAIL: tier 1 tests failed (full log: $WORK/tier1.log)"; exit 1; }

echo "==> [5/6] tier 1.5 — Robolectric: real Android framework code EXECUTED on the JVM"
if [ "${OIW_SKIP_ROBOLECTRIC:-0}" = "1" ]; then
  echo "    SKIPPED (OIW_SKIP_ROBOLECTRIC=1) — filesystem/Context/provider behavior is UNVERIFIED"
else
  # androidx.test:monitor is Google-Maven-only and blocked here; tools/robolectric_shim supplies the
  # API surface Robolectric calls from it. See tools/robolectric_shim/README.md for what that does
  # and does not buy us. Under ./gradlew testDebugUnitTest the real artifact is used instead.
  mkdir -p "$WORK/out/shim" "$WORK/out/robotests"
  javac -nowarn -cp "$LIB/android-all.jar" -d "$WORK/out/shim" \
    $(find "$REPO_ROOT/tools/robolectric_shim" -name '*.java') 2>&1 | grep -v '^Note:' || true
  [ -n "$(find "$WORK/out/shim" -name '*.class' -print -quit)" ] || { echo "FAIL: shim did not compile"; exit 1; }

  ROBO_TEST_CP="$LIB/kotlin-stdlib.jar:$LIB/gson.jar:$LIB/junit.jar:$LIB/hamcrest.jar"
  ROBO_TEST_CP="$ROBO_TEST_CP:$LIB/android-all.jar:$WORK/out/t2:$WORK/out/shim$ROBO_CP"
  kotlinc -jvm-target 17 -nowarn -cp "$ROBO_TEST_CP" -d "$WORK/out/robotests" \
    "$CAM_SRC/test/robolectric/java" 2>&1 | grep -vE '^warning:' || true
  [ -n "$(find "$WORK/out/robotests" -name '*.class' -print -quit)" ] || {
    echo "FAIL: tier 1.5 tests did not compile"; exit 1; }

  ROBO_TESTS=$(test_classes "$CAM_SRC/test/robolectric/java")
  set +e
  java -cp "$WORK/out/robotests:$ROBO_TEST_CP" \
    -Drobolectric.offline=true -Drobolectric.dependency.dir="$ROBO_DEPS_DIR" \
    org.junit.runner.JUnitCore $ROBO_TESTS > "$WORK/tier15.log" 2>&1
  ROBO_STATUS=$?
  set -e
  tail -6 "$WORK/tier15.log"
  [ $ROBO_STATUS -eq 0 ] || { echo "FAIL: tier 1.5 tests failed (full log: $WORK/tier15.log)"; exit 1; }
fi

echo "==> [6/6] wiring guard"
python3 "$REPO_ROOT/tools/check_wiring.py" || exit 1

echo
echo "VERIFY OK — scope reminder:"
echo "  tiers 2/3 prove COMPILATION only."
echo "  tiers 1/1.5 prove EXECUTION on a JVM (Robolectric = a simulated Android runtime)."
echo "  Neither proves behavior on the device. ./gradlew assembleDebug + a real take remain the gates."
