import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { copyLabStepToBank, createTeacherLabStep, deleteTeacherLabStep, listTeacherLabs, listTeacherLabSteps, updateTeacherLabStep } from '@/api/labs';
import { listQuestions } from '@/api/questions';
import type { LabItem, LabQuestionType, LabStepItem } from '@/types/lab';
import type { ConfiguredQuestionItem } from '@/types/question-config';
import QuestionPickerDialog from '@/components/question-config/QuestionPickerDialog.vue';
import FillBlankEditor from '@/components/typed-editor/FillBlankEditor.vue';
import ObjectiveEditor from '@/components/typed-editor/ObjectiveEditor.vue';
import SubjectiveEditor from '@/components/typed-editor/SubjectiveEditor.vue';
import TypedEditorShell from '@/components/typed-editor/TypedEditorShell.vue';
import { buildLabStepPayloadFromDraft, validateLabStepDraft } from '@/components/typed-editor/adapters/labStepAdapter';
import { useTypedEditor } from '@/components/typed-editor/useTypedEditor';
import { hydrateTypedEditorFromQuestion } from '@/components/typed-editor/hydrators/fromQuestion';
import {
  createEmptyLabStepEditorState,
  getLabQuestionTypeLabel,
  hydrateTypedEditorFromLabStep,
  mapTypedKindToLabQuestionType,
  normalizeLabQuestionType,
  type HydratedLabStepEditorState,
} from '@/components/typed-editor/hydrators/fromLabStep';
import type { InjectionKey } from 'vue';

