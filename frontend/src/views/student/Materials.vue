<template>
  <div class="student-materials-page workbench-page">
    <section class="workbench-header student-materials-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="student-materials-header__eyebrow">学生端 / 教学资料</div>
          <h1>课程资料工作区</h1>
          <p>优先查看最新更新、按类别快速筛选，并在同一页面完成资料判断与下载。</p>
        </div>
      </div>

      <div class="workbench-meta student-materials-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 当前筛出</span>
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 全部公开资料</span>
        <span class="workbench-meta__item">{{ activeCategoryLabel }}</span>
        <span class="workbench-meta__item">公开资料</span>
      </div>
    </section>

    <section class="student-materials-updates">
      <div class="student-materials-section-head">
        <div class="student-materials-section-head__title-block">
          <div class="student-materials-section-head__eyebrow">最近更新</div>
          <div class="student-materials-section-head__headline">
            <h2>最近更新</h2>
            <span class="student-materials-section-head__count">{{ highlightedRows.length }} 份</span>
          </div>
          <p class="student-materials-section-head__desc">优先展示最新可见资料，并保留一键下载入口。</p>
        </div>
        <span class="student-materials-section-head__hint">压缩展示，避免挤占资料列表区域</span>
      </div>

      <div class="student-materials-updates__grid">
        <article v-for="row in highlightedRows" :key="row.id" class="update-card">
          <div class="update-card__leading">
            <div :class="['update-card__icon', `update-card__icon--${fileTone(row)}`]">
              <span class="material-symbols-outlined">{{ fileIcon(row) }}</span>
            </div>

            <div class="update-card__body">
              <div class="update-card__topline">
                <h3>{{ row.title }}</h3>
                <span class="update-card__badge">最新</span>
              </div>
              <p>{{ row.description || '可直接下载查看的课程资料。' }}</p>
              <div class="update-card__meta">
                <span>{{ row.category || '未分类资料' }}</span>
                <span>{{ fileExtensionLabel(row.fileName) }}</span>
                <span>{{ visibilityLabel(row.visibility) }}</span>
              </div>
            </div>
          </div>

          <el-button class="update-card__button" type="primary" plain @click="download(row.id, row.fileName)">
            <span class="material-symbols-outlined">download</span>
            下载资料
          </el-button>
        </article>
      </div>
    </section>

    <section class="student-materials-toolbar">
      <div class="student-materials-toolbar__main">
        <div class="student-materials-search">
          <span class="material-symbols-outlined">filter_list</span>
          <el-input v-model="searchQuery" placeholder="按资料名称、类别或说明筛选" clearable />
        </div>

        <div class="student-materials-toolbar__filters">
          <el-select v-model="categoryFilter" placeholder="全部类别" clearable>
            <el-option label="全部类别" value="" />
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
        </div>
      </div>

      <div class="student-materials-toolbar__meta">
        <span>共 {{ rows.length }} 份公开资料</span>
        <span class="student-materials-toolbar__divider"></span>
        <span>{{ currentFilterSummary }}</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="student-materials-list" v-loading="loading">
      <template v-if="filteredRows.length">
        <article v-for="row in filteredRows" :key="row.id" class="material-row-card">
          <div :class="['material-row-card__icon', `material-row-card__icon--${fileTone(row)}`]">
            <span class="material-symbols-outlined">{{ fileIcon(row) }}</span>
          </div>

          <div class="material-row-card__main">
            <div class="material-row-card__title-line">
              <div>
                <h3>{{ row.title }}</h3>
                <p>{{ row.description || '当前资料未提供额外说明，请结合文件名与类别判断后下载。' }}</p>
              </div>
              <span class="material-row-card__tag">{{ requirementLabel(row) }}</span>
            </div>

            <div class="material-row-card__meta">
              <span>
                <span class="material-symbols-outlined">school</span>
                {{ row.category || '未分类资料' }}
              </span>
              <span>
                <span class="material-symbols-outlined">description</span>
                {{ row.fileName }}
              </span>
              <span>
                <span class="material-symbols-outlined">visibility</span>
                {{ visibilityLabel(row.visibility) }}
              </span>
            </div>
          </div>

          <div class="material-row-card__actions">
            <div class="material-row-card__action-text">
              <strong>{{ fileExtensionLabel(row.fileName) }}</strong>
              <small>可直接下载到本地学习</small>
            </div>
            <el-button type="primary" round @click="download(row.id, row.fileName)">
              <span class="material-symbols-outlined">download</span>
              下载资料
            </el-button>
          </div>
        </article>
      </template>

      <el-empty v-else description="暂无匹配资料" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { downloadMaterial, listMaterials } from '@/api/materials';
