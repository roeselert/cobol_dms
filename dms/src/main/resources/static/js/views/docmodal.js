// Document detail modal: lifecycle, renditions, metadata form, PDF preview.
// Shared by the documents, upload and search views.

import { api } from '../api.js';
import { getConfig } from '../config.js';
import { el, esc, badge, fmtDate, toast, openModal, closeModal, modalHead } from '../ui.js';

export async function openDocumentModal(documentId, { onSaved } = {}) {
  let doc;
  let config;
  try {
    [doc, config] = await Promise.all([
      api.get(`/api/v1/documents/${documentId}`),
      getConfig(),
    ]);
  } catch (e) {
    toast(e.message, 'error');
    return;
  }
  const meta = await api.get(`/api/v1/documents/${documentId}/metadata`).catch(() => null);

  const dateInput = el('input', { type: 'date', value: meta?.documentDate ?? '', required: '' });
  const classSelect = el('select', { required: '' },
    el('option', { value: '' }, '— choose —'),
    config.documentClasses.map((c) =>
      el('option', { value: c, ...(meta?.documentClass === c ? { selected: '' } : {}) }, c)));
  const refInput = el('input', {
    type: 'text', placeholder: '2026/PER/001', value: meta?.filePlanReference ?? '', required: '',
  });

  const aiHint = meta?.extractedByAi
    ? el('p', { class: 'muted small' }, '✨ Prefilled by AI extraction — please review before saving.')
    : null;

  const ordnungsbegriffe = meta?.ordnungsbegriffe?.length
    ? el('div', {}, meta.ordnungsbegriffe.map((ob) => el('p', { class: 'muted small' },
      el('span', { class: 'badge role' }, ob.typeName), ` ${ob.value}`)))
    : null;

  const intent = meta?.intent
    ? el('div', {},
      el('p', { class: 'muted small' }, 'Intent ', el('span', { class: 'badge role' }, meta.intent.name)),
      Object.entries(meta.intent.fields ?? {}).map(([name, value]) =>
        el('p', { class: 'muted small' }, `${name}: ${value}`)))
    : null;

  const flagHint = {
    MANUAL_INDEXING: el('p', { class: 'muted small' },
      '⚑ No Ordnungsbegriffe found — this document needs manual indexing.'),
    REVIEW: el('p', { class: 'muted small' },
      '⚑ AI extraction incomplete — please review and index manually.'),
  }[meta?.indexingFlag] ?? null;

  const form = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.put(`/api/v1/documents/${documentId}/metadata`, {
          documentDate: dateInput.value,
          documentClass: classSelect.value,
          filePlanReference: refInput.value,
        });
        toast('Metadata saved — document filed into its Akte.', 'ok');
        closeModal();
        onSaved?.();
      } catch (e) {
        toast(e.message, 'error');
      }
    },
  },
    el('label', { class: 'field' }, 'Document date', dateInput),
    el('label', { class: 'field' }, 'Document class', classSelect),
    el('label', { class: 'field' }, 'Ordnungsbegriff', refInput),
    el('button', { class: 'primary', type: 'submit' }, 'Save metadata'));

  const previewSlot = el('div', {});
  const previewBtn = el('button', {
    onclick: async () => {
      previewBtn.disabled = true;
      try {
        const url = await api.blobUrl(`/api/v1/documents/${documentId}/file`);
        previewSlot.replaceChildren(el('iframe', { class: 'preview-frame', src: url, title: 'Preview' }));
      } catch (e) {
        toast(`Preview failed: ${e.message}`, 'error');
        previewBtn.disabled = false;
      }
    },
  }, 'Preview PDF');

  const downloadBtn = el('button', {
    onclick: async () => {
      try {
        const url = await api.blobUrl(`/api/v1/documents/${documentId}/file`);
        const a = el('a', { href: url, download: doc.name });
        a.click();
      } catch (e) {
        toast(`Download failed: ${e.message}`, 'error');
      }
    },
  }, 'Download');

  // Re-run conversion + AI classification: recovers a FAILED document and
  // re-classifies one whose extraction was skipped or incomplete.
  const retryBtn = el('button', {
    onclick: async () => {
      retryBtn.disabled = true;
      try {
        await api.post(`/api/v1/documents/${documentId}/reprocess`);
        toast('Re-running conversion and classification — refresh in a moment.', 'ok');
        closeModal();
        onSaved?.();
      } catch (e) {
        toast(`Retry failed: ${e.message}`, 'error');
        retryBtn.disabled = false;
      }
    },
  }, 'Retry classification');

  openModal(
    modalHead(doc.name),
    el('p', {}, badge(doc.status), ' ',
      el('span', { class: 'muted small' }, `ingested ${fmtDate(doc.ingestDate)}`)),
    el('p', { class: 'muted small' },
      `Renditions: ${doc.renditions.map((r) => `${r.type} (${formatBytes(r.sizeBytes)})`).join(' · ') || 'none yet'}`),
    el('div', { class: 'form-row' }, previewBtn, downloadBtn, retryBtn),
    previewSlot,
    el('h4', {}, 'Metadata'),
    aiHint,
    intent,
    ordnungsbegriffe,
    flagHint,
    form,
    meta?.akteId
      ? el('p', { class: 'muted small' }, `Filed in Akte ${esc(meta.filePlanReference)} (v${meta.version})`)
      : el('p', { class: 'muted small' }, 'Not yet filed into an Akte — confirm the metadata above.'));
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
