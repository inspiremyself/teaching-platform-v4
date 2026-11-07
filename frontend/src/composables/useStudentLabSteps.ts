import { SUBMISSION_STATUS } from '@/types/enums';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import QuestionAnswerShell from '@/views/student/components/lab-answer/QuestionAnswerShell.vue';
import ChoiceAnswerEditor from '@/views/student/components/lab-answer/ChoiceAnswerEditor.vue';
import FillBlankInlineEditor from '@/views/student/components/lab-answer/FillBlankInlineEditor.vue';
import TextAnswerEditor from '@/views/student/components/lab-answer/TextAnswerEditor.vue';
import CodeLabAnswerEditor from '@/views/student/components/lab-answer/CodeLabAnswerEditor.vue';
import { getStudentLabDetail, saveStudentLabAnswer, submitStudentLab } from '@/api/labs';
import type { LabQuestionType, LabReportStatus, StudentLabDetail, StudentLabStepItem } from '@/types/lab';
import {
  hydrateDraftFromStep,
  buildAnswerPayloadJson,
  buildCompatibilityAnswerText,
} from '@/views/student/components/lab-answer/answerPayload';
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from '@/views/student/components/lab-answer/types';
import { useLabAnswerDrafts } from '@/views/student/components/lab-answer/useLabAnswerDrafts';
import { behaviorCollector } from '@/utils/behaviorCollector';
import type { InjectionKey } from 'vue';

