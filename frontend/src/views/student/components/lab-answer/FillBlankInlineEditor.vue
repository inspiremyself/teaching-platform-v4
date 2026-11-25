<template>
  <div class="fill-blank-inline-editor">
    <div v-if="segments.length" class="fill-blank-inline-editor__prompt">
      <template v-for="(segment, index) in segments" :key="`${segment.type}-${index}`">
        <span v-if="segment.type === 'text'" class="fill-blank-inline-editor__text">{{ segment.value }}</span>
        <span v-else class="fill-blank-inline-editor__blank">
          <input
            class="fill-blank-inline-editor__input"
            :value="answerOf(segment.index)"
            :disabled="disabled"
            :style="blankInputStyle(answerOf(segment.index))"
            @input="updateBlank(segment.index, ($event.target as HTMLInputElement).value)"
          /><!-- 动态值：输入框宽度按答案文本长度估算 -->
        </span>
      </template>
    </div>

    <div v-else class="fill-blank-inline-editor__fallback">
      <div
        v-for="blank in blankList"
        :key="blank.index"
        class="fill-blank-inline-editor__fallback-item"
      >
        <label :for="`fallback-blank-${blank.index}`">填空 {{ blank.index }}</label>
        <input
          :id="`fallback-blank-${blank.index}`"
          class="fill-blank-inline-editor__input"
          :value="blank.answer"
          :disabled="disabled"
          :style="blankInputStyle(blank.answer)"
          @input="updateBlank(blank.index, ($event.target as HTMLInputElement).value)"
        /><!-- 动态值：输入框宽度按答案文本长度估算 -->
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { StudentBlankMeta } from '@/types/lab';
import type { LabAnswerDraft } from './types';

type Segment =
  | { type: 'text'; value: string }
  | { type: 'blank'; index: number };

const props = defineProps<{
  modelValue: LabAnswerDraft | null;
  prompt: string;
  blanks?: StudentBlankMeta[];
  disabled?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: LabAnswerDraft];
}>();

const sortedBlankMetas = computed(() =>
  (props.blanks ?? []).slice().sort((left, right) => left.index - right.index),
);

const blankList = computed(() => {
  if (props.modelValue?.kind === 'fill') {
    return props.modelValue.blanks.slice().sort((left, right) => left.index - right.index);
  }

  return sortedBlankMetas.value.map(blank => ({ index: blank.index, answer: '' }));
});

const tokenPattern = /【填空\d+】/g;

const resolveIndexFromToken = (token: string, sequence: number) => {
  const numberMatch = token.match(/(\d+)/);
  if (numberMatch) {
    return Number(numberMatch[1]);
  }

  const matchedMeta = sortedBlankMetas.value.find(blank => blank.token === token);
  return matchedMeta?.index ?? sequence + 1;
};

const segments = computed<Segment[]>(() => {
  if (!props.prompt.trim() || !tokenPattern.test(props.prompt)) {
    tokenPattern.lastIndex = 0;
    return [];
  }

  tokenPattern.lastIndex = 0;
  const result: Segment[] = [];
  let cursor = 0;
  let sequence = 0;

  for (const match of props.prompt.matchAll(tokenPattern)) {
    const token = match[0];
    const start = match.index ?? 0;

    if (start > cursor) {
      result.push({ type: 'text', value: props.prompt.slice(cursor, start) });
    }

    const index = resolveIndexFromToken(token, sequence);
    result.push({ type: 'blank', index });
    cursor = start + token.length;
    sequence += 1;
  }

  if (cursor < props.prompt.length) {
    result.push({ type: 'text', value: props.prompt.slice(cursor) });
  }

  return result;
});

const answerOf = (index: number) => blankList.value.find(blank => blank.index === index)?.answer ?? '';

const blankInputStyle = (value: string) => {
  const length = String(value ?? '').trim().length;
  const width = Math.max(6, Math.min(18, length + 2));
  return {
    width: `${width}ch`,
  };
};

const updateBlank = (index: number, value: string) => {
  if (props.disabled) {
    return;
  }

  const current = blankList.value.length
    ? blankList.value.map(blank => ({ ...blank }))
    : [{ index, answer: '' }];
  const target = current.find(blank => blank.index === index);

  if (target) {
    target.answer = value;
  } else {
    current.push({ index, answer: value });
    current.sort((left, right) => left.index - right.index);
  }

  emit('update:modelValue', {
    kind: 'fill',
    blanks: current,
  });
};
</script>

<style scoped>
.fill-blank-inline-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.fill-blank-inline-editor__prompt {
  line-height: var(--line-height-body);
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.fill-blank-inline-editor__text {
  color: inherit;
}

.fill-blank-inline-editor__blank {
  display: inline-flex;
  align-items: center;
  margin: 0 4px;
  vertical-align: middle;
}

.fill-blank-inline-editor__input {
  min-width: 6ch;
  max-width: min(18ch, 38vw);
  padding: 0 2px 6px;
  border: none;
  border-bottom: 2px solid #60a5fa;
  background: transparent;
  color: var(--color-text-primary);
  font: inherit;
  outline: none;
}

.fill-blank-inline-editor__input:focus {
  border-bottom-color: var(--color-brand-student-border);
  box-shadow: inset 0 -1px 0 var(--color-brand-student-border);
}

.fill-blank-inline-editor__input:disabled {
  color: var(--color-text-muted);
  border-bottom-color: var(--color-border-strong);
}

.fill-blank-inline-editor__fallback {
  display: grid;
  gap: 12px;
  padding-top: 4px;
}

.fill-blank-inline-editor__fallback-item {
  display: grid;
  gap: 8px;
}

.fill-blank-inline-editor__fallback-item label {
  color: #334155;
  font-size: var(--font-size-base);
  font-weight: 700;
}

@media (max-width: 900px) {
  .fill-blank-inline-editor__blank {
    display: flex;
    width: auto;
    margin: 6px 2px;
  }

  .fill-blank-inline-editor__input {
    max-width: 100%;
  }
}
</style>
