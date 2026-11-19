<template>
  <div class="page submissions-page workbench-page">
    <section class="workbench-header submissions-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="submissions-header__eyebrow">教师后台 / 作业提交与批改</div>
          <h1>{{ homeworkTitle || '作业提交与批改' }}</h1>
          <p>围绕学生提交明细、得分区与教师反馈区组织批改工作流。</p>
        </div>
        <div class="workbench-header__actions submissions-hero__actions">
          <el-button @click="router.push('/teacher/homeworks')">返回作业管理</el-button>
          <el-button type="primary" :disabled="!activeSubmission" @click="openGradeDialog(activeSubmission)">快速批改</el-button>
        </div>
      </div>

      <div class="workbench-meta submissions-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 总提交数</span>
        <span class="workbench-meta__item"><strong>{{ pendingCount }}</strong> 待批改</span>
        <span class="workbench-meta__item"><strong>{{ gradedCount }}</strong> 已批改</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ averageScoreText }}</strong> 平均得分</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索学生姓名或学号" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable>
            <el-option label="全部状态" value="" />
            <el-option label="已保存" :value="SUBMISSION_STATUS.SAVED" />
            <el-option label="待批改" :value="SUBMISSION_STATUS.SUBMITTED" />
            <el-option label="已批改" :value="SUBMISSION_STATUS.GRADED" />
          </el-select>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>当前作业共 {{ rows.length }} 份提交</span>
        <span class="toolbar-divider"></span>
        <span>筛出 {{ filteredRows.length }} 份</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="submissions-workspace">
      <div class="submissions-list-panel" v-loading="loading">
        <header class="panel-header">
          <div>
            <h2>提交队列</h2>
            <p>保留学生上下文、提交状态与得分表达，支持直接切换当前批改对象。</p>
          </div>
        </header>

        <div v-if="filteredRows.length" class="submission-list-shell">
          <button
            v-for="row in filteredRows"
            :key="row.id"
            type="button"
            :class="['submission-row', { 'submission-row--active': activeSubmission?.id === row.id }]"
            @click="setActiveSubmission(row)"
          >
            <div class="submission-row__identity">
              <div class="submission-row__avatar">{{ row.studentName.slice(0, 1) }}</div>
              <div>
                <div class="submission-row__name-line">
                  <strong>{{ row.studentName }}</strong>
                  <span class="submission-row__username">{{ row.studentUsername }}</span>
                </div>
              </div>
            </div>

            <div class="submission-row__meta-grid">
              <div>
                <span class="submission-row__label">提交状态</span>
                <el-tag :type="submissionStatusTagType(row.submitStatus)" effect="light">
                  {{ submissionStatusLabel(row.submitStatus) }}
                </el-tag>
              </div>
              <div>
                <span class="submission-row__label">总分</span>
                <strong>{{ scoreText(row.totalScore) }}</strong>
              </div>
            </div>
          </button>
        </div>

        <el-empty v-else description="暂无匹配提交记录" />
      </div>

      <aside class="quick-grade-panel">
        <div v-if="activeSubmission" class="quick-grade-card">
          <div class="quick-grade-card__header">
            <div>
              <span class="quick-grade-card__eyebrow">快速批改</span>
              <h2>{{ activeSubmission.studentName }}</h2>
              <p>学号：{{ activeSubmission.studentUsername }}</p>
            </div>
            <el-button type="primary" plain @click="openGradeDialog(activeSubmission)">
              {{ activeSubmission.submitStatus === SUBMISSION_STATUS.SAVED ? '查看' : activeSubmission.submitStatus === SUBMISSION_STATUS.GRADED ? '查看/调整' : '批改' }}
            </el-button>
          </div>

          <div class="quick-grade-card__stats">
            <div>
              <span class="quick-grade-card__label">提交状态</span>
              <strong>{{ submissionStatusLabel(activeSubmission.submitStatus) }}</strong>
            </div>
            <div>
              <span class="quick-grade-card__label">当前得分</span>
              <strong>{{ scoreText(activeSubmission.totalScore) }}</strong>
            </div>
          </div>

          <div class="quick-grade-card__section">
            <div class="quick-grade-card__section-title">提交内容预览</div>
            <div class="quick-grade-card__preview">{{ previewText(activeSubmission.answerText) }}</div>
          </div>

          <div class="quick-grade-card__section">
            <div class="quick-grade-card__section-title">教师反馈摘要</div>
            <p class="quick-grade-card__muted">
              {{ activeSubmission.teacherComment || '尚未填写教师评语。' }}
            </p>
          </div>
        </div>

        <el-empty v-else description="请选择一个提交记录进行查看" />
      </aside>
    </section>

    <el-dialog v-model="dialogVisible" title="作业批改" width="920px" :before-close="handleDialogBeforeClose">
      <template v-if="selectedSubmission">
        <div class="grade-dialog">
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
      </template>
      <template #footer>
        <el-button @click="closeGradeDialog">取消</el-button>
        <el-button
          v-if="canReturnSubmission"
          type="warning"
          plain
          :loading="returning"
          @click="confirmReturnSubmission"
        >打回</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canGradeAnswer" @click="submitGrade">{{ selectedSubmission?.submitStatus === SUBMISSION_STATUS.SAVED ? '学生尚未提交' : selectedSubmission?.submitStatus === SUBMISSION_STATUS.GRADED ? '已完成批改' : '保存评分' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { SUBMISSION_STATUS } from '@/types/enums';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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

