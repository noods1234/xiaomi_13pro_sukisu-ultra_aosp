#!/usr/bin/env python3
"""
Anti-recurrence guard against orphaned ("dead") components.

WHY: three separate audits of this project found the same failure mode — a class gets written,
unit-tested, documented as "Implemented", and never connected to anything. Unit tests do not catch
this, because a well-tested orphan still passes its tests. This converts that honesty problem into
a mechanical check.

RULE: every class under a `main/` source tree must be referenced from at least one file other than
itself and its own tests. Anything genuinely intended to be standalone must be listed in
tools/unwired_allowlist.txt WITH A REASON.

Exit 0 = no unexplained orphans. Exit 1 = orphans found (prints them).
"""
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APPS = os.path.join(REPO, "packages", "apps")
ALLOWLIST = os.path.join(REPO, "tools", "unwired_allowlist.txt")

# Entry points are referenced by AndroidManifest.xml / the framework, not by other Kotlin files.
MANIFEST_KINDS = ("Activity", "Service", "Provider", "Receiver", "Application")


def load_allowlist():
    allowed = {}
    if not os.path.exists(ALLOWLIST):
        return allowed
    for line in open(ALLOWLIST):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, _, reason = line.partition(":")
        allowed[name.strip()] = reason.strip()
    return allowed


def strip_comments(src):
    """Remove block and line comments. A class named only in prose is NOT wired — that exact
    false-negative (LtcDecoder was 'referenced' solely by a KDoc comment) motivated this."""
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


def manifest_declared():
    """Class names referenced from any AndroidManifest.xml (android:name=".Foo")."""
    declared = set()
    for root, _, files in os.walk(APPS):
        for f in files:
            if f == "AndroidManifest.xml":
                text = open(os.path.join(root, f)).read()
                for m in re.finditer(r'android:name="\.?([\w.]+)"', text):
                    declared.add(m.group(1).split(".")[-1])
    return declared


def main():
    allowed = load_allowlist()
    declared = manifest_declared()

    main_files, all_files = [], []
    for root, _, files in os.walk(APPS):
        if os.sep + "build" + os.sep in root:
            continue
        for f in files:
            if not f.endswith(".kt"):
                continue
            p = os.path.join(root, f)
            all_files.append(p)
            if os.sep + "main" + os.sep in p:
                main_files.append(p)

    orphans = []
    for path in main_files:
        cls = os.path.basename(path)[:-3]
        referenced = False
        for other in all_files:
            # Only references from PRODUCTION code count. A class used solely by tests is exactly
            # what an orphan looks like — that was the original blind spot this guard exists to close.
            if other == path or (os.sep + "main" + os.sep) not in other:
                continue
            # Word-boundary match so `Recorder` doesn't match `AudioRecorder`.
            if re.search(r"\b" + re.escape(cls) + r"\b", strip_comments(open(other).read())):
                referenced = True
                break
        if referenced:
            continue
        if cls in declared or cls.endswith(MANIFEST_KINDS):
            continue  # framework entry point
        if cls in allowed:
            continue
        orphans.append(cls)

    if orphans:
        print("FAIL: unwired components (referenced nowhere outside themselves/their tests):")
        for o in sorted(orphans):
            print(f"  - {o}")
        print("\nEither wire it into the app, or add it to tools/unwired_allowlist.txt with a reason.")
        print("Do NOT document an unwired component as 'Implemented'.")
        return 1

    print(f"wiring OK — {len(main_files)} main classes, no unexplained orphans")
    return 0


if __name__ == "__main__":
    sys.exit(main())
