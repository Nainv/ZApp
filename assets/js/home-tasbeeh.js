/* The home counter shares its saved total with the remembrance cell. */
(() => {
  'use strict';
  const key = 'zikra_tasbeeh';
  const button = document.getElementById('homeTasbeehCount');
  const reset = document.getElementById('homeTasbeehReset');
  if (!button || !reset) return;
  const phrases = ['سبحان الله', 'الحمد لله', 'الله أكبر'];
  let count = 0;
  function readCount() {
    try { return Math.max(0, Math.trunc(Number(localStorage.getItem(key)) || 0)); }
    catch (_) { return count; }
  }
  function render() {
    const language = document.documentElement.lang || 'ar';
    const ar = language === 'ar';
    const format = new Intl.NumberFormat(ar ? 'ar-u-nu-arab' : 'en', { useGrouping: false });
    const phrase = phrases[Math.floor(count / 33) % phrases.length];
    document.getElementById('homeTasbeehValue').textContent = format.format(count);
    document.getElementById('homeTasbeehPhrase').textContent = phrase;
    document.getElementById('homeTasbeehProgress').textContent = format.format(count % 33) + ' / ' + format.format(33);
    document.getElementById('homeTasbeehTitle').textContent = ar ? 'المسبحة' : language === 'de' ? 'Tasbih-Zähler' : 'Tasbih';
    document.getElementById('homeTasbeehResetLabel').textContent = ar ? 'تصفير' : language === 'de' ? 'Zurücksetzen' : 'Reset';
    reset.setAttribute('aria-label', ar ? 'تصفير عداد المسبحة' : 'Reset tasbih counter');
    button.setAttribute('aria-label', phrase + '، ' + format.format(count) + (ar ? ' تسبيحة. اضغط للإضافة' : '. Add one'));
    button.style.setProperty('--tasbeeh-progress', (count % 33) / 33 * 360 + 'deg');
  }
  function save() {
    try { localStorage.setItem(key, String(count)); } catch (_) {}
    render();
  }
  button.addEventListener('click', () => { count = Math.min(Number.MAX_SAFE_INTEGER, count + 1); save(); });
  reset.addEventListener('click', () => { count = 0; save(); });
  for (const control of [button, reset]) {
    control.addEventListener('keydown', event => {
      if (event.key !== 'Select' && event.key !== 'OK' && event.keyCode !== 23) return;
      event.preventDefault();
      event.stopPropagation();
      control.click();
    });
  }
  window.addEventListener('storage', event => { if (event.key === key || event.key === null) { count = readCount(); render(); } });
  window.addEventListener('pageshow', () => { count = readCount(); render(); });
  new MutationObserver(render).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });
  count = readCount();
  render();
})();
