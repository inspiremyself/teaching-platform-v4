<template>
  <GradingWorkspace>
    <template #headerActions>
      <button type="button" class="action-button action-button--ghost" @click="router.push({ name: 'teacher-lab-matrix', params: { labId } })">班级总览</button>
      <button v-if="workspace.selectedId.value !== null" type="button" class="action-button action-button--ghost" @click="openReportView">实验报告</button>
      <button type="button" class="action-button action-button--ghost" @click="router.push('/teacher/labs/' + labId + '/blank-regrade/batch')">批量重判</button>
      <button v-if="workspace.selectedId.value !== null" type="button" class="action-button action-button--ghost" @click="goBatchGrade">批量快捷批改</button>
      <button v-if="workspace.selectedId.value !== null" type="button" class="action-button action-button--warning" :disabled="!canReturn" @click="handleReturn">打回</button>
      <button type="button" class="action-button action-button--ghost" :disabled="loading" @click="refreshWithLoading">刷新</button>
    </template>

    <template #queue>
      <SubmissionQueue
        :items="workspace.filteredQueue.value"
        :selected-id="workspace.selectedId.value"
        :keyword="workspace.keyword.value"
        :status-filter="workspace.statusFilter.value"
        @update:keyword="workspace.keyword.value = $event"
        @update:status-filter="workspace.statusFilter.value = $event"
        @select="id => workspace.selectSubmission(id)"
      />
    </template>

    <template #detail>
      <el-empty
        v-if="workspace.queue.value.length === 0 && !loading"
        description="本实验暂无提交，可到班级总览查看未提交学生"
      />
      <main v-else v-loading="loading" class="submission-panel">
        <template v-if="workspace.detail.value">
          <section class="lab-context-card">
            <strong>{{ workspace.detail.value.studentName || '未知学生' }}</strong>
            <span>{{ workspace.detail.value.studentNo || workspace.detail.value.studentUsername || '' }}</span>
            <span>{{ statusLabel(workspace.detail.value.submitStatus) }}</span>
            <span class="lab-context-card__title">{{ workspace.detail.value.labTitle || '实验报告' }}</span>
          </section>

          <section
            v-for="item in workspace.detail.value.answers ?? workspace.detail.value.items ?? []"
            :key="String(item.answerId ?? item.id ?? item.stepId)"
            class="question-card"
          >
            <header class="question-card__header">
              <h3>第{{ item.stepNo ?? '-' }}题 {{ item.stepTitle || item.title || '' }}</h3>
              <span class="question-card__score">满分 {{ itemMaxScore(item) }}</span>
            </header>
            <p class="question-card__prompt">{{ questionTypeLabel(item.questionType) }}</p>
            <div class="answer-pane">{{ item.answerText || item.studentAnswer || '暂无作答内容' }}</div>
            <LabAnswerImageGallery
              v-if="extractReportImages(item).length"
              :images="extractReportImages(item)"
            />
            <div class="question-card__grade">
              <el-input-number
                :model-value="workspace.draft.value?.scores[String(item.answerId ?? item.id ?? item.stepId)] ?? undefined"
                :min="0"
                :max="itemMaxScore(item)"
                :disabled="workspace.readOnly.value"
                placeholder="待评分"
                @update:model-value="workspace.setScore(String(item.answerId ?? item.id ?? item.stepId), $event ?? null)"
              />
              <el-input
                :model-value="workspace.draft.value?.comments[String(item.answerId ?? item.id ?? item.stepId)] ?? ''"
                type="textarea"
                :rows="2"
                :disabled="workspace.readOnly.value"
                placeholder="本题评语"
                @update:model-value="workspace.setComment(String(item.answerId ?? item.id ?? item.stepId), String($event))"
              />
            </div>
            <div class="question-card__actions">
              <button
                type="button"
                class="action-button action-button--ghost"
                :disabled="workspace.readOnly.value || item.suggestedScore == null"
                @click="acceptSuggested(item)"
              >采纳参考分 {{ item.suggestedScore ?? '—' }}</button>
              <button
                type="button"
                class="action-button action-button--primary"
                :disabled="workspace.readOnly.value"
                @click="confirmStep(item)"
              >确认单题</button>
            </div>
          </section>

          <BehaviorSummaryCard
            v-if="typeof workspace.selectedId.value === 'number'"
            :submission-id="workspace.selectedId.value"
            :step-list="stepList"
          />
          <section class="summary-text">
            <h4>实验小结</h4>
            <div>{{ workspace.detail.value.summaryText || '学生未填写实验小结。' }}</div>
          </section>
          <el-input
            v-model="reportComment"
            type="textarea"
            :rows="3"
            :disabled="workspace.readOnly.value"
            placeholder="实验报告评语"
          />
        </template>
      </main>
    </template>

    <template #summary>
      <section class="grading-summary">
        <h2>评分汇总</h2>
        <div class="grading-summary__row"><span>提交总数</span><strong>{{ rawRows.length }}</strong></div>
        <div class="grading-summary__row"><span>待批改</span><strong>{{ pendingCount }}</strong></div>
        <div class="grading-summary__row"><span>已批改</span><strong>{{ gradedCount }}</strong></div>
        <div class="grading-summary__row"><span>客观题自动得分</span><strong>{{ workspace.summary.value.autoScore }}</strong></div>
        <div class="grading-summary__row"><span>主观题人工得分</span><strong>{{ workspace.summary.value.manualScore }}</strong></div>
        <div class="grading-summary__row grading-summary__row--total"><span>总分</span><strong>{{ workspace.summary.value.totalScore }}</strong></div>
        <p class="grading-summary__progress">
          {{ workspace.summary.value.hasManual ? `评分进度 ${workspace.summary.value.completed}/${workspace.summary.value.total}` : workspace.summary.value.noManualText }}
        </p>
        <button
          type="button"
          class="action-button action-button--primary"
          :disabled="workspace.readOnly.value"
          @click="submitWithConfirm"
        >提交评分</button>
      </section>
    </template>
  </GradingWorkspace>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { SUBMISSION_STATUS } from '@/types/enums';
