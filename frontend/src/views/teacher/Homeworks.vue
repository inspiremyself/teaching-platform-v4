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
              :type="row.status === 'PUBLISHED' ? 'warning' : 'success'"
              plain
              @click="toggleStatus(row)"
            >
              {{ row.status === 'PUBLISHED' ? '关闭作业' : '发布作业' }}
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
            <p class="homework-dialog__desc">围绕班级、说明、附件和时间窗组织作业发布信息，保持当前真实创建与更新行为不变。</p>
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
                <el-select v-model="form.classId" placeholder="请选择班级">
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
                <el-select v-model="form.status" placeholder="请选择状态">
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="已关闭" value="CLOSED" />
                </el-select>
              </el-form-item>
            </div>
          </section>

          <section class="homework-dialog__group">
            <div class="homework-dialog__group-head">
              <div>
                <div class="homework-dialog__group-title">题目配置</div>
                <p class="homework-dialog__group-desc">
                  统一复用题库选题、题目配置清单与共享编辑器。{{ editingId ? '保存后会将这里的题目追加到当前作业。' : '创建作业后会按当前清单写入题目。' }}
                </p>
              </div>
              <div class="homework-dialog__group-actions">
                <el-button plain @click="openQuestionPicker">从题库挑题</el-button>
                <el-button type="primary" plain @click="openInlineQuestionEditor">新增内联题</el-button>
              </div>
            </div>

            <QuestionConfigList
              :items="configuredQuestions"
              @edit="editConfiguredQuestion"
              @remove="removeConfiguredQuestion"
              @reorder="handleConfiguredQuestionsReorder"
              @score-change="handleConfiguredQuestionScoreChange"
            />
          </section>
        </el-form>

        <footer class="homework-dialog__footer">
          <div class="homework-dialog__footer-note">保留作业 API、状态字段和时间格式，仅优化教师弹窗的工作台表达与信息密度。</div>
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
      :show-asset-fields="questionEditorMode === 'BANK'"
      :show-config-fields="questionEditorMode !== 'BANK'"
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
import { listQuestions, updateQuestion } from '@/api/questions';
import {
  addHomeworkQuestionFromBank,
  addInlineHomeworkQuestion,
  changeHomeworkStatus,
  createHomework,
  listTeacherHomeworks,
  releaseHomeworkScores,
  updateHomework,
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
  AddInlineHomeworkQuestionRequest,
  CreateHomeworkRequest,
  HomeworkListItem,
  HomeworkStatus,
} from '@/types/homework';
import type { QuestionItem } from '@/types/question';
import { buildQuestionPayloadFromDraft, normalizeQuestionType } from './components/typed-editor/adapters/questionAdapter';
import { parseQuestionAnswerArray } from './components/typed-editor/hydrators/fromQuestion';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const questionSubmitting = ref(false);
const dialogVisible = ref(false);
const pickerVisible = ref(false);
const questionDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<HomeworkListItem[]>([]);
const classOptions = ref<TeachingClass[]>([]);
const questionBankRows = ref<QuestionItem[]>([]);
const configuredQuestions = ref<ConfiguredQuestionItem[]>([]);
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
  attachmentPath: '',
  startAt: null,
  dueAt: null,
});

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

const currentEditorQuestion = computed<QuestionEditorDialogValue | null>(() => {
  if (!questionDialogVisible.value) {
    return null;
  }

  const current = currentConfiguredQuestion.value;
  if (!current) {
    return null;
  }

  if (questionEditorMode.value === 'BANK') {
    const matchedRow = questionBankRows.value.find(row => row.id === current.questionBankId);
    if (!matchedRow) {
      return null;
    }

    return {
      ...current,
      code: matchedRow.code,
      difficulty: matchedRow.difficulty as QuestionEditorDialogValue['difficulty'],
      defaultScore: matchedRow.defaultScore,
      analysisText: matchedRow.analysisText,
      options: parseQuestionAnswerArray(matchedRow.optionsJson).map((option, index) => {
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
      }),
      answerJson: matchedRow.answerJson,
    };
  }

  return current;
});

