<template>
  <el-dialog v-model="dialogVisible" width="860px" class="question-editor-dialog" destroy-on-close>
    <div class="question-editor-dialog__shell">
      <header class="question-editor-dialog__header">
        <div>
          <p class="question-editor-dialog__eyebrow">{{ headerEyebrow }}</p>
          <h2>{{ dialogTitle }}</h2>
          <p class="question-editor-dialog__desc">{{ description }}</p>
        </div>
        <div class="question-editor-dialog__badges">
          <span class="question-editor-dialog__badge">{{ getTypeLabel(form.type) }}</span>
          <span v-if="showAssetFields" class="question-editor-dialog__badge question-editor-dialog__badge--muted">
            {{ getDifficultyLabel(form.difficulty) }}
          </span>
        </div>
      </header>

      <div class="question-editor-dialog__meta-row">
        <span v-if="showAssetFields" class="question-editor-dialog__meta-chip">
          <span class="material-symbols-outlined">qr_code_2</span>
          {{ codeSummary }}
        </span>
        <span class="question-editor-dialog__meta-chip">
          <span class="material-symbols-outlined">dataset</span>
          {{ editorModeSummary }}
        </span>
        <span v-if="showConfigFields" class="question-editor-dialog__meta-chip question-editor-dialog__meta-chip--muted">
          <span class="material-symbols-outlined">tune</span>
          输出字段：sortOrder / score / stem / answerJson / scoringConfigJson
        </span>
      </div>

      <el-form class="question-editor-dialog__form" label-position="top">
        <section class="question-editor-dialog__group">
          <div class="question-editor-dialog__group-title">题目基础配置</div>
          <div class="question-editor-dialog__grid" :class="showAssetFields ? 'question-editor-dialog__grid--3col' : 'question-editor-dialog__grid--2col'">
            <el-form-item label="题型">
              <el-select v-model="form.type" placeholder="请选择题型" @change="handleTypeChange">
                <el-option v-for="item in typeOptions" :key="item" :label="typeLabelMap[item]" :value="item" />
              </el-select>
            </el-form-item>

            <template v-if="showAssetFields">
              <el-form-item label="难度">
                <el-select v-model="form.difficulty" placeholder="请选择难度">
                  <el-option v-for="item in difficultyOptions" :key="item" :label="difficultyLabelMap[item]" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="默认分值">
                <el-input-number v-model="form.defaultScore" :min="1" :max="100" style="width: 100%" />
              </el-form-item>
            </template>

            <template v-else-if="showConfigFields">
              <el-form-item label="排序">
                <el-input-number v-model="form.sortOrder" :min="1" :max="999" style="width: 100%" />
              </el-form-item>
              <el-form-item label="分值">
                <el-input-number v-model="form.score" :min="1" :max="100" style="width: 100%" />
              </el-form-item>
            </template>
          </div>
        </section>

        <section class="question-editor-dialog__group">
          <div class="question-editor-dialog__group-title question-editor-dialog__group-title--row">
            <span>题干与讲评</span>
            <div v-if="form.type === 'FILL'" class="question-editor-dialog__group-actions">
              <el-button size="small" @click="triggerBlankInsert">插入填空位</el-button>
            </div>
          </div>

          <FillBlankEditor
            v-if="form.type === 'FILL'"
            ref="fillBlankEditorRef"
            :prompt="typedEditor.draft.prompt"
            :blanks="typedEditor.draft.fillBlanks"
            @update:prompt="typedEditor.draft.prompt = $event"
            @update-blank-answer="updateBlankAnswer"
            @insert-token="insertBlankToken"
            @remove-blank="removeBlank"
          />

          <div v-else class="question-editor-dialog__fill-main">
            <el-form-item label="题干内容">
              <el-input v-model="typedEditor.draft.prompt" type="textarea" :rows="5" :placeholder="stemPlaceholder" />
            </el-form-item>
          </div>

          <el-form-item v-if="showAssetFields" label="教师解析">
            <el-input
              v-model="form.analysisText"
              type="textarea"
              :rows="4"
              placeholder="填写讲评要点、评分依据或常见误区"
            />
          </el-form-item>
        </section>

        <TypedEditorShell title="结构化答案编辑" :messages="validationVisible ? validationMessages : []">
          <ObjectiveEditor
            v-if="form.type === 'SINGLE' || form.type === 'MULTI' || form.type === 'JUDGE'"
            :kind="form.type"
            :options="typedEditor.draft.objectiveOptions"
            :selected-ids="typedEditor.draft.selectedObjectiveOptionIds"
            :judge-answer="typedEditor.draft.judgeAnswer"
            @add-option="typedEditor.addObjectiveOption"
            @remove-option="typedEditor.removeObjectiveOption"
            @toggle-answer="typedEditor.toggleObjectiveAnswer"
            @update-option-content="updateObjectiveOptionContent"
            @update:judge-answer="typedEditor.draft.judgeAnswer = $event"
          />

          <SubjectiveEditor
            v-else-if="form.type === 'SHORT'"
            :kind="form.type"
            v-model="typedEditor.draft.subjectiveAnswer"
          />
        </TypedEditorShell>
      </el-form>

      <footer class="question-editor-dialog__footer">
        <div class="question-editor-dialog__footer-note">{{ footerNote }}</div>
        <div class="question-editor-dialog__footer-actions">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="loading" @click="submit">{{ submitText }}</el-button>
        </div>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ConfiguredQuestionItem } from '@/types/question-config'
