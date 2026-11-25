<template>
  <div class="page admin-students workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>学生管理</h1>
          <p>按班级维护学生名册，支持新建、调班、导入与启停。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button :disabled="!selectedClassId" @click="openImportDialog">导入学生</el-button>
          <el-button type="primary" @click="openCreateDialog">新增学生</el-button>
        </div>
      </div>

      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ filteredRows.length }}</strong> 可见学生</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 启用</span>
        <span class="workbench-meta__item"><strong>{{ disabledCount }}</strong> 停用</span>
      </div>
    </section>

    <AppToolbarCard>
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="按账号、姓名或班级搜索" clearable class="app-search-field" />
        <el-select v-model="selectedClassId" clearable placeholder="全部班级" class="app-filter-field--md" @change="refresh">
          <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="全部状态" class="app-filter-field">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </div>
    </AppToolbarCard>

    <AppTableCard>
      <el-table :data="filteredRows" v-loading="loading" empty-text="暂无学生数据">
        <el-table-column label="学生" min-width="240">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.displayName }}</strong>
              <span>账号 {{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="180">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.className || '未分配' }}</strong>
              <span>{{ row.classCode || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openResetDialog(row)">重置密码</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppTableCard>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学生' : '新增学生'" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="!editingId" label="账号" for="admin-student-username">
          <el-input id="admin-student-username" v-model="form.username" placeholder="例如：20260011" />
        </el-form-item>
        <el-form-item label="学生姓名" for="admin-student-display-name">
          <el-input id="admin-student-display-name" v-model="form.displayName" placeholder="请输入学生姓名" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" for="admin-student-password">
          <el-input id="admin-student-password" v-model="form.password" show-password placeholder="默认可用 123456" />
        </el-form-item>
        <el-form-item label="所属班级" for="admin-student-class-id">
          <el-select id="admin-student-class-id" v-model="form.classId" placeholder="请选择班级" class="app-control-full">
            <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit(submitHandlers)">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialogVisible" title="重置密码" width="480px" destroy-on-close>
      <el-alert
        v-if="resetTarget"
        type="error"
        :closable="false"
        show-icon
        title="即将修改以下学生的密码，请再次确认"
      >
        <div class="reset-confirm-name">
          <strong>{{ resetTarget.displayName }}</strong>
          <span class="reset-confirm-username">账号 {{ resetTarget.username }}</span>
        </div>
      </el-alert>
      <el-form label-position="top" class="app-mt-3">
        <el-form-item label="新密码" for="admin-student-reset-password" required>
          <el-input
            id="admin-student-reset-password"
            v-model="resetPasswordForm.password"
            show-password
            maxlength="64"
            placeholder="8~64 位，需包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认新密码" for="admin-student-reset-password-confirm" required>
          <el-input
            id="admin-student-reset-password-confirm"
            v-model="resetPasswordForm.confirmPassword"
            show-password
            maxlength="64"
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="批量导入学生" width="640px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请先选择班级后再导入。支持 Excel（.xlsx）或文本：学号,姓名,密码（每行一条）；Excel 表头为学号/姓名/密码，密码可空（默认学号）。"
      />

      <section class="import-section">
        <div class="import-section__title">Excel 文件导入</div>
        <div
          :class="['import-file-panel', { 'is-dragover': isImportDragOver }]"
          @dragenter.prevent="handleImportDragEnter"
          @dragover.prevent="handleImportDragOver"
          @dragleave.prevent="handleImportDragLeave"
          @drop.prevent="handleImportDrop"
        >
          <div class="import-file-panel__meta">
            <strong>{{ importFileName || '尚未选择 Excel 文件' }}</strong>
            <span>
              {{ parsedImportRows.length ? `已解析 ${parsedImportRows.length} 条有效记录` : '支持 .xlsx，仅读取首个工作表' }}
            </span>
          </div>
          <div class="import-file-panel__actions">
            <el-button @click="downloadStudentImportTemplate">下载模板</el-button>
            <el-upload accept=".xlsx" :auto-upload="false" :show-file-list="false" :on-change="handleImportFileChange">
              <el-button type="primary" plain>选择 Excel 文件</el-button>
            </el-upload>
            <el-button v-if="selectedImportFile" @click="clearImportFile">清空文件</el-button>
          </div>
        </div>
        <div class="import-file-panel__tips">
          <span>可直接将 Excel 文件拖拽到上方区域</span>
          <span>表头建议：学号、姓名、密码</span>
          <span>密码列可空，系统会自动回退为学号</span>
        </div>
      </section>

      <el-form label-position="top" class="import-text-form">
        <el-form-item label="文本补充导入" for="admin-student-import-text">
          <el-input id="admin-student-import-text" v-model="importText" type="textarea" :rows="6" placeholder="20260011,张三,123456" />
        </el-form-item>
      </el-form>

      <section v-if="importResult" class="import-result">
        <strong>成功 {{ importResult.successCount }} 条，失败 {{ importResult.failureCount }} 条</strong>
        <el-table :data="importResult.failures" size="small" empty-text="无失败记录" class="app-mt-2">
          <el-table-column prop="row" label="行号" width="100" />
          <el-table-column prop="reason" label="失败原因" />
        </el-table>
      </section>

      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UploadFile } from 'element-plus';
import {
  changeAdminStudentStatus,
  createAdminStudent,
  importAdminStudents,
  listAdminClasses,
  listAdminStudents,
  resetAdminStudentPassword,
  updateAdminStudent,
} from '@/api/admin';
import type { AdminClass, AdminStudent, ImportStudentsResult } from '@/types/admin';
import type { ImportStudentRow, StudentStatus } from '@/types/student';
import AppTableCard from '@/components/ui/AppTableCard.vue';
import AppToolbarCard from '@/components/ui/AppToolbarCard.vue';
import { useCrudTable, type CrudSubmitHandlers } from '@/composables/useCrudTable';
import {
  downloadStudentImportTemplate,
  parseStudentExcelFile,
  parseStudentImportText,
} from '@/utils/studentImport';

const importing = ref(false);
const importDialogVisible = ref(false);
const selectedClassId = ref<number | undefined>();
const classes = ref<AdminClass[]>([]);
const searchQuery = ref('');
const statusFilter = ref<StudentStatus | ''>('');
const importText = ref('');
const importResult = ref<ImportStudentsResult | null>(null);
const selectedImportFile = ref<File | null>(null);
const importFileName = ref('');
const parsedImportRows = ref<ImportStudentRow[]>([]);
const isImportDragOver = ref(false);

const form = reactive({
  username: '',
  displayName: '',
  password: '123456',
  classId: undefined as number | undefined,
});

const resetDialogVisible = ref(false);
const resetSubmitting = ref(false);
const resetTarget = ref<AdminStudent | null>(null);
const resetPasswordForm = reactive({
  password: '',
  confirmPassword: '',
});

const {
  loading,
  submitting,
  dialogVisible,
  editingId,
  rows,
  filteredRows,
  resetForm,
  refresh,
  openCreateDialog,
  openEditDialog,
  submit,
  toggleStatus: runToggleStatus,
} = useCrudTable<AdminStudent, StudentStatus>({
  fetchRows: () => listAdminStudents(selectedClassId.value),
  keyword: searchQuery,
  statusFilter,
  keywordFieldsOf: row => [row.username, row.displayName, row.className, row.classCode],
  refine: list => [...list].sort((a, b) => a.username.localeCompare(b.username)),
  resetForm: () => {
    form.username = '';
    form.displayName = '';
    form.password = '123456';
    form.classId = selectedClassId.value ?? classes.value[0]?.id;
    editingId.value = null;
  },
  fillEditForm: row => {
    form.displayName = row.displayName;
    form.classId = row.classId ?? selectedClassId.value ?? classes.value[0]?.id;
  },
});

const activeCount = computed(() => filteredRows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => filteredRows.value.filter(item => item.status === 'DISABLED').length);

const fetchClasses = async () => {
  classes.value = await listAdminClasses();
  if (!selectedClassId.value && classes.value.length === 1) {
    selectedClassId.value = classes.value[0].id;
  }
};

const clearImportFile = () => {
  selectedImportFile.value = null;
  importFileName.value = '';
  parsedImportRows.value = [];
  isImportDragOver.value = false;
};

const applyImportFile = async (file: File) => {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xlsx 格式的 Excel 文件');
    clearImportFile();
    return;
  }

  try {
    const parsedRows = await parseStudentExcelFile(file);
    if (!parsedRows.length) {
      ElMessage.warning('Excel 中没有可导入的有效学生记录');
      clearImportFile();
      return;
    }
    selectedImportFile.value = file;
    importFileName.value = file.name;
    parsedImportRows.value = parsedRows;
    importResult.value = null;
    ElMessage.success(`Excel 解析成功，共 ${parsedRows.length} 条有效记录`);
  } catch (error) {
    clearImportFile();
    ElMessage.error(error instanceof Error ? error.message : 'Excel 解析失败，请检查文件内容');
  } finally {
    isImportDragOver.value = false;
  }
};

