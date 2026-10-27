<template>
  <div class="labs-page">
    <section class="labs-header">
      <div class="labs-header__top">
        <div class="labs-header__main">
          <div class="labs-header__eyebrow">教师后台 / 实验管理</div>
          <h1>实验台账</h1>
          <p>按班级维护实验、题项与发布状态。</p>
        </div>
        <div class="labs-header__actions">
          <el-button @click="goReports()">查看报告队列</el-button>
          <el-button type="primary" @click="openCreateDialog">新建实验</el-button>
        </div>
      </div>

      <div class="labs-header__meta">
        <span class="labs-header__meta-item"><strong>{{ rows.length }}</strong> 实验总数</span>
        <span class="labs-header__meta-item"><strong>{{ publishedCount }}</strong> 已发布</span>
        <span class="labs-header__meta-item"><strong>{{ draftCount }}</strong> 待完善</span>
        <span class="labs-header__meta-item"><strong>{{ summaryRequiredCount }}</strong> 要求实验小结</span>
        <span class="labs-header__meta-item labs-header__meta-item--accent">{{ filteredRows.length }} 条命中 · {{ activeStatusLabel }} · {{ activeClassLabel }}</span>
      </div>
    </section>

    <section class="summary-grid labs-summary-grid">
      <article class="summary-card summary-card--primary">
        <div class="summary-card__icon">
          <span class="material-symbols-outlined">inventory_2</span>
        </div>
        <div>
          <div class="summary-card__label">实验总数</div>
          <div class="summary-card__value">{{ rows.length }}</div>
           <div class="summary-card__desc">当前实验台账条目。</div>
        </div>
      </article>
      <article class="summary-card summary-card--light">
        <div class="summary-card__icon summary-card__icon--teal">
          <span class="material-symbols-outlined">publish</span>
        </div>
        <div>
          <div class="summary-card__label">已发布</div>
          <div class="summary-card__value">{{ publishedCount }}</div>
           <div class="summary-card__desc">学生端当前可进入。</div>
        </div>
      </article>
        <article class="summary-card summary-card--light">
          <div class="summary-card__icon summary-card__icon--amber">
            <span class="material-symbols-outlined">edit_note</span>
          </div>
          <div>
          <div class="summary-card__label">草稿/待完善</div>
          <div class="summary-card__value">{{ draftCount }}</div>
             <div class="summary-card__desc">仍需补步骤或未发布。</div>
          </div>
        </article>
       <article class="summary-card summary-card--light">
         <div class="summary-card__icon summary-card__icon--violet">
           <span class="material-symbols-outlined">description</span>
         </div>
         <div>
           <div class="summary-card__label">要求实验小结</div>
           <div class="summary-card__value">{{ summaryRequiredCount }}</div>
             <div class="summary-card__desc">提交时需补实验小结。</div>
         </div>
       </article>
       <article class="summary-card summary-card--accent">
         <div class="summary-card__header-line">
           <div>
             <div class="summary-card__label">当前视图</div>
             <div class="summary-card__value summary-card__value--small">{{ filteredRows.length }} 条命中</div>
          </div>
          <span class="summary-chip">{{ activeStatusLabel }}</span>
        </div>
        <div class="summary-card__desc summary-card__desc--spaced">
           {{ searchKeyword ? `关键词“${searchKeyword}”` : '未使用关键词' }}，{{ activeClassLabel }}。
        </div>
      </article>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-card__main">
        <div class="toolbar-search">
          <span class="material-symbols-outlined">search</span>
          <el-input v-model="searchKeyword" placeholder="搜索实验标题、要求、内容或班级" clearable />
        </div>
        <div class="toolbar-filters">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable>
            <el-option label="全部状态" value="" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-select v-model="classFilter" placeholder="全部班级" clearable>
            <el-option label="全部班级" value="" />
            <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
        </div>
      </div>
      <div class="toolbar-card__meta">
        <span>共 {{ rows.length }} 个实验</span>
        <span class="toolbar-divider"></span>
        <span>当前筛出 {{ filteredRows.length }} 个</span>
        <el-button link type="primary" @click="resetFilters">清空筛选</el-button>
      </div>
    </section>

    <section class="labs-console">
      <header class="labs-console__header">
        <div>
          <h2>实验台账</h2>
           <p>核对题项数量与发布动作。</p>
        </div>
      </header>

      <div v-loading="loading" class="labs-table-shell">
        <template v-if="filteredRows.length">
          <article v-for="row in filteredRows" :key="row.id" class="lab-row">
            <div class="lab-row__identity">
              <div>
                <div class="lab-row__title-line">
                  <h3>{{ row.title }}</h3>
                  <span class="lab-class-chip">{{ row.className || '未绑定班级' }}</span>
                </div>
                <div class="lab-row__meta-chips">
                  <span class="lab-meta-chip" :class="row.summaryRequired ? 'lab-meta-chip--violet' : 'lab-meta-chip--muted'">
                    {{ row.summaryRequired ? '要求实验小结' : '小结非必填' }}
                  </span>
                  <span class="lab-meta-chip">{{ experimentTypeLabel(row.experimentType) }}</span>
                  <span class="lab-meta-chip" :class="row.materialId ? 'lab-meta-chip--success' : 'lab-meta-chip--muted'">
                    {{ row.materialId ? '已上传实验指导' : '未上传实验指导' }}
                  </span>
                  <span class="lab-meta-chip">{{ scoreVisibilityLabel(row.scoreVisibilityMode) }}</span>
                </div>
                <p>{{ row.description || '暂无实验要求。' }}</p>
                <p class="lab-row__subcopy">{{ row.experimentContent || '暂无实验内容。' }}</p>
              </div>
            </div>

            <div class="lab-row__type">
              <span class="meta-label">题项规模</span>
              <strong>{{ row.stepCount ?? 0 }} 个题项</strong>
              <small v-if="(row.stepCount ?? 0) === 0">请先配置题项</small>
            </div>

            <div class="lab-row__status">
              <span :class="['status-dot', `status-dot--${statusTone(row.status)}`]"></span>
              <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
              <small>{{ updatedLabel(row) }}</small>
            </div>

            <div class="lab-row__actions">
              <div class="lab-row__actions-grid">
                <el-button type="primary" plain @click="goSteps(row.id)">步骤配置</el-button>
                <el-button plain @click="goBlankRegrade(row.id)">批量重判</el-button>
                <el-button @click="openEditDialog(row)">编辑</el-button>
                <el-button class="lab-row__toggle-action" :type="row.status === 'PUBLISHED' ? 'warning' : 'success'" plain @click="toggleStatus(row)">
                  {{ row.status === 'PUBLISHED' ? '关闭实验' : '发布实验' }}
                </el-button>
              </div>
            </div>
          </article>
        </template>
        <el-empty v-else description="暂无匹配实验" />
      </div>
    </section>

     <section class="labs-guidance-strip">
       <article class="side-card side-card--visual">
         <div class="side-card__header">
           <span class="material-symbols-outlined">account_tree</span>
           <strong>发布前核对</strong>
         </div>
           <p>先核对班级、步骤和状态，再进入后续动作。</p>
       </article>
     </section>

    <el-dialog v-model="createDialogVisible" :title="editingId ? '编辑实验' : '新建实验'" width="600px">
      <el-form label-width="92px">
        <el-form-item v-if="!editingId" label="创建方式">
          <el-radio-group v-model="createMode">
            <el-radio-button value="blank">空白创建</el-radio-button>
            <el-radio-button value="copy" :disabled="!rows.length">从已有实验复制</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="createMode === 'copy' && !editingId">
          <el-form-item label="源实验" required>
            <el-select v-model="sourceLabId" style="width: 100%" placeholder="请选择源实验" @change="handleSourceLabChange">
              <el-option v-for="item in rows" :key="item.id" :label="sourceLabLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标班级" required>
            <el-select v-model="createForm.classId" style="width: 100%" placeholder="请选择目标班级">
              <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="新实验名称" required>
            <el-input v-model="createForm.title" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="发布状态">
            <el-select v-model="createForm.status" style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发布" value="PUBLISHED" :disabled="(sourceLab?.stepCount ?? 0) === 0" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="sourceLab" label="复制预览">
            <div class="lab-copy-preview">
              <span class="lab-meta-chip">{{ sourceLab.stepCount ?? 0 }} 个题项</span>
              <span class="lab-meta-chip" :class="sourceLab.materialId ? 'lab-meta-chip--success' : 'lab-meta-chip--muted'">
                {{ sourceLab.materialId ? '含实验指导' : '无实验指导' }}
              </span>
              <span class="lab-meta-chip" :class="sourceLab.summaryRequired ? 'lab-meta-chip--violet' : 'lab-meta-chip--muted'">
                {{ sourceLab.summaryRequired ? '要求实验小结' : '小结非必填' }}
              </span>
              <span class="lab-meta-chip">{{ scoreVisibilityLabel(sourceLab.scoreVisibilityMode) }}</span>
              <p v-if="(sourceLab.stepCount ?? 0) === 0" class="lab-copy-preview__warn">源实验没有题项，复制后需先配置题项</p>
            </div>
          </el-form-item>
        </template>

        <template v-else>
        <el-form-item label="实验名称" required>
          <el-input v-model="createForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="所属班级" required>
          <el-select v-model="createForm.classId" style="width: 100%" placeholder="请选择班级">
            <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验类型" required>
          <el-select v-model="createForm.experimentType" style="width: 100%">
            <el-option v-for="item in experimentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验要求">
          <el-input v-model="createForm.description" type="textarea" :rows="4" maxlength="800" show-word-limit />
        </el-form-item>
        <el-form-item label="实验内容">
          <el-input v-model="createForm.experimentContent" type="textarea" :rows="5" maxlength="1500" show-word-limit />
        </el-form-item>
        <el-form-item label="实验指导">
          <div class="lab-guidance-form">
            <div class="lab-guidance-form__meta">
              <strong>{{ createForm.materialFileName || '未上传实验指导' }}</strong>
              <span>{{ createForm.materialId ? '已绑定资料记录' : '上传后将自动绑定到当前实验' }}</span>
            </div>
            <div class="lab-guidance-form__actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept=".pdf,.doc,.docx,.ppt,.pptx,.zip,.rar,.7z,.txt,.md"
                :on-change="handleGuidanceSelected"
              >
                <el-button :loading="uploadingGuidance">{{ createForm.materialId ? '替换实验指导' : '上传实验指导' }}</el-button>
              </el-upload>
              <el-button v-if="createForm.materialId" link type="danger" @click="clearGuidance">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="createForm.status" style="width: 100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验小结">
          <el-switch
            v-model="createForm.summaryRequired"
            inline-prompt
            active-text="必填"
            inactive-text="选填"
          />
        </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listClasses } from '@/api/classes';
