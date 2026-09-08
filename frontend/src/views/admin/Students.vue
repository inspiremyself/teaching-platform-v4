<template>
  <div class="page admin-students workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 学生管理</div>
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

    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="按账号、姓名或班级搜索" clearable style="max-width: 280px" />
        <el-select v-model="selectedClassId" clearable placeholder="全部班级" style="width: 200px" @change="fetchStudents">
          <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </div>
    </section>

    <section class="table-card">
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
        <el-table-column label="操作" width="160" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学生' : '新增学生'" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="!editingId" label="账号">
          <el-input v-model="form.username" placeholder="例如：20260011" />
        </el-form-item>
        <el-form-item label="学生姓名">
          <el-input v-model="form.displayName" placeholder="请输入学生姓名" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码">
          <el-input v-model="form.password" show-password placeholder="默认可用 123456" />
        </el-form-item>
        <el-form-item label="所属班级">
          <el-select v-model="form.classId" placeholder="请选择班级" style="width: 100%">
            <el-option v-for="item in classes" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="批量导入学生" width="640px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon title="请先选择班级后再导入；文本格式：学号,姓名,密码（每行一条）" />
      <el-form label-position="top" style="margin-top: 16px">
        <el-form-item label="批量录入文本">
          <el-input v-model="importText" type="textarea" :rows="8" placeholder="20260011,张三,123456" />
        </el-form-item>
      </el-form>
      <section v-if="importResult" class="import-result">
        <strong>成功 {{ importResult.successCount }} 条，失败 {{ importResult.failureCount }} 条</strong>
        <el-table :data="importResult.failures" size="small" empty-text="无失败记录" style="margin-top: 8px">
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
import { ElMessage } from 'element-plus';
import {
  changeAdminStudentStatus,
  createAdminStudent,
  importAdminStudents,
  listAdminClasses,
  listAdminStudents,
  updateAdminStudent,
} from '@/api/admin';
import type { AdminClass, AdminStudent, ImportStudentsResult } from '@/types/admin';
import type { StudentStatus } from '@/types/student';

const loading = ref(false);
const submitting = ref(false);
const importing = ref(false);
const dialogVisible = ref(false);
const importDialogVisible = ref(false);
const editingId = ref<number | null>(null);
const selectedClassId = ref<number | undefined>();
const rows = ref<AdminStudent[]>([]);
const classes = ref<AdminClass[]>([]);
const searchQuery = ref('');
const statusFilter = ref<StudentStatus | ''>('');
const importText = ref('');
const importResult = ref<ImportStudentsResult | null>(null);

const form = reactive({
  username: '',
  displayName: '',
  password: '123456',
  classId: undefined as number | undefined,
});

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.username.toLowerCase().includes(keyword) ||
      row.displayName.toLowerCase().includes(keyword) ||
      (row.className?.toLowerCase().includes(keyword) ?? false) ||
      (row.classCode?.toLowerCase().includes(keyword) ?? false);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

const activeCount = computed(() => filteredRows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => filteredRows.value.filter(item => item.status === 'DISABLED').length);

const resetForm = () => {
  form.username = '';
  form.displayName = '';
  form.password = '123456';
  form.classId = selectedClassId.value ?? classes.value[0]?.id;
  editingId.value = null;
};

const fetchClasses = async () => {
  classes.value = await listAdminClasses();
  if (!selectedClassId.value && classes.value.length === 1) {
    selectedClassId.value = classes.value[0].id;
  }
};

const fetchStudents = async () => {
  loading.value = true;
  try {
    rows.value = await listAdminStudents(selectedClassId.value);
  } finally {
    loading.value = false;
  }
};

const parseImportTextRows = () =>
  importText.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const [username = '', displayName = '', password = ''] = line.split(',').map(item => item.trim());
      return { username, displayName, password: password || username };
    })
    .filter(item => item.username && item.displayName);

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: AdminStudent) => {
  editingId.value = row.id;
  form.displayName = row.displayName;
  form.classId = row.classId ?? selectedClassId.value ?? classes.value[0]?.id;
  dialogVisible.value = true;
};

const submit = async () => {
  if (!form.displayName.trim() || !form.classId) {
    ElMessage.warning('请填写姓名并选择班级');
    return;
  }
  if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
    ElMessage.warning('请填写账号和初始密码');
    return;
  }

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateAdminStudent(editingId.value, {
        displayName: form.displayName.trim(),
        classId: form.classId,
      });
      ElMessage.success('学生更新成功');
    } else {
      await createAdminStudent({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password.trim(),
        classId: form.classId,
      });
      ElMessage.success('学生创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchStudents();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: AdminStudent) => {
  const nextStatus: StudentStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await changeAdminStudentStatus(row.id, nextStatus);
  ElMessage.success('学生状态更新成功');
  await fetchStudents();
};

const openImportDialog = () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }
  importText.value = '';
  importResult.value = null;
  importDialogVisible.value = true;
};

const submitImport = async () => {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择导入班级');
    return;
  }
  const parsedRows = parseImportTextRows();
  if (!parsedRows.length) {
    ElMessage.warning('请输入至少一条有效记录');
    return;
  }

  importing.value = true;
  try {
    importResult.value = await importAdminStudents({ classId: selectedClassId.value, rows: parsedRows });
    ElMessage.success('导入已完成');
    await fetchStudents();
  } finally {
    importing.value = false;
  }
};

onMounted(async () => {
  await fetchClasses();
  resetForm();
  await fetchStudents();
});
</script>

<style scoped>
.admin-eyebrow {
  margin-bottom: 2px;
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.toolbar-card,
.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.toolbar-card {
  padding: 12px 14px;
}

.toolbar-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.table-card {
  padding: 0 0 8px;
  overflow: hidden;
}

.identity-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.identity-cell strong {
  color: #0f172a;
  font-size: 13px;
}

.identity-cell span {
  color: #64748b;
  font-size: 11px;
}

.import-result {
  margin-top: 16px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
</style>
