import type { RouteLocationNormalizedLoaded, RouteLocationRaw } from 'vue-router'
import type { AppRole } from '@/layout/navigation'
import { resolveRoleView } from '@/layout/roleView'

/** 解析后的面包屑段：对外只暴露可直接渲染的静态地址。 */
export interface BreadcrumbSegment {
  label: string
  to?: RouteLocationRaw
}

/** 配置项：to 为函数，带参路由从当前路由取参。 */
export interface BreadcrumbConfigItem {
  label: string
  to?: (route: RouteLocationNormalizedLoaded) => RouteLocationRaw
}

export type BreadcrumbRouteName =
  | 'teacher-dashboard'
  | 'teacher-materials'
  | 'teacher-questions'
  | 'teacher-labs'
  | 'teacher-lab-steps'
  | 'teacher-lab-submission-overview'
  | 'teacher-lab-matrix'
  | 'teacher-lab-item-answers'
  | 'teacher-lab-blank-regrade-batch'
  | 'teacher-lab-blank-regrade'
  | 'teacher-lab-report-detail'
  | 'teacher-lab-report-batch-grade'
  | 'teacher-homeworks'
  | 'teacher-homework-submissions'
  | 'teacher-exams'
  | 'teacher-exam-results'
  | 'teacher-analysis'
  | 'teacher-student-lab-submissions'
  | 'teacher-lab-report-view'
  | 'student-dashboard'
  | 'student-materials'
  | 'student-content'
  | 'student-labs'
  | 'student-lab-detail'
  | 'student-lab-steps'
  | 'student-homeworks'
  | 'student-homework-detail'
  | 'student-exams'
  | 'student-exam-detail'
  | 'student-scores'
  | 'admin-dashboard'
  | 'admin-teachers'
  | 'admin-subjects'
  | 'admin-classes'
  | 'admin-students'
  | 'admin-audit-logs'
  | 'admin-school'
  | 'admin-schools'
  | 'admin-platform-content'
  | 'profile'

/** 40 条业务路由的面包屑配置（不含角色根；角色根由 resolveBreadcrumbTrail 注入）。 */
export const ROUTE_BREADCRUMBS: Record<BreadcrumbRouteName, readonly BreadcrumbConfigItem[]> = {
  // 教师端
  'teacher-dashboard': [{ label: '总览' }],
  'teacher-materials': [{ label: '教学资料' }],
  'teacher-questions': [{ label: '题库' }],
  'teacher-labs': [{ label: '实验' }],
  'teacher-lab-steps': [
    { label: '实验', to: () => '/teacher/labs' },
    { label: '实验步骤配置' },
  ],
  'teacher-lab-submission-overview': [
    { label: '实验', to: () => '/teacher/labs' },
    { label: '实验结果' },
  ],
  'teacher-lab-matrix': [
    { label: '实验', to: () => '/teacher/labs' },
    {
      label: '实验结果',
      to: route => ({ name: 'teacher-lab-submission-overview', params: { labId: route.params.labId } }),
    },
    { label: '班级总览' },
  ],
  'teacher-lab-item-answers': [
    { label: '实验', to: () => '/teacher/labs' },
    {
      label: '实验结果',
      to: route => ({ name: 'teacher-lab-submission-overview', params: { labId: route.params.labId } }),
    },
    { label: '子题答案墙' },
  ],
  'teacher-lab-blank-regrade-batch': [
    { label: '实验', to: () => '/teacher/labs' },
    {
      label: '实验结果',
      to: route => ({ name: 'teacher-lab-submission-overview', params: { labId: route.params.id } }),
    },
    { label: '批量重判' },
  ],
  'teacher-lab-blank-regrade': [
    { label: '实验', to: () => '/teacher/labs' },
    {
      label: '实验结果',
      to: route => ({ name: 'teacher-lab-submission-overview', params: { labId: route.params.id } }),
    },
    { label: '填空重判' },
  ],
  'teacher-lab-report-detail': [
    { label: '实验', to: () => '/teacher/labs' },
    { label: '实验评分台' },
  ],
  'teacher-lab-report-batch-grade': [
    { label: '实验', to: () => '/teacher/labs' },
    {
      label: '实验评分台',
      to: route => ({ name: 'teacher-lab-report-detail', params: { id: route.params.id } }),
    },
    { label: '批量快捷批改' },
  ],
  'teacher-homeworks': [{ label: '作业' }],
  'teacher-homework-submissions': [
    { label: '作业', to: () => '/teacher/homeworks' },
    { label: '作业批改' },
  ],
  'teacher-exams': [{ label: '考试' }],
  'teacher-exam-results': [
    { label: '考试', to: () => '/teacher/exams' },
    { label: '考试结果' },
  ],
  'teacher-analysis': [{ label: '分析报表' }],
  'teacher-student-lab-submissions': [
    { label: '实验', to: () => '/teacher/labs' },
    { label: '学生实验提交' },
  ],
  'teacher-lab-report-view': [
    { label: '实验', to: () => '/teacher/labs' },
    { label: '实验报告' },
  ],
  // 学生端
  'student-dashboard': [{ label: '总览' }],
  'student-materials': [{ label: '课程资料' }],
  'student-content': [{ label: '学习内容' }],
  'student-labs': [{ label: '实验任务' }],
  'student-lab-detail': [
    { label: '实验任务', to: () => '/student/labs' },
    { label: '实验详情' },
  ],
  'student-lab-steps': [
    { label: '实验任务', to: () => '/student/labs' },
    {
      label: '实验详情',
      to: route => ({ name: 'student-lab-detail', params: { id: route.params.id } }),
    },
    { label: '题项作答' },
  ],
  'student-homeworks': [{ label: '作业任务' }],
  'student-homework-detail': [
    { label: '作业任务', to: () => '/student/homeworks' },
    { label: '作业详情' },
  ],
  'student-exams': [{ label: '考试任务' }],
  'student-exam-detail': [
    { label: '考试任务', to: () => '/student/exams' },
    { label: '考试详情' },
  ],
  'student-scores': [{ label: '成绩反馈' }],
  // 校管端 / 平台超管
  'admin-dashboard': [{ label: '总览' }],
  'admin-teachers': [{ label: '教师管理' }],
  'admin-subjects': [{ label: '学科管理' }],
  'admin-classes': [{ label: '班级管理' }],
  'admin-students': [{ label: '学生管理' }],
  'admin-audit-logs': [{ label: '操作日志' }],
  'admin-school': [{ label: '学校信息' }],
  'admin-schools': [{ label: '学校管理' }],
  'admin-platform-content': [{ label: '平台内容' }],
  // 公共
  profile: [{ label: '个人设置' }],
}

/**
 * 组装最终面包屑：角色根 + 路由配置段。
 * - 路由未配置（登录页 / 重定向 / catch-all）→ 返回空数组，组件不渲染。
 * - 角色缺失 → 不注入角色根，避免把 STUDENT 文案错误显示给其他角色。
 */
export function resolveBreadcrumbTrail(
  route: RouteLocationNormalizedLoaded,
  role: AppRole | null | undefined,
  homePath: string,
): BreadcrumbSegment[] {
  const name = typeof route.name === 'string' ? route.name : null
  const config = name
    ? ROUTE_BREADCRUMBS[name as BreadcrumbRouteName]
    : undefined
  if (!config) {
    return []
  }
  const root: BreadcrumbSegment[] = role
    ? [{ label: resolveRoleView(role).brandText, to: homePath }]
    : []
  return [
    ...root,
    ...config.map(item => ({ label: item.label, to: item.to?.(route) })),
  ]
}
