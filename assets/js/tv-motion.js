/* TV-only animation ownership. No animation loop and no geometry reads on arrow presses. */
(() => {
  'use strict';
  if (!document.documentElement.classList.contains('tv-device')) return;
  const controls = 'button,a[href],input,select,textarea,[tabindex="0"]';
  const panels = '.editor,.choice-overlay,.confirm-overlay,.modal-backdrop';
  const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  let selected = null;
  const animations = new WeakMap();
  function animate(node, frames, duration) {
    if (!node?.animate) return;
    animations.get(node)?.cancel();
    const animation = node.animate(frames, {duration:reduced()?70:duration,easing:'cubic-bezier(.2,.8,.2,1)'});
    animations.set(node, animation);
    animation.onfinish = () => { if (animations.get(node) === animation) animations.delete(node); };
  }
  function focus(node) {
    const target = node?.closest?.(controls);
    if (target === selected) return;
    selected?.classList.remove('tv-motion-focus');
    selected = target;
    if (document.body.dataset.active !== 'false') selected?.classList.add('tv-motion-focus');
  }
  document.addEventListener('focusin', event => focus(event.target), true);
  document.addEventListener('focusout', event => {
    if (!event.relatedTarget) { selected?.classList.remove('tv-motion-focus');selected=null; }
  }, true);
  // Activation flash uses opacity rather than an animated filter or shadow.
  document.addEventListener('click', event => {
    const target = event.target.closest?.(controls);
    if (target) animate(target,[{opacity:.72},{opacity:1}],140);
  }, true);
  function visible(node) {
    return !node.hidden && (!node.matches('.modal-backdrop') || node.classList.contains('open'));
  }
  function reveal(node) {
    if (visible(node)) animate(node,[{opacity:0,transform:'translateY(10px) scale(.985)'},{opacity:1,transform:'translateY(0) scale(1)'}],220);
  }
  const start = () => {
    animate(document.querySelector('main,.app') || document.body,[{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],220);
    focus(document.activeElement);
    const watch = node => new MutationObserver(records => {
      if (records.some(record => record.oldValue !== node.getAttribute(record.attributeName))) reveal(node);
    }).observe(node,{attributes:true,attributeOldValue:true,attributeFilter:['hidden','class']});
    document.querySelectorAll(panels).forEach(watch);
    // Hidden cached pages drop their focus ring and restore it when resumed.
    new MutationObserver(() => {
      selected?.classList.toggle('tv-motion-focus',document.body.dataset.active !== 'false');
    }).observe(document.body,{attributes:true,attributeFilter:['data-active']});
    window.addEventListener('message',event => {
      if (event.source === parent && event.data?.type === 'zikra:cell-active' && event.data.active) {
        animate(document.querySelector('main,.app') || document.body,[{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],220);
      }
    });
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
