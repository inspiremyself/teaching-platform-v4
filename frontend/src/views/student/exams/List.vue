<template>
  <div class="student-exams-page workbench-page">
    <section class="workbench-header student-exams-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>考试任务流</h1>
          <p>将可参加、进行中和已出结果的考试按任务节奏收拢到同一页面。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="loadExams" :loading="loading">刷新考试列表</el-button>
        </div>
      </div>

      <div class="workbench-meta student-exams-meta">
        <span class="workbench-meta__item"><strong>{{ startableCount }}</strong> 可参加</span>
        <span class="workbench-meta__item"><strong>{{ resumableCount }}</strong> 进行中</span>
        <span class="workbench-meta__item"><strong>{{ resultCount }}</strong> 已出结果</span>
        <span class="workbench-meta__item workbench-meta__item--accent">{{ activeFilterLabel }}</span>
      </div>
    </section>

    <section class="exams-toolbar-card">
      <div class="toolbar-search">
        <span class="material-symbols-outlined">search</span>
        <el-input v-model="searchQuery" placeholder="搜索考试标题或班级" clearable />
      </div>
      <div class="toolbar-filters">
        <el-select v-model="stageFilter" clearable placeholder="全部阶段">
          <el-option label="可参加" value="start" />
          <el-option label="进行中" value="resume" />
          <el-option label="已出结果" value="result" />
          <el-option label="暂不可用" value="locked" />
        </el-select>
      </div>
    </section>

    <div v-if="loading && !rows.length" class="loading-shell">
      <el-skeleton :rows="8" animated />
    </div>

    <section v-else class="exam-grid">
      <article v-for="row in filteredRows" :key="row.id" class="exam-card exam-card--compact">
        <div class="exam-card__head">
          <div class="exam-card__title-wrap">
            <div class="exam-card__topline">
              <div class="exam-card__badge">{{ stageLabel(row) }}</div>
              <span class="exam-class">{{ row.className || '未绑定班级' }}</span>
            </div>
            <h2>{{ row.title }}</h2>
            <p>{{ availabilityHint(row) }}</p>
          </div>
          <div :class="['exam-card__status', `exam-card__status--${statusTone(row)}`]">
            {{ statusLabel(row.status) }}
          </div>
        </div>

        <div class="exam-card__info-row">
          <div class="exam-info-chip">
            <span>考试窗口</span>
            <strong>{{ formatWindow(row.startAt, row.endAt) }}</strong>
          </div>
          <div class="exam-info-chip">
            <span>时长</span>
            <strong>{{ row.durationMinutes }} 分钟</strong>
          </div>
          <div class="exam-info-chip exam-info-chip--wide">
            <span>操作提示</span>
            <strong>{{ actionHint(row) }}</strong>
          </div>
        </div>

        <div class="exam-card__footer exam-card__footer--compact">
          <span class="exam-inline-hint">{{ stageLabel(row) }} · {{ statusLabel(row.status) }}</span>
          <button class="exam-action exam-action--compact" type="button" @click="handleCardAction(row)">
            {{ actionText(row) }}
            <span class="material-symbols-outlined">arrow_forward</span>
          </button>
        </div>
      </article>

      <AppEmptyState v-if="!filteredRows.length" description="暂无匹配考试" class="empty-state" />
    </section>

    <el-dialog v-model="startDialogVisible" title="确认开始考试" width="min(560px, calc(100vw - 40px))" destroy-on-close>
      <div v-if="startDialogRow" class="start-dialog">
        <h3>{{ startDialogRow.title }}</h3>
        <p class="start-dialog__class">{{ startDialogRow.className || '未绑定班级' }}</p>
        <div class="start-dialog__grid">
          <div class="start-dialog__item">
            <span>考试窗口</span>
            <strong>{{ formatWindow(startDialogRow.startAt, startDialogRow.endAt) }}</strong>
          </div>
          <div class="start-dialog__item">
            <span>时长</span>
            <strong>{{ startDialogRow.durationMinutes }} 分钟</strong>
          </div>
        </div>
        <p v-if="startDialogRow.status === ACTIVITY_STATUS.PUBLISHED && !startDialogRow.endAt" class="start-dialog__notice">
          本场未设置统一截止时间，按个人限时收卷；如教师需要统一截止时间，需新建考试。
        </p>
        <ul class="start-dialog__rules">
          <li>开始后连续计时 {{ startDialogRow.durationMinutes }} 分钟；若考试窗口先结束，则按窗口结束时间收卷。</li>
          <li>交卷后不可修改答案。</li>
          <li>主观题结果可能稍后更新。</li>
        </ul>
        <p v-if="startDisabledReason" class="start-dialog__warn">{{ startDisabledReason }}</p>
      </div>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" :disabled="!!startDisabledReason" @click="confirmStart">
          开始考试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ACTIVITY_STATUS, EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listExams, startExam } from '@/api/exams'
