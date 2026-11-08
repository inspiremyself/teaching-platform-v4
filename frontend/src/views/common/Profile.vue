<template>
  <div class="profile-page">
    <section class="workbench-header profile-hero">
      <div class="workbench-header__top profile-hero__top">
        <div class="workbench-header__main profile-hero__main">
          <div class="profile-hero__eyebrow">账户中心 / 个人资料与安全</div>
          <h1>个人设置</h1>
          <p>这里集中维护你的真实账户资料、联系信息和密码安全，不再拆成伪标签页或假分区导航。</p>
        </div>
        <div class="workbench-header__actions profile-hero__actions">
          <el-button type="primary" plain @click="goHome">返回角色首页</el-button>
        </div>
      </div>

      <div class="profile-summary-panel">
        <div class="profile-identity">
          <div class="profile-avatar">{{ user?.displayName?.charAt(0) || '?' }}</div>
          <div class="profile-identity__meta">
            <strong>{{ user?.displayName }}</strong>
            <span>{{ roleLabel }} · {{ user?.username }}</span>
            <small>{{ user?.email || '尚未填写邮箱' }}</small>
          </div>
        </div>

        <div class="profile-summary-grid">
          <article class="profile-summary-item">
            <span class="material-symbols-outlined">verified_user</span>
            <div>
              <strong>{{ roleLabel }}</strong>
              <p>当前账号已接入真实角色权限与首页跳转。</p>
            </div>
          </article>
          <article class="profile-summary-item">
            <span class="material-symbols-outlined">task_alt</span>
            <div>
              <strong>资料完成度 {{ profileCompletion }}%</strong>
              <p>{{ profileCompletionHint }}</p>
            </div>
          </article>
          <article class="profile-summary-item">
            <span class="material-symbols-outlined">lock_clock</span>
            <div>
              <strong>{{ securityStatusTitle }}</strong>
              <p>{{ securityStatusHint }}</p>
            </div>
          </article>
        </div>
      </div>
    </section>

    <div class="profile-grid">
      <aside class="profile-sidebar-card">
        <div class="profile-sidebar-head">
          <div class="sidebar-avatar">{{ user?.displayName?.charAt(0) || '?' }}</div>
          <h3>{{ user?.displayName }}</h3>
          <p>{{ roleLabel }} · {{ user?.username }}</p>
        </div>

        <div class="profile-sidebar-block">
          <span class="profile-sidebar-block__label">当前资料状态</span>
          <div class="profile-sidebar-progress">
            <div class="profile-sidebar-progress__track">
              <span :style="{ width: `${profileCompletion}%` }"></span>
            </div>
            <strong>{{ profileCompletion }}%</strong>
          </div>
          <p>{{ profileCompletionHint }}</p>
        </div>

        <div class="profile-overview-list">
          <article class="profile-overview-item">
            <span class="material-symbols-outlined">badge</span>
            <div>
              <strong>账户身份</strong>
              <p>{{ roleLabel }}，用户名 {{ user?.username }}</p>
            </div>
          </article>
          <article class="profile-overview-item">
            <span class="material-symbols-outlined">mail</span>
            <div>
              <strong>联系方式</strong>
              <p>{{ user?.email || '邮箱未填写' }} / {{ user?.phone || '手机号未填写' }}</p>
            </div>
          </article>
          <article class="profile-overview-item">
            <span class="material-symbols-outlined">lock_clock</span>
            <div>
              <strong>密码状态</strong>
              <p>{{ securityStatusHint }}</p>
            </div>
          </article>
        </div>
      </aside>

      <section class="profile-content">
        <section class="profile-section-intro">
          <div>
            <span class="profile-section-intro__label">资料维护</span>
            <h2>基本资料</h2>
            <p>优先维护姓名、联系方式和个人简介，这些信息会直接影响教师/学生空间中的真实展示。</p>
          </div>
          <el-tag type="success" effect="plain">{{ roleLabel }}</el-tag>
        </section>

        <el-card id="profile-basics" class="profile-card" shadow="never">
          <template #header>
            <div class="card-header-row card-header-row--aligned-start">
              <div>
                <span>账户资料</span>
                <p>所有字段均保存到当前真实账户资料中，保存后会同步影响个人展示信息。</p>
              </div>
              <span class="profile-card__hint">先完成基本资料，再根据需要更新密码。</span>
            </div>
          </template>
          <el-alert v-if="profileFeedback" :type="profileFeedback.type" :title="profileFeedback.message" :closable="false" class="feedback-alert" />
          <el-form :model="profileForm" label-position="top" class="profile-form" @submit.prevent="saveProfile">
            <div class="profile-form-grid">
              <el-form-item label="用户名">
                <el-input :model-value="user?.username || ''" disabled />
              </el-form-item>
              <el-form-item label="角色">
                <el-input :model-value="roleLabel" disabled />
              </el-form-item>
              <el-form-item label="姓名">
                <el-input v-model="profileForm.displayName" placeholder="请输入姓名" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="办公时间 / 联系提示">
                <el-input v-model="profileForm.officeHours" placeholder="请输入办公时间或联系提示" />
              </el-form-item>
              <el-form-item label="个人简介" class="profile-form-grid__full">
                <el-input v-model="profileForm.bio" type="textarea" :rows="5" placeholder="请输入个人简介" />
              </el-form-item>
            </div>
            <div class="profile-actions">
              <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
            </div>
          </el-form>
        </el-card>

        <section class="profile-section-intro profile-section-intro--security">
          <div>
            <span class="profile-section-intro__label">账户安全</span>
            <h2>密码与安全</h2>
            <p>这里直接走真实改密接口，主要处理密码更新，不再伪装成额外的“设置标签页”。</p>
          </div>
          <span class="security-score">安全评分：{{ user?.mustChangePassword ? '需尽快处理' : '状态良好' }}</span>
        </section>

        <el-card id="profile-security" class="profile-card" shadow="never">
          <template #header>
            <div class="card-header-row card-header-row--aligned-start">
              <div>
                <span>修改登录密码</span>
                <p>提交后会立即调用真实改密接口；如系统提示必须改密，建议优先在这里完成处理。</p>
              </div>
              <span class="profile-card__hint">密码更新成功后会立刻清空当前输入。</span>
            </div>
          </template>
          <el-alert v-if="passwordFeedback" :type="passwordFeedback.type" :title="passwordFeedback.message" :closable="false" class="feedback-alert" />
          <el-form :model="pwdForm" label-position="top" class="security-form" @submit.prevent="changePassword">
            <el-form-item label="当前密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <div class="password-strength">
              <span class="strength-bar strength-bar--active"></span>
              <span class="strength-bar strength-bar--active"></span>
              <span class="strength-bar" :class="{ 'strength-bar--active': pwdForm.newPassword.length >= 8 }"></span>
              <span class="strength-bar" :class="{ 'strength-bar--active': pwdForm.newPassword.length >= 12 }"></span>
            </div>
            <div class="profile-actions">
              <el-button type="primary" :loading="changing" @click="changePassword">保存新密码</el-button>
            </div>
          </el-form>
        </el-card>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { changePassword as changePasswordApi } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();
