<template>
  <div class="page exams-console workbench-page">
    <section class="workbench-header exams-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="exams-header__eyebrow">教师控制台 / 考试闭环 / 考试管理</div>
          <h1>考试管理工作台</h1>
          <p>围绕发布、进行、收卷和批改入口维护考试闭环。</p>
        </div>
        <div class="workbench-header__actions console-actions">
          <button class="console-button console-button--secondary" type="button" @click="fetchExams" :disabled="loading">
            <span class="material-symbols-outlined">refresh</span>
            刷新列表
          </button>
          <button class="console-button console-button--primary" type="button" @click="openCreateDialog">
            <span class="material-symbols-outlined">add_circle</span>
            创建考试
          </button>
        </div>
      </div>

      <div class="workbench-meta exams-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 考试总数</span>
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 筛选结果</span>
        <span class="workbench-meta__item"><strong>{{ draftCount }}</strong> 草稿</span>
        <span class="workbench-meta__item"><strong>{{ publishedCount }}</strong> 进行中</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ totalSubmissionCount }}</strong> 累计提交</span>
        <span class="workbench-meta__item">{{ highlightedExamTitle }}</span>
      </div>
    </section>

    <div class="toolbar-card">
        <div class="toolbar-card__main">
          <div class="toolbar-search">
            <span class="material-symbols-outlined">search</span>
            <el-input v-model="searchQuery" placeholder="搜索考试标题、班级或描述" clearable />
          </div>
          <div class="toolbar-filters">
            <div class="toolbar-filter">
              <label>状态</label>
              <el-select v-model="statusFilter" clearable placeholder="全部状态">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </div>
            <div class="toolbar-filter">
              <label>班级</label>
              <el-select v-model="classFilter" clearable placeholder="全部班级">
                <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </div>
          </div>
        </div>
        <div class="toolbar-card__meta">
          <span>当前筛选结果 {{ filteredRows.length }} / {{ rows.length }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ statusFilter ? `已锁定状态 ${statusLabel(statusFilter)}` : '未锁定状态，可查看全部考试阶段' }}</span>
          <span class="toolbar-card__dot"></span>
          <span>{{ classFilter ? `已锁定班级 ${classOptions.find(c => c.id === classFilter)?.name || ''}` : '全部班级' }}</span>
        </div>
    </div>

    <div class="table-card">
        <div class="table-card__header">
          <div>
            <h2>考试清单</h2>
            <p>把阶段、时间窗、收卷量和结果入口压进同一层级，避免考试管理页显得过散。</p>
          </div>
          <div class="table-card__chips">
            <span class="table-chip">真实列表接口</span>
            <span class="table-chip">发布状态保留</span>
            <span class="table-chip">批改入口保留</span>
          </div>
        </div>

        <el-table
          :data="filteredRows"
          v-loading="loading"
          empty-text="暂无考试"
          class="exams-table"
          :header-cell-style="headerCellStyle"
        >
          <el-table-column label="考试信息" min-width="340">
            <template #default="{ row }">
              <div class="exam-cell">
                <div :class="['exam-cell__icon', `is-${row.status.toLowerCase()}`]">
                  <span class="material-symbols-outlined">quiz</span>
                </div>
                <div class="exam-cell__body">
                  <div class="exam-cell__title-row">
                    <span class="exam-cell__title">{{ row.title }}</span>
                    <span :class="['status-pill', `status-pill--${row.status.toLowerCase()}`]">
                      <span class="status-pill__dot"></span>
                      {{ statusLabel(row.status) }}
                    </span>
                  </div>
                  <div class="exam-cell__meta">
                    <span>
                      <span class="material-symbols-outlined">school</span>
                      {{ row.className || '未绑定班级' }}
                    </span>
                    <span>
                      <span class="material-symbols-outlined">schedule</span>
                      {{ formatWindow(row.startAt, row.endAt) }}
                    </span>
                  </div>
                  <p class="exam-cell__desc">{{ examDescription(row) }}</p>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="考试节奏" min-width="220">
            <template #default="{ row }">
              <div class="timeline-cell">
                <div class="timeline-item">
                  <span class="timeline-item__label">开始</span>
                  <strong>{{ formatDateTime(row.startAt) }}</strong>
                </div>
                <div class="timeline-item">
                  <span class="timeline-item__label">结束</span>
                  <strong>{{ formatDateTime(row.endAt) }}</strong>
                </div>
                <div class="timeline-item">
                  <span class="timeline-item__label">时长</span>
                  <strong>{{ row.durationMinutes }} 分钟</strong>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="收卷概览" min-width="180">
            <template #default="{ row }">
              <div class="submission-cell">
                <div class="submission-cell__count">{{ row.submissionCount ?? 0 }}</div>
                <div class="submission-cell__label">已提交答卷</div>
                <div class="submission-cell__hint">{{ submissionHint(row) }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="260" fixed="right" align="right">
            <template #default="{ row }">
              <div class="action-cell">
                <button v-if="row.status === 'DRAFT'" class="action-button action-button--ghost" type="button" @click="openEditDialog(row)">
                  编辑
                </button>
                <button v-if="row.status === 'DRAFT'" class="action-button action-button--success" type="button" @click="publishExam(row)">
                  发布
                </button>
                <button v-if="row.status === 'PUBLISHED'" class="action-button action-button--warning" type="button" @click="closeExam(row)">
                  关闭
                </button>
                <button class="action-button action-button--primary" type="button" @click="goResults(row.id)">
                  查看结果
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
    </div>

    <el-dialog v-model="dialogVisible" width="1040px" destroy-on-close class="exam-dialog">
      <div class="exam-dialog__shell">
        <header class="exam-dialog__header">
          <div>
            <p class="exam-dialog__eyebrow">考试管理工作台 / {{ editingId ? '考试维护' : '考试创建' }}</p>
            <h2>{{ editingId ? '编辑考试配置' : '创建考试' }}</h2>
            <p class="exam-dialog__desc">在同一工作面板中完成考试基础信息、时间窗与题目池编排，保留现有真实考试接口和组卷行为。</p>
          </div>
          <div class="exam-dialog__badges">
            <span class="exam-dialog__badge">{{ form.classId ? classOptions.find(c => c.id === form.classId)?.name || '已选班级' : '待选班级' }}</span>
            <span class="exam-dialog__badge exam-dialog__badge--muted">{{ configuredQuestions.length }} 题</span>
          </div>
        </header>

        <section class="exam-dialog__summary-grid">
          <article class="exam-dialog__summary-card">
            <span class="exam-dialog__label">考试窗口</span>
            <strong>{{ formatWindow(form.startAt, form.endAt) }}</strong>
            <p>开始时间、结束时间与考试时长共同决定学生侧作答区间。</p>
          </article>
          <article class="exam-dialog__summary-card">
            <span class="exam-dialog__label">组卷状态</span>
            <strong>{{ configuredQuestions.length ? `已选 ${configuredQuestions.length} 题` : '尚未选择试题' }}</strong>
            <p>复用共享题目配置组件完成选题、排序与分值微调，保存时仍按考试既有题目引用结构提交。</p>
          </article>
        </section>

        <div class="dialog-layout">
        <section class="dialog-main">
          <div class="dialog-block">
            <div class="dialog-block__title">基础信息</div>
            <el-form class="exam-dialog__form" label-position="top">
              <el-form-item label="考试标题" required>
                <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：软件体系结构期末考试" />
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="补充考试说明、注意事项或题型范围" />
              </el-form-item>
              <el-form-item label="班级" required>
                <el-select v-model="form.classId" style="width: 100%" placeholder="请选择班级">
                  <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
              <div class="dialog-grid">
                <el-form-item label="开始时间">
                  <el-date-picker
                    v-model="form.startAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ssZ"
                    placeholder="请选择开始时间"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="结束时间">
                  <el-date-picker
                    v-model="form.endAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ssZ"
                    placeholder="请选择结束时间"
                    style="width: 100%"
                  />
                </el-form-item>
              </div>
              <el-form-item label="时长(分钟)">
                <el-input-number v-model="form.durationMinutes" :min="1" :max="600" />
              </el-form-item>
              <el-form-item label="题目乱序">
                <el-switch v-model="form.shuffleQuestions" />
                <p class="exam-dialog__hint">开启后，题目顺序按学生生成；同一学生刷新后顺序保持不变。</p>
              </el-form-item>
              <el-form-item label="成绩可见性">
                <el-select v-model="form.scoreVisibilityMode" style="width: 100%">
                  <el-option label="交卷后立即可见" value="IMMEDIATE" />
                  <el-option label="教师确认后可见" value="AFTER_TEACHER_CONFIRM" />
                  <el-option
                    v-if="isLegacyScoreMode"
                    :label="legacyScoreModeLabel"
                    :value="form.scoreVisibilityMode"
                    disabled
                  />
                </el-select>
                <p v-if="form.scoreVisibilityMode === 'AFTER_TEACHER_CONFIRM'" class="exam-dialog__hint">
                  学生交卷后不会立即看到成绩。
                </p>
                <p v-if="isLegacyScoreMode" class="exam-dialog__hint exam-dialog__hint--danger">
                  当前为历史配置「{{ legacyScoreModeLabel }}」，必须改选「交卷后立即可见」或「教师确认后可见」后才能保存。
                </p>
              </el-form-item>
            </el-form>
          </div>

          <div class="dialog-block">
            <div class="dialog-block__header">
              <div>
                <div class="dialog-block__title">题目配置</div>
                <p class="dialog-block__desc">通过共享选题弹窗从题库带入题目，再在当前考试壳中调整顺序与分值。</p>
              </div>
              <div class="dialog-block__actions">
                <el-button plain @click="openQuestionPicker">从题库挑题</el-button>
              </div>
            </div>

            <div v-if="legacyInlineQuestions.length" class="legacy-inline-block">
              <el-alert
                v-for="item in legacyInlineQuestions"
                :key="item.localId"
                type="warning"
                :closable="false"
                show-icon
                :title="`该题不支持，请删除后从题库重新选择：${item.stem}`"
              >
                <el-button link type="danger" @click="removeLegacyInlineQuestion(item.localId)">删除该题</el-button>
              </el-alert>
            </div>

            <QuestionConfigList
              :items="configuredQuestions"
              @edit="editConfiguredQuestion"
              @remove="removeConfiguredQuestion"
              @reorder="handleConfiguredQuestionsReorder"
              @score-change="handleConfiguredQuestionScoreChange"
            />
          </div>
        </section>

        <aside class="dialog-side">
          <section class="selection-panel">
            <div class="selection-panel__header">
              <div>
                <div class="selection-panel__title">已选题目</div>
                <p>考试题目全部来自题库；这里复用共享题目配置清单管理真实组卷顺序与分值。</p>
              </div>
              <span class="selection-panel__badge">{{ configuredQuestions.length }} 题</span>
            </div>
            <div v-if="configuredQuestions.length" class="selection-list">
              <article v-for="row in configuredQuestions" :key="row.localId" class="selection-card">
                <div class="selection-card__stem">{{ row.stem }}</div>
                <div class="selection-card__controls">
                  <div>
                    <label>题型</label>
                    <strong>{{ row.questionType }}</strong>
                  </div>
                  <div>
                    <label>当前分值</label>
                    <strong>{{ row.score }} 分</strong>
                  </div>
                </div>
              </article>
            </div>
            <el-empty v-else description="请先在左侧题目池中选择试题" />
          </section>
        </aside>
      </div>
        <footer class="exam-dialog__footer">
          <div class="exam-dialog__footer-note">保留创建 / 编辑考试、题目选择、排序与分值保存逻辑，仅强化教师台工作面板样式与信息层级。</div>
          <div class="exam-dialog__footer-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="legacyInlineQuestions.length > 0 || isLegacyScoreMode"
              @click="submitForm"
            >保存考试</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <QuestionEditorDialog
      v-model:visible="questionDialogVisible"
      :initial-question="currentEditorQuestion"
      :loading="questionSubmitting"
      :editing="Boolean(currentConfiguredQuestion)"
      description="在考试壳中直接复用共享题目编辑器维护题库题资产；保存后会同步到当前考试配置清单。"
      submit-text="保存题库题"
      :show-asset-fields="true"
      :show-config-fields="false"
      @saved="submitConfiguredQuestion"
    />

    <QuestionPickerDialog
      v-model:visible="pickerVisible"
      validate-judge-format
      @confirm="applyPickedQuestions"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listClasses } from '@/api/classes'
