import { ACTIVITY_STATUS, EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QuestionAnswerShell from '@/views/student/components/lab-answer/QuestionAnswerShell.vue'
import ChoiceAnswerEditor from '@/views/student/components/lab-answer/ChoiceAnswerEditor.vue'
import FillBlankInlineEditor from '@/views/student/components/lab-answer/FillBlankInlineEditor.vue'
import TextAnswerEditor from '@/views/student/components/lab-answer/TextAnswerEditor.vue'
import CodeLabAnswerEditor from '@/views/student/components/lab-answer/CodeLabAnswerEditor.vue'
import { buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromAnswerText } from '@/views/student/components/lab-answer/answerPayload'
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from '@/views/student/components/lab-answer/types'
import { getExamDetail, startExam, submitExam, saveExamDraft } from '@/api/exams'
import { ApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import { shuffleExamQuestions } from '@/utils/examShuffle'
import { parseQuestionOptions } from '@/utils/examAnswer'
import type { ExamAnswerDetail, ExamDetail, ExamQuestionView } from '@/types/exam'
import type { StudentChoiceOption, StudentLabStepItem } from '@/types/lab'
import type { InjectionKey } from 'vue';

export function useExamDetail() {
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const examId = route.params.id as string

const state = ref<'A' | 'B' | 'C'>('A')
const detail = ref<ExamDetail | null>(null)
const questions = ref<ExamQuestionView[]>([])
const currentIndex = ref(0)
const answers = ref<Record<number, LabAnswerDraft>>({})
const remainingSeconds = ref(0)
const starting = ref(false)
const submitting = ref(false)
const resultAnswerMap = ref<Record<number, ExamAnswerDetail>>({})

let timer: number | undefined
let baselineSeconds = 0
let baselineAt = 0
let lastRemainingSeconds = 0
let autoSubmitTriggered = false
// 考试截止时间：来自 startExam 或详情接口，用于截止只读判定
const deadlineAt = ref<string | null>(null)
// 覆盖历史 deadline_at 为空的进行中提交：remaining 归零也视为到期
const deadlineReached = computed(() => (
  remainingSeconds.value <= 0
  && (deadlineAt.value !== null
    || (state.value === 'B' && detail.value?.submissionStatus === EXAM_SUBMISSION_STATUS.IN_PROGRESS))
))
const isTimerUrgent = computed(() => remainingSeconds.value <= 300)
const limitMinutes = computed(() => {
  const startedAt = detail.value?.startedAt
  const deadline = deadlineAt.value
  if (startedAt && deadline) {
    const diff = Math.round((new Date(deadline).getTime() - new Date(startedAt).getTime()) / 60000)
    if (Number.isFinite(diff)) return Math.max(0, diff)
  }
  return detail.value?.durationMinutes ?? 0
})
const windowTruncated = computed(() => {
  const deadline = deadlineAt.value
  const endAt = detail.value?.endAt
  const configured = detail.value?.durationMinutes
  if (!deadline || !endAt || !configured) return false
  const endTime = new Date(endAt).getTime()
  const deadlineTime = new Date(deadline).getTime()
  return Math.abs(endTime - deadlineTime) <= 1000 && limitMinutes.value < configured
})
const limitText = computed(() => {
  // 仅数据完全缺失时使用兜底文案；四舍五入后可为 0（临近截止进入）
  if (limitMinutes.value <= 0 && !detail.value?.durationMinutes) return '本次限时以服务端为准'
  return `本次限时 ${limitMinutes.value} 分钟${windowTruncated.value ? '（窗口提前结束）' : ''}`
})
const deadlineText = computed(() => (
  deadlineAt.value ? `截止 ${formatDateTime(deadlineAt.value)}` : '截止时间待同步'
))
const isLegacyNoEndAt = computed(() => (
  detail.value?.status === ACTIVITY_STATUS.PUBLISHED && !detail.value?.endAt
))

// 截止后自动交卷失败：常驻错误条 + 有限次自动重试，手动「重新交卷」可重置计数
const MAX_AUTO_RETRIES = 6
const RETRY_INTERVAL_MS = 10000
let retryTimer: number | undefined
let retryCount = 0
const deadlineSubmitFailed = ref(false)

// 草稿保存：1500ms 防抖 + 串行队列，避免同题并发覆盖与请求乱序
const DRAFT_DEBOUNCE_MS = 1500
let draftTimer: number | undefined
let draftQueue: Promise<boolean> = Promise.resolve(true)
let draftErrorNotified = false

const currentQuestion = computed(() => questions.value[currentIndex.value] ?? null)
/** 后端短路详情时会置 notStarted，前端据此禁用开始入口并展示提示。 */
const notStarted = computed(() => detail.value?.notStarted === true)
const currentQuestionDraft = computed(() => (currentQuestion.value ? ensureDraft(currentQuestion.value) : null))
const currentQuestionType = computed(() => normalizeStudentAnswerQuestionType(qType(currentQuestion.value)))
const currentQuestionOptions = computed(() => resolveQuestionOptions(currentQuestion.value))
const answeredCount = computed(() => questions.value.filter(question => hasAnswer(question.questionId)).length)
const unansweredCount = computed(() => Math.max(questions.value.length - answeredCount.value, 0))
const progressPercent = computed(() => {
  if (!questions.value.length) return 0
  return Math.round((answeredCount.value / questions.value.length) * 100)
})
/** 是否允许展示成绩：只以后端 resultAvailable 为准，避免前端凭状态值猜测可见性。 */
const canShowResult = computed(() => detail.value?.resultAvailable === true)
/**
 * 结果页三态：已出分 / 主观题阅卷中 / 成绩待发布。
 * 后两种都不渲染分数字卡、题目结果与解析，防止不完整分数提前泄露。
 */
const resultMode = computed<'result' | 'grading' | 'pending'>(() => {
  if (detail.value?.resultAvailable === true) return 'result'
  if (detail.value?.gradingInProgress === true) return 'grading'
  return 'pending'
})
/** 计划满分 = Σ 每题快照分值；计划满分 0 时成绩页不显示分母 */
const planTotalScore = computed(() =>
  questions.value.reduce(
    (sum, question) => sum + (Number(question.questionScore ?? question.score) || 0),
    0,
  ),
)
/** 总分卡文案：成绩未发布但答卷已提交/已批改时，明确提示等待教师发布。 */
const totalScoreText = computed(() => {
  if (canShowResult.value) return displayScore(detail.value?.totalScore)
  const status = detail.value?.submissionStatus
  if (status === SUBMISSION_STATUS.SUBMITTED || status === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED || status === SUBMISSION_STATUS.GRADED) {
    return '成绩待教师发布'
  }
  return '-'
})
const resultStatusDescription = computed(() => {
  if (!canShowResult.value) {
    return '答卷已记录，但成绩和评语尚未开放，请等待教师确认。'
  }
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.GRADED) {
    return '教师已完成批改，本页展示自动评分与人工评分的完整结果。'
  }
  return '系统已记录你的答卷，主观题部分可能仍在等待教师评分。'
})

function qType(question: ExamQuestionView): string {
  return question.questionType ?? question.type ?? 'TEXT'
}

/**
 * 返回学生侧题目顺序：开启乱序时按「学生 + 考试」稳定洗牌，刷新后顺序不变。
 */
function orderedQuestions(data: ExamDetail): ExamQuestionView[] {
  const raw = data.questions ?? []
  if (data.shuffleQuestions !== true) return raw
  const studentId = authStore.user?.id ?? 0
  return shuffleExamQuestions(raw, `${studentId}:${data.id}`)
}

function normalizeType(type: string): string {
  const map: Record<string, string> = {
    SINGLE: 'SINGLE_CHOICE',
    SINGLE_CHOICE: 'SINGLE_CHOICE',
    MULTIPLE: 'MULTIPLE_CHOICE',
    MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
    JUDGE: 'TRUE_FALSE',
    TRUE_FALSE: 'TRUE_FALSE',
    SHORT: 'TEXT',
    TEXT: 'TEXT',
  }
  return map[type] ?? 'TEXT'
}

function toStudentLabStepItem(question: ExamQuestionView): StudentLabStepItem {
  return {
    id: question.questionId,
    stepNo: questions.value.findIndex(item => item.questionId === question.questionId) + 1,
    title: `第${questions.value.findIndex(item => item.questionId === question.questionId) + 1}题`,
    questionType: normalizeStudentAnswerQuestionType(qType(question)),
    content: question.stem,
    stepScore: question.questionScore ?? question.score ?? 0,
    allowPaste: true,
    answerText: '',
    answerPayloadJson: null,
    options: resolveQuestionOptions(question),
  }
}

function resolveQuestionOptions(question?: ExamQuestionView | null): StudentChoiceOption[] {
  if (!question) return []
  return parseQuestionOptions(question.optionsJson ?? question.options).map(option => ({
    key: option.key,
    label: option.label,
  }))
}

function normalizeJudgeSavedValue(raw: string): 'TRUE' | 'FALSE' | null {
  const normalized = raw.trim().toUpperCase()
  if (['TRUE', 'T', '正确', 'YES', 'Y', '1', 'A'].includes(normalized)) return 'TRUE'
  if (['FALSE', 'F', '错误', 'NO', 'N', '0', 'B'].includes(normalized)) return 'FALSE'
  return null
}

function normalizeSavedAnswer(question: ExamQuestionView): LabAnswerDraft {
  const saved = question.answer ?? question.studentAnswer ?? question.answerJson ?? ''
  if (!String(saved ?? '').trim()) {
    return hydrateDraftFromAnswerText(toStudentLabStepItem(question), '')
  }

  if (typeof saved === 'string' && (saved.trim().startsWith('{') || saved.trim().startsWith('['))) {
    try {
      const parsed = JSON.parse(saved) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && 'kind' in (parsed as Record<string, unknown>)) {
        return parsed as LabAnswerDraft
      }
      if (Array.isArray(parsed)) {
        // 旧版草稿只保存选项数组；按题型还原，空数组表示已清空的多选
        const values = parsed.map(item => String(item)).filter(Boolean)
        const type = normalizeStudentAnswerQuestionType(qType(question))
        if (type === 'SINGLE_CHOICE') {
          return { kind: 'single', selectedKey: values[0] ?? null }
        }
        if (type === 'MULTIPLE_CHOICE') {
          return { kind: 'multiple', selectedKeys: values }
        }
        if (type === 'TRUE_FALSE') {
          return { kind: 'judge', value: values.length ? normalizeJudgeSavedValue(values[0]) : null }
        }
        return hydrateDraftFromAnswerText(toStudentLabStepItem(question), values.join(','))
      }
    } catch {
      // ignore invalid structured payload and fallback to legacy parsing
    }
  }

  return hydrateDraftFromAnswerText(toStudentLabStepItem(question), String(saved ?? ''))
}

function ensureDraft(question: ExamQuestionView): LabAnswerDraft {
  if (!answers.value[question.questionId]) {
    answers.value = {
      ...answers.value,
      [question.questionId]: normalizeSavedAnswer(question),
    }
  }
  return answers.value[question.questionId]
}

function updateCurrentQuestionDraft(value: LabAnswerDraft) {
  if (!currentQuestion.value) return
  answers.value = {
    ...answers.value,
    [currentQuestion.value.questionId]: value,
  }
  // 唯一用户编辑入口：写入答案后调度草稿保存，初始化回填不会触发
  scheduleDraftSave()
}

/** 组装交卷 / 草稿共用的作答载荷。 */
function buildSubmitPayload() {
  return {
    answers: questions.value.map(question => {
      const draft = ensureDraft(question)
      return {
        questionId: question.questionId,
        questionType: normalizeStudentAnswerQuestionType(qType(question)),
        answerText: buildCompatibilityAnswerText(draft),
        answerJson: buildAnswerPayloadJson(draft),
        selectedOptions: selectedOptionsOf(draft),
      }
    }),
  }
}

function clearDraftTimer() {
  if (draftTimer) {
    window.clearTimeout(draftTimer)
    draftTimer = undefined
  }
}

/** 立即保存草稿；返回是否成功。失败只提示一次，不阻断继续作答。 */
function flushDraft(): Promise<boolean> {
  clearDraftTimer()
  if (deadlineReached.value) {
    return Promise.resolve(true)
  }
  if (state.value !== 'B' || detail.value?.submissionStatus !== EXAM_SUBMISSION_STATUS.IN_PROGRESS) {
    return Promise.resolve(true)
  }
  const task = () => saveExamDraft(examId, buildSubmitPayload())
    .then(() => {
      draftErrorNotified = false
      return true
    })
    .catch(() => {
      if (!draftErrorNotified) {
        draftErrorNotified = true
        ElMessage.warning('草稿保存失败，答题内容仍保留在本页，请检查网络')
      }
      return false
    })
  const next = draftQueue.then(task, task)
  draftQueue = next
  return next
}

function scheduleDraftSave() {
  clearDraftTimer()
  if (deadlineReached.value) return
  if (state.value !== 'B' || detail.value?.submissionStatus !== EXAM_SUBMISSION_STATUS.IN_PROGRESS) return
  draftTimer = window.setTimeout(() => {
    draftTimer = undefined
    void flushDraft()
  }, DRAFT_DEBOUNCE_MS)
}

// 切题时立即落盘，避免切换后草稿停留在上一题的旧内容
watch(currentIndex, () => {
  void flushDraft()
})

function selectedOptionsOf(draft: LabAnswerDraft): string[] | undefined {
  if (draft.kind === 'multiple') return draft.selectedKeys
  if (draft.kind === 'single' && draft.selectedKey) return [draft.selectedKey]
  if (draft.kind === 'judge' && draft.value) return [draft.value]
  return undefined
}

function hasAnswer(questionId: number): boolean {
  const question = questions.value.find(item => item.questionId === questionId)
  if (!question) return false
  return isDraftAnswered(ensureDraft(question))
}

function questionTypeLabel(question?: ExamQuestionView | null) {
  const normalized = normalizeStudentAnswerQuestionType(qType(question ?? {} as ExamQuestionView))
  if (normalized === 'SINGLE_CHOICE') return '单选题'
  if (normalized === 'MULTIPLE_CHOICE') return '多选题'
  if (normalized === 'TRUE_FALSE') return '判断题'
  if (normalized === 'FILL_BLANK') return '填空题'
  if (normalized === 'CODE') return '代码题'
  if (normalized === 'TEXT') return '文本题'
  return '简答题'
}

function answerHint(question?: ExamQuestionView | null) {
  const normalized = normalizeStudentAnswerQuestionType(qType(question ?? {} as ExamQuestionView))
  if (normalized === 'SINGLE_CHOICE') return '点击选项完成单选作答。'
  if (normalized === 'MULTIPLE_CHOICE') return '可多选，再次点击可取消。'
  if (normalized === 'TRUE_FALSE') return '按照实验判断题交互选择正确或错误。'
  if (normalized === 'FILL_BLANK') return '请直接在题干中的空位填写答案。'
  if (normalized === 'CODE') return '使用实验题同款代码交互作答。'
  if (normalized === 'SHORT_ANSWER') return '请输入简答内容。'
  return '请输入本题答案。'
}

function formatDraftAnswer(draft: LabAnswerDraft): string {
  if (draft.kind === 'single') return draft.selectedKey ?? ''
  if (draft.kind === 'multiple') return draft.selectedKeys.join('，')
  if (draft.kind === 'judge') return draft.value === 'TRUE' ? '正确' : draft.value === 'FALSE' ? '错误' : ''
  if (draft.kind === 'fill') return draft.blanks.map(blank => blank.answer.trim()).filter(Boolean).join('，')
  if (draft.kind === 'code') return draft.code
  return draft.text
}

function formatTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const leftSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(leftSeconds).padStart(2, '0')}`
}

/** 以服务端返回的剩余秒数为基线本地递减，避免本机时间偏移影响倒计时。 */
function startTimer(remaining: number) {
  stopTimer()
  baselineSeconds = Math.max(0, Math.floor(remaining))
  baselineAt = performance.now()
  lastRemainingSeconds = baselineSeconds
  remainingSeconds.value = baselineSeconds
  const update = () => {
    const elapsed = Math.floor((performance.now() - baselineAt) / 1000)
    const next = Math.max(0, baselineSeconds - elapsed)
    remainingSeconds.value = Math.min(lastRemainingSeconds, next)
    lastRemainingSeconds = remainingSeconds.value
    if (remainingSeconds.value <= 0) {
      stopTimer()
      clearDraftTimer()
      if (!autoSubmitTriggered) {
        autoSubmitTriggered = true
        autoSubmit()
      }
    }
  }
  if (baselineSeconds <= 0) {
    remainingSeconds.value = 0
    lastRemainingSeconds = 0
    clearDraftTimer()
    if (!autoSubmitTriggered) {
      autoSubmitTriggered = true
      autoSubmit()
    }
    return
  }
  autoSubmitTriggered = false
  timer = window.setInterval(update, 1000)
  update()
}

/** 回到可见态时先用单调时钟补齐隐藏期间的时间差，再决定是否立即交卷。 */
function recomputeRemainingFromBaseline(): number {
  if (baselineAt === 0) return remainingSeconds.value
  const elapsed = Math.floor((performance.now() - baselineAt) / 1000)
  const next = Math.max(0, baselineSeconds - elapsed)
  remainingSeconds.value = Math.min(lastRemainingSeconds, next)
  lastRemainingSeconds = remainingSeconds.value
  return remainingSeconds.value
}

/** 响应缺少 remainingSeconds 时，用 deadlineAt 计算一次基线。 */
function secondsUntil(deadline?: string | null): number {
  if (!deadline) return 0
  return Math.max(0, Math.floor((new Date(deadline).getTime() - Date.now()) / 1000))
}

function stopTimer() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function statusLabel(status?: string): string {
  const map: Record<string, string> = {
    IN_PROGRESS: '进行中',
    SUBMITTED: '已提交',
    AUTO_SUBMITTED: '自动提交',
    GRADED: '已批改',
  }
  return status ? map[status] ?? status : '-'
}

function formatDisplayAnswer(answer: string | null | undefined): string {
  if (!answer) return '未作答'
  try {
    const parsed = JSON.parse(answer)
    if (parsed && typeof parsed === 'object' && 'kind' in (parsed as Record<string, unknown>)) {
      return formatDraftAnswer(parsed as LabAnswerDraft) || '未作答'
    }
    if (Array.isArray(parsed)) {
      return parsed.join('，')
    }
    return String(parsed)
  } catch {
    return String(answer)
  }
}

function loadSavedAnswers() {
  answers.value = {}
  for (const question of questions.value) {
    answers.value[question.questionId] = normalizeSavedAnswer(question)
  }
}

function formatWindow(startAt?: string | null, endAt?: string | null): string {
  if (!startAt && !endAt) return '时间待定'
  return `${formatDateTime(startAt)} - ${formatDateTime(endAt)}`
}

function formatDateTime(value?: string | null): string {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function questionMapTone(questionId: number, index: number) {
  if (currentIndex.value === index) return 'is-current'
  return hasAnswer(questionId) ? 'is-done' : 'is-empty'
}

function displayScore(score: number | null | undefined) {
  return typeof score === 'number' ? String(score) : '-'
}

function questionScoreText(questionId: number, question: ExamQuestionView) {
  if (!canShowResult.value) {
    return `满分 ${question.questionScore ?? question.score ?? 0}`
  }
  const result = resultAnswerMap.value[questionId]
  if (!result) {
    return `满分 ${question.questionScore ?? question.score ?? 0}`
  }
  return `${displayScore(result.score)} / ${result.questionScore}`
}

function referenceAnswerText(questionId: number, question: ExamQuestionView) {
  const result = resultAnswerMap.value[questionId]
  const standard = result?.standardAnswer ?? question.standardAnswer ?? null
  if (standard) {
    return formatDisplayAnswer(standard)
  }
  if (result?.isCorrect === true) {
    return '已按系统标准答案判定正确'
  }
  if (result?.isCorrect === false) {
    return '已按系统标准答案判定错误'
  }
  return '暂未公开参考答案'
}

function resultMetaText(questionId: number) {
  const result = resultAnswerMap.value[questionId]
  if (!result) return '结果待同步'
  if (result.score !== null && result.score !== undefined && result.teacherComment) {
    return '教师已完成评分并附带评语'
  }
  if (result.score !== null && result.score !== undefined && result.isCorrect === null) {
    return '教师已完成人工评分'
  }
  if (result.isCorrect === true) return '系统判定正确'
  if (result.isCorrect === false) return '系统判定错误'
  return '教师人工评分中'
}

/** 题目解析：优先取结果明细，回退到题目快照，缺省展示占位文案 */
function analysisTextOf(questionId: number, question: ExamQuestionView): string {
  const text = resultAnswerMap.value[questionId]?.analysisText ?? question.analysisText ?? ''
  return text.trim() ? text : '暂无解析'
}

/**
 * 结果页对错三态视觉：true→correct / false→wrong / null→manual；
 * 无判分条目→none（不渲染任何新增视觉元素）。对错唯一真相是 isCorrect，不做二次推断。
 */
function resultTone(questionId: number): 'correct' | 'wrong' | 'manual' | 'none' {
  const result = resultAnswerMap.value[questionId]
  if (!result) return 'none'
  if (result.isCorrect === true) return 'correct'
  if (result.isCorrect === false) return 'wrong'
  return 'manual'
}

/** 答对题数：仅统计 isCorrect === true 的判分条目；分母用 questions.length（汇总卡展示）。 */
const correctCount = computed(() =>
  Object.values(resultAnswerMap.value).filter(result => result.isCorrect === true).length
)

/** 结果态题号导航点击定位：平滑滚动到对应题卡。 */
function scrollToResultQuestion(questionId: number) {
  document.getElementById(`result-question-${questionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function doStart() {
  if (notStarted.value) {
    ElMessage.warning('考试尚未开始')
    return
  }
  starting.value = true
  try {
    const response = await startExam(examId) as { deadlineAt?: string; remainingSeconds?: number }
    if (response?.deadlineAt) {
      deadlineAt.value = response.deadlineAt
      detail.value = await getExamDetail(examId)
      questions.value = detail.value ? orderedQuestions(detail.value) : []
      loadSavedAnswers()
      startTimer(response.remainingSeconds ?? secondsUntil(response.deadlineAt))
      state.value = 'B'
    } else {
      await loadExam()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '开始考试失败')
  } finally {
    starting.value = false
  }
}

function clearRetryTimer() {
  if (retryTimer) {
    window.clearTimeout(retryTimer)
    retryTimer = undefined
  }
}

/** 清空交卷失败态与自动重试计数，用于手动重试或交卷成功。 */
function resetSubmitFailure() {
  clearRetryTimer()
  retryCount = 0
  deadlineSubmitFailed.value = false
}

/** 服务端明确表示「已提交」，可直接回收为结果态，无需继续重试。 */
function isAlreadySubmittedError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 40000 && error.message.includes('考试已提交，不能重复提交')
}

