<script lang="ts">
  import '../app.css';
  import { auth, logout } from '$lib/stores/auth';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { toasts } from '$lib/stores/toast';

  const nav = [
    { href: '/dashboard', label: 'Dashboard' },
    { href: '/activities', label: 'Activities' },
    { href: '/analytics', label: 'Analytics' },
    { href: '/recommendations', label: 'Recommendations' },
    { href: '/notifications', label: 'Notifications' },
    { href: '/coach', label: 'Coach' }
  ];
</script>

<div class="min-h-screen flex flex-col">
  <header class="bg-indigo-600 text-white">
    <div class="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
      <a href="/" class="text-xl font-bold">Activityual</a>
      {#if $auth}
        <nav class="hidden md:flex gap-4 text-sm">
          {#each nav as n}
            <a href={n.href} class:underline={$page.url.pathname.startsWith(n.href)}>{n.label}</a>
          {/each}
        </nav>
        <div class="flex items-center gap-3 text-sm">
          <span class="hidden sm:inline">Hi, {$auth.displayName}</span>
          <button class="bg-white/10 px-3 py-1 rounded" on:click={() => { logout(); goto('/login'); }}>Logout</button>
        </div>
      {:else}
        <div class="text-sm flex gap-3">
          <a href="/login">Login</a>
          <a href="/register">Register</a>
        </div>
      {/if}
    </div>
    {#if $auth}
      <nav class="md:hidden flex gap-3 px-4 pb-3 text-sm overflow-x-auto">
        {#each nav as n}
          <a href={n.href} class="whitespace-nowrap" class:underline={$page.url.pathname.startsWith(n.href)}>{n.label}</a>
        {/each}
      </nav>
    {/if}
  </header>

  <main class="flex-1 max-w-6xl w-full mx-auto px-4 py-6">
    <slot />
  </main>

  <footer class="text-center text-xs text-slate-500 py-4">© Activityual</footer>

  <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-xs">
    {#each $toasts as t (t.id)}
      <div
        class="rounded shadow px-3 py-2 text-sm text-white"
        class:bg-rose-600={t.kind === 'error'}
        class:bg-emerald-600={t.kind === 'success'}
        class:bg-slate-700={t.kind === 'info'}
      >{t.text}</div>
    {/each}
  </div>
</div>