import { listQuestions, updateQuestion } from '@/api/questions'
import { changeExamStatus, createExam, getExamDetail, listExams, updateExam } from '@/api/exams'
import QuestionConfigList from '@/components/question-config/QuestionConfigList.vue'
import QuestionEditorDialog, {
  type QuestionEditorDialogSubmitPayload,
  type QuestionEditorDialogValue,
} from '@/components/question-config/QuestionEditorDialog.vue'
import QuestionPickerDialog from '@/components/question-config/QuestionPickerDialog.vue'
import type { TeachingClass } from '@/types/class'
import type { ConfiguredQuestionItem } from '@/types/question-config'
import type { CreateExamPayload, ExamListItem, ExamQuestionView, ScoreVisibilityMode, UpdateExamPayload } from '@/types/exam'
import type { QuestionItem } from '@/types/question'
import { buildQuestionPayloadFromDraft } from '../components/typed-editor/adapters/questionAdapter'
import { parseQuestionAnswerArray } from '../components/typed-editor/hydrators/fromQuestion'

/** 编辑态下允许历史内联题（questionId 为 null）进入只读分流 */
type EditableExamQuestion = ExamQuestionView & { questionId: number | null }

interface ExamFormState {
  title: string
  description: string
  classId: number
  startAt: string | null
  endAt: string | null
  durationMinutes: number
  scoreVisibilityMode: ScoreVisibilityMode
  shuffleQuestions: boolean
}

