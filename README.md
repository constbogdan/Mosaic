# Mosaic — an Android TV client for Jellyfin

Mosaic is a downstream Android TV client derived from
[Wholphin](https://github.com/damontecres/Wholphin). It keeps Wholphin's playback and television
interface foundation while adding Mosaic's independently validated application and release
lifecycle.

Mosaic is separate from the
[official Jellyfin Android TV client](https://github.com/jellyfin/jellyfin-androidtv) and from the
upstream Wholphin application. Mosaic's Android package is `io.github.constbogdan.mosaic`.

<p align="center">
<a href="https://github.com/constbogdan/Mosaic/releases/latest">
<img alt="Current Mosaic Release" src="https://img.shields.io/github/release/constbogdan/Mosaic.svg"/>
</a>
<a href="https://translate.codeberg.org/engage/wholphin/">
<img src="https://translate.codeberg.org/widget/wholphin/wholphin/svg-badge.svg" alt="Translation status" />
</a>
<br/>
</p>

![v0_5_1_home](https://github.com/user-attachments/assets/62bb1703-abdf-4154-9054-e00b6ceb57b5)

## Features

### User interface

- Customize the home page to see the content you are interested in
    - Use poster or thumb images, show/hide titles, add/remove/re-order different types of rows!
    - Option to combine the Continue Watching & Next Up into a single row
    - Pin collections, playlists, favorites, genres, and studios
    - Remove series from next up
- A navigation drawer for quick access to libraries, favorites, search, and settings from almost anywhere in the app
- Integration with [Seerr](https://github.com/seerr-team/seerr) to discover new movies and TV shows
    - Included in the Mosaic GitHub distribution
- Customize library display
  - Option to show Movie/TV Show titles
  - Choose image types & size
  - Choose from grid or list layouts
- Play theme music, if available
- Customize subtitle style for plain text subtitles with separate HDR settings
- Search & download subtitles (requires compatible server plugin such as [OpenSubtitles](https://github.com/jellyfin/jellyfin-plugin-opensubtitles))
- Multiple app color themes
- Protect user profile switches with PIN code or require server login
- Change the user interface language per user
- In-app & Android OS screensaver support

### Playback

- Different media playback engines:
  - **ExoPlayer** w/ optional extra audio, SSA/ASS, & AV1 software decoding
  - **MPV** for direct playing anything with great SSA/ASS subtitle support
- Plex inspired playback controls:
  - Using D-Pad left/right for seeking during playback
  - Quickly access video chapters & queue during playback
  - Optionally skip back a few seconds when resuming playback
- Live TV & DVR support
- Music & music video support
- Cinema mode/pre-roll intro support (requires compatible server plugin such as [Intros](https://github.com/jellyfin/jellyfin-plugin-intros))
- Auto play next episodes with pass out protection
- Option for automatic refresh rate & resolution switching on supported displays
- Trickplay (video preview) support
- Subtly show playback position along the bottom of the screen while seeking w/ D-Pad

### Roadmap

See the [Mosaic roadmap](docs/Wholphin_ROADMAP.md).

## Installation

Mosaic is currently distributed directly through GitHub Releases. Download
[`Mosaic-release.apk`](https://github.com/constbogdan/Mosaic/releases/latest/download/Mosaic-release.apk)
from the current Stable release. Development builds are available from the
[`develop` release](https://github.com/constbogdan/Mosaic/releases/tag/develop) for explicit testing.

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

Seerr integration is generally tested with the latest stable version. Older versions may not work as expected. The Seerr server should configured with integration to your Jellyfin server for full compatibility.

## Contributions

Issues and pull requests are always welcome! Please check before submitting that your issue or pull request is not a duplicate.

If you plan to contribute, please read the [contributing guide](CONTRIBUTING.md)!

Mosaic currently inherits upstream Wholphin translations. Translation contributions belong in
the [upstream translation project](https://translate.codeberg.org/engage/wholphin/).

## Acknowledgements

- Thanks to the Jellyfin team for creating and maintaining such a great open-source media server
- Thanks to the official Jellyfin Android TV client developers, some code for creating the device direct play profile is adapted from there
- Thanks to the Jellyfin Kotlin SDK developers for making it easier to interact with the Jellyfin server API
- Thanks to numerous other libraries that make app development even possible

## Additional screenshots

### Customized home page
![customize_home_example](https://github.com/user-attachments/assets/9a4f04b7-9604-4ea7-b352-50f2b15dc2f1)

### Movie library browsing
![v0_5_1_library](https://github.com/user-attachments/assets/fad0424b-0631-4438-a8bc-d4fbb95a5bf3)

### Movie page
![v0_5_1_movie](https://github.com/user-attachments/assets/849aad34-49d5-4864-8de7-005bbcb68ac6)

### Series page
![v0_5_1_series](https://github.com/user-attachments/assets/655389e1-6a6f-43bc-85e1-e2feffb20429)

### Genres in library
![v0_5_1_genres](https://github.com/user-attachments/assets/5bbcbeb6-edc9-42c7-a1d8-d92fa432a498)


### Playlist
![v0_5_1_playlist](https://github.com/user-attachments/assets/98268f7d-479d-41c6-b47b-3e67bbe661bc)
