(function () {
  'use strict';

  var track = document.getElementById('duaTrack');
  var source = Array.isArray(window.ZIKRA_DUAS_AR) ? window.ZIKRA_DUAS_AR : [];
  var duas = source.filter(function (dua) {
    return typeof dua === 'string' && dua.trim();
  });
  if (!track || !duas.length) return;

  function makeGroup() {
    var group = document.createElement('div');
    group.className = 'dua-group';

    duas.forEach(function (text) {
      var item = document.createElement('span');
      item.className = 'dua-item';
      item.setAttribute('dir', 'rtl');
      item.textContent = text;
      group.appendChild(item);

      var mark = document.createElement('span');
      mark.className = 'dua-separator';
      mark.setAttribute('aria-hidden', 'true');
      mark.textContent = '۞';
      group.appendChild(mark);
    });

    return group;
  }

  // Two matching groups make the ticker loop without exposing text outside
  // the clipped viewport. The Zikra mark is the only separator between duas.
  while (track.firstChild) track.removeChild(track.firstChild);
  track.appendChild(makeGroup());
  track.appendChild(makeGroup());
  track.setAttribute('aria-label', 'شريط متحرك يضم ' + duas.length + ' دعاءً');

  function updateSpeed() {
    var group = track.firstElementChild;
    if (!group) return;
    var width = group.getBoundingClientRect().width;
    if (width > 0) {
      // About 42 CSS pixels per second, with no upper duration cap that could
      // force a long list to race across the screen.
      track.style.setProperty('--dua-duration', Math.max(180, Math.round(width / 42)) + 's');
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
