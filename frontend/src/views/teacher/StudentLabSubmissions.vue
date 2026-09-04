<template>
  <div class="student-submissions-page">
    <section class="student-submissions-header">
      <button type="button" class="student-submissions-back" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
        <span>返回总览</span>
      </button>
      <div class="student-submissions-header__top">
        <div>
          <div class="student-submissions-eyebrow">教师后台 / 学生实验提交</div>
          <h1>{{ pageTitle }}</h1>
          <p>查看该学生在可见实验中的提交记录，点击进入报告详情。</p>
        </div>
        <el-button plain :loading="loading" @click="fetchData">刷新</el-button>
      </div>
    </section>

    <section v-loading="loading" class="student-submissions-list">
      <template v-if="rows.length">
        <article v-for="row in rows" :key="row.submissionId" class="submission-row">
          <div>
            <h3>{{ row.labTitle }}</h3>
            <p>{{ row.className || '未标注班级' }}</p>
          </div>
          <div class="submission-row__meta">
            <el-tag :type="statusTagType(row.submitStatus)" effect="light">{{ statusLabel(row.submitStatus) }}</el-tag>
            <span>总分 {{ row.totalScore ?? '-' }}</span>
            <span>{{ formatDateTime(row.submitStatus, row.submittedAt) }}</span>
          </div>
          <el-button type="primary" plain @click="goReportDetail(row.submissionId)">查看报告</el-button>
        </article>
      </template>
      <el-empty v-else-if="!loading" description="该学生暂无实验提交记录" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listTeacherStudentLabSubmissions } from '@/api/labs';
import type { LabReportStatus, StudentLabSubmissionItem } from '@/types/lab';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const rows = ref<StudentLabSubmissionItem[]>([]);

const studentId = () => Number(route.params.studentId);
const pageTitle = computed(() => {
  const studentName = typeof route.query.studentName === 'string' ? route.query.studentName.trim() : '';
  const studentNo = typeof route.query.studentNo === 'string' ? route.query.studentNo.trim() : '';
  if (studentName && studentNo) {
    return `${studentName}（${studentNo}）历次实验提交`;
  }
  if (studentName) {
    return `${studentName} 历次实验提交`;
  }
  return '学生历次实验提交';
});

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listTeacherStudentLabSubmissions(studentId());
  } finally {
    loading.value = false;
  }
};

const goReportDetail = (submissionId: number) => {
  router.push(`/teacher/lab-reports/${submissionId}`);
};

const statusLabel = (status?: LabReportStatus) => {
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  if (status === 'SAVED') return '已保存';
  return status || '未知';
};

const statusTagType = (status?: LabReportStatus) => {
  if (status === 'SUBMITTED') return 'warning';
  if (status === 'GRADED') return 'success';
  if (status === 'SAVED') return 'info';
  return 'info';
};

const formatDateTime = (status?: LabReportStatus, value?: string | null) => {
  if (status === 'SAVED') {
    return '已保存，尚未正式提交';
  }
  if (!value) {
    return '尚未提交';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN');
};

onMounted(() => {
  void fetchData();
});
</script>

<style scoped>
.student-submissions-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.student-submissions-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  padding: 0;
}

.student-submissions-header__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.student-submissions-eyebrow {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 8px;
}

.student-submissions-header h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.student-submissions-header p {
  margin: 0;
  color: #64748b;
}

.student-submissions-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.submission-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.submission-row h3 {
  margin: 0 0 4px;
}

.submission-row p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.submission-row__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #64748b;
  font-size: 13px;
  align-items: flex-start;
}
</style>
