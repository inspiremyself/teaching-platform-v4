import { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE } from '@/types/enums';
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listClasses } from '@/api/classes'
import { listSubjects } from '@/api/admin'
import type { AdminSubject } from '@/types/admin'
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
import { buildQuestionPayloadFromDraft } from '@/components/typed-editor/adapters/questionAdapter'
import { parseQuestionAnswerArray } from '@/components/typed-editor/hydrators/fromQuestion'
import type { InjectionKey } from 'vue';

export function useExamList() {
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
  subjectId: number
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
const subjectOptions = ref<AdminSubject[]>([])
const fetchSubjects = async () => {
  subjectOptions.value = await listSubjects('ACTIVE')
}
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
  scoreVisibilityMode: SCORE_VISIBILITY_MODE.IMMEDIATE,
  shuffleQuestions: false,
  subjectId: subjectOptions.value[0]?.id ?? 0,
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

const draftCount = computed(() => rows.value.filter(row => row.status === ACTIVITY_STATUS.DRAFT).length)
const publishedCount = computed(() => rows.value.filter(row => row.status === ACTIVITY_STATUS.PUBLISHED).length)
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
    form.value.scoreVisibilityMode === SCORE_VISIBILITY_MODE.AFTER_DEADLINE ||
    form.value.scoreVisibilityMode === SCORE_VISIBILITY_MODE.MANUAL_RELEASE,
)

const legacyScoreModeLabel = computed(() => {
  if (form.value.scoreVisibilityMode === SCORE_VISIBILITY_MODE.AFTER_DEADLINE) {
    return '考试截止后可见';
  }
  if (form.value.scoreVisibilityMode === SCORE_VISIBILITY_MODE.MANUAL_RELEASE) {
    return '手动发布成绩';
  }
  return String(form.value.scoreVisibilityMode);
})

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
  classOptions.value = res
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
    scoreVisibilityMode: SCORE_VISIBILITY_MODE.IMMEDIATE,
    shuffleQuestions: false,
    subjectId: subjectOptions.value[0]?.id ?? 0,
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
    scoreVisibilityMode: detail.scoreVisibilityMode ?? SCORE_VISIBILITY_MODE.IMMEDIATE,
    shuffleQuestions: detail.shuffleQuestions === true,
    // 详情不含 subjectId；编辑态默认「保持原值」，用户主动改选才提交新值
    subjectId: 0,
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

const validateTimeWindow = (startAt: string | null, endAt: string | null, durationMinutes: number, requireEndAt: boolean) => {
  const duration = Number(durationMinutes)
  if (!Number.isInteger(duration) || duration < 1 || duration > 600) {
    ElMessage.warning('考试时长需为 1–600 分钟的整数')
    return false
  }
  if (requireEndAt && !endAt) {
    ElMessage.warning('请先设置考试结束时间')
    return false
  }
  const start = startAt ? new Date(startAt).getTime() : null
  const end = endAt ? new Date(endAt).getTime() : null
  if (start !== null && Number.isNaN(start)) {
    ElMessage.warning('开始时间格式不正确')
    return false
  }
  if (end !== null && Number.isNaN(end)) {
    ElMessage.warning('结束时间格式不正确')
    return false
  }
  if (start !== null && end !== null && start >= end) {
    ElMessage.warning('开始时间必须早于结束时间')
    return false
  }
  return true
}

const durationExceedsWindow = computed(() => {
  const duration = form.value.durationMinutes
  const start = form.value.startAt ? new Date(form.value.startAt).getTime() : null
  const end = form.value.endAt ? new Date(form.value.endAt).getTime() : null
  if (!duration || start === null || end === null || Number.isNaN(start) || Number.isNaN(end)) return false
  const windowMinutes = (end - start) / 60000
  return Number.isFinite(windowMinutes) && windowMinutes > 0 && duration > windowMinutes
})

