<template>
  <LabListPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import LabListPanel from '@/views/teacher/components/LabListPanel.vue';
import { LabsKey, useLabs } from '@/composables/useLabs';
provide(LabsKey, useLabs());
</script>