export function useLabSteps() {
interface FillBlankEditorExpose {
  insertTokenAtCursor: () => void;
  focusAt: (position: number) => void;
}

interface ElTextareaLike {
  textarea?: HTMLTextAreaElement;
}

const route = useRoute();
const router = useRouter();
const labId = Number(route.params.id);

const loading = ref(false);
const submitting = ref(false);
const rows = ref<LabStepItem[]>([]);
const labs = ref<LabItem[]>([]);
const activeExistingStepId = ref<number | null>(null);
const fillBlankEditorRef = ref<FillBlankEditorExpose | null>(null);
const fillBlankEditorPromptRef = ref<ElTextareaLike | null>(null);

const typedEditor = useTypedEditor({
  validationContext: {
    promptLabel: '题项说明 / 题干',
    fillAnswerLabel: index => `请填写空 ${index} 的正确答案`,
    subjectiveRequiredLabel: {
      SHORT: '请填写参考答案或评分要点',
      CODE: '请填写代码题参考实现或评分要点',
    },
  },
});

const form = reactive({
  stepNo: 1,
  title: '',
  questionType: 'SHORT_ANSWER',
  stepScore: 10,
  allowPaste: true,
});

const subjectiveKeywords = ref<string[]>(['']);
const subjectiveSettings = reactive({
  minLength: 0,
  commentTemplate: '',
});
const fillSettings = reactive({
  ignoreCase: true,
});
const codeSettings = reactive({
  language: 'JAVA',
});

const pickerVisible = ref(false);
const copyDialogVisible = ref(false);
const copying = ref(false);
const copyCode = ref('');
const copyPreview = ref<{
  stepId: number;
  stepNo: number;
  typeLabel: string;
  stem: string;
  options: string[];
  answer: string;
  score: number;
} | null>(null);

const labSummaryTitle = computed(() => labs.value.find(item => item.id === labId)?.title?.trim() || `实验 #${labId}`);
const sortedRows = computed(() => [...rows.value].sort((left, right) => left.stepNo - right.stepNo));
const totalScore = computed(() => rows.value.reduce((sum, row) => sum + (Number(row.stepScore) || 0), 0));
const currentQuestionTypeLabel = computed(() => getLabQuestionTypeLabel(normalizeLabQuestionType(form.questionType)));
const allowPasteLabel = computed(() => (form.allowPaste ? '允许粘贴' : '限制粘贴'));
const typedValidationMessages = computed(() => validateLabStepDraft(form.questionType, typedEditor.draft, form.title));
const contentLength = computed(() => typedEditor.draft.prompt.trim().length);
const isReadyToSubmit = computed(() => typedValidationMessages.value.length === 0);
const completionSummary = computed(() => (isReadyToSubmit.value ? '可保存' : '待完善'));
const currentActionLabel = computed(() => (activeExistingStepId.value ? '保存当前题项' : '创建当前题项'));
const activeStepHeading = computed(() => activeExistingStepId.value ? `题项 ${form.stepNo} · ${form.title.trim() || '未命名题项'}` : `新增题项草稿 · 序号 ${form.stepNo}`);
const completionHint = computed(() => {
  if (isReadyToSubmit.value) {
    return activeExistingStepId.value ? '当前题项结构已完整，保存后会保留当前选中状态。' : '当前草稿已达到创建条件，可直接保存为新的实验题项。';
  }
  return typedValidationMessages.value[0] ?? '请继续补齐当前题项的必填信息。';
});

const summarizeText = (text: string, limit: number) => {
  const normalized = text.trim();
  return normalized.length > limit ? `${normalized.slice(0, limit)}…` : normalized;
};

/** 把已水合的编辑器状态写入表单与侧栏约束 */
const applyHydratedState = (hydrated: HydratedLabStepEditorState) => {
  typedEditor.reset(hydrated.draft.kind);
  typedEditor.draft.prompt = hydrated.draft.prompt;
  typedEditor.draft.objectiveOptions = hydrated.draft.objectiveOptions.map(option => ({ ...option }));
  typedEditor.draft.selectedObjectiveOptionIds = [...hydrated.draft.selectedObjectiveOptionIds];
  typedEditor.draft.judgeAnswer = hydrated.draft.judgeAnswer;
  typedEditor.draft.fillBlanks = hydrated.draft.fillBlanks.map(blank => ({ ...blank }));
  typedEditor.draft.subjectiveAnswer = hydrated.draft.subjectiveAnswer;
  subjectiveKeywords.value = hydrated.draft.kind === 'SHORT'
    ? (hydrated.draft.subjectiveAnswer.trim() ? hydrated.draft.subjectiveAnswer.split(/\r?\n/).map(item => item.trim()).filter(Boolean) : [''])
    : [''];
  subjectiveSettings.minLength = hydrated.subjectiveSettings.minLength;
  subjectiveSettings.commentTemplate = hydrated.subjectiveSettings.commentTemplate;
  fillSettings.ignoreCase = hydrated.fillSettings.ignoreCase;
  codeSettings.language = hydrated.codeSettings.language;
};

/** 按题项行或当前题型重建编辑器状态 */
const applyHydratedRow = (row?: LabStepItem) => {
  applyHydratedState(row ? hydrateTypedEditorFromLabStep(row) : createEmptyLabStepEditorState(form.questionType));
};

const resetForm = () => {
  activeExistingStepId.value = null;
  form.stepNo = rows.value.length + 1;
  form.title = '';
  form.questionType = 'SHORT_ANSWER';
  form.stepScore = 10;
  form.allowPaste = true;
  applyHydratedRow();
};

const populateFormFromRow = (row: LabStepItem) => {
  activeExistingStepId.value = row.id;
  form.stepNo = row.stepNo;
  form.title = row.title;
  form.questionType = normalizeLabQuestionType(row.questionType);
  form.stepScore = row.stepScore;
  form.allowPaste = row.allowPaste;
  applyHydratedRow(row);
};

const fetchLabContext = async () => {
  try {
    labs.value = await listTeacherLabs();
  } catch {
    labs.value = [];
  }
};

const fetchData = async (preferredStepId?: number | null) => {
  loading.value = true;
  try {
    const result = await listTeacherLabSteps(labId);
    rows.value = result;

    if (!result.length) {
      resetForm();
      return;
    }

    const targetId = preferredStepId ?? activeExistingStepId.value ?? result[0]?.id ?? null;
    const targetRow = result.find(item => item.id === targetId) ?? [...result].sort((left, right) => left.stepNo - right.stepNo)[0];
    if (targetRow) {
      populateFormFromRow(targetRow);
    }
  } finally {
    loading.value = false;
  }
};

const openCreatePanel = () => {
  resetForm();
};

const selectStep = (stepId: number) => {
  const row = rows.value.find(item => item.id === stepId);
  if (row) {
    populateFormFromRow(row);
  }
};

const handleQuestionTypeChange = () => {
  applyHydratedRow();
};

const updateObjectiveOptionContent = ({ id, content }: { id: string; content: string }) => {
  typedEditor.draft.objectiveOptions = typedEditor.draft.objectiveOptions.map(option => option.id === id ? { ...option, content } : option);
};

const updateBlankAnswer = ({ index, answersText }: { index: number; answersText: string }) => {
  typedEditor.draft.fillBlanks = typedEditor.draft.fillBlanks.map(blank => blank.index === index ? { ...blank, answersText } : blank);
};

const removeBlank = (index: number) => {
  typedEditor.removeBlank(index);
};

const triggerBlankInsert = () => {
  const textarea = fillBlankEditorPromptRef.value?.textarea;
  if (!textarea) {
    fillBlankEditorRef.value?.insertTokenAtCursor();
    return;
  }

  insertBlankToken({
    start: textarea.selectionStart ?? typedEditor.draft.prompt.length,
    end: textarea.selectionEnd ?? typedEditor.draft.prompt.length,
  });
};

const insertBlankToken = async (selection?: { start: number; end: number }) => {
  const inserted = typedEditor.insertBlankToken(selection);
  if (inserted) {
    await nextTick();
    const textarea = fillBlankEditorPromptRef.value?.textarea;
    textarea?.focus();
    textarea?.setSelectionRange(inserted.nextCursor, inserted.nextCursor);
  }
};

const addKeyword = () => {
  subjectiveKeywords.value.push('');
};

const removeKeyword = (index: number) => {
  subjectiveKeywords.value.splice(index, 1);
  if (!subjectiveKeywords.value.length) {
    subjectiveKeywords.value = [''];
  }
};

const syncKeywordsFromSubjectiveAnswer = (value: string) => {
  if (typedEditor.draft.kind !== 'SHORT') {
    return;
  }

  const nextKeywords = value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean);

  subjectiveKeywords.value = nextKeywords.length ? nextKeywords : [''];
};

