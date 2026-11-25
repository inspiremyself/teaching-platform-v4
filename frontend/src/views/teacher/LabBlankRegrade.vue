<template>
  <div class="blank-regrade-page">
    <section class="blank-regrade-header">
      <div class="blank-regrade-header__top">
        <div class="blank-regrade-header__main">
          <h1>实验填空批量重判台</h1>
          <p>维护当前实验填空题的可接受答案，保存后立即回刷分布并触发重评分。</p>
        </div>
        <div class="blank-regrade-hero__actions">
          <el-button plain :disabled="!previousClassLab" @click="goLab(previousClassLab?.id)">上一个班级</el-button>
          <el-button plain :disabled="!nextClassLab" @click="goLab(nextClassLab?.id)">下一个班级</el-button>
          <el-button plain @click="goBatchRegrade">批量重判总览</el-button>
          <el-button plain @click="refreshCurrentDistribution" :disabled="!selectedExperimentItemId">刷新分布</el-button>
          <el-button type="primary" :loading="saving" :disabled="!selectedExperimentItemId" @click="saveAcceptedAnswers">保存可接受答案</el-button>
        </div>
      </div>

      <div class="blank-regrade-header__meta">
        <span class="blank-regrade-chip"><strong>{{ blankItems.length }}</strong> 个填空题项</span>
        <span class="blank-regrade-chip"><strong>{{ answerDistributionState?.acceptedAnswers.length ?? 0 }}</strong> 个可接受答案</span>
        <span class="blank-regrade-chip"><strong>{{ answerDistributionState?.answerDistribution.length ?? 0 }}</strong> 条答案分布</span>
        <span class="blank-regrade-chip blank-regrade-chip--accent">{{ selectedItemLabel }} · {{ lastRegradedCountLabel }}</span>
      </div>
    </section>

    <div class="blank-regrade-layout">
      <aside class="blank-regrade-sidebar">
        <section class="blank-regrade-panel">
          <div class="blank-regrade-panel__header">
            <div>
              <div class="blank-regrade-section-kicker">题项列表</div>
              <h2>填空题项列表</h2>
            </div>
            <span class="blank-regrade-chip">{{ blankItems.length }} 个题项</span>
          </div>

          <div v-loading="loadingItems" class="blank-regrade-item-list">
            <template v-if="blankItems.length">
              <button
                v-for="item in blankItems"
                :key="item.id"
                type="button"
                class="blank-regrade-item-card"
                :class="{ 'is-active': item.id === selectedExperimentItemId }"
                @click="selectItem(item.id)"
              >
                <div class="blank-regrade-item-card__main">
                  <div class="blank-regrade-item-card__badge">{{ String(item.stepNo).padStart(2, '0') }}</div>
                  <div>
                    <div class="blank-regrade-item-card__topline">
                      <span>题项 {{ item.stepNo }}</span>
                      <span>{{ questionTypeLabel(item.questionType) }}</span>
                    </div>
                    <strong>{{ item.title }}</strong>
                    <p>{{ item.content || '暂无题项说明' }}</p>
                  </div>
                </div>
                <div class="blank-regrade-item-card__meta">
                  <span>{{ item.stepScore }} 分</span>
                  <span>ID #{{ item.id }}</span>
                </div>
              </button>
            </template>
            <div v-else class="blank-regrade-empty blank-regrade-empty--compact">
              <strong>当前实验暂无填空题</strong>
              <p>返回实验步骤配置后新增填空题项，再回到这里执行批量重判。</p>
            </div>
          </div>
        </section>
      </aside>

      <section id="blank-regrade-detail-panel" class="blank-regrade-main">
        <section class="blank-regrade-panel">
          <div class="blank-regrade-panel__header blank-regrade-panel__header--split">
            <div>
              <div class="blank-regrade-section-kicker">答案分布</div>
              <h2>答案分布与可接受答案</h2>
            </div>
            <div class="blank-regrade-toolbar">
              <span class="blank-regrade-chip">{{ selectedDistributionRows.length }} 条已勾选</span>
              <span class="blank-regrade-chip">{{ lastRegradedCountLabel }}</span>
            </div>
          </div>

          <div class="blank-regrade-current-item">
            <template v-if="answerDistributionState?.item">
              <div class="blank-regrade-current-item__summary">
                <div class="blank-regrade-current-item__title-line">
                  <strong>{{ answerDistributionState.item.title }}</strong>
                  <span class="blank-regrade-chip blank-regrade-chip--soft">题项 {{ answerDistributionState.item.stepNo }}</span>
                </div>
                <p>{{ answerDistributionState.item.content || '暂无题项说明' }}</p>
                <div class="blank-regrade-current-item__meta-strip">
                  <span>{{ answerDistributionState.item.stepScore }} 分</span>
                  <span>{{ answerDistributionState.answerDistribution.length }} 条分布</span>
                  <span>{{ answerDistributionState.acceptedAnswers.length }} 个已收录答案</span>
                </div>
              </div>
              <div class="blank-regrade-current-item__accepted">
                <span class="blank-regrade-section-kicker">当前可接受答案</span>
                <div class="blank-regrade-accepted-list">
                  <span v-for="answer in answerDistributionState.acceptedAnswers" :key="answer" class="blank-regrade-answer-chip">{{ answer }}</span>
                  <span v-if="!answerDistributionState.acceptedAnswers.length" class="blank-regrade-answer-chip blank-regrade-answer-chip--muted">尚未设置</span>
                </div>
              </div>
            </template>
            <div v-else class="blank-regrade-empty">
              <strong>先从左侧选择一个填空题项</strong>
              <p>选中后即可查看答案分布、勾选新的可接受答案，并直接触发重判。</p>
            </div>
          </div>

          <el-table
            v-loading="loadingDistribution"
            :data="answerDistributionState?.answerDistribution || []"
            row-key="normalizedAnswer"
            class="blank-regrade-table"
            @selection-change="handleSelectionChange"
            ref="distributionTableRef"
          >
            <el-table-column type="selection" width="56" reserve-selection />
            <el-table-column prop="answerText" label="学生答案" min-width="240" show-overflow-tooltip />
            <el-table-column prop="normalizedAnswer" label="归一化答案" min-width="220" show-overflow-tooltip />
            <el-table-column prop="count" label="人数" width="100" />
            <el-table-column label="当前判对" width="120">
              <template #default="scope">
                <AppStatusTag v-bind="resolveStatusOf('regradeDecision', scope.row.accepted ? 'ACCEPTED' : 'REJECTED')" />
              </template>
            </el-table-column>
          </el-table>

          <div v-if="answerDistributionState && !answerDistributionState.answerDistribution.length && !loadingDistribution" class="blank-regrade-empty blank-regrade-empty--table">
            <strong>当前题项还没有可展示的答案分布</strong>
            <p>可点击“刷新分布”重新获取统计，或在学生有新作答后再回来执行重判。</p>
          </div>
        </section>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import { resolveStatusOf } from '@/components/ui/statusPresentation';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { ElTable } from 'element-plus';
