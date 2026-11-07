<template>
  <div class="page homeworks-page workbench-page">
    <section class="workbench-header homeworks-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="homeworks-header__eyebrow">教师后台 / 作业管理</div>
          <h1>作业管理台</h1>
          <p>围绕班级、时间窗、发布状态与提交进度维护作业闭环。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">新建作业</el-button>
        </div>
      </div>

      <div class="workbench-meta homeworks-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 作业总数</span>
        <span class="workbench-meta__item"><strong>{{ publishedCount }}</strong> 已发布</span>
        <span class="workbench-meta__item"><strong>{{ draftCount }}</strong> 草稿/待处理</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ totalSubmissionCount }}</strong> 提交总量</span>
        <span class="workbench-meta__item">{{ activeStatusLabel }}</span>
        <span class="workbench-meta__item">{{ classFilterLabel }}</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索作业标题、班级或说明" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable>
            <el-option label="全部状态" value="" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-select v-model="classFilter" placeholder="全部班级" clearable>
            <el-option label="全部班级" value="0" />
            <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
          <el-select v-model="dueFilter" placeholder="全部时效" clearable>
            <el-option label="全部时效" value="" />
            <el-option label="即将截止" value="upcoming" />
            <el-option label="已截止" value="overdue" />
            <el-option label="未设置截止" value="undated" />
          </el-select>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>共 {{ rows.length }} 条作业</span>
        <span class="toolbar-divider"></span>
        <span>当前筛出 {{ filteredRows.length }} 条</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="homeworks-console" v-loading="loading">
      <header class="homeworks-console__header">
        <div>
          <h2>作业清单</h2>
          <p>保留作业摘要、班级归属、提交数、时间窗和状态动作，避免退回成单薄表格页。</p>
        </div>
      </header>

      <div v-if="filteredRows.length" class="homework-list-shell">
        <article v-for="row in filteredRows" :key="row.id" class="homework-row">
          <div class="homework-row__primary">
            <div class="homework-row__title-line">
              <div>
                <h3>{{ row.title }}</h3>
                <p>{{ row.description || '暂无作业说明，建议补充作答目标、提交格式与评分重点。' }}</p>
              </div>
              <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </div>

            <div class="homework-row__meta-grid">
              <div>
                <span class="homework-row__label">所属班级</span>
                <strong>{{ row.className || '-' }}</strong>
              </div>
              <div>
                <span class="homework-row__label">开始时间</span>
                <strong>{{ formatDateTime(row.startAt) }}</strong>
              </div>
              <div>
                <span class="homework-row__label">截止时间</span>
                <strong>{{ formatDateTime(row.dueAt) }}</strong>
              </div>
              <div>
                <span class="homework-row__label">提交数</span>
                <strong>{{ row.submissionCount ?? 0 }}</strong>
              </div>
            </div>

            <div class="homework-row__supporting">
              <span class="homework-chip">
                <span class="material-symbols-outlined">timelapse</span>
                {{ dueHint(row.dueAt) }}
              </span>
              <span v-if="row.attachmentPath" class="homework-chip homework-chip--muted">
                <span class="material-symbols-outlined">attach_file</span>
                {{ row.attachmentPath }}
              </span>
            </div>
          </div>

          <div class="homework-row__actions">
            <el-button type="primary" plain @click="goSubmissions(row.id)">提交列表</el-button>
            <el-button @click="openEditDialog(row)">编辑</el-button>
            <el-button plain @click="releaseScores(row)">发布成绩</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              plain
              @click="toggleStatus(row)"
            >
              发布作业
            </el-button>
            <el-button
              v-else-if="row.status === 'PUBLISHED'"
              type="warning"
              plain
              @click="toggleStatus(row)"
            >
              关闭作业
            </el-button>
          </div>
        </article>
      </div>

      <el-empty v-else description="暂无匹配作业" />
    </section>

    <el-dialog v-model="dialogVisible" width="860px" class="homework-dialog" destroy-on-close>
      <div class="homework-dialog__shell">
        <header class="homework-dialog__header">
          <div>
            <p class="homework-dialog__eyebrow">作业管理台 / {{ editingId ? '配置调整' : '发布准备' }}</p>
            <h2>{{ editingId ? '编辑作业配置' : '新建作业' }}</h2>
            <p class="homework-dialog__desc">围绕班级、说明、附件和时间窗组织作业发布信息；题目按清单增量同步，已发布作业仅支持调整排序。</p>
          </div>
          <div class="homework-dialog__badges">
            <span class="homework-dialog__badge">{{ statusLabel(form.status) }}</span>
            <span class="homework-dialog__badge homework-dialog__badge--muted">{{ form.classId ? classOptions.find(item => item.id === form.classId)?.name || '已选班级' : '待选班级' }}</span>
          </div>
        </header>

        <section class="homework-dialog__summary-grid">
          <article class="homework-dialog__summary-card">
            <span class="homework-dialog__label">时间窗状态</span>
            <strong>{{ form.dueAt ? dueHint(form.dueAt) : '未设置截止时间' }}</strong>
            <p>开始时间与截止时间会直接影响学生侧可见与提交窗口。</p>
          </article>
          <article class="homework-dialog__summary-card">
            <span class="homework-dialog__label">发布说明</span>
            <strong>{{ editingId ? '更新既有作业' : '创建新作业条目' }}</strong>
            <p>标题与班级仍是当前保存动作的必填条件，原有校验保持不变。</p>
          </article>
        </section>

        <el-form class="homework-dialog__form" label-position="top">
          <section class="homework-dialog__group">
            <div class="homework-dialog__group-title">基础信息</div>
            <div class="homework-dialog__grid homework-dialog__grid--2col">
              <el-form-item label="作业标题" required>
                <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：实验一报告" />
              </el-form-item>
              <el-form-item label="所属班级" required>
                <el-select v-model="form.classId" placeholder="请选择班级" :disabled="editingSubmissionCount > 0">
                  <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="作业说明">
              <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="补充作答目标、提交格式、评分重点等" />
            </el-form-item>
            <el-form-item label="附件路径">
              <el-input v-model="form.attachmentPath" placeholder="可选，本地挂载路径" />
            </el-form-item>
          </section>

          <section class="homework-dialog__group">
            <div class="homework-dialog__group-title">时间与状态配置</div>
            <div class="homework-dialog__grid homework-dialog__grid--3col">
              <el-form-item label="开始时间">
                <el-date-picker
                  v-model="form.startAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ssZ"
                  placeholder="请选择开始时间"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="截止时间">
                <el-date-picker
                  v-model="form.dueAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ssZ"
                  placeholder="请选择截止时间"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="发布状态">
                <el-select v-model="form.status" placeholder="请选择状态" :disabled="originalStatus === 'CLOSED'">
                  <el-option
                    label="草稿"
                    value="DRAFT"
                    :disabled="originalStatus === 'PUBLISHED' && editingSubmissionCount > 0"
                  />
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="已关闭" value="CLOSED" :disabled="originalStatus === 'DRAFT'" />
                </el-select>
              </el-form-item>
              <el-form-item label="成绩可见性">
                <el-select v-model="form.scoreVisibilityMode" placeholder="请选择可见性">
                  <el-option label="立即可见" value="IMMEDIATE" />
                  <el-option label="教师确认后可见" value="AFTER_TEACHER_CONFIRM" />
                  <el-option label="截止后可见" value="AFTER_DEADLINE" />
                  <el-option label="教师手动发布" value="MANUAL_RELEASE" />
                </el-select>
              </el-form-item>
            </div>
          </section>

          <section class="homework-dialog__group">
            <div class="homework-dialog__group-head">
              <div>
                <div class="homework-dialog__group-title">题目配置</div>
                <p class="homework-dialog__group-desc">
                  统一复用题库选题、题目配置清单与共享编辑器。{{ editingId ? '保存时按清单增量同步：新增、修改与删除都会落到当前作业。' : '创建作业后会按当前清单写入题目。' }}
                </p>
                <p v-if="questionLockHint" class="homework-dialog__group-lock">{{ questionLockHint }}</p>
              </div>
              <div class="homework-dialog__group-actions">
                <el-switch v-model="saveToQuestionBank" active-text="同时保存到题库" :disabled="submitting || detailLoading" />
                <el-button plain :disabled="questionContentLocked || detailLoading" @click="openQuestionPicker">从题库挑题</el-button>
                <el-button type="primary" plain :disabled="questionContentLocked || detailLoading" @click="openInlineQuestionEditor">新增内联题</el-button>
              </div>
            </div>

            <QuestionConfigList
              :items="configuredQuestions"
              :content-locked="questionContentLocked"
              :reorder-locked="questionReorderLocked"
              :busy="detailLoading"
              copyable
              bank-snapshot-hint
              @edit="editConfiguredQuestion"
              @remove="removeConfiguredQuestion"
              @reorder="handleConfiguredQuestionsReorder"
              @score-change="handleConfiguredQuestionScoreChange"
              @copy-to-bank="copyConfiguredQuestionToBank"
            />
          </section>
        </el-form>

        <footer class="homework-dialog__footer">
          <div class="homework-dialog__footer-note">题目按清单增量同步；已发布作业仅支持调整排序，已关闭作业不可再修改题目。</div>
          <div class="homework-dialog__footer-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">{{ editingId ? '保存作业' : '创建作业' }}</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <QuestionEditorDialog
      v-model:visible="questionDialogVisible"
      :initial-question="currentEditorQuestion"
      :loading="questionSubmitting"
      :editing="Boolean(currentConfiguredQuestion)"
      :description="questionDialogDescription"
      :submit-text="questionDialogSubmitText"
      @saved="submitConfiguredQuestion"
    />

    <QuestionPickerDialog v-model:visible="pickerVisible" @confirm="applyPickedQuestions" />
  </div>
