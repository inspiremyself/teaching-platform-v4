<template>
  <div class="blank-batch-page">
    <section class="blank-batch-header">
      <button type="button" class="blank-batch-back" @click="goDetail()">
        <span class="material-symbols-outlined">arrow_back</span>
        <span>返回填空重判详情</span>
      </button>
      <div class="blank-batch-header__top">
        <div>
          <h1>填空题重判总览</h1>
          <p>独立批量界面集中展示当前实验全部填空题的答案分布与可接受答案，便于逐题刷新、重判和跳转到详细重判。</p>
        </div>
        <div class="blank-batch-actions">
          <el-button plain :disabled="!previousClassLab" @click="goLab(previousClassLab?.id)">上一个班级</el-button>
          <el-button plain :disabled="!nextClassLab" @click="goLab(nextClassLab?.id)">下一个班级</el-button>
          <el-button plain @click="goDetail()">回到详细重判</el-button>
          <el-button type="primary" :loading="loadingOverview" @click="refreshAllDistributions">刷新全部</el-button>
        </div>
      </div>
      <div class="blank-batch-meta">
        <span class="blank-batch-chip"><strong>{{ blankItems.length }}</strong> 个填空题项</span>
        <span class="blank-batch-chip blank-batch-chip--accent">{{ loadedDistributionCount }}/{{ blankItems.length }} 已加载</span>
      </div>
    </section>

    <section class="blank-batch-panel">
      <el-table
        v-loading="loadingItems || loadingOverview"
        :data="blankItems"
        :row-key="blankItemKey"
        class="blank-batch-table"
      >
        <el-table-column label="题项" min-width="280">
          <template #default="scope">
            <button type="button" class="blank-batch-question-link" @click="goDetail(scope.row.id)">
              <span>题项 {{ scope.row.stepNo }} / {{ questionTypeLabel(scope.row.questionType) }}</span>
              <strong>{{ scope.row.title }}</strong>
              <small>{{ scope.row.content || '暂无题项说明' }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="分值" width="90">
          <template #default="scope">{{ scope.row.stepScore }} 分</template>
        </el-table-column>
        <el-table-column label="答案分布" min-width="280">
          <template #default="scope">
            <div class="blank-batch-chip-list">
              <template v-if="distributionCount(scope.row.id)">
                <span
                  v-for="entry in distributionPreview(scope.row.id)"
                  :key="`${scope.row.id}-${entry.normalizedAnswer}`"
                  class="blank-batch-answer-chip"
                  :class="{ 'blank-batch-answer-chip--muted': !entry.accepted }"
                >
                  {{ entry.answerText }} × {{ entry.count }}
                </span>
              </template>
              <span v-else class="blank-batch-answer-chip blank-batch-answer-chip--muted">暂无分布</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可接受答案" min-width="240">
          <template #default="scope">
            <div class="blank-batch-chip-list">
              <template v-if="acceptedAnswersOf(scope.row.id).length">
                <span v-for="answer in acceptedAnswersOf(scope.row.id)" :key="`${scope.row.id}-${answer}`" class="blank-batch-answer-chip">
                  {{ answer }}
                </span>
              </template>
              <span v-else class="blank-batch-answer-chip blank-batch-answer-chip--muted">尚未设置</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="right" fixed="right">
          <template #default="scope">
            <div class="blank-batch-row-actions">
              <el-button link type="primary" @click="goDetail(scope.row.id)">详情</el-button>
              <el-button link type="primary" @click="refreshItemDistribution(scope.row.id)">刷新</el-button>
              <el-button
                link
                type="primary"
                :loading="savingItemId === scope.row.id"
                :disabled="!blankDistributionMap[scope.row.id]"
                @click="regradeItemWithExistingAnswers(scope.row.id)"
              >
                重判
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
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
  TeacherLabBlankItem,
} from '@/types/lab';

const route = useRoute();
const router = useRouter();

const loadingItems = ref(false);
const loadingOverview = ref(false);
const savingItemId = ref<number | null>(null);
const labs = ref<LabItem[]>([]);
const blankItems = ref<TeacherLabBlankItem[]>([]);
const blankDistributionMap = reactive<Record<number, TeacherLabBlankAnswerDistribution>>({});

const labId = computed(() => Number(route.params.id));
const loadedDistributionCount = computed(() => blankItems.value.filter((item) => Boolean(blankDistributionMap[item.id])).length);
const currentLab = computed(() => labs.value.find((item) => item.id === labId.value) || null);
const relatedLabs = computed(() => sortLabs(labs.value.filter((item) => {
  if (!currentLab.value) return false;
  return item.title === currentLab.value.title || item.id === currentLab.value.id;
})));
const currentLabIndex = computed(() => relatedLabs.value.findIndex((item) => item.id === labId.value));
const previousClassLab = computed(() => currentLabIndex.value > 0 ? relatedLabs.value[currentLabIndex.value - 1] : null);
const nextClassLab = computed(() => currentLabIndex.value >= 0 ? relatedLabs.value[currentLabIndex.value + 1] || null : null);

