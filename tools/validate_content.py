#!/usr/bin/env python3
"""Development-time validator for SkillVerse curriculum files.

Usage:  python3 tools/validate_content.py app/src/main/assets/content

Checks, with a diagnostic per problem:
  * every file parses and has the expected schema version and required fields
  * challenge ids are unique and well formed; questions are not duplicated
  * types have the fields they need (options + correctIndex, acceptedAnswers, steps)
  * skills, prerequisites, boss and project references point at real ids
  * per-category counts meet the curriculum targets
  * every challenge with a "verify" block is executed with a real Python interpreter
    (isolated mode, temp directory, timeout) and its accepted answers must produce the
    expected output. This is how Python answer keys are verified before shipping.

The Android app performs its own structural validation at startup (ContentValidator.kt);
it never executes code.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

SCHEMA_VERSION = 1
TYPES = {"MULTIPLE_CHOICE", "PREDICT_OUTPUT", "FILL_BLANK", "IDENTIFY_BUG", "ARRANGE_STEPS", "CODE_PUZZLE"}
DIFFICULTIES = {"EASY", "MEDIUM", "HARD"}
CATEGORIES = {"PYTHON", "LOGIC", "DEBUGGING", "TEST_AUTOMATION", "API_TESTING", "LINUX", "GIT", "EMBEDDED", "DEVOPS"}
MATCH_MODES = {"EXACT", "TRIMMED", "NORMALIZED", "CASE_INSENSITIVE", "CODE"}
TARGETS = {  # category groups required by the curriculum spec
    "Python": ({"PYTHON"}, 35),
    "Debugging & logic": ({"DEBUGGING", "LOGIC"}, 20),
    "Test automation": ({"TEST_AUTOMATION"}, 15),
    "API testing": ({"API_TESTING"}, 10),
    "Linux & Git": ({"LINUX", "GIT"}, 10),
    "Embedded, firmware & CI/CD": ({"EMBEDDED", "DEVOPS"}, 10),
}
ID_RE = re.compile(r"^[a-z]+-\d{3}$")

errors = []


def err(where, msg):
    errors.append(f"{where}: {msg}")


def trimmed(s):
    lines = s.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    lines = [l.rstrip() for l in lines]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def normalize(s, mode):
    if mode == "EXACT":
        return s
    if mode == "TRIMMED":
        return trimmed(s)
    if mode == "NORMALIZED":
        return "\n".join(re.sub(r"[ \t]+", " ", l.strip()) for l in trimmed(s).split("\n"))
    if mode == "CASE_INSENSITIVE":
        return "\n".join(re.sub(r"[ \t]+", " ", l.strip()) for l in trimmed(s).split("\n")).lower()
    if mode == "CODE":
        return re.sub(r"\s+", "", s).replace('"', "'")
    raise ValueError(mode)


def run_python(code, where):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "snippet.py")
        with open(path, "w", encoding="utf-8") as f:
            f.write(code + "\n")
        try:
            proc = subprocess.run([sys.executable, "-I", "-W", "ignore", path], capture_output=True, text=True,
                                  timeout=10, cwd=tmp)
        except subprocess.TimeoutExpired:
            err(where, "verification snippet timed out")
            return None
        if proc.returncode != 0:
            err(where, f"verification snippet failed: {proc.stderr.strip().splitlines()[-1:]}")
            return None
        return proc.stdout


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        err(os.path.basename(path), f"cannot parse: {e}")
        return None
    if data.get("schemaVersion") != SCHEMA_VERSION:
        err(os.path.basename(path), f"schemaVersion {data.get('schemaVersion')} != {SCHEMA_VERSION}")
    if not isinstance(data.get("contentVersion"), int):
        err(os.path.basename(path), "contentVersion must be an integer")
    return data


def main(root):
    manifest = load(os.path.join(root, "manifest.json"))
    if manifest is None:
        return
    skills = load(os.path.join(root, manifest["skills"])) or {"nodes": []}
    skill_ids = [n["id"] for n in skills["nodes"]]
    for n in skills["nodes"]:
        for p in n.get("prerequisites", []):
            if p not in skill_ids:
                err(f"skill {n['id']}", f"unknown prerequisite {p}")

    challenges = []
    for name in manifest["packs"]:
        data = load(os.path.join(root, name))
        if data:
            for c in data.get("challenges", []):
                challenges.append((name, c))

    ids, questions = {}, {}
    verified = 0
    for pack, c in challenges:
        cid = c.get("id", "<missing id>")
        where = f"{pack} {cid}"
        for field in ("id", "category", "skillId", "type", "difficulty", "estimatedSeconds", "title",
                      "description", "instructions", "hints", "explanation", "source"):
            if field not in c or c[field] in ("", None, []):
                err(where, f"missing required field '{field}'")
        if not ID_RE.match(cid):
            err(where, "id must look like abc-001")
        if cid in ids:
            err(where, f"duplicate id (also in {ids[cid]})")
        ids[cid] = pack
        key = (c.get("description", "") + c.get("instructions", "") + (c.get("code") or "")).strip().lower()
        if key in questions:
            err(where, f"duplicate question text (same as {questions[key]})")
        questions[key] = cid
        if c.get("type") not in TYPES:
            err(where, f"unknown type {c.get('type')}")
        if c.get("difficulty") not in DIFFICULTIES:
            err(where, f"unknown difficulty {c.get('difficulty')}")
        if c.get("category") not in CATEGORIES:
            err(where, f"unknown category {c.get('category')}")
        if c.get("skillId") not in skill_ids:
            err(where, f"unknown skillId {c.get('skillId')}")
        secs = c.get("estimatedSeconds", 0)
        if not isinstance(secs, int) or not 30 <= secs <= 900:
            err(where, "estimatedSeconds must be an int between 30 and 900")
        if len(c.get("hints", [])) < 1:
            err(where, "needs at least one hint")

        t = c.get("type")
        if t in ("MULTIPLE_CHOICE", "IDENTIFY_BUG"):
            opts = c.get("options", [])
            if len(opts) < 2:
                err(where, "needs at least two options")
            if len(set(opts)) != len(opts):
                err(where, "options are not unique")
            ci = c.get("correctIndex")
            if not isinstance(ci, int) or not 0 <= ci < len(opts):
                err(where, "correctIndex out of range")
            if t == "IDENTIFY_BUG" and not c.get("code"):
                err(where, "IDENTIFY_BUG needs code")
        elif t == "ARRANGE_STEPS":
            steps = c.get("steps", [])
            if len(steps) < 3:
                err(where, "ARRANGE_STEPS needs at least three steps")
            if len(set(steps)) != len(steps):
                err(where, "steps are not unique")
        else:
            acc = c.get("acceptedAnswers", [])
            mode = c.get("matchMode")
            if not acc:
                err(where, "needs acceptedAnswers")
            if mode not in MATCH_MODES:
                err(where, f"unknown matchMode {mode}")
            elif len({normalize(a, mode) for a in acc}) != len(acc):
                err(where, "acceptedAnswers collapse to duplicates under matchMode")
            if t in ("PREDICT_OUTPUT", "IDENTIFY_BUG", "FILL_BLANK") and not c.get("code"):
                err(where, f"{t} needs code")
            if t == "FILL_BLANK" and c.get("code", "").count("____") != 1:
                err(where, "FILL_BLANK code must contain exactly one ____ blank")
            v = c.get("verify")
            if v:
                for a in acc:
                    out = run_python(v["template"].replace("{{answer}}", a), f"{where} [{a}]")
                    if out is not None and trimmed(out) != trimmed(v["expectedOutput"]):
                        err(where, f"answer {a!r} prints {trimmed(out)!r}, expected {v['expectedOutput']!r}")
                if t == "PREDICT_OUTPUT" and trimmed(acc[0]) != trimmed(v["expectedOutput"]):
                    err(where, "acceptedAnswers[0] differs from verified output")
                verified += 1

    for pack, c in challenges:
        for p in c.get("prerequisites", []):
            if p not in ids:
                err(f"{pack} {c.get('id')}", f"unknown prerequisite challenge {p}")

    for fname, key in ((manifest["bosses"], "bosses"), (manifest["projects"], "projects")):
        data = load(os.path.join(root, fname)) or {key: []}
        seen = set()
        for item in data[key]:
            if item["id"] in seen:
                err(fname, f"duplicate id {item['id']}")
            seen.add(item["id"])
            if len(item.get("challengeIds", [])) < 3:
                err(f"{fname} {item['id']}", "needs at least three challenges")
            for cid in item.get("challengeIds", []):
                if cid not in ids:
                    err(f"{fname} {item['id']}", f"unknown challenge {cid}")

    print(f"Challenges: {len(challenges)} in {len(manifest['packs'])} packs; executed-and-verified: {verified}")
    for label, (cats, target) in TARGETS.items():
        n = sum(1 for _, c in challenges if c.get("category") in cats)
        status = "ok" if n >= target else "TOO FEW"
        print(f"  {label:28s} {n:3d} (target {target}) {status}")
        if n < target:
            err("curriculum", f"{label}: {n} challenges, need {target}")
    by_type = {}
    for _, c in challenges:
        by_type[c.get("type")] = by_type.get(c.get("type"), 0) + 1
    print("  types:", ", ".join(f"{k}={v}" for k, v in sorted(by_type.items())))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/content")
    if errors:
        print(f"\n{len(errors)} problem(s):")
        for e in errors:
            print("  -", e)
        sys.exit(1)
    print("Content is valid.")
