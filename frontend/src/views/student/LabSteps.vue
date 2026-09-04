<template>
  <div class="lab-steps-page workbench-page workbench-page--locked" v-loading="loading">
    <div class="page-header">
      <div class="page-header__main">
        <div class="steps-eyebrow">学生端 / 实验题项作答</div>
        <h2>{{ detail?.title || '实验作答' }}</h2>
        <p>{{ detail?.description || '按题目顺序作答。' }}</p>
        <div v-if="detail" class="page-header__meta">
          <span class="header-meta-chip">{{ submissionLabel(detail.submissionStatus) }}</span>
          <span class="header-meta-chip">{{ currentIndex + 1 }}/{{ labItems.length }}</span>
          <span class="header-meta-chip">{{ detail.summaryRequired ? '实验小结必填' : '实验小结选填' }}</span>
          <span class="header-meta-chip">{{ readonly ? '只读' : '可编辑' }}</span>
        </div>
      </div>
      <div class="page-header__actions">
        <el-button @click="router.push(`/student/labs/${labId}`)">返回实验详情</el-button>
        <el-button type="success" :loading="submitting" :disabled="readonly || !labItems.length" @click="submitAll">
          提交实验
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !detail" description="未获取到实验题项数据" />

    <template v-else-if="detail">
      <section class="steps-layout">
        <aside class="steps-sidebar">
          <article class="sidebar-card status-card">
            <div class="status-card__header">
              <div>
                <span class="status-card__eyebrow">当前状态</span>
                <strong>{{ submissionLabel(detail.submissionStatus) }}</strong>
              </div>
              <el-tag :type="submissionTagType" effect="light">{{ readonly ? '只读' : '可编辑' }}</el-tag>
            </div>
            <div class="status-card__meta">
              <span>{{ labItems.length }} 个题项</span>
              <span>{{ detail.summaryRequired ? '实验小结必填' : '实验小结选填' }}</span>
            </div>
            <div class="progress-panel">
              <div class="progress-panel__header">
                <span>已保存进度</span>
                <span>{{ progressPercent }}%</span>
              </div>
              <div class="progress-track"><span class="progress-fill" :style="{ width: `${progressPercent}%` }"></span></div>
              <p>{{ progressText }}</p>
            </div>
          </article>

          <article class="sidebar-card workflow-card">
            <div class="workflow-card__header">
              <div>
                <h3>题项导航</h3>
                <p>切换题目或提交前，请先保存当前内容。</p>
              </div>
            </div>
            <div class="workflow-list">
              <button
                v-for="(item, index) in labItems"
                :key="item.id"
                type="button"
                class="workflow-item"
                :class="{
                  'workflow-item--active': index === currentIndex,
                  'workflow-item--done': hasSavedAnswer(item),
                  'workflow-item--dirty': isItemDirty(item),
                }"
                @click="selectItem(index)"
              >
                <div class="workflow-item__badge">{{ item.stepNo }}</div>
                <div class="workflow-item__body">
                  <span class="workflow-item__eyebrow">{{ answerStateLabel(item) }}</span>
                  <strong>{{ item.title }}</strong>
                  <small>{{ questionTypeLabel(item.questionType) }} · {{ item.stepScore }} 分</small>
                </div>
              </button>
            </div>
          </article>
        </aside>

        <div class="steps-main">
          <div class="steps-main__scroll">
            <section v-if="currentItem" class="question-focus-strip">
              <div class="question-focus-strip__main">
                <span class="question-focus-strip__eyebrow">当前题目</span>
                <strong>第 {{ currentItem.stepNo }} 题 · {{ currentItem.title }}</strong>
                <small>{{ questionTypeLabel(currentItem.questionType) }} · {{ currentItem.stepScore }} 分</small>
              </div>
              <div class="question-focus-strip__status">
                <span class="focus-state-pill" :class="focusStateClass">{{ currentSaveStateLabel }}</span>
              </div>
            </section>

            <section v-if="currentItem" class="question-card">
              <header class="question-card__header">
                <div>
                  <div class="question-card__eyebrow">第 {{ currentItem.stepNo }} 题 / {{ questionTypeLabel(currentItem.questionType) }}</div>
                  <h3>{{ currentItem.title }}</h3>
                </div>
                <div class="question-card__score">{{ currentItem.stepScore }} 分</div>
              </header>

              <div class="question-card__body">
                <section class="question-card__prompt" :class="{ 'question-card__prompt--embedded': currentQuestionType === 'FILL_BLANK' }">
                  <div class="pane-title">题目</div>
                  <FillBlankInlineEditor
                    v-if="currentQuestionType === 'FILL_BLANK'"
                    :model-value="currentDraft"
                    :prompt="currentItem.content"
                    :blanks="currentItem.blanks"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />
                  <div v-else class="prompt-text">{{ currentItem.content }}</div>
                </section>

                <section v-if="currentItem.teacherComment || currentItem.score !== null && currentItem.score !== undefined" class="feedback-card">
                  <div class="pane-title">当前反馈</div>
                  <p>{{ itemFeedback(currentItem) }}</p>
                </section>

                <QuestionAnswerShell
                  v-if="currentQuestionType !== 'FILL_BLANK'"
                  title="作答区"
                  :hint="answerHint(currentItem.questionType)"
                  :chip="currentItemCodeLanguage ? `${questionTypeLabel(currentItem.questionType)} · ${currentItemCodeLanguage}` : questionTypeLabel(currentItem.questionType)"
                >
                  <template #callout>
                    <div v-if="currentQuestionType === 'CODE'" class="code-callout">
                      <div class="code-callout__title">代码作答</div>
                      <p>请在下方输入代码内容。</p>
                    </div>
                  </template>

                  <ChoiceAnswerEditor
                    v-if="currentQuestionType === 'SINGLE_CHOICE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="single"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'MULTIPLE_CHOICE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="multiple"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <ChoiceAnswerEditor
                    v-else-if="currentQuestionType === 'TRUE_FALSE'"
                    :model-value="currentDraft"
                    :options="currentItem.options"
                    mode="judge"
                    :disabled="readonly"
                    @update:model-value="updateCurrentDraft"
                  />

                  <CodeLabAnswerEditor
                    v-else-if="currentQuestionType === 'CODE'"
                    :model-value="currentDraft"
                    :language="currentItemCodeLanguage"
                    :rows="16"
                    :maxlength="20000"
                    :disabled="readonly"
                    placeholder="// 在此输入代码答案"
                    @update:model-value="updateCurrentDraft"
                  />

                  <TextAnswerEditor
                    v-else-if="currentQuestionType === 'SHORT_ANSWER'"
                    :model-value="currentDraft"
                    :lab-id="labId"
                    :step-id="currentItem.id"
                    enable-images
                    :rows="7"
                    :maxlength="3000"
                    :disabled="readonly"
                    placeholder="请输入简答内容"
                    @update:model-value="updateCurrentDraft"
                    @images-removed="syncImagesAfterRemove"
                  />

                  <TextAnswerEditor
                    v-else
                    :model-value="currentDraft"
                    :lab-id="labId"
                    :step-id="currentItem.id"
                    enable-images
                    :rows="12"
                    :maxlength="5000"
                    :disabled="readonly"
                    placeholder="请输入本题作答内容"
                    @update:model-value="updateCurrentDraft"
                    @images-removed="syncImagesAfterRemove"
                  />
                </QuestionAnswerShell>
              </div>
            </section>

            <section class="summary-card">
              <div class="summary-card__header">
                <div>
                  <div class="summary-card__eyebrow">实验小结</div>
                  <h3>实验小结</h3>
                </div>
                <el-tag :type="detail.summaryRequired ? 'warning' : 'info'" effect="light">
                  {{ detail.summaryRequired ? '必填' : '选填' }}
                </el-tag>
              </div>
              <p class="summary-card__desc">
                {{ detail.summaryRequired ? '提交前需填写实验小结。' : '可在提交前补充实验小结。' }}
              </p>
              <div v-if="detail.summaryRequired && !summaryDraft.trim() && !readonly" class="summary-required-tip">
                请在提交前补充实验小结。
              </div>
              <el-input
                v-model="summaryDraft"
                type="textarea"
                :rows="6"
                maxlength="5000"
                show-word-limit
                :disabled="readonly"
                placeholder="请输入实验小结、结论或问题分析"
              />
            </section>
          </div>

          <footer class="sticky-footer">
            <div class="sticky-footer__status">
              <span class="material-symbols-outlined">check_circle</span>
              <span>{{ footerStatusText }}</span>
            </div>
            <div class="sticky-footer__actions">
              <el-button :disabled="currentIndex === 0" @click="goPrev">上一题</el-button>
              <el-button :disabled="currentIndex === labItems.length - 1" @click="goNext">下一题</el-button>
              <el-button type="primary" :loading="saving" :disabled="readonly || !currentItem" @click="saveCurrent">保存当前题项</el-button>
              <el-button type="success" :loading="submitting" :disabled="readonly || !labItems.length" @click="submitAll">
                提交实验
              </el-button>
            </div>
          </footer>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import QuestionAnswerShell from './components/lab-answer/QuestionAnswerShell.vue';
