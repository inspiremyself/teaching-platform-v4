<template>
  <HomeworkAnswerPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import HomeworkAnswerPanel from '@/views/student/components/HomeworkAnswerPanel.vue';
import { HomeworkDetailKey, useHomeworkDetail } from '@/composables/useHomeworkDetail';
provide(HomeworkDetailKey, useHomeworkDetail());
</script>
