// Application shell: sign-in overlay, sidebar navigation, view lifecycle.
// Views are plain ES modules exposing { title, render(root, ctx) }.

import { api, currentUserEmail, signIn, signOut } from './api.js';
import { resetConfig } from './config.js';
import { toast } from './ui.js';
import * as documents from './views/documents.js';
import * as upload from './views/upload.js';
import * as search from './views/search.js';
import * as akten from './views/akten.js';
import * as jobs from './views/jobs.js';
import * as admin from './views/admin.js';
import * as feed from './views/feed.js';

const VIEWS = { documents, upload, search, akten, jobs, admin, feed };

const root = document.getElementById('view-root');
const viewTitle = document.getElementById('view-title');
let timers = [];

// per-view scheduler: pending polls die when the user navigates away
const ctx = {
  schedule(fn, delay) {
    timers.push(setTimeout(fn, delay));
  },
};

function show(name) {
  timers.forEach(clearTimeout);
  timers = [];
  document.querySelectorAll('#nav button').forEach((b) =>
    b.classList.toggle('active', b.dataset.view === name));
  const view = VIEWS[name];
  viewTitle.textContent = view.title;
  document.getElementById('view-actions').replaceChildren();
  root.replaceChildren();
  Promise.resolve(view.render(root, ctx)).catch((e) => toast(e.message, 'error'));
}

document.querySelectorAll('#nav button').forEach((btn) =>
  btn.addEventListener('click', () => {
    show(btn.dataset.view);
    setNavOpen(false);
  }));

// ---- mobile navigation (off-canvas sidebar) ---------------------------
const shell = document.querySelector('.shell');
const navToggle = document.getElementById('nav-toggle');
const sidebarBackdrop = document.getElementById('sidebar-backdrop');

function setNavOpen(open) {
  shell.classList.toggle('nav-open', open);
  navToggle.setAttribute('aria-expanded', String(open));
  sidebarBackdrop.hidden = !open;
}

navToggle.addEventListener('click', () => setNavOpen(!shell.classList.contains('nav-open')));
sidebarBackdrop.addEventListener('click', () => setNavOpen(false));

// ---- sign-in ---------------------------------------------------------
const overlay = document.getElementById('signin-overlay');

document.getElementById('signin-form').addEventListener('submit', (event) => {
  event.preventDefault();
  signIn(document.getElementById('signin-email').value.trim());
  overlay.classList.add('hidden');
  boot();
});

document.getElementById('switch-user').addEventListener('click', () => {
  signOut();
  resetConfig();
  setNavOpen(false);   // the sidebar would shadow the sign-in overlay on mobile
  overlay.classList.remove('hidden');
  document.getElementById('signin-email').focus();
});

async function boot() {
  document.getElementById('user-email').textContent = currentUserEmail();
  try {
    const me = await api.get('/api/v1/users/me');
    const roles = me.memberships.map((m) => m.role);
    document.getElementById('user-role').textContent =
      me.admin ? 'Administrator' : (roles.length ? [...new Set(roles)].join(' · ') : 'no memberships yet');
  } catch (e) {
    document.getElementById('user-role').textContent = '';
    if (e.status === 401) {
      overlay.classList.remove('hidden');
      return;
    }
    toast(e.message, 'error');
  }
  show('documents');
}

if (currentUserEmail()) {
  boot();
} else {
  overlay.classList.remove('hidden');
}
