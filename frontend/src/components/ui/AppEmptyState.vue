<template>
  <el-result v-if="tone === 'error'" icon="error" :title="title" :sub-title="description">
    <template #extra><slot name="actions" /></template>
  </el-result>
  <el-empty v-else :description="description">
    <template #extra><slot name="actions" /></template>
  </el-empty>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    description: string;
    title?: string;
    tone?: 'default' | 'error';
  }>(),
  { title: '加载失败', tone: 'default' },
);
</script>
