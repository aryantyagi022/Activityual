import { writable } from 'svelte/store';
export type Toast = {
  id: number;
  kind: 'info' | 'success' | 'error';
  text: string;
};
const _toasts = writable<Toast[]>([]);
let nextId = 1;
export const toasts = { subscribe: _toasts.subscribe };
export function pushToast(kind: Toast['kind'], text: string, ttlMs = 3500) {
  const id = nextId++;
  _toasts.update((arr) => [...arr, { id, kind, text }]);
  setTimeout(() => {
    _toasts.update((arr) => arr.filter((t) => t.id !== id));
  }, ttlMs);
}
export const toast = {
  info:    (m: string) => pushToast('info', m),
  success: (m: string) => pushToast('success', m),
  error:   (m: string) => pushToast('error', m)
};
