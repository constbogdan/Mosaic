# T0-1 CP7 performance, latency, and efficiency audit

> **Historical performance evidence.** Measurements and acceptance decisions are point-in-time,
> not current timing guarantees or operating instructions. Current validation authority is
> [VALIDATION.md](VALIDATION.md); related history is indexed in [history](history/README.md).

Status: **CP7 COMPLETE / HOSTED VALIDATED**
Measured: **2026-09-15 through 2026-09-16**
Closure: **CP8 COMPLETE; T0-1 COMPLETE**. Next roadmap phase: **T0-2**.

The original audit records the surviving pipeline, timing evidence, latency causes, and ranked
optimization plan. The final addendum records the one approved optimization, its hosted acceptance,
and the decision that no further CP7 implementation is justified before CP8.

## Executive judgment

The pipeline no longer has a broad duplication problem. CP4 moved complete authority to PR CI,
protected main normally authenticates and reuses that exact result, and local checks are short
feedback. The remaining recurring costs are concentrated:

1. The complete offline suite is now **115-156 seconds hosted**. `test_prepare_pr.py` accounts for
   about **127 seconds** in the current hosted sample. On Windows, the same suite is about **11m40s**;
   `test_prepare_pr.py` is **6m04s** and `test_hosted_upstream.py` is **5m05s**.
2. An `ANDROID_FULL` PR adds **26-42 seconds** of Android/JDK/cache setup and **7m12s-7m20s** of
   Gradle work. The sampled Gradle graph executed 74 of 89 tasks; KSP and Kotlin compilation dominate.
3. An APK-relevant protected-main run adds a distinct **7m36s-9m55s** Release Build. That build is
   not reusable PR Debug work: it allocates final main identity and builds the minified Release
   variant before isolated signing.

Everything else is already small relative to those costs: exact-tree main reuse is **9-12 seconds**,
the no-build eligibility job is **8-12 seconds**, Sign is **26-27 seconds**, Publish is **18-23
seconds**, and normal Upstream Synchronization is **31-37 seconds**. Stable and Hold runner work is
about **50 seconds** each; their larger wall time is human Environment approval, not execution.

CP7.1 addressed offline fixture orchestration without weakening isolation. Its hosted result and the
remaining-candidate decisions are recorded in the final addendum. No Android/Gradle change is
justified from the current samples.

## Surviving lifecycle

```text
local edit
  -> optional validate-local Fast (feedback)
  -> prepare-pr: scope/tree/branch audit + Fast feedback + commit/publish
  -> PR CI / Full validation
       -> changed-range pre-commit
       -> complete offline tooling suite
       -> NON_ANDROID: emit exact-tree policy evidence
       -> ANDROID_FULL: setup + Full defaultDebug + emit evidence/test APK
  -> native auto-merge after repository protection
  -> protected main / Full validation
       -> exact PR/run/parent/tree/class/artifact authentication
       -> exact: reuse
       -> uncertainty: complete conservative fallback
  -> Development eligibility
       -> no build required: stop
       -> APK relevant: Release Build -> release-sign -> Publish Development
  -> optional Stable Promotion: Prepare -> release-promote -> Release
  -> emergency Hold Release: Prepare -> release-hold -> Hold
```

Upstream uses a separate authority path:

```text
Observe (read-only)
  -> fetch + ancestry/ownership/candidate analysis
  -> retained machine observation
Publish (conditional mutation authority)
  -> reauthenticate exact upstream/downstream inputs
  -> re-run current Git/GitHub/candidate checks
  -> reuse/create deterministic candidate or refuse
  -> retained outcome
Conflict Draft
  -> resolve-upstream authenticated local handoff
  -> human/Codex semantic resolution
  -> prepare-pr preserves native two-parent merge and human authority
```

The following apparent repetitions are intentional trust boundaries:

- Build, Sign, and Publish each authenticate the exact artifact/provenance they receive.
- Stable Prepare authenticates the Development candidate; Release rechecks after approval.
- Hold Prepare authenticates current Stable; Hold rechecks `/releases/latest` after approval.
- Upstream Publish does not trust Observe blindly; it reauthenticates current inputs and state.
- Release Build uses final main identity and a Release variant, unlike PR Debug validation.

## Measurement method

No workflow was dispatched and no GitHub or release state was changed. Hosted measurements use
existing Actions run/job/step timestamps and artifact metadata. Local measurements use retained
`.logs` summaries plus a read-only per-module execution of the existing offline runner. Durations are
rounded because Actions step timestamps have one-second granularity.

Latency buckets used below:

