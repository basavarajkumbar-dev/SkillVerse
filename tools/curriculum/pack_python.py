from helpers import mc, bug, predict, fill, puzzle, arrange

PY = "Python Automation Engineer Handbook"
C = "PYTHON"
PF, DS, FL, AP = "python_foundations", "data_structures", "file_log_parsing", "automation_python"

CHALLENGES = [
    # ---------------- Python Foundations ----------------
    mc("py-001", C, PF, "EASY", 90, "Hashable inventory keys",
       "Dictionary keys must be hashable. Pick the value that can key a rack-inventory dict.",
       "Which of these values can be used as a dictionary key?",
       ["['rack12', 7]", "('rack12', 7)", "{'rack': 12}", "{'rack12', 7}"], 1,
       ["Only immutable built-in containers are hashable.", "Lists, dicts and sets are all mutable."],
       """A tuple of hashable elements is itself hashable, so ('rack12', 7) works as a key.
       Lists, dicts and sets are mutable and therefore unhashable; using one raises TypeError: unhashable type.""",
       f"{PY} · Ch. 7 Data Structures; Ch. 39 B2"),
    predict("py-002", C, PF, "EASY", 120, "Equal is not identical",
            "Compare two firmware-version lists with == and is.",
            """
            expected = ["2.14", "1.9"]
            actual = ["2.14", "1.9"]
            print(expected == actual, expected is actual)
            """,
            "True False",
            ["== compares values; is compares object identity.", "Two list literals always create two objects."],
            """== calls __eq__ and compares contents, so the lists are equal. is checks whether both names refer to
            the same object; each list literal creates a new object, so the identity check is False.
            Use is only for singletons such as None.""",
            f"{PY} · Ch. 3 Operators; Ch. 39 B3"),
    predict("py-003", C, PF, "EASY", 120, "append vs extend",
            "Build an ipmitool command line and count its parts.",
            """
            cmd = ["ipmitool", "-I", "lanplus"]
            cmd.append(["sdr", "list"])
            print(len(cmd), cmd[-1])
            """,
            "4 ['sdr', 'list']",
            ["append adds exactly one element, whatever it is.", "What is that one element here?"],
            """append adds its argument as a single element, so the inner list becomes the 4th item.
            extend(["sdr", "list"]) would have added two separate strings and produced length 5.""",
            f"{PY} · Ch. 7 Data Structures; Ch. 39 B5"),
    fill("py-004", C, PF, "EASY", 150, "Format a temperature",
         "Print a CPU temperature rounded to one decimal place with an f-string.",
         "Fill the blank so the program prints: CPU0: 41.3 C",
         """
         temp = 41.256
         print(f"CPU0: {temp____} C")
         """,
         [":.1f"],
         ["Format specifiers go after a colon inside the braces.", "f means fixed-point; the number before it is the precision."],
         """The format spec :.1f renders a float in fixed-point notation with one digit after the decimal point,
         rounding 41.256 to 41.3.""",
         f"{PY} · Ch. 4 Input, Output and String Formatting",
         expected_output="CPU0: 41.3 C"),
    mc("py-005", C, PF, "EASY", 120, "A reading of zero is valid",
       "A sensor function returns None when no reading is available and a float otherwise; 0.0 is a valid reading.",
       "Which condition correctly detects a missing reading?",
       ["if not reading:", "if reading is None:", "if reading == False:", "if reading is False:"], 1,
       ["0.0 is falsy.", "Check for the specific sentinel value."],
       """reading is None is true only for the missing case. if not reading would also fire for a valid 0.0 reading,
       because 0.0 is falsy. Comparing with False is wrong for the same reason (0.0 == False is True).""",
       f"{PY} · Ch. 5 Conditional Statements; Ch. 39 B11, B14"),
    predict("py-006", C, PF, "EASY", 120, "Slicing a serial number",
            "Extract the year and the suffix from a board serial number.",
            """
            serial = "SN-2026-AX91"
            print(serial[3:7], serial[-4:])
            """,
            "2026 AX91",
            ["Indexes start at 0 and the stop index is excluded.", "Negative indexes count from the end."],
            """serial[3:7] takes characters 3, 4, 5 and 6, which are "2026". serial[-4:] takes the last four
            characters, "AX91".""",
            f"{PY} · Ch. 2 Variables, Data Types and Memory; Ch. 39 B16"),
    puzzle("py-007", C, PF, "MEDIUM", 240, "Deduplicate, keep order",
           "A scan returns host names with duplicates. Remove the duplicates but keep first-seen order.",
           "Write one expression that evaluates to the deduplicated list, in first-seen order. Use the variable hosts and the dict.fromkeys idiom.",
           ["list(dict.fromkeys(hosts))"],
           ["Dictionaries preserve insertion order.", "dict.fromkeys(iterable) creates one key per unique item."],
           """dict.fromkeys(hosts) keeps the first occurrence of every key in insertion order (guaranteed since
           Python 3.7); wrapping it in list() gives the ordered unique hosts. list(set(hosts)) also removes
           duplicates but loses the order.""",
           f"{PY} · Ch. 7 Data Structures; Ch. 39 B15",
           template="""
           hosts = ["u07", "u03", "u07", "u12", "u03"]
           print({{answer}})
           """,
           expected_output="['u07', 'u03', 'u12']",
           starter='hosts = ["u07", "u03", "u07", "u12", "u03"]\n# your expression:',
           test_cases=[{"input": 'hosts = ["u07", "u03", "u07", "u12", "u03"]', "expected": "['u07', 'u03', 'u12']"}]),
    bug("py-008", C, PF, "EASY", 150, "The disappearing host list",
        "This loop crashes with TypeError: 'NoneType' object is not iterable.",
        "What is the bug?",
        """
        hosts = ["u12", "u03", "u07"]
        hosts = hosts.sort()
        for host in hosts:
            print("checking", host)
        """,
        ["list.sort() sorts in place and returns None, so hosts becomes None",
         "Strings cannot be sorted, so sort() fails",
         "The for loop must use range(len(hosts))",
         "print() cannot take two arguments"], 0,
        ["What does list.sort() return?", "Compare sort() with sorted()."],
        """list.sort() sorts the list in place and returns None. Assigning that result back to hosts throws the list
        away. Either call hosts.sort() on its own line or write hosts = sorted(hosts).""",
        f"{PY} · Ch. 7 Data Structures; Ch. 39 B17"),

    # ---------------- Data Structures and Functions ----------------
    mc("py-009", C, DS, "EASY", 90, "Dictionary lookup cost",
       "Redfish responses become dicts, and automation code looks keys up constantly.",
       "What is the average time complexity of looking up a key in a Python dict?",
       ["O(1)", "O(log n)", "O(n)", "O(n log n)"], 0,
       ["A dict is a hash table.", "The key's hash points straight to its slot."],
       """A dict is a hash table: the key's hash locates its slot directly, so lookup, insertion and deletion
       are O(1) on average. Worst-case collisions are possible but rare with good hashes.""",
       f"{PY} · Ch. 7 Data Structures; Ch. 39 B4"),
    predict("py-010", C, DS, "EASY", 120, "get() with defaults",
            "Read fan speeds where some fans are missing and some report no value.",
            """
            fans = {"fan1": 4200, "fan2": None}
            print(fans.get("fan3", 0), fans.get("fan2", 0))
            """,
            "0 None",
            ["The default is used only when the key is absent.", "Is fan2 absent?"],
            """get() returns the default only when the key is missing. fan3 is missing, so 0 is returned. fan2
            exists with the value None, so None is returned, not the default.""",
            f"{PY} · Ch. 7 Data Structures"),
    predict("py-011", C, DS, "MEDIUM", 180, "The shared default list",
            "A helper records test results in a history list.",
            """
            def record(result, history=[]):
                history.append(result)
                return history

            record("pass")
            print(record("fail"))
            """,
            "['pass', 'fail']",
            ["Default values are evaluated once, when def runs.", "Both calls see the same list object."],
            """The default list is created once at function definition time and reused by every call that omits
            history, so results accumulate. Use history=None and create a new list inside the function.""",
            f"{PY} · Ch. 8 Functions; Ch. 39 I4"),
    fill("py-012", C, DS, "EASY", 150, "Filter hot sensors",
         "Keep only the sensors hotter than 80 °C with a dict comprehension.",
         "Fill the blank so the comprehension iterates over key–value pairs.",
         """
         temps = {"cpu0": 71, "cpu1": 88, "dimm3": 92}
         hot = {name: t for name, t in temps.____() if t > 80}
         print(hot)
         """,
         ["items"],
         ["You need both the key and the value in each iteration.", "keys() and values() give only half."],
         """dict.items() yields (key, value) pairs that unpack into name and t. The comprehension keeps the
         pairs where t > 80.""",
         f"{PY} · Ch. 9 Comprehensions and Iteration",
         expected_output="{'cpu1': 88, 'dimm3': 92}"),
    puzzle("py-013", C, DS, "MEDIUM", 240, "Missing devices",
           "Compare the expected inventory with what the scan found.",
           "Write one expression that returns the devices in expected but not in found, as a sorted list. Both are sets; use the - operator.",
           ["sorted(expected - found)", "sorted(expected.difference(found))"],
           ["Set difference keeps items of the left set that are not in the right set.", "sorted() turns any iterable into a sorted list."],
           """expected - found is the set difference: devices that were expected but not discovered. sorted() makes
           the result deterministic, which matters for readable test reports.""",
           f"{PY} · Ch. 7 Data Structures (sets for comparing inventories)",
           template="""
           expected = {"nvme0", "nvme1", "nic0", "psu1"}
           found = {"nvme0", "psu1"}
           print({{answer}})
           """,
           expected_output="['nic0', 'nvme1']",
           starter='expected = {"nvme0", "nvme1", "nic0", "psu1"}\nfound = {"nvme0", "psu1"}\n# your expression:',
           test_cases=[{"input": "expected = {nvme0, nvme1, nic0, psu1}; found = {nvme0, psu1}", "expected": "['nic0', 'nvme1']"}]),
    predict("py-014", C, DS, "MEDIUM", 150, "*args and **kwargs",
            "A command wrapper forwards extra arguments.",
            """
            def run(cmd, *args, **kwargs):
                print(cmd, len(args), sorted(kwargs))

            run("ipmitool", "sel", "list", timeout=10, retries=2)
            """,
            "ipmitool 2 ['retries', 'timeout']",
            ["*args collects extra positional arguments into a tuple.", "sorted() on a dict sorts its keys."],
            """cmd receives "ipmitool"; the two extra positional strings go into args; the keyword arguments go into
            kwargs. sorted(kwargs) iterates over the dict's keys and returns them alphabetically.""",
            f"{PY} · Ch. 8 Functions; Ch. 39 B7"),
    mc("py-015", C, DS, "MEDIUM", 120, "Can a tuple change?",
       "Consider t = ([], 5).",
       "Which statement is true?",
       ["t[0].append(1) works and changes what t displays",
        "t[0].append(1) raises TypeError because tuples are immutable",
        "t[1] = 6 works because 5 is an int",
        "t can be used as a dictionary key"], 0,
       ["Immutability applies to the tuple's own slots.", "The slot holds a reference to a list."],
       """The tuple's slots cannot be reassigned, but slot 0 refers to a mutable list, which can still be changed.
       Because it contains a list, t is also unhashable and cannot be a dict key.""",
       f"{PY} · Ch. 39 B2"),
    predict("py-016", C, DS, "MEDIUM", 180, "Stable sorting",
            "Order components by priority; two share the same priority.",
            """
            results = [("fan", 3), ("psu", 1), ("cpu", 3), ("nic", 2)]
            print([name for name, _ in sorted(results, key=lambda r: r[1])])
            """,
            "['psu', 'nic', 'fan', 'cpu']",
            ["Python's sort is stable.", "Items with equal keys keep their original relative order."],
            """sorted() orders by the second element. fan and cpu both have key 3; because the sort is stable they
            stay in their original order, fan before cpu.""",
            f"{PY} · Ch. 39 B17 (stable sorts)"),
    bug("py-017", C, DS, "MEDIUM", 180, "Zeros that will not go away",
        "This code is meant to remove all zero readings, but prints [12, 0, 7].",
        "What causes the wrong result?",
        """
        readings = [12, 0, 0, 7]
        for r in readings:
            if r == 0:
                readings.remove(r)
        print(readings)
        """,
        ["Removing items from a list while iterating over it makes the loop skip elements",
         "remove() deletes every matching value, not just one",
         "Comparing integers with == is unreliable",
         "print() is called inside the loop"], 0,
        ["Track the loop's index as items shift left.", "After a removal, the next element moves into the current position."],
        """The loop advances by index. Removing the first 0 shifts the second 0 into the current index, and the loop
        then moves past it. Build a new list instead: readings = [r for r in readings if r != 0].""",
        f"{PY} · Ch. 6 Loops; Ch. 36 Debugging Python Programs"),

    # ---------------- File Handling and Log Parsing ----------------
    mc("py-018", C, FL, "EASY", 90, "Always pass an encoding",
       "with open(path) as f: works on your laptop but fails on a Windows CI agent with UnicodeDecodeError.",
       "What is the most reliable fix?",
       ["Open the file with encoding=\"utf-8\"", "Open the file in binary mode and ignore the content",
        "Wrap the read in a bare except", "Run the script as administrator"], 0,
       ["Without encoding=, Python uses the machine's locale default.", "Different machines, different defaults."],
       """Without an explicit encoding, open() uses the platform's locale encoding, which differs between machines.
       Passing encoding="utf-8" makes the behaviour the same everywhere.""",
       f"{PY} · Ch. 12 File Handling; Ch. 39 B10; Ch. 38 Troubleshooting"),
    predict("py-019", C, FL, "EASY", 150, "Split with a limit",
            "Split a log line into date, time, level and message, keeping colons in the message.",
            """
            line = "2026-10-21 14:02:11 ERROR psu2: input lost: AC"
            date, time, level, msg = line.split(" ", 3)
            print(level, "|", msg)
            """,
            "ERROR | psu2: input lost: AC",
            ["The second argument to split() is the maximum number of splits.", "After 3 splits the rest stays together."],
            """split(" ", 3) splits at most three times, giving exactly four fields. Everything after the third space,
            including further spaces, stays in msg.""",
            f"{PY} · Project P3 Log Parser and Error-Summary Generator"),
    fill("py-020", C, FL, "EASY", 150, "Capture the fan speed",
         "Pull the numeric speed out of a fan log line with a regular expression.",
         "Fill the blank so the program prints 4200.",
         r"""
         import re
         line = "fan3 speed=4200 rpm"
         m = re.search(r"speed=(\d+)", line)
         print(int(m.____(1)))
         """,
         ["group"],
         ["A match object exposes captured groups.", "Group 1 is the first pair of parentheses."],
         """m.group(1) returns the text captured by the first parenthesised group, "4200"; int() converts it.
         Check that m is not None before using it in real parsers.""",
         f"{PY} · Ch. 17 Regular Expressions",
         expected_output="4200"),
    predict("py-021", C, FL, "MEDIUM", 150, "Most common log levels",
            "Summarise log levels for an error report.",
            """
            from collections import Counter
            levels = ["INFO", "ERROR", "WARN", "ERROR", "INFO", "ERROR"]
            print(Counter(levels).most_common(2))
            """,
            "[('ERROR', 3), ('INFO', 2)]",
            ["most_common(n) returns (item, count) tuples.", "Count each level."],
            """Counter counts each level: ERROR 3, INFO 2, WARN 1. most_common(2) returns the two most frequent as a
            list of (item, count) tuples.""",
            f"{PY} · Ch. 18 Standard Library Mastery; Project P3"),
    puzzle("py-022", C, FL, "MEDIUM", 240, "Collect error lines",
           "Keep only the lines containing ERROR, without surrounding whitespace.",
           "Write a list comprehension over lines that keeps lines containing \"ERROR\" and strips each one. Use line as the loop variable.",
           ['[line.strip() for line in lines if "ERROR" in line]',
            '[line.strip() for line in lines if "ERROR" in line.strip()]'],
           ["The filter goes after the for clause.", "strip() removes leading and trailing whitespace, including newlines."],
           """The comprehension filters with if "ERROR" in line and transforms each kept line with strip(), which
           removes the newline and any leading spaces.""",
           f"{PY} · Ch. 9 Comprehensions; Project P3",
           template="""
           lines = ["boot ok\\n", "ERROR fan1 stalled\\n", "  ERROR psu2 lost\\n"]
           print({{answer}})
           """,
           expected_output="['ERROR fan1 stalled', 'ERROR psu2 lost']",
           starter='lines = ["boot ok\\n", "ERROR fan1 stalled\\n", "  ERROR psu2 lost\\n"]\n# your expression:',
           test_cases=[{"input": 'lines = ["boot ok\\n", "ERROR fan1 stalled\\n", "  ERROR psu2 lost\\n"]',
                        "expected": "['ERROR fan1 stalled', 'ERROR psu2 lost']"}]),
    predict("py-023", C, FL, "MEDIUM", 180, "Find completion codes",
            "Extract two-digit hex completion codes from an IPMI trace.",
            r"""
            import re
            trace = "cc=0xD5 retry cc=0x00 len=0x1"
            print(re.findall(r"\b0x[0-9A-Fa-f]{2}\b", trace))
            """,
            "['0xD5', '0x00']",
            ["{2} means exactly two hex digits.", "\\b requires a word boundary after the digits."],
            """findall returns every non-overlapping match. 0x1 has only one hex digit, so it does not match
            0x[0-9A-Fa-f]{2}.""",
            f"{PY} · Ch. 17 Regular Expressions"),
    mc("py-024", C, FL, "EASY", 90, "load vs loads",
       "You have a Redfish response body as a str in memory.",
       "Which call parses it into Python objects?",
       ["json.loads(body)", "json.load(body)", "json.dumps(body)", "json.dump(body)"], 0,
       ["The trailing s stands for string.", "load() expects a file object."],
       """json.loads parses a str; json.load reads from a file-like object. dumps/dump go the other way,
       serialising Python objects to JSON.""",
       f"{PY} · Ch. 13 Working with Structured Data"),
    predict("py-025", C, FL, "MEDIUM", 180, "One pass only",
            "A generator yields the line numbers of errors.",
            """
            def errors(lines):
                for n, line in enumerate(lines, 1):
                    if "ERROR" in line:
                        yield n

            gen = errors(["ok", "ERROR a", "ok", "ERROR b"])
            print(list(gen), list(gen))
            """,
            "[2, 4] []",
            ["enumerate(lines, 1) starts counting at 1.", "What is left in a generator after it has been consumed?"],
            """The first list() consumes the generator and gets [2, 4]. A generator can be iterated only once, so the
            second list() gets nothing.""",
            f"{PY} · Ch. 15 Iterators, Generators; Ch. 39 I1"),
    bug("py-026", C, FL, "MEDIUM", 180, "One test too many",
        "results.csv has a header row and 40 result rows, but the script reports 41 tests executed.",
        "What is the bug?",
        """
        import csv
        with open("results.csv", newline="", encoding="utf-8") as f:
            rows = list(csv.reader(f))
        print(f"{len(rows)} tests executed")
        """,
        ["The header row is counted as a test; use csv.DictReader or skip the first row",
         "newline=\"\" adds an extra empty row",
         "The file must be opened in binary mode for csv",
         "len() counts characters, not rows"], 0,
        ["What is the first row of the file?", "csv.reader does not know which row is the header."],
        """csv.reader returns every row, including the header. csv.DictReader consumes the header as field names,
        or you can call next(reader) before counting. newline="" is the recommended way to open CSV files.""",
        f"{PY} · Ch. 13 Working with Structured Data; Project P4"),

    # ---------------- Automation with Python ----------------
    mc("py-027", C, AP, "MEDIUM", 150, "Running commands safely",
       "A health check pings a host name read from a configuration file.",
       "Which call is the safest?",
       ['subprocess.run(["ping", "-c", "1", host], capture_output=True, text=True, timeout=5)',
        'subprocess.run(f"ping -c 1 {host}", shell=True)',
        'os.system("ping -c 1 " + host)',
        'subprocess.Popen("ping -c 1 " + host, shell=True).wait()'], 0,
       ["Think about a host value like 'x; rm -rf ~'.", "Which option also stops a hung command?"],
       """Passing an argument list without shell=True means the host is never interpreted by a shell, so it cannot
       inject commands. capture_output and text collect output as str, and timeout prevents a hung ping from
       blocking the run.""",
       f"{PY} · Ch. 28 Linux and Command-Line Automation; Ch. 34 Security and Reliability"),
    predict("py-028", C, AP, "MEDIUM", 150, "finally always runs",
            "A check function returns while a cleanup block is pending.",
            """
            def check():
                try:
                    print("try")
                    return "ok"
                finally:
                    print("cleanup")

            print(check())
            """,
            "try\ncleanup\nok",
            ["finally runs before the function actually returns.", "The caller prints the return value last."],
            """The return value is computed, then the finally block runs, then the function returns to the caller,
            which prints "ok". This is why cleanup such as closing a BMC session belongs in finally.""",
            f"{PY} · Ch. 11 Exception Handling; Ch. 39 B6"),
    fill("py-029", C, AP, "MEDIUM", 150, "A clock for timeouts",
         "A wait loop must not break if NTP adjusts the system clock during the test.",
         "Fill the blank with the time function suited to measuring timeouts.",
         """
         import time
         deadline = time.____() + 30
         while not device_ready():
             if time.monotonic() > deadline:
                 raise TimeoutError("device not ready after 30 s")
             time.sleep(1)
         """,
         ["monotonic"],
         ["Wall-clock time can jump backwards or forwards.", "The answer appears later in the same snippet."],
         """time.monotonic() never goes backwards and is unaffected by clock changes, so durations measured with
         it are reliable. time.time() follows the wall clock, which NTP or a user can change mid-test.""",
         f"{PY} · Ch. 18 Standard Library Mastery; API & Protocol Automation Handbook Q24"),
    predict("py-030", C, AP, "MEDIUM", 180, "Custom exception hierarchy",
            "A lab framework defines its own exceptions.",
            """
            class LabError(Exception):
                pass

            class DeviceTimeout(LabError):
                pass

            try:
                raise DeviceTimeout("psu2 did not respond")
            except LabError as e:
                print(type(e).__name__, "-", e)
            """,
            "DeviceTimeout - psu2 did not respond",
            ["An except clause also catches subclasses.", "type(e) is the class that was actually raised."],
            """except LabError catches any subclass, including DeviceTimeout. The object keeps its real class, and
            str(e) is the message passed to the constructor.""",
            f"{PY} · Ch. 11 Exception Handling; Ch. 14 OOP"),
    puzzle("py-031", C, AP, "MEDIUM", 270, "All sensors in range",
           "A thermal check passes only if every reading is between 0 and 85 °C inclusive.",
           "Write one expression that is True when every value in the dict readings is within 0..85. Use all() with a generator expression and the loop variable v.",
           ["all(0 <= v <= 85 for v in readings.values())",
            "all(v >= 0 and v <= 85 for v in readings.values())"],
           ["Chained comparisons like 0 <= v <= 85 work in Python.", "Iterate over readings.values()."],
           """all() returns True only if every item of the generator is truthy, and stops at the first False.
           The chained comparison 0 <= v <= 85 checks both bounds at once.""",
           f"{PY} · Ch. 9 Comprehensions; Project P10 Device-Health Validation",
           template="""
           ok = {"cpu0": 71, "cpu1": 64}
           bad = {"cpu0": 71, "cpu1": 88}
           for readings in (ok, bad):
               print({{answer}})
           """,
           expected_output="True\nFalse",
           starter='readings = {"cpu0": 71, "cpu1": 88}\n# your expression:',
           test_cases=[{"input": '{"cpu0": 71, "cpu1": 64}', "expected": "True"},
                       {"input": '{"cpu0": 71, "cpu1": 88}', "expected": "False"}]),
    mc("py-032", C, AP, "MEDIUM", 120, "Logging the right way",
       "A test helper logs every BMC request at DEBUG level, but DEBUG is normally switched off.",
       "Which call avoids formatting the message when DEBUG is disabled?",
       ['log.debug("GET %s took %.2f s", url, elapsed)',
        'log.debug(f"GET {url} took {elapsed:.2f} s")',
        'log.debug("GET " + url + " took " + str(elapsed))',
        'print("GET", url, elapsed)'], 0,
       ["When is an f-string evaluated?", "logging can defer the % formatting."],
       """With %-style arguments, logging formats the message only if the record is actually emitted. An f-string
       or concatenation is built before the call, even when the level is disabled. print bypasses logging
       configuration entirely.""",
       f"{PY} · Ch. 30 Logging, Reporting and Observability; Ch. 39 B13"),
    predict("py-033", C, AP, "HARD", 210, "A session context manager",
            "A generator-based context manager wraps a BMC session.",
            """
            from contextlib import contextmanager

            @contextmanager
            def session(host):
                print("open", host)
                try:
                    yield host.upper()
                finally:
                    print("close", host)

            with session("bmc1") as s:
                print("using", s)
            """,
            "open bmc1\nusing BMC1\nclose bmc1",
            ["Code before yield is setup; the yielded value is bound by as.", "finally runs when the with block exits."],
            """Entering the with block runs the code up to yield; the yielded value becomes s. When the block ends,
            execution resumes after yield, and the finally clause prints the close message.""",
            f"{PY} · Ch. 15 Context Managers; Ch. 39 I3"),
    bug("py-034", C, AP, "MEDIUM", 180, "The always-healthy sensor",
        "A dashboard shows 0.0 °C for a sensor that is actually disconnected.",
        "What is the bug?",
        """
        def read_temp(sensor):
            try:
                return float(sensor.read())
            except:
                return 0.0
        """,
        ["The bare except swallows every error and returns a plausible-looking value",
         "float() cannot convert sensor output",
         "The function should use a while loop",
         "return cannot be used inside try"], 0,
        ["What happens to the real error?", "Is 0.0 distinguishable from a real reading?"],
        """A bare except catches everything, even KeyboardInterrupt, and hides the real failure behind a valid-looking
        number. Catch the specific exception you expect and either re-raise it or return None so the caller can
        tell a missing reading from 0.0.""",
        f"{PY} · Ch. 11 Exception Handling; Ch. 34 Security and Reliability"),
    arrange("py-035", C, AP, "MEDIUM", 210, "Run a command and check it",
            "Assemble a small script that runs uptime safely and fails loudly on errors.",
            "Put the lines in an order that runs correctly. Indentation is shown.",
            ["import subprocess",
             'result = subprocess.run(["uptime"], capture_output=True, text=True, timeout=5)',
             "if result.returncode != 0:",
             "    raise RuntimeError(result.stderr)",
             "print(result.stdout.strip())"],
            ["Imports come first.", "Check the return code before trusting stdout."],
            """The module is imported, the command runs with a timeout, a non-zero return code raises with the
            captured stderr, and only then is the output used.""",
            f"{PY} · Ch. 28 Linux and Command-Line Automation; Project P8"),
]
