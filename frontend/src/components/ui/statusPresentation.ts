import {
  ACTIVITY_STATUS,
  EXAM_SUBMISSION_STATUS,
  SUBMISSION_STATUS,
} from '@/types/enums'

/** 状态所属模块；同一状态在不同模块下允许不同文案与颜色 */
export type StatusModule = 'lab' | 'homework' | 'exam' | 'workbench'
/** 状态枚举族：activity → ActivityStatus，submission → SubmissionStatus（LabReportStatus 为别名），examSubmission → ExamSubmissionStatus */
export type StatusKind = 'activity' | 'submission' | 'examSubmission'
/** 展示视角 */
export type StatusScene = 'teacher' | 'student'
/** 展示色板语义 */
export type StatusTone = 'neutral' | 'success' | 'info' | 'warning' | 'danger'

export interface StatusPresentation {
  label: string
  tone: StatusTone
}

export interface StatusPresentationInput {
  module: StatusModule
  kind: StatusKind
  scene: StatusScene
  status: string
  /** 仅用于保留现网「未知非空状态」兜底；未提供时返回原值 */
  unknownLabel?: string
}

type StatusTable = Record<string, Record<string, StatusPresentation>>

/** 模块无关基表：key 为 `kind:scene`，内层 key 为状态值 */
const BASE: StatusTable = {
  'activity:teacher': {
    [ACTIVITY_STATUS.DRAFT]: { label: '草稿', tone: 'warning' },
    [ACTIVITY_STATUS.PUBLISHED]: { label: '已发布', tone: 'success' },
    [ACTIVITY_STATUS.CLOSED]: { label: '已关闭', tone: 'info' },
  },
  'activity:student': {
    [ACTIVITY_STATUS.DRAFT]: { label: '草稿', tone: 'warning' },
    [ACTIVITY_STATUS.PUBLISHED]: { label: '已发布', tone: 'success' },
    [ACTIVITY_STATUS.CLOSED]: { label: '已关闭', tone: 'info' },
  },
  'submission:teacher': {
    [SUBMISSION_STATUS.SAVED]: { label: '已保存', tone: 'info' },
    [SUBMISSION_STATUS.SUBMITTED]: { label: '已提交', tone: 'warning' },
    [SUBMISSION_STATUS.GRADED]: { label: '已批改', tone: 'success' },
  },
  'submission:student': {
    [SUBMISSION_STATUS.SAVED]: { label: '已保存', tone: 'info' },
    [SUBMISSION_STATUS.SUBMITTED]: { label: '已提交', tone: 'warning' },
    [SUBMISSION_STATUS.GRADED]: { label: '已批改', tone: 'success' },
  },
  'examSubmission:teacher': {
    [EXAM_SUBMISSION_STATUS.IN_PROGRESS]: { label: '进行中', tone: 'info' },
    [EXAM_SUBMISSION_STATUS.SUBMITTED]: { label: '待评分', tone: 'warning' },
    [EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED]: { label: '超时提交', tone: 'danger' },
    [EXAM_SUBMISSION_STATUS.GRADED]: { label: '已评分', tone: 'success' },
  },
  'examSubmission:student': {
    [EXAM_SUBMISSION_STATUS.IN_PROGRESS]: { label: '进行中', tone: 'info' },
    [EXAM_SUBMISSION_STATUS.SUBMITTED]: { label: '已提交', tone: 'warning' },
    [EXAM_SUBMISSION_STATUS.AUTO_SUBMITTED]: { label: '自动提交', tone: 'danger' },
    [EXAM_SUBMISSION_STATUS.GRADED]: { label: '已批改', tone: 'success' },
  },
}

/** 模块覆盖表：key 为 `module:kind:scene`，只登记与基表不同的条目 */
const OVERRIDES: StatusTable = {
  'homework:activity:student': {
    [ACTIVITY_STATUS.PUBLISHED]: { label: '进行中', tone: 'success' },
  },
  'lab:submission:student': {
    [SUBMISSION_STATUS.SAVED]: { label: '已保存草稿', tone: 'info' },
  },
  'homework:submission:teacher': {
    [SUBMISSION_STATUS.SUBMITTED]: { label: '待批改', tone: 'warning' },
  },
  'workbench:submission:teacher': {
    [SUBMISSION_STATUS.SAVED]: { label: '草稿', tone: 'info' },
    [SUBMISSION_STATUS.SUBMITTED]: { label: '待评分', tone: 'warning' },
    [SUBMISSION_STATUS.GRADED]: { label: '已评分', tone: 'success' },
  },
}

