# Remote, cells, and prayer data

Home owns one prayer snapshot and sends it to cells using source-checked messages. Embedded prayer/weather cells do not fetch their own conflicting data. A settings save updates home before the cell closes. Loaded cells are reused; closing them never navigates or reloads home.

Android serves bundled files at `https://appassets.androidplatform.net/assets/` through a restricted local asset handler. The first upgrade captures the previous `file:///android_asset/index.html` storage and imports missing keys into the new origin, preserving settings, Quran progress, and tasbeeh totals. File/universal cross-origin access remain disabled. D-pad down/up events go straight to the focused WebView document; OK, media, and Back use the existing explicit bridge. System Back and remote Back are routed to the same application handler.

The shared focus graph is cached and uses physical directions, with explicit home control lanes. It rebuilds for layout/visible-control changes, never for a countdown tick. Tasbeeh updates immediately and defers persistent storage writes. The non-focusable dua lane uses linear compositor animations rather than a JavaScript animation-frame loop, and pauses behind cells/dialogs.

## Mosque data boundary

City search uses Open-Meteo geocoding. Nearby mosque names (within 25 km of the selected coordinates) come from the documented public PrayerZone directory and are sorted by distance. Android uses a narrow asynchronous HTTPS adapter because the directory does not return browser CORS headers; a deployed web edition needs an equivalent backend adapter. A directory result is **not** proof of an official mosque timetable. PrayerZone's calculated times are not presented as mosque-published times.

For a selected mosque the app currently supports importing its timetable as CSV and specifying its IANA timezone. CSV headers: `Date,Fajr,Sunrise,Dhuhr,Asr,Maghrib,Isha`; date: `YYYY-MM-DD`; time: `HH:mm` in 24-hour format; Sunrise is optional. If today's or tomorrow's schedule is missing, it reports the missing data instead of substituting a calculation. The imported file must be supplied from the mosque; it is not independently authenticated by Zikra.

Automatic retrieval of MAWAQIT mosque timetables is not implemented. MAWAQIT's official help says its API is private and offers approved direct URLs/widgets on request: <https://help.mawaqit.net/en/articles/11991838-can-i-use-your-api>. No unofficial API, demo credential, or HTML scraping is used. Until a mosque is selected, the explicitly labeled AlAdhan astronomical calculation remains available. A licensed/public official schedule provider is still needed for the requested automatic mosque-to-timetable experience.

## Verification

Run `node --test tests/prayer-model.test.cjs` for DST conversion, day rollover, prayer boundaries, missing official data, and timetable import validation. Browser integration checks should cover independent cell origins, identical displayed times, save acknowledgments, Back, frame reuse, and a 960×540 TV viewport. These checks do not establish performance on physical televisions. Native origin migration, D-pad delivery, file selection, and Android permission flows require device/emulator validation.

The current deliverable is an Android package and a browser interface. Apple, Samsung Tizen, and LG webOS packaging/store publication remain separate platform work.
