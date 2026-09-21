# Mosaic roadmap

This is the canonical product and engineering roadmap. Detailed completed-program evidence is in
the [history index](history/README.md); current architecture and operation have dedicated owners in
the [documentation index](README.md).

## Current engineering phase

- T0-1 validation, operator UX, and pipeline simplification — complete.
- T0-2 bounded authority/bootstrap/diagnostic corrections — complete.
- T0-3.0 Mosaic identity establishment — complete.
- T0-3 Documentation, Wiki & Roadmap consolidation — complete through D7.

Baseline T0 remains open until its independent final acceptance is performed and authorized.
Completing T0-3 does not itself declare Baseline T0 complete.

## Current priorities

1. Perform the separately authorized Baseline T0 final acceptance and closure.
2. Resume evidence-driven product work from the feature roadmap below after that decision.

## Product direction

Mosaic should feel like one integrated media application rather than a Jellyfin client with isolated
add-ons. Jellyfin remains authoritative for local playback. Optional Seerr-backed capabilities add
discovery, requests, acquisition awareness, watchlists, and unavailable-content context without
breaking the base Jellyfin experience.

Shared media identity and independently sourced state should be reusable across Library, Series,
Downloads, Discover, Watchlist, Collections, and Suggestions. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the stable model.

## Feature roadmap

### Acquisition and library integrity

- Continue real-world hardening of acquisition tracking and Jellyfin readiness.
- Improve progress, completion feedback, and low-storage guidance.
- Preserve contextual season-integrity evaluation from trusted expectations plus fresh Jellyfin and
  acquisition evidence; do not introduce continuous whole-library auditing.
- Consider separate Movies/Series Acquiring rows only after evaluating the existing Home surface.

### Series completeness

- Add Continuing/Ended production status.
- Improve More Seasons selection, availability filtering, and focus behavior.
- Keep available and unavailable seasons in one coherent series experience.

### Watchlist

- Add a dedicated destination, filtering, sorting, and grouping.
- Reuse watchlist membership on cards and across Discover, Collections, and Suggestions.

### Collections and franchises

- Combine locally available and unavailable collection items.
- Reuse TMDB collection identity where appropriate.
- Explore broader franchise relationships and release/chronological viewing orders.

### Discover

- Evolve the landing experience across movies, series, animation/anime, and franchises.
- Add useful genre, decade, studio, network, country, and language browsing.
- Surface local availability and request/acquisition state without duplicating state logic.
- Evaluate consumption of the optional Seerr Fresh source through the shared product model.

### Suggestions and automation

- Build availability-, watchlist-, collection-, and franchise-aware suggestions.
- Design opt-in next-season acquisition with duplicate-request and specials safeguards.
- Investigate quality-upgrade actions when higher profiles are genuinely available.

### Playback

- Expand external-player resume, completion, progress, and chapter-thumbnail validation.
- Preserve exact Jellyfin readiness and destination identity.

### External integrations

- Investigate optional Trakt, IMDb, and TMDB list integrations.
- Define import versus live synchronization and keep core Jellyfin use independent.

## Engineering direction

- Centralize reusable media state incrementally rather than replacing all upstream models.
- Keep integrations optional and preserve graceful degradation.
- Prefer native GitHub/Git/Gradle capabilities over custom lifecycle state.
- Revisit performance, dependencies, security tooling, or cross-repository standardization only from
  concrete evidence and separately approved scope.
- Maintain one protected downstream integration branch and PR-only integration; do not introduce a
  permanent staging branch without a demonstrated need.

## Major completed foundations

- Protected PR-only integration with authoritative hosted validation and exact-tree main reuse.
- Automatic change-aware Development Build → isolated Sign → Publish.
- Zero-input exact-byte Stable Promotion and emergency Hold/forward-fix policy.
- Ownership-aware hosted upstream synchronization with human-controlled REVIEW/conflict resolution.
- Permanent Mosaic repository, package, signer, updater, asset, URI, and presentation identity.
- Android TV Home acquisition presentation, season-scoped state, and contextual integrity foundation.

Acceptance chronology, measurements, incidents, SHAs, PRs, and run IDs remain available through
[historical engineering records](history/README.md), not this roadmap.
