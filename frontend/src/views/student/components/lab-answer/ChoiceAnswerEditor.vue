<template>
  <div class="choice-answer-editor">
    <button
      v-for="option in resolvedOptions"
      :key="option.key"
      type="button"
      class="choice-answer-editor__option"
      :class="{
        'is-selected': isSelected(option.key),
        'is-disabled': disabled,
      }"
      :disabled="disabled"
      @click="toggleOption(option.key)"
    >
      <span class="choice-answer-editor__marker">{{ option.key }}</span>
      <span class="choice-answer-editor__label">{{ option.label }}</span>
      <span class="choice-answer-editor__state">{{ isSelected(option.key) ? '已选择' : selectionLabel }}</span>
    </button>

    <div v-if="!resolvedOptions.length" class="choice-answer-editor__empty">
      当前题目暂无可展示选项，暂时无法使用点击交互作答。
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { StudentChoiceOption } from '@/types/lab';
import type { LabAnswerDraft } from './types';

const props = defineProps<{
  modelValue: LabAnswerDraft | null;
  options?: StudentChoiceOption[];
  mode: 'single' | 'multiple' | 'judge';
  disabled?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: LabAnswerDraft];
}>();

const resolvedOptions = computed<StudentChoiceOption[]>(() => {
  if (props.options?.length) {
    return props.options;
  }

  if (props.mode === 'judge') {
    return [
      { key: 'TRUE', label: '正确' },
      { key: 'FALSE', label: '错误' },
    ];
  }

  return [];
});

const selectionLabel = computed(() => (props.mode === 'multiple' ? '点击勾选' : '点击选择'));

const isSelected = (key: string) => {
  if (!props.modelValue) {
    return false;
  }

  if (props.mode === 'multiple' && props.modelValue.kind === 'multiple') {
    return props.modelValue.selectedKeys.includes(key);
  }

  if (props.mode === 'judge' && props.modelValue.kind === 'judge') {
    return props.modelValue.value === key;
  }

  return props.modelValue.kind === 'single' && props.modelValue.selectedKey === key;
};

const toggleOption = (key: string) => {
  if (props.disabled) {
    return;
  }

  if (props.mode === 'multiple') {
    const currentKeys = props.modelValue?.kind === 'multiple' ? [...props.modelValue.selectedKeys] : [];
    const exists = currentKeys.includes(key);
    const nextKeys = exists ? currentKeys.filter(item => item !== key) : [...currentKeys, key];
    emit('update:modelValue', {
      kind: 'multiple',
      selectedKeys: nextKeys,
    });
    return;
  }

  if (props.mode === 'judge') {
    const currentValue = props.modelValue?.kind === 'judge' ? props.modelValue.value : null;
    emit('update:modelValue', {
      kind: 'judge',
      value: currentValue === key ? null : (key as 'TRUE' | 'FALSE'),
    });
    return;
  }

  const currentValue = props.modelValue?.kind === 'single' ? props.modelValue.selectedKey : null;
  emit('update:modelValue', {
    kind: 'single',
    selectedKey: currentValue === key ? null : key,
  });
};
</script>

<style scoped>
.choice-answer-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.choice-answer-editor__option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--color-border-strong);
  border-radius: 16px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.choice-answer-editor__option:hover:not(.is-disabled) {
  border-color: #93c5fd;
  box-shadow: 0 8px 20px rgba(30, 64, 175, 0.08);
}

.choice-answer-editor__option.is-selected {
  border-color: var(--color-brand-student-border);
  background: var(--color-brand-student-soft);
}

.choice-answer-editor__option.is-disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.choice-answer-editor__marker {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: var(--color-border);
  color: #334155;
  font-weight: var(--font-weight-bold);
}

.choice-answer-editor__option.is-selected .choice-answer-editor__marker {
  background: var(--color-brand-student-border);
  color: var(--color-text-inverse);
}

.choice-answer-editor__label {
  line-height: var(--line-height-body);
  white-space: pre-wrap;
}

.choice-answer-editor__state {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.choice-answer-editor__option.is-selected .choice-answer-editor__state {
  color: var(--color-brand-student);
}

.choice-answer-editor__empty {
  padding: 12px 14px;
  border: 1px solid #fdba74;
  border-radius: 16px;
  background: var(--color-warning-soft);
  color: #9a3412;
  font-size: var(--font-size-base);
}

@media (max-width: 900px) {
  .choice-answer-editor__option {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .choice-answer-editor__state {
    grid-column: 2 / 3;
  }
}
</style>
