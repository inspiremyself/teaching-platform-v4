<template>
  <div class="page admin-teachers workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>教师管理</h1>
          <p>维护本校教师账号、姓名与启停状态。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button type="primary" @click="openCreateDialog">
            <span class="material-symbols-outlined app-icon--sm app-icon--leading">person_add</span>
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

    <AppToolbarCard>
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="搜索账号或姓名" clearable class="app-search-field" />
        <el-select v-model="statusFilter" clearable placeholder="全部状态" class="app-filter-field">
          <el-option label="在职" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </div>
    </AppToolbarCard>

    <AppTableCard>
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
            <AppStatusTag v-bind="resolveStatusOf('orgTeacherStatus', row.status)" />
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
    </AppTableCard>

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
        <el-button type="primary" :loading="submitting" @click="submit(submitHandlers)">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import { resolveStatusOf } from '@/components/ui/statusPresentation';
import { ElMessage } from 'element-plus';
import { changeTeacherStatus, createTeacher, listTeachers, updateTeacher } from '@/api/admin';
import type { AdminTeacher, TeacherStatus } from '@/types/admin';
import AppTableCard from '@/components/ui/AppTableCard.vue';
import AppToolbarCard from '@/components/ui/AppToolbarCard.vue';
import { useCrudTable, type CrudSubmitHandlers } from '@/composables/useCrudTable';

const searchQuery = ref('');
const statusFilter = ref<TeacherStatus | ''>('');

const form = reactive({
  username: '',
  displayName: '',
  password: '123456',
});

const { loading, submitting, dialogVisible, editingId, rows, filteredRows, refresh, openCreateDialog, openEditDialog, submit, toggleStatus: runToggleStatus } =
  useCrudTable<AdminTeacher, TeacherStatus>({
    fetchRows: () => listTeachers(),
    keyword: searchQuery,
    statusFilter,
    keywordFieldsOf: row => [row.username, row.displayName],
    resetForm: () => {
      form.username = '';
      form.displayName = '';
      form.password = '123456';
      editingId.value = null;
    },
    fillEditForm: row => {
      form.displayName = row.displayName;
    },
  });

const activeCount = computed(() => rows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => rows.value.filter(item => item.status === 'DISABLED').length);

const submitHandlers: CrudSubmitHandlers = {
  validate: () => {
    if (!form.displayName.trim()) {
      ElMessage.warning('请输入教师姓名');
      return false;
    }
    if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
      ElMessage.warning('请填写账号和初始密码');
      return false;
    }
    return true;
  },
  create: () => createTeacher({
    username: form.username.trim(),
    displayName: form.displayName.trim(),
    password: form.password.trim(),
  }),
  update: id => updateTeacher(id, { displayName: form.displayName.trim() }),
  createdMessage: '教师创建成功',
  updatedMessage: '教师信息已更新',
};

const toggleStatus = (row: AdminTeacher) => {
  const nextStatus: TeacherStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  return runToggleStatus(row, () => changeTeacherStatus(row.id, nextStatus), '教师状态已更新');
};

onMounted(refresh);
</script>
