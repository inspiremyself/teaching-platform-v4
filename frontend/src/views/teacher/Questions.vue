<template>
  <div class="page question-console workbench-page">
    <section class="workbench-header question-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="question-header__eyebrow">教学控制台 / 题库资产</div>
          <h1 class="question-console__title">题库管理台</h1>
          <p class="question-console__subtitle">统一检索、维护和校验题库资产。</p>
        </div>
        <div class="workbench-header__actions question-console__hero-actions">
          <el-button class="question-console__ghost-button" @click="openPickerDialog">挑选题目配置</el-button>
          <el-button class="question-console__ghost-button" @click="resetFilters">重置筛选</el-button>
          <el-button class="question-console__ghost-button" @click="importVisible = true">批量导入</el-button>
          <el-button type="primary" class="question-console__primary-button" @click="openCreateDialog">
            新增题目
          </el-button>
        </div>
      </div>

      <div class="workbench-meta question-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 题库总量</span>
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 筛选结果</span>
        <span class="workbench-meta__item"><strong>{{ hardQuestionCount }}</strong> 高难度</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ incompleteQuestionCount }}</strong> 待完善</span>
      </div>
    </section>

    <section class="question-console__filter-panel">
      <div class="question-console__filter-header">
        <div>
          <p class="question-console__section-kicker">高级筛选</p>
          <h2 class="question-console__section-title">题目资产筛选台</h2>
        </div>
        <div class="question-console__filter-summary">
          <span class="question-console__summary-chip">当前排序：编号升序</span>
          <span class="question-console__summary-text">{{ filterSummaryText }}</span>
        </div>
      </div>

      <div class="question-console__filters-grid">
        <label class="question-console__field">
          <span class="question-console__field-label">关键词检索</span>
          <el-input v-model="filters.keyword" clearable placeholder="按编码、题干、解析内容搜索" />
        </label>

        <label class="question-console__field">
          <span class="question-console__field-label">题型</span>
          <el-select v-model="filters.type" clearable placeholder="全部题型">
            <el-option v-for="item in typeOptions" :key="item" :label="typeLabelMap[item]" :value="item" />
          </el-select>
        </label>

        <label class="question-console__field">
          <span class="question-console__field-label">难度</span>
          <el-select v-model="filters.difficulty" clearable placeholder="全部难度">
            <el-option v-for="item in difficultyOptions" :key="item" :label="difficultyLabelMap[item]" :value="item" />
          </el-select>
        </label>

        <div class="question-console__quick-tags">
          <span class="question-console__field-label">快捷视图</span>
          <div class="question-console__quick-tag-list">
            <button
              v-for="view in quickViews"
              :key="view.key"
              type="button"
              class="question-console__quick-tag"
              :class="{ 'is-active': activeQuickView === view.key }"
              @click="applyQuickView(view.key)"
            >
              {{ view.label }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="question-console__config-shell">
      <div class="question-console__config-header">
        <div>
          <p class="question-console__section-kicker">题目配置暂存区</p>
          <h2 class="question-console__section-title">当前页选题与配置清单</h2>
          <p class="question-console__config-subtitle">
            这里承接“挑选题目配置”对话框带回的题目，继续在当前题库页微调顺序与分值；若要改题干或答案，可直接回到本页编辑对应题目资产。
          </p>
        </div>
        <div class="question-console__table-meta">
          <span class="question-console__table-count">已暂存 {{ configuredQuestions.length }} 题</span>
          <span class="question-console__summary-chip">页面内真实复用 QuestionConfigList</span>
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

    <section class="question-console__table-shell">
      <div class="question-console__table-header">
        <div>
          <p class="question-console__section-kicker">资产目录</p>
          <h2 class="question-console__section-title">题目资产目录</h2>
        </div>
        <div class="question-console__table-meta">
          <span class="question-console__table-count">显示 {{ filteredRows.length }} / {{ rows.length }}</span>
          <span class="question-console__summary-chip">高密度教师视图</span>
          <span class="question-console__summary-chip">保留真实新增/编辑/删除链路</span>
        </div>
      </div>

      <el-table
        :data="filteredRows"
        v-loading="loading"
        empty-text="暂无题目数据"
        class="question-console__table"
        :header-cell-style="headerCellStyle"
      >
        <el-table-column label="资产标识" min-width="220">
          <template #default="{ row }">
            <div class="question-row__identity">
              <div class="question-row__identity-main">{{ row.code }}</div>
              <div class="question-row__identity-tags">
                <span class="question-chip question-chip--type">{{ getTypeLabel(row.type) }}</span>
                <span class="question-chip question-chip--score">{{ row.defaultScore }} 分</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="题干内容" min-width="360">
          <template #default="{ row }">
            <div class="question-row__stem">
              <p class="question-row__stem-text">{{ row.stem }}</p>
              <p class="question-row__stem-subtext">
                {{ row.analysisText?.trim() ? `解析摘要：${summarizeText(row.analysisText, 56)}` : '当前未填写题目解析，建议补全教师讲评与评分依据。' }}
              </p>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="校验指标" min-width="240">
          <template #default="{ row }">
            <div class="question-row__metrics">
              <div class="question-row__difficulty">
                <span class="question-row__difficulty-label">难度 {{ getDifficultyLabel(row.difficulty) }}</span>
                <div class="question-row__difficulty-stars">
                  <span
                    v-for="star in 3"
                    :key="star"
                    class="question-row__difficulty-star"
                    :class="{ 'is-filled': star <= getDifficultyLevel(row.difficulty) }"
                  >
                    ★
                  </span>
                </div>
              </div>
              <div class="question-row__metric-list">
                <span class="question-row__metric-pill">{{ getOptionsCount(row) }} 选项</span>
                <span class="question-row__metric-pill">{{ getAnswerCount(row) }} 答案项</span>
                <span class="question-row__metric-pill">{{ row.analysisText?.trim() ? '含解析' : '缺解析' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="健康状态" min-width="180">
          <template #default="{ row }">
            <div class="question-row__health">
              <div class="question-row__health-line">
                <span class="question-row__health-dot" :class="getHealthStatus(row).dotClass"></span>
                <span class="question-row__health-text" :class="getHealthStatus(row).textClass">{{ getHealthStatus(row).label }}</span>
              </div>
              <div class="question-row__health-bar">
                <span class="question-row__health-bar-fill" :class="getHealthStatus(row).barClass" :style="{ width: `${getHealthStatus(row).progress}%` }"></span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="right" fixed="right">
          <template #default="{ row }">
            <div class="question-row__actions">
              <button type="button" class="question-row__icon-button" title="编辑题目" @click="openEditDialog(row)">
                <span class="material-symbols-outlined">edit</span>
              </button>
              <button type="button" class="question-row__icon-button question-row__icon-button--neutral" title="复制编码" @click="copyCode(row.code)">
                <span class="material-symbols-outlined">content_copy</span>
              </button>
              <button type="button" class="question-row__icon-button question-row__icon-button--danger" title="删除题目" @click="remove(row.id)">
                <span class="material-symbols-outlined">delete</span>
              </button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <QuestionEditorDialog
      v-model:visible="dialogVisible"
      class="question-dialog"
      :initial-question="currentEditorQuestion"
      :loading="submitting"
      :title="editingId ? '编辑题目资产' : '新增题目资产'"
      description="按题型直接维护题干、答案和结构数据，保存时仍由 Questions.vue 组装现有 API 所需字段。"
      :submit-text="editingId ? '保存题目' : '创建题目'"
      :show-asset-fields="true"
      :show-config-fields="false"
      @saved="submit"
    />

    <QuestionPickerDialog v-model:visible="pickerVisible" @confirm="applyPickedQuestions" />

    <QuestionImportDialog v-model:visible="importVisible" @imported="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createQuestion, deleteQuestion, listQuestions, updateQuestion } from '@/api/questions';
import QuestionConfigList from '@/components/question-config/QuestionConfigList.vue';
import type { QuestionDifficulty, QuestionItem, QuestionType } from '@/types/question';
import type { ConfiguredQuestionItem } from '@/types/question-config';
import { buildQuestionPayloadFromDraft } from './components/typed-editor/adapters/questionAdapter';
import { parseQuestionAnswerArray } from './components/typed-editor/hydrators/fromQuestion';
import { QUESTION_TYPED_EDITOR_KIND_OPTIONS, TYPED_EDITOR_LABELS } from './components/typed-editor/constants';
import type { TypedEditorKind } from './components/typed-editor/types';
import QuestionEditorDialog, {
  type QuestionEditorDialogSubmitPayload,
  type QuestionEditorDialogValue,
} from '@/components/question-config/QuestionEditorDialog.vue';
import QuestionPickerDialog from '@/components/question-config/QuestionPickerDialog.vue';
import QuestionImportDialog from '@/components/question-config/QuestionImportDialog.vue';

type QuickViewKey = 'ALL' | 'INCOMPLETE' | 'HARD' | 'OBJECTIVE';

interface HealthStatus {
  label: string;
  progress: number;
  dotClass: string;
  textClass: string;
  barClass: string;
}

interface QuestionFormState {
  code: string;
  type: QuestionType;
  stem: string;
  difficulty: QuestionDifficulty;
  defaultScore: number;
  analysisText: string;
}

const typeOptions: QuestionType[] = [...QUESTION_TYPED_EDITOR_KIND_OPTIONS];
const difficultyOptions: QuestionDifficulty[] = ['EASY', 'MEDIUM', 'HARD'];
const typeLabelMap: Record<QuestionType, string> = TYPED_EDITOR_LABELS;

const difficultyLabelMap: Record<QuestionDifficulty, string> = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战',
};

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const pickerVisible = ref(false);
const importVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<QuestionItem[]>([]);
const configuredQuestions = ref<ConfiguredQuestionItem[]>([]);
const activeQuickView = ref<QuickViewKey>('ALL');
const filters = reactive({ type: '', difficulty: '', keyword: '' });
const form = reactive<QuestionFormState>({
  code: '',
  type: 'SINGLE',
  stem: '',
  difficulty: 'EASY',
  defaultScore: 5,
  analysisText: '',
});

const headerCellStyle = {
  background: '#f8fafc',
  color: '#64748b',
  fontWeight: '700',
  textTransform: 'uppercase',
  fontSize: '12px',
  letterSpacing: '0.08em',
};

const quickViews: Array<{ key: QuickViewKey; label: string }> = [
  { key: 'ALL', label: '全部资产' },
  { key: 'INCOMPLETE', label: '待完善' },
  { key: 'HARD', label: '高难度' },
  { key: 'OBJECTIVE', label: '客观题' },
];

const hardQuestionCount = computed(() => rows.value.filter(item => item.difficulty === 'HARD').length);
const incompleteQuestionCount = computed(
  () => rows.value.filter(item => !item.analysisText?.trim() || !hasRequiredAnswerStructure(item)).length,
);
const currentEditorQuestion = computed<QuestionEditorDialogValue | null>(() => {
  if (!dialogVisible.value) {
    return null;
  }

  return {
    localId: editingId.value ? `question-${editingId.value}` : 'question-new',
    questionBankId: editingId.value,
    sourceType: 'INLINE',
    questionType: form.type,
    stem: form.stem,
    sortOrder: 1,
    score: form.defaultScore,
    options: editingId.value
      ? parseQuestionAnswerArray(rows.value.find(item => item.id === editingId.value)?.optionsJson ?? null).map((option, index) => {
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
        })
      : null,
    answerJson: editingId.value ? rows.value.find(item => item.id === editingId.value)?.answerJson ?? '[]' : '[]',
    scoringConfigJson: null,
    code: form.code,
    difficulty: form.difficulty,
    defaultScore: form.defaultScore,
    analysisText: form.analysisText,
  };
});

const filterSummaryText = computed(() => {
  const parts: string[] = [];
  if (filters.type) {
    parts.push(`题型 ${getTypeLabel(filters.type)}`);
  }
  if (filters.difficulty) {
    parts.push(`难度 ${getDifficultyLabel(filters.difficulty)}`);
  }
  if (filters.keyword.trim()) {
    parts.push(`关键词“${filters.keyword.trim()}”`);
  }
  if (activeQuickView.value !== 'ALL') {
    const currentQuickView = quickViews.find(view => view.key === activeQuickView.value);
    if (currentQuickView) {
      parts.push(currentQuickView.label);
    }
  }

  return parts.length ? `已应用：${parts.join(' / ')}` : '未启用附加筛选';
});

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();

  return [...rows.value]
    .filter(item => {
      const matchesType = !filters.type || item.type === filters.type;
      const matchesDifficulty = !filters.difficulty || item.difficulty === filters.difficulty;
      const matchesKeyword =
        !keyword ||
        item.code.toLowerCase().includes(keyword) ||
        item.stem.toLowerCase().includes(keyword) ||
        item.analysisText.toLowerCase().includes(keyword);

      const matchesQuickView =
        activeQuickView.value === 'ALL' ||
        (activeQuickView.value === 'INCOMPLETE' && getHealthStatus(item).progress < 100) ||
        (activeQuickView.value === 'HARD' && item.difficulty === 'HARD') ||
        (activeQuickView.value === 'OBJECTIVE' && ['SINGLE', 'MULTI', 'JUDGE'].includes(item.type));

      return matchesType && matchesDifficulty && matchesKeyword && matchesQuickView;
    })
    .sort((left, right) => left.code.localeCompare(right.code));
});

