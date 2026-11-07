<template>
  <div class="student-homeworks-page workbench-page">
    <section class="workbench-header student-homeworks-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="student-homeworks-header__eyebrow">学生端 / 我的作业</div>
          <div class="student-homeworks-header__title-row">
            <h1>我的作业</h1>
            <span class="student-homeworks-header__summary">{{ filteredRows.length }} 条任务</span>
          </div>
        </div>
      </div>

      <div class="workbench-meta student-homeworks-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 当前可见</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 进行中</span>
        <span class="workbench-meta__item"><strong>{{ upcomingCount }}</strong> 即将截止</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ completedCount }}</strong> 已提交/批改</span>
      </div>
    </section>

    <section class="student-toolbar-card">
      <div class="student-toolbar-card__main">
        <div class="student-toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索作业标题或班级" clearable />
        </div>
        <div class="student-toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部作业状态" clearable>
            <el-option label="全部作业状态" value="" />
            <el-option label="进行中" :value="ACTIVITY_STATUS.PUBLISHED" />
            <el-option label="已关闭" :value="ACTIVITY_STATUS.CLOSED" />
            <el-option label="草稿" :value="ACTIVITY_STATUS.DRAFT" />
          </el-select>
          <el-select v-model="submissionFilter" placeholder="全部提交状态" clearable>
            <el-option label="全部提交状态" value="" />
            <el-option label="未开始" value="NONE" />
            <el-option label="已保存" :value="SUBMISSION_STATUS.SAVED" />
            <el-option label="已提交" :value="SUBMISSION_STATUS.SUBMITTED" />
            <el-option label="已批改" :value="SUBMISSION_STATUS.GRADED" />
          </el-select>
        </div>
      </div>
      <div class="student-toolbar-card__meta">
        <span>共 {{ rows.length }} 条作业</span>
        <span class="student-toolbar-divider"></span>
        <span>当前筛出 {{ filteredRows.length }} 条</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="student-homework-list" v-loading="loading">
      <template v-if="filteredRows.length">
          <article v-for="row in filteredRows" :key="row.id" class="student-homework-card student-homework-card--compact">
            <div class="student-homework-card__head">
              <div class="student-homework-card__title-wrap">
                <div class="student-homework-card__topline">
                  <span class="student-homework-card__eyebrow">{{ row.className || '当前班级作业' }}</span>
                  <span class="student-homework-chip">{{ dueHint(row.dueAt) }}</span>
              </div>
              <div class="student-homework-card__title-row">
                <h2>{{ row.title }}</h2>
                <el-tag :type="statusTagType(row.submissionStatus)" effect="light">
                  {{ submissionStatusLabel(row.submissionStatus) }}
                </el-tag>
              </div>
                <p class="student-homework-card__desc">
                  {{ row.description || '暂无补充说明，进入详情页查看完整作答区与反馈信息。' }}
                </p>
              </div>
              <div class="student-homework-card__cta">
                <el-button type="primary" @click="goDetail(row.id)">进入详情页</el-button>
              </div>
            </div>

          <div class="student-homework-card__info-row">
            <div class="homework-info-chip">
              <span class="student-homework-card__label">作业状态</span>
              <strong>{{ taskStageLabel(row) }}</strong>
            </div>
            <div class="homework-info-chip">
              <span class="student-homework-card__label">截止时间</span>
              <strong>{{ formatDateTime(row.dueAt) }}</strong>
            </div>
            <div class="homework-info-chip">
              <span class="student-homework-card__label">当前结果</span>
              <strong>{{ typeof row.totalScore === 'number' ? `${row.totalScore} 分` : '待反馈' }}</strong>
            </div>
            <div class="homework-info-chip homework-info-chip--status">
              <span class="student-homework-card__label">提交状态</span>
              <strong>{{ submissionStatusLabel(row.submissionStatus) }}</strong>
            </div>
          </div>
        </article>
      </template>

      <el-empty v-else description="暂无匹配作业" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ACTIVITY_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listStudentHomeworks } from '@/api/homeworks';
import type { HomeworkListItem, HomeworkStatus, HomeworkSubmissionStatus } from '@/types/homework';

