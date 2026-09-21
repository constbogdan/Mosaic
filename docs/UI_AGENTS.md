# Mosaic UI agent guidance

This file contains only UI-specific guidance. [AGENTS.md](AGENTS.md) is the authoritative general
agent contract and takes precedence. Read it, the [architecture](ARCHITECTURE.md), and the
[roadmap](MOSAIC_ROADMAP.md) before UI work.

## Scope

Use this guidance for Android TV Compose presentation, navigation, focus, cards, rows, and screen
integration. It does not redefine repository workflow, validation, publication, release, upstream,
or handoff rules.

## TV interaction and focus

- Treat focus as part of behavior, not decoration. Verify entry focus, directional movement,
  removal of the focused item/row, restoration after navigation, and empty-state transitions.
- Reuse established TV components and focus/navigation patterns before creating screen-local ones.
- Keep primary actions reachable without pointer input and avoid layouts that clip focused scale or
  place important content outside TV safe areas.
- When a row disappears, preserve a predictable adjacent destination rather than leaving focus on a
  removed node.

## Product state presentation

- Present the actual semantic condition: `Incomplete`, `Requested`, `Downloading`, `Available`, or
  another established state. Do not expose internal workflow terminology.
- Do not treat Seerr availability as Jellyfin playability. Navigation to playable content requires a
  verified Jellyfin identity.
- Acquisition, library integrity, watchlist, availability, and collection membership are separate
  dimensions. Compose UI from shared state instead of recomputing them inside cards/screens.
- Preserve base Jellyfin surfaces when optional extended capabilities are disabled or unavailable.

## Reuse and consistency

- Reuse shared card badges, progress treatment, spacing, typography, focus scale, and navigation
  primitives where they express the same meaning.
- Prefer the most specific valid destination, such as a known Jellyfin season instead of its series.
- Keep ordinary Movies and Series grids Jellyfin-local; non-local requested/acquiring items belong
  on explicit transient or discovery surfaces.
- Avoid adding a second visual language for downstream features. New assets and presentation should
  fit Mosaic's established icon, banner, and typography system.

## Validation

Automated tests do not replace Android TV device/emulator checks when focus, navigation, scaling,
clipping, installation, or runtime integration changes. Use the existing debug fixture surfaces
described in [AGENTS.md](AGENTS.md#debug-home-acquisition-fixtures) where applicable, and report the
remaining manual validation explicitly.
