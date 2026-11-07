<template>
  <div class="student-labs-page">
    <section class="labs-header-card">
      <div class="labs-header-card__main">
        <div class="student-labs-hero__eyebrow">学生端 / 我的实验</div>
        <h1>实验任务</h1>
        <p>查看实验状态、题项数量、截止时间和实验小结要求后，直接进入作答。</p>
      </div>
      <div class="labs-header-card__stats">
        <span class="header-stat-chip"><strong>{{ rows.length }}</strong> 全部实验</span>
        <span class="header-stat-chip header-stat-chip--primary"><strong>{{ activeCount }}</strong> 进行中</span>
        <span class="header-stat-chip header-stat-chip--muted">{{ activeFilterLabel }} · {{ filteredRows.length }} 项</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchKeyword" placeholder="搜索实验名称、说明或班级" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部任务" clearable>
            <el-option label="全部任务" value="" />
            <el-option label="待开始" value="TODO" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已提交" :value="SUBMISSION_STATUS.SUBMITTED" />
            <el-option label="已批改" :value="SUBMISSION_STATUS.GRADED" />
          </el-select>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>{{ keywordLabel }}</span>
        <span>当前聚焦：{{ activeFilterDescription }}</span>
      </div>
    </section>

    <section class="lab-list-shell" v-loading="loading">
      <template v-if="filteredRows.length">
        <article v-for="row in filteredRows" :key="row.id" class="student-lab-card">
          <div class="student-lab-card__main">
            <div class="student-lab-card__title-line">
              <div>
                <h3>{{ row.title }}</h3>
                <p>{{ row.description || '进入实验后按题项逐个保存作答，并在确认实验小结后统一提交。' }}</p>
              </div>
              <el-tag :type="submissionTagType(row)" effect="light">{{ taskStatusLabel(row) }}</el-tag>
            </div>

            <div class="student-lab-card__meta-grid">
              <div>
                <span class="meta-label">班级</span>
                <strong>{{ row.className }}</strong>
              </div>
              <div>
                <span class="meta-label">题项</span>
                <strong>{{ itemCountLabel(row) }}</strong>
              </div>
              <div>
                <span class="meta-label">截止</span>
                <strong>{{ formatDateTime(row.dueAt) }}</strong>
                <el-tag v-if="labTimeState(row) === 'NOT_STARTED'" size="small" type="info" effect="light">未开始</el-tag>
                <el-tag v-else-if="labTimeState(row) === 'OVERDUE'" size="small" type="warning" effect="light">已截止</el-tag>
              </div>
              <div>
                <span class="meta-label">结果</span>
                <strong>{{ typeof row.totalScore === 'number' ? `${row.totalScore} 分` : '待反馈' }}</strong>
              </div>
            </div>

            <div class="student-lab-card__footer-line">
              <div class="student-lab-card__chips">
                <span class="lab-meta-chip" :class="row.summaryRequired ? 'lab-meta-chip--violet' : 'lab-meta-chip--muted'">
                  {{ row.summaryRequired ? '提交时需写实验小结' : '实验小结选填' }}
                </span>
              </div>
              <div class="student-lab-card__progress-row">
                <div class="progress-header">
                  <span>{{ progressText(row) }}</span>
                  <span>{{ progressPercent(row) }}%</span>
                </div>
                <div class="progress-track">
                  <span class="progress-fill" :style="{ width: `${progressPercent(row)}%` }"></span>
                </div>
              </div>
            </div>
          </div>

          <div class="student-lab-card__side">
            <el-button type="primary" @click="goDetail(row.id)">
              {{ actionLabel(row) }}
            </el-button>
          </div>
        </article>
      </template>
      <el-empty v-else description="暂无匹配实验任务" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import type { SubmissionStatus } from '@/types/enums';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listStudentLabs } from '@/api/labs';
import type { StudentLabListItem } from '@/types/lab';

type TaskFilter = '' | 'TODO' | 'ACTIVE' | SubmissionStatus;

