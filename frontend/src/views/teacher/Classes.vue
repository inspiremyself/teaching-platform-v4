<template>
  <div class="page classes-console workbench-page">
    <section class="workbench-header classes-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="classes-header__eyebrow">教师控制台 / 班级管理</div>
          <h1 class="classes-console__title">我的班级</h1>
          <p class="classes-console__subtitle">维护班级名册、状态与规模。</p>
        </div>

        <div class="workbench-header__actions classes-console__hero-actions">
          <button class="classes-console__ghost-button" type="button" @click="resetFilters">
            <span class="material-symbols-outlined">filter_alt_off</span>
            重置筛选
          </button>
          <button class="classes-console__primary-button" type="button" disabled title="组织维护已迁至校管端">
            <span class="material-symbols-outlined">add</span>
            新建班级
          </button>
        </div>
      </div>

      <div class="workbench-meta classes-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 班级总数</span>
        <span class="workbench-meta__item"><strong>{{ totalStudents }}</strong> 学生总数</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 启用中</span>
        <span class="workbench-meta__item"><strong>{{ archivedCount }}</strong> 已归档</span>
        <span class="workbench-meta__item"><strong>{{ averageStudents }}</strong> 平均规模</span>
      </div>
    </section>

    <section class="classes-console__toolbar">
      <div class="classes-console__toolbar-main">
        <div class="classes-console__search-field">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索班级名称或代码" clearable />
        </div>

        <label class="classes-console__filter-field classes-console__filter-field--compact">
          <span class="classes-console__filter-label">状态</span>
          <el-select v-model="statusFilter" clearable placeholder="全部状态">
            <el-option label="启用中" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </label>

        <div class="classes-console__quick-switches" role="tablist" aria-label="快捷状态筛选">
          <button
            v-for="option in quickStatusOptions"
            :key="option.value"
            type="button"
            class="classes-console__quick-switch"
            :class="{ 'is-active': activeQuickStatus === option.value }"
            @click="applyQuickStatus(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div class="classes-console__toolbar-meta">
        <span>显示 {{ paginatedRows.length }} / {{ filteredRows.length }} 个班级</span>
        <span class="classes-console__toolbar-dot"></span>
        <span>{{ filterSummaryText }}</span>
        <button class="classes-console__toolbar-link" type="button" @click="resetFilters">清空条件</button>
      </div>
    </section>

    <section class="classes-console__table-shell">
      <div class="classes-console__table-header">
        <div>
          <p class="classes-console__section-kicker">班级工作台</p>
          <h2 class="classes-console__section-title">班级目录</h2>
          <p class="classes-console__section-description">集中查看规模、状态和动作。</p>
        </div>

        <div class="classes-console__table-meta">
          <span class="classes-console__summary-chip">真实接口驱动</span>
          <span class="classes-console__summary-chip">创建/编辑/归档/删除保留</span>
        </div>
      </div>

      <el-table
        :data="paginatedRows"
        v-loading="loading"
        empty-text="暂无班级数据"
        class="classes-console__table"
        :header-cell-style="headerCellStyle"
      >
        <el-table-column label="班级名称与代码" min-width="330">
          <template #default="{ row }">
            <div class="class-row__identity">
              <div :class="['class-row__icon', row.status === 'ACTIVE' ? 'is-active' : 'is-archived']">
                <span class="material-symbols-outlined">{{ row.status === 'ACTIVE' ? 'cast_for_education' : 'inventory_2' }}</span>
              </div>
              <div class="class-row__identity-main">
                <div class="class-row__title-line">
                  <strong>{{ row.name }}</strong>
                  <span :class="['status-pill', row.status === 'ACTIVE' ? 'status-pill--active' : 'status-pill--archived']">
                    <span class="status-pill__dot"></span>
                    {{ row.status === 'ACTIVE' ? '启用中' : '已归档' }}
                  </span>
                </div>
                <div class="class-row__subline">班级代码 {{ row.code }}</div>
                <div class="class-row__meta">
                  <span>
                    <span class="material-symbols-outlined">badge</span>
                    {{ assignmentLabel(row) }}
                  </span>
                  <span>
                    <span class="material-symbols-outlined">group</span>
                    {{ row.studentCount || 0 }} 名学生
                  </span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="班级概况" min-width="220">
          <template #default="{ row }">
            <div class="class-row__summary">
              <div class="class-row__summary-item">
                <span class="class-row__summary-label">学生规模</span>
                <strong class="class-row__summary-value">{{ studentScaleLabel(row.studentCount) }}</strong>
              </div>
              <div class="class-row__summary-item">
                <span class="class-row__summary-label">工作状态</span>
                <strong class="class-row__summary-value">{{ row.status === 'ACTIVE' ? '可继续维护' : '历史归档视图' }}</strong>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="学生规模进度" min-width="220">
          <template #default="{ row }">
            <div class="class-row__progress">
              <div class="class-row__progress-head">
                <span>{{ row.studentCount || 0 }} 人</span>
                <strong>{{ progressPercent(row.studentCount) }}%</strong>
              </div>
              <div class="class-row__progress-track">
                <span
                  class="class-row__progress-fill"
                  :class="row.status === 'ACTIVE' ? 'is-active' : 'is-archived'"
                  :style="{ width: `${progressPercent(row.studentCount)}%` }"
                ></span>
              </div>
              <div class="class-row__progress-caption">{{ progressCaption(row.studentCount) }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right" fixed="right">
          <template #default>
            <span class="class-row__readonly-hint">只读</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="classes-console__empty-state">
            <div class="classes-console__empty-visual">
              <span class="material-symbols-outlined">school</span>
            </div>
            <strong>{{ searchQuery || statusFilter ? '当前筛选下暂无班级' : '还没有班级数据' }}</strong>
            <p>
              {{
                searchQuery || statusFilter
                  ? '可以调整关键词或状态条件，继续查看其他班级。'
                  : '先创建班级后，这里会展示真实班级规模、状态与维护动作。'
              }}
            </p>
            <button class="classes-console__empty-action" type="button" @click="resetFilters">
              {{ searchQuery || statusFilter ? '重置筛选' : '刷新列表' }}
            </button>
          </div>
        </template>
      </el-table>

      <div class="classes-console__table-footer">
        <div class="classes-console__results-copy">
          第 {{ pageStart }} - {{ pageEnd }} 项，共 {{ filteredRows.length }} 个班级
        </div>

        <div class="classes-console__pagination">
          <button class="classes-console__pagination-button" type="button" :disabled="currentPage === 1" @click="goToPrevPage">
            上一页
          </button>
          <button class="classes-console__pagination-button" type="button" :disabled="currentPage === totalPages" @click="goToNextPage">
            下一页
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { listClasses } from '@/api/classes';
import type { ClassStatus, TeachingClass } from '@/types/class';

const PAGE_SIZE = 4;

const headerCellStyle = {
  background: '#f8fafc',
  color: '#64748b',
  fontWeight: '700',
  textTransform: 'uppercase',
  fontSize: '11px',
  letterSpacing: '0.08em',
};

const quickStatusOptions: Array<{ label: string; value: '' | ClassStatus }> = [
  { label: '全部', value: '' },
  { label: '启用中', value: 'ACTIVE' },
  { label: '已归档', value: 'ARCHIVED' },
];

const loading = ref(false);
const rows = ref<TeachingClass[]>([]);
const searchQuery = ref('');
const statusFilter = ref<ClassStatus | ''>('');
const activeQuickStatus = ref<'' | ClassStatus>('');
const currentPage = ref(1);

const assignmentLabel = (row: TeachingClass) => {
  const assignment = row.myAssignment;
  if (!assignment) return '未指派';
  if (assignment.role === 'HEAD') return '班主任';
  return assignment.subjectName || '学科教师';
};

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();

  return rows.value.filter((row) => {
    const matchesKeyword = !keyword
      || row.name.toLowerCase().includes(keyword)
      || row.code.toLowerCase().includes(keyword);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

const totalStudents = computed(() => rows.value.reduce((sum, row) => sum + (row.studentCount || 0), 0));
const activeCount = computed(() => rows.value.filter((row) => row.status === 'ACTIVE').length);
const archivedCount = computed(() => rows.value.filter((row) => row.status === 'ARCHIVED').length);
const averageStudents = computed(() => {
  if (!rows.value.length) {
    return '0';
  }
  return Math.round(totalStudents.value / rows.value.length).toString();
});
const maxStudents = computed(() => Math.max(...rows.value.map((row) => row.studentCount || 0), 0));

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / PAGE_SIZE)));
const paginatedRows = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE;
  return filteredRows.value.slice(start, start + PAGE_SIZE);
});

