<template>
  <section class="typed-editor-shell question-dialog__group">
    <div class="question-dialog__group-title typed-editor-shell__header">
      <span>{{ title }}</span>
      <div v-if="$slots.actions" class="question-dialog__group-actions typed-editor-shell__actions">
        <slot name="actions" />
      </div>
    </div>

    <div v-if="messages.length" class="question-dialog__validation typed-editor-shell__validation">
      <div class="question-dialog__validation-title">保存前请补齐以下内容</div>
      <ul>
        <li v-for="message in messages" :key="message">{{ message }}</li>
      </ul>
    </div>

    <div class="typed-editor-shell__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  messages: string[];
}>();
</script>

<style scoped>
.typed-editor-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 14px;
  min-height: 0;
  overflow: visible;
}

.typed-editor-shell__header {
  margin-bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.typed-editor-shell__actions {
  display: flex;
  gap: 8px;
}

.typed-editor-shell__validation {
  padding: 14px 16px;
  border-color: #fdba74;
  background: linear-gradient(180deg, #fff7ed, #fffaf5);
}

.typed-editor-shell__validation ul {
  margin: 10px 0 0;
  padding-left: 18px;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.6;
}

.typed-editor-shell__body {
  display: block;
  min-height: 0;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
  overflow: visible;
}

.typed-editor-shell__body > * {
  width: 100%;
  min-width: 0;
}

@media (max-width: 768px) {
  .typed-editor-shell__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
