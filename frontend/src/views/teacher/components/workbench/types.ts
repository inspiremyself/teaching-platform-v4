export type WorkbenchDomain = 'EXAM' | 'HOMEWORK' | 'LAB'

export type DeadlineBucket = 'ALL' | 'UPCOMING' | 'OVERDUE' | 'UNDATED'

export interface TaskRowVM {
  id: number
  title: string
  status: string
  statusLabel: string
  className: string
  startAt: string | null
  endAt: string | null
  submissionCount: number | null
  itemCount: number | null
  description: string
  raw: any
}

export interface TaskFilterSnapshot {
  classId: number | null
  status: string
  deadlineBucket: DeadlineBucket
}

export type TaskActionKey = 'steps' | 'viewResult' | 'edit' | 'publish' | 'close'
export type ActionTone = 'ghost' | 'success' | 'warning' | 'primary'

export interface TaskActionVM {
  key: TaskActionKey
  label: string
  tone: ActionTone
  handler: (row: TaskRowVM) => void
}

export interface WorkbenchMeta {
  total: number
  filtered: number
  draft: number
  inProgress: number
  submissions: number
  hint: string
}

export interface QueueItemVM {
  id: number | string
  studentName: string
  status: string
  statusLabel: string
  objectiveScore: number | null
  manualScore: number | null
  totalScore: number | null
  draftFlag: boolean
}
