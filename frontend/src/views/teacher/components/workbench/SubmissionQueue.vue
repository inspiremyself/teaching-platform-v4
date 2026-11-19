<script setup lang="ts">
import type { QueueItemVM } from './types'

defineProps<{
  items: QueueItemVM[]
  selectedId: number | string | null
  keyword: string
  statusFilter: string
}>()
const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:statusFilter': [value: string]
  select: [id: number | string]
}>()

const scoreText = (value: number | null): string => (value === null ? '—' : String(value))
</script>

<template>
  <section class="workbench-panel submission-queue">
    <header class="submission-queue__header">
      <h2>提交队列</h2>
      <p>选择一个学生进入逐题批改。</p>
    </header>
    <div class="submission-queue__filters">
      <el-input
        :model-value="keyword"
        placeholder="搜索学生姓名"
        clearable
        @update:model-value="emit('update:keyword', String($event))"
      />
      <el-select
        :model-value="statusFilter"
        clearable
        placeholder="全部状态"
        @update:model-value="emit('update:statusFilter', String($event ?? ''))"
      >
        <el-option v-for="status in [...new Set(items.map(item => item.status))]" :key="status" :label="items.find(item => item.status === status)?.statusLabel ?? status" :value="status" />
      </el-select>
    </div>
    <div class="submission-queue__list">
      <button
        v-for="item in items"
        :key="String(item.id)"
        type="button"
        class="submission-queue__item"
        :class="{ 'is-active': String(item.id) === String(selectedId) }"
        @click="emit('select', item.id)"
      >
        <span class="submission-queue__name">{{ item.studentName }}</span>
        <span class="submission-queue__status">{{ item.statusLabel }}</span>
        <span class="submission-queue__scores">
          客观 {{ scoreText(item.objectiveScore) }} · 主观 {{ scoreText(item.manualScore) }} · 总分 {{ scoreText(item.totalScore) }}
        </span>
        <span v-if="item.draftFlag" class="submission-queue__draft">有未保存的评分</span>
      </button>
      <p v-if="items.length === 0" class="submission-queue__empty">暂无提交记录</p>
    </div>
  </section>
</template>
