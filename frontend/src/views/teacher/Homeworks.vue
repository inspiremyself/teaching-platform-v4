<template>
  <HomeworkListPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import HomeworkListPanel from '@/views/teacher/components/HomeworkListPanel.vue';
import { HomeworksKey, useHomeworks } from '@/composables/useHomeworks';
provide(HomeworksKey, useHomeworks());
</script>
