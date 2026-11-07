<template>
  <div class="typed-objective-editor">
    <div v-if="kind === 'JUDGE'" class="question-dialog__judge-grid typed-objective-editor__judge-grid">
      <button
        v-for="choice in TYPED_EDITOR_JUDGE_CHOICES"
        :key="choice.value"
        type="button"
        class="question-dialog__judge-card"
        :class="{ 'is-selected': judgeAnswer === choice.value }"
        @click="$emit('update:judgeAnswer', choice.value)"
      >
        <span class="question-dialog__judge-title">{{ choice.label }}</span>
        <span class="question-dialog__judge-desc">{{ judgeAnswer === choice.value ? '已设为正确答案' : '点击设为正确答案' }}</span>
      </button>
    </div>

    <div v-else class="question-dialog__editor-stack typed-objective-editor__stack">
      <div class="question-dialog__editor-toolbar typed-objective-editor__toolbar">
        <div class="question-dialog__editor-toolbar-copy typed-objective-editor__toolbar-copy">
          <span class="question-dialog__editor-note">{{ toolbarLabel }}</span>
          <span class="question-dialog__editor-subnote">先点左侧选择正确答案，再填写选项文案。</span>
        </div>
        <el-button type="primary" size="small" class="typed-objective-editor__add-button" @click="$emit('add-option')">
          新增选项
        </el-button>
      </div>

      <div class="question-dialog__option-list typed-objective-editor__option-list">
        <article
          v-for="option in options"
          :key="option.id"
          class="question-dialog__option-card"
          :class="{ 'is-selected': selectedIds.includes(option.id) }"
        >
          <button
            type="button"
            class="question-dialog__option-select"
            :class="{ 'is-selected': selectedIds.includes(option.id) }"
            @click="$emit('toggle-answer', option.id)"
          >
            <span class="question-dialog__option-key">{{ option.key }}</span>
            <span class="question-dialog__option-state">{{ kind === 'SINGLE' ? '正确答案' : '多选答案' }}</span>
          </button>

          <el-input :model-value="option.content" placeholder="输入选项内容" @update:model-value="updateOptionContent(option.id, $event)" />

          <button
            type="button"
            class="question-dialog__option-remove"
            :disabled="options.length <= 2"
            @click="$emit('remove-option', option.id)"
          >
            <span class="material-symbols-outlined">delete</span>
          </button>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { TYPED_EDITOR_JUDGE_CHOICES } from './constants';
import type { TypedEditorJudgeAnswer, TypedEditorKind, TypedEditorOptionDraft } from './types';

const props = defineProps<{
  kind: Extract<TypedEditorKind, 'SINGLE' | 'MULTI' | 'JUDGE'>;
  options: TypedEditorOptionDraft[];
  selectedIds: string[];
  judgeAnswer: TypedEditorJudgeAnswer;
}>();

const emit = defineEmits<{
  (e: 'add-option'): void;
  (e: 'remove-option', id: string): void;
  (e: 'toggle-answer', id: string): void;
  (e: 'update-option-content', payload: { id: string; content: string }): void;
  (e: 'update:judgeAnswer', value: TypedEditorJudgeAnswer): void;
}>();

const toolbarLabel = computed(() =>
  props.kind === 'SINGLE' ? '点击一个选项设为正确答案' : '点击多个选项切换为正确答案',
);

const updateOptionContent = (id: string, content: string) => {
  emit('update-option-content', { id, content });
};
</script>

<style scoped>
.typed-objective-editor {
  display: flex;
  width: 100%;
  min-height: 0;
  overflow: visible;
}

.typed-objective-editor__stack {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: visible;
}

.typed-objective-editor__toolbar {
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.04);
}

.typed-objective-editor__toolbar-copy {
  min-width: 0;
  gap: 4px;
}

.question-dialog__editor-note {
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.4;
}

.question-dialog__editor-subnote {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.typed-objective-editor__add-button {
  flex-shrink: 0;
}

.typed-objective-editor__add-button :deep(.el-button) {
  font-weight: 700;
}

.typed-objective-editor__toolbar :deep(.el-button--primary) {
  --el-button-bg-color: #0f766e;
  --el-button-border-color: #0f766e;
  --el-button-hover-bg-color: #0d9488;
  --el-button-hover-border-color: #0d9488;
  --el-button-active-bg-color: #115e59;
  --el-button-active-border-color: #115e59;
  min-height: 34px;
  padding-inline: 14px;
  border-radius: 999px;
  box-shadow: 0 10px 20px rgba(15, 118, 110, 0.18);
  font-weight: 700;
}

.typed-objective-editor__toolbar :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
}

.typed-objective-editor__option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: visible;
}

.question-dialog__option-card {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr) 44px;
  gap: 10px;
  align-items: stretch;
  padding: 10px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.03);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.question-dialog__option-card:hover {
  border-color: #99f6e4;
  box-shadow: 0 16px 28px rgba(20, 184, 166, 0.1);
}

.question-dialog__option-card.is-selected {
  border-color: #14b8a6;
  background: linear-gradient(180deg, #f0fdfa, #ffffff);
  box-shadow: 0 18px 30px rgba(20, 184, 166, 0.14);
}

.question-dialog__option-select,
.question-dialog__judge-card {
  border: 1px solid #cbd5e1;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.question-dialog__option-select {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 8px;
  min-height: 64px;
  padding: 10px 12px;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fafc, #eef6ff);
  color: #334155;
}

.question-dialog__option-select:hover,
.question-dialog__judge-card:hover {
  transform: translateY(-1px);
}

.question-dialog__option-select.is-selected {
  border-color: #14b8a6;
  background: linear-gradient(180deg, #ccfbf1, #f0fdfa);
  color: #0f766e;
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.1);
}

.question-dialog__option-key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: inherit;
  font-size: 12px;
  font-weight: 800;
}

.question-dialog__option-state {
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.question-dialog__option-card :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 14px;
  box-shadow: 0 0 0 1px #dbe4f0 inset;
  background: #ffffff;
}

.question-dialog__option-card.is-selected :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #99f6e4 inset;
}

.question-dialog__option-remove {
  align-self: center;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid #fecaca;
  border-radius: 14px;
  background: linear-gradient(180deg, #fff1f2, #ffe4e6);
  color: #dc2626;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.question-dialog__option-remove:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(220, 38, 38, 0.14);
}

.question-dialog__option-remove:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.typed-objective-editor__judge-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-content: start;
  min-height: 0;
}

.question-dialog__judge-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 14px;
  border-radius: 16px;
  background: #ffffff;
}

.question-dialog__judge-card.is-selected {
  border-color: #14b8a6;
  background: linear-gradient(180deg, #f0fdfa, #ffffff);
  box-shadow: 0 16px 28px rgba(20, 184, 166, 0.12);
}

.question-dialog__judge-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.question-dialog__judge-desc {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .typed-objective-editor__toolbar,
  .question-dialog__option-card,
  .typed-objective-editor__judge-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .question-dialog__option-card {
    padding: 12px;
  }

  .question-dialog__option-remove {
    width: 100%;
  }
}
</style>
