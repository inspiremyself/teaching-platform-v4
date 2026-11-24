import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ApiError, AUTH_FAILURE_CODES } from '@/utils/request';
import Layout from '@/layout/index.vue';
import type { AppRole } from '@/layout/navigation';
import { shouldRedirectToHome } from './guardRules';
import { ROUTE_BREADCRUMBS } from './breadcrumb';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/auth/Login.vue'), meta: { public: true, title: '登录' } },
    {
      path: '/',
      component: Layout,
      redirect: () => {
        const authStore = useAuthStore();
        return authStore.homePath;
      },
      children: [
        { path: 'profile', name: 'profile', component: () => import('@/views/common/Profile.vue'), meta: { title: '个人中心', breadcrumb: ROUTE_BREADCRUMBS['profile'] } }
      ]
    },
    {
      path: '/teacher',
      component: Layout,
      meta: { roles: ['TEACHER'] },
      redirect: '/teacher/dashboard',
      children: [
        { path: 'classes', redirect: '/teacher/dashboard' },
        { path: 'students', redirect: '/teacher/dashboard' },
        { path: 'dashboard', name: 'teacher-dashboard', component: () => import('@/views/teacher/Dashboard.vue'), meta: { title: '教师工作台', breadcrumb: ROUTE_BREADCRUMBS['teacher-dashboard'] } },
        { path: 'students/:studentId/lab-submissions', name: 'teacher-student-lab-submissions', component: () => import('@/views/teacher/StudentLabSubmissions.vue'), meta: { title: '学生实验提交', breadcrumb: ROUTE_BREADCRUMBS['teacher-student-lab-submissions'] } },
        { path: 'materials', name: 'teacher-materials', component: () => import('@/views/teacher/Materials.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-materials'] } },
        { path: 'questions', name: 'teacher-questions', component: () => import('@/views/teacher/Questions.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-questions'] } },
        { path: 'labs', name: 'teacher-labs', component: () => import('@/views/teacher/Labs.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-labs'] } },
        { path: 'labs/:id/steps', name: 'teacher-lab-steps', component: () => import('@/views/teacher/LabSteps.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-steps'] } },
        { path: 'labs/:labId/submission-overview', name: 'teacher-lab-submission-overview', component: () => import('@/views/teacher/LabGradingWorkspace.vue'), meta: { title: '实验结果', breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-submission-overview'] } },
        { path: 'labs/:labId/matrix', name: 'teacher-lab-matrix', component: () => import('@/views/teacher/LabSubmissionOverview.vue'), meta: { title: '实验班级总览', breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-matrix'] } },
        { path: 'labs/:labId/items/:itemId/answers', name: 'teacher-lab-item-answers', component: () => import('@/views/teacher/LabItemAnswersWall.vue'), meta: { title: '子题答案墙', breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-item-answers'] } },
        { path: 'labs/:id/blank-regrade/batch', name: 'teacher-lab-blank-regrade-batch', component: () => import('@/views/teacher/LabBlankRegradeBatch.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-blank-regrade-batch'] } },
        { path: 'labs/:id/blank-regrade', name: 'teacher-lab-blank-regrade', component: () => import('@/views/teacher/LabBlankRegrade.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-blank-regrade'] } },
        { path: 'lab-reports/:id/batch-grade', name: 'teacher-lab-report-batch-grade', component: () => import('@/views/teacher/LabReportBatchGrade.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-report-batch-grade'] } },
        { path: 'lab-reports/:id', name: 'teacher-lab-report-detail', component: () => import('@/views/teacher/LabReportDetail.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-report-detail'] } },
        { path: 'homeworks', name: 'teacher-homeworks', component: () => import('@/views/teacher/Homeworks.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-homeworks'] } },
        { path: 'homeworks/:id/submissions', name: 'teacher-homework-submissions', component: () => import('@/views/teacher/HomeworkSubmissions.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-homework-submissions'] } },
        { path: 'exams', name: 'teacher-exams', component: () => import('@/views/teacher/exams/List.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-exams'] } },
        { path: 'exams/:id/results', name: 'teacher-exam-results', component: () => import('@/views/teacher/exams/Results.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-exam-results'] } },
        { path: 'analysis', name: 'teacher-analysis', component: () => import('@/views/teacher/Analysis.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['teacher-analysis'] } }
      ]
    },
    {
      path: '/teacher/labs/:labId/report/:studentId',
      name: 'teacher-lab-report-view',
      component: () => import('@/views/teacher/LabReportViewPage.vue'),
      meta: { roles: ['TEACHER'], title: '实验报告', breadcrumb: ROUTE_BREADCRUMBS['teacher-lab-report-view'] }
    },
    {
      path: '/student',
      component: Layout,
      meta: { roles: ['STUDENT'] },
      redirect: '/student/dashboard',
      children: [
        { path: 'dashboard', name: 'student-dashboard', component: () => import('@/views/student/Dashboard.vue'), meta: { title: '学生工作台', breadcrumb: ROUTE_BREADCRUMBS['student-dashboard'] } },
        { path: 'materials', name: 'student-materials', component: () => import('@/views/student/Materials.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-materials'] } },
        { path: 'content', name: 'student-content', component: () => import('@/views/student/Content.vue'), meta: { title: '学习内容', roles: ['STUDENT'], breadcrumb: ROUTE_BREADCRUMBS['student-content'] } },
        { path: 'labs', name: 'student-labs', component: () => import('@/views/student/Labs.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-labs'] } },
        { path: 'labs/:id', name: 'student-lab-detail', component: () => import('@/views/student/LabDetail.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-lab-detail'] } },
        { path: 'labs/:id/steps', name: 'student-lab-steps', component: () => import('@/views/student/LabSteps.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-lab-steps'] } },
        { path: 'homeworks', name: 'student-homeworks', component: () => import('@/views/student/Homeworks.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-homeworks'] } },
        { path: 'homeworks/:id', name: 'student-homework-detail', component: () => import('@/views/student/HomeworkDetail.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-homework-detail'] } },
        { path: 'exams', name: 'student-exams', component: () => import('@/views/student/exams/List.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-exams'] } },
        { path: 'exams/:id', name: 'student-exam-detail', component: () => import('@/views/student/exams/Detail.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-exam-detail'] } },
        { path: 'scores', name: 'student-scores', component: () => import('@/views/student/Scores.vue'), meta: { breadcrumb: ROUTE_BREADCRUMBS['student-scores'] } }
      ]
    },
    {
      path: '/admin',
      component: Layout,
      meta: { roles: ['SCHOOL_ADMIN', 'PLATFORM_ADMIN'] },
      redirect: () => useAuthStore().homePath,
      children: [
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '校管总览', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-dashboard'] } },
        { path: 'teachers', name: 'admin-teachers', component: () => import('@/views/admin/Teachers.vue'), meta: { title: '教师管理', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-teachers'] } },
        { path: 'subjects', name: 'admin-subjects', component: () => import('@/views/admin/Subjects.vue'), meta: { title: '学科管理', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-subjects'] } },
        { path: 'classes', name: 'admin-classes', component: () => import('@/views/admin/Classes.vue'), meta: { title: '班级管理', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-classes'] } },
        { path: 'students', name: 'admin-students', component: () => import('@/views/admin/Students.vue'), meta: { title: '学生管理', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-students'] } },
        { path: 'audit-logs', name: 'admin-audit-logs', component: () => import('@/views/admin/AuditLogs.vue'), meta: { title: '操作日志', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-audit-logs'] } },
        { path: 'school', name: 'admin-school', component: () => import('@/views/admin/School.vue'), meta: { title: '学校信息', roles: ['SCHOOL_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-school'] } },
        { path: 'schools', name: 'admin-schools', component: () => import('@/views/admin/Schools.vue'), meta: { title: '学校管理', roles: ['PLATFORM_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-schools'] } },
        { path: 'platform-content', name: 'admin-platform-content', component: () => import('@/views/admin/PlatformContent.vue'), meta: { title: '平台内容', roles: ['PLATFORM_ADMIN'], breadcrumb: ROUTE_BREADCRUMBS['admin-platform-content'] } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});

router.beforeEach(async to => {
  const authStore = useAuthStore();

  if (to.meta.public) {
    if (authStore.token) {
      if (!authStore.user) {
        try {
          await authStore.fetchUserInfo();
        } catch (err) {
          if (err instanceof ApiError && AUTH_FAILURE_CODES.has(err.code)) {
            authStore.logout();
          }
          // 网络/超时/5xx：保留 token 与 user，停留在当前页
          return true;
        }
      }
      if (to.path === '/login' || to.path === '/') {
        return authStore.homePath;
      }
    }
    return true;
  }

  if (!authStore.token) {
    return '/login';
  }

  if (!authStore.user) {
    try {
      await authStore.fetchUserInfo();
    } catch (err) {
      if (err instanceof ApiError && AUTH_FAILURE_CODES.has(err.code)) {
        authStore.logout();
        return '/login';
      }
      // 网络/超时/5xx：保留 token 与 user，取消本次导航；提示由请求拦截器统一给出
      return false;
    }
  }

  const roles = to.meta.roles as AppRole[] | undefined;
  if (shouldRedirectToHome(roles, authStore.user?.role)) {
    return authStore.homePath;
  }

  if (to.path === '/') {
    return authStore.homePath;
  }

  return true;
});

export default router;