/**
 * 解析状态展示：先查模块覆盖表，再查模块无关基表；
 * 都未命中时返回 unknownLabel（若提供）或原状态值，并统一落 neutral。
 */
export function resolveStatusPresentation(input: StatusPresentationInput): StatusPresentation {
  const { module, kind, scene, status, unknownLabel } = input
  const override = OVERRIDES[`${module}:${kind}:${scene}`]?.[status]
  if (override) {
    return override
  }
  const base = BASE[`${kind}:${scene}`]?.[status]
  if (base) {
    return base
  }
  return { label: unknownLabel ?? status, tone: 'neutral' }
}

/**
 * 非枚举族状态分组：key 为页面语义，不参与 StatusKind 体系。
 * 用于组织、平台内容、评分来源、行为风险、实验时间门禁、编辑态、小结要求、重判判定等直接登记状态。
 */
export type StatusGroup =
  | 'orgSchoolStatus'
  | 'orgTeacherStatus'
  | 'orgStudentStatus'
  | 'orgClassStatus'
  | 'orgSubjectStatus'
  | 'platformSubjectStatus'
  | 'coursewareStatus'
  | 'scoreSource'
  | 'behaviorRisk'
  | 'labTimeGate'
  | 'labEditMode'
  | 'labSummaryRequirement'
  | 'regradeDecision'

/** 直接登记表：按数据库真实取值写死，与枚举族基表互不影响 */
const DIRECT: Record<StatusGroup, Record<string, StatusPresentation>> = {
  orgSchoolStatus: {
    ACTIVE: { label: '启用', tone: 'success' },
    DISABLED: { label: '停用', tone: 'info' },
  },
  orgTeacherStatus: {
    ACTIVE: { label: '在职', tone: 'success' },
    DISABLED: { label: '停用', tone: 'info' },
  },
  orgStudentStatus: {
    ACTIVE: { label: '启用', tone: 'success' },
    DISABLED: { label: '停用', tone: 'info' },
  },
  orgClassStatus: {
    ACTIVE: { label: '启用中', tone: 'success' },
    ARCHIVED: { label: '已归档', tone: 'info' },
  },
  orgSubjectStatus: {
    ACTIVE: { label: '启用中', tone: 'success' },
    DISABLED: { label: '已停用', tone: 'info' },
  },
  platformSubjectStatus: {
    ACTIVE: { label: '启用', tone: 'success' },
    DISABLED: { label: '停用', tone: 'info' },
  },
  coursewareStatus: {
    PUBLISHED: { label: '已发布', tone: 'success' },
    DRAFT: { label: '草稿', tone: 'info' },
  },
  scoreSource: {
    AUTO: { label: '自动评分', tone: 'success' },
    AUTO_ACCEPTED: { label: '已采纳自动分', tone: 'success' },
    SUGGESTED: { label: '建议评分', tone: 'warning' },
    RECOMMENDED: { label: '建议评分', tone: 'warning' },
    TEACHER: { label: '教师评分', tone: 'info' },
  },
  behaviorRisk: {
    HIGH: { label: '高风险', tone: 'danger' },
    MEDIUM: { label: '中风险', tone: 'warning' },
    LOW: { label: '低风险', tone: 'info' },
    NO_BEHAVIOR_DATA: { label: '无数据', tone: 'info' },
  },
  labTimeGate: {
    NOT_STARTED: { label: '未开始', tone: 'info' },
    OVERDUE: { label: '已截止', tone: 'warning' },
  },
  labEditMode: {
    READONLY: { label: '只读', tone: 'info' },
    EDITABLE: { label: '可编辑', tone: 'success' },
  },
  labSummaryRequirement: {
    REQUIRED: { label: '必填', tone: 'warning' },
    OPTIONAL: { label: '选填', tone: 'info' },
  },
  regradeDecision: {
    ACCEPTED: { label: '是', tone: 'success' },
    REJECTED: { label: '否', tone: 'info' },
  },
}

/**
 * 直接登记表解析：未命中返回 unknownLabel 或原值，tone 统一 neutral。
 * 与 resolveStatusPresentation 互补：枚举族状态继续走上者，非枚举族状态走本函数。
 */
export function resolveStatusOf(
  group: StatusGroup,
  value?: string | null,
  unknownLabel?: string,
): StatusPresentation {
  const hit = value ? DIRECT[group][value] : undefined
  return hit ?? { label: unknownLabel ?? (value ?? ''), tone: 'neutral' }
}