export function useStudentLabSteps() {
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const detail = ref<StudentLabDetail | null>(null);
const currentIndex = ref(0);
const summaryDraft = ref('');
const itemAutosaveStates = reactive<Record<number, 'idle' | 'pending' | 'saved' | 'error'>>({});
const { ensureDraft, getDraft, patchDraft, buildSavePayload, hydrateAllDrafts } = useLabAnswerDrafts();

let autosaveTimer: ReturnType<typeof setTimeout> | null = null;
let saveQueue: Promise<boolean> = Promise.resolve(true);
let clockTimer: ReturnType<typeof setInterval> | undefined;
// 响应式时钟：每秒刷新一次，跨过开始/截止时间后自动切换到只读态。
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
const currentItemQueryId = computed(() => {
  const raw = route.query.item;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed !== 0 ? parsed : null;
});
const labItems = computed(() => {
  if (!detail.value) return [];
  return detail.value.items?.length ? detail.value.items : detail.value.steps || [];
});
const currentItem = computed(() => labItems.value[currentIndex.value] ?? null);
const currentDraft = computed<LabAnswerDraft | null>(() => (currentItem.value ? ensureDraft(currentItem.value) : null));
const currentQuestionType = computed(() => normalizeStudentAnswerQuestionType(currentItem.value?.questionType));
const currentCodeEditorLanguage = ref('JAVA');
const currentItemCodeLanguage = computed(() => {
  if (!currentItem.value || normalizedQuestionType(currentItem.value.questionType) !== 'CODE') {
    return '';
  }

  const draft = currentDraft.value;
  return draft?.kind === 'code'
    ? draft.language || currentCodeEditorLanguage.value || currentItem.value.editorLanguage || 'CODE'
    : currentCodeEditorLanguage.value || currentItem.value.editorLanguage || 'CODE';
});
const readonly = computed(() => ([SUBMISSION_STATUS.SUBMITTED, SUBMISSION_STATUS.GRADED] as string[]).includes(detail.value?.submissionStatus || '')
  || isNotStarted.value
  || isOverdue.value);
// 提交按钮只允许“已保存且存在题项”的草稿推进，与后端“仅 SAVED 可提交”状态机保持一致
const canSubmit = computed(
  () => detail.value?.submissionStatus === SUBMISSION_STATUS.SAVED
    && labItems.value.length > 0
    && !isNotStarted.value
    && !isOverdue.value
);
const submitHint = computed(() => {
  if (isNotStarted.value) return '实验尚未开始';
  if (isOverdue.value) return '实验已截止，不能保存或提交';
  const status = detail.value?.submissionStatus;
  if (!status) return '请先保存实验答案后再提交';
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交，如需修改请联系教师打回';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改，如需修改请联系教师打回';
  if (labItems.value.length === 0) return '暂无可提交的实验题项';
  return '';
});
const answeredCount = computed(() => labItems.value.filter((item) => hasSavedAnswer(item)).length);
const dirtyItemCount = computed(() => labItems.value.filter((item) => isItemDirty(item)).length);
const progressPercent = computed(() => {
  const total = labItems.value.length;
  if (!total) return 0;
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.GRADED) return 100;
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return 92;
  return Math.round((answeredCount.value / total) * 100);
});
const progressText = computed(() => {
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.GRADED) return '实验已完成批改，可逐题查看教师反馈。';
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return '实验已提交，当前内容仅支持查看。';
  if (dirtyItemCount.value > 0) return `还有 ${dirtyItemCount.value} 道题未保存。`;
  return `已保存 ${answeredCount.value}/${labItems.value.length} 个题项。`;
});
const submissionTagType = computed(() => {
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.GRADED) return 'success';
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.SUBMITTED) return 'warning';
  if (detail.value?.submissionStatus === SUBMISSION_STATUS.SAVED) return 'primary';
  return 'info';
});
const currentSaveStateLabel = computed(() => {
  if (!currentItem.value) return '未选择题目';
  if (readonly.value) return '当前内容已锁定';
  if (submitting.value) return '正在提交';
  if (saving.value || itemAutosaveStates[currentItem.value.id] === 'pending') return '正在保存';
  if (itemAutosaveStates[currentItem.value.id] === 'error') return '保存失败';
  if (!isItemDirty(currentItem.value) && hasSavedAnswer(currentItem.value)) return '已安全保存';
  if (isItemDirty(currentItem.value)) return '有未保存修改';
  if (detail.value?.summaryRequired && !summaryDraft.value.trim()) return '待填写实验小结';
  return '尚未作答';
});
const saveStateClass = computed(() => {
  if (!currentItem.value || readonly.value) return 'save-state-banner--readonly';
  if (submitting.value) return 'save-state-banner--saving';
  if (itemAutosaveStates[currentItem.value.id] === 'error') return 'save-state-banner--error';
  if (saving.value || itemAutosaveStates[currentItem.value.id] === 'pending') return 'save-state-banner--saving';
  if (!isItemDirty(currentItem.value) && hasSavedAnswer(currentItem.value)) return 'save-state-banner--saved';
  if (isItemDirty(currentItem.value)) return 'save-state-banner--dirty';
  if (detail.value?.summaryRequired && !summaryDraft.value.trim()) return 'save-state-banner--dirty';
  return 'save-state-banner--idle';
});
const focusStateClass = computed(() => saveStateClass.value.replace('save-state-banner', 'focus-state-pill'));
const footerStatusText = computed(() => {
  if (isNotStarted.value) return '实验尚未开始，暂不可作答。';
  if (isOverdue.value) return '实验已截止，暂不可作答。';
  if (readonly.value) return '当前实验已提交，可查看作答与反馈。';
  if (submitting.value) return '正在提交实验与实验小结…';
  if (saving.value) return '正在保存当前题目…';
  if (currentItem.value && itemAutosaveStates[currentItem.value.id] === 'error') return '当前题目保存失败，请点击“保存当前题项”重试。';
  if (currentItem.value && itemAutosaveStates[currentItem.value.id] === 'saved') return '当前题目已自动保存。';
  if (currentItem.value && (itemAutosaveStates[currentItem.value.id] === 'pending' || isItemDirty(currentItem.value))) return '当前修改将在停止输入后自动保存。';
  if (dirtyItemCount.value > 0) return `还有 ${dirtyItemCount.value} 道题未保存。`;
  if (detail.value?.summaryRequired && !summaryDraft.value.trim()) return '提交前请先填写实验小结。';
  return '当前题目内容已保存，可继续作答或提交。';
});

const updateCurrentDraft = (value: LabAnswerDraft) => {
  if (!currentItem.value) {
    return;
  }

  ensureDraft(currentItem.value);
  patchDraft(currentItem.value.id, value);
};