import type { MaterialItem, MaterialVisibility } from '@/types/material';

const loading = ref(false);
const materials = ref<MaterialItem[]>([]);
const searchQuery = ref('');
const categoryFilter = ref('');

const rows = computed(() => materials.value.filter(item => item.visibility === 'ALL'));

const categoryOptions = computed(() => Array.from(new Set(rows.value.map(item => item.category).filter(Boolean))));

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();

  return rows.value.filter(item => {
    const matchesKeyword =
      !keyword
      || item.title.toLowerCase().includes(keyword)
      || item.category.toLowerCase().includes(keyword)
      || item.description.toLowerCase().includes(keyword)
      || item.fileName.toLowerCase().includes(keyword);
    const matchesCategory = !categoryFilter.value || item.category === categoryFilter.value;
    return matchesKeyword && matchesCategory;
  });
});

const highlightedRows = computed(() => filteredRows.value.slice(0, 3));

const activeCategoryLabel = computed(() => categoryFilter.value || '全部类别');
const keywordSummary = computed(() => {
  const keyword = searchQuery.value.trim();
  return keyword ? `关键词“${keyword}”生效中` : '未使用关键词';
});
const currentFilterSummary = computed(() => {
  if (!searchQuery.value.trim() && !categoryFilter.value) {
    return `当前展示 ${filteredRows.value.length} 份资料`;
  }

  return `筛出 ${filteredRows.value.length} 份资料`;
});

const visibilityLabel = (visibility: MaterialVisibility) => (visibility === 'ALL' ? '公开可见' : '教师可见');

const fileExtensionLabel = (fileName: string) => {
  const extension = fileName.split('.').pop()?.trim();
  return extension ? extension.toUpperCase() : '文件';
};

const fileIcon = (row: MaterialItem) => {
  const extension = fileExtensionLabel(row.fileName);
  if (extension === 'PDF') return 'picture_as_pdf';
  if (['PPT', 'PPTX', 'KEY'].includes(extension)) return 'slideshow';
  if (['MP4', 'AVI', 'MOV', 'WMV', 'MKV'].includes(extension)) return 'movie';
  if (['XLS', 'XLSX', 'CSV'].includes(extension)) return 'table_chart';
  if (['ZIP', 'RAR', '7Z'].includes(extension)) return 'folder_zip';
  return 'article';
};

const fileTone = (row: MaterialItem) => {
  const icon = fileIcon(row);
  if (icon === 'picture_as_pdf') return 'red';
  if (icon === 'slideshow') return 'blue';
  if (icon === 'movie') return 'indigo';
  if (icon === 'table_chart') return 'amber';
  if (icon === 'folder_zip') return 'slate';
  return 'teal';
};

const requirementLabel = (row: MaterialItem) => (row.description?.trim() ? '课程资料' : '待查看');

const download = async (id: number, fileName: string) => {
  try {
    await downloadMaterial(id, fileName);
  } catch {
    ElMessage.error('资料下载失败，请稍后重试');
  }
};

const resetFilters = () => {
  searchQuery.value = '';
  categoryFilter.value = '';
};

const fetchData = async () => {
  loading.value = true;
  try {
    materials.value = await listMaterials();
  } finally {
    loading.value = false;
  }
};

onMounted(fetchData);
</script>