import {
  confirmTeacherLabStepScore,
  getTeacherLabReportDetail,
  gradeTeacherLabReport,
  listTeacherLabReportQueue,
  returnTeacherLabReport,
} from '@/api/labs';
import GradingWorkspace from './components/workbench/GradingWorkspace.vue';
import SubmissionQueue from './components/workbench/SubmissionQueue.vue';
import BehaviorSummaryCard from './components/BehaviorSummaryCard.vue';
import LabAnswerImageGallery from '@/components/lab/LabAnswerImageGallery.vue';
import { extractReportImages } from '@/utils/labReportImages';
import { createSharedDraftPort, useGradingWorkspace } from './components/workbench/useWorkbench';
import { questionTypeLabel, scoringStrategyOf } from './components/scoreDisplay';
import type { GradingDraftState } from './components/scoreDisplay';
import type { LabReportAnswerItem, LabReportDetail, LabReportItem, LabReportStatus } from '@/types/lab';

const route = useRoute();
const router = useRouter();
const labId = computed(() => Number(route.params.labId));
const loading = ref(false);
const reportComment = ref('');
const rawRows = ref<LabReportItem[]>([]);

const loadQueue = async (): Promise<LabReportItem[]> => {
  const all = await listTeacherLabReportQueue();
  const filtered = all.filter(row => row.labId === labId.value);
  const sorted = [...filtered].sort((a, b) => {
    const classResult = (a.className || String(a.classId ?? '')).localeCompare(
      b.className || String(b.classId ?? ''),
      'zh-CN',
    );
    if (classResult !== 0) return classResult;
    const leftStudent = a.studentNo || a.studentUsername || a.studentName || String(a.id);
    const rightStudent = b.studentNo || b.studentUsername || b.studentName || String(b.id);
    return leftStudent.localeCompare(rightStudent, 'zh-CN', { numeric: true });
  });
  rawRows.value = sorted;
  return sorted;
};

