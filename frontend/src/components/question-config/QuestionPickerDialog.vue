<template>
  <el-dialog v-model="dialogVisible" width="1100px" class="question-picker-dialog" destroy-on-close>
    <div class="question-picker-dialog__shell">
      <header class="question-picker-dialog__header">
        <div>
          <p class="question-picker-dialog__eyebrow">共享题目配置 / 题库选择</p>
          <h2>从题库挑选题目</h2>
          <p class="question-picker-dialog__desc">复用现有题库检索能力，批量挑选题目并生成后续作业/考试可复用的题目配置。</p>
        </div>
        <div class="question-picker-dialog__summary">
          <span class="question-picker-dialog__summary-chip">已选 {{ selectedRows.length }} 题</span>
        </div>
      </header>

      <section class="question-picker-dialog__panel">
        <div class="question-picker-dialog__filters-grid">
          <label class="question-picker-dialog__field">
            <span class="question-picker-dialog__field-label">关键词</span>
            <el-input v-model="filters.keyword" clearable placeholder="按编码、题干、解析内容搜索" />
          </label>

          <label class="question-picker-dialog__field">
            <span class="question-picker-dialog__field-label">题型</span>
            <el-select v-model="filters.type" clearable placeholder="全部题型">
              <el-option v-for="item in typeOptions" :key="item" :label="typeLabelMap[item]" :value="item" />
            </el-select>
          </label>

          <label class="question-picker-dialog__field">
            <span class="question-picker-dialog__field-label">难度</span>
            <el-select v-model="filters.difficulty" clearable placeholder="全部难度">
              <el-option v-for="item in difficultyOptions" :key="item" :label="difficultyLabelMap[item]" :value="item" />
            </el-select>
          </label>
        </div>

        <el-table
          ref="tableRef"
          :data="filteredRows"
          v-loading="loading"
          row-key="id"
          empty-text="暂无可选题目"
          class="question-picker-dialog__table"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="56" reserve-selection />
          <el-table-column label="题目" min-width="360">
            <template #default="{ row }">
              <div class="question-picker-dialog__cell-main">
                <strong>{{ row.code }}</strong>
                <span>{{ row.stem }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="120">
            <template #default="{ row }">{{ typeLabelMap[row.type] ?? row.type }}</template>
          </el-table-column>
          <el-table-column label="难度" width="120">
            <template #default="{ row }">{{ getDifficultyLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column label="默认分值" width="120">
            <template #default="{ row }">{{ row.defaultScore }} 分</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="question-picker-dialog__selected-panel">
        <div class="question-picker-dialog__selected-head">
          <div>
            <p class="question-picker-dialog__eyebrow">已选题目</p>
            <h3>配置分值与顺序</h3>
          </div>
        </div>

        <QuestionConfigList
          :items="selectedConfigs"
          @edit="noopEdit"
          @remove="removeSelected"
          @reorder="selectedConfigs = $event"
          @score-change="updateSelectedScore"
        />
      </section>

      <footer class="question-picker-dialog__footer">
        <div class="question-picker-dialog__footer-note">仅输出共享题目配置，不在此组件内处理页面保存或后端持久化。</div>
        <div class="question-picker-dialog__footer-actions">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedConfigs.length" @click="confirmSelection">确认带入</el-button>
        </div>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listQuestions } from '@/api/questions'
import type { ConfiguredQuestionItem } from '@/types/question-config'
import type { QuestionDifficulty, QuestionItem, QuestionType } from '@/types/question'
import { QUESTION_TYPED_EDITOR_KIND_OPTIONS, TYPED_EDITOR_LABELS } from '@/components/typed-editor/constants'
import { parseQuestionAnswerArray } from '@/components/typed-editor/hydrators/fromQuestion'
import QuestionConfigList from './QuestionConfigList.vue'

const props = withDefaults(defineProps<{ visible: boolean; validateJudgeFormat?: boolean }>(), {
  validateJudgeFormat: false,
})

const emit = defineEmits<{
  'update:visible': [boolean]
  confirm: [ConfiguredQuestionItem[]]
}>()

interface TableLike {
  toggleRowSelection?: (row: QuestionItem, selected?: boolean) => void
}

const typeOptions: QuestionType[] = [...QUESTION_TYPED_EDITOR_KIND_OPTIONS]
const difficultyOptions: QuestionDifficulty[] = ['EASY', 'MEDIUM', 'HARD']
const typeLabelMap: Record<string, string> = TYPED_EDITOR_LABELS
const difficultyLabelMap: Record<QuestionDifficulty, string> = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战',
}

const dialogVisible = computed({
  get: () => props.visible,
  set: value => emit('update:visible', value),
})

const tableRef = ref<TableLike | null>(null)
const loading = ref(false)
const rows = ref<QuestionItem[]>([])
const selectedRows = ref<QuestionItem[]>([])
const selectedConfigs = ref<ConfiguredQuestionItem[]>([])
const filters = reactive({
  keyword: '',
  type: '',
  difficulty: '',
})

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()

  return rows.value.filter(item => {
    const matchesType = !filters.type || item.type === filters.type
    const matchesDifficulty = !filters.difficulty || item.difficulty === filters.difficulty
    const matchesKeyword =
      !keyword ||
      item.code.toLowerCase().includes(keyword) ||
      item.stem.toLowerCase().includes(keyword) ||
      item.analysisText.toLowerCase().includes(keyword)

    return matchesType && matchesDifficulty && matchesKeyword
  })
})

const getDifficultyLabel = (difficulty: QuestionItem['difficulty']) =>
  typeof difficulty === 'string' && difficulty in difficultyLabelMap
    ? difficultyLabelMap[difficulty as QuestionDifficulty]
    : String(difficulty ?? '')

