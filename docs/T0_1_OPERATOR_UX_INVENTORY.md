# Baseline T0-1 operator UX, workflow, and presentation inventory

Status: **CP4B.1 COMPLETE / HOSTED VALIDATED**
Next: **CP4B.2 — define the authoritative PR validation evidence model**

Current sequence: **CP1 COMPLETE; CP2 COMPLETE / HOSTED VALIDATED; CP3 COMPLETE / HOSTED
VALIDATED; CP4A COMPLETE — BEHAVIORAL EXECUTION AUDITED; CP4B.1 COMPLETE / HOSTED VALIDATED;
CP4B.2 NEXT.**

This is the authoritative execution ledger for T0-1. The inventory tables preserve the CP1
baseline; historical names in acceptance records are evidence, not active UX. CP2 removed only
the two obsolete inherited workflow surfaces and updated their ownership contract. CP3 changed
operator presentation without changing machine contracts. CP4A records the measured behavior
from local edits through hosted validation and Development publication. CP4B.1 repaired and
live-proved the exact-tree reuse selector contract exposed by that audit. CP4B.2 is the next
checkpoint; no CP4B.2 behavior is implemented by this documentation update.

## T0-1 CP4B.1 exact-tree reuse contract repair

Status: **COMPLETE / HOSTED VALIDATED**

The fail-safe PR-to-main reuse defect is repaired. The actual `ci.yml` step is
`Run Full validation`; `mosaic_validation_reuse.py` now requires that exact case-sensitive value.
No workflow, check, job, step, artifact, permission, validation-scope, fallback, or release name was
changed.

The reuse fixture now derives the authoritative side independently from the real workflow file. It
locates the `full-validation` job and its `id: full-validation` step, then compares their actual YAML
names with the consumer's `JOB` and `FULL_STEP` selectors. It also checks the repository selector,
the evidence-record command, and the upload's use of the record step's `artifact_name` output. A
lowercase `Run full validation` fixture is explicitly rejected. Existing valid, missing, failed,
cancelled, ambiguous, non-Full, API-uncertain, parent-mismatched, and tree-mismatched paths retain
their fail-closed outcomes.

The adjacent selector audit found one internal duplication: the GitHub Actions API endpoint repeated
`ci.yml` separately from `WORKFLOW`. It now derives the endpoint filename from `WORKFLOW`, so a
future path migration cannot update the filesystem/run-path selector while silently leaving the API
lookup behind. The repository literal remains an intentional trust boundary; the job and step names
remain GitHub API selectors; and the artifact producer/parser remains one shared implementation with
workflow YAML transporting its exact output. No adjacent behavior change was required.

Focused local evidence was 12/12 reuse fixtures plus 10/10 delivery/presentation contract fixtures.
PR #60 then supplied the required hosted acceptance. Required PR Full run `34778154476`, attempt
`1`, passed and emitted exact-tree evidence. The normal two-parent merge preserved that tested tree;
protected main authenticated the PR evidence, reported `Full validation reused — this exact tree
already passed on PR #60.`, and did not execute fallback Gradle Full. The protected-main workflow
classified the unpublished range `tooling-only`, checked Development eligibility, and skipped Sign
and Publish. Observed protected-main timing was Full validation 1m35s, Build Development Release
10s, Sign and Publish skipped, and 1m51s total. This closes CP4B.1 without changing validation scope
or weakening the conservative fallback.

## T0-1 CP4B.2 boundary and approved direction

Status: **NEXT — DESIGN/IMPLEMENTATION NOT STARTED**

CP4B.2 must establish the simplest complete PR evidence contract before removing any existing gate.
The preferred architecture is broad hosted authority with coarse relevance gates:

```text
Working tree
  -> optional fast/relevant developer checks
prepare-pr
  -> exact scope/tree audit, cheap hygiene and relevant feedback, commit/push/PR
PR CI (authoritative)
  -> trusted-base classification and changed-range pre-commit
  -> complete offline tooling suite for every PR
  -> Full Debug for Android/build/unknown scope
  -> no Android Full for proven non-Android scope
  -> exact tested-tree evidence for the complete required policy
protected main
  -> authenticate PR policy evidence and exact final tree
     -> match: reuse the complete PR result
     -> uncertainty/direct/ambiguous: run the complete conservative fallback
  -> classify the unpublished Development range
     -> non-APK: stop
     -> APK: Release Build -> Sign -> Publish
```

This direction prefers deleting stages and local state over making duplicate execution smarter.
Existing scripts, fixtures, or documentation are not by themselves evidence that a mechanism must
remain. Local logs remain diagnostics, not authoritative attestations. Complete hosted offline
tooling has been observed in the tens-of-seconds range (about 18–24 seconds for approximately 232
tests), but that is measured evidence rather than a guaranteed duration. A sophisticated
authoritative test catalog or dependency-query system is therefore not justified today: run the
complete offline suite once in authoritative PR CI and reconsider only if future hosted measurements
justify the maintenance burden. Android is materially expensive and retains a coarse relevance gate.

Later simplification candidates, after replacement evidence is implemented and hosted-proven, are:
remove operator Full from the normal publication recipe while retaining it on demand; remove
mandatory prepare-pr Full and mandatory complete local offline execution; avoid authoritative
fine-grained offline/Android mapping; make blanket protected-main validation a fallback rather than
the normal exact-tree path; move Android setup behind a failed reuse decision; avoid a local
attestation subsystem; challenge prepare-pr resumable validation state where Git/GitHub state is
sufficient; remove the Signing Diagnostic manual SHA separately; and remove root compatibility logs
only if no consumer remains.

The simplification must preserve exact prepare-pr scope/tree verification, required PR CI,
exact-tree authentication, protected-main fail-safe fallback, final-context Release Build,
first-parent publication version allocation, `release-sign` isolation, unsigned/signed artifact
authentication, Publish freshness/idempotency, Stable Promotion and Hold Release authorization,
upstream ownership/native topology, human REVIEW/conflict authority, and native failed-job recovery.
These are distinct trust, byte, and authority boundaries rather than duplicate tests.

CP4B.2 must define evidence for both non-Android and Full PR paths before blanket main validation can
be removed. The controlled migration order is:

1. CP4B.1 reuse contract proven — **COMPLETE**.
2. Define the complete PR evidence contract.
3. Make PR CI authoritative for that contract.
4. Teach protected main to reuse complete PR policy evidence.
5. Live-prove tooling-only and Android paths.
6. Only then simplify local and prepare-pr validation.
7. Remove superseded state, catalog, tests, and documentation.
8. Simplify Signing Diagnostic separately.

No safety mechanism is removed before its replacement is implemented and hosted-proven. Performance
work remains CP7/T0-2: retain the observed 6+ minute local versus tens-of-seconds hosted offline gap,
external queue latency, transient Maven dependency-resolution incident, Windows Application
Control/pre-commit incident, Android setup/cache overhead, 9–10 minute Release Build, upstream
workflow-file publication permission mismatch, and missing sanitized `git push --porcelain`
diagnostics as evidence rather than folding them into CP4B.2.

## T0-1 CP4A behavioral execution audit

Status: **COMPLETE — BEHAVIORAL EXECUTION AUDITED**

Repository baseline: `d36f39342d1d2a003a6e52fca30d4d11a65391ba`

Baseline tree: `e4b408ce46b7038784e63b69338d41b8443f55ac`
Audit date: 2026-09-13

This is the measured input to T0-1 CP4B. It describes current behavior; it does not authorize or
claim any validation, workflow, publication, permission, or repository-setting change.

### Executive answer

