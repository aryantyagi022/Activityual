import { browser } from '$app/environment';
import { writable, type Writable } from 'svelte/store';

export interface AuthUser {
  userId: string;
  email: string;
  displayName: string;
  accessToken: string;
  refreshToken: string;
}

const KEY = 'activityual.auth';

function load(): AuthUser | null {
  if (!browser) return null;
  const raw = localStorage.getItem(KEY);
  return raw ? (JSON.parse(raw) as AuthUser) : null;
}

export const auth: Writable<AuthUser | null> = writable(load());

auth.subscribe((v) => {
  if (!browser) return;
  if (v) localStorage.setItem(KEY, JSON.stringify(v));
  else localStorage.removeItem(KEY);
});

export function logout() {
  auth.set(null);
}

