<template>
  <div class="reports-page">
    <section class="reports-header">
      <div class="reports-header__top">
        <div class="reports-header__main">
          <div class="reports-header__eyebrow">教师后台 / 实验报告批改</div>
          <h1>实验报告批改队列</h1>
          <p>按实验、学生与状态筛出待处理报告；每条报告行可进入该实验的提交总览。</p>
        </div>
        <div class="reports-header__actions">
          <el-button plain @click="resetFilters">重置筛选</el-button>
          <el-button type="primary" @click="fetchData">刷新队列</el-button>
        </div>
      </div>

      <div class="reports-header__meta">
        <span class="reports-header__meta-item"><strong>{{ rows.length }}</strong> 报告总量</span>
        <span class="reports-header__meta-item"><strong>{{ submittedCount }}</strong> 待批改</span>
        <span class="reports-header__meta-item"><strong>{{ gradedCount }}</strong> 已批改</span>
        <span class="reports-header__meta-item reports-header__meta-item--accent">{{ filteredRows.length }} 条 · {{ activeStatusLabel }} · {{ priorityDescription }}</span>
      </div>
    </section>

    <section class="summary-grid reports-summary-grid">
      <article class="summary-card summary-card--primary">
        <div class="summary-card__icon">
          <span class="material-symbols-outlined">assignment_turned_in</span>
        </div>
        <div>
          <div class="summary-card__label">报告总量</div>
          <div class="summary-card__value">{{ rows.length }}</div>
           <div class="summary-card__desc">当前报告队列全部记录。</div>
        </div>
      </article>
      <article class="summary-card summary-card--light">
        <div class="summary-card__icon summary-card__icon--amber">
          <span class="material-symbols-outlined">pending_actions</span>
        </div>
        <div>
          <div class="summary-card__label">待批改</div>
          <div class="summary-card__value">{{ submittedCount }}</div>
           <div class="summary-card__desc">已提交且尚未完成评分。</div>
        </div>
      </article>
      <article class="summary-card summary-card--light">
        <div class="summary-card__icon summary-card__icon--teal">
          <span class="material-symbols-outlined">verified</span>
        </div>
        <div>
          <div class="summary-card__label">已批改</div>
          <div class="summary-card__value">{{ gradedCount }}</div>
           <div class="summary-card__desc">已完成总评和给分。</div>
        </div>
      </article>
       <article class="summary-card summary-card--accent">
         <div class="summary-card__header-line">
           <div>
             <div class="summary-card__label">当前筛选</div>
             <div class="summary-card__value summary-card__value--small">{{ filteredRows.length }} 条</div>
          </div>
          <span class="summary-chip">{{ activeStatusLabel }}</span>
        </div>
        <div class="summary-card__desc summary-card__desc--spaced">
           {{ keywordLabel }}，聚焦 {{ priorityDescription }}。
         </div>
       </article>
     </section>

     <section class="toolbar-card">
       <div class="toolbar-card__main">
         <div class="toolbar-search">
           <span class="material-symbols-outlined">search</span>
           <el-input v-model="query.keyword" placeholder="搜索实验名、学号、学生姓名或班级" clearable />
         </div>
         <div class="toolbar-filters">
           <el-select v-model="query.labId" placeholder="全部实验" clearable>
             <el-option label="全部实验" value="" />
             <el-option
               v-for="item in labOptions"
               :key="`lab-${item.id}`"
               :label="item.title || `实验 #${item.id}`"
               :value="String(item.id)"
             />
           </el-select>
           <el-select v-model="query.status" placeholder="全部状态" clearable>
             <el-option label="全部状态" value="" />
             <el-option label="已保存" value="SAVED" />
             <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已批改" value="GRADED" />
          </el-select>
          <el-button type="primary" @click="fetchData">刷新队列</el-button>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>共 {{ rows.length }} 条报告</span>
        <span class="toolbar-divider"></span>
        <span>当前显示 {{ filteredRows.length }} 条</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="reports-console">
      <header class="reports-console__header">
        <div>
          <h2>批改队列</h2>
          <p>先做优先级判断，再进入详情页处理。</p>
        </div>
      </header>

      <div v-loading="loading" class="reports-list-shell">
        <template v-if="filteredRows.length">
          <article v-for="row in filteredRows" :key="row.id" class="report-row">
            <div class="report-row__identity">
              <div class="report-avatar">{{ studentInitial(row) }}</div>
              <div>
              <div class="report-row__title-line">
                <h3>{{ row.labTitle || `实验 #${row.labId}` }}</h3>
                <span class="report-class-chip">{{ row.className || '未标注班级' }}</span>
                <span class="report-summary-chip" :class="row.summaryRequired ? 'is-required' : ''">
                  {{ row.summaryRequired ? '要求实验小结' : '无强制小结' }}
                </span>
              </div>
                <p>{{ row.studentName || '未知学生' }} · {{ row.studentNo || row.studentUsername || '未标注学号' }}</p>
              </div>
            </div>

            <div class="report-row__status">
              <span class="meta-label">提交状态</span>
              <el-tag :type="statusTagType(row.submitStatus)" effect="light">{{ statusLabel(row.submitStatus) }}</el-tag>
              <small>{{ submittedLabel(row) }}</small>
            </div>

            <div class="report-row__score">
              <span class="meta-label">当前总分</span>
              <strong>{{ row.totalScore ?? '-' }}</strong>
              <small>{{ row.gradedAt ? `批改于 ${formatDateTime(row.gradedAt)}` : '尚未完成总评' }}</small>
            </div>

            <div class="report-row__priority">
              <span :class="['priority-pill', `priority-pill--${priorityTone(row)}`]">{{ priorityLabel(row) }}</span>
            </div>

            <div class="report-row__actions">
              <el-button plain @click="openReportView(row)">实验报告</el-button>
              <el-button type="primary" plain @click="goBatchGrade(row.id)">
                {{ row.submitStatus === 'GRADED' ? '查看批量评分' : '进入批量批改' }}
              </el-button>
              <el-button
                type="primary"
                plain
                :disabled="!row.labId"
                @click="goSubmissionOverview(row.labId)"
              >
                提交总览
              </el-button>
              <el-button plain @click="goBlankRegradeBatch(row.labId)">批量重判（填空）</el-button>
              <el-button
                v-if="row.submitStatus === 'SUBMITTED' || row.submitStatus === 'GRADED'"
                type="warning"
                plain
                :disabled="returningId !== null"
                :loading="returningId === row.id"
                @click="handleReturn(row)"
              >
                打回
              </el-button>
            </div>
          </article>
        </template>
        <el-empty v-else description="暂无匹配实验报告" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listTeacherLabReports, listTeacherLabs, returnTeacherLabReport } from '@/api/labs';
