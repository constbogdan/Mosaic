"""Disposable-repository coverage for the local prepare-pr integrity boundary."""

import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

from tooling_test_support import normalized_native_output


ROOT = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell")


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
            (
                "\x1b]8;;file:///tmp/stage.log\x1b\\[log]\x1b]8;;\x1b\\\n",
                "\x1b]8;;https://example.invalid/pr/63\x1b\\[open]\x1b]8;;\x1b\\\n",
            ): "[log] [open]",
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
        for name in (
            "mosaic_output.ps1",
            "mosaic_validation_policy.py",
            "mosaic_change_classification.py",
        ):
            shutil.copy2(ROOT / "scripts" / name, self.root / "scripts" / name)
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

    def fake_publish_env(
        self,
        remote_sha="",
        pr_mode="create",
        scenario="default",
        list_head_before="",
    ):
        fixture_root = self.root / ".logs" / f"fake-publish-{scenario}"
        tool_dir = fixture_root / "tools"
        tool_dir.mkdir(parents=True, exist_ok=True)
        git_trace = fixture_root / "git.jsonl"
        gh_trace = fixture_root / "gh.jsonl"
        git_proxy = tool_dir / "git_proxy.py"
        gh_proxy = tool_dir / "gh_proxy.py"
        git_proxy.write_text(
            """import json
import os
from pathlib import Path
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
    head = subprocess.check_output(
        [os.environ[\"REAL_GIT\"], \"rev-parse\", \"HEAD\"],
        text=True,
    ).strip()
    (Path(os.environ[\"FAKE_GH_STATE\"]) / \"published-head\").write_text(head)
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
from pathlib import Path
import sys

args = sys.argv[1:]
with open(os.environ[\"FAKE_GH_TRACE\"], \"a\", encoding=\"utf-8\") as stream:
    stream.write(json.dumps(args) + \"\\n\")
state_dir = Path(os.environ[\"FAKE_GH_STATE\"])
created = state_dir / \"created\"
armed = state_dir / \"armed\"
list_count_path = state_dir / \"list-count\"
view_count_path = state_dir / \"view-count\"
mode = os.environ.get(\"FAKE_PR_MODE\", \"create\")


def bump(path):
    value = int(path.read_text() if path.exists() else \"0\") + 1
    path.write_text(str(value))
    return value


def summary(number=63):
    list_count = bump(list_count_path)
    published_head = state_dir / \"published-head\"
    head = published_head.read_text() if published_head.exists() else os.environ[\"FAKE_HEAD\"]
    before = os.environ.get(\"FAKE_LIST_HEAD_BEFORE\", \"\")
    if before and list_count == 1:
        head = before
    return {
        \"number\": number,
        \"url\": f\"https://example.invalid/pr/{number}\",
        \"isDraft\": mode in {\"draft\", \"upstream_draft\"},
        \"headRefOid\": head,
    }


def view():
    view_count = bump(view_count_path)
    published_head = state_dir / \"published-head\"
    head = published_head.read_text() if published_head.exists() else os.environ[\"FAKE_HEAD\"]
    if mode == \"wrong_head_sha\":
        head = \"f\" * 40
    if mode == \"head_drift\" and view_count > 1:
        head = \"e\" * 40
    state = \"OPEN\"
    merged_at = None
    if mode == \"closed\":
        state = \"CLOSED\"
    if mode == \"merged\":
        state = \"MERGED\"
        merged_at = \"2026-09-14T00:00:00Z\"
    head_repository = \"constbogdan/Wholphin\"
    if mode == \"wrong_repo\":
        head_repository = \"someone/Wholphin\"
    return {
        \"number\": 63,
        \"url\": \"https://example.invalid/pr/63\",
        \"state\": state,
        \"isDraft\": mode in {\"draft\", \"upstream_draft\"},
        \"baseRefName\": \"develop\" if mode == \"wrong_base\" else \"main\",
        \"headRefName\": \"other-branch\" if mode == \"wrong_head_branch\" else os.environ[\"FAKE_BRANCH\"],
        \"headRefOid\": head,
        \"headRepository\": {\"nameWithOwner\": head_repository},
        \"headRepositoryOwner\": {\"login\": head_repository.split(\"/\", 1)[0]},
        \"autoMergeRequest\": (
            {\"mergeMethod\": \"SQUASH\" if mode == \"wrong_auto_method\" else \"MERGE\"}
            if mode in {\"already_enabled\", \"wrong_auto_method\"} or armed.exists()
            else None
        ),
        \"mergedAt\": merged_at,
    }


if args[:2] == [\"auth\", \"status\"]:
    raise SystemExit(0)
if args[:2] == [\"pr\", \"list\"]:
    if mode == \"create\" and not created.exists():
        print(\"[]\")
    elif mode == \"ambiguous\":
        print(json.dumps([summary(), summary(64)]))
    else:
        print(json.dumps([summary()]))
    raise SystemExit(0)
if args[:2] == [\"pr\", \"create\"]:
    created.touch()
    print(\"https://example.invalid/pr/63\")
    raise SystemExit(0)
if args[:2] == [\"pr\", \"view\"]:
    print(json.dumps(view()))
    raise SystemExit(0)
if args and args[0] == \"api\":
    print(json.dumps({
        \"full_name\": \"constbogdan/Wholphin\",
        \"allow_auto_merge\": mode != \"settings_disabled\",
        \"allow_merge_commit\": mode != \"merge_disabled\",
        \"allow_squash_merge\": True,
        \"allow_rebase_merge\": True,
    }))
    raise SystemExit(0)
if args[:2] == [\"pr\", \"merge\"]:
    if mode in {\"merge_failure\", \"head_drift_at_mutation\"}:
        message = \"head commit does not match\" if mode == \"head_drift_at_mutation\" else \"auto-merge unavailable\"
        print(message, file=sys.stderr)
        raise SystemExit(1)
    armed.touch()
    print(\"auto-merge enabled\")
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
            "FAKE_GH_STATE": str(fixture_root),
            "FAKE_REMOTE_SHA": remote_sha,
            "FAKE_PR_MODE": pr_mode,
            "FAKE_HEAD": self.git("rev-parse", "HEAD").stdout.strip(),
            "FAKE_BRANCH": self.git("branch", "--show-current").stdout.strip(),
            "FAKE_LIST_HEAD_BEFORE": list_head_before,
        }

    def fake_trace(self, name, scenario="default"):
        path = self.root / ".logs" / f"fake-publish-{scenario}" / f"{name}.jsonl"
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

    def auto_merge_commands(self, scenario="default"):
        return [
            args
            for args in self.fake_trace("gh", scenario)
            if args[:2] == ["pr", "merge"]
        ]

    def create_native_upstream_merge(self):
        first = self.commit_current_worktree("fix: downstream baseline")
        base = self.git("rev-parse", "origin/main").stdout.strip()
        base_tree = self.git("rev-parse", "origin/main^{tree}").stdout.strip()
        upstream = subprocess.run(
            [shutil.which("git"), "commit-tree", base_tree, "-p", base],
            cwd=self.root,
            input="upstream fixture\n",
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        resolved_tree = self.git("rev-parse", "HEAD^{tree}").stdout.strip()
        merge = subprocess.run(
            [shutil.which("git"), "commit-tree", resolved_tree, "-p", first, "-p", upstream],
            cwd=self.root,
            input="merge: resolved upstream fixture\n",
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        self.git("update-ref", "HEAD", merge)
        self.git("branch", "-m", "chore/sync-upstream-fixture")
        return first, upstream, merge, resolved_tree

    def test_guided_success_is_concise_with_readable_link_fallbacks(self):
        env = {
            **self.fake_publish_env(),
            "MOSAIC_TERMINAL_HYPERLINKS": "never",
        }
        result = self.prepare("Guided", env=env, check=False)
        diagnostic = normalized_native_output(result)
        self.assertEqual(0, result.returncode, diagnostic)
        for number, name in (
            (1, "PREFLIGHT"),
            (2, "AUDIT CHANGES"),
            (3, "LOCAL CHECKS"),
            (4, "STAGE CONFIRMED SCOPE"),
            (5, "COMMIT"),
            (6, "PUBLISH"),
        ):
            run_line = next(
                line for line in result.stdout.splitlines()
                if f"[{number}/6] {name} [RUN]" in line
            )
            pass_line = next(
                line for line in result.stdout.splitlines()
                if f"[{number}/6] {name} [PASS]" in line
            )
            self.assertIn("[log:", run_line)
            self.assertNotIn("[log", pass_line)
        self.assertIn("[log: 01-preflight.log]", result.stdout)
        self.assertIn("[log: 06-publish.log]", result.stdout)
        self.assertIn("Scope: 1 path · unknown · high risk", result.stdout)
        self.assertIn(
            "PR #63 created  [open: https://example.invalid/pr/63]",
            result.stdout,
        )
        self.assertIn("Auto-merge: ENABLED", result.stdout)
        self.assertIn("Required CI / Full validation: PENDING", result.stdout)
        self.assertIn(
            "Expected path: Conservative Android Full authoritative validation",
            result.stdout,
        )
        self.assertIn("SUCCESS: prepare-pr completed in", result.stdout)
        self.assertIn("Logs: .logs\\prepare-pr\\", result.stdout)
        for noise in (
            "origin ->",
            "HEAD:",
            "Base:",
            "Tracking:",
            "Known remote branch:",
            "Intended snapshot:",
            "Staged tree:",
        ):
            self.assertNotIn(noise, result.stdout)
        run_dirs = list((self.root / ".logs/prepare-pr").iterdir())
        latest = max(run_dirs, key=lambda path: path.stat().st_mtime_ns)
        self.assertEqual(6, len(list(latest.glob("[0-9][0-9]-*.log"))))
        forensic = (latest / "prepare-pr.log").read_text(encoding="utf-8")
        self.assertIn("Preflight passed. Branch=", forensic)
        self.assertIn("Confirmed publication path: file.txt", forensic)
        self.assertIn("Publication completed.", forensic)

    def test_guided_hyperlinks_use_osc8_and_normalize_to_semantic_labels(self):
        env = {
            **self.fake_publish_env(),
            "MOSAIC_TERMINAL_HYPERLINKS": "always",
        }
        result = self.prepare("Guided", env=env, check=False)
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("\x1b]8;;file:", result.stdout)
        self.assertIn("\x1b]8;;https://example.invalid/pr/63", result.stdout)
        normalized = normalized_native_output(result)
        self.assertIn("[log]", normalized)
        self.assertIn("PR #63 created [open]", normalized)
        self.assertNotIn("\x1b]8;;", normalized)
        stage_lines = [
            line for line in result.stdout.splitlines()
            if "[1/6] PREFLIGHT" in line
        ]
        self.assertIn("[RUN]", stage_lines[0])
        self.assertIn("[log]", stage_lines[0])
        self.assertNotIn("[log]", stage_lines[1])

    def test_expected_hosted_path_uses_existing_policy_for_non_android(self):
        self.git("restore", "--", "file.txt")
        path = self.root / "scripts/test_terminal_fixture.py"
        path.write_text("# tooling fixture\n", encoding="utf-8")
        result = self.prepare(
            "Guided",
            env={
                **self.fake_publish_env(scenario="non-android"),
                "MOSAIC_TERMINAL_HYPERLINKS": "never",
            },
            check=False,
        )
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("Scope: 1 path · tooling-only · normal risk", result.stdout)
        self.assertIn(
            "Expected path: Non-Android authoritative validation",
            result.stdout,
        )

    def test_expected_hosted_path_uses_existing_policy_for_android(self):
        self.git("restore", "--", "file.txt")
        path = self.root / "app/src/main/java/example/Feature.kt"
        path.parent.mkdir(parents=True)
        path.write_text("class Feature\n", encoding="utf-8")
        result = self.prepare(
            "Guided",
            env={
                **self.fake_publish_env(scenario="android"),
                "MOSAIC_TERMINAL_HYPERLINKS": "never",
            },
            check=False,
        )
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("Scope: 1 path · apk-relevant · normal risk", result.stdout)
        self.assertIn(
            "Expected path: Android Full authoritative validation",
            result.stdout,
        )

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
        self.assertIn("PR #63 created", result.stdout)
        self.assertIn("Auto-merge: ENABLED", result.stdout)
        self.assertEqual(reviewed_head, self.git("rev-parse", "HEAD").stdout.strip())
        pushes = [args for args in self.fake_trace("git") if args and args[0] == "push"]
        self.assertEqual([["push", "-u", "origin", "chore/cp4b3-fixture"]], pushes)
        self.assertNotIn("--force", result.stdout + result.stderr)
        merge_commands = self.auto_merge_commands()
        self.assertEqual(1, len(merge_commands))
        self.assertIn("--auto", merge_commands[0])
        self.assertIn("--merge", merge_commands[0])
        self.assertNotIn("--admin", merge_commands[0])
        self.assertEqual(
            reviewed_head,
            merge_commands[0][merge_commands[0].index("--match-head-commit") + 1],
        )

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
        reviewed_head = self.git("rev-parse", "HEAD").stdout.strip()
        result = self.prepare("Publish", env=self.fake_publish_env())
        self.assertIn("Auto-merge: ENABLED", result.stdout)
        self.assertEqual(reviewed_head, self.git("rev-parse", "HEAD").stdout.strip())

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
        diagnostic = normalized_native_output(result)
        self.assertIn("publication would require a force push", diagnostic)
        self.assertIn("[log: 02-publish.log]", diagnostic)
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
        self.assertIn("PR #63 reused  [open: https://example.invalid/pr/63]", result.stdout)
        self.assertIn("Auto-merge: ENABLED", result.stdout)
        gh_commands = self.fake_trace("gh")
        self.assertTrue(any(args[:2] == ["pr", "list"] for args in gh_commands))
        self.assertFalse(any(args[:2] == ["pr", "create"] for args in gh_commands))
        self.assertEqual(1, len(self.auto_merge_commands()))

    def test_new_pr_is_authenticated_before_head_bound_native_auto_merge(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        result = self.prepare("Publish", env=self.fake_publish_env())
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        commands = self.fake_trace("gh")
        create_index = next(i for i, args in enumerate(commands) if args[:2] == ["pr", "create"])
        view_indexes = [i for i, args in enumerate(commands) if args[:2] == ["pr", "view"]]
        merge_index = next(i for i, args in enumerate(commands) if args[:2] == ["pr", "merge"])
        self.assertEqual(2, len(view_indexes))
        self.assertLess(create_index, view_indexes[0])
        self.assertLess(view_indexes[1], merge_index)
        merge = commands[merge_index]
        self.assertEqual(
            reviewed_head,
            merge[merge.index("--match-head-commit") + 1],
        )
        self.assertIn("--auto", merge)
        self.assertIn("--merge", merge)
        self.assertNotIn("--admin", merge)

    def test_already_enabled_exact_auto_merge_is_idempotent(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        result = self.prepare(
            "Publish",
            env=self.fake_publish_env(
                remote_sha=reviewed_head,
                pr_mode="already_enabled",
            ),
        )
        self.assertIn("already enabled for the exact reviewed head", result.stdout)
        self.assertEqual([], self.auto_merge_commands())
        self.assertFalse(any(args and args[0] == "api" for args in self.fake_trace("gh")))

    def test_draft_pr_is_never_armed(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        result = self.prepare(
            "Publish",
            env=self.fake_publish_env(
                remote_sha=reviewed_head,
                pr_mode="draft",
            ),
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("PR is Draft", normalized_native_output(result))
        self.assertEqual([], self.auto_merge_commands())

    def test_upstream_resolution_draft_remains_human_controlled(self):
        first, upstream, _, tree = self.create_native_upstream_merge()
        identity = (
            "-PreserveMergeCommit",
            "-ExpectedMergeFirstParent",
            first,
            "-ExpectedMergeSecondParent",
            upstream,
            "-ExpectedMergeTree",
            tree,
        )
        env = self.fake_publish_env(
            remote_sha=first,
            pr_mode="upstream_draft",
            list_head_before=first,
        )
        result = self.prepare(
            "Guided",
            *identity,
            "-TestFilter",
            "*UpstreamFixtureTest*",
            env=env,
            check=False,
        )
        self.assertEqual(0, result.returncode, normalized_native_output(result))
        self.assertIn("Auto-merge: EXCLUDED", result.stdout)
        gh_commands = self.fake_trace("gh")
        git_commands = self.fake_trace("git")
        self.assertTrue(any(args[:2] == ["pr", "list"] for args in gh_commands))
        self.assertFalse(any(args[:2] == ["pr", "create"] for args in gh_commands))
        self.assertFalse(any(args[:2] == ["pr", "ready"] for args in gh_commands))
        self.assertEqual([], self.auto_merge_commands())
        self.assertFalse(any(args and args[0] == "api" for args in gh_commands))
        self.assertFalse(any("--force" in args for args in git_commands))

    def test_wrong_repository_base_or_head_identity_refuses(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        cases = {
            "wrong_repo": "does not match expected repository",
            "wrong_base": "does not match expected 'main'",
            "wrong_head_branch": "does not match expected 'chore/cp4b3-fixture'",
            "wrong_head_sha": "does not match reviewed published HEAD",
        }
        for mode, expected in cases.items():
            with self.subTest(mode=mode):
                result = self.prepare(
                    "Publish",
                    env=self.fake_publish_env(
                        remote_sha=reviewed_head,
                        pr_mode=mode,
                        scenario=mode,
                    ),
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn(expected, normalized_native_output(result))
                self.assertEqual([], self.auto_merge_commands(mode))

    def test_closed_merged_or_ambiguous_pr_refuses(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        for mode in ("closed", "merged", "ambiguous"):
            with self.subTest(mode=mode):
                result = self.prepare(
                    "Publish",
                    env=self.fake_publish_env(
                        remote_sha=reviewed_head,
                        pr_mode=mode,
                        scenario=mode,
                    ),
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                diagnostic = normalized_native_output(result)
                self.assertTrue(
                    "open unmerged candidate" in diagnostic
                    or "ambiguous PR identity" in diagnostic
                )
                self.assertEqual([], self.auto_merge_commands(mode))

    def test_head_drift_before_or_at_mutation_refuses(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        for mode in ("head_drift", "head_drift_at_mutation"):
            with self.subTest(mode=mode):
                result = self.prepare(
                    "Publish",
                    env=self.fake_publish_env(
                        remote_sha=reviewed_head,
                        pr_mode=mode,
                        scenario=mode,
                    ),
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                diagnostic = normalized_native_output(result)
                self.assertTrue(
                    "does not match reviewed published HEAD" in diagnostic
                    or "could not enable native auto-merge" in diagnostic
                )

    def test_disabled_repository_setting_and_merge_failure_are_actionable(self):
        reviewed_head = self.commit_current_worktree()
        self.prepare("Audit")
        cases = {
            "settings_disabled": "Allow auto-merge' is disabled",
            "merge_disabled": "merge commits are disabled",
            "merge_failure": "could not enable native auto-merge",
            "wrong_auto_method": "unexpected method 'SQUASH'",
        }
        for mode, expected in cases.items():
            with self.subTest(mode=mode):
                result = self.prepare(
                    "Publish",
                    env=self.fake_publish_env(
                        remote_sha=reviewed_head,
                        pr_mode=mode,
                        scenario=mode,
                    ),
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn(expected, normalized_native_output(result))
                commands = self.auto_merge_commands(mode)
                if mode == "merge_failure":
                    self.assertEqual(1, len(commands))
                else:
                    self.assertEqual([], commands)

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
