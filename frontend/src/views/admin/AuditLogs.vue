<template>
  <div class="page admin-audit-logs workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 操作日志</div>
          <h1>操作日志</h1>
          <p>本校组织与账号相关操作审计记录（只读）。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" :loading="loading" @click="loadLogs">刷新</el-button>
        </div>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-select v-model="actionFilter" clearable placeholder="操作类型" style="width: 220px" @change="onFilterChange">
          <el-option v-for="item in actionOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="onFilterChange"
        />
      </div>
    </section>

    <section class="table-card">
      <el-table :data="rows" v-loading="loading" empty-text="暂无操作日志">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作者" min-width="160">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.displayName || '—' }}</strong>
              <span v-if="row.username">账号 {{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="160" />
        <el-table-column prop="resourceType" label="资源" width="100" />
        <el-table-column prop="resourceId" label="资源 ID" width="100" />
        <el-table-column label="详情" min-width="120">
          <template #default="{ row }">
            <el-popover v-if="row.detailJson" placement="left" width="360" trigger="click">
              <template #reference>
                <el-button link type="primary">查看 JSON</el-button>
              </template>
              <pre class="audit-json">{{ formatJson(row.detailJson) }}</pre>
            </el-popover>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace" min-width="120" show-overflow-tooltip />
      </el-table>

      <div class="pager-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadLogs"
          @size-change="onPageSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listAuditLogs } from '@/api/admin';
import type { AuditLogListItem } from '@/types/admin';

const loading = ref(false);
const rows = ref<AuditLogListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const actionFilter = ref<string | undefined>();
const dateRange = ref<[string, string] | null>(null);

const actionOptions = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'STUDENT_CREATE',
  'STUDENT_UPDATE',
  'STUDENT_STATUS',
  'STUDENT_PASSWORD_RESET',
  'CLASS_UPDATE',
  'TEACHER_CREATE',
  'QUESTION_DELETE',
];

function formatTime(value: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString();
}

function formatJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function onFilterChange() {
  page.value = 1;
  loadLogs();
}

function onPageSizeChange() {
  page.value = 1;
  loadLogs();
}

async function loadLogs() {
  loading.value = true;
  try {
    const params: Record<string, string | number | undefined> = {
      page: page.value,
      pageSize: pageSize.value,
    };
    if (actionFilter.value) params.action = actionFilter.value;
    if (dateRange.value?.[0]) params.from = dateRange.value[0];
    if (dateRange.value?.[1]) params.to = dateRange.value[1];
    const data = await listAuditLogs(params);
    rows.value = data.items;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadLogs();
});
</script>

<style scoped>
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.audit-json {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.identity-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.identity-cell span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
