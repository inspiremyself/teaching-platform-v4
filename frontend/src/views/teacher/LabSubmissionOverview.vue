<template>
  <WorkbenchShell
    title="实验班级总览"
    description="班级全员 × 子题矩阵。点击学生名查看历次提交，点击题列查看全班答案。"
  >
    <template #actions>
      <ActionButton label="刷新" tone="ghost" :disabled="loading" @click="fetchOverview" />
      <ActionButton label="批量重判" tone="ghost" @click="goBlankRegrade" />
    </template>
    <template #meta>
      <span class="workbench-meta__item"><strong>{{ overview?.students.length ?? 0 }}</strong> 学生</span>
      <span class="workbench-meta__item"><strong>{{ overview?.items.length ?? 0 }}</strong> 题项</span>
      <span class="workbench-meta__item workbench-meta__item--accent">{{ overview?.labTitle || '暂无实验' }}</span>
    </template>
    <AppTableCard v-loading="loading">
      <el-table :data="overview?.students ?? []" empty-text="暂无总览数据" :header-cell-style="headerCellStyle">
        <el-table-column label="学生" min-width="160" fixed>
          <template #default="{ row: student }">
            <button type="button" class="overview-student-link" @click="goStudentSubmissions(student.studentId, student.studentName, student.studentNo)">
              <strong>{{ student.studentName }}</strong>
              <span>{{ student.studentNo }}</span>
            </button>
          </template>
        </el-table-column>
        <el-table-column
          v-for="item in overview?.items ?? []"
          :key="item.stepId"
          :label="`第${item.stepNo}题`"
          min-width="140"
        >
          <template #header>
            <button type="button" class="overview-item-link" @click="goItemWall(item.stepId)">{{ item.title }}</button>
          </template>
          <template #default="{ row: student }">
            <div class="overview-cell">
              <span class="overview-cell__badge">{{ cellOf(student, item.stepId)?.answered ? '已答' : '未答' }}</span>
              <span v-if="cellOf(student, item.stepId)?.hasText" class="overview-cell__meta">文字</span>
              <span v-if="(cellOf(student, item.stepId)?.imageCount ?? 0) > 0" class="overview-cell__meta">
                图 {{ cellOf(student, item.stepId)?.imageCount }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交状态" width="120">
          <template #default="{ row: student }">
            <AppStatusTag v-if="student.submitStatus" :tone="statusTone(student.submitStatus)" :label="statusLabel(student.submitStatus)" />
            <span v-else class="overview-matrix__muted">未提交</span>
          </template>
        </el-table-column>
        <el-table-column label="批改操作" width="220" align="right" fixed="right">
          <template #default="{ row: student }">
            <div class="workbench-actions">
              <ActionButton v-if="student.submissionId != null" label="批改报告" tone="primary" @click="router.push({ name: 'teacher-lab-submission-overview', params: { labId: labId() }, query: { submissionId: student.submissionId } })" />
              <ActionButton v-if="student.submissionId != null" label="实验报告" tone="ghost" @click="openReportView(student)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="140">
          <template #default="{ row: student }">
            <strong v-if="student.behavior">{{ formatTotalDuration(student.behavior) }}</strong>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="粘贴" width="140">
          <template #default="{ row: student }">
            <strong v-if="student.behavior">{{ student.behavior.pasteCount }}次 · {{ student.behavior.pastedChars }}字符</strong>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="120">
          <template #default="{ row: student }">
            <AppStatusTag v-bind="resolveStatusOf('behaviorRisk', riskKeyOf(student.behavior))" size="small" />
          </template>
        </el-table-column>
      </el-table>
    </AppTableCard>
  </WorkbenchShell>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTeacherLabSubmissionOverview } from '@/api/labs';
import WorkbenchShell from './components/workbench/WorkbenchShell.vue';
import ActionButton from './components/workbench/ActionButton.vue';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import AppTableCard from '@/components/ui/AppTableCard.vue';
import type { LabReportStatus, LabSubmissionOverview, LabSubmissionOverviewCell, LabSubmissionOverviewStudent } from '@/types/lab';
import { resolveStatusPresentation, resolveStatusOf } from '@/components/ui/statusPresentation';
import {
  formatActiveDuration,
  formatTotalDuration,
  hasNoBehaviorData,
  riskKeyOf,
  riskTagTooltip,
} from './components/behaviorDisplay';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const overview = ref<LabSubmissionOverview | null>(null);

