<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';
  import { toast } from '$lib/stores/toast';
  let items: any[] = [];
  let loading = true;
  let filter: 'all' | 'unread' = 'all';
  async function load() {
    if (!$auth) { goto('/login'); return; }
    loading = true;
    try { items = await api.notifications($auth.userId); } catch {}
    loading = false;
  }
  onMount(load);
  async function markRead(id: string) {
    try {
      await api.markNotificationRead(id);
      items = items.map((n) => (n.id === id ? { ...n, readFlag: true } : n));
      toast.success('Marked as read');
    } catch {}
  }
  async function markAll() {
    const unread = items.filter((n) => !n.readFlag);
    for (const n of unread) {
      try { await api.markNotificationRead(n.id); } catch {}
    }
    items = items.map((n) => ({ ...n, readFlag: true }));
    if (unread.length) toast.success(`Marked ${unread.length} as read`);
  }
  $: visible = filter === 'unread' ? items.filter((n) => !n.readFlag) : items;
  $: unreadCount = items.filter((n) => !n.readFlag).length;
</script>
<div class="flex items-center justify-between mb-4">
  <h1 class="text-2xl font-semibold">Notifications {#if unreadCount}<span class="ml-2 text-sm bg-rose-600 text-white rounded-full px-2 py-0.5 align-middle">{unreadCount} unread</span>{/if}</h1>
  <div class="flex gap-2">
    <select class="border rounded px-2 py-1 text-sm" bind:value={filter}>
      <option value="all">All</option>
      <option value="unread">Unread</option>
    </select>
    <button class="bg-indigo-600 text-white text-sm rounded px-3 py-1 disabled:opacity-40" on:click={markAll} disabled={unreadCount === 0}>Mark all read</button>
  </div>
</div>
{#if loading}
  <p class="text-slate-500">Loading…</p>
{:else if visible.length === 0}
  <p class="text-slate-500">Nothing here yet — log an activity to generate a notification.</p>
{:else}
  <ul class="bg-white rounded shadow divide-y">
    {#each visible as n (n.id)}
      <li class="flex items-start justify-between p-3" class:bg-slate-50={n.readFlag}>
        <div>
          <p class="text-sm" class:font-semibold={!n.readFlag}>{n.message}</p>
          <p class="text-xs text-slate-500">{new Date(n.createdAt).toLocaleString()}</p>
        </div>
        {#if !n.readFlag}
          <button class="text-xs text-indigo-600 underline" on:click={() => markRead(n.id)}>Mark read</button>
        {:else}
          <span class="text-xs text-slate-400">read</span>
        {/if}
      </li>
    {/each}
  </ul>
{/if}