/** 仅网络异常或服务端 5xx 类错误值得自动重试，业务校验失败不重试。 */
function isRetryableFailure(error: unknown): boolean {
  return !(error instanceof ApiError) || error.code >= 50000
}

/** 截止后失败按固定间隔自动重试，次数上限 MAX_AUTO_RETRIES。 */
function scheduleAutoRetry() {
  if (retryTimer || retryCount >= MAX_AUTO_RETRIES) return
  retryTimer = window.setTimeout(() => {
    retryTimer = undefined
    retryCount += 1
    void doSubmit()
  }, RETRY_INTERVAL_MS)
}

async function doSubmit(options: { manual?: boolean } = {}) {
  if (submitting.value) return
  if (options.manual) resetSubmitFailure()
  submitting.value = true
  stopTimer()
  try {
    // 交卷前先 flush 草稿，保证后台自动交卷与手动交卷都使用最新草稿；失败不阻断交卷
    if (!deadlineReached.value) await flushDraft()
    await submitExam(examId, buildSubmitPayload(), { silent: true })
    resetSubmitFailure()
    ElMessage.success('交卷成功')
    try {
      await loadExam()
    } catch {
      // 结果同步失败不影响已完成的交卷，保留当前页面
    }
  } catch (error) {
    if (isAlreadySubmittedError(error)) {
      clearRetryTimer()
      try {
        await loadExam()
        if (state.value !== 'B') {
          resetSubmitFailure()
          return
        }
      } catch {
        // 详情同步失败时保留失败态，交由用户手动重试
      }
      deadlineSubmitFailed.value = true
      ElMessage.error('交卷结果未同步，请重试')
      // 未到截止点仍需继续倒计时，避免计时停摆
      if (!deadlineReached.value && deadlineAt.value) startTimer(remainingSeconds.value)
      return
    }
    if (deadlineReached.value) {
      deadlineSubmitFailed.value = true
      if (isRetryableFailure(error)) {
        scheduleAutoRetry()
      } else {
        ElMessage.error(error instanceof Error ? error.message : '交卷失败')
      }
    } else {
      ElMessage.error(error instanceof Error ? error.message : '交卷失败')
    }
  } finally {
    submitting.value = false
    // 未到截止点且未处于失败态时恢复倒计时，避免一次失败导致计时永久停摆
    if (!deadlineSubmitFailed.value && state.value === 'B' && !deadlineReached.value && deadlineAt.value) {
      startTimer(remainingSeconds.value)
    }
  }
}

