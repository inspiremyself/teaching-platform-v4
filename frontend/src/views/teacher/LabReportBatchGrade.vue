<template>
  <div class="batch-grade-page" v-loading="loading">
    <section class="batch-grade-header">
      <button type="button" class="batch-grade-back" @click="goDetail()">
        <span class="material-symbols-outlined">arrow_back</span>
        <span>返回报告详情</span>
      </button>
      <div class="batch-grade-header__top">
        <div>
          <div class="batch-grade-eyebrow">教师后台 / 实验批量批改</div>
          <h1>题项快捷批改表</h1>
          <p>独立批量界面集中展示当前报告全部题项，支持快速改分、填写评语、确认单题或采纳建议分。</p>
        </div>
        <div class="batch-grade-actions">
          <el-button plain :disabled="!previousClassReport" @click="goReport(previousClassReport?.id)">上一个班级</el-button>
          <el-button plain :disabled="!previousStudentReport" @click="goReport(previousStudentReport?.id)">上一个学生</el-button>
          <el-button plain :disabled="!nextStudentReport" @click="goReport(nextStudentReport?.id)">下一个学生</el-button>
          <el-button plain :disabled="!nextClassReport" @click="goReport(nextClassReport?.id)">下一个班级</el-button>
          <el-button plain @click="goDetail()">回到详细批改</el-button>
          <el-button type="primary" :loading="submitting" @click="submitGrade">提交全部评分</el-button>
        </div>
      </div>
      <div v-if="detail" class="batch-grade-meta">
        <span class="batch-grade-chip"><strong>{{ detail.studentName || '-' }}</strong>{{ detail.studentNo || detail.studentUsername || '未标注学号' }}</span>
        <span class="batch-grade-chip"><strong>{{ answerItems.length }}</strong> 个题项</span>
        <span class="batch-grade-chip batch-grade-chip--accent">页内合计 {{ computedTotalScore }}</span>
      </div>
    </section>

    <el-empty v-if="!loading && !detail" description="未获取到报告详情" />

    <section v-else class="batch-grade-panel">
      <div class="batch-grade-panel__header">
        <div>
          <h2>{{ detail?.labTitle || '实验报告' }}</h2>
          <p>{{ detail?.className || '未标注班级' }} · {{ detail?.submitStatus || '未知状态' }}</p>
        </div>
      </div>

      <el-table :data="answerItems" :row-key="itemKey" class="batch-grade-table">
        <el-table-column label="题项" min-width="260">
          <template #default="scope">
            <button type="button" class="batch-question-link" @click="goDetail(itemKey(scope.row))">
              <span>题项 {{ scope.row.stepNo || '-' }} / {{ questionTypeLabel(scope.row.questionType) }}</span>
              <strong>{{ scope.row.stepTitle || scope.row.title || '未命名题项' }}</strong>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="学生答案" min-width="320">
          <template #default="scope">
            <div class="batch-answer-cell">
              <div class="batch-answer-text">{{ scope.row.answerText || scope.row.studentAnswer || '暂无作答内容' }}</div>
              <LabAnswerImageGallery
                v-if="reportImages(scope.row).length"
                :images="reportImages(scope.row)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="参考" width="120">
          <template #default="scope">{{ displaySuggestedScore(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="得分" width="150">
          <template #default="scope">
            <el-input-number
              v-model="gradeFormMap[gradeKey(scope.row)].score"
              :min="0"
              :max="maxScoreOf(scope.row)"
              size="small"
              controls-position="right"
              style="width: 120px"
            />
          </template>
        </el-table-column>
        <el-table-column label="评语" min-width="240">
          <template #default="scope">
            <el-input
              v-model="gradeFormMap[gradeKey(scope.row)].teacherComment"
              size="small"
              maxlength="500"
              placeholder="本题评语"
              clearable
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <div class="batch-row-actions">
              <el-button plain size="small" @click="goDetail(itemKey(scope.row))">详情</el-button>
              <el-button plain size="small" :disabled="!gradeFormMap[gradeKey(scope.row)]?.answerId" @click="confirmSingleStep(scope.row, false)">确认</el-button>
              <el-button plain size="small" :disabled="!gradeFormMap[gradeKey(scope.row)]?.answerId || scope.row.suggestedScore == null" @click="confirmSingleStep(scope.row, true)">采纳</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { confirmTeacherLabStepScore, getTeacherLabReportDetail, gradeTeacherLabReport, listTeacherLabReports } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import type { LabQuestionType, LabReportAnswerItem, LabReportDetail, LabReportItem } from '@/types/lab';