import {
  getTeacherLabBlankAnswerDistribution,
  listTeacherLabs,
  listTeacherLabBlankItems,
  saveTeacherLabBlankAcceptedAnswers,
} from '@/api/labs';
import type {
  LabItem,
  LabQuestionType,
  TeacherLabBlankAnswerDistribution,
  TeacherLabBlankAnswerDistributionItem,
  TeacherLabBlankItem,
} from '@/types/lab';

const route = useRoute();
const router = useRouter();

const loadingItems = ref(false);
const loadingDistribution = ref(false);
const saving = ref(false);
const labs = ref<LabItem[]>([]);
const blankItems = ref<TeacherLabBlankItem[]>([]);
const answerDistributionState = ref<TeacherLabBlankAnswerDistribution | null>(null);
const selectedExperimentItemId = ref<number | null>(null);
const selectedDistributionRows = ref<TeacherLabBlankAnswerDistributionItem[]>([]);
const lastRegradedCount = ref<number | null>(null);
const distributionTableRef = ref<InstanceType<typeof ElTable> | null>(null);

const labId = computed(() => Number(route.params.id));
const currentLab = computed(() => labs.value.find((item) => item.id === labId.value) || null);
const relatedLabs = computed(() => sortLabs(labs.value));
const currentLabIndex = computed(() => relatedLabs.value.findIndex((item) => item.id === labId.value));
const previousClassLab = computed(() => currentLabIndex.value > 0 ? relatedLabs.value[currentLabIndex.value - 1] : null);
const nextClassLab = computed(() => currentLabIndex.value >= 0 ? relatedLabs.value[currentLabIndex.value + 1] || null : null);