function confirmSubmit() {
  const message = unansweredCount.value
    ? `你还有 ${unansweredCount.value} 题未作答，确认现在提交吗？提交后不可修改。`
    : '确认提交当前答卷吗？提交后不可修改。'
  ElMessageBox.confirm(message, '确认交卷', {
    confirmButtonText: '确认提交',
    cancelButtonText: '继续作答',
    type: 'warning',
  }).then(() => doSubmit()).catch(() => undefined)
}

function autoSubmit() {
  if (submitting.value) return
  ElMessage.warning('考试时间已到，系统正在自动交卷...')
  void doSubmit()
}

async function loadExam() {
  const data = await getExamDetail(examId)
  detail.value = data
  questions.value = orderedQuestions(data)

  const map: Record<number, ExamAnswerDetail> = {}
  const answerDetails = (data as ExamDetail & { answerDetails?: ExamAnswerDetail[] }).answerDetails
  if (answerDetails?.length) {
    answerDetails.forEach(answer => {
      map[answer.questionId] = answer
    })
  } else {
    questions.value.forEach(question => {
      const earnedScore = question.earnedScore ?? question.score ?? null
      const isCorrect = question.isCorrect ?? null
      const teacherComment = question.teacherComment ?? null
      // 仅当判分/得分/评语任一存在时才构建逐题结果，未判分题不生成占位结果
      if (isCorrect == null && earnedScore == null && teacherComment == null) return
      map[question.questionId] = {
        questionId: question.questionId,
        questionType: qType(question),
        stem: question.stem,
        optionsJson: question.optionsJson ?? null,
        standardAnswer: question.standardAnswer ?? question.answerJson ?? null,
        studentAnswer: question.studentAnswer ?? question.answer ?? null,
        isCorrect,
        score: earnedScore,
        teacherComment,
        questionScore: question.questionScore ?? question.score ?? 0,
        analysisText: question.analysisText ?? null,
      }
    })
  }
  resultAnswerMap.value = map

  if (data.submissionStatus === EXAM_SUBMISSION_STATUS.IN_PROGRESS) {
    loadSavedAnswers()
    // 详情缺省 deadlineAt 时保留已知值，避免已获得的截止时间被覆盖后重新可编辑
    deadlineAt.value = data.deadlineAt ?? deadlineAt.value
    const baseline = data.remainingSeconds ?? (deadlineAt.value ? secondsUntil(deadlineAt.value) : null)
    if (baseline != null) {
      startTimer(baseline)
    }
    state.value = 'B'
  } else if (data.submissionStatus) {
    // SUBMITTED / AUTO_SUBMITTED / GRADED 统一进结果页，由 resultMode 决定展示哪一态
    resetSubmitFailure()
    state.value = 'C'
  } else {
    state.value = 'A'
  }
}

