// Job queue view: every conversion job for documents the user may see,
// active work first (QUEUED/RUNNING), with live refresh while the pipeline
// is still working.

import { api } from '../api.js';
import { el, badge, fmtDate, panel, empty, toast } from '../ui.js';
import { openDocumentModal } from './docmodal.js';

export const title = 'Job Queue';

export function render(root, ctx) {
  const body = el('div', { class: 'panel-body flush' });
  root.append(panel('Conversion jobs',
    body,
    el('button', { class: 'small-btn', onclick: () => load(body, ctx) }, '↻ Refresh')));
  load(body, ctx);
}

async function load(body, ctx) {
  try {
    const jobs = await api.get('/api/v1/jobs');
    if (!jobs.length) {
      body.replaceChildren(empty('No conversion jobs yet. Upload a document to start the pipeline.'));
      return;
    }
    const rows = jobs.map((job) => el('tr', {},
      el('td', {}, el('a', {
        href: '#', onclick: (e) => {
          e.preventDefault();
          openDocumentModal(job.documentId, { onSaved: () => load(body, ctx) });
        },
      }, job.documentName)),
      el('td', {}, badge(job.status)),
      el('td', {}, String(job.attempts)),
      el('td', { class: 'muted' }, fmtDate(job.createdAt)),
      el('td', { class: 'muted small' }, job.lastError ?? '—')));

    body.replaceChildren(el('table', { class: 'grid' },
      el('thead', {}, el('tr', {},
        el('th', {}, 'Document'), el('th', {}, 'Status'), el('th', {}, 'Attempts'),
        el('th', {}, 'Created'), el('th', {}, 'Last error'))),
      el('tbody', {}, rows)));

    // keep refreshing while the queue still has active work
    if (jobs.some((j) => j.status === 'QUEUED' || j.status === 'RUNNING')) {
      ctx.schedule(() => load(body, ctx), 2500);
    }
  } catch (e) {
    body.replaceChildren(empty(e.message));
    if (e.status !== 401) toast(e.message, 'error');
  }
}