const resetForm = () => {
  form.code = '';
  form.type = 'SINGLE';
  form.stem = '';
  form.difficulty = 'EASY';
  form.defaultScore = 5;
  form.analysisText = '';
  editingId.value = null;
};

const resetFilters = () => {
  filters.type = '';
  filters.difficulty = '';
  filters.keyword = '';
  activeQuickView.value = 'ALL';
};

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listQuestions();
  } finally {
    loading.value = false;
  }
};

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openPickerDialog = () => {
  pickerVisible.value = true;
};

const openEditDialog = (row: QuestionItem) => {
  editingId.value = row.id;
  form.code = row.code;
  form.type = isQuestionType(row.type) ? row.type : 'SINGLE';
  form.stem = row.stem;
  form.difficulty = isQuestionDifficulty(row.difficulty) ? row.difficulty : 'EASY';
  form.defaultScore = row.defaultScore;
  form.analysisText = row.analysisText;
  dialogVisible.value = true;
};

const applyPickedQuestions = (items: ConfiguredQuestionItem[]) => {
  configuredQuestions.value = items.map((item, index) => ({
    ...item,
    sortOrder: index + 1,
  }));
  ElMessage.success(`已带入 ${items.length} 道题目配置`);
};

const handleConfiguredQuestionsReorder = (items: ConfiguredQuestionItem[]) => {
  configuredQuestions.value = items;
};

