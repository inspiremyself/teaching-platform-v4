<template>
  <div class="student-exams-page workbench-page">
    <section class="workbench-header student-exams-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="student-exams-header__eyebrow">学生空间 / 考试任务</div>
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

    <section class="toolbar-card">
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
          <button class="exam-action exam-action--compact" type="button" @click="goDetail(row)">
            {{ actionText(row) }}
            <span class="material-symbols-outlined">arrow_forward</span>
          </button>
        </div>
      </article>

      <el-empty v-if="!filteredRows.length" description="暂无匹配考试" class="empty-state" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ACTIVITY_STATUS, EXAM_SUBMISSION_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listExams } from '@/api/exams'
import type { ExamListItem } from '@/types/exam'

const router = useRouter()
const rows = ref<ExamListItem[]>([])
const loading = ref(false)
const searchQuery = ref('')
const stageFilter = ref<'start' | 'resume' | 'result' | 'locked' | ''>('')

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
  if (row.canStart) return '进入详情页后可先查看说明，再开始考试。'
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return '进入详情页后可查看自动评分与当前批改进度。'
  if (row.canViewResult) return '进入详情页后查看总分、题目反馈和状态。'
  return '当前仅展示考试信息。'
}

const actionText = (row: ExamListItem) => {
  if (row.canResume) return '继续考试'
  if (row.canStart) return '查看并开始'
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED) return '查看进度'
  if (row.canViewResult) return '查看结果'
  return '查看详情'
}

const goDetail = (row: ExamListItem) => {
  router.push(`/student/exams/${row.id}`)
}

onMounted(loadExams)
</script>

<style scoped>
.student-exams-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.student-exams-header__eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  font-weight: 700;
  margin-bottom: 6px;
}

.student-exams-page h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  color: #191c22;
}

.student-exams-page p {
  margin: 6px 0 0;
  color: #414753;
  max-width: 760px;
}

.toolbar-card,
.exam-card {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(193, 198, 214, 0.45);
  box-shadow: 0 8px 24px rgba(25, 28, 34, 0.05);
}

.toolbar-card {
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
  border-radius: 12px;
  background: #f8fafc;
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
  font-size: 12px;
  color: #64748b;
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
  border-radius: 999px;
  padding: 6px 10px;
  background: #ecfeff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.exam-card h2 {
  margin: 0;
  font-size: 16px;
  color: #191c22;
}

.exam-card p {
  margin: 4px 0 0;
  color: #727785;
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.exam-card__status {
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 700;
}

.exam-card__status--start {
  background: #ecfdf5;
  color: #0f766e;
}

.exam-card__status--resume {
  background: #fff7ed;
  color: #c2410c;
}

.exam-card__status--result {
  background: #eff6ff;
  color: #1d4ed8;
}

.exam-card__status--locked {
  background: #f1f5f9;
  color: #64748b;
}

.exam-card__info-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.exam-info-chip {
  border-radius: 12px;
  background: #f8fafc;
  padding: 7px 9px;
  border: 1px solid #e2e8f0;
}

.exam-info-chip span {
  display: block;
  font-size: 12px;
  color: #727785;
}

.exam-info-chip strong {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
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
  color: #64748b;
  font-size: 11px;
}

.exam-action {
  border: none;
  border-radius: 12px;
  background: #191c22;
  color: #fff;
  padding: 8px 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.exam-action--compact {
  width: auto;
}

.empty-state {
  grid-column: 1 / -1;
}

@media (max-width: 1100px) {
  .exam-card__info-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .toolbar-card,
  .exam-card__head,
  .exam-card__footer--compact {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
