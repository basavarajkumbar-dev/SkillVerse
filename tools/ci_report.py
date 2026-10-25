"""Collects build output, test results and the debug APK into ci-report/.

Used by .github/workflows/android.yml. Produces ci-report/SUMMARY.md with real counts
parsed from the JUnit XML that Gradle writes; nothing here is estimated.
"""
import glob
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

content_outcome = sys.argv[1] if len(sys.argv) > 1 else "unknown"
gradle_outcome = sys.argv[2] if len(sys.argv) > 2 else "unknown"
out = "ci-report"
os.makedirs(out, exist_ok=True)

lines = []
sha = os.environ.get("GITHUB_SHA", "local")
lines.append(f"# CI report for {sha}\n")
lines.append(f"- Content validation: **{content_outcome}**")
lines.append(f"- Gradle build + tests: **{gradle_outcome}**")

apks = glob.glob("app/build/outputs/apk/debug/*.apk")
for apk in apks:
    shutil.copy(apk, out)
    lines.append(f"- APK: `{os.path.basename(apk)}` ({os.path.getsize(apk):,} bytes)")
if not apks:
    lines.append("- APK: **not produced**")

total = failures = errors = skipped = 0
failed_cases = []
for path in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    total += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        for tag in ("failure", "error"):
            node = case.find(tag)
            if node is not None:
                msg = (node.get("message") or "").strip()[:300]
                detail = (node.text or "").strip().splitlines()[:12]
                failed_cases.append(
                    f"### {case.get('classname')}.{case.get('name')}\n{msg}\n```\n" + "\n".join(detail) + "\n```"
                )
lines.append(
    f"- Unit/Robolectric tests: {total} run, {failures} failures, {errors} errors, {skipped} skipped"
)

if os.path.isdir("app/build/test-results/testDebugUnitTest"):
    shutil.copytree("app/build/test-results/testDebugUnitTest", f"{out}/test-results", dirs_exist_ok=True)

for name in ("gradle.log", "content-validation.log"):
    if os.path.exists(name):
        shutil.copy(name, out)

if os.path.exists("gradle.log"):
    with open("gradle.log", encoding="utf-8", errors="replace") as f:
        log = f.read().splitlines()
    compile_errors = [l for l in log if re.match(r"^e: ", l)]
    if compile_errors:
        lines.append("\n## Kotlin compile errors\n```")
        lines.extend(compile_errors[:200])
        lines.append("```")
    fail_idx = [i for i, l in enumerate(log) if "What went wrong" in l]
    if fail_idx:
        lines.append("\n## Gradle failure details\n```")
        for i in fail_idx[:5]:
            lines.extend(log[i : i + 25])
        lines.append("```")

if failed_cases:
    lines.append("\n## Failed tests\n")
    lines.extend(failed_cases[:60])

with open(f"{out}/SUMMARY.md", "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines[:12]))