/** 历史成绩可见性值到展示文案的映射 */
const LEGACY_SCORE_MODE_LABELS: Record<string, string> = {
  AFTER_DEADLINE: '考试截止后可见',
  MANUAL_RELEASE: '手动发布成绩',
}

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const questionSubmitting = ref(false)
const dialogVisible = ref(false)
const pickerVisible = ref(false)
const questionDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const rows = ref<ExamListItem[]>([])
const classOptions = ref<TeachingClass[]>([])
const questionPool = ref<QuestionItem[]>([])
const configuredQuestions = ref<ConfiguredQuestionItem[]>([])
/** 历史内联题：不进入组卷提交，必须先删除并从题库重新选择 */
const legacyInlineQuestions = ref<Array<{ localId: string; stem: string }>>([])
const searchQuery = ref('')
const statusFilter = ref<ExamListItem['status'] | ''>('')
const classFilter = ref<number | null>(null)
const activeConfiguredQuestionLocalId = ref<string | null>(null)

const form = ref<ExamFormState>({
  title: '',
  description: '',
  classId: 0 as number,
  startAt: null as string | null,
  endAt: null as string | null,
  durationMinutes: 60,
  scoreVisibilityMode: 'IMMEDIATE',
  shuffleQuestions: false,
})

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return rows.value.filter(row => {
    const matchesKeyword = !keyword || [row.title, row.className ?? '', examDescription(row)].some(item => item.toLowerCase().includes(keyword))
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value
    const matchesClass = !classFilter.value || row.classId === classFilter.value
    return matchesKeyword && matchesStatus && matchesClass
  })
})