import type { QuestionDifficulty, QuestionItem, QuestionType } from '@/types/question'
import FillBlankEditor from '@/components/typed-editor/FillBlankEditor.vue'
import ObjectiveEditor from '@/components/typed-editor/ObjectiveEditor.vue'
import SubjectiveEditor from '@/components/typed-editor/SubjectiveEditor.vue'
import TypedEditorShell from '@/components/typed-editor/TypedEditorShell.vue'
import { QUESTION_TYPED_EDITOR_KIND_OPTIONS, TYPED_EDITOR_LABELS } from '@/components/typed-editor/constants'
import { buildQuestionCode, normalizeQuestionType } from '@/components/typed-editor/adapters/questionAdapter'
import { useTypedEditor } from '@/components/typed-editor/useTypedEditor'
import type { TypedEditorDraft } from '@/components/typed-editor/types'

interface FillBlankEditorExpose {
  insertTokenAtCursor: () => void
  focusAt: (position: number) => void
}

export interface QuestionEditorDialogValue extends ConfiguredQuestionItem {
  code?: string
  difficulty?: QuestionDifficulty
  defaultScore?: number
  analysisText?: string
}

export interface QuestionEditorDialogSubmitPayload {
  code: string
  type: QuestionType
  difficulty: QuestionDifficulty
  defaultScore: number
  analysisText: string
  draft: TypedEditorDraft
}

const props = withDefaults(
  defineProps<{
    visible: boolean
    initialQuestion?: QuestionEditorDialogValue | null
    loading?: boolean
    editing?: boolean
    description?: string
    submitText?: string
    showAssetFields?: boolean
    showConfigFields?: boolean
  }>(),
  {
    initialQuestion: null,
    loading: false,
    editing: false,
    description: '先选择题型并维护题干与结构化答案；题目编码由系统在打开弹窗时自动生成。',
    submitText: '保存题目',
    showAssetFields: false,
    showConfigFields: true,
  },
)

const emit = defineEmits<{
  'update:visible': [boolean]
  saved: [QuestionEditorDialogSubmitPayload]
}>()

const typeOptions: QuestionType[] = [...QUESTION_TYPED_EDITOR_KIND_OPTIONS]
const difficultyOptions: QuestionDifficulty[] = ['EASY', 'MEDIUM', 'HARD']
const typeLabelMap: Record<QuestionType, string> = TYPED_EDITOR_LABELS
const difficultyLabelMap: Record<QuestionDifficulty, string> = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战',
}

const dialogVisible = computed({
  get: () => props.visible,
  set: value => emit('update:visible', value),
})

const fillBlankEditorRef = ref<FillBlankEditorExpose | null>(null)
const typedEditor = useTypedEditor()
const form = reactive({
  localId: '',
  questionBankId: null as number | null,
  sourceType: 'INLINE' as ConfiguredQuestionItem['sourceType'],
  type: 'SINGLE' as QuestionType,
  sortOrder: 1,
  score: 5,
  difficulty: 'EASY' as QuestionDifficulty,
  defaultScore: 5,
  analysisText: '',
  scoringConfigJson: null as string | null,
})

