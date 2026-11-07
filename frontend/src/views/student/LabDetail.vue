<template>
  <div class="lab-detail-page" v-loading="loading">
    <div class="page-header">
      <div class="page-header__main">
        <div class="detail-eyebrow">学生端 / 实验题目索引</div>
        <h2>{{ detail?.title || '实验详情' }}</h2>
        <p>{{ detail?.description || '先查看实验要求，再进入对应题目作答。' }}</p>
        <div v-if="detail" class="page-header__meta">
          <span class="header-meta-chip">{{ submissionLabel(detail.submissionStatus) }}</span>
          <span class="header-meta-chip">{{ experimentTypeLabel(detail.experimentType) }}</span>
          <span class="header-meta-chip">进度：{{ answeredCount }}/{{ labItems.length }}</span>
          <span class="header-meta-chip">{{ labItems.length }} 题</span>
          <span class="header-meta-chip">开始：{{ detail.startAt ? formatDateTime(detail.startAt) : '不限' }}</span>
          <span class="header-meta-chip">截止：{{ detail.dueAt ? formatDateTime(detail.dueAt) : '不限' }}</span>
          <span v-if="isNotStarted" class="header-meta-chip header-meta-chip--warning">实验尚未开始</span>
          <span v-else-if="isOverdue" class="header-meta-chip header-meta-chip--warning">实验已截止</span>
          <span class="header-meta-chip" :class="detail.summaryRequired ? 'header-meta-chip--warning' : ''">
            {{ detail.summaryRequired ? '实验小结必填' : '实验小结选填' }}
          </span>
        </div>
      </div>
      <div class="page-header__actions">
        <el-button @click="router.push('/student/labs')">返回实验列表</el-button>
        <el-button v-if="detail" type="primary" @click="goSteps()">{{ primaryActionLabel }}</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !detail" description="未获取到实验详情" />

    <template v-else-if="detail">
      <section v-if="showLabFeedback" class="feedback-card">
        <div class="feedback-card__header">
          <span class="material-symbols-outlined">chat</span>
          <strong>教师总评</strong>
        </div>
        <p>{{ detail.teacherComment || '当前尚未开放教师总评。' }}</p>
      </section>

      <section class="catalog-card lab-info-card">
        <header class="catalog-card__header">
          <div>
            <h3>实验信息</h3>
            <p>先阅读实验要求与实验内容，再进入题目作答。</p>
          </div>
          <el-button v-if="hasGuidanceFile" type="primary" plain @click="downloadGuidance">下载实验指导</el-button>
        </header>

        <div class="lab-info-grid">
          <div class="lab-info-block">
            <div class="lab-info-block__label">实验类型</div>
            <div class="lab-info-block__value">{{ experimentTypeLabel(detail.experimentType) }}</div>
          </div>
          <div class="lab-info-block">
            <div class="lab-info-block__label">实验指导</div>
            <div class="lab-info-block__value">{{ detail.materialFileName || '未提供实验指导文件' }}</div>
          </div>
        </div>

        <div class="lab-info-section">
          <div class="lab-info-section__title">实验要求</div>
          <p>{{ detail.description || '暂无实验要求。' }}</p>
        </div>

        <div class="lab-info-section">
          <div class="lab-info-section__title">实验内容</div>
          <p>{{ detail.experimentContent || '暂无实验内容。' }}</p>
        </div>
      </section>

      <section class="catalog-card">
        <header class="catalog-card__header">
          <div>
            <h3>题目目录</h3>
            <p>{{ progressText }}</p>
            <p class="catalog-card__summary" v-if="detail.summaryText?.trim()">实验小结：{{ detail.summaryText.trim() }}</p>
            <p class="catalog-card__summary" v-else>{{ summaryPreviewText }}</p>
          </div>
        </header>

        <div class="catalog-list">
          <button v-for="item in labItems" :key="item.id" type="button" class="catalog-item" @click="goSteps(item.id)">
            <span class="catalog-item__index">{{ item.stepNo }}</span>
            <span class="catalog-item__main">
              <span class="catalog-item__title">{{ item.title }}</span>
              <span class="catalog-item__meta">
                <span>{{ questionTypeLabel(item.questionType) }}</span>
                <span>{{ item.stepScore }} 分</span>
                <span>{{ item.answerText?.trim() ? '已保存答案' : '待作答' }}</span>
                <span v-if="showLabScore && item.score !== null && item.score !== undefined">得分 {{ item.score }}/{{ item.stepScore }}</span>
              </span>
            </span>
            <span class="catalog-item__link">进入</span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { downloadMaterial } from '@/api/materials';