import { uploadMaterial } from '@/api/materials';
import { changeTeacherLabStatus, copyTeacherLab, createTeacherLab, listTeacherLabs, updateTeacherLab } from '@/api/labs';
import type { TeachingClass } from '@/types/class';
import type { UploadMaterialPayload } from '@/types/material';
import { LAB_EXPERIMENT_TYPE_LABELS, type CreateLabPayload, type LabItem, type LabStatus } from '@/types/lab';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const createDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<LabItem[]>([]);
const classOptions = ref<TeachingClass[]>([]);
const searchKeyword = ref('');
const statusFilter = ref<LabStatus | ''>('');
const classFilter = ref('');
const uploadingGuidance = ref(false);

type CreateMode = 'blank' | 'copy';

const createMode = ref<CreateMode>('blank');
const sourceLabId = ref<number | null>(null);

const experimentTypeOptions = Object.entries(LAB_EXPERIMENT_TYPE_LABELS).map(([value, label]) => ({
  value: Number(value),
  label,
}));

const createForm = reactive<Omit<CreateLabPayload, 'classId'> & { classId?: number }>({
  title: '',
  description: '',
  experimentContent: '',
  experimentType: 1,
  classId: undefined,
  status: 'DRAFT',
  materialId: null,
  summaryRequired: false,
  materialTitle: '',
  materialFileName: '',
  materialDownloadUrl: '',
});

