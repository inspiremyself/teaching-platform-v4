import { ACTIVITY_STATUS, SUBMISSION_STATUS } from '@/types/enums';
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import QuestionAnswerShell from '@/views/student/components/lab-answer/QuestionAnswerShell.vue';
import ChoiceAnswerEditor from '@/views/student/components/lab-answer/ChoiceAnswerEditor.vue';
import FillBlankInlineEditor from '@/views/student/components/lab-answer/FillBlankInlineEditor.vue';
import TextAnswerEditor from '@/views/student/components/lab-answer/TextAnswerEditor.vue';
import CodeLabAnswerEditor from '@/views/student/components/lab-answer/CodeLabAnswerEditor.vue';
import { buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromStep } from '@/views/student/components/lab-answer/answerPayload';
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from '@/views/student/components/lab-answer/types';
import { getStudentHomeworkDetail, saveStudentHomeworkDraft, submitHomework } from '@/api/homeworks';
import type { HomeworkQuestionItem, HomeworkSubmissionStatus, StudentHomeworkDetail, SubmitHomeworkRequest } from '@/types/homework';
import type { StudentChoiceOption, StudentLabStepItem } from '@/types/lab';
import type { InjectionKey } from 'vue';

export function useHomeworkDetail() {
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
const readonly = computed(() => detail.value?.submissionStatus === SUBMISSION_STATUS.SUBMITTED
  || detail.value?.submissionStatus === SUBMISSION_STATUS.GRADED
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

/** 计划满分 = Σ 题目分值；附件型作业（无题目）为 0，成绩页不显示分母 */
const planTotalScore = computed(() =>
  (detail.value?.questions ?? []).reduce((sum, question) => sum + (Number(question.score) || 0), 0),
);

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
      detail.value.submissionStatus = SUBMISSION_STATUS.SAVED;
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
  if (detail.value?.status === ACTIVITY_STATUS.CLOSED) return;
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
  if (!detail.value || detail.value.status === ACTIVITY_STATUS.CLOSED) return;
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
  if (status === SUBMISSION_STATUS.SAVED) return '已保存';
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
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

  return { ACTIVITY_STATUS, SUBMISSION_STATUS, computed, nextTick, onMounted, onUnmounted, reactive, ref, watch, onBeforeRouteLeave, useRoute, useRouter, ElMessage, ElMessageBox, QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, buildAnswerPayloadJson, buildCompatibilityAnswerText, hydrateDraftFromStep, isDraftAnswered, normalizeStudentAnswerQuestionType, getStudentHomeworkDetail, saveStudentHomeworkDraft, submitHomework, route, router, loading, submitting, detail, currentQuestionIndex, questionDrafts, form, autoSaveStatus, suppressAutoSave, lastSavedDraft, autoSaveTimer, draftFailureNotified, homeworkId, nowMs, isOverdue, isNotStarted, readonly, hasQuestionItems, currentQuestion, currentQuestionDraft, currentQuestionType, currentQuestionOptions, textHomeworkDraft, answeredQuestionCount, homeworkProgressPercent, showHomeworkResult, planTotalScore, countdownLabel, syncForm, fetchData, buildDraftPayload, submitForm, clearAutoSaveTimer, saveDraft, isDraftResponseStale, scheduleAutoSave, flushDraft, restoreSavedDraft, submissionStatusLabel, toStudentLabStepItem, resolveQuestionOptions, parseJsonArray, ensureQuestionDraft, isQuestionAnswered, updateCurrentQuestionDraft, updateTextHomeworkDraft, selectedOptionsOf, showQuestionFeedback, answerHint, questionTypeLabel, formatRate, formatDateTime, handleBeforeUnload, clockTimer };
}

export type HomeworkDetailState = ReturnType<typeof useHomeworkDetail>;
export const HomeworkDetailKey: InjectionKey<HomeworkDetailState> = Symbol('HomeworkDetailState');