import { getStudentLabDetail } from '@/api/labs';
import { LAB_EXPERIMENT_TYPE_LABELS, type LabQuestionType, type LabReportStatus, type StudentLabDetail } from '@/types/lab';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<StudentLabDetail | null>(null);
// 响应式时钟：每秒刷新一次，跨过开始/截止时间后无需重新加载即可更新状态。
const nowMs = ref(Date.now());

const isNotStarted = computed(() => {
  const startAt = detail.value?.startAt;
  if (!startAt) return false;
  const startTime = new Date(startAt).getTime();
  return !Number.isNaN(startTime) && nowMs.value < startTime;
});
const isOverdue = computed(() => {
  const dueAt = detail.value?.dueAt;
  if (!dueAt) return false;
  const dueTime = new Date(dueAt).getTime();
  return !Number.isNaN(dueTime) && nowMs.value >= dueTime;
});

const labId = computed(() => Number(route.params.id));
const labItems = computed(() => {
  if (!detail.value) return [];
  return detail.value.items?.length ? detail.value.items : detail.value.steps || [];
});
const answeredCount = computed(() => labItems.value.filter((item) => item.answerText?.trim()).length);
const showLabScore = computed(() => typeof detail.value?.totalScore === 'number');
const showLabFeedback = computed(() => !!detail.value?.teacherComment || showLabScore.value);
const progressPercent = computed(() => {
  const total = labItems.value.length;
  if (!total) return 0;
  if (detail.value?.submissionStatus === 'GRADED') return 100;
  if (detail.value?.submissionStatus === 'SUBMITTED') return 90;
  return Math.round((answeredCount.value / total) * 100);
});
  const progressText = computed(() => {
    if (detail.value?.submissionStatus === 'GRADED') return '实验已批改，可回看题项反馈与实验小结。';
    if (detail.value?.submissionStatus === 'SUBMITTED') return '实验已提交，当前等待教师评分。';
    if (answeredCount.value > 0) return `已保存 ${answeredCount.value} 道题，可继续作答。`;
    return '尚未保存题项答案。';
  });
const primaryActionLabel = computed(() => {
  if (detail.value?.submissionStatus === 'GRADED') return '查看作答与反馈';
  if (detail.value?.submissionStatus === 'SUBMITTED') return '查看已提交内容';
  if (detail.value?.submissionStatus === 'SAVED') return '继续作答';
  return '开始作答';
});
const summaryPreviewText = computed(() => {
  const summary = detail.value?.summaryText?.trim();
  if (summary) return summary;
  return detail.value?.summaryRequired ? '当前尚未填写实验小结，提交实验时必须补充。' : '当前尚未填写实验小结，提交时可按需补充。';
});
const hasGuidanceFile = computed(() => Boolean(detail.value?.materialId && detail.value?.materialDownloadUrl && detail.value?.materialFileName));

const fetchData = async () => {
  loading.value = true;
  try {
    detail.value = await getStudentLabDetail(labId.value);
  } finally {
    loading.value = false;
  }
};

const goSteps = (itemId?: number) => {
  router.push({
    path: `/student/labs/${labId.value}/steps`,
    query: itemId !== undefined ? { item: String(itemId) } : {},
  });
};

