<template>
  <div class="page admin-subjects workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 学科管理</div>
          <h1>学科管理</h1>
          <p>维护本校学科字典，供班级指派学科教师时使用。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">
            <span class="material-symbols-outlined" style="font-size: 16px; margin-right: 4px;">add</span>
            新增学科
          </el-button>
        </div>
      </div>

      <div class="workbench-meta">
        <span class="workbench-meta__item"><strong>{{ rows.length }}</strong> 学科总数</span>
        <span class="workbench-meta__item"><strong>{{ activeCount }}</strong> 启用中</span>
        <span class="workbench-meta__item"><strong>{{ disabledCount }}</strong> 已停用</span>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="搜索学科名称或编码" clearable style="max-width: 280px" />
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px">
          <el-option label="启用中" value="ACTIVE" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
      </div>
    </section>

    <section class="table-card">
      <el-table :data="filteredRows" v-loading="loading" empty-text="暂无学科数据">
        <el-table-column label="学科" min-width="220">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.name }}</strong>
              <span>编码 {{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="assignmentCount" label="班级指派" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用中' : '已停用' }}
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学科' : '新增学科'" width="480px">
      <el-form label-position="top">
        <el-form-item label="学科名称">
          <el-input v-model="form.name" maxlength="128" placeholder="例如：软件体系结构" />
        </el-form-item>
        <el-form-item label="学科编码">
          <el-input v-model="form.code" maxlength="64" placeholder="例如：SE-ARCH" />
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
import { changeSubjectStatus, createSubject, listSubjects, updateSubject } from '@/api/admin';
import type { AdminSubject, SubjectStatus } from '@/types/admin';

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const rows = ref<AdminSubject[]>([]);
const searchQuery = ref('');
const statusFilter = ref<SubjectStatus | ''>('');

const form = reactive({
  name: '',
  code: '',
});

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const matchesKeyword =
      !keyword ||
      row.name.toLowerCase().includes(keyword) ||
      row.code.toLowerCase().includes(keyword);
    const matchesStatus = !statusFilter.value || row.status === statusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});

const activeCount = computed(() => rows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => rows.value.filter(item => item.status === 'DISABLED').length);

const resetForm = () => {
  form.name = '';
  form.code = '';
  editingId.value = null;
};

const fetchData = async () => {
  loading.value = true;
  try {
    rows.value = await listSubjects();
  } finally {
    loading.value = false;
  }
};

const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const openEditDialog = (row: AdminSubject) => {
  editingId.value = row.id;
  form.name = row.name;
  form.code = row.code;
  dialogVisible.value = true;
};

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请输入学科名称');
    return;
  }
  if (!form.code.trim()) {
    ElMessage.warning('请输入学科编码');
    return;
  }

  submitting.value = true;
  try {
    const payload = { name: form.name.trim(), code: form.code.trim() };
    if (editingId.value) {
      await updateSubject(editingId.value, payload);
      ElMessage.success('学科信息已更新');
    } else {
      await createSubject(payload);
      ElMessage.success('学科创建成功');
    }
    dialogVisible.value = false;
    resetForm();
    await fetchData();
  } finally {
    submitting.value = false;
  }
};

const toggleStatus = async (row: AdminSubject) => {
  const nextStatus: SubjectStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await changeSubjectStatus(row.id, nextStatus);
  ElMessage.success('学科状态已更新');
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
