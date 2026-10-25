from helpers import mc, bug, predict, fill, puzzle, arrange

LX = "Linux, Bash & Embedded Debugging Handbook"
CI = "Git, Jenkins & CI/CD Handbook"
PY = "Python Automation Engineer Handbook"
API = "API & Protocol Automation Handbook"
GL, CJ, EF = "git_linux", "cicd_jenkins", "embedded_firmware"

LINUX_GIT = [
    mc("lx-001", "LINUX", GL, "EASY", 90, "Counting matches",
       "boot.log contains 3 lines with ERROR; one of those lines contains ERROR twice.",
       "What does grep -c \"ERROR\" boot.log print?",
       ["3", "4", "The matching lines themselves", "Nothing; -c only sets the exit code"], 0,
       ["-c counts lines, not occurrences.", "Use grep -o ... | wc -l to count occurrences."],
       """grep -c prints the number of matching lines, so a line with two matches still counts once. To count every
       occurrence, use grep -o "ERROR" boot.log | wc -l.""",
       f"{LX} · Text processing with grep"),
    predict("lx-002", "LINUX", GL, "MEDIUM", 150, "Reading octal permissions",
            "A diagnostic script is installed with chmod 754 collect.sh.",
            "chmod 754 collect.sh",
            "rwxr-xr--",
            ["Each digit is read (4) + write (2) + execute (1).", "Digits are owner, group, others."],
            """7 = 4+2+1 = rwx for the owner, 5 = 4+1 = r-x for the group, 4 = r-- for others. Others can read the script
            but not execute it.""",
            f"{LX} · Ch. 3 Users, Groups and Permissions", verify=False,
            question="Type the 9-character permission string (as shown by ls -l, without the leading file-type character)."),
    fill("lx-003", "LINUX", GL, "EASY", 120, "Follow a service's journal",
         "bmcweb is restarting during a Redfish test and you want to watch its log live.",
         "Fill the blank so journalctl keeps printing new entries for the unit.",
         "journalctl -u bmcweb ____",
         ["-f", "--follow"],
         ["Same letter as tail's live option.", "There is a long form too."],
         """journalctl -u UNIT limits output to one systemd unit and -f (--follow) keeps streaming new entries, like
         tail -f. Add --since "10 min ago" to start from a recent point.""",
         f"{LX} · systemd & journald", match="NORMALIZED"),
    mc("lx-004", "LINUX", GL, "EASY", 90, "Exit status",
       "A Bash wrapper runs a command and then checks $?.",
       "What does $? equal to 0 mean?",
       ["The previous command succeeded", "The previous command printed nothing",
        "The script has no arguments", "The previous command ran for 0 seconds"], 0,
       ["Non-zero exit codes signal failure.", "Automation should check this after every critical step."],
       """$? holds the exit status of the last command: 0 means success, anything else is a failure code. set -e makes
       a script stop at the first failing command.""",
       f"{LX} · Bash automation"),
    bug("lx-005", "LINUX", GL, "MEDIUM", 180, "The dangerous cleanup",
        "A log-rotation script runs as root on a lab controller.",
        "What is the most serious problem?",
        """
        #!/bin/bash
        LOG_DIR=$(get_log_dir)
        rm -rf $LOG_DIR/*
        """,
        ["If LOG_DIR is empty, the command becomes rm -rf /* ; quote it and guard with ${LOG_DIR:?}",
         "The shebang must be #!/bin/sh",
         "rm cannot delete directories",
         "Command substitution is not allowed in assignments"], 0,
        ["What does the command expand to when get_log_dir prints nothing?", "Bash can abort on empty variables."],
        """An empty or unset variable turns the command into rm -rf /*. Use rm -rf -- "${LOG_DIR:?LOG_DIR is empty}"/*
        so the script aborts instead, and add set -euo pipefail at the top.""",
        f"{LX} · Bash safety; safety labels"),
    mc("git-001", "GIT", GL, "EASY", 90, "fetch versus pull",
       "You want to see what teammates pushed without changing your working branch.",
       "Which command does that?",
       ["git fetch", "git pull", "git reset --hard origin/main", "git stash pop"], 0,
       ["One command only downloads.", "The other downloads and merges."],
       """git fetch updates remote-tracking branches such as origin/main without touching your branch or files.
       git pull is fetch followed by merge (or rebase) into the current branch.""",
       f"{CI} · Ch. 1 Git Fundamentals"),
    arrange("git-002", "GIT", GL, "EASY", 150, "Feature branch workflow",
            "You are adding UART retry logic to the shared test framework.",
            "Order the steps of a standard feature-branch workflow.",
            ["git switch -c feature/uart-retry",
             "Edit the files and stage them with git add",
             "git commit -m \"Add UART read retries\"",
             "git push -u origin feature/uart-retry",
             "Open a pull request for review"],
            ["Create the branch before you change anything.", "A pull request needs the branch on the remote."],
            """Branch first so main stays clean, commit your staged work, push the branch with -u to set its upstream,
            then open a pull request so CI and reviewers check the change before it merges.""",
            f"{CI} · Ch. 3 Professional Git Workflows"),
    fill("git-003", "GIT", GL, "MEDIUM", 150, "Undo the last commit",
         "You committed too early and want to undo the last commit but keep its changes staged.",
         "Fill the blank.",
         "git reset ____ HEAD~1",
         ["--soft"],
         ["Three modes: one keeps the index, one keeps only files, one discards everything.", "You want to keep the most."],
         """--soft moves the branch pointer back and leaves the changes staged. --mixed (the default) unstages them;
         --hard discards them. Only reset commits you have not pushed.""",
         f"{CI} · Ch. 5 Git Troubleshooting", match="NORMALIZED"),
    mc("git-004", "GIT", GL, "MEDIUM", 150, "A leaked password",
       "You pushed a commit containing a BMC admin password to a shared repository.",
       "What must happen first?",
       ["Rotate (change) the password, then remove it from history and add secret scanning",
        "Delete the file in a new commit; that is enough",
        "Rename the repository",
        "Force-push and assume nobody saw it"], 0,
       ["Clones and forks may already have the commit.", "History rewriting cannot recall copies."],
       """Once pushed, assume the secret is compromised: change it immediately. Then purge it from history (for
       example with git filter-repo), and prevent recurrences with credentials stores and pre-commit scanning.""",
       f"{CI} · Ch. 5 Git Troubleshooting and Security"),
    mc("git-005", "GIT", GL, "MEDIUM", 120, "Hunting a regression",
       "A test passed at tag v2.13 and fails at HEAD, 300 commits later.",
       "Which Git feature finds the first failing commit most efficiently?",
       ["git bisect", "git blame", "git log --oneline", "git cherry-pick"], 0,
       ["About log2(300) ≈ 9 test runs.", "It performs a binary search over history."],
       """git bisect binary-searches between a good and a bad commit; git bisect run can even execute the test
       automatically at each step. blame shows who last changed each line, which is a different question.""",
       f"{CI} · Ch. 5 Git Troubleshooting"),
]