const route = useRoute();
const router = useRouter();
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
  return `${avg.toFixed(1)} 分`;
});

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listHomeworkSubmissions(homeworkId.value);
    if (!activeSubmissionId.value || !rows.value.some(item => item.id === activeSubmissionId.value)) {
      activeSubmissionId.value = rows.value[0]?.id ?? null;
    }
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
  dialogVisible.value = false;
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
    dialogVisible.value = false;
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

const submissionStatusLabel = (status: HomeworkSubmissionStatus) => {
  if (status === SUBMISSION_STATUS.SAVED) return '已保存';
  if (status === SUBMISSION_STATUS.SUBMITTED) return '待批改';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
  return status || '未知';
};

const submissionStatusTagType = (status: HomeworkSubmissionStatus) => {
  if (status === SUBMISSION_STATUS.GRADED) return 'success';
  if (status === SUBMISSION_STATUS.SUBMITTED) return 'warning';
  return 'info';
};

const scoreText = (score?: number | null) => {
  return typeof score === 'number' ? score.toFixed(1) : '待评分';
};

const previewText = (text?: string | null) => {
  if (!text?.trim()) return '未提交文本内容';
  return text.length > 220 ? `${text.slice(0, 220)}...` : text;
};

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
  border-radius: 32px;
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

.submissions-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(219, 234, 254, 0.78);
}

.submissions-hero__heading {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.submissions-hero__heading h1 {
  margin: 0;
  font-size: 32px;
  color: #ffffff;
}

.submissions-hero__heading p {
  margin: 10px 0 0;
  max-width: 760px;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(219, 234, 254, 0.86);
}

.submissions-hero__actions {
  display: flex;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  padding: 24px;
  border-radius: 28px;
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
  border-radius: 18px;
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
  font-size: 13px;
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
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.summary-card__value--small {
  font-size: 24px;
}

.summary-card__desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
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
  font-size: 12px;
  font-weight: 700;
}

.toolbar-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.06);
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
  border-radius: 18px;
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
  font-size: 13px;
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
  border-radius: 28px;
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
  font-size: 14px;
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
  border-radius: 22px;
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
  font-size: 18px;
  font-weight: 800;
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
  font-size: 16px;
}

.submission-row__username {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.submission-row__identity p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
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
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.submission-row__meta-grid strong,
.quick-grade-card__stats strong,
.identity-box strong {
  color: #0f172a;
  font-size: 14px;
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

.quick-grade-card__eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  border-radius: 18px;
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
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.quick-grade-card__preview,
.quick-grade-card__muted,
.answer-text {
  margin: 0;
  padding: 16px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 14px;
  line-height: 1.7;
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
  border-radius: 22px;
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
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
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
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .summary-grid,
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
  .toolbar-card,
  .submissions-list-panel,
  .quick-grade-card {
    padding: 20px;
    border-radius: 24px;
  }

  .submissions-hero__heading,
  .submissions-hero__actions,
  .quick-grade-card__header,
  .toolbar-filters {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid {
    grid-template-columns: 1fr;
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
