<template>
  <div class="page admin-schools workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">平台管理台 / 学校管理</div>
          <h1>学校管理</h1>
          <p>开通学校并创建首位校管；停用后该校账号将无法登录。</p>
        </div>
        <el-button type="primary" @click="openCreate">开通学校</el-button>
      </div>
    </section>

    <section class="schools-card" v-loading="loading">
      <el-table :data="schools" border stripe>
        <el-table-column prop="name" label="学校名称" min-width="160" />
        <el-table-column prop="code" label="学校编码" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adminUsername" label="首位校管" min-width="140" />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" title="开通学校" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="学校名称" prop="name">
          <el-input v-model="form.name" maxlength="128" placeholder="请输入学校名称" />
        </el-form-item>
        <el-form-item label="学校编码" prop="code">
          <el-input v-model="form.code" maxlength="64" placeholder="请输入学校编码" />
        </el-form-item>
        <el-form-item label="校管账号" prop="adminUsername">
          <el-input v-model="form.adminUsername" maxlength="64" placeholder="请输入校管账号" />
        </el-form-item>
        <el-form-item label="初始密码" prop="adminPassword">
          <el-input v-model="form.adminPassword" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认开通</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createPlatformSchool,
  listPlatformSchools,
  updatePlatformSchoolStatus,
} from '@/api/admin';
import type { CreateSchoolPayload, PlatformSchoolItem, SchoolStatus } from '@/types/admin';

const schools = ref<PlatformSchoolItem[]>([]);
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<CreateSchoolPayload>({
  name: '',
  code: '',
  adminUsername: '',
  adminPassword: '',
});

const rules = {
  name: [
    { required: true, message: '请输入学校名称', trigger: 'blur' },
    { max: 128, message: '学校名称长度不能超过128个字符', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入学校编码', trigger: 'blur' },
    { max: 64, message: '学校编码长度不能超过64个字符', trigger: 'blur' },
  ],
  adminUsername: [
    { required: true, message: '请输入校管账号', trigger: 'blur' },
    { max: 64, message: '校管账号长度不能超过64个字符', trigger: 'blur' },
  ],
  adminPassword: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
};

const loadSchools = async () => {
  loading.value = true;
  try {
    schools.value = await listPlatformSchools();
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  form.name = '';
  form.code = '';
  form.adminUsername = '';
  form.adminPassword = '';
  formRef.value?.clearValidate();
};

const openCreate = () => {
  resetForm();
  dialogVisible.value = true;
};

const submitCreate = async () => {
  if (!formRef.value) {
    return;
  }
  await formRef.value.validate(async valid => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      await createPlatformSchool({ ...form });
      ElMessage.success('学校已开通');
      dialogVisible.value = false;
      await loadSchools();
    } finally {
      submitting.value = false;
    }
  });
};

const toggleStatus = async (row: PlatformSchoolItem) => {
  const target: SchoolStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  try {
    await ElMessageBox.confirm(
      target === 'DISABLED'
        ? `停用后「${row.name}」的账号将无法登录，确认继续？`
        : `确认启用「${row.name}」？`,
      target === 'DISABLED' ? '停用学校' : '启用学校',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await updatePlatformSchoolStatus(row.id, target);
  ElMessage.success(target === 'DISABLED' ? '学校已停用' : '学校已启用');
  await loadSchools();
};

const formatTime = (value: string) => (value ? value.replace('T', ' ').slice(0, 19) : '—');

onMounted(loadSchools);
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

.schools-card {
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}
</style>