CICD = [
    mc("ci-001", "DEVOPS", CJ, "EASY", 120, "post { always }",
       "A declarative Jenkinsfile ends with: post { always { junit 'reports/*.xml' } }",
       "Why put the junit step in post { always }?",
       ["So test results are published even when the test stage fails",
        "So the tests run twice",
        "So the build always passes",
        "So Jenkins deletes old reports"], 0,
       ["Failed builds are when you most need the report.", "post conditions run after the stages."],
       """post { always } runs whatever the build result. Publishing JUnit results there means failing builds still
       show which tests failed and keep the trend charts complete.""",
       f"{CI} · Ch. 12 Jenkinsfile Engineering; Ch. 15"),
    arrange("ci-002", "DEVOPS", CJ, "EASY", 150, "Order the pipeline stages",
            "Design a Python test-automation pipeline.",
            "Order the stages from first to last.",
            ["Checkout the repository",
             "Create a virtual environment and install pinned dependencies",
             "Run linters and static checks",
             "Run the pytest suite with --junitxml",
             "Publish JUnit results and archive logs"],
            ["You need the code before you can install anything.", "Fast checks run before slow ones."],
            """Checkout first, then a reproducible environment, then cheap static checks that fail fast, then the
            tests, and finally reporting, which should run even after failures.""",
            f"{CI} · Project 1 Python Test Automation CI Pipeline"),
    bug("ci-003", "DEVOPS", CJ, "HARD", 210, "The leaking credential",
        "This stage works, but the security review flags it.",
        "What is wrong?",
        """
        withCredentials([usernamePassword(credentialsId: 'bmc-admin',
                         usernameVariable: 'BMC_USER', passwordVariable: 'BMC_PASS')]) {
            sh "curl -k -u ${BMC_USER}:${BMC_PASS} https://bmc1/redfish/v1/"
        }
        """,
        ["The double-quoted Groovy string interpolates the secret into the command text; use single quotes so the shell expands it",
         "withCredentials cannot be used inside a stage",
         "curl does not support -u",
         "usernamePassword needs a third variable"], 0,
        ["Who expands ${...} in a double-quoted Groovy string?", "Jenkins warns about insecure interpolation of secrets."],
        """In double quotes Groovy substitutes the values before the shell runs, so the password becomes part of the
        command string, where it can leak into process lists and logs. Use sh 'curl -u "$BMC_USER:$BMC_PASS" ...'
        so the shell reads the environment variables. (-k also disables TLS checks; prefer a CA bundle.)""",
        f"{CI} · Ch. 19 Jenkins Credentials and Security"),
    mc("ci-004", "DEVOPS", CJ, "MEDIUM", 150, "Green locally, red in CI",
       "After a merge, the pipeline fails on import errors that nobody can reproduce locally.",
       "What is the most likely cause?",
       ["Unpinned dependencies resolved to different versions on the CI agent",
        "Jenkins runs Python more slowly",
        "The developer's laptop has more RAM",
        "Git changed the file contents"], 0,
       ["What does pip install -r requirements.txt pick without version pins?", "The agent installs fresh."],
       """Without pinned versions or a lock file, a fresh CI install can pull a newer release with breaking changes.
       Pin versions, use the same Python version, and consider building in a container for reproducibility.""",
       f"{CI} · Ch. 21 Containers and Reproducible CI Environments"),
    fill("ci-005", "DEVOPS", CJ, "MEDIUM", 120, "pytest exit codes",
         "A pipeline step decides whether to mark the build UNSTABLE or FAILED based on pytest's exit code.",
         "Fill the blank with the exit code pytest uses when tests ran and some failed.",
         "pytest exits with code ____ when tests were collected and run but some failed.",
         ["1"],
         ["0 means all passed.", "2 means interrupted, 5 means no tests collected."],
         """pytest returns 0 when all tests pass, 1 when some fail, 2 when interrupted, 3 on internal error, 4 on usage
         error and 5 when no tests were collected. Treating 5 as success is a classic CI mistake.""",
         f"{CI} · Ch. 14 Python Test Execution in CI", match="TRIMMED"),
]

