<template>
  <div class="lab-report-view" v-loading="loading">
    <AppBreadcrumb />
    <AppEmptyState v-if="!loading && !detail" description="未获取到实验报告" />

    <article v-else-if="detail" class="lab-report-sheet workbench-panel">
      <div class="lab-report-sheet__body">
        <header class="lab-report-header">
          <h1>{{ detail.reportTitle }}</h1>
          <p>{{ detail.reportDate }}</p>
        </header>

        <section class="lab-report-meta" aria-label="报告基本信息">
          <div class="workbench-meta lab-report-meta__chips">
            <span class="workbench-meta__item"><strong>课程</strong>{{ detail.courseName }}</span>
            <span class="workbench-meta__item"><strong>班级</strong>{{ detail.className || '—' }}</span>
            <span class="workbench-meta__item"><strong>实验</strong>{{ detail.labTitle }}</span>
            <span class="workbench-meta__item"><strong>学号</strong>{{ detail.studentNo || '—' }}</span>
            <span class="workbench-meta__item"><strong>姓名</strong>{{ detail.studentName || '—' }}</span>
            <span class="workbench-meta__item"><strong>同组人</strong>—</span>
            <span class="workbench-meta__item"><strong>签名</strong>—</span>
            <span
              v-if="displayScore"
              class="workbench-meta__item workbench-meta__item--accent"
            >
              <strong>指导教师评定</strong>{{ displayScore }}
            </span>
            <span v-else class="workbench-meta__item"><strong>指导教师评定</strong>—</span>
          </div>
        </section>

        <section class="lab-report-section">
          <h2>一、实验目的</h2>
          <div class="lab-report-block">{{ detail.purpose || '' }}</div>
        </section>

        <section class="lab-report-section">
          <h2>二、实验内容</h2>
          <div class="lab-report-block">{{ detail.experimentContent || '' }}</div>
        </section>

        <section class="lab-report-section">
          <h2>三、实验步骤</h2>
          <article v-for="step in detail.steps" :key="step.stepId" class="lab-report-step">
            <h3><span class="lab-report-step__badge">第{{ step.stepNo }}步</span>{{ step.stepTitle }}</h3>
            <p class="lab-report-prompt">{{ step.content }}</p>
            <p class="lab-report-label">解答</p>
            <div class="lab-report-answer-pane">{{ step.answerText || '' }}</div>
            <LabAnswerImageGallery
              v-if="step.images?.length"
              :images="step.images"
            />
          </article>
        </section>

        <section class="lab-report-section">
          <h2>四、实验小结</h2>
          <div class="lab-report-block lab-report-block--summary">{{ detail.summaryText || '' }}</div>
        </section>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import { useRoute } from 'vue-router';
import { getTeacherLabReportView } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import AppBreadcrumb from '@/layout/AppBreadcrumb.vue';
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
  min-height: calc(100vh - 34px);
  background: #f0f4f8;
  padding: 24px 20px 40px;
}

.lab-report-view :deep(.app-breadcrumb) {
  max-width: 880px;
}

.lab-report-sheet {
  max-width: 880px;
  margin: 0 auto;
}

.lab-report-sheet__body {
  padding: 28px 28px 32px;
}

.lab-report-header {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.lab-report-header h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: 'DM Sans', 'Inter', sans-serif;
}

.lab-report-header p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-md);
}

.lab-report-meta {
  margin-bottom: 28px;
}

.lab-report-meta__chips {
  gap: 10px;
}

.lab-report-section {
  margin-bottom: 28px;
}

.lab-report-section:last-child {
  margin-bottom: 0;
}

.lab-report-section h2 {
  margin: 0 0 12px;
  color: var(--color-text-primary);
  font-family: 'DM Sans', 'Inter', sans-serif;
}

.lab-report-block {
  white-space: pre-wrap;
  word-break: break-word;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--color-bg-app);
  border: 1px solid var(--color-border);
  color: #334155;
  line-height: var(--line-height-body);
  font-size: var(--font-size-md);
  min-height: 24px;
}

.lab-report-block--summary {
  min-height: 48px;
}

.lab-report-step {
  padding: 16px 18px;
  margin-bottom: 14px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
}

.lab-report-step:last-child {
  margin-bottom: 0;
}

.lab-report-step h3 {
  margin: 0 0 12px;
  color: var(--color-text-primary);}

.lab-report-step__badge {
  display: inline-block;
  margin-right: 6px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #ecfeff;
  color: var(--color-brand-teacher);
  font-size: var(--font-size-sm);
  font-weight: 700;
  vertical-align: middle;
}

.lab-report-prompt {
  margin: 0 0 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-app);
  border: 1px solid var(--color-border);
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: var(--line-height-body);
  font-size: var(--font-size-md);
}

.lab-report-label {
  margin: 0 0 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-wide);
}

.lab-report-answer-pane {
  min-height: 48px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-app);
  border: 1px solid var(--color-border-soft);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: var(--line-height-body);
  font-size: var(--font-size-md);
}

@media (max-width: 900px) {
  .lab-report-view {
    padding: 16px 12px 32px;
  }

  .lab-report-sheet__body {
    padding: 20px 16px 24px;
  }
}

@media print {
  .lab-report-view {
    background: var(--color-surface);
    padding: 0;
  }

  .lab-report-sheet {
    box-shadow: none !important;
    border: none !important;
  }

  .lab-report-step {
    break-inside: avoid;
  }
}
</style>