import { extractReportImages } from '@/utils/labReportImages';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitting = ref(false);
const detail = ref<LabReportDetail | null>(null);
const reports = ref<LabReportItem[]>([]);
const reportComment = ref('');
const gradeFormMap = reactive<Record<string, { answerId: number; score: number; teacherComment: string }>>({});

const reportId = computed(() => Number(route.params.id));
const answerItems = computed(() => detail.value?.answers || detail.value?.items || []);
const reportImages = (item: LabReportAnswerItem) => extractReportImages(item);
const computedTotalScore = computed(() => answerItems.value.reduce((sum, item) => sum + (gradeFormMap[gradeKey(item)]?.score ?? 0), 0));
const reportQueue = computed(() => sortReports(reports.value));
const currentReportIndex = computed(() => reportQueue.value.findIndex((row) => row.id === reportId.value));
const previousStudentReport = computed(() => currentReportIndex.value > 0 ? reportQueue.value[currentReportIndex.value - 1] : null);
const nextStudentReport = computed(() => currentReportIndex.value >= 0 ? reportQueue.value[currentReportIndex.value + 1] || null : null);
const classGroups = computed(() => {
  const groups = new Map<string, LabReportItem[]>();
  reportQueue.value.forEach((row) => {
    const key = String(row.classId ?? row.className ?? 'unknown');
    groups.set(key, [...(groups.get(key) || []), row]);
  });
  return [...groups.entries()];
});
const currentClassIndex = computed(() => classGroups.value.findIndex(([, rows]) => rows.some((row) => row.id === reportId.value)));
const previousClassReport = computed(() => currentClassIndex.value > 0 ? classGroups.value[currentClassIndex.value - 1][1][0] : null);
const nextClassReport = computed(() => currentClassIndex.value >= 0 ? classGroups.value[currentClassIndex.value + 1]?.[1][0] || null : null);

const sortReports = (items: LabReportItem[]) => [...items].sort((left, right) => {
  const leftClass = left.className || String(left.classId ?? '');
  const rightClass = right.className || String(right.classId ?? '');
  const classResult = leftClass.localeCompare(rightClass, 'zh-CN');
  if (classResult !== 0) return classResult;
  const leftStudent = left.studentNo || left.studentUsername || left.studentName || String(left.id);
  const rightStudent = right.studentNo || right.studentUsername || right.studentName || String(right.id);
  return leftStudent.localeCompare(rightStudent, 'zh-CN', { numeric: true });
});

const resolveItemId = (item: LabReportAnswerItem) => item.answerId ?? item.id ?? item.stepId;
const isValidItemId = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const itemKey = (item: LabReportAnswerItem) => String(resolveItemId(item) ?? 0);
const gradeKey = (item: LabReportAnswerItem) => itemKey(item);
const maxScoreOf = (item: LabReportAnswerItem) => item.maxScore ?? item.stepScore ?? 100;
const resolvedInitialScore = (item: LabReportAnswerItem) => item.score ?? item.autoScore ?? item.suggestedScore ?? 0;

const questionTypeLabel = (type?: LabQuestionType) => {
  if (type === 'TEXT') return '文本题';
  if (type === 'SHORT_ANSWER') return '简答题';
  if (type === 'SINGLE_CHOICE') return '单选题';
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  if (type === 'FILL_BLANK') return '填空题';
  if (type === 'CODE') return '代码题';
  return type || '未标注题型';
};

const displaySuggestedScore = (item: LabReportAnswerItem) => {
  if (item.autoScore !== null && item.autoScore !== undefined) return `自动 ${item.autoScore}`;
  if (item.suggestedScore !== null && item.suggestedScore !== undefined) return `建议 ${item.suggestedScore}`;
  return '暂无';
};

const hydrateGradeForm = (items: LabReportAnswerItem[]) => {
  Object.keys(gradeFormMap).forEach((key) => delete gradeFormMap[key]);
  items.forEach((item) => {
    const key = gradeKey(item);
    if (!gradeFormMap[key]) {
      const resolvedId = resolveItemId(item);
      gradeFormMap[key] = {
        answerId: isValidItemId(resolvedId) ? resolvedId : 0,
        score: Number(resolvedInitialScore(item)),
        teacherComment: item.teacherComment || '',
      };
    }
  });
};

const fetchData = async () => {
  loading.value = true;
  try {
    const [data, reportRows] = await Promise.all([
      getTeacherLabReportDetail(reportId.value),
      listTeacherLabReports(),
    ]);
    detail.value = data;
    reports.value = reportRows;
    reportComment.value = data.teacherComment || data.reportComment || '';
    hydrateGradeForm(data.answers || data.items || []);
  } finally {
    loading.value = false;
  }
};

