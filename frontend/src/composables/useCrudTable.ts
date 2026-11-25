import { computed, ref, type ComputedRef, type Ref } from 'vue';
import { ElMessage } from 'element-plus';

export interface CrudTableRow {
  id: number;
  status: string;
}

export interface CrudTableOptions<T extends CrudTableRow, S extends string> {
  /** 拉取主列表；并联请求（如班级页的教师/学科）在调用方闭包内完成 */
  fetchRows: () => Promise<T[]>;
  /** 关键词输入框绑定的 ref，页面保留原字段名 */
  keyword: Ref<string>;
  /** 状态筛选 ref，'' 表示全部 */
  statusFilter: Ref<S | ''>;
  /** 关键词命中字段，逐页写死 */
  keywordFieldsOf: (row: T) => ReadonlyArray<string | null | undefined>;
  /** 过滤后的附加处理（学生页按账号排序） */
  refine?: (rows: T[]) => T[];
  /** 页面表单重置 */
  resetForm: () => void;
  /** 打开编辑弹窗前的表单回填 */
  fillEditForm?: (row: T) => void;
  /** 打开新建弹窗前的页面准备（班级页同步学科下拉） */
  prepareCreate?: () => void;
}

export interface CrudSubmitHandlers {
  /** 返回 false 表示页面已提示并中止 */
  validate: () => boolean;
  /** 返回值被忽略，允许直接返回 API 的实体结果 */
  create: () => Promise<unknown>;
  update: (id: number) => Promise<unknown>;
  createdMessage: string;
  updatedMessage: string;
  /** 提交异常是否由页面吞掉；默认 false */
  swallowError?: boolean;
}

export interface CrudTableState<T extends CrudTableRow> {
  loading: Ref<boolean>;
  submitting: Ref<boolean>;
  dialogVisible: Ref<boolean>;
  editingId: Ref<number | null>;
  rows: Ref<T[]>;
  filteredRows: ComputedRef<T[]>;
  /** 即 options.resetForm，页面在 onMounted 中直接调用 */
  resetForm: () => void;
  refresh: () => Promise<void>;
  openCreateDialog: () => void;
  openEditDialog: (row: T) => void;
  submit: (handlers: CrudSubmitHandlers) => Promise<void>;
  toggleStatus: (row: T, run: (row: T) => Promise<unknown>, message: string) => Promise<void>;
}

export function useCrudTable<T extends CrudTableRow, S extends string>(
  options: CrudTableOptions<T, S>,
): CrudTableState<T> {
  const loading = ref(false);
  const submitting = ref(false);
  const dialogVisible = ref(false);
  const editingId = ref<number | null>(null);
  const rows = ref([]) as Ref<T[]>;

  const filteredRows = computed<T[]>(() => {
    const keyword = options.keyword.value.trim().toLowerCase();
    const status = options.statusFilter.value;
    const filtered = rows.value.filter(row => {
      const matchesKeyword =
        !keyword ||
        options.keywordFieldsOf(row).some(field => (field ?? '').toLowerCase().includes(keyword));
      const matchesStatus = !status || row.status === status;
      return matchesKeyword && matchesStatus;
    });
    return options.refine ? options.refine(filtered) : filtered;
  });

  const refresh = async () => {
    loading.value = true;
    try {
      rows.value = await options.fetchRows();
    } finally {
      loading.value = false;
    }
  };

  const openCreateDialog = () => {
    options.resetForm();
    options.prepareCreate?.();
    dialogVisible.value = true;
  };

  const openEditDialog = (row: T) => {
    editingId.value = row.id;
    options.fillEditForm?.(row);
    dialogVisible.value = true;
  };

  const submit = async (handlers: CrudSubmitHandlers) => {
    if (!handlers.validate()) {
      return;
    }

    submitting.value = true;
    try {
      if (editingId.value) {
        await handlers.update(editingId.value);
        ElMessage.success(handlers.updatedMessage);
      } else {
        await handlers.create();
        ElMessage.success(handlers.createdMessage);
      }
      dialogVisible.value = false;
      options.resetForm();
      await refresh();
    } catch (error) {
      if (!handlers.swallowError) {
        throw error;
      }
    } finally {
      submitting.value = false;
    }
  };

  const toggleStatus = async (row: T, run: (row: T) => Promise<unknown>, message: string) => {
    await run(row);
    ElMessage.success(message);
    await refresh();
  };

  return {
    loading,
    submitting,
    dialogVisible,
    editingId,
    rows,
    filteredRows,
    resetForm: options.resetForm,
    refresh,
    openCreateDialog,
    openEditDialog,
    submit,
    toggleStatus,
  };
}
