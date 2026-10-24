/* Shared Android TV remote routing for standalone cells. */
(() => {
  'use strict';

  const keys = { left: 'ArrowLeft', right: 'ArrowRight', up: 'ArrowUp', down: 'ArrowDown' };
  const focusableSelector = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])';

  function visible(element) {
    if (!element || element.disabled || element.getAttribute('aria-hidden') === 'true' || element.closest('[aria-hidden="true"], [hidden]')) return false;
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
  }

  function focusables() {
    const dialogs = [...document.querySelectorAll('[aria-modal="true"], dialog[open], .modal-backdrop.open, .settings-screen.open')].filter(visible);
    const scope = dialogs[dialogs.length - 1] || document;
    return [...scope.querySelectorAll(focusableSelector)].filter(visible);
  }

  function dispatch(key) {
    const target = document.activeElement && document.activeElement !== document.documentElement
      ? document.activeElement
      : document.body;
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    return event;
  }

  function closeCell() {
    if (window.parent !== window) {
      window.parent.postMessage({ type: 'zikra:cell-close' }, '*');
      return;
    }
    if (window.history.length > 1) window.history.back();
    else window.location.href = '../../index.html';
  }

  window.closeZikraCell = closeCell;

  const closeButton = document.getElementById('cellHomeButton');
  if (closeButton) {
    closeButton.textContent = '×';
    closeButton.setAttribute('aria-label', 'إغلاق الخلية');
    closeButton.classList.add('cell-close-button');
    const style = document.createElement('style');
    style.textContent = '.cell-close-button{position:fixed!important;top:max(12px,env(safe-area-inset-top))!important;left:max(12px,env(safe-area-inset-left))!important;z-index:99999!important;width:48px!important;height:48px!important;padding:0!important;display:grid!important;place-items:center!important;border:1px solid rgba(255,255,255,.48)!important;border-radius:50%!important;background:rgba(16,27,35,.76)!important;color:#fff!important;font:400 32px/1 Arial,sans-serif!important;box-shadow:0 5px 18px #0003!important;cursor:pointer!important}.cell-close-button:focus-visible{outline:3px solid #ffe08c!important;outline-offset:3px!important}';
    document.head.append(style);
  }

  window.addEventListener('message', event => {
    const message = event.data;
    if (event.source !== parent || !message || typeof message !== 'object') return;
    if (message.type === 'zikra:cell-dpad') window.handleAndroidDpad?.(message.direction);
    else if (message.type === 'zikra:cell-ok') window.handleAndroidOk?.();
    else if (message.type === 'zikra:cell-back') window.handleAndroidBack?.();
    else if (message.type === 'zikra:cell-focus') focusFirst();
  });

  function move(direction) {
    if (window.ZikraNavigation?.move(direction)) return;
    const list = focusables();
    if (!list.length) return;
    const current = document.activeElement;
    if (!list.includes(current)) {
      list[0].focus({ preventScroll: true });
      return;
    }

    if (current instanceof HTMLInputElement && ['text', 'search', 'email', 'url', 'tel', 'password'].includes((current.type || 'text').toLowerCase()) && (direction === 'left' || direction === 'right')) return;
    if (current instanceof HTMLSelectElement && (direction === 'up' || direction === 'down')) {
      const next = Math.max(0, Math.min(current.options.length - 1, current.selectedIndex + (direction === 'down' ? 1 : -1)));
      if (next !== current.selectedIndex) {
        current.selectedIndex = next;
        current.dispatchEvent(new Event('change', { bubbles: true }));
      }
      return;
    }

    const vector = { left: [-1, 0], right: [1, 0], up: [0, -1], down: [0, 1] }[direction];
    if (!vector) return;
    const [dx, dy] = vector;
    const from = current.getBoundingClientRect();
    const sx = from.left + from.width / 2;
    const sy = from.top + from.height / 2;
    const candidates = list.filter(element => element !== current).map(element => {
      const rect = element.getBoundingClientRect();
      const x = rect.left + rect.width / 2;
      const y = rect.top + rect.height / 2;
      const primary = dx ? (x - sx) * dx : (y - sy) * dy;
      const cross = dx ? Math.abs(y - sy) : Math.abs(x - sx);
      const overlap = dx
        ? Math.min(from.bottom, rect.bottom) - Math.max(from.top, rect.top)
        : Math.min(from.right, rect.right) - Math.max(from.left, rect.left);
      return { element, primary, cross, aligned: overlap > 1, score: primary + cross * 1.7 };
    }).filter(item => item.primary > 1);

    const aligned = candidates.filter(item => item.aligned);
    const choices = aligned.length ? aligned : candidates;
    choices.sort((a, b) => (aligned.length ? a.primary - b.primary || a.cross - b.cross : a.score - b.score));
    const target = choices[0]?.element;
    if (target) {
      target.focus({ preventScroll: true });
      const bounds=target.getBoundingClientRect();if(bounds.top<0||bounds.bottom>innerHeight||bounds.left<0||bounds.right>innerWidth)target.scrollIntoView({block:'nearest',inline:'nearest',behavior:'instant'});
    }
  }

  // Page-specific handlers get first chance to implement local controls.
  document.addEventListener('keydown', event => {
    if(event.key==='Tab'){
      const list=focusables(),first=list[0],last=list[list.length-1];
      if(first&&event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus({preventScroll:true})}
      else if(first&&!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus({preventScroll:true})}
      return;
    }
    const direction = Object.keys(keys).find(name => keys[name] === event.key);
    if (!direction || event.defaultPrevented) return;
    event.preventDefault();
    move(direction);
  });

  document.addEventListener('keydown', event => {
    if (!['Escape', 'Backspace', 'BrowserBack', 'GoBack', 'XF86Back'].includes(event.key) && event.keyCode !== 4) return;
    if (event.key === 'Backspace' && (document.activeElement instanceof HTMLInputElement || document.activeElement instanceof HTMLTextAreaElement || document.activeElement?.isContentEditable)) return;
    if (event.defaultPrevented) return;
    event.preventDefault();
    window.handleAndroidBack?.();
  });

  if (typeof window.handleAndroidDpad !== 'function') {
    window.handleAndroidDpad = direction => {
      const key = keys[String(direction).toLowerCase()];
      if (!key) return;
      const event = dispatch(key);
      if (!event.defaultPrevented) {
        event.preventDefault();
        move(String(direction).toLowerCase());
      }
    };
  }

  if (typeof window.handleAndroidOk !== 'function') {
    window.handleAndroidOk = () => {
      const event = dispatch('Enter');
      if (!event.defaultPrevented) document.activeElement?.click();
    };
  }

  if (typeof window.handleAndroidBack !== 'function') {
    window.handleAndroidBack = closeCell;
  }

  const focusFirst = () => {
    const list = focusables();
    if (list.length && !list.includes(document.activeElement)) list[0].focus({ preventScroll: true });
  };
  if (document.readyState === 'complete') setTimeout(focusFirst, 0);
  else window.addEventListener('load', focusFirst, { once: true });
})();
