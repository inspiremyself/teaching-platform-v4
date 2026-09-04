<template>
  <div class="overview-page">
    <section class="overview-header">
      <button type="button" class="overview-back" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
        <span>返回批改队列</span>
      </button>
      <div class="overview-header__top">
        <div>
          <div class="overview-eyebrow">教师后台 / 实验提交总览</div>
          <h1>{{ overview?.labTitle || '实验提交总览' }}</h1>
          <p>班级全员 × 子题矩阵。点击学生名查看历次提交，点击题列查看全班答案。</p>
        </div>
        <el-button plain :loading="loading" @click="fetchOverview">刷新</el-button>
      </div>
    </section>

    <section v-loading="loading" class="overview-matrix-shell">
      <template v-if="overview">
        <div class="overview-matrix-wrap">
          <table class="overview-matrix">
            <thead>
              <tr>
                <th class="overview-matrix__corner">学生</th>
                <th
                  v-for="item in overview.items"
                  :key="item.stepId"
                  class="overview-matrix__item-head"
                >
                  <button type="button" class="overview-item-link" @click="goItemWall(item.stepId)">
                    <span>第{{ item.stepNo }}题</span>
                    <strong>{{ item.title }}</strong>
                  </button>
                </th>
                <th class="overview-matrix__status-head">提交状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="student in overview.students" :key="student.studentId">
                <th class="overview-matrix__student">
                  <button
                    type="button"
                    class="overview-student-link"
                    @click="goStudentSubmissions(student.studentId, student.studentName, student.studentNo)"
                  >
                    <strong>{{ student.studentName }}</strong>
                    <span>{{ student.studentNo }}</span>
                  </button>
                </th>
                <td
                  v-for="cell in student.cells"
                  :key="`${student.studentId}-${cell.stepId}`"
                  class="overview-matrix__cell"
                  :class="{ 'is-answered': cell.answered }"
                >
                  <div class="overview-cell">
                    <span class="overview-cell__badge">{{ cell.answered ? '已答' : '未答' }}</span>
                    <span v-if="cell.hasText" class="overview-cell__meta">文字</span>
                    <span v-if="cell.imageCount > 0" class="overview-cell__meta">图 {{ cell.imageCount }}</span>
                  </div>
                </td>
                <td class="overview-matrix__status">
                  <el-tag v-if="student.submitStatus" :type="statusTagType(student.submitStatus)" effect="light">
                    {{ statusLabel(student.submitStatus) }}
                  </el-tag>
                  <span v-else class="overview-matrix__muted">未提交</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无总览数据" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTeacherLabSubmissionOverview } from '@/api/labs';
import type { LabReportStatus, LabSubmissionOverview } from '@/types/lab';

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

const statusLabel = (status?: LabReportStatus | null) => {
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  if (status === 'SAVED') return '已保存';
  return status || '未知';
};

const statusTagType = (status?: LabReportStatus | null) => {
  if (status === 'SUBMITTED') return 'warning';
  if (status === 'GRADED') return 'success';
  if (status === 'SAVED') return 'info';
  return 'info';
};

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

.overview-matrix__muted {
  color: #94a3b8;
  font-size: 13px;
}
</style>
