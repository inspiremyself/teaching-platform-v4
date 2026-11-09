<template>
  <section v-if="rows.length" class="exam-option-review">
    <div class="exam-option-review__title">选项明细</div>
    <ul class="exam-option-review__list">
      <li
        v-for="row in rows"
        :key="row.key"
        :class="['exam-option-review__row', row.selected ? 'is-selected' : '', row.correct ? 'is-correct' : '']"
      >
        <span class="exam-option-review__key">{{ row.key }}</span>
        <span class="exam-option-review__label">{{ row.label }}</span>
        <span v-if="row.selected" class="exam-option-review__tag exam-option-review__tag--selected">{{ selectedLabel }}</span>
        <span v-if="row.correct" class="exam-option-review__tag exam-option-review__tag--correct">{{ correctLabel }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildOptionReviewRows } from '@/utils/examAnswer'

const props = withDefaults(
  defineProps<{
    optionsJson?: string | Array<{ key: string; label: string }> | null
    questionType?: string | null
    studentAnswer?: string | null
    standardAnswer?: string | null
    selectedLabel?: string
    correctLabel?: string
  }>(),
  {
    optionsJson: null,
    questionType: null,
    studentAnswer: null,
    standardAnswer: null,
    selectedLabel: '你的答案',
    correctLabel: '正确答案',
  },
)

const rows = computed(() => buildOptionReviewRows(props))
</script>

<style scoped>
.exam-option-review__title {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #727785;
  font-weight: 700;
}

.exam-option-review__list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.exam-option-review__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.exam-option-review__row.is-selected {
  background: #ecfeff;
  border-color: #ccfbf1;
}

.exam-option-review__row.is-correct {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.exam-option-review__key {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.exam-option-review__label {
  flex: 1;
  min-width: 0;
  color: #191c22;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.exam-option-review__tag {
  flex: none;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.exam-option-review__tag--selected {
  background: #cffafe;
  color: #0f766e;
}

.exam-option-review__tag--correct {
  background: #dcfce7;
  color: #16a34a;
}
</style>
