<template>
  <section class="question-answer-shell">
    <div class="question-answer-shell__header">
      <div>
        <div class="question-answer-shell__title">{{ title }}</div>
        <p v-if="hint" class="question-answer-shell__hint">{{ hint }}</p>
      </div>
      <span v-if="chip" class="question-answer-shell__chip">{{ chip }}</span>
    </div>

    <slot name="callout"></slot>
    <div class="question-answer-shell__body">
      <slot></slot>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  hint?: string;
  chip?: string;
}>();
</script>

<style scoped>
.question-answer-shell {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
}

.question-answer-shell__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.question-answer-shell__title {
  margin-bottom: 8px;
  color: #0f172a;
  font-weight: 700;
}

.question-answer-shell__hint {
  margin: 0;
  color: #64748b;
  line-height: var(--line-height-body);
}

.question-answer-shell__chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #334155;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.question-answer-shell__body {
  min-width: 0;
}

@media (max-width: 900px) {
  .question-answer-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