| Bucket | Meaning |
|---|---|
| Queue | Run/job created but no runner executing |
| Bootstrap | Checkout, language/toolchain setup, SDK/cache restore |
| Useful execution | Hook, test, Gradle, build, sign, or mutation work |
| Transport | Artifact/cache upload or download |
| Policy/authentication | Classification, exact-tree, provenance, API/ref checks |
| Approval | Human-controlled Environment wait |

## Hosted timing corpus

| Path and evidence | Wall/runner time | Dominant measured components | Judgment |
|---|---:|---|---|
| Docs `NON_ANDROID`, PR #76, run [34986995016](https://github.com/constbogdan/Wholphin/actions/runs/34986995016) | queue 3s; job 2m54s | checkout 3s; policy 1s; pre-commit 6s; offline 2m36s; evidence upload 2s | Offline suite dominates |
| Tooling `NON_ANDROID`, PR #72, run [34894264045](https://github.com/constbogdan/Wholphin/actions/runs/34894264045) | job 2m46s | offline 2m27s | Same bottleneck |
| Older `NON_ANDROID`, PR #62, run [34787725082](https://github.com/constbogdan/Wholphin/actions/runs/34787725082) | job 41s | offline about 22s | Test corpus/process cost has grown materially |
| Tooling/high-risk `ANDROID_FULL`, PR #74, run [34930521117](https://github.com/constbogdan/Wholphin/actions/runs/34930521117) | queue 2s; job 10m33s | pre-commit 5s; offline 2m31s; setup 26s; Gradle 7m20s | Offline and Gradle are serialized |
| `ANDROID_FULL`, PR #68, run [34851599657](https://github.com/constbogdan/Wholphin/actions/runs/34851599657) | job about 10m11s | offline 2m03s; setup 37s; Gradle 7m17s | Confirms stable Gradle cost |
| Genuine APK PR #51, run [34692407899](https://github.com/constbogdan/Wholphin/actions/runs/34692407899) | queue 2s; job 5m59s | pre-commit 5s; setup 50s; then-current targeted Android 4m52s | Historical pre-CP4 contract, useful app-change reference only |
| Protected-main exact reuse, run [34987357967](https://github.com/constbogdan/Wholphin/actions/runs/34987357967) | Full job 9s; no-build job 12s; workflow 27s | checkout 3s; reuse API/Git 3s; eligibility/API about 4s | Effectively solved |
| Protected-main fallback, run [34771225286](https://github.com/constbogdan/Wholphin/actions/runs/34771225286) | Full job 7m48s; workflow 8m16s | pre-commit 21s; offline 24s; setup 51s; Gradle 5m59s | Correct expensive fail-safe; older test corpus |
| APK Development, run [34839462247](https://github.com/constbogdan/Wholphin/actions/runs/34839462247) | workflow 11m09s | reuse 11s; Build 9m55s; Sign 27s; Publish 23s | Release Build is 89% of wall time |
| Genuine APK source `b78fcaa...`, run [34692693013](https://github.com/constbogdan/Wholphin/actions/runs/34692693013), attempt 1 | Full fallback 6m58s; Build 7m36s; Sign 27s | protected-main fallback plus distinct Release variant | Actual app/release reference; Sign failure was Environment configuration |
| Stable, run [34694610864](https://github.com/constbogdan/Wholphin/actions/runs/34694610864) | 3m53s wall; 52s runner | Prepare 38s; approval 2m56s; Release 14s | Human wait dominates |
| Hold, run [34690727709](https://github.com/constbogdan/Wholphin/actions/runs/34690727709) | 3m17s wall; 50s runner | Prepare 38s; approval 2m21s; Hold 12s | Human wait dominates |
| Signing Diagnostic, run [34323962085](https://github.com/constbogdan/Wholphin/actions/runs/34323962085) | 17m26s wall | build 16m43s: setup 38s, pre-commit 25s, offline 7s, Debug 6m04s, Release 9m21s; sign 34s | Historical run; duplicated validation is a simplification candidate |
| Upstream scheduled, run [34961250547](https://github.com/constbogdan/Wholphin/actions/runs/34961250547) | 37s wall | Observe 14s; handoff 3s; Publish 15s | Small; reauthentication is intentional |
| Upstream manual, run [34896625479](https://github.com/constbogdan/Wholphin/actions/runs/34896625479) | 31s wall | Observe 11s; Publish 11s | Small |
| Upstream no delta, run [34603795016](https://github.com/constbogdan/Wholphin/actions/runs/34603795016) | 15s wall | Observe 11s; Publish skipped | Native ancestry exits early |
| Upstream candidate mutation, run [34701161886](https://github.com/constbogdan/Wholphin/actions/runs/34701161886) | 47s wall | Observe 14s; App token/publish 24s | Mutation remains bounded |

Queue time in this corpus is normally **1-6 seconds**. One old dependent no-build job waited 14
seconds. That variance is external scheduling, not validation execution. Environment approval is
shown separately and must not be reported as build/sign latency.

## Local timing corpus

| Path | Samples | Critical cost |
|---|---:|---|
| Fast, docs/tooling | 2.1-6.7s | changed-scope pre-commit 1.6-4.1s; whitespace 0.1-0.4s |
| Fast with one mapped offline module | 6.2-6.3s | mapped module about 3.5s |
| Standard, focused Android, warm | 6.7-12.4s | compile/focused tests 4.3-5.4s |
| Standard, focused Android, cold | 4m46s-5m48s | Gradle compile/focused tests 4m41s-5m45s |
| Full, warm Gradle | 7m35s-7m57s | offline 6m02s-6m23s; Gradle 4.8-5.2s |
| Full, cold Gradle | 10m53s-12m43s | offline 5m29s-7m42s; Gradle 4m22s-5m36s; pre-commit 25-36s |
| prepare-pr working-tree path | about 26-35s | publish 7-18s; local checks 4-8s; other audited stages 1-5s each |
| prepare-pr committed-only path | no clean retained end-to-end timing sample | implementation skips local validation, staging, and commit work; do not invent a number |

Fast and normal prepare-pr are no longer optimization priorities. Cold/warm local Gradle variance is
expected from daemon/build caches; local Full is explicit diagnosis, not the routine publication
gate.

## Critical paths

| Lifecycle | Current critical path | Avoidable/repeated part |
|---|---|---|
| Local Fast | classify -> pre-commit -> optional mapped test -> whitespace | None material |
| prepare-pr | preflight -> audit -> Fast -> stage -> commit -> Git/GitHub publish/auth | Git/GitHub calls dominate once local work is warm; most repeats protect drift/races |
| PR `NON_ANDROID` | queue -> checkout/policy -> pre-commit -> offline -> evidence | Offline fixture execution |
| PR `ANDROID_FULL` | `NON_ANDROID` work -> Android setup -> one Gradle graph -> evidence/APK | Offline and Android are serialized; Gradle compilation misses dominate |
| Main reuse/no build | queue -> checkout/reuse -> dependent eligibility job | About 27s total; no meaningful target |
| Main fallback | pre-commit -> offline -> setup -> Gradle -> eligibility | Deliberately complete fail-safe |
| APK Development | main validation/reuse -> eligibility+Release Build -> Sign -> Publish | Release assembly; job boundaries are authority boundaries |
| Stable/Hold | Prepare authentication -> approval -> mutating recheck/action | Approval is deliberate; execution is already short |
| Upstream | Observe -> artifact/output handoff -> Publish reauthentication | Two jobs are permission boundaries; duplicated authentication is intentional |

## Offline tooling profile

The current runner discovers 14 `test_*.py` modules, executes them serially in one Python process,
uses `buffer=True`, and removes `GITHUB_STEP_SUMMARY`/`GITHUB_OUTPUT` from test environments. Output
is therefore isolated correctly; the cost is fixture/process execution, not summary leakage.

### Per-module evidence

| Module | Windows seconds | Hosted indication | Main cost |
|---|---:|---:|---|
| `test_prepare_pr.py` | 364.2 | about 126.7s | 24 isolated repositories; repeated native PowerShell process and Git/GitHub-proxy scenarios |
| `test_hosted_upstream.py` | 304.9 | about 10.4s | 54 isolated Git topologies; Windows process/filesystem startup dominates locally |
| `test_mosaic_version.py` | 16.5 | about 0.5s | disposable Git history |
| `test_resolve_upstream.py` | 6.2 | about 2.6s | wrapper/native merge fixtures |
| `test_mosaic_validation_policy.py` | 5.1 | about 9.6s | validation integration invokes scripts/PowerShell |
| `test_mosaic_change_classification.py` | 3.3 | about 0.1s | disposable Git classification |
| `test_mosaic_signing_exercise.py` | 2.0 | under 0.1s | subprocess/static contract fixtures |
| remaining seven modules | each 0.3-0.7 | each under 0.1s | in-memory/API fakes and parsers |

The measured Windows module total is about **11m40s**. Hosted module timestamps are approximate
because `unittest` emits each result line after the test and Actions timestamps output lines, not
internal test clocks. They nevertheless explain the 156-second hosted step: `test_prepare_pr.py`
dominates, not `test_hosted_upstream.py`.

The slowest hosted fixture intervals in the sample were:

| Fixture | Approximate seconds |
|---|---:|
| disabled repository setting / merge failure diagnostics | 13.7 |
| wrong repository/base/head refusal variants | 12.7 |
| closed/merged/ambiguous PR refusal variants | 10.5 |
| committed plus uncommitted normal path | 9.3 |
| committed-only clean publication | 8.7 |
| head drift refusal variants | 7.4 |
| commit-hook tree mutation refusal | 6.8 |
| reviewed staged/commit tree equality | 6.0 |

These are valuable end-to-end safety fixtures. Their semantic coverage should remain. The likely
waste is orchestration: every prepare-pr test builds a repository from scratch with at least nine
setup Git processes, and many tests start PowerShell repeatedly to prove cross-invocation state.
The latter is partly the contract; replacing it with in-process unit calls would lose real CLI,
PowerShell, Git, and persisted-state coverage.

The safe optimization order is:

1. add stable per-module/per-test timing to the runner output;
2. build immutable fixture seeds once, then copy/clone them into a fresh mutable directory per test;
3. run independent modules in bounded processes;
4. only if still material, shard independent `test_prepare_pr.py` test IDs across bounded processes.

Threads are inappropriate because tests patch process-wide environment variables. Shared mutable
Git repositories are also inappropriate. Each test must retain a private worktree, refs, logs,
state, and fake GitHub trace.

## Pre-commit

The sampled PR step took 5-6 seconds. It spent about **3.6 seconds** installing the `pre-commit`
Python package, **0.7 seconds** restoring a roughly **10 MB** hook cache, and **1.1 seconds** running
the changed-range hooks. The cache key includes the Python location and `.pre-commit-config.yaml`
content; hooks themselves are revision-pinned.

This is adequately cached. Replacing it to save a few seconds is not justified until offline and
Gradle work are addressed. A maintenance concern remains: the pinned `pre-commit/action` composite
installs the current unpinned pre-commit engine and itself references `actions/cache@v4`. That is
dependency/runtime drift, not a current performance bottleneck.

## Android and Gradle

The shared setup correctly runs only on Android paths. The PR #74 setup took 26 seconds:

- Zulu JDK 21 patch resolution/download was about 6 seconds;
- an approximately **801 MB** Gradle cache was found and restored;
- Android command-line tools/licenses and the pinned `platform-tools`, Build Tools 36.0.0, and NDK
  29.0.14206865 were checked/installed;
- setup emitted large license text, which is log noise but not the main wall-clock cost.

The cache is not proven waste. In the sampled Full graph, 15 tasks came from cache. Removing it
without a cache-miss A/B run would trade reproducibility/network resilience for an unproven saving.
The action and package versions are SHA/version pinned, while `ubuntu-latest`, Python `3.14`, and
Java `21` still float within their supported images/patch lines.

The PR #74 Gradle invocation was already one graph:

```text
compileDefaultDebugKotlin + testDefaultDebugUnitTest + assembleDefaultDebug
```

Gradle reported **89 actionable tasks: 74 executed, 15 from cache**, and stored a configuration-cache
entry. There is no `clean`; project parallelism and build/configuration caches are enabled. The
ephemeral workspace does not currently demonstrate reuse of the stored project configuration cache.

Approximate sampled costs inside the 7m19s Gradle run:

- startup/configuration/dependency graph: about 20s;
- main KSP plus Kotlin compilation: about 3m20s;
- resources, manifests, dexing, Hilt, packaging, and other graph work: about 2m30s;
- unit-test KSP/compile and test execution: about 1m;

PR #74 did not change application or Gradle source, yet main KSP/Kotlin still executed. The generated
`BuildConfig.SOURCE_SHA` changes for every tested commit and is a plausible invalidation source. This
was not proof; the original audit therefore required otherwise-identical builds and task-input/cache
evidence before any application identity change. The final decision remains DEFER.

Release Build must remain separate. It runs on final protected main, allocates `1.0.N`, embeds exact
source identity, and assembles minified/resource-shrunk `defaultRelease` bytes. Its
`--no-parallel --max-workers=1` boundary is evidence-based: a previous concurrent Debug/Release
compiler run exhausted hosted memory. PR Debug bytes cannot replace it.

## Reuse, delivery, and API cost

Exact-tree reuse performs PR association, workflow/run/job/step lookup, unique unexpired artifact
lookup, Git commit parent/tree authentication, validation-class authentication, and final-tree
comparison in about three seconds after checkout. It queries artifact metadata; it does not download
the evidence archive on main. Further shortening this path is not worth weakening or redesigning it.

Development eligibility takes only a few seconds of script/API work, but it occupies a separate
8-12 second job because Build must depend on a completed successful `Full validation` job. Moving
eligibility inside the still-running validation job would lose that completed-job authentication.
Splitting it again would add another runner handoff. Keep it.

Release publication currently scans Release inventory and authenticates exact tags/assets before and
after mutation. As immutable builds exceed GitHub API page sizes, exact-tag lookups might eventually
scale better, but current Publish is only 12 seconds of API work. The inventory also detects
ambiguity. Do not replace it until paging is a measured cost and ambiguity equivalence is proven.

## Artifact transport

| Artifact | Observed size | Retention/consumer | Decision |
|---|---:|---|---|
| `NON_ANDROID` PR policy evidence | 635 bytes | 7d; main authenticates metadata identity/digest | KEEP |
| `ANDROID_FULL` policy evidence + Debug APK | 54.6 MB | 7d; main authenticates evidence and operators may install exact tested APK | KEEP; upload was about 1s |
| unsigned Development APK | 27.8 MB | 7d; exact Build -> Sign ID/digest | KEEP |
| Release mapping | 9.5 MB compressed | 7d; diagnostic/deobfuscation output | KEEP |
| signed Development APK | 27.8 MB | 7d; exact Sign -> Publish ID/digest | KEEP |
| Stable/Hold prepared APK | about 27.7 MB | 7d; exact Prepare -> approved mutation boundary | KEEP |
| upstream observation/outcome | about 6 KB each | 14d; durable machine evidence | KEEP |

Uploads/downloads are typically 1-4 seconds. Replacing authenticated artifacts with mutable job
outputs would remove byte transport but also remove the exact ID/digest boundary; that is not an
acceptable optimization.

## Live operator-feedback latency

Feedback latency is not execution latency. In PR #76:

```text
run created                         T+0s
runner started                      T+3s
checkout complete                   T+9s
classification complete             T+10s
Validation path - Non-Android log    T+10s
job complete / overview summary      T+2m54s
```

The current dynamic step name and its immediate log are trustworthy because they consume the same
authoritative `Choose PR validation path` outputs. No second classifier or durable state exists.

GitHub uploads per-step summary content and presents it as the job summary when the job finishes;
it is therefore not a reliable workflow-overview signal during the running job. GitHub also does not
allow a job's display name to consume outputs from its own steps: job-name expressions can use
`needs` outputs only from an earlier job. See GitHub's [workflow commands documentation](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/workflow-commands-for-github-actions?tool=powershell)
and [contexts reference](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts).

Possible earlier overview signals all add cost or authority surface:

- a separate classifier job plus dynamic downstream job names adds a runner/queue boundary and
  migrates the required-check/evidence graph;
- a custom Check Run needs new write permission and external state;
- a no-op presentation job duplicates an ephemeral fact without improving validation.

Therefore **KEEP the current dynamic step-name/live-log solution**. The delayed overview/job summary
is a GitHub UI limitation. If CP7 later justifies splitting offline and Android validation into
parallel authoritative jobs, that architecture may incidentally expose path-specific job state; it
must not be undertaken merely for presentation.

## Ordering and duplication audit

| Observation | Class | Decision |
|---|---|---|
| Classify before Android setup | Safe early rejection/skip | KEEP |
| Offline before Android setup | Avoids expensive setup after tooling failure, but serializes successful Android paths | KEEP now; compare with parallel-job proposal |
| Exact artifact ID checked before download/mutation | Cheap early refusal | KEEP |
| Sign installs Build Tools before archive authentication | Failure-only ordering opportunity; no normal-path saving | Secondary |
| Publish validates artifact/manifest/freshness before mutation | Security boundary | KEEP |
| PR validation then main exact reuse | Safely reusable | KEEP |
| Local Fast then hosted PR authority | Same-looking work, different purpose | KEEP |
| PR Debug then main Release Build | Different input/variant/version identity | KEEP |
| Build/Sign/Publish reauthentication | Intentional trust verification | KEEP |
| Observe/Publish reauthentication | Intentional permission/TOCTOU boundary | KEEP |
| Diagnostic pre-commit/offline/Debug before diagnostic Release build | Historical duplication, distinct purpose is signing custody | SIMPLIFY candidate |

## Version and runner reliability

All top-level third-party Actions are pinned to commit SHAs. Gradle is pinned to 9.6.1; Build Tools
and NDK are exact. Reliability gaps to address independently of speed are:

- `gradle-wrapper.properties` has no `distributionSha256Sum`;
- compile SDK 37 is required by the build but the shared setup does not explicitly request its
  default-channel base package `platforms;android-37.0`, relying on the current runner image;
- `ubuntu-latest`, Python `3.14`, and Zulu Java `21` can move over time;
- the pre-commit engine installed by the pinned composite action is not version-pinned;
- clean-host dependency resolution has one recorded transient variant-resolution failure; one clean
  rerun succeeded, so dependency/repository redesign remains recurrence-driven.

The first two are small reproducibility hardening candidates. The others should be monitored and
changed only with compatibility evidence; dependency upgrades are not CP7 optimization work.

## Ranked optimization plan

Savings are estimates until an implementation branch supplies before/after measurements.

### P0 - obvious waste or reliability defect

| Candidate | Current measured cost | Expected saving | Frequency | Risk | Complexity | Confidence |
|---|---:|---:|---|---|---|---|
| Add per-module/per-test timing to `run_offline_tests.py` output | No durable attribution; one-off audit required log parsing | 0 direct; prevents blind regressions | every PR | low | low | high |
| Preserve isolated tests but create immutable repository seeds once | Windows: prepare 364s + upstream 305s; hosted prepare 127s | hosted 15-40s; Windows 1-3m | every PR / explicit local Full | low-medium | medium | medium |
| Run independent test modules in two bounded worker processes after isolation audit | Windows modules total about 700s; hosted about 156s | Windows about 280-320s; hosted 8-15s | every PR / local Full | low-medium | medium | high locally, medium hosted |
| Add Gradle distribution checksum and explicitly request compile SDK 37 | No normal timing saving; avoids integrity/runner-image failure | reliability, not time | every Android/build run | low | low | high |

The first bounded implementation checkpoint should combine timing output with immutable fixture-seed
reuse only. It must benchmark before/after on Windows and hosted-equivalent Linux and keep every test
in a private mutable repository. Parallel execution should be a second approval, based on residual
cost.

### P1 - high-value optimization

| Candidate | Current measured cost | Expected saving | Frequency | Risk | Complexity | Confidence |
|---|---:|---:|---|---|---|---|
| Bounded process sharding of independent `test_prepare_pr.py` test IDs | about 127s hosted / 364s Windows | 40-70s hosted; 2-3m Windows | every PR | medium | medium-high | medium |
| Prove and, only if confirmed, isolate volatile `SOURCE_SHA` from Kotlin compile inputs | main KSP/Kotlin about 3m20s even on workflow-only PR #74 | 1.5-3m on non-source `ANDROID_FULL`; smaller on app changes | Android/full and Release runs | medium-high | medium-high | medium-low until A/B |

The source-identity candidate must preserve the exact installed `SOURCE_SHA`, APK/version/provenance
contracts, and updater behavior. A packaged resource is only a hypothesis, not an approved design.

### P2 - worthwhile but secondary

| Candidate | Current measured cost | Expected saving | Frequency | Risk | Complexity | Confidence |
|---|---:|---:|---|---|---|---|
| Parallel authoritative offline and Android jobs with fixed `Full validation` aggregator | Android PR 10m11s-10m33s; offline 2m03s-2m31s serialized | about 1m50s-2m25s | Android PRs | medium-high | high; evidence contract migration | high timing, medium implementation |
| Persist authenticated/encrypted Gradle configuration cache using supported tooling | startup/config about 20s per fresh Gradle job | 10-20s per eligible build | Android/Release | medium | medium-high | medium-low |
| Simplify Signing Diagnostic to authenticated protected-main Release build + sign | historical 16m43s build; Debug about 6m04s | about 6m plus current offline cost | rare manual diagnostic | medium | medium | high saving, low frequency |
| Move Sign's numeric ID/download/manifest rejection before Java/Build Tools setup where possible | 14-16s setup, but only failed-input paths benefit | up to 14s on malformed/missing artifact | rare failure | low | low | high |

Parallel validation would preserve the exact required-check name only through an aggregator and would
need exact job/outcome/evidence authentication. It is not the first change: optimize the test runner
before accepting this contract complexity.

### P3 - technically possible, not currently worth it

| Candidate | Current measured cost | Expected saving | Reason to keep |
|---|---:|---:|---|
| Shallow PR/main/release checkouts | 2-4s each | 1-2s | Full history/base objects simplify version and evidence correctness |
| Replace current pre-commit action/install | 5-6s total | 2-4s | Correct and cached; maintenance cost exceeds saving |
| Replace Release inventory scans with exact-tag calls | Publish API work about 12s | a few seconds now | Existing scan detects ambiguity; scaling cost not yet material |
| Remove PR Debug APK from evidence artifact | upload about 1s | about 1s/storage | It is the exact operator-installable tested APK and participates in artifact identity |
| Optimize Stable/Hold runner work | about 50s each | under 15s | Rare; approval dominates |
| Optimize Upstream Observe/Publish | 31-47s total | under 10s | Reauthentication and permission separation justify cost |
| Optimize exact-tree reuse | 9-12s job | under 3s | Already the main performance win |
| Optimize no-build eligibility | 8-12s job | under 5s | Completed prior-job authentication requires the boundary |

### DO NOT OPTIMIZE

- Do not accept local evidence as PR authority.
- Do not weaken exact repository/workflow/run/attempt/parent/tree/class/artifact authentication.
- Do not remove protected-main fallback.
- Do not reuse Debug bytes as Release bytes or combine concurrent Debug/Release compilation; the
  variants and final identities differ, and concurrency already exhausted hosted memory.
- Do not merge Build, Sign, and Publish jobs or expose signing secrets to Gradle/publication.
- Do not replace artifact ID/digest handoffs with mutable text outputs.
- Do not remove post-approval Stable/Hold freshness rechecks.
- Do not trust Observe as publication authority or broaden upstream permissions.
- Do not remove version/tool pins merely because `ubuntu-latest` currently contains a package.

## Recommended migration sequence

1. **CP7.1 - offline fixture timing and seed reuse:** add durable timing, remove repeated immutable
   repository construction while retaining per-test mutable isolation, and compare complete suite
   results/timing on Windows and hosted Linux.
2. **CP7.2 - bounded offline parallelism, only if still material:** first modules, then prepare-pr
   test-ID sharding only if needed. Preserve deterministic failure output and summary isolation.
3. **CP7.3 - Gradle invalidation A/B:** collect task-input/cache evidence for identical application
   source with differing commit identity. Approve an identity-plumbing change only if causality and
   contract preservation are proven.
4. **CP7.4 - secondary reliability/setup work:** checksum the Gradle distribution, explicitly
   provision compile SDK, and decide whether configuration-cache persistence is worth its trust/key
   complexity.
5. **CP7.5 - optional rare-path simplification:** revisit Signing Diagnostic duplication. Do not
   optimize Stable, Hold, Sign, Publish, Upstream, reuse, or eligibility without new evidence.
6. **CP8 - final consistency:** only after approved CP7 changes are hosted-validated.

This was the original audit sequence. The operator subsequently authorized CP7.1; the final hosted
acceptance and disposition of every unimplemented candidate follow.

## Final hosted acceptance and closure

### PR #77 evidence

PR #77 merged as `f235374ed3e59678914d7c788af2eddf3a0a03e6`. The retained local logs and
hosted timestamps reconcile as follows:

| Evidence | Measured result |
|---|---:|
| Local prepare-pr | 42.5s total |
| Local `LOCAL CHECKS` | 13.3s |
| PR run [35057161593](https://github.com/constbogdan/Wholphin/actions/runs/35057161593) | 7m06s total |
| Hosted complete offline tooling | 1m28s / 88s |
| Android setup | 29s |
| Full defaultDebug validation | 4m42s |
| Protected-main run [35057619731](https://github.com/constbogdan/Wholphin/actions/runs/35057619731) | 27s total |
| Protected-main exact-tree reuse job | 11s |
| Development eligibility job | 10s; no build required |

The protected-main job authenticated the exact PR evidence, skipped repeated pre-commit, offline,
Android setup and Gradle work, then evaluated Development eligibility independently. Release Build,
Sign and Publish did not run.

### CP7.1 - complete / hosted validated

The test-only fake Python Git transport in `test_prepare_pr.py` was replaced with a private real
local bare origin for every fixture. Fake `gh` remains only for GitHub-only PR, Draft, repository
settings, API and auto-merge state. Real Git now exercises fetch, `ls-remote`, push, remote-tracking
refs, branch publication, divergence, native non-fast-forward refusal, preservation of remote state
and no-force behavior. Each fixture retains private repository, remote, refs, configuration, index,
worktree, hooks, logs and cleanup.

Controlled Windows medians improved approximately **22.5%** for `test_prepare_pr.py` and
approximately **11.5%** for the complete local offline suite. The first hosted acceptance completed
the complete offline suite in **88 seconds**. That hosted result is consistent with improvement, but
one post-change hosted sample does not prove that CP7.1 alone caused the entire difference from the
older 115-156 second samples.

Acceptance also found that the generic changed-test-module mapping made local Fast synchronously run
heavy process-integration suites. The corrected permanent split is:

- local Fast runs only explicitly categorized bounded feedback modules derived from the existing
  validation policy;
- `test_prepare_pr.py`, `test_hosted_upstream.py` and complete `test_*.py` coverage remain explicit
  local/manual and unconditional hosted-PR coverage, not implicit Fast work;
- Standard, Full and explicit module execution remain available;
- an explicitly requested offline pattern that discovers zero tests now fails with an actionable
  diagnostic instead of passing silently.

The acceptance Fast run completed in **8.3 seconds** while running only the two bounded modules
relevant to that scope. Authoritative hosted coverage remained the complete suite.

One presentation lesson is durable: PowerShell or redirected-output encoding can change decorative
Unicode separators. Tests assert the ordered semantic scope fields while tolerating only the
observed separator representations. Exact external protocols such as OSC 8 remain exact contracts.
Production prepare-pr output was not changed because no production rendering defect was proven.

### Remaining candidates - final decisions

No candidate meets all requirements for `GO before CP8`: a repeatable current hosted bottleneck,
meaningful expected wall-clock saving, low/moderate implementation risk, and unchanged validation,
reproducibility, authentication, release and security guarantees.

| Candidate | Final decision | Evidence-based reason |
|---|---|---|
| Permanent per-module/per-test timing output | KEEP current runner / not worth T0-1 complexity | No direct saving; the audit and hosted step timing now identify the material costs |
| Shared immutable repository seeds | KEEP private per-test origins | CP7.1 already achieved material improvement with the simpler isolation model |
| Two-process module execution | DEFER to future evidence | One 88s hosted sample does not establish a recurring residual problem; estimated saving was only 8-15s hosted |
| `test_prepare_pr.py` test-ID sharding | DEFER to future evidence | Medium-high orchestration complexity; residual hosted cost needs repeated post-CP7.1 samples |
| Volatile `SOURCE_SHA` / Kotlin input redesign | DEFER to future evidence | PR #77 Full fell to 4m42 from prior 7m12-7m20 without a Gradle change, so causality is not established |
| Parallel offline and Android authority jobs | KEEP current serial authority graph | At most 88s now overlaps, while the aggregator/evidence migration is high-risk and high-complexity |
| Persisted Gradle configuration cache | DEFER to future evidence | Expected 10-20s saving is unproven and introduces cache trust/key complexity |
| Gradle checksum and explicit compile-SDK provisioning | DEFER as reproducibility hardening | Valuable reliability questions, but no normal-path performance saving and not a CP7 optimization |
| Signing Diagnostic validation simplification | KEEP | Rare manual path; custody validation is distinct and does not justify another contract change |
| Move Sign rejection before Java/Build Tools setup | KEEP | Benefits rare malformed-input failures only, not normal delivery |
| Shallow checkouts | KEEP full-history behavior | Saves about 1-2s while complicating version/evidence object availability |
| Replace current pre-commit action/install | KEEP | Current 5-6s cost is small and cached |
| Replace Release inventory scans | KEEP | Current API cost is small and the scan protects ambiguity detection |
| Remove the PR Debug APK | KEEP | About 1s upload; it is the exact tested operator-installable artifact |
| Optimize exact-tree reuse or no-build eligibility | KEEP | Already 11s and 10s respectively; each protects a distinct authentication boundary |
| Merge/reuse PR Debug and final Release Build | KEEP separate | Different variant and final protected-main identity; prior concurrent builds exhausted memory |
| Optimize Sign, Publish, Stable or Hold | KEEP | Execution is already bounded; authority, byte authentication and approval boundaries are distinct |
| Optimize Upstream Observe/Publish | KEEP | Recent successful natural runs remain about 31-39s; authentication, permissions and TOCTOU rechecks are intentional |
| Add another workflow-level early validation signal | KEEP current dynamic step/live log | GitHub overview timing is a UI limitation; alternatives duplicate state or change the authority graph |

The 4m42 Gradle result is a useful natural sample, not evidence that CP7.1 improved Gradle. More
natural `ANDROID_FULL` samples are required before any Gradle/cache/input optimization is proposed.
Likewise, the roughly 305-second Windows hosted-upstream fixture timing does not describe hosted
Linux behavior: natural Upstream workflows remain short, so no upstream optimization is justified.

**CP7 is COMPLETE / HOSTED VALIDATED. CP8 subsequently closed T0-1; T0-2 is next.**
