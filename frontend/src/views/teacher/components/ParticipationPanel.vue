<template>
  <section class="participation-panel">
    <article v-for="row in overview" :key="row.businessType" class="participation-panel__type">
      <header class="participation-panel__header">
        <div>
          <span class="participation-panel__eyebrow">参与与完成情况</span>
          <h2 class="participation-panel__title">{{ row.label }}</h2>
        </div>
        <div class="participation-panel__rates">
          <span>参与率 {{ formatScore(row.participationRate) }}%</span>
          <span>完成率 {{ formatScore(row.completionRate) }}%</span>
        </div>
      </header>

      <div class="participation-panel__metrics">
        <span>应参加 {{ row.expectedCount }}</span>
        <span>已参与 {{ row.participatedCount }}</span>
        <span>已完成 {{ row.completedCount }}</span>
        <span>未开始 {{ row.notStartedCount }}</span>
      </div>

      <el-table v-if="row.activities.length" :data="row.activities" size="small" border>
        <el-table-column prop="businessTitle" label="活动" min-width="180" />
        <el-table-column prop="expectedCount" label="应参加" width="90" />
        <el-table-column prop="participatedCount" label="已参与" width="90" />
        <el-table-column prop="completedCount" label="已完成" width="90" />
        <el-table-column prop="notStartedCount" label="未开始" width="90" />
        <el-table-column label="操作" width="110" align="right" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(row.businessType, scope.row.businessId)">
              查看名单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无活动数据" :image-size="48" />
    </article>

    <el-dialog v-model="dialogVisible" title="未开始名单" width="860px" @closed="closeDetail">
      <el-alert v-if="error" :title="error" type="error" show-icon />
      <el-table v-if="detail" :data="detail.students" size="small" border v-loading="loading">
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="studentName" label="姓名" width="140" />
        <el-table-column label="参与状态" width="110">
          <template #default="scope">{{ participationLabel(scope.row.participation) }}</template>
        </el-table-column>
        <el-table-column label="完成情况" width="110">
          <template #default="scope">{{ completionLabel(scope.row.completion) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="180">
          <template #default="scope">{{ scope.row.submittedAt || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!error" description="暂无学生名单" :image-size="48" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { useTeacherParticipation } from '@/composables/useTeacherParticipation';
import type { TeacherParticipationOverview } from '@/types/analysis';
import { formatScore } from '@/utils/format';

withDefaults(defineProps<{ overview: TeacherParticipationOverview }>(), { overview: () => [] });

const { detail, loading, error, dialogVisible, openDetail, closeDetail } = useTeacherParticipation();

const participationLabel = (value: 'NOT_STARTED' | 'PARTICIPATED') =>
  value === 'NOT_STARTED' ? '未开始' : '已参与';

const completionLabel = (value: 'COMPLETED' | 'IN_PROGRESS') =>
  value === 'COMPLETED' ? '已完成' : '进行中';
</script>

<style scoped>
.participation-panel {
  display: grid;
  gap: 16px;
}

.participation-panel__type {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  background: #fff;
}

.participation-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.participation-panel__eyebrow {
  color: #909399;
  font-size: var(--font-size-sm);
}

.participation-panel__title {
  margin: 4px 0 0;
  font-size: var(--font-size-lg);
}

.participation-panel__rates {
  display: flex;
  gap: 12px;
  color: #409eff;
  font-size: var(--font-size-base);
}

.participation-panel__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 12px 0;
  color: #606266;
  font-size: var(--font-size-base);
}
</style>