const pageStart = computed(() => {
  if (!filteredRows.value.length) {
    return 0;
  }
  return (currentPage.value - 1) * PAGE_SIZE + 1;
});

const pageEnd = computed(() => {
  if (!filteredRows.value.length) {
    return 0;
  }
  return Math.min(currentPage.value * PAGE_SIZE, filteredRows.value.length);
});

const filterSummaryText = computed(() => {
  const keywordText = searchQuery.value.trim() ? `关键词“${searchQuery.value.trim()}”` : '未使用关键词';
  const statusText = statusFilter.value === 'ACTIVE'
    ? '仅看启用中'
    : statusFilter.value === 'ARCHIVED'
      ? '仅看已归档'
      : '全部状态';
  return `${keywordText} · ${statusText}`;
});

const barHeights = computed(() => {
  const values = rows.value.slice(0, 6).map((row) => progressPercent(row.studentCount));
  if (!values.length) {
    return [26, 44, 38, 58, 72, 84];
  }
  while (values.length < 6) {
    values.push(values[values.length - 1] || 32);
  }
  return values;
});

watch([filteredRows, totalPages], () => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value;
  }
  if (currentPage.value < 1) {
    currentPage.value = 1;
  }
});

const resetFilters = () => {
  searchQuery.value = '';
  statusFilter.value = '';
  activeQuickStatus.value = '';
  currentPage.value = 1;
};

