// Aktensicht: two-pane view — visible Akten on the left, the selected
// Akte's paginated, ACL-filtered document list on the right (US-09).

import { api } from '../api.js';
import { el, badge, fmtDate, panel, empty, toast } from '../ui.js';
import { openDocumentModal } from './docmodal.js';

export const title = 'Akten';

export async function render(root) {
  const list = el('div', { class: 'select-list' });
  const detailBody = el('div', { class: 'panel-body flush' }, empty('Select an Akte.'));
  const detailPanel = panel('Documents', detailBody);

  root.append(el('div', { class: 'split' },
    panel('Akten', el('div', { class: 'panel-body flush' }, list)),
    detailPanel));

  try {
    const akten = await api.get('/api/v1/akten');
    if (!akten.length) {
      list.replaceChildren(empty('No Akten yet — they are formed when document metadata is confirmed.'));
      return;
    }
    for (const akte of akten) {
      const btn = el('button', {
        type: 'button',
        onclick: () => {
          list.querySelectorAll('button').forEach((b) => b.classList.remove('active'));
          btn.classList.add('active');
          openAkte(akte, detailPanel, detailBody);
        },
      }, akte.filePlanReference);
      list.append(btn);
    }
  } catch (e) {
    list.replaceChildren(empty(e.message));
    toast(e.message, 'error');
  }
}

async function openAkte(akte, detailPanel, body) {
  detailPanel.querySelector('h3').textContent = `Akte ${akte.filePlanReference}`;
  try {
    const docs = await api.get(`/api/v1/akten/${akte.id}/documents`);
    if (!docs.length) {
      body.replaceChildren(empty('No visible documents in this Akte.'));
      return;
    }
    body.replaceChildren(el('table', { class: 'grid' },
      el('thead', {}, el('tr', {},
        el('th', {}, 'Name'), el('th', {}, 'Status'), el('th', {}, 'Ingested'), el('th', {}, ''))),
      el('tbody', {}, docs.map((doc) => el('tr', {},
        el('td', {}, doc.name),
        el('td', {}, badge(doc.status ?? '—')),
        el('td', { class: 'muted' }, fmtDate(doc.ingestDate)),
        el('td', {}, el('div', { class: 'row-actions' },
          el('button', { class: 'small-btn', onclick: () => openDocumentModal(doc.documentId) }, 'Details'))))))));
  } catch (e) {
    body.replaceChildren(empty(e.message));
  }
}
