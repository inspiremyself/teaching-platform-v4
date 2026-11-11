<template>
  <PlatformContentPanel />
</template>

<script setup lang="ts">
import { onMounted, provide } from 'vue';
import PlatformContentPanel from './components/PlatformContentPanel.vue';
import { platformContentKey, usePlatformContent } from '@/composables/usePlatformContent';

const platformContent = usePlatformContent();
provide(platformContentKey, platformContent);

onMounted(() => {
  void platformContent.loadAll();
});
</script>