// 行为采集：作答区外层容器捕获粘贴（D13，不改编辑器内部）；blocked 由服务端按步骤配置回填
const onAnswerPaste = (event: ClipboardEvent) => {
  const stepId = currentItem.value?.id;
  if (stepId == null) {
    return;
  }
  const pastedLength = event.clipboardData?.getData('text').length ?? 0;
  if (pastedLength <= 0) {
    return;
  }
  behaviorCollector.track('PASTE', {
    fieldKind: currentQuestionType.value === 'CODE' ? 'CODE' : 'TEXT',
    pastedLength,
  }, stepId);
};

const syncImagesAfterRemove = () => {
  void persistCurrentItem({ silent: true });
};

const fetchData = async () => {
  loading.value = true;
  try {
    detail.value = await getStudentLabDetail(labId.value);
    syncDraftState();
    syncCurrentItemWithQuery();
  } finally {
    loading.value = false;
  }
};

const syncDraftState = () => {
  Object.keys(itemAutosaveStates).forEach((key) => {
    delete itemAutosaveStates[Number(key)];
  });
  hydrateAllDrafts(labItems.value);
  labItems.value.forEach((item) => {
    itemAutosaveStates[item.id] = 'idle';
  });
  summaryDraft.value = detail.value?.summaryText || '';
};

watch(currentItem, (item) => {
  if (!item || normalizeStudentAnswerQuestionType(item.questionType) !== 'CODE') {
    return;
  }

  const draft = ensureDraft(item);
  currentCodeEditorLanguage.value = draft.kind === 'code'
    ? draft.language || item.editorLanguage || 'JAVA'
    : item.editorLanguage || 'JAVA';
}, { immediate: true });

const clearAutosaveTimer = () => {
  if (autosaveTimer) {
    clearTimeout(autosaveTimer);
    autosaveTimer = null;
  }
};

const queueSave = (task: () => Promise<boolean>) => {
  const nextSave = saveQueue.then(task, task);
  saveQueue = nextSave.catch(() => false);
  return nextSave;
};

const updateItemQuery = (itemId: number, replace = true) => {
  const query = { ...route.query, item: String(itemId) };
  const navigation = {
    path: `/student/labs/${labId.value}/steps`,
    query,
  };
  return replace ? router.replace(navigation) : router.push(navigation);
};

const syncCurrentItemWithQuery = async () => {
  if (!labItems.value.length) {
    currentIndex.value = 0;
    return;
  }

  const targetId = currentItemQueryId.value;
  const targetIndex = targetId === null ? -1 : labItems.value.findIndex((item) => item.id === targetId);

  if (targetIndex >= 0) {
    currentIndex.value = targetIndex;
    return;
  }

  currentIndex.value = 0;
  const fallbackItem = labItems.value[0];
  if (fallbackItem && currentItemQueryId.value !== fallbackItem.id) {
    await updateItemQuery(fallbackItem.id);
  }
};

const selectItem = async (index: number) => {
  const target = labItems.value[index];
  if (!target) {
    return;
  }
  // 切题前先保存当前题；保存失败不切换，避免静默丢失已输入内容
  const saved = await persistCurrentItem({ silent: true });
  if (!saved) {
    ElMessage.error('当前题保存失败，请重试');
    return;
  }
  const previousStepId = currentItem.value?.id ?? null;
  currentIndex.value = index;
  await updateItemQuery(target.id);
  // 行为采集：题项切换成功后上报 STEP_VIEW（dwellMs 由采集器归属上一题）
  if (target.id !== previousStepId) {
    behaviorCollector.setStep(target.id);
  }
};

const draftAnswerOf = (item: StudentLabStepItem) => buildCompatibilityAnswerText(ensureDraft(item));

const savedDraftOf = (item: StudentLabStepItem) => hydrateDraftFromStep(item);

const savedAnswerOf = (item: StudentLabStepItem) => buildCompatibilityAnswerText(savedDraftOf(item));

const hasSavedAnswer = (item: StudentLabStepItem) => isDraftAnswered(savedDraftOf(item));

const isItemDirty = (item: StudentLabStepItem) => {
  return buildAnswerPayloadJson(ensureDraft(item)) !== buildAnswerPayloadJson(savedDraftOf(item));
};

const answerStateLabel = (item: StudentLabStepItem) => {
  if (isItemDirty(item)) return '未保存修改';
  if (hasSavedAnswer(item)) return '已保存';
  if (isDraftAnswered(ensureDraft(item))) return '待保存';
  return '待作答';
};

