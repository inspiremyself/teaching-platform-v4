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
/** 原生 el-tag 支持的 theme 五值 */
export type ElTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

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

/** 把展示色板转成原生 el-tag 的 theme 值；neutral 按调用页现网默认落 primary 或 info */
export function toElTagType(tone: StatusTone, neutralAs: Extract<ElTagType, 'primary' | 'info'>): ElTagType {
  return tone === 'neutral' ? neutralAs : tone
}
