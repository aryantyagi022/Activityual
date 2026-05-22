import { get } from 'svelte/store';
import { auth } from '$lib/stores/auth';
import { toast } from '$lib/stores/toast';
import { env } from '$env/dynamic/public';

const BASE = env.PUBLIC_API_BASE || 'http://localhost:8080';

async function request<T>(method: string, path: string, body?: unknown, requireAuth = true): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (requireAuth) {
    const u = get(auth);
    if (!u) throw new Error('Not authenticated');
    headers['Authorization'] = `Bearer ${u.accessToken}`;
  }
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });
  } catch (e: any) {
    toast.error('Network error — is the API reachable?');
    throw e;
  }

  if (res.status === 401) {
    auth.set(null);
    toast.error('Session expired. Please sign in again.');
    if (typeof window !== 'undefined') window.location.assign('/login');
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    const raw = await res.text();
    let msg = `${res.status} ${res.statusText}`;
    try {
      const parsed = JSON.parse(raw);
      msg = parsed.message || parsed.error || msg;
    } catch {
      if (raw) msg = raw;
    }
    toast.error(msg);
    throw new Error(msg);
  }

  const ct = res.headers.get('content-type') ?? '';
  if (res.status === 204) return undefined as unknown as T;
  return (ct.includes('json') ? await res.json() : (await res.text() as unknown)) as T;
}

export const api = {

  register: (b: { email: string; password: string; displayName: string }) =>
    request<any>('POST', '/auth/register', b, false),
  login: (b: { email: string; password: string }) =>
    request<any>('POST', '/auth/login', b, false),

  listActivities: () => request<any[]>('GET', '/activities'),
  createActivity: (b: any) => request<any>('POST', '/activities', b),
  updateActivity: (id: string, b: any) => request<any>('PUT', `/activities/${id}`, b),
  deleteActivity: (id: string) => request<void>('DELETE', `/activities/${id}`),

  log: (b: any) => request<any>('POST', '/logs', b),
  dueToday: () => request<any[]>('GET', '/logs/due-today'),
  dueWeek: () => request<any[]>('GET', '/logs/due-week'),
  recent: () => request<any[]>('GET', '/logs/recent'),

  analytics: (userId: string) => request<any>('GET', `/analytics/${userId}`),

  ask: (question: string) => request<{ answer: string; contextChunks: string[] }>('POST', '/coach/ask', { question }),

  recommendations: (userId: string) => request<any[]>('GET', `/recommendations/${userId}`),
  accept: (b: { recommendationId: string; accepted: boolean }) =>
    request<any>('POST', '/recommendations/accept', b),
  heatmap: (userId: string, activityId: string) =>
    request<Record<string, Record<string, number>>>('GET', `/recommendations/${userId}/heatmap/${activityId}`),

  notifications: (userId: string) => request<any[]>('GET', `/notifications/${userId}`),
  markNotificationRead: (id: string) => request<any>('POST', `/notifications/${id}/read`)
};