const persistItem = (item: StudentLabStepItem, options: { silent?: boolean } = {}) => queueSave(async () => {
  if (readonly.value) {
    return true;
  }

  const draft = ensureDraft(item);
  const payload = buildSavePayload(item.id);
  const savedPayloadJson = buildAnswerPayloadJson(savedDraftOf(item));

  if (!isDraftAnswered(draft) && !hasSavedAnswer(item)) {
    itemAutosaveStates[item.id] = 'idle';
    return true;
  }

  if (payload.answerPayloadJson === savedPayloadJson) {
    itemAutosaveStates[item.id] = 'idle';
    return true;
  }

  saving.value = true;
  try {
    const result = await saveStudentLabAnswer(labId.value, item.id, payload);
    item.answerText = payload.answerText;
    item.answerPayloadJson = payload.answerPayloadJson;
    itemAutosaveStates[item.id] = options.silent ? 'saved' : 'idle';
    if (detail.value) {
      detail.value.submissionStatus = result.submissionStatus;
    }
    if (!options.silent) {
      ElMessage.success('当前题项已保存');
    }
    return true;
  } catch {
    itemAutosaveStates[item.id] = 'error';
    // 行为采集：保存失败（自动/手动均采集，silent 区分来源）
    behaviorCollector.track('SAVE_FAILED', { stepId: item.id, silent: !!options.silent }, item.id);
    if (!options.silent) {
      ElMessage.error('保存失败，请稍后重试');
    }
    return false;
  } finally {
    saving.value = false;
  }
});

const persistCurrentItem = (options: { silent?: boolean } = {}) => {
  clearAutosaveTimer();
  if (!currentItem.value) {
    return Promise.resolve(true);
  }
  return persistItem(currentItem.value, options);
};

const scheduleAutosave = () => {
  const item = currentItem.value;
  if (!item || readonly.value || !isItemDirty(item)) {
    return;
  }
  clearAutosaveTimer();
  itemAutosaveStates[item.id] = 'pending';
  autosaveTimer = setTimeout(() => {
    autosaveTimer = null;
    // 定时器绑定创建时刻的题项，切题不会把待保存内容写到新题上
    void persistItem(item, { silent: true });
  }, 1200);
};

const saveCurrent = async () => {
  if (!currentItem.value) {
    return;
  }
  // 空内容也允许保存，用于清空已保存的答案
  await persistCurrentItem();
};

const goPrev = async () => {
  if (currentIndex.value > 0) {
    const saved = await persistCurrentItem({ silent: true });
    if (!saved) {
      ElMessage.error('当前题保存失败，请重试');
      return;
    }
    await selectItem(currentIndex.value - 1);
  }
};

const goNext = async () => {
  if (currentIndex.value < labItems.value.length - 1) {
    const saved = await persistCurrentItem({ silent: true });
    if (!saved) {
      ElMessage.error('当前题保存失败，请重试');
      return;
    }
    await selectItem(currentIndex.value + 1);
  }
};

/**
 * 保存全部 dirty 题项：返回第一个失败题目的下标（从 0 开始），全部成功返回 -1。
 * 提交与路由离开共用同一遍历规则，保证「保存成功才继续」。
 */
const persistDirtyItems = async () => {
  clearAutosaveTimer();
  for (let index = 0; index < labItems.value.length; index++) {
    const item = labItems.value[index];
    if (!isItemDirty(item)) {
      continue;
    }
    const saved = await persistItem(item, { silent: true });
    if (!saved) {
      return index;
    }
  }
  return -1;
};

