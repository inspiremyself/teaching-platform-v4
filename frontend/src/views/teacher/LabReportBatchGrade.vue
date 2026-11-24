<template>
  <GradingWorkspace>
    <template #headerActions>
      <button class="action-button action-button--ghost" type="button" :disabled="!previousClassReport" @click="goReport(previousClassReport?.id)">上一个班级</button>
      <button class="action-button action-button--ghost" type="button" :disabled="!previousStudentReport" @click="goReport(previousStudentReport?.id)">上一个学生</button>
      <button class="action-button action-button--ghost" type="button" :disabled="!nextStudentReport" @click="goReport(nextStudentReport?.id)">下一个学生</button>
      <button class="action-button action-button--ghost" type="button" :disabled="!nextClassReport" @click="goReport(nextClassReport?.id)">下一个班级</button>
      <button class="action-button action-button--ghost" type="button" @click="goDetail()">回到详细批改</button>
      <button class="action-button action-button--primary" type="button" :loading="submitting" :disabled="isReadOnly" @click="submitGrade">提交全部评分</button>
    </template>

    <template #queue>
      <SubmissionQueue
        :items="queueItems"
        :selected-id="reportId"
        :keyword="queueKeyword"
        :status-filter="queueStatusFilter"
        @update:keyword="queueKeyword = $event"
        @update:status-filter="queueStatusFilter = $event"
        @select="id => goReport(Number(id))"
      />
    </template>

    <template #detail>
    <div class="batch-grade-page" v-loading="loading">
    <el-empty v-if="!loading && !detail" description="未获取到报告详情" />

    <template v-else>
      <BehaviorSummaryCard v-if="detail" :submission-id="reportId" :step-list="stepList" />

      <section class="batch-grade-panel">
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
        <el-table-column label="参考" min-width="200">
          <template #default="scope">
            <span v-if="scope.row.suggestedScore != null">系统参考分 {{ scope.row.suggestedScore }}（仅供参考，不直接计入）</span>
            <span v-else-if="scope.row.autoScore != null">自动分 {{ scope.row.autoScore }}</span>
            <span v-else>暂无</span>
            <span
              v-if="scope.row.scoreSource === 'AUTO' || scope.row.scoreSource === 'AUTO_ACCEPTED'"
              class="batch-reference-note"
            >客观题由系统判分，不支持修改</span>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="150">
          <template #default="scope">
            <el-input-number
              :model-value="gradeFormMap[gradeKey(scope.row)]?.score ?? undefined"
              :min="0"
              :max="itemMaxScore(scope.row)"
              size="small"
              controls-position="right"
              class="app-score-input"
              :disabled="isReadOnly"
              placeholder="待评分"
              @update:model-value="setItemScore(scope.row, $event)"
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
              :disabled="isReadOnly"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="right" fixed="right">
          <template #default="scope">
            <div class="batch-row-actions">
              <el-button link type="primary" @click="goDetail(itemKey(scope.row))">详情</el-button>
              <el-button link type="primary" :disabled="!gradeFormMap[gradeKey(scope.row)]?.answerId || isReadOnly" @click="confirmSingleStep(scope.row, false)">确认</el-button>
              <el-button link type="primary" :disabled="!gradeFormMap[gradeKey(scope.row)]?.answerId || scope.row.suggestedScore == null || isReadOnly" @click="confirmSingleStep(scope.row, true)">采纳参考分</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
    </template>
    </div>
    </template>

    <template #summary>
      <section class="grading-summary">
        <h2>评分汇总</h2>
        <div class="grading-summary__row"><span>学生</span><strong>{{ detail?.studentName || '未选择' }}</strong></div>
        <div class="grading-summary__row"><span>客观题自动得分</span><strong>{{ scoringSummary.autoScore }}</strong></div>
        <div class="grading-summary__row"><span>主观题人工得分</span><strong>{{ scoringSummary.manualScore }}</strong></div>
        <div class="grading-summary__row grading-summary__row--total"><span>总分</span><strong>{{ computedTotalScore }}</strong></div>
        <p class="grading-summary__progress">
          {{ scoringSummary.hasManual ? `评分进度 ${scoringSummary.completed}/${scoringSummary.total}` : scoringSummary.noManualText }}
        </p>
        <button class="action-button action-button--primary" type="button" :disabled="isReadOnly" @click="submitGrade">提交全部评分</button>
      </section>
    </template>
  </GradingWorkspace>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { confirmTeacherLabStepScore, getTeacherLabReportDetail, gradeTeacherLabReport, listTeacherLabReportQueue } from '@/api/labs';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import GradingWorkspace from './components/workbench/GradingWorkspace.vue';
