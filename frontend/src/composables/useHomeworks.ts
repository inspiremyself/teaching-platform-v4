import { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE } from '@/types/enums';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listClasses } from '@/api/classes';
import {
  addHomeworkQuestionFromBank,
  addInlineHomeworkQuestion,
  changeHomeworkStatus,
  copyHomeworkQuestionToBank,
  createHomework,
  deleteHomeworkQuestion,
  getTeacherHomeworkDetail,
  listTeacherHomeworks,
  releaseHomeworkScores,
  updateHomework,
  updateHomeworkQuestion,
} from '@/api/homeworks';
import QuestionConfigList from '@/components/question-config/QuestionConfigList.vue';
import QuestionEditorDialog, {
  type QuestionEditorDialogSubmitPayload,
  type QuestionEditorDialogValue,
} from '@/components/question-config/QuestionEditorDialog.vue';
import QuestionPickerDialog from '@/components/question-config/QuestionPickerDialog.vue';
import type { TeachingClass } from '@/types/class';
import type { ConfiguredQuestionItem } from '@/types/question-config';
import type {
  AddHomeworkQuestionFromBankRequest,
  CopyQuestionToBankResult,
  CreateHomeworkRequest,
  HomeworkListItem,
  HomeworkStatus,
  SaveHomeworkQuestionRequest,
} from '@/types/homework';
import { ApiError } from '@/utils/request';
import { buildQuestionPayloadFromDraft, normalizeQuestionType } from '@/components/typed-editor/adapters/questionAdapter';
import { parseQuestionAnswerArray } from '@/components/typed-editor/hydrators/fromQuestion';
import { defaultScoreByType } from '@/constants/questionScoreDefaults';
import type { InjectionKey } from 'vue';

export function useHomeworks() {
const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const questionSubmitting = ref(false);
const detailLoading = ref(false);
const dialogVisible = ref(false);
const pickerVisible = ref(false);
const questionDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const originalStatus = ref<HomeworkStatus>(ACTIVITY_STATUS.DRAFT);
const rows = ref<HomeworkListItem[]>([]);
const classOptions = ref<TeachingClass[]>([]);
const configuredQuestions = ref<ConfiguredQuestionItem[]>([]);
/** 已落库的作业题目 id：用于计算删除集合与增量提交 */
const originalQuestionIds = ref<number[]>([]);
/** 作业保存成功后是否把本次内联题复制到题库 */
const saveToQuestionBank = ref(false);
const searchQuery = ref('');
const statusFilter = ref<HomeworkStatus | ''>('');
const classFilter = ref('0');
const dueFilter = ref<'upcoming' | 'overdue' | 'undated' | ''>('');
const questionEditorMode = ref<'BANK' | 'INLINE'>('INLINE');
const activeConfiguredQuestionLocalId = ref<string | null>(null);

const form = reactive<CreateHomeworkRequest>({
  title: '',
  description: '',
  classId: 0,
  status: ACTIVITY_STATUS.DRAFT,
  scoreVisibilityMode: SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM,
  attachmentPath: '',
  startAt: null,
  dueAt: null,
});

/** 编辑态已存在的提交数量：>0 时锁定班级与“回退草稿”，与后端状态机保持一致。 */
const editingSubmissionCount = ref(0);

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  const now = Date.now();

  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.title.toLowerCase().includes(keyword) ||
      row.className?.toLowerCase().includes(keyword) ||
      row.description?.toLowerCase().includes(keyword);

    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    const matchesClass = classFilter.value === '0' || String(row.classId) === classFilter.value;

    const dueTime = row.dueAt ? new Date(row.dueAt).getTime() : NaN;
    const matchesDue =
      !dueFilter.value ||
      (dueFilter.value === 'undated' && !row.dueAt) ||
      (dueFilter.value === 'upcoming' && row.dueAt && !Number.isNaN(dueTime) && dueTime >= now) ||
      (dueFilter.value === 'overdue' && row.dueAt && !Number.isNaN(dueTime) && dueTime < now);

    return matchesKeyword && matchesStatus && matchesClass && matchesDue;
  });
});

const publishedCount = computed(() => filteredRows.value.filter(row => row.status === ACTIVITY_STATUS.PUBLISHED).length);
const draftCount = computed(() => filteredRows.value.filter(row => row.status === ACTIVITY_STATUS.DRAFT).length);
const totalSubmissionCount = computed(() => filteredRows.value.reduce((sum, row) => sum + (row.submissionCount ?? 0), 0));

