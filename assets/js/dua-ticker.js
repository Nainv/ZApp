(function () {
  'use strict';

  var track = document.getElementById('duaTrack');
  var duas = Array.isArray(window.ZIKRA_DUAS_AR) ? window.ZIKRA_DUAS_AR : [];
  if (!track || !duas.length) return;

  // Keep the text in just two identical nodes. The viewport clips both copies,
  // so a long dua can never spill over the rest of the dashboard.
  var text = duas.filter(function (dua) {
    return typeof dua === 'string' && dua.trim();
  }).join('  ۞  ');
  if (!text) return;

  function makeGroup() {
    var group = document.createElement('span');
    group.className = 'dua-group';
    group.setAttribute('dir', 'rtl');
    group.textContent = text;
    return group;
  }

  while (track.firstChild) track.removeChild(track.firstChild);
  track.appendChild(makeGroup());
  track.appendChild(makeGroup());
  track.setAttribute('aria-label', 'شريط متحرك يضم ' + duas.length + ' دعاءً');

  function updateSpeed() {
    var group = track.firstElementChild;
    if (!group) return;
    var width = group.getBoundingClientRect().width;
    if (width > 0) {
      track.style.setProperty('--dua-duration', Math.min(360, Math.max(90, Math.round(width / 150))) + 's');
    }
  }

  if (window.requestAnimationFrame) window.requestAnimationFrame(updateSpeed);
  else window.setTimeout(updateSpeed, 0);

  var resizeTimer;
  window.addEventListener('resize', function () {
    window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(updateSpeed, 120);
  });
})();