const goDetail = (answerId?: string) => {
  router.push({
    path: `/teacher/lab-reports/${reportId.value}`,
    query: answerId ? { answerId } : undefined,
  });
};

const goReport = (targetReportId?: number) => {
  if (!targetReportId) return;
  router.push(`/teacher/lab-reports/${targetReportId}/batch-grade`);
};

const confirmSingleStep = async (item: LabReportAnswerItem, acceptSuggested: boolean) => {
  const entry = gradeFormMap[gradeKey(item)];
  if (!entry?.answerId) {
    ElMessage.warning('当前题项没有可确认的答案记录');
    return;
  }
  await confirmTeacherLabStepScore(reportId.value, {
    answerId: entry.answerId,
    score: acceptSuggested ? undefined : Number(entry.score || 0),
    teacherComment: entry.teacherComment.trim(),
    acceptSuggested,
  });
  ElMessage.success(acceptSuggested ? '已采纳题项建议分' : '题项评分已确认');
  await fetchData();
};

const submitGrade = async () => {
  const items = answerItems.value
    .map(item => gradeFormMap[gradeKey(item)])
    .filter(entry => entry && isValidItemId(entry.answerId))
    .map(entry => ({
      answerId: entry.answerId,
      score: Number(entry.score || 0),
      teacherComment: entry.teacherComment.trim(),
    }));

  if (!items.length) {
    ElMessage.warning('当前报告没有可提交的题项评分记录');
    return;
  }

  submitting.value = true;
  try {
    await gradeTeacherLabReport(reportId.value, {
      items,
      teacherComment: reportComment.value.trim(),
    });
    ElMessage.success('实验报告评分成功');
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

watch(() => route.params.id, () => fetchData());
fetchData();
</script>

<style scoped>
.batch-grade-page {
  --batch-primary: #0f766e;
  --batch-primary-soft: #ecfeff;
  --batch-border: #dbe5ef;
  --batch-text: #0f172a;
  --batch-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.batch-grade-header,
.batch-grade-panel {
  border: 1px solid var(--batch-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.batch-grade-header {
  padding: 16px 18px 14px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.82), rgba(255, 255, 255, 0.98));
}

.batch-grade-back,
.batch-question-link {
  border: none;
  background: transparent;
  cursor: pointer;
}

.batch-grade-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  color: var(--batch-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.batch-grade-header__top,
.batch-grade-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.batch-grade-header__top {
  margin-top: 10px;
}

.batch-grade-eyebrow {
  color: var(--batch-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.batch-grade-header h1,
.batch-grade-panel h2 {
  margin: 0;
  color: var(--batch-text);
  font-family: 'DM Sans', sans-serif;
}

.batch-grade-header h1 {
  font-size: 24px;
}

.batch-grade-header p,
.batch-grade-panel p {
  color: var(--batch-muted);
  line-height: 1.5;
}

.batch-grade-actions,
.batch-grade-meta,
.batch-row-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.batch-grade-meta {
  margin-top: 12px;
  justify-content: flex-start;
}

.batch-grade-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.batch-grade-chip strong {
  margin-right: 4px;
  color: var(--batch-text);
}

.batch-grade-chip--accent {
  background: var(--batch-primary-soft);
  color: var(--batch-primary);
}

.batch-grade-panel {
  padding: 16px;
}

.batch-grade-table {
  width: 100%;
}

.batch-question-link {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 0;
  color: var(--batch-text);
  text-align: left;
}

.batch-question-link span {
  color: var(--batch-primary);
  font-size: 12px;
  font-weight: 700;
}

.batch-question-link strong {
  color: var(--batch-text);
  font-size: 13px;
  line-height: 1.4;
}

.batch-answer-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.batch-answer-text {
  color: var(--batch-text);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.batch-answer-cell :deep(.lab-answer-image-gallery) {
  margin-top: 8px;
  gap: 8px;
}

.batch-answer-cell :deep(.lab-answer-image-gallery__item) {
  width: 72px;
}

.batch-answer-cell :deep(.lab-answer-image-gallery__image) {
  width: 72px;
  height: 72px;
}

.batch-answer-cell :deep(.lab-answer-image-gallery__caption) {
  display: none;
}

@media (max-width: 900px) {
  .batch-grade-header__top,
  .batch-grade-panel__header {
    flex-direction: column;
  }

  .batch-grade-actions {
    justify-content: flex-start;
  }
}
</style>