const activeStatusLabel = computed(() => {
  if (!statusFilter.value) return '全部状态';
  return statusLabel(statusFilter.value);
});

const classFilterLabel = computed(() => {
  if (classFilter.value === '0') return '全部班级';
  return classOptions.value.find(item => String(item.id) === classFilter.value)?.name || '当前班级';
});

const currentConfiguredQuestion = computed(
  () => configuredQuestions.value.find(item => item.localId === activeConfiguredQuestionLocalId.value) ?? null,
);

/** 已发布/已关闭：禁止新增、编辑、删除与改分 */
const questionContentLocked = computed(() => form.status === ACTIVITY_STATUS.PUBLISHED || form.status === ACTIVITY_STATUS.CLOSED);

/** 已关闭：连排序也禁止 */
const questionReorderLocked = computed(() => form.status === ACTIVITY_STATUS.CLOSED);

const questionLockHint = computed(() => {
  if (form.status === ACTIVITY_STATUS.PUBLISHED) return '已发布作业仅支持调整排序；新增、编辑、删除与改分均已禁用。';
  if (form.status === ACTIVITY_STATUS.CLOSED) return '已关闭作业不可再调整题目。';
  return '';
});

const currentEditorQuestion = computed<QuestionEditorDialogValue | null>(() => {
  const current = questionDialogVisible.value ? currentConfiguredQuestion.value : null;
  if (!current) {
    return null;
  }

  // 编辑器要求 analysisText 为字符串，历史快照可能为 null
  return { ...current, analysisText: current.analysisText ?? '' };
});

const questionDialogDescription = computed(() =>
  questionEditorMode.value === 'BANK'
    ? '修改仅作用于本作业快照，不同步题库；题库资产请到题库管理台维护。'
    : '使用共享题目编辑器维护内联题的题干、选项、答案与解析，保存后写入当前作业题目清单。',
);

const questionDialogSubmitText = computed(() => (questionEditorMode.value === 'BANK' ? '保存作业题目' : '保存内联题'));

const fetchHomeworks = async () => {
  loading.value = true;
  try {
    rows.value = await listTeacherHomeworks();
  } finally {
    loading.value = false;
  }
};

const fetchClasses = async () => {
  const response = await listClasses();
  classOptions.value = response;
};

const resetForm = () => {
  form.title = '';
  form.description = '';
  form.classId = classOptions.value[0]?.id ?? 0;
  form.status = ACTIVITY_STATUS.DRAFT;
  form.attachmentPath = '';
  form.startAt = null;
  form.dueAt = null;
  form.scoreVisibilityMode = SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM;
  editingSubmissionCount.value = 0;
  editingId.value = null;
  originalStatus.value = ACTIVITY_STATUS.DRAFT;
  configuredQuestions.value = [];
  originalQuestionIds.value = [];
  saveToQuestionBank.value = false;
  activeConfiguredQuestionLocalId.value = null;
};

const resetFilters = () => {
  searchQuery.value = '';
  statusFilter.value = '';
  classFilter.value = '0';
  dueFilter.value = '';
};

const buildPayload = (): CreateHomeworkRequest => ({
  title: form.title.trim(),
  description: form.description.trim(),
  classId: form.classId,
  status: form.status,
  scoreVisibilityMode: form.scoreVisibilityMode ?? null,
  attachmentPath: form.attachmentPath?.trim() || null,
  startAt: form.startAt || null,
  dueAt: form.dueAt || null,
});

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = async (row: HomeworkListItem) => {
  // 先拉取详情再打开弹窗，避免加载失败时教师面对空题目清单
  detailLoading.value = true;
  try {
    const detail = await getTeacherHomeworkDetail(row.id);
    editingId.value = row.id;
    editingSubmissionCount.value = row.submissionCount ?? 0;
    originalStatus.value = detail.status;
    form.title = detail.title;
    form.description = detail.description;
    form.classId = detail.classId;
    form.status = detail.status;
    form.attachmentPath = detail.attachmentPath || '';
    form.startAt = detail.startAt || null;
    form.dueAt = detail.dueAt || null;
    form.scoreVisibilityMode = detail.scoreVisibilityMode || SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM;
    configuredQuestions.value = detail.questions.map(question => ({
      localId: `hwq-${question.id}`,
      homeworkQuestionId: question.id,
      questionBankId: question.questionId,
      sourceType: question.questionId != null ? 'BANK' : 'INLINE',
      questionType: question.type,
      stem: question.stem,
      sortOrder: question.sortOrder,
      score: question.score,
      options: question.options.map(option => ({ key: option.key, label: option.content })),
      optionsJson: question.optionsJson,
      answerJson: question.answerJson,
      analysisText: question.analysisText,
      scoringConfigJson: question.scoringConfigJson,
    }));
    originalQuestionIds.value = detail.questions.map(question => question.id);
    saveToQuestionBank.value = false;
    activeConfiguredQuestionLocalId.value = null;
    dialogVisible.value = true;
  } catch {
    ElMessage.error('作业详情加载失败，请重试');
  } finally {
    detailLoading.value = false;
  }
};

