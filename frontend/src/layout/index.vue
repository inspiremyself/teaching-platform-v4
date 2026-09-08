<template>
  <div class="app-layout" :class="themeClass">
    <!-- TopAppBar -->
    <header class="top-navbar">
      <div class="top-navbar-left">
        <div class="brand-logo">
          <span class="material-symbols-outlined brand-icon">school</span>
          <span class="brand-text">{{ brandText }}</span>
        </div>
      </div>

      <div class="top-navbar-right">
        <button
          type="button"
          class="user-profile"
          :aria-label="`打开${user?.displayName || '当前用户'}的个人设置`"
          @click="goProfile"
        >
          <div class="avatar">{{ user?.displayName?.charAt(0) || '?' }}</div>
          <div class="user-profile__meta">
            <strong>{{ user?.displayName || '未登录用户' }}</strong>
            <span>{{ spaceLabel }}</span>
          </div>
          <span class="material-symbols-outlined user-profile__arrow">chevron_right</span>
        </button>
        <el-button type="danger" text @click="logout" class="logout-btn">
          <span class="material-symbols-outlined" style="font-size: 18px; margin-right: 4px;">logout</span>退出
        </el-button>
      </div>
    </header>

    <div class="layout-body">
      <!-- SideNavBar -->
      <aside class="sidebar">
        <div class="sidebar-org" v-if="isSchoolAdmin">
          <div class="org-icon">
            <span class="material-symbols-outlined">account_balance</span>
          </div>
          <div>
            <div class="org-name">{{ user?.schoolName || '学校管理' }}</div>
            <div class="org-term">学校管理空间</div>
          </div>
        </div>

        <div class="sidebar-org" v-else-if="isTeacher">
          <div class="org-icon">
            <span class="material-symbols-outlined">account_balance</span>
          </div>
          <div>
            <div class="org-name">{{ user?.schoolName || '教学一体化平台' }}</div>
            <div class="org-term">当前教学周期</div>
          </div>
        </div>
        
        <div class="sidebar-org" v-else>
          <div>
            <div class="org-name">{{ user?.schoolName || '教学一体化平台' }}</div>
            <div class="org-term">学生学习空间</div>
          </div>
        </div>

        <nav class="sidebar-nav" :aria-label="sidebarNavLabel">
          <template v-if="isSchoolAdmin">
            <router-link to="/admin/dashboard" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">dashboard</span><span>总览</span>
            </router-link>
            <router-link to="/admin/teachers" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">badge</span><span>教师管理</span>
            </router-link>
            <router-link to="/admin/classes" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">groups</span><span>班级管理</span>
            </router-link>
            <router-link to="/admin/students" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">person</span><span>学生管理</span>
            </router-link>
            <router-link to="/admin/school" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">domain</span><span>学校信息</span>
            </router-link>
          </template>

          <template v-else-if="isTeacher">
            <router-link to="/teacher/dashboard" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">dashboard</span><span>总览</span>
            </router-link>
            <router-link to="/teacher/materials" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">folder</span><span>教学资料</span>
            </router-link>
            <router-link to="/teacher/questions" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">database</span><span>题库</span>
            </router-link>
            <router-link to="/teacher/labs" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">science</span><span>实验</span>
            </router-link>
            <router-link to="/teacher/lab-reports" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">history_edu</span><span>实验报告</span>
            </router-link>
            <router-link to="/teacher/homeworks" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">assignment</span><span>作业</span>
            </router-link>
            <router-link to="/teacher/exams" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">quiz</span><span>考试</span>
            </router-link>
            <router-link to="/teacher/analysis" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">analytics</span><span>分析报表</span>
            </router-link>
            <router-link to="/teacher/plagiarism" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">search</span><span>查重治理</span>
            </router-link>
          </template>

          <template v-else>
            <router-link to="/student/dashboard" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">dashboard</span><span>总览</span>
            </router-link>
            <router-link to="/student/materials" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">menu_book</span><span>课程资料</span>
            </router-link>
            <router-link to="/student/labs" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">science</span><span>实验任务</span>
            </router-link>
            <router-link to="/student/homeworks" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">assignment_turned_in</span><span>作业任务</span>
            </router-link>
            <router-link to="/student/exams" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">quiz</span><span>考试任务</span>
            </router-link>
            <router-link to="/student/scores" class="nav-item" active-class="nav-active">
              <span class="material-symbols-outlined">grade</span><span>成绩反馈</span>
            </router-link>
          </template>
        </nav>

        <div class="sidebar-footer">
          <router-link to="/profile" class="nav-item" active-class="nav-active" aria-label="进入个人设置页面">
            <span class="material-symbols-outlined">settings</span><span>个人设置</span>
          </router-link>
        </div>
      </aside>

      <!-- Main Content -->
      <main class="main-content" id="main-content" tabindex="-1">
        <div class="main-content__shell">
          <router-view />
        </div>
      </main>
    </div>

    <nav class="mobile-nav" aria-label="移动端主导航">
      <router-link
        v-for="item in mobileNavItems"
        :key="item.to"
        :to="item.to"
        class="mobile-nav__item"
        active-class="mobile-nav__item--active"
      >
        <span class="material-symbols-outlined">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { logoutRequest } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

