<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';

  let analytics: any = null;
  let recos: any[] = [];
  let recent: any[] = [];
  let loading = true, error = '';

  onMount(async () => {
    if (!$auth) { goto('/login'); return; }
    try {
      [analytics, recos, recent] = await Promise.all([
        api.analytics($auth.userId),
        api.recommendations($auth.userId),
        api.recent()
      ]);
    } catch (e: any) { error = e.message; }
    loading = false;
  });

  $: topNudge = recos?.find(r => !r.accepted && !r.dismissed && r.confidence >= 0.6);
</script>

{#if loading}
  <p>Loading…</p>
{:else if error}
  <p class="text-red-600">{error}</p>
{:else}
  {#if topNudge}
    <div class="bg-amber-100 border-l-4 border-amber-500 p-3 rounded mb-4 flex items-start justify-between">
      <div>
        <strong>Nudge:</strong> {topNudge.message}
      </div>
      <a href="/recommendations" class="text-sm text-amber-800 underline ml-3">View all</a>
    </div>
  {/if}

  <div class="grid md:grid-cols-4 gap-4 mb-6">
    <div class="bg-white p-4 rounded shadow">
      <p class="text-xs text-slate-500">Total logs</p>
      <p class="text-2xl font-bold">{analytics?.total ?? 0}</p>
    </div>
    <div class="bg-white p-4 rounded shadow">
      <p class="text-xs text-slate-500">Done</p>
      <p class="text-2xl font-bold text-emerald-600">{analytics?.done ?? 0}</p>
    </div>
    <div class="bg-white p-4 rounded shadow">
      <p class="text-xs text-slate-500">Missed</p>
      <p class="text-2xl font-bold text-rose-600">{analytics?.missed ?? 0}</p>
    </div>
    <div class="bg-white p-4 rounded shadow">
      <p class="text-xs text-slate-500">Consistency</p>
      <p class="text-2xl font-bold">{(analytics?.consistencyPct ?? 0).toFixed(0)}%</p>
    </div>
  </div>

  <div class="grid md:grid-cols-2 gap-4">
    <section class="bg-white p-4 rounded shadow">
      <h2 class="font-semibold mb-2">Most consistent</h2>
      <ul class="text-sm">
        {#each analytics?.mostConsistent ?? [] as s}
          <li class="flex justify-between border-b py-1"><span>{s.title}</span><span>{s.rate.toFixed(0)}%</span></li>
        {/each}
      </ul>
    </section>
    <section class="bg-white p-4 rounded shadow">
      <h2 class="font-semibold mb-2">Most missed</h2>
      <ul class="text-sm">
        {#each analytics?.mostMissed ?? [] as s}
          <li class="flex justify-between border-b py-1"><span>{s.title}</span><span>{s.missed} miss</span></li>
        {/each}
      </ul>
    </section>
  </div>

  <section class="bg-white p-4 rounded shadow mt-4">
    <h2 class="font-semibold mb-2">Recent activity</h2>
    <ul class="text-sm divide-y">
      {#each recent.slice(0, 10) as r}
        <li class="py-1 flex justify-between">
          <span>{r.activityTitle}</span>
          <span class="text-slate-500">{r.status} · {new Date(r.occurredAt).toLocaleString()}</span>
        </li>
      {/each}
      {#if recent.length === 0}<li class="py-1 text-slate-500">No activity yet — log your first one!</li>{/if}
    </ul>
  </section>
{/if}

