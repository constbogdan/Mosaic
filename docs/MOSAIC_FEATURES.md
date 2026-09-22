# Mosaic product features

[Wholphin](https://github.com/damontecres/Wholphin) remains the baseline Android TV client. This
catalog describes application features Mosaic adds or plans to add; it does not inventory inherited
Wholphin behavior. Checked boxes mean implemented in the current Mosaic code. Unchecked boxes are
explicitly **Partial** or **Planned**. Upstream contribution status is separate from Mosaic
implementation status: a submitted candidate is not an accepted upstream feature.

The [product roadmap](MOSAIC_ROADMAP.md) owns longer-term direction; the
[architecture](ARCHITECTURE.md) explains source-of-truth and state boundaries. Optional Seerr
features depend on a configured integration; Jellyfin remains authoritative for local playback.

## Seasons and requests

- [x] Show known local and Seerr seasons together, including unavailable season placeholders and
  direct requests from a placeholder. **Upstream:** PR #1919 candidate; the refreshed trial is not
  part of current Mosaic main.
- [x] Show series/season availability, including a partially available series indicator and
  deterministic numeric season ordering. **Upstream:** PR #1919 candidate for the season work.
- [x] Offer a request-more-seasons dialog, with the existing server, 4K, profile, and root-folder
  choices. **Upstream:** PR #1919 candidate for its scoped dialog improvements.
- [ ] **Partial** Make every request action/badge and more-seasons choice unambiguous: the current
  flow works, while actionable filtering, partially available seasons, duplicate-selection
  avoidance, and TV focus refinements remain under review. **Upstream:** PR #1919 candidate.
- [ ] **Planned** Show Continuing/Ended production status on series. **Depends on:** series
  metadata. **Upstream:** Not assessed.

## Acquisition and readiness

- [x] Track Seerr request lifecycle through a shared acquisition tracker and ledger, including
  immediate queueing, active progress, recent completion, and problem states. **Upstream:** Candidate
  for a separate foundation contribution, not part of PR #1919.
- [x] Project movie and season-scoped series acquisition state without treating Seerr availability
  as Jellyfin playability. **Depends on:** acquisition tracker and Jellyfin identity. **Upstream:**
  Candidate for a separate foundation contribution.
- [x] Determine Jellyfin readiness from fresh usable movie/episode data and retain exact season
  destinations when available. **Depends on:** acquisition projection. **Upstream:** Candidate for
  separate foundation/UI review.
- [x] Persist trusted released-episode expectations independently of short-lived Downloads history;
  evaluate missing playable episodes contextually when a series is opened or refreshed. **Depends
  on:** Jellyfin readiness and Seerr episode expectations. **Upstream:** Not assessed.
- [ ] **Partial** Harden lifecycle/progress and recently completed/problem presentation against
  real-world edge cases. Core states and progress are implemented; further device use is planned.
  **Upstream:** Not assessed.
- [ ] **Planned** Add acquisition-completion notifications and investigate low-storage warnings.
  **Depends on:** reliable lifecycle events. **Upstream:** Not assessed.

## Downloads and Home

- [x] Provide a dedicated Downloads destination with active, processing, and recently completed
  sections, status/progress, and navigation into the relevant movie, series, or exact available
  season. **Depends on:** acquisition and readiness. **Upstream:** Foundation + UI candidate.
- [x] Show a transient Home **Acquiring** row for active movie and season acquisitions, with a
  route to Downloads; hide it when empty or enhanced tracking is disabled. **Depends on:** the shared
  acquisition projection. **Upstream:** Foundation + UI candidate.
- [x] Preserve acquisition items across Home user/settings reloads through separate Home acquisition
  state rather than discarding them with ordinary rows. **Upstream:** Not assessed.
- [ ] **Planned** Consider separate Movies/Series Acquiring rows only if the Home row proves
  insufficient. **Depends on:** device feedback. **Upstream:** Not assessed.

## Shared presentation and optional behavior

- [x] Reuse acquisition/availability presentation on relevant cards and series surfaces, including
  season-scoped progress and coarse series summaries. **Depends on:** acquisition index and media
  identity. **Upstream:** Foundation + UI candidate.
- [ ] **Partial** Compose library, request, acquisition, integrity, watchlist, collection, and
  quality state consistently across all media surfaces. Shared acquisition/integrity foundations
  exist; universal cross-screen state does not. **Upstream:** Not assessed.
- [x] Gate Mosaic's enhanced features separately from ordinary Jellyfin browsing/playback and fall
  back to base series details when enhancements or Seerr are unavailable. **Upstream:** Mosaic-only
  product policy; individual bug fixes can still be contributed.

## Watchlist

- [ ] **Planned** Add a dedicated Watchlist destination for movies and series. **Depends on:**
  optional Seerr integration and shared media identity. **Upstream:** Not assessed.
- [ ] **Planned** Add filtering, date/year sorting, genre and collection/franchise grouping, and
  persisted view preferences where feasible. **Depends on:** Watchlist destination. **Upstream:**
  Not assessed.
- [ ] **Planned** Show watchlist membership on library/Discover/collection cards and suggestions.
  **Depends on:** shared media state. **Upstream:** Not assessed.

## Collections and franchises

- [ ] **Planned** Add a dedicated movie Collections destination combining locally available and
  unavailable items; optionally include unavailable collections connected to watchlisted titles.
  **Depends on:** Jellyfin/Seerr identity and Watchlist. **Upstream:** Not assessed.
- [ ] **Planned** Connect formal collections and broader franchise/universe relationships, such as
  related film series and cross-franchise stories. **Depends on:** stable collection/media identity.
  **Upstream:** Not assessed.
- [ ] **Planned** Offer release-order and chronological browsing for collections/franchises.
  **Depends on:** relationship and ordering metadata. **Upstream:** Not assessed.

## Discover and suggestions

- [ ] **Partial** Evolve existing Seerr Discover into a unified Mosaic landing experience with
  Movies, Series, Animation/Anime, and Franchises sections. Existing discovery is usable, but the
  planned organization is not complete. **Upstream:** Not assessed.
- [ ] **Planned** Add decade/year and genre browsing, and evaluate studio, network, country, and
  language groupings. **Depends on:** Discover organization and metadata. **Upstream:** Not assessed.
- [ ] **Planned** Show local availability and request/acquisition state consistently on Discover
  cards. **Depends on:** shared media state. **Upstream:** Not assessed.
- [ ] **Planned** Build availability-, watchlist-, and collection/franchise-aware suggestions,
  including related films and cross-franchise browsing. **Depends on:** shared media state and
  collection relationships. **Upstream:** Not assessed.
- [ ] **Planned** Evaluate optional external discovery/list sources, including Seerr Fresh and
  Trakt/IMDb/TMDB lists, without making them necessary for Jellyfin use. **Upstream:** Not assessed.

## Future actions and playback

- [ ] **Planned** Offer opt-in next-season acquisition with duplicate-request, specials, and
  continuing/ended safeguards. **Depends on:** season availability and reliable acquisition state.
  **Upstream:** Not assessed.
- [ ] **Planned** Offer higher-quality upgrade requests only when Seerr/Radarr/Sonarr exposes a
  genuinely higher profile. **Depends on:** profile and availability evidence. **Upstream:** Not
  assessed.
- [ ] **Planned** Extend validation of external-player resume, completion, progress, and chapter
  thumbnails while retaining Jellyfin playback authority. **Upstream:** Not assessed.

## Mosaic-specific product identity

- [x] Use the Mosaic app identity, `mosaic:` URI scheme, canonical updater repository, and Mosaic
  Development/Stable assets and channels. These are downstream product identities, not upstream
  Wholphin contribution candidates. See [Mosaic identity](MOSAIC_IDENTITY.md).

## Upstream contribution map

- **Already contributed, not claimed accepted:** PR #1919 covers missing/requestable seasons and
  associated series/request presentation. Its refreshed trial branch is separate from current
  Mosaic main; this catalog does not mark its refinements implemented until integrated.
- **Likely candidates for separate review:** acquisition tracking/projection, Jellyfin readiness,
  Downloads, and Home Acquiring. Split reusable state/identity foundations from UI surfaces so each
  can be assessed independently. No upstream acceptance is asserted.
- **Mosaic-only:** app/updater/channel identity and the policy for optional enhanced behavior stay
  downstream. Watchlist, collections, Discover expansion, suggestions, automation, and upgrades
  remain product plans; their upstream suitability is **Not assessed**.