const publishedCount = computed(() => rows.value.filter(row => row.status === 'PUBLISHED').length);
const draftCount = computed(() => rows.value.filter(row => row.status !== 'PUBLISHED').length);
const summaryRequiredCount = computed(() => rows.value.filter(row => row.summaryRequired).length);

// 复制模式：源实验从当前台账行直接取（stepCount/materialId/summaryRequired 等预览字段均在行内），不额外请求
const sourceLab = computed(() => rows.value.find(row => row.id === sourceLabId.value) ?? null);

// 与服务端 resolveCopyTitle 同一规则：base 截到 26 字 + 「（副本）」，总长 ≤ 30
const copyDefaultTitle = (sourceTitle: string) => {
  const suffix = '（副本）';
  const maxBase = 30 - suffix.length;
  const base = sourceTitle.length > maxBase ? sourceTitle.slice(0, maxBase) : sourceTitle;
  return base + suffix;
};

const sourceLabLabel = (row: LabItem) =>
  `${row.title} · ${row.className || '未绑定班级'} · ${statusLabel(row.status)} · ${row.stepCount ?? 0} 个题项`;

const handleSourceLabChange = (id: number) => {
  const source = rows.value.find(row => row.id === id);
  if (!source) {
    return;
  }
  createForm.title = copyDefaultTitle(source.title);
  // 目标班级优先沿用源班级（当前仍任教时），否则落到第一个任教班级
  const sourceClassAvailable = classOptions.value.some(item => item.id === source.classId);
  createForm.classId = sourceClassAvailable ? source.classId : classOptions.value[0]?.id;
  createForm.status = 'DRAFT';
};

