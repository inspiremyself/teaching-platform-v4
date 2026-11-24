import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { ROUTE_BREADCRUMBS } from '@/router/breadcrumb'

const source = (relative: string): string =>
  readFileSync(new URL(relative, import.meta.url), 'utf8')

const routeSource = (): string => source('../../router/index.ts')

const FORBIDDEN_PREFIXES = ['教师控制台', '教师后台', '教学控制台', '教师实验工作台', '学生端']

const LEGACY_EYEBROWS: Array<[string, string]> = [
  ['../../views/teacher/components/DashboardOverviewPanel.vue', '教师控制台 / 工作台总览'],
  ['../../views/teacher/Materials.vue', '教师后台 / 资料管理'],
  ['../../views/teacher/Questions.vue', '教学控制台 / 题库资产'],
  ['../../views/teacher/components/ExamListPanel.vue', '教师控制台 / 考试闭环 / 考试管理'],
  ['../../views/teacher/components/LabListPanel.vue', '教师控制台 / 实验闭环 / 实验管理'],
  ['../../views/teacher/components/HomeworkListPanel.vue', '教师控制台 / 作业闭环 / 作业管理'],
  ['../../views/teacher/LabSubmissionOverview.vue', '教师控制台 / 实验闭环 / 班级总览'],
  ['../../views/teacher/Analysis.vue', '教师控制台 / 分析报表'],
  ['../../views/teacher/components/LabStepEditorPanel.vue', '教师实验工作台 / 步骤配置'],
  ['../../views/teacher/LabItemAnswersWall.vue', '教师后台 / 子题答案墙'],
  ['../../views/teacher/StudentLabSubmissions.vue', '教师后台 / 学生实验提交'],
  ['../../views/teacher/LabBlankRegrade.vue', '教师后台 / 填空重判'],
  ['../../views/teacher/LabBlankRegradeBatch.vue', '教师后台 / 填空批量重判'],
  ['../../views/teacher/components/workbench/GradingWorkspace.vue', '教师控制台 / 批改工作区'],
  ['../../views/student/Dashboard.vue', '学生空间 / 今日学习'],
  ['../../views/student/Materials.vue', '学生端 / 教学资料'],
  ['../../views/student/Labs.vue', '学生端 / 我的实验'],
  ['../../views/student/LabDetail.vue', '学生端 / 实验题目索引'],
  ['../../views/student/Homeworks.vue', '学生端 / 我的作业'],
  ['../../views/student/components/ScoresPanel.vue', '学生端 / 成绩总览'],
  ['../../views/student/exams/List.vue', '学生空间 / 考试任务'],
  ['../../views/student/components/LabStepAnswerPanel.vue', '学生端 / 实验题项作答'],
  ['../../views/admin/Dashboard.vue', '学校管理台 / 总览'],
  ['../../views/admin/Teachers.vue', '学校管理台 / 教师管理'],
  ['../../views/admin/Subjects.vue', '学校管理台 / 学科管理'],
  ['../../views/admin/Classes.vue', '学校管理台 / 班级管理'],
  ['../../views/admin/Students.vue', '学校管理台 / 学生管理'],
  ['../../views/admin/AuditLogs.vue', '学校管理台 / 操作日志'],
  ['../../views/admin/School.vue', '学校管理台 / 学校信息'],
  ['../../views/admin/Schools.vue', '平台管理台 / 学校管理'],
  ['../../views/common/Profile.vue', '账户中心 / 个人资料与安全'],
]

describe('ROUTE_BREADCRUMBS 配置完整性', () => {
  it('覆盖 40 条业务路由且每段数组非空', () => {
    const entries = Object.entries(ROUTE_BREADCRUMBS)
    expect(entries).toHaveLength(40)
    entries.forEach(([, items]) => expect(items.length).toBeGreaterThan(0))
  })

  it('末段无 to，其余段 to 为函数', () => {
    Object.values(ROUTE_BREADCRUMBS).forEach(items => {
      items.forEach((item, index) => {
        if (index === items.length - 1) {
          expect(item.to).toBeUndefined()
        } else {
          expect(typeof item.to).toBe('function')
        }
      })
    })
  })

  it('文案不含旧角色前缀', () => {
    Object.values(ROUTE_BREADCRUMBS).forEach(items => {
      items.forEach(item => {
        FORBIDDEN_PREFIXES.forEach(prefix => expect(item.label).not.toContain(prefix))
      })
    })
  })
})

describe('router/index.ts 接入', () => {
  it('40 条路由均引用配置表', () => {
    const router = routeSource()
    Object.keys(ROUTE_BREADCRUMBS).forEach(key => {
      expect(router).toContain(`breadcrumb: ROUTE_BREADCRUMBS['${key}']`)
    })
  })

  it('合并 meta 时保留原有 roles 与 title', () => {
    const router = routeSource()
    const count = (needle: string) => router.split(needle).length - 1
    expect(count("roles: ['SCHOOL_ADMIN']")).toBe(7)
    expect(count("roles: ['PLATFORM_ADMIN']")).toBe(2)
    expect(router).toContain("title: '实验报告'")
    expect(count("roles: ['TEACHER']")).toBeGreaterThanOrEqual(2)
    expect(router).toContain("roles: ['STUDENT'], breadcrumb: ROUTE_BREADCRUMBS['student-content']")
  })

  it('补齐 teacher-analysis 与 student-scores 命名', () => {
    const router = routeSource()
    expect(router).toContain("name: 'teacher-analysis'")
    expect(router).toContain("name: 'student-scores'")
  })
})

describe('旧页面级 eyebrow 清理', () => {
  it.each(LEGACY_EYEBROWS)('%s 不再含旧定位文案', (relative, text) => {
    expect(source(relative)).not.toContain(text)
  })
})
