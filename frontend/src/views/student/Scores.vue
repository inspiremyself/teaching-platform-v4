<template>
  <ScoresPanel />
</template>

<script setup lang="ts">
import { provide } from 'vue';
import ScoresPanel from '@/views/student/components/ScoresPanel.vue';
import { ScoresKey, useScores } from '@/composables/useScores';
provide(ScoresKey, useScores());
</script>