const filteredRows = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();

  return rows.value.filter(row => {
    const matchesKeyword = !keyword
      || row.title.toLowerCase().includes(keyword)
      || row.description.toLowerCase().includes(keyword)
      || (row.experimentContent || '').toLowerCase().includes(keyword)
      || (row.className || '').toLowerCase().includes(keyword);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    const matchesClass = !classFilter.value || String(row.classId || '') === classFilter.value;
    return matchesKeyword && matchesStatus && matchesClass;
  });
});

const activeStatusLabel = computed(() => statusFilter.value ? statusLabel(statusFilter.value) : '全部状态');
const activeClassLabel = computed(() => {
  if (!classFilter.value) {
    return '全部班级';
  }

  return classOptions.value.find(item => String(item.id) === classFilter.value)?.name || '全部班级';
});

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listTeacherLabs();
  } finally {
    loading.value = false;
  }
};

const resetCreateForm = () => {
  createForm.title = '';
  createForm.description = '';
  createForm.experimentContent = '';
  createForm.experimentType = 1;
  createForm.classId = classOptions.value[0]?.id;
  createForm.status = 'DRAFT';
  createForm.materialId = null;
  createForm.materialTitle = '';
  createForm.materialFileName = '';
  createForm.materialDownloadUrl = '';
  createForm.summaryRequired = false;
  editingId.value = null;
};

const resetFilters = () => {
  searchKeyword.value = '';
  statusFilter.value = '';
  classFilter.value = '';
};

const fetchClasses = async () => {
  const result = await listClasses();
  classOptions.value = result.items;
};

const openCreateDialog = () => {
  resetCreateForm();
  createMode.value = 'blank';
  sourceLabId.value = null;
  createDialogVisible.value = true;
};

const openEditDialog = (row: LabItem) => {
  editingId.value = row.id;
  // 编辑态强制空白模式：不显示创建方式切换，复制语义不适用于已存在实验
  createMode.value = 'blank';
  sourceLabId.value = null;
  createForm.title = row.title;
  createForm.description = row.description;
  createForm.experimentContent = row.experimentContent || '';
  createForm.experimentType = row.experimentType || 1;
  createForm.classId = row.classId;
  createForm.status = row.status;
  createForm.materialId = row.materialId || null;
  createForm.materialTitle = row.materialTitle || '';
  createForm.materialFileName = row.materialFileName || '';
  createForm.materialDownloadUrl = row.materialDownloadUrl || '';
  createForm.summaryRequired = Boolean(row.summaryRequired);
  createDialogVisible.value = true;
};

const experimentTypeLabel = (value?: number | null) => LAB_EXPERIMENT_TYPE_LABELS[value || 1] || `类型 ${value}`;

