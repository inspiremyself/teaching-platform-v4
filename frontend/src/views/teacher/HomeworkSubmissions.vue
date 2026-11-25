<template>
  <GradingWorkspace>
    <template #headerActions>
      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 总提交数</span>
        <span class="workbench-meta__item"><strong>{{ pendingCount }}</strong> 待批改</span>
        <span class="workbench-meta__item"><strong>{{ gradedCount }}</strong> 已批改</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ averageScoreText }}</strong> 平均得分</span>
      </div>
      <button class="action-button action-button--warning" type="button" @click="releaseScores">发布成绩</button>
      <button class="action-button action-button--ghost" type="button" @click="resetFilters">清空筛选</button>
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
      <main class="submission-panel" v-loading="loading">
        <AppEmptyState v-if="!selectedSubmission" description="请选择左侧提交记录查看详情" />
        <div v-else class="grade-dialog">
          <section class="grade-dialog__main">
            <div class="grade-dialog__identity-grid">
              <div class="identity-box identity-box--wide">
                <span class="identity-box__label">学生</span>
                <strong>{{ selectedSubmission.studentName }}</strong>
                <small>{{ selectedSubmission.studentUsername }}</small>
              </div>
              <div class="identity-box">
                <span class="identity-box__label">提交状态</span>
                <strong>{{ submissionStatusLabel(selectedSubmission.submitStatus) }}</strong>
              </div>
              <div class="identity-box">
                <span class="identity-box__label">附件路径</span>
                <strong>{{ selectedSubmission.answerFilePath || '-' }}</strong>
              </div>
            </div>

            <div class="grade-dialog__content-card">
              <div class="grade-dialog__section-title">作业正文</div>
              <div class="answer-text">{{ selectedSubmission.answerText || '未提交文本内容' }}</div>
            </div>

            <div v-if="selectedSubmission.questions?.length" class="grade-dialog__content-card">
              <div class="grade-dialog__section-title">逐题评分</div>
              <div class="question-grade-list">
                <article v-for="question in selectedSubmission.questions" :key="question.id" class="question-grade-item">
                  <div class="question-grade-item__head">
                    <div>
                      <strong>第 {{ question.sortOrder }} 题</strong>
                      <p>{{ question.stem }}</p>
                    </div>
                    <el-tag effect="light">{{ questionTypeLabel(question.type) }} / {{ question.score }} 分</el-tag>
                  </div>
                  <div class="question-grade-item__body">
                    <div>
                      <span class="identity-box__label">学生答案</span>
                      <div class="answer-text answer-text--compact">{{ question.answer?.answerText || question.answer?.answerJson || '未作答' }}</div>
                    </div>
                    <div class="question-grade-item__meta">
                      <span v-if="question.answer?.suggestedScore != null">系统参考分 {{ question.answer.suggestedScore }}（仅供参考，不直接计入）</span>
                      <span v-else-if="question.answer?.autoScore != null">自动分 {{ question.answer.autoScore }}</span>
                      <span>来源：{{ scoreSourceLabel(question.answer?.scoreSource) }}</span>
                    </div>
                    <p class="quick-grade-card__muted">{{ formatJudgeDetail(question.answer?.judgeDetail) }}</p>
                    <p
                      v-if="question.answer?.scoreSource === 'AUTO' || question.answer?.scoreSource === 'AUTO_ACCEPTED'"
                      class="quick-grade-card__muted"
                    >客观题由系统判分，不支持修改</p>
                    <div class="question-grade-item__actions">
                      <el-input-number
                        :model-value="draftState.scores[String(question.answer?.id || 0)] ?? undefined"
                        :min="0"
                        :max="question.score"
                        :precision="0"
                        :step="1"
                        :disabled="!question.answer?.id || !canGradeAnswer"
                        placeholder="待评分"
                        @update:model-value="setAnswerScore(question.answer?.id || 0, $event)"
                      />
                      <el-input
                        :model-value="draftState.comments[String(question.answer?.id || 0)] ?? ''"
                        type="textarea"
                        :rows="2"
                        :disabled="!question.answer?.id || !canGradeAnswer"
                        placeholder="输入本题评语"
                        @update:model-value="setAnswerComment(question.answer?.id || 0, $event)"
                      />
                      <el-button
                        type="primary"
                        plain
                        :disabled="!question.answer?.id || !canGradeAnswer"
                        @click="submitAnswerGrade(question.answer?.id || 0, false)"
                      >
                        保存本题
                      </el-button>
                      <el-button
                        plain
                        :disabled="!question.answer?.id || question.answer?.suggestedScore == null || !canGradeAnswer"
                        @click="submitAnswerGrade(question.answer?.id || 0, true)"
                      >
                        采纳参考分
                      </el-button>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </section>

          <aside class="grade-dialog__aside">
            <div class="grade-form-card">
              <div class="grade-dialog__section-title">评分汇总</div>
              <p class="quick-grade-card__muted">客观题 + 主观题</p>
              <div class="submission-row__meta-grid">
                <div><span class="submission-row__label">客观题自动得分</span><strong>{{ scoringSummary.autoScore }}</strong></div>
                <div><span class="submission-row__label">主观题人工得分</span><strong>{{ scoringSummary.manualScore }}</strong></div>
                <div><span class="submission-row__label">总分</span><strong>{{ scoringSummary.totalScore }}</strong></div>
              </div>
              <p v-if="scoringSummary.hasManual" class="quick-grade-card__muted">
                主观题评分进度 {{ scoringSummary.completed }}/{{ scoringSummary.total }} 题
              </p>
              <p v-else class="quick-grade-card__muted">{{ scoringSummary.noManualText }}</p>
              <p v-if="hasDraftChanges(draftState)" class="quick-grade-card__muted">有未保存的评分</p>
            </div>

            <div class="grade-form-card">
              <div class="grade-dialog__section-title">评分与反馈</div>
              <el-alert
                v-if="!canGradeAnswer"
                type="info"
                :closable="false"
                :title="selectedSubmission.submitStatus === SUBMISSION_STATUS.SAVED
                  ? '学生尚未提交，当前不可评分'
                  : '该提交已完成批改，当前以结果查看为主；如需继续评分，请切换到待批改记录。'"
                class="grade-readonly-alert"
              />
              <el-form label-position="top" class="grade-form">
                <el-form-item label="评语">
                  <el-input v-model="gradeForm.teacherComment" type="textarea" :rows="8" maxlength="500" show-word-limit :disabled="!canGradeAnswer" />
                </el-form-item>
              </el-form>

              <div class="quick-comment-block">
                <p>快捷评语</p>
                <div class="quick-comment-block__chips">
                  <button type="button" class="quick-comment-chip" :disabled="!canGradeAnswer" @click="appendQuickComment('论述详尽，结构较完整。')">论述详尽</button>
                  <button type="button" class="quick-comment-chip" :disabled="!canGradeAnswer" @click="appendQuickComment('逻辑清晰，可继续加强细节论证。')">逻辑清晰</button>
                  <button type="button" class="quick-comment-chip" :disabled="!canGradeAnswer" @click="appendQuickComment('建议补充格式规范与关键步骤说明。')">需加强格式</button>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </main>
    </template>

    <template #summary>
      <section class="grading-summary">
        <h2>评分汇总</h2>
        <div class="grading-summary__row"><span>学生</span><strong>{{ selectedSubmission?.studentName || '未选择' }}</strong></div>
        <div class="grading-summary__row"><span>客观题自动得分</span><strong>{{ scoringSummary.autoScore }}</strong></div>
        <div class="grading-summary__row"><span>主观题人工得分</span><strong>{{ scoringSummary.manualScore }}</strong></div>
        <div class="grading-summary__row grading-summary__row--total"><span>总分</span><strong>{{ scoringSummary.totalScore }}</strong></div>
        <p class="grading-summary__progress">
          {{ scoringSummary.hasManual ? `评分进度 ${scoringSummary.completed}/${scoringSummary.total}` : scoringSummary.noManualText }}
        </p>
        <p v-if="hasDraftChanges(draftState)" class="grading-summary__progress">有未保存的评分</p>
        <p class="quick-grade-card__muted">{{ selectedSubmission?.teacherComment || '尚未填写教师评语。' }}</p>
        <button
          v-if="canReturnSubmission"
          class="action-button action-button--warning"
          type="button"
          :loading="returning"
          @click="confirmReturnSubmission"
        >打回</button>
        <button class="action-button action-button--primary" type="button" :disabled="!canGradeAnswer" @click="submitGrade">保存评分</button>
      </section>
    </template>
  </GradingWorkspace>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  buildScoringSummary,
  createGradingDraft,
  formatJudgeDetail,
  hasDraftChanges,
  questionTypeLabel,
  scoreSourceLabel,
  scoringStrategyOf,
  setDraftComment,
  setDraftScore,
  type GradingDraftState,
} from './components/scoreDisplay';
import {
  getHomeworkSubmissionDetail,
  gradeHomeworkAnswer,
  gradeHomeworkSubmission,
  listHomeworkSubmissions,
  returnHomeworkSubmission,
} from '@/api/homeworks';
import type { GradeHomeworkRequest, HomeworkQuestionAnswer, HomeworkSubmissionItem, HomeworkSubmissionStatus } from '@/types/homework';
import { releaseHomeworkScores } from '@/api/homeworks';
import { formatScore } from '@/utils/format';
import { resolveStatusPresentation } from '@/components/ui/statusPresentation';
import { useGradingWorkspace, type DraftPort } from './components/workbench/useWorkbench';
import GradingWorkspace from './components/workbench/GradingWorkspace.vue';
import SubmissionQueue from './components/workbench/SubmissionQueue.vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';