<style scoped>
.student-materials-page {
  --materials-primary: #0f766e;
  --materials-primary-deep: #115e59;
  --materials-primary-soft: #ecfdf5;
  --materials-surface: rgba(255, 255, 255, 0.9);
  --materials-border: rgba(226, 232, 240, 0.95);
  --materials-text: #111827;
  --materials-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.student-materials-hero,
.student-materials-toolbar,
.materials-summary-card,
.material-row-card,
.update-card {
  border: 1px solid var(--materials-border);
  background: var(--materials-surface);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.student-materials-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.55fr);
  gap: 18px;
  padding: 30px 32px;
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(45, 212, 191, 0.18), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(240, 253, 250, 0.96));
}

.student-materials-hero__eyebrow,
.student-materials-section-head__eyebrow,
.materials-summary-card__label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.student-materials-hero__eyebrow {
  color: var(--materials-primary);
}

.student-materials-hero h1,
.student-materials-section-head h2,
.update-card h3,
.material-row-card h3 {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
  color: var(--materials-text);
}

.student-materials-hero h1 {
  margin-top: 10px;
  font-size: 34px;
}

.student-materials-hero p,
.student-materials-hero__panel p,
.update-card p,
.material-row-card p,
.materials-summary-card__desc,
.student-materials-section-head__hint {
  color: var(--materials-muted);
  line-height: 1.7;
}

.student-materials-hero p {
  margin: 12px 0 0;
  max-width: 760px;
  font-size: 15px;
}

.student-materials-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 22px;
}

.student-materials-hero__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(15, 118, 110, 0.08);
  color: var(--materials-primary-deep);
}

.student-materials-hero__meta-item--soft {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(204, 251, 241, 0.95);
}

.student-materials-hero__meta-item .material-symbols-outlined {
  font-size: 24px;
}

.student-materials-hero__meta-item strong {
  display: block;
  font-size: 22px;
}

.student-materials-hero__meta-item small {
  color: #4b5563;
}

.student-materials-hero__panel {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #0f766e, #115e59);
  color: #fff;
}

.student-materials-hero__panel strong,
.student-materials-hero__panel p {
  color: #fff;
}

.student-materials-hero__panel-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.16);
}

.student-materials-updates {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.student-materials-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.student-materials-section-head__title-block {
  min-width: 0;
}

.student-materials-section-head__eyebrow {
  color: #94a3b8;
}

.student-materials-section-head__headline {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.student-materials-section-head h2 {
  font-size: 22px;
}

.student-materials-section-head__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: var(--materials-primary-deep);
  font-size: 12px;
  font-weight: 800;
}

.student-materials-section-head__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--materials-muted);
}

.student-materials-section-head__hint {
  font-size: 13px;
  white-space: nowrap;
}

.student-materials-updates__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.update-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 0;
  padding: 16px 18px;
  border-radius: 22px;
}

.update-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 9px;
  border-radius: 999px;
  background: #d1fae5;
  color: #047857;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.update-card__icon,
.material-row-card__icon {
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 16px;
  flex-shrink: 0;
}

.update-card__icon .material-symbols-outlined,
.material-row-card__icon .material-symbols-outlined {
  font-size: 26px;
}

.update-card__leading {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
  flex: 1;
}

.update-card__body {
  flex: 1;
  min-width: 0;
}

.update-card__topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.update-card h3 {
  font-size: 17px;
  line-height: 1.35;
}

.update-card p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.update-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.update-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.update-card__button {
  flex-shrink: 0;
  justify-content: center;
  min-height: 38px;
  padding-inline: 14px;
  border-radius: 999px;
  color: var(--materials-primary-deep);
  font-weight: 700;
}

.update-card__button :deep(span) {
  margin-right: 6px;
}

.student-materials-toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 28px;
}

.student-materials-toolbar__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.student-materials-search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-height: 52px;
  padding: 0 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #f8fafc;
}

.student-materials-search .material-symbols-outlined {
  color: #94a3b8;
}

.student-materials-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.student-materials-toolbar__filters {
  display: flex;
  gap: 12px;
}

.student-materials-toolbar__filters :deep(.el-select) {
  width: 180px;
}

.student-materials-toolbar__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: var(--materials-muted);
  font-size: 13px;
}

.student-materials-toolbar__divider {
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}

