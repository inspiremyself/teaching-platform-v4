<template>
  <el-dialog
    v-model="dialogVisible"
    title="批量导入题目"
    width="980px"
    destroy-on-close
    class="question-import-dialog"
    :close-on-click-modal="!importing"
    :close-on-press-escape="!importing"
    :show-close="!importing"
    :before-close="handleBeforeClose"
  >
    <div class="question-import-dialog__shell">
      <p class="question-import-dialog__desc">
        粘贴客观题 JSON 数组（支持单选 / 多选 / 判断），先解析校验再逐条写入题库；已存在的编码会自动跳过。
      </p>

      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="10"
        class="question-import-dialog__textarea"
        :disabled="importing"
        :placeholder="placeholderText"
      />

      <div class="question-import-dialog__toolbar">
        <el-button :loading="parsing" :disabled="importing" @click="handleParse">解析预览</el-button>
        <span class="question-import-dialog__toolbar-hint">编码会自动去空格并转大写后判重。</span>
      </div>

      <el-alert
        v-if="fatalError"
        type="error"
        :closable="false"
        show-icon
        :title="fatalError"
        class="question-import-dialog__alert"
      />

      <template v-if="parseResult && !fatalError">
        <div class="question-import-dialog__summary">
          <span class="question-import-dialog__summary-chip">待导入 {{ parseResult.readyCount }}</span>
          <span class="question-import-dialog__summary-chip question-import-dialog__summary-chip--warn">
            将跳过 {{ parseResult.skipCount }}
          </span>
          <span class="question-import-dialog__summary-chip question-import-dialog__summary-chip--error">
            错误 {{ parseResult.errorCount }}
          </span>
        </div>

        <el-table
          :data="parseResult.rows"
          class="question-import-dialog__table"
          :row-class-name="rowClassName"
          max-height="340"
          empty-text="暂无可预览题目"
        >
          <el-table-column label="行号" width="70">
            <template #default="{ row }">#{{ row.line }}</template>
          </el-table-column>
          <el-table-column label="编码" width="140" prop="code" />
          <el-table-column label="题型" width="80" prop="typeLabel" />
          <el-table-column label="题干摘要" min-width="220" prop="stemBrief" />
          <el-table-column label="答案" width="100" prop="answerText" />
          <el-table-column label="解析" width="90">
            <template #default="{ row }">
              <span v-if="row.hasAnalysis">有</span>
              <span v-else-if="row.status === 'READY'" class="question-import-dialog__analysis-warn">无解析</span>
              <span v-else>无</span>
            </template>
          </el-table-column>
          <el-table-column label="校验结果" min-width="200">
            <template #default="{ row }">{{ row.status === 'READY' ? '通过' : row.message }}</template>
          </el-table-column>
        </el-table>

        <div v-if="importing || progress.total" class="question-import-dialog__progress">
          <el-progress
            :percentage="progressPercentage"
            :status="importing ? '' : 'success'"
          />
          <span class="question-import-dialog__progress-text">{{ progress.current }} / {{ progress.total }}</span>
        </div>

        <section v-if="importResults.length" class="question-import-dialog__results">
          <div class="question-import-dialog__results-head">
            <strong>导入结果</strong>
            <el-button size="small" text type="primary" @click="copyFailedItems">复制失败项</el-button>
          </div>
          <ul class="question-import-dialog__results-list">
            <li
              v-for="item in importResults"
              :key="item.line"
              :class="['question-import-dialog__result-item', `is-${item.status.toLowerCase()}`]"
            >
              <span class="question-import-dialog__result-line">#{{ item.line }}</span>
              <span class="question-import-dialog__result-code">{{ item.code }}</span>
              <span class="question-import-dialog__result-status">{{ resultStatusLabel(item.status) }}</span>
              <span class="question-import-dialog__result-message">{{ item.message }}</span>
            </li>
          </ul>
        </section>
      </template>
    </div>

    <template #footer>
      <div class="question-import-dialog__footer">
        <el-button :disabled="importing" @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="importDisabled"
          @click="startImport"
        >
          {{ hasImportRound ? '再次导入' : '开始导入' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createQuestion, listQuestions } from '@/api/questions'
import { ApiError } from '@/utils/request'
import { normalizeImportCode, parseImportJson } from '@/utils/questionImport'
import type { ImportParseResult, ImportPreviewRow, ImportQuestionDraft } from '@/types/question-import'
import type { SaveQuestionPayload } from '@/types/question'

const props = defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  'update:visible': [boolean]
  imported: []
}>()

