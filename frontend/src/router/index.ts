import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import Layout from '@/layout/index.vue';

const redirectToRoleHome = (role?: string) => {
  if (role === 'TEACHER') {
    return '/teacher/dashboard';
  }
  if (role === 'STUDENT') {
    return '/student/dashboard';
  }
  return '/login';
};

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
        { path: 'profile', name: 'profile', component: () => import('@/views/common/Profile.vue'), meta: { title: '个人中心' } }
      ]
    },
    {
      path: '/teacher',
      component: Layout,
      meta: { role: 'TEACHER' },
      redirect: '/teacher/dashboard',
      children: [
        { path: 'dashboard', name: 'teacher-dashboard', component: () => import('@/views/teacher/Dashboard.vue'), meta: { title: '教师工作台' } },
        { path: 'classes', name: 'teacher-classes', component: () => import('@/views/teacher/Classes.vue') },
        { path: 'students', name: 'teacher-students', component: () => import('@/views/teacher/Students.vue') },
        { path: 'students/:studentId/lab-submissions', name: 'teacher-student-lab-submissions', component: () => import('@/views/teacher/StudentLabSubmissions.vue'), meta: { title: '学生实验提交' } },
        { path: 'materials', name: 'teacher-materials', component: () => import('@/views/teacher/Materials.vue') },
        { path: 'questions', name: 'teacher-questions', component: () => import('@/views/teacher/Questions.vue') },
        { path: 'labs', name: 'teacher-labs', component: () => import('@/views/teacher/Labs.vue') },
        { path: 'labs/:id/steps', name: 'teacher-lab-steps', component: () => import('@/views/teacher/LabSteps.vue') },
        { path: 'labs/:labId/submission-overview', name: 'teacher-lab-submission-overview', component: () => import('@/views/teacher/LabSubmissionOverview.vue'), meta: { title: '实验提交总览' } },
        { path: 'labs/:labId/items/:itemId/answers', name: 'teacher-lab-item-answers', component: () => import('@/views/teacher/LabItemAnswersWall.vue'), meta: { title: '子题答案墙' } },
        { path: 'labs/:id/blank-regrade/batch', name: 'teacher-lab-blank-regrade-batch', component: () => import('@/views/teacher/LabBlankRegradeBatch.vue') },
        { path: 'labs/:id/blank-regrade', name: 'teacher-lab-blank-regrade', component: () => import('@/views/teacher/LabBlankRegrade.vue') },
        { path: 'lab-reports', name: 'teacher-lab-reports', component: () => import('@/views/teacher/LabReports.vue') },
        { path: 'lab-reports/:id/batch-grade', name: 'teacher-lab-report-batch-grade', component: () => import('@/views/teacher/LabReportBatchGrade.vue') },
        { path: 'lab-reports/:id', name: 'teacher-lab-report-detail', component: () => import('@/views/teacher/LabReportDetail.vue') },
        { path: 'homeworks', name: 'teacher-homeworks', component: () => import('@/views/teacher/Homeworks.vue') },
        { path: 'homeworks/:id/submissions', name: 'teacher-homework-submissions', component: () => import('@/views/teacher/HomeworkSubmissions.vue') },
        { path: 'exams', name: 'teacher-exams', component: () => import('@/views/teacher/exams/List.vue') },
        { path: 'exams/:id/results', name: 'teacher-exam-results', component: () => import('@/views/teacher/exams/Results.vue') },
        { path: 'analysis', component: () => import('@/views/teacher/Analysis.vue') },
        { path: 'plagiarism', component: () => import('@/views/teacher/Plagiarism.vue') }
      ]
    },
    {
      path: '/teacher/labs/:labId/report/:studentId',
      name: 'teacher-lab-report-view',
      component: () => import('@/views/teacher/LabReportViewPage.vue'),
      meta: { role: 'TEACHER', title: '实验报告' }
    },
    {
      path: '/student',
      component: Layout,
      meta: { role: 'STUDENT' },
      redirect: '/student/dashboard',
      children: [
        { path: 'dashboard', name: 'student-dashboard', component: () => import('@/views/student/Dashboard.vue'), meta: { title: '学生工作台' } },
        { path: 'materials', name: 'student-materials', component: () => import('@/views/student/Materials.vue') },
        { path: 'labs', name: 'student-labs', component: () => import('@/views/student/Labs.vue') },
        { path: 'labs/:id', name: 'student-lab-detail', component: () => import('@/views/student/LabDetail.vue') },
        { path: 'labs/:id/steps', name: 'student-lab-steps', component: () => import('@/views/student/LabSteps.vue') },
        { path: 'homeworks', name: 'student-homeworks', component: () => import('@/views/student/Homeworks.vue') },
        { path: 'homeworks/:id', name: 'student-homework-detail', component: () => import('@/views/student/HomeworkDetail.vue') },
        { path: 'exams', name: 'student-exams', component: () => import('@/views/student/exams/List.vue') },
        { path: 'exams/:id', name: 'student-exam-detail', component: () => import('@/views/student/exams/Detail.vue') },
        { path: 'scores', component: () => import('@/views/student/Scores.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});

router.beforeEach(async to => {
  const authStore = useAuthStore();

  if (to.meta.public) {
    if (authStore.token && to.path === '/login') {
      return authStore.homePath;
    }
    return true;
  }

  if (!authStore.token) {
    return '/login';
  }

  if (!authStore.user) {
    try {
      await authStore.fetchUserInfo();
    } catch {
      authStore.logout();
      return '/login';
    }
  }

  if (to.meta.role && authStore.user?.role !== to.meta.role) {
    return redirectToRoleHome(authStore.user?.role);
  }

  if (to.path === '/') {
    return authStore.homePath;
  }

  return true;
});

export default router;
