<template>
  <WorkbenchShell
    title="实验班级总览"
    description="班级全员 × 子题矩阵。点击学生名查看历次提交，点击题列查看全班答案。"
  >
    <template #actions>
      <button class="action-button action-button--ghost" type="button" @click="goBackToGrading">返回批改工作区</button>
      <button class="action-button action-button--ghost" type="button" :disabled="loading" @click="fetchOverview">刷新</button>
      <button class="action-button action-button--ghost" type="button" @click="goBlankRegrade">批量重判</button>
    </template>
    <template #meta>
      <span class="workbench-meta__item"><strong>{{ overview?.students.length ?? 0 }}</strong> 学生</span>
      <span class="workbench-meta__item"><strong>{{ overview?.items.length ?? 0 }}</strong> 题项</span>
      <span class="workbench-meta__item workbench-meta__item--accent">{{ overview?.labTitle || '暂无实验' }}</span>
    </template>
    <div class="workbench-table-card" v-loading="loading">
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
            <el-tag v-if="student.submitStatus" :type="statusTagType(student.submitStatus)" effect="light">{{ statusLabel(student.submitStatus) }}</el-tag>
            <span v-else class="overview-matrix__muted">未提交</span>
          </template>
        </el-table-column>
        <el-table-column label="批改操作" width="220" fixed="right">
          <template #default="{ row: student }">
            <div class="workbench-actions">
              <button v-if="student.submissionId != null" class="action-button action-button--primary" type="button" @click="router.push({ name: 'teacher-lab-submission-overview', params: { labId: labId() }, query: { submissionId: student.submissionId } })">批改报告</button>
              <button v-if="student.submissionId != null" class="action-button action-button--ghost" type="button" @click="openReportView(student)">实验报告</button>
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
            <el-tag :type="riskTagType(student.behavior)" size="small" effect="light">{{ riskTagLabel(student.behavior) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </WorkbenchShell>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTeacherLabSubmissionOverview } from '@/api/labs';
import WorkbenchShell from './components/workbench/WorkbenchShell.vue';
import type { LabReportStatus, LabSubmissionOverview, LabSubmissionOverviewCell, LabSubmissionOverviewStudent } from '@/types/lab';
import {
  formatActiveDuration,
  formatTotalDuration,
  hasNoBehaviorData,
  riskTagLabel,
  riskTagTooltip,
  riskTagType,
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

const goBackToGrading = () => {
  router.push({ name: 'teacher-lab-submission-overview', params: { labId: labId() } });
};

const openReportView = (student: LabSubmissionOverviewStudent) => {
  if (student.submissionId == null) return;
  const href = router.resolve({
    name: 'teacher-lab-report-view',
    params: { labId: labId(), studentId: student.studentId },
  }).href;
  window.open(href, '_blank', 'noopener,noreferrer');
};

const statusLabel = (status?: LabReportStatus | null) => {
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
  if (status === SUBMISSION_STATUS.SAVED) return '已保存';
  return status || '未知';
};

const statusTagType = (status?: LabReportStatus | null) => {
  if (status === SUBMISSION_STATUS.SUBMITTED) return 'warning';
  if (status === SUBMISSION_STATUS.GRADED) return 'success';
  if (status === SUBMISSION_STATUS.SAVED) return 'info';
  return 'info';
};

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
  color: #475569;
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
  color: #64748b;
  font-size: 13px;
  margin-bottom: 8px;
}

.overview-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.overview-header p {
  margin: 0;
  color: #64748b;
}

.overview-matrix-shell {
  background: #fff;
  border: 1px solid #e2e8f0;
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
  border: 1px solid #e2e8f0;
  padding: 12px;
  vertical-align: middle;
}

.overview-matrix__corner,
.overview-matrix__student {
  position: sticky;
  left: 0;
  background: #f8fafc;
  z-index: 1;
  min-width: 180px;
  text-align: left;
}

.overview-matrix__item-head {
  min-width: 140px;
  background: #f8fafc;
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
  color: #0f766e;
}

.overview-matrix__cell.is-answered {
  background: #ecfdf5;
}

.overview-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.overview-cell__badge {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.overview-cell__meta {
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
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
  background: #f8fafc;
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
  background: #f8fafc;
}

.overview-matrix__behavior strong {
  font-size: 12px;
  color: #0f172a;
  white-space: nowrap;
}

.overview-matrix__behavior {
  font-size: 12px;
}

.risk-tag--muted {
  opacity: 0.65;
}

.overview-matrix__muted {
  color: #94a3b8;
  font-size: 13px;
}
</style>
