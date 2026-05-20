<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';
  import Chart from 'chart.js/auto';

  let analytics: any = null;
  let canvas: HTMLCanvasElement;
  let chart: Chart | null = null;

  onMount(async () => {
    if (!$auth) { goto('/login'); return; }
    analytics = await api.analytics($auth.userId);
    const labels = (analytics.mostConsistent ?? []).map((s: any) => s.title);
    const data = (analytics.mostConsistent ?? []).map((s: any) => s.rate);
    chart = new Chart(canvas, {
      type: 'bar',
      data: { labels, datasets: [{ label: 'Consistency %', data, backgroundColor: '#4f46e5' }] },
      options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, max: 100 } } }
    });
  });
</script>

<h1 class="text-2xl font-semibold mb-4">Analytics</h1>

<div class="grid md:grid-cols-3 gap-4 mb-4">
  <div class="bg-white p-4 rounded shadow"><p class="text-xs text-slate-500">Total logs</p><p class="text-2xl font-bold">{analytics?.total ?? 0}</p></div>
  <div class="bg-white p-4 rounded shadow"><p class="text-xs text-slate-500">Consistency</p><p class="text-2xl font-bold">{(analytics?.consistencyPct ?? 0).toFixed(1)}%</p></div>
  <div class="bg-white p-4 rounded shadow"><p class="text-xs text-slate-500">Missed</p><p class="text-2xl font-bold text-rose-600">{analytics?.missed ?? 0}</p></div>
</div>

<div class="bg-white p-4 rounded shadow h-80">
  <canvas bind:this={canvas}></canvas>
</div>

{#if analytics}
<section class="bg-white p-4 rounded shadow mt-4">
  <h2 class="font-semibold mb-2">Streaks</h2>
  <ul class="text-sm">
    {#each Object.entries(analytics.streaks ?? {}) as [id, count]}
      <li class="flex justify-between border-b py-1"><span class="font-mono text-xs">{id.slice(0,8)}…</span><span>🔥 {count} days</span></li>
    {/each}
  </ul>
</section>
{/if}