const sortLabs = (items: LabItem[]) => [...items].sort((left, right) => {
  const leftClass = left.className || String(left.classId ?? '');
  const rightClass = right.className || String(right.classId ?? '');
  return leftClass.localeCompare(rightClass, 'zh-CN', { numeric: true });
});

const selectedItemLabel = computed(() => {
  const item = blankItems.value.find((entry) => entry.id === selectedExperimentItemId.value);
  return item ? `题项 ${item.stepNo}` : '尚未选择题项';
});

const lastRegradedCountLabel = computed(() => lastRegradedCount.value == null ? '未执行重判' : `重判 ${lastRegradedCount.value} 条`);

const questionTypeLabel = (type?: LabQuestionType) => {
  if (type === 'FILL_BLANK') return '填空题';
  if (type === 'SHORT_ANSWER') return '简答题';
  if (type === 'TEXT') return '文本题';
  if (type === 'CODE') return '代码题';
  if (type === 'SINGLE_CHOICE') return '单选题';
  if (type === 'MULTIPLE_CHOICE') return '多选题';
  if (type === 'TRUE_FALSE') return '判断题';
  return type || '未标注题型';
};

const syncSelectedRows = async () => {
  await nextTick();
  const table = distributionTableRef.value;
  const rows = answerDistributionState.value?.answerDistribution || [];
  if (!table) {
    return;
  }
  table.clearSelection();
  rows.forEach((row) => {
    if (row.accepted) {
      table.toggleRowSelection(row, true);
    }
  });
  selectedDistributionRows.value = rows.filter((row) => row.accepted);
};

const fetchBlankItems = async () => {
  loadingItems.value = true;
  try {
    blankItems.value = await listTeacherLabBlankItems(labId.value);
    if (!blankItems.value.length) {
      selectedExperimentItemId.value = null;
      answerDistributionState.value = null;
      return;
    }
    if (!selectedExperimentItemId.value || !blankItems.value.some((item) => item.id === selectedExperimentItemId.value)) {
      selectedExperimentItemId.value = blankItems.value[0].id;
    }
  } finally {
    loadingItems.value = false;
  }
};

const fetchAnswerDistribution = async (experimentItemId: number) => {
  loadingDistribution.value = true;
  try {
    answerDistributionState.value = await getTeacherLabBlankAnswerDistribution(labId.value, experimentItemId);
    await syncSelectedRows();
  } finally {
    loadingDistribution.value = false;
  }
};

const selectItem = async (experimentItemId: number) => {
  selectedExperimentItemId.value = experimentItemId;
  lastRegradedCount.value = null;
  await fetchAnswerDistribution(experimentItemId);
};

const selectItemAndFocus = async (experimentItemId: number) => {
  await selectItem(experimentItemId);
  await nextTick();
  document.getElementById('blank-regrade-detail-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const selectQueryItem = async () => {
  const itemId = route.query.itemId;
  if (typeof itemId !== 'string') {
    return false;
  }
  const numericItemId = Number(itemId);
  if (!Number.isFinite(numericItemId) || !blankItems.value.some((item) => item.id === numericItemId)) {
    return false;
  }
  await selectItemAndFocus(numericItemId);
  return true;
};

const goBatchRegrade = () => {
  router.push(`/teacher/labs/${labId.value}/blank-regrade/batch`);
};

const goLab = (targetLabId?: number) => {
  if (!targetLabId) return;
  router.push(`/teacher/labs/${targetLabId}/blank-regrade`);
};

const refreshCurrentDistribution = async () => {
  if (!selectedExperimentItemId.value) {
    ElMessage.warning('请先选择一个填空题项');
    return;
  }
  await fetchAnswerDistribution(selectedExperimentItemId.value);
};

const handleSelectionChange = (rows: TeacherLabBlankAnswerDistributionItem[]) => {
  selectedDistributionRows.value = rows;
};

const saveAcceptedAnswers = async () => {
  if (!selectedExperimentItemId.value) {
    ElMessage.warning('请先选择一个填空题项');
    return;
  }
  saving.value = true;
  try {
    const result = await saveTeacherLabBlankAcceptedAnswers(labId.value, {
      experimentItemId: selectedExperimentItemId.value,
      acceptedAnswers: selectedDistributionRows.value.map((row) => row.answerText),
    });
    lastRegradedCount.value = result.regradedCount;
    ElMessage.success(`可接受答案已保存，重判 ${result.regradedCount} 条作答`);
    await fetchAnswerDistribution(selectedExperimentItemId.value);
  } finally {
    saving.value = false;
  }
};

const initialize = async () => {
  labs.value = await listTeacherLabs();
  await fetchBlankItems();
  const selectedFromQuery = await selectQueryItem();
  if (!selectedFromQuery && selectedExperimentItemId.value) {
    await fetchAnswerDistribution(selectedExperimentItemId.value);
  }
};

watch(() => route.params.id, () => initialize());
initialize();
</script>

<style scoped>
.blank-regrade-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.blank-regrade-header,
.blank-regrade-panel {
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.blank-regrade-header {
  padding: 16px 18px 14px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.82), rgba(255, 255, 255, 0.98));
}