const buildConfiguredQuestion = (row: QuestionItem, index: number): ConfiguredQuestionItem => ({
  localId: `bank-${row.id}`,
  questionBankId: row.id,
  sourceType: 'BANK',
  questionType: row.type,
  stem: row.stem,
  sortOrder: index + 1,
  score: row.defaultScore,
  options: parseQuestionAnswerArray(row.optionsJson).map((option, optionIndex) => {
    if (option && typeof option === 'object') {
      const record = option as Record<string, unknown>
      return {
        key: String(record.key ?? String.fromCharCode(65 + optionIndex)),
        label: String(record.content ?? record.label ?? record.text ?? ''),
      }
    }

    return {
      key: String.fromCharCode(65 + optionIndex),
      label: String(option ?? ''),
    }
  }),
  answerJson: row.answerJson,
  scoringConfigJson: null,
})

const syncSelectedConfigs = (selection: QuestionItem[]) => {
  const existingMap = new Map(selectedConfigs.value.map(item => [item.questionBankId, item]))

  selectedConfigs.value = selection.map((row, index) => {
    const existing = existingMap.get(row.id)
    return {
      ...(existing ?? buildConfiguredQuestion(row, index)),
      sortOrder: index + 1,
    }
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    rows.value = await listQuestions()
  } finally {
    loading.value = false
  }
}

/**
 * 判断题格式兼容性：选项 key 必须恰为 T/F，答案必须为非空的 T/F 列表。
 * 仅当调用方开启 validateJudgeFormat 时生效（考试组卷场景）。
 */
const isJudgeFormatCompatible = (row: QuestionItem): boolean => {
  if (row.type !== 'JUDGE') return true
  try {
    const options = JSON.parse(row.optionsJson || '[]')
    if (!Array.isArray(options)) return false
    const keys = options
      .filter((item: unknown) => item !== null && typeof item === 'object')
      .map((item: any) => String(item.key ?? '').trim().toUpperCase())
    if (keys.length !== 2 || !keys.includes('T') || !keys.includes('F')) return false
    const answer = JSON.parse(row.answerJson || '[]')
    if (!Array.isArray(answer) || answer.length === 0) return false
    return answer.every((item: unknown) => {
      const value = String(item ?? '').trim().toUpperCase()
      return value === 'T' || value === 'F'
    })
  } catch {
    return false
  }
}

const handleSelectionChange = (selection: QuestionItem[]) => {
  if (!props.validateJudgeFormat) {
    selectedRows.value = selection
    syncSelectedConfigs(selection)
    return
  }

  const validRows = selection.filter(row => isJudgeFormatCompatible(row))
  const invalidRows = selection.filter(row => !isJudgeFormatCompatible(row))
  selectedRows.value = validRows
  syncSelectedConfigs(validRows)

  if (invalidRows.length) {
    ElMessage.warning('该判断题格式不兼容，请重新导入该题')
    // 在事件回调内同步取消勾选会触发重入，放到下一帧统一对齐视觉状态
    nextTick(() => {
      invalidRows.forEach(row => tableRef.value?.toggleRowSelection?.(row, false))
    })
  }
}

const updateSelectedScore = ({ localId, score }: { localId: string; score: number }) => {
  selectedConfigs.value = selectedConfigs.value.map(item => (item.localId === localId ? { ...item, score } : item))
}

const removeSelected = (localId: string) => {
  const row = rows.value.find(item => `bank-${item.id}` === localId)
  if (row) {
    tableRef.value?.toggleRowSelection?.(row, false)
  }

  selectedRows.value = selectedRows.value.filter(item => `bank-${item.id}` !== localId)
  syncSelectedConfigs(selectedRows.value)
}

const noopEdit = () => {
  ElMessage.info('题库题目请在题库管理台中编辑题干与答案。')
}

const confirmSelection = () => {
  emit('confirm', selectedConfigs.value)
  dialogVisible.value = false
}

watch(
  () => props.visible,
  value => {
    if (value) {
      void fetchData()
    }
  },
)
</script>

<style scoped>
.question-picker-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.question-picker-dialog :deep(.el-dialog__header) {
  display: none;
}

.question-picker-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.question-picker-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.question-picker-dialog__header,
.question-picker-dialog__panel,
.question-picker-dialog__selected-panel,
.question-picker-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
}

.question-picker-dialog__header,
.question-picker-dialog__footer,
.question-picker-dialog__selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.question-picker-dialog__header {
  padding: 16px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(240, 253, 250, 0.95));
}

.question-picker-dialog__eyebrow {
  margin: 0;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.question-picker-dialog__header h2,
.question-picker-dialog__selected-head h3 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.question-picker-dialog__desc {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.question-picker-dialog__summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #115e59;
  font-size: 11px;
  font-weight: 800;
}

.question-picker-dialog__panel,
.question-picker-dialog__selected-panel,
.question-picker-dialog__footer {
  padding: 16px;
}

.question-picker-dialog__filters-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 14px;
}

.question-picker-dialog__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-picker-dialog__field-label {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.question-picker-dialog__table {
  margin-top: 16px;
}

.question-picker-dialog__cell-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.question-picker-dialog__cell-main strong {
  color: #0f766e;
  font-size: 13px;
}

.question-picker-dialog__cell-main span {
  color: #334155;
  line-height: 1.6;
}

.question-picker-dialog__selected-head {
  margin-bottom: 14px;
}

.question-picker-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.question-picker-dialog__footer-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 900px) {
  .question-picker-dialog__filters-grid {
    grid-template-columns: 1fr;
  }

  .question-picker-dialog__header,
  .question-picker-dialog__footer,
  .question-picker-dialog__selected-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