const router = useRouter();
const loading = ref(false);
const rows = ref<HomeworkListItem[]>([]);
const searchQuery = ref('');
const statusFilter = ref<HomeworkStatus | ''>('');
const submissionFilter = ref<HomeworkSubmissionStatus | 'NONE' | ''>('');

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();

  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.title.toLowerCase().includes(keyword) ||
      row.className?.toLowerCase().includes(keyword) ||
      row.description?.toLowerCase().includes(keyword);

    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    const matchesSubmission =
      !submissionFilter.value ||
      (submissionFilter.value === 'NONE' ? !row.submissionStatus : row.submissionStatus === submissionFilter.value);

    return matchesKeyword && matchesStatus && matchesSubmission;
  });
});

const activeCount = computed(() => filteredRows.value.filter(row => row.status === ACTIVITY_STATUS.PUBLISHED).length);
const completedCount = computed(() => filteredRows.value.filter(row => row.submissionStatus === SUBMISSION_STATUS.SUBMITTED || row.submissionStatus === SUBMISSION_STATUS.GRADED).length);
const upcomingCount = computed(() => {
  const now = Date.now();
  const nextDay = now + 24 * 60 * 60 * 1000;
  return filteredRows.value.filter(row => {
    if (!row.dueAt) return false;
    const dueTime = new Date(row.dueAt).getTime();
    return !Number.isNaN(dueTime) && dueTime >= now && dueTime <= nextDay;
  }).length;
});

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listStudentHomeworks();
  } finally {
    loading.value = false;
  }
};

const goDetail = (id: number) => {
  router.push(`/student/homeworks/${id}`);
};

const resetFilters = () => {
  searchQuery.value = '';
  statusFilter.value = '';
  submissionFilter.value = '';
};

const statusLabel = (status: HomeworkStatus) => {
  if (status === ACTIVITY_STATUS.PUBLISHED) return '进行中';
  if (status === ACTIVITY_STATUS.CLOSED) return '已关闭';
  if (status === ACTIVITY_STATUS.DRAFT) return '草稿';
  return status || '未知';
};

const taskStageLabel = (row: HomeworkListItem) => {
  if (row.submissionStatus === SUBMISSION_STATUS.GRADED) return '已批改';
  if (row.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (row.submissionStatus === SUBMISSION_STATUS.SAVED) return '进行中';
  return statusLabel(row.status);
};

const submissionStatusLabel = (status?: HomeworkSubmissionStatus | null) => {
  if (status === SUBMISSION_STATUS.SAVED) return '已保存';
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
  return '未开始';
};

const statusTagType = (status?: HomeworkSubmissionStatus | null) => {
  if (status === SUBMISSION_STATUS.GRADED) return 'success';
  if (status === SUBMISSION_STATUS.SUBMITTED) return 'warning';
  if (status === SUBMISSION_STATUS.SAVED) return 'info';
  return 'primary';
};

const dueHint = (value?: string | null) => {
  if (!value) return '时间待定';
  const dueTime = new Date(value).getTime();
  if (Number.isNaN(dueTime)) return '截止时间待确认';

  const diff = dueTime - Date.now();
  const dayMs = 24 * 60 * 60 * 1000;
  const days = Math.ceil(Math.abs(diff) / dayMs);

  if (diff < 0) return `已截止 ${days} 天`;
  if (days <= 1) return '24 小时内截止';
  return `${days} 天后截止`;
};

const formatDateTime = (value?: string | null) => {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
};

onMounted(fetchData);
</script>

<style scoped>
.student-homeworks-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.student-homeworks-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: stretch;
  padding: 30px 32px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top right, rgba(191, 219, 254, 0.4), transparent 28%),
    linear-gradient(135deg, rgba(255,255,255,0.95), rgba(219,234,254,0.9));
  border: 1px solid rgba(191, 219, 254, 0.7);
  box-shadow: 0 20px 44px rgba(30, 41, 59, 0.08);
}

.student-homeworks-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.student-homeworks-hero h1 {
  margin: 10px 0 0;
  font-size: 34px;
  color: #191c22;
}

.student-homeworks-hero p {
  margin: 10px 0 0;
  max-width: 720px;
  color: #475569;
  font-size: 15px;
  line-height: 1.75;
}

.student-homeworks-hero__badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 150px;
  padding: 18px;
  border-radius: 28px;
  background: #0f172a;
  color: #ffffff;
}

.student-homeworks-hero__badge .material-symbols-outlined {
  font-size: 28px;
}

.student-homeworks-hero__badge strong {
  margin-top: 8px;
  font-size: 32px;
}

.student-homeworks-hero__badge small {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.72);
}

