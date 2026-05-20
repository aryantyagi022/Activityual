<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';
  import { toast } from '$lib/stores/toast';
  let items: any[] = [];
  let recentLogs: any[] = [];
  let categoryFilter = '';
  let statusFilter = '';
  let editingId: string | null = null;
  let draft = { title: '', category: 'reading', frequency: 'DAILY', notes: '' };
  let form = { title: '', category: 'reading', frequency: 'DAILY', notes: '' };
  let loading = true;
  async function load() {
    if (!$auth) { goto('/login'); return; }
    loading = true;
    try {
      [items, recentLogs] = await Promise.all([api.listActivities(), api.recent()]);
    } catch {}
    loading = false;
  }
  onMount(load);
  async function add() {
    try {
      await api.createActivity(form);
      form = { title: '', category: 'reading', frequency: 'DAILY', notes: '' };
      toast.success('Activity created');
      await load();
    } catch {}
  }
  function startEdit(a: any) {
    editingId = a.id;
    draft = { title: a.title, category: a.category, frequency: a.frequency, notes: a.notes ?? '' };
  }
  function cancelEdit() { editingId = null; }
  async function saveEdit(id: string) {
    try {
      await api.updateActivity(id, draft);
      editingId = null;
      toast.success('Saved');
      await load();
    } catch {}
  }
  async function remove(id: string) {
    if (!confirm('Delete this activity?')) return;
    try {
      await api.deleteActivity(id);
      toast.success('Activity deleted');
      await load();
    } catch {}
  }
  async function quickLog(a: any, status: string) {
    try {
      await api.log({ activityId: a.id, activityTitle: a.title, activityCategory: a.category, status });
      toast.success(`Logged "${status}" for ${a.title}`);
      recentLogs = await api.recent();
    } catch {}
  }
  $: latestStatusFor = (id: string) => {
    const hit = recentLogs.find((l) => l.activityId === id);
    return hit?.status ?? '';
  };
  $: filtered = items.filter((i) => {
    if (categoryFilter && i.category !== categoryFilter) return false;
    if (statusFilter) {
      const last = latestStatusFor(i.id);
      if (statusFilter === 'unlogged' && last) return false;
      if (statusFilter !== 'unlogged' && last !== statusFilter) return false;
    }
    return true;
  });
  $: categories = Array.from(new Set(items.map((i) => i.category)));
</script>
<h1 class="text-2xl font-semibold mb-4">My Activities</h1>
<section class="bg-white p-4 rounded shadow mb-6">
  <h2 class="font-semibold mb-2">Add new</h2>
  <form on:submit|preventDefault={add} class="grid sm:grid-cols-5 gap-2">
    <input class="border rounded px-2 py-1 sm:col-span-2" placeholder="Title (e.g. Read 20 pages)" bind:value={form.title} required />
    <select class="border rounded px-2 py-1" bind:value={form.category}>
      <option>reading</option><option>workout</option><option>meditation</option><option>sleep</option><option>other</option>
    </select>
    <select class="border rounded px-2 py-1" bind:value={form.frequency}>
      <option value="DAILY">Daily</option>
      <option value="WEEKLY_3">3×/week</option>
      <option value="WEEKLY_5">5×/week</option>
      <option value="WEEKLY_7">Every day</option>
    </select>
    <button class="bg-indigo-600 text-white rounded px-3 py-1">Add</button>
    <input class="border rounded px-2 py-1 sm:col-span-5" placeholder="Notes (optional)" bind:value={form.notes} />
  </form>
</section>
<div class="flex flex-wrap items-center gap-3 mb-3">
  <div class="flex items-center gap-2">
    <label class="text-sm">Category</label>
    <select class="border rounded px-2 py-1" bind:value={categoryFilter}>
      <option value="">All</option>
      {#each categories as c}<option>{c}</option>{/each}
    </select>
  </div>
  <div class="flex items-center gap-2">
    <label class="text-sm">Status</label>
    <select class="border rounded px-2 py-1" bind:value={statusFilter}>
      <option value="">Any</option>
      <option value="done">Last log: done</option>
      <option value="completed">Last log: completed</option>
      <option value="missed">Last log: missed</option>
      <option value="unlogged">No logs yet</option>
    </select>
  </div>
</div>
{#if loading}
  <p class="text-slate-500">Loading…</p>
{:else}
<div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
  {#each filtered as a (a.id)}
    <article class="bg-white rounded shadow p-3 flex flex-col justify-between">
      {#if editingId === a.id}
        <form on:submit|preventDefault={() => saveEdit(a.id)} class="space-y-2">
          <input class="w-full border rounded px-2 py-1" bind:value={draft.title} required />
          <div class="flex gap-2">
            <select class="flex-1 border rounded px-2 py-1" bind:value={draft.category}>
              <option>reading</option><option>workout</option><option>meditation</option><option>sleep</option><option>other</option>
            </select>
            <select class="flex-1 border rounded px-2 py-1" bind:value={draft.frequency}>
              <option value="DAILY">Daily</option>
              <option value="WEEKLY_3">3×/week</option>
              <option value="WEEKLY_5">5×/week</option>
              <option value="WEEKLY_7">Every day</option>
            </select>
          </div>
          <input class="w-full border rounded px-2 py-1" placeholder="Notes" bind:value={draft.notes} />
          <div class="flex gap-2">
            <button type="submit" class="flex-1 bg-emerald-600 text-white py-1 rounded text-sm">Save</button>
            <button type="button" class="flex-1 bg-slate-200 py-1 rounded text-sm" on:click={cancelEdit}>Cancel</button>
          </div>
        </form>
      {:else}
        <div>
          <h3 class="font-semibold">{a.title}</h3>
          <p class="text-xs text-slate-500">{a.category} · {a.frequency}</p>
          {#if latestStatusFor(a.id)}
            <p class="text-xs mt-1">Last log:
              <span
                class="inline-block px-1.5 rounded text-white"
                class:bg-emerald-600={latestStatusFor(a.id) === 'done' || latestStatusFor(a.id) === 'completed'}
                class:bg-rose-600={latestStatusFor(a.id) === 'missed'}
              >{latestStatusFor(a.id)}</span>
            </p>
          {/if}
          {#if a.notes}<p class="text-sm mt-1">{a.notes}</p>{/if}
        </div>
        <div class="flex gap-2 mt-3">
          <button class="flex-1 bg-emerald-600 text-white py-1 rounded text-sm" on:click={() => quickLog(a, 'done')}>Done</button>
          <button class="flex-1 bg-blue-600 text-white py-1 rounded text-sm" on:click={() => quickLog(a, 'completed')}>Completed</button>
          <button class="flex-1 bg-rose-600 text-white py-1 rounded text-sm" on:click={() => quickLog(a, 'missed')}>Missed</button>
        </div>
        <div class="flex gap-2 mt-2">
          <button class="flex-1 bg-slate-200 py-1 rounded text-xs" on:click={() => startEdit(a)}>Edit</button>
          <button class="flex-1 bg-slate-200 py-1 rounded text-xs text-rose-600" on:click={() => remove(a.id)}>Delete</button>
        </div>
      {/if}
    </article>
  {/each}
  {#if filtered.length === 0}
    <p class="text-slate-500 sm:col-span-2 lg:col-span-3">No activities match the current filters.</p>
  {/if}
</div>
{/if}