const route = useRoute();
const loading = ref(false);
const submitting = ref(false);
const returning = ref(false);
const dialogVisible = ref(false);
const rows = ref<HomeworkSubmissionItem[]>([]);
const selectedSubmission = ref<HomeworkSubmissionItem | null>(null);
const draftState = reactive<GradingDraftState>(createGradingDraft([]));
const activeSubmissionId = ref<number | null>(null);
const searchQuery = ref('');
const statusFilter = ref<HomeworkSubmissionStatus | ''>('');

const gradeForm = reactive<GradeHomeworkRequest>({
  totalScore: 0,
  teacherComment: '',
});

const homeworkId = computed(() => route.params.id as string);
const homeworkTitle = computed(() => rows.value[0]?.homeworkTitle || '');

const homeworkStrategy = scoringStrategyOf('HOMEWORK');
const isReadOnly = computed(() => homeworkStrategy.isReadOnly(selectedSubmission.value?.submitStatus));
const canGradeAnswer = computed(() => selectedSubmission.value?.submitStatus === SUBMISSION_STATUS.SUBMITTED);

const scoringItems = computed(() =>
  (selectedSubmission.value?.questions ?? []).map(question => homeworkStrategy.adapt(question)),
);
const scoringSummary = computed(() =>
  buildScoringSummary('HOMEWORK', scoringItems.value, draftState),
);

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();

  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.studentName.toLowerCase().includes(keyword) ||
      row.studentUsername.toLowerCase().includes(keyword);

    const matchesStatus = !statusFilter.value || row.submitStatus === statusFilter.value;

    return matchesKeyword && matchesStatus;
  });
});

