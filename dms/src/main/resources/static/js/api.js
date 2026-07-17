// REST transport: native fetch against the boundary, one origin, no CORS.
// In dev security mode the signed-in email travels as X-Dev-User; with OIDC
// the platform injects the bearer token instead.

const state = { user: localStorage.getItem('dms.devUser') || '' };

export function currentUserEmail() {
  return state.user;
}

export function signIn(email) {
  state.user = email;
  localStorage.setItem('dms.devUser', email);
}

export function signOut() {
  state.user = '';
  localStorage.removeItem('dms.devUser');
}

function headers(extra = {}) {
  const h = { ...extra };
  if (state.user) h['X-Dev-User'] = state.user;
  return h;
}

async function handle(response) {
  if (response.status === 204) return null;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.error || `HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return body;
}

export const api = {
  get: (url) => fetch(url, { headers: headers() }).then(handle),
  post: (url, body) => fetch(url, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(handle),
  put: (url, body) => fetch(url, {
    method: 'PUT',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(handle),
  del: (url) => fetch(url, { method: 'DELETE', headers: headers() }).then(handle),
  upload: (url, formData) => fetch(url, {
    method: 'POST',
    headers: headers(),
    body: formData,
  }).then(handle),

  // binary download as an object URL, so previews work with header auth
  blobUrl: async (url) => {
    const response = await fetch(url, { headers: headers() });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return URL.createObjectURL(await response.blob());
  },
};