For the representative APK-producing change (PR #46), Mosaic paid for repository pre-commit four
times, the complete local offline suite twice, Android Debug Full three times, and Release assembly
once before publishing the APK. The first two Android Full executions were local and covered the
same mutable five-path working snapshot; the second was almost entirely a cache check. PR CI then
validated the synthetic merge in a clean hosted environment and produced authenticated exact-tree
evidence. Protected main reused that evidence and correctly did not run Android Full again. Release
assembly was not a duplicate: it built the minified `defaultRelease` publication variant after the
Debug gate. Signing, APK inspection, and publication authentication were repeated only where bytes
crossed a build/sign/write-authority boundary.

The clearest removable duplication is the operator-run local Full immediately followed by the same
Full inside `prepare-pr`. The broader design opportunity is to make stable-commit PR evidence the
authoritative broad gate, while retaining quick local feedback and protected-main fallback whenever
tree or evidence equivalence cannot be proved. Local logs currently cannot support safe reuse: they
do not bind a successful run to a Git commit/tree or an immutable validation-contract identity.

At audit time there was one defect that had to be corrected before CP4B could rely on PR-to-main
reuse. The CI step was named `Run Full validation`, but `mosaic_validation_reuse.py` required
`Run full validation`. PR #59 produced valid exact-tree evidence, yet protected-main run
`34771225286` rejected it as missing/ambiguous and reran Full. This failed safely, so it was an
efficiency/contract-integration defect rather than weakened validation. CP4B.1 repaired the selector
and added real-YAML contract coverage; the incident remains here as historical evidence.

### Method and evidence corpus

The audit traced YAML to composite actions, scripts, subprocesses, tests/tasks, artifacts, summaries,
and downstream consumers. It inspected all 71 retained local validation run directories and 38
prepare-pr run directories, then selected runs with correlated Git and GitHub identities. GitHub CLI
access was used read-only.

| Case | Local evidence | Hosted evidence | Why selected |
|---|---|---|---|
| APK-producing, high risk | validation `20260912-020713-19588`; prepare `20260912-021616-20800` and child validation `20260912-021622-20800` | PR #46 run `34657784835`; main/release run `34673534555`; immutable `downstream-build-29` | Complete working-tree-to-published-APK chain plus failed-job rerun |
| Normal application, targeted PR | validation `20260912-145800-24548` | PR #51 run `34692407899`; main/release run `34692693013` | Shows targeted PR followed by authoritative main Full and Release |
| Non-APK high-risk control | validation `20260913-195152-21292`; prepare `20260913-200344-12912` and child `20260913-200359-12912` | PR #59 run `34770840853`; main run `34771225286` | Shows release skip and the historical fail-safe exact-tree reuse defect repaired in CP4B.1 |
| Successful exact-tree reuse | n/a | PR #42 evidence; main run `34613938306` | Proves reuse is implemented, not hypothetical |
| Stable | n/a | run `34694610864` | Exact-byte Development-to-Stable promotion |
| Hold | n/a | run `34690727709` | Emergency Stable withdrawal without rebuild/resign |
| Signing diagnostic | n/a | run `34323962085` | Independent credential/signature diagnostic |
| Native upstream FOLLOW | n/a | runs `34701161886`, `34701197155`, `34702111274`, `34702881758`; PR #55 | Observe, native candidate, required CI, merge, Development, no-delta |
| Clean-host dependency incident | local Full success; no dependency/config change | PR #57 run `34720066335`, attempts 1–3 | Transient external resolution and unchanged retry |

Run durations below use job/step timestamps or local ISO timestamps. GitHub queue time, Environment
wait, setup, execution, and publication are kept separate. The local duration formatter rounds
`TotalMinutes` before combining it with seconds, so timestamp deltas are authoritative where a
displayed duration is inconsistent.

### Current-state pipeline diagrams

#### Ordinary change through Development

```text
VS Code / working tree
  |- optional local Fast/Standard
  |- operator local Full (when run) ---------------- pre-commit #1
  |                                                  offline suite #1
  |                                                  Debug Full #1
  v
prepare-pr
  |- scope + Git-native mutable-snapshot identity
  |- selected local validation --------------------- pre-commit #2
  |                                                  offline suite #2
  |                                                  Debug Full #2
  |- exact staging -> commit-tree verification -> push -> PR
  v
PR CI (`Full validation` required check)
  |- trusted-base classification ------------------- classification #3
  |- changed-range pre-commit ----------------------- pre-commit #3
  |- mapped offline suite (or all) ----------------- offline #3
  |- targeted Android OR Debug Full ---------------- Debug Full #3 for PR #46
  `- PR Full only: tested merge/tree + Debug APK artifact
  v
human merge
  v
protected-main CI
  |- all-files pre-commit --------------------------- pre-commit #4
  |- all offline tooling ---------------------------- offline #4
  |- authenticate PR evidence
  |- exact tree: reuse Debug Full; uncertainty: run Debug Full #4
  v
Build Development Release
  |- classify last published Development source -> current main
  |- non-APK: stop
  `- APK: assembleDefaultRelease -------------------- Release build #1 (distinct variant)
       v
Sign Development (`release-sign`)
  |- authenticate exact unsigned artifact
  |- sign only; no Gradle
  `- verify signer/package/version/payload
       v
Publish Development (contents write; no signing secrets)
  |- authenticate exact signed artifact + manifest + current main
  `- idempotent immutable `downstream-build-N` + rolling `develop`
       v
Development APK
```

#### Native upstream integration

```text
scheduled/manual Observe (read only)
  |- fetch exact origin/main + upstream/main
  |- contained upstream -> quiet no_delta -> STOP
  `- complete range -> ownership + isolated native merge
       |- clean FOLLOW -> normal PR
       |- clean REVIEW -> Draft PR
       `- conflict -> single-parent Draft workspace
                           -> local resolve-upstream
                           -> human/Codex semantic resolution
                           -> real two-parent merge
  v
required PR CI (forced Full for chore/sync-upstream-*)
  v
human Ready/merge decision
  v
protected-main exact-tree reuse/fallback -> Development eligibility -> optional APK
```

The manual `sync-upstream.ps1` remains a documented break-glass path. It creates a dated branch and
performs an ordinary `git merge upstream/main`; it does not publish or resolve conflicts.

### Complete execution matrix

Commands are literal except for angle-bracket placeholders representing the identities or reviewed
path/test lists supplied by the enclosing stage.

#### Local validation, preparation, PR CI, and protected main

| Stage | Trigger | Job/step | Exact command/script | Purpose | Trusted input identity | Output/evidence | Consumer | Duplicate? | Correct timing? | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| Local classify | `validate-local.ps1` | policy | `python -B scripts/mosaic_validation_policy.py --base origin/main --head HEAD --include-working-tree` (or repeated `--path`) | Select Fast/targeted/Full, offline pattern, release relevance | local `origin/main`, HEAD, index/worktree, reviewed explicit paths | terminal plan + run summary | local stage builder | prepare and PR classify related scope again, but at different trust points | Correct for feedback; not authoritative | **KEEP HERE** |
| Local hygiene | Full | Repository-wide pre-commit | `pre-commit run --all-files` | Execute pinned hygiene/format hooks | mutable checkout and `.pre-commit-config.yaml` | per-stage log; possible autofix | operator/prepare drift guard | repeated by prepare, PR, and main | Too broad when immediately followed by prepare; useful before expensive local work | **SPLIT BY RELEVANCE** |
| Local hygiene | Fast/Standard/non-Android | Changed-scope pre-commit | `pre-commit run --files <changed paths>` plus reviewed untracked files | Fast hygiene of candidate scope | classifier path set | per-stage log; possible autofix | operator/prepare drift guard | PR checks committed range again | Correct feedback boundary | **KEEP HERE** |
| Local tooling | Full | Offline tooling tests | `python -B scripts/run_offline_tests.py --pattern 'test_*.py'` | Entire offline fixture suite; isolate synthetic summaries | mutable checkout | buffered unittest result + stage log | operator only | exactly repeated by prepare Full; main also runs all | Wrong as an automatic second local pass on unchanged input | **REMOVE DUPLICATE** |
| Local tooling | selected | Offline tooling tests | same runner with classifier-selected pattern | Relevant script/policy fixtures | changed paths and explicit map | buffered result | operator/prepare | PR may run same mapped test | Correct for fast feedback; shared/unknown code falls back broad | **KEEP HERE** |
| Local Android | Standard targeted | Kotlin compile + focused JVM tests | `.\gradlew :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest --tests <pattern>...` | Compile production Debug Kotlin and exercise mapped behavior | mutable checkout; explicit/mapped filters | Gradle result/log | operator only | PR repeats on synthetic merge | Correct fast feedback | **KEEP HERE** |
| Local Android | Full | Full default-debug validation | `.\gradlew :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest :app:assembleDefaultDebug` | Full Debug compile/unit/APK graph | mutable checkout and local caches | Gradle result/log | operator only | prepare can immediately repeat; PR repeats clean-host synthetic merge | Useful on demand, not reusable today | **SIMPLIFY** |
| Local whitespace | every level | Git whitespace check | `git diff --check` | Detect whitespace errors | mutable tracked diff | stage result/log | operator | prepare audit and child validation repeat it | Cheap, but repeated inside one prepare transaction | **SIMPLIFY** |
| Prepare preflight/audit | guided `prepare-pr` | PREFLIGHT/AUDIT | Git status/ref/remote/fetch/log/diff/`ls-files --stage`/`hash-object` operations | Confirm complete branch-only + dirty scope and exact mode/type/blob/deletion state | `origin/main`, branch HEAD, worktree/index, configured remotes | `.git/wholphin-prepare-pr-state.json`; `.logs/prepare-pr/**` | later prepare phases | distinct from validation | Correct before mutation | **KEEP HERE** |
| Prepare validate | after scope confirmation | VALIDATE | `.\scripts\validate-local.ps1 -Level <selected> -ChangedPath <path>... [-TestFilter <filter>]` | Enforce policy and detect autofix/drift before staging | confirmed publication path union + working snapshot hash | local validation logs; level/time in resumable state | Stage phase only | duplicates a just-completed operator Full; no prior-run lookup | Wrong when unchanged Full just ran | **REUSE PRIOR EVIDENCE** |
| Prepare upstream validate | sync branch | Standard then Full | same command first with `-FocusedBeforeFull` and filters, then `-Level Full` | Meaningful semantic tests plus conservative complete gate | resolved native merge and derived/operator filters | two local results tied only to prepare snapshot | Stage phase | PR is then forced Full; Full local work repeats hosted | Safety policy is conservative but expensive | **SIMPLIFY** |
| Prepare stage/commit | validated snapshot | STAGE/COMMIT | exact `git add`; `git write-tree`; `git commit`; `git rev-parse HEAD^{tree}` | Commit exactly reviewed content; preserve native merge commit | intended/staged snapshot, modes/types/blobs, expected merge parents/tree | commit SHA/tree, state/log | Publish | not duplicate | Correct mutation boundary | **KEEP HERE** |
| Prepare publish | committed clean branch | PUBLISH | `git push -u origin <branch>` or ordinary `git push`; `gh pr list --head <owner:branch> --base main --state open --json number,url`; `gh pr create ...` | Publish without force or duplicate PR | exact commit/tree, remote non-divergence, authenticated `gh` | remote branch + PR URL + log | GitHub CI/human | not duplicate | Correct after explicit authorization; stops before CI/merge | **KEEP HERE** |
| PR classification | PR to main | Choose PR validation path | trusted-base copy of `mosaic_validation_policy.py --base <base SHA> --head "$GITHUB_SHA" --github-output "$GITHUB_OUTPUT"` | Prevent PR from weakening its own selector; force upstream branches Full | base SHA, synthetic merge checkout, base policy | mode/tests/offline pattern/release fields | later PR steps and summary | similar local decision, stronger identity/trust | Correct early stage | **KEEP HERE** |
| PR hygiene | every PR | Check changed files | pre-commit action `--from-ref <base SHA> --to-ref <synthetic merge SHA>` | Authoritative committed-range hygiene | GitHub PR base + merge ref | required job status/log | branch rules/human | local pass is feedback, not authority | Correct hosted gate | **MUST REMAIN AUTHORITATIVE** |
| PR offline | mapped or Full | Run offline tooling checks | `python -B scripts/run_offline_tests.py --pattern "$PATTERN"`; empty pattern becomes `test_*.py` | Exercise affected tooling/security fixtures | trusted classifier output | required job result/log | branch rules; not persisted separately | may repeat local suite; Full can still be mapped narrow | Correct authority; selection needs explicit dependency safety | **SPLIT BY RELEVANCE** |
| PR Android setup | non-Android excluded | composite `.github/actions/setup` | Python 3.14; Zulu JDK 21 Gradle cache; SDK `tools platform-tools build-tools;36.0.0 ndk;29.0.14206865` | Hosted Android toolchain | pinned action SHAs and versions; runner image supplies compile platform | environment/cache | Gradle | repeated later in fresh Release job | Necessary per isolated job; noisy/heavy | **DEFER TO T0-2** |
| PR Android targeted | `targeted-android` | Run targeted Android validation | `./gradlew :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest --tests <pattern>... --no-daemon` | Clean-host compile + mapped JVM behavior | synthetic merge tree | required job result | branch rules | repeats local feedback, but on authoritative merge tree | Correct | **MUST REMAIN AUTHORITATIVE** |
| PR Android Full | `full` | Run Full validation | `./gradlew :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest :app:assembleDefaultDebug --no-daemon` | Clean-host complete Debug graph | synthetic merge tree | required job result + Debug APK | branch rules/reuse | repeats local Full but provides authoritative identity/environment | Correct broad gate | **MUST REMAIN AUTHORITATIVE** |
| PR evidence | successful PR Full | record/upload | `python -B scripts/mosaic_validation_reuse.py record`; upload named Debug artifact | Bind PR/head/tested merge/tree/run/attempt to successful job and APK | GitHub PR event/ref, checkout HEAD/tree, run identity | 7-day artifact name/ID/archive digest + GitHub job/step result | protected-main reuse | unique reusable evidence | Correct after Full | **KEEP HERE** |
| Main hygiene | every push/dispatch | Check repository formatting | pre-commit action `--all-files` | Recheck repository baseline | protected-main checkout | job result/log | required check | PR/local hygiene repeated | On exact reused tree this is repeated computation | **REUSE PRIOR EVIDENCE** |
| Main tooling | every push/dispatch | Run offline tooling checks | `python -B scripts/run_offline_tests.py --pattern 'test_*.py'` | Complete trusted tooling/security fixtures | protected-main checkout | job result/log | required check | local Full/prepare and often PR repeat it | Currently necessary because PR Full may run only mapped offline tests | **SIMPLIFY** |
| Main reuse decision | protected-main push | Check for reusable PR Full validation | `python -B scripts/mosaic_validation_reuse.py reuse` | Authenticate PR, parents, successful job/step, unique unexpired artifact, GitHub tested tree, final main tree | exact protected main merge + read-only GitHub API | outputs reuse/reason/tree/PR/run | Full-step condition and summary | no duplicate | Correct before Gradle, but Android setup still runs afterward even on reuse | **MOVE EARLIER** |
| Main Android setup | every non-PR event | Set up Android validation | same composite setup | Prepare possible Full | event checkout | toolchain/cache | Full | on reuse it has no consumer | Wrong when reuse succeeds | **MOVE LATER** |
| Main Debug Full | no authenticated reuse | Run Full validation | same hosted Full Gradle command as PR | Fail-safe authoritative validation of final main | protected-main tree | required job result | Release job | legitimate fallback | Correct fallback | **MUST REMAIN AUTHORITATIVE** |

`workflow_dispatch` of CI uses all-files pre-commit, all offline tests, Android setup, and Full. It
never enters automatic Development because the release jobs require a protected-main `push`.

#### Development, Stable, Hold, Signing Diagnostic, and Upstream

| Stage | Trigger | Job/step | Exact command/script | Purpose | Trusted input identity | Output/evidence | Consumer | Duplicate? | Correct timing? | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| Development eligibility | successful protected-main Full job | Build / Decide | `python -B scripts/mosaic_development_release.py ci-eligibility` | Classify last successfully published Development source through current trusted main | protected repo/ref/event/SHA, current main, authenticated rolling/immutable Release history | `release_required`, policy/path summary | remaining Build steps and Sign/Publish job conditions | candidate classification occurred earlier, but this range is different | Correct range; too late to avoid main Debug work | **KEEP HERE** |
| Release setup | release required | Build / setup composite | same Python/JDK/SDK/NDK composite | Prepare clean Release build | exact main SHA/full history | hosted toolchain/cache | Release Gradle | Debug job had a separate workspace/cache | Isolation is intentional; performance is CP7/T0-2 | **DEFER TO T0-2** |
| Release assembly | release required | Build unsigned Development Release APK | `./gradlew :app:assembleDefaultRelease -PmosaicPublication=true --no-daemon --no-parallel --max-workers=1` | Build minified/resource-shrunk unsigned publication APK with real version allocation | protected-main source/tree, first-parent history, publication property | unsigned APK, mapping, output metadata | preparation/sign | not equivalent to Debug validation | Correct after validation | **KEEP HERE** |
| Unsigned preparation | after Release assembly | Prepare authenticated unsigned APK | `python -B scripts/mosaic_signing_exercise.py prepare --directory "$RUNNER_TEMP/mosaic-main-release"` | Select one universal APK and record source/tree/version/payload identity | exact build checkout/output | provenance + unsigned APK; named 7-day artifact and mapping artifact | Sign; diagnostics | no | Correct boundary | **KEEP HERE** |
| Sign input auth | release required | Sign / download + verify | exact numeric artifact download with `digest-mismatch: error`; `mosaic_development_release.py ci-artifact`; `zipalign -c`; reject signed input | Prove same-run/current-or-prior-attempt unsigned bytes before credentials | native `needs.release-build` outputs, run/attempt/name/ID/archive digest/provenance | verified local unsigned input | signer | verification repeats identity intentionally across trust boundary | Correct before secret use | **KEEP HERE** |
| APK signing | authenticated input + Environment approval | Sign exact APK | `.github/actions/mosaic-sign-apk`: `apksigner sign ...` with four step-scoped secrets | Use protected key only in sign job | `release-sign` approval/secrets + exact unsigned APK | signed APK; temporary keystore deleted | post-sign verification | no Gradle or Release write | Correct isolated boundary | **MUST REMAIN AUTHORITATIVE** |
| Signed verification | after sign | compare/verify/upload | `mosaic_signing_exercise.py compare`; `verify_mosaic_apk.py ...`; upload exact artifact | Prove unchanged payload plus package/version/certificate/provenance | signed + unsigned bytes and signing policy | verification JSON + 7-day signed artifact ID/digest | Publish | necessary independent byte check | Correct | **KEEP HERE** |
| Publish auth | successful Sign | Publish / validate manifest | exact numeric artifact download with `digest-mismatch: error`; `mosaic_development_release.py ci-manifest` | Authenticate signed bytes before contents-write use | native needs outputs, run/attempt, manifest/APK | accepted publication directory | publisher | repeats identity intentionally across authority boundary | Correct | **KEEP HERE** |
| Development publish | authenticated current main | Publish verified Development APK | `python -B scripts/mosaic_development_release.py ci-publish --directory "$RUNNER_TEMP/mosaic-result"` | Idempotently create/verify immutable release and update rolling `develop` | exact current protected main, build/run/attempt, signed digest/provenance, remote state | `downstream-build-N`, `develop`, APK/manifest/source assets, summary | updater, Stable Prepare, operators | no build/sign repetition | Correct; fail closed on stale/conflict | **MUST REMAIN AUTHORITATIVE** |
| Native failed-job recovery | failed Build/Sign/Publish | GitHub Re-run failed jobs | GitHub-native rerun | Retain successful jobs/artifacts; rerun failed job and dependents | same run ID, incremented attempt; accepted prior attempt only | completed chain or refusal | operator/updater | avoids rebuild/resign | Correct and live proven Cases A/B | **KEEP HERE** |
| Stable prepare | manual protected-main dispatch | Prepare | `mosaic_stable.py prepare`; `verify_mosaic_apk.py`; `mosaic_stable.py verify` | Resolve current `develop`, immutable source, manifest, signer and exact bytes | current protected main + Development release graph | 7-day exact evidence artifact ID/digest and approval summary | Release job | public APK verification repeats by design | Correct before approval | **KEEP HERE** |
| Stable release | `release-promote` approval | Release | exact evidence download; `python3 -B scripts/mosaic_stable.py publish --directory ...` | Recheck current main/Development and publish exact bytes as Stable | Environment authorization + prepared evidence + fresh remote state | immutable/latest Stable release; summary | Stable updater | no build or resign | Correct authority boundary | **MUST REMAIN AUTHORITATIVE** |
| Hold prepare | manual protected-main dispatch | Prepare | `mosaic_hold_release.py get`; `verify_mosaic_apk.py`; `mosaic_hold_release.py verify` | Resolve/authenticate the exact current Stable before emergency action | `/releases/latest`, manifest/APK/source/signature/current main | 7-day evidence artifact ID/digest and approval summary | Hold job | repeats public verification intentionally | Correct | **KEEP HERE** |
| Hold mutation | `release-hold` approval | Hold | exact evidence download; `python3 -B scripts/mosaic_hold_release.py hold --directory ...` | Recheck latest and mark current Stable prerelease/held | Environment authorization + exact release ID/evidence + current `/latest` | preserved held Release; new/latest-or-none summary | updater/operators | no build/sign | Correct minimal emergency boundary | **MUST REMAIN AUTHORITATIVE** |
| Signing diagnostic build | manual exact SHA | Validate and build diagnostic input | all-files pre-commit; all offline tests; hosted Debug Full with `-PmosaicPublication=true --no-parallel --max-workers=1`; then Release assemble | Independently exercise build and key-custody path without publication | required input equals protected `github.sha` | exact unsigned diagnostic artifact | diagnostic Sign | duplicates CI/build deliberately for independence | Purpose valid; manual SHA adds no independent authorization | **SIMPLIFY** |
| Signing diagnostic sign | `release-sign` approval | Sign and verify diagnostic APK | exact artifact auth; signer composite; compare; `verify_mosaic_apk.py`; upload | Prove secrets/certificate/signing/payload without Releases | Environment + exact artifact/source | 7-day signed diagnostic APK and summary | operator only | same sign mechanics as Development, distinct diagnostic | Correct isolation | **KEEP HERE** |
| Upstream observe | schedule or manual main run | Observe upstream integration | `python3 scripts/hosted_upstream.py --output "$RUNNER_TEMP/observation.json"` | Fetch exact refs, prove ancestry, classify complete upstream range, construct isolated candidate | fixed origin/upstream URLs, official refs, ownership policy, current main, read token | 14-day observation JSON + rich summary + outputs | Publish job/operator | no Android validation | Correct read-only first phase | **KEEP HERE** |
| Upstream publish | publishable/blocked observation | Publish candidate | same helper with `--publish --expected-upstream <SHA> --expected-downstream <SHA>` | Reobserve, check drift/PR decisions, optionally push deterministic ref and create normal/Draft PR | observation SHA pair; fresh refs; scoped App token only when mutation required | candidate branch/PR or refusal; 14-day outcome JSON | PR CI/human/resolver | no force, no auto-merge | Correct separate authority; generic push diagnostics remain weak | **KEEP HERE** |
| Upstream resolver | human selects attention Draft | local wrapper/helper | `.\scripts\resolve-upstream.ps1 [-Pr N]` -> `python -B scripts/resolve_upstream.py [--pr N]` | Authenticate exact candidate/evidence, start/continue native merge, derive meaningful tests, hand off same Draft | clean worktree, authenticated GitHub, exact PR/episode/upstream/downstream parents | local merge/prompt/log and exact prepare command | human/Codex then prepare-pr | distinct semantic work | Correct human boundary | **KEEP HERE** |
| Manual upstream break-glass | explicit operator | `sync-upstream.ps1` | fetch origin/upstream; `git switch -c chore/sync-upstream-<date> main`; `git merge --no-edit upstream/main` | Provide local path if hosted automation is unavailable | clean local main equal/behind origin/main and configured remotes | local merge branch or conflict stop | human validation/prepare | overlaps hosted path but is break-glass | Not normal lifecycle | **DEFER TO T0-2** |

`mosaic_delivery_output.py` renders Development/Stable summaries and Release bodies; it carries no
independent authority. `mosaic_output.ps1` is a local diagnostic/logging layer: one writer owns each
per-stage log, then a post-run compatibility snapshot replaces root `validation.log` or
`prepare-pr.log`. `run_offline_tests.py` buffers successful unittest output and removes hosted output
variables so synthetic fixture summaries cannot escape into real Actions summaries.

### Primary APK lifecycle ledger: PR #46

The local working set contained five paths. Prepare committed head
`18d9a5e0d87f832950792a97cb0915837e2bb98a`, tree
`a0d10a2a0e05c6c97df50a80ae2f5406dddbefef`. GitHub tested a synthetic merge of the same PR,
then human merge produced `1d17c94ab86b3ed8d9e6e0f42398ac99e4d7eb23` with the same final tree.

| Seq. | Location | Tool/run | Operation | Exact work | Active duration | Identity/result | Evidence/reuse |
|---:|---|---|---|---|---:|---|---|
| 1 | Windows | validation `20260912-020713-19588` | explicit Full | all-files pre-commit; all 218 offline tests; Debug Full; diff check | 9m49s | mutable five-path working tree; pass | ignored logs only; no commit/tree binding; not reused |
| 2 | Windows | prepare `20260912-021616-20800` | audit + Full | exact scope/snapshot; same hooks; same 218 tests; same Debug tasks; diff check | 5m45s total; child 5m24s | same five paths; unchanged snapshot `87066aea...`; pass | staged/commit tree checks consume only this validation state |
| 3 | GitHub PR | `34657784835` attempt 1 | required PR Full | changed-range hooks 8s; mapped Development-release offline test; setup 45s; Debug Full 5m28s | 6m36s job | PR #46 head; hosted synthetic merge; pass | artifact `10286099224`, archive digest `8c3733...`, exact tested SHA/tree/run/attempt |
| 4 | GitHub main | `34673534555` attempt 1 | protected-main gate | all hooks 27s; all offline 20s; reuse 2s; setup 45s; Gradle skipped | 1m45s job | main `1d17c94a...`; exact tree matched PR | PR evidence reused; setup had no Gradle consumer |
| 5 | GitHub main | same run | Build | unpublished-range classification; setup 49s; Release assembly 8m46s; provenance/artifacts | 9m50s | APK-relevant; version 1.0.29 | unsigned artifact `10291054170`; mapping `10291104210` |
| 6 | GitHub main | same run | Sign | setup 16s; artifact auth; sign; verify; upload | 24s | exact unsigned bytes; pass | signed artifact `10291668087`; signer/package/payload evidence |
| 7 | GitHub main | same run attempt 1 | Publish | signed auth + intentional Case-B pre-mutation refusal | 7s | no remote mutation | safe failure; Build/Sign retained |
| 8 | GitHub main | same run attempt 2 | Re-run failed jobs | Publish only; same run ID, prior signed artifact | 19s | pass | immutable `downstream-build-29` + rolling `develop` |

The published manifest binds application `io.github.constbogdan.mosaic`, version `1.0.29`, source
SHA/tree, build workflow/run/attempt, unsigned and signed APK hashes, and signing fingerprint. The
release APK hash was `df362aa5...`. Build, Sign, and Publish are directly connected to the same
validated source/tree; the extra checks are authentication at distinct privilege boundaries, not
recompilation.

#### Measured cost

| Category | Active time | Notes |
|---|---:|---|
| Developer-local waiting before PR | about 15m34s | 9m49s explicit Full plus about 5m45s prepare; excludes human time |
| Duplicate second local validation | 5m24s | 23.3s pre-commit, 4m56.9s actual offline duration, 3.8s cache-only Gradle, diff check |
| Hosted PR active job | 6m36s | queue excluded |
| Hosted main validation + delivery work | about 12m25s | 1m45s Full job + 9m50s Build + 24s Sign + 7s/19s Publish attempts; gaps and operator rerun wait excluded |
| Environment/setup within main gate/build/sign | about 1m50s | 45s unused main Android setup after reuse, 49s Release setup, about 16s signer setup |
| Release compilation | 8m46s | distinct Release variant, not safely removable as Debug duplication |

At least 5m24s of the local path was demonstrably repeated on an unchanged scope. Eliminating the
explicit Full as well would avoid its 9m49s when the intended model is fast local feedback followed
by authoritative PR Full, but that policy change must retain a deliberate on-demand local Full and
must not reduce pre-PR feedback to zero.

### Duplicate work accounting

| Logical operation | Explicit local Full | prepare-pr | PR CI | Main CI | Release/Sign/Publish | Total for PR #46 | Necessary boundary today |
|---|---:|---:|---:|---:|---:|---:|---|
| Validation classification | 1 | 1 | 1 | 0 | 1 | 4 | Local/prepare duplicate candidate scope; PR uses trusted base/synthetic merge; Release uses complete unpublished range |
| Pre-commit | 1 | 1 | 1 | 1 | 0 | 4 | One local feedback pass and one authoritative committed-tree pass are defensible; exact-tree downstream repetition is reusable |
| Complete offline suite | 1 | 1 | 0 | 1 | 0 | 3 | PR ran only the mapped release test; main complete suite currently supplies broad hosted tooling evidence |
| Mapped release offline test | included | included | 1 | included | 0 | logically 4 | Local repetition removable; hosted execution remains authoritative |
| Debug compile/unit/assemble Full | 1 | 1 | 1 | reused | 0 | 3 | PR synthetic-merge Full is authoritative; local work is feedback, and one of the two local passes is plainly duplicate |
| Explicit `git diff --check` | 1 | prepare audit + child validation | 0 | 0 | 0 | 3 local checks | Cheap but duplicated within one preparation transaction |
| Release assemble | 0 | 0 | 0 | 0 | 1 | 1 | Required final `defaultRelease` artifact graph |
| APK payload/package/version checks | 0 | 0 | Debug artifact shape | 0 | unsigned then signed then publisher | 3 boundary checks | Security-essential byte/authority handoff checks |
| Signing | 0 | 0 | 0 | 0 | 1 | 1 | Secret-isolated, required |
| Publication | 0 | 0 | 0 | 0 | 1 successful attempt | 1 | Write-authority boundary, required and idempotent |

The two local Full offline logs contain the identical 218 discovered tests; only elapsed time differs
(307.915s and 296.386s). The more recent CP3 pair likewise contains the identical 232 tests
(366.007s and 383.218s). This proves duplication by test identity, not merely stage label.

### Local Full versus prepare-pr

| Operation | Explicit local Full | prepare-pr-selected Full | Equivalent? | Extra assurance from prepare-pr |
|---|---|---|---|---|
| Classification | `origin/main..HEAD` plus worktree | exact complete publication path union | Same classifier, potentially different inputs | Prepare includes branch-only committed paths and confirmed scope explicitly |
| Pre-commit | `--all-files` | `--all-files` | Yes for same checkout/config | Drift is checked against confirmed snapshot afterward |
| Offline tooling | runner with `test_*.py` | same | Yes; exact discovered names/count matched | None from re-execution itself |
| Android Full | same three Gradle tasks | same | Effective task graph equivalent; second used local cache | Prepare knows validation completed before its staging phase |
| Whitespace | `git diff --check` | prepare audit plus child `git diff --check` | Yes for unchanged diff | Prepare associates its result with the reviewed scope |
| Evidence | ignored run logs, no Git tree | ignored logs + resumable state with snapshot hash/level/time | No | Prepare protects mutable-snapshot drift and later exact staged/commit tree |

Prepare therefore gains essential scope and mutation safety, but its repeated Full commands do not
gain additional behavioral evidence when an immediately preceding Full covered the same bytes. The
missing element is trustworthy local evidence identity, not another test execution.

### Local versus hosted authority

Local validation is valuable for quick correction before a push, for tests needing local iteration,
and for semantic upstream resolution before publication. It is not an integration authority: its
logs are ignored, replaceable, workstation-specific, and not tied to a stable commit/tree. A mutable
working-tree pass also cannot prove GitHub's synthetic merge.

Hosted PR CI is the correct authoritative integration gate because it tests the committed synthetic
merge under branch protection, has trusted-base selection, emits a durable check, and can bind exact
tree/run/artifact evidence. Protected main should authenticate that evidence and fall back safely.
Release Build must remain final-context because it allocates publication identity and builds a
different variant from protected main. Sign and Publish must remain separate privileged jobs.

The measured offline-suite environment gap is real. Equivalent 232-test runs took 366–383s locally
and about 18.6–24s hosted: roughly 16–20 times faster hosted. Test names and discovery pattern were
the same. The likely category is Windows process/filesystem cost in Git-heavy temporary-repository
fixtures, potentially amplified by antivirus/Application Control; the evidence does not isolate one
cause. Python logic or suite selection is not a sufficient explanation. Profiling/optimization
belongs to CP7/T0-2, not CP4A.

### Exact-tree evidence map

| Evidence | Identity represented | Durability/authority | Current consumer | Reusable? |
|---|---|---|---|---|
| `.logs/validation/<run>/summary.txt` and stage logs | requested mode/paths/filters/commands/results; no recorded HEAD/tree | ignored local diagnostics; mutable/deletable | operator; prepare copies root compatibility log | No safe cross-invocation reuse |
| root `validation.log` | post-run compatibility copy | replaced by next run | prepare diagnostic copy/operator | No |
| prepare resumable state | base, branch HEAD, publication paths, content/mode/type/blob/deletion snapshot, validation level/time, staged/commit tree | local `.git` state; transaction guard | later prepare phases | Yes only inside the same unchanged prepare transaction |
| prepare log/PR body | commit/tree in log; PR body says local level passed | ignored local log; PR prose is not attestation | human | No machine reuse |
| GitHub required job | workflow/job/step conclusions on PR head and synthetic merge execution | GitHub control-plane record | branch rules and main reuse lookup | Authoritative if all selectors match |
| PR Debug artifact | numeric ID, exact name encoding PR/head/tested merge/tree/run/attempt, archive digest, owner run, 7-day retention | immutable while retained; GitHub-authenticated | main reuse | Yes, with API/Git proof |
| GitHub Git commit API | tested synthetic merge parents/tree | canonical hosted Git object evidence | main reuse | Yes |
| protected-main reuse outputs/summary | final tree and accepted PR/run/attempt | per-run outputs/summary | Full condition/operator | Within run; not a new reusable artifact |
| unsigned/signed artifacts | exact numeric ID/name/archive digest plus provenance/APK hashes/run/attempt/source/tree | 7-day GitHub artifacts | downstream jobs/native rerun | Yes inside authenticated delivery chain |
| `mosaic-release.json` and immutable Release | permanent source/tree/version/run/attempt/APK/signature identity | public permanent provenance | Development eligibility, Stable, Hold, updater/operator | Yes for release state, not Debug validation |

#### Reuse invalidation

Main reuse correctly falls back when any of these cannot be proved: protected-main push identity;
event SHA equals checkout; exactly two merge parents; unique same-repository merged PR; exact
base/head parents; successful current `ci.yml` PR run; exactly one successful `Full validation` job;
required Full step success; one matching unexpired artifact with valid digest/owner identity; tested
synthetic merge parents/tree from GitHub Git data; and equality of tested tree with final main tree.

Consequences:

- rebase/squash/non-two-parent main integration invalidates reuse;
- a changed base that changes the synthetic merge tree invalidates old evidence;
- a different final merge tree invalidates reuse even if file names look the same;
- missing, expired, failed, foreign, future-attempt, duplicate, or ambiguous evidence invalidates
  reuse;
- validation policy/tooling changes are part of the tested Git tree, but machine step names are an
  additional contract and can drift independently from consumer constants;
- runner images and external repositories remain environmental inputs, so exact-tree evidence is not
  a proof that all external services will remain available later.

#### CP4A reuse defect, repaired by CP4B.1

PR #59 run `34770840853` completed Full and uploaded artifact `10321387874` for its exact synthetic
merge/tree. Final main tree `e4b408ce...` matched. Nevertheless `FULL_STEP` still equals
`Run full validation`, while current YAML reports `Run Full validation`. The candidate loop therefore
accepted no run and returned `required PR Full evidence is missing or ambiguous`. Main run
`34771225286` safely reran Full for 5m59s. Existing fixtures imported the same stale constant, so
they tested internal agreement rather than agreement with YAML. CP4B.1 corrected the literal,
derived the workflow API endpoint from the workflow-path selector, and added an independent parser
of the real workflow job/step contract.

### Change-aware validation safety map

| Change area | Current offline/JVM selection | Safety judgment | Conservative boundary |
|---|---|---|---|
| Documentation only | no offline test unless mapped; non-Android | Safe for PR when links/hygiene are checked | Main currently runs all tooling + Debug Full because no reusable PR Full exists; this is policy duplication, not a docs need |
| `hosted_upstream.py` / ownership policy | `test_hosted_upstream.py`; ownership policy also forces Full | Safe narrow feedback; hosted upstream imports many Git/security semantics | Workflow/policy/shared or unknown changes require all offline tests/Full |
| `resolve_upstream.py` | `test_resolve_upstream.py` | Safe narrow feedback for resolver-only change | Cross-change with hosted policy or shared selector must broaden |
| Development release | `test_mosaic_development_release.py` | Safe narrow feedback for isolated edits | Imports classification, version, signer, verification, presentation; changes to those shared helpers require their suites or all |
| Stable/Hold | corresponding Stable/Hold test | Individual suites share Development publisher and fixtures | Shared manifest/publisher/API changes require all release suites |
| Signing/verification | signing or APK-verification suite | Safe for direct helper change only | certificate policy, manifest, signer composite, workflows, or payload helpers require Full/all release suites |
| Validation classifier/policy | `test_mosaic_validation_policy.py` (classification has its own suite) | Mapping is high leverage and easy to underselect | classifier, reuse, workflow or unclassified path stays Full; add cross-file YAML/constant checks |
| Production app mapped package | package-level JVM patterns plus compile | Reasonable fast feedback, not proof of complete app integration | unmapped production source gets all-JVM fallback; build/proto/persistence/update/release source forces Full |
| Test-only Android path | exact test-class wildcard | Reasonable | migration/androidTest and release source remain high/Full |
| Unknown script/config/path | unknown/high -> Full, all offline | Correct fail-closed behavior | Never broadly label extensions/directories merely to save time |

`offline_test_pattern` can express one filename; more than one relevant mapped suite deliberately
falls back to `test_*.py`. The import graph is cross-cutting: Hold imports Stable and Development;
Stable imports Development; Development imports classification, version, signing, verification, and
delivery output. Filename proximity alone is therefore insufficient for release/security changes.

### Gradle and toolchain comparison

| Path | Variant/tasks | Process settings | Toolchain/cache | Equivalent artifact? |
|---|---|---|---|---|
| Local Standard | `compileDefaultDebugKotlin`, `testDefaultDebugUnitTest --tests ...` | wrapper default; daemon permitted | discovered local JDK; `GRADLE_USER_HOME`; project cache/parallel/config-cache enabled | No APK; focused Debug evidence |
| Local Full | compile + all default-debug JVM tests + `assembleDefaultDebug` | wrapper default; daemon permitted | same local toolchain/caches | Debug test APK only |
| PR/main Full | same three Debug tasks with `--no-daemon` | hosted `GRADLE_OPTS` gives 8 GiB; project parallel/cache/config-cache remain | Gradle 9.6.1; Zulu 21; Build Tools 36; NDK 29; setup-java Gradle cache | Same variant/task goals, different clean hosted environment |
| PR targeted | compile + filtered JVM tests, `--no-daemon` | same hosted setup | same | No APK and not reusable as Full |
| Development Build | `assembleDefaultRelease -PmosaicPublication=true --no-daemon --no-parallel --max-workers=1` | bounded sequential workers | fresh job, same composite; full history for version allocation | Distinct minified/resource-shrunk unsigned Release APK |
| Signing Diagnostic | Debug Full with publication property, then Release assemble; both `--no-parallel --max-workers=1` | sequential same workspace | same hosted composite | Independent diagnostic Release input, not published |

Pinned project versions are Gradle 9.6.1, AGP 9.2.1, Kotlin 2.4.20 (language 2.3), KSP 2.3.9,
compile/target SDK 37, and min SDK 23. `gradle.properties` sets 2 GiB daemon heap, build cache,
parallelism, and configuration cache. Hosted `GRADLE_OPTS` raises the daemon heap for CI. Every app
configuration invokes `mosaic_version.py`; only publication builds pass `-PmosaicPublication=true`.
OpenAPI generation, protobuf, KSP/Room/Hilt, resources, and variant-specific Android tasks are part
of the graph. Debug execution cannot substitute for Release assembly.

### Scenario analysis

| Scenario | Current path | Duplication/unnecessary work | Correctness outcome |
|---|---|---|---|
| Docs-only PR | PR changed-range hooks; usually no offline/Android; main all-hooks + all-offline + setup + Debug Full; release eligibility skips | Main Full cannot reuse because PR emitted no Full artifact | Safe but expensive |
| Tooling-only normal risk | PR hooks + mapped offline, no Android; main broad suite + Debug Full; release skips | Main work is broader than PR and not reusable | Safe, conservative |
| Tooling-only high risk | local/prepare often Full; PR Full; main should reuse; release skips | Local twice; PR/main broad tooling repeats; the historical name drift forced main Android rerun before CP4B.1 | Safe fallback preserved; CP4B.1 restored exact-tree reuse |
| Normal application change | local/prepare Standard targeted; PR targeted; main Full; Release if unpublished range is APK-relevant | Debug compile/tests run local, PR, main; no exact Full artifact from PR | Correct current model; candidate for authoritative PR Full when `releaseRequired=true` |
| High-risk application/security change | local/prepare Full; PR Full; main exact-tree reuse/fallback | local broad repetition | Correct conservative boundary |
| APK-producing change | main Full/reuse, then distinct Release Build, isolated Sign, Publish | Release is not Debug duplication; handoff checks are security-essential | Correct and live proven |
| Exact PR tree reusable | PR Full artifact + exact two-parent final tree; main broad tooling then reuse | Main still runs all hooks/offline and Android setup even though Gradle skips | Safe reuse opportunity; run #42 proves it |
| Rebase/main movement | PR synthetic merge is regenerated; old local evidence has no standing; main only reuses exact parent/tree | local work may be stale | Correct fallback/revalidation |
| PR synthetic merge differs from local | PR tests merge ref; main tree comparison decides reuse | local Full cannot establish integration result | Correct hosted authority |
| Upstream clean FOLLOW | Observe -> native two-parent normal PR -> forced PR Full -> human merge -> main reuse/fallback -> eligibility | resolver/local Standard+Full absent for clean path; normal PR pipeline only | PR #55 live proved this path |
| Upstream REVIEW/conflict | Draft; conflict resolver performs semantic native merge; prepare requires Standard + Full; PR forced Full | local Standard/Full plus PR Full is intentional today but likely over-validates | Keep until natural conflict evidence supports simplification |
| Transient dependency failure | hosted Gradle fails before compile; unchanged rerun may pass | retry repeats setup/work | Correct red signal; do not change dependencies from one transient incident |
| Runner queue delay | job remains queued before `started_at` | no execution occurred | External latency; exclude from performance totals |
| Failed-job rerun | same run ID, incremented attempt; successful jobs retained; failed job/dependents rerun | avoids rebuild/resign | Live-proven for Sign and Publish failures |
| Stable Promotion | authenticate current Development -> Environment -> exact-byte publish | APK verified again, but no build/sign | Necessary privileged promotion boundary |
| Hold Release | authenticate latest Stable -> Environment -> recheck/hold | APK verified again, but no build/sign | Necessary emergency boundary |

### Failure and retry taxonomy

| Incident | Classification | Evidence and consequence |
|---|---|---|
| PR #57 dependency resolution | **EXTERNAL/ENVIRONMENTAL**; clean-host reproducibility signal | Attempt 1 could not find `jellyfin-core-android-debug:1.7.1` and `programguide-android-debug:1.6.0` before compilation. The same SHA passed attempt 3 unchanged; local Full passed; CP2 changed no repositories/dependencies/Gradle. Logs do not retain an HTTP/status cause, so the exact transient repository/metadata/cache mechanism is unproved. One rerun was justified; recurrence belongs to T0-2. |
| About 20-minute hosted wait | **EXTERNAL/ENVIRONMENTAL** | Queue/runner assignment delay, not test/build time. No repository change follows from it. |
| Windows Application Control/pre-commit failures | **EXTERNAL/ENVIRONMENTAL** | Local pre-commit environment execution was blocked until recreated. Preserve as environment failure, not product test failure. |
| Upstream push failure | **PERMISSION/POLICY FAILURE** | Candidate contained `.github/workflows/release.yml`; scoped App had contents/PR write but not workflow write. No branch/PR was created. CP2 exposed a deliberate least-privilege limit; it did not break candidate construction. Do not broaden permission for a retired downstream-owned workflow. Current generic subprocess error discards the exact `git push --porcelain` rejection from both streams; bounded redacted diagnostics remain CP6/T0-2 work. |
| PR #59 reuse refusal | **REPOSITORY DEFECT, FAIL-SAFE** | Step-name contract drift caused false missing/ambiguous evidence; main Full ran and passed. Fix before depending on reuse. |
| Case A/Case B Development failures | **CONTROLLED/LIVE RECOVERY EVIDENCE** | Native failed-job rerun retained successful Build, then successful Build+Sign, respectively. Run ID stayed fixed, attempt increased, prior artifacts were authenticated, and no duplicate/conflicting publication occurred. |

### Findings by required classification

#### DUPLICATE WORK

- Explicit local Full followed by prepare-pr Full repeats identical hooks, test names, Gradle goals,
  and whitespace checks without an identity-bearing evidence handoff.
- Local Full, prepare Full, PR, and main can execute the same offline tests. For PR #46 the mapped
  release test logically ran four times; for CP3 all 232 local tests ran twice before PR.
- Exact-tree main runs repeat all-files pre-commit and all offline tooling even when PR evidence is
  accepted; current PR evidence covers Android Full but not necessarily all offline tooling.

#### WRONG TIMING

- Main installs the Android/JDK/SDK/NDK toolchain before it acts on successful reuse. Run #46 paid
  45s for setup with no Gradle consumer.
- Development release relevance is evaluated only in the downstream Build job, so docs/non-APK
  main pushes can still pay for protected-main Debug Full first. Validation risk and release
  relevance are distinct, but main currently has no change-aware validation decision.
- The Signing Diagnostic asks the human to repeat the current SHA even though the workflow already
  authenticates protected main and Environment approval supplies authorization.

#### UNUSED EVIDENCE

- A standalone successful `validate-local -Level Full` produces rich logs but no stable commit/tree
  attestation, so prepare-pr cannot consume it.
- Prepare records local validation level/time and an exact later commit tree, but no hosted stage can
  authenticate the local execution; PR prose is informational only.
- On a successful main reuse decision, completed Android setup is unused.

#### MISSING EVIDENCE

- Local validation summaries do not record base, HEAD, Git tree, snapshot digest, validation-policy
  identity, or a tamper-resistant result suitable for reuse.
- Reuse tests do not compare their required job/step contract to the actual workflow YAML; the CP3
  capitalization drift escaped offline coverage.
- The transient Maven resolution failure lacks HTTP/repository-response evidence, so its exact cause
  cannot be distinguished among repository availability, metadata/cache behavior, or transport.
- Upstream push errors discard bounded sanitized stdout/stderr, hiding actionable rejection detail.

#### SAFE REUSE OPPORTUNITY

- Fix and retain exact-tree PR-to-main Debug Full reuse; it is already live proven.
- Move Android setup behind a failed reuse decision.
- Avoid two consecutive local Full executions for an unchanged reviewed scope. Prefer one entry
  point rather than inventing weak trust in `validation.log`.
- If CP4B moves all release-required/high-risk broad validation to PR CI, persist exact committed
  tree/contract evidence and reuse it on main; uncertainty must still run Full.

#### TARGETED VALIDATION OPPORTUNITY

- Keep mapped offline suites and package JVM filters for quick local/PR feedback.
- Expand only from proven dependency edges; shared release/provenance/classifier/workflow inputs
  remain Full/all-suite.
- A docs-only PR can remain non-Android, but protected main needs authenticated evidence for that
  selected path before its blanket Full can be removed.

#### MUST REMAIN AUTHORITATIVE

- Required GitHub `CI / Full validation` and branch protection.
- Protected-main fallback when exact tree/run/job/step/artifact proof is uncertain.
- Final-context Release assembly and first-parent version allocation.
- Unsigned/signed artifact ID, digest, source/tree, package/version/payload and signer checks.
- Isolated `release-sign`, `release-promote`, and `release-hold` Environment boundaries.
- Idempotent immutable/rolling publication and current-main freshness checks.
- Native upstream parent/tree/ref/ownership checks, no-force behavior, Draft conflict safety, and
  human merge/reject authority.

#### EXTERNAL/ENVIRONMENTAL

- Runner queue delay, Maven/network availability, runner image/tool installation behavior, and local
  Windows process/filesystem/Application Control overhead must be measured separately from logic.
- Hosted tool setup restores large caches and downloads SDK tooling; changing this belongs to
  performance/reproducibility work, not validation-policy simplification.

#### DEFER TO T0-2

- Gradle/cache/setup optimization, dependency-repository hardening, runner image pinning, and offline
  fixture performance.
- Whether CI's `cancel-in-progress: true` on `main` is appropriate now that the same workflow owns
  Build/Sign/Publish. Publication is idempotent and native rerun exists, but cancellation during
  remote mutation deserves an explicit concurrency threat-model audit.
- Sanitized upstream push rejection diagnostics and any decision about workflow-write App authority.
- Removal or redesign of the manual upstream break-glass helper.

### CP4B design principles

1. Use one normal publication entry point. Do not tell operators to run Full and then let
   `prepare-pr` run the same Full again.
2. Keep local checks fast, relevant, and fail-fast; retain Full as an explicit diagnostic/on-demand
   option rather than the routine source of authoritative integration evidence.
3. Establish a stable commit/tree before expensive broad authoritative validation. PR CI must test
   the actual synthetic merge, not trust a mutable worktree.
4. Persist evidence only when a downstream consumer exists. Evidence must bind tree, validation
   contract, run/job/step outcome, and artifact identity; prose/log files are not attestations.
5. Reuse authenticated exact-tree evidence downstream. Any rebase, parent/tree difference, failed,
   missing, expired, ambiguous, or contract-mismatched evidence must fall back conservatively.
6. Keep change selection deterministic and trusted-base controlled. Targeted tests are feedback and
   authority only for explicitly modeled dependencies; unknown/cross-cutting security inputs remain
   Full.
7. Keep Debug validation, Release assembly, signing, and publication conceptually separate. They
   validate different variants or cross different privilege/byte boundaries.
8. Measure active execution, setup, queue, Environment wait, and publication independently. Do not
   optimize from labels or wall-clock anecdotes.
9. Prefer GitHub's required checks, artifacts, job dependencies, reruns, and merge controls over a
   second local state machine.
10. Preserve the current check/job names until the separately coordinated machine-contract and
    ruleset migration.

### Preconditions and blockers for CP4B

1. **COMPLETE / HOSTED VALIDATED IN CP4B.1:** correct the reuse step-name contract, add
   YAML-to-consumer regression coverage, and prove exact-tree reuse through PR #60 and protected
   main without fallback Gradle Full.
2. Decide the intended authoritative composition of “Full”: PR Full currently means full Android
   but can run only a mapped offline suite, while main always supplies the all-offline layer. Any
   removal of main broad tooling must first move or authenticate that evidence.
3. Define the evidence contract for non-Full PR paths before removing blanket main Full. A targeted
   or non-Android PR currently emits no reusable main evidence.
4. Do not create a local-validation attestation merely to preserve operator Full. First simplify the
   operator contract so prepare-pr owns the one necessary local gate; add reusable local evidence
   only if a distinct remaining consumer is demonstrated.
5. Keep Release/Sign/Publish and Stable/Hold boundary checks out of the duplicate-test cleanup.

The narrow exact-tree reuse contract repair is complete and hosted validated. The smallest safe
CP4B.2 step is defining complete policy evidence for both non-Android and Full committed PR paths.
Only after that replacement is implemented and hosted-proven may local/prepare duplication or
blanket main execution change.


---

## CP3 implementation record

- The supported Actions sidebar is now designed as `CI`, `Hold Release`, `Signing Diagnostic`,
  `Stable Promotion`, and `Upstream Synchronization`. Only the three presentation-only workflow
  display names changed; workflow paths, triggers, job IDs, required checks, permissions,
  Environments, artifacts, tags, version fields, and provenance identities did not.
- `CI` now explains the selected PR validation path as soon as classification succeeds, then gives
  a concise final result. PR APK, protected-main reuse/fallback, Development eligibility, and
  Development/Stable publication summaries put the result and operator action first while keeping
  SHA/tree/run/artifact/classifier evidence in collapsed technical details.
- Stable Prepare now says `Ready to release: vX` and explains the approval wait; successful Stable
  publication says `Released: vX`. Hold uses `Ready to hold: vX` and `Held: vX`, and both workflows
  surface refusal/failure action without hiding it in technical evidence.
- Signing Diagnostic states before expensive work that it validates signing only and publishes
  nothing. Its manual SHA input remains unchanged and belongs to CP4.
- Upstream summaries now distinguish no changes, attention-free candidates, review-required work,
  and changes `observed but excluded`. The main result and required action stay visible; operator
  links, classifications, paths, schedule evidence, and full JSON are progressively disclosed.
- `Upstream check · Manual/Scheduled` uses a stable event description. The no-change summary says
  the existing UTC schedule will check again; no historical run title embeds a future wall-clock
  time or local/DST conversion.
- The requested bounded/sanitized `git push --porcelain` rejection diagnostic is **not cosmetic**:
  it requires changing subprocess/error evidence behavior. S07 is therefore retained for CP6 (or
  T0-2 security review), rather than being smuggled into CP3.
- Focused static/output tests protect the new display names and summary hierarchy while retaining
  `CI`, `Full validation`, `Build Development Release`, `Sign Development`, and
  `Publish Development` as unchanged machine contracts. Hosted rendering/sidebar acceptance and
  the repository Full gate remain external acceptance for this branch.

## CP2 implementation record

- Removed `.github/workflows/main.yml` (`Development build`). Its only job was restricted to the
  upstream repository, and Mosaic's `CI` already exclusively owns supported Development Build,
  Sign, and Publish delivery.
- Removed `.github/workflows/release.yml` (`Create release`). Its default APK role was superseded
  by Stable Promotion, and Baseline T0 explicitly does **not** own Appstore or Fire TV AAB
  distribution. If either store becomes a product requirement, it needs a deliberately supported
  pipeline rather than restoration of this secret-bearing inherited workflow.
- Both removed paths are explicit `DOWNSTREAM-OWNED` absences in
  `scripts/upstream_ownership_policy.json`. I06 still observes upstream changes and records their
  evidence, but preserves the downstream deletion instead of resurrecting either publisher.
- No helper became dead: both removed workflows were self-contained. Gradle's Appstore/Fire TV
  flavors remain application build capability, not a currently supported Mosaic distribution
  channel. Historical AAB behavior remains documented in the Item 6 audit.
- Focused local evidence: all 54 hosted-upstream fixtures and all 10 delivery/presentation tests
  pass. The former proves approved absence, REVIEW fallback, native candidate safety, retries and
  refusals; the latter proves the supported workflow file set is exactly the five workflows below.
- Classification after moving this durable ledger under `docs/`: the complete branch is
  `tooling-only / high`, selects Full validation because it changes security-sensitive workflow
  ownership, and correctly has `releaseRequired=false` because it changes no APK input.
- Hosted acceptance confirmed that GitHub's Actions sidebar dropped both deleted entries and now
  exposes exactly the five supported workflows listed below.

### CP2 hosted acceptance and operational evidence

- The live Actions sidebar is `CI`, `Hold Release`, `Mosaic — Signing Diagnostic`,
  `Mosaic — Stable Promotion`, and `Upstream — Synchronization`. Ownership is unambiguous:
  Development delivery belongs to CI; normal Stable publication to Stable Promotion; emergency
  Stable containment to Hold Release; credential/signature verification to Signing Diagnostic;
  and upstream integration to Upstream Synchronization.
- The protected-main acceptance run completed in **1m53s**: `Full validation` **1m35s**, Build
  Development Release **11s**, and Sign/Publish **0s (skipped)**. Exact-tree PR evidence was reused,
  and the complete CP2 range remained `tooling-only / high` with `releaseRequired=false`; no APK
  was built, signed, or published.
- The first hosted PR attempt failed before compilation while resolving
  `jellyfin-core-android-debug:1.7.1` and `programguide-android-debug:1.6.0`. Both artifacts exist,
  local Full had passed, CP2 changed no dependency/Gradle/repository configuration, and an unchanged
  rerun passed. Treat this as a transient clean-host dependency/cache/repository-resolution incident,
  not a CP2 regression or evidence for a speculative dependency change.
- A separate protected-main attempt spent about **20 minutes** waiting for a hosted runner before
  validation began; its rerun was scheduled normally and completed in 1m53s. Queue time, setup,
  validation/build execution, and signing/publication waits are distinct measurements.
- One hosted run measured 232 offline tooling tests in about **18.6s**, while restoring roughly
  **801 MB** of Gradle cache and **133 MB** of wrapper data. JDK setup took about **15–20s** and
  Android setup about **20s**, rejected preinstalled command-line tools, installed another toolset,
  and emitted noisy legacy/preview license output. These are CP7 evidence, not an optimization made
  by CP2.

### Upstream publication lesson discovered during CP2 acceptance

- A separate Upstream Synchronization observation succeeded, but publication of its pre-CP2
  candidate failed at `git push`. The candidate included `.github/workflows/release.yml`; the
  repository-scoped App token intentionally had `contents: write` and `pull-requests: write`, not
  `workflows: write`. This is the high-confidence refusal cause, although current subprocess
  diagnostics discard the exact push rejection. The retained outcome recorded only the generic
  publication error; no candidate branch or PR was created.
- Do not broaden the App permission or retry that obsolete candidate. After CP2, the deleted
  workflow is a downstream-owned absence: a fresh observation should record and exclude the path,
  preserve the deletion, and carry useful non-workflow changes through the normal native candidate.
  “Observed but excluded” is not “synced”; accepted Git ancestry remains the terminal integration
  fact.
- A future policy decision must choose between isolated repository-scoped Workflows write and a
  narrower manual path for genuine upstream workflow-file candidates. Prefer the narrower manual
  path unless a concrete automation use case justifies the additional privilege. Retired
  downstream-owned workflows remain excluded and require no Workflows permission.
- Later diagnostics should classify `git push --porcelain` using bounded, sanitized stdout and
  stderr, and report the operation, remote/refspec, exit code, destination existence, and safe
  rejection excerpt. Credentials remain environment-only and must never appear in summaries or
  artifacts.

## Method and boundaries

The CP1 audit covered all seven files then under `.github/workflows`, both composite actions,
Python and PowerShell operator-output producers, PR templates/tooling, release helpers, validation
helpers, active tests that bind presentation, and documentation that identifies external/manual
contracts. It found **114 YAML workflow/action name declarations** (workflow, run, job, step, and
composite-action names) plus **31 generated presentation families** in scripts/templates: **145
human-visible labels or label families** in the CP1 baseline before ordinary Git/Gradle tool output.

Registry rows below consolidate repeated setup/checkout/upload labels only when they share one
producer, risk, and recommended treatment. `LOW`, `MEDIUM`, and `HIGH` refer to migration risk, not
quality. `HIGH` means the label or adjacent identity participates in a ruleset, lookup,
authentication, provenance, evidence reuse, or externally configured contract.

## Workflow registry

| Workflow | File | Trigger | Purpose | Current operator value | Recommendation |
|---|---|---|---|---|---|
| `CI` | `.github/workflows/ci.yml` | PR to `main`; push to `main`; manual | Risk-tiered PR validation, protected-main exact-tree reuse/fallback, Development Build → Sign → Publish | Essential; one run owns validation and Development delivery | **KEEP**; presentation cleanup only until coordinated `Full validation` migration |
| `Hold Release` | `.github/workflows/hold-release.yml` | Manual on protected `main` | Authenticate current Stable, await `release-hold`, stop advertising it | Essential emergency Stable containment | **KEEP** |
| `Stable Promotion` | `.github/workflows/mosaic-stable-promotion.yml` | Manual on protected `main` | Authenticate current Development, await `release-promote`, publish exact bytes as Stable | Essential normal Stable promotion | **RENAMED IN CP3**; file/API/provenance identities preserved |
| `Signing Diagnostic` | `.github/workflows/mosaic-signing-exercise.yml` | Manual with exact-main SHA | Non-publishing signing credential/certificate diagnostic | Legitimate after key/secret/Environment changes, but expensive and asks the operator to repeat `github.sha` | **RENAMED IN CP3**; zero-input authentication remains CP4 |
| `Upstream Synchronization` | `.github/workflows/upstream-sync.yml` | Fixed schedule and manual | Observe/classify upstream; create/reuse native normal/Draft candidate | Essential I06 operator surface | **RENAMED IN CP3**; workflow path, outcomes and evidence contracts preserved |
| `Development build` | `.github/workflows/main.yml` | Push to `main` or `develop/*`; job restricted to `damontecres/Wholphin` | Inherited upstream rolling build/release | No downstream job could run; the row duplicated CI-owned Development delivery | **REMOVED IN CP2**; path remains an explicit downstream-owned absence |
| `Create release` | `.github/workflows/release.yml` | `v*` tag; job restricted to `damontecres/Wholphin` | Inherited upstream signed APK/AAB/mapping draft release | Could not run downstream; default APK/Stable responsibility was superseded and store/AAB distribution is outside Baseline T0 | **REMOVED IN CP2**; historical AAB evidence retained, path is downstream-owned absence |

### Removal evidence and CP2 decisions

- `main.yml` has no downstream execution path: its sole job requires
  `github.repository == 'damontecres/Wholphin'`. Current `.github/workflows/ci.yml` owns the only
  supported Development Build, Sign, and Publish chain. Repository search found only policy,
  fixtures, tests, and historical docs consuming its path/name; no runtime artifact consumer.
- `release.yml` was equally upstream-repository guarded. Its default APK release is superseded by
  zero-input Stable Promotion. It uniquely documented/built `bundleAppstoreRelease` and
  `bundleFiretvRelease` AABs, but Mosaic has no store publication contract or AAB consumer.
  Baseline T0 now explicitly defers those channels; removal does not imply AAB support moved.
- Neither inherited workflow should be consolidated into CI. They should disappear, not create a
  second publisher or release authority.

## Actions sidebar

### CP1 baseline sidebar

```text
CI
Create release
Development build
Hold Release
Mosaic — Signing Diagnostic
Mosaic — Stable Promotion
Upstream — Synchronization
```

### Supported sidebar after CP2 (historical hosted evidence)

```text
CI
Hold Release
Mosaic — Signing Diagnostic
Mosaic — Stable Promotion
Upstream — Synchronization
```

### Supported sidebar after CP3 implementation

```text
CI
Hold Release
Signing Diagnostic
Stable Promotion
Upstream Synchronization
```

| Current | Final | Decision | Constraint |
|---|---|---|---|
| `CI` | `CI` | KEEP | Renaming changes `CI / Full validation`, a required and authenticated check contract |
| `Create release` | absent | REMOVED IN CP2 | Store/AAB distribution is deliberately unsupported in Baseline T0 |
| `Development build` | absent | REMOVED IN CP2 | Downstream CI already owns Development delivery |
| `Hold Release` | unchanged | KEEP | Already concise and operator-oriented |
| `Mosaic — Signing Diagnostic` | `Signing Diagnostic` | RENAMED IN CP3; SIMPLIFY LATER | Tests/docs bind display; path/artifact/auth contracts remain |
| `Mosaic — Stable Promotion` | `Stable Promotion` | RENAMED IN CP3 | Tests/docs bind display; file and release contracts remain |
| `Upstream — Synchronization` | `Upstream Synchronization` | RENAMED IN CP3 | Tests/docs bind display; workflow path and evidence consumers remain |

Repository context makes the `Mosaic —` and `Upstream —` prefixes redundant. This is not permission
to rename `CI`, job IDs, workflow filenames, tags, artifacts, Environments, or classifier values.

## Presentation registry

| ID | Surface/type | Current label(s) | Producer | Proposed human presentation | Contract? | Risk |
|---|---|---|---|---|---|---|
| P01 | Workflow/check | `CI` | `ci.yml:1` | Keep | GitHub ruleset/check prefix | HIGH |
| P02 | CI run | `PR #N · branch`; native push title; `Validate · branch` | `ci.yml:2-7` | Keep; already identifies context | Tests/docs | MEDIUM |
| P03 | CI job/check | `Full validation` | `ci.yml:33` | Deferred coordinated migration to `Prepare` | Ruleset, reuse, provenance, upstream selection | HIGH |
| P04 | CI validation steps | `Classify PR validation`; pre-commit; offline fixtures; full/targeted validation | `ci.yml:45-121` | `Plan validation`; `Check changed files`; `Run tooling tests`; `Validate application` | Tests and conditional structure | MEDIUM |
| P05 | PR APK steps | locate/record/upload/summarize PR test APK | `ci.yml:123-205` | `Prepare test APK`; one result statement, evidence collapsed | Artifact/evidence identity adjacent | HIGH |
| P06 | PR policy summary | `Mosaic PR validation`, `release relevance`, `validation risk`, `selected path` | `ci.yml:245-287` | `Validation plan/result`; translate to plain reason and release consequence | Classifier values remain machine contract | MEDIUM |
| P07 | Main reuse summary | `Protected-main validation`; executed/reused reason | `ci.yml:207-243` | `Full validation reused from PR #N` or `Full validation required — reason` | Reuse evidence fields | HIGH |
| P08 | Development jobs | `Build Development Release`; `Sign Development`; `Publish Development` | `ci.yml:301,384,454` | `Build`; `Sign`; `Publish` after tests/contracts migrate | Tests, provenance docs; job IDs remain stable | MEDIUM |
| P09 | Development classification | `Classify complete unpublished Development range` | `ci.yml:327`, `mosaic_development_release.py:368-413` | Early `Release plan`: build required/not required and why | Outputs drive jobs | HIGH |
| P10 | Release build steps | setup/build/prepare/upload authoritative unsigned Release | `ci.yml:332-352` | Concise verbs; hide “authoritative” from primary labels | Artifact ID/name/provenance remain | MEDIUM |
| P11 | Mapping steps/summary | retain/upload/summarize Release mapping diagnostic | `ci.yml:354-381` | `Save diagnostic mapping`; technical link collapsed | Artifact identity | MEDIUM |
| P12 | Sign steps | require ID, verify input, sign, verify payload | `ci.yml:408-452` | `Authenticate unsigned APK`; `Sign`; `Verify signed APK` | Security boundary | HIGH |
| P13 | Publish steps | require ID, validate manifest, publish bytes | `ci.yml:468-483` | `Authenticate signed APK`; `Publish Development` | Publication boundary | HIGH |
| P14 | Development artifacts | `unsigned-mosaic-main-ci-…`; `signed-mosaic-development-…`; `mapping-…` | `ci.yml`, signing/release helpers | Keep machine names; show `Development vX` outside details | Exact artifact auth | HIGH |
| P15 | Development Releases | tags `downstream-build-N`, `develop`; title/body `Mosaic vX — Development [Build N]` | `mosaic_delivery_output.py:17-36` | Human: `vX-N-gSHA — Development`; machine tags unchanged; deferred compatibility audit | Updater reads Release `name`; tags/manifests | HIGH |
| P16 | Development result | `Published · Mosaic vX` plus source/hash/run | `mosaic_delivery_output.py:39-57` | `Published Development vX`; main result visible, evidence collapsed | Tests/docs | MEDIUM |
| P17 | Stable workflow/run | `Mosaic — Stable Promotion`; `Stable Promotion` | `mosaic-stable-promotion.yml:1-2` | `Stable Promotion` | Tests/docs, not file identity | MEDIUM |
| P18 | Stable jobs | `Prepare`; `Release` | `mosaic-stable-promotion.yml:21,74` | Keep | Environment boundary/operator model | MEDIUM |
| P19 | Stable prepare steps | resolve/authenticate; certificate/package/version; manifest/hash | stable workflow lines 43-54 | `Authenticate Development candidate`; details collapsed | Security/provenance | HIGH |
| P20 | Stable summaries | `Ready to release`; `Promoted · Mosaic vX`; failed stage | stable workflow; `mosaic_delivery_output.py` | `Ready to release: vX`; `Released: vX`; explicit next action/refusal | Tests/docs | MEDIUM |
| P21 | Stable artifact | `stable-downstream-build-N-run-R-attempt-A` | stable workflow line 63 | Keep machine name; display `Stable vX evidence` | Exact handoff/auth | HIGH |
| P22 | Stable Release | tag `mosaic-vX`, name/version `vX`, exact Development bytes | `mosaic_stable.py`, delivery output | Human `vX — Stable`; retain machine tag/update compatibility | Updater/provenance | HIGH |
| P23 | Hold workflow/run/jobs | `Hold Release`; `Prepare`; `Hold` | `hold-release.yml` | Keep | Tests/docs and Environments | MEDIUM |
| P24 | Hold steps | `Get Release`; long authentication label; artifact requirement; `Hold` | hold workflow | `Find current Stable`; `Authenticate Stable`; `Hold Release` | Security sequence | MEDIUM |
| P25 | Hold summaries | `Ready to hold`; `Held: ✕ … → …`; `Refused` | hold workflow; `mosaic_hold_release.py` | Keep result; replace symbols with accessible words where useful | Tests/docs | MEDIUM |
| P26 | Hold artifact | `hold-release-ID-run-R-attempt-A` | hold workflow line 66 | Keep machine name; collapse exact identity | Exact handoff/auth | HIGH |
| P27 | Signing workflow/run | `Mosaic — Signing Diagnostic`; `Diagnose signing from SHA` | signing workflow lines 1-2 | `Signing Diagnostic`; run `Signing diagnostic · current main` | Tests/docs | MEDIUM |
| P28 | Signing input | required `expected_sha` | signing workflow lines 5-10 | Zero input; authenticate dispatched protected-main `github.sha` | Current authorization predicate | HIGH |
| P29 | Signing jobs/steps | long validate/build/sign/verify labels | signing workflow lines 28-142 | `Prepare diagnostic APK`; `Sign and verify`; concise substeps | Security boundaries/tests | MEDIUM |
| P30 | Signing artifacts | `unsigned/signed-mosaic-signing-exercise-…` | workflow and `mosaic_signing_exercise.py` | Preserve exact names; show signed diagnostic version prominently | Artifact validation | HIGH |
| P31 | Signing result | `Mosaic signing diagnostic verified`, artifact/source/retention | signing workflow line 133 | `Signing verified`; action/no-publication statement visible; details collapsed | Tests/docs | MEDIUM |
| P32 | Upstream workflow/run | `Upstream — Synchronization`; `Observe upstream · event` | upstream workflow lines 1-2 | `Upstream Synchronization`; `Upstream check · Scheduled/Manual` | Tests/docs | MEDIUM |
| P33 | Upstream jobs/steps | `Observe upstream integration`; read-only attempt; retain; publish/recheck | upstream workflow | `Observe`; `Publish candidate`; concise authentication verbs | Credential/condition structure | MEDIUM |
| P34 | Upstream outcomes | `No upstream delta`; `Ready candidate`; `Review required · Draft candidate`; etc. | `hosted_upstream.py:333-369` | Plain first-line counts/result/action; keep machine outcome in details | Tests and operational semantics | MEDIUM |
| P35 | Ownership classes | `FOLLOW`, `REVIEW`, `DOWNSTREAM-OWNED` | ownership policy/hosted summary | Keep machine tokens in evidence; explain as “integrate”, “review”, “preserve Mosaic” | Policy/classification | HIGH |
| P36 | Upstream PR titles | `chore: synchronize official upstream`; `chore: review upstream changes to …` | `hosted_upstream.py:323-330` | `Sync official upstream`; `Review upstream changes: area` | Tests/dedup uses branch/evidence, not title | MEDIUM |
| P37 | Upstream PR body | incoming/attention counts, filenames, run link, `<details>` evidence | `hosted_upstream.py:271-321,747-762` | `What changed / Why review / Validation / Next`; retain details collapsed | Episode marker/evidence embedded | HIGH |
| P38 | Upstream artifacts | `upstream-observation-A`; `upstream-outcome-A` | upstream workflow | Keep machine names; show operator result in summary | Resolver retrieval/auth | HIGH |
| P39 | Resolver CLI | candidate list, dependency states, publication plan, `Ready to PUSH?` | `resolve_upstream.py:604-843` | Sentence case; lead with action and next step; technical identities in prompt/log | Tests/operator contract | MEDIUM |
| P40 | Resolver prompt | Incoming, attention, CI, constraints, exact evidence | `resolve_upstream.py:692-809` | Keep bounded handoff; collapse/reference machine evidence where safe | Semantic safety contract | HIGH |
| P41 | Prepare-pr phases | uppercase `AUDIT/VALIDATE/STAGE/COMMIT/PUBLISH`, `[RUN/PASS/FAIL]` | `prepare-pr.ps1` | Title case lifecycle with one-line intent/result | Tests/log parsers | MEDIUM |
| P42 | Prepare-pr scope | `TOTAL COMPLETE PR SCOPE`; tracked/untracked/raw stats | `prepare-pr.ps1:387-479` | `PR scope: N files`; details collapsed/logged | Snapshot confirmation | HIGH |
| P43 | Prepare-pr title | Conventional title inferred from branch | `prepare-pr.ps1:600-627` | Keep concise conventional title; allow explicit human title | Commit/PR title, tests | MEDIUM |
| P44 | Generated PR body | Description/title; Confirmed paths; application/UI flags; Testing; review-sensitive; docs/screenshots/AI | `prepare-pr.ps1:665-719`, PR template | Default `What changed / Why / Validation / Next`; collapse paths/evidence | Template/tests/review process | MEDIUM |
| P45 | Prepare-pr completion | PR URL, `Required CI / Full validation pending`, `Review/merge in GitHub` | `prepare-pr.ps1:769-784` | Add auto-merge armed/not armed and direct next action | GitHub capability/settings | MEDIUM |
| P46 | Prepare-pr logs | `prepare-pr.log`, `.logs/prepare-pr/<run>` | `prepare-pr.ps1`, `.gitignore` | Keep machine-safe names; show one final location | Local compatibility | LOW |
| P47 | Local validation plan | `Wholphin validation`; requested level; relevance/risk/path | `validate-local.ps1:112-118` | `Validation plan`; plain reason and actual checks | Classifier values in log only | MEDIUM |
| P48 | Local validation progress | `[N/T] stage [RUN/PASS/FAIL] duration → log` | `mosaic_output.ps1` | Keep concise lifecycle; sentence-case stages | Tests/operators | MEDIUM |
| P49 | Local validation result/logs | final success/failure, `.logs/validation`, `validation.log` | output/validation scripts | Keep visible; technical excerpt and logs progressive | Compatibility log consumer | MEDIUM |
| P50 | Shared setup/action labels | `Setup`, SDK/tool setup, `Sign exact Mosaic APK` | `.github/actions/*/action.yml` | `Set up Android build`; `Sign APK`; keep internal action paths | Multiple workflows/tests | MEDIUM |
| P51 | Inherited Development labels | workflow/build/sign/checksum/delete release | `main.yml` | Removed with workflow in CP2 | Downstream-owned absence | LOW |
| P52 | Inherited tag-release labels | `Create release`, build/AAB/mapping/draft Release | `release.yml` | Removed in CP2 after AAB ownership decision | Downstream-owned absence | MEDIUM |

Presentation registry: **52 grouped rows** — **2 LOW**, **30 MEDIUM**, **20 HIGH**. The most
important non-cosmetic labels are `CI`, `Full validation`, artifact names, tags/Release names,
Environment names, ownership tokens, embedded PR evidence markers, and exact SHA/run/tree fields.

## PR presentation audit

There are two automated PR producers:

1. `scripts/prepare-pr.ps1` generates a Conventional Commit title from the task branch (or explicit
   `-Title`) and a large body with Description, Confirmed paths, application/UI flags, Testing,
   Review-sensitive paths, Documentation, Screenshots, and AI/LLM usage. The default view repeats
   raw paths already available in GitHub Files changed and leaves “why”/“what next” partly implicit.
2. `scripts/hosted_upstream.py` creates a normal FOLLOW title or area-based REVIEW/conflict title.
   Its body correctly keeps exact technical evidence collapsed, but should foreground why human
   attention is or is not needed, validation state, and the next semantic action.

`.github/pull_request_template.md` supplies the manual fallback structure; no other active
maintenance workflow creates PRs. Recommended default body order is **What changed → Why →
Validation/result → What happens next**, followed by collapsed Confirmed Paths, review-sensitive
paths, provenance/authentication, and raw inventories. Never collapse the refusal, required operator
action, validation result, or Draft/ready state.

Prepare-pr currently creates/reuses the PR but neither enables nor arms auto-merge. It can invoke
`gh pr merge --auto --merge` after exact PR/head authentication for ordinary non-Draft PRs, but only
after repository auto-merge is enabled and the policy explicitly excludes upstream Drafts. That is
a process checkpoint with mocked/offline tests and hosted acceptance, not a cosmetic edit.

## Release and artifact presentation

| Channel | Human presentation today | Machine identity | Proposed later presentation |
|---|---|---|---|
| Development rolling | Release body `Mosaic vX — Development`; Release API name/version remains `vX`; result `Published · Mosaic vX` | tag `develop`; immutable `downstream-build-N`; manifest; exact signed artifact | `vStable-N-gSHA — Development`, Compare Changes from current Stable, primary install link; identities collapsed |
| Development immutable | `Mosaic vX — Development Build N` | tag/identity `downstream-build-N` | Treat as provenance/archive, not primary human release |
| Stable | name/title `vX`; body `Mosaic vX — Stable`; result `Promoted · Mosaic vX` | tag `mosaic-vX`; exact immutable source; manifest/APK digest | `vX — Stable`, Compare Changes from previous Stable, concise release result |

`UpdateChecker` compatibility makes Release `name`, tag, versionCode, filenames and manifest fields
HIGH-risk. The future Development version format is presentation work only after a dedicated
updater/versionCode/provenance audit. `Wholphin-release.apk` also remains a compatibility filename.

Operator-visible authenticated artifacts include PR Full Debug APK evidence, unsigned/signed main
Release handoffs, mappings, Stable verification evidence, Hold evidence, signing diagnostic
inputs/results, upstream observation/outcome, and failure diagnostics. Their long names are exact
machine identity and should not be prettified. Summaries should show `Development vX`, `Stable vX`,
`PR #N test APK`, or `Signing diagnostic vX`, with artifact IDs/names under Technical details.

Compare Changes belongs in Development bodies/summaries from current Stable source to Development
source, and in Stable bodies/summaries from previous Stable source to promoted source. Immutable
archive pages may retain exact source links without becoming the primary comparison surface.

## Output registry

| ID | Surface | Producer | Purpose / typical size | Default now | Available / shown | Recommendation |
|---|---|---|---|---|---|---|
| O01 | PR validation policy | `ci.yml` inline Python | decision + 2–4 checks, ~10 lines | visible | classification in seconds / end of validation job | REDESIGN SUMMARY |
| O02 | PR test APK | `ci.yml` inline Python | download + 8 identity fields/instructions, ~20 lines | visible | after Full / after Full | COLLAPSE |
| O03 | Main validation reuse | `ci.yml` inline Python | main result + tree/run evidence, 5–7 lines | visible | before Full / summary step after Full or skip | REDESIGN SUMMARY |
| O04 | Development eligibility | `mosaic_development_release.record_eligibility` | decision, SHAs, policy, full path list, variable | visible | start of Build job / after classification | REDESIGN SUMMARY |
| O05 | Release mapping | `ci.yml` printf | artifact URL/retention, 5 lines | visible | after Build / after upload | COLLAPSE |
| O06 | Development publication | `publication_summary` | release result + source/hash/build/run, ~10 lines | visible | after publish / after publish | REDESIGN SUMMARY |
| O07 | Stable prepare | stable workflow printf | ready version/link, 3 lines | visible | after authentication / after authentication | KEEP VISIBLE |
| O08 | Stable release/failure | `publication_summary`, workflow printf | result or failed stage, 5–10 lines | visible | after action / after action | REDESIGN SUMMARY |
| O09 | Hold prepare | hold workflow printf | ready version/link, 3 lines | visible | after authentication / after authentication | KEEP VISIBLE |
| O10 | Hold result/refusal | hold workflow/helper | held/fallback or refusal, 3–6 lines | visible | after recheck/mutation / immediately then | KEEP VISIBLE |
| O11 | Signing diagnostic | workflow printf | artifact/link/source/retention/no-publication, ~10 lines | visible | after sign / after sign | REDESIGN SUMMARY |
| O12 | Upstream Actions summary | `upstream_summary` | decision, policy counts, per-path lists, navigation, full JSON; tens/hundreds lines | visible | observation in seconds / at observation end | COLLAPSE |
| O13 | Upstream PR body | `candidate_body` | commits, attention, run, JSON, variable | evidence partly collapsed | at candidate creation / creation | REDESIGN SUMMARY |
| O14 | Resolver prompt | `codex_prompt` file | exact semantic task/evidence, large | file/log | after selection / immediately | MOVE TO ARTIFACT |
| O15 | Resolver CLI transcript | resolver print functions | candidates, dependencies, scope, filters, prompts, variable | visible | progressively / progressively | KEEP VISIBLE |
| O16 | Prepare-pr audit/transcript | `prepare-pr.ps1` | commits, every path/stat/risk/snapshot/commands, large | concise mode partly suppresses lists | early / progressively | MOVE TO LOG |
| O17 | Generated PR Confirmed Paths | `New-PullRequestBody` | complete raw path list, potentially large | visible | before PR / PR creation | COLLAPSE |
| O18 | Validation terminal | `validate-local.ps1`, `mosaic_output.ps1` | plan, one line/stage, concise result/log, bounded failure | visible | progressively / progressively | KEEP VISIBLE |
| O19 | Release bodies | `release_body` | purpose/version/build/source/hash/install/provenance, ~15 lines | visible | publication / publication | REDESIGN SUMMARY |
| O20 | Raw evidence JSON | upstream PR/details and retained artifacts | complete authentication/provenance, unbounded | PR details plus artifact | observation / PR and artifact | REMOVE DUPLICATE from PR when artifact + minimum embedded resolver evidence suffice; keep artifact authoritative |

Output recommendations: **KEEP VISIBLE 5; COLLAPSE 4; MOVE TO LOG 1; MOVE TO ARTIFACT 1;
REDESIGN SUMMARY 8; REMOVE DUPLICATE 1**. “Move to artifact” for the resolver means retain its
existing ignored prompt file; it does not mean upload to GitHub.

## Progressive disclosure rules

Use `<details><summary>Technical details</summary>` for Confirmed Paths, raw paths/commits,
tree/SHA/run/attempt provenance, artifact inventories and IDs, authentication checks, APK hashes,
classifier tokens, and verbose diagnostics. Keep visible the main decision/result, affected
version/build/PR, failure or refusal reason, operator action, approval wait, and next stage.

Target:

```markdown
Full validation reused — this exact tree passed on PR #55.

<details><summary>Technical details</summary>
tested tree, run, attempt, artifact identity
</details>
```

## Information timing registry

| Workflow/process | Information | Available when | Shown now | Avoidable delay | Recommended mechanism |
|---|---|---|---|---|---|
| PR CI | change class, validation path, release relevance | classification, usually seconds | summary after validation job | targeted/full duration: ~3–11 min | append an early plan summary, then final result update/second section |
| Main CI | exact-tree reuse decision | before Gradle | summary step after Full executes/skips | up to observed ~5m25s | early summary line immediately after reuse inspection |
| Main CI Development | whether unpublished range needs an APK | only after validation in separate Build job | Build-job eligibility summary | validation duration, ~5m when not reused | keep release authority after validation, but expose anticipated PR release relevance earlier and clearly say final range check follows |
| Signing | approval requirement | graph creation | Environment waiting UI; no custom summary beforehand | immediate UI is native | improve job/display wording; do not add a Plan job |
| Stable Promotion | candidate version | after authenticated prepare | immediately after prepare | authentication time only, necessary | keep; run name cannot safely claim version before authentication |
| Hold Release | current Stable | after authenticated prepare | immediately after prepare | authentication time only, necessary | keep |
| Upstream Sync | no-delta/counts/candidate need | observe completes, seconds | same job summary | negligible | simplify first line; no Plan job |
| Signing Diagnostic | current source and no-publication intent | dispatch | SHA-heavy run name; result after 9–11 min build/sign | intent unclear for entire run | zero-input run title + immediate purpose summary |
| prepare-pr | complete scope/classification/validation path | audit/classification | terminal progressively; PR body only at end | no major delay | keep early terminal plan; collapse/log detail |
| local validation | selected work and reason | classifier, seconds | immediately | none | translate jargon while retaining raw values in log |

Do not add lightweight Plan jobs by default. Early summary writes inside existing cheap
classification/prepare steps are enough for CI, upstream, and local flows. Stable/Hold must not
announce an unauthenticated version early.

## Conditional path and explanation registry

| Surface | Current condition | Current explanation | Desired immediate explanation |
|---|---|---|---|
| PR validation | non-Android / targeted / Full | classifier tokens shown at end | `Application validation not required`, `Focused Android checks selected`, or `Full validation required — <plain reason>` |
| Main Full | exact-tree evidence reuse or fallback | accurate but late/technical | `Full validation reused — exact tree passed on PR #N` or `Full validation required — evidence missing/different/ambiguous` |
| Development Build | complete unpublished range release relevance | `Skipped · tooling-only` plus internals | `No build required — these changes do not affect the application` |
| Development Sign/Publish | Build required and dependencies succeeded | native skipped/waiting only | Build summary says what follows; job title/UI says `Waiting for approval — signing authorization required` |
| Stable Promotion | authenticated candidate then Environment | native wait + prepare line | `Ready to release: vX`; `Waiting for approval — Stable publication authorization required` |
| Hold Release | authenticated Stable then Environment/recheck | good prepare/result; refusals technical | `Ready to hold: vX`; `Release refused — prepared Stable changed; nothing modified` |
| Upstream publish | no delta vs ready/review/conflict | detailed outcome with policy vocabulary | `No upstream changes`; `Candidate ready`; or `Review required — N files need semantic decisions` |
| Signing Diagnostic | exact SHA guard/build/sign | skipped job may not explain invalid input | after zero-input migration: `Diagnostic will build and sign current protected main; nothing will be published` |
| prepare-pr | scope drift/autofix/validation/PR reuse | safe but verbose | retain refusal reason and “nothing staged/pushed”; state next action and auto-merge state |

Failures and refusals must never be collapsed away. A skipped job caused by a failed prerequisite
should point to that prerequisite rather than appear as a successful policy skip.

## Prepare-pr and validation process

Current possible Full chain is:

```text
operator manually runs Full
→ guided prepare-pr classifies and may run Full again
→ PR CI runs Full for high-risk/release-sensitive paths
→ protected main reuses exact PR Full only when all predicates match; otherwise Full runs again
```

Prepare-pr does not know or authenticate a prior local `validation.log` as reusable evidence, so an
operator-run Full immediately before guided publication is duplicate work. PR Full evidence reuse
already works on main when the required run/artifact/tested tree and final main tree match. The
minimum target is:

- focused/Standard local checks during implementation;
- prepare-pr performs only the still-required local gate and never repeats an authenticated
  unchanged local snapshot without a deliberately designed evidence format;
- required PR CI is the authoritative Full for high-risk and every `releaseRequired=true` APK path;
- protected main reuses PR Full on exact-tree equality and otherwise fails safely to Full.

Removing local Full from upstream resolution, changing which PRs run Full, or renaming the check is
a HIGH-risk process migration. It requires classifier tests, prepare-pr acceptance fixtures,
PR/main evidence-reuse tests, ruleset coordination where names change, and live exact-tree/fallback
acceptance. Do not solve duplication by trusting `validation.log` or weakening main fallback.

Auto-merge is technically feasible through authenticated `gh` after PR creation/reuse. The design
must authenticate the same head, require non-Draft status, use normal merge (especially for native
upstream ancestry), preserve required CI, refuse ambiguity, and report whether auto-merge was armed.
It also requires the repository’s native auto-merge setting to be enabled manually. Human approval
of publication would arm merge; protected rules/checks would still decide when merge occurs.

## Performance inventory

Known evidence: local Full is approximately **9–11 minutes**; one observed offline tooling phase
was **6m38s**; Android Full was **3m16s**; protected-main Release Build is commonly **10+ minutes**
(I02 measured 9m12s). The hosted upstream fixture suite alone creates many real disposable Git
topologies and was recently ~4m35s, making it the first offline profiling suspect. Other Python
suites, process startup, serial execution and repeated fixture setup need measurement rather than
assumption. Release assembly runs in a fresh job/workspace after Debug validation, so cross-job
Gradle/cache misses are the first Build hypothesis.

T0-1 CP7 must capture per-suite/per-test and Gradle task/cache/configuration timings before changing
parallelism, fixtures, task graphs or caches. Preserve deterministic isolation and provenance;
performance is not permission to merge security fixtures or build/sign jobs.

## Signing Diagnostic conclusion

**KEEP AND SIMPLIFY.** Its distinct operator purpose is to verify key custody, Environment secret
wiring, signer certificate, package/version and payload integrity without publishing a Release. It
is useful after signing-key restoration/rotation or Environment migration. The manual
`expected_sha` adds no independent authorization: workflow dispatch is already on protected main,
the guard requires the input equal `github.sha`, and `release-sign` controls access to credentials.
Resolve/authenticate current protected main automatically, show the version/source and
“diagnostic only; nothing published” before expensive work, and retain the Environment boundary.
Whether it can consume an already authenticated unsigned artifact instead of rebuilding belongs to
the performance/process checkpoint and must preserve diagnostic independence.

## T0-1 implementation ledger

Every row states the problem/current source, target UX, coupling/risk, required validation,
external action, and owner checkpoint.

### REMOVE (3)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| R01 — COMPLETE / HOSTED VALIDATED | `.github/workflows/main.yml`; dead guarded `Development build` polluted sidebar | Deleted; ownership policy, fixtures and current docs preserve its intended absence. Current CI is sole owner | Focused policy/workflow tests; hosted sidebar confirmed absent; no settings | CP2 |
| R02 — COMPLETE / HOSTED VALIDATED | `.github/workflows/release.yml`; dead guarded `Create release`, but unique AAB knowledge | Baseline T0 declines store/AAB ownership; deleted and marked downstream-owned absence; capability retained in historical audit | Focused ownership/release tests; hosted sidebar confirmed absent | CP2 |
| R03 — COMPLETE FOR REMOVED SURFACES | Active docs presented deleted/obsolete workflow surfaces | Current docs point to CI/Stable/Hold; historical evidence is explicitly labeled historical | Link/pre-commit checks; no GitHub change | CP2; final consistency sweep CP8 |

### LOW-RISK PRESENTATION (4)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| L01 — CP3 COMPLETE / HOSTED VALIDATED | Stable workflow display repeats repository prefix | `Stable Promotion`; tests/docs only, file unchanged | Focused presentation tests pass; supported hosted display observed | CP3 |
| L02 — CP3 COMPLETE / HOSTED VALIDATED | Upstream workflow display repeats prefix and run exposes raw `workflow_dispatch` | `Upstream Synchronization`; `Upstream check · Manual/Scheduled` | Focused presentation tests pass; supported hosted display observed | CP3 |
| L03 — CP3 COMPLETE / HOSTED VALIDATED | Signing Diagnostic repeats prefix | `Signing Diagnostic`; artifacts/file unchanged | Focused signing/presentation tests pass; supported hosted display observed | CP3 |
| L04 — CP3 COMPLETE / HOSTED VALIDATED | Setup/composite and safe step labels are inconsistent (`Setup`, `Get Release`) | Natural verb/object labels without changing action paths or job IDs | YAML/static tests pass; hosted presentation accepted at checkpoint level | CP3 |

### SUMMARY / COLLAPSE (7)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| S01 — CP3 COMPLETE / HOSTED VALIDATED | `ci.yml` PR policy summary is late and classifier-centric | Early plain-language plan + final result; raw policy in details | Static/output tests pass; hosted PR rendering observed | CP3 |
| S02 — CP3 COMPLETE / HOSTED VALIDATED | PR APK/main reuse summaries expose all identities equally | Main result/link visible; SHA/tree/run/artifact in `<details>` | Exact evidence assertions pass; hosted exact-tree reuse observed, with fallback retained by fixtures | CP3 |
| S03 — CP3 COMPLETE / HOSTED VALIDATED | `mosaic_development_release.record_eligibility` emits full paths/default | One build/no-build sentence; paths and policy evidence collapsed | Release classifier/output tests pass; hosted non-APK presentation observed and APK paths remain live-proven elsewhere | CP3 |
| S04 — CP3 COMPLETE / HOSTED VALIDATED | `hosted_upstream.upstream_summary` exposes policy tables and full JSON | Counts/attention/action first; lists/navigation/details collapsed; artifact remains complete | Hostile-input/quiet-surface fixtures and natural hosted Upstream runs observed | CP3 |
| S05 | prepare-pr console and PR body repeat raw scope | `PR scope: N files`; paths/stats in logs and collapsed PR details | Disposable prepare-pr tests; no snapshot weakening | CP6 |
| S06 — CP3 PRESENTATION IMPLEMENTED / CP8 SWEEP REMAINS | failure summaries name `$GITHUB_JOB` but not always action/remedy | Plain refusal/failure, mutation status, retry/forward-fix action visible | Current Stable/Hold/Signing/CI failures now state action; final cross-surface sweep remains CP8 | CP3/CP8 |
| S07 — RECLASSIFIED AS OPERATIONAL | Upstream publication hides the actionable `git push --porcelain` rejection because stdout/stderr are captured but discarded | Bounded sanitized failure detail: operation, remote/refspec, exit, destination existence, rejection category/excerpt from both streams; never credentials | Requires subprocess/error-contract tests, not cosmetic summary editing | CP6 or T0-2 security |

### PROCESS SIMPLIFICATION (5)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| P01 — CP4A MEASURED / CP4B PENDING | Operator Full can precede prepare-pr Full | Document/use one publication entry point; design authenticated snapshot evidence only if reuse is needed | Disposable drift/autofix/snapshot tests | CP4 |
| P02 — CP4A MEASURED / CP4B PENDING | prepare-pr + PR Full duplicate local/hosted assurance | For ordinary high-risk/APK work, local focused/Standard then authoritative PR Full; retain fail-closed main fallback | Classifier/prepare-pr/CI fixtures and live PR/main reuse | CP4 |
| P03 — CP4A MEASURED / CP4B PENDING | Upstream candidates require local Standard then Full plus PR Full | Remove duplicate local Full only after required upstream PR Full and exact merge evidence fully cover it | Native merge/filter/evidence tests; live natural candidate | CP4 |
| P04 — CP4A AUDITED / CP4B PENDING | prepare-pr stops after PR creation and manual auto-merge click | Authenticate same non-Draft head and arm native auto-merge; never arm upstream Draft | Mocked `gh`, branch/rules tests; enable GitHub auto-merge manually; hosted acceptance | CP4 |
| P05 — CP4A AUDITED / CP4B PENDING | Signing Diagnostic requires repeated SHA | Zero-input authenticated protected-main source; keep Environment approval and no publication | Signing auth/refusal tests + manual hosted diagnostic | CP4 |

### HIGH-RISK CONTRACT MIGRATION (6)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| H01 | `Full validation` is desired as `Prepare` but is ruleset/reuse/provenance input | Coordinated rename across workflow, ruleset, API consumers, tests/docs with no interval lacking required protection | Old/new check migration tests and hosted PR/main acceptance; manual ruleset update | CP5 |
| H02 | Development job names are verbose and test-bound | `Build`, `Sign`, `Publish` only after proving names are not external API/provenance selectors; job IDs unchanged | Release/security tests + hosted APK delivery/rerun | CP5 |
| H03 | Artifact names are ugly but authenticated | Do not rename by default; improve surrounding display. Any change needs dual-reader/migration proof | Complete artifact/provenance/Stable/Hold tests | CP5 or reject |
| H04 | Release names/tags/installer filenames mix human/machine identity | Keep updater contracts; isolate human title changes from machine identity | Updater/version/manifest/device update acceptance | CP5/CP6 |
| H05 | Ownership path state for removed inherited workflows | Explicit downstream-owned absence so upstream sync cannot resurrect publishers | I06 classification/native candidate fixtures + natural observation | CP2/CP5 |
| H06 | I06 can construct a genuine `.github/workflows/**` candidate while its least-privilege App token lacks Workflows write | Decide explicitly between manual handling (preferred absent a concrete need) and isolated repo-scoped Workflows write; never silently broaden authority | Permission/threat-model audit plus authorized hosted acceptance only if automation is chosen | CP5 or T0-2 security |

### PERFORMANCE (6)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| F01 | Offline tooling ~6m38s; hosted Git fixtures likely dominate | Per-suite/test profile; share only immutable setup proven isolation-safe; assess process parallelism | Repeated timing + full offline equivalence | CP7 |
| F02 | Android Full ~3m16s and repeated across paths | Measure Gradle task/cache overlap before changing authoritative validation model | Task graph/cache evidence + complete validation | CP7 after CP4 |
| F03 | Release Build ~10m+, fresh job after Debug | Profile configuration/task/cache/download time; assess safe cache/artifact reuse without crossing build/sign authority | Hosted timing and provenance/security regression | CP7 |
| F04 | Hosted duration reports can conflate runner queue, setup, execution and Environment/publication wait | Record those phases separately; never present runner wait as validation/build time | PR/main/rerun samples with job timestamps | CP7 |
| F05 | One run spent ~18.6s on 232 offline tests but restored ~934 MB of Gradle/wrapper cache and performed heavy Android/JDK setup | Measure cache value, preinstalled-tool rejection, duplicate downloads and license noise before changing setup | Repeated clean/warm hosted runs; preserve pinned/reproducible toolchain | CP7 |
| F06 | No consolidated real-run corpus yet covers every surviving workflow and alternate path | Analyze PR/main CI, Development Build/Sign/Publish, Stable, Hold, Signing Diagnostic, Upstream Sync, reruns, reuse, queues, transient dependency failures, refusals and skips before optimization | Evidence table from existing hosted logs; no manufactured mutations | CP7; feed CP3/CP6 and T0-2/T0-3 |

### RELEASE PRESENTATION (4)

| ID | Source / current problem | Target and dependency/risk | Validation / external action | CP |
|---|---|---|---|---|
| V01 | Development primary title lacks distance from Stable | After compatibility audit, `vStable-distance-gSHA — Development`; machine `downstream-build-N` unchanged | VersionCode/updater/provenance tests + device acceptance | CP6 |
| V02 | Stable/Development bodies lack prominent comparison | Compare Stable→Development and prior Stable→new Stable | Source-baseline/ref authentication tests + live releases | CP6 |
| V03 | Immutable archive looks like another human release | Mark archive/provenance role; rolling channel is primary | Release body/updater tests | CP6 |
| V04 | Artifact identities dominate summaries | Human version/channel first; exact artifact in details | Artifact authentication unchanged; summary tests | CP6 |

### DEFER TO T0-2 (5)

| ID | Finding | Why deferred | T0-2 evidence needed |
|---|---|---|---|
| D01 | Whether every current provenance field has a distinct consumer | Correctness/security architecture, not presentation | Producer/consumer and threat-model audit |
| D02 | Whether ruleset, Environment and permission configuration exactly matches documented assumptions | External assurance beyond repository UX | Read-only settings inventory and drift checks |
| D03 | Previously unknown races/idempotency/security gaps discovered during cleanup | Must not expand T0-1 speculatively | Whole-system adversarial audit and categorization |
| D04 | Application architecture/test adequacy for resumed features | Product engineering boundary, not operator cleanup | T0-2 architecture and coverage audit |
| D05 | A clean hosted runner transiently failed to resolve two existing Android debug variants while local cache and an unchanged rerun succeeded | Reproducibility/dependency-resolution assurance, not CP2 presentation work; do not alter dependencies after one transient failure | Compare clean/warm resolution, repository availability and dependency metadata only if recurrence supplies evidence |

Ledger totals: **REMOVE 3; LOW-RISK PRESENTATION 4; SUMMARY/COLLAPSE 7; PROCESS
SIMPLIFICATION 5; HIGH-RISK CONTRACT MIGRATION 6; PERFORMANCE 6; RELEASE PRESENTATION 4;
DEFER TO T0-2 5** — **40 finite items**.

## Recommended T0-1 checkpoint sequence

1. **CP1 — Inventory (complete):** this document; no behavior change.
2. **CP2 — Remove obsolete surfaces (complete / hosted validated):** deleted `main.yml` and
   `release.yml`, explicitly deferred AAB/store distribution, updated ownership/tests/docs
   atomically, and confirmed the resulting five-workflow Actions sidebar and non-APK main path.
3. **CP3 — Low-risk names and summaries (complete / hosted validated):** removed redundant
   prefixes, translated primary decisions, added early summary lines, and collapsed technical
   detail without touching `CI / Full validation`. Repository Full passed, the change merged, and
   the supported hosted rendering/sidebar presentation was observed.
4. **CP4 — Process simplification:** eliminate redundant validation paths, simplify Signing
   Diagnostic input, and arm native auto-merge. This depends on CP3’s clear explanations and
   requires GitHub auto-merge enablement plus hosted exact-tree/fallback acceptance.
5. **CP5 — High-risk contracts:** only if still valuable, coordinate `Full validation → Prepare`
   and Development job presentation with ruleset and every authenticated consumer. Manual GitHub
   ruleset change and hosted old/new transition acceptance are mandatory.
6. **CP6 — PR and Release presentation:** restructure PR default view, add Compare Changes, and
   implement human Development versioning only after updater/version/provenance proof. Device and
   live release acceptance required for version/title changes.
7. **CP7 — Performance:** profile first, then optimize offline fixtures, Android validation and
   Release Build independently. Depends on CP4 so obsolete duplicate work is not optimized.
8. **CP8 — Final consistency sweep:** compare every surviving Actions/PR/Release/CLI surface to this
   registry, reconcile docs, and prove no machine contract was cosmetically renamed.

Cosmetic CP3 is deliberately separated from CP5 machine-contract migration. CP2 should not wait for
cosmetic work because dead workflows distort the inventory operators see. CP7 follows CP4 because
removing duplication is higher leverage than accelerating duplicate work.

## Future “Learn more” integration

| Surface | Future handbook topic |
|---|---|
| CI PR/main summaries | Validation and Evidence Reuse |
| PR test APK | Pull Requests and Test APKs |
| Build/Sign/Publish | Development Delivery |
| Signing wait/diagnostic | Signing and Environment Authorization |
| Stable Prepare/Release | Stable Release |
| Hold Prepare/Hold | Hold Release and Forward Recovery |
| Upstream summary/PR | Upstream Synchronization; FOLLOW, REVIEW, and Conflicts |
| Release bodies/settings | Development vs Stable Channels; Versioning and Provenance |
| Refusal/rerun guidance | Failed-job Recovery |

Do not hardcode wiki URLs during T0-1. Use centrally managed stable destinations during T0-3.
Every surface must remain understandable without following a link.

## CP2 completion statement

The two obsolete surfaces and their executable definitions are removed without transferring their
authority or changing a surviving workflow. Current CI remains the sole Development delivery owner;
Stable Promotion and Hold Release retain their exact responsibilities. Appstore/Fire TV AAB
distribution is explicitly deferred. The ownership policy and regression fixtures make both
deletions intentional downstream state.

Hosted acceptance confirmed the five-workflow sidebar, exact-tree reuse, and a tooling-only main
path with no APK Build/Sign/Publish. At CP2 completion, CP3 was authorized on a separate branch
with the coordinated `CI / Full validation` machine-contract migration reserved for later.

## CP3 completion statement

The low-risk presentation checkpoint is **COMPLETE / HOSTED VALIDATED**. The supported workflows now lead
with intent, result, operator action, and next step, while technical classifier/provenance/path
evidence remains available under collapsible details or in the existing machine artifact. The
required check and delivery identities `CI`, `Full validation`, `Build Development Release`,
`Sign Development`, and `Publish Development` are unchanged, as are all execution and security
semantics. Repository Full passed, the CP3 branch merged, and hosted rendering/sidebar presentation
was observed. Alternate rare-path presentation remains fixture-protected and may be recorded
opportunistically without reopening CP3.
