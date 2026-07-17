// Search view: full text + metadata filters; results come pre-filtered by
// the server's ACL push-down, snippets carry <mark> highlights.

import { api } from '../api.js';
import { getConfig } from '../config.js';
import { el, panel, empty, highlight, toast } from '../ui.js';
import { openDocumentModal } from './docmodal.js';

export const title = 'Search';

export async function render(root) {
  const config = await getConfig();
  const q = el('input', { type: 'search', placeholder: 'Full-text search…', style: 'min-width: 220px' });
  const cls = el('select', {},
    el('option', { value: '' }, 'any class'),
    config.documentClasses.map((c) => el('option', { value: c }, c)));
  const ref = el('input', { type: 'text', placeholder: 'Ordnungsbegriff' });
  const from = el('input', { type: 'date', title: 'Document date from' });
  const to = el('input', { type: 'date', title: 'Document date to' });
  const results = el('div', { class: 'panel-body flush' });

  const form = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      const params = new URLSearchParams();
      if (q.value) params.set('q', q.value);
      if (cls.value) params.set('documentClass', cls.value);
      if (ref.value) params.set('filePlanReference', ref.value);
      if (from.value) params.set('dateFrom', from.value);
      if (to.value) params.set('dateTo', to.value);
      try {
        const hits = await api.get(`/api/v1/search?${params}`);
        if (!hits.length) {
          results.replaceChildren(empty('No matching documents.'));
          return;
        }
        results.replaceChildren(...hits.map((hit) => el('div', { class: 'upload-item' },
          el('span', { class: 'grow' },
            el('a', {
              href: '#',
              onclick: (e) => { e.preventDefault(); openDocumentModal(hit.documentId); },
            }, hit.name),
            hit.snippet ? el('div', {}, highlight(hit.snippet)) : null),
          el('button', { class: 'small-btn', onclick: () => openDocumentModal(hit.documentId) }, 'Details'))));
      } catch (e) {
        results.replaceChildren(empty(e.message));
        if (e.status !== 400) toast(e.message, 'error');
      }
    },
  },
    el('label', { class: 'field' }, 'Query', q),
    el('label', { class: 'field' }, 'Class', cls),
    el('label', { class: 'field' }, 'Ordnungsbegriff', ref),
    el('label', { class: 'field' }, 'From', from),
    el('label', { class: 'field' }, 'To', to),
    el('button', { class: 'primary', type: 'submit' }, 'Search'));

  root.append(
    panel('Search', el('div', { class: 'panel-body' }, form)),
    panel('Results', results));
  results.replaceChildren(empty('Enter a query or at least one filter.'));
}