const initialSelectedId = (): number | null => {
  const queryId = route.query.submissionId;
  if (typeof queryId === 'string') {
    const matched = rawRows.value.find(row => String(row.id) === queryId);
    if (matched) return matched.id;
  }
  return rawRows.value[0]?.id ?? null;
};

const labStrategy = scoringStrategyOf('LAB');

const workspace = useGradingWorkspace<LabReportDetail, GradingDraftState>({
  domain: 'LAB',
  loadQueue: async () => loadQueue(),
  loadDetail: id => getTeacherLabReportDetail(id),
  scoreLinesOf: detail => (detail.answers ?? detail.items ?? []).map(item => labStrategy.adapt(item)),
  draftPort: createSharedDraftPort<LabReportDetail>(d =>
    (d.answers ?? d.items ?? []).map(item => ({
      key: String(item.answerId ?? item.id ?? item.stepId),
      score: item.score ?? null,
      teacherComment: item.teacherComment ?? '',
    })),
  ),
  statusOf: detail => detail.submitStatus,
  isReadOnly: status => labStrategy.isReadOnly(status),
  handlers: {
    saveGrade: async (detail, draft) => {
      const answerItems = detail.answers ?? detail.items ?? [];
      const items = answerItems
        .filter(item => draft.marked.includes(String(item.answerId ?? item.id ?? item.stepId)))
        .map(item => {
          const key = String(item.answerId ?? item.id ?? item.stepId);
          return {
            answerId: Number(item.answerId ?? 0),
            score: Number(draft.scores[key] ?? 0),
            teacherComment: (draft.comments[key] ?? '').trim(),
          };
        })
        .filter(item => Number.isFinite(item.answerId) && item.answerId > 0);
      if (!items.length) {
        ElMessage.warning('请先输入至少一道题的分数');
        return;
      }
      await gradeTeacherLabReport(detail.id, { items, teacherComment: reportComment.value.trim() });
      ElMessage.success('评分已保存，报告已结案');
    },
    saveAnswerGrade: async (detail, key) => {
      const item = (detail.answers ?? detail.items ?? []).find(
        i => String(i.answerId ?? i.id ?? i.stepId) === key,
      );
      if (!item?.answerId) {
        ElMessage.warning('当前题项没有可确认的答案记录');
        return;
      }
      await confirmTeacherLabStepScore(detail.id, {
        answerId: item.answerId,
        score: Number(workspace.draft.value?.scores[key] ?? 0),
        teacherComment: (workspace.draft.value?.comments[key] ?? '').trim(),
        acceptSuggested: false,
      });
      ElMessage.success('题项评分已确认');
    },
    returnSubmission: async detail => {
      await returnTeacherLabReport(detail.id);
      ElMessage.success('已打回，学生可重新编辑提交');
    },
  },
});

const stepList = computed(() =>
  (workspace.detail.value?.answers ?? workspace.detail.value?.items ?? [])
    .filter(i => i.stepId != null && i.stepNo != null)
    .map(i => ({ stepId: i.stepId!, stepNo: i.stepNo!, title: i.stepTitle || i.title || '' })),
);

watch(() => workspace.detail.value, d => {
  reportComment.value = d?.teacherComment ?? d?.reportComment ?? '';
});

const canReturn = computed(() =>
  workspace.detail.value?.submitStatus === SUBMISSION_STATUS.SUBMITTED
  || workspace.detail.value?.submitStatus === SUBMISSION_STATUS.GRADED,
);

const pendingCount = computed(() => rawRows.value.filter(row => row.submitStatus === SUBMISSION_STATUS.SUBMITTED).length);
const gradedCount = computed(() => rawRows.value.filter(row => row.submitStatus === SUBMISSION_STATUS.GRADED).length);

const statusLabel = (status?: LabReportStatus | null) => {
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
  if (status === SUBMISSION_STATUS.SAVED) return '已保存';
  return status || '未知';
};

