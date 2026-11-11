<template>
  <ContentBrowsePanel />
</template>

<script setup lang="ts">
import { onMounted, provide } from 'vue';
import ContentBrowsePanel from './components/ContentBrowsePanel.vue';
import { teacherContentKey, useTeacherContent } from '@/composables/useTeacherContent';

const teacherContent = useTeacherContent();
provide(teacherContentKey, teacherContent);

onMounted(() => {
  void teacherContent.loadFilters();
  void teacherContent.loadCoursewares();
});
</script>
