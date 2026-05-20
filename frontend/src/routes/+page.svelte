<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/stores/auth';
  import { goto } from '$app/navigation';
  onMount(() => goto($auth ? '/dashboard' : '/login'));
</script>

<p class="text-center text-slate-500 mt-20">Loading…</p>

