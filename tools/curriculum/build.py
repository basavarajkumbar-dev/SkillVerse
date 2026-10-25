"""Builds app/src/main/assets/content/*.json from the authoring packs.

Options of multiple-choice and identify-the-bug items are shuffled with a seed derived from the
challenge id, so correct answers are not always in the same position. The output is stable.
"""
import hashlib
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import pack_python, pack_debugging, pack_testing_api, pack_ops  # noqa: E402

OUT = sys.argv[1]
CONTENT_VERSION = 1
SCHEMA_VERSION = 1


def shuffle_options(c):
    if "options" not in c:
        return c
    seed = int(hashlib.sha256(c["id"].encode()).hexdigest()[:8], 16)
    rng = random.Random(seed)
    idx = list(range(len(c["options"])))
    rng.shuffle(idx)
    correct_text = c["options"][c["correctIndex"]]
    c["options"] = [c["options"][i] for i in idx]
    c["correctIndex"] = c["options"].index(correct_text)
    return c


def with_review_time(c):
    # estimatedSeconds covers solving plus reading the explanation and hints (+90 s).
    c["estimatedSeconds"] = c["estimatedSeconds"] + 90
    return c


def pack(pack_id, title, sources, challenges):
    return {
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": CONTENT_VERSION,
        "packId": pack_id,
        "title": title,
        "sources": sources,
        "challenges": [with_review_time(shuffle_options(dict(c))) for c in challenges],
    }


PY = "Python Automation Engineer Handbook (complete edition, Oct 2026)"
API = "API & Protocol Automation Handbook (Edition 1.0, Oct 2026)"
LX = "Linux, Bash & Embedded Debugging Handbook (Oct 2026)"
CI = "Git, Jenkins & CI/CD Automation Handbook (Edition 1.0, Oct 2026)"

testing = [c for c in pack_testing_api.CHALLENGES if c["category"] == "TEST_AUTOMATION"]
api = [c for c in pack_testing_api.CHALLENGES if c["category"] == "API_TESTING"]
linux_git = [c for c in pack_ops.CHALLENGES if c["category"] in ("LINUX", "GIT")]
emb_ci = [c for c in pack_ops.CHALLENGES if c["category"] in ("EMBEDDED", "DEVOPS")]

packs = {
    "pack_python.json": pack("python", "Python", [PY], pack_python.CHALLENGES),
    "pack_debugging_logic.json": pack("debugging_logic", "Debugging & Logic", [PY, LX], pack_debugging.CHALLENGES),
    "pack_test_automation.json": pack("test_automation", "Test Automation", [PY, CI, API], testing),
    "pack_api_testing.json": pack("api_testing", "API Testing", [API, PY], api),
    "pack_linux_git.json": pack("linux_git", "Linux & Git", [LX, CI], linux_git),
    "pack_embedded_cicd.json": pack("embedded_cicd", "Embedded & CI/CD", [LX, CI, API, PY], emb_ci),
}

skills = {
    "schemaVersion": SCHEMA_VERSION,
    "contentVersion": CONTENT_VERSION,
    "unlockThresholdPercent": 60,
    "nodes": [
        {"id": "python_foundations", "title": "Python Foundations", "glyph": "Py",
         "description": "Types, operators, formatting, slicing and the habits that prevent beginner bugs.",
         "prerequisites": []},
        {"id": "data_structures", "title": "Data Structures & Functions", "glyph": "{}",
         "description": "Lists, dicts, sets, tuples, comprehensions, arguments and sorting.",
         "prerequisites": ["python_foundations"]},
        {"id": "file_log_parsing", "title": "File Handling & Log Parsing", "glyph": "Log",
         "description": "Encodings, CSV/JSON, regular expressions, Counter and generators for big logs.",
         "prerequisites": ["data_structures"]},
        {"id": "automation_python", "title": "Automation with Python", "glyph": "Run",
         "description": "subprocess, exceptions, timeouts, logging and context managers.",
         "prerequisites": ["file_log_parsing"]},
        {"id": "api_testing", "title": "API Testing", "glyph": "API",
         "description": "HTTP status codes, Redfish discovery, sessions, ETags, TLS and pagination.",
         "prerequisites": ["automation_python"]},
        {"id": "test_frameworks", "title": "Test Frameworks", "glyph": "Test",
         "description": "pytest fixtures, parametrisation, markers, reports and reliable suites.",
         "prerequisites": ["api_testing"]},
        {"id": "git_linux", "title": "Git & Linux", "glyph": "$_",
         "description": "Shell safety, permissions, journald and day-to-day Git workflows.",
         "prerequisites": ["test_frameworks"]},
        {"id": "cicd_jenkins", "title": "CI/CD & Jenkins", "glyph": "CI",
         "description": "Pipeline stages, Jenkinsfile post actions, credentials and reproducible builds.",
         "prerequisites": ["git_linux"]},
        {"id": "embedded_firmware", "title": "Embedded Testing & Firmware", "glyph": "FW",
         "description": "UART consoles, the boot chain, version gates and boot failure analysis.",
         "prerequisites": ["cicd_jenkins"]},
        {"id": "advanced_projects", "title": "Advanced Projects", "glyph": "Pro",
         "description": "Hard debugging and algorithm challenges: concurrency, closures, bisection.",
         "prerequisites": ["embedded_firmware"]},
    ],
}