import ChoiceAnswerEditor from './components/lab-answer/ChoiceAnswerEditor.vue';
import FillBlankInlineEditor from './components/lab-answer/FillBlankInlineEditor.vue';
import TextAnswerEditor from './components/lab-answer/TextAnswerEditor.vue';
import CodeLabAnswerEditor from './components/lab-answer/CodeLabAnswerEditor.vue';
import { getStudentLabDetail, saveStudentLabAnswer, submitStudentLab } from '@/api/labs';
import type { LabQuestionType, LabReportStatus, StudentLabDetail, StudentLabStepItem } from '@/types/lab';
import {
  hydrateDraftFromStep,
  buildAnswerPayloadJson,
  buildCompatibilityAnswerText,
} from './components/lab-answer/answerPayload';
import { isDraftAnswered, normalizeStudentAnswerQuestionType, type LabAnswerDraft } from './components/lab-answer/types';
import { useLabAnswerDrafts } from './components/lab-answer/useLabAnswerDrafts';

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
const readonly = computed(() => ['SUBMITTED', 'GRADED'].includes(detail.value?.submissionStatus || ''));
const answeredCount = computed(() => labItems.value.filter((item) => hasSavedAnswer(item)).length);
const dirtyItemCount = computed(() => labItems.value.filter((item) => isItemDirty(item)).length);
const progressPercent = computed(() => {
  const total = labItems.value.length;
  if (!total) return 0;
  if (detail.value?.submissionStatus === 'GRADED') return 100;
  if (detail.value?.submissionStatus === 'SUBMITTED') return 92;
  return Math.round((answeredCount.value / total) * 100);
});
const progressText = computed(() => {
  if (detail.value?.submissionStatus === 'GRADED') return '实验已完成批改，可逐题查看教师反馈。';
  if (detail.value?.submissionStatus === 'SUBMITTED') return '实验已提交，当前内容仅支持查看。';
  if (dirtyItemCount.value > 0) return `还有 ${dirtyItemCount.value} 道题未保存。`;
  return `已保存 ${answeredCount.value}/${labItems.value.length} 个题项。`;
});
const submissionTagType = computed(() => {
  if (detail.value?.submissionStatus === 'GRADED') return 'success';
  if (detail.value?.submissionStatus === 'SUBMITTED') return 'warning';
  if (detail.value?.submissionStatus === 'SAVED') return 'primary';
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
  currentIndex.value = index;
  await updateItemQuery(target.id);
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
  if (!currentItem.value || readonly.value || !isItemDirty(currentItem.value)) {
    return;
  }
  clearAutosaveTimer();
  itemAutosaveStates[currentItem.value.id] = 'pending';
  autosaveTimer = setTimeout(() => {
    autosaveTimer = null;
    void persistCurrentItem({ silent: true });
  }, 1200);
};

const saveCurrent = async () => {
  if (!currentItem.value) {
    return;
  }

  const draft = ensureDraft(currentItem.value);
  if (!isDraftAnswered(draft)) {
    ElMessage.warning('请输入文字或上传图片后再保存');
    return;
  }

  await persistCurrentItem();
};

const goPrev = async () => {
  if (currentIndex.value > 0) {
    const saved = await persistCurrentItem({ silent: true });
    if (!saved) {
      return;
    }
    await selectItem(currentIndex.value - 1);
  }
};

const goNext = async () => {
  if (currentIndex.value < labItems.value.length - 1) {
    const saved = await persistCurrentItem({ silent: true });
    if (!saved) {
      return;
    }
    await selectItem(currentIndex.value + 1);
  }
};

const submitAll = async () => {
  if (!detail.value) {
    return;
  }
  if (!labItems.value.some((item) => hasSavedAnswer(item))) {
    ElMessage.warning('请至少先保存一道题目后再提交');
    return;
  }
  if (dirtyItemCount.value > 0) {
    const saved = await persistCurrentItem({ silent: true });
    if (!saved) {
      ElMessage.error('当前题项自动保存失败，请稍后重试');
      return;
    }
  }
  if (detail.value.summaryRequired && !summaryDraft.value.trim()) {
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
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const submissionLabel = (status?: LabReportStatus | null) => {
  if (status === 'SAVED') return '已保存草稿';
  if (status === 'SUBMITTED') return '已提交';
  if (status === 'GRADED') return '已批改';
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

watch(() => route.query.item, () => {
  void syncCurrentItemWithQuery();
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

onMounted(fetchData);

onBeforeUnmount(() => {
  clearAutosaveTimer();
});
</script>

<style scoped>
.lab-steps-page {
  --steps-primary: #1e40af;
  --steps-primary-soft: #eff6ff;
  --steps-border: #dbe5ef;
  --steps-text: #0f172a;
  --steps-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-shrink: 0;
}

.page-header__main {
  min-width: 0;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.page-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.header-meta-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.steps-eyebrow,
.status-card__eyebrow,
.workflow-item__eyebrow,
.question-card__eyebrow,
.summary-card__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.steps-eyebrow {
  margin-bottom: 8px;
  color: var(--steps-primary);
}

.page-header h2,
.workflow-card h3,
.question-card h3,
.summary-card h3 {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
}

.page-header p,
.progress-panel p,
.feedback-card p,
.sticky-footer__status,
.workflow-card__header p,
.answer-card__hint,
.summary-card__desc,
.code-callout p,
.choice-callout {
  color: var(--steps-muted);
  line-height: 1.6;
}

.page-header p {
  margin: 6px 0 0;
  font-size: 13px;
}

.steps-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 880px);
  gap: 18px;
  align-items: stretch;
  justify-content: center;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  height: 100%;
}

.steps-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workflow-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.workflow-card__header {
  flex-shrink: 0;
}

.sidebar-card,
.question-focus-strip,
.question-card,
.summary-card,
.sticky-footer {
  border: 1px solid var(--steps-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.sidebar-card,
.summary-card {
  padding: 16px;
}

.status-card__header,
.summary-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.status-card__eyebrow,
.workflow-item__eyebrow,
.question-card__eyebrow,
.summary-card__eyebrow {
  color: var(--steps-primary);
}

.status-card strong,
.workflow-item strong,
.question-card__score {
  color: var(--steps-text);
}

.status-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.status-card__meta span {
  padding: 5px 10px;
  border-radius: 999px;
  background: #f8fafc;
}

.progress-panel {
  margin-top: 14px;
}

.progress-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.progress-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1e40af, #60a5fa);
}

.progress-panel p {
  margin: 8px 0 0;
  font-size: 12px;
}

.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: auto;
  padding-right: 4px;
  overscroll-behavior: contain;
}

.workflow-item {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--steps-border);
  border-radius: 12px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}

.workflow-item__badge {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #e2e8f0;
  color: #475569;
  font-weight: 700;
}

.workflow-item__body {
  min-width: 0;
}

.workflow-item__body strong {
  display: block;
  margin-top: 2px;
  font-size: 14px;
}

.workflow-item__body small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--steps-muted);
}

.workflow-item--active {
  border-color: #93c5fd;
  background: var(--steps-primary-soft);
}

.workflow-item--active .workflow-item__badge {
  background: var(--steps-primary);
  color: #fff;
}

.workflow-item--done .workflow-item__badge {
  background: #dcfce7;
  color: #15803d;
}

.workflow-item--dirty {
  border-color: #fbbf24;
  background: #fffbeb;
}

.steps-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.steps-main__scroll {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
  overscroll-behavior: contain;
}

.question-focus-strip {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 1));
}