const router = useRouter();
const loading = ref(false);
const rows = ref<StudentLabListItem[]>([]);
const searchKeyword = ref('');
const statusFilter = ref<TaskFilter>('');

const filteredRows = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();

  return rows.value.filter((row) => {
    const matchesKeyword =
      !keyword ||
      row.title.toLowerCase().includes(keyword) ||
      (row.description || '').toLowerCase().includes(keyword) ||
      row.className.toLowerCase().includes(keyword);
    const matchesFilter = !statusFilter.value || taskFilterOf(row) === statusFilter.value;
    return matchesKeyword && matchesFilter;
  });
});

const activeCount = computed(() => rows.value.filter((row) => taskFilterOf(row) === 'ACTIVE').length);
const activeFilterLabel = computed(() => statusFilterLabel(statusFilter.value));
const activeFilterDescription = computed(() => (statusFilter.value ? statusFilterLabel(statusFilter.value) : '全部实验任务'));
const keywordLabel = computed(() => (searchKeyword.value.trim() ? `关键词“${searchKeyword.value.trim()}”` : '未使用关键词'));

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listStudentLabs();
  } finally {
    loading.value = false;
  }
};

const goDetail = (id: number) => {
  router.push(`/student/labs/${id}`);
};

const taskFilterOf = (row: StudentLabListItem): TaskFilter => {
  if (row.submissionStatus === SUBMISSION_STATUS.GRADED) return SUBMISSION_STATUS.GRADED;
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return SUBMISSION_STATUS.SUBMITTED;
  if (row.submissionStatus === SUBMISSION_STATUS.SAVED) return 'ACTIVE';
  if (typeof row.totalScore === 'number' || !!row.teacherComment) return SUBMISSION_STATUS.GRADED;
  return 'TODO';
};

const statusFilterLabel = (value: TaskFilter) => {
  if (value === 'TODO') return '待开始';
  if (value === 'ACTIVE') return '进行中';
  if (value === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (value === SUBMISSION_STATUS.GRADED) return '已批改';
  return '全部任务';
};

const taskStatusLabel = (row: StudentLabListItem) => statusFilterLabel(taskFilterOf(row));

const submissionTagType = (row: StudentLabListItem) => {
  const status = taskFilterOf(row);
  if (status === SUBMISSION_STATUS.GRADED) return 'success';
  if (status === SUBMISSION_STATUS.SUBMITTED) return 'warning';
  if (status === 'ACTIVE') return 'primary';
  return 'info';
};

const progressPercent = (row: StudentLabListItem) => {
  const status = taskFilterOf(row);
  if (status === SUBMISSION_STATUS.GRADED) return 100;
  if (status === SUBMISSION_STATUS.SUBMITTED) return 88;
  if (status === 'ACTIVE') return 54;
  return 12;
};

const progressText = (row: StudentLabListItem) => {
  if (row.submissionStatus === SUBMISSION_STATUS.GRADED) return '已批改，可查看题项反馈与实验小结';
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return '已提交，等待教师评分';
  if (row.submissionStatus === SUBMISSION_STATUS.SAVED) return '题项草稿已保存，可继续完善';
  return '尚未开始作答';
};

const itemCountLabel = (row: StudentLabListItem) => `${row.stepCount ?? 0} 个题项`;

const actionLabel = (row: StudentLabListItem) => {
  if (row.submissionStatus === SUBMISSION_STATUS.GRADED) return '查看结果';
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return '查看详情';
  if (row.submissionStatus === SUBMISSION_STATUS.SAVED) return '继续实验';
  return '开始实验';
};

const formatDateTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-');

const labTimeState = (row: StudentLabListItem) => {
  const now = Date.now();
  if (row.startAt && now < new Date(row.startAt).getTime()) return 'NOT_STARTED';
  if (row.dueAt && now >= new Date(row.dueAt).getTime()) return 'OVERDUE';
  return 'OPEN';
};

onMounted(fetchData);
</script>

<style scoped>
.student-labs-page {
  --labs-primary: #1e40af;
  --labs-primary-soft: #eff6ff;
  --labs-border: #dbe5ef;
  --labs-text: #0f172a;
  --labs-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.labs-header-card,
.toolbar-card,
.student-lab-card {
  border: 1px solid var(--labs-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.labs-header-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 18px 20px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.92), rgba(255, 255, 255, 1));
}

.student-labs-hero__eyebrow,
.meta-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.student-labs-hero__eyebrow {
  color: var(--labs-primary);
  margin-bottom: 10px;
}

.student-labs-page h1,
.student-lab-card h3 {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
}

.student-labs-page h1 {
  font-size: 24px;
  color: var(--labs-text);
}

.labs-header-card p,
.student-lab-card p,
.student-lab-card small,
.toolbar-card__meta {
  color: var(--labs-muted);
  line-height: 1.6;
}

.labs-header-card p {
  margin: 6px 0 0;
  max-width: 720px;
  font-size: 13px;
}

.labs-header-card__stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  max-width: 460px;
}

