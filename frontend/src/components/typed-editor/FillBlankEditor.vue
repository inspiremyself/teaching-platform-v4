<template>
  <div class="question-dialog__fill-layout typed-fill-editor" :class="{ 'is-active': true }">
    <div v-if="!hidePrompt" class="question-dialog__fill-main typed-fill-editor__main">
      <el-form-item :label="promptLabel">
        <el-input
          ref="promptInputRef"
          :model-value="prompt"
          type="textarea"
          :rows="rows"
          :placeholder="placeholder"
          @update:model-value="$emit('update:prompt', $event)"
        />
      </el-form-item>
    </div>

    <div class="question-dialog__fill-side typed-fill-editor__side">
      <div class="question-dialog__blank-panel typed-fill-editor__panel">
        <div class="question-dialog__blank-panel-title">填空位答案</div>

        <div v-if="blanks.length" class="question-dialog__blank-list">
          <article v-for="blank in blanks" :key="blank.index" class="question-dialog__blank-card">
            <div class="question-dialog__blank-card-head">
              <strong>空 {{ blank.index }}</strong>
              <button type="button" class="question-dialog__blank-remove" :title="`删除空 ${blank.index}`" @click="$emit('remove-blank', blank.index)">
                <span class="material-symbols-outlined">delete</span>
              </button>
            </div>
            <el-input
              :model-value="blank.answersText"
              type="textarea"
              :rows="2"
              placeholder="每行一个可接受答案"
              @update:model-value="updateBlankAnswer(blank.index, $event)"
            />
          </article>
        </div>

        <div v-else class="question-dialog__blank-empty">先在题干中插入填空位，再为每个空设置答案。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { TypedEditorBlankDraft } from './types';

interface ElTextareaLike {
  textarea?: HTMLTextAreaElement;
}

const props = withDefaults(
  defineProps<{
    prompt: string;
    blanks: TypedEditorBlankDraft[];
    promptLabel?: string;
    placeholder?: string;
    rows?: number;
    hidePrompt?: boolean;
  }>(),
  {
    promptLabel: '题干内容（支持插入填空位）',
    placeholder: '填写题干，并在需要的位置插入如【填空1】的填空位。',
    rows: 8,
    hidePrompt: false,
  },
);

const emit = defineEmits<{
  (e: 'update:prompt', value: string): void;
  (e: 'update-blank-answer', payload: { index: number; answersText: string }): void;
  (e: 'insert-token', selection?: { start: number; end: number }): void;
  (e: 'remove-blank', index: number): void;
}>();

const promptInputRef = ref<ElTextareaLike | null>(null);

const updateBlankAnswer = (index: number, answersText: string) => {
  emit('update-blank-answer', { index, answersText });
};

const insertTokenAtCursor = () => {
  const textarea = promptInputRef.value?.textarea;
  if (!textarea) {
    emit('insert-token');
    return;
  }

  emit('insert-token', {
    start: textarea.selectionStart ?? props.prompt.length,
    end: textarea.selectionEnd ?? props.prompt.length,
  });
};

defineExpose({
  insertTokenAtCursor,
  focusAt(position: number) {
    const textarea = promptInputRef.value?.textarea;
    textarea?.focus();
    textarea?.setSelectionRange(position, position);
  },
});
</script>

<style scoped>
.typed-fill-editor {
  display: flex;
  flex: 1;
  min-height: 0;
}

.question-dialog__fill-layout.is-active {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  min-height: 0;
}

.typed-fill-editor__main,
.typed-fill-editor__side {
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.typed-fill-editor__panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 10px;
  width: 100%;
  min-height: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.question-dialog__blank-panel-title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.question-dialog__blank-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.question-dialog__blank-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.035);
}

.question-dialog__blank-card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.question-dialog__blank-card-head strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.question-dialog__blank-card :deep(.el-textarea__inner) {
  min-height: 108px;
}

.question-dialog__blank-card :deep(.el-textarea__inner),
.typed-fill-editor__main :deep(.el-textarea__inner) {
  border-radius: 18px;
  box-shadow: 0 0 0 1px #dbe4f0 inset;
  background: #ffffff;
  line-height: 1.65;
}

.typed-fill-editor__main :deep(.el-textarea__inner) {
  min-height: 200px;
}

.question-dialog__blank-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid #fecaca;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff1f2, #ffe4e6);
  color: #dc2626;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.question-dialog__blank-remove .material-symbols-outlined {
  font-size: 18px;
}

.question-dialog__blank-remove:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(220, 38, 38, 0.14);
}

.question-dialog__blank-empty {
  padding: 16px;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  background: #ffffff;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1120px) {
  .question-dialog__blank-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .question-dialog__fill-layout.is-active {
    grid-template-columns: 1fr;
  }

   .question-dialog__blank-list {
    grid-template-columns: 1fr;
  }

  .question-dialog__blank-remove {
    width: 34px;
  }
}
</style>
