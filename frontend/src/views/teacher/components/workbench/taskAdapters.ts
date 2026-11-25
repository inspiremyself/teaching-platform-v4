import type { DeadlineBucket, TaskFilterSnapshot, TaskRowVM, WorkbenchDomain } from './types'
import { resolveStatusPresentation } from '@/components/ui/statusPresentation'

type AdapterRecord = Record<string, unknown>

export interface TaskAdapter {
  adapt(raw: any): TaskRowVM
  filtersOf(raw: any): TaskFilterSnapshot
}

const statusOf = (status: string) => resolveStatusPresentation({ module: 'workbench', kind: 'activity', scene: 'teacher', status, unknownLabel: '未知' })

const numberOrNull = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null

const stringOrNull = (value: unknown): string | null =>
  typeof value === 'string' && value.length > 0 ? value : null

export function bucketOf(value: unknown): DeadlineBucket {
  const raw = stringOrNull(value)
  if (!raw) return 'UNDATED'
  const time = Date.parse(raw)
  if (!Number.isFinite(time)) return 'UNDATED'
  return time >= Date.now() ? 'UPCOMING' : 'OVERDUE'
}

function adaptBase(raw: AdapterRecord): TaskRowVM {
  const status = stringOrNull(raw.status) ?? ''
  return {
    id: numberOrNull(raw.id) ?? 0,
    title: stringOrNull(raw.title) ?? '',
    status,
    statusLabel: status ? statusOf(status).label : '未知',
    className: stringOrNull(raw.className) ?? '未分配班级',
    startAt: stringOrNull(raw.startAt),
    endAt: stringOrNull(raw.endAt) ?? stringOrNull(raw.dueAt),
    submissionCount: null,
    itemCount: null,
    description: stringOrNull(raw.description) ?? '',
    raw,
  }
}

function adaptExam(raw: AdapterRecord): TaskRowVM {
  const row = adaptBase(raw)
  return {
    ...row,
    endAt: stringOrNull(raw.endAt),
    submissionCount: numberOrNull(raw.submissionCount) ?? 0,
    itemCount: numberOrNull(raw.questionCount),
  }
}

function adaptHomework(raw: AdapterRecord): TaskRowVM {
  const row = adaptBase(raw)
  return {
    ...row,
    endAt: stringOrNull(raw.dueAt),
    submissionCount: numberOrNull(raw.submissionCount) ?? 0,
  }
}

function adaptLab(raw: AdapterRecord): TaskRowVM {
  const row = adaptBase(raw)
  return {
    ...row,
    endAt: stringOrNull(raw.dueAt),
    itemCount: numberOrNull(raw.stepCount),
  }
}

const ADAPTERS: Record<WorkbenchDomain, (raw: AdapterRecord) => TaskRowVM> = {
  EXAM: adaptExam,
  HOMEWORK: adaptHomework,
  LAB: adaptLab,
}

export function taskAdapterOf(domain: WorkbenchDomain): TaskAdapter {
  const adaptRaw = ADAPTERS[domain]
  return {
    adapt(raw: any): TaskRowVM {
      if (!raw || typeof raw !== 'object') {
        return {
          id: 0,
          title: '',
          status: '',
          statusLabel: '未知',
          className: '未分配班级',
          startAt: null,
          endAt: null,
          submissionCount: null,
          itemCount: null,
          description: '',
          raw: null,
        }
      }
      return adaptRaw(raw as AdapterRecord)
    },
    filtersOf(raw: any): TaskFilterSnapshot {
      const row = raw && typeof raw === 'object' ? (raw as AdapterRecord) : {}
      const endAt = stringOrNull(row.endAt) ?? stringOrNull(row.dueAt)
      return {
        classId: numberOrNull(row.classId),
        status: stringOrNull(row.status) ?? '',
        deadlineBucket: bucketOf(endAt),
      }
    },
  }
}