import SubmissionQueue from './components/workbench/SubmissionQueue.vue';
import { submissionAdapterOf } from './components/workbench/submissionAdapters';
import BehaviorSummaryCard from './components/BehaviorSummaryCard.vue';
import type { StepBrief } from './components/BehaviorSummaryCard.vue';
import type { LabQuestionType, LabReportAnswerItem, LabReportDetail, LabReportItem } from '@/types/lab';
import { extractReportImages } from '@/utils/labReportImages';
import {
  buildScoringSummary,
  createGradingDraft,
  hasDraftChanges,
  questionTypeLabel,
  scoringStrategyOf,
  type GradingDraftState,
} from './components/scoreDisplay';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitting = ref(false);
const detail = ref<LabReportDetail | null>(null);
const reports = ref<LabReportItem[]>([]);
const reportComment = ref('');
const gradeFormMap = reactive<Record<string, { answerId: number; score: number | null; teacherComment: string }>>({});
const markedKeys = ref<string[]>([]);
const baselineKeys = ref<string[]>([]);
const initialComments = ref<Record<string, string>>({});

const reportId = computed(() => Number(route.params.id));
const answerItems = computed(() => detail.value?.answers || detail.value?.items || []);
const labStrategy = scoringStrategyOf('LAB');
const isReadOnly = computed(() => labStrategy.isReadOnly(detail.value?.submitStatus));
const scoringItems = computed(() => answerItems.value.map(item => labStrategy.adapt(item)));
const itemMaxScore = (item: LabReportAnswerItem) => labStrategy.adapt(item).maxScore ?? 0;

const currentDraft = (): GradingDraftState => {
  const state = createGradingDraft(
    scoringItems.value.map(item => ({
      key: item.key,
      score: item.score ?? null,
      teacherComment: item.teacherComment,
    })),
  );
  for (const item of scoringItems.value) {
    state.scores[item.key] = gradeFormMap[item.key]?.score ?? null;
    state.comments[item.key] = gradeFormMap[item.key]?.teacherComment ?? '';
  }
  state.marked = [...markedKeys.value];
  state.baselineMarked = [...baselineKeys.value];
  state.initialComments = { ...initialComments.value };
  return state;
};

const scoringSummary = computed(() => buildScoringSummary('LAB', scoringItems.value, currentDraft()));

/** stepId → stepNo/title 映射（BehaviorSummaryCard Step Dwell 面板用，D2） */
const stepList = computed<StepBrief[]>(() =>
  answerItems.value
    .filter((i) => i.stepId != null && i.stepNo != null)
    .map((i) => ({
      stepId: i.stepId!,
      stepNo: i.stepNo!,
      title: i.stepTitle || i.title || '',
    })),
);
const reportImages = (item: LabReportAnswerItem) => extractReportImages(item);
const computedTotalScore = computed(() => scoringSummary.value.totalScore);
const labQueueAdapter = submissionAdapterOf('LAB');
const queueKeyword = ref('');
const queueStatusFilter = ref('');
const queueItems = computed(() => {
  const key = queueKeyword.value.trim().toLowerCase();
  return reports.value
    .map(row => labQueueAdapter.adapt(row, {
      draftFlag: String(row.id) === String(reportId.value) && hasDraftChanges(currentDraft()),
    }))
    .filter(item =>
      (!key || item.studentName.toLowerCase().includes(key)) &&
      (!queueStatusFilter.value || item.status === queueStatusFilter.value),
    );
});
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

const hydrateGradeForm = (items: LabReportAnswerItem[]) => {
  Object.keys(gradeFormMap).forEach((key) => delete gradeFormMap[key]);
  markedKeys.value = [];
  baselineKeys.value = [];
  initialComments.value = {};
  items.forEach((item) => {
    const key = gradeKey(item);
    const adapted = labStrategy.adapt(item);
    const resolvedId = resolveItemId(item);
    gradeFormMap[key] = {
      answerId: isValidItemId(resolvedId) ? resolvedId : 0,
      score: adapted.score ?? null,
      teacherComment: adapted.teacherComment,
    };
    initialComments.value[key] = adapted.teacherComment;
    if (isReadOnly.value && adapted.score !== null) {
      if (!markedKeys.value.includes(key)) markedKeys.value.push(key);
      if (!baselineKeys.value.includes(key)) baselineKeys.value.push(key);
    }
  });
};

