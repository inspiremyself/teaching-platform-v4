<template>
  <div class="grading-page workbench-page">
    <section class="workbench-header grading-header-card grading-header-card--refactored">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="grading-header__eyebrow">考试管理 / {{ examTitle }} / 批改工作流</div>
          <h1>{{ examTitle }}</h1>
          <p>{{ examSummary }}</p>
        </div>
        <div class="workbench-header__actions grading-header-actions">
          <button class="header-button header-button--secondary" type="button" @click="router.push('/teacher/exams')">
            <span class="material-symbols-outlined">arrow_back</span>
            返回考试管理
          </button>
          <button class="header-button header-button--secondary" type="button" :disabled="releasing" @click="releaseScores">
            <span class="material-symbols-outlined">campaign</span>
            {{ scoreReleased ? '重新发布成绩' : '发布成绩' }}
          </button>
          <button class="header-button header-button--primary" type="button" @click="fetchData" :disabled="loading">
            <span class="material-symbols-outlined">refresh</span>
            刷新结果
          </button>
        </div>
      </div>

      <div class="workbench-meta grading-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 总提交数</span>
        <span class="workbench-meta__item"><strong>{{ pendingCount }}</strong> 待人工处理</span>
        <span class="workbench-meta__item"><strong>{{ gradedCount }}</strong> 已评分</span>
        <span class="workbench-meta__item workbench-meta__item--accent">{{ activeStudentLabel }}</span>
      </div>
    </section>

    <section class="grading-layout" v-loading="loading">
      <aside class="queue-panel">
        <div class="queue-panel__header">
          <div>
            <h2>提交队列</h2>
            <p>按当前收卷结果浏览学生答卷，并保持与原型一致的工作流节奏。</p>
          </div>
          <span class="queue-panel__badge">{{ pendingCount }} 待处理</span>
        </div>

        <div class="queue-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索学生姓名或账号" clearable />
        </div>

        <div class="queue-filter-row">
          <el-select v-model="statusFilter" clearable placeholder="全部状态">
            <el-option label="进行中" :value="EXAM_SUBMISSION_STATUS.IN_PROGRESS" />
            <el-option label="待评分" :value="SUBMISSION_STATUS.SUBMITTED" />
            <el-option label="已评分" :value="SUBMISSION_STATUS.GRADED" />
            <el-option label="超时提交" :value="EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED" />
          </el-select>
        </div>

        <div class="queue-meta-row">
          <span>{{ filteredRows.length }} / {{ rows.length }} 份答卷</span>
          <span>{{ statusFilter ? `已筛到 ${submissionStatusLabel(statusFilter)}` : '全部阶段' }}</span>
        </div>

        <div v-if="filteredRows.length" class="queue-list">
          <button
            v-for="row in filteredRows"
            :key="row.submissionId"
            type="button"
            :class="['queue-item', activeSubmissionId === row.submissionId ? 'is-active' : '']"
            @click="selectSubmission(row)"
          >
            <div class="queue-item__main">
              <div class="queue-avatar">{{ getAvatarText(row.studentName, row.studentUsername) }}</div>
              <div class="queue-item__body">
                <div class="queue-item__top">
                  <strong>{{ row.studentName }}</strong>
                  <span :class="['queue-status', `queue-status--${submissionStatusTone(row.status)}`]">
                    {{ submissionStatusLabel(row.status) }}
                  </span>
                </div>
                <div class="queue-item__meta">{{ row.studentUsername }} · {{ submissionTimeLabel(row) }}</div>
                <div class="queue-item__score">
                  自动 {{ formatScore(row.autoScore) }} · 人工 {{ formatScore(row.manualScore) }} · 总分 {{ formatScore(row.totalScore) }}
                </div>
              </div>
            </div>
          </button>
        </div>
        <el-empty v-else description="暂无匹配提交记录" />
      </aside>

      <main class="submission-panel">
        <div v-if="detail" class="submission-panel__inner">
          <section class="submission-context-card">
            <div class="submission-context-card__lead">
              <div class="submission-context-card__avatar">{{ getAvatarText(detail.studentName, detail.studentName) }}</div>
              <div>
                <h2>{{ detail.studentName }}</h2>
                <p>{{ activeStudentSubline }}</p>
              </div>
            </div>
            <div class="submission-context-card__nav">
              <span>{{ activeStudentOrderLabel }}</span>
            </div>
          </section>

          <section class="question-stack">
            <article
              v-for="(answer, index) in detail.answers"
              :key="answer.questionId"
              :class="['question-card', isManualQuestion(answer) ? 'question-card--manual' : 'question-card--auto']"
            >
              <div class="question-card__header">
                <div class="question-card__title-wrap">
                  <span class="question-badge">第{{ index + 1 }}题</span>
                  <div>
                    <h3>{{ answer.stem }}</h3>
                    <p>{{ questionTypeLabel(answer.questionType) }} · 满分 {{ answer.questionScore }}</p>
                  </div>
                </div>
                <span :class="['question-status', isManualQuestion(answer) ? 'question-status--manual' : answer.isCorrect ? 'question-status--correct' : 'question-status--wrong']">
                  {{ questionStatusLabel(answer) }}
                </span>
              </div>

              <div class="question-card__body">
                <div class="answer-block">
                  <div class="answer-block__label">学生答案</div>
                  <div class="answer-block__value">{{ formatDisplayAnswer(answer.studentAnswer) }}</div>
                </div>

                <div class="answer-grid">
                  <div class="answer-block answer-block--light">
                    <div class="answer-block__label">标准答案</div>
                    <div class="answer-block__value">{{ formatDisplayAnswer(answer.standardAnswer) }}</div>
                  </div>
                  <div class="answer-block answer-block--light">
                    <div class="answer-block__label">当前得分</div>
                    <div class="answer-block__value">{{ formatScore(answer.score) }} / {{ answer.questionScore }}</div>
                  </div>
                </div>

                <div v-if="isManualQuestion(answer)" class="manual-grade-box">
                  <div class="manual-grade-box__row">
                    <div class="manual-grade-field">
                      <label>分数</label>
                      <el-input-number
                        v-model="gradeMap[answer.questionId].score"
                        :min="0"
                        :max="answer.questionScore"
                        controls-position="right"
                      />
                    </div>
                    <div class="manual-grade-field manual-grade-field--full">
                      <label>评语</label>
                      <el-input
                        v-model="gradeMap[answer.questionId].teacherComment"
                        type="textarea"
                        :rows="3"
                        placeholder="输入针对该题的批改意见"
                      />
                    </div>
                  </div>
                </div>
                <div v-else class="auto-grade-tip auto-grade-tip--stack">
                  <span class="material-symbols-outlined">smart_toy</span>
                  <span>建议分 {{ formatScore(answer.suggestedScore) }}，当前 {{ formatScore(answer.score) }}</span>
                  <span>{{ answer.judgeDetail || '当前无自动判题说明。' }}</span>
                </div>
              </div>
            </article>
          </section>
        </div>
        <el-empty v-else description="请选择左侧提交记录查看详情" />
      </main>

      <aside class="summary-side">
        <section class="score-panel">
          <h2>评分汇总</h2>
          <div class="score-summary-list">
            <div class="score-summary-item">
              <span>自动分</span>
              <strong>{{ formatScore(detail?.autoScore) }}</strong>
            </div>
            <div class="score-summary-item">
              <span>人工分</span>
              <strong>{{ currentManualScore }}</strong>
            </div>
            <div class="score-summary-item score-summary-item--total">
              <span>总分</span>
              <strong>{{ currentTotalScore }}</strong>
            </div>
          </div>

          <div class="completion-block">
            <div class="completion-block__row">
              <span>人工评分完成度</span>
              <strong>{{ completionPercent }}%</strong>
            </div>
            <div class="completion-bar">
              <div class="completion-bar__fill" :style="{ width: `${completionPercent}%` }"></div>
            </div>
          </div>
        </section>

        <section class="action-panel">
          <el-alert
            v-if="detail?.status === SUBMISSION_STATUS.GRADED"
            type="info"
            :closable="false"
            title="当前答卷已评分完成，如需验证保存链路请切换到待评分答卷。"
            class="grading-readonly-alert"
          />
          <button class="action-panel__primary" type="button" :disabled="!detail || detail.status === SUBMISSION_STATUS.GRADED || submitting" @click="submitGrade">
            <span class="material-symbols-outlined">save</span>
            保存评分
          </button>
          <button class="action-panel__secondary" type="button" :disabled="!nextSubmission" @click="selectSubmission(nextSubmission!)">
            切换到下一份
          </button>
          <div class="action-panel__meta">
            <div class="action-panel__tip">
              <span class="material-symbols-outlined">tips_and_updates</span>
              <p>主观题评分会直接通过 `gradeExamSubmission` 提交，保存后左侧队列立即刷新。</p>
            </div>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExamDetail, getExamResults, getExamSubmissionDetail, gradeExamSubmission, releaseExamScores } from '@/api/exams'