const headerEyebrow = computed(() => (props.showAssetFields ? '题库管理台 / 资产维护' : '共享题目配置 / 编辑器'))
const footerNote = computed(() =>
  props.showAssetFields
    ? '保存后将写入题库，可在题库页查看。'
    : '页面外层负责最终保存与持久化；此处仅输出标准化题目配置数据。',
)
const dialogTitle = computed(() => (props.editing ? '编辑题目资产' : '新增题目资产'))
const lockedCode = ref('')
const isPersistedEdit = computed(() => Boolean(props.initialQuestion?.code?.trim()))
const codeSummary = computed(() => (props.showAssetFields ? `系统自动生成编码 ${lockedCode.value}` : ''))
const editorModeSummary = typedEditor.modeSummary
const validationMessages = typedEditor.validationMessages
const stemPlaceholder = computed(() =>
  form.type === 'FILL' ? '填写题干，并在需要的位置插入如【填空1】的填空位。' : '填写完整题干、限制条件与作答要求',
)

const submitAttempted = ref(false)
const baselineSignature = ref('')

const interactionSignature = computed(() =>
  JSON.stringify({
    type: form.type,
    prompt: typedEditor.draft.prompt,
    optionContents: typedEditor.draft.objectiveOptions.map(option => option.content),
    selectedMask: typedEditor.draft.objectiveOptions
      .map(option => (typedEditor.draft.selectedObjectiveOptionIds.includes(option.id) ? '1' : '0'))
      .join(''),
    judgeAnswer: typedEditor.draft.judgeAnswer,
    fillAnswers: typedEditor.draft.fillBlanks.map(blank => `${blank.index}:${blank.answersText}`),
    subjectiveAnswer: typedEditor.draft.subjectiveAnswer,
    analysisText: form.analysisText,
  }),
)

const validationVisible = computed(
  () => submitAttempted.value || interactionSignature.value !== baselineSignature.value,
)

const editTargetKey = computed(() => {
  const question = props.initialQuestion
  if (!question) {
    return 'new'
  }
  return question.questionBankId != null ? `bank-${question.questionBankId}` : question.localId ?? 'new'
})

const getTypeLabel = (type: QuestionType) => typeLabelMap[type] ?? type
const getDifficultyLabel = (difficulty: QuestionDifficulty) => difficultyLabelMap[difficulty] ?? difficulty

const hydrateEditor = (question: QuestionEditorDialogValue | null) => {
  const normalizedType = normalizeQuestionType(question?.questionType)

  form.localId = question?.localId ?? `inline-${Date.now()}`
  form.questionBankId = question?.questionBankId ?? null
  form.sourceType = question?.sourceType ?? 'INLINE'
  form.type = normalizedType
  form.sortOrder = question?.sortOrder ?? 1
  form.score = question?.score ?? 5
  form.difficulty = question?.difficulty ?? 'EASY'
  form.defaultScore = question?.defaultScore ?? question?.score ?? 5
  form.analysisText = question?.analysisText ?? ''
  form.scoringConfigJson = question?.scoringConfigJson ?? null

  const hydratedRow: QuestionItem = {
    id: question?.questionBankId ?? 0,
    code: question?.code ?? '',
    type: normalizedType,
    stem: question?.stem ?? '',
    difficulty: question?.difficulty ?? 'EASY',
    defaultScore: question?.defaultScore ?? question?.score ?? 5,
    optionsJson: question?.options?.length
      ? JSON.stringify(
          question.options.map(option => ({
            key: option.key,
            content: option.label,
          })),
        )
      : null,
    answerJson: question?.answerJson ?? '[]',
    analysisText: question?.analysisText ?? '',
  }

  typedEditor.hydrateFromQuestion(hydratedRow)
  if (!question) {
    typedEditor.reset(form.type)
  }

  // 打开即锁定编码：题库题沿用原编码，新建/内联题按归一后的题型生成一次
  lockedCode.value = question?.code?.trim() || buildQuestionCode(normalizedType)
  submitAttempted.value = false
  baselineSignature.value = interactionSignature.value
  void nextTick(() => {
    // FILL 题干 watcher 在本轮 flush 中可能重排空位，据此刷新基线
    baselineSignature.value = interactionSignature.value
  })
}

const handleTypeChange = (value: QuestionType) => {
  const cleared = typedEditor.setKind(value)
  if (cleared) {
    ElMessage.warning('已切换为单选题，请重新选择正确答案')
  }
  if (!isPersistedEdit.value) {
    lockedCode.value = buildQuestionCode(value)
  }
}

