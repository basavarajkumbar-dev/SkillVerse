from helpers import mc, bug, predict, puzzle, arrange

PY = "Python Automation Engineer Handbook"
LX = "Linux, Bash & Embedded Debugging Handbook"
D, L = "DEBUGGING", "LOGIC"
PF, DS, FL, AP, ADV = "python_foundations", "data_structures", "file_log_parsing", "automation_python", "advanced_projects"

CHALLENGES = [
    # Python Foundations
    bug("dbg-001", D, PF, "EASY", 150, "Three retries, two attempts",
        "A connectivity check is supposed to try up to three times, but logs show only two attempts.",
        "What is the bug?",
        """
        max_retries = 3
        for attempt in range(1, max_retries):
            if ping("rack12-u07"):
                break
        """,
        ["range(1, max_retries) stops before 3, so only attempts 1 and 2 run",
         "break exits the loop too late",
         "ping() must be called before the loop",
         "max_retries must be a string"], 0,
        ["range's stop value is excluded.", "List what range(1, 3) produces."],
        """range(1, 3) yields 1 and 2. Use range(1, max_retries + 1) or range(max_retries) to get three attempts.
        Off-by-one errors at loop bounds are among the most common automation bugs.""",
        f"{PY} · Ch. 6 Loops"),
    arrange("dbg-002", L, PF, "EASY", 180, "Build an error counter",
            "Reassemble a function that counts ERROR lines in a log file.",
            "Put the lines in an order that produces a working function. Indentation is shown.",
            ["def count_errors(path):",
             "    count = 0",
             '    with open(path, encoding="utf-8") as f:',
             "        for line in f:",
             '            if "ERROR" in line:',
             "                count += 1",
             "    return count"],
            ["The counter must exist before the loop uses it.", "return goes after the with block finishes."],
            """The counter is initialised, the file is opened with an explicit encoding, each line is checked, and the
            total is returned after the file is closed by the with block.""",
            f"{PY} · Ch. 12 File Handling; Project P3"),
    # Data Structures
    predict("dbg-003", D, DS, "MEDIUM", 180, "The copy that was not a copy",
            "A test copies a config dict before changing the BMC user.",
            """
            base = {"bmc": {"user": "admin"}}
            copy = dict(base)
            copy["bmc"]["user"] = "operator"
            print(base["bmc"]["user"])
            """,
            "operator",
            ["dict() makes a shallow copy.", "The inner dict is shared by both outer dicts."],
            """dict(base) copies only the outer level; both dicts reference the same inner dict, so changing it through
            copy also changes base. Use copy.deepcopy(base) for nested configuration.""",
            f"{PY} · Ch. 20 Memory Management; Ch. 7 Data Structures"),
    bug("dbg-004", D, DS, "EASY", 150, "Status check that sometimes fails",
        "This check passes in the REPL but fails intermittently when the status comes from a parsed response.",
        "What is the bug?",
        """
        status = response_text.split(":")[1].strip()
        if status is "OK":
            mark_passed()
        """,
        ["is compares identity, not value; use == for strings",
         "strip() changes the case of the string",
         "split() always returns one element",
         "mark_passed() must be called before the if"], 0,
        ["Two equal strings are not necessarily the same object.", "Which operator compares values?"],
        """is checks whether both operands are the same object. Interned literals can make it look as if it works,
        but a string built at runtime is a different object. Python 3.8+ even warns: "is" with a literal.""",
        f"{PY} · Ch. 3 Operators; Ch. 39 B3"),
    # File Handling and Log Parsing
    predict("dbg-005", D, FL, "MEDIUM", 180, "Greedy regex",
            "Extract the first component name in angle brackets.",
            """
            import re
            m = re.search(r"<(.+)>", "<fan1> stalled, <psu2> lost")
            print(m.group(1))
            """,
            "fan1> stalled, <psu2",
            [".+ matches as much as it can.", "The final > in the pattern matches the last > in the text."],
            """.+ is greedy: it expands to the end and backtracks only as far as needed to find a >, which is the last
            one. Use the lazy form <(.+?)> or a negated class <([^>]+)> to stop at the first >.""",
            f"{PY} · Ch. 17 Regular Expressions"),
    bug("dbg-006", D, FL, "EASY", 150, "BOOT OK never matches",
        "The UART log clearly contains a line BOOT OK, yet this loop never reports success.",
        "What is the bug?",
        """
        with open("uart.log", encoding="utf-8") as f:
            for line in f:
                if line == "BOOT OK":
                    print("booted")
        """,
        ["Each line still ends with a newline character, so it never equals \"BOOT OK\"",
         "Files must be read with f.read() before looping",
         "The with block closes the file before the loop runs",
         "String comparison is case-insensitive"], 0,
        ["Iterating a file yields lines with their line endings.", "What does repr(line) show?"],
        """Lines from a file keep their trailing "\\n" (and serial captures often have "\\r\\n"). Compare
        line.strip() == "BOOT OK" or use line.rstrip("\\r\\n").""",
        f"{LX} · UART capture analysis; {PY} · Ch. 12"),
    mc("dbg-007", L, FL, "EASY", 150, "Zero errors found?",
       "A parser that searches for \"ERROR\" reports 0 errors, but the log contains lines like \"error: i2c-3 timeout\" and \"Error: PSU2 lost\".",
       "What is the most likely cause?",
       ["The search is case-sensitive", "The log is too large to parse",
        "The file is opened in text mode", "Python cannot search strings for substrings"], 0,
       ["Compare the exact letters.", "\"error\" != \"ERROR\""],
       """Substring checks are case-sensitive. Normalise with line.upper() or use re.search(r"\\berror\\b", line,
       re.IGNORECASE) when the log mixes capitalisations.""",
       f"{PY} · Project P3; {LX} · log analysis"),
    # Automation with Python
    bug("dbg-008", D, AP, "MEDIUM", 180, "The 20-minute timeout",
        "A 60-second power-on wait occasionally runs for 20 minutes on lab machines that sync with NTP.",
        "What is the bug?",
        """
        start = time.time()
        while not powered_on():
            if time.time() - start > 60:
                raise TimeoutError("power-on timed out")
            time.sleep(2)
        """,
        ["time.time() follows the wall clock, which NTP can step backwards; use time.monotonic()",
         "time.sleep(2) is too short",
         "TimeoutError cannot be raised manually",
         "The while condition is evaluated only once"], 0,
        ["What happens to the elapsed time if the clock jumps back?", "Which clock is guaranteed never to go backwards?"],
        """If NTP steps the clock backwards, time.time() - start shrinks and the deadline moves away. time.monotonic()
        is immune to clock changes and is the right choice for measuring durations.""",
        f"{PY} · Ch. 18; API & Protocol Automation Handbook Q24"),
    predict("dbg-009", D, AP, "HARD", 210, "Late-binding lambdas",
            "A list of check callbacks is built in a loop.",
            """
            checks = [lambda: i * 10 for i in range(3)]
            print([c() for c in checks])
            """,
            "[20, 20, 20]",
            ["A lambda looks up i when it is called, not when it is created.", "What is i after the loop finishes?"],
            """Closures capture variables, not values. All three lambdas read i when called, after the loop has left
            it at 2. Bind the value with a default argument: lambda i=i: i * 10.""",
            f"{PY} · Ch. 19 Advanced Functions"),
    arrange("dbg-010", L, AP, "MEDIUM", 180, "Bisect a regression",
            "A sensor regression appeared somewhere between a known-good and a known-bad firmware build.",
            "Order the steps of a binary search for the first bad build.",
            ["Mark the newest failing build as bad and an older passing build as good",
             "Test the build halfway between good and bad",
             "If it fails it becomes the new bad, otherwise it becomes the new good",
             "Repeat until the good and bad builds are adjacent",
             "Report the bad build of the adjacent pair as the first bad build"],
            ["You need two known endpoints before you can halve the range.", "Each test halves the remaining range."],
            """Binary search needs a good and a bad endpoint, then repeatedly tests the midpoint and moves one boundary.
            With N candidate builds it needs about log2(N) tests. git bisect automates exactly this for commits.""",
            f"Git, Jenkins & CI/CD Handbook · Ch. 5 Git Troubleshooting (git bisect)"),
    # Advanced Projects (hard)
    predict("dbg-011", L, ADV, "HARD", 180, "Floor division and banker's rounding",
            "Compute buffer pages and display rounded values.",
            """
            print(7 // 2, -7 // 2, round(2.5), round(3.5))
            """,
            "3 -4 2 4",
            ["// rounds towards negative infinity.", "round() uses round-half-to-even."],
            """Floor division always rounds down, so -7 // 2 is -4, not -3. round() rounds halves to the nearest even
            integer, so 2.5 becomes 2 and 3.5 becomes 4.""",
            f"{PY} · Ch. 3 Operators"),
    bug("dbg-012", D, ADV, "HARD", 240, "Lost updates across threads",
        "Eight threads each call record_pass() 10,000 times, yet the final count is sometimes below 80,000.",
        "What is the root cause?",
        """
        passed = 0

        def record_pass():
            global passed
            passed += 1
        """,
        ["passed += 1 is a read-modify-write that is not atomic; protect it with a threading.Lock",
         "global variables cannot be used from threads",
         "The GIL guarantees += is atomic, so the threads must be crashing",
         "Integers overflow after 65,535"], 0,
        ["+= reads the value, adds one and writes it back.", "Another thread can run between the read and the write."],
        """Even with the GIL, a thread switch can happen between loading passed and storing the new value, so two
        threads can write the same result. Guard the update with a Lock, or have each thread count locally and
        combine the totals.""",
        f"{PY} · Ch. 21 Concurrency and Parallelism; Project P11"),
    predict("dbg-013", D, ADV, "HARD", 210, "Mutating a dict while iterating",
            "A loop adds keys to the dict it is iterating over.",
            """
            d = {"a": 1}
            try:
                for k in d:
                    d["b"] = 2
            except RuntimeError as e:
                print("RuntimeError:", e)
            """,
            "RuntimeError: dictionary changed size during iteration",
            ["Dicts detect size changes during iteration.", "The exception message describes exactly what happened."],
            """Adding a key changes the dict's size, and the iterator raises RuntimeError on its next step. Iterate over a
            copy, for example for k in list(d):, when you need to modify the dict.""",
            f"{PY} · Ch. 7 Data Structures; Ch. 36 Debugging"),
    mc("dbg-014", L, ADV, "MEDIUM", 150, "Passes alone, fails in the suite",
       "test_fan_curve passes when run alone but fails when the whole suite runs.",
       "Which cause is the most likely?",
       ["Another test leaves shared state behind (globals, files, device settings)",
        "pytest runs tests too quickly",
        "The test file name is too long",
        "Assertions are disabled in suite runs"], 0,
       ["What differs between the two runs?", "Earlier tests run first only in the suite run."],
       """Order-dependent failures nearly always mean shared state: a module-level variable, a temp file, an
       environment variable or a device setting changed by an earlier test. Reset state in fixtures, and run the
       suite in random order (for example with the pytest-randomly plugin) to expose hidden dependencies.""",
       f"{PY} · Ch. 25 pytest; Ch. 37 Code Quality"),
    puzzle("dbg-015", L, ADV, "HARD", 300, "Parse key=value pairs",
           "A firmware banner prints space-separated key=value pairs; values may contain dots but not spaces.",
           "Write one expression that turns the string line into a dict. Use dict() with a generator expression, the loop variable pair, and split(\"=\", 1).",
           ['dict(pair.split("=", 1) for pair in line.split())'],
           ["line.split() with no argument splits on any whitespace.", "dict() accepts an iterable of 2-item sequences."],
           """line.split() produces the pairs; pair.split("=", 1) splits each at the first = only, giving a 2-item list
           that dict() uses as key and value. maxsplit=1 keeps any later = characters in the value.""",
           f"{PY} · Ch. 9 Comprehensions; Ch. 42 Coding Interview Practice",
           template="""
           line = "fw=2.14.1 bmc=ok psu=2"
           print({{answer}})
           """,
           expected_output="{'fw': '2.14.1', 'bmc': 'ok', 'psu': '2'}",
           starter='line = "fw=2.14.1 bmc=ok psu=2"\n# your expression:',
           test_cases=[{"input": 'line = "fw=2.14.1 bmc=ok psu=2"', "expected": "{'fw': '2.14.1', 'bmc': 'ok', 'psu': '2'}"}]),
    predict("dbg-016", L, ADV, "MEDIUM", 150, "Floating-point equality",
            "A power calculation compares floats.",
            """
            print(0.1 + 0.2 == 0.3, abs((0.1 + 0.2) - 0.3) < 1e-9)
            """,
            "False True",
            ["0.1 and 0.2 cannot be represented exactly in binary.", "Compare with a tolerance instead."],
            """0.1 + 0.2 evaluates to 0.30000000000000004, so == fails. Compare with a tolerance: math.isclose, an
            absolute bound, or pytest.approx in tests.""",
            f"{PY} · Ch. 2 Data Types; Ch. 25 pytest (approx)"),
    bug("dbg-017", D, ADV, "MEDIUM", 180, "The loop that never ends",
        "When check() keeps failing, this retry loop runs forever instead of stopping after three tries.",
        "What is the bug?",
        """
        retries = 0
        while retries < 3:
            ok = check()
            if ok:
                break
        retries += 1
        """,
        ["retries += 1 is outside the loop body because of its indentation",
         "break should be continue",
         "while loops cannot use <",
         "check() must return a string"], 0,
        ["Look at the indentation of the last line.", "Which statements belong to the while body?"],
        """In Python, indentation defines the block. retries += 1 runs only after the loop ends, so inside the loop
        retries stays 0 forever. Indent it to the loop body level.""",
        f"{PY} · Ch. 6 Loops; Ch. 36 Debugging"),
    arrange("dbg-018", L, ADV, "HARD", 300, "Find the first bad build",
            "Reassemble a binary-search helper that returns the first failing build from an ordered list.",
            "Put the lines in an order that produces a correct function. Indentation is shown.",
            ["def first_bad(builds, is_bad):",
             "    lo, hi = 0, len(builds) - 1",
             "    while lo < hi:",
             "        mid = (lo + hi) // 2",
             "        if is_bad(builds[mid]):",
             "            hi = mid",
             "        else:",
             "            lo = mid + 1",
             "    return builds[lo]"],
            ["The bounds must exist before the loop.", "If mid is bad, the first bad build is at mid or earlier."],
            """When the midpoint is bad, the answer is at mid or before it, so hi = mid. When it is good, the answer is
            after it, so lo = mid + 1. The loop ends when lo == hi, which is the first bad build (assuming the last
            build is bad).""",
            f"{PY} · Ch. 35 Data Structures, Algorithms and Coding Interviews"),
    predict("dbg-019", L, ADV, "MEDIUM", 180, "Natural version order",
            "Sort firmware build names.",
            """
            builds = ["fw10", "fw9", "fw2"]
            print(sorted(builds), sorted(builds, key=lambda s: int(s[2:])))
            """,
            "['fw10', 'fw2', 'fw9'] ['fw2', 'fw9', 'fw10']",
            ["Strings sort character by character.", "The key converts the number part to an int."],
            """Without a key, "fw10" sorts before "fw2" because "1" < "2". Converting the numeric part to int gives
            numeric order. The same trap appears when comparing version strings.""",
            f"{PY} · Ch. 7 Data Structures"),
    mc("dbg-020", L, ADV, "MEDIUM", 150, "The slow allow-list",
       "A script checks 50,000 serial numbers against an allow-list of 20,000 entries stored in a Python list. It takes minutes.",
       "Which change gives the biggest speed-up?",
       ["Convert the allow-list to a set before the loop",
        "Sort the 50,000 serial numbers first",
        "Use a while loop instead of a for loop",
        "Print fewer log messages"], 0,
       ["How expensive is x in some_list?", "Which structure has O(1) membership tests?"],
       """x in list scans the list, so the job is about 50,000 × 20,000 comparisons. A set uses hashing, making each
       membership test O(1) on average and the whole job roughly 50,000 operations.""",
       f"{PY} · Ch. 20 Memory Management and Performance; Ch. 35"),
]
