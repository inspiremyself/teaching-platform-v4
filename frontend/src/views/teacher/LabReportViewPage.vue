<template>
  <div class="lab-report-view" v-loading="loading">
    <el-empty v-if="!loading && !detail" description="未获取到实验报告" />

    <article v-else-if="detail" class="lab-report-sheet">
      <header class="lab-report-header">
        <h1>{{ detail.reportTitle }}</h1>
        <p>{{ detail.reportDate }}</p>
      </header>

      <section class="lab-report-meta">
        <div class="lab-report-meta__column">
          <p><span>课程名称：</span>{{ detail.courseName }}</p>
          <p><span>班级：</span>{{ detail.className || '' }}</p>
          <p><span>同组人：</span></p>
        </div>

        <div class="lab-report-meta__column">
          <p><span>实验名称：</span>{{ detail.labTitle }}</p>
          <p><span>学号：</span>{{ detail.studentNo || '' }}</p>
          <p><span>指导教师评定：</span>{{ displayScore }}</p>
        </div>

        <div class="lab-report-meta__column">
          <p><span>姓名：</span>{{ detail.studentName || '' }}</p>
          <p><span>签名：</span></p>
        </div>
      </section>

      <section class="lab-report-section">
        <h2>一、实验目的</h2>
        <p>{{ detail.purpose || '' }}</p>
      </section>

      <section class="lab-report-section">
        <h2>二、实验内容</h2>
        <p>{{ detail.experimentContent || '' }}</p>
      </section>

      <section class="lab-report-section">
        <h2>三、实验步骤</h2>
        <article v-for="step in detail.steps" :key="step.stepId" class="lab-report-step">
          <h3>第{{ step.stepNo }}步,{{ step.stepTitle }}</h3>
          <p class="lab-report-answer">{{ step.content }}</p>
          <p>解答：</p>
          <div class="lab-report-answer lab-report-answer--box">{{ step.answerText || '' }}</div>
          <LabAnswerImageGallery
            v-if="step.images?.length"
            :images="step.images"
          />
        </article>
      </section>

      <section class="lab-report-section">
        <h2>四、实验小结</h2>
        <div class="lab-report-answer">{{ detail.summaryText || '' }}</div>
      </section>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { getTeacherLabReportView } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import type { TeacherLabReportView } from '@/types/lab';

const route = useRoute();
const loading = ref(false);
const detail = ref<TeacherLabReportView | null>(null);

const labId = computed(() => Number(route.params.labId));
const studentId = computed(() => Number(route.params.studentId));
const displayScore = computed(() => detail.value?.totalScore == null ? '' : String(detail.value.totalScore));

const fetchDetail = async () => {
  if (!Number.isFinite(labId.value) || !Number.isFinite(studentId.value)) {
    detail.value = null;
    return;
  }

  loading.value = true;
  try {
    detail.value = await getTeacherLabReportView(labId.value, studentId.value);
  } finally {
    loading.value = false;
  }
};

onMounted(fetchDetail);
</script>

<style scoped>
.lab-report-view {
  min-height: 100vh;
  background: #fff;
  padding: 16px 20px 32px;
}

.lab-report-sheet {
  max-width: 880px;
  margin: 0 auto;
  color: #111827;
  line-height: 1.75;
  font-size: 14px;
}

.lab-report-header {
  text-align: center;
  margin-bottom: 28px;
}

.lab-report-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.lab-report-header p {
  margin: 6px 0 0;
}

.lab-report-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin-bottom: 28px;
}

.lab-report-meta__column p {
  margin: 0 0 4px;
}

.lab-report-meta__column span {
  font-weight: 500;
}

.lab-report-section {
  margin-bottom: 24px;
}

.lab-report-section h2 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
}

.lab-report-step {
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #e5e7eb;
}

.lab-report-step h3 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
}

.lab-report-answer {
  white-space: pre-wrap;
  word-break: break-word;
}

.lab-report-answer--box {
  min-height: 48px;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
}

@media (max-width: 900px) {
  .lab-report-meta {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