const sortLabs = (items: LabItem[]) => [...items].sort((left, right) => {
  const leftClass = left.className || String(left.classId ?? '');
  const rightClass = right.className || String(right.classId ?? '');
  return leftClass.localeCompare(rightClass, 'zh-CN', { numeric: true });
});

const blankItemKey = (item: TeacherLabBlankItem) => item.id;
const acceptedAnswersOf = (experimentItemId: number) => blankDistributionMap[experimentItemId]?.acceptedAnswers || [];
const distributionCount = (experimentItemId: number) => blankDistributionMap[experimentItemId]?.answerDistribution.length || 0;
const distributionPreview = (experimentItemId: number) =>
  (blankDistributionMap[experimentItemId]?.answerDistribution || []).slice(0, 4);

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

const fetchBlankItems = async () => {
  loadingItems.value = true;
  try {
    blankItems.value = await listTeacherLabBlankItems(labId.value);
  } finally {
    loadingItems.value = false;
  }
};

const fetchOverviewDistribution = async (experimentItemId: number) => {
  blankDistributionMap[experimentItemId] = await getTeacherLabBlankAnswerDistribution(labId.value, experimentItemId);
};

const refreshAllDistributions = async () => {
  if (!blankItems.value.length) {
    return;
  }
  loadingOverview.value = true;
  try {
    await Promise.all(blankItems.value.map((item) => fetchOverviewDistribution(item.id)));
  } finally {
    loadingOverview.value = false;
  }
};

const refreshItemDistribution = async (experimentItemId: number) => {
  await fetchOverviewDistribution(experimentItemId);
  ElMessage.success('题项答案分布已刷新');
};

const regradeItemWithExistingAnswers = async (experimentItemId: number) => {
  const state = blankDistributionMap[experimentItemId];
  if (!state) {
    ElMessage.warning('请先刷新该题项的答案分布');
    return;
  }

  savingItemId.value = experimentItemId;
  try {
    const result = await saveTeacherLabBlankAcceptedAnswers(labId.value, {
      experimentItemId,
      acceptedAnswers: state.acceptedAnswers,
    });
    ElMessage.success(`题项 ${state.item.stepNo} 已重判 ${result.regradedCount} 条作答`);
    await fetchOverviewDistribution(experimentItemId);
  } finally {
    savingItemId.value = null;
  }
};

const goDetail = (itemId?: number) => {
  router.push({
    path: `/teacher/labs/${labId.value}/blank-regrade`,
    query: itemId ? { itemId: String(itemId) } : undefined,
  });
};

const goLab = (targetLabId?: number) => {
  if (!targetLabId) return;
  router.push(`/teacher/labs/${targetLabId}/blank-regrade/batch`);
};

const initialize = async () => {
  Object.keys(blankDistributionMap).forEach((key) => delete blankDistributionMap[Number(key)]);
  labs.value = await listTeacherLabs();
  await fetchBlankItems();
  await refreshAllDistributions();
};

watch(() => route.params.id, () => initialize());
initialize();
</script>

<style scoped>
.blank-batch-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.blank-batch-header,
.blank-batch-panel {
  border: 1px solid var(--color-border-soft);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.blank-batch-header {
  padding: 16px 18px 14px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.82), rgba(255, 255, 255, 0.98));
}

.blank-batch-back,
.blank-batch-question-link {
  border: none;
  background: transparent;
  cursor: pointer;
}

.blank-batch-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  color: var(--color-brand-teacher);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.blank-batch-header__top {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  margin-top: 10px;
}

.blank-batch-header h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 22px;
}

.blank-batch-header p {
  color: var(--color-text-muted);
  line-height: 1.5;
}

.blank-batch-actions,
.blank-batch-meta,
.blank-batch-chip-list,
.blank-batch-row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.blank-batch-actions,
.blank-batch-row-actions {
  justify-content: flex-end;
}

.blank-batch-meta {
  margin-top: 12px;
}

.blank-batch-chip,
.blank-batch-answer-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.blank-batch-chip {
  min-height: 28px;
  padding: 0 10px;
  background: #f8fafc;
  color: #475569;
}

.blank-batch-chip strong {
  margin-right: 4px;
  color: var(--color-text-primary);
}

.blank-batch-chip--accent {
  background: var(--color-brand-teacher-soft);
  color: var(--color-brand-teacher);
}

.blank-batch-panel {
  padding: 16px;
}

.blank-batch-table {
  width: 100%;
}

.blank-batch-question-link {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 0;
  text-align: left;
}

.blank-batch-question-link span {
  color: var(--color-brand-teacher);
  font-size: 12px;
  font-weight: 700;
}

.blank-batch-question-link strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 1.4;
}

.blank-batch-question-link small {
  color: var(--color-text-muted);
  line-height: 1.45;
}

.blank-batch-answer-chip {
  min-height: 24px;
  padding: 0 8px;
  background: #dcfce7;
  color: #166534;
}

.blank-batch-answer-chip--muted {
  background: #f8fafc;
  color: #64748b;
}

@media (max-width: 900px) {
  .blank-batch-header__top {
    flex-direction: column;
  }

  .blank-batch-actions {
    justify-content: flex-start;
  }
}
</style>