const windowTruncationHint = computed(() => {
  if (!durationExceedsWindow.value) return ''
  return `考试时长晚于窗口结束前 ${form.value.durationMinutes} 分钟，进入较晚的学生会被提前收卷。`
})

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
  if (!validateTimeWindow(form.value.startAt, form.value.endAt, form.value.durationMinutes, false)) {
    return
  }
  // 新建必填：学科列表为空或加载失败时本地拦截，不带 subjectId=0 提交
  if (!editingId.value && !form.value.subjectId) {
    ElMessage.warning('请先在学科管理中启用至少一个 ACTIVE 学科')
    return
  }
  if (form.value.scoreVisibilityMode === SCORE_VISIBILITY_MODE.IMMEDIATE && hasSubjectiveQuestion.value) {
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
      subjectId: form.value.subjectId,
      questions,
    }

    if (editingId.value) {
      const { subjectId: _omitted, ...rest } = basePayload
      const payload: UpdateExamPayload = {
        // 编辑态选中「保持原值」时省略 subjectId，避免覆盖历史 NULL / 原学科
        ...(form.value.subjectId ? basePayload : rest),
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
        status: ACTIVITY_STATUS.DRAFT,
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
  if (!validateTimeWindow(detail.startAt, detail.endAt, detail.durationMinutes, true)) {
    return
  }
  await changeExamStatus(row.id, ACTIVITY_STATUS.PUBLISHED)
  ElMessage.success('考试已发布')
  await fetchExams()
}

const closeExam = async (row: ExamListItem) => {
  await changeExamStatus(row.id, ACTIVITY_STATUS.CLOSED)
  ElMessage.success('考试已关闭')
  await fetchExams()
}

const goResults = (id: number) => {
  router.push(`/teacher/exams/${id}/results`)
}

const statusLabel = (status: string) => {
  if (status === ACTIVITY_STATUS.DRAFT) return '草稿'
  if (status === ACTIVITY_STATUS.PUBLISHED) return '已发布'
  if (status === ACTIVITY_STATUS.CLOSED) return '已关闭'
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
  if (row.status === ACTIVITY_STATUS.DRAFT) {
    return '草稿阶段，可继续补充题目、时间与考试说明。'
  }
  if (row.status === ACTIVITY_STATUS.PUBLISHED) {
    return '考试已对学生开放，建议关注提交进度与异常关闭操作。'
  }
  return '考试流程已收口，可进入结果页进行总览与主观题评分。'
}

const submissionHint = (row: ExamListItem) => {
  const count = row.submissionCount ?? 0
  if (row.status === ACTIVITY_STATUS.DRAFT) {
    return '尚未进入学生作答阶段'
  }
  if (row.status === ACTIVITY_STATUS.PUBLISHED) {
    return count > 0 ? `已有 ${count} 份提交，批改入口已开放` : '考试已发布，等待学生提交'
  }
  return count > 0 ? `共收到 ${count} 份提交，可汇总批改` : '考试已关闭，暂无提交记录'
}

/** 计划满分 = Σ 每题配置分值；只做展示，不参与提交 */
const planTotalScore = computed(() =>
  configuredQuestions.value.reduce((total, item) => total + (Number(item.score) || 0), 0),
)

onMounted(async () => {
  await Promise.all([fetchClasses(), fetchQuestions(), fetchSubjects()])
  resetForm()
  await fetchExams()
})

  return { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, computed, onMounted, ref, useRouter, ElMessage, ElMessageBox, listClasses, listQuestions, updateQuestion, changeExamStatus, createExam, getExamDetail, listExams, updateExam, QuestionConfigList, QuestionEditorDialog, QuestionPickerDialog, buildQuestionPayloadFromDraft, parseQuestionAnswerArray, router, loading, submitting, questionSubmitting, dialogVisible, pickerVisible, questionDialogVisible, editingId, rows, classOptions, questionPool, configuredQuestions, planTotalScore, legacyInlineQuestions, searchQuery, statusFilter, classFilter, activeConfiguredQuestionLocalId, form, filteredRows, draftCount, publishedCount, totalSubmissionCount, highlightedExam, highlightedExamTitle, highlightedExamMeta, headerCellStyle, currentConfiguredQuestion, currentEditorQuestion, isLegacyScoreMode, legacyScoreModeLabel, hasSubjectiveQuestion, durationExceedsWindow, windowTruncationHint, fetchExams, fetchClasses, fetchQuestions, resequenceConfiguredQuestions, mergeConfiguredQuestions, resetForm, openCreateDialog, openEditDialog, openQuestionPicker, applyPickedQuestions, handleConfiguredQuestionsReorder, handleConfiguredQuestionScoreChange, removeConfiguredQuestion, removeLegacyInlineQuestion, editConfiguredQuestion, syncConfiguredBankQuestion, submitConfiguredQuestion, submitForm, publishExam, closeExam, goResults, statusLabel, formatDateTime, formatWindow, examDescription, submissionHint, subjectOptions, fetchSubjects };
}

export type ExamListState = ReturnType<typeof useExamList>;
export const ExamListKey: InjectionKey<ExamListState> = Symbol('ExamListState');