const handleImportFileChange = async (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw;
  if (!rawFile) {
    return;
  }
  await applyImportFile(rawFile);
};

const handleImportDragEnter = () => {
  isImportDragOver.value = true;
};

const handleImportDragOver = () => {
  isImportDragOver.value = true;
};

const handleImportDragLeave = (event: DragEvent) => {
  const currentTarget = event.currentTarget;
  const relatedTarget = event.relatedTarget;
  if (currentTarget instanceof HTMLElement && relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) {
    return;
  }
  isImportDragOver.value = false;
};

const handleImportDrop = async (event: DragEvent) => {
  const file = event.dataTransfer?.files?.[0];
  if (!file) {
    isImportDragOver.value = false;
    return;
  }
  await applyImportFile(file);
};

const submitHandlers: CrudSubmitHandlers = {
  validate: () => {
    if (!form.displayName.trim() || !form.classId) {
      ElMessage.warning('请填写姓名并选择班级');
      return false;
    }
    if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
      ElMessage.warning('请填写账号和初始密码');
      return false;
    }
    return true;
  },
  create: () => createAdminStudent({
    username: form.username.trim(),
    displayName: form.displayName.trim(),
    password: form.password.trim(),
    classId: form.classId!,
  }),
  update: id => updateAdminStudent(id, {
    displayName: form.displayName.trim(),
    classId: form.classId!,
  }),
  createdMessage: '学生创建成功',
  updatedMessage: '学生更新成功',
};