const { user } = storeToRefs(authStore);
const router = useRouter();
const roleLabel = computed(() => {
  switch (user.value?.role) {
    case 'TEACHER':
      return '教师用户';
    case 'STUDENT':
      return '学生用户';
    case 'SCHOOL_ADMIN':
      return '学校管理员';
    case 'PLATFORM_ADMIN':
      return '平台超管';
    default:
      return '用户';
  }
});
const changing = ref(false);
const savingProfile = ref(false);
const profileFeedback = ref<{ type: 'success' | 'error'; message: string } | null>(null);
const passwordFeedback = ref<{ type: 'success' | 'error' | 'warning'; message: string } | null>(null);

const profileCompletion = computed(() => {
  const fields = [profileForm.displayName, profileForm.email, profileForm.phone, profileForm.officeHours, profileForm.bio];
  const filled = fields.filter(value => value.trim()).length;
  return Math.round((filled / fields.length) * 100);
});

const profileCompletionHint = computed(() => {
  if (profileCompletion.value === 100) {
    return '联系信息、办公提示和个人简介都已完善。';
  }
  if (profileCompletion.value >= 60) {
    return '建议继续补全办公提示和简介，减少沟通往返。';
  }
  return '当前资料仍较简略，建议先补齐邮箱、手机和联系说明。';
});

const securityStatusTitle = computed(() => (user.value?.mustChangePassword ? '建议尽快更新密码' : '当前密码状态稳定'));
const securityStatusHint = computed(() => (user.value?.mustChangePassword
  ? '系统标记该账号需要尽快修改密码，建议立即完成。'
  : '如近期无密码调整需求，可继续保持当前登录凭据。'));

const profileForm = reactive({
  displayName: '',
  email: '',
  phone: '',
  officeHours: '',
  bio: ''
});

watch(
  user,
  currentUser => {
    profileForm.displayName = currentUser?.displayName ?? '';
    profileForm.email = currentUser?.email ?? '';
    profileForm.phone = currentUser?.phone ?? '';
    profileForm.officeHours = currentUser?.officeHours ?? '';
    profileForm.bio = currentUser?.bio ?? '';
  },
  { immediate: true }
);

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

const goHome = () => {
  router.push(authStore.homePath);
};