import type { LabItem, LabReportItem, LabReportStatus } from '@/types/lab';

const router = useRouter();
const loading = ref(false);
const returningId = ref<number | null>(null);
const rows = ref<LabReportItem[]>([]);
const teacherLabs = ref<LabItem[]>([]);
const query = reactive({
  keyword: '',
  status: '',
  labId: '',
});

const filteredRows = computed(() => rows.value.filter((row) => !query.labId || String(row.labId) === query.labId));
const submittedCount = computed(() => rows.value.filter(row => row.submitStatus === 'SUBMITTED').length);
const gradedCount = computed(() => rows.value.filter(row => row.submitStatus === 'GRADED').length);
const activeStatusLabel = computed(() => statusLabel(query.status || undefined));
const keywordLabel = computed(() => query.keyword.trim() ? `关键词“${query.keyword.trim()}”` : '未使用关键词');
const priorityDescription = computed(() => query.status === 'SUBMITTED' ? '待批改报告' : query.labId ? '单实验报告队列' : '实验报告全队列');
const labOptions = computed(() =>
  [...teacherLabs.value].sort((left, right) => left.title.localeCompare(right.title, 'zh-CN')),
);

const fetchTeacherLabs = async () => {
  teacherLabs.value = await listTeacherLabs();
};

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listTeacherLabReports({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
    });
  } finally {
    loading.value = false;
  }
};

const resetFilters = async () => {
  query.keyword = '';
  query.status = '';
  query.labId = '';
  await fetchData();
};