const updateObjectiveOptionContent = ({ id, content }: { id: string; content: string }) => {
  typedEditor.draft.objectiveOptions = typedEditor.draft.objectiveOptions.map(option =>
    option.id === id ? { ...option, content } : option,
  )
}

const updateBlankAnswer = ({ index, answersText }: { index: number; answersText: string }) => {
  typedEditor.draft.fillBlanks = typedEditor.draft.fillBlanks.map(blank =>
    blank.index === index ? { ...blank, answersText } : blank,
  )
}

const removeBlank = (index: number) => {
  typedEditor.removeBlank(index)
}

const triggerBlankInsert = () => {
  fillBlankEditorRef.value?.insertTokenAtCursor()
}

const insertBlankToken = async (selection?: { start: number; end: number }) => {
  const inserted = typedEditor.insertBlankToken(selection)

  if (inserted) {
    await nextTick()
    fillBlankEditorRef.value?.focusAt(inserted.nextCursor)
  }
}

const submit = () => {
  submitAttempted.value = true

  if (validationMessages.value.length) {
    ElMessage.warning(validationMessages.value[0])
    return
  }

  emit('saved', {
    code: lockedCode.value,
    type: form.type,
    difficulty: form.difficulty,
    defaultScore: form.defaultScore,
    analysisText: form.analysisText.trim(),
    draft: {
      kind: typedEditor.draft.kind,
      prompt: typedEditor.draft.prompt,
      objectiveOptions: typedEditor.draft.objectiveOptions.map(option => ({ ...option })),
      selectedObjectiveOptionIds: [...typedEditor.draft.selectedObjectiveOptionIds],
      judgeAnswer: typedEditor.draft.judgeAnswer,
      fillBlanks: typedEditor.draft.fillBlanks.map(blank => ({ ...blank })),
      subjectiveAnswer: typedEditor.draft.subjectiveAnswer,
    },
  })
}

watch(
  () => [props.visible, editTargetKey.value] as const,
  ([visible]) => {
    if (visible) {
      hydrateEditor(props.initialQuestion ?? null)
    }
  },
  { immediate: true },
)

watch(
  () => props.loading,
  (loading, previous) => {
    if (previous && !loading && props.visible && !isPersistedEdit.value) {
      lockedCode.value = buildQuestionCode(form.type)
    }
  },
)
</script>

<style scoped>
.question-editor-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  max-height: min(84vh, 920px);
}

.question-editor-dialog :deep(.el-dialog__header) {
  display: none;
}

.question-editor-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.question-editor-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  max-height: min(84vh, 920px);
  overflow: auto;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.question-editor-dialog__header,
.question-editor-dialog__group,
.question-editor-dialog__footer,
.question-editor-dialog__validation {
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
}

.question-editor-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(240, 253, 250, 0.95));
}

.question-editor-dialog__eyebrow {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.question-editor-dialog__header h2 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.question-editor-dialog__desc {
  margin: 4px 0 0;
  max-width: 560px;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.question-editor-dialog__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.question-editor-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #115e59;
  font-size: 11px;
  font-weight: 800;
}

.question-editor-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.question-editor-dialog__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-editor-dialog__meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid #dbe4f0;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.question-editor-dialog__meta-chip .material-symbols-outlined {
  font-size: 16px;
}

.question-editor-dialog__meta-chip--muted {
  background: #f8fafc;
  color: #64748b;
}

.question-editor-dialog__group,
.question-editor-dialog__footer {
  padding: 14px;
}

.question-editor-dialog__group-title {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.question-editor-dialog__group-title--row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.question-editor-dialog__group-actions {
  display: flex;
  gap: 8px;
}

.question-editor-dialog__grid {
  display: grid;
  gap: 14px;
}

.question-editor-dialog__grid--3col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.question-editor-dialog__grid--2col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.question-editor-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-editor-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.question-editor-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.question-editor-dialog__form :deep(.el-input__wrapper),
.question-editor-dialog__form :deep(.el-select__wrapper),
.question-editor-dialog__form :deep(.el-textarea__inner),
.question-editor-dialog__form :deep(.el-input-number) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.question-editor-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.question-editor-dialog__footer-note {
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

.question-editor-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .question-editor-dialog__grid--3col,
  .question-editor-dialog__grid--2col {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .question-editor-dialog__header,
  .question-editor-dialog__footer,
  .question-editor-dialog__group-title--row {
    flex-direction: column;
    align-items: stretch;
  }

  .question-editor-dialog__badges {
    justify-content: flex-start;
  }
}
</style>