const resequenceConfiguredQuestions = (items: ConfiguredQuestionItem[]) =>
  items.map((item, index) => ({
    ...item,
    sortOrder: index + 1,
  }));

/** 把选择题 JSON 归一为配置清单使用的 {key,label} 结构 */
const mapOptionsForList = (optionsJson?: string | null) =>
  parseQuestionAnswerArray(optionsJson ?? null).map((option, index) => {
    if (option && typeof option === 'object') {
      const record = option as Record<string, unknown>;
      return {
        key: String(record.key ?? String.fromCharCode(65 + index)),
        label: String(record.content ?? record.label ?? record.text ?? ''),
      };
    }

    return {
      key: String.fromCharCode(65 + index),
      label: String(option ?? ''),
    };
  });

const buildConfiguredInlineQuestion = (
  editorPayload: QuestionEditorDialogSubmitPayload,
  current?: ConfiguredQuestionItem | null,
): ConfiguredQuestionItem => {
  const questionPayload = buildQuestionPayloadFromDraft(editorPayload.draft, {
    code: editorPayload.code,
    type: editorPayload.type,
    difficulty: editorPayload.difficulty,
    analysisText: editorPayload.analysisText,
  });

  return {
    localId: current?.localId ?? `inline-${Date.now()}`,
    homeworkQuestionId: current?.homeworkQuestionId ?? null,
    questionBankId: null,
    sourceType: 'INLINE',
    questionType: questionPayload.type,
    stem: questionPayload.stem,
    sortOrder: current?.sortOrder ?? configuredQuestions.value.length + 1,
    score: current?.score ?? defaultScoreByType(questionPayload.type),
    options: mapOptionsForList(questionPayload.optionsJson),
    optionsJson: questionPayload.optionsJson,
    answerJson: questionPayload.answerJson,
    analysisText: questionPayload.analysisText,
    // 编辑态保留原评分参数，避免已发布作业仅改排序时因 scoringConfig 差异被误判为内容变更
    scoringConfigJson: current?.scoringConfigJson ?? null,
  };
};

/** BANK 行只更新本作业快照：保留原题 id、题库 id 与复制写入的评分参数 */
const buildConfiguredBankQuestion = (
  editorPayload: QuestionEditorDialogSubmitPayload,
  current: ConfiguredQuestionItem,
): ConfiguredQuestionItem => ({
  ...buildConfiguredInlineQuestion(editorPayload, current),
  sourceType: 'BANK',
  questionBankId: current.questionBankId,
  homeworkQuestionId: current.homeworkQuestionId ?? null,
});

const mergeConfiguredQuestions = (items: ConfiguredQuestionItem[]) => {
  const existingByBankId = new Map(
    configuredQuestions.value.filter(item => item.questionBankId).map(item => [item.questionBankId, item]),
  );
  const inlineItems = configuredQuestions.value.filter(item => item.sourceType === 'INLINE');
  const merged = [...inlineItems];

  for (const item of items) {
    if (item.questionBankId) {
      merged.push(existingByBankId.get(item.questionBankId) ?? item);
      continue;
    }
    merged.push(item);
  }

  configuredQuestions.value = resequenceConfiguredQuestions(merged);
};

const openQuestionPicker = () => {
  pickerVisible.value = true;
};

const openInlineQuestionEditor = () => {
  questionEditorMode.value = 'INLINE';
  activeConfiguredQuestionLocalId.value = null;
  questionDialogVisible.value = true;
};

const applyPickedQuestions = (items: ConfiguredQuestionItem[]) => {
  mergeConfiguredQuestions(items);
  ElMessage.success(`已带入 ${items.length} 道题目配置`);
};

const handleConfiguredQuestionsReorder = (items: ConfiguredQuestionItem[]) => {
  configuredQuestions.value = items;
};

const handleConfiguredQuestionScoreChange = ({ localId, score }: { localId: string; score: number }) => {
  configuredQuestions.value = configuredQuestions.value.map(item => (item.localId === localId ? { ...item, score } : item));
};

