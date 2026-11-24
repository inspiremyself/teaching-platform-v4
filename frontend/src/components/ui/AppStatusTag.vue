<template>
  <el-tag
    class="app-status-tag"
    :class="`app-status-tag--${tone}`"
    :size="size"
    effect="light"
  >{{ label }}</el-tag>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    tone: 'neutral' | 'success' | 'info' | 'warning' | 'danger';
    label: string;
    size?: 'default' | 'small';
  }>(),
  { size: 'default' },
);
</script>
