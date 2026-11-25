<template>
  <div class="page admin-subjects workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <h1>学科管理</h1>
          <p>维护本校学科字典，供班级指派学科教师时使用。</p>
        </div>
        <div class="workbench-header__actions">
          <el-button @click="openPlatformDialog">从平台启用</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <span class="material-symbols-outlined app-icon--sm app-icon--leading">add</span>
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

    <AppToolbarCard>
      <div class="toolbar-row">
        <el-input v-model="searchQuery" placeholder="搜索学科名称或编码" clearable class="app-search-field" />
        <el-select v-model="statusFilter" clearable placeholder="全部状态" class="app-filter-field">
          <el-option label="启用中" value="ACTIVE" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
      </div>
    </AppToolbarCard>

    <AppTableCard>
      <el-table :data="filteredRows" v-loading="loading" empty-text="暂无学科数据">
        <el-table-column label="学科" min-width="220">
          <template #default="{ row }">
            <div class="identity-cell">
              <strong>{{ row.name }}</strong>
              <span>编码 {{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="140">
          <template #default="{ row }">
            <span>{{ row.globalSubjectCode != null ? row.globalSubjectCode : '本校自建' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="assignmentCount" label="班级指派" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <AppStatusTag v-bind="resolveStatusOf('orgSubjectStatus', row.status)" />
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
    </AppTableCard>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学科' : '新增学科'" width="480px">
      <el-form label-position="top">
        <el-form-item label="学科名称">
          <el-input v-model="form.name" maxlength="128" placeholder="例如：软件体系结构" />
        </el-form-item>
        <el-form-item label="学科编码">
          <el-input v-model="form.code" maxlength="64" placeholder="例如：SE-ARCH" :disabled="codeLocked" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit(submitHandlers)">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="platformDialogVisible" title="从平台启用学科" width="480px">
      <el-form label-position="top">
        <el-form-item label="平台学科">
          <el-select v-model="selectedPlatformId" placeholder="请选择平台学科" class="app-control-full">
            <el-option
              v-for="item in platformOptions"
              :key="item.id"
              :label="`${item.name} (${item.code})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="platformDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="platformSubmitting" @click="submitPlatformEnable">启用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import AppStatusTag from '@/components/ui/AppStatusTag.vue';
import { resolveStatusOf } from '@/components/ui/statusPresentation';
import { ElMessage } from 'element-plus';
import {
  changeSubjectStatus,
  createSubject,
  enablePlatformSubject,
  listPlatformSubjects,
  listSubjects,
  updateSubject,
} from '@/api/admin';
import type { AdminSubject, PlatformSubject, SubjectStatus } from '@/types/admin';
import AppTableCard from '@/components/ui/AppTableCard.vue';
import AppToolbarCard from '@/components/ui/AppToolbarCard.vue';
import { useCrudTable, type CrudSubmitHandlers } from '@/composables/useCrudTable';

const searchQuery = ref('');
const statusFilter = ref<SubjectStatus | ''>('');
const platformDialogVisible = ref(false);
const platformOptions = ref<PlatformSubject[]>([]);
const selectedPlatformId = ref<number | null>(null);
const platformSubmitting = ref(false);
const codeLocked = ref(false);

const form = reactive({
  name: '',
  code: '',
});

const { loading, submitting, dialogVisible, editingId, rows, filteredRows, refresh, openCreateDialog, openEditDialog, submit, toggleStatus: runToggleStatus } =
  useCrudTable<AdminSubject, SubjectStatus>({
    fetchRows: () => listSubjects(),
    keyword: searchQuery,
    statusFilter,
    keywordFieldsOf: row => [row.name, row.code],
    resetForm: () => {
      form.name = '';
      form.code = '';
      editingId.value = null;
      codeLocked.value = false;
    },
    fillEditForm: row => {
      form.name = row.name;
      form.code = row.code;
      codeLocked.value = row.globalSubjectId != null;
    },
  });

const activeCount = computed(() => rows.value.filter(item => item.status === 'ACTIVE').length);
const disabledCount = computed(() => rows.value.filter(item => item.status === 'DISABLED').length);

const submitHandlers: CrudSubmitHandlers = {
  validate: () => {
    if (!form.name.trim()) {
      ElMessage.warning('请输入学科名称');
      return false;
    }
    if (!form.code.trim()) {
      ElMessage.warning('请输入学科编码');
      return false;
    }
    return true;
  },
  create: () => createSubject({ name: form.name.trim(), code: form.code.trim() }),
  update: id => updateSubject(id, { name: form.name.trim(), code: form.code.trim() }),
  createdMessage: '学科创建成功',
  updatedMessage: '学科信息已更新',
  swallowError: true,
};

const toggleStatus = (row: AdminSubject) => {
  const nextStatus: SubjectStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  return runToggleStatus(row, () => changeSubjectStatus(row.id, nextStatus), '学科状态已更新');
};

const openPlatformDialog = async () => {
  selectedPlatformId.value = null;
  try {
    platformOptions.value = await listPlatformSubjects();
    platformDialogVisible.value = true;
  } catch {
    // 全局拦截器已提示，这里仅阻止异常冒泡且不打开空下拉
  }
};

const submitPlatformEnable = async () => {
  if (selectedPlatformId.value == null) {
    ElMessage.warning('请选择平台学科');
    return;
  }
  platformSubmitting.value = true;
  try {
    await enablePlatformSubject(selectedPlatformId.value);
    ElMessage.success('平台学科已启用');
    platformDialogVisible.value = false;
    selectedPlatformId.value = null;
    await refresh();
  } catch {
    // 全局拦截器已提示，这里仅阻止异常冒泡并保持弹窗打开
  } finally {
    platformSubmitting.value = false;
  }
};

onMounted(refresh);
</script>