.header-stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.header-stat-chip strong {
  color: var(--labs-text);
  font-size: 14px;
}

.header-stat-chip--primary {
  background: var(--labs-primary-soft);
  border-color: #bfdbfe;
  color: var(--labs-primary);
}

.header-stat-chip--violet {
  background: rgba(124, 58, 237, 0.08);
  border-color: rgba(124, 58, 237, 0.18);
  color: #6d28d9;
}

.header-stat-chip--muted {
  background: #f8fafc;
  color: var(--labs-muted);
}

.toolbar-card {
  padding: 12px 14px;
}

.toolbar-card__main {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.toolbar-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 10px;
  font-size: 12px;
}

.toolbar-search {
  flex: 1;
  min-width: 280px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--labs-border);
  border-radius: 12px;
  background: #f8fafc;
}

.toolbar-search .material-symbols-outlined {
  color: var(--labs-muted);
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  padding: 0;
}

.toolbar-filters {
  display: flex;
  gap: 10px;
  align-items: center;
}

.lab-list-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.student-lab-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 188px;
  gap: 16px;
  padding: 16px 18px;
}

.student-lab-card__title-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.student-lab-card__title-line h3 {
  font-size: 18px;
}

.student-lab-card__title-line p {
  margin: 4px 0 0;
  font-size: 13px;
}

.student-lab-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.lab-meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: var(--labs-text);
  font-size: 12px;
  font-weight: 600;
}

.lab-meta-chip--violet {
  background: rgba(124, 58, 237, 0.12);
  color: #7c3aed;
}

.lab-meta-chip--muted {
  background: rgba(100, 116, 139, 0.12);
  color: var(--labs-muted);
}

.student-lab-card__meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.student-lab-card__meta-grid > div {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.student-lab-card__meta-grid strong {
  color: var(--labs-text);
  font-size: 13px;
}

.student-lab-card__footer-line {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-end;
  margin-top: 12px;
}

.student-lab-card__progress-row {
  min-width: 240px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--labs-muted);
}

.progress-track {
  margin-top: 6px;
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--labs-primary), #60a5fa);
}

.student-lab-card__side {
  display: flex;
  align-items: center;
  padding-left: 14px;
  border-left: 1px dashed rgba(148, 163, 184, 0.35);
}

.student-lab-card__side .el-button {
  width: 100%;
}

@media (max-width: 1200px) {
  .student-lab-card__meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-lab-card {
    grid-template-columns: 1fr;
  }

  .student-lab-card__side {
    padding-left: 0;
    border-left: none;
    border-top: 1px dashed rgba(148, 163, 184, 0.35);
    padding-top: 12px;
  }

  .student-lab-card__footer-line {
    flex-direction: column;
    align-items: stretch;
  }

  .student-lab-card__progress-row {
    min-width: 0;
  }
}

@media (max-width: 900px) {
  .labs-header-card,
  .student-lab-card__meta-grid {
    grid-template-columns: 1fr;
  }

  .labs-header-card {
    flex-direction: column;
  }

  .labs-header-card__stats {
    justify-content: flex-start;
    max-width: none;
  }
}
</style>
