# Zikra Android TV — Modular UI Rebuild

The home page is `index.html`. Each screen is in `cells/`, shared styles are in `assets/css/`, and shared/screen behavior is in `assets/js/`.

## Important
- Do not upload this ZIP as a file inside the GitHub repository. Extract it and upload/replace the repository contents.
- Keep the `.github/workflows/build-apk.yml` workflow and repository Actions secrets/settings.
- The UI is an initial modular rebuild. Quran, weather, and prayer data require internet access. Background adhan notifications and a real AI assistant are not implemented in this version.
- Validate the GitHub Actions build and test remote navigation on an actual Android TV device before treating it as production-ready.
