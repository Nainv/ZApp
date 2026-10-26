# Zikra for LG webOS

The LG target reuses the real Android application's HTML, Quran text, remembrance,
fonts, audio, settings and cell pages. It does not open the mosque directory dashboard.
Android Java services do not execute on LG.

## Build and install

Use Node 22 and the official `@webos-tools/cli@3.2.6`:

```sh
node --test tests/*.test.cjs
node webos/build.cjs ../zikra-lg-build
ares-package ../zikra-lg-build/app -o ../zikra-lg-packages
ares-install -d zikra-lg ../zikra-lg-packages/com.zikra.tv_1.0.1_all.ipk
ares-launch -d zikra-lg com.zikra.tv
```

The output directory must be new. CI uses its existing `GITHUB_RUN_NUMBER` to
generate a matching LG version. Local builds default to 1.0.1; set
`GITHUB_RUN_NUMBER` to a larger integer to upgrade an existing install.

## How shared updates work

The `build-lg` job runs only after the existing Android build/release job succeeds.
It builds a matching IPK, attaches it to the existing v1.0.5 release, and publishes
the complete LG interface at
`https://koukach91-hash.github.io/zikra-android-tv-new/lg/`.
The original browser site is preserved at the project root.

One-time repository setup: Settings → Pages → Build and deployment → Source →
**GitHub Actions**. If the repository has environment protection, an authorized
reviewer may also need to approve the `github-pages` deployment. Then run the
existing Android workflow or push an Android update.

The installed local shell checks the hosted build on each launch (4-second
timeout). It opens the latest published LG interface if the build is at least as
new as its bundled build. If hosting is unavailable, invalid, or older, it opens
the complete bundled app. If the remote interface cannot finish initialization
in 20 seconds, it also falls back locally. A local shell stores app-owned settings
and bookmarks and restores them before either interface starts. Other apps' data
on the shared GitHub Pages origin is never cleared or copied.

This automatically updates shared interface code at the next app launch. It does
not silently replace the installed IPK. Shell/platform integration changes need
a new IPK. An offline fallback is the installed bundled version, not a downloaded
copy of the last hosted release. Streaming Quran audio, weather and fresh prayer
data still need internet, as in the source app.

## Platform differences

- A 1280×720 logical canvas scales to the TV's full viewport for readable controls.
- LG Back (461) and media key codes use the shared remote handler.
- LG does not support the browser Geolocation API. The location action opens
  city search instead of waiting for a GPS permission that will never work.
- City/mosque selection and stored preferences persist across reinstall, restart
  and switching between hosted and bundled interfaces when LG retains app data.
- Adhan works only while the app is open. Android background alarms and boot
  startup are unavailable; their controls explain this rather than claiming success.
- The Android-only native Erfurt calendar integration is not ported. LG uses the
  selected location's calculated timings, labeled accordingly, even for that mosque.
- A direction/bearing can be displayed; the TV does not have a phone compass.
- Developer Mode expiration can remove the app. Long-term store distribution
  requires LG submission/review; this setup does not bypass Developer Mode.

## Validation

Node tests cover shared app regressions, packaging contents, Android-source
preservation, LG keys/updater replacement, storage handshakes and origin isolation.
On OLED55G48LW / webOS SDK 10.3.1: installed and launched the local bundle;
confirmed the 1280×720 logical viewport fills 1920×1080, saved Erfurt/mosque
selection survives reinstall, and live weather/calculated timings load.
Hosted update delivery still requires the first successful Pages deployment and
an on-TV online/offline update test. Background adhan/autostart are not claimed.
