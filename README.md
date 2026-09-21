# Mosaic - Android TV client for Jellyfin

Mosaic is a downstream Android TV client derived from
[Wholphin](https://github.com/damontecres/Wholphin).

See the [documentation index](docs/README.md) for engineering and contributor guidance.

## Installation

Mosaic is currently distributed directly through GitHub Releases. [![Current Mosaic Release](https://img.shields.io/github/release/constbogdan/Mosaic.svg)](https://github.com/constbogdan/Mosaic/releases/latest)

Development builds are available from the [`develop` release](https://github.com/constbogdan/Mosaic/releases/tag/develop) for testing.

1. Enable side-loading "unknown" apps
    - https://androidtvnews.com/unknown-sources-chromecast-google-tv/
    - https://www.xda-developers.com/how-to-sideload-apps-android-tv/
    - https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources
    - https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/
2. Install the APK on your Android TV device with one of these options:
    - Download the canonical APK from the [Mosaic releases page](https://github.com/constbogdan/Mosaic/releases/latest).
        - Put the APK on an SD Card/USB stick/network share and use a file manager app from the Google Play Store / Amazon AppStore (e.g. `FX File Explorer`). Android's preinstalled file manager probably will not work!
        - Use `Send files to TV` from the Google Play Store on your phone & TV
        - (Expert) Use [ADB](https://developer.android.com/studio/command-line/adb) to install the APK from your computer ([guide](https://fossbytes.com/side-load-apps-android-tv/#h-how-to-sideload-apps-on-your-android-tv-using-adb))

### Upgrading the app

After the initial install above, the app will automatically check for updates. The updates can be installed in settings.

The first time you attempt an update, the OS should guide you through enabling the required additional permissions for the app to install updates.

Mosaic is not currently distributed through an app store.

## Compatibility

Requires Android 6+ (or Fire TV OS 6+) and Jellyfin server `10.10.x` or `10.11.x` (tested on primarily `10.11`).

The app is tested on a variety of Android TV/Fire TV OS devices, but if you encounter issues, please file an issue!

Seerr integration is generally tested with the latest stable version. Older versions may not work as expected. The Seerr server should be configured with integration to your Jellyfin server for full compatibility.