type NavItem = {
  to: string;
  label: string;
  icon: string;
};

const authStore = useAuthStore();
const router = useRouter();
const { user } = storeToRefs(authStore);

const isTeacher = computed(() => user.value?.role === 'TEACHER');
const isSchoolAdmin = computed(() => user.value?.role === 'SCHOOL_ADMIN');
const brandText = computed(() => {
  if (isSchoolAdmin.value) return '学校管理台';
  if (isTeacher.value) return '教学工作台';
  return '学习工作台';
});
const spaceLabel = computed(() => {
  if (isSchoolAdmin.value) return '校管空间';
  if (isTeacher.value) return '教师空间';
  return '学生空间';
});
const sidebarNavLabel = computed(() => {
  if (isSchoolAdmin.value) return '校管端主导航';
  if (isTeacher.value) return '教师端主导航';
  return '学生端主导航';
});
const themeClass = computed(() => {
  if (isSchoolAdmin.value) return 'theme-admin';
  if (isTeacher.value) return 'theme-teacher';
  return 'theme-student';
});
const mobileNavItems = computed<NavItem[]>(() => {
  if (isSchoolAdmin.value) {
    return [
      { to: '/admin/dashboard', label: '总览', icon: 'dashboard' },
      { to: '/admin/teachers', label: '教师', icon: 'badge' },
      { to: '/admin/classes', label: '班级', icon: 'groups' },
      { to: '/admin/students', label: '学生', icon: 'person' },
      { to: '/profile', label: '我的', icon: 'settings' },
    ];
  }
  if (isTeacher.value) {
    return [
      { to: '/teacher/dashboard', label: '总览', icon: 'dashboard' },
      { to: '/teacher/labs', label: '实验', icon: 'science' },
      { to: '/teacher/homeworks', label: '作业', icon: 'assignment' },
      { to: '/teacher/exams', label: '考试', icon: 'quiz' },
      { to: '/profile', label: '我的', icon: 'person' },
    ];
  }

  return [
    { to: '/student/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/student/materials', label: '资料', icon: 'menu_book' },
    { to: '/student/labs', label: '实验', icon: 'science' },
    { to: '/student/homeworks', label: '作业', icon: 'assignment_turned_in' },
    { to: '/profile', label: '我的', icon: 'person' },
  ];
});

const goProfile = () => {
  router.push('/profile');
};

const logout = async () => {
  try {
    await logoutRequest();
  } catch {
    // ignore logout request failure and still clear local session
  } finally {
    authStore.logout();
    router.push('/login');
  }
};
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  font-family: 'Inter', 'Plus Jakarta Sans', sans-serif;
  background-color: #f8fafc;
}

@media (min-width: 900px) {
  .app-layout:has(.workbench-page--locked) {
    height: 100vh;
    overflow: hidden;
  }
}

/* Theme Colors */
.theme-teacher {
  --primary-color: #0f766e;
  --primary-light: #f0fdfa;
  --primary-border: #0d9488;
}
.theme-admin {
  --primary-color: #7c3aed;
  --primary-light: #f5f3ff;
  --primary-border: #8b5cf6;
}
.theme-student {
  --primary-color: #1d4ed8;
  --primary-light: #eff6ff;
  --primary-border: #2563eb;
}

/* Top Navbar */
.top-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  height: 4rem;
  position: sticky;
  top: 0;
  z-index: 50;
  background-color: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.top-navbar-left {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--primary-color);
}
.brand-icon {
  font-size: 1.75rem;
  font-variation-settings: 'FILL' 1;
}
.brand-text {
  font-size: 1.25rem;
  font-weight: 700;
  font-family: 'IBM Plex Sans', 'Plus Jakarta Sans', sans-serif;
  letter-spacing: -0.025em;
}

