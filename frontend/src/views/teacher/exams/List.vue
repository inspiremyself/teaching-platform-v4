<template>
  <ExamListPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import ExamListPanel from '@/views/teacher/components/ExamListPanel.vue';
import { ExamListKey, useExamList } from '@/composables/useExamList';
provide(ExamListKey, useExamList());
</script>