.question-focus-strip__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.question-focus-strip__eyebrow {
  color: var(--steps-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.question-focus-strip__main strong {
  color: var(--steps-text);
  font-size: 16px;
}

.question-focus-strip__main small,
.question-focus-strip__status {
  color: var(--steps-muted);
  font-size: 12px;
}

.question-focus-strip__status {
  display: flex;
  align-items: center;
}

.focus-state-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--steps-primary-soft);
  color: var(--steps-primary);
  font-size: 12px;
  font-weight: 700;
}

.focus-state-pill--saved {
  background: #dcfce7;
  color: #15803d;
}

.focus-state-pill--saving,
.focus-state-pill--dirty {
  background: #fffbeb;
  color: #b45309;
}

.focus-state-pill--error {
  background: #fef2f2;
  color: #b91c1c;
}

.focus-state-pill--readonly,
.focus-state-pill--idle {
  background: #f1f5f9;
  color: #475569;
}

.question-card {
  padding: 18px 20px;
}

.question-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--steps-border);
}

.question-card__header h3 {
  font-size: 22px;
}

.question-card__score {
  padding: 6px 10px;
  border-radius: 12px;
  background: var(--steps-primary-soft);
  font-size: 14px;
  font-weight: 700;
}

.question-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
}

.question-card__prompt,
.feedback-card,
.answer-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
}

