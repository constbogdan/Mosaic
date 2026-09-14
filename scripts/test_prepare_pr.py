"""Disposable-repository coverage for the local prepare-pr integrity boundary."""

import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell")
ANSI_CONTROL = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")


def normalized_native_output(result):
    """Remove observed PowerShell presentation and retain semantic diagnostics."""
    presentation = ANSI_CONTROL.sub("", (result.stdout or "") + (result.stderr or ""))
    presentation = re.sub(r"(?<!\S)\|(?=\s|$)", " ", presentation)
    return " ".join(presentation.split())


class PreparePrFixtureTest(unittest.TestCase):
    def test_native_diagnostic_normalization_handles_ansi_wrapping_and_columns(self):
        cases = {
            (
                "\x1b[31;1mThe produced commit tree does |\x1b[0m\n",
                "\x1b[31;1mnot match the reviewed staged tree |\x1b[0m\n"
                "\x1b[31;1mpublication is refused.\x1b[0m\n",
            ): (
                "The produced commit tree does not match the reviewed staged tree "
                "publication is refused."
            ),
            ("publication would require a force | push\n", ""): (
                "publication would require a force push"
            ),
            ("branch-only committed paths |\n", "were found\n"): (
                "branch-only committed paths were found"
            ),
            ("existing preserved | native-merge identity arguments\n", ""): (
                "existing preserved native-merge identity arguments"
            ),
        }
        for (stdout, stderr), expected in cases.items():
            with self.subTest(expected=expected):
                result = subprocess.CompletedProcess(
                    args=[],
                    returncode=1,
                    stdout=stdout,
                    stderr=stderr,
                )
                self.assertEqual(expected, normalized_native_output(result))

    def setUp(self):
        if not POWERSHELL:
            self.skipTest("PowerShell is unavailable")
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / ".github").mkdir()
        shutil.copy2(ROOT / "scripts/prepare-pr.ps1", self.root / "scripts/prepare-pr.ps1")
        shutil.copy2(
            ROOT / "scripts/prepare-pr.config.psd1",
            self.root / "scripts/prepare-pr.config.psd1",
        )
        (self.root / ".github/pull_request_template.md").write_text("fixture\n", encoding="utf-8")
        (self.root / ".gitignore").write_text(".logs/\n*.log\n", encoding="utf-8")
        (self.root / "file.txt").write_text("base\n", encoding="utf-8")
        (self.root / "scripts/validate-local.ps1").write_text(
            """[CmdletBinding()]
param(
    [string]$Level,
    [string[]]$TestFilter = @(),
    [string[]]$ChangedPath = @()
)
$repoRoot = Split-Path -Parent $PSScriptRoot
$capture = Join-Path $repoRoot '.logs/fixture-validation.json'
[pscustomobject]@{
    level = $Level
    testFilter = @($TestFilter)
    changedPath = @($ChangedPath)
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $capture -Encoding UTF8
if ($env:PREPARE_PR_FIXTURE_MUTATE) {
    Add-Content -LiteralPath (Join-Path $repoRoot $env:PREPARE_PR_FIXTURE_MUTATE) -Value 'validator mutation'
}
Set-Content -LiteralPath (Join-Path $repoRoot 'validation.log') -Value "fixture $Level checks"
$global:LASTEXITCODE = 0
""",
            encoding="utf-8",
        )
        self.git("init", "--quiet", "--initial-branch=main")
        self.git("config", "user.name", "Fixture User")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("remote", "add", "origin", "https://github.com/constbogdan/Wholphin.git")
        self.git("remote", "add", "upstream", "https://github.com/damontecres/Wholphin.git")
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "fixture baseline")
        self.git("update-ref", "refs/remotes/origin/main", "HEAD")
        self.git("switch", "--quiet", "-c", "chore/cp4b3-fixture")
        (self.root / "file.txt").write_text("reviewed\n", encoding="utf-8")

    def tearDown(self):
        self.temporary.cleanup()

    def git(self, *args, check=True):
        return subprocess.run(
            ["git", *args],
            cwd=self.root,
            check=check,
            capture_output=True,
            text=True,
        )

    def prepare(self, phase, *extra, env=None, check=True):
        command = [
            POWERSHELL,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(self.root / "scripts/prepare-pr.ps1"),
            "-Phase",
            phase,
            "-NoFetch",
            "-NonInteractive",
            *extra,
        ]
        return subprocess.run(
            command,
            cwd=self.root,
            check=check,
            capture_output=True,
            encoding="utf-8",
            errors="replace",
            env={**os.environ, **(env or {})},
        )

    def state(self):
        state_path = self.git("rev-parse", "--git-path", "wholphin-prepare-pr-state.json").stdout.strip()
        return json.loads((self.root / state_path).read_text(encoding="utf-8-sig"))

    def commit_current_worktree(self, message="docs: committed-only fixture"):
        self.git("add", "-A")
        self.git("commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD").stdout.strip()

    def fake_publish_env(self, remote_sha="", pr_mode="create"):
        tool_dir = self.root / ".logs" / "fake-publish" / "tools"
        tool_dir.mkdir(parents=True, exist_ok=True)
        git_trace = self.root / ".logs" / "fake-publish" / "git.jsonl"
        gh_trace = self.root / ".logs" / "fake-publish" / "gh.jsonl"
        git_proxy = tool_dir / "git_proxy.py"
        gh_proxy = tool_dir / "gh_proxy.py"
        git_proxy.write_text(
            """import json
import os
import subprocess
import sys

args = sys.argv[1:]
logical = args[2:] if args[:2] == [\"-c\", \"core.quotepath=false\"] else args
with open(os.environ[\"FAKE_GIT_TRACE\"], \"a\", encoding=\"utf-8\") as stream:
    stream.write(json.dumps(logical) + \"\\n\")
if logical and logical[0] == \"ls-remote\":
    remote_sha = os.environ.get(\"FAKE_REMOTE_SHA\", \"\")
    if remote_sha:
        print(f\"{remote_sha}\\t{logical[-1]}\")
    raise SystemExit(0)
if logical and logical[0] == \"fetch\" and \"origin\" in logical:
    raise SystemExit(0)
if logical and logical[0] == \"push\":
    print(\"simulated push\")
    raise SystemExit(0)
delegated = [
    f\"{arg[:-6]}^{{tree}}\"
    if os.name == \"nt\" and arg.endswith(\"{tree}\") and not arg.endswith(\"^{tree}\")
    else arg
    for arg in args
]
raise SystemExit(subprocess.run([os.environ[\"REAL_GIT\"], *delegated]).returncode)
""",
            encoding="utf-8",
        )
        gh_proxy.write_text(
            """import json
import os
import sys

args = sys.argv[1:]
with open(os.environ[\"FAKE_GH_TRACE\"], \"a\", encoding=\"utf-8\") as stream:
    stream.write(json.dumps(args) + \"\\n\")
if args[:2] == [\"auth\", \"status\"]:
    raise SystemExit(0)
if args[:2] == [\"pr\", \"list\"]:
    if os.environ.get(\"FAKE_PR_MODE\") == \"existing\":
        print(json.dumps([{\"number\": 63, \"url\": \"https://example.invalid/pr/63\", \"isDraft\": False, \"headRefOid\": os.environ[\"FAKE_HEAD\"]}]))
    else:
        print(\"[]\")
    raise SystemExit(0)
if args[:2] == [\"pr\", \"create\"]:
    print(\"https://example.invalid/pr/63\")
    raise SystemExit(0)
print(\"unexpected fake gh command\", file=sys.stderr)
raise SystemExit(2)
""",
            encoding="utf-8",
        )
        if os.name == "nt":
            for name, proxy in (("git", git_proxy), ("gh", gh_proxy)):
                (tool_dir / f"{name}.cmd").write_text(
                    f'@"{sys.executable}" "{proxy}" %*\n',
                    encoding="utf-8",
                )
        else:
            for name, proxy in (("git", git_proxy), ("gh", gh_proxy)):
                wrapper = tool_dir / name
                wrapper.write_text(
                    f'#!/bin/sh\nexec "{sys.executable}" "{proxy}" "$@"\n',
                    encoding="utf-8",
                )
                wrapper.chmod(wrapper.stat().st_mode | stat.S_IXUSR)
        return {
            "PATH": str(tool_dir) + os.pathsep + os.environ["PATH"],
            "REAL_GIT": shutil.which("git"),
            "FAKE_GIT_TRACE": str(git_trace),
            "FAKE_GH_TRACE": str(gh_trace),
            "FAKE_REMOTE_SHA": remote_sha,
            "FAKE_PR_MODE": pr_mode,
            "FAKE_HEAD": self.git("rev-parse", "HEAD").stdout.strip(),
        }

    def fake_trace(self, name):
        path = self.root / ".logs" / "fake-publish" / f"{name}.jsonl"
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

    def test_default_local_checks_are_fast_and_preserve_explicit_filter(self):
        self.prepare("Audit")
        self.prepare("Validate", "-TestFilter", "*FocusedFixtureTest")
        capture = json.loads(
            (self.root / ".logs/fixture-validation.json").read_text(encoding="utf-8-sig")
        )
        self.assertEqual("Fast", capture["level"])
        self.assertEqual(["*FocusedFixtureTest"], capture["testFilter"])
        self.assertIn("file.txt", capture["changedPath"])
        state = self.state()
        self.assertEqual(2, state["version"])
        self.assertEqual("Checked", state["completedPhase"])
        self.assertEqual("Fast", state["localCheckLevel"])
        self.assertNotIn("validationResults", state)

    def test_committed_only_clean_branch_can_publish_without_new_commit(self):
        reviewed_head = self.commit_current_worktree()
        reviewed_tree = self.git("rev-parse", "HEAD^{tree}").stdout.strip()
        self.prepare("Audit")
        state = self.state()
        self.assertTrue(state["committedOnly"])
        self.assertEqual("Committed", state["completedPhase"])
        self.assertEqual(reviewed_head, state["commit"])
        self.assertEqual(reviewed_tree, state["stagedTree"])
        for phase in ("Validate", "Stage", "Commit"):
            result = self.prepare(phase)
            self.assertIn("[SKIP]", result.stdout)
            self.assertEqual(reviewed_head, self.git("rev-parse", "HEAD").stdout.strip())
        result = self.prepare("Publish", env=self.fake_publish_env(), check=False)
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("PR created", result.stdout)
        self.assertEqual(reviewed_head, self.git("rev-parse", "HEAD").stdout.strip())
        pushes = [args for args in self.fake_trace("git") if args and args[0] == "push"]
        self.assertEqual([["push", "-u", "origin", "chore/cp4b3-fixture"]], pushes)
        self.assertNotIn("--force", result.stdout + result.stderr)

    def test_committed_only_multiple_commits_retain_complete_scope(self):
        self.commit_current_worktree("docs: first committed change")
        (self.root / "second.txt").write_text("second\n", encoding="utf-8")
        self.commit_current_worktree("docs: second committed change")
        self.prepare("Audit")
        state = self.state()
        self.assertTrue(state["committedOnly"])
        self.assertEqual(2, len(state["branchCommits"]))
        self.assertEqual(["file.txt", "second.txt"], state["publicationPaths"])

    def test_clean_branch_equal_to_main_still_refuses(self):
        self.git("restore", "--", "file.txt")
        result = self.prepare("Audit", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "No modified, deleted, staged, untracked, or branch-only committed paths were found",
            normalized_native_output(result),
        )

    def test_committed_and_uncommitted_work_uses_normal_commit_path(self):
        first = self.commit_current_worktree("docs: existing branch work")
        (self.root / "second.txt").write_text("candidate\n", encoding="utf-8")
        self.prepare("Audit")
        state = self.state()
        self.assertFalse(state["committedOnly"])
        self.assertEqual("ScopeConfirmed", state["completedPhase"])
        self.assertEqual(["file.txt", "second.txt"], state["publicationPaths"])
        self.prepare("Validate")
        self.prepare("Stage")
        self.prepare("Commit", "-Title", "docs: add candidate work")
        self.assertNotEqual(first, self.git("rev-parse", "HEAD").stdout.strip())
        self.assertEqual("Committed", self.state()["completedPhase"])

    def test_committed_only_remote_divergence_refuses_without_push(self):
        self.commit_current_worktree()
        self.prepare("Audit")
        base = self.git("rev-parse", "origin/main").stdout.strip()
        tree = self.git("rev-parse", "origin/main^{tree}").stdout.strip()
        divergent = subprocess.run(
            [shutil.which("git"), "commit-tree", tree, "-p", base],
            cwd=self.root,
            input="divergent remote\n",
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        result = self.prepare(
            "Publish",
            env=self.fake_publish_env(remote_sha=divergent),
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("publication would require a force push", normalized_native_output(result))
        self.assertFalse(any(args and args[0] == "push" for args in self.fake_trace("git")))

    def test_committed_only_existing_pr_is_reused_without_duplicate(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        result = self.prepare(
            "Publish",
            env=self.fake_publish_env(remote_sha=reviewed_head, pr_mode="existing"),
            check=False,
        )
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("PR: #63 https://example.invalid/pr/63", result.stdout)
        gh_commands = self.fake_trace("gh")
        self.assertTrue(any(args[:2] == ["pr", "list"] for args in gh_commands))
        self.assertFalse(any(args[:2] == ["pr", "create"] for args in gh_commands))

    def test_committed_only_upstream_branch_requires_preserved_merge_identity(self):
        self.commit_current_worktree()
        self.git("branch", "-m", "chore/sync-upstream-fixture")
        result = self.prepare("Audit", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "requires the existing preserved native-merge identity arguments",
            normalized_native_output(result),
        )

    def test_local_check_mutation_refuses_before_staging(self):
        self.prepare("Audit")
        result = self.prepare(
            "Validate",
            env={"PREPARE_PR_FIXTURE_MUTATE": "file.txt"},
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Nothing was staged", normalized_native_output(result))
        self.assertEqual(0, self.git("diff", "--cached", "--quiet", check=False).returncode)
        self.assertEqual("ScopeConfirmed", self.state()["completedPhase"])

    def test_reviewed_stage_and_commit_tree_match_exactly(self):
        self.prepare("Audit")
        self.prepare("Validate")
        self.prepare("Stage")
        staged = self.state()
        self.assertEqual("Staged", staged["completedPhase"])
        self.assertEqual(staged["stagedTree"], self.git("write-tree").stdout.strip())
        self.prepare("Commit", "-Title", "chore: verify prepare fixture")
        committed = self.state()
        self.assertEqual("Committed", committed["completedPhase"])
        self.assertEqual(staged["stagedTree"], self.git("rev-parse", "HEAD^{tree}").stdout.strip())

    def test_commit_hook_tree_mutation_refuses_publication_state(self):
        self.prepare("Audit")
        self.prepare("Validate")
        self.prepare("Stage")
        reviewed_tree = self.state()["stagedTree"]
        hooks = self.root / ".git/hooks"
        hooks.mkdir(parents=True, exist_ok=True)
        hook = hooks / "pre-commit"
        hook.write_text(
            "#!/bin/sh\nprintf '\\nhook mutation\\n' >> file.txt\ngit add -- file.txt\n",
            encoding="utf-8",
        )
        hook.chmod(hook.stat().st_mode | stat.S_IXUSR)
        result = self.prepare(
            "Commit",
            "-Title",
            "chore: trigger hook mutation",
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        diagnostic = normalized_native_output(result)
        self.assertIn("COMMIT [FAIL]", diagnostic)
        self.assertIn("produced commit tree", diagnostic)
        self.assertIn("reviewed staged tree", diagnostic)
        self.assertIn("publication is refused", diagnostic)
        self.assertNotEqual(reviewed_tree, self.git("rev-parse", "HEAD^{tree}").stdout.strip())
        self.assertEqual("Staged", self.state()["completedPhase"])


if __name__ == "__main__":
    unittest.main()
