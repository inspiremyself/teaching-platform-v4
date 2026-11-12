<template>
  <div class="page materials-page workbench-page">
    <section class="workbench-header materials-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="materials-header__eyebrow">教师后台 / 资料管理</div>
          <h1>教学资料</h1>
          <p>集中管理上传、可见范围、下载和资料信息维护。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openUploadDialog">上传资料</el-button>
        </div>
      </div>

      <div class="workbench-meta materials-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 总资料</span>
        <span class="workbench-meta__item"><strong>{{ publicCount }}</strong> 全体可见</span>
        <span class="workbench-meta__item"><strong>{{ teacherOnlyCount }}</strong> 教师内部</span>
        <span class="workbench-meta__item workbench-meta__item--accent"><strong>{{ filteredRows.length }}</strong> 当前命中</span>
        <span class="workbench-meta__item">{{ activeVisibilityLabel }}</span>
        <span class="workbench-meta__item">{{ activeCategoryLabel }}</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchQuery" placeholder="搜索标题、文件名、类别或说明" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="visibilityFilter" placeholder="全部可见范围" clearable>
            <el-option label="全部可见范围" value="" />
            <el-option label="全体可见" value="ALL" />
            <el-option label="仅教师" value="TEACHER" />
          </el-select>
          <el-select v-model="categoryFilter" placeholder="全部类别" clearable>
            <el-option label="全部类别" value="" />
            <el-option v-for="category in categoryOptions" :key="category" :label="categoryLabel(category)" :value="category" />
          </el-select>
        </div>
      </div>
        <div class="toolbar-card__meta">
          <span>共 {{ rows.length }} 条资料</span>
          <span class="toolbar-divider"></span>
          <span>当前筛出 {{ filteredRows.length }} 条</span>
          <span class="toolbar-divider"></span>
          <span>{{ searchQuery ? `关键词“${searchQuery}”` : '未使用关键词检索' }}</span>
          <span class="toolbar-divider"></span>
          <span>{{ visibilityFilter ? visibilityLabel(visibilityFilter) : '全部范围' }} · {{ activeCategoryLabel }}</span>
          <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
        </div>
      </section>

      <section class="materials-console">
        <header class="materials-console__header">
          <div>
            <h2>资料明细</h2>
            <p>按文件、范围与说明直接执行下载、编辑和删除。</p>
          </div>
        </header>

      <div v-loading="loading" class="materials-list-shell">
        <template v-if="filteredRows.length">
          <article v-for="row in filteredRows" :key="row.id" class="material-row">
            <div :class="['material-row__icon', `material-row__icon--${fileTone(row.fileName)}`]">
              <span class="material-symbols-outlined">{{ fileIcon(row.fileName) }}</span>
            </div>

            <div class="material-row__primary">
              <div class="material-row__title-line">
                <h3>{{ row.title }}</h3>
                <span class="material-chip material-chip--category">{{ categoryLabel(row.category) }}</span>
              </div>
              <p class="material-row__description">{{ row.description || '暂无资料说明' }}</p>
              <div class="material-row__meta">
                <span>
                  <span class="material-symbols-outlined">attach_file</span>
                  {{ row.fileName }}
                </span>
                <span>
                  <span class="material-symbols-outlined">badge</span>
                  上传者 #{{ row.uploaderUserId }}
                </span>
              </div>
            </div>

            <div class="material-row__visibility">
              <span :class="['material-chip', row.visibility === 'ALL' ? 'material-chip--public' : 'material-chip--teacher']">
                <span class="material-chip__dot"></span>
                {{ visibilityLabel(row.visibility) }}
              </span>
              <small>{{ visibilityHint(row.visibility) }}</small>
            </div>

            <div class="material-row__actions">
              <el-button type="primary" plain @click="download(row.id, row.fileName)">下载</el-button>
              <el-button @click="openEditDialog(row)">编辑资料信息</el-button>
              <el-button type="danger" plain @click="remove(row.id)">删除</el-button>
            </div>
          </article>
        </template>
        <el-empty v-else description="暂无匹配资料" />
      </div>
    </section>

    <el-dialog v-model="uploadDialogVisible" width="760px" class="materials-dialog" destroy-on-close>
      <div class="materials-dialog__shell">
        <header class="materials-dialog__header">
          <div>
            <p class="materials-dialog__eyebrow">教学资料 / 上传流程</p>
            <h2>上传教学资料</h2>
            <p class="materials-dialog__desc">补充资料标题、类别、说明和可见范围，文件上传仍走当前真实资源接口。</p>
          </div>
          <div class="materials-dialog__header-side">
            <span class="materials-dialog__badge">资料入库</span>
            <span class="materials-dialog__badge materials-dialog__badge--muted">真实文件上传</span>
          </div>
        </header>

        <section class="materials-dialog__context-grid">
          <article class="materials-dialog__context-card">
            <span class="materials-dialog__label">可见范围说明</span>
            <strong>{{ visibilityLabel(uploadForm.visibility) }}</strong>
            <p>{{ visibilityHint(uploadForm.visibility) }}</p>
          </article>
          <article class="materials-dialog__context-card">
            <span class="materials-dialog__label">上传状态</span>
            <strong>{{ selectedFile ? selectedFile.name : '尚未选择文件' }}</strong>
            <p>{{ selectedFile ? '文件已就绪，保存后将写入资料台账。' : '请先选择一个本地文件，再执行上传。' }}</p>
          </article>
        </section>

        <el-form class="materials-dialog__form" label-position="top">
          <section class="materials-dialog__group">
              <div class="materials-dialog__group-title">资料信息</div>
            <div class="materials-dialog__grid materials-dialog__grid--2col">
              <el-form-item label="资料标题"><el-input v-model="uploadForm.title" placeholder="例如：实验一讲义" /></el-form-item>
              <el-form-item label="资料类别">
                <el-select v-model="uploadForm.category" placeholder="请选择资料类别">
                  <el-option v-for="category in materialCategoryOptions" :key="`upload-${category}`" :label="categoryLabel(category)" :value="category" />
                </el-select>
              </el-form-item>
              <el-form-item label="学科" required>
                <el-select v-model="uploadForm.subjectId" placeholder="请选择学科">
                  <el-option v-for="item in subjectOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="资料说明"><el-input v-model="uploadForm.description" type="textarea" :rows="4" placeholder="补充适用章节、投放对象或使用方式" /></el-form-item>
          </section>

          <section class="materials-dialog__group">
            <div class="materials-dialog__group-title">发布控制</div>
            <div class="materials-dialog__grid materials-dialog__grid--2col">
              <el-form-item label="可见范围">
                <el-select v-model="uploadForm.visibility" placeholder="请选择可见范围">
                  <el-option label="全体可见" value="ALL" />
                  <el-option label="仅教师" value="TEACHER" />
                </el-select>
              </el-form-item>
              <el-form-item label="上传文件">
                <input class="materials-dialog__file-input" type="file" @change="handleFileChange" />
              </el-form-item>
            </div>
          </section>
        </el-form>

        <footer class="materials-dialog__footer">
          <div class="materials-dialog__footer-note">上传接口、文件选择与保存方式保持不变，本次仅优化资料管理文案表达。</div>
          <div class="materials-dialog__footer-actions">
            <el-button @click="uploadDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="uploading" @click="submitUpload">上传资料</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" width="760px" class="materials-dialog" destroy-on-close>
      <div class="materials-dialog__shell">
        <header class="materials-dialog__header">
          <div>
            <p class="materials-dialog__eyebrow">教学资料 / 信息维护</p>
            <h2>编辑资料信息</h2>
            <p class="materials-dialog__desc">更新标题、类别、说明和可见范围，不改变当前资料文件本身。</p>
          </div>
          <div class="materials-dialog__header-side">
            <span class="materials-dialog__badge">信息更新</span>
            <span class="materials-dialog__badge materials-dialog__badge--muted">文件内容不变</span>
          </div>
        </header>

        <section class="materials-dialog__context-grid">
          <article class="materials-dialog__context-card">
            <span class="materials-dialog__label">当前可见范围</span>
            <strong>{{ visibilityLabel(editForm.visibility) }}</strong>
            <p>{{ visibilityHint(editForm.visibility) }}</p>
          </article>
          <article class="materials-dialog__context-card">
            <span class="materials-dialog__label">维护目标</span>
            <strong>统一资料台账字段</strong>
            <p>建议优先补齐说明和类别，让教师端与学生端检索语义保持一致。</p>
          </article>
        </section>

        <el-form class="materials-dialog__form" label-position="top">
          <section class="materials-dialog__group">
              <div class="materials-dialog__group-title">资料信息</div>
            <div class="materials-dialog__grid materials-dialog__grid--2col">
              <el-form-item label="资料标题"><el-input v-model="editForm.title" placeholder="例如：实验一讲义" /></el-form-item>
              <el-form-item label="资料类别">
                <el-select v-model="editForm.category" placeholder="请选择资料类别">
                  <el-option v-for="category in materialCategoryOptions" :key="`edit-${category}`" :label="categoryLabel(category)" :value="category" />
                </el-select>
              </el-form-item>
              <el-form-item label="学科" :required="false">
                <el-select v-model="editForm.subjectId" placeholder="保持原值">
                  <el-option label="保持原值" :value="0" />
                  <el-option v-for="item in subjectOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="资料说明"><el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="补充适用章节、投放对象或使用方式" /></el-form-item>
            <el-form-item label="可见范围">
              <el-select v-model="editForm.visibility" placeholder="请选择可见范围">
                <el-option label="全体可见" value="ALL" />
                <el-option label="仅教师" value="TEACHER" />
              </el-select>
            </el-form-item>
          </section>
        </el-form>

        <footer class="materials-dialog__footer">
          <div class="materials-dialog__footer-note">仅更新资料展示信息，不新增任何额外业务逻辑。</div>
          <div class="materials-dialog__footer-actions">
            <el-button @click="editDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitEdit">保存修改</el-button>
          </div>
        </footer>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { deleteMaterial, downloadMaterial, listMaterials, updateMaterial, uploadMaterial } from '@/api/materials';