const questionDialogDescription = computed(() =>
  questionEditorMode.value === 'BANK'
    ? '在作业壳内直接维护题库题目资产，保存后同步更新当前配置清单。'
    : '使用共享题目编辑器维护内联题的题干、答案与评分配置，保存时走原有内联题接口。',
);

const questionDialogSubmitText = computed(() => (questionEditorMode.value === 'BANK' ? '保存题库题' : '保存内联题'));

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

const fetchQuestionBank = async () => {
  questionBankRows.value = await listQuestions();
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
  editingId.value = null;
  configuredQuestions.value = [];
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
  attachmentPath: form.attachmentPath?.trim() || null,
  startAt: form.startAt || null,
  dueAt: form.dueAt || null,
});

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: HomeworkListItem) => {
  editingId.value = row.id;
  form.title = row.title;
  form.description = row.description;
  form.classId = row.classId;
  form.status = row.status;
  form.attachmentPath = row.attachmentPath || '';
  form.startAt = row.startAt || null;
  form.dueAt = row.dueAt || null;
  form.scoreVisibilityMode = row.scoreVisibilityMode || 'AFTER_TEACHER_CONFIRM';
  configuredQuestions.value = [];
  dialogVisible.value = true;
};

const resequenceConfiguredQuestions = (items: ConfiguredQuestionItem[]) =>
  items.map((item, index) => ({
    ...item,
    sortOrder: index + 1,
  }));

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
    questionBankId: null,
    sourceType: 'INLINE',
    questionType: questionPayload.type,
    stem: questionPayload.stem,
    sortOrder: current?.sortOrder ?? configuredQuestions.value.length + 1,
    score: current?.score ?? editorPayload.defaultScore,
    options: parseQuestionAnswerArray(questionPayload.optionsJson).map((option, index) => {
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
    }),
    answerJson: questionPayload.answerJson,
    scoringConfigJson: JSON.stringify({
      type: questionPayload.type,
      optionsJson: questionPayload.optionsJson,
      answerJson: questionPayload.answerJson,
      analysisText: questionPayload.analysisText,
    }),
  };
};

const buildInlineScoringConfigJson = (item: ConfiguredQuestionItem) => {
  const normalizedType = normalizeQuestionType(item.questionType);

  if (item.scoringConfigJson?.trim()) {
    try {
      const parsed = JSON.parse(item.scoringConfigJson);
      return JSON.stringify(parsed);
    } catch {
      // fall through to a clean rebuild from the current configured item
    }
  }

  return JSON.stringify({
    type: normalizedType,
    optionsJson: item.options?.length
      ? JSON.stringify(
          item.options.map(option => ({
            key: option.key,
            content: option.label,
          })),
        )
      : null,
    answerJson: item.answerJson?.trim() || '[]',
    analysisText: '',
  });
};

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

const editConfiguredQuestion = async (item: ConfiguredQuestionItem) => {
  activeConfiguredQuestionLocalId.value = item.localId;
  questionEditorMode.value = item.sourceType;

  if (item.sourceType === 'BANK') {
    if (!questionBankRows.value.length) {
      await fetchQuestionBank();
    }
    const matchedRow = questionBankRows.value.find(row => row.id === item.questionBankId);
    if (!matchedRow) {
      ElMessage.warning('未在当前题库列表中找到该题目，请刷新后重试。');
      return;
    }
  }

  questionDialogVisible.value = true;
};

const syncConfiguredBankQuestion = (question: QuestionItem) => {
  configuredQuestions.value = configuredQuestions.value.map(item =>
    item.questionBankId === question.id
      ? {
          ...item,
          questionType: question.type,
          stem: question.stem,
          options: parseQuestionAnswerArray(question.optionsJson).map((option, index) => {
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
          }),
          answerJson: question.answerJson,
        }
      : item,
  );
};