const applyQuickStatus = (value: '' | ClassStatus) => {
  activeQuickStatus.value = value;
  statusFilter.value = value;
  currentPage.value = 1;
};

const progressPercent = (studentCount: number) => {
  if (!maxStudents.value) {
    return 0;
  }
  return Math.max(8, Math.round((studentCount / maxStudents.value) * 100));
};

const studentScaleLabel = (studentCount: number) => {
  if (studentCount >= 100) {
    return '大班规模';
  }
  if (studentCount >= 40) {
    return '标准班规模';
  }
  if (studentCount > 0) {
    return '小班规模';
  }
  return '待接入学生';
};

const progressCaption = (studentCount: number) => {
  if (!studentCount) {
    return '当前尚未分配学生';
  }
  if (studentCount === maxStudents.value) {
    return '当前列表中的最大班级';
  }
  if (studentCount >= Math.round(maxStudents.value * 0.6)) {
    return '已达到较高班级规模';
  }
  return '规模仍有增长空间';
};

const goToPrevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value -= 1;
  }
};

const goToNextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value += 1;
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await listClasses();
    rows.value = res.items;
  } finally {
    loading.value = false;
  }
};

onMounted(fetchData);
</script>

<style scoped>
.classes-console {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.classes-console__hero {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.classes-console__breadcrumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.classes-console__breadcrumbs-current {
  color: #0f172a;
}

.classes-console__heading-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.classes-console__title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.05;
  color: #0f172a;
}

.classes-console__subtitle {
  margin: 4px 0 0;
  max-width: 560px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.classes-console__hero-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.classes-console__ghost-button,
.classes-console__primary-button,
.classes-console__empty-action,
.classes-console__toolbar-link,
.classes-console__support-link,
.classes-console__pagination-button,
.classes-console__quick-switch,
.class-row__icon-button {
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
}

.classes-console__ghost-button,
.classes-console__primary-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px;
  height: 34px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
}

.classes-console__ghost-button {
  background: #fff;
  color: #334155;
  border: 1px solid #dbe4f0;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
}

.classes-console__ghost-button:hover {
  transform: translateY(-1px);
  border-color: #bfd5e7;
}

.classes-console__primary-button {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 12px 24px rgba(20, 184, 166, 0.2);
}

.classes-console__primary-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 28px rgba(20, 184, 166, 0.24);
}