import { listSubjects } from '@/api/admin';
import type { AdminSubject } from '@/types/admin';
import type { MaterialItem, MaterialVisibility } from '@/types/material';

const loading = ref(false);
const uploading = ref(false);
const saving = ref(false);
const uploadDialogVisible = ref(false);
const editDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const selectedFile = ref<File | null>(null);
const rows = ref<MaterialItem[]>([]);
const searchQuery = ref('');
const visibilityFilter = ref<MaterialVisibility | ''>('');
const categoryFilter = ref('');

const uploadForm = reactive({
  title: '',
  category: 'COURSE',
  description: '',
  visibility: 'ALL' as MaterialVisibility,
  subjectId: 0,
});

const subjectOptions = ref<AdminSubject[]>([]);
const fetchSubjects = async () => {
  subjectOptions.value = await listSubjects('ACTIVE');
  uploadForm.subjectId = subjectOptions.value[0]?.id ?? 0;
};

const editForm = reactive({
  title: '',
  category: 'COURSE',
  description: '',
  visibility: 'ALL' as MaterialVisibility,
  subjectId: 0,
});

const materialCategoryOptions = ['COURSE', 'LECTURE', 'LAB', 'HOMEWORK', 'EXAM', 'NOTICE'];

const publicCount = computed(() => rows.value.filter(row => row.visibility === 'ALL').length);
const teacherOnlyCount = computed(() => rows.value.filter(row => row.visibility === 'TEACHER').length);

