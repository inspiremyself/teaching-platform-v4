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
import {
  mapOptionsForList,
  mergeConfiguredQuestions,
  planTotalScore as sumPlanTotalScore,
  resequenceConfiguredQuestions,
} from '@/utils/questionConfig'
import { useWorkbenchList } from '@/views/teacher/components/workbench/useWorkbench'
import { resolveStatusPresentation } from '@/components/ui/statusPresentation'
import { formatDateTime } from '@/utils/format'
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
    options: mapOptionsForList(matchedRow.optionsJson),
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
    await workbench.refresh()
    rows.value = workbench.rows.value.map(row => row.raw as ExamListItem)
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
      options: q.options ?? mapOptionsForList(q.optionsJson),
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
  configuredQuestions.value = mergeConfiguredQuestions(configuredQuestions.value, items, { includeInline: false })
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
          options: mapOptionsForList(question.optionsJson),
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

const statusOf = (status: string) => resolveStatusPresentation({ module: 'exam', kind: 'activity', scene: 'teacher', status })

const statusLabel = (status: string) => (status ? statusOf(status).label : '未知')

const formatWindow = (startAt?: string | null, endAt?: string | null) => {
  if (!startAt && !endAt) {
    return '未配置考试窗口'
  }
  return `${formatDateTime(startAt, { fallback: '未设置' })} 至 ${formatDateTime(endAt, { fallback: '未设置' })}`
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
const planTotalScore = computed(() => sumPlanTotalScore(configuredQuestions.value, item => item.score))

const workbench = useWorkbenchList<ExamListItem>({
  domain: 'EXAM',
  fetchRows: () => listExams(),
  handlers: {
    steps: () => {},
    viewResult: row => goResults(Number(row.id)),
    edit: row => openEditDialog(row.raw as ExamListItem),
    publish: row => publishExam(row.raw as ExamListItem),
    close: row => closeExam(row.raw as ExamListItem),
  },
})

onMounted(async () => {
  await Promise.allSettled([fetchClasses(), fetchQuestions(), fetchSubjects()])
  resetForm()
  await fetchExams()
})

  return { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, computed, onMounted, ref, useRouter, ElMessage, ElMessageBox, listClasses, listQuestions, updateQuestion, changeExamStatus, createExam, getExamDetail, listExams, updateExam, QuestionConfigList, QuestionEditorDialog, QuestionPickerDialog, buildQuestionPayloadFromDraft, router, loading, submitting, questionSubmitting, dialogVisible, pickerVisible, questionDialogVisible, editingId, rows, classOptions, questionPool, configuredQuestions, planTotalScore, legacyInlineQuestions, activeConfiguredQuestionLocalId, form, headerCellStyle, currentConfiguredQuestion, currentEditorQuestion, isLegacyScoreMode, legacyScoreModeLabel, hasSubjectiveQuestion, durationExceedsWindow, windowTruncationHint, fetchExams, fetchClasses, fetchQuestions, resetForm, openCreateDialog, openEditDialog, openQuestionPicker, applyPickedQuestions, handleConfiguredQuestionsReorder, handleConfiguredQuestionScoreChange, removeConfiguredQuestion, removeLegacyInlineQuestion, editConfiguredQuestion, syncConfiguredBankQuestion, submitConfiguredQuestion, submitForm, publishExam, closeExam, goResults, statusLabel, formatWindow, submissionHint, subjectOptions, fetchSubjects, workbench };
}

export type ExamListState = ReturnType<typeof useExamList>;
export const ExamListKey: InjectionKey<ExamListState> = Symbol('ExamListState');
