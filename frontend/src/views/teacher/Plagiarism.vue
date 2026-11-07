<template>
  <PlagiarismResultPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import PlagiarismResultPanel from '@/views/teacher/components/PlagiarismResultPanel.vue';
import { PlagiarismKey, usePlagiarism } from '@/composables/usePlagiarism';
provide(PlagiarismKey, usePlagiarism());
</script>
