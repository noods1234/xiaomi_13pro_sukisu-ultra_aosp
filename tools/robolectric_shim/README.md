# `androidx.test` shim — why it exists, and what it is *not*

Robolectric runs real Android framework code on a desktop JVM. That is the strongest evidence this
project can produce without hardware, so `tools/verify_compile.sh` uses it (tier 1.5).

Robolectric depends on **`androidx.test:monitor`** and **`androidx.test.espresso:espresso-idling-resource`**.
Those artifacts exist only on Google's Maven, which this environment's egress policy blocks:

```
$ curl -I https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.6.1/monitor-1.6.1.aar
curl: (56) CONNECT tunnel failed, response 403
```

That is an organization policy denial, not a transient failure, and it is not something to route
around. So this directory supplies the **minimal API surface Robolectric actually calls** — 28
classes, hand-written from the exact method descriptors Robolectric's own bytecode references
(`javap -c` over `robolectric-4.12.2.jar` and `shadows-framework-4.12.2.jar`). No `androidx.test`
code is copied here; these are independent implementations of a published API.

## Scope, stated honestly

This is **test-harness plumbing, not application code**, and that distinction is the whole reason
it is acceptable:

- Nothing under `packages/apps/` imports any of it. It is never compiled into an APK.
- A wrong signature here fails **loudly** — `NoSuchMethodError` / `NoClassDefFoundError` while
  Robolectric boots — rather than silently mis-verifying app code. This is unlike
  `tools/verify_stubs` (tier 3), where a wrong androidx stub signature genuinely could mask a real
  compile error in app code.
- Where a shim is a no-op (`TestOutputEmitter.dumpThreadStates`), it stands in for a diagnostic
  side effect, never a test outcome.

**What it does not cover:** Espresso, `ActivityScenario`, `ApplicationProvider`, intent stubbing,
and idling-resource synchronisation are represented only well enough for Robolectric to start. Do
not write tests against those APIs in the offline harness — they are unexercised here.

## When you have unrestricted network access

Prefer the real artifacts. Under `./gradlew testDebugUnitTest` on a normal machine, AGP resolves
`androidx.test:monitor` itself and this shim is **not used at all** — Gradle never looks in
`tools/`. The same Robolectric tests then run against the genuine dependency, which is the
authoritative result. If those two ever disagree, believe Gradle.

## Regenerating the reference list

```sh
unzip -qo robolectric-4.12.2.jar -d /tmp/robo && cd /tmp/robo
grep -rla 'androidx/test' . --include='*.class' \
  | xargs -n1 javap -p -c \
  | grep -oE '// *(Method|InterfaceMethod|Field|class) +androidx/test/[^ ]*' | sort -u
```

Anything that appears there and is missing from this directory will surface as a
`NoClassDefFoundError` on the next harness run.