const categoryOptions = computed(() => {
  const categories = rows.value.map(row => row.category.trim()).filter(Boolean);
  const extraCategories = [...new Set(categories)].filter(category => !materialCategoryOptions.includes(category));
  return [...materialCategoryOptions, ...extraCategories];
});

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();

  return rows.value.filter(row => {
    const matchesKeyword = !keyword
      || row.title.toLowerCase().includes(keyword)
      || row.fileName.toLowerCase().includes(keyword)
      || row.category.toLowerCase().includes(keyword)
      || row.description.toLowerCase().includes(keyword);
    const matchesVisibility = !visibilityFilter.value || row.visibility === visibilityFilter.value;
    const matchesCategory = !categoryFilter.value || row.category === categoryFilter.value;
    return matchesKeyword && matchesVisibility && matchesCategory;
  });
});

const activeVisibilityLabel = computed(() => visibilityFilter.value ? visibilityLabel(visibilityFilter.value) : '全部可见范围');
const activeCategoryLabel = computed(() => categoryFilter.value ? categoryLabel(categoryFilter.value) : '全部类别');

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listMaterials();
  } finally {
    loading.value = false;
  }
};

const resetUploadForm = () => {
  uploadForm.title = '';
  uploadForm.category = 'COURSE';
  uploadForm.description = '';
  uploadForm.visibility = 'ALL';
  uploadForm.subjectId = subjectOptions.value[0]?.id ?? 0;
  selectedFile.value = null;
};

const resetFilters = () => {
  searchQuery.value = '';
  visibilityFilter.value = '';
  categoryFilter.value = '';
};