const removeConfiguredQuestion = (localId: string) => {
  configuredQuestions.value = resequenceConfiguredQuestions(
    configuredQuestions.value.filter(item => item.localId !== localId),
  );
};

const editConfiguredQuestion = (item: ConfiguredQuestionItem) => {
  if (questionContentLocked.value || detailLoading.value) {
    return;
  }

  activeConfiguredQuestionLocalId.value = item.localId;
  questionEditorMode.value = item.sourceType;
  questionDialogVisible.value = true;
};

const submitConfiguredQuestion = (editorPayload: QuestionEditorDialogSubmitPayload) => {
  const current = currentConfiguredQuestion.value;

  if (questionEditorMode.value === 'BANK') {
    if (!current) {
      return;
    }

    // 题库题在作业内只更新快照，不回写题库资产（TD31）
    const nextItem = buildConfiguredBankQuestion(editorPayload, current);
    configuredQuestions.value = resequenceConfiguredQuestions(
      configuredQuestions.value.map(item => (item.localId === current.localId ? nextItem : item)),
    );
    ElMessage.success('题库题配置已更新（仅作用于本作业）');
  } else {
    const nextItem = buildConfiguredInlineQuestion(editorPayload, current);
    if (current) {
      configuredQuestions.value = resequenceConfiguredQuestions(
        configuredQuestions.value.map(item => (item.localId === current.localId ? nextItem : item)),
      );
    } else {
      configuredQuestions.value = resequenceConfiguredQuestions([...configuredQuestions.value, nextItem]);
    }
    ElMessage.success(current ? '内联题已更新' : '内联题已加入当前作业配置');
  }

  questionDialogVisible.value = false;
  activeConfiguredQuestionLocalId.value = null;
};

/** 把配置清单转换为新增/更新接口共用的题目载荷 */
const buildQuestionSavePayload = (item: ConfiguredQuestionItem): SaveHomeworkQuestionRequest => ({
  type: normalizeQuestionType(item.questionType),
  stem: item.stem.trim(),
  questionScore: Number(item.score),
  optionsJson: item.optionsJson?.trim()
    ? item.optionsJson
    : item.options?.length
      ? JSON.stringify(item.options.map(option => ({ key: option.key, content: option.label })))
      : null,
  answerJson: item.answerJson?.trim() || '[]',
  analysisText: item.analysisText ?? '',
  scoringConfigJson: item.scoringConfigJson ?? null,
  sortOrder: Number(item.sortOrder),
});

/**
 * 增量同步作业题目：先删除被移除的题，再按“有 id 走 PUT、无 id 走新增”的顺序落库。
 *
 * <p>任一条失败立即中止并抛出，已有的本地配置保持不变；已成功删除/新增的 id 会即时回写到
 * {@link originalQuestionIds}，便于教师修复后重试而不重复操作。</p>
 */