const submitAll = async () => {
  if (!detail.value) {
    return;
  }
  // 行为采集：提交点击（无论后续是否被拦截）
  behaviorCollector.track('SUBMIT_CLICK', {});
  // 先保存全部 dirty 题项，再走「至少一题已保存」等校验，避免首次作答被提前拦截
  const failedIndex = await persistDirtyItems();
  if (failedIndex >= 0) {
    behaviorCollector.track('SUBMIT_BLOCKED', { reason: 'autosave-failed' });
    ElMessage.error(`第 ${failedIndex + 1} 题保存失败，请重试`);
    return;
  }
  if (!labItems.value.some((item) => hasSavedAnswer(item))) {
    behaviorCollector.track('SUBMIT_BLOCKED', { reason: 'no-saved-answer' });
    ElMessage.warning('请至少先保存一道题目后再提交');
    return;
  }
  if (detail.value.summaryRequired && !summaryDraft.value.trim()) {
    behaviorCollector.track('SUBMIT_BLOCKED', { reason: 'summary-required' });
    ElMessage.warning('当前实验要求填写实验小结后再提交');
    return;
  }

  await ElMessageBox.confirm('提交后将不能继续修改题项答案与实验小结，是否继续？', '提交确认', { type: 'warning' });

  submitting.value = true;
  try {
    await submitStudentLab(labId.value, {
      summaryText: summaryDraft.value.trim(),
    });
    ElMessage.success('实验提交成功');
    // 行为采集：提交成功收尾（leave 内已触发 flush），随后停采
    behaviorCollector.leave('submitted');
    behaviorCollector.stop();
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const submissionLabel = (status?: LabReportStatus | null) => {
  if (status === SUBMISSION_STATUS.SAVED) return '已保存草稿';
  if (status === SUBMISSION_STATUS.SUBMITTED) return '已提交';
  if (status === SUBMISSION_STATUS.GRADED) return '已批改';
  return '待开始';
};

const normalizedQuestionType = (type?: LabQuestionType) => {
  if (type === 'SHORT_ANSWER') return 'SHORT_ANSWER';
  if (type === 'TEXT') return 'TEXT';
  if (type === 'FILL_BLANK') return 'FILL_BLANK';
  if (type === 'CODE') return 'CODE';
  if (type === 'SINGLE_CHOICE') return 'SINGLE_CHOICE';
  if (type === 'MULTIPLE_CHOICE') return 'MULTIPLE_CHOICE';
  if (type === 'TRUE_FALSE') return 'TRUE_FALSE';
  return 'TEXT';
};

const questionTypeLabel = (type?: LabQuestionType) => {
  if (type === 'SHORT_ANSWER') return '简答题';
  if (type === 'TEXT') return '文本题';
  if (type === 'FILL_BLANK') return '填空题';
  if (type === 'CODE') return '代码题';
  if (type === 'SINGLE_CHOICE') return '单选题';
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  return '作答题';
};

const answerHint = (type?: LabQuestionType) => {
  if (type === 'SHORT_ANSWER') return '请输入简答内容。';
  if (type === 'TEXT') return '请输入本题作答内容。';
  if (type === 'FILL_BLANK') return '请按题目顺序填写答案。';
  if (type === 'CODE') return '请输入代码内容。';
  if (type === 'SINGLE_CHOICE') return '请输入单选答案。';
  if (type === 'MULTIPLE_CHOICE') return '请输入多选答案，多个选项可用逗号分隔。';
  if (type === 'TRUE_FALSE') return '请选择正确或错误。';
  return '请输入本题作答内容。';
};

const isChoiceWithoutOptions = (type?: LabQuestionType) => {
  return normalizedQuestionType(type) === 'SINGLE_CHOICE' || normalizedQuestionType(type) === 'MULTIPLE_CHOICE';
};

const itemFeedback = (item: StudentLabStepItem) => {
  const parts: string[] = [];
  if (item.score !== null && item.score !== undefined) {
    parts.push(`本题得分：${item.score}/${item.stepScore}`);
  }
  if (item.teacherComment) {
    parts.push(`教师评语：${item.teacherComment}`);
  }
  return parts.join('；') || '暂无教师反馈';
};

watch(() => route.query.item, async () => {
  // 失败回填 URL 会再次触发本 watch，已与当前题一致时直接返回，避免重复保存与二次弹错
  if (!currentItem.value || currentItemQueryId.value === currentItem.value.id) {
    return;
  }
  const saved = await persistCurrentItem({ silent: true });
  if (!saved) {
    ElMessage.error('当前题保存失败，请重试');
    await updateItemQuery(currentItem.value.id);
    return;
  }
  await syncCurrentItemWithQuery();
});

watch(labItems, () => {
  void syncCurrentItemWithQuery();
});

watch(() => (currentItem.value ? buildAnswerPayloadJson(ensureDraft(currentItem.value)) : ''), () => {
  if (!currentItem.value || readonly.value) {
    return;
  }
  if (itemAutosaveStates[currentItem.value.id] === 'saved') {
    itemAutosaveStates[currentItem.value.id] = 'idle';
  }
  scheduleAutosave();
});

/**
 * 路由离开兜底：先保存全部 dirty 题项，失败时二次确认是否离开。
 * 离开页面不是提交意图，因此不记录 SUBMIT_BLOCKED 埋点。
 */
onBeforeRouteLeave(async () => {
  if (readonly.value) {
    return true;
  }
  const failedIndex = await persistDirtyItems();
  if (failedIndex < 0) {
    return true;
  }
  try {
    await ElMessageBox.confirm('有未保存答案，仍要离开？', '离开确认', { type: 'warning' });
    return true;
  } catch {
    return false;
  }
});

/**
 * 关闭标签页兜底：存在 dirty 题项时触发浏览器原生离开确认。
 * 浏览器不保证异步请求发出，因此这里不发起草稿保存。
 */
const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (dirtyItemCount.value > 0) {
    event.preventDefault();
    event.returnValue = '';
  }
};

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload);
  clockTimer = setInterval(() => {
    nowMs.value = Date.now();
  }, 1000);
  void fetchData().then(() => {
    // 行为采集：仅在可编辑作答会话内激活；已提交/已批改的只读查看不采集
    if (readonly.value) {
      return;
    }
    behaviorCollector.start({ labId: labId.value });
    behaviorCollector.track('LAB_ENTER', {});
    // 首题（含深链 query 指定的题项）产生首条 STEP_VIEW，无 fromStepId
    if (currentItem.value) {
      behaviorCollector.setStep(currentItem.value.id);
    }
  });
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload);
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = undefined;
  }
  clearAutosaveTimer();
  // 行为采集：路由离开收尾并停采；直接关浏览器由采集器 beforeunload 钩子兜底（D17②）
  behaviorCollector.leave('route');
  behaviorCollector.stop();
});

  return { SUBMISSION_STATUS, computed, onBeforeUnmount, onMounted, reactive, ref, watch, useRoute, useRouter, onBeforeRouteLeave, ElMessage, ElMessageBox, QuestionAnswerShell, ChoiceAnswerEditor, FillBlankInlineEditor, TextAnswerEditor, CodeLabAnswerEditor, getStudentLabDetail, saveStudentLabAnswer, submitStudentLab, hydrateDraftFromStep, buildAnswerPayloadJson, buildCompatibilityAnswerText, isDraftAnswered, normalizeStudentAnswerQuestionType, useLabAnswerDrafts, behaviorCollector, route, router, loading, saving, submitting, detail, currentIndex, summaryDraft, itemAutosaveStates, autosaveTimer, saveQueue, clockTimer, nowMs, isNotStarted, isOverdue, labId, currentItemQueryId, labItems, currentItem, currentDraft, currentQuestionType, currentCodeEditorLanguage, currentItemCodeLanguage, readonly, canSubmit, submitHint, answeredCount, dirtyItemCount, progressPercent, progressText, submissionTagType, currentSaveStateLabel, saveStateClass, focusStateClass, footerStatusText, updateCurrentDraft, onAnswerPaste, syncImagesAfterRemove, fetchData, syncDraftState, clearAutosaveTimer, queueSave, updateItemQuery, syncCurrentItemWithQuery, selectItem, draftAnswerOf, savedDraftOf, savedAnswerOf, hasSavedAnswer, isItemDirty, answerStateLabel, persistItem, persistCurrentItem, scheduleAutosave, saveCurrent, goPrev, goNext, persistDirtyItems, submitAll, submissionLabel, normalizedQuestionType, questionTypeLabel, answerHint, isChoiceWithoutOptions, itemFeedback, handleBeforeUnload, ensureDraft, getDraft, patchDraft, buildSavePayload, hydrateAllDrafts };
}

export type StudentLabStepsState = ReturnType<typeof useStudentLabSteps>;
export const StudentLabStepsKey: InjectionKey<StudentLabStepsState> = Symbol('StudentLabStepsState');
