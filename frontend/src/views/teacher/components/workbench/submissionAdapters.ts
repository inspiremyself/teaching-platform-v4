import { scoringStrategyOf, type ScoreLineItem, type ScoringSummary } from '../scoringStrategies'
import { resolveStatusPresentation } from '@/components/ui/statusPresentation'
import type { QueueItemVM, WorkbenchDomain } from './types'

type AdapterRecord = Record<string, unknown>

const numberOrNull = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null

const stringOrNull = (value: unknown): string | null =>
  typeof value === 'string' && value.length > 0 ? value : null

const statusOf = (status: string, domain: WorkbenchDomain) => resolveStatusPresentation({
  module: 'workbench',
  kind: domain === 'EXAM' ? 'examSubmission' : 'submission',
  scene: 'teacher',
  status,
  unknownLabel: '未知',
})

export interface SubmissionAdapter {
  adapt(raw: any, options: { draftFlag?: boolean }): QueueItemVM
}

function homeworkScores(raw: AdapterRecord): { objective: number | null; manual: number | null } {
  const questions = Array.isArray(raw.questions) ? (raw.questions as AdapterRecord[]) : null
  if (!questions) return { objective: null, manual: null }
  const strategy = scoringStrategyOf('HOMEWORK')
  const lines: ScoreLineItem[] = questions.map(item => strategy.adapt(item))
  const objective = lines
    .filter(item => item.kind === 'AUTO')
    .reduce((sum, item) => sum + (item.score ?? item.autoScore ?? 0), 0)
  const manual = lines
    .filter(item => item.kind === 'MANUAL')
    .reduce((sum, item) => sum + (item.score ?? 0), 0)
  return { objective, manual }
}

function adaptBase(raw: AdapterRecord, domain: WorkbenchDomain, draftFlag: boolean): QueueItemVM {
  const status =
    domain === 'EXAM'
      ? stringOrNull(raw.status) ?? ''
      : stringOrNull(raw.submitStatus) ?? 'SAVED'
  const scores = domain === 'HOMEWORK' ? homeworkScores(raw) : { objective: null, manual: null }

  return {
    id: (raw.submissionId ?? raw.id ?? '') as number | string,
    studentName: stringOrNull(raw.studentName) ?? stringOrNull(raw.studentUsername) ?? '未知学生',
    status,
    statusLabel: status ? statusOf(status, domain).label : '未知',
    objectiveScore:
      domain === 'EXAM' ? numberOrNull(raw.autoScore) : scores.objective,
    manualScore:
      domain === 'EXAM' ? numberOrNull(raw.manualScore) : scores.manual,
    totalScore: numberOrNull(raw.totalScore),
    draftFlag,
  }
}

export function submissionAdapterOf(domain: WorkbenchDomain): SubmissionAdapter {
  return {
    adapt(raw: any, options: { draftFlag?: boolean }): QueueItemVM {
      if (!raw || typeof raw !== 'object') {
        return {
          id: '',
          studentName: '',
          status: '',
          statusLabel: '未知',
          objectiveScore: null,
          manualScore: null,
          totalScore: null,
          draftFlag: false,
        }
      }
      return adaptBase(raw as AdapterRecord, domain, options.draftFlag ?? false)
    },
  }
}

export function backfillQueueScores(
  items: QueueItemVM[],
  id: number | string,
  summary: ScoringSummary,
): QueueItemVM[] {
  let changed = false
  const next = items.map(item => {
    if (item.id !== id) return item
    changed = true
    return { ...item, objectiveScore: summary.autoScore, manualScore: summary.manualScore }
  })
  return changed ? next : items
}
