<template>
  <div class="page admin-teachers workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 教师管理</div>
          <h1>教师管理</h1>
          <p>维护本校教师账号、姓名与启停状态。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">
            <span class="material-symbols-outlined" style="font-size: 16px; margin-right: 4px;">person_add</span>
            新增教师
          </el-button>
        </div>
      </div>

      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 教师总数</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 在职</span>
        <span class="workbench-meta__item"><strong>{{ disabledCount }}</strong> 停用</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="搜索账号或姓名" clearable style="max-width: 280px" />
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px">
          <el-option label="在职" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </div>
    </section>

    <section class="table-card">
      <el-table :data="filteredRows" v-loading="loading" empty-text="暂无教师数据">
        <el-table-column label="教师" min-width="240">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.displayName }}</strong>
              <span>账号 {{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '在职' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="classCount" label="关联班级" width="120" />
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑教师' : '新增教师'" width="480px">
      <el-form label-position="top">
        <el-form-item v-if="!editingId" label="账号">
          <el-input v-model="form.username" placeholder="例如 t9003" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.displayName" placeholder="请输入教师姓名" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码">
          <el-input v-model="form.password" show-password placeholder="默认可用 123456" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { changeTeacherStatus, createTeacher, listTeachers, updateTeacher } from '@/api/admin';
import type { AdminTeacher, TeacherStatus } from '@/types/admin';

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<AdminTeacher[]>([]);
const searchQuery = ref('');
const statusFilter = ref<TeacherStatus | ''>('');

const form = reactive({
  username: '',
  displayName: '',
  password: '123456',
});

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.username.toLowerCase().includes(keyword) ||
      row.displayName.toLowerCase().includes(keyword);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

const activeCount = computed(() => rows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => rows.value.filter(item => item.status === 'DISABLED').length);

const resetForm = () => {
  form.username = '';
  form.displayName = '';
  form.password = '123456';
  editingId.value = null;
};

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listTeachers();
  } finally {
    loading.value = false;
  }
};

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: AdminTeacher) => {
  editingId.value = row.id;
  form.displayName = row.displayName;
  dialogVisible.value = true;
};

const submit = async () => {
  if (!form.displayName.trim()) {
    ElMessage.warning('请输入教师姓名');
    return;
  }
  if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
    ElMessage.warning('请填写账号和初始密码');
    return;
  }

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateTeacher(editingId.value, { displayName: form.displayName.trim() });
      ElMessage.success('教师信息已更新');
    } else {
      await createTeacher({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password.trim(),
      });
      ElMessage.success('教师创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: AdminTeacher) => {
  const nextStatus: TeacherStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await changeTeacherStatus(row.id, nextStatus);
  ElMessage.success('教师状态已更新');
  await fetchData();
};

onMounted(fetchData);
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
</style>
