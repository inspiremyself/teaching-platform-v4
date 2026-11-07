<template>
  <div class="code-lab-answer-editor">
    <div class="code-lab-answer-editor__toolbar">
      <span class="code-lab-answer-editor__label">编辑器语言</span>
      <el-select
        :model-value="selectedLanguage"
        :disabled="disabled"
        class="code-lab-answer-editor__select"
        placeholder="选择语言"
        @update:model-value="updateLanguage"
      >
        <el-option
          v-for="language in languageOptions"
          :key="language"
          :label="language"
          :value="language"
        />
      </el-select>
    </div>

    <CodeAnswerEditor
      :model-value="currentCode"
      :language="selectedLanguage"
      :rows="rows"
      :maxlength="maxlength"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:model-value="updateCode"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElSelect, ElOption } from 'element-plus';
import CodeAnswerEditor from '../../CodeAnswerEditor.vue';
import type { LabAnswerDraft } from './types';

const props = withDefaults(defineProps<{
  modelValue: LabAnswerDraft | null;
  language?: string | null;
  disabled?: boolean;
  placeholder?: string;
  rows?: number;
  maxlength?: number;
}>(), {
  language: 'CODE',
  disabled: false,
  placeholder: '',
  rows: 16,
  maxlength: 20000,
});

const emit = defineEmits<{
  'update:modelValue': [value: LabAnswerDraft];
}>();

const languageOptions = ['JAVA', 'PYTHON', 'C', 'CPP', 'JAVASCRIPT', 'TYPESCRIPT', 'GO', 'RUST', 'SQL', 'BASH'];

const currentCode = computed(() => (props.modelValue?.kind === 'code' ? props.modelValue.code : ''));
const selectedLanguage = computed(() => (props.modelValue?.kind === 'code'
  ? props.modelValue.language || props.language || 'CODE'
  : props.language || 'CODE'));

const updateCode = (value: string) => {
  emit('update:modelValue', {
    kind: 'code',
    code: value,
    language: selectedLanguage.value,
  });
};

const updateLanguage = (value: string) => {
  emit('update:modelValue', {
    kind: 'code',
    code: currentCode.value,
    language: value,
  });
};
</script>

<style scoped>
.code-lab-answer-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.code-lab-answer-editor__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.code-lab-answer-editor__label {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.code-lab-answer-editor__select {
  width: 180px;
}
</style>
