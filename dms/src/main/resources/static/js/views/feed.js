// RSS feed view: issue a personal, revocable feed token (shown exactly once).

import { api } from '../api.js';
import { el, panel, toast } from '../ui.js';

export const title = 'RSS Feed';

export function render(root) {
  const result = el('div', {});
  const generate = el('button', {
    class: 'primary',
    onclick: async () => {
      try {
        const issued = await api.post('/api/v1/feeds/token', {});
        result.replaceChildren(
          el('p', {}, 'Your personal feed URL — shown once, keep it secret:'),
          el('p', {}, el('code', {}, issued.url)),
          el('div', { class: 'form-row' },
            el('button', {
              onclick: () => navigator.clipboard.writeText(issued.url)
                  .then(() => toast('Copied to clipboard.', 'ok')),
            }, 'Copy URL'),
            el('button', {
              class: 'danger',
              onclick: async () => {
                await api.del(`/api/v1/feeds/token/${issued.id}`);
                result.replaceChildren(el('p', { class: 'muted' }, 'Token revoked.'));
                toast('Feed token revoked.', 'ok');
              },
            }, 'Revoke this token')));
      } catch (e) {
        toast(e.message, 'error');
      }
    },
  }, 'Generate feed URL');

  root.append(panel('RSS inbox feed', el('div', { class: 'panel-body' },
    el('p', { class: 'muted' },
      'Subscribe your RSS reader to newly received documents. The feed shows only documents you are allowed to see; the token is stored server-side as an HMAC and can be revoked at any time.'),
    generate,
    result)));
}
