// Small DOM helpers shared by all views — no framework, no build step.

export const esc = (value) => String(value ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** el('button', {class:'primary', onclick: fn}, 'Save') */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2), value);
    } else if (key === 'class') {
      node.className = value;
    } else if (value !== null && value !== undefined) {
      node.setAttribute(key, value);
    }
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined) continue;
    node.append(child.nodeType ? child : document.createTextNode(child));
  }
  return node;
}

export function toast(message, type = 'info') {
  const node = el('div', { class: `toast ${type}` }, message);
  document.getElementById('toasts').append(node);
  setTimeout(() => node.remove(), 4200);
}

export function badge(status) {
  return el('span', { class: `badge ${esc(status)}` }, status);
}

export function fmtDate(iso) {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

const modal = document.getElementById('modal');
const modalBody = document.getElementById('modal-body');

export function openModal(...content) {
  modalBody.replaceChildren(...content.filter((c) => c !== null && c !== undefined));
  if (!modal.open) modal.showModal();
}

export function closeModal() {
  if (modal.open) modal.close();
  modalBody.replaceChildren();
}

modal.addEventListener('click', (event) => {
  if (event.target === modal) closeModal();
});

export function modalHead(title) {
  return el('div', { class: 'modal-head' },
    el('h3', {}, title),
    el('button', { class: 'ghost small-btn', onclick: closeModal, 'aria-label': 'Close' }, '✕'));
}

export function panel(title, bodyNode, headExtra = null) {
  const head = el('div', { class: 'panel-head' }, el('h3', {}, title));
  if (headExtra) head.append(headExtra);
  return el('div', { class: 'panel' }, head, bodyNode);
}

export function empty(message) {
  return el('div', { class: 'empty' }, message);
}

/** Escape server text but keep the <mark> highlights produced by FTS snippets. */
export function highlight(snippet) {
  const span = document.createElement('span');
  span.className = 'snippet';
  span.innerHTML = esc(snippet).replaceAll('&lt;mark&gt;', '<mark>').replaceAll('&lt;/mark&gt;', '</mark>');
  return span;
}