const toggleStatus = (row: AdminStudent) => {
  const nextStatus: StudentStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  return runToggleStatus(row, () => changeAdminStudentStatus(row.id, nextStatus), '学生状态更新成功');
};

const openResetDialog = (row: AdminStudent) => {
  resetTarget.value = row;
  resetPasswordForm.password = '';
  resetPasswordForm.confirmPassword = '';
  resetDialogVisible.value = true;
};

const submitReset = async () => {
  if (!resetTarget.value) {
    return;
  }
  const password = resetPasswordForm.password.trim();
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(password)) {
    ElMessage.warning('新密码需 8-64 位，且同时包含字母和数字');
    return;
  }
  if (password !== resetPasswordForm.confirmPassword.trim()) {
    ElMessage.warning('两次输入的密码不一致');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认将学生「${resetTarget.value.username} ${resetTarget.value.displayName}」的密码重置为新密码？`,
      '重置密码',
      { type: 'warning' }
    );
  } catch {
    return;
  }

  resetSubmitting.value = true;
  try {
    await resetAdminStudentPassword(resetTarget.value.id, { password });
    ElMessage.success(`已把 ${resetTarget.value.username} ${resetTarget.value.displayName} 的密码重置`);
    resetDialogVisible.value = false;
  } finally {
    resetSubmitting.value = false;
  }
};

const openImportDialog = () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }
  importText.value = '';
  importResult.value = null;
  clearImportFile();
  importDialogVisible.value = true;
};

const submitImport = async () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }

  const parsedRows =
    parsedImportRows.value.length > 0 ? parsedImportRows.value : parseStudentImportText(importText.value);

  if (!parsedRows.length) {
    ElMessage.warning('请先选择有效的 Excel 文件，或输入至少一条有效文本记录');
    return;
  }

  importing.value = true;
  try {
    importResult.value = await importAdminStudents({ classId: selectedClassId.value, rows: parsedRows });
    ElMessage.success('导入已完成');
    await refresh();
  } finally {
    importing.value = false;
  }
};

onMounted(async () => {
  await fetchClasses();
  resetForm();
  await refresh();
});
</script>

<style scoped>
.reset-confirm-name {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
}

.reset-confirm-name strong {
  font-size: var(--font-size-lg);
  color: var(--el-text-color-primary);
}

.reset-confirm-username {
  font-size: var(--font-size-base);
  color: var(--el-text-color-secondary);
}

.import-section {
  margin-top: 16px;
}

.import-section__title {
  margin-bottom: 8px;
  color: #0f172a;
  font-size: var(--font-size-base);
  font-weight: 700;
}

.import-text-form {
  margin-top: 16px;
}

.import-file-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.import-file-panel.is-dragover {
  border-color: var(--primary-color, #14b8a6);
  background: rgba(20, 184, 166, 0.08);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.import-file-panel__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.import-file-panel__meta strong {
  color: #0f172a;
  font-size: var(--font-size-base);
  font-weight: 700;
  word-break: break-all;
}

.import-file-panel__meta span,
.import-file-panel__tips {
  color: #64748b;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-caption);
}

.import-file-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.import-file-panel__tips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 8px;
}

.import-result {
  margin-top: 16px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

@media (max-width: 640px) {
  .import-file-panel {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