const handleConfiguredQuestionScoreChange = ({ localId, score }: { localId: string; score: number }) => {
  configuredQuestions.value = configuredQuestions.value.map(item => (item.localId === localId ? { ...item, score } : item));
};

const removeConfiguredQuestion = (localId: string) => {
  configuredQuestions.value = configuredQuestions.value
    .filter(item => item.localId !== localId)
    .map((item, index) => ({
      ...item,
      sortOrder: index + 1,
    }));
};

const editConfiguredQuestion = (item: ConfiguredQuestionItem) => {
  if (!item.questionBankId) {
    ElMessage.info('当前配置未绑定题库题目，暂不能跳转编辑。');
    return;
  }

  const matchedRow = rows.value.find(row => row.id === item.questionBankId);
  if (!matchedRow) {
    ElMessage.warning('未在当前题库列表中找到该题目，请先刷新后重试。');
    return;
  }

  openEditDialog(matchedRow);
};

const buildQuestionCode = (type: QuestionType) => {
  const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 14);
  return `Q-${type.slice(0, 3)}-${stamp}`;
};

const toQuestionType = (kind: TypedEditorKind): QuestionType => (kind === 'TEXT' ? 'SHORT' : kind);

const buildPayload = (editorPayload: QuestionEditorDialogSubmitPayload) =>
  buildQuestionPayloadFromDraft(editorPayload.draft, {
    code: editorPayload.code.trim() || buildQuestionCode(toQuestionType(editorPayload.draft.kind)),
    difficulty: editorPayload.difficulty,
    defaultScore: editorPayload.defaultScore,
    analysisText: editorPayload.analysisText,
  });