const goDetail = (reportId: number) => {
  router.push(`/teacher/lab-reports/${reportId}`);
};

const goBatchGrade = (reportId: number) => {
  router.push(`/teacher/lab-reports/${reportId}/batch-grade`);
};

const returnConfirmMessage = (status?: LabReportStatus) =>
  status === 'GRADED'
    ? '该报告已批改，打回后成绩将清除，学生可重新编辑提交，是否继续？'
    : '打回后学生可修改作答并重新提交，是否继续？';

const handleReturn = async (row: LabReportItem) => {
  if (returningId.value !== null) {
    return;
  }

  try {
    await ElMessageBox.confirm(returnConfirmMessage(row.submitStatus), '打回确认', { type: 'warning' });
  } catch {
    return;
  }

  returningId.value = row.id;
  try {
    await returnTeacherLabReport(row.id);
    ElMessage.success('已打回，学生可重新编辑提交');
    await fetchData();
  } finally {
    returningId.value = null;
  }
};

const goBlankRegrade = (labId?: number) => {
  if (!labId) {
    return;
  }
  router.push(`/teacher/labs/${labId}/blank-regrade`);
};

const goBlankRegradeBatch = (labId?: number) => {
  if (!labId) {
    return;
  }
  router.push(`/teacher/labs/${labId}/blank-regrade/batch`);
};

const goSubmissionOverview = (labId?: number) => {
  if (!labId) {
    return;
  }
  router.push({ name: 'teacher-lab-submission-overview', params: { labId } });
};

const openReportView = (row: LabReportItem) => {
  if (!row.labId || !row.studentId) {
    return;
  }
  const href = router.resolve({
    name: 'teacher-lab-report-view',
    params: {
      labId: row.labId,
      studentId: row.studentId,
    },
  }).href;
  window.open(href, '_blank', 'noopener,noreferrer');
};

const goReportsByLab = async (row: LabReportItem) => {
  query.labId = row.labId ? String(row.labId) : '';
  query.keyword = row.labTitle || '';
  await fetchData();
};

const statusLabel = (status?: LabReportStatus) => {
  if (status === 'SAVED') return '已保存';
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  return status || '全部状态';
};

const statusTagType = (status?: LabReportStatus) => {
  if (status === 'GRADED') return 'success';
  if (status === 'SUBMITTED') return 'warning';
  return 'info';
};

const priorityTone = (row: LabReportItem) => {
  if (row.submitStatus === 'SUBMITTED') return 'warning';
  if (row.submitStatus === 'GRADED') return 'success';
  return 'muted';
};

const priorityLabel = (row: LabReportItem) => {
  if (row.submitStatus === 'SUBMITTED') return '优先批改';
  if (row.submitStatus === 'GRADED') return '已完成';
  return '草稿跟进';
};

const studentInitial = (row: LabReportItem) => (row.studentName || row.studentNo || '学').slice(0, 1).toUpperCase();

const formatDateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '-';

const submittedLabel = (row: LabReportItem) => {
  if (row.submittedAt) {
    return `提交于 ${formatDateTime(row.submittedAt)}`;
  }
  return '尚无正式提交时间';
};

onMounted(() => {
  const initialLabId = router.currentRoute.value.query.labId;
  const initialKeyword = router.currentRoute.value.query.keyword;
  query.labId = typeof initialLabId === 'string' ? initialLabId : '';
  query.keyword = typeof initialKeyword === 'string' ? initialKeyword : '';
  void fetchTeacherLabs();
  void fetchData();
});
</script>

