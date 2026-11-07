<template>
  <LabStepEditorPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import LabStepEditorPanel from '@/views/teacher/components/LabStepEditorPanel.vue';
import { LabStepsKey, useLabSteps } from '@/composables/useLabSteps';
provide(LabStepsKey, useLabSteps());
</script>
