/* TV policy is isolated from phone/tablet layout and motion. Run before page styles. */
(() => {
  'use strict';
  let television = false;
  try { television = window.AndroidTV?.isTelevision?.() === true; } catch (_) {}
  try { television ||= parent !== window && parent.document.documentElement.classList.contains('tv-device'); } catch (_) {}
  if (television) {
    document.documentElement.classList.add('tv-device');
    document.documentElement.dataset.inputMode = 'remote';
  }
})();