</template>

<script setup lang="ts">
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
import { buildQuestionPayloadFromDraft, normalizeQuestionType } from './components/typed-editor/adapters/questionAdapter';
import { parseQuestionAnswerArray } from './components/typed-editor/hydrators/fromQuestion';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const questionSubmitting = ref(false);
const detailLoading = ref(false);
const dialogVisible = ref(false);
const pickerVisible = ref(false);
const questionDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const originalStatus = ref<HomeworkStatus>('DRAFT');
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
  status: 'DRAFT',
  scoreVisibilityMode: 'AFTER_TEACHER_CONFIRM',
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

const publishedCount = computed(() => filteredRows.value.filter(row => row.status === 'PUBLISHED').length);
const draftCount = computed(() => filteredRows.value.filter(row => row.status === 'DRAFT').length);
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
const questionContentLocked = computed(() => form.status === 'PUBLISHED' || form.status === 'CLOSED');

/** 已关闭：连排序也禁止 */
const questionReorderLocked = computed(() => form.status === 'CLOSED');

const questionLockHint = computed(() => {
  if (form.status === 'PUBLISHED') return '已发布作业仅支持调整排序；新增、编辑、删除与改分均已禁用。';
  if (form.status === 'CLOSED') return '已关闭作业不可再调整题目。';
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
  classOptions.value = response.items;
};

const resetForm = () => {
  form.title = '';
  form.description = '';
  form.classId = classOptions.value[0]?.id ?? 0;
  form.status = 'DRAFT';
  form.attachmentPath = '';
  form.startAt = null;
  form.dueAt = null;
  form.scoreVisibilityMode = 'AFTER_TEACHER_CONFIRM';
  editingSubmissionCount.value = 0;
  editingId.value = null;
  originalStatus.value = 'DRAFT';
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
    form.scoreVisibilityMode = detail.scoreVisibilityMode || 'AFTER_TEACHER_CONFIRM';
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
    defaultScore: editorPayload.defaultScore,
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
    score: editorPayload.defaultScore,
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
      if (originalStatus.value !== 'CLOSED') {
        await persistConfiguredQuestions(editingId.value);
      }
      await updateHomework(editingId.value, payload);
      await copyInlineQuestionsAfterSave();
      ElMessage.success(`作业已保存，题目共 ${configuredQuestions.value.length} 道`);
    } else {
      // 新建时先以草稿落库，题目写入后再更新为目标状态
      const created = await createHomework({ ...payload, status: 'DRAFT' });
      if (!created.id) {
        throw new Error('作业创建失败：未返回作业 ID');
      }
      await persistConfiguredQuestions(created.id);
      if (payload.status !== 'DRAFT') {
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
  if (row.status !== 'DRAFT' && row.status !== 'PUBLISHED') return;
  const nextStatus: HomeworkStatus = row.status === 'DRAFT' ? 'PUBLISHED' : 'CLOSED';
  await changeHomeworkStatus(row.id, { status: nextStatus });
  ElMessage.success(nextStatus === 'PUBLISHED' ? '作业已发布' : '作业已关闭');
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
  if (status === 'DRAFT') return '草稿';
  if (status === 'PUBLISHED') return '已发布';
  if (status === 'CLOSED') return '已关闭';
  return status || '未知';
};

const statusTagType = (status: HomeworkStatus) => {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'CLOSED') return 'info';
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

onMounted(async () => {
  await fetchClasses();
  resetForm();
  await fetchHomeworks();
});
</script>

<style scoped>
.homeworks-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.homeworks-hero {
  position: relative;
  overflow: hidden;
  border-radius: 32px;
  padding: 30px 32px;
  background:
    radial-gradient(circle at top right, rgba(96, 165, 250, 0.22), transparent 28%),
    linear-gradient(135deg, #0f172a, #1d4ed8 58%, #3b82f6);
  box-shadow: 0 20px 50px rgba(30, 64, 175, 0.22);
}

.homeworks-hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 22px;
  color: #eff6ff;
}

.homeworks-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(219, 234, 254, 0.82);
}

.homeworks-hero__heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.homeworks-hero__heading h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 800;
  color: #ffffff;
}

