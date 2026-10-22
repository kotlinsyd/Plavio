/* No analytics, cookies, remote scripts, or stored identifiers. */
(() => {
  'use strict';
  const status = document.getElementById('copy-status');
  let statusTimeout;
  function notify(message) {
    if (!status) return;
    status.textContent = message;
    status.classList.add('visible');
    clearTimeout(statusTimeout);
    statusTimeout = setTimeout(() => status.classList.remove('visible'), 5500);
  }
  document.querySelectorAll('[data-copy]').forEach(button => {
    button.hidden = false;
    button.addEventListener('click', async () => {
      const value = button.dataset.copy;
      const original = button.innerHTML;
      let copied = false;
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(value);
          copied = true;
        }
      } catch (_) { /* Permission may be denied; try the local selection fallback. */ }
      if (!copied) {
        const field = document.createElement('textarea');
        field.value = value;
        field.setAttribute('readonly', '');
        field.setAttribute('aria-hidden', 'true');
        field.style.position = 'fixed';
        field.style.opacity = '0';
        document.body.appendChild(field);
        field.select();
        try { copied = document.execCommand('copy'); } catch (_) { copied = false; }
        field.remove();
        button.focus({ preventScroll: true });
      }
      if (copied) {
        button.textContent = 'Copied';
        notify('PlavioMeta URL copied. Paste it in Addons → + in Plavio.');
        setTimeout(() => { button.innerHTML = original; }, 2500);
      } else {
        const code = document.getElementById('meta-manifest');
        if (code && window.getSelection) {
          const range = document.createRange();
          range.selectNodeContents(code);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
        }
        notify('Copy was blocked. Press and hold the manifest URL to copy it manually.');
      }
    });
  });
})();