import type { ExamListItem } from '@/types/exam'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'

const router = useRouter()
const rows = ref<ExamListItem[]>([])
const loading = ref(false)
const searchQuery = ref('')
const stageFilter = ref<'start' | 'resume' | 'result' | 'locked' | ''>('')
const startDialogVisible = ref(false)
const startDialogRow = ref<ExamListItem | null>(null)
const starting = ref(false)

const startableCount = computed(() => rows.value.filter(row => row.canStart).length)
const resumableCount = computed(() => rows.value.filter(row => row.canResume).length)
const resultCount = computed(() => rows.value.filter(row => row.canViewResult).length)
const activeFilterLabel = computed(() => {
  if (stageFilter.value === 'start') return '可参加'
  if (stageFilter.value === 'resume') return '进行中'
  if (stageFilter.value === 'result') return '已出结果'
  if (stageFilter.value === 'locked') return '暂不可用'
  return '全部考试'
})

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return rows.value.filter(row => {
    const matchesKeyword = !keyword || [row.title, row.className ?? ''].some(item => item.toLowerCase().includes(keyword))
    const stage = stageKey(row)
    const matchesStage = !stageFilter.value || stage === stageFilter.value
    return matchesKeyword && matchesStage
  })
})

const loadExams = async () => {
  loading.value = true
  try {
    rows.value = await listExams()
  } finally {
    loading.value = false
  }
}

const stageKey = (row: ExamListItem) => {
  if (row.canResume) return 'resume'
  if (row.canStart) return 'start'
  if (row.canViewResult) return 'result'
  return 'locked'
}

const stageLabel = (row: ExamListItem) => {
  const stage = stageKey(row)
  if (stage === 'resume') return '继续作答'
  if (stage === 'start') return '待参加'
  if (stage === 'result') return '结果反馈'
  return '尚未开放'
}

const statusLabel = (status: string) => {
  if (status === ACTIVITY_STATUS.DRAFT) return '草稿'
  if (status === ACTIVITY_STATUS.PUBLISHED) return '已发布'
  if (status === ACTIVITY_STATUS.CLOSED) return '已关闭'
  return status
}

const statusTone = (row: ExamListItem) => {
  if (row.canResume) return 'resume'
  if (row.canStart) return 'start'
  if (row.canViewResult) return 'result'
  return 'locked'
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const formatWindow = (startAt?: string | null, endAt?: string | null) => {
  if (!startAt && !endAt) {
    return '时间待定'
  }
  return `${formatDateTime(startAt)} - ${formatDateTime(endAt)}`
}

const availabilityHint = (row: ExamListItem) => {
  const now = Date.now()
  if (row.submissionStatus === EXAM_SUBMISSION_STATUS.IN_PROGRESS && !row.canResume) {
    return '本次作答已超时，等待系统结算'
  }
  if (row.canResume) return '你已进入答题状态，可从上次进度继续。'
  if (row.startAt && new Date(row.startAt).getTime() > now) {
    return `考试将于 ${formatDateTime(row.startAt)} 开始`
  }
  if (!row.submissionStatus && row.endAt && new Date(row.endAt).getTime() <= now) {
    return '已错过考试时间'
  }
  if (row.canStart) return '当前处于允许参加的考试窗口，建议尽快开始。'
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) {
    return '你已完成交卷，主观题仍可能在等待教师批改。'
  }
  if (row.canViewResult) return '考试已结束，可查看作答结果与教师反馈。'
  return row.status === ACTIVITY_STATUS.DRAFT ? '教师仍在准备试卷，学生端尚不可见。' : '当前不在可参加或可查看结果的状态。'
}

const actionHint = (row: ExamListItem) => {
  if (row.canResume) return '进入后直接回到当前答题界面。'
  if (row.canStart) return '确认考试信息后进入答题，开始后连续计时。'
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return '可查看自动评分与当前批改进度。'
  if (row.canViewResult) return '可查看总分、题目反馈和状态。'
  return '当前仅展示考试信息。'
}

const actionText = (row: ExamListItem) => {
  if (row.canResume) return '继续考试'
  if (row.canStart) return '开始考试'
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return '查看进度'
  if (row.canViewResult) return '查看结果'
  return '暂不可用'
}