const draftCount = computed(() => rows.value.filter(row => row.status === 'DRAFT').length)
const publishedCount = computed(() => rows.value.filter(row => row.status === 'PUBLISHED').length)
const totalSubmissionCount = computed(() => rows.value.reduce((sum, row) => sum + (row.submissionCount ?? 0), 0))
const highlightedExam = computed(() => filteredRows.value[0] ?? rows.value[0] ?? null)
const highlightedExamTitle = computed(() => highlightedExam.value?.title ?? '暂无考试记录')
const highlightedExamMeta = computed(() => {
  if (!highlightedExam.value) {
    return '创建考试后即可在这里看到发布节奏、班级归属和批改入口。'
  }
  return `${highlightedExam.value.className || '未绑定班级'} · ${statusLabel(highlightedExam.value.status)} · ${submissionHint(highlightedExam.value)}`
})

const headerCellStyle = () => ({
  background: '#f8fafc',
  color: '#475569',
  fontWeight: 700,
})

const currentConfiguredQuestion = computed(
  () => configuredQuestions.value.find(item => item.localId === activeConfiguredQuestionLocalId.value) ?? null,
)

const currentEditorQuestion = computed<QuestionEditorDialogValue | null>(() => {
  if (!questionDialogVisible.value) {
    return null
  }

  const current = currentConfiguredQuestion.value
  if (!current?.questionBankId) {
    return null
  }

  const matchedRow = questionPool.value.find(row => row.id === current.questionBankId)
  if (!matchedRow) {
    return null
  }

  return {
    ...current,
    code: matchedRow.code,
    difficulty: matchedRow.difficulty as QuestionEditorDialogValue['difficulty'],
    defaultScore: matchedRow.defaultScore,
    analysisText: matchedRow.analysisText,
    options: parseQuestionAnswerArray(matchedRow.optionsJson).map((option, index) => {
      if (option && typeof option === 'object') {
        const record = option as Record<string, unknown>
        return {
          key: String(record.key ?? String.fromCharCode(65 + index)),
          label: String(record.content ?? record.label ?? record.text ?? ''),
        }
      }

      return {
        key: String.fromCharCode(65 + index),
        label: String(option ?? ''),
      }
    }),
    answerJson: matchedRow.answerJson,
  }
})

/** 是否停留在旧版成绩可见性值上，需强制改选后才能保存 */
const isLegacyScoreMode = computed(
  () =>
    form.value.scoreVisibilityMode === 'AFTER_DEADLINE' ||
    form.value.scoreVisibilityMode === 'MANUAL_RELEASE',
)

