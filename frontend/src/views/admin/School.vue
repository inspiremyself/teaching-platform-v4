<template>
  <div class="page admin-school workbench-page">
    <section class="workbench-header">
      <div class="workbench-header__top">
        <div class="workbench-header__main">
          <div class="admin-eyebrow">学校管理台 / 学校信息</div>
          <h1>学校信息</h1>
          <p>维护本校名称等基础信息（编码第一期只读）。</p>
        </div>
      </div>
    </section>

    <section class="school-card" v-loading="loading">
      <el-form label-position="top" class="school-form">
        <el-form-item label="学校名称">
          <el-input v-model="form.name" maxlength="128" placeholder="请输入学校名称" />
        </el-form-item>
        <el-form-item label="学校编码">
          <el-input v-model="school.code" disabled />
        </el-form-item>
        <el-form-item label="状态">
          <el-tag :type="school.status === 'ACTIVE' ? 'success' : 'info'">{{ school.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getSchool, updateSchool } from '@/api/admin';
import type { SchoolInfo } from '@/types/admin';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const school = ref<SchoolInfo>({ id: 0, name: '', code: '', status: 'ACTIVE' });
const form = reactive({ name: '' });

const fetchSchool = async () => {
  loading.value = true;
  try {
    school.value = await getSchool();
    form.name = school.value.name;
  } finally {
    loading.value = false;
  }
};

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请输入学校名称');
    return;
  }
  submitting.value = true;
  try {
    school.value = await updateSchool({ name: form.name.trim() });
    await authStore.fetchUserInfo();
    ElMessage.success('学校信息已更新');
  } finally {
    submitting.value = false;
  }
};

onMounted(fetchSchool);
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

.school-card {
  max-width: 520px;
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.school-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}
</style>