.classes-console__stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.classes-console__stat-card {
  display: flex;
  gap: 14px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.06);
}

.classes-console__stat-card--dark {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  border-color: transparent;
  color: #fff;
}

.classes-console__stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  color: inherit;
  flex-shrink: 0;
}

.classes-console__stat-icon--soft {
  background: #ecfeff;
  color: #0f766e;
}

.classes-console__stat-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: inherit;
  opacity: 0.82;
}

.classes-console__stat-value {
  margin-top: 6px;
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
}

.classes-console__stat-card--dark .classes-console__stat-value {
  color: #fff;
}

.classes-console__stat-value--teal {
  color: #0f766e;
}

.classes-console__stat-note {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.classes-console__stat-card--dark .classes-console__stat-note {
  color: rgba(255, 255, 255, 0.84);
}

.classes-console__toolbar,
.classes-console__table-shell,
.classes-console__support-card {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.04);
}

.classes-console__toolbar {
  padding: 12px 14px;
}

.classes-console__toolbar-main {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(180px, 220px) minmax(0, 280px);
  gap: 10px;
  align-items: end;
}

.classes-console__search-field,
.classes-console__filter-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.classes-console__search-field {
  position: relative;
}

.classes-console__search-field > .material-symbols-outlined {
  position: absolute;
  top: 10px;
  left: 12px;
  z-index: 1;
  color: #94a3b8;
  font-size: 16px;
}

.classes-console__search-field :deep(.el-input__wrapper) {
  min-height: 36px;
  padding-left: 32px;
  border-radius: 10px;
  box-shadow: inset 0 0 0 1px #dbe4f0;
  background: #fff;
}

.classes-console__filter-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.classes-console__filter-field :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: 10px;
  box-shadow: inset 0 0 0 1px #dbe4f0;
  background: #fff;
}

.classes-console__quick-switches {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  padding: 3px;
  min-height: 36px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.classes-console__quick-switch {
  padding: 7px 10px;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
}

.classes-console__quick-switch.is-active {
  background: #ffffff;
  color: #0f766e;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.06);
}

.classes-console__toolbar-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: #64748b;
  font-size: 10px;
}

.classes-console__toolbar-dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
}

.classes-console__toolbar-link {
  padding: 0;
  background: transparent;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.classes-console__table-shell {
  overflow: hidden;
}

.classes-console__table-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px 0;
}

.classes-console__section-kicker {
  margin: 0 0 2px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.classes-console__section-title {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 800;
}

.classes-console__section-description {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 11px;
}

.classes-console__table-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.classes-console__summary-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
}

.classes-console__table {
  margin-top: 6px;
}

.classes-console__table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.classes-console__table :deep(.el-table__cell) {
  padding: 9px 0;
}

.class-row__identity {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.class-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  flex-shrink: 0;
}

.class-row__icon.is-active {
  background: #ccfbf1;
  color: #0f766e;
}

.class-row__icon.is-archived {
  background: #f1f5f9;
  color: #64748b;
}

