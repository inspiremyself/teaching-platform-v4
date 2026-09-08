<template>
  <div class="login-page">
    <header class="login-header">
      <div class="login-header__brand">教学一体化平台</div>
      <div class="login-header__actions">
        <button type="button" class="text-link" @click="showPlaceholder('帮助中心功能暂未开放')">帮助中心</button>
        <button type="button" class="ghost-button" @click="showPlaceholder('机构账号登录功能暂未开放')">机构账号登录</button>
      </div>
    </header>

    <div class="login-left">
      <div class="hero-panel">
        <p class="hero-eyebrow">教学与学习一体化平台</p>
        <h1 class="brand-title">用统一教学工作空间串联实验、作业、考试与反馈闭环。</h1>
        <p class="brand-desc">覆盖实验、作业、考试、查重与成绩反馈，服务教师管理台与学生任务流的真实闭环。</p>

        <div class="feature-grid">
          <div class="feature-card">
            <span class="material-symbols-outlined">rule</span>
            <div>
              <h3>智能辅助批改</h3>
              <p>让批改、反馈与评分上下文更聚合。</p>
            </div>
          </div>
          <div class="feature-card">
            <span class="material-symbols-outlined">hub</span>
            <div>
              <h3>统一教学入口</h3>
              <p>统一资料、实验、作业与考试入口。</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="login-right">
      <div class="login-card">
        <div class="login-card-header">
          <h2>欢迎登录</h2>
          <p>请使用机构账号登录，并选择当前身份进入对应工作台。</p>
        </div>

        <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" class="login-alert" />

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" size="large" @submit.prevent>
          <el-form-item label="机构账号或用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名，例如 t9001 / 20260001" prefix-icon="User" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>

          <el-form-item label="当前身份" prop="role">
            <el-radio-group v-model="form.role" class="role-group">
              <el-radio-button value="TEACHER">教师</el-radio-button>
              <el-radio-button value="STUDENT">学生</el-radio-button>
              <el-radio-button value="SCHOOL_ADMIN">学校管理员</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <div class="demo-accounts">
            <button type="button" class="demo-chip" @click="fillDemo('TEACHER')">教师演示账号</button>
            <button type="button" class="demo-chip" @click="fillDemo('STUDENT')">学生演示账号</button>
            <button type="button" class="demo-chip" @click="fillDemo('SCHOOL_ADMIN')">校管演示账号</button>
          </div>

          <el-form-item>
            <el-button type="primary" @click="handleLogin" :loading="loading" class="login-btn">登录系统</el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer-tip">如需修改密码或重置账号，请联系系统管理员。</div>
      </div>
    </div>

    <footer class="login-footer">
      <span>© 2026 教学一体化平台</span>
      <div>
        <a href="javascript:void(0)" @click.prevent="showPlaceholder('隐私政策内容整理中')">隐私政策</a>
        <a href="javascript:void(0)" @click.prevent="showPlaceholder('服务条款内容整理中')">服务条款</a>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/utils/request';

const KICKED_OUT_NOTICE_KEY = 'kicked_out_notice';

const authStore = useAuthStore();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const errorMessage = ref('');