.student-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.student-summary-card {
  display: flex;
  gap: 16px;
  padding: 24px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.student-summary-card--primary {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  color: #ffffff;
}

.student-summary-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 18px;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  flex-shrink: 0;
}

.student-summary-card--primary .student-summary-card__icon {
  background: rgba(255,255,255,0.16);
  color: #ffffff;
}

.student-summary-card__icon--amber {
  background: rgba(254, 243, 199, 0.9);
  color: #b45309;
}

.student-summary-card__icon--green {
  background: rgba(220, 252, 231, 0.9);
  color: #15803d;
}

.student-summary-card__label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.student-summary-card--primary .student-summary-card__label,
.student-summary-card--primary .student-summary-card__desc,
.student-summary-card--primary .student-summary-card__value {
  color: #ffffff;
}

.student-summary-card__value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  color: #191c22;
}

.student-summary-card__desc {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.student-toolbar-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.05);
}

.student-toolbar-card__main {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
}

.student-toolbar-search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  padding: 0 16px;
  min-height: 52px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.student-toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.student-toolbar-filters {
  display: flex;
  gap: 12px;
}

.student-toolbar-filters :deep(.el-select) {
  width: 180px;
}

.student-toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
}

.student-toolbar-divider {
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}

.student-homework-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.student-homework-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.05);
}

.student-homework-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.student-homework-card__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.student-homework-card__header h2 {
  margin: 8px 0 0;
  font-size: 22px;
  color: #191c22;
}

.student-homework-card__desc {
  margin: 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.75;
}

.student-homework-card__meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.student-homework-card__meta-grid > div {
  padding: 14px 16px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.student-homework-card__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.student-homework-card__meta-grid strong {
  color: #191c22;
  font-size: 14px;
}

.student-homework-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.student-homework-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .student-homework-list,
  .student-summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .student-homeworks-hero,
  .student-toolbar-card,
  .student-homework-card,
  .student-summary-card {
    padding: 20px;
    border-radius: 24px;
  }

  .student-homeworks-hero,
  .student-toolbar-card__main,
  .student-toolbar-filters,
  .student-homework-card__header,
  .student-homework-card__footer,
  .student-homework-card__meta-grid {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
}

/* Student homework refactor overrides */
.student-homeworks-page {
  gap: 16px;
}

.student-homeworks-header {
  padding: 10px 12px 6px;
}

.student-homeworks-header__eyebrow {
  font-size: 11px;
  font-weight: 800;
  color: #2563eb;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.student-homeworks-header__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.student-homeworks-header__summary {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #dbe4f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.student-homeworks-page h1 {
  margin: 0;
  font-size: 22px;
}

.student-homeworks-page p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.35;
}

.student-toolbar-card {
  gap: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.student-toolbar-card__main {
  gap: 12px;
}

.student-toolbar-search {
  min-height: 36px;
  border-radius: 10px;
  padding: 0 9px;
}

.student-toolbar-filters {
  gap: 10px;
}

.student-toolbar-filters :deep(.el-select) {
  width: 148px;
}

.student-toolbar-card__meta {
  font-size: 11px;
  gap: 8px;
}

.student-homework-list {
  gap: 10px;
}

.student-homework-card {
  gap: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.student-homework-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.student-homework-card__title-wrap {
  min-width: 0;
  flex: 1;
}

.student-homework-card__topline {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
}

.student-homework-card__title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.student-homework-card__title-row h2 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
}

.student-homework-card__desc {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.25;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.student-homework-card__info-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.homework-info-chip {
  padding: 7px 9px;
  border-radius: 9px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.homework-info-chip strong {
  color: #191c22;
  font-size: 11px;
}

.homework-info-chip--status {
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.96), rgba(239, 246, 255, 0.9));
}

.student-homework-card__cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.student-homework-card__cta :deep(.el-button) {
  padding: 6px 10px;
  min-height: 28px;
}

.student-homework-chip {
  padding: 5px 8px;
  font-size: 11px;
}

@media (max-width: 1024px) {
  .student-homework-card__info-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .student-homeworks-header__title-row,
  .student-homework-card__head,
  .student-homework-card__title-row,
  .student-homework-card__cta {
    flex-direction: column;
    align-items: stretch;
  }

  .student-homework-card__cta {
    width: 100%;
  }

  .student-homework-card__info-row {
    grid-template-columns: 1fr;
  }
}
</style>