const submit = async (editorPayload: QuestionEditorDialogSubmitPayload) => {
  submitting.value = true;
  try {
    form.code = editorPayload.code;
    form.type = toQuestionType(editorPayload.draft.kind);
    form.stem = editorPayload.draft.prompt.trim();
    form.difficulty = editorPayload.difficulty;
    form.defaultScore = editorPayload.defaultScore;
    form.analysisText = editorPayload.analysisText;

    const payload = buildPayload(editorPayload);

    if (editingId.value) {
      await updateQuestion(editingId.value, payload);
      ElMessage.success('题目更新成功');
    } else {
      await createQuestion(payload);
      ElMessage.success('题目创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const remove = async (id: number) => {
  await ElMessageBox.confirm('删除题目后无法恢复，是否继续？', '删除确认', { type: 'warning' });
  await deleteQuestion(id);
  ElMessage.success('题目删除成功');
  await fetchData();
};

const applyQuickView = (view: QuickViewKey) => {
  activeQuickView.value = activeQuickView.value === view ? 'ALL' : view;
};

const copyCode = async (code: string) => {
  try {
    await navigator.clipboard.writeText(code);
    ElMessage.success('题目编码已复制');
  } catch {
    ElMessage.warning('当前环境不支持复制，请手动复制题目编码');
  }
};

const getTypeLabel = (type: string) => (isQuestionType(type) ? typeLabelMap[type] : type);

const getDifficultyLabel = (difficulty: string) =>
  isQuestionDifficulty(difficulty) ? difficultyLabelMap[difficulty] : difficulty;

const getDifficultyLevel = (difficulty: string) => {
  if (difficulty === 'HARD') {
    return 3;
  }
  if (difficulty === 'MEDIUM') {
    return 2;
  }
  return 1;
};

const summarizeText = (text: string, limit: number) => {
  const normalized = text.trim();
  return normalized.length > limit ? `${normalized.slice(0, limit)}…` : normalized;
};

const requiresOptions = (item: QuestionItem) => ['SINGLE', 'MULTI', 'JUDGE'].includes(item.type) && getOptionsCount(item) === 0;

const hasFillAnswers = (item: QuestionItem) => {
  if (item.type !== 'FILL') {
    return true;
  }

  const blanks = parseQuestionAnswerArray(item.answerJson);
  return blanks.length > 0 && blanks.every(blank => {
    if (!blank || typeof blank !== 'object') {
      return false;
    }
    const record = blank as Record<string, unknown>;
    const answers = Array.isArray(record.answers) ? record.answers : [];
    return answers.some(answer => String(answer ?? '').trim().length > 0);
  });
};

const hasSubjectiveAnswer = (item: QuestionItem) => {
  if (!['SHORT', 'CODE'].includes(item.type)) {
    return true;
  }

  return parseQuestionAnswerArray(item.answerJson).some(answer => String(answer ?? '').trim().length > 0);
};

const hasRequiredAnswerStructure = (item: QuestionItem) => {
  if (requiresOptions(item)) {
    return false;
  }

  if (!hasFillAnswers(item)) {
    return false;
  }

  if (!hasSubjectiveAnswer(item)) {
    return false;
  }

  return true;
};

const getOptionsCount = (item: QuestionItem) => parseQuestionAnswerArray(item.optionsJson).length;

const getAnswerCount = (item: QuestionItem) => parseQuestionAnswerArray(item.answerJson).length;

const getHealthStatus = (item: QuestionItem): HealthStatus => {
  const hasAnalysis = Boolean(item.analysisText?.trim());
  const hasRequiredAnswerData = hasRequiredAnswerStructure(item);

  if (hasAnalysis && hasRequiredAnswerData) {
    return {
      label: '结构完整',
      progress: 100,
      dotClass: 'is-complete',
      textClass: 'is-complete',
      barClass: 'is-complete',
    };
  }

  if (hasAnalysis || hasRequiredAnswerData) {
    return {
      label: '待补充',
      progress: 66,
      dotClass: 'is-warning',
      textClass: 'is-warning',
      barClass: 'is-warning',
    };
  }

  return {
    label: '草稿结构',
    progress: 36,
    dotClass: 'is-draft',
    textClass: 'is-draft',
    barClass: 'is-draft',
  };
};

const isQuestionType = (value: string): value is QuestionType => typeOptions.includes(value as QuestionType);

const isQuestionDifficulty = (value: string): value is QuestionDifficulty =>
  difficultyOptions.includes(value as QuestionDifficulty);

onMounted(() => {
  resetForm();
  void fetchData();
});
</script>

<style scoped>
.question-console {
  gap: 16px;
}

.question-console__filter-panel,
.question-console__config-shell,
.question-console__table-shell {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.question-console__section-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.question-console__filter-header,
.question-console__config-header,
.question-console__table-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
}

.question-console__title {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.question-console__subtitle {
  margin: 8px 0 0;
  max-width: 720px;
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
}

.question-console__hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.question-console__ghost-button,
.question-console__primary-button {
  min-height: 42px;
  padding-inline: 18px;
  font-weight: 700;
}

.question-console__ghost-button {
  border-color: #cbd5e1;
  color: #334155;
  background: #fff;
}

.question-console__primary-button {
  border-color: #0f766e;
  background: linear-gradient(135deg, #0f766e, #115e59);
}

.question-console__filter-panel,
.question-console__config-shell,
.question-console__table-shell {
  padding: 18px 20px;
}

.question-console__config-subtitle {
  margin: 8px 0 0;
  max-width: 760px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.question-console__section-title {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.question-console__filter-summary,
.question-console__table-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.question-console__summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.question-console__summary-text,
.question-console__table-count {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.question-console__filters-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1.4fr;
  gap: 14px;
  margin-top: 14px;
}

.question-console__field,
.question-console__quick-tags {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-console__field-label {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.question-console__quick-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-console__quick-tag {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.question-console__quick-tag.is-active {
  border-color: #99f6e4;
  background: #ccfbf1;
  color: #115e59;
}

.question-console__table {
  margin-top: 14px;
}

.question-row__identity {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-row__identity-main {
  color: #0f766e;
  font-size: 14px;
  font-weight: 800;
  font-family: 'DM Sans', 'Inter', sans-serif;
  letter-spacing: 0.02em;
}

.question-row__identity-tags,
.question-row__metric-list,
.question-row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-chip,
.question-row__metric-pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.question-chip--type {
  background: #dbeafe;
  color: #1d4ed8;
}

.question-chip--score {
  background: #ecfeff;
  color: #0f766e;
}

.question-row__stem {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-row__stem-text {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.6;
  font-weight: 600;
}

.question-row__stem-subtext {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}

.question-row__metrics,
.question-row__health {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-row__difficulty {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.question-row__difficulty-label {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.question-row__difficulty-stars {
  display: flex;
  gap: 4px;
}

.question-row__difficulty-star {
  color: #cbd5e1;
  font-size: 14px;
}

.question-row__difficulty-star.is-filled {
  color: #f59e0b;
}

.question-row__metric-pill {
  background: #f8fafc;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.question-row__health-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-row__health-dot {
  width: 9px;
  height: 9px;
  border-radius: 999px;
}

.question-row__health-text {
  font-size: 12px;
  font-weight: 800;
}

.question-row__health-dot.is-complete,
.question-row__health-bar-fill.is-complete {
  background: #10b981;
}

.question-row__health-text.is-complete {
  color: #047857;
}

.question-row__health-dot.is-warning,
.question-row__health-bar-fill.is-warning {
  background: #f59e0b;
}

.question-row__health-text.is-warning {
  color: #b45309;
}

.question-row__health-dot.is-draft,
.question-row__health-bar-fill.is-draft {
  background: #94a3b8;
}

.question-row__health-text.is-draft {
  color: #64748b;
}

.question-row__health-bar {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  overflow: hidden;
  background: #e2e8f0;
}

.question-row__health-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.question-row__actions {
  justify-content: flex-end;
}

.question-row__icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #ccfbf1;
  border-radius: 10px;
  background: #f0fdfa;
  color: #0f766e;
  cursor: pointer;
  transition: all 0.2s ease;
}

.question-row__icon-button:hover {
  transform: translateY(-1px);
  background: #ccfbf1;
}

.question-row__icon-button--neutral {
  border-color: #e2e8f0;
  background: #fff;
  color: #475569;
}

.question-row__icon-button--danger {
  border-color: #fecaca;
  background: #fff1f2;
  color: #dc2626;
}

@media (max-width: 1200px) {
  .question-console__filters-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .question-console__filter-panel,
  .question-console__table-shell {
    padding: 18px;
  }

  .question-console__filter-header,
  .question-console__config-header,
  .question-console__table-header,
  .question-console__hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .question-console__filter-summary,
  .question-console__table-meta {
    justify-content: flex-start;
  }

  .question-console__filters-grid {
    grid-template-columns: 1fr;
  }
}
</style>