const form = reactive({
  username: '',
  password: '',
  role: 'TEACHER' as 'TEACHER' | 'STUDENT' | 'SCHOOL_ADMIN'
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
};

const performLogin = async (forceLogin = false) => {
  await authStore.login({
    username: form.username,
    password: form.password,
    role: form.role,
    forceLogin
  });
};

const isMessageBoxCancel = (value: unknown) => value === 'cancel' || value === 'close';

const resolveLoginError = (error: unknown) => {
  if (error instanceof ApiError) {
    return error.message || '登录失败，请检查账号、密码和当前身份是否正确';
  }
  const message = error instanceof Error ? error.message : '';
  return /status code 400/i.test(message)
    ? '登录失败，请检查账号、密码和当前身份是否正确'
    : message || '登录失败，请检查账号、密码和当前身份是否正确';
};

const handleLogin = async () => {
  if (!formRef.value) {
    return;
  }
  await formRef.value.validate(async valid => {
    if (!valid) {
      return;
    }
    loading.value = true;
    errorMessage.value = '';
    try {
      await performLogin();
      ElMessage.success('登录成功');
      router.push(authStore.homePath);
    } catch (error) {
      if (error instanceof ApiError && error.code === 40901) {
        try {
          await ElMessageBox.confirm(error.message, '账号已在其他设备登录', {
            type: 'warning',
            confirmButtonText: '继续登录',
            cancelButtonText: '取消'
          });
          await performLogin(true);
          ElMessage.success('登录成功');
          router.push(authStore.homePath);
          return;
        } catch (confirmError) {
          if (!isMessageBoxCancel(confirmError)) {
            errorMessage.value = resolveLoginError(confirmError);
          }
          return;
        }
      }
      errorMessage.value = resolveLoginError(error);
    } finally {
      loading.value = false;
    }
  });
};

const showPlaceholder = (message: string) => {
  ElMessage.info(message);
};

const fillDemo = (role: 'TEACHER' | 'STUDENT' | 'SCHOOL_ADMIN') => {
  form.role = role;
  if (role === 'TEACHER') {
    form.username = 't9001';
  } else if (role === 'STUDENT') {
    form.username = '20260001';
  } else {
    form.username = 'admin9001';
  }
  form.password = '123456';
};

onMounted(async () => {
  const kickedOutNotice = sessionStorage.getItem(KICKED_OUT_NOTICE_KEY);
  if (!kickedOutNotice) {
    return;
  }
  sessionStorage.removeItem(KICKED_OUT_NOTICE_KEY);
  errorMessage.value = `${kickedOutNotice}，当前设备已退出登录。`;
  await ElMessageBox.alert(
    '你的账号已在其他设备继续登录，当前设备已被强制下线。若这不是你的操作，请尽快修改密码或联系管理员。',
    '你已被下线',
    {
      type: 'error',
      confirmButtonText: '我知道了'
    }
  );
});
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-wrap: wrap;
  background: #f8fafc;
}

.login-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.75);
  z-index: 2;
}

.login-header__brand {
  font-size: 20px;
  font-weight: 800;
  color: #0f766e;
}

.login-header__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.text-link {
  border: none;
  background: transparent;
  color: #475569;
  font-weight: 600;
  cursor: pointer;
}

.ghost-button {
  border: none;
  background: #0f766e;
  color: #fff;
  border-radius: 999px;
  padding: 10px 16px;
  font-weight: 700;
  cursor: pointer;
}

.login-left {
  flex: 1;
  min-height: 100vh;
  background: radial-gradient(circle at top left, rgba(20, 184, 166, 0.28), transparent 35%), linear-gradient(135deg, #0f172a 0%, #0f766e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px 48px 80px;
}

.hero-panel {
  max-width: 540px;
  padding: 40px;
  border-radius: 32px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.32);
  backdrop-filter: blur(18px);
}

.hero-eyebrow {
  margin: 0 0 16px;
  color: #99f6e4;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-weight: 700;
}

.brand-title {
  color: #fff;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.12;
  margin: 0 0 18px;
}

.brand-desc {
  color: rgba(226, 232, 240, 0.82);
  font-size: 15px;
  line-height: 1.8;
  margin: 0;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
}

.feature-card {
  display: flex;
  gap: 12px;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
}

.feature-card span {
  color: #99f6e4;
}

.feature-card h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.feature-card p {
  margin: 0;
  color: rgba(226, 232, 240, 0.72);
  font-size: 12px;
  line-height: 1.5;
}

.login-right {
  width: 480px;
  min-width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px 48px 80px;
  background: #fff;
}

.login-card {
  width: 100%;
  background: #fff;
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
  border: 1px solid #e2e8f0;
}

.login-card-header {
  margin-bottom: 28px;
}

.login-card-header h2 {
  font-size: 30px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 6px;
}

.login-card-header p {
  color: #64748b;
  font-size: 14px;
  margin: 0;
}

.login-alert {
  margin-bottom: 20px;
}

.role-group {
  width: 100%;
}

.role-group :deep(.el-radio-button) {
  flex: 1;
}

.role-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.demo-accounts {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.demo-chip {
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  color: #0f172a;
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 14px !important;
}

.login-footer-tip {
  margin-top: 16px;
  color: #64748b;
  font-size: 12px;
  text-align: center;
}

.login-footer {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32px 24px;
  margin-top: -24px;
  color: #64748b;
  font-size: 12px;
}

.login-footer div {
  display: flex;
  gap: 16px;
}

.login-footer a {
  color: inherit;
  text-decoration: none;
}

@media (max-width: 900px) {
  .login-left {
    display: none;
  }

  .login-right {
    width: 100%;
    min-width: unset;
    padding: 120px 20px 40px;
  }

  .login-header {
    padding: 0 16px;
  }

  .login-footer {
    padding: 0 16px 16px;
    flex-direction: column;
    gap: 8px;
  }

  .demo-accounts {
    flex-wrap: wrap;
  }
}
</style>