const legacyScoreModeLabel = computed(
  () => LEGACY_SCORE_MODE_LABELS[form.value.scoreVisibilityMode] ?? String(form.value.scoreVisibilityMode),
)

/** 组卷中是否包含主观题，用于「立即可见」保存前提示 */
const hasSubjectiveQuestion = computed(() =>
  configuredQuestions.value.some(item => !['SINGLE', 'MULTI', 'JUDGE'].includes(item.questionType)),
)

const fetchExams = async () => {
  loading.value = true
  try {
    rows.value = await listExams()
  } finally {
    loading.value = false
  }
}

const fetchClasses = async () => {
  const res = await listClasses()
  classOptions.value = res.items
}

const fetchQuestions = async () => {
  questionPool.value = await listQuestions()
}

const resequenceConfiguredQuestions = (items: ConfiguredQuestionItem[]) =>
  items.map((item, index) => ({
    ...item,
    sortOrder: index + 1,
  }))

const mergeConfiguredQuestions = (items: ConfiguredQuestionItem[]) => {
  const existingByBankId = new Map(
    configuredQuestions.value.filter(item => item.questionBankId).map(item => [item.questionBankId, item]),
  )
  const merged = items
    .filter(item => item.questionBankId)
    .map(item => existingByBankId.get(item.questionBankId) ?? item)

  configuredQuestions.value = resequenceConfiguredQuestions(merged)
}

