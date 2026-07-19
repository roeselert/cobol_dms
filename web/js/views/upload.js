// Upload view: drag & drop (or click) multi-file upload into an org unit,
// with per-file status polling and a shortcut into the metadata dialog.

import { api } from '../api.js';
import { el, badge, panel, toast } from '../ui.js';
import { openDocumentModal } from './docmodal.js';

export const title = 'Upload';

const ACCEPT = '.pdf,.docx,.jpg,.jpeg,.png,.tif,.tiff,.eml';

export async function render(root, ctx) {
  const orgSelect = el('select', { required: '' });
  try {
    const orgs = await api.get('/api/v1/orgs');
    if (!orgs.length) {
      root.append(panel('Upload', el('div', { class: 'panel-body' },
        el('p', { class: 'muted' },
          'You are not a member of any org unit yet. Ask an administrator to assign you (Administration tab).'))));
      return;
    }
    orgSelect.append(...orgs.map((o) => el('option', { value: o.id }, o.name)));
  } catch (e) {
    toast(e.message, 'error');
    return;
  }

  const fileInput = el('input', { type: 'file', accept: ACCEPT, multiple: '', class: 'hidden' });
  const queue = el('div', {});

  const dropzone = el('div', { class: 'dropzone' },
    el('div', {}, '⇪ Drop files here or click to choose'),
    el('div', { class: 'small' }, 'PDF · DOCX · JPG · PNG · TIFF · EML — max 100 MB'));

  dropzone.addEventListener('click', () => fileInput.click());
  dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
  dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
  dropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    uploadAll(e.dataTransfer.files);
  });
  fileInput.addEventListener('change', () => uploadAll(fileInput.files));

  function uploadAll(files) {
    [...files].forEach((file) => uploadOne(file));
    fileInput.value = '';
  }

  async function uploadOne(file) {
    const status = el('span', { class: 'muted small' }, 'uploading…');
    const actions = el('span', {});
    const item = el('div', { class: 'upload-item' },
      el('span', { class: 'grow' }, file.name), status, actions);
    queue.prepend(item);
    const form = new FormData();
    form.append('file', file);
    try {
      const doc = await api.upload(
        `/api/v1/documents?orgUnitId=${encodeURIComponent(orgSelect.value)}`, form);
      status.replaceChildren(badge(doc.status));
      actions.append(el('button', {
        class: 'small-btn',
        onclick: () => openDocumentModal(doc.id),
      }, 'Metadata'));
      poll(doc.id, status);
    } catch (e) {
      status.replaceChildren(el('span', { class: 'small' , style: 'color: var(--danger)' }, e.message));
    }
  }

  function poll(documentId, status) {
    ctx.schedule(async () => {
      try {
        const doc = await api.get(`/api/v1/documents/${documentId}`);
        status.replaceChildren(badge(doc.status));
        if (doc.status === 'RECEIVED' || doc.status === 'CONVERTING') poll(documentId, status);
      } catch { /* view switched or document gone — stop polling */ }
    }, 2000);
  }

  root.append(
    panel('Upload documents', el('div', { class: 'panel-body' },
      el('div', { class: 'form-row', style: 'margin-bottom: 0.9rem' },
        el('label', { class: 'field' }, 'Target org unit', orgSelect)),
      dropzone, fileInput)),
    panel('This session', queue));
}