const updateSubjectiveAnswer = (value: string) => {
  typedEditor.draft.subjectiveAnswer = value;
  syncKeywordsFromSubjectiveAnswer(value);
};

const syncSubjectiveAnswers = () => {
  if (typedEditor.draft.kind !== 'SHORT') {
    return;
  }
  typedEditor.draft.subjectiveAnswer = subjectiveKeywords.value.map(item => item.trim()).filter(Boolean).join('\n');
};

watch(
  subjectiveKeywords,
  () => {
    if (typedEditor.draft.kind === 'SHORT') {
      syncSubjectiveAnswers();
    }
  },
  { deep: true },
);

const buildPayload = () => {
  syncSubjectiveAnswers();
  return buildLabStepPayloadFromDraft({
    stepNo: form.stepNo,
    title: form.title.trim(),
    questionType: form.questionType,
    stepScore: form.stepScore,
    allowPaste: form.allowPaste,
  }, typedEditor.draft, {
    subjective: { ...subjectiveSettings },
    fill: { ...fillSettings },
    code: { ...codeSettings },
  });
};

const isStepConfigured = (row: LabStepItem) => {
  const hydrated = hydrateTypedEditorFromLabStep(row);
  return validateLabStepDraft(row.questionType, hydrated.draft, row.title).length === 0;
};

const submitForm = async () => {
  syncSubjectiveAnswers();
  if (typedValidationMessages.value.length) {
    ElMessage.warning(typedValidationMessages.value[0]);
    return;
  }

  submitting.value = true;
  try {
    const wasEditing = activeExistingStepId.value !== null;
    const payload = buildPayload();
    const savedStep = activeExistingStepId.value
      ? await updateTeacherLabStep(activeExistingStepId.value, payload)
      : await createTeacherLabStep(labId, payload);

    await fetchData(savedStep.id);
    ElMessage.success(wasEditing ? '题项保存成功' : '题项创建成功');
  } finally {
    submitting.value = false;
  }
};

/** 生成复制预览：基于已保存题项内容，避免把未保存的编辑带入题库 */
const buildCopyPreview = (row: LabStepItem) => {
  const hydrated = hydrateTypedEditorFromLabStep(row);
  const draft = hydrated.draft;
  const typeLabel = getLabQuestionTypeLabel(normalizeLabQuestionType(row.questionType));

  const optionLines = draft.kind === 'JUDGE'
    ? ['T. 正确', 'F. 错误']
    : draft.objectiveOptions
        .filter(option => option.key.trim() && option.content.trim())
        .map(option => `${option.key}. ${option.content.trim()}`);

  let answerLines: string[] = [];
  if (draft.kind === 'SINGLE' || draft.kind === 'MULTI') {
    answerLines = draft.objectiveOptions
      .filter(option => draft.selectedObjectiveOptionIds.includes(option.id))
      .map(option => option.key.trim())
      .filter(Boolean);
  } else if (draft.kind === 'JUDGE') {
    answerLines = draft.judgeAnswer ? [draft.judgeAnswer === 'T' ? '正确' : '错误'] : [];
  } else if (draft.kind === 'FILL') {
    answerLines = draft.fillBlanks
      .filter(blank => blank.answersText.trim())
      .map(blank => `【填空${blank.index}】${blank.answersText.split(/\r?\n/).map(item => item.trim()).filter(Boolean).join(' / ')}`);
  } else {
    answerLines = draft.subjectiveAnswer.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
  }

  return {
    stepId: row.id,
    stepNo: row.stepNo,
    typeLabel,
    stem: row.content.trim(),
    options: optionLines,
    answer: answerLines.length ? answerLines.join('；') : '未填写',
    score: row.stepScore,
  };
};

const openCopyDialog = (row: LabStepItem) => {
  copyPreview.value = buildCopyPreview(row);
  copyCode.value = `LAB${labId}S${row.stepNo}`;
  copyDialogVisible.value = true;
};