const saveProfile = async () => {
  if (!profileForm.displayName.trim()) {
    ElMessage.warning('姓名不能为空');
    return;
  }
  savingProfile.value = true;
  profileFeedback.value = null;
  try {
    await authStore.updateProfile({
      displayName: profileForm.displayName,
      email: profileForm.email,
      phone: profileForm.phone,
      officeHours: profileForm.officeHours,
      bio: profileForm.bio
    });
    profileFeedback.value = { type: 'success', message: '个人资料已更新。' };
    ElMessage.success('个人资料已保存');
  } catch (error) {
    const message = error instanceof Error ? error.message : '个人资料保存失败';
    profileFeedback.value = { type: 'error', message };
  } finally {
    savingProfile.value = false;
  }
};

const changePassword = async () => {
  passwordFeedback.value = null;
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    passwordFeedback.value = { type: 'warning', message: '请填写完整密码信息' };
    ElMessage.warning('请填写完整密码信息');
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    passwordFeedback.value = { type: 'warning', message: '两次输入的新密码不一致' };
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(pwdForm.newPassword)) {
    passwordFeedback.value = { type: 'warning', message: '新密码需 8-64 位，且同时包含字母和数字' };
    ElMessage.warning('新密码需 8-64 位，且同时包含字母和数字');
    return;
  }
  changing.value = true;
  try {
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword });
    passwordFeedback.value = { type: 'success', message: '密码修改成功' };
    ElMessage.success('密码修改成功');
    pwdForm.oldPassword = '';
    pwdForm.newPassword = '';
    pwdForm.confirmPassword = '';
  } catch {
    passwordFeedback.value = { type: 'error', message: '密码修改失败，请检查当前密码是否正确' };
    ElMessage.error('密码修改失败，请检查当前密码是否正确');
  } finally {
    changing.value = false;
  }
};
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-hero {
  gap: 18px;
}

.profile-hero__top {
  align-items: flex-start;
}

.profile-hero__eyebrow,
.profile-section-intro__label,
.profile-sidebar-block__label {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}

.profile-summary-panel {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.profile-identity {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 14px;
  padding: 18px 20px;
  min-height: 148px;
  border: 1px solid #dbe4f0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.profile-identity__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-avatar,
.sidebar-avatar {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

.profile-identity__meta small {
  color: #94a3b8;
  font-size: 12px;
}

.profile-identity strong,
.profile-sidebar-head h3 {
  display: block;
  color: #0f172a;
}

.profile-identity span,
.profile-sidebar-head p {
  color: #64748b;
  font-size: 13px;
}

.profile-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-summary-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid #dbe4f0;
  border-radius: 18px;
  background: #f8fafc;
}

.profile-summary-item .material-symbols-outlined {
  font-size: 20px;
  color: #0f766e;
}

.profile-summary-item strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.profile-summary-item p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.profile-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
}

.profile-sidebar-card,
.profile-card {
  border-radius: 24px;
  border: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.profile-sidebar-card {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 24px;
  align-self: start;
}

.profile-sidebar-head {
  text-align: center;
}

.sidebar-avatar {
  margin: 0 auto 14px;
}

.profile-sidebar-head p {
  margin: 6px 0 0;
}

.profile-sidebar-block {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.profile-sidebar-block p {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.profile-sidebar-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.profile-sidebar-progress strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.profile-sidebar-progress__track {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #dbe4f0;
}

.profile-sidebar-progress__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #14b8a6, #0f766e);
}

.profile-overview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.profile-overview-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.profile-overview-item .material-symbols-outlined {
  margin-top: 2px;
  color: #0f766e;
  font-size: 18px;
}

.profile-overview-item strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.profile-overview-item p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-section-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.profile-section-intro h2 {
  margin: 6px 0 0;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.profile-section-intro p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  gap: 16px;
}

.card-header-row--aligned-start {
  align-items: flex-start;
}

.card-header-row p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.profile-card__hint {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  text-align: right;
}

.feedback-alert {
  margin-bottom: 20px;
}

.profile-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.profile-form-grid__full {
  grid-column: 1 / -1;
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
}

.security-score {
  font-size: 12px;
  color: #0f766e;
  font-weight: 700;
}

.security-form {
  max-width: 560px;
}

.password-strength {
  display: flex;
  gap: 6px;
  margin: 8px 0 18px;
}

.strength-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
}

.strength-bar--active {
  background: linear-gradient(90deg, #14b8a6, #0f766e);
}

@media (max-width: 980px) {
  .profile-summary-panel,
  .profile-summary-grid,
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .profile-sidebar-card {
    position: static;
  }

  .profile-form-grid {
    grid-template-columns: 1fr;
  }

  .profile-hero__top,
  .profile-section-intro,
  .card-header-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-card__hint {
    text-align: left;
  }
}
</style>
