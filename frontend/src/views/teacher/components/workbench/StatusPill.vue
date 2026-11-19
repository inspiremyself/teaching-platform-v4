<script setup lang="ts">
defineProps<{ tone: 'draft' | 'published' | 'closed' | 'warning'; label: string }>()
</script>

<template>
  <span class="status-pill" :class="`status-pill--${tone}`">
    <span class="status-pill__dot" />
    {{ label }}
  </span>
</template>
