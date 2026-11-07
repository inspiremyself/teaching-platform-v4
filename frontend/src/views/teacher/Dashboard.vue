<template>
  <DashboardOverviewPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import DashboardOverviewPanel from '@/views/teacher/components/DashboardOverviewPanel.vue';
import { DashboardKey, useDashboard } from '@/composables/useDashboard';
provide(DashboardKey, useDashboard());
</script>