const activeSubmission = computed(() => filteredRows.value.find(item => item.id === activeSubmissionId.value) || filteredRows.value[0] || null);
/** 计划满分 = Σ 题目分值；附件型作业（无题目）不设上限，返回 undefined 由 el-input-number 回落默认上限 */
const gradeTotalMax = computed(() => {
  const questions = selectedSubmission.value?.questions ?? [];
  if (!questions.length) return undefined;
  return questions.reduce((sum, question) => sum + (Number(question.score) || 0), 0);
});

const gradeScoreLabel = computed(() =>
  gradeTotalMax.value === undefined ? '评分' : `评分（0-${gradeTotalMax.value}）`,
);

const pendingCount = computed(() => filteredRows.value.filter(row => row.submitStatus === SUBMISSION_STATUS.SUBMITTED).length);
const gradedCount = computed(() => filteredRows.value.filter(row => row.submitStatus === SUBMISSION_STATUS.GRADED).length);

const averageScoreText = computed(() => {
  const scored = filteredRows.value.filter(row => typeof row.totalScore === 'number');
  if (!scored.length) return '-';
  const avg = scored.reduce((sum, row) => sum + (row.totalScore ?? 0), 0) / scored.length;
  return `${formatScore(avg)} 分`;
});

const fetchData = async () => {
  loading.value = true;
  try {
    await workspace.refresh();
  } finally {
    loading.value = false;
  }
};