.blank-regrade-header__top {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  margin-top: 10px;
}

.blank-regrade-section-kicker,
.summary-card__label {
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
}

.blank-regrade-section-kicker {
  color: var(--color-brand-teacher);
}

.blank-regrade-header h1,
.blank-regrade-panel h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: 'DM Sans', sans-serif;
}
.blank-regrade-header p,
.blank-regrade-item-card p,
.blank-regrade-current-item p {
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
}

.blank-regrade-hero__actions,
.blank-regrade-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.blank-regrade-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.blank-regrade-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.blank-regrade-sidebar,
.blank-regrade-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.blank-regrade-panel {
  padding: 16px;
}

.blank-regrade-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.blank-regrade-panel__header--split {
  align-items: flex-start;
}

.blank-regrade-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.blank-regrade-chip strong {
  margin-right: 4px;
  color: var(--color-text-primary);
}

.blank-regrade-chip--accent {
  background: #e0f2fe;
  color: #0369a1;
}

.blank-regrade-chip--soft {
  background: var(--color-bg-app);
  color: var(--color-text-secondary);
}

.blank-regrade-item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.blank-regrade-item-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-surface);
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.blank-regrade-item-card:hover,
.blank-regrade-item-card.is-active {
  border-color: var(--color-brand-teacher-accent);
  box-shadow: 0 14px 28px rgba(20, 184, 166, 0.12);
}

.blank-regrade-item-card__main {
  display: flex;
  gap: 10px;
}

.blank-regrade-item-card__badge {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--color-bg-app);
  color: var(--color-text-secondary);
  font-weight: 700;
  flex-shrink: 0;
}

.blank-regrade-item-card__topline,
.blank-regrade-item-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.blank-regrade-item-card strong,
.blank-regrade-current-item strong {
  color: var(--color-text-primary);
}

.blank-regrade-current-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--color-bg-app);
}

.blank-regrade-current-item__summary {
  min-width: 0;
  flex: 1;
}

.blank-regrade-current-item__title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.blank-regrade-current-item__meta-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 10px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.blank-regrade-current-item__accepted {
  min-width: 240px;
}

.blank-regrade-accepted-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.blank-regrade-answer-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--color-success-soft);
  color: #166534;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.blank-regrade-answer-chip--muted {
  background: var(--color-bg-app);
  color: var(--color-text-muted);
}

.blank-regrade-table {
  width: 100%;
}

.blank-regrade-empty {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px dashed var(--color-border-soft);
  border-radius: 16px;
  background: var(--color-bg-app);
}

.blank-regrade-empty strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.blank-regrade-empty p {
  margin: 0;
  font-size: var(--font-size-base);
}

.blank-regrade-empty--compact {
  padding: 12px 14px;
}

.blank-regrade-empty--table {
  margin-top: 12px;
}

@media (max-width: 1280px) {
  .blank-regrade-layout {
    grid-template-columns: 1fr;
  }

  .blank-regrade-header__top {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .blank-regrade-header__top,
  .blank-regrade-current-item,
  .blank-regrade-panel__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .blank-regrade-hero__actions,
  .blank-regrade-toolbar,
  .blank-regrade-current-item__accepted,
  .blank-regrade-hero__actions .el-button {
    width: 100%;
  }
}
</style>
