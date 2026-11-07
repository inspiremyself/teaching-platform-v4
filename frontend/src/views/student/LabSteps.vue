<template>
  <LabStepAnswerPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import LabStepAnswerPanel from '@/views/student/components/LabStepAnswerPanel.vue';
import { StudentLabStepsKey, useStudentLabSteps } from '@/composables/useStudentLabSteps';
provide(StudentLabStepsKey, useStudentLabSteps());
</script>