const openUploadDialog = () => {
  resetUploadForm();
  uploadDialogVisible.value = true;
};

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
};

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的文件');
    return;
  }
  // 新建必填：学科列表为空或加载失败时本地拦截，不带 subjectId=0 提交
  if (!uploadForm.subjectId) {
    ElMessage.warning('请先在学科管理中启用至少一个 ACTIVE 学科');
    return;
  }

  uploading.value = true;
  try {
    await uploadMaterial({ ...uploadForm, file: selectedFile.value });
    ElMessage.success('资料上传成功');
    uploadDialogVisible.value = false;
    await fetchData();
  } finally {
    uploading.value = false;
  }
};

const openEditDialog = (row: MaterialItem) => {
  editingId.value = row.id;
  editForm.title = row.title;
  editForm.category = row.category;
  editForm.description = row.description;
  editForm.visibility = row.visibility;
  // 详情不含 subjectId；编辑态默认「保持原值」
  editForm.subjectId = 0;
  editDialogVisible.value = true;
};

const submitEdit = async () => {
  if (!editingId.value) {
    return;
  }

  saving.value = true;
  try {
    // 编辑态选中「保持原值」时省略 subjectId，避免覆盖历史 NULL / 原学科
    const { subjectId, ...rest } = editForm;
    await updateMaterial(editingId.value, subjectId ? { ...editForm } : rest);
    ElMessage.success('资料信息更新成功');
    editDialogVisible.value = false;
    await fetchData();
  } finally {
    saving.value = false;
  }
};

const remove = async (id: number) => {
  await ElMessageBox.confirm('删除后无法恢复，是否继续？', '删除确认', { type: 'warning' });
  await deleteMaterial(id);
  ElMessage.success('资料删除成功');
  await fetchData();
};

const download = async (id: number, fileName: string) => {
  try {
    await downloadMaterial(id, fileName);
  } catch {
    ElMessage.error('资料下载失败，请稍后重试');
  }
};

const visibilityLabel = (visibility: MaterialVisibility) => (visibility === 'ALL' ? '全体可见' : '仅教师');

const visibilityHint = (visibility: MaterialVisibility) => (visibility === 'ALL' ? '学生与教师均可下载' : '仅教师工作台可见');

const categoryLabel = (category: string) => {
  const normalized = category.trim();
  if (!normalized) {
    return '未分类';
  }

  const dictionary: Record<string, string> = {
    COURSE: '课程资料',
    LECTURE: '课件讲义',
    LAB: '实验附件',
    HOMEWORK: '作业材料',
    EXAM: '考试文档',
    NOTICE: '通知公告',
  };

  return dictionary[normalized] ?? normalized;
};

const fileIcon = (fileName: string) => {
  const lowerName = fileName.toLowerCase();
  if (lowerName.endsWith('.pdf')) {
    return 'picture_as_pdf';
  }
  if (lowerName.endsWith('.ppt') || lowerName.endsWith('.pptx')) {
    return 'slideshow';
  }
  if (lowerName.endsWith('.doc') || lowerName.endsWith('.docx')) {
    return 'description';
  }
  if (lowerName.endsWith('.xls') || lowerName.endsWith('.xlsx')) {
    return 'table_chart';
  }
  if (lowerName.endsWith('.zip') || lowerName.endsWith('.rar') || lowerName.endsWith('.7z')) {
    return 'folder_zip';
  }
  if (lowerName.endsWith('.png') || lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg') || lowerName.endsWith('.gif')) {
    return 'image';
  }
  return 'draft';
};

const fileTone = (fileName: string) => {
  const icon = fileIcon(fileName);
  if (icon === 'picture_as_pdf') {
    return 'red';
  }
  if (icon === 'slideshow') {
    return 'orange';
  }
  if (icon === 'table_chart') {
    return 'emerald';
  }
  if (icon === 'folder_zip') {
    return 'violet';
  }
  if (icon === 'image') {
    return 'cyan';
  }
  return 'blue';
};

onMounted(() => {
  void fetchSubjects().catch(() => { subjectOptions.value = []; });
  void fetchData();
});
</script>

<style scoped>
.materials-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.materials-meta {
  row-gap: 10px;
}

