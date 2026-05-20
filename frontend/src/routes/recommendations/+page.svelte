<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';
  import { toast } from '$lib/stores/toast';
  let recos: any[] = [];
  let activities: any[] = [];
  let selectedActivityId = '';
  let heat: Record<string, Record<string, number>> = {};
  let loading = true;
  let heatLoading = false;
  const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  async function load() {
    if (!$auth) { goto('/login'); return; }
    loading = true;
    try {
      [recos, activities] = await Promise.all([
        api.recommendations($auth.userId),
        api.listActivities()
      ]);
      if (activities.length && !selectedActivityId) {
        selectedActivityId = activities[0].id;
        await loadHeat();
      }
    } catch {}
    loading = false;
  }
  onMount(load);
  async function loadHeat() {
    if (!selectedActivityId || !$auth) return;
    heatLoading = true;
    try {
      heat = await api.heatmap($auth.userId, selectedActivityId);
    } catch { heat = {}; }
    heatLoading = false;
  }
  async function decide(id: string, accepted: boolean) {
    try {
      await api.accept({ recommendationId: id, accepted });
      toast.success(accepted ? 'Recommendation accepted' : 'Recommendation dismissed');
      await load();
    } catch {}
  }
  function cellFor(status: 'done' | 'missed', dow: number): number {
    const m = heat[status] ?? {};
    return Math.round(m[String(dow)] ?? 0);
  }
  function ratioFor(dow: number): number {
    const d = cellFor('done', dow);
    const m = cellFor('missed', dow);
    const total = d + m;
    return total === 0 ? -1 : d / total;
  }
  function bg(ratio: number): string {
    if (ratio < 0) return 'bg-slate-100';
    if (ratio >= 0.8) return 'bg-emerald-600 text-white';
    if (ratio >= 0.6) return 'bg-emerald-400 text-white';
    if (ratio >= 0.4) return 'bg-amber-300';
    if (ratio >= 0.2) return 'bg-rose-300';
    return 'bg-rose-500 text-white';
  }
</script>
<h1 class="text-2xl font-semibold mb-4">Personalized Recommendations</h1>
<section class="grid md:grid-cols-2 gap-4 mb-6">
  <div>
    <h2 class="font-semibold mb-2">Suggestions</h2>
    {#if loading}
      <p class="text-slate-500">Loading…</p>
    {:else if recos.length === 0}
      <p class="text-slate-500">No recommendations yet — log a few activities so the engine can learn your patterns.</p>
    {:else}
      <div class="grid gap-3">
        {#each recos as r (r.id)}
          <article class="bg-white rounded shadow p-4">
            <div class="flex justify-between items-start mb-2">
              <span class="text-xs uppercase tracking-wide text-indigo-600 font-semibold">{r.kind}</span>
              <span class="text-xs text-slate-500">conf {(r.confidence * 100).toFixed(0)}%</span>
            </div>
            <h3 class="font-semibold">{r.activityTitle}</h3>
            <p class="text-sm mt-1">{r.message}</p>
            <div class="flex gap-2 mt-3">
              <button class="flex-1 bg-emerald-600 text-white py-1 rounded text-sm" on:click={() => decide(r.id, true)}>Accept</button>
              <button class="flex-1 bg-slate-200 py-1 rounded text-sm" on:click={() => decide(r.id, false)}>Dismiss</button>
            </div>
          </article>
        {/each}
      </div>
    {/if}
  </div>
  <div>
    <h2 class="font-semibold mb-2">Completion heat-map</h2>
    <div class="bg-white p-4 rounded shadow">
      <div class="flex items-center gap-2 mb-3">
        <label class="text-sm">Activity</label>
        <select class="flex-1 border rounded px-2 py-1 text-sm" bind:value={selectedActivityId} on:change={loadHeat}>
          {#each activities as a}<option value={a.id}>{a.title}</option>{/each}
        </select>
      </div>
      {#if heatLoading}
        <p class="text-slate-500 text-sm">Loading…</p>
      {:else if !selectedActivityId}
        <p class="text-slate-500 text-sm">Add an activity first.</p>
      {:else}
        <div>
          <p class="text-xs text-slate-500 mb-1">Completion rate by day of week</p>
          <div class="grid grid-cols-7 gap-1 text-center text-xs">
            {#each DAY_LABELS as label, idx}
              <div class="text-slate-500">{label}</div>
            {/each}
            {#each DAY_LABELS as _, idx}
              {@const dow = idx + 1}
              {@const r = ratioFor(dow)}
              <div class="rounded py-3 {bg(r)}">
                {#if r < 0}–{:else}{Math.round(r * 100)}%{/if}
              </div>
            {/each}
          </div>
          <p class="text-xs text-slate-500 mt-4 mb-1">Done vs Missed counts</p>
          <table class="w-full text-xs">
            <thead><tr class="text-slate-500"><th class="text-left">Day</th><th>Done</th><th>Missed</th></tr></thead>
            <tbody>
              {#each DAY_LABELS as label, idx}
                {@const dow = idx + 1}
                <tr class="border-t">
                  <td class="py-1">{label}</td>
                  <td class="text-center text-emerald-700">{cellFor('done', dow)}</td>
                  <td class="text-center text-rose-700">{cellFor('missed', dow)}</td>
                </tr>
              {/each}
            </tbody>
          </table>
          <p class="text-[10px] text-slate-400 mt-2">Tip: log more activities to fill the map. Empty cells show "–".</p>
        </div>
      {/if}
    </div>
  </div>
</section>
