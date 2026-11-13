<template>
  <ContentReaderPanel />
</template>

<script setup lang="ts">
import { onMounted, provide } from 'vue';
import ContentReaderPanel from './components/ContentReaderPanel.vue';
import { studentContentKey, useStudentContent } from '@/composables/useStudentContent';

const studentContent = useStudentContent();
provide(studentContentKey, studentContent);

onMounted(() => {
  void studentContent.loadGroups();
});
</script>
