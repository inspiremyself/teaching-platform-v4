<template>
  <div class="question-dialog__subjective-panel">
    <el-form-item :label="label">
      <el-input :model-value="modelValue" type="textarea" :rows="rows" :placeholder="placeholder" @update:model-value="$emit('update:modelValue', $event)" />
    </el-form-item>
    <p class="question-dialog__subjective-note">{{ note }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { TypedEditorKind } from './types';

const props = defineProps<{
  kind: Extract<TypedEditorKind, 'SHORT' | 'TEXT' | 'CODE'>;
  modelValue: string;
}>();

defineEmits<{
  (e: 'update:modelValue', value: string): void;
}>();

const label = computed(() => {
  if (props.kind === 'CODE') {
    return '参考实现 / 评分要点';
  }

  return '参考答案 / 评分要点';
});
const rows = computed(() => (props.kind === 'CODE' ? 10 : 6));
const placeholder = computed(() => {
  if (props.kind === 'CODE') {
    return '填写参考实现思路、关键代码片段或评分标准；可分段录入。';
  }

  return '填写参考答案、评分点或关键词；每行可作为一个答案项。';
});
const note = computed(() =>
  props.kind === 'CODE'
    ? '当前仅提供教师侧题目编写与参考答案维护，不涉及学生端代码编辑器。'
    : '可按分点方式填写参考答案、评分要点或关键词。',
);
</script>

<style scoped>
.question-dialog__subjective-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 8px;
  min-height: 0;
  overflow: visible;
}

.question-dialog__subjective-panel :deep(.el-form-item) {
   display: flex;
   flex-direction: column;
   flex: 1;
   margin-bottom: 0;
   min-height: 0;
}

.question-dialog__subjective-panel :deep(.el-form-item__content) {
  min-height: 0;
}

.question-dialog__subjective-panel :deep(.el-textarea) {
  min-height: 0;
}

.question-dialog__subjective-panel :deep(.el-textarea__inner) {
  border-radius: 16px;
  box-shadow: 0 0 0 1px #dbe4f0 inset;
  background: #ffffff;
  line-height: 1.65;
}

.question-dialog__subjective-note {
  margin: 0;
}
</style>
