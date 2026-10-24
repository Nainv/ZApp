# Zikra modular cell structure

- `index.html` is the main app entry and preserves the original full UI/DOM/scripts.
- Each `cells/<name>/index.html` is an independent entry point focused on that cell.
- Original Android project files, package ID (`com.zikra.tv`), workflow and assets are retained.

Compatibility note: cell entry points retain the original DOM and scripts to avoid breaking existing cross-cell JavaScript dependencies. This is a safe first split of entry points, not yet a full deduplication of shared JS/CSS. Android/WebView runtime and upgrade installation have not been tested in this environment.