/** 页面隐藏（切后台 / 锁屏）时立即落盘；重新可见时重拉详情，用服务端剩余时间校正倒计时。 */
function onVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    void flushDraft()
    return
  }
  if (document.visibilityState === 'visible' && state.value === 'B') {
    const remaining = recomputeRemainingFromBaseline()
    if (remaining <= 0 && !autoSubmitTriggered) {
      autoSubmitTriggered = true
      autoSubmit()
    }
    void loadExam().catch(() => undefined)
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  void loadExam()
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  clearDraftTimer()
  clearRetryTimer()
  stopTimer()
})

  return { EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS, computed, onMounted, onUnmounted, ref, watch, useRoute, useRouter, ElMessage, ElMessageBox, QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromAnswerText, isDraftAnswered, normalizeStudentAnswerQuestionType, getExamDetail, startExam, submitExam, saveExamDraft, ApiError, useAuthStore, shuffleExamQuestions, route, router, authStore, examId, state, detail, questions, currentIndex, answers, remainingSeconds, starting, submitting, resultAnswerMap, timer, baselineSeconds, baselineAt, lastRemainingSeconds, deadlineAt, deadlineReached,
  isTimerUrgent, limitMinutes, limitText, deadlineText, isLegacyNoEndAt, windowTruncated, MAX_AUTO_RETRIES, RETRY_INTERVAL_MS, retryTimer, retryCount, deadlineSubmitFailed, DRAFT_DEBOUNCE_MS, draftTimer, draftQueue, draftErrorNotified, currentQuestion, notStarted, currentQuestionDraft, currentQuestionType, currentQuestionOptions, answeredCount, unansweredCount, progressPercent, canShowResult, resultMode, totalScoreText, planTotalScore, resultStatusDescription, qType, orderedQuestions, normalizeType, toStudentLabStepItem, resolveQuestionOptions, normalizeJudgeSavedValue, normalizeSavedAnswer, ensureDraft, updateCurrentQuestionDraft, buildSubmitPayload, flushDraft, scheduleDraftSave, selectedOptionsOf, hasAnswer, questionTypeLabel, answerHint, formatDraftAnswer, formatTime, startTimer, secondsUntil, stopTimer, statusLabel, formatDisplayAnswer, loadSavedAnswers, formatWindow, formatDateTime, questionMapTone, displayScore, questionScoreText, referenceAnswerText, resultMetaText, analysisTextOf, resultTone, correctCount, scrollToResultQuestion, doStart, clearRetryTimer, resetSubmitFailure, isAlreadySubmittedError, isRetryableFailure, scheduleAutoRetry, doSubmit, confirmSubmit, autoSubmit, loadExam, onVisibilityChange };
}

export type ExamDetailState = ReturnType<typeof useExamDetail>;
export const ExamDetailKey: InjectionKey<ExamDetailState> = Symbol('ExamDetailState');
