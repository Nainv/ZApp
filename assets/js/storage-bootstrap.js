/* Preserve application data when upgrading file:// assets to a secure local origin. */
(() => {
  try {
    if (!window.AndroidTV?.getLegacyStorage || localStorage.getItem('zikra_storage_migrated_v1')) return;
    const saved = JSON.parse(window.AndroidTV.getLegacyStorage());
    for (const [key, value] of Object.entries(saved)) {
      if (typeof value === 'string' && localStorage.getItem(key) === null) localStorage.setItem(key, value);
    }
    localStorage.setItem('zikra_storage_migrated_v1', '1');
  } catch (error) { console.warn('Saved settings could not be restored', error); }
})();
