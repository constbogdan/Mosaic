# Upstream resolution decisions

This compact log preserves useful context from completed semantic reviews. Previous decisions are
context, not authority: every future `REVIEW` path must be evaluated against its new upstream
change, current Mosaic behavior, and the authenticated candidate evidence.

## Stage B — upstream `55960c24` (local native merge, uncommitted)

See the [bounded Stage B review](history/UPSTREAM_STAGE_B.md) for all 37 commits,
actual conflicts, clean semantic overlaps, validation and acceptance limitations.
The parent pair is protected main `8c800002` plus exact upstream `55960c24`, not PR #114's
later target. Retain retired workflow absences and Mosaic's updater/signing contract.
Adapt legacy session/HDR migration to Mosaic's independent versions; preserve server identity
through user-switch deduplication, cancelled Seerr authentication and metadata refresh.
Do not substitute Stage C track persistence or the Stage D candidate tree.

## PR #111 — upstream `65954a99` (local resolution, publication pending)

- Original episode: `08119bd3875ca9d34e4699978add5864cad7a7b0e0e0c712ffa8ed194aa7aa1a`.
- Original upstream target: `65954a99eb0aa318d9f88813d49768bd95cf37ee`, one commit after
  `365f8f8adacf4dcaf7d05317917830ed3a439668`. Later observed upstream commits are excluded.
- Fresh hosted run `42182021907` authenticated the older Draft against protected main
  `1b67471658d585bc97d6cfd98eca99082d5d1367`. The supported resolver created the separately
  approved local reconciliation `a5a73c8f3e35d0a685d3e771a5da2e1edf1e9a00` with parents
  `[7d540cc94e7ee9be876211f133b3c9436208db4c, 1b67471658d585bc97d6cfd98eca99082d5d1367]`.
  The original upstream merge remains uncommitted for review.
- Preserve upstream's timestamp-independent `ServerUserConfig` and its compatible repository,
  Home settings, navigation-service and test adaptations. Resolve Home's remaining type/collector
  conflict rather than reverting the new stable configuration model.
- Same-user/server refresh must retain loaded rows and Mosaic Acquiring items while marking refresh
  as loading; do not restore the unconditional reset from the older Mosaic collector. Invalid
  user/settings or identity changes reset the ordinary Home rows. Track server identity as well as
  user identity so the same user UUID on another server cannot refresh old-server rows in place.
- Every reset retains the Acquiring provider's current projection, as before. Session invalidation
  and clearing remain the acquisition tracker's authority; Home continues to consume its empty or
  replacement projection. Do not infer playability or fabricate acquisition ownership in Home.
- Home navigation, Acquiring keys, focus restoration and destination selection are unchanged.
  The upstream commented diagnostic was not retained.
- Focused coverage extends `HomeViewModelTest` for stable metadata equality, same-session refresh,
  row-setting changes, invalid context, server changes, cancelled old-user completion and manual
  refresh; retain Home acquisition/fixture and acquisition-session isolation regressions.

## PR #106 — upstream `365f8f8`

- Episode: `0d04a7ca2773ce58dd1b7f33d0d8c86be29f4f17b19d85c5e51ea5a163844916`
- Upstream: `365f8f8adacf4dcaf7d05317917830ed3a439668`
- Original downstream baseline: `4a6db6df866c4182576a3a9f96e3de697d5596e7`
- `.github/workflows/main.yml` and `.github/workflows/release.yml` remain absent as
  `DOWNSTREAM-OWNED` publication authorities.
- `.github/workflows/pr.yml` remains absent by deliberate semantic `REVIEW`; it remains a
  `REVIEW` path. Mosaic's `.github/workflows/ci.yml` is the canonical PR-validation authority, but
  future upstream `pr.yml` changes remain worth reviewing for underlying CI improvements.
- This upstream `pr.yml` changed Gradle heap from 8 GiB to 12 GiB and metaspace from 512 MiB to
  1024 MiB. Mosaic currently uses `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` without an
  explicit metaspace cap; no resource-setting change was adopted implicitly.
- `.github/actions/setup/action.yml` retained Mosaic's explicit Android platform, Build Tools, and
  NDK provisioning while accepting compatible upstream pinned-action digest updates.
- `IntentService` retained upstream home/missing-item navigation and playback validation together
  with Mosaic's dynamic application-ID playback action and legacy compatibility.
- `HomeViewModel` retained upstream reactive user/settings loading and Mosaic acquisition-state
  collection. Acquisition items must survive the user/settings reset and reload transition.
- Focused regression coverage: `HomeViewModelTest`, `IntentServiceTest`,
  `HomeAcquiringSourceTest`, and `HomeAcquiringFixturesTest`.