const setItemScore = (item: LabReportAnswerItem, score: number | null) => {
  const key = gradeKey(item);
  if (!gradeFormMap[key]) return;
  gradeFormMap[key].score = score;
  const index = markedKeys.value.indexOf(key);
  if (score === null) {
    if (index >= 0) markedKeys.value.splice(index, 1);
  } else if (index < 0) {
    markedKeys.value.push(key);
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const [data, reportRows] = await Promise.all([
      getTeacherLabReportDetail(reportId.value),
      listTeacherLabReportQueue(),
    ]);
    detail.value = data;
    reports.value = reportRows;
    reportComment.value = data.teacherComment || data.reportComment || '';
    hydrateGradeForm(data.answers || data.items || []);
  } finally {
    loading.value = false;
  }
};

const guardDraft = async (action: () => void | Promise<void>) => {
  if (!hasDraftChanges(currentDraft())) {
    await action();
    return;
  }
  try {
    await ElMessageBox.confirm('当前评分尚未保存，确定切换吗？', '离开当前答卷', {
      confirmButtonText: '确定切换',
      cancelButtonText: '继续评分',
      type: 'warning',
    });
    await action();
  } catch {
    // 取消则停留在当前批量页
  }
};

const goDetail = (answerId?: string) => {
  void guardDraft(() => {
    router.push({
      path: `/teacher/lab-reports/${reportId.value}`,
      query: answerId ? { answerId } : undefined,
    });
  });
};

const goReport = (targetReportId?: number) => {
  if (!targetReportId) return;
  void guardDraft(() => {
    router.push(`/teacher/lab-reports/${targetReportId}/batch-grade`);
  });
};

const confirmSingleStep = async (item: LabReportAnswerItem, acceptSuggested: boolean) => {
  if (isReadOnly.value) {
    ElMessage.warning('当前报告不可评分');
    return;
  }
  const entry = gradeFormMap[gradeKey(item)];
  if (!entry?.answerId) {
    ElMessage.warning('当前题项没有可确认的答案记录');
    return;
  }
  if (!acceptSuggested && !markedKeys.value.includes(gradeKey(item))) {
    ElMessage.warning('请先输入本题分数');
    return;
  }
  await confirmTeacherLabStepScore(reportId.value, {
    answerId: entry.answerId,
    score: acceptSuggested ? undefined : Number(entry.score || 0),
    teacherComment: entry.teacherComment.trim(),
    acceptSuggested,
  });
  ElMessage.success(acceptSuggested ? '已采纳参考分' : '题项评分已确认');
  await fetchData();
};

const submitGrade = async () => {
  if (isReadOnly.value) {
    ElMessage.warning('当前报告不可评分');
    return;
  }
  try {
    await ElMessageBox.confirm('保存后评分将结案且不可修改，确认保存？', '保存评分', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  const items = answerItems.value
    .filter(item => markedKeys.value.includes(gradeKey(item)))
    .map(item => gradeFormMap[gradeKey(item)])
    .filter(entry => entry && isValidItemId(entry.answerId))
    .map(entry => ({
      answerId: entry.answerId,
      score: Number(entry.score || 0),
      teacherComment: entry.teacherComment.trim(),
    }));

  if (!items.length) {
    ElMessage.warning('请先输入至少一道题的分数');
    return;
  }

  submitting.value = true;
  try {
    await gradeTeacherLabReport(reportId.value, {
      items,
      teacherComment: reportComment.value.trim(),
    });
    ElMessage.success('评分已保存，报告已结案');
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
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.batch-grade-header,
.batch-grade-panel {
  border: 1px solid var(--color-border-soft);
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
  color: var(--color-brand-teacher);
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
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

.batch-grade-header h1,
.batch-grade-panel h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: 'DM Sans', sans-serif;
}
.batch-grade-header p,
.batch-grade-panel p {
  color: var(--color-text-muted);
  line-height: var(--line-height-caption);
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

/* 学生未提交时的只读预览提示 */
.preview-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #b45309;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.preview-banner p {
  margin: 0;
}

.batch-grade-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.batch-grade-chip strong {
  margin-right: 4px;
  color: var(--color-text-primary);
}

.batch-grade-chip--accent {
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
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
  color: var(--color-text-primary);
  text-align: left;
}

.batch-question-link span {
  color: var(--color-brand-teacher);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.batch-question-link strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  line-height: var(--line-height-heading);
}

.batch-answer-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.batch-answer-text {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  line-height: var(--line-height-caption);
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
.batch-reference-note {
  display: block;
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.batch-grade-table .el-button:disabled,
.batch-grade-table .el-input-number.is-disabled,
.batch-grade-table .el-input.is-disabled,
.batch-grade-actions .el-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