const submitConfiguredQuestion = async (editorPayload: QuestionEditorDialogSubmitPayload) => {
  questionSubmitting.value = true;
  try {
    const current = currentConfiguredQuestion.value;

    if (questionEditorMode.value === 'BANK') {
      if (!current?.questionBankId) {
        ElMessage.warning('当前题目未绑定题库题。');
        return;
      }

      const payload = buildQuestionPayloadFromDraft(editorPayload.draft, {
        code: editorPayload.code,
        type: editorPayload.type,
        difficulty: editorPayload.difficulty,
        defaultScore: editorPayload.defaultScore,
        analysisText: editorPayload.analysisText,
      });
      await updateQuestion(current.questionBankId, payload);
      await fetchQuestionBank();
      const refreshed = questionBankRows.value.find(row => row.id === current.questionBankId);
      if (refreshed) {
        syncConfiguredBankQuestion(refreshed);
      }
      ElMessage.success('题库题更新成功');
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
  } finally {
    questionSubmitting.value = false;
  }
};

const persistConfiguredQuestions = async (homeworkId: number) => {
  for (const item of configuredQuestions.value) {
    if (item.sourceType === 'BANK' && item.questionBankId) {
      const questionId = Number(item.questionBankId);
      const sortOrder = Number(item.sortOrder);
      const questionScore = Number(item.score);

      if (!Number.isFinite(questionId) || !Number.isFinite(sortOrder) || !Number.isFinite(questionScore)) {
        ElMessage.warning(`题库题「${item.stem || item.questionBankId}」的配置数据无效，请重新选择后再试`);
        continue;
      }

      const payload: AddHomeworkQuestionFromBankRequest = {
        questionId,
        sortOrder,
        questionScore,
      };
      const normalizedPayload = JSON.parse(JSON.stringify(payload)) as AddHomeworkQuestionFromBankRequest;
      await addHomeworkQuestionFromBank(homeworkId, normalizedPayload);
      continue;
    }

    const questionScore = Number(item.score);
    const sortOrder = Number(item.sortOrder);

    if (!Number.isFinite(questionScore) || !Number.isFinite(sortOrder)) {
      ElMessage.warning(`内联题「${item.stem || item.localId}」的配置数据无效，请重新编辑后再试`);
      continue;
    }

    const payload: AddInlineHomeworkQuestionRequest = {
      type: normalizeQuestionType(item.questionType),
      stem: item.stem.trim(),
      questionScore,
      sortOrder,
      saveToQuestionBank: false,
      scoringConfigJson: buildInlineScoringConfigJson(item),
    };
    const normalizedPayload = JSON.parse(JSON.stringify(payload)) as AddInlineHomeworkQuestionRequest;
    await addInlineHomeworkQuestion(homeworkId, normalizedPayload);
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
    let homeworkId = editingId.value;
    if (editingId.value) {
      await updateHomework(editingId.value, payload);
      if (configuredQuestions.value.length) {
        await persistConfiguredQuestions(editingId.value);
      }
      ElMessage.success(configuredQuestions.value.length ? `作业更新成功，并追加了 ${configuredQuestions.value.length} 道题目` : '作业更新成功');
    } else {
      const created = await createHomework(payload);
      homeworkId = created.id;
      if (homeworkId && configuredQuestions.value.length) {
        await persistConfiguredQuestions(homeworkId);
      }
      ElMessage.success(configuredQuestions.value.length ? `作业创建成功，并写入了 ${configuredQuestions.value.length} 道题目` : '作业创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchHomeworks();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: HomeworkListItem) => {
  const nextStatus: HomeworkStatus = row.status === 'PUBLISHED' ? 'CLOSED' : 'PUBLISHED';
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
  await Promise.all([fetchClasses(), fetchQuestionBank()]);
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