import type { ExamAnswerDetail, ExamSubmissionDetail, ExamSubmissionListItem, GradeExamPayload } from '@/types/exam'

interface GradeItem {
  score: number
  teacherComment: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const releasing = ref(false)
const scoreReleased = ref(false)
const rows = ref<ExamSubmissionListItem[]>([])
const detail = ref<ExamSubmissionDetail | null>(null)
const examTitle = ref('考试批改工作流')
const searchQuery = ref('')
const statusFilter = ref('')
const activeSubmissionId = ref<number | null>(null)
const gradeMap = reactive<Record<number, GradeItem>>({})

const examId = computed(() => route.params.id as string)

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return rows.value.filter(row => {
    const matchesKeyword = !keyword || row.studentName.toLowerCase().includes(keyword) || row.studentUsername.toLowerCase().includes(keyword)
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const pendingCount = computed(() => rows.value.filter(row => row.status === SUBMISSION_STATUS.SUBMITTED || row.status === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED).length)
const gradedCount = computed(() => rows.value.filter(row => row.status === SUBMISSION_STATUS.GRADED).length)
const activeRow = computed(() => rows.value.find(row => row.submissionId === activeSubmissionId.value) ?? null)
const activeStudentLabel = computed(() => activeRow.value?.studentName ?? '尚未选择')
const examSummary = computed(() => `共 ${rows.value.length} 份提交记录，其中 ${pendingCount.value} 份待处理、${gradedCount.value} 份已评分。`)
const activeStudentSubline = computed(() => {
  if (!activeRow.value) {
    return '请选择左侧学生答卷进入批改工作流。'
  }
  return `${activeRow.value.studentUsername} · ${submissionStatusLabel(activeRow.value.status)} · ${submissionTimeLabel(activeRow.value)}`
})
const activeStudentOrderLabel = computed(() => {
  const index = filteredRows.value.findIndex(row => row.submissionId === activeSubmissionId.value)
  if (index < 0) {
    return '未选中答卷'
  }
  return `学生 ${index + 1} / ${filteredRows.value.length}`
})
const nextSubmission = computed(() => {
  const index = filteredRows.value.findIndex(row => row.submissionId === activeSubmissionId.value)
  if (index < 0) {
    return filteredRows.value[0] ?? null
  }
  return filteredRows.value[index + 1] ?? null
})
const manualAnswers = computed(() => detail.value?.answers.filter(answer => isManualQuestion(answer)) ?? [])
const currentManualScore = computed(() => manualAnswers.value.reduce((sum, answer) => sum + (gradeMap[answer.questionId]?.score ?? answer.score ?? 0), 0))
const currentTotalScore = computed(() => (detail.value?.autoScore ?? 0) + currentManualScore.value)
const completionPercent = computed(() => {
  if (!manualAnswers.value.length) {
    return 100
  }
  const completed = manualAnswers.value.filter(answer => gradeMap[answer.questionId] && gradeMap[answer.questionId].score >= 0).length
  return Math.round((completed / manualAnswers.value.length) * 100)
})

const resetGradeMap = () => {
  Object.keys(gradeMap).forEach(key => delete gradeMap[Number(key)])
}

const initGradeMap = () => {
  resetGradeMap()
  if (!detail.value?.answers) {
    return
  }
  detail.value.answers.forEach(answer => {
    if (isManualQuestion(answer)) {
      gradeMap[answer.questionId] = {
        score: answer.score ?? 0,
        teacherComment: answer.teacherComment || '',
      }
    }
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const [resultRows, examDetail] = await Promise.all([getExamResults(examId.value), getExamDetail(examId.value)])
    rows.value = resultRows
    examTitle.value = examDetail.title || '考试批改工作流'
    scoreReleased.value = examDetail.scoreReleased === true
    const target = resultRows.find(row => row.submissionId === activeSubmissionId.value) ?? resultRows[0] ?? null
    if (target) {
      await selectSubmission(target)
    } else {
      detail.value = null
      activeSubmissionId.value = null
    }
  } finally {
    loading.value = false
  }
}

const selectSubmission = async (row: ExamSubmissionListItem) => {
  activeSubmissionId.value = row.submissionId
  detail.value = await getExamSubmissionDetail(row.submissionId)
  initGradeMap()
}

const submitGrade = async () => {
  if (!detail.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const subjectiveAnswers = manualAnswers.value.map(answer => ({
      questionId: answer.questionId,
      score: gradeMap[answer.questionId]?.score ?? 0,
      teacherComment: gradeMap[answer.questionId]?.teacherComment || undefined,
    }))
    const payload: GradeExamPayload = { answers: subjectiveAnswers }
    await gradeExamSubmission(detail.value.submissionId, payload)
    ElMessage.success('评分保存成功')
    await fetchData()
  } finally {
    submitting.value = false
  }
}

const releaseScores = async () => {
  if (releasing.value) return
  try {
    await ElMessageBox.confirm('确认发布本次考试成绩？发布后学生即可查看。', '发布成绩', {
      confirmButtonText: '确认发布',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  releasing.value = true
  try {
    await releaseExamScores(examId.value)
    // 文案读取发布前的状态：原值 true 表示本次为「重新发布」
    ElMessage.success(scoreReleased.value ? '考试成绩已重新发布' : '考试成绩已发布')
    await fetchData()
  } catch {
    // 错误提示已由 request.ts 响应拦截器统一弹出，这里只阻止未捕获的 Promise 拒绝
    return
  } finally {
    releasing.value = false
  }
}

const isManualQuestion = (answer: ExamAnswerDetail) => answer.isCorrect === null

const submissionStatusLabel = (status: string) => {
  if (status === EXAM_SUBMISSION_STATUS.IN_PROGRESS) return '进行中'
  if (status === SUBMISSION_STATUS.SUBMITTED) return '待评分'
  if (status === SUBMISSION_STATUS.GRADED) return '已评分'
  if (status === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return '超时提交'
  return status || '未知'
}

const submissionStatusTone = (status: string) => {
  if (status === SUBMISSION_STATUS.GRADED) return 'success'
  if (status === SUBMISSION_STATUS.SUBMITTED) return 'warning'
  if (status === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return 'danger'
  return 'info'
}

const questionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    TEXT: '主观题',
    SHORT: '主观题',
    SINGLE: '单选题',
    MULTIPLE: '多选题',
    JUDGE: '判断题',
  }
  return map[type] ?? type
}

const questionStatusLabel = (answer: ExamAnswerDetail) => {
  if (isManualQuestion(answer)) {
    return '人工评分'
  }
  return answer.isCorrect ? '自动判对' : '自动判错'
}

const formatScore = (score: number | null | undefined) => (typeof score === 'number' ? String(score) : '-')

/** 判断题真值集合，与 PRD FR-3 约定保持一致（大小写经 toUpperCase 归一） */
const JUDGE_TRUE_VALUES = new Set(['TRUE'])
const JUDGE_FALSE_VALUES = new Set(['FALSE'])

/** 判断是否为普通对象（排除数组与 null） */
const isPlainRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

/**
 * 将任意答案节点压平成可读文本。
 * 数组按“，”连接；对象优先取 answer/text/value/answerText/selectedKey/code。
 */
const toScalarText = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) {
    return value.map(toScalarText).filter(Boolean).join('，')
  }
  if (isPlainRecord(value)) {
    for (const key of ['answer', 'text', 'value', 'answerText', 'selectedKey', 'code']) {
      const text = toScalarText(value[key])
      if (text) return text
    }
  }
  return ''
}

/**
 * 按 kind 解析学生答案；无 kind 时按标准答案字段兜底。
 * 返回空字符串表示无可读内容，由调用方统一转“未作答”。
 */
const formatAnswerNode = (node: unknown): string => {
  if (node === null || node === undefined) return ''
  if (!isPlainRecord(node)) return toScalarText(node)

  const kind = typeof node.kind === 'string' ? node.kind : ''
  if (kind === 'single') return toScalarText(node.selectedKey)
  if (kind === 'multiple') return toScalarText(node.selectedKeys)
  if (kind === 'judge') {
    const raw = String(node.value ?? '').trim().toUpperCase()
    if (JUDGE_TRUE_VALUES.has(raw)) return '正确'
    if (JUDGE_FALSE_VALUES.has(raw)) return '错误'
    return toScalarText(node.value)
  }
  if (kind === 'fill') return toScalarText(node.blanks)
  if (kind === 'code') return toScalarText(node.code)
  if (kind === 'text') return toScalarText(node.text ?? node.answer ?? node.answerText)

  // 标准答案形态（PRD FR-3 规则 5）：按顺序取第一个非空字段；
  // 该字段无法压平为标量时，原样输出该字段的 JSON 字符串，继续向后兜底
  for (const key of ['correctAnswer', 'correct', 'answer', 'answers', 'blanks', 'selectedKey', 'selectedKeys']) {
    const field = node[key]
    if (field === undefined || field === null) continue
    if (Array.isArray(field) && field.length === 0) continue
    if (typeof field === 'string' && !field.trim()) continue
    const text = toScalarText(field)
    if (text) return text
    const rawField = JSON.stringify(field)
    if (rawField && rawField !== '{}') return rawField
  }
  // 无任何可读字段时原样输出对象 JSON，禁止 [object Object]
  const rawNode = JSON.stringify(node)
  return rawNode && rawNode !== '{}' ? rawNode : ''
}

/** 批改页答案展示：空值与不可读内容统一显示“未作答” */
const formatDisplayAnswer = (value: string | null): string => {
  if (!value || !value.trim()) return '未作答'
  try {
    return formatAnswerNode(JSON.parse(value)) || '未作答'
  } catch {
    return value
  }
}

const submissionTimeLabel = (row: ExamSubmissionListItem) => {
  const target = row.submittedAt || row.startedAt
  if (!target) {
    return '时间未记录'
  }
  const date = new Date(target)
  if (Number.isNaN(date.getTime())) {
    return target
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const getAvatarText = (name: string, fallback: string) => {
  const raw = (name || fallback || '').trim()
  return raw ? raw.slice(0, 2).toUpperCase() : 'ST'
}

onMounted(fetchData)
</script>

<style scoped>
.grading-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.grading-header-card,
.queue-panel,
.submission-context-card,
.question-card,
.score-panel,
.action-panel {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 28px;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.06);
}

.grading-header-card {
  padding: 24px 28px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
}

.grading-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.grading-breadcrumb .is-current {
  color: #0f766e;
  font-weight: 700;
}

.grading-header-card h1 {
  margin: 8px 0 0;
  font-size: 32px;
  font-weight: 800;
  color: #0f172a;
}

.grading-header-card p {
  margin: 10px 0 0;
  color: #64748b;
}

.grading-header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.header-button {
  border: none;
  border-radius: 18px;
  padding: 12px 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.header-button--secondary {
  background: #f8fafc;
  color: #334155;
}

.header-button--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
}

.grading-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  border-radius: 24px;
  padding: 22px;
}

.summary-card--teal {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
}

.summary-card--amber {
  background: linear-gradient(135deg, #c2410c, #f59e0b);
  color: #fff;
}

.summary-card--slate {
  background: linear-gradient(135deg, #0f172a, #334155);
  color: #fff;
}

.summary-card--light {
  background: #fff;
  color: #0f172a;
}

.summary-card__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.82;
}

.summary-card__value {
  margin-top: 10px;
  font-size: 32px;
  font-weight: 800;
}

.summary-card__value--small {
  font-size: 22px;
  line-height: 1.3;
}

.summary-card__desc {
  margin-top: 10px;
  font-size: 13px;
  opacity: 0.76;
}

.grading-layout {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(0, 1.38fr) minmax(250px, 0.82fr);
  gap: 18px;
  align-items: start;
}

.queue-panel {
  padding: 20px;
  position: sticky;
  top: 24px;
}

.queue-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.queue-panel__header h2,
.score-panel h2 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.queue-panel__header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.queue-panel__badge {
  align-self: flex-start;
  padding: 8px 12px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.queue-search {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 0 14px;
}

.queue-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.queue-filter-row {
  margin-top: 12px;
}

.queue-meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
}

.queue-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 420px);
  overflow: auto;
}

.queue-item {
  border: none;
  text-align: left;
  width: 100%;
  background: #fff;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  padding: 14px;
  cursor: pointer;
}

.queue-item.is-active {
  background: #ecfeff;
  border-color: #14b8a6;
  box-shadow: inset 4px 0 0 #0f766e;
}

.queue-item__main {
  display: flex;
  gap: 12px;
}

.queue-avatar,
.submission-context-card__avatar {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.queue-item__body {
  min-width: 0;
  flex: 1;
}

.queue-item__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.queue-status {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.queue-status--success {
  background: #ecfdf5;
  color: #0f766e;
}

.queue-status--warning {
  background: #fff7ed;
  color: #c2410c;
}

.queue-status--danger {
  background: #fef2f2;
  color: #dc2626;
}

.queue-status--info {
  background: #eff6ff;
  color: #1d4ed8;
}

.queue-item__meta,
.queue-item__score {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.submission-panel__inner {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.submission-context-card {
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.submission-context-card__lead {
  display: flex;
  align-items: center;
  gap: 16px;
}

.submission-context-card h2 {
  margin: 0;
  font-size: 24px;
}

.submission-context-card p {
  margin: 8px 0 0;
  color: #64748b;
}

.submission-context-card__nav {
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.question-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-card {
  padding: 22px;
}

.question-card--manual {
  border-color: #99f6e4;
  box-shadow: 0 18px 38px rgba(20, 184, 166, 0.08);
}

.question-card--auto {
  background: #f8fafc;
}

.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.question-card__title-wrap {
  display: flex;
  gap: 12px;
}

.question-badge {
  min-width: 34px;
  height: 34px;
  border-radius: 12px;
  background: #0f766e;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
}

.question-card--auto .question-badge {
  background: #94a3b8;
}

.question-card h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.5;
}

.question-card p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.question-status {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.question-status--manual {
  background: #fff7ed;
  color: #c2410c;
}

.question-status--correct {
  background: #ecfdf5;
  color: #0f766e;
}

.question-status--wrong {
  background: #fef2f2;
  color: #dc2626;
}

.question-card__body {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.answer-block {
  border-radius: 18px;
  background: #f8fafc;
  padding: 16px;
}

.answer-block--light {
  background: #fff;
  border: 1px solid #e2e8f0;
}

.answer-block__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #64748b;
  font-weight: 700;
}

.answer-block__value {
  margin-top: 10px;
  color: #0f172a;
  line-height: 1.7;
  white-space: pre-wrap;
}

.answer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.manual-grade-box {
  border-radius: 20px;
  border: 1px solid #ccfbf1;
  background: #f0fdfa;
  padding: 16px;
}

.manual-grade-box__row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 14px;
}

.manual-grade-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.manual-grade-field--full {
  min-width: 0;
}

.manual-grade-field label {
  font-size: 12px;
  color: #475569;
  font-weight: 700;
}

.auto-grade-tip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 13px;
}

.summary-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 24px;
}

.score-panel,
.action-panel {
  padding: 22px;
}

.score-summary-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
  background: #f8fafc;
}

.score-summary-item--total {
  background: linear-gradient(135deg, #0f172a, #334155);
  color: #fff;
}

.score-summary-item strong {
  font-size: 18px;
}

.completion-block {
  margin-top: 18px;
}

.completion-block__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.completion-bar {
  margin-top: 10px;
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.completion-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0f766e, #14b8a6);
}

.action-panel {
  background: linear-gradient(180deg, #0f172a, #1e293b);
  color: #fff;
}

.action-panel__primary,
.action-panel__secondary {
  width: 100%;
  border: none;
  border-radius: 18px;
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.action-panel__primary {
  background: #14b8a6;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.action-panel__secondary {
  margin-top: 10px;
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
}

.action-panel__meta {
  margin-top: 18px;
  border-top: 1px solid rgba(226, 232, 240, 0.12);
  padding-top: 18px;
}

.action-panel__tip {
  display: flex;
  gap: 10px;
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1.7;
}

.action-panel__tip p {
  margin: 0;
}

@media (max-width: 1280px) {
  .grading-layout {
    grid-template-columns: minmax(260px, 0.95fr) minmax(0, 1.4fr);
  }

  .summary-side {
    grid-column: 1 / -1;
    position: static;
  }
}

@media (max-width: 1024px) {
  .grading-summary-grid,
  .grading-layout,
  .answer-grid,
  .manual-grade-box__row {
    grid-template-columns: 1fr;
  }

  .queue-panel,
  .summary-side {
    position: static;
  }
}

@media (max-width: 768px) {
  .grading-header-card,
  .submission-context-card,
  .question-card__header,
  .queue-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .grading-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
