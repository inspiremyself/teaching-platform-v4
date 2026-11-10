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
        上传题目 JSON 文件（支持单选 / 多选 / 判断）。任一行校验错误则整批不导入；已存在的编码与文件内重复编码自动跳过。
      </p>

      <div class="question-import-dialog__toolbar">
        <el-button :disabled="importing" @click="downloadQuestionImportTemplate">下载规范示例</el-button>
        <span class="question-import-dialog__toolbar-note">示例文件自包含字段要求、编码要求与 JSON Schema，上传的文件即模板格式。</span>
      </div>

      <section class="question-import-dialog__file-panel">
        <div class="question-import-dialog__file-panel-title">JSON 文件导入</div>
        <div
          :class="['question-import-dialog__file-panel-drop', { 'is-dragover': isDragOver }]"
          @dragenter.prevent="handleImportDragEnter"
          @dragover.prevent="handleImportDragOver"
          @dragleave.prevent="handleImportDragLeave"
          @drop.prevent="handleImportDrop"
        >
          <div class="question-import-dialog__file-panel-meta">
            <strong>{{ importFileName || '尚未选择文件' }}</strong>
            <span>已解析 {{ parseResult?.rows.length ?? 0 }} 道题</span>
          </div>
          <div class="question-import-dialog__file-panel-actions">
            <el-upload
              accept=".json"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleImportFileChange"
            >
              <el-button type="primary" plain :loading="parsing" :disabled="importing">选择 JSON 文件</el-button>
            </el-upload>
            <el-button v-if="selectedImportFile" :disabled="importing" @click="clearImportFile">清空文件</el-button>
          </div>
        </div>
        <div class="question-import-dialog__file-panel-tips">
          <span>可直接拖拽 .json 文件到此处</span>
          <span>示例题为格式演示，导入前请替换，否则会写入真实题库</span>
        </div>
      </section>

      <QuestionImportSpecPanel />

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
import type { UploadFile } from 'element-plus'
import { createQuestion, listQuestions } from '@/api/questions'
import { ApiError } from '@/utils/request'
import { normalizeImportCode, parseImportDocument } from '@/utils/questionImport'
import {
  buildFailedItemsDocument,
  downloadQuestionImportTemplate,
  stripBom,
  validateImportFileMeta,
} from '@/utils/questionImportSpec'
import QuestionImportSpecPanel from './QuestionImportSpecPanel.vue'
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

const dialogVisible = computed({
  get: () => props.visible,
  set: value => emit('update:visible', value),
})

const parsing = ref(false)
const importing = ref(false)
const fatalError = ref('')
const parseResult = ref<ImportParseResult | null>(null)
const importResults = ref<ImportResultRow[]>([])
/** 本轮导入是否已执行过，用于「再次导入」文案 */
const hasImportRound = ref(false)
const progress = ref({ current: 0, total: 0 })
const selectedImportFile = ref<File | null>(null)
const importFileName = ref('')
const isDragOver = ref(false)
/** 并发文件选择快照：丢弃过期请求的预览写入 */
let importFileSeq = 0

const progressPercentage = computed(() =>
  progress.value.total ? Math.round((progress.value.current / progress.value.total) * 100) : 0,
)

const importDisabled = computed(() => {
  if (importing.value) return true
  const result = parseResult.value
  if (!result) return true
  return result.readyCount === 0
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

/** 重置本轮导入进度与结果 */
const resetRoundState = () => {
  importResults.value = []
  hasImportRound.value = false
  progress.value = { current: 0, total: 0 }
}

const clearImportFile = () => {
  selectedImportFile.value = null
  importFileName.value = ''
  parseResult.value = null
  fatalError.value = ''
  isDragOver.value = false
  resetRoundState()
}

/** 选择 / 拖拽文件后自动读取、判重并解析预览 */
const applyImportFile = async (file: File) => {
  const seq = ++importFileSeq
  const metaError = validateImportFileMeta(file.name, file.size)
  if (metaError) {
    ElMessage.warning(metaError)
    clearImportFile()
    return
  }

  parsing.value = true
  try {
    const text = stripBom(await file.text())
    const items = await listQuestions().catch(() => null)
    if (seq !== importFileSeq) return
    if (!items) {
      clearImportFile()
      return
    }
    const existingCodes = new Set(items.map(item => normalizeImportCode(item.code)))
    const { result, fatalError: error } = parseImportDocument(text, existingCodes)
    selectedImportFile.value = file
    importFileName.value = file.name
    parseResult.value = result
    fatalError.value = error
    resetRoundState()
  } catch (error) {
    if (seq !== importFileSeq) return
    clearImportFile()
    ElMessage.error(error instanceof Error ? error.message : '导入文件解析失败，请检查文件内容')
  } finally {
    if (seq === importFileSeq) {
      parsing.value = false
      isDragOver.value = false
    }
  }
}

const handleImportFileChange = async (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw
  if (!rawFile) return
  await applyImportFile(rawFile)
}

const handleImportDragEnter = () => {
  isDragOver.value = true
}

const handleImportDragOver = () => {
  isDragOver.value = true
}

const handleImportDragLeave = (event: DragEvent) => {
  const currentTarget = event.currentTarget
  const relatedTarget = event.relatedTarget
  if (currentTarget instanceof HTMLElement && relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) {
    return
  }
  isDragOver.value = false
}

const handleImportDrop = async (event: DragEvent) => {
  const file = event.dataTransfer?.files?.[0]
  if (!file) {
    isDragOver.value = false
    return
  }
  await applyImportFile(file)
}

/** 将校验通过的草稿转换为单题新增接口所需的 payload */
const toPayload = (draft: ImportQuestionDraft): SaveQuestionPayload => ({
  code: draft.code,
  type: draft.type,
  stem: draft.stem,
  difficulty: draft.difficulty,
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

/** 复制失败项 wrapper 文档，可直接保存为 .json 重新上传 */
const copyFailedItems = async () => {
  const failedDrafts = importResults.value
    .filter(item => item.status === 'FAILED' && item.draft)
    .map(item => item.draft)
    .filter((draft): draft is ImportQuestionDraft => !!draft)
  if (!failedDrafts.length) {
    ElMessage.info('当前没有失败项')
    return
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(buildFailedItemsDocument(failedDrafts), null, 2))
    ElMessage.success('失败项已复制，可直接保存为 .json 文件重新上传')
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
    selectedImportFile.value = null
    importFileName.value = ''
    isDragOver.value = false
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

.question-import-dialog__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-import-dialog__toolbar-note {
  color: #94a3b8;
  font-size: 12px;
}

.question-import-dialog__file-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-import-dialog__file-panel-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.question-import-dialog__file-panel-drop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.question-import-dialog__file-panel-drop.is-dragover {
  border-color: #0d9488;
  background: #f0fdfa;
}

.question-import-dialog__file-panel-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #475569;
  font-size: 12px;
}

.question-import-dialog__file-panel-meta strong {
  color: #0f172a;
  font-size: 13px;
}

.question-import-dialog__file-panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-import-dialog__file-panel-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
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
