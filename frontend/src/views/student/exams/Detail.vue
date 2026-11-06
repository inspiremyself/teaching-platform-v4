<template>
  <div class="exam-detail-page">
    <section v-if="state === 'A' && detail" class="intro-layout">
      <article class="intro-card intro-card--hero">
        <div class="intro-card__topline">
          <div>
            <div class="intro-card__eyebrow">考试概览</div>
            <h1>{{ detail.title }}</h1>
            <p>{{ detail.description || '确认时间与规则后即可开始。' }}</p>
          </div>
          <div class="intro-actions">
            <button class="primary-action" type="button" :loading="false" :disabled="starting" @click="doStart">
              <span class="material-symbols-outlined">play_circle</span>
              {{ starting ? '正在开始考试' : '开始考试' }}
            </button>
            <button class="secondary-action" type="button" @click="router.push('/student/exams')">返回考试列表</button>
          </div>
        </div>

        <div class="intro-metrics">
            <div class="intro-metric intro-metric--wide">
              <span>考试窗口</span>
              <strong>{{ formatWindow(detail.startAt, detail.endAt) }}</strong>
            </div>
          <div class="intro-metric">
            <span>时长</span>
            <strong>{{ detail.durationMinutes }} 分钟</strong>
          </div>
          <div class="intro-metric">
            <span>题目数</span>
            <strong>{{ questions.length }} 题</strong>
          </div>
        </div>
      </article>

      <aside class="intro-side">
        <article class="side-card side-card--mini">
          <div class="side-card__title">作答须知</div>
          <ul class="bullet-list">
            <li>开始后计时，到点自动交卷。</li>
            <li>交卷后不可修改答案。</li>
            <li>主观题结果可能稍后更新。</li>
          </ul>
        </article>
      </aside>
    </section>

    <section v-else-if="state === 'B'" class="taking-layout">
      <header class="taking-header">
        <div>
          <div class="taking-header__eyebrow">进行中</div>
          <h1>{{ detail?.title }}</h1>
            <p>{{ answeredCount }} / {{ questions.length }} 题已作答。</p>
        </div>
        <div class="timer-shell">
          <span class="material-symbols-outlined">timer</span>
          <div>
            <strong>{{ formatTime(remainingSeconds) }}</strong>
            <small>到点自动交卷</small>
          </div>
        </div>
      </header>

      <div v-if="deadlineSubmitFailed" class="submit-failure-bar">
        <span class="material-symbols-outlined">error</span>
        <p>考试已结束，自动交卷失败，请立即重试或联系教师</p>
        <button class="primary-action primary-action--danger" type="button" :disabled="submitting" @click="doSubmit({ manual: true })">
          重新交卷
        </button>
      </div>

      <div class="taking-grid">
        <main class="question-column">
          <div class="exam-banner">
            <div>
              <strong>当前作答状态：进行中</strong>
              <p>优先处理未作答题。</p>
            </div>
            <div class="exam-banner__warn">未作答 {{ unansweredCount }} 题</div>
          </div>

          <article v-if="currentQuestion" class="question-card">
            <div class="question-card__header">
              <div>
                <span class="question-card__badge">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</span>
                <h2>{{ currentQuestion.stem }}</h2>
              </div>
              <span class="question-card__score">{{ currentQuestion.questionScore ?? currentQuestion.score ?? 0 }} 分</span>
            </div>

            <div class="question-card__body">
              <section v-if="currentQuestionType === 'FILL_BLANK'" class="exam-question-prompt">
                <div class="exam-question-prompt__label">题目内容</div>
                <FillBlankInlineEditor
                  :model-value="currentQuestionDraft"
                  :prompt="currentQuestion.stem"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />
              </section>

              <QuestionAnswerShell
                v-if="currentQuestionType !== 'FILL_BLANK'"
                title="作答区"
                :hint="answerHint(currentQuestion)"
                :chip="questionTypeLabel(currentQuestion)"
              >
                <template #callout>
                  <div v-if="currentQuestionType === 'CODE'" class="exam-code-callout">
                    <div class="exam-code-callout__title">代码题作答</div>
                    <p>复用实验题的代码作答交互，交卷时同步提交兼容文本与结构化答案。</p>
                  </div>
                </template>

                <ChoiceAnswerEditor
                  v-if="currentQuestionType === 'SINGLE_CHOICE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="single"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <ChoiceAnswerEditor
                  v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="multiple"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <ChoiceAnswerEditor
                  v-else-if="currentQuestionType === 'TRUE_FALSE'"
                  :model-value="currentQuestionDraft"
                  :options="currentQuestionOptions"
                  mode="judge"
                  :disabled="submitting || deadlineReached"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <CodeLabAnswerEditor
                  v-else-if="currentQuestionType === 'CODE'"
                  :model-value="currentQuestionDraft"
                  language="CODE"
                  :rows="14"
                  :maxlength="20000"
                  :disabled="submitting || deadlineReached"
                  placeholder="// 请输入代码答案"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <TextAnswerEditor
                  v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                  :model-value="currentQuestionDraft"
                  :rows="8"
                  :maxlength="5000"
                  :disabled="submitting || deadlineReached"
                  placeholder="请输入简答内容"
                  @update:model-value="updateCurrentQuestionDraft"
                />

                <TextAnswerEditor
                  v-else
                  :model-value="currentQuestionDraft"
                  :rows="12"
                  :maxlength="5000"
                  :disabled="submitting || deadlineReached"
                  placeholder="请输入答案"
                  @update:model-value="updateCurrentQuestionDraft"
                />
              </QuestionAnswerShell>
            </div>

            <div class="question-card__footer">
              <button class="secondary-action" type="button" :disabled="currentIndex === 0" @click="currentIndex -= 1">上一题</button>
              <div class="question-card__footer-actions">
                <button class="secondary-action" type="button" :disabled="currentIndex >= questions.length - 1" @click="currentIndex += 1">下一题</button>
                <button class="primary-action primary-action--danger" type="button" :disabled="submitting || deadlineReached" @click="confirmSubmit">
                  交卷
                </button>
              </div>
            </div>
          </article>
        </main>

        <aside class="map-column">
          <section class="map-card">
            <div class="map-card__header">
              <h3>题号导航</h3>
              <span>{{ progressPercent }}%</span>
            </div>
            <div class="progress-track">
              <div class="progress-track__fill" :style="{ width: `${progressPercent}%` }"></div>
            </div>
            <div class="question-map-grid">
              <button
                v-for="(question, index) in questions"
                :key="question.questionId"
                type="button"
                :class="['question-map-item', questionMapTone(question.questionId, index)]"
                @click="currentIndex = index"
              >
                {{ index + 1 }}
              </button>
            </div>
            <div class="map-legend">
              <span><i class="dot dot--done"></i>已作答</span>
              <span><i class="dot dot--current"></i>当前题</span>
            </div>
          </section>

          <section class="submit-card">
            <h3>交卷控制</h3>
            <div class="submit-card__stats">
              <div><span>总题数</span><strong>{{ questions.length }}</strong></div>
              <div><span>未作答</span><strong>{{ unansweredCount }}</strong></div>
              <div><span>已作答</span><strong>{{ answeredCount }}</strong></div>
            </div>
            <p>提交后立即锁定。</p>
            <button class="primary-action" type="button" :disabled="submitting || deadlineReached" @click="confirmSubmit">提交本次考试</button>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="state === 'C' && detail" class="result-layout">
      <header class="result-header">
        <div>
          <div class="result-header__eyebrow">结果</div>
          <h1>{{ detail.title }}</h1>
          <p>{{ resultStatusDescription }}</p>
        </div>
        <button class="secondary-action" type="button" @click="router.push('/student/exams')">返回考试列表</button>
      </header>

      <section class="result-summary-grid">
        <article class="result-summary-card result-summary-card--status">
          <div class="result-summary-card__label">状态</div>
          <div class="result-summary-card__value">{{ statusLabel(detail.submissionStatus) }}</div>
          </article>
        <template v-if="resultMode === 'result'">
          <article class="result-summary-card">
            <div class="result-summary-card__label">自动评分</div>
            <div class="result-summary-card__value">{{ canShowResult ? displayScore(detail.autoScore) : '-' }}</div>
            </article>
          <article class="result-summary-card">
            <div class="result-summary-card__label">教师评分</div>
            <div class="result-summary-card__value">{{ canShowResult ? displayScore(detail.manualScore) : '-' }}</div>
            </article>
          <article class="result-summary-card result-summary-card--accent">
            <div class="result-summary-card__label">总分</div>
            <div class="result-summary-card__value">{{ totalScoreText }}</div>
            </article>
        </template>
      </section>

      <el-alert
        v-if="resultMode === 'grading'"
        title="主观题阅卷中，成绩暂不公开"
        type="info"
        :closable="false"
        class="result-alert"
      />
      <el-alert
        v-else-if="resultMode === 'pending'"
        title="成绩待发布"
        type="info"
        :closable="false"
        class="result-alert"
      />

      <div v-if="resultMode === 'result'" class="result-grid">
        <main class="result-main">
          <article v-for="(question, index) in questions" :key="question.questionId" class="result-question-card">
            <div class="result-question-card__header">
              <div>
                <span class="result-question-card__badge">第{{ index + 1 }}题</span>
                <h2>{{ question.stem }}</h2>
              </div>
              <span class="result-question-card__score">{{ questionScoreText(question.questionId, question) }}</span>
            </div>

            <div class="result-answer-block">
              <div class="result-answer-block__label">你的答案</div>
              <div class="result-answer-block__value">{{ formatDisplayAnswer(question.studentAnswer ?? question.answer) }}</div>
            </div>

            <div class="result-answer-grid">
              <div class="result-answer-block result-answer-block--light">
                <div class="result-answer-block__label">参考答案</div>
              <div class="result-answer-block__value">{{ canShowResult ? referenceAnswerText(question.questionId, question) : '暂未公开参考答案' }}</div>
              </div>
              <div class="result-answer-block result-answer-block--light">
                <div class="result-answer-block__label">评分信息</div>
                <div class="result-answer-block__value">{{ canShowResult ? resultMetaText(question.questionId) : '成绩暂未开放' }}</div>
              </div>
              <div class="result-answer-block result-answer-block--light result-answer-block--full">
                <div class="result-answer-block__label">题目解析</div>
                <div class="result-answer-block__value">
                  {{ canShowResult ? analysisTextOf(question.questionId, question) : '成绩暂未开放' }}
                </div>
              </div>
            </div>

            <div v-if="canShowResult && resultAnswerMap[question.questionId]?.teacherComment" class="teacher-comment-box">
              <div class="teacher-comment-box__label">教师评语</div>
              <div>{{ resultAnswerMap[question.questionId].teacherComment }}</div>
            </div>
          </article>
        </main>

        <aside class="result-side">
          <article class="side-card side-card--mini">
            <div class="side-card__title">结果说明</div>
            <ul class="bullet-list">
              <li>自动题立即展示结果。</li>
              <li>主观题批改后更新。</li>
              <li>若仍为“已提交”，请稍后再查看。</li>
            </ul>
          </article>
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QuestionAnswerShell from '../components/lab-answer/QuestionAnswerShell.vue'
import ChoiceAnswerEditor from '../components/lab-answer/ChoiceAnswerEditor.vue'
import FillBlankInlineEditor from '../components/lab-answer/FillBlankInlineEditor.vue'
import TextAnswerEditor from '../components/lab-answer/TextAnswerEditor.vue'
import CodeLabAnswerEditor from '../components/lab-answer/CodeLabAnswerEditor.vue'
import { buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromAnswerText } from '../components/lab-answer/answerPayload'
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from '../components/lab-answer/types'
import { getExamDetail, startExam, submitExam, saveExamDraft } from '@/api/exams'
import { ApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import { shuffleExamQuestions } from '@/utils/examShuffle'
import type { ExamAnswerDetail, ExamDetail, ExamQuestionView } from '@/types/exam'
import type { StudentChoiceOption, StudentLabStepItem } from '@/types/lab'

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
// 考试截止时间：来自 startExam 或详情接口，用于截止只读判定与失败后恢复计时
const deadlineAt = ref<string | null>(null)
const deadlineReached = computed(() => deadlineAt.value != null && remainingSeconds.value <= 0)

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
/** 总分卡文案：成绩未发布但答卷已提交/已批改时，明确提示等待教师发布。 */
const totalScoreText = computed(() => {
  if (canShowResult.value) return displayScore(detail.value?.totalScore)
  const status = detail.value?.submissionStatus
  if (status === 'SUBMITTED' || status === 'AUTO_SUBMITTED' || status === 'GRADED') {
    return '成绩待教师发布'
  }
  return '-'
})
const resultStatusDescription = computed(() => {
  if (!canShowResult.value) {
    return '答卷已记录，但成绩和评语尚未开放，请等待教师确认。'
  }
  if (detail.value?.submissionStatus === 'GRADED') {
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

function parseOptions(options: ExamQuestionView['options'] | string | null | undefined): Array<{ key: string; label: string }> {
  if (!options) return []
  let list: Array<{ key: string; label: string } | string> | null = null
  if (Array.isArray(options)) {
    list = options
  } else if (typeof options === 'string') {
    try {
      list = JSON.parse(options)
    } catch {
      return []
    }
  }
  if (!Array.isArray(list)) return []
  return list.map((item, index) => {
    if (typeof item === 'object' && item && 'key' in item && 'label' in item) {
      return item as { key: string; label: string }
    }
    if (typeof item === 'object' && item && 'key' in item) {
      const record = item as Record<string, unknown>
      return {
        key: String(record.key ?? ''),
        label: String(record.label ?? record.content ?? record.text ?? record.value ?? '').trim(),
      }
    }
    if (typeof item === 'string') {
      const dotIndex = item.indexOf('.')
      if (dotIndex > 0) {
        return { key: item.slice(0, dotIndex), label: item.slice(dotIndex + 1) }
      }
      return { key: String.fromCharCode(65 + index), label: item }
    }
    return { key: String.fromCharCode(65 + index), label: String(item) }
  })
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
  return parseOptions(question.optionsJson ?? question.options).map(option => ({
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

/** 立即保存草稿；返回是否成功。失败只提示一次，不阻断继续作答。 */
function flushDraft(): Promise<boolean> {
  if (draftTimer) {
    window.clearTimeout(draftTimer)
    draftTimer = undefined
  }
  if (state.value !== 'B' || detail.value?.submissionStatus !== 'IN_PROGRESS') {
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
  if (state.value !== 'B' || detail.value?.submissionStatus !== 'IN_PROGRESS') return
  if (draftTimer) window.clearTimeout(draftTimer)
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

function startTimer(deadlineAt: string) {
  stopTimer()
  const update = () => {
    remainingSeconds.value = Math.max(0, Math.floor((new Date(deadlineAt).getTime() - Date.now()) / 1000))
    if (remainingSeconds.value <= 0) {
      stopTimer()
      autoSubmit()
    }
  }
  update()
  timer = window.setInterval(update, 1000)
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

async function doStart() {
  starting.value = true
  try {
    const response = await startExam(examId) as { deadlineAt?: string }
    if (response?.deadlineAt) {
      deadlineAt.value = response.deadlineAt
      detail.value = await getExamDetail(examId)
      questions.value = detail.value ? orderedQuestions(detail.value) : []
      loadSavedAnswers()
      startTimer(response.deadlineAt)
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
      if (!deadlineReached.value && deadlineAt.value) startTimer(deadlineAt.value)
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
      startTimer(deadlineAt.value)
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

  if (data.submissionStatus === 'IN_PROGRESS') {
    loadSavedAnswers()
    // 详情缺省 deadlineAt 时保留已知值，避免已获得的截止时间被覆盖后重新可编辑
    deadlineAt.value = data.deadlineAt ?? deadlineAt.value
    if (deadlineAt.value) {
      startTimer(deadlineAt.value)
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

/** 页面隐藏（切后台 / 锁屏）时立即落盘；关闭标签页浏览器不保证异步请求，故不注册 beforeunload 保存。 */
function onVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    void flushDraft()
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  void loadExam()
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (draftTimer) {
    window.clearTimeout(draftTimer)
    draftTimer = undefined
  }
  clearRetryTimer()
  stopTimer()
})
</script>

<style scoped>
.exam-detail-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.intro-layout,
.taking-grid,
.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.56fr) minmax(220px, 0.58fr);
  gap: 10px;
}

.intro-card,
.side-card,
.taking-header,
.exam-banner,
.question-card,
.map-card,
.submit-card,
.result-summary-card,
.result-question-card {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 10px 26px rgba(25, 28, 34, 0.05);
}

.intro-card,
.side-card,
.taking-header,
.exam-banner,
.question-card,
.map-card,
.submit-card,
.result-question-card {
  padding: 12px;
}

.side-card--mini {
  padding: 10px 12px;
}

.intro-card--hero {
  background: linear-gradient(135deg, #ffffff, #f0fdfa 50%, #f8fafc);
}

.intro-card__topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.intro-card__eyebrow,
.taking-header__eyebrow,
.result-header__eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
}

.intro-card h1,
.taking-header h1,
.result-header h1 {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 800;
  color: #191c22;
}

.intro-card p,
.taking-header p,
.result-header p,
.submit-card p,
.side-card p {
  margin: 4px 0 0;
  color: #414753;
  line-height: 1.35;
  font-size: 12px;
}

.intro-metrics,
.result-summary-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.intro-metric--wide {
  grid-column: span 2;
}

.intro-metric,
.result-summary-card {
  border-radius: 12px;
  background: #fff;
  padding: 7px 9px;
  border: 1px solid #e2e8f0;
}

.intro-metric span,
.result-summary-card__label {
  display: block;
  font-size: 12px;
  color: #727785;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.intro-metric strong,
.result-summary-card__value {
  display: block;
  margin-top: 3px;
  font-size: 15px;
  color: #191c22;
}

.result-summary-card__desc {
  margin-top: 4px;
  color: #727785;
  font-size: 11px;
  line-height: 1.3;
}

.result-summary-card--status {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff;
}

.result-summary-card--status .result-summary-card__label,
.result-summary-card--status .result-summary-card__value,
.result-summary-card--status .result-summary-card__desc {
  color: inherit;
}

.result-summary-card--accent {
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.intro-actions,
.question-card__footer,
.question-card__footer-actions,
.result-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.primary-action,
.secondary-action {
  border: none;
  border-radius: 10px;
  padding: 8px 11px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.primary-action {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.primary-action--danger {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.secondary-action {
  background: #f8fafc;
  color: #334155;
}

.intro-side,
.map-column,
.result-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-card--accent {
  background: linear-gradient(135deg, #eff6ff, #ecfeff);
}

.side-card__title,
.map-card h3,
.submit-card h3 {
  font-size: 13px;
  font-weight: 800;
  color: #191c22;
}

.bullet-list {
  margin: 8px 0 0;
  padding-left: 16px;
  color: #414753;
  line-height: 1.35;
  font-size: 11px;
}

.taking-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.taking-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.timer-shell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  border-radius: 999px;
  background: #ecfeff;
  border: 1px solid #ccfbf1;
}

.timer-shell .material-symbols-outlined {
  color: #0f766e;
}

.timer-shell strong {
  display: block;
  font-size: 17px;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
}

.timer-shell small {
  color: #0f766e;
  font-weight: 700;
}

.exam-question-prompt {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
}

.exam-question-prompt__label {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.exam-code-callout {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #0f766e;
}

.exam-code-callout__title {
  font-size: 12px;
  font-weight: 800;
}

.exam-code-callout p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.question-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-banner {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #ecfeff);
}

.exam-banner strong {
  color: #0f172a;
}

.exam-banner__warn {
  border-radius: 999px;
  padding: 6px 10px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 11px;
  font-weight: 700;
}

/* 截止后自动交卷失败的常驻提示条 */
.submit-failure-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.submit-failure-bar p {
  flex: 1;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.question-card__badge,
.result-question-card__badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 10px;
  background: #ecfeff;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
}

.question-card h2,
.result-question-card h2 {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.35;
  color: #191c22;
}

.question-card__score,
.result-question-card__score {
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
}

.question-card__body {
  margin-top: 8px;
}

.option-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.option-card {
  margin: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 9px 10px;
  background: #fff;
  width: 100%;
}

.option-card__key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #334155;
  margin-right: 10px;
  font-weight: 700;
}

.text-answer-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.text-answer-box :deep(.el-textarea__inner) {
  border-radius: 14px;
  padding: 12px 14px;
}

.text-answer-box label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.map-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.progress-track {
  margin-top: 10px;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.progress-track__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0f766e, #14b8a6);
}

.question-map-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 5px;
}

.question-map-item {
  aspect-ratio: 1;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  font-weight: 700;
  font-size: 11px;
  cursor: pointer;
}

.question-map-item.is-done {
  background: #0f766e;
  border-color: #0f766e;
  color: #fff;
}

.question-map-item.is-current {
  background: #ecfeff;
  border: 2px solid #14b8a6;
  color: #0f766e;
}

.question-map-item.is-empty {
  background: #fff;
}

.map-legend {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #475569;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-right: 8px;
}

.dot--done {
  background: #0f766e;
}

.dot--current {
  background: #14b8a6;
}

.dot--empty {
  background: #cbd5e1;
}

.submit-card__stats {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.submit-card__stats div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 0;
  border-bottom: 1px solid #e2e8f0;
}

.submit-card__stats div:last-child {
  border-bottom: none;
}

.result-alert {
  margin-top: 6px;
}

.result-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.result-answer-block,
.teacher-comment-box {
  border-radius: 12px;
  background: #f8fafc;
  padding: 8px 10px;
  margin-top: 6px;
}

.result-answer-block--light {
  background: #fff;
  border: 1px solid #e2e8f0;
}

.result-answer-block--full {
  grid-column: 1 / -1;
}

.result-answer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 6px;
}

.result-answer-block__label,
.teacher-comment-box__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #727785;
  font-weight: 700;
}

.result-answer-block__value,
.teacher-comment-box div {
  margin-top: 3px;
  color: #191c22;
  line-height: 1.3;
  white-space: pre-wrap;
}

@media (max-width: 1100px) {
  .intro-layout,
  .taking-grid,
  .result-grid,
  .intro-metrics,
  .result-summary-grid,
  .result-answer-grid {
    grid-template-columns: 1fr;
  }

  .intro-metric--wide {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .intro-card__topline,
  .page-hero,
  .taking-header,
  .exam-banner,
  .question-card__header,
  .question-card__footer,
  .question-card__footer-actions,
  .result-header,
  .result-question-card__header,
  .intro-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .question-map-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
