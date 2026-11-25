<template>
  <section v-loading="loading" class="behavior-summary-card">
    <header class="behavior-summary-card__header">
      <div>
        <div class="behavior-summary-card__eyebrow">作答行为</div>
        <h3>作答行为摘要</h3>
      </div>
      <div class="behavior-summary-card__actions">
        <el-tooltip :content="riskTagTooltip(metrics)" placement="top">
          <AppStatusTag v-bind="resolveStatusOf('behaviorRisk', riskKeyOf(metrics))" :class="{ 'risk-tag--muted': hasNoBehaviorData(metrics) }" />
        </el-tooltip>
        <el-button plain size="small" :disabled="!detail" @click="drawerVisible = true">行为时间线</el-button>
      </div>
    </header>

    <template v-if="metrics">
      <div class="behavior-metrics-grid">
        <article class="behavior-metric">
          <span class="behavior-metric__label">作答时长</span>
          <strong>{{ formatTotalDuration(metrics) }}</strong>
          <small v-if="metrics.durationOpen">进行中，截至最近事件</small>
          <small v-else>{{ formatRangeLabel }}</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">活跃时长</span>
          <strong>{{ formatActiveDuration(metrics) }}</strong>
          <small>心跳累计</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">会话数</span>
          <strong>{{ metrics.sessionCount }}</strong>
          <small>断续作答次数（LAB_ENTER 计数）</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">保存次数</span>
          <strong>{{ metrics.saveCount }}</strong>
          <small>服务端确认的答案变更次数</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">粘贴</span>
          <strong>{{ metrics.pasteCount }} 次 · {{ metrics.pastedChars }} 字符</strong>
          <small
            v-if="metrics.pasteBlockedCount > 0"
            class="behavior-metric__subtext--warn"
          >
            含 {{ metrics.pasteBlockedCount }} 次禁止粘贴题的尝试
          </small>
          <small v-else>含被禁止题目的粘贴尝试</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">切屏</span>
          <strong>{{ metrics.hiddenCount }} 次 · {{ formatBehaviorDuration(metrics.hiddenMs) }}</strong>
          <small>页面切到后台的次数与时长</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">失焦</span>
          <strong>{{ metrics.blurCount }} 次</strong>
          <small>浏览器内切换到其他标签页</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">计算时间</span>
          <strong class="behavior-metric__value--small">{{ formatBehaviorTime(metrics.computedAt) }}</strong>
          <small>指标最近一次聚合时间</small>
        </article>
      </div>
    </template>
    <AppEmptyState v-else-if="!loading" description="暂无行为数据（历史提交或未采集）" />

    <BehaviorTimelineDrawer
      v-model="drawerVisible"
      :timeline="detail?.timeline ?? []"
      :total="detail?.timelineTotal ?? 0"
      :step-dwell-ms="detail?.metrics?.stepDwellMs ?? {}"
      :step-list="stepList"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppEmptyState from '@/components/ui/AppEmptyState.vue';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import { resolveStatusOf } from '@/components/ui/statusPresentation';
import { getTeacherLabReportBehavior } from '@/api/labs';
import type { LabReportBehaviorDetail } from '@/types/lab';
import {
  formatActiveDuration,
  formatBehaviorDuration,
  formatBehaviorTime,
  formatTotalDuration,
  hasNoBehaviorData,
  riskKeyOf,
  riskTagTooltip,
} from './behaviorDisplay';
import BehaviorTimelineDrawer from './BehaviorTimelineDrawer.vue';

/**
 * 题项简要结构（stepId → stepNo/title 映射用，D2）。
 * 两个使用点（LabReportDetail / LabReportBatchGrade）都已有完整 step 列表，
 * 按需通过 step-list prop 传入；不传时 Drawer 的 Step Dwell 面板按 stepId 兜底展示。
 */
export interface StepBrief {
  stepId: number;
  stepNo: number;
  title: string;
}

const props = defineProps<{
  /** 报告（提交）ID，即 behavior 详情接口路径参数 */
  submissionId: number;
  /** 可选：题项列表，用于 Step Dwell 面板 stepId → stepNo/title 映射 */
  stepList?: StepBrief[];
}>();

const loading = ref(false);
const detail = ref<LabReportBehaviorDetail | null>(null);
const drawerVisible = ref(false);

const metrics = computed(() => detail.value?.metrics ?? null);

/** 起止时间副文案：开始作答 → 最近事件 */
const formatRangeLabel = computed(() => {
  const first = metrics.value?.firstStartedAt;
  const last = metrics.value?.lastEventAt;
  if (!first || !last) {
    return '起止时间未知';
  }
  return `${formatBehaviorTime(first)} ~ ${formatBehaviorTime(last)}`;
});

const fetchBehavior = async () => {
  loading.value = true;
  try {
    detail.value = await getTeacherLabReportBehavior(props.submissionId);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  void fetchBehavior();
});
</script>

<style scoped>
.behavior-summary-card {
  border: 1px solid #dbe5ef;
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.behavior-summary-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.behavior-summary-card__eyebrow {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: var(--letter-spacing-caps);
  text-transform: uppercase;
  color: var(--color-brand-teacher);
  margin-bottom: 2px;
}

.behavior-summary-card__header h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.behavior-summary-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-tag--muted {
  opacity: 0.65;
}

/* 8 指标卡 4×2 网格（D1-D8 对齐） */
.behavior-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.behavior-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: linear-gradient(180deg, var(--color-surface), #fbfdff);
}

.behavior-metric__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.behavior-metric strong {
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
}

.behavior-metric__value--small {
  font-size: var(--font-size-sm) !important;
}

.behavior-metric small {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
  line-height: var(--line-height-heading);
}

.behavior-metric__subtext--warn {
  color: #b45309;
  font-weight: 600;
}

@media (max-width: 1280px) {
  .behavior-metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
