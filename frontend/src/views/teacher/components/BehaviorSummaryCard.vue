<template>
  <section v-loading="loading" class="behavior-summary-card">
    <header class="behavior-summary-card__header">
      <div>
        <div class="behavior-summary-card__eyebrow">作答行为</div>
        <h3>作答行为摘要</h3>
      </div>
      <div class="behavior-summary-card__actions">
        <el-tooltip :content="riskTagTooltip(metrics)" placement="top">
          <el-tag :type="riskTagType(metrics)" effect="light" :class="{ 'risk-tag--muted': hasNoBehaviorData(metrics) }">
            {{ riskTagLabel(metrics) }}
          </el-tag>
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
          <small>心跳累计 · 会话数 {{ metrics.sessionCount }}</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">保存次数</span>
          <strong>{{ metrics.saveCount }}</strong>
          <small>服务端确认的答案变更次数</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">粘贴</span>
          <strong>{{ metrics.pasteCount }} 次 · {{ metrics.pastedChars }} 字符</strong>
          <small>含被禁止题目的粘贴尝试</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">切屏</span>
          <strong>{{ metrics.hiddenCount }} 次 · {{ formatBehaviorDuration(metrics.hiddenMs) }}</strong>
          <small>页面切到后台的次数与时长</small>
        </article>
        <article class="behavior-metric">
          <span class="behavior-metric__label">计算时间</span>
          <strong class="behavior-metric__value--small">{{ formatBehaviorTime(metrics.computedAt) }}</strong>
          <small>指标最近一次聚合时间</small>
        </article>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="暂无行为数据（历史提交或未采集）" :image-size="60" />

    <BehaviorTimelineDrawer
      v-model="drawerVisible"
      :timeline="detail?.timeline ?? []"
      :total="detail?.timelineTotal ?? 0"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getTeacherLabReportBehavior } from '@/api/labs';
import type { LabReportBehaviorDetail } from '@/types/lab';
import {
  formatActiveDuration,
  formatBehaviorDuration,
  formatBehaviorTime,
  formatTotalDuration,
  hasNoBehaviorData,
  riskTagLabel,
  riskTagTooltip,
  riskTagType,
} from './behaviorDisplay';
import BehaviorTimelineDrawer from './BehaviorTimelineDrawer.vue';

const props = defineProps<{
  /** 报告（提交）ID，即 behavior 详情接口路径参数 */
  submissionId: number;
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
  border-radius: 14px;
  background: #fff;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
  margin-bottom: 2px;
}

.behavior-summary-card__header h3 {
  margin: 0;
  font-size: 15px;
  color: #0f172a;
}

.behavior-summary-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-tag--muted {
  opacity: 0.65;
}

.behavior-metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.behavior-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
}

.behavior-metric__label {
  font-size: 11px;
  color: #64748b;
}

.behavior-metric strong {
  font-size: 15px;
  color: #0f172a;
}

.behavior-metric__value--small {
  font-size: 12px !important;
}

.behavior-metric small {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}

@media (max-width: 1180px) {
  .behavior-metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
