<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { api } from '$lib/api';
  import { goto } from '$app/navigation';

  type Msg = { role: 'user' | 'coach'; text: string; chunks?: string[] };

  let messages: Msg[] = [];
  let input = '';
  let loading = false;

  onMount(() => { if (!$auth) goto('/login'); });

  async function send() {
    if (!input.trim()) return;
    const q = input;
    input = '';
    messages = [...messages, { role: 'user', text: q }];
    loading = true;
    try {
      const r = await api.ask(q);
      messages = [...messages, { role: 'coach', text: r.answer, chunks: r.contextChunks }];
    } catch {}
    loading = false;
  }
</script>

<h1 class="text-2xl font-semibold mb-4">AI Activity Coach</h1>

<div class="grid md:grid-cols-3 gap-4">
  <div class="md:col-span-2 bg-white rounded shadow p-4 flex flex-col h-[70vh]">
    <div class="flex-1 overflow-y-auto space-y-3 pr-1">
      {#each messages as m}
        <div class:text-right={m.role === 'user'}>
          <div class="inline-block max-w-[90%] px-3 py-2 rounded-lg text-sm"
               class:bg-indigo-600={m.role === 'user'} class:text-white={m.role === 'user'}
               class:bg-slate-100={m.role === 'coach'}>
            {m.text}
          </div>
        </div>
      {/each}
      {#if loading}<p class="text-slate-400 text-sm">Coach is thinking…</p>{/if}
    </div>
    <form on:submit|preventDefault={send} class="mt-3 flex gap-2">
      <input class="flex-1 border rounded px-3 py-2" placeholder="Ask: Why do I keep missing my workout?" bind:value={input} />
      <button class="bg-indigo-600 text-white px-4 rounded" disabled={loading}>Send</button>
    </form>
  </div>

  <aside class="bg-white rounded shadow p-4 h-[70vh] overflow-y-auto">
    <h2 class="font-semibold mb-2 text-sm">Retrieved context</h2>
    {#if messages.length === 0 || !messages[messages.length-1].chunks}
      <p class="text-xs text-slate-500">Your activity log snippets used to ground the latest answer will appear here.</p>
    {:else}
      <ul class="text-xs space-y-2">
        {#each messages[messages.length-1].chunks ?? [] as c}
          <li class="border-l-2 border-indigo-400 pl-2">{c}</li>
        {/each}
      </ul>
    {/if}
  </aside>
</div>