const experimentTypeLabel = (value?: number | null) => LAB_EXPERIMENT_TYPE_LABELS[value || 1] || `类型 ${value}`;

const downloadGuidance = async () => {
  if (!detail.value?.materialDownloadUrl || !detail.value?.materialFileName) {
    return;
  }

  try {
    await downloadMaterial(detail.value.materialDownloadUrl, detail.value.materialFileName);
  } catch {
    ElMessage.error('实验指导下载失败，请稍后重试');
  }
};

const submissionLabel = (status?: LabReportStatus | null) => {
  if (status === 'SAVED') return '已保存草稿';
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  return '待开始';
};

const questionTypeLabel = (type?: LabQuestionType) => {
  if (type === 'SHORT_ANSWER') return '简答题';
  if (type === 'TEXT') return '文本题';
  if (type === 'FILL_BLANK') return '填空题';
  if (type === 'CODE') return '代码题';
  if (type === 'SINGLE_CHOICE') return '单选题';
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  return type || '作答题';
};

const formatDateTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-');

let clockTimer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  void fetchData();
  clockTimer = setInterval(() => {
    nowMs.value = Date.now();
  }, 1000);
});
onUnmounted(() => {
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = undefined;
  }
});
</script>

<style scoped>
.lab-detail-page {
  --detail-primary: #1e40af;
  --detail-primary-soft: #eff6ff;
  --detail-border: #dbe5ef;
  --detail-text: #0f172a;
  --detail-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.page-header__main {
  min-width: 0;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.page-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.header-meta-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.header-meta-chip--warning {
  border-color: #fdba74;
  background: #fff7ed;
  color: #9a3412;
}

.detail-eyebrow,
.catalog-item__meta {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-eyebrow {
  margin-bottom: 8px;
  color: var(--detail-primary);
}

.page-header h2,
.catalog-card h3 {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
}

.page-header p,
.feedback-card p,
.catalog-card__header p,
.summary-tile small,
 .catalog-item__meta {
  color: var(--detail-muted);
  line-height: 1.6;
}

.page-header p {
  margin: 6px 0 0;
  font-size: 13px;
}

.feedback-card,
.catalog-card {
  border: 1px solid var(--detail-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.catalog-card__header {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.catalog-card__summary {
  margin: 6px 0 0;
  font-size: 12px;
}

.lab-info-card {
  padding: 16px 18px;
}

.lab-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.lab-info-block {
  padding: 12px 14px;
  border: 1px solid var(--detail-border);
  border-radius: 12px;
  background: #f8fafc;
}

.lab-info-block__label,
.lab-info-section__title {
  margin-bottom: 6px;
  color: var(--detail-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.lab-info-block__value {
  color: var(--detail-text);
  font-weight: 700;
}

.lab-info-section {
  margin-top: 14px;
}

.lab-info-section p {
  margin: 0;
  color: var(--detail-muted);
  line-height: 1.7;
  white-space: pre-wrap;
}

.feedback-card {
  padding: 16px 18px;
}

.feedback-card__header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.catalog-card {
  padding: 16px 18px;
}

.catalog-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.catalog-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.catalog-item:hover {
  background: #f8fafc;
}

.catalog-item__index {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(30, 64, 175, 0.08);
  color: var(--detail-primary);
  font-size: 15px;
  font-weight: 700;
}

.catalog-item__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.catalog-item__title {
  color: var(--detail-text);
  font-size: 15px;
  font-weight: 700;
}

.catalog-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 11px;
  letter-spacing: 0.04em;
}

.catalog-item__link {
  align-self: center;
  color: var(--detail-primary);
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .page-header,
  .page-header,
  .page-header__actions,
  .catalog-card__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .catalog-item {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .lab-info-grid {
    grid-template-columns: 1fr;
  }

  .catalog-item__link {
    grid-column: 2;
    justify-self: start;
    margin-top: 2px;
  }
}
</style>
