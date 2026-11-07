import { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE } from '@/types/enums';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listClasses } from '@/api/classes';
import { uploadMaterial } from '@/api/materials';
import { changeTeacherLabStatus, copyTeacherLab, createTeacherLab, listTeacherLabs, updateTeacherLab } from '@/api/labs';
import type { TeachingClass } from '@/types/class';
import type { UploadMaterialPayload } from '@/types/material';
import { LAB_EXPERIMENT_TYPE_LABELS, type CreateLabPayload, type LabItem, type LabStatus } from '@/types/lab';
import type { InjectionKey } from 'vue';

export function useLabs() {
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
  status: ACTIVITY_STATUS.DRAFT,
  materialId: null,
  summaryRequired: false,
  materialTitle: '',
  materialFileName: '',
  materialDownloadUrl: '',
  startAt: undefined,
  dueAt: undefined,
  scoreVisibilityMode: SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM,
});

const publishedCount = computed(() => rows.value.filter(row => row.status === ACTIVITY_STATUS.PUBLISHED).length);
const draftCount = computed(() => rows.value.filter(row => row.status !== ACTIVITY_STATUS.PUBLISHED).length);
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
  createForm.status = ACTIVITY_STATUS.DRAFT;
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
  createForm.status = ACTIVITY_STATUS.DRAFT;
  createForm.materialId = null;
  createForm.materialTitle = '';
  createForm.materialFileName = '';
  createForm.materialDownloadUrl = '';
  createForm.summaryRequired = false;
  createForm.startAt = undefined;
  createForm.dueAt = undefined;
  createForm.scoreVisibilityMode = SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM;
  editingId.value = null;
};

const resetFilters = () => {
  searchKeyword.value = '';
  statusFilter.value = '';
  classFilter.value = '';
};

const fetchClasses = async () => {
  const result = await listClasses();
  classOptions.value = result;
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
  createForm.startAt = row.startAt || undefined;
  createForm.dueAt = row.dueAt || undefined;
  createForm.scoreVisibilityMode = row.scoreVisibilityMode || SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM;
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
    if (createForm.status === ACTIVITY_STATUS.PUBLISHED && (sourceLab.value?.stepCount ?? 0) === 0) {
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
  if (createForm.startAt && createForm.dueAt && new Date(createForm.startAt) >= new Date(createForm.dueAt)) {
    ElMessage.warning('开始时间必须早于结束时间');
    return;
  }
  if (createForm.scoreVisibilityMode === SCORE_VISIBILITY_MODE.AFTER_DEADLINE && !createForm.dueAt) {
    ElMessage.warning('请先设置实验结束时间');
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
      startAt: createForm.startAt,
      dueAt: createForm.dueAt,
      scoreVisibilityMode: createForm.scoreVisibilityMode,
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
  const nextStatus: LabStatus = row.status === ACTIVITY_STATUS.PUBLISHED ? ACTIVITY_STATUS.CLOSED : ACTIVITY_STATUS.PUBLISHED;
  if (nextStatus === ACTIVITY_STATUS.PUBLISHED && (row.stepCount ?? 0) === 0) {
    ElMessage.warning('请先配置至少 1 个题项');
    return;
  }
  await changeTeacherLabStatus(row.id, { status: nextStatus });
  ElMessage.success(nextStatus === ACTIVITY_STATUS.PUBLISHED ? '实验已发布' : '实验已关闭');
  await fetchData();
};

const statusLabel = (status: LabStatus) => {
  if (status === ACTIVITY_STATUS.DRAFT) return '草稿';
  if (status === ACTIVITY_STATUS.PUBLISHED) return '已发布';
  if (status === ACTIVITY_STATUS.CLOSED) return '已关闭';
  return status || '未知';
};

const statusTagType = (status: LabStatus) => {
  if (status === ACTIVITY_STATUS.PUBLISHED) return 'success';
  if (status === ACTIVITY_STATUS.CLOSED) return 'info';
  return 'warning';
};

const statusTone = (status: LabStatus) => {
  if (status === ACTIVITY_STATUS.PUBLISHED) return 'success';
  if (status === ACTIVITY_STATUS.CLOSED) return 'muted';
  return 'warning';
};

const scoreVisibilityLabel = (mode?: string | null) => {
  if (mode === SCORE_VISIBILITY_MODE.IMMEDIATE) return '即时出分';
  if (mode === SCORE_VISIBILITY_MODE.AFTER_TEACHER_CONFIRM) return '教师确认后可见';
  if (mode === SCORE_VISIBILITY_MODE.AFTER_DEADLINE) return '截止后可见';
  if (mode === SCORE_VISIBILITY_MODE.MANUAL_RELEASE) return '教师手动发布';
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

  return { ACTIVITY_STATUS, SCORE_VISIBILITY_MODE, computed, onMounted, reactive, ref, useRouter, ElMessage, listClasses, uploadMaterial, changeTeacherLabStatus, copyTeacherLab, createTeacherLab, listTeacherLabs, updateTeacherLab, LAB_EXPERIMENT_TYPE_LABELS, router, loading, submitting, createDialogVisible, editingId, rows, classOptions, searchKeyword, statusFilter, classFilter, uploadingGuidance, createMode, sourceLabId, experimentTypeOptions, createForm, publishedCount, draftCount, summaryRequiredCount, sourceLab, copyDefaultTitle, sourceLabLabel, handleSourceLabChange, filteredRows, activeStatusLabel, activeClassLabel, fetchData, resetCreateForm, resetFilters, fetchClasses, openCreateDialog, openEditDialog, experimentTypeLabel, handleGuidanceSelected, clearGuidance, submitCreate, goSteps, goReports, goBlankRegrade, toggleStatus, statusLabel, statusTagType, statusTone, scoreVisibilityLabel, updatedLabel };
}

export type LabsState = ReturnType<typeof useLabs>;
export const LabsKey: InjectionKey<LabsState> = Symbol('LabsState');