const handleGuidanceSelected = async (uploadFile: { raw?: File }) => {
  const rawFile = uploadFile.raw;
  if (!rawFile) {
    return;
  }

  uploadingGuidance.value = true;
  try {
    const payload: UploadMaterialPayload = {
      title: `${createForm.title.trim() || '实验'}指导文件`,
      category: 'LAB_GUIDE',
      description: createForm.description.trim() || '实验指导文件',
      visibility: 'ALL',
      file: rawFile,
    };
    const material = await uploadMaterial(payload);
    createForm.materialId = material.id;
    createForm.materialTitle = material.title;
    createForm.materialFileName = material.fileName;
    createForm.materialDownloadUrl = material.downloadUrl;
    ElMessage.success('实验指导上传成功');
  } finally {
    uploadingGuidance.value = false;
  }
};

const clearGuidance = () => {
  createForm.materialId = null;
  createForm.materialTitle = '';
  createForm.materialFileName = '';
  createForm.materialDownloadUrl = '';
};

const submitCreate = async () => {
  // 复制模式：只传 目标班级/新名称/发布状态，其余字段由服务端从源实验复制
  if (createMode.value === 'copy' && !editingId.value) {
    if (!sourceLabId.value) {
      ElMessage.warning('请选择源实验');
      return;
    }
    if (!createForm.classId) {
      ElMessage.warning('请选择目标班级');
      return;
    }
    if (!createForm.title.trim()) {
      ElMessage.warning('请输入新实验名称');
      return;
    }
    if (createForm.status === 'PUBLISHED' && (sourceLab.value?.stepCount ?? 0) === 0) {
      ElMessage.warning('源实验没有题项，不能直接发布');
      return;
    }

    submitting.value = true;
    try {
      const copied = await copyTeacherLab(sourceLabId.value, {
        classId: createForm.classId,
        title: createForm.title.trim(),
        status: createForm.status,
      });
      ElMessage.success(`实验复制成功，共 ${copied.stepCount ?? 0} 个题项`);
      createDialogVisible.value = false;
      await fetchData();
    } finally {
      submitting.value = false;
    }
    return;
  }

  if (!createForm.title.trim()) {
    ElMessage.warning('请输入实验名称');
    return;
  }
  if (!createForm.classId) {
    ElMessage.warning('请选择所属班级');
    return;
  }

  submitting.value = true;
  try {
    const payload: CreateLabPayload = {
      title: createForm.title.trim(),
      description: createForm.description.trim(),
      experimentContent: createForm.experimentContent.trim(),
      experimentType: createForm.experimentType || 1,
      classId: createForm.classId,
      status: createForm.status,
      materialId: createForm.materialId,
      summaryRequired: createForm.summaryRequired,
    };

    if (editingId.value) {
      await updateTeacherLab(editingId.value, payload);
      ElMessage.success('实验更新成功');
    } else {
      await createTeacherLab(payload);
      ElMessage.success('实验创建成功');
    }
    createDialogVisible.value = false;
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const goSteps = (labId: number) => {
  router.push(`/teacher/labs/${labId}/steps`);
};

const goReports = (row?: LabItem) => {
  router.push({
    path: '/teacher/lab-reports',
    query: row
      ? {
          labId: String(row.id),
          keyword: row.title,
        }
      : undefined,
  });
};

const goBlankRegrade = (labId: number) => {
  router.push(`/teacher/labs/${labId}/blank-regrade/batch`);
};

const toggleStatus = async (row: LabItem) => {
  const nextStatus: LabStatus = row.status === 'PUBLISHED' ? 'CLOSED' : 'PUBLISHED';
  if (nextStatus === 'PUBLISHED' && (row.stepCount ?? 0) === 0) {
    ElMessage.warning('请先配置至少 1 个题项');
    return;
  }
  await changeTeacherLabStatus(row.id, { status: nextStatus });
  ElMessage.success(nextStatus === 'PUBLISHED' ? '实验已发布' : '实验已关闭');
  await fetchData();
};

const statusLabel = (status: LabStatus) => {
  if (status === 'DRAFT') return '草稿';
  if (status === 'PUBLISHED') return '已发布';
  if (status === 'CLOSED') return '已关闭';
  return status || '未知';
};

const statusTagType = (status: LabStatus) => {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'CLOSED') return 'info';
  return 'warning';
};

const statusTone = (status: LabStatus) => {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'CLOSED') return 'muted';
  return 'warning';
};