const confirmCopyToBank = async () => {
  if (!copyPreview.value) {
    return;
  }

  copying.value = true;
  try {
    const result = await copyLabStepToBank(copyPreview.value.stepId, { code: copyCode.value.trim() || null });
    if (result.created) {
      ElMessage.success(`已创建题库题 ${result.question.code}`);
    } else {
      ElMessage.info(`该题项已复制过，返回已有题目 ${result.question.code}`);
    }
    if (result.duplicateQuestionId) {
      ElMessage.warning(`题库已有相同题目（ID ${result.duplicateQuestionId}），请按需合并维护`);
    }
    copyDialogVisible.value = false;
  } finally {
    copying.value = false;
  }
};

const openQuestionBankPicker = () => {
  pickerVisible.value = true;
};

/**
 * 从题库选题预填实验题项：
 * 题库仅提供题干/选项/答案等内容，题项标题、序号与分值由本页维护，
 * 分值取选题弹窗按题型给出的默认分（题库已无分值），保存前教师可在本页调整。
 */
const applyPickedQuestion = async (items: ConfiguredQuestionItem[]) => {
  const item = items[0];
  if (!item?.questionBankId) {
    return;
  }

  try {
    const bank = await listQuestions();
    const question = bank.find(row => row.id === item.questionBankId);
    if (!question) {
      ElMessage.warning('题库题目不存在或已被删除');
      return;
    }

    const hydrated = hydrateTypedEditorFromQuestion(question);
    const nextQuestionType = mapTypedKindToLabQuestionType(hydrated.draft.kind);
    form.questionType = nextQuestionType;
    applyHydratedState({
      draft: hydrated.draft,
      subjectiveSettings: { minLength: 0, commentTemplate: '' },
      fillSettings: { ignoreCase: true },
      codeSettings: { language: 'JAVA' },
    });
    form.stepScore = item.score;
    ElMessage.success('已带入题库题目，保存后生成新的实验题项');
  } catch {
    // 请求错误已由统一拦截器提示
  }
};

const remove = async (stepId: number) => {
  await ElMessageBox.confirm('删除该题项后不可恢复，是否继续？', '删除确认', { type: 'warning' });
  await deleteTeacherLabStep(stepId);
  ElMessage.success('题项删除成功');

  const remaining = rows.value.filter(row => row.id !== stepId);
  const nextPreferredId = remaining.find(row => row.id === activeExistingStepId.value)?.id ?? remaining[0]?.id ?? null;
  await fetchData(nextPreferredId);
};

onMounted(async () => {
  await Promise.all([fetchLabContext(), fetchData()]);
});

  return { computed, nextTick, onMounted, reactive, ref, watch, useRoute, useRouter, ElMessage, ElMessageBox, copyLabStepToBank, createTeacherLabStep, deleteTeacherLabStep, listTeacherLabs, listTeacherLabSteps, updateTeacherLabStep, listQuestions, QuestionPickerDialog, FillBlankEditor, ObjectiveEditor, SubjectiveEditor, TypedEditorShell, buildLabStepPayloadFromDraft, validateLabStepDraft, useTypedEditor, hydrateTypedEditorFromQuestion, createEmptyLabStepEditorState, getLabQuestionTypeLabel, hydrateTypedEditorFromLabStep, mapTypedKindToLabQuestionType, normalizeLabQuestionType, route, router, labId, loading, submitting, rows, labs, activeExistingStepId, fillBlankEditorRef, fillBlankEditorPromptRef, typedEditor, form, subjectiveKeywords, subjectiveSettings, fillSettings, codeSettings, pickerVisible, copyDialogVisible, copying, copyCode, copyPreview, labSummaryTitle, sortedRows, totalScore, currentQuestionTypeLabel, allowPasteLabel, typedValidationMessages, contentLength, isReadyToSubmit, completionSummary, currentActionLabel, activeStepHeading, completionHint, summarizeText, applyHydratedState, applyHydratedRow, resetForm, populateFormFromRow, fetchLabContext, fetchData, openCreatePanel, selectStep, handleQuestionTypeChange, updateObjectiveOptionContent, updateBlankAnswer, removeBlank, triggerBlankInsert, insertBlankToken, addKeyword, removeKeyword, syncKeywordsFromSubjectiveAnswer, updateSubjectiveAnswer, syncSubjectiveAnswers, buildPayload, isStepConfigured, submitForm, buildCopyPreview, openCopyDialog, confirmCopyToBank, openQuestionBankPicker, applyPickedQuestion, remove };
}

export type LabStepsState = ReturnType<typeof useLabSteps>;
export const LabStepsKey: InjectionKey<LabStepsState> = Symbol('LabStepsState');