const persistConfiguredQuestions = async (homeworkId: number) => {
  const currentIds = new Set(
    configuredQuestions.value
      .map(item => item.homeworkQuestionId)
      .filter((id): id is number => typeof id === 'number'),
  );
  const toDelete = originalQuestionIds.value.filter(id => !currentIds.has(id));

  for (const homeworkQuestionId of toDelete) {
    await deleteHomeworkQuestion(homeworkId, homeworkQuestionId);
    originalQuestionIds.value = originalQuestionIds.value.filter(id => id !== homeworkQuestionId);
  }

  for (const [index, item] of configuredQuestions.value.entries()) {
    try {
      const payload = buildQuestionSavePayload(item);
      if (!Number.isFinite(payload.questionScore) || !Number.isFinite(payload.sortOrder)) {
        throw new Error('配置数据无效，请重新编辑后再试');
      }

      if (item.homeworkQuestionId) {
        await updateHomeworkQuestion(homeworkId, item.homeworkQuestionId, payload);
        continue;
      }

      if (item.sourceType === 'BANK') {
        const questionId = Number(item.questionBankId);
        if (!Number.isFinite(questionId)) {
          throw new Error('未绑定题库题目');
        }

        const fromBankPayload: AddHomeworkQuestionFromBankRequest = {
          questionId,
          sortOrder: payload.sortOrder,
          questionScore: payload.questionScore,
        };
        const created = await addHomeworkQuestionFromBank(homeworkId, fromBankPayload);
        item.homeworkQuestionId = created.id;
      } else {
        const created = await addInlineHomeworkQuestion(homeworkId, payload);
        item.homeworkQuestionId = created.id;
      }

      if (typeof item.homeworkQuestionId === 'number') {
        originalQuestionIds.value = [...originalQuestionIds.value, item.homeworkQuestionId];
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '保存失败';
      throw new Error(`第 ${index + 1} 题保存失败：${message}`);
    }
  }
};

/** 汇总复制结果提示，失败不回滚作业保存 */
const copyInlineQuestionsAfterSave = async () => {
  if (!saveToQuestionBank.value) {
    return;
  }

  const candidates = configuredQuestions.value.filter(
    item => item.sourceType === 'INLINE' && typeof item.homeworkQuestionId === 'number',
  );
  if (!candidates.length) {
    return;
  }

  const createdCodes: string[] = [];
  const duplicateIds: number[] = [];
  const failures: string[] = [];

  for (const item of candidates) {
    try {
      const result = await copyHomeworkQuestionToBank(item.homeworkQuestionId as number, {});
      if (result.created) {
        createdCodes.push(result.question.code);
      }
      if (result.duplicateQuestionId) {
        duplicateIds.push(result.duplicateQuestionId);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '复制失败';
      failures.push(`${item.stem || item.localId}：${message}`);
    }
  }

  if (createdCodes.length) {
    ElMessage.success(`已复制 ${createdCodes.length} 道内联题到题库：${createdCodes.join('、')}`);
  }
  if (duplicateIds.length) {
    ElMessage.warning(`题库已有相同题目，可前往题库查看：ID ${duplicateIds.join('、')}`);
  }
  if (failures.length) {
    ElMessage.error(`部分题目复制失败：${failures.join('；')}`);
  }
};

/** 配置行“复制到题库”：失败重试与后续手动沉淀的唯一入口 */
const copyConfiguredQuestionToBank = async (item: ConfiguredQuestionItem) => {
  if (item.sourceType !== 'INLINE') {
    return;
  }
  if (typeof item.homeworkQuestionId !== 'number') {
    ElMessage.warning('请先保存作业，保存成功后才能复制到题库');
    return;
  }

  try {
    const result: CopyQuestionToBankResult = await copyHomeworkQuestionToBank(item.homeworkQuestionId, {});
    if (!result.created) {
      ElMessage.info('已保存到题库');
      return;
    }
    ElMessage.success(`已复制到题库：${result.question.code}`);
    if (result.duplicateQuestionId) {
      ElMessage.warning('题库已有相同题目');
    }
  } catch {
    // 错误提示已由统一请求拦截器呈现
  }
};

const submitForm = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入作业标题');
    return;
  }
  if (!form.classId) {
    ElMessage.warning('请选择所属班级');
    return;
  }

  submitting.value = true;
  try {
    const payload = buildPayload();

    if (editingId.value) {
      // 先落题目再落作业状态，允许同一次保存完成 DRAFT → PUBLISHED 与题目修改
      // 已关闭作业的题目不可变更，跳过题目写入避免命中锁定规则
      if (originalStatus.value !== ACTIVITY_STATUS.CLOSED) {
        await persistConfiguredQuestions(editingId.value);
      }
      await updateHomework(editingId.value, payload);
      await copyInlineQuestionsAfterSave();
      ElMessage.success(`作业已保存，题目共 ${configuredQuestions.value.length} 道`);
    } else {
      // 新建时先以草稿落库，题目写入后再更新为目标状态
      const created = await createHomework({ ...payload, status: ACTIVITY_STATUS.DRAFT });
      if (!created.id) {
        throw new Error('作业创建失败：未返回作业 ID');
      }
      await persistConfiguredQuestions(created.id);
      if (payload.status !== ACTIVITY_STATUS.DRAFT) {
        await updateHomework(created.id, payload);
      }
      await copyInlineQuestionsAfterSave();
      ElMessage.success(`作业已创建，题目共 ${configuredQuestions.value.length} 道`);
    }

    dialogVisible.value = false;
    resetForm();
    await fetchHomeworks();
  } catch (error) {
    // 接口错误已由统一请求拦截器提示，这里只补充本地组装出的题目保存错误
    if (!(error instanceof ApiError)) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试');
    }
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: HomeworkListItem) => {
  // 目标态固定为 DRAFT → PUBLISHED、PUBLISHED → CLOSED，CLOSED 不再提供状态操作。
  if (row.status !== ACTIVITY_STATUS.DRAFT && row.status !== ACTIVITY_STATUS.PUBLISHED) return;
  const nextStatus: HomeworkStatus = row.status === ACTIVITY_STATUS.DRAFT ? ACTIVITY_STATUS.PUBLISHED : ACTIVITY_STATUS.CLOSED;
  await changeHomeworkStatus(row.id, { status: nextStatus });
  ElMessage.success(nextStatus === ACTIVITY_STATUS.PUBLISHED ? '作业已发布' : '作业已关闭');
  await fetchHomeworks();
};

