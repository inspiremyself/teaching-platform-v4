<template>
  <div class="student-homework-detail" v-loading="loading">
    <el-empty v-if="!loading && !detail" description="未获取到作业详情" />

    <template v-else-if="detail">
      <section class="detail-hero">
        <div class="detail-hero__main">
          <div class="detail-hero__topline">
            <button type="button" class="back-button" @click="router.back()">
              <span class="material-symbols-outlined">arrow_back</span>
              <span>返回</span>
            </button>
            <div class="detail-hero__actions">
              <el-button
                round
                :disabled="readonly || (detail?.submissionStatus !== 'SAVED' && !lastSavedDraft)"
                @click="restoreSavedDraft"
              >
                重置为已保存内容
              </el-button>
              <el-button type="primary" round :loading="submitting" :disabled="readonly" @click="submitForm">提交作业</el-button>
            </div>
          </div>

          <div class="detail-hero__body">
            <div class="detail-hero__title-block">
              <h1>{{ detail.title }}</h1>
            </div>

            <div class="detail-hero__chips">
              <span class="detail-chip">
                <span class="material-symbols-outlined">school</span>
                {{ detail.className || '-' }}
              </span>
              <span class="detail-chip">
                <span class="material-symbols-outlined">event</span>
                截止：{{ formatDateTime(detail.dueAt) }}
              </span>
              <span class="detail-chip detail-chip--status">
                <span class="material-symbols-outlined">task_alt</span>
                {{ submissionStatusLabel(detail.submissionStatus) }}
              </span>
              <span v-if="autoSaveStatus !== 'idle'" class="detail-chip detail-chip--status">
                <span class="material-symbols-outlined">{{ autoSaveStatus === 'failed' ? 'error' : autoSaveStatus === 'saving' ? 'sync' : 'cloud_done' }}</span>
                {{ autoSaveStatus === 'saving' ? '保存中…' : autoSaveStatus === 'saved' ? '已保存草稿' : '草稿保存失败' }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <section class="detail-workspace">
        <div v-if="hasQuestionItems" class="detail-nav-column">
          <article class="detail-card detail-card--nav">
            <div class="detail-card__header detail-card__header--stacked">
              <div>
                <span class="detail-card__eyebrow">题目导航</span>
                <h2>作业题目导航</h2>
              </div>
              <p class="detail-card__muted">切换题目并查看当前完成状态。</p>
            </div>

            <div class="progress-panel">
              <div class="progress-panel__header">
                <span>已完成进度</span>
                <span>{{ homeworkProgressPercent }}%</span>
              </div>
              <div class="progress-track">
                <span class="progress-fill" :style="{ width: `${homeworkProgressPercent}%` }"></span>
              </div>
              <p>{{ answeredQuestionCount }}/{{ detail.questions?.length || 0 }} 题已填写</p>
            </div>

            <div class="homework-question-wheel">
              <button
                v-for="(question, index) in detail.questions"
                :key="question.id"
                type="button"
                class="homework-question-wheel__item"
                :class="{
                  'is-active': currentQuestionIndex === index,
                  'is-done': isQuestionAnswered(question),
                }"
                @click="currentQuestionIndex = index"
              >
                <span class="homework-question-wheel__badge">{{ question.sortOrder }}</span>
                <span class="homework-question-wheel__body">
                  <span class="homework-question-wheel__state">
                    {{ isQuestionAnswered(question) ? '已填写' : '待作答' }}
                  </span>
                  <strong>第{{ question.sortOrder }}题</strong>
                  <small>{{ questionTypeLabel(question.type) }} · {{ question.score }} 分</small>
                </span>
              </button>
            </div>
          </article>
        </div>

        <div class="detail-main-column">
          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">作业说明</span>
                <h2>作业说明</h2>
              </div>
            </div>

            <p class="detail-card__paragraph">{{ detail.description || '当前作业暂无额外说明。' }}</p>

            <div v-if="detail.attachmentPath" class="attachment-note">
              <span class="material-symbols-outlined">attach_file</span>
              <div>
                <strong>教师提供的附件信息</strong>
                <p>{{ detail.attachmentPath }}</p>
              </div>
            </div>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">作答区</span>
                <h2>{{ hasQuestionItems ? '逐题作答' : '在线作答' }}</h2>
              </div>
              <span class="detail-card__tip">{{ hasQuestionItems ? '填写后统一提交' : '完成作答后提交' }}</span>
            </div>

            <QuestionAnswerShell
              v-if="!hasQuestionItems"
              title="作业答案"
              hint="完成整份在线作答内容后统一提交。"
              chip="文本作答"
            >
              <TextAnswerEditor
                :model-value="textHomeworkDraft"
                :rows="16"
                :maxlength="20000"
                :disabled="readonly"
                placeholder="请输入整份文本作业内容"
                @update:model-value="updateTextHomeworkDraft"
              />
            </QuestionAnswerShell>

            <div v-else class="homework-question-list">
              <article v-if="currentQuestion" class="result-question-card">
                <div class="result-question-card__header">
                  <div>
                    <span class="result-question-card__badge">第{{ currentQuestion.sortOrder }}题</span>
                    <h2>{{ currentQuestion.stem }}</h2>
                  </div>
                  <span class="result-question-card__score">{{ currentQuestion.score }} 分</span>
                </div>

                <section
                  v-if="currentQuestionType === 'FILL_BLANK'"
                  class="homework-question-prompt"
                >
                  <div class="homework-question-prompt__label">题目内容</div>
                  <FillBlankInlineEditor
                    :model-value="currentQuestionDraft"
                    :prompt="currentQuestion.stem"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />
                </section>

                <QuestionAnswerShell
                  v-if="currentQuestion && currentQuestionType !== 'FILL_BLANK'"
                  title="作答区"
                  :hint="answerHint(currentQuestion.type)"
                  :chip="questionTypeLabel(currentQuestion.type)"
                >
                  <template #callout>
                    <div v-if="currentQuestionType === 'CODE'" class="code-callout">
                      <div class="code-callout__title">代码作答</div>
                      <p>请按照实验答题模式输入代码内容，提交时会同时保存兼容文本和结构化答案。</p>
                    </div>
                  </template>

                  <ChoiceAnswerEditor
                    v-if="currentQuestionType === 'SINGLE_CHOICE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="single"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="multiple"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'TRUE_FALSE'"
                    :model-value="currentQuestionDraft"
                    :options="currentQuestionOptions"
                    mode="judge"
                    :disabled="readonly"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <CodeLabAnswerEditor
                    v-else-if="currentQuestionType === 'CODE'"
                    :model-value="currentQuestionDraft"
                    language="CODE"
                    :rows="14"
                    :maxlength="20000"
                    :disabled="readonly"
                    placeholder="// 请输入该题代码答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <TextAnswerEditor
                    v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                    :model-value="currentQuestionDraft"
                    :rows="8"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入该题答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />

                  <TextAnswerEditor
                    v-else
                    :model-value="currentQuestionDraft"
                    :rows="10"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入该题答案"
                    @update:model-value="updateCurrentQuestionDraft"
                  />
                </QuestionAnswerShell>

                <div
                  v-if="showQuestionFeedback(currentQuestion)"
                  class="homework-question-feedback"
                >
                  <div class="homework-question-feedback__meta">
                    <span v-if="currentQuestion.answer?.score !== null && currentQuestion.answer?.score !== undefined">
                      得分 {{ currentQuestion.answer?.score }}/{{ currentQuestion.score }}
                    </span>
                    <span v-if="currentQuestion.answer?.teacherComment">教师已给出评语</span>
                  </div>
                  <p>{{ currentQuestion.answer?.teacherComment || '当前题目暂无教师评语。' }}</p>
                </div>
              </article>
            </div>
          </article>
        </div>

        <aside class="detail-side-column">
          <article class="detail-card detail-card--meta">
            <div class="detail-meta-grid">
              <div class="detail-meta-item">
                <span>开始时间</span>
                <strong>{{ formatDateTime(detail.startAt) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>截止时间</span>
                <strong>{{ formatDateTime(detail.dueAt) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>提交状态</span>
                <strong>{{ submissionStatusLabel(detail.submissionStatus) }}</strong>
              </div>
              <div class="detail-meta-item">
                <span>查重率</span>
                <strong>{{ formatRate(detail.plagiarismRate) }}</strong>
              </div>
              <div v-if="showHomeworkResult" class="detail-meta-item detail-meta-item--accent">
                <span>总分</span>
                <strong>{{ detail.totalScore ?? '-' }}</strong>
              </div>
            </div>
          </article>

          <article class="detail-card detail-card--dark">
            <span class="detail-card__eyebrow detail-card__eyebrow--light">剩余时间</span>
            <h2>{{ countdownLabel }}</h2>
            <p>{{ isNotStarted ? '作业尚未开始' : isOverdue ? '作业已截止' : readonly ? '当前为结果查看状态。' : '截止前确认作答后提交。' }}</p>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">附件信息</span>
                <h2>附件信息</h2>
              </div>
            </div>

            <el-input
              v-model="form.attachmentPath"
              :disabled="readonly"
                placeholder="如需补充附件，请填写附件位置或说明后再提交"
              />

            <p class="detail-card__muted">需要附件时再补充说明。</p>
          </article>

          <article class="detail-card">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">提交清单</span>
                <h2>提交清单</h2>
              </div>
            </div>

            <ul class="check-list">
                <li :class="{ 'check-list__item--done': hasQuestionItems ? answeredQuestionCount > 0 : !!(form.answerText || '').trim() }">
                  <span class="material-symbols-outlined">check_circle</span>
                  {{ hasQuestionItems ? `已填写 ${answeredQuestionCount}/${detail.questions?.length || 0} 题` : '已填写文本作答' }}
                </li>
              <li :class="{ 'check-list__item--done': !detail.attachmentPath || !!form.attachmentPath?.trim() }">
                <span class="material-symbols-outlined">check_circle</span>
                已确认附件信息
              </li>
              <li :class="{ 'check-list__item--done': detail.submissionStatus === 'SUBMITTED' || detail.submissionStatus === 'GRADED' }">
                <span class="material-symbols-outlined">check_circle</span>
                已进入正式提交状态
              </li>
            </ul>
          </article>

          <article class="detail-card" v-if="showHomeworkResult">
            <div class="detail-card__header">
              <div>
                <span class="detail-card__eyebrow">反馈回看</span>
                <h2>反馈回看</h2>
              </div>
            </div>

            <div class="feedback-stack">
              <div class="feedback-pill">
                <span>总分</span>
                <strong>{{ detail.totalScore ?? '-' }}</strong>
              </div>
              <div class="feedback-pill feedback-pill--blue">
                <span>查重率</span>
                <strong>{{ formatRate(detail.plagiarismRate) }}</strong>
              </div>
            </div>

            <p class="detail-card__paragraph detail-card__paragraph--compact">{{ detail.teacherComment || '当前尚无教师评语。' }}</p>
          </article>
        </aside>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import QuestionAnswerShell from './components/lab-answer/QuestionAnswerShell.vue';
import ChoiceAnswerEditor from './components/lab-answer/ChoiceAnswerEditor.vue';
import FillBlankInlineEditor from './components/lab-answer/FillBlankInlineEditor.vue';
import TextAnswerEditor from './components/lab-answer/TextAnswerEditor.vue';
import CodeLabAnswerEditor from './components/lab-answer/CodeLabAnswerEditor.vue';
import { buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromStep } from './components/lab-answer/answerPayload';
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from './components/lab-answer/types';
import { getStudentHomeworkDetail, saveStudentHomeworkDraft, submitHomework } from '@/api/homeworks';
import type { HomeworkQuestionItem, HomeworkSubmissionStatus, StudentHomeworkDetail, SubmitHomeworkRequest } from '@/types/homework';
import type { StudentChoiceOption, StudentLabStepItem } from '@/types/lab';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const detail = ref<StudentHomeworkDetail | null>(null);
const currentQuestionIndex = ref(0);
const questionDrafts = reactive<Record<number, LabAnswerDraft>>({});

const form = reactive<SubmitHomeworkRequest>({
  answerText: '',
  attachmentPath: '',
});

/** 草稿自动保存状态：顶部状态条三态展示。 */
const autoSaveStatus = ref<'idle' | 'saving' | 'saved' | 'failed'>('idle');
/** 详情回填/提交期间抑制自动保存，避免用户未操作就产生草稿请求。 */
const suppressAutoSave = ref(false);
/** 最近一次成功保存的载荷快照，供“重置为已保存内容”回填。 */
const lastSavedDraft = ref<SubmitHomeworkRequest | null>(null);
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined;
let draftFailureNotified = false;

const homeworkId = computed(() => Number(route.params.id));
// 响应式时钟：每秒刷新一次，保证截止后页面无需重新加载即可自动进入只读态。
const nowMs = ref(Date.now());
const isOverdue = computed(() => {
  const dueAt = detail.value?.dueAt;
  if (!dueAt) return false;
  const dueTime = new Date(dueAt).getTime();
  return !Number.isNaN(dueTime) && nowMs.value > dueTime;
});
const isNotStarted = computed(() => {
  const startAt = detail.value?.startAt;
  if (!startAt) return false;
  const startTime = new Date(startAt).getTime();
  return !Number.isNaN(startTime) && nowMs.value < startTime;
});
const readonly = computed(() => detail.value?.submissionStatus === 'SUBMITTED'
  || detail.value?.submissionStatus === 'GRADED'
  || isNotStarted.value
  || isOverdue.value);
const hasQuestionItems = computed(() => !!detail.value?.questions?.length);
const currentQuestion = computed(() => detail.value?.questions?.[currentQuestionIndex.value] ?? null);
const currentQuestionDraft = computed(() => (currentQuestion.value ? ensureQuestionDraft(currentQuestion.value) : null));
const currentQuestionType = computed(() => normalizeStudentAnswerQuestionType(currentQuestion.value?.type));
const currentQuestionOptions = computed(() => resolveQuestionOptions(currentQuestion.value));
const textHomeworkDraft = computed<LabAnswerDraft>(() => ({ kind: 'text', text: form.answerText || '', images: [] }));
const answeredQuestionCount = computed(() => detail.value?.questions?.filter(question => isQuestionAnswered(question)).length || 0);
const homeworkProgressPercent = computed(() => {
  const total = detail.value?.questions?.length || 0;
  if (!total) return 0;
  return Math.round((answeredQuestionCount.value / total) * 100);
});
const showHomeworkResult = computed(() => typeof detail.value?.totalScore === 'number' || !!detail.value?.teacherComment);

const countdownLabel = computed(() => {
  const dueAt = detail.value?.dueAt;
  if (!dueAt) return '时间待定';
  const dueTime = new Date(dueAt).getTime();
  if (Number.isNaN(dueTime)) return '时间待确认';

  const diff = dueTime - nowMs.value;
  if (diff <= 0) return '已截止';

  const hours = Math.floor(diff / (60 * 60 * 1000));
  const days = Math.floor(hours / 24);
  const remainHours = hours % 24;
  const minutes = Math.floor((diff % (60 * 60 * 1000)) / (60 * 1000));

  if (days > 0) {
    return `${days} 天 ${remainHours} 小时`;
  }
  return `${remainHours} 小时 ${minutes} 分`;
});

/**
 * 用服务端详情回填页面表单与各题草稿。
 *
 * <p>回填本身会触发 `questionDrafts` / `form` 的深度监听，若不抑制就会产生
 * 用户未操作却自动保存草稿的请求，因此整个过程用 `suppressAutoSave` 包裹，
 * 并等待一次 `nextTick`（watcher 默认 pre 刷新）结束后再恢复。</p>
 */
const syncForm = async () => {
  suppressAutoSave.value = true;
  form.answerText = detail.value?.answerText || '';
  form.attachmentPath = detail.value?.attachmentAnswerPath || detail.value?.answerFilePath || '';
  Object.keys(questionDrafts).forEach(key => delete questionDrafts[Number(key)]);
  detail.value?.questions?.forEach(question => {
    questionDrafts[question.id] = hydrateDraftFromStep(toStudentLabStepItem(question));
  });
  currentQuestionIndex.value = 0;
  await nextTick();
  suppressAutoSave.value = false;
};

const fetchData = async () => {
  loading.value = true;
  try {
    detail.value = await getStudentHomeworkDetail(homeworkId.value);
    await syncForm();
  } finally {
    loading.value = false;
  }
};

/**
 * 组装草稿与正式提交共用的载荷，保证两条链路字段完全一致。
 * 题目型作业把逐题草稿映射为 `answers[]`，整份文本作业只传 `answerText`。
 */
const buildDraftPayload = (): SubmitHomeworkRequest => ({
  answerText: hasQuestionItems.value ? undefined : form.answerText,
  attachmentPath: form.attachmentPath?.trim() || null,
  answers: hasQuestionItems.value
    ? detail.value?.questions?.map(question => ({
        homeworkQuestionId: question.id,
        questionType: normalizeStudentAnswerQuestionType(question.type),
        answerText: buildCompatibilityAnswerText(ensureQuestionDraft(question)),
        answerJson: buildAnswerPayloadJson(ensureQuestionDraft(question)),
        selectedOptions: selectedOptionsOf(ensureQuestionDraft(question)),
      }))
    : undefined,
});

const submitForm = async () => {
  if (isOverdue.value) {
    ElMessage.warning('作业已截止，不能提交');
    return;
  }
  if (isNotStarted.value) {
    ElMessage.warning('作业尚未开始');
    return;
  }
  if (!hasQuestionItems.value && !(form.answerText || '').trim()) {
    ElMessage.warning('请输入作业答案后再提交');
    return;
  }
  if (hasQuestionItems.value && !answeredQuestionCount.value) {
    ElMessage.warning('请至少完成一道题目后再提交');
    return;
  }

  await ElMessageBox.confirm('提交后将按当前内容覆盖本次作答并进入已提交状态，是否继续？', '提交确认', {
    type: 'warning',
  });

  submitting.value = true;
  // 正式提交期间禁止自动保存，避免草稿请求与提交请求互相覆盖。
  suppressAutoSave.value = true;
  clearAutoSaveTimer();
  let submitSucceeded = false;
  try {
    await submitHomework(homeworkId.value, buildDraftPayload());
    submitSucceeded = true;
    await fetchData();
    ElMessage.success('作业提交成功');
  } finally {
    submitting.value = false;
    // 成功后保持抑制（此时 readonly 已为真）；失败则恢复自动保存能力。
    suppressAutoSave.value = submitSucceeded;
  }
};

/** 清理未触发的防抖定时器。 */
function clearAutoSaveTimer() {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer);
    autoSaveTimer = undefined;
  }
}

/**
 * 立即保存草稿。
 *
 * <p>成功时同步更新本地提交状态与快照，供“重置为已保存内容”回填；
 * 失败不阻断作答，只在连续失败序列中提示一次。</p>
 */
const saveDraft = async () => {
  const payload = buildDraftPayload();
  autoSaveStatus.value = 'saving';
  try {
    await saveStudentHomeworkDraft(homeworkId.value, payload);
    if (isDraftResponseStale()) {
      autoSaveStatus.value = 'idle';
      return;
    }
    autoSaveStatus.value = 'saved';
    if (detail.value) {
      detail.value.submissionStatus = 'SAVED';
    }
    lastSavedDraft.value = structuredClone(payload);
    draftFailureNotified = false;
  } catch {
    if (isDraftResponseStale()) {
      autoSaveStatus.value = 'idle';
      return;
    }
    autoSaveStatus.value = 'failed';
    if (!draftFailureNotified) {
      draftFailureNotified = true;
      ElMessage.warning('草稿保存失败，请检查网络后重试');
    }
  }
};

/**
 * 迟到的草稿回执判定：提交或详情回填已接管时，不再用旧回执改写页面状态，
 * 避免提交成功后仍显示“保存中/保存失败”。
 */
const isDraftResponseStale = () => submitting.value || suppressAutoSave.value;

/**
 * 防抖排期自动保存：只读、加载中、提交中、回填期间或作业已关闭时不排期，
 * 避免必然被后端拒绝的请求与失败提示。
 */
const scheduleAutoSave = () => {
  if (readonly.value || loading.value || submitting.value || suppressAutoSave.value) return;
  if (detail.value?.status === 'CLOSED') return;
  clearAutoSaveTimer();
  autoSaveTimer = setTimeout(() => {
    autoSaveTimer = undefined;
    void saveDraft();
  }, 800);
};

/**
 * 离开页面前冲刷未保存的草稿。
 * 提交后、截止/未开始、加载中、作业已关闭时直接返回，不再发请求。
 */
const flushDraft = async () => {
  clearAutoSaveTimer();
  if (readonly.value || submitting.value || loading.value) return;
  if (!detail.value || detail.value.status === 'CLOSED') return;
  await saveDraft();
};

/**
 * 重置为最近一次成功保存的草稿内容。
 * 有快照时按题目 id 回填逐题草稿；无快照（例如初始即为已保存状态）则回退到服务端详情。
 */
const restoreSavedDraft = () => {
  if (!lastSavedDraft.value) {
    void syncForm();
    return;
  }
  const snapshot = lastSavedDraft.value;
  form.answerText = snapshot.answerText ?? '';
  form.attachmentPath = snapshot.attachmentPath ?? '';
  snapshot.answers?.forEach(answer => {
    const question = detail.value?.questions?.find(item => item.id === answer.homeworkQuestionId);
    if (!question) return;
    questionDrafts[question.id] = hydrateDraftFromStep({
      ...toStudentLabStepItem(question),
      answerText: answer.answerText ?? '',
      answerPayloadJson: answer.answerJson ?? null,
    });
  });
};

const submissionStatusLabel = (status?: HomeworkSubmissionStatus | null) => {
  if (status === 'SAVED') return '已保存';
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
  return '未开始';
};

const toStudentLabStepItem = (question: HomeworkQuestionItem): StudentLabStepItem => ({
  id: question.id,
  stepNo: question.sortOrder,
  title: `第${question.sortOrder}题`,
  questionType: normalizeStudentAnswerQuestionType(question.type),
  content: question.stem,
  stepScore: question.score,
  allowPaste: true,
  answerText: question.answer?.answerText || '',
  answerPayloadJson: question.answer?.answerJson || null,
  options: resolveQuestionOptions(question),
});

function resolveQuestionOptions(question?: HomeworkQuestionItem | null): StudentChoiceOption[] {
  if (!question?.options) return [];

  const rawOptions = Array.isArray(question.options)
    ? question.options
    : parseJsonArray(question.options);

  if (!Array.isArray(rawOptions)) return [];

  return rawOptions.map((item, index) => {
    if (item && typeof item === 'object' && 'key' in item) {
      const record = item as Record<string, unknown>;
      const label = String(record.label ?? record.content ?? record.text ?? record.value ?? '').trim();
      return {
        key: String(record.key ?? '').trim(),
        label,
      };
    }

    const value = String(item ?? '');
    const dotIndex = value.indexOf('.');
    if (dotIndex > 0) {
      return {
        key: value.slice(0, dotIndex).trim(),
        label: value.slice(dotIndex + 1).trim(),
      };
    }

    return {
      key: String.fromCharCode(65 + index),
      label: value,
    };
  }).filter(option => option.key && option.label);
}

function parseJsonArray(value?: string | null) {
  if (!value?.trim()) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function ensureQuestionDraft(question: HomeworkQuestionItem): LabAnswerDraft {
  if (!questionDrafts[question.id]) {
    questionDrafts[question.id] = hydrateDraftFromStep(toStudentLabStepItem(question));
  }
  return questionDrafts[question.id];
}

function isQuestionAnswered(question: HomeworkQuestionItem) {
  return isDraftAnswered(ensureQuestionDraft(question));
}

function updateCurrentQuestionDraft(value: LabAnswerDraft) {
  if (!currentQuestion.value) return;
  questionDrafts[currentQuestion.value.id] = value;
}

function updateTextHomeworkDraft(value: LabAnswerDraft) {
  form.answerText = value.kind === 'text' ? value.text : buildCompatibilityAnswerText(value);
}

function selectedOptionsOf(draft: LabAnswerDraft): string[] | undefined {
  if (draft.kind === 'multiple') return draft.selectedKeys;
  if (draft.kind === 'single' && draft.selectedKey) return [draft.selectedKey];
  if (draft.kind === 'judge' && draft.value) return [draft.value];
  return undefined;
}

function showQuestionFeedback(question: HomeworkQuestionItem) {
  return Boolean(question.answer?.teacherComment) || question.answer?.score !== null && question.answer?.score !== undefined;
}

function answerHint(type?: string | null) {
  const normalized = normalizeStudentAnswerQuestionType(type);
  if (normalized === 'SINGLE_CHOICE') return '点击选项完成单选作答。';
  if (normalized === 'MULTIPLE_CHOICE') return '可多选，再次点击可取消。';
  if (normalized === 'TRUE_FALSE') return '按照实验判断题交互选择正确或错误。';
  if (normalized === 'CODE') return '使用代码编辑器样式输入答案。';
  if (normalized === 'SHORT_ANSWER') return '请输入简答内容，提交时同步保存文本与结构化答案。';
  return '请输入本题作答内容。';
}

function questionTypeLabel(type?: string | null) {
  const normalized = normalizeStudentAnswerQuestionType(type);
  if (normalized === 'SINGLE_CHOICE') return '单选题';
  if (normalized === 'MULTIPLE_CHOICE') return '多选题';
  if (normalized === 'TRUE_FALSE') return '判断题';
  if (normalized === 'FILL_BLANK') return '填空题';
  if (normalized === 'CODE') return '代码题';
  if (normalized === 'TEXT') return '文本题';
  return '简答题';
}

const formatRate = (rate?: number | null) => {
  return typeof rate === 'number' ? `${rate.toFixed(2)}%` : '-';
};

const formatDateTime = (value?: string | null) => {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
};

fetchData();

// 逐题草稿与整份文本/附件任一变化都进入防抖自动保存。
watch([questionDrafts, () => form.answerText, () => form.attachmentPath], scheduleAutoSave, { deep: true });

// 路由离开前冲刷草稿；浏览器关闭/刷新时只发起不等待结果的保存。
onBeforeRouteLeave(() => flushDraft());

const handleBeforeUnload = () => {
  void flushDraft();
};

let clockTimer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  clockTimer = setInterval(() => {
    nowMs.value = Date.now();
  }, 1000);
  window.addEventListener('beforeunload', handleBeforeUnload);
});
onUnmounted(() => {
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = undefined;
  }
  clearAutoSaveTimer();
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<style scoped>
.student-homework-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-hero {
  padding: 14px 16px;
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(191, 219, 254, 0.28), transparent 24%),
    linear-gradient(135deg, rgba(255,255,255,0.96), rgba(248,250,252,0.96));
  border: 1px solid rgba(191, 219, 254, 0.72);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.detail-hero__main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-hero__topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: none;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.detail-hero__actions {
  display: flex;
  gap: 6px;
}

.detail-hero__actions :deep(.el-button) {
  min-height: 30px;
  padding-inline: 12px;
}

.detail-hero__body {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.detail-hero__title-block {
  min-width: 0;
}

.detail-hero h1 {
  margin: 0;
  font-size: 22px;
  color: #191c22;
}

.detail-hero__desc {
  margin: 0;
  max-width: 680px;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
}

.detail-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.detail-chip--status {
  background: #dbeafe;
  color: #1d4ed8;
}

.info-card__label,
.detail-card__eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
}

.detail-card__eyebrow--light {
  color: rgba(191, 219, 254, 0.78);
}

.detail-workspace {
  display: grid;
  grid-template-columns: minmax(220px, 0.72fr) minmax(0, 1.48fr) minmax(220px, 0.62fr);
  gap: 10px;
}

.detail-nav-column,
.detail-main-column,
.detail-side-column {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.detail-card--meta {
  padding: 10px;
}

.detail-card--nav {
  position: sticky;
  top: 12px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-meta-item {
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.detail-meta-item span {
  display: block;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.detail-meta-item strong {
  display: block;
  margin-top: 4px;
  color: #191c22;
  font-size: 12px;
}

.detail-meta-item--accent {
  background: #eff6ff;
}

.detail-card--dark {
  background: linear-gradient(135deg, #0f172a, #1e3a8a);
  color: #ffffff;
}

.detail-card--dark h2,
.detail-card--dark p {
  color: #ffffff;
}

.detail-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.detail-card__header--stacked {
  flex-direction: column;
  align-items: stretch;
}

.detail-card__header h2,
.detail-card--dark h2 {
  margin: 0;
  font-size: 17px;
  color: #191c22;
}

.detail-card__tip {
  color: #64748b;
  font-size: 11px;
  line-height: 1.3;
  max-width: 140px;
  text-align: right;
}

.detail-card__paragraph,
.detail-card__muted {
  margin: 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.35;
}

.detail-card__paragraph--compact {
  line-height: 1.5;
}

.attachment-note {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
}

.attachment-note strong {
  display: block;
  font-size: 14px;
}

.attachment-note p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.answer-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.answer-form :deep(.el-textarea__inner),
.result-question-card :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 12px 14px;
}

.homework-question-wheel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.homework-question-wheel__item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.homework-question-wheel__item:hover {
  border-color: #93c5fd;
  box-shadow: 0 8px 20px rgba(30, 64, 175, 0.08);
}

.homework-question-wheel__item.is-active {
  border-color: #2563eb;
  background: #eff6ff;
}

.homework-question-wheel__item.is-done:not(.is-active) {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.homework-question-wheel__badge {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #e2e8f0;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.homework-question-wheel__item.is-active .homework-question-wheel__badge {
  background: #2563eb;
  color: #fff;
}

.homework-question-wheel__item.is-done:not(.is-active) .homework-question-wheel__badge {
  background: #16a34a;
  color: #fff;
}

.homework-question-wheel__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.homework-question-wheel__state {
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

.homework-question-wheel__body strong {
  color: #191c22;
  font-size: 13px;
}

.homework-question-wheel__body small {
  color: #64748b;
  font-size: 11px;
}

.homework-question-prompt {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
}

.homework-question-prompt__label {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.code-callout {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
}

.code-callout__title {
  font-size: 12px;
  font-weight: 800;
}

.code-callout p {
  margin: 4px 0 0;
  color: #334155;
  font-size: 12px;
}

.homework-question-feedback {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.homework-question-feedback__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.homework-question-feedback p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.check-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #64748b;
  font-size: 12px;
}

.check-list__item--done {
  color: #15803d !important;
  background: #f0fdf4 !important;
  border-color: #bbf7d0 !important;
}

.feedback-stack {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.feedback-pill {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.feedback-pill span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.feedback-pill strong {
  color: #191c22;
  font-size: 15px;
}

.feedback-pill--blue {
  background: #eff6ff;
}

.homework-question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.progress-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.progress-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.progress-track {
  width: 100%;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #dbeafe;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
}

.progress-panel p {
  margin: 0;
  color: #64748b;
  font-size: 11px;
}

.result-question-card {
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.result-question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.result-question-card__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.result-question-card h2 {
  margin: 8px 0 0;
  font-size: 15px;
  line-height: 1.5;
  color: #191c22;
}

.result-question-card__score {
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

@media (max-width: 1200px) {
  .detail-workspace {
    grid-template-columns: 1fr;
  }

  .detail-card--nav {
    position: static;
  }
}

@media (max-width: 900px) {
  .detail-hero,
  .detail-card,
  .info-card {
    padding: 14px;
    border-radius: 16px;
  }

  .detail-hero__body,
  .detail-hero__topline,
  .detail-hero__actions,
  .detail-card__header,
  .feedback-stack,
  .detail-meta-grid {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .detail-card__tip {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .result-question-card__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