.materials-header__eyebrow {
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.toolbar-card,
.materials-console {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.toolbar-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
}

.toolbar-card__main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.toolbar-search > .material-symbols-outlined {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #eff6ff;
  color: #2563eb;
  flex-shrink: 0;
}

.toolbar-search :deep(.el-input__wrapper) {
  min-height: 40px;
  border-radius: 12px;
  box-shadow: none;
  background: #f8fafc;
}

.toolbar-filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-filters :deep(.el-select) {
  width: 172px;
}

.toolbar-filters :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 12px;
  box-shadow: none;
  background: #f8fafc;
}

.toolbar-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 12px;
}

.toolbar-divider {
  width: 1px;
  height: 12px;
  background: #cbd5e1;
}

.materials-console {
  padding: 16px;
}

.materials-console__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}

.materials-console__header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.materials-console__header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.materials-list-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
}

.material-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1.9fr) minmax(160px, 0.7fr) minmax(220px, 0.95fr);
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
  border: 1px solid #e5edf7;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.material-row:hover {
  transform: translateY(-1px);
  border-color: #bfdbfe;
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.08);
}

.material-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
}

.material-row__icon .material-symbols-outlined {
  font-size: 22px;
}

.material-row__icon--red { background: #fef2f2; color: #dc2626; }
.material-row__icon--orange { background: #fff7ed; color: #ea580c; }
.material-row__icon--emerald { background: #ecfdf5; color: #059669; }
.material-row__icon--violet { background: #f5f3ff; color: #7c3aed; }
.material-row__icon--cyan { background: #ecfeff; color: #0891b2; }
.material-row__icon--blue { background: #eff6ff; color: #2563eb; }

.material-row__primary {
  min-width: 0;
}

.material-row__title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.material-row__title-line h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.material-row__description {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: #475569;
}

.material-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.material-row__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.material-row__meta .material-symbols-outlined {
  font-size: 16px;
}

.material-row__visibility {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.material-row__visibility small {
  color: #64748b;
  line-height: 1.4;
  font-size: 12px;
}

.material-row__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.material-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.material-chip--category {
  background: #eff6ff;
  color: #1d4ed8;
}

.material-chip--public {
  background: #ecfdf5;
  color: #047857;
}

.material-chip--teacher {
  background: #fff7ed;
  color: #c2410c;
}

.material-chip__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.materials-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.materials-dialog :deep(.el-dialog__header) {
  display: none;
}

.materials-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.materials-dialog__shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.materials-dialog__header,
.materials-dialog__context-card,
.materials-dialog__group,
.materials-dialog__footer {
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
}

.materials-dialog__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(239, 246, 255, 0.96));
}

.materials-dialog__eyebrow,
.materials-dialog__label {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.materials-dialog__header h2 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 800;
}

.materials-dialog__desc {
  margin: 8px 0 0;
  max-width: 540px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.materials-dialog__header-side {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.materials-dialog__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.materials-dialog__badge--muted {
  background: #f1f5f9;
  color: #475569;
}

.materials-dialog__context-grid,
.materials-dialog__grid--2col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.materials-dialog__context-card,
.materials-dialog__group {
  padding: 18px;
}

.materials-dialog__context-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.materials-dialog__context-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.materials-dialog__group-title {
  margin-bottom: 14px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.materials-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.materials-dialog__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.materials-dialog__form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.materials-dialog__form :deep(.el-input__wrapper),
.materials-dialog__form :deep(.el-select__wrapper),
.materials-dialog__form :deep(.el-textarea__inner) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: #f8fafc;
}

.materials-dialog__file-input {
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  border: 1px dashed #bfdbfe;
  border-radius: 14px;
  background: #eff6ff;
  color: #334155;
}

.materials-dialog__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.materials-dialog__footer-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.materials-dialog__footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1280px) {
  .material-row {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .material-row__visibility,
  .material-row__actions {
    grid-column: 2;
  }

  .material-row__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 960px) {
  .toolbar-card__main,
  .materials-console__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-filters {
    width: 100%;
    flex-direction: column;
  }

  .toolbar-filters :deep(.el-select) {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .toolbar-card,
  .materials-console {
    padding: 18px;
    border-radius: 22px;
  }

  .material-row {
    grid-template-columns: 1fr;
  }

  .material-row__icon,
  .material-row__visibility,
  .material-row__actions {
    grid-column: auto;
  }

  .material-row__actions {
    justify-content: flex-start;
  }

  .materials-dialog__header,
  .materials-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .materials-dialog__header-side {
    justify-content: flex-start;
  }

  .materials-dialog__context-grid,
  .materials-dialog__grid--2col {
    grid-template-columns: 1fr;
  }
}
</style>
