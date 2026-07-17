// Deployment configuration (controlled vocabulary, AI status) — fetched
// once per session from the backend instead of hardcoding class lists.

import { api } from './api.js';

let cached = null;

export async function getConfig() {
  if (!cached) {
    cached = await api.get('/api/v1/config');
  }
  return cached;
}

export function resetConfig() {
  cached = null;
}