/** 单条导入结果状态 */
type ImportResultStatus = 'SUCCESS' | 'SKIPPED' | 'FAILED'

interface ImportResultRow {
  line: number
  code: string
  status: ImportResultStatus
  message: string
  draft?: ImportQuestionDraft
}

const placeholderText = `[
  {
    "code": "S-001",
    "type": "SINGLE",
    "stem": "题干内容",
    "difficulty": "EASY",
    "score": 2,
    "options": [{ "key": "A", "content": "选项A" }, { "key": "B", "content": "选项B" }],
    "answer": ["A"],
    "analysis": "解析说明"
  }
]`

const dialogVisible = computed({
  get: () => props.visible,
  set: value => emit('update:visible', value),
})

const jsonText = ref('')
const parsing = ref(false)
const importing = ref(false)
const fatalError = ref('')
const parseResult = ref<ImportParseResult | null>(null)
const importResults = ref<ImportResultRow[]>([])
/** 本轮导入是否已执行过，用于「再次导入」文案 */
const hasImportRound = ref(false)
const progress = ref({ current: 0, total: 0 })

const progressPercentage = computed(() =>
  progress.value.total ? Math.round((progress.value.current / progress.value.total) * 100) : 0,
)

const importDisabled = computed(() => {
  if (importing.value) return true
  const result = parseResult.value
  if (!result) return true
  return result.errorCount > 0 || result.readyCount === 0
})

const rowClassName = ({ row }: { row: ImportPreviewRow }) => {
  if (row.status === 'ERROR') return 'question-import-dialog__row--error'
  if (row.status === 'SKIP') return 'question-import-dialog__row--skip'
  return ''
}

const resultStatusLabel = (status: ImportResultStatus) => {
  if (status === 'SUCCESS') return '成功'
  if (status === 'SKIPPED') return '跳过'
  return '失败'
}

/** 重置本轮导入进度与结果，保留 JSON 与预览 */
const resetRoundState = () => {
  importResults.value = []
  hasImportRound.value = false
  progress.value = { current: 0, total: 0 }
}

/** 解析粘贴的 JSON：先拉取题库编码做存量判重 */
const handleParse = async () => {
  parsing.value = true
  try {
    const items = await listQuestions()
    const existingCodes = new Set(items.map(item => normalizeImportCode(item.code)))
    const { result, fatalError: error } = parseImportJson(jsonText.value, existingCodes)
    parseResult.value = result
    fatalError.value = error
    resetRoundState()
  } finally {
    parsing.value = false
  }
}

/** 将校验通过的草稿转换为单题新增接口所需的 payload */
const toPayload = (draft: ImportQuestionDraft): SaveQuestionPayload => ({
  code: draft.code,
  type: draft.type,
  stem: draft.stem,
  difficulty: draft.difficulty,
  defaultScore: draft.score,
  optionsJson: JSON.stringify(draft.options),
  answerJson: JSON.stringify(draft.answer),
  analysisText: draft.analysis,
})