.top-navbar-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-profile {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 2.75rem;
  padding: 0.375rem 0.625rem 0.375rem 0.375rem;
  border: 1px solid #dbe4f0;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.92);
  color: #0f172a;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.user-profile:hover {
  border-color: var(--primary-border);
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.user-profile:focus-visible {
  outline: 2px solid var(--primary-border);
  outline-offset: 2px;
}

.user-profile__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.user-profile__meta strong {
  max-width: 11rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
  font-weight: 700;
}

.user-profile__meta span {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 600;
}

.user-profile__arrow {
  color: #94a3b8;
  font-size: 1rem;
}

.avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background-color: var(--primary-light);
  color: var(--primary-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  border: 1px solid var(--primary-border);
}

.logout-btn {
  margin-left: 0.5rem;
}

/* Body Layout */
.layout-body {
  display: flex;
  flex: 1;
}

/* Sidebar */
.sidebar {
  display: none;
}
@media (min-width: 1024px) {
  .sidebar {
    display: flex;
    flex-direction: column;
    width: 16rem;
    position: fixed;
    top: 4rem; /* below header */
    bottom: 0;
    left: 0;
    background-color: #f8fafc;
    border-right: 1px solid #e2e8f0;
    padding: 1.5rem 0;
    z-index: 40;
  }
}

@media (min-width: 900px) {
  .layout-body:has(.workbench-page--locked) {
    height: calc(100vh - 4rem);
    min-height: 0;
  }

  .sidebar {
    display: flex;
    flex-direction: column;
    width: 15rem;
    position: fixed;
    top: 4rem;
    bottom: 0;
    left: 0;
    background-color: #f8fafc;
    border-right: 1px solid #e2e8f0;
    padding: 1.25rem 0;
    z-index: 40;
  }

  .main-content {
    margin-left: 15rem;
    padding: 1.5rem 1.75rem 2rem;
  }
}

.sidebar-org {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0 1.5rem;
  margin-bottom: 2rem;
}
.org-icon {
  width: 2rem;
  height: 2rem;
  background-color: var(--primary-color);
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}
.org-icon .material-symbols-outlined {
  font-size: 1.125rem;
}
.org-name {
  font-size: 1.125rem;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.2;
}
.org-term {
  font-size: 0.625rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
  font-weight: 700;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0 0.75rem;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  color: #475569;
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  border-radius: 0.5rem;
  transition: all 0.2s;
}
.nav-item:hover {
  background-color: #f1f5f9;
  transform: translateX(4px);
}
.nav-item .material-symbols-outlined {
  color: #94a3b8;
  font-size: 1.25rem;
  transition: color 0.2s;
}

.nav-active {
  background-color: var(--primary-light) !important;
  color: var(--primary-color) !important;
  border-right: 4px solid var(--primary-color);
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}
.nav-active .material-symbols-outlined {
  color: var(--primary-color);
}

.sidebar-footer {
  margin-top: auto;
  padding: 0 0.75rem;
  border-top: 1px solid #e2e8f0;
  padding-top: 1rem;
}

/* Main Content */
.main-content {
  flex: 1;
  padding: 1.5rem 1.75rem 2rem;
  padding-bottom: 6.5rem;
  min-height: 0;
}
@media (min-width: 1024px) {
  .main-content {
    margin-left: 16rem;
    padding: 1.75rem 2rem 2.25rem;
  }
}

.main-content__shell {
  width: 100%;
  max-width: 1380px;
  margin: 0 auto;
}

.main-content__shell :deep(.workbench-page--locked) {
  height: 100%;
  min-height: 0;
}

@media (min-width: 900px) {
  .main-content:has(.workbench-page--locked) {
    display: flex;
    flex-direction: column;
    height: calc(100vh - 4rem);
    min-height: 0;
    box-sizing: border-box;
    padding-bottom: 2rem;
  }

  .layout-body,
  .main-content,
  .main-content__shell {
    min-height: 0;
  }

  .layout-body:has(.workbench-page--locked),
  .main-content:has(.workbench-page--locked) {
    overflow: hidden;
  }

  .main-content__shell:has(.workbench-page--locked) {
    display: flex;
    flex: 1;
    min-height: 0;
    height: 100%;
    overflow: hidden;
  }
}

@media (max-width: 640px) {
  .top-navbar {
    padding: 0 1rem;
  }

  .brand-text {
    font-size: 1.05rem;
  }

  .user-profile {
    min-width: auto;
    padding-right: 0.45rem;
  }

  .user-profile__meta,
  .user-profile__arrow {
    display: none;
  }

  .top-navbar-right {
    gap: 0.5rem;
  }
}

.mobile-nav {
  position: sticky;
  bottom: 0;
  z-index: 45;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.25rem;
  padding: 0.6rem 0.75rem calc(0.6rem + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid #e2e8f0;
  background: rgba(248, 250, 252, 0.96);
  backdrop-filter: blur(12px);
}

.mobile-nav__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
  min-height: 3.5rem;
  border-radius: 0.9rem;
  color: #64748b;
  text-decoration: none;
  font-size: 0.7rem;
  font-weight: 600;
}

.mobile-nav__item .material-symbols-outlined {
  font-size: 1.3rem;
}

.mobile-nav__item--active {
  background: var(--primary-light);
  color: var(--primary-color);
}

@media (min-width: 900px) {
  .main-content {
    padding-bottom: 2rem;
  }

  .mobile-nav {
    display: none;
  }
}

@media (min-width: 1440px) {
  .main-content__shell {
    max-width: 1320px;
  }
}

@media (min-width: 1680px) {
  .main-content__shell {
    max-width: 1280px;
  }
}
</style>