const resetForm = () => {
  form.value = {
    title: '',
    description: '',
    classId: classOptions.value[0]?.id ?? 0,
    startAt: null,
    endAt: null,
    durationMinutes: 60,
    scoreVisibilityMode: 'IMMEDIATE',
    shuffleQuestions: false,
  }
  configuredQuestions.value = []
  legacyInlineQuestions.value = []
  editingId.value = null
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = async (row: ExamListItem) => {
  editingId.value = row.id
  const detail = await getExamDetail(row.id)
  form.value = {
    title: detail.title,
    description: detail.description || '',
    classId: detail.classId,
    startAt: detail.startAt,
    endAt: detail.endAt,
    durationMinutes: detail.durationMinutes,
    scoreVisibilityMode: detail.scoreVisibilityMode ?? 'IMMEDIATE',
    shuffleQuestions: detail.shuffleQuestions === true,
  }

  // 历史内联题（questionId 为 null）不能进入组卷提交，只做只读分流提示
  const rawQuestions = (detail.questions || []) as EditableExamQuestion[]
  const normalQuestions: EditableExamQuestion[] = []
  legacyInlineQuestions.value = []
  for (const [index, item] of rawQuestions.entries()) {
    if (item.questionId == null) {
      // 用数组下标而非 sortOrder 生成 localId：历史数据允许 sortOrder 重复，下标天然唯一
      legacyInlineQuestions.value.push({ localId: `legacy-${index}`, stem: item.stem })
    } else {
      normalQuestions.push(item)
    }
  }

  configuredQuestions.value = resequenceConfiguredQuestions(
    normalQuestions.map((q, index) => ({
      localId: `bank-${q.questionId}`,
      questionBankId: q.questionId,
      sourceType: 'BANK',
      questionType: q.questionType ?? q.type ?? '',
      stem: q.stem,
      sortOrder: index + 1,
      score: q.questionScore ?? q.score ?? 0,
      options: q.options ?? parseQuestionAnswerArray(q.optionsJson ?? null).map((option, optionIndex) => {
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
      answerJson: q.answerJson ?? null,
      scoringConfigJson: null,
    })),
  )
  dialogVisible.value = true
}

const openQuestionPicker = () => {
  pickerVisible.value = true
}

const applyPickedQuestions = (items: ConfiguredQuestionItem[]) => {
  mergeConfiguredQuestions(items)
  ElMessage.success(`已带入 ${items.length} 道题目配置`)
}

const handleConfiguredQuestionsReorder = (items: ConfiguredQuestionItem[]) => {
  configuredQuestions.value = items
}

const handleConfiguredQuestionScoreChange = ({ localId, score }: { localId: string; score: number }) => {
  configuredQuestions.value = configuredQuestions.value.map(item => (item.localId === localId ? { ...item, score } : item))
}

const removeConfiguredQuestion = (localId: string) => {
  configuredQuestions.value = resequenceConfiguredQuestions(
    configuredQuestions.value.filter(item => item.localId !== localId),
  )
}

const removeLegacyInlineQuestion = (localId: string) => {
  legacyInlineQuestions.value = legacyInlineQuestions.value.filter(item => item.localId !== localId)
}

const editConfiguredQuestion = async (item: ConfiguredQuestionItem) => {
  if (!item.questionBankId) {
    return
  }
  activeConfiguredQuestionLocalId.value = item.localId

  if (!questionPool.value.length) {
    await fetchQuestions()
  }

  const matched = questionPool.value.find(row => row.id === item.questionBankId)
  if (!matched) {
    ElMessage.warning('未找到对应题库题，请刷新题库后重试。')
    return
  }

  questionDialogVisible.value = true
}

const syncConfiguredBankQuestion = (question: QuestionItem) => {
  configuredQuestions.value = configuredQuestions.value.map(item =>
    item.questionBankId === question.id
      ? {
          ...item,
          questionType: question.type,
          stem: question.stem,
          options: parseQuestionAnswerArray(question.optionsJson).map((option, index) => {
            if (option && typeof option === 'object') {
              const record = option as Record<string, unknown>
              return {
                key: String(record.key ?? String.fromCharCode(65 + index)),
                label: String(record.content ?? record.label ?? record.text ?? ''),
              }
            }

            return {
              key: String.fromCharCode(65 + index),
              label: String(option ?? ''),
            }
          }),
          answerJson: question.answerJson,
        }
      : item,
  )
}

const submitConfiguredQuestion = async (editorPayload: QuestionEditorDialogSubmitPayload) => {
  const current = currentConfiguredQuestion.value
  if (!current?.questionBankId) {
    ElMessage.warning('当前题目未绑定题库题。')
    return
  }

  questionSubmitting.value = true
  try {
    const payload = buildQuestionPayloadFromDraft(editorPayload.draft, {
      code: editorPayload.code,
      type: editorPayload.type,
      difficulty: editorPayload.difficulty,
      defaultScore: editorPayload.defaultScore,
      analysisText: editorPayload.analysisText,
    })
    await updateQuestion(current.questionBankId, payload)
    await fetchQuestions()
    const refreshed = questionPool.value.find(row => row.id === current.questionBankId)
    if (refreshed) {
      syncConfiguredBankQuestion(refreshed)
    }
    ElMessage.success('题库题更新成功')

    questionDialogVisible.value = false
    activeConfiguredQuestionLocalId.value = null
  } finally {
    questionSubmitting.value = false
  }
}

const submitForm = async () => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入考试标题')
    return
  }
  if (!form.value.classId) {
    ElMessage.warning('请选择班级')
    return
  }
  if (legacyInlineQuestions.value.length) {
    ElMessage.error('请先将题目保存到题库后再组卷')
    return
  }
  if (!configuredQuestions.value.length) {
    ElMessage.warning('请至少选择一道题目')
    return
  }
  if (isLegacyScoreMode.value) {
    ElMessage.error('请先将成绩可见性改为「交卷后立即可见」或「教师确认后可见」')
    return
  }
  if (form.value.scoreVisibilityMode === 'IMMEDIATE' && hasSubjectiveQuestion.value) {
    await ElMessageBox.alert('主观题需人工批改，交卷后不会立即出成绩', '提示', {
      confirmButtonText: '继续保存',
      type: 'warning',
    })
  }

  submitting.value = true
  try {
    const questions = configuredQuestions.value
      .filter(item => item.sourceType === 'BANK' && item.questionBankId)
      .map(item => ({
        questionId: item.questionBankId as number,
        sourceType: 'BANK' as const,
        sortOrder: item.sortOrder,
        questionScore: item.score,
      }))

    const basePayload = {
      title: form.value.title.trim(),
      description: form.value.description.trim(),
      classId: form.value.classId,
      durationMinutes: form.value.durationMinutes,
      scoreVisibilityMode: form.value.scoreVisibilityMode,
      shuffleQuestions: form.value.shuffleQuestions,
      questions,
    }

    if (editingId.value) {
      const payload: UpdateExamPayload = {
        ...basePayload,
        startAt: form.value.startAt || undefined,
        endAt: form.value.endAt || undefined,
      }
      await updateExam(editingId.value, payload)
      ElMessage.success(`考试更新成功，当前共保存 ${questions.length} 道题目`)
    } else {
      const payload: CreateExamPayload = {
        ...basePayload,
        startAt: form.value.startAt || undefined,
        endAt: form.value.endAt || undefined,
        status: 'DRAFT',
      }
      await createExam(payload)
      ElMessage.success(`考试创建成功，并写入了 ${questions.length} 道题目`)
    }
    dialogVisible.value = false
    configuredQuestions.value = []
    legacyInlineQuestions.value = []
    await fetchExams()
  } finally {
    submitting.value = false
  }
}

const publishExam = async (row: ExamListItem) => {
  const detail = await getExamDetail(row.id)
  if ((detail.questions || []).some(q => q.questionId == null)) {
    ElMessage.error('请先将题目保存到题库后再组卷')
    return
  }
  await changeExamStatus(row.id, 'PUBLISHED')
  ElMessage.success('考试已发布')
  await fetchExams()
}

const closeExam = async (row: ExamListItem) => {
  await changeExamStatus(row.id, 'CLOSED')
  ElMessage.success('考试已关闭')
  await fetchExams()
}

const goResults = (id: number) => {
  router.push(`/teacher/exams/${id}/results`)
}

const statusLabel = (status: string) => {
  if (status === 'DRAFT') return '草稿'
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'CLOSED') return '已关闭'
  return status || '未知'
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 16)
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const formatWindow = (startAt?: string | null, endAt?: string | null) => {
  if (!startAt && !endAt) {
    return '未配置考试窗口'
  }
  return `${formatDateTime(startAt)} 至 ${formatDateTime(endAt)}`
}