// 弹窗内仅在开始时间晚于当前时刻时给出禁用提示，其余情况允许确认
const startDisabledReason = computed(() => {
  const row = startDialogRow.value
  if (!row?.startAt) return ''
  return new Date(row.startAt).getTime() > Date.now()
    ? `考试将于 ${formatDateTime(row.startAt)} 开始`
    : ''
})

// 列表行主操作：按行能力分流，直接进入考生只需要的状态，避免经过中间页
const handleCardAction = (row: ExamListItem) => {
  if (row.canStart) {
    startDialogRow.value = row
    startDialogVisible.value = true
    return
  }
  const submitted = row.submissionStatus === SUBMISSION_STATUS.SUBMITTED
    || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED
  if (row.canResume || row.canViewResult || submitted) {
    router.push(`/student/exams/${row.id}`)
    return
  }
  if (row.startAt && new Date(row.startAt).getTime() > Date.now()) {
    ElMessage.warning(`考试尚未开始，开始时间 ${formatDateTime(row.startAt)}`)
  } else {
    ElMessage.warning('当前不在可参加或可查看结果的窗口')
  }
}

const confirmStart = async () => {
  const row = startDialogRow.value
  if (!row || starting.value || startDisabledReason.value) return
  starting.value = true
  try {
    await startExam(row.id)
    startDialogVisible.value = false
    router.push(`/student/exams/${row.id}`)
  } catch {
    // request.ts 已统一 toast；保留弹窗供重试或取消
  } finally {
    starting.value = false
  }
}

onMounted(loadExams)
</script>

<style scoped>
.student-exams-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.student-exams-page h1 {
  margin: 0;
  color: #191c22;
}

.student-exams-page p {
  margin: 6px 0 0;
  color: #414753;
  max-width: 760px;
}

.exams-toolbar-card,
.exam-card {
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}

.exams-toolbar-card {
  padding: 14px 16px;
  display: flex;
  gap: 14px;
  align-items: center;
}

.toolbar-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: var(--radius-lg);
  background: var(--color-surface-muted);
  padding: 0 14px;
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.toolbar-filters {
  min-width: 220px;
}

.exam-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.exam-card {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exam-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.exam-card__topline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.exam-class {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: 600;
}

.exam-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.exam-card__badge {
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-pill);
  padding: 6px 10px;
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.exam-card h2 {
  margin: 0;
  color: #191c22;
}

.exam-card p {
  margin: 4px 0 0;
  color: #727785;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-caption);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.exam-card__status {
  border-radius: var(--radius-pill);
  padding: 6px 10px;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.exam-card__status--start {
  background: var(--color-status-active-soft);
  color: var(--color-brand-teacher);
}

.exam-card__status--resume {
  background: var(--color-status-warning-soft);
  color: var(--color-status-warning);
}

.exam-card__status--result {
  background: var(--color-brand-student-soft);
  color: var(--color-brand-student);
}

.exam-card__status--locked {
  background: var(--color-status-draft-soft);
  color: var(--color-text-muted);
}

.exam-card__info-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.exam-info-chip {
  border-radius: var(--radius-lg);
  background: var(--color-surface-muted);
  padding: 7px 9px;
  border: 1px solid var(--color-border);
}

.exam-info-chip span {
  display: block;
  font-size: var(--font-size-sm);
  color: #727785;
}

.exam-info-chip strong {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-heading);
  color: #191c22;
}

.exam-card__footer {
  margin-top: auto;
}

.exam-card__footer--compact {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.exam-inline-hint {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.exam-action {
  border: none;
  border-radius: var(--radius-lg);
  background: #191c22;
  color: var(--color-text-inverse);
  padding: 8px 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

.exam-action--compact {
  width: auto;
}

.empty-state {
  grid-column: 1 / -1;
}

.start-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: #191c22;
}

.start-dialog h3 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.start-dialog__class {
  margin: 0;
  color: #414753;
  font-size: var(--font-size-base);
}

.start-dialog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.start-dialog__item {
  background: #f7f8fa;
  border-radius: var(--radius-lg);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.start-dialog__item span {
  font-size: var(--font-size-sm);
  color: #6b7280;
}

.start-dialog__notice {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-lg);
  background: #fff7e6;
  color: #9a5b00;
  font-size: var(--font-size-base);
}

.start-dialog__rules {
  margin: 0;
  padding-left: 18px;
  color: #414753;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
}

.start-dialog__warn {
  margin: 0;
  color: #c2410c;
  font-size: var(--font-size-base);
}

@media (max-width: 1024px) {
  .exam-card__info-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .start-dialog__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .exams-toolbar-card,
  .exam-card__head,
  .exam-card__footer--compact {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
