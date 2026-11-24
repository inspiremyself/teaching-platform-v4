<template>
  <span class="app-meta-chip" :class="{ 'app-meta-chip--accent': tone === 'accent' }">
    <strong v-if="value" class="app-meta-chip__value">{{ value }}</strong>
    <span v-if="label" class="app-meta-chip__label">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string;
    value?: string;
    tone?: 'neutral' | 'accent';
  }>(),
  { tone: 'neutral' },
);
</script>
