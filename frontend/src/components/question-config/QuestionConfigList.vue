<template>
  <div class="question-config-list">
    <div v-if="items.length" class="question-config-list__items">
      <article v-for="(item, index) in items" :key="item.localId" class="question-config-list__card">
        <div class="question-config-list__main">
          <div class="question-config-list__topline">
            <div class="question-config-list__badges">
              <span class="question-config-list__badge question-config-list__badge--accent">
                {{ item.sourceType === 'BANK' ? '题库题目' : '内联题目' }}
              </span>
              <span class="question-config-list__badge">{{ typeLabelMap[item.questionType] ?? item.questionType }}</span>
              <span v-if="item.questionBankId" class="question-config-list__badge">#{{ item.questionBankId }}</span>
            </div>
            <div class="question-config-list__order">第 {{ item.sortOrder }} 题</div>
          </div>

          <h3 class="question-config-list__stem">{{ item.stem || '未填写题干' }}</h3>

          <div v-if="item.options?.length" class="question-config-list__options">
            <span v-for="option in item.options" :key="`${item.localId}-${option.key}`" class="question-config-list__option">
              {{ option.key }}. {{ option.label }}
            </span>
          </div>

          <p v-if="bankSnapshotHint && item.sourceType === 'BANK'" class="question-config-list__hint">
            修改仅作用于本作业快照，不同步题库。
          </p>
        </div>

        <div class="question-config-list__side">
          <label class="question-config-list__score-field">
            <span>分值</span>
            <el-input-number
              :model-value="item.score"
              :min="1"
              :max="100"
              :disabled="busy || contentLocked"
              @update:model-value="handleScoreChange(item.localId, $event)"
            />
          </label>

          <div class="question-config-list__actions">
            <button
              type="button"
              class="question-config-list__icon-button"
              :disabled="busy || reorderLocked || index === 0"
              @click="moveItem(index, -1)"
            >
              <span class="material-symbols-outlined">arrow_upward</span>
            </button>
            <button
              type="button"
              class="question-config-list__icon-button"
              :disabled="busy || reorderLocked || index === items.length - 1"
              @click="moveItem(index, 1)"
            >
              <span class="material-symbols-outlined">arrow_downward</span>
            </button>
            <button
              v-if="copyable && item.sourceType !== 'BANK'"
              type="button"
              class="question-config-list__icon-button"
              title="复制到题库"
              :disabled="busy"
              @click="$emit('copyToBank', item)"
            >
              <span class="material-symbols-outlined">bookmark_add</span>
            </button>
            <button
              type="button"
              class="question-config-list__icon-button"
              :disabled="busy || contentLocked"
              @click="$emit('edit', item)"
            >
              <span class="material-symbols-outlined">edit</span>
            </button>
            <button
              type="button"
              class="question-config-list__icon-button question-config-list__icon-button--danger"
              :disabled="busy || contentLocked"
              @click="$emit('remove', item.localId)"
            >
              <span class="material-symbols-outlined">delete</span>
            </button>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="question-config-list__empty">当前还没有可展示的题目配置。</div>
  </div>
</template>

<script setup lang="ts">
import { TYPED_EDITOR_LABELS } from '@/views/teacher/components/typed-editor/constants'
import type { ConfiguredQuestionItem } from '@/types/question-config'

const props = withDefaults(
  defineProps<{
    items: ConfiguredQuestionItem[]
    /** 内容锁定：禁用改分、编辑、删除，但仍允许调整顺序（已发布作业） */
    contentLocked?: boolean
    /** 排序锁定：连排序一并禁用（已关闭作业或加载中） */
    reorderLocked?: boolean
    /** 加载中：禁用全部操作 */
    busy?: boolean
    /** 是否渲染“复制到题库”按钮，题库题（BANK）始终不渲染 */
    copyable?: boolean
    /** BANK 行是否展示“仅作用于本作业快照”提示 */
    bankSnapshotHint?: boolean
  }>(),
  {
    contentLocked: false,
    reorderLocked: false,
    busy: false,
    copyable: false,
    bankSnapshotHint: false,
  },
)

const emit = defineEmits<{
  edit: [ConfiguredQuestionItem]
  remove: [string]
  reorder: [ConfiguredQuestionItem[]]
  scoreChange: [{ localId: string; score: number }]
  copyToBank: [ConfiguredQuestionItem]
}>()

const typeLabelMap = TYPED_EDITOR_LABELS as Record<string, string>

const resequence = (items: ConfiguredQuestionItem[]) =>
  items.map((item, index) => ({
    ...item,
    sortOrder: index + 1,
  }))

const moveItem = (index: number, direction: -1 | 1) => {
  if (props.busy || props.reorderLocked) {
    return
  }

  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= props.items.length) {
    return
  }

  const nextItems = [...props.items]
  const [current] = nextItems.splice(index, 1)
  nextItems.splice(targetIndex, 0, current)
  emit('reorder', resequence(nextItems))
}

const handleScoreChange = (localId: string, value: number | undefined) => {
  if (props.busy || props.contentLocked) {
    return
  }

  emit('scoreChange', {
    localId,
    score: value ?? 1,
  })
}
</script>

<style scoped>
.question-config-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-config-list__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-config-list__card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.05);
}

.question-config-list__main,
.question-config-list__side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.question-config-list__side {
  align-items: stretch;
}

.question-config-list__topline,
.question-config-list__actions,
.question-config-list__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-config-list__topline {
  justify-content: space-between;
  align-items: center;
}

.question-config-list__badge,
.question-config-list__order,
.question-config-list__option {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.question-config-list__badge {
  background: #f1f5f9;
  color: #475569;
}

.question-config-list__badge--accent {
  background: #ccfbf1;
  color: #115e59;
}

.question-config-list__order {
  background: #eff6ff;
  color: #1d4ed8;
}

.question-config-list__stem {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.6;
  font-weight: 700;
}

.question-config-list__options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-config-list__option {
  background: #ffffff;
  color: #475569;
  border: 1px solid #dbe4f0;
}

.question-config-list__hint {
  margin: 0;
  color: #b45309;
  font-size: 11px;
  line-height: 1.5;
}

.question-config-list__score-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.question-config-list__score-field :deep(.el-input-number) {
  width: 100%;
}

.question-config-list__actions {
  justify-content: flex-end;
}

.question-config-list__icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  transition: transform 0.18s ease, background 0.18s ease;
}

.question-config-list__icon-button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #f8fafc;
}

.question-config-list__icon-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.question-config-list__icon-button--danger {
  border-color: #fecaca;
  background: #fff1f2;
  color: #dc2626;
}

.question-config-list__empty {
  padding: 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 18px;
  background: #ffffff;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .question-config-list__card {
    grid-template-columns: 1fr;
  }

  .question-config-list__actions {
    justify-content: flex-start;
  }
}
</style>
