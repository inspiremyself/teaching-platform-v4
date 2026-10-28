<template>
  <el-drawer
    :model-value="modelValue"
    title="行为时间线"
    size="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="timeline-drawer">
      <el-tabs v-model="activeTab" class="timeline-drawer__tabs">
        <el-tab-pane label="行为时间线" name="timeline">
          <p class="timeline-drawer__meta">
            共 {{ total }} 条行为事件<template v-if="total > timeline.length">，当前显示最近 {{ timeline.length }} 条</template>；按时间倒序排列。
          </p>
          <el-empty v-if="!timeline.length" description="暂无行为事件" />
          <el-timeline v-else class="timeline-drawer__list">
            <el-timeline-item
              v-for="(item, index) in reversedTimeline"
              :key="`${item.occurredAt}-${index}`"
              :timestamp="formatBehaviorTime(item.occurredAt)"
              :type="timelineItemType(item)"
              placement="top"
            >
              <div class="timeline-entry">
                <strong>{{ behaviorEventTitle(item) }}</strong>
                <p v-if="behaviorEventPayloadSummary(item)">{{ behaviorEventPayloadSummary(item) }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="逐题停留" name="dwell">
          <p class="timeline-drawer__meta">
            学生在每道题上的停留时长（STEP_VIEW / LAB_LEAVE 事件聚合）；跳过标记表示停留 ≤ 1s。
          </p>
          <el-empty v-if="stepDwellItems.length === 0" description="无逐题停留数据" />
          <div v-else class="step-dwell-list">
            <div
              v-for="item in stepDwellItems"
              :key="item.stepId"
              :class="['step-dwell-row', { 'step-dwell-row--skip': item.skip }]"
            >
              <div class="step-dwell-row__label">
                <strong>题项 {{ item.stepNo ?? `#${item.stepId}` }}</strong>
                <span v-if="item.stepTitle" class="step-dwell-row__title">{{ item.stepTitle }}</span>
                <span v-if="item.skip" class="step-dwell-row__skip-tag">跳过</span>
              </div>
              <el-progress
                :percentage="maxDwellMs > 0 ? Math.round(item.dwellMs / maxDwellMs * 100) : 0"
                :stroke-width="14"
                :format="() => formatStepDwell(item.dwellMs) ?? '—'"
                :status="item.skip ? 'exception' : (item.dwellMs === 0 ? undefined : 'success')"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { LabBehaviorTimelineItem } from '@/types/lab';
import {
  behaviorEventPayloadSummary,
  behaviorEventTitle,
  formatBehaviorTime,
  formatStepDwell,
} from './behaviorDisplay';

/** BehaviorSummaryCard 导出的题项简要结构（D2 共用） */
interface StepBrief {
  stepId: number;
  stepNo: number;
  title: string;
}

/** Step Dwell 面板条目（§4.2） */
interface StepDwellItem {
  stepId: number;
  stepNo?: number;
  stepTitle?: string;
  dwellMs: number;
  /** dwellMs > 0 且 < 1000ms 判跳过（D7） */
  skip: boolean;
}

const props = defineProps<{
  /** 抽屉显隐（v-model） */
  modelValue: boolean;
  /** 后端返回的时间线（occurredAt 升序，上限 500 条） */
  timeline: LabBehaviorTimelineItem[];
  /** 服务端事件总数（可能大于 timeline.length） */
  total: number;
  /** 逐题停留 Map：stepId 字符串 → 毫秒（来自 behavior detail 接口） */
  stepDwellMs?: Record<string, number>;
  /** 题项列表：用于 stepId → stepNo/title 映射（D2）；不传时按 stepId 兜底 */
  stepList?: StepBrief[];
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

/** Tab 切换状态；默认「行为时间线」（保持改造前行为不变） */
const activeTab = ref<'timeline' | 'dwell'>('timeline');

// 展示层按时间倒序（§6.2）；后端返回升序，这里反转
const reversedTimeline = computed(() => [...props.timeline].reverse());

/** 关键事件着色：粘贴/拦截/保存失败警示，离开/切后台提示，其余中性 */
const timelineItemType = (item: LabBehaviorTimelineItem): 'danger' | 'warning' | 'primary' => {
  if (item.eventType === 'PASTE' || item.eventType === 'SUBMIT_BLOCKED' || item.eventType === 'SAVE_FAILED') {
    return 'danger';
  }
  if (item.eventType === 'LAB_LEAVE' || item.eventType === 'VISIBILITY' || item.eventType === 'WINDOW_BLUR') {
    return 'warning';
  }
  return 'primary';
};

/**
 * Step Dwell 面板数据组装（§4.2）：
 * - 从 metrics.stepDwellMs 读原始 Map；
 * - 用 props.stepList 做 stepId → stepNo/title 映射（D2），无匹配 stepId 直接展示 stepId 兜底（D6）；
 * - 按 stepNo 升序排列；
 * - dwellMs > 0 且 < 1000ms 标记 skip（D7）。
 */
const stepDwellItems = computed<StepDwellItem[]>(() => {
  const raw = props.stepDwellMs ?? {};
  const briefMap = new Map(
    (props.stepList ?? []).map((s) => [String(s.stepId), s] as const),
  );
  const entries = Object.entries(raw)
    .map(([stepIdStr, dwellMs]) => {
      const brief = briefMap.get(stepIdStr);
      const stepIdNum = Number(stepIdStr);
      return {
        stepId: Number.isFinite(stepIdNum) ? stepIdNum : 0,
        stepNo: brief?.stepNo,
        stepTitle: brief?.title,
        dwellMs: dwellMs ?? 0,
        skip: dwellMs != null && dwellMs > 0 && dwellMs < 1000,
      } satisfies StepDwellItem;
    });
  // stepNo 升序，无 stepNo 兜底排末尾
  return entries.sort((a, b) => {
    const aNo = a.stepNo ?? Number.MAX_SAFE_INTEGER;
    const bNo = b.stepNo ?? Number.MAX_SAFE_INTEGER;
    return aNo - bNo || a.stepId - b.stepId;
  });
});

/** 最大 dwellMs，用于 el-progress 百分比归一化 */
const maxDwellMs = computed(() =>
  stepDwellItems.value.reduce((m, it) => Math.max(m, it.dwellMs), 0),
);
</script>

<style scoped>
.timeline-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.timeline-drawer__tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.timeline-drawer__meta {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.timeline-entry {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.timeline-entry strong {
  color: #0f172a;
  font-size: 13px;
}

.timeline-entry p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

/* ── Step Dwell 面板 ── */

.step-dwell-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-dwell-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
}

.step-dwell-row--skip {
  border-color: #fecaca;
  background: linear-gradient(180deg, #fff, #fef2f2);
}

.step-dwell-row__label {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.step-dwell-row__label strong {
  font-size: 13px;
  color: #0f172a;
}

.step-dwell-row__title {
  font-size: 12px;
  color: #64748b;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-dwell-row__skip-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 999px;
  background: #fef2f2;
  color: #b91c1c;
  letter-spacing: 0.04em;
}
</style>
