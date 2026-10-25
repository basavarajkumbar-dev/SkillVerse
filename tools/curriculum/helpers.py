"""Authoring helpers. Each helper returns one challenge dict in the bundled JSON schema."""
import textwrap


def _d(s):
    return textwrap.dedent(s).strip("\n") if s else s


def base(id, category, skill, type, difficulty, secs, title, description, instructions, hints, explanation,
         source, code=None, examples=None, prerequisites=None, tags=None, test_cases=None):
    c = {
        "id": id,
        "category": category,
        "skillId": skill,
        "type": type,
        "difficulty": difficulty,
        "estimatedSeconds": secs,
        "title": title,
        "description": description,
        "instructions": instructions,
        "hints": hints,
        "explanation": _d(explanation),
        "source": source,
        "prerequisites": prerequisites or [],
    }
    if code:
        c["code"] = _d(code)
    if examples:
        c["examples"] = examples
    if tags:
        c["tags"] = tags
    if test_cases:
        c["testCases"] = test_cases
    return c


def mc(id, cat, skill, diff, secs, title, desc, question, options, correct, hints, expl, source, code=None, **kw):
    c = base(id, cat, skill, "MULTIPLE_CHOICE", diff, secs, title, desc, question, hints, expl, source, code=code, **kw)
    c["options"] = options
    c["correctIndex"] = correct
    return c


def bug(id, cat, skill, diff, secs, title, desc, question, code, options, correct, hints, expl, source, **kw):
    c = base(id, cat, skill, "IDENTIFY_BUG", diff, secs, title, desc, question, hints, expl, source, code=code, **kw)
    c["options"] = options
    c["correctIndex"] = correct
    return c


def predict(id, cat, skill, diff, secs, title, desc, code, output, hints, expl, source, verify=True, question=None, **kw):
    instr = question or "Type exactly what this program prints. Each print() call is one line."
    c = base(id, cat, skill, "PREDICT_OUTPUT", diff, secs, title, desc, instr, hints, expl, source, code=code, **kw)
    c["acceptedAnswers"] = [_d(output)]
    c["matchMode"] = "TRIMMED"
    if verify:
        c["verify"] = {"template": _d(code), "expectedOutput": _d(output)}
    return c


def fill(id, cat, skill, diff, secs, title, desc, question, code, answers, hints, expl, source,
         expected_output=None, match="CODE", **kw):
    """code contains exactly one ____ blank."""
    c = base(id, cat, skill, "FILL_BLANK", diff, secs, title, desc, question, hints, expl, source, code=code, **kw)
    c["acceptedAnswers"] = answers
    c["matchMode"] = match
    if expected_output is not None:
        c["verify"] = {"template": _d(code).replace("____", "{{answer}}"), "expectedOutput": _d(expected_output)}
    return c


def puzzle(id, cat, skill, diff, secs, title, desc, question, answers, hints, expl, source,
           template=None, expected_output=None, starter=None, match="CODE", **kw):
    c = base(id, cat, skill, "CODE_PUZZLE", diff, secs, title, desc, question, hints, expl, source, code=starter, **kw)
    c["acceptedAnswers"] = answers
    c["matchMode"] = match
    if template is not None:
        c["verify"] = {"template": _d(template), "expectedOutput": _d(expected_output)}
    return c


def arrange(id, cat, skill, diff, secs, title, desc, question, steps, hints, expl, source, **kw):
    """steps are given in the correct order; the app shuffles them deterministically."""
    c = base(id, cat, skill, "ARRANGE_STEPS", diff, secs, title, desc, question, hints, expl, source, **kw)
    c["steps"] = steps
    return c
