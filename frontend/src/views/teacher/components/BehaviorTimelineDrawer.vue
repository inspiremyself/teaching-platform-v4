<template>
  <el-drawer
    :model-value="modelValue"
    title="行为时间线"
    size="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="timeline-drawer">
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
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { LabBehaviorTimelineItem } from '@/types/lab';
import { behaviorEventPayloadSummary, behaviorEventTitle, formatBehaviorTime } from './behaviorDisplay';

const props = defineProps<{
  /** 抽屉显隐（v-model） */
  modelValue: boolean;
  /** 后端返回的时间线（occurredAt 升序，上限 500 条） */
  timeline: LabBehaviorTimelineItem[];
  /** 服务端事件总数（可能大于 timeline.length） */
  total: number;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

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
</script>

<style scoped>
.timeline-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
</style>