const labId = () => Number(route.params.labId);

const fetchOverview = async () => {
  loading.value = true;
  try {
    overview.value = await getTeacherLabSubmissionOverview(labId());
  } finally {
    loading.value = false;
  }
};

const goStudentSubmissions = (studentId: number, studentName?: string, studentNo?: string) => {
  router.push({
    name: 'teacher-student-lab-submissions',
    params: { studentId },
    query: {
      studentName: studentName || undefined,
      studentNo: studentNo || undefined,
    },
  });
};

const goItemWall = (itemId: number) => {
  router.push({ name: 'teacher-lab-item-answers', params: { labId: labId(), itemId } });
};

const openReportView = (student: LabSubmissionOverviewStudent) => {
  if (student.submissionId == null) return;
  const href = router.resolve({
    name: 'teacher-lab-report-view',
    params: { labId: labId(), studentId: student.studentId },
  }).href;
  window.open(href, '_blank', 'noopener,noreferrer');
};

const statusOf = (status: string) => resolveStatusPresentation({ module: 'lab', kind: 'submission', scene: 'teacher', status });

const statusLabel = (status?: LabReportStatus | null) => (status ? statusOf(status).label : '未知');

const statusTone = (status?: LabReportStatus | null) => (status ? statusOf(status).tone : 'neutral');

const headerCellStyle = () => ({ background: '#f8fafc', color: '#475569', fontWeight: 700 });
const cellOf = (row: LabSubmissionOverviewStudent, stepId: number): LabSubmissionOverviewCell | undefined =>
  row.cells.find(cell => cell.stepId === stepId);
const goBlankRegrade = () => router.push(`/teacher/labs/${labId()}/blank-regrade/batch`);

onMounted(() => {
  void fetchOverview();
});
</script>

<style scoped>
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview-header {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 0;
}

.overview-header__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.overview-eyebrow {
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
  margin-bottom: 8px;
}

.overview-header h1 {
  margin: 0 0 8px;}

.overview-header p {
  margin: 0;
  color: var(--color-text-muted);
}

.overview-matrix-shell {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 16px;
}

.overview-matrix-wrap {
  overflow: auto;
}

.overview-matrix {
  width: 100%;
  border-collapse: collapse;
  min-width: 720px;
}

.overview-matrix th,
.overview-matrix td {
  border: 1px solid var(--color-border);
  padding: 12px;
  vertical-align: middle;
}

.overview-matrix__corner,
.overview-matrix__student {
  position: sticky;
  left: 0;
  background: var(--color-bg-app);
  z-index: 1;
  min-width: 180px;
  text-align: left;
}

.overview-matrix__item-head {
  min-width: 140px;
  background: var(--color-bg-app);
}

.overview-item-link,
.overview-student-link {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  color: inherit;
  text-align: left;
}

.overview-item-link strong,
.overview-student-link strong {
  color: var(--color-brand-teacher);
}

.overview-matrix__cell.is-answered {
  background: var(--color-status-active-soft);
}

.overview-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.overview-cell__badge {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #334155;
}

.overview-cell__meta {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  background: var(--color-bg-subtle);
  border-radius: 999px;
  padding: 2px 8px;
}

.overview-matrix__status {
  min-width: 110px;
}

.overview-matrix__action-head,
.overview-matrix__action {
  min-width: 150px;
  background: #fdfefe;
}

.overview-matrix__action-head {
  background: var(--color-bg-app);
}

.overview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.overview-matrix__behavior-head,
.overview-matrix__behavior {
  min-width: 130px;
  background: #fdfefe;
}

.overview-matrix__behavior-head {
  background: var(--color-bg-app);
}

.overview-matrix__behavior strong {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  white-space: nowrap;
}

.overview-matrix__behavior {
  font-size: var(--font-size-sm);
}

.risk-tag--muted {
  opacity: 0.65;
}

.overview-matrix__muted {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-base);
}
</style>
