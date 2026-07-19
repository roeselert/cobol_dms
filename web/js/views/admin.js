// Administration: org hierarchy management (US-05) and member assignment
// (US-06). The server enforces who may do what; the UI only presents it.

import { api } from '../api.js';
import { el, esc, panel, empty, toast, openModal, closeModal, modalHead } from '../ui.js';

export const title = 'Administration';

const ROLES = ['VIEWER', 'EDITOR', 'ADMIN'];

export function render(root) {
  const tree = el('div', {});
  const nameInput = el('input', { type: 'text', placeholder: 'Unit name', required: '' });
  const parentSelect = el('select', {}, el('option', { value: '' }, '(top level)'));

  const createForm = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.post('/api/v1/orgs', {
          name: nameInput.value,
          parentId: parentSelect.value || null,
        });
        nameInput.value = '';
        toast('Org unit created.', 'ok');
        load(tree, parentSelect);
      } catch (e) {
        toast(e.message, 'error');
      }
    },
  },
    el('label', { class: 'field' }, 'Name', nameInput),
    el('label', { class: 'field' }, 'Parent', parentSelect),
    el('button', { class: 'primary', type: 'submit' }, 'Create unit'));

  root.append(
    panel('Create org unit', el('div', { class: 'panel-body' }, createForm)),
    panel('Organization hierarchy', el('div', { class: 'panel-body flush' }, tree)),
    documentClassesPanel(),
    ordnungsbegriffTypesPanel(),
    intentsPanel());

  load(tree, parentSelect);
}

async function load(tree, parentSelect) {
  try {
    const orgs = await api.get('/api/v1/orgs');
    parentSelect.replaceChildren(
      el('option', { value: '' }, '(top level)'),
      ...orgs.map((o) => el('option', { value: o.id }, o.name)));
    if (!orgs.length) {
      tree.replaceChildren(empty('No org units visible. A bootstrap admin creates the first top-level unit.'));
      return;
    }
    orgs.sort((a, b) => a.path.localeCompare(b.path));
    tree.replaceChildren(...orgs.map((org) => orgRow(org, () => load(tree, parentSelect))));
  } catch (e) {
    tree.replaceChildren(empty(e.message));
  }
}

function orgRow(org, reload) {
  const depth = Math.max(0, (org.path.match(/\//g) || []).length - 2);
  return el('div', { class: 'org-row' },
    el('span', { class: 'org-indent' }, depth ? '└'.padStart(depth * 2, ' ') : ''),
    el('span', { class: 'org-name' }, org.name),
    el('span', { class: 'spacer' }),
    el('button', { class: 'small-btn', onclick: () => openMembers(org) }, 'Members'),
    el('button', {
      class: 'small-btn',
      onclick: async () => {
        const name = prompt(`Rename "${org.name}" to:`, org.name);
        if (!name || name === org.name) return;
        try {
          await api.put(`/api/v1/orgs/${org.id}`, { name });
          reload();
        } catch (e) { toast(e.message, 'error'); }
      },
    }, 'Rename'),
    el('button', {
      class: 'small-btn danger',
      onclick: async () => {
        if (!confirm(`Delete empty unit "${org.name}"?`)) return;
        try {
          await api.del(`/api/v1/orgs/${org.id}`);
          toast('Unit deleted.', 'ok');
          reload();
        } catch (e) { toast(e.message, 'error'); }
      },
    }, 'Delete'));
}

async function openMembers(org) {
  const list = el('div', { class: 'panel-body flush' });
  const email = el('input', { type: 'email', placeholder: 'user@example.com', required: '' });
  const role = el('select', {}, ROLES.map((r) => el('option', { value: r }, r)));

  const assignForm = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.post(`/api/v1/orgs/${org.id}/members`, { email: email.value, role: role.value });
        toast(`Assigned ${email.value} as ${role.value}.`, 'ok');
        email.value = '';
        refresh();
      } catch (e) {
        toast(e.message, 'error');
      }
    },
  },
    el('label', { class: 'field' }, 'Email', email),
    el('label', { class: 'field' }, 'Role', role),
    el('button', { class: 'primary', type: 'submit' }, 'Assign'));

  async function refresh() {
    try {
      const members = await api.get(`/api/v1/orgs/${org.id}/members`);
      if (!members.length) {
        list.replaceChildren(empty('No direct members.'));
        return;
      }
      list.replaceChildren(...members.map((m) => el('div', { class: 'upload-item' },
        el('span', { class: 'grow' }, m.email),
        el('span', { class: 'badge role' }, m.role),
        el('button', {
          class: 'small-btn danger',
          onclick: async () => {
            if (!confirm(`Remove ${m.email} (${m.role}) from "${org.name}"?`)) return;
            try {
              await api.del(`/api/v1/orgs/${org.id}/members/${m.membershipId}`);
              toast(`Removed ${m.email}.`, 'ok');
              refresh();
            } catch (e) { toast(e.message, 'error'); }
          },
        }, 'Remove'))));
    } catch (e) {
      list.replaceChildren(empty(e.message));
    }
  }

  openModal(
    modalHead(`Members of ${esc(org.name)}`),
    assignForm,
    el('p', { class: 'muted small' },
      'Access to a unit includes all of its sub-units (materialized-path inheritance).'),
    list);
  refresh();
}