const itemMaxScore = (item: LabReportAnswerItem): number => labStrategy.adapt(item).maxScore ?? 0;

const openReportView = () => {
  const studentId = workspace.detail.value?.studentId;
  if (studentId == null) return;
  const href = router.resolve({
    name: 'teacher-lab-report-view',
    params: { labId: labId.value, studentId },
  }).href;
  window.open(href, '_blank', 'noopener,noreferrer');
};

const goBatchGrade = () => {
  void workspace.guardBeforeSwitch(() => {
    router.push(`/teacher/lab-reports/${workspace.selectedId.value}/batch-grade`);
  });
};

const confirmStep = async (item: LabReportAnswerItem): Promise<void> => {
  const key = String(item.answerId ?? item.id ?? item.stepId);
  await workspace.saveAnswer(key);
};

const acceptSuggested = async (item: LabReportAnswerItem): Promise<void> => {
  const detail = workspace.detail.value;
  const answerId = item.answerId;
  if (!detail || answerId == null) return;
  await workspace.guardBeforeSwitch(async () => {
    await confirmTeacherLabStepScore(detail.id, {
      answerId,
      acceptSuggested: true,
    });
    ElMessage.success('已采纳参考分');
    await workspace.refresh();
  });
};

const handleReturn = async () => {
  if (!canReturn.value || !workspace.detail.value) return;
  const message = workspace.detail.value.submitStatus === SUBMISSION_STATUS.GRADED
    ? '该报告已批改，打回后成绩将清除，学生可重新编辑提交，是否继续？'
    : '打回后学生可修改作答并重新提交，是否继续？';
  try {
    await ElMessageBox.confirm(message, '打回确认', { type: 'warning' });
  } catch {
    return;
  }
  await workspace.returnSubmission();
};

const submitWithConfirm = async () => {
  try {
    await ElMessageBox.confirm('保存后评分将结案且不可修改，确认保存？', '保存评分', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  await workspace.save();
};

const refreshWithLoading = async () => {
  loading.value = true;
  try {
    await workspace.refresh();
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  loading.value = true;
  try {
    await loadQueue();
    workspace.selectedId.value = initialSelectedId();
    await workspace.refresh();
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.submission-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.lab-context-card {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid #dbe5ef;
  border-radius: 12px;
  background: #f8fafc;
}

.lab-context-card strong {
  font-size: var(--font-size-lg);
  color: #0f172a;
}

.lab-context-card span {
  font-size: var(--font-size-sm);
  color: #64748b;
}

.lab-context-card__title {
  margin-left: auto;
  color: #0f766e !important;
  font-weight: 700;
}

.question-card {
  padding: 14px 16px;
  border: 1px solid #dbe5ef;
  border-radius: 16px;
  background: #fff;
}

.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
}

.question-card__header h3 {
  margin: 0;
  color: #0f172a;
}

.question-card__score {
  font-size: var(--font-size-sm);
  color: #64748b;
}

.question-card__prompt {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  color: #334155;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
}

.answer-pane {
  min-height: 40px;
  padding: 10px 12px;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: #f8fafc;
  color: #0f172a;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.question-card__grade {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.question-card__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.summary-text {
  padding: 12px 14px;
  border: 1px solid #dbe5ef;
  border-radius: 12px;
  background: #f8fafc;
}

.summary-text h4 {
  margin: 0 0 6px;
  color: #334155;
}

.summary-text div {
  font-size: var(--font-size-base);
  color: #0f172a;
  line-height: var(--line-height-body);
  white-space: pre-wrap;
}

.grading-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.grading-summary h2 {
  margin: 0;
  color: #0f172a;
}

.grading-summary__row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: var(--font-size-base);
  color: #475569;
}

.grading-summary__row--total {
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
  font-weight: 700;
  color: #0f172a;
}

.grading-summary__progress {
  margin: 0;
  font-size: var(--font-size-sm);
  color: #64748b;
}

@media (max-width: 1280px) {
  .question-card__grade {
    grid-template-columns: 1fr;
  }
}
</style>
