import { describe, expect, it } from 'vitest'
import { resolveStatusOf, resolveStatusPresentation } from '@/components/ui/statusPresentation'

const resolve = (
  module: 'lab' | 'homework' | 'exam' | 'workbench',
  kind: 'activity' | 'submission' | 'examSubmission',
  scene: 'teacher' | 'student',
  status: string,
  unknownLabel?: string,
) => resolveStatusPresentation({ module, kind, scene, status, unknownLabel })

describe('resolveStatusPresentation 基表', () => {
  const activityRows: Array<[string, string, 'warning' | 'success' | 'info']> = [
    ['DRAFT', '草稿', 'warning'],
    ['PUBLISHED', '已发布', 'success'],
    ['CLOSED', '已关闭', 'info'],
  ]
  const submissionRows: Array<[string, string, 'info' | 'warning' | 'success']> = [
    ['SAVED', '已保存', 'info'],
    ['SUBMITTED', '已提交', 'warning'],
    ['GRADED', '已批改', 'success'],
  ]

  it.each(['teacher', 'student'] as const)('activity 三状态（%s）', scene => {
    for (const [status, label, tone] of activityRows) {
      expect(resolve('exam', 'activity', scene, status)).toEqual({ label, tone })
    }
  })

  it.each(['teacher', 'student'] as const)('submission 三状态（%s）', scene => {
    for (const [status, label, tone] of submissionRows) {
      expect(resolve('exam', 'submission', scene, status)).toEqual({ label, tone })
    }
  })

  it('examSubmission 教师四状态', () => {
    expect(resolve('exam', 'examSubmission', 'teacher', 'IN_PROGRESS')).toEqual({ label: '进行中', tone: 'info' })
    expect(resolve('exam', 'examSubmission', 'teacher', 'SUBMITTED')).toEqual({ label: '待评分', tone: 'warning' })
    expect(resolve('exam', 'examSubmission', 'teacher', 'AUTO_SUBMITTED')).toEqual({ label: '超时提交', tone: 'danger' })
    expect(resolve('exam', 'examSubmission', 'teacher', 'GRADED')).toEqual({ label: '已评分', tone: 'success' })
  })

  it('examSubmission 学生四状态', () => {
    expect(resolve('exam', 'examSubmission', 'student', 'IN_PROGRESS')).toEqual({ label: '进行中', tone: 'info' })
    expect(resolve('exam', 'examSubmission', 'student', 'SUBMITTED')).toEqual({ label: '已提交', tone: 'warning' })
    expect(resolve('exam', 'examSubmission', 'student', 'AUTO_SUBMITTED')).toEqual({ label: '自动提交', tone: 'danger' })
    expect(resolve('exam', 'examSubmission', 'student', 'GRADED')).toEqual({ label: '已批改', tone: 'success' })
  })
})

describe('resolveStatusPresentation 覆盖表', () => {
  it('homework/activity/student 的 PUBLISHED 为进行中', () => {
    expect(resolve('homework', 'activity', 'student', 'PUBLISHED')).toEqual({ label: '进行中', tone: 'success' })
  })

  it('lab/submission/student 的 SAVED 为已保存草稿', () => {
    expect(resolve('lab', 'submission', 'student', 'SAVED')).toEqual({ label: '已保存草稿', tone: 'info' })
  })

  it('homework/submission/teacher 的 SUBMITTED 为待批改', () => {
    expect(resolve('homework', 'submission', 'teacher', 'SUBMITTED')).toEqual({ label: '待批改', tone: 'warning' })
  })

  it('workbench/submission/teacher 三状态覆盖', () => {
    expect(resolve('workbench', 'submission', 'teacher', 'SAVED')).toEqual({ label: '草稿', tone: 'info' })
    expect(resolve('workbench', 'submission', 'teacher', 'SUBMITTED')).toEqual({ label: '待评分', tone: 'warning' })
    expect(resolve('workbench', 'submission', 'teacher', 'GRADED')).toEqual({ label: '已评分', tone: 'success' })
  })

  it('覆盖优先于基表：同状态在 workbench 与 homework 下不同', () => {
    expect(resolve('workbench', 'submission', 'teacher', 'SAVED').label).toBe('草稿')
    expect(resolve('homework', 'submission', 'teacher', 'SAVED').label).toBe('已保存')
  })

  it('场景差异：examSubmission SUBMITTED 教师待评分、学生已提交', () => {
    expect(resolve('exam', 'examSubmission', 'teacher', 'SUBMITTED')).toEqual({ label: '待评分', tone: 'warning' })
    expect(resolve('exam', 'examSubmission', 'student', 'SUBMITTED')).toEqual({ label: '已提交', tone: 'warning' })
  })
})

describe('resolveStatusPresentation 未命中', () => {
  it('未命中返回原值与 neutral', () => {
    expect(resolve('lab', 'submission', 'teacher', 'UNKNOWN')).toEqual({ label: 'UNKNOWN', tone: 'neutral' })
  })

  it('未命中且提供 unknownLabel 时返回兜底文案', () => {
    expect(resolve('workbench', 'activity', 'teacher', 'OTHER', '未知')).toEqual({ label: '未知', tone: 'neutral' })
  })
})

describe('resolveStatusOf 直接登记表', () => {
  const rows: Array<[Parameters<typeof resolveStatusOf>[0], string, string, string]> = [
    ['orgSchoolStatus', 'ACTIVE', '启用', 'success'],
    ['orgTeacherStatus', 'ACTIVE', '在职', 'success'],
    ['orgStudentStatus', 'DISABLED', '停用', 'info'],
    ['orgClassStatus', 'ARCHIVED', '已归档', 'info'],
    ['orgSubjectStatus', 'DISABLED', '已停用', 'info'],
    ['platformSubjectStatus', 'ACTIVE', '启用', 'success'],
    ['coursewareStatus', 'DRAFT', '草稿', 'info'],
    ['scoreSource', 'AUTO', '自动评分', 'success'],
    ['behaviorRisk', 'HIGH', '高风险', 'danger'],
    ['labTimeGate', 'OVERDUE', '已截止', 'warning'],
    ['labEditMode', 'READONLY', '只读', 'info'],
    ['labSummaryRequirement', 'REQUIRED', '必填', 'warning'],
    ['regradeDecision', 'ACCEPTED', '是', 'success'],
  ]

  it.each(rows)('%s 的 %s 命中 %s', (group, value, label, tone) => {
    expect(resolveStatusOf(group, value)).toEqual({ label, tone })
  })

  it('未命中回退原值并落 neutral', () => {
    expect(resolveStatusOf('orgSchoolStatus', 'UNKNOWN')).toEqual({ label: 'UNKNOWN', tone: 'neutral' })
  })

  it('未命中且提供 unknownLabel 时返回兜底文案', () => {
    expect(resolveStatusOf('scoreSource', 'UNKNOWN', '未标注')).toEqual({ label: '未标注', tone: 'neutral' })
  })

  it('null / 空输入落 neutral', () => {
    expect(resolveStatusOf('scoreSource', null, '未标注')).toEqual({ label: '未标注', tone: 'neutral' })
    expect(resolveStatusOf('scoreSource', undefined)).toEqual({ label: '', tone: 'neutral' })
  })
})