const guardDraft = async (action: () => void | Promise<void>) => {
  if (!hasDraftChanges(draftState)) {
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
    // 取消则停留在当前答卷
  }
};

const handleDialogBeforeClose = (done: () => void) => {
  void guardDraft(() => done());
};

const closeGradeDialog = () => {
  void guardDraft(() => {
    dialogVisible.value = false;
  });
};

const setActiveSubmission = (row: HomeworkSubmissionItem) => {
  void guardDraft(() => {
    activeSubmissionId.value = row.id;
  });
};

const hydrateDraft = () => {
  Object.assign(
    draftState,
    createGradingDraft(
      scoringItems.value.map(item => ({
        key: item.key,
        score: item.score ?? null,
        teacherComment: item.teacherComment,
      })),
      { markAll: isReadOnly.value },
    ),
  );
};

const openGradeDialog = async (row: HomeworkSubmissionItem | null) => {
  if (!row) return;
  await guardDraft(async () => {
    selectedSubmission.value = await getHomeworkSubmissionDetail(row.id);
    gradeForm.teacherComment = selectedSubmission.value.teacherComment || '';
    hydrateDraft();
    dialogVisible.value = true;
  });
};

const setAnswerScore = (answerId: number, score: number | null) => {
  setDraftScore(draftState, String(answerId), score);
};

const setAnswerComment = (answerId: number, comment: string) => {
  setDraftComment(draftState, String(answerId), comment);
};

const submitAnswerGrade = async (answerId: number, acceptSuggested: boolean) => {
  if (!canGradeAnswer.value) {
    ElMessage.warning('学生尚未提交作业，不能批改');
    return;
  }
  const key = String(answerId);
  if (!selectedSubmission.value || !(key in draftState.scores)) return;
  if (!acceptSuggested && !draftState.marked.includes(key)) {
    ElMessage.warning('请先输入本题分数');
    return;
  }
  await gradeHomeworkAnswer(answerId, {
    score: acceptSuggested ? undefined : draftState.scores[key] ?? 0,
    teacherComment: draftState.comments[key]?.trim() || '',
    acceptSuggested,
  });
  ElMessage.success(acceptSuggested ? '已采纳参考分' : '单题评分已保存');
  selectedSubmission.value = await getHomeworkSubmissionDetail(selectedSubmission.value.id);
  hydrateDraft();
};

const appendQuickComment = (text: string) => {
  gradeForm.teacherComment = gradeForm.teacherComment.trim()
    ? `${gradeForm.teacherComment.trim()} ${text}`
    : text;
};