const examDescription = (row: ExamListItem) => {
  if (row.status === 'DRAFT') {
    return '草稿阶段，可继续补充题目、时间与考试说明。'
  }
  if (row.status === 'PUBLISHED') {
    return '考试已对学生开放，建议关注提交进度与异常关闭操作。'
  }
  return '考试流程已收口，可进入结果页进行总览与主观题评分。'
}

const submissionHint = (row: ExamListItem) => {
  const count = row.submissionCount ?? 0
  if (row.status === 'DRAFT') {
    return '尚未进入学生作答阶段'
  }
  if (row.status === 'PUBLISHED') {
    return count > 0 ? `已有 ${count} 份提交，批改入口已开放` : '考试已发布，等待学生提交'
  }
  return count > 0 ? `共收到 ${count} 份提交，可汇总批改` : '考试已关闭，暂无提交记录'
}

onMounted(async () => {
  await Promise.all([fetchClasses(), fetchQuestions()])
  resetForm()
  await fetchExams()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exams-console {
  color: #0f172a;
}

.console-hero {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-hero__main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.console-breadcrumb .is-current {
  color: #0f766e;
  font-weight: 700;
}

.console-title-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
}

.console-title-row h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 800;
}

.console-title-row p {
  margin: 10px 0 0;
  color: #64748b;
  max-width: 720px;
}

.console-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.console-button {
  border: none;
  border-radius: 18px;
  padding: 12px 18px;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.console-button--secondary {
  background: #eef2ff;
  color: #334155;
}

.console-button--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 16px 36px rgba(15, 118, 110, 0.24);
}

.context-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border: 1px solid #dbeafe;
  background: linear-gradient(135deg, #eff6ff, #ecfeff 55%, #f8fafc);
  border-radius: 28px;
  padding: 22px 24px;
}

.context-strip__lead {
  display: flex;
  gap: 16px;
  align-items: center;
}

.context-strip__badge {
  width: 56px;
  height: 56px;
  border-radius: 20px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(15, 118, 110, 0.24);
}

.context-strip__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.context-strip__value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 800;
}

.context-strip__meta {
  margin-top: 6px;
  color: #475569;
}

.context-strip__stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: stretch;
}

