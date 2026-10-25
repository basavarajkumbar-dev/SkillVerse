from helpers import mc, bug, predict, fill, puzzle, arrange

PY = "Python Automation Engineer Handbook"
API = "API & Protocol Automation Handbook"
CI = "Git, Jenkins & CI/CD Handbook"
TA, AT = "TEST_AUTOMATION", "API_TESTING"
TF, APIS = "test_frameworks", "api_testing"

TESTING = [
    mc("ta-001", TA, TF, "EASY", 90, "What a fixture is",
       "pytest fixtures replace most setUp/tearDown code in automation frameworks.",
       "Which description of a pytest fixture is correct?",
       ["A function that provides set-up (and optional teardown) to tests that request it by parameter name",
        "A decorator that makes a test run twice",
        "A configuration file that lists tests to skip",
        "A plugin that generates HTML reports"], 0,
       ["Tests receive fixtures as arguments.", "Code after yield in a fixture runs as teardown."],
       """A fixture is a function decorated with @pytest.fixture. A test requests it by naming it as a parameter;
       pytest runs the fixture, passes its value in, and runs any code after yield as teardown. Scope controls
       how often it is created.""",
       f"{PY} · Ch. 25 pytest; {API} Q27"),
    predict("ta-002", TA, TF, "MEDIUM", 150, "Counting parametrised cases",
            "Two parametrize decorators are stacked on one test.",
            """
            import pytest

            @pytest.mark.parametrize("fw", ["2.13", "2.14"])
            @pytest.mark.parametrize("iface", ["lan", "lanplus", "serial"])
            def test_sel_clear(fw, iface):
                ...
            """,
            "6",
            ["Stacked parametrize decorators combine as a cross product.", "2 firmware versions × 3 interfaces."],
            """Stacking parametrize decorators generates every combination: 2 × 3 = 6 test cases, each reported with its
            own ID such as test_sel_clear[lan-2.13].""",
            f"{PY} · Ch. 25 pytest (parametrize)", verify=False,
            question="How many test cases does pytest collect from this function? Type a number."),
    fill("ta-003", TA, TF, "EASY", 150, "Fixture teardown",
         "This fixture must close the BMC session after each test, even when the test fails.",
         "Fill the blank so the test receives the session and the close runs afterwards.",
         """
         import pytest

         @pytest.fixture
         def bmc_session():
             session = open_session("bmc1")
             ____ session
             session.close()
         """,
         ["yield"],
         ["return would end the fixture before teardown.", "The keyword pauses the function and resumes it later."],
         """yield hands the session to the test and pauses the fixture. After the test finishes, pass or fail, pytest
         resumes the fixture and the code after yield runs as teardown.""",
         f"{PY} · Ch. 25 pytest (fixtures)"),
    mc("ta-004", TA, TF, "EASY", 90, "conftest.py",
       "Several test modules need the same bmc_session fixture.",
       "Where should the shared fixture live?",
       ["In a conftest.py in the tests directory; pytest discovers it automatically",
        "In every test file, copied",
        "In setup.py",
        "In pytest's site-packages folder"], 0,
       ["No import is needed for fixtures in this file.", "Its fixtures apply to its directory and below."],
       """pytest automatically loads conftest.py files and makes their fixtures and hooks available to tests in that
       directory and its subdirectories, without imports.""",
       f"{PY} · Ch. 25 pytest; {API} Q28"),
    mc("ta-005", TA, TF, "EASY", 90, "Comparing measured values",
       "A test asserts that a measured PSU efficiency equals the expected 0.94.",
       "Which assertion is the right one?",
       ["assert efficiency == pytest.approx(0.94, abs=0.005)",
        "assert efficiency == 0.94",
        "assert str(efficiency) == \"0.94\"",
        "assert efficiency is 0.94"], 0,
       ["Floating-point values are rarely exactly equal.", "pytest has a helper for tolerant comparisons."],
       """pytest.approx compares within a tolerance, which is what measurements need. Exact float equality fails for
       tiny representation errors, string comparison is fragile, and is compares identity.""",
       f"{PY} · Ch. 25 pytest; {API} Q33"),
    bug("ta-006", TA, TF, "MEDIUM", 180, "The assertion that never fails",
        "A status test has never failed, even against a BMC returning 500.",
        "What is the bug?",
        """
        def test_service_root(client):
            resp = client.get("/redfish/v1/")
            assert (resp.status_code == 200, "service root unavailable")
        """,
        ["The parentheses build a non-empty tuple, which is always truthy, so the assert always passes",
         "assert cannot compare integers",
         "The message must come before the condition",
         "client.get must be awaited"], 0,
        ["What type is (a, b)?", "Is a non-empty tuple truthy?"],
        """assert (x, "msg") asserts a two-item tuple, which is always true. Write assert resp.status_code == 200,
        "service root unavailable" without the parentheses. Python even emits a SyntaxWarning for this.""",
        f"{PY} · Ch. 25 pytest; Ch. 37 Code Quality"),
    fill("ta-007", TA, TF, "EASY", 150, "Expecting an exception",
         "A parser must reject a malformed SEL entry with ValueError.",
         "Fill the blank to assert that the exception is raised.",
         """
         import pytest

         def test_rejects_bad_entry():
             with pytest.____(ValueError):
                 parse_sel_entry("garbage")
         """,
         ["raises"],
         ["It is used as a context manager.", "The test fails if no exception occurs."],
         """pytest.raises(ValueError) passes if the block raises ValueError (or a subclass) and fails if it does not.
         Add match="..." to check the message too.""",
         f"{PY} · Ch. 25 pytest"),
    mc("ta-008", TA, TF, "HARD", 150, "Session fixtures and xdist",
       "You start running the suite with pytest -n 4 (pytest-xdist). A session-scoped fixture that powers on the DUT now misbehaves.",
       "Why?",
       ["Each xdist worker is a separate process with its own session, so the fixture runs once per worker",
        "xdist disables fixtures",
        "Session fixtures run only after all tests",
        "xdist runs tests in a random Python version"], 0,
       ["How many processes does -n 4 start?", "Fixture scope is per process."],
       """xdist starts separate worker processes and each one has its own test session, so a session fixture runs in
       every worker. Shared hardware needs a lock (for example a file lock) or one device per worker.""",
       f"{API} Q35; {PY} · Ch. 25"),
    predict("ta-009", TA, TF, "MEDIUM", 150, "Module-scoped setup",
            "Three tests in one module use the same module-scoped fixture.",
            """
            import pytest

            @pytest.fixture(scope="module")
            def firmware_image():
                print("download image")
                return "bmc-2.14.1.bin"

            def test_size(firmware_image): ...
            def test_signature(firmware_image): ...
            def test_version(firmware_image): ...
            """,
            "1",
            ["scope=\"module\" creates the fixture once per module.", "All three tests share the cached value."],
            """A module-scoped fixture is created the first time a test in the module needs it and reused for the
            rest of the module, so the download runs once.""",
            f"{PY} · Ch. 25 pytest (fixture scope)", verify=False,
            question="How many times is \"download image\" printed when this module runs? Type a number."),
    mc("ta-010", TA, TF, "MEDIUM", 120, "Faking the environment",
       "A function reads the BMC address from the BMC_HOST environment variable.",
       "What is the cleanest way to test it?",
       ["Use the monkeypatch fixture: monkeypatch.setenv(\"BMC_HOST\", \"10.0.0.5\")",
        "Edit the real environment of the CI agent",
        "Hard-code the address in the function",
        "Skip the test"], 0,
       ["pytest has a built-in fixture for temporary changes.", "Changes should be undone after the test."],
       """monkeypatch.setenv sets the variable for one test and restores the original value automatically, so tests
       stay isolated.""",
       f"{API} Q31; {PY} · Ch. 26 unittest and Mocking"),
    arrange("ta-011", TA, TF, "EASY", 150, "Inside a pytest run",
            "Order what pytest does when you run a suite.",
            "Put the phases in the order pytest performs them for a test that uses a fixture.",
            ["Collect test files and functions",
             "Run the fixture set-up code",
             "Run the test function",
             "Run the fixture teardown code",
             "Write the results report (for example JUnit XML)"],
            ["Nothing can run before pytest knows what exists.", "Teardown follows the test."],
            """pytest first collects tests, then for each test sets up fixtures, runs the test and tears the fixtures
            down. Reports such as --junitxml are written at the end of the session.""",
            f"{PY} · Ch. 25 pytest; {CI} · Ch. 15 Pytest Reports"),
    bug("ta-012", TA, TF, "MEDIUM", 180, "Order-dependent tests",
        "test_count passes only when test_add runs first.",
        "What is the design flaw?",
        """
        DEVICES = []

        def test_add():
            DEVICES.append("nvme0")
            assert "nvme0" in DEVICES

        def test_count():
            assert len(DEVICES) == 1
        """,
        ["Tests share a module-level list, so one test depends on another's side effects",
         "len() does not work on lists",
         "Test names must start with check_",
         "append() returns None"], 0,
        ["Could test_count pass if run alone?", "Each test should create its own data."],
        """Tests must be independent. test_count relies on state created by test_add, so running it alone, in a
        different order or in parallel fails. Create the data in a fixture so each test gets a fresh list.""",
        f"{PY} · Ch. 37 Code Quality and Testing Standards"),
    mc("ta-013", TA, TF, "EASY", 90, "Selecting by marker",
       "Hardware-dependent tests are marked @pytest.mark.hardware.",
       "What does pytest -m \"not hardware\" do?",
       ["Runs every test except those marked hardware",
        "Runs only the hardware tests",
        "Deletes the hardware marker",
        "Fails if any hardware test exists"], 0,
       ["-m takes a boolean expression over markers.", "not inverts the selection."],
       """-m selects tests by marker expression; not hardware deselects marked tests. This lets CI run the
       simulator-only subset on every commit and the hardware subset on a lab agent.""",
       f"{CI} · Ch. 17 Test Selection and Regression Strategies"),
    puzzle("ta-014", TA, TF, "MEDIUM", 210, "JUnit XML for Jenkins",
           "Jenkins needs a JUnit XML report to show test trends.",
           "Write the pytest command that runs the tests and writes a JUnit XML report to reports/junit.xml.",
           ["pytest --junitxml=reports/junit.xml",
            "python -m pytest --junitxml=reports/junit.xml",
            "pytest --junit-xml=reports/junit.xml",
            "python -m pytest --junit-xml=reports/junit.xml"],
           ["The option name contains junit and xml.", "It takes the output path after an = sign."],
           """--junitxml=PATH (also spelled --junit-xml) writes a JUnit-format report that Jenkins' junit step can
           publish. python -m pytest is equivalent and ensures the right interpreter's pytest is used.""",
           f"{CI} · Ch. 15 Pytest Reports and Test Results", match="NORMALIZED"),
    mc("ta-015", TA, TF, "MEDIUM", 150, "Handling a flaky test",
       "A Redfish test fails about once in twenty nightly runs with a connection reset.",
       "What is the professional response?",
       ["Quarantine it with a tracked issue, collect evidence from failures, and fix the root cause",
        "Wrap the test in a loop that retries until it passes, forever",
        "Delete the test",
        "Mark it as expected to fail permanently"], 0,
       ["Flaky tests erode trust in the whole suite.", "Retries can hide real product bugs."],
       """Quarantining keeps the main signal trustworthy while the issue is investigated. Blind retries can hide a
       real BMC bug such as a web-server crash; deletion loses coverage.""",
       f"{CI} · Ch. 16 Reliable Test Pipelines"),
]

