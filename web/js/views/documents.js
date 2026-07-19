// Documents view: recent visible documents with live status refresh while
// the async pipeline is still working (RECEIVED/CONVERTING -> READY).

import { api } from '../api.js';
import { el, badge, fmtDate, panel, empty, toast } from '../ui.js';
import { openDocumentModal } from './docmodal.js';

export const title = 'Documents';

export function render(root, ctx) {
  const body = el('div', { class: 'panel-body flush' });
  root.append(panel('Recent documents',
    body,
    el('button', { class: 'small-btn', onclick: () => load(body, ctx) }, '↻ Refresh')));
  load(body, ctx);
}

async function load(body, ctx) {
  try {
    const [docs, orgs] = await Promise.all([
      api.get('/api/v1/documents'),
      api.get('/api/v1/orgs'),
    ]);
    const orgNames = new Map(orgs.map((o) => [o.id, o.name]));
    if (!docs.length) {
      body.replaceChildren(empty('No documents visible yet. Upload one to get started.'));
      return;
    }
    const rows = docs.map((doc) => el('tr', {},
      el('td', {}, el('a', {
        href: '#', onclick: (e) => {
          e.preventDefault();
          openDocumentModal(doc.id, { onSaved: () => load(body, ctx) });
        },
      }, doc.name)),
      el('td', {}, orgNames.get(doc.orgUnitId) ?? '—'),
      el('td', {}, badge(doc.status)),
      el('td', { class: 'muted' }, fmtDate(doc.ingestDate)),
      el('td', {}, el('div', { class: 'row-actions' },
        el('button', { class: 'small-btn', onclick: () => openDocumentModal(doc.id, { onSaved: () => load(body, ctx) }) }, 'Details')))));

    body.replaceChildren(el('table', { class: 'grid' },
      el('thead', {}, el('tr', {},
        el('th', {}, 'Name'), el('th', {}, 'Org unit'), el('th', {}, 'Status'),
        el('th', {}, 'Ingested'), el('th', {}, ''))),
      el('tbody', {}, rows)));

    // keep refreshing while the pipeline is still converting something
    if (docs.some((d) => d.status === 'RECEIVED' || d.status === 'CONVERTING')) {
      ctx.schedule(() => load(body, ctx), 2500);
    }
  } catch (e) {
    body.replaceChildren(empty(e.message));
    if (e.status !== 401) toast(e.message, 'error');
  }
}
