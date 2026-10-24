# Zikra runtime

Home has one Quran audio player and one clock/prayer tick. Quran readers and the index load only when the user opens Quran. The old hidden settings iframe, weather modal, adhan editor and their keyboard handlers were removed.

`tv-remote.js` is the only document keyboard/remote controller. Cells supply small navigation delegates. `fast-navigation.js` caches physical focus links; it does not register another keyboard controller. `cell-host.js` sends preferences/data explicitly to cells, waits for save acknowledgments, keeps at most two cell documents and returns without reloading home. The old Quran cell URL redirects to the shared home player.

Location is normalized by `location.js`. GPS is requested through an explicit action; a previously authorized GPS location can refresh on startup. A pinned mosque remains fixed. City results and mosque results are buttons; the only city text field is readonly until OK/click activates typing. There is no timezone editor or timetable file chooser.

Nearby mosque names/coordinates come from the documented public PrayerZone directory through the narrow Android HTTPS adapter. Selecting a mosque saves its coordinates immediately. Prayer times always use the chosen coordinates and AlAdhan calculation method, including for mosques without any timetable file. They are labeled as location calculations and are not represented as a mosque-published schedule. Native directory lookup works in Android; a web deployment needs a CORS-capable backend adapter for the directory.

`prayer-model.js` supplies the same snapshot to home and the prayer cell, including tomorrow and IANA timezone conversion. One bounded cache entry can retain today's timings for the same coordinates/method while offline. It never reuses another city's timings.

`weather-data.js` fetches ten days once and shares them with the weather cell. Unix timestamps are formatted per instant in the city's timezone, including sunrise/sunset across DST changes. There is one bounded weather cache entry. `weather-scene.js` supplies the same current-weather background inside and outside, with no more than ten animated particles. Selecting a forecast day does not change the background away from the current condition. Weather has fixed-size day buttons, a single detail panel and interactive hourly buttons.

Obsolete assets and duplicate controllers were removed after reference tracing. On an APK upgrade, WebView resource cache is cleared once without clearing DOM storage; completed installer downloads are removed when no longer newer than the installed version. Quran bookmarks and settings remain in DOM storage. The dua animation runs on the compositor and pauses behind a cell/modal.

The settings updater saves pending settings, checks the latest stable GitHub release and update.json, streams the APK off the UI thread, verifies SHA-256, package identity, version and signing certificate, then uses Android's installer consent. Reopening after installation is attempted only for a user-initiated update; Android/OEM restrictions may require Open. The browser can download an APK but cannot install one.

Tests: node --test tests/prayer-model.test.cjs. CI also runs tests/UpdatePolicyTest.java before building/signing the APK. Browser integration checks cover city/mosque selection, persistence, prayer parity, remote direction/wrap/back, weather scenes and TV layout. These do not substitute for physical Android TV validation.