.question-card__prompt--embedded {
  line-height: 1.9;
}

.pane-title {
  margin-bottom: 8px;
  color: var(--steps-text);
  font-weight: 700;
}

.prompt-text {
  white-space: pre-wrap;
  line-height: 1.65;
  color: var(--steps-text);
}

.answer-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.answer-card__hint {
  margin: 0;
}

.save-state-banner--saved {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.save-state-banner--saving,
.save-state-banner--dirty {
  border-color: #fcd34d;
  background: #fffbeb;
  color: #92400e;
}

.save-state-banner--error {
  border-color: #fca5a5;
  background: #fef2f2;
  color: #b91c1c;
}

.save-state-banner--readonly,
.save-state-banner--idle {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #475569;
}

.code-callout,
.summary-required-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  font-size: 12px;
}

.code-callout {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.code-callout__title {
  margin-bottom: 6px;
  color: #1d4ed8;
  font-weight: 700;
}

.summary-required-tip {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
}

.sticky-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(14px);
  flex-shrink: 0;
}

.sticky-footer__status {
  display: inline-flex;
  gap: 10px;
  align-items: center;
}

.sticky-footer__status .material-symbols-outlined {
  color: #22c55e;
}

.sticky-footer__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

@media (max-width: 1180px) {
  .lab-steps-page {
    height: auto;
    overflow: visible;
    padding-bottom: 96px;
  }

  .steps-layout {
    grid-template-columns: 1fr;
    justify-content: stretch;
    overflow: visible;
  }

  .steps-sidebar {
    overflow: visible;
  }

  .workflow-list,
  .steps-main,
  .steps-main__scroll {
    overflow: visible;
  }
}

@media (max-width: 900px) {
  .page-header,
  .question-focus-strip,
  .question-card__header,
  .summary-card__header,
  .answer-card__header,
  .sticky-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-header__actions,
  .sticky-footer__actions {
    justify-content: flex-start;
  }

  .question-focus-strip__status {
    align-items: flex-start;
  }
}
</style>
