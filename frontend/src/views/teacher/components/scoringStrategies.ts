/**
 * 三域评分展示策略（Strategy + Adapter）。
 * 每个域只在这里定义「主观题判定 / 字段归一 / 只读状态 / 无主观题文案」，
 * 页面与 Facade 使用统一的 ScoreLineItem 与 buildScoringSummary。
 */

export type ScoringDomain = 'EXAM' | 'HOMEWORK' | 'LAB'
export type ScoreLineKind = 'AUTO' | 'MANUAL' | 'REFERENCE'

export interface ScoreLineItem {
  key: string
  kind: ScoreLineKind
  questionType: string | null
  score: number | null
  autoScore: number | null
  suggestedScore: number | null
  scoreSource: string | null
  judgeDetail: string | null
  maxScore: number | null
  teacherComment: string
}

export interface ScoringDomainStrategy {
  adapt(raw: any): ScoreLineItem
  isReadOnly(status: string | null | undefined): boolean
  noManualText: string
}

export interface ScoringSummary {
  autoScore: number
  manualScore: number
  totalScore: number
  completed: number
  total: number
  percent: number
  hasManual: boolean
  noManualText: string
}

const numberOrNull = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null

const stringOrNull = (value: unknown): string | null =>
  typeof value === 'string' && value.length > 0 ? value : null

const firstString = (...values: unknown[]): string => {
  for (const value of values) {
    const text = stringOrNull(value)
    if (text) return text
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  }
  return ''
}

function adaptExam(raw: any): ScoreLineItem {
  const manual = raw?.isCorrect === null || raw?.isCorrect === undefined
  return {
    key: firstString(raw?.questionId),
    kind: manual ? 'MANUAL' : 'AUTO',
    questionType: stringOrNull(raw?.questionType),
    score: numberOrNull(raw?.score),
    autoScore: numberOrNull(raw?.autoScore),
    suggestedScore: numberOrNull(raw?.suggestedScore),
    scoreSource: stringOrNull(raw?.scoreSource) ?? (manual ? 'TEACHER' : 'AUTO'),
    judgeDetail: stringOrNull(raw?.judgeDetail),
    maxScore: numberOrNull(raw?.questionScore),
    teacherComment: stringOrNull(raw?.teacherComment) ?? '',
  }
}

function adaptHomework(raw: any): ScoreLineItem {
  const answer = raw?.answer ?? raw
  const question = raw?.question ?? {}
  const source = stringOrNull(answer?.scoreSource) ?? stringOrNull(raw?.scoreSource)
  let kind: ScoreLineKind
  if (source === 'TEACHER') kind = 'MANUAL'
  else if (source === 'AUTO' || source === 'AUTO_ACCEPTED') kind = 'AUTO'
  else if (source === 'SUGGESTED' || source === 'RECOMMENDED') kind = 'REFERENCE'
  else if (raw?.isCorrect === null && answer?.score !== null && answer?.score !== undefined) kind = 'MANUAL'
  else kind = 'REFERENCE'
  return {
    key: firstString(answer?.id, question?.id, answer?.answerId, raw?.id, raw?.questionId),
    kind,
    questionType: stringOrNull(question?.type) ?? stringOrNull(raw?.questionType),
    score: numberOrNull(answer?.score) ?? numberOrNull(raw?.score),
    autoScore: numberOrNull(answer?.autoScore) ?? numberOrNull(raw?.autoScore),
    suggestedScore: numberOrNull(answer?.suggestedScore) ?? numberOrNull(raw?.suggestedScore),
    scoreSource: source,
    judgeDetail: stringOrNull(answer?.judgeDetail) ?? stringOrNull(raw?.judgeDetail),
    maxScore: numberOrNull(question?.score) ?? numberOrNull(raw?.maxScore),
    teacherComment: stringOrNull(answer?.teacherComment) ?? stringOrNull(raw?.teacherComment) ?? '',
  }
}

function adaptLab(raw: any): ScoreLineItem {
  const source = stringOrNull(raw?.scoreSource)
  let kind: ScoreLineKind
  if (source === 'TEACHER') kind = 'MANUAL'
  else if (source === 'AUTO' || source === 'AUTO_ACCEPTED') kind = 'AUTO'
  else kind = 'REFERENCE'
  return {
    key: firstString(raw?.answerId, raw?.id, raw?.stepId, raw?.questionId),
    kind,
    questionType: stringOrNull(raw?.questionType),
    score: numberOrNull(raw?.score),
    autoScore: numberOrNull(raw?.autoScore),
    suggestedScore: numberOrNull(raw?.suggestedScore),
    scoreSource: source,
    judgeDetail: stringOrNull(raw?.autoJudgeDetail) ?? stringOrNull(raw?.judgeDetail),
    maxScore: numberOrNull(raw?.maxScore) ?? numberOrNull(raw?.stepScore),
    teacherComment: stringOrNull(raw?.teacherComment) ?? '',
  }
}

const EXAM_STRATEGY: ScoringDomainStrategy = {
  adapt: adaptExam,
  isReadOnly: status => status === 'GRADED' || status === 'IN_PROGRESS',
  noManualText: '本卷无主观题，系统已完成判分',
}

const HOMEWORK_STRATEGY: ScoringDomainStrategy = {
  adapt: adaptHomework,
  isReadOnly: status => status === 'SAVED' || status === 'GRADED',
  noManualText: '本卷无主观题，系统已完成判分',
}

const LAB_STRATEGY: ScoringDomainStrategy = {
  adapt: adaptLab,
  isReadOnly: status => status === 'SAVED' || status === 'GRADED',
  noManualText: '本卷无需人工评分，系统已完成判分',
}

const STRATEGIES: Record<ScoringDomain, ScoringDomainStrategy> = {
  EXAM: EXAM_STRATEGY,
  HOMEWORK: HOMEWORK_STRATEGY,
  LAB: LAB_STRATEGY,
}

export function scoringStrategyOf(domain: ScoringDomain): ScoringDomainStrategy {
  return STRATEGIES[domain]
}

export function buildScoringSummary(
  domain: ScoringDomain,
  items: readonly ScoreLineItem[],
  draft: { scores: Record<string, number | null>; marked: string[] },
): ScoringSummary {
  const autoScore = items
    .filter(item => item.kind === 'AUTO')
    .reduce((sum, item) => sum + (item.score ?? item.autoScore ?? 0), 0)

  const manualItems = items.filter(item => item.kind === 'MANUAL')
  const manualScore = manualItems
    .filter(item => draft.marked.includes(item.key))
    .reduce((sum, item) => sum + (draft.scores[item.key] ?? 0), 0)

  const total = manualItems.length
  const completed = manualItems.filter(item => draft.marked.includes(item.key)).length
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100)

  return {
    autoScore,
    manualScore,
    totalScore: autoScore + manualScore,
    completed,
    total,
    percent,
    hasManual: total > 0,
    noManualText: scoringStrategyOf(domain).noManualText,
  }
}
