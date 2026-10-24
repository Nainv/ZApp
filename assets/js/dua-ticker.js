(function () {
  'use strict';

  var track = document.getElementById('duaTrack');
  var duas = Array.isArray(window.ZIKRA_DUAS_AR) ? window.ZIKRA_DUAS_AR : [];
  if (!track || !duas.length) return;

  function createGroup() {
    var group = document.createElement('div');
    group.className = 'dua-group';

    duas.forEach(function (text, index) {
      if (index) {
        var separator = document.createElement('span');
        separator.className = 'dua-separator';
        separator.setAttribute('aria-hidden', 'true');
        separator.textContent = '۞';
        group.appendChild(separator);
      }

      var item = document.createElement('span');
      item.className = 'dua-item';
      item.textContent = text;
      group.appendChild(item);
    });

    return group;
  }

  var fragment = document.createDocumentFragment();
  fragment.appendChild(createGroup());
  fragment.appendChild(createGroup());
  while (track.firstChild) track.removeChild(track.firstChild);
  track.appendChild(fragment);
  track.setAttribute('aria-label', 'شريط متحرك يضم ' + duas.length + ' دعاءً');

  function updateSpeed() {
    var group = track.firstElementChild;
    if (!group) return;
    var width = group.getBoundingClientRect().width;
    if (width > 0) {
      track.style.setProperty('--dua-duration', Math.min(420, Math.max(90, Math.round(width / 140))) + 's');
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