.homeworks-hero__heading p {
  max-width: 720px;
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(219, 234, 254, 0.88);
}

.homeworks-context-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.context-item {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(15, 23, 42, 0.2);
  border: 1px solid rgba(191, 219, 254, 0.18);
}

.context-item--accent {
  background: rgba(255, 255, 255, 0.14);
}

.context-item .material-symbols-outlined {
  font-size: 22px;
}

.context-item strong {
  display: block;
  font-size: 14px;
  color: #ffffff;
}

.context-item p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(219, 234, 254, 0.8);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
}

.summary-card--light,
.summary-card--accent {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(191, 219, 254, 0.5);
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

.summary-card__icon--green {
  background: rgba(220, 252, 231, 0.9);
  color: #15803d;
}

.summary-card__icon--amber {
  background: rgba(254, 243, 199, 0.9);
  color: #b45309;
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

.toolbar-search .material-symbols-outlined {
  color: #64748b;
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
  width: 168px;
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

.homeworks-console {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.homeworks-console__header h2 {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}

.homeworks-console__header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.homework-list-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.homework-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.05);
}

.homework-row__primary {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.homework-row__title-line {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.homework-row__title-line h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.homework-row__title-line p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.homework-row__meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.homework-row__meta-grid > div {
  padding: 14px 16px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.homework-row__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.homework-row__meta-grid strong {
  color: #0f172a;
  font-size: 14px;
}

.homework-row__supporting {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.homework-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.homework-chip--muted {
  background: #eff6ff;
}

.homework-row__actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.homework-row__actions .el-button {
  width: 100%;
  margin-left: 0;
}

.homework-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.homework-dialog :deep(.el-dialog__header) {
  display: none;
}

.homework-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.homework-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.homework-dialog__header,
.homework-dialog__summary-card,
.homework-dialog__group,
.homework-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
}

.homework-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, rgba(29, 78, 216, 0.08), rgba(239, 246, 255, 0.96));
}

.homework-dialog__eyebrow,
.homework-dialog__label {
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.homework-dialog__header h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 800;
}

.homework-dialog__desc {
  margin: 8px 0 0;
  max-width: 560px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.homework-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.homework-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.homework-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.homework-dialog__summary-grid,
.homework-dialog__grid--2col,
.homework-dialog__grid--3col {
  display: grid;
  gap: 14px;
}

.homework-dialog__summary-grid,
.homework-dialog__grid--2col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.homework-dialog__grid--3col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.homework-dialog__summary-card,
.homework-dialog__group {
  padding: 18px;
}

.homework-dialog__summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.homework-dialog__summary-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.homework-dialog__group-title {
  margin-bottom: 14px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.homework-dialog__group-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.homework-dialog__group-head .homework-dialog__group-title {
  margin-bottom: 6px;
}

.homework-dialog__group-desc {
  margin: 0;
  max-width: 560px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__group-lock {
  margin: 6px 0 0;
  color: #b45309;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.6;
}

.homework-dialog__group-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.homework-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.homework-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.homework-dialog__form :deep(.el-input__wrapper),
.homework-dialog__form :deep(.el-select__wrapper),
.homework-dialog__form :deep(.el-textarea__inner) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.homework-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.homework-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.homework-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1200px) {
  .summary-grid,
  .homeworks-context-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-card__main,
  .homework-row {
    grid-template-columns: 1fr;
    display: grid;
  }

  .toolbar-filters,
  .homework-row__actions {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    display: grid;
  }
}

@media (max-width: 900px) {
  .homeworks-hero,
  .summary-card,
  .toolbar-card,
  .homework-row {
    padding: 20px;
    border-radius: 24px;
  }

  .homeworks-hero__heading,
  .toolbar-card__main,
  .toolbar-filters,
  .homework-row__title-line,
  .homework-row__actions {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .homeworks-context-strip,
  .homework-row__meta-grid {
    grid-template-columns: 1fr;
  }

  .homework-dialog__header,
  .homework-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .homework-dialog__badges {
    justify-content: flex-start;
  }

  .homework-dialog__summary-grid,
  .homework-dialog__grid--2col,
  .homework-dialog__grid--3col {
    grid-template-columns: 1fr;
  }
}
</style>