API_CHALLENGES = [
    mc("api-001", AT, APIS, "EASY", 120, "Status for async work",
       "A POST to SimpleUpdate starts a firmware update that will take several minutes.",
       "Which status code best signals \"accepted, still in progress\" with a task to poll?",
       ["202 Accepted", "200 OK", "201 Created", "204 No Content"], 0,
       ["The work has not finished yet.", "Redfish returns a task monitor for long operations."],
       """202 Accepted means the request was accepted for processing but is not complete; Redfish services return a
       Location header pointing to a task monitor. 201 means a resource was created, 204 success with no body.""",
       f"{API} Q6, Q18"),
    mc("api-002", AT, APIS, "MEDIUM", 120, "Do not hard-code resource paths",
       "A test uses /redfish/v1/Systems/1 directly and fails on a new platform.",
       "What should the automation do instead?",
       ["Discover members from the Systems collection's Members @odata.id links",
        "Try /Systems/1 to /Systems/100 until one works",
        "Ask the vendor to rename its system",
        "Parse the HTML web UI"], 0,
       ["Resource IDs are implementation-specific.", "Collections list their members."],
       """Redfish IDs vary by implementation (system, 1, sys0...). Start at the service root, follow the Systems link,
       and use each member's @odata.id, which keeps tests portable across vendors.""",
       f"{API} Q13; Project 1"),
    predict("api-003", AT, APIS, "MEDIUM", 180, "Walk a Redfish collection",
            "Validate a Systems collection response.",
            """
            import json
            body = '{"Members@odata.count": 2, "Members": [{"@odata.id": "/redfish/v1/Systems/sys0"}, {"@odata.id": "/redfish/v1/Systems/sys1"}]}'
            data = json.loads(body)
            ids = [m["@odata.id"].rsplit("/", 1)[-1] for m in data["Members"]]
            print(data["Members@odata.count"] == len(ids), ids[-1])
            """,
            "True sys1",
            ["rsplit(\"/\", 1)[-1] keeps the text after the last slash.", "The count matches the number of members."],
            """The comprehension takes the last path segment of each member link. Checking Members@odata.count against
            the actual list length is a cheap consistency assertion worth keeping in collection tests.""",
            f"{API} · Redfish collections; Q4"),
    puzzle("api-004", AT, APIS, "EASY", 180, "Read the health",
           "Redfish resources report health in a nested Status object.",
           "Write the expression that reads the Health value from the parsed response dict data.",
           ['data["Status"]["Health"]', 'data.get("Status", {}).get("Health")'],
           ["Status is an object inside the resource.", "Index the outer key, then the inner one."],
           """Redfish puts State and Health inside a Status object, so the value is data["Status"]["Health"]. Using
           .get with a default avoids KeyError when validating optional properties.""",
           f"{API} · Redfish resource model; Project 1",
           template="""
           data = {"Id": "sys0", "Status": {"State": "Enabled", "Health": "OK"}}
           print({{answer}})
           """,
           expected_output="OK",
           starter='data = {"Id": "sys0", "Status": {"State": "Enabled", "Health": "OK"}}\n# your expression:',
           test_cases=[{"input": 'data = {"Id": "sys0", "Status": {"State": "Enabled", "Health": "OK"}}', "expected": "OK"}]),
    mc("api-005", AT, APIS, "HARD", 150, "Safe concurrent PATCH",
       "Two automation jobs might PATCH the same AccountService settings at once.",
       "How does Redfish let a client avoid overwriting a newer change?",
       ["Send the resource's ETag in an If-Match header; a mismatch returns 412 Precondition Failed",
        "Add a random sleep before every PATCH",
        "Use PUT instead of PATCH",
        "Send the request twice"], 0,
       ["The server labels each version of a resource.", "Conditional requests compare that label."],
       """GET returns an ETag identifying the resource version. Sending it back in If-Match makes the PATCH
       conditional: if someone changed the resource since, the server answers 412 instead of overwriting.""",
       f"{API} Q9"),
    bug("api-006", AT, APIS, "MEDIUM", 180, "The test that hung all night",
        "A nightly job hung for eight hours when a BMC stopped responding mid-request.",
        "What is the bug?",
        """
        import requests

        def get_power(session, base):
            resp = session.get(f"{base}/redfish/v1/Chassis/chassis0/Power")
            resp.raise_for_status()
            return resp.json()
        """,
        ["The request has no timeout, so requests can wait forever on an unresponsive server",
         "raise_for_status() hangs on errors",
         "f-strings cannot build URLs",
         "resp.json() must be called before raise_for_status()"], 0,
        ["What is requests' default timeout?", "A server that accepts the connection but never answers..."],
        """requests has no default timeout, so a silent BMC blocks forever. Always pass timeout=(connect, read), e.g.
        timeout=(5, 30), and let the framework convert the exception into a clear test failure.""",
        f"{API} Q19; {PY} · Ch. 27 API Testing with Python"),
    mc("api-007", AT, APIS, "EASY", 90, "401 versus 403",
       "A test user can log in but receives an error when trying to change NTP settings.",
       "Which status code describes \"authenticated, but not allowed\"?",
       ["403 Forbidden", "401 Unauthorized", "404 Not Found", "405 Method Not Allowed"], 0,
       ["One code means missing or invalid credentials.", "The other means valid credentials without permission."],
       """401 means the request lacks valid authentication. 403 means the server knows who you are but your role
       lacks the privilege, such as an Operator trying a ConfigureManager action.""",
       f"{API} Q14 (privileges and roles)"),
    arrange("api-008", AT, APIS, "MEDIUM", 180, "Redfish session lifecycle",
            "Session authentication avoids sending the password with every request.",
            "Order the steps of a well-behaved Redfish session.",
            ["POST credentials to /redfish/v1/SessionService/Sessions",
             "Read X-Auth-Token and the Location header from the response",
             "Send X-Auth-Token with each later request",
             "DELETE the session URI from Location when finished"],
            ["You cannot use a token before you have received it.", "Clean up at the end."],
            """Creating a session returns a token and the session's URI. The token authenticates later requests;
            deleting the session at the end frees one of the BMC's limited session slots.""",
            f"{API} Q17; Project 1"),
    mc("api-009", AT, APIS, "MEDIUM", 120, "Self-signed BMC certificates",
       "Lab BMCs use certificates signed by an internal CA, and requests raises SSLError.",
       "What is the correct fix?",
       ["Pass the internal CA bundle: verify=\"/etc/lab/ca.pem\"",
        "Set verify=False everywhere and silence the warnings",
        "Switch the BMC to plain HTTP",
        "Retry until the error disappears"], 0,
       ["Keep TLS verification on.", "Tell requests which CA to trust."],
       """verify accepts a path to a CA bundle, so traffic stays encrypted and authenticated. verify=False disables
       certificate checks and invites man-in-the-middle attacks, even in labs.""",
       f"{API} · Ch. 2.3 TLS, certificates; Ch. 34 Security (Python handbook)"),
    predict("api-010", AT, APIS, "MEDIUM", 210, "Following nextLink",
            "Simulate paging through a large collection.",
            """
            pages = {
                "/p1": {"Members": [1, 2], "Members@odata.nextLink": "/p2"},
                "/p2": {"Members": [3], "Members@odata.nextLink": "/p3"},
                "/p3": {"Members": []},
            }
            url, items = "/p1", []
            while url:
                page = pages[url]
                items += page["Members"]
                url = page.get("Members@odata.nextLink")
            print(len(items), items[-1])
            """,
            "3 3",
            ["The loop stops when get() returns None.", "Page 3 is empty but still visited."],
            """The loop follows Members@odata.nextLink until a page has none. It collects 1, 2 and 3; the last page is
            empty and ends the loop. Always follow nextLink instead of assuming one page holds everything.""",
            f"{API} Q4"),
]

CHALLENGES = TESTING + API_CHALLENGES