// ── Document classes (controlled vocabulary) ────────────────────────────────

function documentClassesPanel() {
  const list = el('div', { class: 'panel-body flush' });
  const name = el('input', { type: 'text', placeholder: 'RECHNUNG', required: '' });
  const description = el('input', { type: 'text', placeholder: 'Short description for the AI', required: '' });

  const form = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.post('/api/v1/document-classes', { name: name.value, description: description.value });
        toast('Document class created.', 'ok');
        name.value = '';
        description.value = '';
        loadClasses(list);
      } catch (e) { toast(e.message, 'error'); }
    },
  },
    el('label', { class: 'field' }, 'Name', name),
    el('label', { class: 'field grow' }, 'Description', description),
    el('button', { class: 'primary', type: 'submit' }, 'Add class'));

  loadClasses(list);
  return panel('Document classes', el('div', {},
    el('div', { class: 'panel-body' }, form), list));
}

async function loadClasses(list) {
  try {
    const classes = await api.get('/api/v1/document-classes');
    if (!classes.length) {
      list.replaceChildren(empty('No document classes. Add one above.'));
      return;
    }
    list.replaceChildren(...classes.map((c) => el('div', { class: 'upload-item' },
      el('span', { class: 'badge role' }, c.name),
      el('span', { class: 'grow muted small' }, c.description),
      el('button', { class: 'small-btn', onclick: () => editClass(c, list) }, 'Edit'),
      el('button', {
        class: 'small-btn danger',
        onclick: async () => {
          if (!confirm(`Delete document class "${c.name}"?`)) return;
          try {
            await api.del(`/api/v1/document-classes/${c.id}`);
            toast('Class deleted.', 'ok');
            loadClasses(list);
          } catch (e) { toast(e.message, 'error'); }
        },
      }, 'Delete'))));
  } catch (e) {
    list.replaceChildren(empty(e.message));
  }
}

function editClass(c, list) {
  const name = el('input', { type: 'text', value: c.name, required: '' });
  const description = el('input', { type: 'text', value: c.description, required: '' });
  const form = el('form', {
    class: 'form-col',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.put(`/api/v1/document-classes/${c.id}`, { name: name.value, description: description.value });
        toast('Class updated.', 'ok');
        closeModal();
        loadClasses(list);
      } catch (e) { toast(e.message, 'error'); }
    },
  },
    el('label', { class: 'field' }, 'Name', name),
    el('label', { class: 'field' }, 'Description', description),
    el('button', { class: 'primary', type: 'submit' }, 'Save'));
  openModal(modalHead(`Edit ${esc(c.name)}`), form);
}

// ── Ordnungsbegriff types ───────────────────────────────────────────────────

function ordnungsbegriffTypesPanel() {
  const list = el('div', { class: 'panel-body flush' });
  const name = el('input', { type: 'text', placeholder: 'Kundennummer', required: '' });
  const description = el('input', { type: 'text', placeholder: 'How the AI recognizes the value', required: '' });

  const form = el('form', {
    class: 'form-row',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.post('/api/v1/ordnungsbegriff-types', { name: name.value, description: description.value });
        toast('Ordnungsbegriff type created.', 'ok');
        name.value = '';
        description.value = '';
        loadOrdnungsbegriffTypes(list);
      } catch (e) { toast(e.message, 'error'); }
    },
  },
    el('label', { class: 'field' }, 'Name', name),
    el('label', { class: 'field grow' }, 'Description', description),
    el('button', { class: 'primary', type: 'submit' }, 'Add type'));

  loadOrdnungsbegriffTypes(list);
  return panel('Ordnungsbegriff types', el('div', {},
    el('div', { class: 'panel-body' }, form), list));
}

async function loadOrdnungsbegriffTypes(list) {
  try {
    const types = await api.get('/api/v1/ordnungsbegriff-types');
    if (!types.length) {
      list.replaceChildren(empty('No Ordnungsbegriff types. The AI extracts only values matching a configured type.'));
      return;
    }
    list.replaceChildren(...types.map((t) => el('div', { class: 'upload-item' },
      el('span', { class: 'badge role' }, t.name),
      el('span', { class: 'grow muted small' },
        t.description + (t.active ? '' : ' (inactive)')),
      el('button', {
        class: 'small-btn',
        onclick: async () => {
          try {
            await api.put(`/api/v1/ordnungsbegriff-types/${t.id}`,
              { name: t.name, description: t.description, active: !t.active });
            toast(t.active ? 'Type deactivated.' : 'Type activated.', 'ok');
            loadOrdnungsbegriffTypes(list);
          } catch (e) { toast(e.message, 'error'); }
        },
      }, t.active ? 'Deactivate' : 'Activate'),
      el('button', { class: 'small-btn', onclick: () => editOrdnungsbegriffType(t, list) }, 'Edit'),
      el('button', {
        class: 'small-btn danger',
        onclick: async () => {
          if (!confirm(`Delete Ordnungsbegriff type "${t.name}"? Values already stored on documents are kept.`)) return;
          try {
            await api.del(`/api/v1/ordnungsbegriff-types/${t.id}`);
            toast('Type deleted.', 'ok');
            loadOrdnungsbegriffTypes(list);
          } catch (e) { toast(e.message, 'error'); }
        },
      }, 'Delete'))));
  } catch (e) {
    list.replaceChildren(empty(e.message));
  }
}

