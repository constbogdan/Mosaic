# Mosaic user guide

This is the versioned source for Mosaic's user-facing installation, update-channel, Seerr, and
troubleshooting guidance. The root [README](../README.md) remains the public product entry point.
Engineering behavior is governed by the canonical documents in the
[documentation index](README.md), not by this guide or a GitHub Wiki summary.

## Install Mosaic

Mosaic is distributed as an APK through [GitHub Releases](https://github.com/constbogdan/Mosaic/releases).
It is not currently distributed through an app store.

1. On the Android TV or Fire TV device, allow the application used to open the APK to install
   unknown apps. Android presents the exact setting differently across device versions.
2. Download the APK from the [current Stable release](https://github.com/constbogdan/Mosaic/releases/latest).
3. Transfer it by USB, network share, a TV file-transfer application, or ADB and open it on the
   device.
4. If Android blocks installation, return to the per-application install permission and confirm
   that the application opening the APK is allowed to install unknown apps.

Mosaic requires Android 6+ or Fire TV OS 6+ and a reachable Jellyfin server. The root README records
the currently tested Jellyfin versions.

## Updates

After installation, Mosaic automatically checks for updates. Available updates can be installed
from Mosaic settings. The first in-app update may prompt Android to grant Mosaic permission to
install updates; follow the device's system prompt.

Current releases are available on the [Mosaic Releases page](https://github.com/constbogdan/Mosaic/releases).

## Stable and Development channels

- **Stable** is intended for normal use. It receives explicitly promoted releases and is the
  recommended channel for most users.
- **Development** provides newer testing builds from the
  [`develop` release](https://github.com/constbogdan/Mosaic/releases/tag/develop). It may update and
  change more frequently.

Choose the channel in Mosaic's update settings. Switching channels changes which releases Mosaic
offers; it does not require a separate application installation.

The engineering publication and security contracts remain in
[Development releases](MOSAIC_DEVELOPMENT_RELEASE.md) and [Stable and Hold](MOSAIC_STABLE.md).

## Seerr integration

Seerr is optional. Mosaic's base Jellyfin browsing and playback remain usable when Seerr is not
configured or is temporarily unavailable.

When configured, Seerr extends the user experience with discovery, requests, watchlist state,
acquisition progress, and information about content not yet available in Jellyfin. For full
compatibility, configure Seerr itself to integrate with the same Jellyfin server used by Mosaic.
Mosaic is generally tested with the latest stable Seerr release; older releases may not behave as
expected.

## Troubleshooting

### Installation or update is blocked

- Confirm the device permits the application opening the APK to install unknown apps.
- For an in-app update, confirm Mosaic has the corresponding install permission when Android asks.
- Confirm the APK came from the canonical `constbogdan/Mosaic` Releases page.

### Mosaic is missing from the Android TV launcher or looks incorrect

Record the device and OS version and include a screenshot when reporting the problem. This guide
does not prescribe a device-specific launcher repair that the project has not verified.

### Seerr features are unavailable

Confirm the configured Seerr server is reachable, is a supported stable version, and is integrated
with the same Jellyfin server used by Mosaic. Jellyfin-only features should remain available while
Seerr is unavailable.

### Stable and Development are confusing

Use Stable for normal consumption. Use Development only when you intentionally want newer testing
builds and accept more frequent change.

### Report a problem

Search existing [Mosaic issues](https://github.com/constbogdan/Mosaic/issues) first. A useful report
includes the Mosaic version and channel, Android TV or Fire TV model and OS version, Jellyfin and
Seerr versions where relevant, reproduction steps, expected and observed behavior, and screenshots
or logs that do not contain credentials.

Contributors proposing a fix should continue with [Contributing](../CONTRIBUTING.md), the
[developer guide](../DEVELOPMENT.md), and the [engineering documentation index](README.md).

## GitHub Wiki boundary

Mosaic does not currently maintain a separate Wiki source tree. A future GitHub Wiki may provide
short user-facing copies or navigation for installation, channels, Seerr setup, troubleshooting,
and contributor orientation. Publishing it requires a separate explicit operator action.

A Wiki must link to this versioned guide for user behavior and to the repository documentation
index for engineering contracts. It must not independently define validation, exact-tree evidence,
release or signing authority, repository authentication, provenance, Hold behavior, or upstream
ownership and synchronization.