const scoreVisibilityLabel = (mode?: string | null) => {
  if (mode === 'IMMEDIATE') return '即时出分';
  if (mode === 'AFTER_TEACHER_CONFIRM') return '教师确认后可见';
  if (mode === 'AFTER_DEADLINE') return '截止后可见';
  if (mode === 'MANUAL_RELEASE') return '教师手动发布';
  return '成绩策略未标注';
};

const updatedLabel = (row: LabItem) => {
  const source = row.updatedAt || row.createdAt;
  return source ? `更新于 ${source.replace('T', ' ').slice(0, 16)}` : '暂无更新时间';
};

onMounted(async () => {
  await fetchClasses();
  resetCreateForm();
  await fetchData();
});
</script>

<style scoped>
.lab-row__subcopy {
  color: #94a3b8;
}

.lab-meta-chip--success {
  background: #ecfdf5;
  color: #047857;
}

.lab-guidance-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px dashed var(--labs-border);
  border-radius: 12px;
  background: #f8fafc;
}

.lab-copy-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px;
  border: 1px dashed var(--labs-border);
  border-radius: 12px;
  background: #f8fafc;
}

.lab-copy-preview__warn {
  flex-basis: 100%;
  margin: 2px 0 0;
  color: #d97706;
  font-size: 12px;
}

.lab-guidance-form__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.lab-guidance-form__meta strong {
  color: var(--labs-text);
  font-size: 13px;
}

.lab-guidance-form__meta span {
  color: var(--labs-muted);
  font-size: 12px;
}

.lab-guidance-form__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 1080px) {
  .lab-guidance-form {
    flex-direction: column;
    align-items: flex-start;
  }
}
.labs-page {
  --labs-primary: #0f766e;
  --labs-primary-soft: #ecfeff;
  --labs-accent: #0369a1;
  --labs-surface: #ffffff;
  --labs-border: #dbe5ef;
  --labs-text: #0f172a;
  --labs-muted: #64748b;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.labs-header,
.toolbar-card,
.labs-console,
.side-card,
.summary-card {
  border: 1px solid var(--labs-border);
  border-radius: 14px;
  background: var(--labs-surface);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.labs-header {
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.72), rgba(255, 255, 255, 0.98));
}

.labs-header__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.labs-header__eyebrow {
  margin-bottom: 2px;
  color: var(--labs-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.labs-header h1 {
  margin: 0;
  color: var(--labs-text);
  font-family: 'DM Sans', sans-serif;
  font-size: 20px;
}

.labs-header p {
  margin: 2px 0 0;
  max-width: 520px;
  color: var(--labs-muted);
  font-size: 12px;
  line-height: 1.35;
}

.labs-header__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.labs-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.labs-header__meta-item,
.summary-chip,
.labs-console__hint {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 10px;
  font-weight: 700;
}

.labs-header__meta-item strong {
  margin-right: 4px;
  color: var(--labs-text);
}

.labs-header__meta-item--accent {
  background: var(--labs-primary-soft);
  color: var(--labs-primary);
}

.labs-console__header h2 {
  font-family: 'DM Sans', sans-serif;
}

.labs-console__header p,
.side-card p {
  margin: 2px 0 0;
  color: var(--labs-muted);
  font-size: 11px;
  line-height: 1.35;
}

.labs-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.summary-card {
  padding: 10px 12px;
}

.summary-card__icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
}

.summary-card--primary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  border: none;
}

.summary-card--primary .summary-card__label,
.summary-card--primary .summary-card__desc {
  color: rgba(255, 255, 255, 0.82);
}

.summary-card--light .summary-card__icon,
.summary-card--accent .summary-chip {
  background: var(--labs-primary-soft);
  color: var(--labs-primary);
}

.summary-card__icon--amber {
  background: #fff7ed !important;
  color: #d97706 !important;
}

.summary-card__icon--teal {
  background: var(--labs-primary-soft) !important;
  color: var(--labs-primary) !important;
}

