<script lang="ts">
  import { api } from '$lib/api';
  import { auth } from '$lib/stores/auth';
  import { goto } from '$app/navigation';
  import { toast } from '$lib/stores/toast';

  let email = '', password = '', submitting = false;

  async function submit() {
    submitting = true;
    try {
      const r = await api.login({ email, password });
      auth.set({ userId: r.userId, email: r.email, displayName: r.displayName, accessToken: r.accessToken, refreshToken: r.refreshToken });
      toast.success(`Welcome back, ${r.displayName}`);
      goto('/dashboard');
    } catch {} finally { submitting = false; }
  }
</script>

<div class="max-w-sm mx-auto bg-white p-6 rounded shadow mt-10">
  <h1 class="text-xl font-semibold mb-4">Welcome back</h1>
  <form on:submit|preventDefault={submit} class="space-y-3">
    <input class="w-full border rounded px-3 py-2" type="email" placeholder="Email" bind:value={email} required />
    <input class="w-full border rounded px-3 py-2" type="password" placeholder="Password" bind:value={password} required />
    <button class="w-full bg-indigo-600 text-white py-2 rounded disabled:opacity-50" disabled={submitting}>{submitting ? 'Signing in…' : 'Login'}</button>
  </form>
  <p class="text-sm mt-3">No account? <a href="/register" class="text-indigo-600">Register</a></p>
</div>