const submitGrade = async () => {
  if (!canGradeAnswer.value) {
    ElMessage.warning('学生尚未提交作业，不能批改');
    return;
  }
  if (!selectedSubmission.value) return;
  try {
    await ElMessageBox.confirm('保存后评分将结案且不可修改，确认保存？', '保存评分', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  await gradeHomeworkSubmission(selectedSubmission.value.id, {
    totalScore: scoringSummary.value.totalScore,
    teacherComment: gradeForm.teacherComment.trim(),
  });
  ElMessage.success('评分已保存，答卷已结案');
  await fetchData();
  selectedSubmission.value = await getHomeworkSubmissionDetail(selectedSubmission.value.id);
  gradeForm.teacherComment = selectedSubmission.value.teacherComment || '';
  hydrateDraft();
};

/** 仅已提交或已批改的提交支持打回；打回后学生可重新编辑并再次提交。 */
const canReturnSubmission = computed(() => {
  const status = selectedSubmission.value?.submitStatus;
  return status === SUBMISSION_STATUS.SUBMITTED || status === SUBMISSION_STATUS.GRADED;
});

const doReturnSubmission = async () => {
  if (!selectedSubmission.value) return;
  returning.value = true;
  try {
    await returnHomeworkSubmission(selectedSubmission.value.id);
    ElMessage.success('已打回');
    await fetchData();
  } finally {
    returning.value = false;
  }
};

const confirmReturnSubmission = () => {
  ElMessageBox.confirm(
    '打回后学生可重新编辑并提交，已录入评分将被清除，是否继续？',
    '确认打回',
    {
      confirmButtonText: '确认打回',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => doReturnSubmission())
    .catch(() => undefined);
};

const resetFilters = () => {
  searchQuery.value = '';
  statusFilter.value = '';
};

const statusOf = (status: string) => resolveStatusPresentation({ module: 'homework', kind: 'submission', scene: 'teacher', status });

const submissionStatusLabel = (status: HomeworkSubmissionStatus) => (status ? statusOf(status).label : '未知');

const scoreText = (score?: number | null) => {
  return typeof score === 'number' ? formatScore(score) : '待评分';
};

const previewText = (text?: string | null) => {
  if (!text?.trim()) return '未提交文本内容';
  return text.length > 220 ? `${text.slice(0, 220)}...` : text;
};

const releaseScores = async () => {
  await ElMessageBox.confirm('确认发布本次作业成绩？发布后学生即可查看。', '发布成绩', {
    confirmButtonText: '确认发布',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await releaseHomeworkScores(homeworkId.value);
  ElMessage.success('作业成绩已发布');
  await fetchData();
};

const homeworkDraftPort: DraftPort<HomeworkSubmissionItem, GradingDraftState> = {
  create: detail => {
    selectedSubmission.value = detail;
    gradeForm.teacherComment = detail.teacherComment || '';
    hydrateDraft();
    return draftState;
  },
  setScore: setDraftScore,
  setComment: setDraftComment,
  hasChanges: hasDraftChanges,
  commit: () => {},
  forSummary: draft => ({ scores: draft.scores, marked: draft.marked }),
};

const workspace = useGradingWorkspace<HomeworkSubmissionItem, GradingDraftState>({
  domain: 'HOMEWORK',
  loadQueue: async () => {
    const resultRows = await listHomeworkSubmissions(homeworkId.value);
    rows.value = resultRows;
    return resultRows;
  },
  loadDetail: async id => {
    const nextDetail = await getHomeworkSubmissionDetail(id);
    activeSubmissionId.value = Number(id);
    selectedSubmission.value = nextDetail;
    gradeForm.teacherComment = nextDetail.teacherComment || '';
    hydrateDraft();
    return nextDetail;
  },
  scoreLinesOf: detail => (detail.questions ?? []).map(question => homeworkStrategy.adapt(question)),
  draftPort: homeworkDraftPort,
  statusOf: detail => detail.submitStatus,
  isReadOnly: status => homeworkStrategy.isReadOnly(status ?? ''),
  handlers: {
    saveGrade: async () => submitGrade(),
    saveAnswerGrade: async (_detail, key) => submitAnswerGrade(Number(key), false),
    returnSubmission: async () => doReturnSubmission(),
    releaseScores,
  },
});

watch(filteredRows, value => {
  if (!value.length) {
    activeSubmissionId.value = null;
    return;
  }

  if (!value.some(item => item.id === activeSubmissionId.value)) {
    activeSubmissionId.value = value[0].id;
  }
});

onMounted(fetchData);
</script>

<style scoped>
.submissions-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.submissions-hero {
  overflow: hidden;
  border-radius: 16px;
  padding: 28px 32px;
  background:
    radial-gradient(circle at right top, rgba(191, 219, 254, 0.18), transparent 32%),
    linear-gradient(135deg, #0f172a, #1e3a8a 58%, #2563eb);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
}

.submissions-hero__content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #eff6ff;
}

.submissions-hero__heading {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.submissions-hero__heading h1 {
  margin: 0;
  color: #ffffff;
}

.submissions-hero__heading p {
  margin: 10px 0 0;
  max-width: 760px;
  font-size: var(--font-size-lg);
  line-height: var(--line-height-body);
  color: rgba(219, 234, 254, 0.86);
}

.submissions-hero__actions {
  display: flex;
  gap: 12px;
}

.summary-card {
  padding: 24px;
  border-radius: 16px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.summary-card--primary {
  display: flex;
  gap: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.summary-card--light,
.summary-card--accent {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(226, 232, 240, 0.9);
}

.summary-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  flex-shrink: 0;
}

.summary-card__icon--amber {
  background: rgba(254, 243, 199, 0.9);
  color: #b45309;
}

.summary-card__icon--green {
  background: rgba(220, 252, 231, 0.9);
  color: #15803d;
}

.summary-card__label {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #64748b;
}

.summary-card--primary .summary-card__label,
.summary-card--primary .summary-card__desc,
.summary-card--primary .summary-card__value {
  color: #ffffff;
}

.summary-card__value {
  margin-top: 8px;
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: #0f172a;
}

.summary-card__value--small {
  font-size: var(--font-size-2xl);
}

.summary-card__desc {
  margin-top: 8px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-body);
  color: #64748b;
}

.summary-card__desc--spaced {
  margin-top: 18px;
}

.summary-card__header-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.toolbar-card__main {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
}

.toolbar-search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  padding: 0 16px;
  min-height: 52px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.toolbar-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.toolbar-filters {
  display: flex;
  gap: 12px;
}

.toolbar-filters :deep(.el-select) {
  width: 180px;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: var(--font-size-base);
}

.toolbar-divider {
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}

.submissions-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(340px, 0.9fr);
  gap: 20px;
}

.submissions-list-panel,
.quick-grade-card {
  padding: 24px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.06);
}

.panel-header h2,
.quick-grade-card__header h2 {
  margin: 0;
  color: #0f172a;
}

.panel-header p,
.quick-grade-card__header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: var(--font-size-md);
}

.submission-list-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 18px;
}

.submission-row {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  padding: 18px 20px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.submission-row:hover,
.submission-row--active {
  border-color: #93c5fd;
  background: #eff6ff;
  box-shadow: 0 12px 24px rgba(59, 130, 246, 0.12);
}

.submission-row__identity {
  display: flex;
  gap: 14px;
}

.submission-row__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 16px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  flex-shrink: 0;
}

.submission-row__name-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.submission-row__name-line strong {
  color: #0f172a;
  font-size: var(--font-size-lg);
}

.submission-row__username {
  color: #64748b;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.submission-row__identity p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: var(--font-size-base);
  line-height: var(--line-height-body);
}

.submission-row__meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.submission-row__meta-grid > div {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #e2e8f0;
}

.submission-row__label,
.quick-grade-card__label,
.identity-box__label {
  display: block;
  margin-bottom: 6px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
  color: #94a3b8;
}

.submission-row__meta-grid strong,
.quick-grade-card__stats strong,
.identity-box strong {
  color: #0f172a;
  font-size: var(--font-size-md);
}

.quick-grade-panel {
  display: flex;
  flex-direction: column;
}

.quick-grade-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.quick-grade-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.quick-grade-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.quick-grade-card__stats > div,
.identity-box {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.identity-box--wide {
  grid-column: span 2;
}

.identity-box small {
  display: block;
  margin-top: 6px;
  color: #64748b;
}

.quick-grade-card__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-grade-card__section-title,
.grade-dialog__section-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  color: #0f172a;
}

.quick-grade-card__preview,
.quick-grade-card__muted,
.answer-text {
  margin: 0;
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: var(--font-size-md);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.grade-dialog {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 20px;
}

.grade-dialog__main,
.grade-dialog__aside,
.grade-form-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.grade-dialog__identity-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.grade-dialog__content-card,
.grade-form-card {
  padding: 20px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.grade-form {
  margin-top: 4px;
}

.quick-comment-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-comment-block p {
  margin: 0;
  color: #64748b;
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}

.quick-comment-block__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-comment-chip {
  padding: 8px 12px;
  border: none;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 1280px) {
  .submissions-workspace,
  .grade-dialog {
    grid-template-columns: 1fr;
  }

  .toolbar-card__main {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-filters,
  .quick-grade-card__stats,
  .submission-row__meta-grid,
  .grade-dialog__identity-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .submissions-hero,
  .summary-card,
  .submissions-list-panel,
  .quick-grade-card {
    padding: 20px;
    border-radius: 16px;
  }

  .submissions-hero__heading,
  .submissions-hero__actions,
  .quick-grade-card__header,
  .toolbar-filters {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
}

.grade-form-card .el-button:disabled,
.grade-form-card .el-input-number.is-disabled,
.question-grade-item .el-button:disabled,
.question-grade-item .el-input-number.is-disabled,
.quick-comment-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