.summary-card__icon--violet {
  background: #f5f3ff !important;
  color: #7c3aed !important;
}

.summary-card__label {
  margin-top: 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.summary-card__value {
  margin-top: 2px;
  font-size: 18px;
  font-weight: 700;
  color: inherit;
}

.summary-card__value--small {
  font-size: 15px;
}

.summary-card__desc {
  margin-top: 2px;
  color: #64748b;
  font-size: 10px;
  line-height: 1.3;
}

.summary-card__desc--spaced {
  margin-top: 6px;
}

.summary-card__header-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.toolbar-card {
  padding: 10px 12px;
}

.toolbar-card__main {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  align-items: center;
}

.toolbar-search {
  position: relative;
  flex: 1;
}

.toolbar-search .material-symbols-outlined {
  position: absolute;
  top: 50%;
  left: 12px;
  transform: translateY(-50%);
  z-index: 1;
  color: #94a3b8;
}

.toolbar-search :deep(.el-input__wrapper) {
  padding-left: 32px;
  min-height: 34px;
}

.toolbar-filters {
  display: flex;
  gap: 8px;
}

.toolbar-filters .el-select {
  width: 150px;
}

.toolbar-card__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
}

.toolbar-divider {
  width: 1px;
  height: 14px;
  background: var(--labs-border);
}

.labs-console {
  padding: 10px 12px 12px;
}

.labs-console__header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 8px;
}

.labs-console__header h2 {
  margin: 0;
  color: var(--labs-text);
  font-size: 16px;
}

.labs-console__hint {
  text-align: right;
}

.labs-table-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lab-row {
  display: grid;
  grid-template-columns: minmax(0, 2.4fr) 0.9fr 1.15fr 248px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--labs-border);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
}

.lab-row__identity {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.lab-row__title-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.lab-row__meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}

.lab-meta-chip {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 700;
}

.lab-meta-chip--violet {
  background: #f5f3ff;
  color: #7c3aed;
}

.lab-meta-chip--muted {
  background: #f8fafc;
  color: #64748b;
}

.lab-row h3 {
  margin: 0;
  color: var(--labs-text);
  font-size: 18px;
}

.lab-row p {
  margin: 3px 0 0;
  color: var(--labs-muted);
  font-size: 13px;
  line-height: 1.35;
}

.lab-class-chip {
  padding: 2px 7px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.lab-row__type,
.lab-row__status {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.meta-label {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.lab-row__type strong {
  color: var(--labs-text);
  font-size: 16px;
}

.lab-row__type small,
.lab-row__status small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
}

.status-dot--success {
  background: #22c55e;
}

.status-dot--warning {
  background: #f59e0b;
}

.status-dot--muted {
  background: #94a3b8;
}

.lab-row__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding-right: 8px;
}

.lab-row__actions-grid {
  display: grid;
  width: 228px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 16px;
}

.lab-row__actions-grid :deep(.el-button) {
  width: 100%;
  height: 36px;
  min-width: 0;
  margin-left: 0;
}

.lab-row__toggle-action {
  min-width: 0 !important;
}

.labs-guidance-strip {
  display: grid;
  grid-template-columns: 1fr;
}

.side-card {
  padding: 8px 10px;
}

.side-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--labs-text);
}

.side-card__header .material-symbols-outlined {
  color: var(--labs-primary);
}

@media (max-width: 1440px) {
  .labs-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-row {
    grid-template-columns: minmax(0, 1.8fr) 0.9fr 1fr 228px;
  }

  .lab-row__status,
  .lab-row__actions {
    grid-column: span 1;
  }
}

@media (max-width: 1080px) {
  .labs-summary-grid,
  .lab-row {
    grid-template-columns: 1fr;
  }

  .labs-header__top,
  .toolbar-card__main,
  .labs-console__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-filters {
    width: 100%;
    flex-wrap: wrap;
  }

  .toolbar-filters .el-select {
    width: 100%;
  }

  .lab-row__actions,
  .lab-row__actions-grid {
    align-items: flex-start;
    justify-content: flex-start;
  }

  .lab-row__actions-grid {
    width: 228px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
