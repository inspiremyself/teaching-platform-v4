export type AppRole = 'SCHOOL_ADMIN' | 'TEACHER' | 'STUDENT' | 'PLATFORM_ADMIN';

export interface NavItem {
  to: string;
  label: string;
  icon: string;
}

export const PROFILE_NAV_ITEM: NavItem = { to: '/profile', label: '个人设置', icon: 'settings' };

export const SIDEBAR_NAV: Record<AppRole, NavItem[]> = {
  SCHOOL_ADMIN: [
    { to: '/admin/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/admin/teachers', label: '教师管理', icon: 'badge' },
    { to: '/admin/subjects', label: '学科管理', icon: 'menu_book' },
    { to: '/admin/classes', label: '班级管理', icon: 'groups' },
    { to: '/admin/students', label: '学生管理', icon: 'person' },
    { to: '/admin/audit-logs', label: '操作日志', icon: 'history' },
    { to: '/admin/school', label: '学校信息', icon: 'domain' },
  ],
  TEACHER: [
    { to: '/teacher/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/teacher/materials', label: '教学资料', icon: 'folder' },
    { to: '/teacher/questions', label: '题库', icon: 'database' },
    { to: '/teacher/labs', label: '实验', icon: 'science' },
    { to: '/teacher/lab-reports', label: '实验报告', icon: 'history_edu' },
    { to: '/teacher/homeworks', label: '作业', icon: 'assignment' },
    { to: '/teacher/exams', label: '考试', icon: 'quiz' },
    { to: '/teacher/analysis', label: '分析报表', icon: 'analytics' },
    { to: '/teacher/plagiarism', label: '查重治理', icon: 'search' },
  ],
  STUDENT: [
    { to: '/student/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/student/materials', label: '课程资料', icon: 'menu_book' },
    { to: '/student/labs', label: '实验任务', icon: 'science' },
    { to: '/student/homeworks', label: '作业任务', icon: 'assignment_turned_in' },
    { to: '/student/exams', label: '考试任务', icon: 'quiz' },
    { to: '/student/scores', label: '成绩反馈', icon: 'grade' },
  ],
  PLATFORM_ADMIN: [
    { to: '/admin/schools', label: '学校管理', icon: 'domain' },
    { to: '/admin/platform-content', label: '平台内容', icon: 'menu_book' },
  ],
};

export const MOBILE_NAV: Record<AppRole, NavItem[]> = {
  SCHOOL_ADMIN: [
    { to: '/admin/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/admin/teachers', label: '教师', icon: 'badge' },
    { to: '/admin/subjects', label: '学科', icon: 'menu_book' },
    { to: '/admin/classes', label: '班级', icon: 'groups' },
    { to: '/admin/students', label: '学生', icon: 'person' },
    { to: '/admin/audit-logs', label: '日志', icon: 'history' },
    { to: '/profile', label: '我的', icon: 'settings' },
  ],
  TEACHER: [
    { to: '/teacher/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/teacher/labs', label: '实验', icon: 'science' },
    { to: '/teacher/homeworks', label: '作业', icon: 'assignment' },
    { to: '/teacher/exams', label: '考试', icon: 'quiz' },
    { to: '/profile', label: '我的', icon: 'person' },
  ],
  STUDENT: [
    { to: '/student/dashboard', label: '总览', icon: 'dashboard' },
    { to: '/student/materials', label: '资料', icon: 'menu_book' },
    { to: '/student/labs', label: '实验', icon: 'science' },
    { to: '/student/homeworks', label: '作业', icon: 'assignment_turned_in' },
    { to: '/profile', label: '我的', icon: 'person' },
  ],
  PLATFORM_ADMIN: [
    { to: '/admin/schools', label: '学校管理', icon: 'domain' },
    { to: '/admin/platform-content', label: '平台内容', icon: 'menu_book' },
  ],
};