.mini-stat {
  min-width: 120px;
  border-radius: 20px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.mini-stat__label {
  font-size: 12px;
  color: #64748b;
}

.mini-stat__value {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.console-grid {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.console-summaries {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  border-radius: 26px;
  padding: 24px;
  color: #fff;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.09);
}

.summary-card--teal {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.summary-card--slate {
  background: linear-gradient(135deg, #0f172a, #334155);
}

.summary-card--line {
  background: linear-gradient(135deg, #1d4ed8, #38bdf8);
}

.summary-card__eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.82;
}

.summary-card__value {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 800;
}

.summary-card__desc {
  margin-top: 10px;
  font-size: 13px;
  opacity: 0.78;
}

.toolbar-card,
.table-card,
.selection-panel {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.06);
}

.toolbar-card {
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-card__main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.toolbar-search {
  flex: 1;
  min-width: 260px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 20px;
  background: #f8fafc;
  padding: 0 16px;
  border: 1px solid #e2e8f0;
}

.toolbar-search > .material-symbols-outlined {
  color: #64748b;
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding-left: 0;
}

.toolbar-filters {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.toolbar-filter {
  min-width: 170px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toolbar-filter label {
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #64748b;
}

.toolbar-card__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #14b8a6;
}

.table-card {
  padding: 18px;
}

.table-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.table-card__header h2 {
  margin: 0;
  font-size: 22px;
}

.table-card__header p {
  margin: 8px 0 0;
  color: #64748b;
}

.table-card__chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.exam-cell {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.exam-cell__icon {
  width: 50px;
  height: 50px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.exam-cell__icon.is-draft {
  background: #f1f5f9;
  color: #475569;
}

.exam-cell__icon.is-published {
  background: #ecfdf5;
  color: #0f766e;
}

.exam-cell__icon.is-closed {
  background: #eff6ff;
  color: #1d4ed8;
}

.exam-cell__body {
  min-width: 0;
}

.exam-cell__title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.exam-cell__title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.status-pill--draft {
  background: #f1f5f9;
  color: #475569;
}

.status-pill--published {
  background: #ecfdf5;
  color: #0f766e;
}

.status-pill--closed {
  background: #eff6ff;
  color: #1d4ed8;
}

.exam-cell__meta,
.timeline-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-cell__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 13px;
}

.exam-cell__meta .material-symbols-outlined {
  font-size: 16px;
}

.exam-cell__desc {
  margin: 10px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.timeline-item {
  padding: 10px 12px;
  border-radius: 16px;
  background: #f8fafc;
}

.timeline-item__label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.timeline-item strong {
  color: #0f172a;
}

.submission-cell {
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
}

.submission-cell__count {
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.submission-cell__label {
  margin-top: 4px;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.submission-cell__hint {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.action-cell {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.action-button {
  border: none;
  border-radius: 14px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.action-button--ghost {
  background: #f8fafc;
  color: #334155;
}

.action-button--success {
  background: #ecfdf5;
  color: #0f766e;
}

.action-button--warning {
  background: #fff7ed;
  color: #c2410c;
}

.action-button--primary {
  background: #0f766e;
  color: #fff;
}

.exam-dialog :deep(.el-dialog) {
  border-radius: 30px;
  overflow: hidden;
}

.exam-dialog :deep(.el-dialog__header) {
  display: none;
}

.exam-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.exam-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.exam-dialog__header,
.exam-dialog__summary-card,
.exam-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
}

.exam-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(239, 246, 255, 0.96));
}

.exam-dialog__eyebrow,
.exam-dialog__label {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.exam-dialog__header h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 28px;
  font-weight: 800;
}

.exam-dialog__desc {
  margin: 8px 0 0;
  max-width: 620px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.exam-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.exam-dialog__badge {
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

.exam-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.exam-dialog__summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.exam-dialog__summary-card {
  padding: 18px;
}

.exam-dialog__summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.exam-dialog__summary-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.95fr);
  gap: 20px;
}

.dialog-main,
.dialog-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dialog-block,
.selection-panel {
  padding: 18px;
}

.dialog-block__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.dialog-block {
  border-radius: 24px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.dialog-block__title,
.selection-panel__title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 0;
}

.dialog-block__desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.dialog-block__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.exam-dialog__hint {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.exam-dialog__hint--danger {
  color: #b91c1c;
  font-weight: 600;
}

.legacy-inline-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.exam-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.exam-dialog__form :deep(.el-form-item + .el-form-item) {
  margin-top: 14px;
}

.exam-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.exam-dialog__form :deep(.el-input__wrapper),
.exam-dialog__form :deep(.el-select__wrapper),
.exam-dialog__form :deep(.el-textarea__inner),
.exam-dialog__form :deep(.el-input-number) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.selection-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.selection-panel__header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.selection-panel__badge {
  padding: 6px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.selection-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.selection-card {
  padding: 14px;
  border-radius: 18px;
  background: #f8fafc;
}

.selection-card__stem {
  font-size: 13px;
  line-height: 1.6;
  color: #0f172a;
}

.selection-card__controls {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.selection-card__controls label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.selection-card__controls strong {
  color: #0f172a;
  font-size: 13px;
}

.exam-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.exam-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.exam-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .console-summaries,
  .dialog-layout {
    grid-template-columns: 1fr;
  }

  .toolbar-card__main,
  .table-card__header,
  .context-strip,
  .console-title-row {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 768px) {
  .console-summaries,
  .dialog-grid,
  .selection-card__controls,
  .exam-dialog__summary-grid {
    grid-template-columns: 1fr;
  }

  .context-strip__stats {
    width: 100%;
  }

  .mini-stat {
    flex: 1;
  }

  .exam-dialog__header,
  .exam-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .exam-dialog__badges {
    justify-content: flex-start;
  }
}
</style>