bosses = {
    "schemaVersion": SCHEMA_VERSION,
    "contentVersion": CONTENT_VERSION,
    "bosses": [
        {"id": "boss-syntax-sentinel", "title": "The Syntax Sentinel",
         "description": "Five rapid-fire checks on Python fundamentals and data structures. Clear all five in one run.",
         "challengeIds": ["py-002", "py-007", "py-011", "py-016", "dbg-003"]},
        {"id": "boss-log-leviathan", "title": "The Log Leviathan",
         "description": "Parse, filter and summarise a hostile BMC log. Regex, Counter and generators combined.",
         "challengeIds": ["py-019", "py-021", "py-023", "dbg-005", "py-022"]},
        {"id": "boss-redfish-hydra", "title": "The Redfish Hydra",
         "description": "Discover, authenticate, page and survive a hanging BMC.",
         "challengeIds": ["api-003", "api-008", "api-010", "api-006", "py-030"]},
        {"id": "boss-pipeline-warden", "title": "The Pipeline Warden",
         "description": "Fix a broken test pipeline from assertion to Jenkins credentials.",
         "challengeIds": ["ta-006", "ta-014", "ci-002", "ci-003", "git-003"]},
        {"id": "boss-firmware-golem", "title": "The Firmware Golem",
         "description": "A board will not boot after an update. Read the console and find the fault.",
         "challengeIds": ["emb-002", "emb-004", "emb-003", "lx-003", "emb-005"]},
    ],
}

projects = {
    "schemaVersion": SCHEMA_VERSION,
    "contentVersion": CONTENT_VERSION,
    "projects": [
        {"id": "proj-log-parser", "title": "Log Parser & Error Summary",
         "description": "The core skills of handbook Project P3: split, regex, Counter, generators and clean filtering.",
         "source": "Python Automation Engineer Handbook · Project P3",
         "challengeIds": ["py-019", "py-020", "py-021", "py-022", "py-023", "py-025"]},
        {"id": "proj-redfish-framework", "title": "Redfish API Automation Framework",
         "description": "Discovery, response validation, timeouts, sessions and pagination for a Redfish client.",
         "source": "Python Automation Engineer Handbook · Project P7; API & Protocol Automation Handbook · Project 1",
         "challengeIds": ["api-002", "api-003", "api-004", "api-006", "api-008", "api-010"]},
        {"id": "proj-pytest-framework", "title": "Reusable pytest Framework",
         "description": "Fixtures, conftest, exceptions, scopes and JUnit reporting for a team framework.",
         "source": "Python Automation Engineer Handbook · Project P6",
         "challengeIds": ["ta-001", "ta-003", "ta-004", "ta-007", "ta-009", "ta-014"]},
        {"id": "proj-firmware-validation", "title": "UART & Firmware Validation",
         "description": "Console settings, boot analysis, version gates and journald for device validation.",
         "source": "Python Automation Engineer Handbook · Projects P9–P10",
         "challengeIds": ["emb-001", "emb-003", "emb-004", "emb-005", "lx-003"]},
        {"id": "proj-ci-pipeline", "title": "Python Test Automation CI Pipeline",
         "description": "Branching, stages, reports, credentials and exit codes for a Jenkins pipeline.",
         "source": "Git, Jenkins & CI/CD Handbook · Project 1",
         "challengeIds": ["git-002", "ci-001", "ci-002", "ci-003", "ci-005", "ta-014"]},
    ],
}

manifest = {
    "schemaVersion": SCHEMA_VERSION,
    "contentVersion": CONTENT_VERSION,
    "packs": list(packs.keys()),
    "skills": "skills.json",
    "bosses": "bosses.json",
    "projects": "projects.json",
}

os.makedirs(OUT, exist_ok=True)
for name, data in {**packs, "skills.json": skills, "bosses.json": bosses, "projects.json": projects,
                   "manifest.json": manifest}.items():
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
print("wrote", len(packs), "packs,", sum(len(p["challenges"]) for p in packs.values()), "challenges")