const releaseScores = async (row: HomeworkListItem) => {
  await releaseHomeworkScores(row.id);
  ElMessage.success('作业成绩已发布');
  await fetchHomeworks();
};

const goSubmissions = (homeworkId: number) => {
  router.push(`/teacher/homeworks/${homeworkId}/submissions`);
};

const statusLabel = (status: HomeworkStatus) => {
  if (status === ACTIVITY_STATUS.DRAFT) return '草稿';
  if (status === ACTIVITY_STATUS.PUBLISHED) return '已发布';
  if (status === ACTIVITY_STATUS.CLOSED) return '已关闭';
  return status || '未知';
};

const statusTagType = (status: HomeworkStatus) => {
  if (status === ACTIVITY_STATUS.PUBLISHED) return 'success';
  if (status === ACTIVITY_STATUS.CLOSED) return 'info';
  return 'warning';
};

const dueHint = (value?: string | null) => {
  if (!value) return '未设置截止时间';
  const dueTime = new Date(value).getTime();
  if (Number.isNaN(dueTime)) return '截止时间待确认';

  const diff = dueTime - Date.now();
  const dayMs = 24 * 60 * 60 * 1000;
  const days = Math.ceil(Math.abs(diff) / dayMs);

  if (diff < 0) {
    return `已截止 ${days} 天`;
  }
  if (days <= 1) {
    return '24 小时内截止';
  }
  return `${days} 天后截止`;
};

const formatDateTime = (value?: string | null) => {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
};

/** 计划满分 = Σ 每题配置分值；只做展示，不参与提交 */
const planTotalScore = computed(() =>
  configuredQuestions.value.reduce((total, item) => total + (Number(item.score) || 0), 0),
);

onMounted(async () => {
  await fetchClasses();
  resetForm();
  await fetchHomeworks();
});

  return { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, computed, onMounted, reactive, ref, useRouter, ElMessage, listClasses, addHomeworkQuestionFromBank, addInlineHomeworkQuestion, changeHomeworkStatus, copyHomeworkQuestionToBank, createHomework, deleteHomeworkQuestion, getTeacherHomeworkDetail, listTeacherHomeworks, releaseHomeworkScores, updateHomework, updateHomeworkQuestion, QuestionConfigList, QuestionEditorDialog, QuestionPickerDialog, ApiError, buildQuestionPayloadFromDraft, normalizeQuestionType, parseQuestionAnswerArray, router, loading, submitting, questionSubmitting, detailLoading, dialogVisible, pickerVisible, questionDialogVisible, editingId, originalStatus, rows, classOptions, configuredQuestions, planTotalScore, originalQuestionIds, saveToQuestionBank, searchQuery, statusFilter, classFilter, dueFilter, questionEditorMode, activeConfiguredQuestionLocalId, form, editingSubmissionCount, filteredRows, publishedCount, draftCount, totalSubmissionCount, activeStatusLabel, classFilterLabel, currentConfiguredQuestion, questionContentLocked, questionReorderLocked, questionLockHint, currentEditorQuestion, questionDialogDescription, questionDialogSubmitText, fetchHomeworks, fetchClasses, resetForm, resetFilters, buildPayload, openCreateDialog, openEditDialog, resequenceConfiguredQuestions, mapOptionsForList, buildConfiguredInlineQuestion, buildConfiguredBankQuestion, mergeConfiguredQuestions, openQuestionPicker, openInlineQuestionEditor, applyPickedQuestions, handleConfiguredQuestionsReorder, handleConfiguredQuestionScoreChange, removeConfiguredQuestion, editConfiguredQuestion, submitConfiguredQuestion, buildQuestionSavePayload, persistConfiguredQuestions, copyInlineQuestionsAfterSave, copyConfiguredQuestionToBank, submitForm, toggleStatus, releaseScores, goSubmissions, statusLabel, statusTagType, dueHint, formatDateTime };
}

export type HomeworksState = ReturnType<typeof useHomeworks>;
export const HomeworksKey: InjectionKey<HomeworksState> = Symbol('HomeworksState');