function editOrdnungsbegriffType(t, list) {
  const name = el('input', { type: 'text', value: t.name, required: '' });
  const description = el('input', { type: 'text', value: t.description, required: '' });
  const active = el('input', { type: 'checkbox' });
  active.checked = t.active;
  const form = el('form', {
    class: 'form-col',
    onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.put(`/api/v1/ordnungsbegriff-types/${t.id}`,
          { name: name.value, description: description.value, active: active.checked });
        toast('Type updated.', 'ok');
        closeModal();
        loadOrdnungsbegriffTypes(list);
      } catch (e) { toast(e.message, 'error'); }
    },
  },
    el('label', { class: 'field' }, 'Name', name),
    el('label', { class: 'field' }, 'Description', description),
    el('label', { class: 'field' }, 'Active', active),
    el('p', { class: 'muted small' },
      'Inactive types are dropped from the extraction prompt; already extracted values stay on their documents.'),
    el('button', { class: 'primary', type: 'submit' }, 'Save'));
  openModal(modalHead(`Edit ${esc(t.name)}`), form);
}

// ── AI extraction intents ───────────────────────────────────────────────────

function intentsPanel() {
  const list = el('div', { class: 'panel-body flush' });
  const head = el('button', { class: 'small-btn', onclick: () => editIntent(null, list) }, 'Add intent');
  loadIntents(list);
  return panel('AI intents', el('div', { class: 'panel-body flush' }, list), head);
}

async function loadIntents(list) {
  try {
    const intents = await api.get('/api/v1/intents');
    if (!intents.length) {
      list.replaceChildren(empty('No intents. An intent groups the fields the AI extracts for a kind of document.'));
      return;
    }
    list.replaceChildren(...intents.map((it) => el('div', { class: 'upload-item' },
      el('span', { class: 'grow' },
        el('strong', {}, it.name),
        el('span', { class: 'muted small' }, ` — ${it.fields.length} field${it.fields.length === 1 ? '' : 's'}`)),
      el('button', { class: 'small-btn', onclick: () => editIntent(it, list) }, 'Edit'),
      el('button', {
        class: 'small-btn danger',
        onclick: async () => {
          if (!confirm(`Delete intent "${it.name}"?`)) return;
          try {
            await api.del(`/api/v1/intents/${it.id}`);
            toast('Intent deleted.', 'ok');
            loadIntents(list);
          } catch (e) { toast(e.message, 'error'); }
        },
      }, 'Delete'))));
  } catch (e) {
    list.replaceChildren(empty(e.message));
  }
}

function editIntent(intent, list) {
  const name = el('input', { type: 'text', value: intent?.name ?? '', placeholder: 'Rechnungseingang', required: '' });
  const description = el('input', {
    type: 'text', value: intent?.description ?? '', placeholder: 'When does this intent apply?', required: '',
  });
  const fields = el('div', { class: 'panel-body flush' });

  const addFieldRow = (field) => {
    const fName = el('input', { type: 'text', value: field?.name ?? '', placeholder: 'rechnungsnummer' });
    const fDesc = el('input', { type: 'text', value: field?.description ?? '', placeholder: 'What to extract' });
    const row = el('div', { class: 'form-row' },
      el('label', { class: 'field' }, 'Field', fName),
      el('label', { class: 'field grow' }, 'Description', fDesc),
      el('button', { class: 'small-btn danger', type: 'button', onclick: () => row.remove() }, '✕'));
    row._inputs = () => ({ name: fName.value.trim(), description: fDesc.value.trim() });
    fields.append(row);
  };
  (intent?.fields ?? []).forEach(addFieldRow);

  const form = el('form', {
    class: 'form-col',
    onsubmit: async (event) => {
      event.preventDefault();
      const body = {
        name: name.value,
        description: description.value,
        fields: [...fields.children].map((row) => row._inputs()).filter((f) => f.name || f.description),
      };
      try {
        if (intent) {
          await api.put(`/api/v1/intents/${intent.id}`, body);
        } else {
          await api.post('/api/v1/intents', body);
        }
        toast('Intent saved.', 'ok');
        closeModal();
        loadIntents(list);
      } catch (e) { toast(e.message, 'error'); }
    },
  },
    el('label', { class: 'field' }, 'Name', name),
    el('label', { class: 'field' }, 'Description', description),
    el('p', { class: 'muted small' }, 'Fields — each becomes a JSON key the AI extracts for this intent.'),
    fields,
    el('button', { class: 'small-btn', type: 'button', onclick: () => addFieldRow(null) }, 'Add field'),
    el('button', { class: 'primary', type: 'submit' }, 'Save intent'));

  openModal(modalHead(intent ? `Edit ${esc(intent.name)}` : 'New intent'), form);
}