EMBEDDED = [
    mc("emb-001", "EMBEDDED", EF, "EASY", 90, "115200 8N1",
       "A board's console is documented as 115200 8N1.",
       "What does 8N1 mean?",
       ["8 data bits, no parity bit, 1 stop bit", "8 stop bits, no data bits, 1 parity bit",
        "8 kilobaud, 1 channel", "8 retries, no timeout, 1 port"], 0,
       ["The order is data bits, parity, stop bits.", "N stands for none."],
       """8N1 is the most common UART frame: 8 data bits, no parity, 1 stop bit, at 115200 baud. Both ends must
       match exactly.""",
       f"{API} · UART chapter; {LX} · UART debugging"),
    arrange("emb-002", "EMBEDDED", EF, "MEDIUM", 180, "Embedded Linux boot chain",
            "A BMC hangs during boot and you need to know which stage it reached.",
            "Order the boot stages of a typical embedded Linux system.",
            ["Power-on reset and boot ROM",
             "Bootloader (for example U-Boot) starts",
             "Bootloader loads the kernel and device tree",
             "Kernel initialises drivers and mounts the root filesystem",
             "init (systemd) starts user-space services"],
            ["Hardware ROM code runs first.", "Services need a mounted root filesystem."],
            """Knowing the chain tells you where to look: a hang with no U-Boot banner is pre-bootloader, a panic after
            "Starting kernel" is kernel or rootfs, and a hang after systemd starts is a service issue.""",
            f"{LX} · Ch. 1.6 Boot process; kernel & boot debugging"),
    mc("emb-003", "EMBEDDED", EF, "EASY", 120, "Garbled console",
       "The serial console prints random symbols like Ã¿ø÷ instead of boot messages.",
       "What is the most likely cause?",
       ["Baud rate mismatch between the terminal and the board",
        "The kernel is corrupted",
        "The USB cable is too short",
        "The root filesystem is full"], 0,
       ["Data arrives, but it decodes wrongly.", "Check the timing settings first."],
       """Consistent garbage almost always means the terminal samples at a different baud rate than the board
       transmits. Confirm the documented rate (often 115200) and the 8N1 frame before suspecting firmware.""",
       f"{LX} · UART debugging; {API} · UART"),
    bug("emb-004", "EMBEDDED", EF, "MEDIUM", 180, "Version 2.10 is older?",
        "A firmware gate rejects version 2.10, which should satisfy the minimum 2.9.",
        "What is the bug?",
        """
        MIN_VERSION = "2.9"

        def firmware_ok(current):
            return current >= MIN_VERSION
        """,
        ["Version strings are compared character by character, so \"2.10\" < \"2.9\"; compare numeric tuples",
         ">= cannot compare strings",
         "MIN_VERSION must be a float",
         "The function must print the result"], 0,
        ["Compare \"1\" and \"9\" after \"2.\"", "Split on dots and convert parts to int."],
        """String comparison is lexicographic: at the third character "1" < "9", so "2.10" < "2.9". Compare
        tuple(int(p) for p in v.split(".")) or use packaging.version.Version. Converting to float also fails
        (2.10 becomes 2.1).""",
        f"{PY} · Project P10 Firmware-Version Validation"),
    mc("emb-005", "EMBEDDED", EF, "HARD", 150, "Unable to mount root fs",
       "After a firmware update, the console ends with: Kernel panic - not syncing: VFS: Unable to mount root fs.",
       "Where should the investigation start?",
       ["The root filesystem image or partition, and the root= boot arguments passed by the bootloader",
        "The Redfish web server configuration",
        "The test framework's pytest version",
        "The network switch"], 0,
       ["The kernel started but could not find or read its root filesystem.", "Who tells the kernel where root is?"],
       """The kernel booted, so the bootloader worked, but it could not mount the root filesystem. Check that the
       rootfs image was written correctly, that root= and rootfstype in the bootargs match the new layout, and that
       the driver for the storage is built in.""",
       f"{LX} · Kernel and boot debugging; {CI} · Ch. 26 CI Failure Analysis"),
]

CHALLENGES = LINUX_GIT + CICD + EMBEDDED