.class-row__identity-main {
  min-width: 0;
}

.class-row__title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.class-row__title-line strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.class-row__subline {
  margin-top: 1px;
  color: #64748b;
  font-size: 10px;
}

.class-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 10px;
}

.class-row__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.class-row__meta .material-symbols-outlined {
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
}

.status-pill__dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
}

.status-pill--active {
  background: #dcfce7;
  color: #15803d;
}

.status-pill--active .status-pill__dot {
  background: #22c55e;
}

.status-pill--archived {
  background: #f1f5f9;
  color: #475569;
}

.status-pill--archived .status-pill__dot {
  background: #94a3b8;
}

.class-row__summary {
  display: grid;
  gap: 6px;
}

.class-row__summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.class-row__summary-label {
  color: #94a3b8;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.class-row__summary-value {
  color: #0f172a;
  font-size: 11px;
  font-weight: 700;
}

.class-row__progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.class-row__progress-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #0f172a;
  font-size: 10px;
  font-weight: 700;
}

.class-row__progress-track {
  width: 100%;
  height: 5px;
  border-radius: 999px;
  overflow: hidden;
  background: #e2e8f0;
}

.class-row__progress-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.class-row__progress-fill.is-active {
  background: linear-gradient(90deg, #14b8a6, #0f766e);
}

.class-row__progress-fill.is-archived {
  background: linear-gradient(90deg, #94a3b8, #64748b);
}

.class-row__progress-caption {
  color: #94a3b8;
  font-size: 9px;
}

.class-row__readonly-hint {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.class-row__actions {
  display: flex;
  justify-content: flex-end;
  gap: 5px;
}

.class-row__icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #f0fdfa;
  color: #0f766e;
}

.class-row__icon-button:hover {
  transform: translateY(-1px);
}

.class-row__icon-button--neutral {
  background: #eff6ff;
  color: #1d4ed8;
}

.class-row__icon-button--danger {
  background: #fef2f2;
  color: #dc2626;
}

.classes-console__empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 16px;
  text-align: center;
}

.classes-console__empty-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 22px;
  background: linear-gradient(135deg, #ecfeff, #ccfbf1);
  color: #0f766e;
}

.classes-console__empty-visual .material-symbols-outlined {
  font-size: 30px;
}

.classes-console__empty-state strong {
  color: #0f172a;
  font-size: 18px;
}

.classes-console__empty-state p {
  margin: 0;
  max-width: 420px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.classes-console__empty-action {
  margin-top: 6px;
  padding: 10px 16px;
  border-radius: 12px;
  background: #0f766e;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.classes-console__table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 14px 12px;
  border-top: 1px solid #edf2f7;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.classes-console__results-copy {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}

.classes-console__pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.classes-console__pagination-button {
  padding: 6px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #dbe4f0;
  color: #475569;
  font-size: 10px;
  font-weight: 700;
}

.classes-console__pagination-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.classes-console__support-grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr 1fr;
  gap: 18px;
}

.classes-console__dialog :deep(.el-dialog) {
  border-radius: 22px;
}

.classes-console__dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 24px 24px 8px;
}

.classes-console__dialog :deep(.el-dialog__body) {
  padding: 12px 24px 10px;
}

.classes-console__dialog :deep(.el-dialog__footer) {
  padding: 8px 24px 24px;
}

@media (max-width: 1280px) {
  .classes-console__toolbar-main {
    grid-template-columns: 1fr;
  }

  .classes-console__quick-switches {
    justify-content: flex-start;
  }
}

@media (max-width: 860px) {
  .classes-console__heading-row,
  .classes-console__table-header,
  .classes-console__table-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .classes-console__hero-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .classes-console__toolbar-meta {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .classes-console__title {
    font-size: 26px;
  }

  .classes-console__ghost-button,
  .classes-console__primary-button {
    width: 100%;
    justify-content: center;
  }
}
</style>