/** 串行导入 READY 行；40900 视为编码已被占用并跳过 */
const startImport = async () => {
  const result = parseResult.value
  if (!result) return
  const readyRows = result.rows.filter(row => row.status === 'READY' && row.draft)
  if (!readyRows.length) return

  importing.value = true
  importResults.value = []
  progress.value = { current: 0, total: readyRows.length }
  let successCount = 0

  try {
    for (const [index, row] of readyRows.entries()) {
      progress.value = { current: index + 1, total: readyRows.length }
      try {
        await createQuestion(toPayload(row.draft as ImportQuestionDraft), { silent: true })
        successCount += 1
        importResults.value.push({ line: row.line, code: row.code, status: 'SUCCESS', message: '导入成功' })
      } catch (error) {
        if (error instanceof ApiError && error.code === 40900) {
          importResults.value.push({ line: row.line, code: row.code, status: 'SKIPPED', message: '编码已被占用' })
          continue
        }
        importResults.value.push({
          line: row.line,
          code: row.code,
          status: 'FAILED',
          message: error instanceof Error ? error.message : '导入失败',
          draft: row.draft,
        })
      }
    }
  } finally {
    importing.value = false
    hasImportRound.value = true
    if (successCount > 0) {
      emit('imported')
    }
  }
}

/** 复制失败项原始 JSON，便于修正后再次导入 */
const copyFailedItems = async () => {
  const failedDrafts = importResults.value
    .filter(item => item.status === 'FAILED' && item.draft)
    .map(item => item.draft)
  if (!failedDrafts.length) {
    ElMessage.info('当前没有失败项')
    return
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(failedDrafts, null, 2))
    ElMessage.success('失败项已复制')
  } catch {
    ElMessage.warning('当前环境不支持复制，请手动整理失败项')
  }
}

/** 导入期间阻止关闭，兜底 ESC / 遮罩以外的触发路径 */
const handleBeforeClose = (done: () => void) => {
  if (importing.value) {
    ElMessage.warning('导入进行中，请等待本轮结束')
    return
  }
  done()
}

watch(
  () => props.visible,
  value => {
    if (!value) return
    jsonText.value = ''
    parsing.value = false
    importing.value = false
    fatalError.value = ''
    parseResult.value = null
    resetRoundState()
  },
)
</script>

<style scoped>
.question-import-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.question-import-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-import-dialog__desc {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.question-import-dialog__textarea :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.question-import-dialog__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-import-dialog__toolbar-hint {
  color: #94a3b8;
  font-size: 12px;
}

.question-import-dialog__alert {
  margin-top: 2px;
}

.question-import-dialog__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-import-dialog__summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #115e59;
  font-size: 12px;
  font-weight: 700;
}

.question-import-dialog__summary-chip--warn {
  background: #fef3c7;
  color: #b45309;
}

.question-import-dialog__summary-chip--error {
  background: #fee2e2;
  color: #b91c1c;
}

.question-import-dialog__table {
  margin-top: 2px;
}

.question-import-dialog__table :deep(.question-import-dialog__row--error) {
  background: #fef2f2;
}

.question-import-dialog__table :deep(.question-import-dialog__row--error td) {
  background: #fef2f2;
}

.question-import-dialog__table :deep(.question-import-dialog__row--skip) {
  background: #fffbeb;
}

.question-import-dialog__table :deep(.question-import-dialog__row--skip td) {
  background: #fffbeb;
}

.question-import-dialog__analysis-warn {
  display: inline-flex;
  padding: 1px 8px;
  border-radius: 999px;
  background: #fef3c7;
  color: #b45309;
  font-size: 12px;
  font-weight: 700;
}

.question-import-dialog__progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-import-dialog__progress :deep(.el-progress) {
  flex: 1;
}

.question-import-dialog__progress-text {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.question-import-dialog__results {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fafc;
}

.question-import-dialog__results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 13px;
}

.question-import-dialog__results-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 180px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.question-import-dialog__result-item {
  display: grid;
  grid-template-columns: 54px 140px 54px 1fr;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
}

.question-import-dialog__result-line,
.question-import-dialog__result-code {
  color: #64748b;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
}

.question-import-dialog__result-status {
  font-weight: 800;
}

.question-import-dialog__result-item.is-success .question-import-dialog__result-status {
  color: #047857;
}

.question-import-dialog__result-item.is-skipped .question-import-dialog__result-status {
  color: #b45309;
}

.question-import-dialog__result-item.is-failed .question-import-dialog__result-status {
  color: #b91c1c;
}

.question-import-dialog__result-message {
  color: #475569;
}

.question-import-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