<style scoped>
.reports-page {
  --reports-primary: #0f766e;
  --reports-primary-soft: #ecfeff;
  --reports-border: #dbe5ef;
  --reports-text: #0f172a;
  --reports-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.reports-header,
.toolbar-card,
.reports-console,
.summary-card {
  border: 1px solid var(--reports-border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.reports-header {
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.76), rgba(255, 255, 255, 1));
}

.reports-header__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.reports-header__eyebrow,
.meta-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.reports-header__eyebrow {
  color: var(--reports-primary);
  margin-bottom: 2px;
}

.reports-header h1,
.reports-console__header h2,
.report-row h3 {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
}

.reports-header h1 {
  font-size: 20px;
  color: var(--reports-text);
}

.reports-header p,
.reports-console__header p,
.summary-card__desc,
.report-row p,
.report-row small {
  color: var(--reports-muted);
  line-height: 1.6;
}

.reports-header p {
  margin: 2px 0 0;
  max-width: 520px;
  font-size: 12px;
}

.reports-header__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.reports-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.reports-header__meta-item {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 10px;
  font-weight: 700;
}

.reports-header__meta-item strong {
  margin-right: 4px;
  color: var(--reports-text);
}

.reports-header__meta-item--accent {
  background: var(--reports-primary-soft);
  color: var(--reports-primary);
}

.reports-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.summary-card {
  padding: 8px 12px;
}

.summary-card__icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.2);
}

.summary-card--primary {
  border: none;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
}

.summary-card--primary .summary-card__label,
.summary-card--primary .summary-card__desc {
  color: rgba(255, 255, 255, 0.82);
}

.summary-card__icon--amber {
  background: #fff7ed !important;
  color: #d97706 !important;
}

.summary-card__icon--teal {
  background: var(--reports-primary-soft) !important;
  color: var(--reports-primary) !important;
}

.summary-card__label {
  margin-top: 4px;
  font-size: 10px;
  color: #64748b;
}

.summary-card__value {
  margin-top: 0;
  font-size: 16px;
  font-weight: 700;
}

.summary-card__value--small {
  font-size: 14px;
}

.summary-card__header-line {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--reports-primary-soft);
  color: var(--reports-primary);
  font-size: 10px;
  font-weight: 700;
}

.summary-card__desc--spaced {
  margin-top: 4px;
}

.toolbar-card {
  padding: 10px 12px;
}

.toolbar-card__main {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.toolbar-search {
  position: relative;
  flex: 1;
}

.toolbar-search .material-symbols-outlined {
  position: absolute;
  top: 50%;
  left: 12px;
  transform: translateY(-50%);
  color: #94a3b8;
  z-index: 1;
}

.toolbar-search :deep(.el-input__wrapper) {
  padding-left: 32px;
  min-height: 34px;
}

.toolbar-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-filters .el-select {
  width: 150px;
}

.toolbar-filters .el-select:first-child {
  width: 220px;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  color: var(--reports-muted);
  font-size: 12px;
}

.toolbar-divider {
  width: 1px;
  height: 14px;
  background: var(--reports-border);
}

.reports-console {
  padding: 10px 12px 12px;
}

.reports-console__header {
  margin-bottom: 8px;
}

.reports-console__header h2 {
  font-size: 16px;
  color: var(--reports-text);
}

.reports-list-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.report-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) 0.85fr 0.85fr 0.75fr minmax(280px, auto);
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--reports-border);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
}

.report-row__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.report-row__identity {
  display: flex;
  gap: 8px;
  align-items: center;
}

.report-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 700;
}

.report-row__title-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.report-class-chip {
  padding: 2px 7px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 10px;
  font-weight: 600;
}

.report-summary-chip {
  padding: 2px 7px;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
}

.report-summary-chip.is-required {
  background: #f5f3ff;
  color: #7c3aed;
}

.report-row p {
  margin: 2px 0 0;
}

.report-row__status,
.report-row__score {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.report-row__score strong {
  font-size: 16px;
  color: var(--reports-text);
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}

.priority-pill--warning {
  background: #fff7ed;
  color: #b45309;
}

.priority-pill--success {
  background: #ecfdf5;
  color: #15803d;
}

.priority-pill--muted {
  background: #f1f5f9;
  color: #475569;
}

@media (max-width: 1180px) {
  .reports-summary-grid,
  .report-row {
    grid-template-columns: 1fr;
  }

  .reports-header__top,
  .toolbar-card__main {
    flex-direction: column;
    align-items: flex-start;
  }

  .reports-header__actions,
  .reports-summary-grid {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-filters {
    width: 100%;
    flex-wrap: wrap;
  }

  .toolbar-filters .el-select {
    width: 100%;
  }

  .report-row__actions {
    justify-content: flex-start;
  }
}
</style>
