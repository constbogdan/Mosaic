"""Disposable-repository coverage for the local prepare-pr integrity boundary."""

import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell")


class PreparePrFixtureTest(unittest.TestCase):
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
            text=True,
            env={**os.environ, **(env or {})},
        )

    def state(self):
        state_path = self.git("rev-parse", "--git-path", "wholphin-prepare-pr-state.json").stdout.strip()
        return json.loads((self.root / state_path).read_text(encoding="utf-8-sig"))

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

    def test_local_check_mutation_refuses_before_staging(self):
        self.prepare("Audit")
        result = self.prepare(
            "Validate",
            env={"PREPARE_PR_FIXTURE_MUTATE": "file.txt"},
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Nothing was staged", result.stdout + result.stderr)
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
        self.assertIn("does not match the reviewed staged tree", result.stdout + result.stderr)
        self.assertEqual("Staged", self.state()["completedPhase"])


if __name__ == "__main__":
    unittest.main()