.student-materials-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.materials-summary-card {
  display: flex;
  gap: 16px;
  padding: 22px;
  border-radius: 24px;
}

.materials-summary-card--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  border: none;
}

.materials-summary-card--primary .materials-summary-card__label,
.materials-summary-card--primary .materials-summary-card__value,
.materials-summary-card--primary .materials-summary-card__desc {
  color: #fff;
}

.materials-summary-card--focus {
  flex-direction: column;
}

.materials-summary-card__icon {
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 16px;
  background: rgba(15, 118, 110, 0.14);
  color: var(--materials-primary-deep);
  flex-shrink: 0;
}

.materials-summary-card--primary .materials-summary-card__icon {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.materials-summary-card__icon--teal {
  background: #ecfeff;
  color: #0f766e;
}

.materials-summary-card__label {
  color: #64748b;
}

.materials-summary-card__value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  color: var(--materials-text);
}

.materials-summary-card__value--small {
  font-size: 24px;
}

.materials-summary-card__desc {
  margin-top: 8px;
  font-size: 13px;
}

.materials-summary-card__header-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.materials-summary-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #ecfdf5;
  color: var(--materials-primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.materials-summary-card__desc--spaced {
  margin-top: 2px;
}

.student-materials-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.material-row-card {
  display: grid;
  grid-template-columns: 52px minmax(0, 1.5fr) minmax(220px, 0.55fr);
  gap: 18px;
  align-items: center;
  padding: 20px 22px;
  border-radius: 24px;
}

.material-row-card__main {
  min-width: 0;
}

.material-row-card__title-line {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.material-row-card h3 {
  font-size: 18px;
}

.material-row-card p {
  margin: 8px 0 0;
  font-size: 14px;
}

.material-row-card__tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  white-space: nowrap;
}

.material-row-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  color: #64748b;
  font-size: 13px;
}

.material-row-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.material-row-card__meta .material-symbols-outlined {
  font-size: 16px;
}

.material-row-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 18px;
  background: #f8fafc;
}

.material-row-card__action-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.material-row-card__action-text strong {
  color: var(--materials-text);
  font-size: 14px;
}

.material-row-card__action-text small {
  color: var(--materials-muted);
  font-size: 12px;
}

.material-row-card__actions :deep(.el-button) {
  min-height: 40px;
  padding-inline: 18px;
  border-radius: 999px;
  background: var(--materials-primary);
  border-color: var(--materials-primary);
  font-weight: 700;
}

.material-row-card__actions :deep(.el-button .material-symbols-outlined) {
  font-size: 18px;
  margin-right: 4px;
}

.update-card__icon--red,
.material-row-card__icon--red {
  background: #fef2f2;
  color: #dc2626;
}

.update-card__icon--blue,
.material-row-card__icon--blue {
  background: #eff6ff;
  color: #2563eb;
}

.update-card__icon--indigo,
.material-row-card__icon--indigo {
  background: #eef2ff;
  color: #4f46e5;
}

.update-card__icon--amber,
.material-row-card__icon--amber {
  background: #fffbeb;
  color: #d97706;
}

.update-card__icon--slate,
.material-row-card__icon--slate {
  background: #f1f5f9;
  color: #475569;
}

.update-card__icon--teal,
.material-row-card__icon--teal {
  background: #ecfdf5;
  color: #0f766e;
}

@media (max-width: 1200px) {
  .student-materials-hero,
  .student-materials-updates__grid,
  .student-materials-summary-grid,
  .material-row-card {
    grid-template-columns: 1fr;
  }

  .student-materials-toolbar__main,
  .student-materials-toolbar__filters,
  .material-row-card__title-line,
  .material-row-card__actions,
   .student-materials-section-head,
   .update-card,
   .update-card__topline {
    flex-direction: column;
    align-items: stretch;
  }

  .update-card__button {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .student-materials-hero,
  .student-materials-toolbar,
  .materials-summary-card,
  .material-row-card,
  .update-card {
    padding: 18px;
    border-radius: 22px;
  }

  .student-materials-hero__meta {
    flex-direction: column;
  }
}
</style>
