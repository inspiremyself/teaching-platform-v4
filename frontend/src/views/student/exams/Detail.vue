<template>
  <ExamAnswerPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import ExamAnswerPanel from '@/views/student/components/ExamAnswerPanel.vue';
import { ExamDetailKey, useExamDetail } from '@/composables/useExamDetail';
provide(ExamDetailKey, useExamDetail());
</script>
